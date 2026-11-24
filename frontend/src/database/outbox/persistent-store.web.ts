import type { AccionPendiente, OutboxStore } from './types';

const DATABASE_NAME = 'napa-outbox-web';
const STORE_NAME = 'acciones';
const DATABASE_VERSION = 1;

function claveProducto(a: AccionPendiente): string | null {
    if (!a.ifMatch) return null;

    if (
        a.method === 'PATCH'
        && a.path.startsWith('/api/v1/productos/')
    ) {
        return a.path;
    }

    if (
        a.method === 'POST'
        && a.path.startsWith('/api/v1/productos/')
        && a.path.endsWith('/inventario')
    ) {
        return a.path.slice(0, -'/inventario'.length);
    }

    return null;
}

function claveRespuesta(a: AccionPendiente): string | null {
    if (a.method !== 'POST') return null;

    const match = a.path.match(
        /^(\/api\/v1\/pedidos\/.*)\/(?:aceptar|rechazar)$/,
    );

    return match?.[1] ?? null;
}

function esEdicionEmprendimiento(a: AccionPendiente): boolean {
    return a.method === 'PATCH'
        && a.path === '/api/v1/emprendimientos/me';
}

function emprendimientoEditado(a: AccionPendiente): string | null {
    if (!esEdicionEmprendimiento(a) || !a.body) return null;

    try {
        const body: unknown = JSON.parse(a.body);

        if (typeof body !== 'object' || body === null || !('base' in body)) {
            return null;
        }

        const base = body.base;

        if (
            typeof base !== 'object'
            || base === null
            || !('emprendimientoId' in base)
        ) {
            return null;
        }

        return typeof base.emprendimientoId === 'string'
            ? base.emprendimientoId
            : null;
    } catch {
        return null;
    }
}

function mensajeConfirmacion(a: AccionPendiente): string | null {
    if (a.method !== 'POST') return null;

    if (
        /^\/api\/v1\/domicilios\/.*\/confirmar-(recogida|salida)$/.test(a.path)
    ) {
        return 'Este domicilio ya tiene registrada esa confirmación pendiente.';
    }

    if (/^\/api\/v1\/pedidos\/.*\/pago\/confirmar$/.test(a.path)) {
        return 'Este pedido ya tiene registrada una confirmación de pago.';
    }

    if (/^\/api\/v1\/pedidos\/.*\/confirmar-entrega$/.test(a.path)) {
        return 'Este pedido ya tiene una confirmación de entrega y cobro.';
    }

    return null;
}

function conflicto(
    accion: AccionPendiente,
    existentes: AccionPendiente[],
): string | null {
    const producto = claveProducto(accion);
    const respuesta = claveRespuesta(accion);
    const emprendimiento = emprendimientoEditado(accion);
    const confirmacion = mensajeConfirmacion(accion);

    for (const existente of existentes) {
        if (existente.usuarioId !== accion.usuarioId) continue;

        if (producto !== null && claveProducto(existente) === producto) {
            return 'Este producto ya tiene un cambio pendiente.';
        }

        if (respuesta !== null && claveRespuesta(existente) === respuesta) {
            return 'Este pedido ya tiene una decisión pendiente o por revisar.';
        }

        if (
            emprendimiento !== null
            && emprendimientoEditado(existente) === emprendimiento
        ) {
            return 'Ya hay una edición pendiente para este emprendimiento.';
        }

        if (
            confirmacion !== null
            && existente.method === 'POST'
            && existente.path === accion.path
        ) {
            return confirmacion;
        }
    }

    return null;
}

/** Cola persistente del navegador, separada de SQLite nativo. */
export class PersistentOutboxStore implements OutboxStore {
    private databasePromise: Promise<IDBDatabase> | null = null;

    private async db(): Promise<IDBDatabase> {
        if (!this.databasePromise) {
            this.databasePromise = new Promise<IDBDatabase>((resolve, reject) => {
                if (typeof indexedDB === 'undefined') {
                    reject(new Error(
                        'Este navegador no permite guardar acciones pendientes. '
                        + 'Abre Ñapa en un navegador con almacenamiento habilitado.',
                    ));
                    return;
                }

                let finalizada = false;
                let request: IDBOpenDBRequest;
                try {
                    request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
                } catch {
                    reject(new Error(
                        'No se pudo abrir el almacenamiento de pendientes. '
                        + 'Comprueba los permisos y el espacio del navegador.',
                    ));
                    return;
                }

                request.onupgradeneeded = () => {
                    const database = request.result;

                    if (!database.objectStoreNames.contains(STORE_NAME)) {
                        const store = database.createObjectStore(
                            STORE_NAME,
                            { keyPath: 'id' },
                        );

                        store.createIndex(
                            'por_cuenta_y_clave',
                            ['usuarioId', 'idempotencyKey'],
                            { unique: true },
                        );
                    }
                };

                request.onsuccess = () => {
                    const database = request.result;

                    if (finalizada) {
                        database.close();
                        return;
                    }

                    finalizada = true;

                    database.onversionchange = () => {
                        database.close();
                        this.databasePromise = null;
                    };

                    database.onclose = () => {
                        this.databasePromise = null;
                    };

                    resolve(database);
                };

                request.onerror = () => {
                    if (finalizada) return;
                    finalizada = true;

                    reject(new Error(
                        'No se pudo abrir el almacenamiento de pendientes. '
                        + 'Comprueba los permisos y el espacio del navegador.',
                    ));
                };

                request.onblocked = () => {
                    if (finalizada) return;
                    finalizada = true;

                    reject(new Error(
                        'Otra pestaña impide actualizar el almacenamiento. '
                        + 'Cierra las demás pestañas de Ñapa e inténtalo nuevamente.',
                    ));
                };
            });
        }

        try {
            return await this.databasePromise;
        } catch (error) {
            this.databasePromise = null;
            throw error;
        }
    }

    private async transaccion<T>(
        modo: IDBTransactionMode,
        operar: (
            store: IDBObjectStore,
            acciones: AccionPendiente[],
        ) => T,
    ): Promise<T> {
        const database = await this.db();

        return new Promise<T>((resolve, reject) => {
            const transaction = database.transaction(STORE_NAME, modo);
            const store = transaction.objectStore(STORE_NAME);
            const lectura = store.getAll();

            let resultado: T;
            let errorOperacion: unknown;

            transaction.oncomplete = () => resolve(resultado);

            transaction.onabort = () => {
                reject(errorOperacion ?? new Error(
                    'No se pudo guardar el cambio en los pendientes. '
                    + 'Comprueba el espacio y los permisos del navegador.',
                ));
            };

            lectura.onsuccess = () => {
                try {
                    resultado = operar(
                        store,
                        lectura.result as AccionPendiente[],
                    );
                } catch (error) {
                    errorOperacion = error;
                    transaction.abort();
                }
            };
        });
    }

    async insertar(accion: AccionPendiente): Promise<void> {
        await this.transaccion('readwrite', (store, acciones) => {
            const duplicada = acciones.some((existente) =>
                    existente.id === accion.id
                    || (
                        existente.usuarioId === accion.usuarioId
                        && existente.idempotencyKey === accion.idempotencyKey
                    ),
            );

            const mensaje = conflicto(accion, acciones);

            if (mensaje) throw new Error(mensaje);

            if (duplicada) {
                if (esEdicionEmprendimiento(accion)) {
                    throw new Error(
                        'Ya hay una edición pendiente para este emprendimiento.',
                    );
                }

                if (accion.ifMatch) {
                    throw new Error('Este producto ya tiene un cambio pendiente.');
                }

                return;
            }

            store.add(accion);
        });
    }

    async listar(): Promise<AccionPendiente[]> {
        return this.transaccion('readonly', (_store, acciones) =>
            acciones.sort((a, b) => {
                if (a.createdAt !== b.createdAt) return a.createdAt - b.createdAt;
                return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
            }),
        );
    }

    async actualizar(accion: AccionPendiente): Promise<void> {
        await this.transaccion('readwrite', (store, acciones) => {
            const existente = acciones.find((a) => a.id === accion.id);

            if (!existente || existente.usuarioId !== accion.usuarioId) return;

            store.put({
                ...existente,
                status: accion.status,
                attempts: accion.attempts,
                lastError: accion.lastError,
                nextAttemptAt: accion.nextAttemptAt,
                errorStatus: accion.errorStatus,
            });
        });
    }

    async reemplazarFallida(
        accion: AccionPendiente,
        claveAnterior: string,
    ): Promise<boolean> {
        return this.transaccion('readwrite', (store, acciones) => {
            const existente = acciones.find((a) =>
                a.id === accion.id
                && a.usuarioId === accion.usuarioId
                && a.status === 'FALLIDA'
                && a.idempotencyKey === claveAnterior,
            );

            if (!existente) return false;

            const otras = acciones.filter((a) => a.id !== accion.id);
            const mensaje = conflicto(accion, otras);

            if (mensaje) throw new Error(mensaje);

            if (otras.some((a) =>
                a.usuarioId === accion.usuarioId
                && a.idempotencyKey === accion.idempotencyKey,
            )) {
                throw new Error(
                    'La clave de envío ya pertenece a otra acción pendiente.',
                );
            }

            store.put({
                ...existente,
                body: accion.body,
                idempotencyKey: accion.idempotencyKey,
                descripcion: accion.descripcion,
                status: accion.status,
                attempts: accion.attempts,
                lastError: accion.lastError,
                nextAttemptAt: accion.nextAttemptAt,
                createdAt: accion.createdAt,
                ifMatch: accion.ifMatch,
                errorStatus: undefined,
            });

            return true;
        });
    }

    async iniciarPrimerIntento(id: string): Promise<boolean> {
        return this.transaccion('readwrite', (store, acciones) => {
            const existente = acciones.find((a) =>
                a.id === id
                && a.status === 'PENDIENTE'
                && a.attempts === 0,
            );

            if (!existente) return false;

            store.put({ ...existente, attempts: 1 });
            return true;
        });
    }

    async borrarSinIntentos(
        id: string,
        usuarioId: string,
    ): Promise<boolean> {
        return this.transaccion('readwrite', (store, acciones) => {
            const existente = acciones.find((a) =>
                a.id === id
                && a.usuarioId === usuarioId
                && a.status === 'PENDIENTE'
                && a.attempts === 0,
            );

            if (!existente) return false;

            store.delete(id);
            return true;
        });
    }

    async borrar(id: string): Promise<void> {
        await this.transaccion('readwrite', (store) => {
            store.delete(id);
        });
    }

    async borrarTodo(): Promise<void> {
        await this.transaccion('readwrite', (store) => {
            store.clear();
        });
    }
}
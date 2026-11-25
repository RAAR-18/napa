const DATABASE_NAME = 'napa-pedidos-web';
const STORE = 'datos';

type Fila = { cuenta: string; clave: string; contenido: string; fecha: number };
let conexion: Promise<IDBDatabase> | undefined;

async function database(): Promise<IDBDatabase> {
  if (!conexion) {
    conexion = new Promise<IDBDatabase>((resolve, reject) => {
      if (typeof indexedDB === 'undefined') {
        reject(new Error('El navegador no permite guardar el carrito.'));
        return;
      }
      let terminado = false;
      let request: IDBOpenDBRequest;
      try {
        request = indexedDB.open(DATABASE_NAME, 1);
      } catch {
        reject(new Error('No se pudo abrir el almacenamiento del carrito.'));
        return;
      }
      request.onupgradeneeded = () => {
        if (!request.result.objectStoreNames.contains(STORE)) {
          request.result.createObjectStore(STORE, { keyPath: ['cuenta', 'clave'] });
        }
      };
      request.onerror = () => {
        terminado = true;
        reject(new Error('No se pudo abrir el almacenamiento del carrito.'));
      };
      request.onblocked = () => {
        terminado = true;
        reject(new Error('Cierra las otras pestañas de Ñapa y vuelve a abrir el carrito.'));
      };
      request.onsuccess = () => {
        const db = request.result;
        if (terminado) { db.close(); return; }
        terminado = true;
        db.onversionchange = () => { db.close(); conexion = undefined; };
        db.onclose = () => { conexion = undefined; };
        resolve(db);
      };
    });
  }
  try { return await conexion; }
  catch (error) { conexion = undefined; throw error; }
}

async function operar<T>(
  modo: IDBTransactionMode,
  tarea: (store: IDBObjectStore, resultado: (value: T) => void) => void,
): Promise<T> {
  const db = await database();
  return new Promise<T>((resolve, reject) => {
    const tx = db.transaction(STORE, modo);
    let resultado: T;
    let error: unknown;
    tx.oncomplete = () => resolve(resultado);
    tx.onabort = () => reject(error ?? new Error('No se pudo guardar el carrito en este navegador.'));
    try { tarea(tx.objectStore(STORE), (value) => { resultado = value; }); }
    catch (cause) { error = cause; tx.abort(); }
  });
}

export async function guardar(cuenta: string, clave: string, contenido: unknown): Promise<void> {
  const serializado = JSON.stringify(contenido);
  await operar<void>('readwrite', (store) => {
    store.put({ cuenta, clave, contenido: serializado, fecha: Date.now() });
  });
}

export function leer<T>(cuenta: string, clave: string, desde = 0): Promise<T | null> {
  return operar<T | null>('readonly', (store, resultado) => {
    const request = store.get([cuenta, clave]);
    request.onsuccess = () => {
      const fila = request.result as Fila | undefined;
      if (!fila || fila.fecha < desde) { resultado(null); return; }
      try { resultado(JSON.parse(fila.contenido) as T); }
      catch { resultado(null); }
    };
  });
}

export async function borrar(cuenta: string, clave: string): Promise<void> {
  await operar<void>('readwrite', (store) => { store.delete([cuenta, clave]); });
}

export async function borrarCotizaciones(cuenta: string): Promise<void> {
  await operar<void>('readwrite', (store) => {
    const request = store.openCursor();
    request.onsuccess = () => {
      const cursor = request.result;
      if (!cursor) return;
      const fila = cursor.value as Fila;
      if (fila.cuenta === cuenta && fila.clave.startsWith('cotizacion:')) cursor.delete();
      cursor.continue();
    };
  });
}

const STORE = 'lecturas';
export type LecturaGuardada = { contenido: string; actualizado_at: string };
let conexion: Promise<IDBDatabase> | undefined;
async function database(): Promise<IDBDatabase> {
  if (!conexion) {
    conexion = new Promise<IDBDatabase>((resolve, reject) => {
      if (typeof indexedDB === 'undefined') {
        reject(new Error('El navegador no permite guardar consultas sin conexión.'));
        return;
      }
      const request = indexedDB.open('napa-lecturas-web', 1);
      let terminado = false;
      request.onupgradeneeded = () => {
        if (!request.result.objectStoreNames.contains(STORE)) request.result.createObjectStore(STORE, { keyPath: 'clave' });
      };
      request.onerror = () => {
        terminado = true;
        reject(new Error('No se pudo abrir el almacenamiento de consultas.'));
      };
      request.onblocked = () => {
        terminado = true;
        reject(new Error('Cierra las otras pestañas de Ñapa y vuelve a intentar guardar la consulta.'));
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
async function operar<T>(modo: IDBTransactionMode,
  tarea: (store: IDBObjectStore, resultado: (value: T) => void) => void): Promise<T> {
  const db = await database();
  return new Promise<T>((resolve, reject) => {
    const tx = db.transaction(STORE, modo);
    let resultado: T;
    let error: unknown;
    tx.oncomplete = () => resolve(resultado);
    tx.onabort = () => reject(error ?? new Error('No se pudo completar el almacenamiento de consultas.'));
    try { tarea(tx.objectStore(STORE), (value) => { resultado = value; }); }
    catch (cause) { error = cause; tx.abort(); }
  });
}
export async function guardarLectura(clave: string, contenido: string): Promise<void> {
  await operar<void>('readwrite', (store) => {
    store.put({ clave, contenido, actualizado_at: new Date().toISOString() });
  });
}
export function leerLectura(clave: string): Promise<LecturaGuardada | null> {
  return operar<LecturaGuardada | null>('readonly', (store, resultado) => {
    const request = store.get(clave);
    request.onsuccess = () => {
      const fila = request.result as LecturaGuardada | undefined;
      resultado(fila ? { contenido: fila.contenido, actualizado_at: fila.actualizado_at } : null);
    };
  });
}
export async function borrarLectura(clave: string): Promise<void> {
  await operar<void>('readwrite', (store) => { store.delete(clave); });
}
export async function borrarRutasGuardadas(): Promise<void> {
  await operar<void>('readwrite', (store) => {
    const request = store.openCursor();
    request.onsuccess = () => {
      const cursor = request.result;
      if (!cursor) return;
      if (typeof cursor.key === 'string' && cursor.key.startsWith('ruta-domicilio:')) cursor.delete();
      cursor.continue();
    };
  });
}

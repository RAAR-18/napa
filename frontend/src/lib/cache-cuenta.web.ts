/** Cachés web por cuenta. Los cambios se ejecutan enteros en una transacción IndexedDB. */
export type FilaCache = { clave: string; contenido: string; fecha: number; secuencia: number };
type FilaPersistida = FilaCache & { cuenta: string; espacio: string };
const VIGENCIA_MS = 7 * 24 * 60 * 60 * 1000;
let conexion: Promise<IDBDatabase> | undefined;

async function database(): Promise<IDBDatabase> {
  conexion ??= new Promise<IDBDatabase>((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('El navegador no permite guardar consultas sin conexión.'));
      return;
    }
    const request = indexedDB.open('napa-caches-cuenta-web', 1);
    let terminado = false;
    request.onupgradeneeded = () => {
      const store = request.result.createObjectStore('datos', { keyPath: ['espacio', 'cuenta', 'clave'] });
      store.createIndex('porCuenta', ['espacio', 'cuenta']);
    };
    request.onerror = () => { terminado = true; reject(new Error('No se pudo abrir la caché del navegador.')); };
    request.onblocked = () => { terminado = true; reject(new Error('Cierra las otras pestañas de Ñapa y vuelve a intentar.')); };
    request.onsuccess = () => {
      const db = request.result;
      if (terminado) { db.close(); return; }
      terminado = true;
      db.onversionchange = () => { db.close(); conexion = undefined; };
      db.onclose = () => { conexion = undefined; };
      resolve(db);
    };
  });
  try { return await conexion; }
  catch (error) { conexion = undefined; throw error; }
}

async function operar(cuenta: string, espacio: string,
  cambiar?: (filas: FilaCache[]) => FilaCache[], vigente: () => boolean = () => true): Promise<FilaCache[]> {
  const db = await database();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('datos', cambiar ? 'readwrite' : 'readonly');
    let resultado: FilaCache[] = [];
    let error: unknown;
    tx.oncomplete = () => resolve(resultado);
    tx.onabort = () => reject(error ?? new Error('No se pudo completar la caché del navegador.'));
    const store = tx.objectStore('datos');
    const request = store.index('porCuenta').getAll([espacio, cuenta]);
    request.onsuccess = () => {
      try {
        const todas = request.result as FilaPersistida[];
        const validas = todas.filter((fila) => fila.fecha > Date.now() - VIGENCIA_MS)
          .sort((a, b) => b.fecha - a.fecha || b.secuencia - a.secuencia);
        if (cambiar && vigente()) {
          resultado = cambiar(validas);
          for (const fila of todas) store.delete([espacio, cuenta, fila.clave]);
          for (const fila of resultado) store.put({ ...fila, cuenta, espacio });
        } else resultado = validas;
      } catch (cause) { error = cause; tx.abort(); }
    };
  });
}
export function consultar(cuenta: string, espacio: string) { return operar(cuenta, espacio); }
export async function actualizar(cuenta: string, espacio: string,
  cambiar: (filas: FilaCache[]) => FilaCache[], vigente?: () => boolean): Promise<void> {
  if (vigente && !vigente()) return;
  await operar(cuenta, espacio, cambiar, vigente);
}
export function agregar(filas: FilaCache[], clave: string, valor: unknown): FilaCache[] {
  return [{ clave, contenido: JSON.stringify(valor), fecha: Date.now(),
    secuencia: Math.max(0, ...filas.map((fila) => fila.secuencia)) + 1 },
  ...filas.filter((fila) => fila.clave !== clave)];
}

/** Proveedores frecuentes; se usan para sugerir correcciones como "gmial.com" → "gmail.com". */
export const DOMINIOS_COMUNES = [
  'gmail.com',
  'hotmail.com',
  'hotmail.es',
  'outlook.com',
  'outlook.es',
  'live.com',
  'yahoo.com',
  'yahoo.es',
  'icloud.com',
  'msn.com',
  'protonmail.com',
  'proton.me',
  'aol.com',
];

const FORMATO =
  /^[a-z0-9](?:[a-z0-9._%+-]{0,62}[a-z0-9_%+-])?@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,24}$/;

function distancia(a: string, b: string): number {
  let previa = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i += 1) {
    const actual = [i];
    for (let j = 1; j <= b.length; j += 1) {
      const costo = a[i - 1] === b[j - 1] ? 0 : 1;
      actual[j] = Math.min(actual[j - 1] + 1, previa[j] + 1, previa[j - 1] + costo);
    }
    previa = actual;
  }
  return previa[b.length];
}

/**
 * Devuelve un mensaje de error si el correo no es válido, o null si está bien.
 * Las mismas reglas las aplica el backend, que además comprueba que el dominio reciba correo.
 */
export function validarCorreo(entrada: string): string | null {
  const correo = entrada.trim().toLowerCase();
  if (!correo) {
    return null;
  }
  if (correo.length > 254 || !FORMATO.test(correo) || correo.includes('..')) {
    return 'El correo no tiene un formato válido. Revísalo, por ejemplo: nombre@gmail.com.';
  }
  const arroba = correo.indexOf('@');
  const dominio = correo.slice(arroba + 1);
  if (DOMINIOS_COMUNES.includes(dominio)) {
    return null;
  }
  let mejor: string | null = null;
  let mejorDistancia = Number.POSITIVE_INFINITY;
  for (const comun of DOMINIOS_COMUNES) {
    const d = distancia(dominio, comun);
    if (d < mejorDistancia) {
      mejorDistancia = d;
      mejor = comun;
    }
  }
  if (mejor && mejorDistancia <= 2) {
    return `El dominio "${dominio}" no coincide con ningún correo conocido. ¿Quisiste decir ${correo.slice(0, arroba + 1)}${mejor}?`;
  }
  return null;
}

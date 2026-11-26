export async function solicitarPermisoSegundoPlano(): Promise<void> {
  throw new Error('En el navegador solo puedes compartir ubicación mientras Ñapa está abierta.');
}
export async function iniciarSegundoPlano(): Promise<void> {
  throw new Error('La ubicación en segundo plano requiere el build nativo de Ñapa.');
}
export async function detenerSegundoPlano(): Promise<void> {}
export async function reconciliarSegundoPlano(): Promise<void> {}

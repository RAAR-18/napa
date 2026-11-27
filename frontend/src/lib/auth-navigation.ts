import { router, type Href } from 'expo-router';

/** Las transiciones de sesión comienzan un historial nuevo en el stack raíz. */
export function replaceAuthHistory(destination: Href) {
  router.dismissAll();
  router.replace(destination);
}

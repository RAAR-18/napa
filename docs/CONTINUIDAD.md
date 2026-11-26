# Continuidad entre chats — Ñapa

Actualizado: 25 de noviembre de 2026.

## Repositorio y colaboración

- Carpeta real: `C:\Users\ESTUDIANTE\Documents\GitHub\napa`; no modificar la copia Documents/ChatGPT.
- Rama de trabajo `fix/p0-01-outbox-web`; base actual confirmada `4130e4a` (correcciones del testing en vivo). La carpeta estaba limpia al iniciar el bloque de segundo plano.
- Edición directa y comprobaciones autorizadas. Git manual: entregar siempre bloque completo para Git Bash al recomendar commit. No ejecutar add/commit/push ni ramas por iniciativa propia. Evitar repetir suites correctas; el tester valida recorridos físicos.

## Integrado antes de este bloque

- Auditoría: cachés/outbox web IndexedDB, carrito/dirección, horarios/fechas, navegación, calificaciones, categorías OTROS y privacidad de rutas por cuenta. Ver `docs/CORRECCIONES_AUDITORIA.md`.
- El equipo integró cambios de usabilidad, push y APK; main se incorporó a nuestra rama. La configuración actual mantiene las mejoras del equipo.
- 4130e4a: ambulante sin dirección fija escrita, GPS en primer plano; carrito sin límite comercial 20 en todas las capas; dirección por borrador/última entrega/perfil; anticipos bancarios de reservas aceptadas y avisos flotantes. Specs RF28/38/42/45 y requerimientos actualizados.
- V22 nueva retira CHECK cantidad<=20; no reescribir V11. Verificada en PostgreSQL aislado. La API debe reiniciarse para aplicarla en el entorno del usuario, si aún no lo hizo.
- Último audit observado: 27 moderadas, ninguna alta/crítica; Zod 4.6.7. No usar audit fix --force ni cambiar SDK/Jest sin acuerdo.

## Trabajo vigente sin commit: ubicación voluntaria en segundo plano

- Solicitud expresa: seguir actualizando posición del ambulante mientras usa otra app o bloquea pantalla, con activación y pausa a voluntad. No es historial de ruta diaria ni garantía después de forzar detención/cierre.
- Consentimiento separado: crear negocio conserva solo primer plano. Mi emprendimiento ofrece activar segundo plano y desactivarlo manteniendo foreground, además del control general que detiene ambos. Web no ofrece segundo plano.
- Nueva dependencia Expo compatible `expo-task-manager ~58.0.14`; se instaló con Expo. Task global en `lib/ubicacion-segundo-plano.ts`, importado al principio de _layout. Variante `.web.ts` sin tareas nativas. app.json habilita ubicación background/foreground service Android y UIBackgroundModes iOS.
- Preferencia/callbacks de parada en `ubicacion-preferencia.ts`; publicación sin React en `ubicacion-publicacion.ts`; `ubicacion-automatica.ts` conserva interfaz y bucle visible. No duplica sondeo visible cuando el servicio background está activo.
- Se procesa solo el punto más reciente y fresco (<=2 min, posterior al consentimiento). No hay cola de posiciones ni historial. Se comprueba cuenta/rol/negocio/generación y activación persistida, incluso después de renovar token, justo antes de HTTP.
- Android muestra notificación persistente e iOS indicador. Se detiene al pausar, iniciar/cerrar sesión, eliminar negocio o perder autorización. Revocar permiso downgradea a foreground sin solicitarlo ni reactivarlo automáticamente.
- Auth invoca paradas registradas sin importar módulos nativos/React. SecureStore serializa escrituras y adapta la sesión existente; solo sesión/preferencia usan AFTER_FIRST_UNLOCK para operar con pantalla bloqueada después de desbloquear una vez.
- Validación: 62 casos distintos de 9 archivos dirigidos correctos entre las ejecuciones/repeticiones (tarea, permisos, pausa, cuenta, token, UI y web); tipos y lint finales correctos, sin errores ni advertencias. Introspección de configuración confirmó permisos Android BACKGROUND_LOCATION y FOREGROUND_SERVICE_LOCATION, e iOS location. Exportación web correcta. No se generaron carpetas nativas ni APK; no se enviaron posiciones reales.
- Pendiente necesario: construir e instalar un nuevo APK/build nativo; Expo Go o un APK anterior no validan esta función. Perfil EAS preview existe. El tester prueba pantalla bloqueada, pausa, logout y revocación de permisos. No afirmar esa prueba física como realizada.

## Pendientes generales

- Proveedores SMS/correo, push con app cerrada y pruebas funcionales por roles dependen del entorno/equipo.
- Mapas visuales, ruta diaria como historial y optimización de varias entregas siguen fuera de este bloque.
- Guardar este bloque con sus specs, historias/trazabilidad y continuidad. El usuario gestiona commit/push; entregar comandos completos al terminar.

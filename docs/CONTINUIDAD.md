# Continuidad entre chats — Ñapa

Actualizado: 25 de noviembre de 2026.

## Repositorio y colaboración

- Carpeta real: `C:\Users\ESTUDIANTE\Documents\GitHub\napa`. No modificar la copia Documents/ChatGPT.
- Rama `fix/p0-01-outbox-web`; último commit confirmado `0e05de7`, privacidad de rutas guardadas. Main quedó integrado localmente en 0ed05bf; aún no publicar nuestra rama ni integrarla hacia main. Carpeta limpia al iniciar este bloque.
- Edición directa y comprobaciones autorizadas. Trabajar por bloques, repetir solo lo afectado por cambios/fallos. El tester valida recorridos funcionales.
- Git manual: entregar siempre el bloque completo de Git Bash al recomendar commit, con archivos explícitos. No ejecutar add/commit/push ni gestionar ramas por iniciativa propia. Preferencia guardada en AGENTS.md.

## Bloques integrados

- Outbox/carrito web, dirección guardada, horarios, navegación, calificaciones, selector de reserva, refresco visible de pedidos/notificaciones.
- Categoría Otros personalizada (V21) y ubicación ambulante voluntaria en primer plano: commit b8a8cc4.
- Verificación SMS/correo con concurrencia y cupos Redis: commit 817acbe; 18 pruebas unitarias/caso de uso y 7 Redis aislado correctas. Sin envíos reales ni secretos modificados.
- Listado/rutas con almacenamiento separado por plataforma: commit 180109b; 21 pruebas dirigidas, tipos/lint/exportación web correctos.

## Bloque integrado en 9800874: seis cachés web por cuenta (P0-01)

- Nuevas variantes `.web.ts`: perfil-cache, emprendimiento-propio-cache, inventario-cache, catalogo-cache, notificaciones-cache y pedidos-cliente-cache.
- Motor común `frontend/src/lib/cache-cuenta.web.ts`: transacciones IndexedDB completas, índice cuenta/espacio, vigencia siete días. Límites de 50 páginas por cuenta; consultas de pedidos por tipo. Perfil/negocio una copia. Sesión revalidada al escribir. El error revierte el cambio completo.
- Se conservan minimización de datos, filtros, borrado por propietario, renovación de inventario al cambiar de negocio y eliminación atómica de pedido cancelado de detalle/listados. Implementaciones SQLite nativas intactas.
- Pruebas nuevas `frontend/src/lib/caches-cuenta.web.test.ts`; plan foundations y documentación actualizados. Sin dependencias, API, migraciones o servicios nuevos.
- Validación: 12 pruebas web nuevas y 3 de perfil nativo correctas. Lint completo y dirigido final correctos. Exportación web correcta y sin recursos WASM/worker de SQLite en la salida generada. Tipos finales correctos.
- No se migran cachés web antiguas: se regeneran consultando en línea. El outbox SQLite web anterior no se migra; sincronizar pendientes antes de desplegar. No borrar datos del navegador como solución.

## Pendientes y próximo paso

- El bloque de cachés quedó integrado; el trabajo vigente es la actualización de listados descrita abajo. La revisión funcional en navegador/móvil la realiza el tester; no afirmar que se hizo.
- Detalle de todas las incidencias: `docs/CORRECCIONES_AUDITORIA.md`. Las variantes web completan la separación de almacenamiento de las cachés identificadas; P0-01 queda implementado, pendiente de aceptación funcional.
- Proveedores SMSReactor/Brevo requieren configuración operativa del equipo. No solicitar secretos completos ni enviar mensajes reales sin necesidad autorizada.
- Ubicación automática ambulante solo en primer plano; segundo plano requiere alcance/build/permisos específicos. Notificaciones usan sondeo visible/push, no SSE estricto.
- Pagos anticipados, límite de 20 y composición de contraseña dependen de specs; el informe no redefine sus acuerdos.
- Mapas visuales y rutas optimizadas siguen pospuestos hasta cerrar auditoría y confirmar alcance.
- Selector nativo de reserva de un bloque anterior requiere regenerar el build Android/iOS; este bloque no añade dependencias nativas.


## Actualización de listados integrada en c60d6ab (P1-02)

- `frontend/src/app/domicilios-disponibles.tsx`: consulta enfocada cada 15 s, pausa automática con app inactiva, reanudación al volver, sin consultas superpuestas por ciclo y sin respuestas/errores antiguos al salir. Usa useFocusEffect y AppState existentes.
- `frontend/src/app/mis-pedidos.tsx` y `domicilios-en-curso.tsx`: carga al enfocar y useAutoRefresh cada 30 s cuando no carga y app activa. Conservan filtros y cachés. El cierre de ciclo invalida respuestas.
- Pruebas `frontend/src/app/__tests__/listados-actualizacion.test.tsx` y prueba existente `hooks/useAutoRefresh.test.ts`; plan RF-38 actualizado. Validación: 5 pruebas nuevas de pantallas y 1 del hook existente correctas; repetición de las 5 nuevas tras corregir el 401 de prueba, correcta. Tipos y lint dirigido finales correctos, sin errores/advertencias. No hubo cambios backend/dependencias ni se repitió la exportación web.
- No se añaden SSE, push, permisos, dependencias ni mapas. Revisión funcional por roles pendiente para el tester. El usuario hace commit/push; entregar bloque completo Git Bash al cerrar.


## Revisión de dependencias integrada en 3d5b535

- Main quedó integrado mediante 0ed05bf sin conflictos. Se conservaron los cambios del equipo: pools backend y expo-build-properties para APK Android, junto con nuestras dependencias. El usuario completó npm ci, tipos y lint. No publicar ni integrar nuestra rama hacia main todavía; continuar corrigiendo localmente.
- npm audit reprodujo 28 alertas: 27 moderadas y 1 alta. La alta es GHSA-mrhx-c7xc-m3w5 en zod 3.25.76, consumido por eslint-plugin-react-hooks y zod-validation-error.
- Corrección puntual: npm update zod --ignore-scripts --no-audit --no-fund, sin agregar dependencia directa. El lock resuelve zod 4.6.7; ambos consumidores admiten ^3.25.0 || ^4.0.0. También se deduplicó @expo/env 2.6.0, manteniendo su versión raíz idéntica. package.json y versiones Expo/React Native/Jest intactos.
- Archivos afectados: frontend/package-lock.json, specs/foundations/plan.md y documentación de continuidad/auditoría. Sin cambios de código funcional, API, datos, permisos o builds nativos.
- Validación final: tipos y lint correctos, 1 prueba del hook correcta, npm ls zod sin conflictos. Auditoría final: 27 moderadas, 0 altas, 0 críticas (antes 28, incluida 1 alta). No se repitieron suites completas ni exportación: no cambia código de la app. Este bloque quedó integrado en 3d5b535; no hacer push por iniciativa propia.
- Moderadas pendientes: glob/minimatch en herramientas Expo/Jest/ESLint. La solución automática propone retroceder Expo/Jest o cambiar versiones principales transitivas; no aplicar audit fix --force ni overrides globales. Esperar actualización compatible de consumidores o planificar cambio separado. No afirmar cero vulnerabilidades.


## Privacidad de rutas integrada en 0e05de7 (25 de noviembre)

- `frontend/src/lib/ruta-cache.ts`: guardar/leer/borrar requieren cuenta explícita; clave compuesta cuenta/domicilio. Sin cuenta no accede al almacenamiento. La lectura comprueba que coincide el domicilio. No adopta claves antiguas sin propietario: abrir una vez la ruta con conexión para regenerarla por cuenta.
- `frontend/src/app/ruta-domicilio.tsx`: captura cuenta/rol/generación antes del GPS y API. Comprueba vigencia antes de mostrar, guardar o recuperar copias y descarta errores de sesiones anteriores. El ciclo termina al perder foco. Limpia la vista al iniciar una nueva consulta.
- `domicilio-domiciliario.tsx`: al entregar borra solo la ruta de la cuenta del detalle. La limpieza global existente al cerrar sesión mantiene su contrato.
- Pruebas: ruta-cache.web.test.ts, ruta-domicilio.test.tsx; también se revisaron ejecucion-domicilio.test.tsx y domiciliario.test.tsx. El simulador antiguo de domiciliario necesitaba useFocusEffect y AppState activo, coherentes con el bloque P1-02 anterior; se actualiza sin relajar aserciones.
- Validación final: 40 pruebas dirigidas correctas (16 caché/pantalla de ruta, 15 ejecución de domicilios y 9 domiciliario). Repetición de las 6 de caché y de las 9 de domiciliario tras ajustes, correctas. Tipos correctos; lint final de archivos afectados sin errores ni advertencias. No se repitió backend o exportación: no cambian API, dependencias ni selección de plataforma. La revisión funcional queda al tester.
- Plan RF-22 y seguimiento actualizados. No hay mapas nuevos, cambios API, migraciones, permisos o dependencias. Mantener la rama local, sin push ni integración hacia main.


## Comprobación conjunta de cierre (2026-11-25)

- Carpeta limpia al comenzar. Se ejecutó una vez la suite completa frontend después de todos los bloques: 51 suites y 337 pruebas correctas, cero fallos. No aparecieron archivos de pruebas que requirieran correcciones. No se modificó código de aplicación ni se repitieron comprobaciones individuales.
- Tipos y lint del bloque anterior ya estaban correctos; no se repitieron porque el código no cambió. No presentar la suite Jest como una prueba funcional manual ni de arranque Android/iOS.
- En esta comprobación solo se actualizan CONTINUIDAD.md y CORRECCIONES_AUDITORIA.md para guardar evidencia. Se pueden incluir con el siguiente cambio; no se ejecutó Git de escritura.
- Próximo paso: el tester aporta fallos reproducibles por rol/pantalla. No fabricar incidencias ni extender funcionalidades para mantener una cadena de commits.
- Pendientes externos/de alcance: SMSReactor/Brevo operativos, prueba del build nativo (incluye selector de reservas y corrección del APK del equipo), aceptación funcional de auditoría, decisión de segundo plano/SSE y las 27 alertas moderadas de herramientas pendientes de actualización compatible. Sin alertas altas en el último audit observado.
- Mapas visuales y rutas optimizadas permanecen fuera de esta etapa hasta que el usuario indique pasar a esa funcionalidad. No cambiar reglas de pagos, límites de productos o contraseñas sin acordar sus specs.

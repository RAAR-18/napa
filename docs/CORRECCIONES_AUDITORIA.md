# Seguimiento de correcciones de auditoría — Ñapa

Actualizado: 24 de noviembre de 2026. Base del bloque actual: `9800874`, rama `fix/p0-01-outbox-web`.

## Estado vigente

| Incidencia | Resultado |
|---|---|
| P0-01 | Cola web en IndexedDB; SQLite conservado en Android/iOS. Errores claros, persistencia y concurrencia verificadas. Las cachés de lectura también tienen variantes IndexedDB: listado/rutas, perfil, negocio propio, inventario, catálogo, notificaciones y pedidos. No migra pendientes ni cachés antiguas de SQLite web; validación funcional del tester pendiente. |
| P0-02 | Validación de disponibilidad conocida antes de cotizar. El backend valida y descuenta al aceptar; no se reserva inventario al crear, según RF-45. |
| P0-03 | Entrada de horario compartida, numérica, HH:mm, validación de rango y de hora incompleta. |
| P0-04 | Feedback de longitud/coincidencia y bloqueo ante contraseñas distintas. Se conserva D14: 8–128 caracteres, sin composición obligatoria. |
| P0-05 | Correo recomendado y SMS alternativo; confirmación/reenvío protegidos por generación y operaciones Redis atómicas; TTL y cupos garantizados dentro de Redis. La entrega real depende de la configuración de proveedores. |
| P0-06 | Guardado automático del carrito por cuenta/negocio, restauración y limpieza coordinada tras enviar. |
| P1-01 | Anticipos habilitados para reservas aceptadas por transferencia; confirma el vendedor. Al entregar no se duplica el pago ni el evento. Efectivo se conserva presencial. |
| P1-02 | Aviso flotante global por novedades de la bandeja cada 15 s/push con app abierta, sin marcar lectura ni alertar historial inicial. Las pantallas conservan sus refrescos; push cerrado requiere Firebase/build. |
| P1-03 | Dirección automática por prioridad: borrador no vacío, última entrega y perfil de la cuenta. No pisa escritura manual ni usa GPS para decidir el destino del cliente. |
| P1-04 | Se eliminó el límite comercial de 20 productos/unidades por acuerdo del 25/11. Se conservan stock, cantidades positivas y precisión NUMERIC(10,2); migración V22 nueva. |
| P1-05 | Implementado segundo plano nativo opcional con consentimiento separado, control de pausa, servicio/notificación Android e indicador iOS. Requiere nuevo APK/build y prueba física del tester. Web solo primer plano; forzar detención puede parar el servicio. |
| P1-06 | Acceso a editar negocio desde Mis productos y guía a edición ante conflicto de creación. |
| P1-07 | Selector visual de reserva del sistema en móvil y del navegador en web; validación de fecha real y futura. |
| P2-01 | Otros admite texto personalizado opcional de hasta 80 caracteres, persistido y mostrado en creación, consulta propia, listado y detalle. Migración nueva V21. |
| P2-02 | Ambulante sin dirección/referencia/coordenadas manuales: GPS al publicar. Punto fijo conserva sus datos de ubicación; mapa visual sigue pospuesto. |
| P2-03 | Menú móvil único; se retiró la barra inferior que duplicaba destinos. |
| P2-04 | Confirmación de métodos de pago en un aviso de éxito; disponibilidad de domicilio conserva su estilo informativo. |
| P2-05 | Botón de calificación según opciones pendientes; se actualiza al volver a la pantalla. |
| P3-01 | Crear cuenta unificado en entrada, login y título de registro. |
| P3-02 | Un único acceso Mi cuenta en navegación; perfil conservado dentro de la cuenta. |

## Bloque anterior: categoría y actualización de zona (commit b8a8cc4)

La categoría sigue siendo un enum fijo. `categoriaPersonalizada` complementa únicamente OTROS; un texto vacío se normaliza a ausencia, más de 80 caracteres o combinado con otra categoría se rechaza. El nombre/categoría siguen siendo inmutables tras crear. Las ediciones y eliminaciones conservan el complemento.

V21 agrega una columna nullable y una restricción coherente en PostgreSQL, sin reescribir V1–V20 ni cambiar datos existentes. Las pruebas usan PostgreSQL/PostGIS de Testcontainers. No se ejecutó la migración en la base local del usuario; Flyway la aplicará al iniciar una API con este código.

La ubicación automática requiere activación expresa en Mi emprendimiento y permiso de ubicación. La preferencia pertenece a cuenta y negocio. No lee GPS sin una preferencia válida para la cuenta actual ni en segundo plano; conserva dirección/referencia y solo envía el punto. Las operaciones comprueban generación de sesión, propietario/rol y preferencia vigente. Sin conexión no se encolan posiciones antiguas.

## Comprobaciones y límites

El bloque anterior pasó comprobaciones de tipos, lint, pruebas afectadas y exportación web. En este bloque pasó la comprobación inicial del módulo de emprendimientos y ArchUnit, cargando V21 en PostgreSQL aislado, sin omisiones. Regresiones finales nuevas: 11 pruebas backend (dominio y persistencia), sin fallos, errores ni omisiones; frontend: 12 pruebas de la repetición dirigida final correctas, además de las consultas/pantallas que ya habían pasado. Typecheck, lint y exportación web finales correctos. La prueba que agotó 5 s pasó aislada sin cambiar su timeout. No se ejecutó de nuevo toda la suite porque el resto no cambió.

El tester del grupo valida los recorridos funcionales. Las comprobaciones se ejecutan por bloques y solo se repiten las afectadas por cambios/fallos. El mapa y el recorrido de varias entregas continúan pospuestos.

El selector de reservas incorporó `@react-native-community/datetimepicker` 9.2.1 en el bloque anterior: los builds de desarrollo Android/iOS deben regenerarse para incluirlo. Este bloque no añade dependencias nativas.

## Pendientes reales

- Configurar y comprobar los proveedores de verificación en el entorno del equipo, sin inventar credenciales ni prometer envíos desde un proveedor deshabilitado.
- Seguimiento del ambulante en segundo plano: requiere entrega específica con permisos, servicio/tarea y build nativo. El modo actual explica que funciona mientras se usa Ñapa.
- Decidir si se necesita SSE/WebSocket estricto además del sondeo y push existentes.
- Cambios de política de pagos, stock, límites o contraseñas requieren reflejar el nuevo acuerdo en specs; el informe no sustituye esas reglas por sí solo.

Git: el commit `b8a8cc4` sí quedó creado por el usuario y la carpeta estaba limpia antes del bloque de verificación. No se ejecutaron git add/commit/push ni cambios de rama.


## Bloque actual: fiabilidad de verificación (P0-05)

- Código, contador de intentos, generación y TTL se guardan juntos; no queda un código nuevo sin vencimiento por un fallo entre escrituras.
- La confirmación lee un desafío coherente y compara su generación antes de consumirlo o descontar intentos. Un reenvío concurrente no se elimina ni recibe intentos del código anterior.
- Se mantienen diez minutos, cinco intentos, consumo único y compatibilidad con desafíos anteriores que aún tengan vencimiento. Una clave sin vencimiento se invalida.
- Cupo diario reservado atómicamente; los envíos simultáneos no sobrepasan el último cupo. Si Redis no confirma la reserva, no se contacta al proveedor. Una liberación no crea contadores negativos.
- Un correo faltante se rechaza antes de consumir cupo. Los errores de proveedor no se registran con su cuerpo, que podría reflejar códigos o destinatarios.
- README actualizado: los proveedores implementados son SMSReactor/Brevo y el registro desde la app solicita el código después de crear la cuenta. Se documentan los nombres de variables, sin credenciales ni cambios de entorno.

Comprobación: 18 pruebas unitarias/de caso de uso y 7 de integración Redis correctas, sin fallos, errores u omisiones. Backend compilado. No se ejecutaron pruebas frontend porque no hubo cambios allí. No se enviaron mensajes reales, no hubo migraciones ni cambios de configuración local y no se repitieron las suites no afectadas.

Siguen pendientes la configuración operativa de proveedores por el equipo, el seguimiento en segundo plano y la decisión sobre SSE estricto. Los cambios de reglas de pagos, límites y contraseñas no se tratan como fallos sin cambiar primero el acuerdo. Mapas y rutas optimizadas siguen pospuestos hasta cerrar esta etapa con el usuario.


## Bloque posterior a 817acbe: cachés de listado y rutas (P0-01)

- `frontend/src/lib/cache-lectura-storage.ts` conserva napa.db, tabla cache_lectura, claves y datos existentes en Android/iOS.
- `cache-lectura-storage.web.ts` usa IndexedDB en web y confirma al completar la transacción; permite reintentar si el navegador bloquea almacenamiento.
- `emprendimientos-cache.ts` y `ruta-cache.ts` delegan al almacenamiento seleccionado por plataforma. Conservan validaciones, fechas y eliminación de rutas; borrar direcciones de rutas no elimina el listado de negocios.
- No hay migración de cachés SQLite antiguas del navegador: se regeneran consultando en línea. No se instalaron dependencias ni se cambiaron API, proveedores o funcionalidad de mapas.
- Pruebas observadas: 9 de almacenamiento/consumidores y 12 de las pantallas de listado/ruta, correctas. Exportación web correcta. Comprobación de tipos y lint finales correctos, sin errores ni advertencias.
- Siguiente bloque concreto: perfil-cache.ts, emprendimiento-propio-cache.ts, inventario-cache.ts, catalogo-cache.ts, notificaciones-cache.ts y pedidos-cliente-cache.ts aún importan SQLite sin variante web. Conservar aislamiento por cuenta, vencimiento, límites y orden de escrituras al corregirlos. P0-01 no está cerrado para toda la app.


## Bloque posterior a 180109b: seis cachés web por cuenta

- Se agregan `perfil-cache.web.ts`, `emprendimiento-propio-cache.web.ts`, `inventario-cache.web.ts`, `catalogo-cache.web.ts`, `notificaciones-cache.web.ts` y `pedidos-cliente-cache.web.ts`.
- Almacenamiento compartido en `cache-cuenta.web.ts`: cuenta y espacio separados mediante índice compuesto; lectura/modificación/borrado dentro de una transacción. Confirmación únicamente al completarse; sesión revalidada antes de escribir.
- Se conservan siete días, 50 páginas por cuenta para catálogo/inventario/notificaciones y 50 registros por tipo de consulta de pedidos. Perfil y negocio propio conservan una copia por cuenta.
- Perfil no agrega coordenadas/referencia/foto y las notificaciones no guardan referencias de navegación. Inventario descarta el negocio anterior; cancelación de pedido elimina detalle y presencia en listados atómicamente.
- SQLite nativo no se modifica. Sin dependencias, cambios de API ni migración de datos del navegador. Las cachés se regeneran consultando en línea; los pendientes antiguos de outbox requieren sincronizarse antes de desplegar, según el bloque original.
- Exportación web correcta: el listado de recursos generado ya no incluye el motor WASM ni el worker de SQLite. Esto verifica la selección de variantes, sin sustituir la revisión funcional del tester.
- Preferencia del usuario guardada en AGENTS.md: cada recomendación de commit incluye el bloque completo para Git Bash; Git sigue siendo manual.
- Validación: 12 pruebas web nuevas correctas y 3 de perfil nativo correctas; lint completo y revisión dirigida final sin errores ni advertencias. Exportación web correcta. Comprobación de tipos final correcta.


## Bloque posterior a 9800874: actualización de listados (P1-02)

- `domicilios-disponibles.tsx`: el ciclo de consulta depende del foco; cancela temporizador/listener al salir y descarta respuestas y errores antiguos. Conserva 15 s. No inicia nuevas solicitudes periódicas con la app inactiva y no superpone consultas de un mismo ciclo. Consulta al reanudar la app.
- `mis-pedidos.tsx` y `domicilios-en-curso.tsx`: recargan al volver a la pantalla y usan el hook existente de 30 s, deshabilitado durante carga y con app inactiva. Conservan filtros, paginación y acceso a copias offline.
- Un 401 antiguo de Disponibles no limpia la sesión ni redirige después de salir; Domicilios en curso también descarta errores de un ciclo que perdió el foco.
- Pruebas nuevas `frontend/src/app/__tests__/listados-actualizacion.test.tsx`. Plan RF-38 actualizado. No hay SSE, nuevos permisos, dependencias, cambios backend o proveedores.
- Validación: 5 pruebas nuevas de pantallas y 1 del hook existente correctas. Repetición dirigida final de las 5 pantallas/casos correcta tras ajustar el 401 de la muestra. Typecheck final y lint dirigido final sin errores ni advertencias. No se repitió backend ni exportación: no cambian API, dependencias o resolución de plataforma. La prueba funcional por roles sigue a cargo del tester.


## Dependencias después del merge local 0ed05bf

Main quedó integrado en la rama de correcciones. El usuario completó npm ci/tipos/lint sin errores y pidió mantener el trabajo local sin push ni integración de nuestra rama a main.

La revisión npm audit confirmó 28 alertas (27 moderadas, 1 alta). La alta [GHSA-mrhx-c7xc-m3w5](https://github.com/advisories/GHSA-mrhx-c7xc-m3w5) corresponde a Zod; el proveedor publica versión corregida desde 4.6.6. Se resolvió la dependencia transitiva a 4.6.7, compatible con los rangos declarados de eslint-plugin-react-hooks 7.1.1 y zod-validation-error 4.0.2. Se modifica solo el lock y se deduplica @expo/env 2.6.0 sin cambiar su versión. No se modifica package.json ni se actualiza Expo, React Native o Jest.

Las moderadas de glob/minimatch y sus consumidores quedan pendientes de una actualización compatible. No se fuerza un cambio de major transitivo ni un retroceso del SDK para reducir artificialmente el conteo. Validación final: tipos y lint correctos; 1 prueba del hook correcta; npm ls zod sin conflictos. Auditoría final: 27 moderadas, 0 altas y 0 críticas. No se ejecutó nuevamente la suite completa ni exportación porque no cambia código de la aplicación.


## Privacidad de rutas guardadas (25 de noviembre, posterior a 3d5b535)

Se detectó que la clave de caché de ruta solo contenía el domicilio y podía recuperarse como respaldo ante un error de red desde otra cuenta. Se cambia a clave compuesta cuenta/domicilio, con propietario obligatorio y rechazo de claves antiguas sin propietario. Las copias se regeneran al consultar con conexión; no se eliminan pedidos ni se modifican rutas del servidor.

La pantalla valida cuenta, rol y generación antes de pedir GPS/API y antes de mostrar/guardar/recuperar datos, invalidando su ciclo al perder foco. Al entregar se borra la copia de la cuenta del detalle. Esto corrige almacenamiento y privacidad existentes; no inicia la fase de mapas o rutas optimizadas.

Pruebas dirigidas: aislamiento A/B, borrado por propietario, clave antigua, contenido inválido, domicilio discordante, uso offline autorizado, cambio de sesión durante consulta y error antiguo tras salir. Se actualizaron los simuladores de navegación/AppState de la suite antigua de domiciliario sin cambiar sus expectativas. Validación final: 40 pruebas dirigidas correctas, tipos correctos y lint final de archivos afectados sin errores ni advertencias. No se repitió backend ni exportación porque no cambian API, dependencias o selección de plataforma. Revisión funcional por el tester pendiente.


## Comprobación conjunta de frontend (2026-11-25, base 0e05de7)

Se ejecutó una vez la suite completa tras integrar todos los bloques: **51 suites y 337 pruebas correctas**, cero fallos. No se modificó código de aplicación ni hubo necesidad de repetir archivos aislados. Tipos/lint ya habían pasado en el último bloque y no se repitieron sin cambios.

Esta evidencia corresponde a Jest, no al recorrido funcional del tester ni al arranque de un APK. Permanecen pendientes la aceptación por roles, configuración real de proveedores, build nativo y decisiones de alcance ya registradas (segundo plano, SSE estricto, mapas posteriores). Las 27 alertas moderadas de herramientas no se ocultan ni se fuerzan cambios incompatibles para eliminarlas. No se ejecutaron commits, push o merges en esta comprobación.


## Reapertura por testing en vivo (2026-11-25, base c26bf01)

Los resultados nuevos del tester son un cambio de alcance autorizado: reemplazan la conservación previa de límites de 20 y cobro exclusivo al entregar reservas. Se actualizan RF28/38/42/45 y trazabilidad.

- Ambulante sin campos de dirección fija: GPS al publicar y actualización en primer plano cada 15 s con mínimo 25 m, permiso explícito y pausa voluntaria. No se implementa historial de ruta diaria ni seguimiento con app cerrada.
- Carrito sin límite comercial 20: frontend, HTTP, cotización y dominio coherentes. V22 nueva retira el CHECK <=20; mantiene precisión numérica y cantidad positiva. No se migra la base local manualmente.
- Dirección: recupera perfil cuando no hay borrador no vacío/última entrega; no pisa datos escritos y el perfil no bloquea el guardado del carrito.
- Reserva aceptada por transferencia: confirmación anticipada del vendedor; al entregar se reutiliza el pago confirmado sin repetir evento/cobro. No hay procesamiento de dinero ni reembolsos automáticos.
- Aviso flotante global desde la bandeja (sondeo 15 s/push en primer plano), sin marcar lectura ni mostrar historial inicial como novedad. Firebase no es necesario para este aviso interno, pero sí para push con app cerrada.

Validación final: backend y arquitectura correctos; 13 pruebas dirigidas incluyendo V22 con PostgreSQL aislado (sin omisiones), más repetición de las 2 pruebas de pago tras el cambio final. Frontend: la corrida completa inicial tuvo 7 fallos; se corrigió el bloqueo de recuperación de carrito y se repitieron solo los afectados. Repetición final de 4 archivos: 33 pruebas correctas. Los avisos y DateTime ya habían pasado en la repetición anterior; GPS pasó sus 3 pruebas finales. Tipos, lint y exportación web correctos. No se volvió a ejecutar toda la suite ni se probaron APK, push real o cobros reales. No hubo dependencias nuevas. Antes del testing del límite, reiniciar la API para aplicar V22; no se migró la base local del usuario desde el asistente.


## Ubicación voluntaria en segundo plano (2026-11-25, base 4130e4a)

Se añade expo-task-manager compatible y tarea global, con modo web separado. El alta del negocio no activa este modo por sí sola: se solicita desde Mi emprendimiento y puede apagarse manteniendo solo foreground o detener todos los envíos. Android mantiene una notificación e iOS un indicador visible. El sistema controla intervalos y supervivencia; forzar detención/cierre o restricciones del fabricante pueden detenerlo.

La tarea no registra rutas: usa el punto fresco más reciente, revalida cuenta/rol/negocio/consentimiento y no encola historial sin conexión. Pausar invalida primero la preferencia; logout/login y eliminación detienen el seguimiento. Permiso revocado o autorización perdida detiene background. SecureStore cifrado adapta sesión/preferencia para pantalla bloqueada tras primer desbloqueo; escrituras serializadas.

Se verificaron permisos/modos mediante introspección sin generar carpetas nativas, las pruebas dirigidas y exportación web. Tipos/lint finales correctos, sin errores ni advertencias. Las ejecuciones dirigidas cubrieron 62 casos distintos en 9 archivos; el único timeout de creación se repitió correctamente sin aumentar su límite. No se construyó ni instaló APK ni se probaron lecturas GPS reales en segundo plano. El equipo debe generar un nuevo build e instalarlo para la aceptación funcional.

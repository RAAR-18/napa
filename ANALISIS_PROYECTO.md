# Análisis del proyecto Ñapa

Fecha: 11 de noviembre de 2026. Revisión del repositorio local, sus especificaciones, configuración, código y pruebas. Incluye la validación posterior con Docker, las correcciones de sesiones, OTP, registro y web, y la gestión en línea de productos e inventario, con edición y eliminación.

Ñapa tiene una base técnica bien organizada y una definición funcional extensa. Su estado actual es un prototipo con identidad, creación y exploración de emprendimientos, publicación de productos, inventario manual y consulta del catálogo, más infraestructura de notificaciones. Todavía no permite completar una compra: faltan pedidos, cobros registrados y entregas. Se corrigieron las sesiones, la configuración OTP y las colisiones de registro descritas en H1–H4 y se validaron las integraciones con Docker. El usuario confirmó el acceso desde el iPhone y las pruebas anteriores de emprendimientos. La primera entrega de productos e inventario ya está implementada y activada en `napa_dev`; el usuario confirmó que realizó la prueba manual y funcionó correctamente. Antes del piloto quedan la sincronización sin conexión, las comprobaciones de fallos de red y los riesgos restantes.

## 1. Alcance y evidencia

| Elemento | Inventario del análisis inicial |
|---|---:|
| Requerimientos funcionales documentados | 67 |
| Requerimientos del MVP | 66; RF-59 está diferido |
| Requerimientos no funcionales | 31 |
| Archivos Java de producción | 153 |
| Líneas Java de producción | 7.990 |
| Archivos Java de pruebas | 44 |
| Archivos TypeScript/TSX dentro de `frontend/src`, incluidas pruebas | 23 |
| Líneas TypeScript/TSX dentro de `frontend/src` | 3.401 |
| Migraciones Flyway | 6 |

Se revisaron la estructura completa, los contratos expuestos, los flujos principales de aplicación, persistencia, autenticación, filtros, eventos, caché, CI y documentación. Se inventariaron los 67 directorios RF y se comprobaron los enlaces locales de la documentación. La revisión estática se concentró en los componentes implementados; la existencia de una spec o un plan no se consideró evidencia de implementación.

No se verificó una aplicación desplegada, el estado remoto de GitHub Actions, un dispositivo físico ni los servicios comerciales con credenciales reales. Tampoco se realizó una auditoría de vulnerabilidades de dependencias Java o una prueba de penetración.

## 2. Producto y reglas de negocio

El producto conecta comercio informal y pequeños negocios con clientes cercanos. Distingue cliente, vendedor ambulante, vendedor de punto fijo, domiciliario y administrador.

Las decisiones principales están suficientemente explícitas para guiar el desarrollo:

- Ambulante: entrega directa. Punto fijo: recogida o domicilio.
- La reserva es un atributo del pedido con fecha acordada.
- Los pagos ocurren fuera de Ñapa; el sistema registra sus confirmaciones.
- El domicilio exige transferencia al vendedor antes de publicarse.
- La entrega del domicilio se confirma mediante el código del cliente.
- La predicción de demanda queda fuera del MVP.

La investigación y el mapa de empatía insisten en tareas cortas, conectividad irregular y teléfonos modestos. Eso debe guiar las siguientes funcionalidades: hoy el formulario de emprendimiento requiere escribir latitud y longitud manualmente, una fricción significativa para el público descrito. Conviene validar ese formulario con vendedores y ofrecer una selección de ubicación comprensible.

El alcance de 66 RF para un proyecto académico de cuatro meses es considerable. No resulta útil convertir el número de archivos o de casillas marcadas en un porcentaje de avance: hay funcionalidades de esfuerzos muy distintos y varios planes desactualizados. Conviene medir avance con recorridos completos del usuario.

## 3. Arquitectura y stack

Es un monorepo con un backend que se despliega como una aplicación Spring Boot y un frontend móvil Expo. En Java existen cuatro módulos funcionales: `cuentas`, `emprendimientos`, `productos` y `notificaciones`, además de `shared`.

| Capa | Implementación observada |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring Security, JPA/JDBC |
| Datos persistentes | PostgreSQL/PostGIS, migraciones Flyway, validación del esquema por Hibernate |
| Datos temporales | Redis para sesiones, límites, idempotencia y rutas |
| Frontend | Expo 58, React 19.3, React Native 0.88, TypeScript estricto, Expo Router |
| Almacenamiento móvil | SecureStore para sesión y dispositivo; SQLite para el listado cacheado |
| Integraciones | Adaptador OpenRouteService; Firebase Admin opcional; OTP real pendiente |
| Automatización | GitHub Actions con pruebas Java, ArchUnit, TypeScript y Jest |

El flujo habitual es pantalla → cliente HTTP → controlador → puerto de entrada → caso de uso → puerto de salida → persistencia o proveedor. La separación de dominio y frameworks está comprobada por ArchUnit.

Hay buenas decisiones ya materializadas: contraseñas Argon2id, refresh tokens almacenados como hash, rotación condicional en base de datos, secreto JWT obligatorio, cuenta administrativa sembrada una sola vez, consultas SQL parametrizadas, índice espacial GiST y restricción de un emprendimiento activo por vendedor. La creación de ubicación y emprendimiento se realiza bajo una transacción.

El outbox escribe los eventos dentro de la transacción y dispone de reclamación de lotes, reintentos y estado `DEAD`. Las notificaciones se deduplican por evento y destinatario. Estos mecanismos son una base útil para los módulos que aún faltan.

## 4. Estado funcional real

| Área | Backend | Frontend | Pendiente principal |
|---|---|---|---|
| Registro e inicio de sesión, RF-07/RF-10 | Casos de uso, endpoints, JWT y sesiones; recorrido HTTP y registros simultáneos validados con Docker | Pantallas y almacenamiento de sesión | Validación desde un teléfono |
| Contraseña, RF-09 | Cambio y recuperación con rotación/revocación | Pantallas; tokens de cambio guardados y renovación corregida | Validación desde un teléfono; revisar sesión devuelta por recuperación |
| Verificación, RF-70 | Solicitud, confirmación, mensajes, límites y un adaptador Redis de desarrollo conectado | Confirmación y reenvío | Proveedor real y experiencia de fallback SMS |
| Datos y eliminación de cuenta, RF-08/RF-11 | Infraestructura parcial de entidades; sin endpoints de estos recorridos | Sin pantallas | Implementar sus reglas y operaciones |
| Creación de emprendimiento, RF-28 | Implementada, con unicidad y ubicación | Formulario y resultado | Prueba con usuarios y servicios reales |
| Listado de emprendimientos, RF-31 | Filtros, distancia, radio, horario y paginación | Listado, permiso de ubicación y caché | Gestión de sesión durante uso prolongado y contexto de caché |
| Detalle, edición y eliminación de emprendimiento | Sin endpoints de estas acciones | Sin recorridos completos | RF-29/RF-30/RF-32 y oferta asociada |
| Notificaciones, RF-38/RF-39 | Feed, lectura, preferencias, retención y adaptador FCM | Sin pantalla ni registro de token FCM | Completar recorrido desde el teléfono |
| Productos e inventario | Publicación, edición parcial con versión, borrado lógico, listados e inventario manual; V7/V8 | Mis productos, creación/edición, confirmación de eliminación, catálogo e inventario | Caché y cola sin conexión; integrar pendientes y descuento con pedidos reales; prueba manual de edición/eliminación |
| Pedidos, reservas y pagos | Sin módulos funcionales | Sin pantallas | Ciclo de compra y confirmación de cobros |
| Domicilios y entregas | Solo infraestructura reutilizable de rutas | Sin recorridos | Asignación, transiciones, confirmación y seguimiento |
| Reputación, reportes y administración | Sin módulos funcionales | Sin pantallas | Implementación posterior al ciclo comercial |

El backend de notificaciones anticipa eventos de módulos aún inexistentes. No hay evidencia de un recorrido completo que origine esos eventos desde una compra real. La tabla de tokens de dispositivo y su repositorio existen, pero falta un caso de uso y un endpoint para que la app registre el token.

## 5. Hallazgos prioritarios

### H1. Corregido: pérdida del refresh token al cambiar contraseña

**Estado actual:** el frontend lee los tokens devueltos por el cambio de contraseña y persiste la nueva sesión. Una prueba verifica cambio → vencimiento → renovación con el refresh token nuevo; otra comprueba que un cambio rechazado conserve la sesión original. La recuperación de contraseña mantiene su flujo anterior y sigue siendo un pendiente separado.

Descripción del hallazgo original:

Evidencia: `frontend/src/lib/auth.ts:205` y `backend/src/main/java/co/edu/napa/cuentas/application/usecase/CambiarContrasenaUseCase.java:70`.

El servidor reemplaza el refresh token actual y devuelve un nuevo `AuthTokens`. La app invoca `requestVoid` y descarta el cuerpo de la respuesta. SecureStore conserva el refresh anterior.

Consecuencia: el cambio aparenta funcionar, pero cuando vence el access token la renovación usa un token ya reemplazado y falla. Es un desajuste confirmado por los contratos del código; las pruebas actuales solo comprueban campos enviados.

Acción: leer `AuthTokens`, guardarlos mediante `saveSession` y verificar el recorrido cambio → vencimiento → renovación. La recuperación también devuelve tokens que la app descarta; allí la pantalla envía al usuario a login, por lo que se crea una sesión del servidor que la app no utiliza.

### H2. Corregido: configuración y adaptador OTP de desarrollo

**Estado actual:** el perfil `otp-console` selecciona una sola implementación de `ConsolaVerificacionAdapter`, basada en Redis. El código se toma de `NAPA_OTP_CONSOLE_CODE`, mediante `napa.verification.console-code`, sin valor fijo predeterminado y con validación de seis dígitos. Se eliminó el adaptador duplicado. Las seis pruebas de contexto cargan la configuración principal y verifican la activación del perfil, el código personalizado, el rechazo de códigos ausentes o inválidos y el bloqueo en `prod`. Dos pruebas HTTP adicionales validan registro → verificación → login → cambio de contraseña → renovación, el rechazo del refresh anterior y de otras sesiones, el consumo único del código y el bloqueo tras cinco intentos incorrectos. El proveedor real sigue deshabilitado por defecto y no se envían mensajes externos.

Descripción del hallazgo original:

Evidencia: `README.md:79`, `backend/src/main/resources/application-otp-console.yml:1`, `backend/src/main/resources/application.yml:49` y `backend/src/main/java/co/edu/napa/cuentas/infrastructure/adapters/out/messaging/ConsolaVerificacionAdapter.java:27`.

El README indica activar el perfil `otp-console` y establecer `NAPA_OTP_CONSOLE_CODE`. Ese perfil solo define `napa.auth.verification.console-code`. El adaptador registrado depende de `napa.verification.provider=console` y lee `napa.verification.console-code`.

La clase alternativa `ConsoleVerificarCelularAdapter` no tiene anotación de componente ni se encontró una declaración `@Bean` que la registre. Por ello, seguir únicamente el README mantiene el proveedor `unavailable`. La solicitud de verificación seguirá fallando; el registro puede conservar la cuenta y devolver el estado de envío `FALLIDO`.

Acción identificada originalmente: conservar una única configuración y un único adaptador de desarrollo, y verificar la selección de beans y el arranque documentado con una prueba de contexto. Las variables alternativas que usaba la implementación original se sustituyeron por el perfil y la variable documentados en el README.

### H3. Corregido: expiración de sesión durante el uso de una pantalla

**Estado actual:** las operaciones de emprendimientos, cambio de contraseña y cierre de sesión obtienen las credenciales vigentes mediante `withValidSession`. Renuevan antes de operar y, ante un 401, reintentan una sola vez. Las renovaciones simultáneas comparten una petición; la creación conserva la misma clave de idempotencia y el mismo cuerpo durante el reintento. Las pruebas verifican el uso prolongado de listado/formulario, concurrencia, revocación, fallos temporales y que una renovación pendiente no restaure una sesión ya eliminada.

Descripción del hallazgo original:

Evidencia: `frontend/src/app/emprendimientos.tsx:80`, `:119`, `:148` y `frontend/src/app/emprendimiento.tsx:56`, `:151`.

Las pantallas obtienen una sesión al montarse y la mantienen en estado. Los cambios de filtro, paginación o envío del formulario reutilizan ese access token. No vuelven a solicitar una sesión válida antes de cada operación. Al recibir 401, borran la sesión y redirigen al login.

Consecuencia: tras permanecer en la pantalla hasta que vence el token, el usuario pierde una sesión que todavía podía renovarse. Esto es especialmente molesto si estaba completando el formulario.

Acción: centralizar las solicitudes autenticadas, renovar antes de enviar o reintentar una vez tras un 401 y coordinar renovaciones concurrentes. Distinguir expiración recuperable de revocación real.

### H4. Corregido: traducción de colisiones de registro

**Estado actual:** `UsuarioPersistenceAdapter` reconoce `uq_usuario_celular` y `uq_usuario_correo_normalizado` y devuelve los mismos códigos específicos que la comprobación previa: `PHONE_ALREADY_REGISTERED` y `EMAIL_ALREADY_REGISTERED`, ambos HTTP 409. Se añadieron pruebas con dos transacciones independientes que compiten por cada dato, verificando que solo quede una fila. Las pruebas HTTP envían registros simultáneos y cubren celular local/internacional y correo sin distinguir mayúsculas. Una prueba adicional comprueba que otros errores de integridad no se confundan con cuentas ya registradas.

Las dos pruebas de colisión en PostgreSQL reprodujeron el error antes de aplicar la corrección.

Descripción del hallazgo original:

Evidencia: `backend/src/main/java/co/edu/napa/cuentas/infrastructure/adapters/out/persistence/UsuarioPersistenceAdapter.java:27` y `backend/src/main/resources/db/migration/V3__identity.sql:17`.

El adaptador compara nombres de restricciones con `PHONE_ALREADY_REGISTERED` y `EMAIL_ALREADY_REGISTERED`. La migración define `uq_usuario_celular` y `uq_usuario_correo_normalizado`.

Dos registros simultáneos pueden superar la comprobación previa de existencia. La base evita el duplicado, pero el adaptador no reconoce la restricción y deja escapar la excepción; el manejador genérico devuelve 500.

Acción identificada originalmente: mapear los nombres reales a los códigos de conflicto del contrato y probar registros concurrentes con el mismo celular o correo. No se modificaron las migraciones ni las restricciones de unicidad.

### H5. Media: Redis es un punto obligatorio para la continuidad de sesión

Evidencia: `backend/src/main/java/co/edu/napa/cuentas/infrastructure/adapters/out/cache/RedisSessionVersionAdapter.java:33` y `specs/foundations/plan.md`, sección JWT.

El diseño documenta un respaldo en base de datos cuando Redis no responde. El adaptador implementado solo consulta Redis: si falla, devuelve un error de servicio; si desaparece la clave, la sesión deja de coincidir aunque siga activa en PostgreSQL.

Acción: definir explícitamente la disponibilidad esperada. Si se conserva el respaldo diseñado, comprobar sesión, revocación y expiración en base de datos y reconstruir la caché con cuidado. Mantener el rechazo seguro cuando no pueda validarse ninguna fuente.

Además, los cambios de sesión escriben en PostgreSQL y Redis, que no comparten una transacción. Conviene probar fallos entre ambas escrituras y carreras entre renovación, cierre y cambio de contraseña.

### H6. Media: la idempotencia conserva errores transitorios durante 24 horas

Evidencia: `backend/src/main/java/co/edu/napa/shared/infrastructure/idempotency/IdempotencyFilter.java:55` y `:282`.

El filtro guarda la respuesta sin distinguir su estado HTTP. Un 503 o un 429 resuelto por el manejador de excepciones puede quedar registrado como `DONE`. Reintentar la misma operación con la misma clave devuelve ese error aunque el servicio ya se haya recuperado.

Acción: definir qué respuestas son definitivas y cómo se trata un resultado incierto. Permitir reintentos de fallos transitorios cuya ausencia de efectos esté garantizada y mantener restricciones en base de datos para operaciones críticas.

El filtro también lee el cuerpo completo en memoria. Antes de exponerlo a tráfico externo conviene establecer un límite explícito para los cuerpos JSON.

### H7. Media: las reglas de arquitectura comprueban menos de lo que promete el diseño

Evidencia: `backend/src/test/java/co/edu/napa/ArchitectureTest.java` y `backend/src/main/java/co/edu/napa/notificaciones/infrastructure/web/NotificationController.java:35`.

ArchUnit comprueba las dependencias entre capas, pero no impone de forma general que los módulos se comuniquen únicamente por puertos de entrada. El controlador de notificaciones usa directamente `NotificationUseCase`, y la aplicación de emprendimientos importa `RolUsuario` del dominio de cuentas.

Acción: decidir si estos acoplamientos forman parte de la arquitectura aceptada. Si se mantiene el diseño actual, agregar reglas generales de dependencia entre módulos y puertos para feed/preferencias. Evitar crear abstracciones sin una frontera útil.

También coexisten `AuthUseCase`, los casos de uso individuales, dos tipos `AuthTokens` y dos familias de límites OTP. Los controladores actuales usan los casos de uso individuales. Esta duplicación aumenta el riesgo de probar componentes que no participan en el recorrido real. La duplicación de adaptadores de consola se eliminó al corregir H2.

### H8. Media: la caché de cercanía pierde el contexto de búsqueda

Evidencia: `frontend/src/lib/emprendimientos-cache.ts:72`.

La clave incluye tipo, orden, radio y página, pero no distingue el origen de la búsqueda ni el tamaño de página. Dos consultas de cercanía desde lugares diferentes pueden sobrescribirse. Al quedar sin red, la app puede presentar resultados de la ubicación anterior.

Eliminar `distanciaMetros` antes de persistir protege frente a distancias obsoletas, pero no corrige la composición del listado. El indicador `abierto` también se conserva aunque pase el tiempo.

Acción: invalidar o delimitar el contexto de la caché sin persistir innecesariamente la ubicación precisa del cliente. Mostrar claramente la antigüedad del resultado y recalcular el estado de apertura a partir del horario si se presenta como actual.

### H9. Alta antes de publicar: dependencias con avisos de seguridad pendientes de evaluar

Se ejecutó `npm.cmd audit --omit=dev --json`: informó 27 paquetes afectados, distribuidos en 3 de severidad alta, 23 moderada y 1 baja; ninguna crítica. Los paquetes marcados con severidad alta son `@expo/cli`, `node-forge` y `zod`.

Esos valores describen el árbol resuelto por npm y sus avisos, no 27 vulnerabilidades explotables comprobadas en la app. El árbol incluye herramientas y dependencias transitivas; hace falta determinar cuáles llegan al ejecutable o al entorno de compilación y si reciben entradas no confiables.

Acción: evaluar las rutas afectadas y actualizar dentro de una combinación compatible del SDK. Las correcciones automáticas propuestas incluyen cambios mayores y retrocesos de versión; requieren una revisión de compatibilidad. No se aplicaron cambios de versiones.

### H10. Media: hay planes asignados al requerimiento equivocado

`specs/RF-27-consulta-de-informacion-del-domiciliario/RF-27-plan.md` contiene un plan de eliminación de cuenta, RF-11. `specs/RF-44-configuracion-de-la-tarifa-minima-de-domicilio/RF-44-plan.md` contiene un plan de confirmación del pago de tarifa, RF-43.

No se trata solo de un enlace equivocado: el título, los contratos, las tareas y los módulos descritos pertenecen a otras funcionalidades. Usarlos para implementar RF-27 o RF-44 conduciría al comportamiento incorrecto.

Acción: restaurar planes correspondientes a sus specs y revisar su cobertura antes de construir esas funcionalidades.

## 6. Documentación y trazabilidad

La documentación contiene actores, reglas, escenarios, contratos, eventos, restricciones y orden de dependencias. Esa inversión facilita implementar reglas complejas sin inventarlas durante el desarrollo.

Sin embargo, hay deriva entre documentos y código:

- Se encontraron **135 enlaces locales rotos**, excluyendo plantillas y bloques de código: 66 en trazabilidad, 67 en el informe capstone y dos en los planes equivocados. La causa predominante es apuntar a `spec.md` cuando los archivos tienen nombres `RF-NN-...md`.
- El README afirma que no existen pantallas funcionales de otros módulos, aunque ya existen creación y listado de emprendimientos.
- F042 afirma que el frontend no tiene autenticación, navegación ni cliente HTTP; los tres están implementados.
- El plan RF-31 afirma que no existe `requerimientos/trazabilidad.md`, pero el archivo sí está presente.
- Hay planes dobles en RF-11, RF-38 y RF-39, con nombres `plan.md` y `RF-NN-plan.md`, lo que dificulta identificar la versión vigente.
- Hay nombres de carpetas que no coinciden con los enlaces, por ejemplo `supervicion`, `interativo` y la variante de RF-23.

Acción: elegir un nombre canónico para cada plan, actualizar el estado a partir del código y agregar una comprobación automática de enlaces y de correspondencia entre número RF, título y spec. La matriz de trazabilidad debería incorporar evidencia de implementación y validación, además de enlaces a especificaciones.

## 7. Verificaciones ejecutadas

| Comprobación | Resultado de esta revisión |
|---|---|
| Backend: `gradlew.bat test` | Con edición/eliminación: BUILD SUCCESSFUL; 209 pruebas en 50 suites, sin fallos ni omisiones; duración de 2 min 31 s |
| Frontend: `npm.cmd ci --no-audit --no-fund` | Instalación reproducible completada desde el lockfile |
| Frontend: `npm.cmd run lint` | Correcto |
| Frontend: `npm.cmd run typecheck` | Correcto |
| Frontend: `npm.cmd test` | Con edición/eliminación: 12 suites, 68 pruebas, todas correctas |
| Frontend: `npx.cmd expo export --platform ios --platform web --max-workers 2` | Correcto; genera el bytecode Hermes de iOS y los paquetes web, incluido WebAssembly de SQLite |
| Dependencias: `npm.cmd audit --omit=dev --json` | 27 paquetes afectados según el informe de npm |
| Enlaces Markdown locales | 135 rotos fuera de plantillas y bloques de código |

La primera ejecución del frontend encontró dependencias locales ausentes. Se reinstalaron usando `npm ci`; no se cambió `package.json` ni el lockfile. Una ejecución posterior tuvo un timeout de cinco segundos en una prueba de registro. La repetición completa pasó en 5,7 segundos. Tras las correcciones de sesiones y contrato de respuesta se añadieron 13 pruebas de regresión y pasaron las 35 pruebas totales. Se registra esta incidencia para distinguir un resultado verde final de una ausencia absoluta de inestabilidad.

Durante la prueba manual de la vista web apareció `ExpoSecureStore.default.getValueWithKeyAsync is not a function`: la autenticación llamaba a SecureStore también en navegador. Se separó el almacenamiento por plataforma: SecureStore para iOS/Android y `localStorage` para web, con lectura sin sesión durante el renderizado en servidor y un mensaje claro si el navegador bloquea el almacenamiento. Se añadieron seis pruebas que comprueban lectura inicial, login/logout, identidad del dispositivo, sesión corrupta, ausencia de `window`, bloqueo y fallo de escritura. Pasaron las 41 pruebas, lint y TypeScript. Se corrigió también la URL local del backend para usar la IP Wi-Fi actual y evitar duplicar `/api/v1`.

La comprobación de compilación web detectó además que Metro no reconocía el archivo `.wasm` de SQLite. Se añadió `frontend/metro.config.js` con esa extensión y cabeceras de aislamiento para el servidor de desarrollo. La exportación web posterior terminó correctamente e incluyó el módulo WebAssembly. Para desplegar esa versión web, el servidor de hosting también deberá configurar las cabeceras requeridas por SQLite; la exportación no sustituye una prueba desde el iPhone ni del recorrido web contra la API.

La prueba HTTP de identidad puso de manifiesto otro desajuste: la API devuelve `accessToken`, `refreshToken`, `expiresIn` y `usuario` (con el rol dentro de ese objeto). La app esperaba un rol de primer nivel y `tokenType` en la respuesta. Se corrigieron login, confirmación del registro, renovación y cambio de contraseña para transformar la respuesta real al formato local de sesión. Los fixtures de las pruebas ahora representan ese contrato y una prueba comprueba que la sesión guardada pueda volver a leerse con su rol.

Las nuevas pruebas usan el cliente HTTP real de la app con respuestas simuladas y un almacenamiento SecureStore simulado. No equivalen a una ejecución en un teléfono contra la API. Se consultó la [documentación de SecureStore para Expo SDK 58](https://docs.expo.dev/versions/v58.0.0/sdk/securestore/) antes de implementar los cambios.

La primera ejecución omitió 19 pruebas porque Docker no estaba disponible. Tras activar Docker Desktop, se repitió la tarea con `--rerun-tasks`: las 156 pruebas pasaron, sin omisiones. Después de unificar los adaptadores de consola y añadir pruebas de configuración y HTTP, pasaron 159 pruebas. Con las cinco pruebas nuevas de colisiones de registro pasaron 164 pruebas. Tras añadir nueve pruebas de CORS, el total es de 173 pruebas correctas, incluidas 31 de integración en ocho suites. Testcontainers utilizó contenedores de prueba separados; PostgreSQL y Redis de Compose también estaban activos y saludables. Los informes JUnit están en `backend/build/test-results/test/`.

Al preparar la prueba desde un iPhone, el arranque contra la base persistente `napa` falló por incompatibilidad del historial Flyway: sus versiones 4–6 correspondían a `emprendimiento`, `producto` e `imagen_emprendimiento`, frente a `notifications`, `mensaje` y `emprendimiento` en el código actual. Se conservó esa base y se creó `napa_dev`, seleccionada mediante `DATABASE_URL=jdbc:postgresql://localhost:5432/napa_dev`. Las seis migraciones se aplicaron correctamente y `/actuator/health` respondió HTTP 200 con estado `UP`. No se alteró el historial de la base anterior ni se trasladaron sus cuentas. El procedimiento de arranque quedó documentado en `README.md`. Posteriormente el usuario confirmó que pudo acceder desde el iPhone; falta detallar los resultados del recorrido completo de identidad y emprendimientos.

Al intentar registrarse desde `http://localhost:8081`, la vista web mostró `Failed to fetch` aunque el backend estaba saludable. La solicitud previa `OPTIONS` respondía 200 sin `Access-Control-Allow-Origin`. Se integró CORS en la cadena de Spring Security con orígenes explícitos, métodos y cabeceras del cliente. El perfil `otp-console` admite los dos nombres locales del navegador; otros orígenes requieren `NAPA_WEB_ALLOWED_ORIGINS`. La configuración base y `prod` no heredan esos permisos. Las nueve pruebas añadidas comprueban configuración, solicitudes previas sin autenticación, respuestas 401 legibles desde web, rechazo de orígenes ajenos y conservación de las reglas nativas. Se reinició la instancia local manteniendo `napa_dev` y el secreto JWT; la comprobación HTTP real devolvió estado `UP` y autorizó la solicitud previa desde `http://localhost:8081` con las cabeceras de registro. Después de la corrección, el usuario confirmó que las pruebas funcionaron y que el emprendimiento aparece en el listado. Esta evidencia es una confirmación manual del usuario; no se registró una comprobación independiente de cada paso en ambas plataformas.

La entrega de productos añade 21 pruebas al backend, incluidas siete HTTP con PostgreSQL y Redis reales y dos reglas ArchUnit del módulo; el total es 194, incluidas 38 de integración en nueve suites. Se comprueban publicación, unidades, decimales, validación sin redondeos, agotamiento, reposición, aislamiento entre vendedores, roles, nombres duplicados concurrentes, paginación, productos eliminados e idempotencia. Las 15 pruebas nuevas del frontend cubren contratos, conservación de claves al renovar sesión, formulario, navegación, inventario, estado vacío y refresco al volver al primer plano. Las 56 pruebas del frontend, lint y TypeScript pasaron. La primera exportación iOS encontró un permiso de ejecución del sandbox sobre `hermesc.exe`; el reintento autorizado en el entorno real compiló iOS y web correctamente. La migración V7 se aplicó en `napa_dev`, el backend reiniciado respondió `UP` y el recurso de productos devolvió 401 sin sesión con la cabecera CORS esperada. El 11 de noviembre de 2026 el usuario confirmó que realizó la prueba manual de productos e inventario y todo funcionó correctamente. La confirmación no detalla resultados separados por plataforma ni sustituye las pruebas de fallos de red pendientes.

La ampliación de edición/eliminación añade 15 pruebas backend: diez HTTP de gestión, dos de dominio y tres de configuración de la consulta de pendientes. El total verificado es 209 en 50 suites, incluidas 48 de integración en diez suites. Se cubren cambios parciales y atómicos, precisión del precio, concurrencia con inventario/otra edición/eliminación, CORS con `If-Match`, confirmación explícita, permisos, borrado lógico y reenvío idempotente. Las doce nuevas pruebas frontend cubren formulario, conservación del borrador ante conflicto, recarga explícita, cancelación/confirmación, fallos al eliminar y corrección de la última página. Pasaron las 68 pruebas, lint, TypeScript y exportaciones iOS/web.

RF-58 tiene una integración pendiente: no existen tablas ni módulo de pedidos. `PedidosPendientesProductoPort` se prueba con conteos simulados y el proveedor de cero pendientes solo se habilita mientras `napa.pedidos.enabled=false`. Al activar `NAPA_PEDIDOS_ENABLED=true` sin adaptador real el contexto no arranca. Quedan por comprobar los estados reales de pedidos, el historial RF-51 y la carrera con creación de un pedido. V8 añade una versión mediante migración nueva; JDBC compara esa versión y la app envía `If-Match`. Ante conflicto conserva el borrador y permite recargar. La prueba manual de edición/eliminación sigue pendiente.

Se verificó V8 aplicada con éxito en `napa_dev`, salud `UP`, consulta protegida sin sesión con `401` y CORS, y preflight de PATCH con `If-Match` autorizado desde `localhost:8081`. Durante la comprobación cambió la red local: la IP Wi-Fi actual es `172.20.10.3` y la anterior `192.168.1.41` no responde. Se actualizó únicamente la URL en `.env.local` (ignorado por Git); Expo debe reiniciarse para cargarla. La API respondió `UP` también desde la nueva dirección de red.

Estas pruebas validan las integraciones que cubren sus escenarios; no sustituyen un recorrido completo desde la app móvil. La configuración `disabledWithoutDocker=true` sigue permitiendo omisiones si Docker no está disponible en futuras ejecuciones.

El CI incluye `gradlew test`, por lo que ejecuta ArchUnit dentro de esa tarea. El job frontend incluye TypeScript y Jest, pero no lint. No se inspeccionó una ejecución remota de ese pipeline.

## 8. Brechas de preparación para un piloto

- **Recorrido comercial:** la oferta y el inventario manual ya existen; faltan configuración de pagos, pedido, respuesta del vendedor, descuento de stock y cierre de una venta.
- **Conectividad:** existe caché de lectura del listado; no existe la cola SQLite de operaciones pendientes ni su sincronización prevista para acciones críticas. RF-28 documenta que crear emprendimiento requiere conexión.
- **Notificaciones:** falta registrar tokens desde la app, mostrar el feed y completar la configuración de una build móvil con push real.
- **Pruebas del sistema:** el recorrido HTTP de registro, verificación, login, cambio de contraseña y renovación/revocación ya pasó con PostgreSQL y Redis. El usuario confirmó las pruebas manuales y el emprendimiento visible en el listado. Quedan por comprobar los fallos de red entre componentes reales y registrar los resultados por plataforma.
- **Rendimiento y accesibilidad:** hay controles con etiquetas accesibles y paginación; no se observaron mediciones de p95, validación con lector de pantalla o pruebas en gama baja.
- **Operación:** Compose prepara PostgreSQL y Redis para desarrollo. No hay configuración de despliegue completa de API/app, procedimiento probado de restauración ni evidencias de alertas operativas.
- **Seguridad de dependencias:** falta evaluar los avisos npm en su contexto y revisar el árbol Java.

El relay reclama hasta 100 eventos con una concesión de 30 segundos y los procesa secuencialmente. Al incorporar notificaciones masivas, conviene medir si el lote puede superar ese plazo y requerir renovación de la concesión. Es un riesgo de carga pendiente de validar, no un fallo reproducido en esta revisión.

## 9. Orden de trabajo sugerido

1. Conservar el recorrido básico confirmado por el usuario y completar las comprobaciones de fallos de red y riesgos restantes antes del piloto. H1–H4 ya están corregidos con pruebas de regresión.
2. Mantener las diez suites de integración ejecutándose con Docker en CI y registrar los resultados del recorrido de identidad, emprendimientos y productos por plataforma.
3. Evaluar los avisos de dependencias y corregir los planes RF-27/RF-44 y los enlaces de trazabilidad.
4. Probar manualmente edición y eliminación del catálogo y continuar una venta por recogida: configuración de pago → pedido → aceptación y descuento de stock → listo para recoger → entrega y cobro confirmado. Al implementar pedidos, conectar la consulta de pendientes antes de habilitarlos. Esto permite validar valor de negocio con menos dependencias que el domicilio completo.
5. Incorporar notificaciones móviles y la cola de operaciones pendientes antes de habilitar acciones críticas bajo conectividad irregular.
6. Ampliar a entregas directas y domicilios, incluyendo concurrencia, privacidad de ubicación y trazabilidad.
7. Completar administración, reputación y reportes según las prioridades del piloto.

Como criterio para el siguiente hito, un vendedor debería poder publicar oferta y completar una venta con un cliente, conservar su sesión al cambiar contraseña o esperar más de 15 minutos y recuperar de forma clara las operaciones afectadas por un corte de red. Las métricas de rendimiento y usabilidad deben medirse sobre ese recorrido.

## 10. Cambios realizados durante el análisis

Durante el análisis inicial se generó este informe y se reinstalaron las dependencias locales ignoradas de `frontend/node_modules`; no se modificó entonces el código de la aplicación. `backend/bin/` ya estaba sin seguimiento y se conservó. Posteriormente, a petición del usuario, se añadió `/backend/bin/` al `.gitignore`; el informe permanece disponible para versionarlo. Tras activar Docker, se actualizaron los resultados de las pruebas de integración.

Con autorización para comenzar las correcciones se modificó el frontend para guardar tokens tras cambiar contraseña, centralizar la renovación de sesión y leer la respuesta real de autenticación. Se actualizaron sus consumidores y se añadieron 13 pruebas de regresión.

Se unificó el adaptador OTP de desarrollo en Redis, se conectó mediante el perfil documentado y se añadieron pruebas de configuración y del recorrido HTTP de identidad. Se actualizaron la documentación y la configuración de desarrollo.

Se corrigió la traducción de las restricciones únicas de celular y correo para responder con HTTP 409 y el código específico cuando dos registros compiten. Se añadieron cinco pruebas con PostgreSQL y HTTP, incluidas las variantes de formato del celular y mayúsculas del correo. Las dos pruebas de colisión en persistencia fallaron antes del cambio y pasaron después; la suite completa terminó con 164 pruebas correctas. No se modificaron las migraciones ni las dependencias. Los cambios siguen pendientes de commit y no se han subido a GitHub.

Posteriormente se implementó la primera entrega en línea de productos e inventario (RF-54/RF-55, inventario manual de RF-57 y consulta de RF-33). Se añadió V7 sin reescribir V1–V6, se expuso la consulta del emprendimiento por su puerto de entrada y se añadieron cuatro endpoints, tres pantallas y navegación desde la cuenta del vendedor y las tarjetas de emprendimientos. El precio se expresa en pesos enteros y la cantidad admite dos decimales; el servidor deriva la propiedad del usuario autenticado, resuelve la unicidad en PostgreSQL y bloquea la fila para las actualizaciones manuales. Esta entrega no implementa pedidos, reservas, descuento de stock por pedidos ni escrituras o caché sin conexión para productos. Se actualizaron README y los cuatro planes implicados para distinguir el avance de los pendientes. No se cambiaron las dependencias ni sus versiones.

Se incorporaron después RF-56 en línea y la parte de RF-58 independiente de pedidos: tres endpoints de consulta/edición/eliminación, formulario de edición, confirmación en Mis productos, versión mediante V8 y el puerto de consulta de pendientes con activación explícita del módulo futuro. No se reescribieron migraciones previas ni se eliminaron datos del usuario para probar; las eliminaciones automatizadas se ejecutaron en contenedores de prueba aislados. Se actualizaron los planes implicados y se reinició el backend con la misma base `napa_dev` y secreto JWT.

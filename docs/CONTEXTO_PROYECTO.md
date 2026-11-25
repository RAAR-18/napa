# Mapa de conocimiento — Ñapa

Estado vigente: leer primero `CONTINUIDAD.md` y `CORRECCIONES_AUDITORIA.md`. El mapa inferior conserva la revisión histórica del 13 de noviembre; no usar sus pendientes como estado actual.

Revisión inicial: 13 de noviembre de 2026. Fuente: rama `main` del repositorio https://github.com/RAAR-18/napa, commit `704244f`. Es una inspección de documentación, estructura, configuración, rutas HTTP y archivos de implementación y pruebas; no una certificación de funcionamiento. No se ejecutaron pruebas ni servicios en esta revisión.

## Propósito y dominio

Proyecto para la asignatura Experiencia final. Ñapa conecta vendedores informales y pequeños comerciantes con clientes cercanos: oferta de productos, pedidos anticipados y coordinación de entregas sin abandonar el punto de venta.

| Actor | Responsabilidad y restricción principal |
|---|---|
| CLIENTE | Explorar emprendimientos, comprar y consultar sus pedidos |
| VENDEDOR_AMBULANTE | Administrar su oferta; modalidad de entrega directa |
| VENDEDOR_PUNTO_FIJO | Administrar su oferta; recogida y domicilio |
| DOMICILIARIO | Aceptar tarifa publicada o rechazar; recoger y entregar |
| ADMINISTRADOR | Supervisión y configuración de tarifa mínima |

Reserva es un atributo con fecha/hora, no una cuarta modalidad. La plataforma registra confirmaciones de pago; no procesa dinero. Domicilio exige transferencia digital; el vendedor recibe el total y después paga la tarifa al domiciliario. La tarifa no se negocia. Consultar las specs antes de modificar estos comportamientos.

## Fuentes y orden de lectura

| Necesidad | Ruta |
|---|---|
| Procedimiento con el usuario | `AGENTS.md` |
| Último punto de trabajo | `docs/CONTINUIDAD.md` |
| Inicio y configuración local | `README.md`, `docker-compose.yml` |
| Problema, entrevistas y evidencia académica | `investigacion/` |
| Historias de usuario | `investigacion/HistoriasDeUsuario/historias_de_usuario.md` |
| Funcionales, no funcionales y relaciones | `requerimientos/funcionales.md`, `no-funcionales.md`, `trazabilidad.md` |
| Casos de uso | `casos de uso/` |
| Entregable del curso | `capstone/informe-capstone.md` |
| Procedimiento de diseño | `specs/README.md` |
| Roles, API, estados y modelo compartido | `specs/general/spec-general.md` |
| Eventos de dominio | `specs/general/eventos.md` |
| Arquitectura transversal y decisiones | `specs/foundations/plan.md` |
| Comportamiento y diseño de una funcionalidad | `specs/RF-NN-nombre/` |
| Análisis histórico | `ANALISIS_PROYECTO.md`; contrastar con código actual |

Flujo del repositorio: spec → plan y checklist → implementación → validación → actualización de trazabilidad. Los planes reales suelen llamarse `RF-NN-plan.md`; algunas carpetas contienen además `plan.md`. No asumir un nombre único ni borrar duplicados sin revisar su contenido. RF-20, RF-21 y RF-46 están retirados; RF-59 (predicción de demanda) está diferido según el índice de specs.

## Arquitectura y recorrido de una operación

```text
Pantalla: frontend/src/app/
    → lógica de acceso: frontend/src/lib/
    → HTTP: frontend/src/lib/api.ts + sesión/auth
    → controlador backend: infrastructure/.../web/
    → caso de uso: application/usecase/
    → dominio y puertos
    → adaptadores: persistencia / Redis / proveedores
    → PostgreSQL y outbox transaccional
    → listeners y notificaciones
```

Backend: `backend/src/main/java/co/edu/napa/`. Módulos presentes: `cuentas`, `emprendimientos`, `productos`, `pedidos`, `pagos`, `domicilios`, `tarifas`, `notificaciones`, `shared`. El diseño es hexagonal por módulo, con `domain`, `application` e `infrastructure`. La ruta web varía: no todos los módulos usan exactamente la misma profundidad.

Frontend: `frontend/src/app/` contiene las rutas Expo Router; `components/` los componentes compartidos; `lib/` clientes API, sesión, almacenamiento y cachés; `hooks/` lógica reutilizable. Revisar `frontend/AGENTS.md` antes de proponer cambios allí.

## Mapa para localizar un cambio sin revisar todo

Las rutas backend de la tabla son relativas a `backend/src/main/java/co/edu/napa/`; las del frontend a `frontend/src/`.

| Tema / RF de referencia | Backend | Frontend: archivos principales |
|---|---|---|
| Registro, sesión, contraseña, verificación: RF-07/09/10/70 | `cuentas/` | `lib/auth.ts`, `auth-storage.ts`, `api.ts`; `app/register.tsx`, `verify.tsx`, `login.tsx`, `recover.tsx`, `change-password.tsx`, `session.tsx` |
| Emprendimientos: RF-28 a RF-34 | `emprendimientos/` | `lib/emprendimientos.ts`, `emprendimientos-cache.ts`; `app/mi-emprendimiento.tsx`, `emprendimientos.tsx`, `emprendimiento-detalle.tsx` |
| Productos e inventario: RF-33/54 a RF-58 | `productos/` | `lib/productos.ts`; `app/mis-productos.tsx`, `nuevo-producto.tsx`, `editar-producto.tsx`, `catalogo.tsx` |
| Cotización, compra y respuesta: RF-45/47/48/50/51/53 | `pedidos/` | `lib/pedidos.ts`, `components/PedidoCatalogo.tsx`; `app/mis-pedidos.tsx`, `pedido-cliente.tsx`, `pedidos-vendedor.tsx`, `pedido-vendedor.tsx` |
| Entrega directa y recogida: RF-35/36/37/64/65 | `pedidos/`, `EntregaPedidoController` | `lib/pedidos.ts`; pantallas de pedido y `app/ubicacion-entrega.tsx` |
| Configuración y consulta de pagos: RF-40/41/42/63 | `pagos/`, confirmación en `pedidos/` | `lib/pagos.ts`; `app/configuracion-pagos.tsx`, `cuenta-domiciliario.tsx`, `historial-pagos.tsx` |
| Domicilios: RF-12 a RF-19 y RF-22 a RF-27 | `domicilios/` | `lib/domicilios.ts`; `app/domicilios-vendedor.tsx`, `domicilios-disponibles.tsx`, `domicilios-en-curso.tsx`, `domicilio-domiciliario.tsx`, `domicilios-admin.tsx`, `domicilio-admin.tsx` |
| Rutas y seguimiento: RF-22/26 | `domicilios/`, infraestructura de rutas | `lib/mapas.ts`, `ruta-cache.ts`; `app/ruta-domicilio.tsx`, `trazabilidad-domicilio.tsx`; componentes `MapaRutaDomicilio*` |
| Pago de tarifa: RF-43 | `domicilios/`, `PagoTarifaController` | `components/PagoTarifaDomicilio.tsx`, `lib/domicilios.ts` |
| Tarifa mínima: RF-44 | `tarifas/` | Localizar consumidor concreto antes de proponer una interfaz |
| Notificaciones: RF-38/39 y dispositivos | `notificaciones/`, `cuentas/.../DispositivoController` | `lib/notificaciones.ts`, `push.ts`; `app/notificaciones.tsx`, `preferencias-notificaciones.tsx` |
| Infraestructura transversal | `shared/` | `lib/api.ts`, autenticación y almacenamiento |

Ejemplo de solicitud acotada: para cambiar la aceptación de pedidos, pedir `backend/src/main/java/co/edu/napa/pedidos/application/usecase/ResponderPedidoUseCase.java` y, si cambia el contrato HTTP, `backend/src/main/java/co/edu/napa/pedidos/infrastructure/adapters/in/web/PedidoController.java`. Pedir el cliente o pantalla únicamente si también cambia su comportamiento.

## Estado observado y límites

- Hay controladores, casos de uso, pantallas y pruebas para identidad, emprendimientos, productos, pedidos, pagos y domicilios. La presencia de archivos y endpoints demuestra implementación presente, no cumplimiento total del RF.
- En pedidos existen cotización, creación, consultas de cliente/vendedor, aceptación/rechazo, confirmación de pago y acciones de entrega/recogida.
- En domicilios existen publicación, consultas por actor, aceptación/rechazo, recogida/salida/entrega, código, trazabilidad, ruta, cancelación administrativa y pago de tarifa.
- Hay registro/baja de dispositivos y pantallas de notificaciones. La entrega real de push depende de la configuración y del proveedor.
- Existen cachés de emprendimientos y rutas. No deducir que toda la app funciona offline por tener SQLite instalado o por existir un outbox del servidor. Verificar cada operación por separado.
- No se identificaron módulos dedicados de reputación/calificaciones o reportes en la estructura backend revisada. Su alcance sigue documentado en specs y requiere revisión puntual antes de planificarlo.
- README y ANALISIS_PROYECTO contienen pendientes que el código ya supera: entregas/domicilios, confirmación de pagos y registro de dispositivos. No usar sus conteos ni resultados históricos de pruebas como estado actual.
- No se definió con el usuario cuál será el siguiente RF. No convertir el orden histórico del análisis en una prioridad nueva.

## Stack y decisiones que orientan cambios

Versiones observadas, no recomendaciones de actualización: Java 21, Spring Boot 4.1.1 y Gradle; PostgreSQL 16/PostGIS, Redis 7; Expo ~58.0.17, React 19.3.0, React Native 0.88.0, TypeScript y Expo Router. Fuentes: `backend/build.gradle`, `frontend/package.json` y Compose.

- API `/api/v1`, fechas ISO-8601 UTC, dinero entero en COP. Transiciones mediante POST de acción; conservar el contrato de idempotencia definido en las specs.
- Sesión con JWT corto y refresh rotativo, controles de versión y Redis. Reutilizar el mecanismo existente de renovación; no guardar tokens por caminos paralelos.
- Eventos durables mediante outbox en la misma transacción del cambio. No confundir ese outbox con la cola offline del teléfono.
- Flyway: migraciones presentes V1–V16 en `backend/src/main/resources/db/migration/`. V11 pedidos, V12 pagos, V13 domicilios, V15 cancelación administrativa y V16 referencia de notificación. Añadir migraciones nuevas, sin reescribir las aplicadas.
- Configuración bancaria cifrada con AES-GCM; no versionar claves. OTP de consola es de desarrollo; proveedor externo y credenciales deben comprobarse antes de prometer envíos reales.
- Mapas y push nativo requieren revisar el build de desarrollo y la configuración específica de plataforma. Hay variantes `.web.tsx` para mapas.

## Validación que debe pedir el asistente

El usuario ejecuta los comandos. Backend, desde `backend/`: `.\gradlew.bat test`; las integraciones usan Testcontainers y requieren Docker. Reglas de arquitectura: `backend/src/test/java/co/edu/napa/ArchitectureTest.java`.

Frontend, desde `frontend/`: `npm test`, `npm run typecheck`, `npm run lint`, más un recorrido manual del actor afectado. Pruebas próximas a `lib/` y en `app/__tests__/`. La CI en `.github/workflows/ci.yml` ejecuta backend test y frontend typecheck/test; no incluye lint actualmente.

No iniciar ni recrear la base para preparar una respuesta. Consultar README y configuración vigente antes de dar comandos de arranque; no asumir que una base histórica del usuario coincide con la definida en Compose.

## Mantenimiento del contexto

Actualizar este mapa cuando cambien arquitectura, rutas clave o alcance confirmado. Guardar el estado breve y cambiante en `CONTINUIDAD.md`. Mantener decisiones funcionales en specs y decisiones técnicas en planes, enlazándolas desde aquí en vez de duplicarlas. Leer primero este índice y abrir únicamente las piezas del trabajo actual.

## Actualización de ubicación y alcance (2026-11-24)

- Repositorio real: `C:\Users\ESTUDIANTE\Documents\GitHub\napa`. La copia de `Documents\ChatGPT\napa` se usó para el análisis inicial y no debe modificarse para esta etapa.
- Base actual inspeccionada puntualmente: `35cc46d`, rama `fix/p0-01-outbox-web`. El cuerpo anterior conserva el mapa histórico del 13 de noviembre: no es un inventario actualizado de todos los módulos.
- Se trabaja en las incidencias del informe técnico aportado por el usuario. Leer `docs/CONTINUIDAD.md` y `docs/CORRECCIONES_AUDITORIA.md` para conocer el avance actual.
- El usuario autorizó edición directa y pruebas. Los commits y push continúan siendo manuales.


## Rutas añadidas en el bloque posterior a a561f42

- Categoría Otros personalizada: modelo `backend/src/main/java/co/edu/napa/emprendimientos/domain/model/Emprendimiento.java`, command/DTOs del mismo módulo, JPA y listado JDBC. Campo JSON `categoriaPersonalizada`, nullable, solo OTROS, hasta 80 caracteres.
- Migración V21: `backend/src/main/resources/db/migration/V21__emprendimiento_categoria_personalizada.sql`. Se mantienen V1–V20; las pruebas usan PostgreSQL aislado.
- Formulario y presentación: `frontend/src/app/emprendimiento.tsx`, `mi-emprendimiento.tsx`, `emprendimientos.tsx`, `emprendimiento-detalle.tsx`; tipos/API en `frontend/src/lib/emprendimientos.ts`.
- Actualización voluntaria de zona: `frontend/src/lib/ubicacion-automatica.ts`, iniciada desde `_layout.tsx`, control en Mi emprendimiento. Expo Location existente, cada 2 min en primer plano, mínimo 100 m; preferencia por cuenta/negocio. No es seguimiento en segundo plano.


## Corrección de verificación posterior a b8a8cc4

El adaptador `cuentas/infrastructure/adapters/out/messaging/SmsCorreoVerificacionAdapter.java` usa scripts Redis para guardar código/generación/TTL, consumir el desafío correcto, contabilizar intentos y reservar/liberar cupos. Pruebas específicas de unidad e integración Redis junto al adaptador. El README describe los proveedores SMSReactor/Brevo actuales; el proveedor predeterminado sigue siendo unavailable y la configuración real del equipo es independiente de la corrección. No se enviaron mensajes externos.


## Cachés de listado y rutas posteriores a 817acbe

`frontend/src/lib/cache-lectura-storage.ts` centraliza cache_lectura nativa y su variante `.web.ts` usa IndexedDB. Los consumidores son `emprendimientos-cache.ts` y `ruta-cache.ts`. Las seis cachés de perfil/negocio/inventario/catálogo/notificaciones/pedidos ya tienen variantes `.web.ts` mediante `cache-cuenta.web.ts`; consultar el seguimiento P0-01 para validación y límites. No hay migración de cachés antiguas del navegador.


## Actualización de listados posterior a 9800874

Mis pedidos y Domicilios en curso usan useAutoRefresh y recarga al enfocar. Domicilios disponibles conserva 15 s mediante un ciclo ligado al foco y AppState con exclusión de solicitudes superpuestas. Plan RF-38 y seguimiento P1-02 contienen los límites: sondeo visible, sin SSE ni entrega garantizada con app cerrada.


## Privacidad de rutas guardadas (2026-11-25)

`lib/ruta-cache.ts` exige cuenta explícita en guardar/leer/borrar; las claves incluyen cuenta y domicilio y no se recuperan claves históricas sin propietario. `app/ruta-domicilio.tsx` verifica rol/cuenta/generación antes del GPS y de usar resultados o copias. `domicilio-domiciliario.tsx` pasa la cuenta del detalle al borrar tras entregar. Plan RF-22 y seguimiento documentan regeneración de caché en línea y validación; no hay mapas nuevos.


## Acuerdo de testing en vivo (2026-11-25, posterior a c26bf01)

- Ambulante: `app/emprendimiento.tsx` publica posición GPS sin campos fijos; `lib/ubicacion-automatica.ts` actualiza en primer plano cada 15 s/25 m con permiso y pausa. No guarda historia de ruta diaria.
- Carrito: `components/PedidoCatalogo.tsx` sin límite comercial de 20, con dirección del perfil como respaldo no destructivo. Backend alinea HTTP/cotización/dominio; V22 nueva retira CHECK <=20.
- Anticipos: `pedidos/.../ConfirmarPagoDomicilioUseCase.java` admite reserva aceptada por transferencia y `pagos/.../ConfirmarPagoPedidoUseCase.java` reutiliza pago confirmado al entregar sin duplicarlo. No procesa dinero.
- Avisos: `components/AvisosNovedades.tsx` en _layout, sondeo 15 s activo y listener push, Toast informativo; no marca leída ni mezcla cuentas. Sin nuevas dependencias ni configuración Firebase para el aviso interno.
- Las specs/planes RF28/38/42/45 y requerimientos registran que este acuerdo reemplaza conservar 20 y cobrar reservas solo al entregar. Ver seguimiento y continuidad para validación.


## Seguimiento nativo voluntario en segundo plano (2026-11-25)

- `lib/ubicacion-segundo-plano.ts` define la tarea global y el servicio nativo visible; `.web.ts` mantiene solo foreground. Se importa desde _layout antes de montar React.
- `ubicacion-preferencia.ts` almacena consentimiento y paradas; `ubicacion-publicacion.ts` valida/publica sin React; `ubicacion-automatica.ts` mantiene controles/foreground. Mi emprendimiento permite activar background y pausar.
- Auth revoca consentimiento y detiene tareas al cambiar/cerrar sesión. SecureStore adapta sesión/preferencia al primer desbloqueo y serializa escrituras.
- Configuración en app.json + expo-task-manager SDK58; requiere nuevo build. No garantiza forzar cierre ni guarda historial diario. Plan/spec RF28 y foundations describen permisos y comprobaciones.

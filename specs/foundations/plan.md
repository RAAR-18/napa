# Implementation Plan: Fundaciones transversales de Ñapa

**Date**: 2026-10-05
**Spec**: no aplica (infraestructura compartida). Spec general: [spec-general](../general/spec-general.md) · [eventos](../general/eventos.md)
**Requerimientos que habilita**: los 66 RF del MVP
**Requerimientos NFR cubiertos**: RNF01–RNF05, RNF07, RNF08, RNF13, RNF15–RNF16, RNF18–RNF19, RNF21–RNF23, RNF25, RNF27–RNF31
**Depende de**: ninguno
**Habilita a**: todos los planes por RF

> Define **CÓMO** se construye la base. No cambia el **QUÉ**. Donde la base obliga a ajustar una spec, se lista en "Spec Change Requests" y se corrige la spec primero.

## Summary

Monorepo con backend Spring Boot hexagonal y modular por dominio, eventos de dominio con outbox transaccional, autenticación por contraseña con verificación del celular mediante Twilio Verify (WhatsApp con respaldo SMS), JWT con versión de sesión, idempotencia (Redis + únicos en BD), PostGIS para geografía y una app Expo con cola de salida offline sobre SQLite.

## Decisiones (ADR resumido)

| # | Decisión | Motivo |
|---|---|---|
| D1 | **Autenticación**: contraseña como credencial; código de un solo uso (Twilio Verify) solo para verificar el celular y recuperar la contraseña (RF-70). El administrador entra solo con contraseña (cuenta sembrada). | Limita el costo de mensajes (un código por alta, cambio de celular o recuperación, no por inicio de sesión), conserva el límite de intentos de RF-10 FR-004 y no depende de WhatsApp para entrar a vender. |
| D2 | Spring Boot **4.1.x**, Java 21 LTS | 3.2 está sin soporte. Verificar parche y compatibilidad de Hibernate Spatial al iniciar. |
| D3 | Se elimina "Cash-Only". Medios: efectivo o transferencia; domicilio solo digital | RNF29, RF-42, RF-63. |
| D4 | Push con **token FCM nativo** (`getDevicePushTokenAsync`) + Firebase Admin, y **development build** de Expo | Expo Go no sirve para push remoto ni react-native-maps con claves propias. |
| D5 | Offline: **expo-sqlite + outbox + `Idempotency-Key`**, sin WatermelonDB | La app no necesita sincronización bidireccional de grafos; el servidor es la autoridad. Menos dependencias nativas en gama baja (RNF31). |
| D6 | Idempotencia en dos capas: Redis (respuesta cacheada por clave, 24 h) y **restricciones únicas en BD** como garantía final | Si Redis cae, la BD sigue evitando duplicados (RNF27). |
| D7 | **Outbox transaccional**: el evento se escribe en la misma transacción; un relay lo despacha. `@TransactionalEventListener(AFTER_COMMIT)` solo para efectos locales no críticos (invalidar caché) | Evita perder notificaciones si el proceso cae tras el commit. |
| D8 | JWT de acceso corto + refresh rotativo + claim `sv` (versión de sesión) validado contra Redis | Permite cerrar sesiones (RF-09 FR-003), eliminar cuentas (RF-11, RF-68) y cambiar rol sin esperar expiración. |
| D9 | Rutas y distancias por servicio externo tras `RoutingPort`; resultados cacheados en Redis | RNF19. Tarifa = distancia A–B (RF-45 FR-006) con piso RF-44. |
| D10 | PostGIS con `geography(Point,4326)`; cercanía con `ST_DWithin`/`ST_Distance`; índice GiST | RNF22. |
| D11 | Límites entre módulos verificados con **ArchUnit** (dominio sin Spring/JPA; módulos solo se hablan por puertos) | RNF13, RNF25. |
| D12 | Verificación del celular con **Twilio Verify** (WhatsApp con respaldo SMS) detrás de `VerificarCelularPort` | El equipo ya lo conoce y delega a Twilio la generación del código, su vigencia, los intentos y las plantillas aprobadas. Costo: 0,05 USD por verificación exitosa más la tarifa del canal (a confirmar para Colombia). Adaptador de consola para desarrollo y pruebas, sin gastar unidades. Cambiar de proveedor es cambiar un adaptador. |

## Technical Context

Ver `specs/templates/plan-template.md` (sección Technical Context ya completada; es la fuente de verdad del stack). Metas ajustadas respecto al borrador original:

- "Autenticación OTP < 1,5 s" → **solicitar la verificación responde ≤ 1,5 s p95**; la entrega del código depende de Twilio y del canal.
- "Despacho de eventos < 100 ms" → **commit → handler ≤ 1 s p95** (relay por sondeo cada 500 ms con `FOR UPDATE SKIP LOCKED`). Cumple de sobra los SC de notificación (< 1 min).
- Verificación JWT ≤ 10 ms p95 incluye un `GET` a Redis (versión de sesión).

## Arquitectura

### Estructura (hexagonal por módulo + eventos)

Se combinan las dos propuestas: **un paquete por módulo** (los 12 de `funcionales.md`) para que ArchUnit pueda vigilar los límites (RNF13, RNF25), y **dentro de cada módulo** las capas `domain` / `application` / `infrastructure` con la nomenclatura del equipo (`ports/in`, `ports/out`, `usecase`, `adapters/in/web`, `adapters/in/event`, `adapters/out/*`). La estructura completa está en `specs/templates/plan-template.md` (Project Structure), con raíz `co.edu.napa`, `shared/` como núcleo compartido y `frontend/` para la app.

**UseCase, no Service.** En la capa de aplicación solo existe `usecase`: el puerto de entrada es una interfaz `XxxPort` (`ports/in`) y su única implementación es `XxxUseCase` (`application/usecase`). Un caso de uso es una acción del usuario: valida permisos y precondiciones, carga agregados, invoca al dominio, guarda y emite eventos. Las reglas de negocio puras (p. ej. el cálculo de la tarifa con su piso mínimo) viven en las entidades y value objects de `domain/model`; no existe `domain/service`. Si una regla futura involucra dos agregados y no cabe en ninguno, se evalúa crear ese paquete en ese momento.

**Eventos + hexagonal.**
1. El caso de uso emite el evento por `EventPublisherPort`; no sabe quién escucha.
2. Un *listener* es un adaptador de entrada (`adapters/in/event`) que traduce el evento a una llamada a un puerto `in`. No tiene lógica.
3. El listener se planifica en el módulo que reacciona, nunca en el caso de uso emisor (p. ej. el aviso de nuevo pedido va en el plan de notificaciones, no en "realizar pedido").
4. Reglas ArchUnit: `domain` no importa Spring/JPA/Jackson; un módulo solo usa de otro sus `domain/event` y sus `ports/in`; controladores y listeners solo llaman puertos `in`.

### Núcleo compartido (`shared`)

`domain/`: `DomainEvent`, `AggregateRoot`, `DomainException`, value objects comunes. `infrastructure/`: outbox y relay, `IdempotencyFilter`, `SessionVersionFilter`, `ApiError` (códigos de `spec-general`), utilidades geográficas, `Clock` inyectable.

### Eventos de dominio y outbox

- Tabla `outbox_event` (definida en `spec-general`). Catálogo de eventos en `specs/general/eventos.md`.
- El caso de uso publica el evento dentro de su transacción; el relay lo lee por lotes, lo entrega a los manejadores registrados y marca `processed_at`.
- Manejadores **idempotentes** (entrega al menos una vez): clave natural `event_id` en `notificacion`.
- `domicilio_historial` (trazabilidad, RF-26) NO se alimenta por eventos: el caso de uso inserta la fila en la misma transacción que cambia el estado del domicilio.
- Reintentos con retroceso exponencial y límite; tras el límite, estado `DEAD` y alerta.

### Notificaciones (RF-38, RF-39)

- Entidades `notificacion`, `preferencia_notificacion` y `device_token` (en `spec-general`). La notificación siempre se guarda.
- Un listener por evento en `notificaciones/infrastructure/adapters/in/event`, que llama al puerto `EnviarNotificacionPort`. Este consulta las preferencias (RF-39), guarda la notificación y envía FCM con `sendEachForMulticast` en lotes de 500; tokens inválidos se borran.
- Si FCM falla, la notificación sigue visible en RF-38 (degradación controlada, RNF19/RNF15).
- Pendiente de decidir: notificaciones críticas que no se puedan silenciar (ver Open Questions).

### Seguridad y autenticación

| Flujo | Diseño |
|---|---|
| Registro (RF-07) | `POST /auth/register` crea cuenta `PENDIENTE_VERIFICACION` con celular único, contraseña (Argon2id/BCrypt) y rol; pide la verificación del celular (RF-70). `POST /auth/verify-phone` activa la cuenta y entrega tokens (cumple FR-004 "iniciar sesión de inmediato"). |
| Verificación del celular (RF-70) | **Twilio Verify** detrás del puerto `VerificarCelularPort` (`iniciar(celular, canal)` y `confirmar(celular, codigo)`). Twilio genera y entrega el código por WhatsApp con respaldo SMS, controla su vigencia y el número de intentos; Ñapa nunca ve el código, así que no lo guarda ni lo hashea. Ñapa mantiene sus **propios límites** en Redis (`verif-rl:{celular}` y por IP: 1/min, 5/hora) para controlar el costo y el abuso. Cada solicitud se registra en la entidad `mensaje` (sin código). Adaptadores: `TwilioVerifyAdapter` (producción) y `ConsolaVerificacionAdapter` (desarrollo y pruebas; acepta un código fijo y no llama a Twilio). La verificación no usa el outbox: es una llamada directa al puerto y, si se pierde, el usuario pulsa "reenviar". |
| Login (RF-10) | `POST /auth/login` con celular o correo + contraseña; bloqueo temporal tras N fallos (contador en Redis); mensaje genérico sin indicar qué dato falló. Cuenta sin verificar → `ACCOUNT_NOT_VERIFIED` e invita a reenviar OTP. |
| Cambio de contraseña (RF-09) | Con contraseña actual, o recuperación con OTP. Incrementa `sv`, revoca refresh tokens salvo el actual y devuelve tokens nuevos. |
| Admin | Contraseña únicamente, cuenta sembrada por migración o variable de entorno; rate-limit estricto; evaluar TOTP después del MVP. |
| JWT | Acceso 15 min con `sub`, `role`, `sv`; refresh opaco rotativo guardado hasheado por dispositivo. El filtro compara `sv` con `sv:{userId}` en Redis (fallback a BD si Redis no responde). |
| Autorización (RNF03) | `@PreAuthorize` por rol + comprobación de pertenencia en el caso de uso (nunca solo en el controlador). |
| Eliminación (RF-11, RF-68) | Estado `ELIMINADA`, incrementa `sv`, revoca refresh. |

### Idempotencia (RNF27)

- Todas las operaciones `POST/PATCH` que cambian estado o dinero exigen `Idempotency-Key` (UUID generado en el dispositivo al tocar el botón).
- Redis `idem:{userId}:{key}` guarda hash de la petición + estado (`IN_PROGRESS`/`DONE`) + respuesta; `SET NX` para el bloqueo. Misma clave y mismo cuerpo → misma respuesta; mismo clave y distinto cuerpo → 422.
- Garantía final en BD: único por pago de pedido, único por pago de tarifa, único por calificación (autor + entidad + interacción), un domicilio activo por pedido, etc.

### Concurrencia (RNF08)

- Inventario: `UPDATE producto SET cantidad = cantidad - :n WHERE id = :id AND cantidad >= :n` (cero filas = sin stock).
- Aceptar domicilio: `UPDATE domicilio SET estado='ASIGNADO', domiciliario_id=:d WHERE id=:id AND estado='DISPONIBLE'`; una fila = ganó.
- Transiciones de pedido/domicilio con `@Version` y `SELECT ... FOR UPDATE` donde se lean varias tablas.

### Geografía (RNF22, RNF04)

- `geography(Point,4326)` en punto fijo, zona del ambulante, ubicación de entrega, puntos A y B.
- La **ubicación en vivo del domiciliario** no se guarda en PostGIS: la app la envía cada 15 s y se mantiene en Redis (`loc:{domicilioId}`, TTL 2 min). El cliente la consulta cada 15–30 s (RNF30). Se elimina al finalizar (RNF04).
- La ubicación del cliente solo se devuelve al vendedor o domiciliario del pedido activo (RF-35 FR-003).

### Frontend (RNF28, RNF31)

- `frontend/src/database/outbox`: tabla SQLite `outbox(id, method, path, body, idempotency_key, status, attempts, created_at)`. Cada acción crítica se escribe aquí y se envía con reintento y retroceso; la UI muestra "pendiente de envío".
- Caché de lectura en SQLite para pedidos, lista de domicilios y emprendimiento; el servidor es la autoridad y resuelve los conflictos con `INVALID_STATE`/`ALREADY_CONFIRMED`.
- Mensajes de error claros ante rechazo tras reconectar (RNF24).
- Evitar librerías pesadas; medir en un dispositivo de gama baja desde la primera pantalla.

## Fases de construcción de la base

**Phase F0 – Repositorio y CI**
- [ ] F001 Monorepo `backend/` y `frontend/`; Gradle/Maven con Java 21 y Boot 4.1.x en `backend/build.gradle`
- [ ] F002 Docker Compose (PostGIS 16, Redis 7) en `docker-compose.yml`
- [ ] F003 Pipeline CI: pruebas backend, Jest, ArchUnit
- [ ] F004 Flyway y migración base con extensión PostGIS en `backend/src/main/resources/db/migration/V1__base.sql`

**Phase F1 – Núcleo compartido**
- [ ] F010 `ApiError` y manejador global en `backend/.../shared/infrastructure/web/`
- [ ] F011 `DomainEvent` + outbox + relay + manejadores idempotentes en `shared/infrastructure/outbox/`
- [ ] F012 `IdempotencyFilter` con Redis y pruebas de concurrencia en `shared/infrastructure/idempotency/`
- [ ] F013 Utilidades geo y `RoutingPort` con adaptador del proveedor elegido en `shared/infrastructure/geo/`
- [ ] F014 Reglas ArchUnit en `backend/src/test/.../ArchitectureTest.java`
- [ ] F015 Pruebas de integración con Testcontainers (PostGIS, Redis) como base de test

**Phase F2 – Identidad (RF-07, RF-09, RF-10, RF-11)**
- [ ] F020 Entidades `usuario`, `refresh_token`, `device_token` y migración
- [ ] F021 `SolicitarVerificacionUseCase` y `ConfirmarVerificacionUseCase` sobre `VerificarCelularPort`, con límites propios en Redis
- [ ] F022 Crear el servicio de Twilio Verify, habilitar WhatsApp con respaldo SMS, confirmar vigencia, intentos y tarifa para Colombia, y fijar un tope mensual de gasto con alerta. Activar la cuenta de prueba solo al llegar a esta tarea (dura 30 días y solo envía a números verificados)
- [ ] F023 Endpoints register / verify-phone / login / refresh / logout / change-password / recover
- [ ] F024 Filtro JWT con `sv` y cuenta admin sembrada
- [ ] F025 Pruebas de seguridad: fuerza bruta, OTP agotado, token revocado

**Phase F3 – Notificaciones (RF-38, RF-39)**
- [ ] F030 Tablas `notificacion`, `preferencia_notificacion`, `device_token`
- [ ] F031 Adaptador FCM (Firebase Admin) con limpieza de tokens inválidos
- [ ] F032 `EnviarNotificacionPort` + listeners por evento del catálogo (E01–E17) en `notificaciones/infrastructure/adapters/in/event`
- [ ] F033 Entidad `mensaje`, `TwilioVerifyAdapter` y `ConsolaVerificacionAdapter` (RF-70); actualizar el estado del mensaje si Twilio ofrece notificaciones de estado

**Phase F4 – Frontend base**
- [ ] F040 Proyecto Expo con development build, navegación y cliente Axios en `frontend/src/services/http/`
- [ ] F041 Outbox SQLite + reintentos + `Idempotency-Key` en `frontend/src/database/outbox/`
- [ ] F042 Registro del token FCM nativo y pantalla de notificaciones
- [ ] F043 Pruebas Jest de outbox (sin red, reintento, duplicado)

**Checkpoint**: se puede registrar un usuario, verificar su celular, iniciar sesión, recibir una notificación push y reenviar una acción creada sin conexión sin duplicarla.

## Orden de planes por RF (P1 primero, respetando dependencias)

| Ola | Planes | Nota |
|---|---|---|
| 0 | Esta base | |
| 1 | RF-07, RF-70, RF-10, RF-09 (+ RF-38 y RF-39) | RF-38 es P2 pero lo necesitan casi todos los flujos P1 |
| 2 | RF-28, RF-54, RF-55, RF-57, RF-63, RF-31, RF-32 | Oferta y configuración de pago |
| 3 | RF-44 (mínimo), RF-45, RF-53, RF-50, RF-51, RF-48 | Pedido completo |
| 4 | RF-42 y RF-35, RF-36, RF-37 (ambulante); RF-64, RF-65 (recogida) | Cobro y cierre de modalidades simples |
| 5 | **RF-14**, RF-16, RF-18, RF-19, RF-22, RF-23, RF-24, RF-25, RF-26 | Ciclo del domicilio |
| 6 | RF-60, RF-01, RF-02, RF-06 | Reportes y reputación |
| 7 | Resto de P2 y P3 | |

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Proveedor de rutas (Google, Mapbox, OSRM propio) | Costo y cuotas | Puerto + adaptador; decidir en F013 | Equipo |
| Habilitar el canal de WhatsApp en Twilio Verify para Colombia (remitente y plantilla) | Plazo de F022 | SMS como respaldo | Equipo |
| Costo mensual de Twilio Verify (0,05 USD por verificación exitosa + canal) y tope de gasto | Presupuesto del piloto | Con unos cientos de verificaciones al mes, decenas de dólares; fijar tope y alerta | Equipo |
| ¿Verify cancela la verificación anterior al pedir otra para el mismo celular (RF-70 FR-005)? | Seguridad | Si no, cancelarla explícitamente antes de crear la nueva | Equipo |
| ¿Notificaciones críticas no silenciables (estado de domicilio)? | RF-39 | Sí para domicilios en curso | Equipo |
| Vencimientos (pedido sin respuesta, pago sin hacer, llegada sin confirmar) | Varias specs | Sin vencimiento automático en MVP | Equipo y usuarios |

## Plan Review Checklist

- [ ] Spec Change Requests aplicados a las specs
- [ ] Proveedor de rutas elegido
- [ ] ArchUnit y Testcontainers corren en CI
- [ ] Checkpoint de la base verificado en un móvil de gama baja
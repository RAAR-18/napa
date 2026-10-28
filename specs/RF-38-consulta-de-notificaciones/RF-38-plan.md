# Implementation Plan: Consulta de notificaciones

**Date**: 2026-10-20
**Spec**: [spec.md](./RF-38-consulta-de-notificaciones.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [specs/general/spec-general.md](../general/spec-general.md) · [eventos](../general/eventos.md) · cambios pendientes en `cambios/spec-general-cambios.md`
**Requerimiento funcional**: RF-38
**Historias de usuario**: HU-63
**Prioridad (la mayor de la spec)**: P1
**Depende de**: foundations F011 (outbox y relay), F030–F031 (tablas y FCM), F042 (token FCM en la app), RF-10
**Habilita a**: RF-39 (preferencias) y el aviso a las partes en todos los flujos de pedidos, domicilios, pagos y reportes

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".
> Este plan es dueño de **todos los listeners** de notificaciones (eventos.md, regla 4); `foundations/plan.md` F032 pasa a remitir aquí.

## Summary

Consulta paginada de notificaciones con estado de lectura, y listeners en `notificaciones/infrastructure/adapters/in/event` que traducen cada evento del catálogo (E01–E18) en una `Notificacion` y un envío FCM, de forma idempotente por `eventId`.

## Estado actual del código

`DispositivoToken` ya tiene entidad y adaptador de persistencia en `cuentas`. El plan crea el módulo `notificaciones` completo. Decisiones cerradas en foundations: sin excepciones a las preferencias de notificación (ni siquiera las de estado de domicilio); sin vencimientos automáticos.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Recibe y consulta notificaciones |
| Vendedor (ambulante / punto fijo) | Igual |
| Domiciliario | Igual |
| Administrador | Consulta sus notificaciones (no hay eventos dirigidos al administrador en el catálogo actual) |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Notificación visible en < 1 min desde el evento (SC-001); evento → handler ≤ 1 s p95.
- FCM `sendEachForMulticast` en lotes de 500; tokens inválidos se borran (foundations).
- El payload del evento lleva solo identificadores; el texto de la notificación es genérico, sin datos personales (eventos.md, regla 7).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Consultar notificaciones | `GET /notificaciones`, `ConsultarNotificacionesUseCase` | T010, T013, T014 |
| FR-002 | Ordenadas por fecha, leídas vs. no leídas | `sort=created_at,desc` por defecto; filtro `leida`; `POST /notificaciones/{id}/leer` (204, spec-general) | T011, T015, T016 |
| FR-003 | Generar notificaciones ante eventos relevantes | Listeners por evento + `EnviarNotificacionPort` | T020–T029, T037 |
| FR-004 | Notificar como mínimo la lista de eventos | Tabla "Listeners" (abajo): E01–E07 (E07 solo si el pedido no es de domicilio), E08–E12, E14–E18 | T020–T031 |
| SC-001 | Visibles en < 1 min | Relay cada 500 ms + prueba de latencia | T040 |
| SC-002 | 100 % de eventos relevantes generan notificación | Un test por evento del catálogo | T032, T038 |
| US1-E1 | Notificaciones pendientes ordenadas, con no leídas | `GET /notificaciones` | T010 |
| US1-E2 | Sin notificaciones | `200` con `content: []` y mensaje de lista vacía en la app | T012 |
| Edge 1 | Múltiples notificaciones del mismo evento | Idempotencia por `eventId` (único) | T021 |
| Edge 2 | Retención del historial | Decisión pendiente | — |
| Edge 3 | Usuario elimina su cuenta con notificaciones pendientes | Decisión pendiente (no se envían push a cuentas `ELIMINADA`) | T033 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Cada usuario solo consulta las suyas |
| RNF04 Privacidad | ☑ | Texto genérico sin datos personales; payload solo con IDs |
| RNF07 Rendimiento | ☑ | Listado ≤ 3 s con índice `(usuario, created_at)` |
| RNF15 Fiabilidad | ☑ | Si FCM falla, la notificación sigue visible en la lista |
| RNF16 Trazabilidad | ☑ | `created_at`, `leida_at` |
| RNF18 Notificaciones | ☑ | Entrega oportuna vía outbox + FCM |
| RNF19 Interoperabilidad | ☑ | Firebase Admin detrás de un puerto |
| RNF23 Consistencia | ☑ | Estados de lectura solo `leida_at` nulo → fecha |
| RNF28 Conectividad | ☑ | Lista consultable al recuperar señal; la app guarda caché de lectura |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Notificacion | Escribe / lee | Sin `event_id` en el modelo; la idempotencia es detalle de implementación |
| PreferenciaNotificacion | Lee | Sin filas, todo habilitado (la gestión es RF-39) |
| DispositivoToken | Lee | Único `(usuario, token)` |
| outbox_event | Lee (relay) | `processed_at` |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento |
|---|---|---|---|---|---|
| Notificacion | no leída (`leida_at` nulo) | leída | Su destinatario | Es la propietaria | Ninguno |

**Transiciones inválidas**: marcar como leída una ya leída es idempotente (204).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Listar notificaciones | Todos los roles | Solo las propias |
| Marcar como leída | Todos los roles | La notificación pertenece al usuario |
| Registrar token de dispositivo | Todos los roles | Token ligado al usuario autenticado |

## API Contracts

### GET /api/v1/notificaciones

- **Rol**: todos los autenticados.
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| page, size, sort | int, int, string | ☐ | `0`, `20`, `created_at,desc` | Paginación y orden (por defecto fecha descendente) |
| leida | boolean | ☐ | `false` | Filtra por estado de lectura |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas**: `200` `{ "content": [{ "id", "tipo", "titulo", "cuerpo", "leida", "createdAt", "referencia": { "tipo", "id" } }], "page", "size", "totalElements" }`; `X-Request-Id`.
- **Errores**: `400 VALIDATION_ERROR` (filtro desconocido), `401 UNAUTHORIZED`, `403 FORBIDDEN`.

### POST /api/v1/notificaciones/{id}/leer

- **Rol**: todos · **Path params**: `id`. **Cabeceras**: `Authorization`; `Idempotency-Key` no se exige (marcar leída es idempotente).
- **Respuestas**: `204`. **Errores**: `401 UNAUTHORIZED`, `404 NOT_FOUND` (inexistente o ajena).

### POST /api/v1/dispositivos

- **Rol**: todos · **Cabeceras**: `Authorization`, `X-Device-Id`. **Body**: `{ "token": "<FCM>", "plataforma": "ANDROID" | "IOS" }`.
- **Respuestas**: `204` (único por `(usuario, token)`, repetir no duplica). **Errores**: `400 VALIDATION_ERROR`, `401 UNAUTHORIZED`.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Este módulo no emite eventos |

**Eventos que consume esta feature** (listeners en `notificaciones/infrastructure/adapters/in/event`, sin lógica: llaman a `EnviarNotificacionPort`):

| Evento | Destinatario(s) | Tipo de notificación |
|---|---|---|
| E01 `PedidoCreado` | Vendedor (`vendedorId`) | `NUEVO_PEDIDO` |
| E02 `PedidoAceptado` | Comprador (en domicilio, aviso de transferir el total) | `PEDIDO_ACEPTADO` |
| E03 `PedidoRechazado` | Comprador | `PEDIDO_RECHAZADO` |
| E04 `PagoPedidoConfirmado` | Comprador | `PAGO_PEDIDO_CONFIRMADO` |
| E05 `EntregaDirectaIniciada` | Comprador | `ENTREGA_DIRECTA_INICIADA` |
| E06 `PedidoListoParaRecoger` | Comprador | `PEDIDO_LISTO_PARA_RECOGER` |
| E07 `PedidoEntregado` | Comprador, **solo si `domicilioId` es nulo** (entrega directa o retiro): aviso de entrega con invitación a calificar (RF-01). En domicilio no hace nada porque E12 ya avisa al comprador | `PEDIDO_ENTREGADO` |
| E08 `DomicilioPublicado` | Todos los domiciliarios con cuenta `ACTIVA` (decidido) | `DOMICILIO_DISPONIBLE` |
| E09 `DomicilioAsignado` | Comprador y vendedor | `DOMICILIO_ASIGNADO` |
| E10 `DomicilioRecogido` | Vendedor | `DOMICILIO_RECOGIDO` |
| E11 `DomicilioEnCamino` | Comprador | `DOMICILIO_EN_CAMINO` |
| E12 `DomicilioEntregado` | Vendedor (pagar la tarifa) y comprador (aviso de entrega) | `DOMICILIO_ENTREGADO_VENDEDOR` / `DOMICILIO_ENTREGADO_CLIENTE`; el texto del comprador incluye la invitación a calificar (RF-01) |
| E14 `PagoTarifaRegistrado` | Domiciliario | `PAGO_TARIFA_REGISTRADO` |
| E15 `DomicilioCancelado` | Comprador, vendedor y domiciliario si existe | `DOMICILIO_CANCELADO` |
| E16 `ReporteResuelto` | Autor del reporte | `REPORTE_RESUELTO` |
| E17 `ComentarioModerado` | Autor del comentario; el texto dice qué se hizo (`accion`) y el `motivo` que trae el evento | `COMENTARIO_MODERADO` |
| E18 `ReporteCreado` | Todos los administradores con cuenta `ACTIVA` | `REPORTE_CREADO` |

E09 no está en la lista mínima de RF-38 FR-004, pero está en el catálogo y la exige RF-19 SC-002 (FR-004 dice "como mínimo").

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la notificación queda guardada aunque el push falle; la app la ve al consultar. Caché de lectura en SQLite en el dispositivo (foundations).
- **Concurrencia (RNF08)**: el relay usa `FOR UPDATE SKIP LOCKED`; el listener es idempotente por `eventId` (restricción única en la tabla de eventos procesados / `notificacion`).
- **Idempotencia (RNF27)**: clave natural `eventId` + destinatario + tipo.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| FCM falla | La notificación queda guardada; se reintenta con retroceso; tras el límite, `DEAD` y alerta | foundations D7 |
| Token FCM inválido | Se borra el `DispositivoToken` | foundations |
| Destinatario con cuenta `ELIMINADA` | No se envía push (decisión pendiente) | Edge 3 |
| Preferencia deshabilitada (RF-39) | No se genera (ni fila ni push); lo implementa RF-39 | RF-39 |
| Filtro desconocido | 400 | spec-general|

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Mapeo evento → destinatarios y tipo | FR-003, FR-004 |
| Integración | Endpoints, orden, filtro `leida`, idempotencia del listener, Testcontainers | US1-E1, E2, Edge 1 |
| E2E | Un evento real (p. ej. `PedidoCreado`) → notificación visible | SC-001, SC-002 |

**Cobertura mínima**: todos los escenarios Given/When/Then y un test por evento del catálogo.

## Project Structure

### Documentation (this feature)

```text
specs/RF-38-consulta-de-notificaciones/
├── RF-38-consulta-de-notificaciones.md   # spec
└── plan.md                               # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/notificaciones/
├── domain/model/                          # Notificacion, TipoNotificacion
├── application/ports/in/                  # ConsultarNotificacionesPort, MarcarLeidaPort, EnviarNotificacionPort
├── application/ports/out/                 # NotificacionRepositoryPort, PreferenciaRepositoryPort, PushPort, DispositivoRepositoryPort
├── application/usecase/                   # ConsultarNotificacionesUseCase, MarcarLeidaUseCase, EnviarNotificacionUseCase
└── infrastructure/adapters/
    ├── in/web/                            # NotificacionController, DispositivoController
    ├── in/event/                          # un listener por evento (Pedido…, Pago…, Domicilio…, Reporte…, Comentario…)
    ├── out/persistence/                   # entidades JPA
    └── out/messaging/                     # FcmPushAdapter (Firebase Admin)
frontend/src/app/(comun)/notificaciones.tsx          # lista paginada con leídas/no leídas (todos los roles)
frontend/src/services/push/                          # registro del token FCM nativo (expo-notifications) y `POST /dispositivos`
frontend/src/services/notificaciones.ts              # listar y marcar como leída
frontend/src/database/schema/                        # caché de lectura de notificaciones (SQLite)
frontend/__tests__/notificaciones/
```

**Structure Decision**: módulo `notificaciones`. Los listeners son adaptadores de entrada sin lógica; los eventos se consumen desde `domain/event` de los demás módulos (ArchUnit).

## Phase 1: Setup

- [ ] T001 Confirmar tablas `Notificacion`, `PreferenciaNotificacion`, `DispositivoToken` (foundations F030) y adaptador FCM (F031)

## Phase 2: Foundational

- [ ] T002 `EnviarNotificacionPort` y `EnviarNotificacionUseCase` (consulta preferencias, guarda y envía push) en `notificaciones/application/usecase/`
- [ ] T003 Tabla o restricción de idempotencia por `eventId` en `backend/src/main/resources/db/migration/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Conocer novedades de mi cuenta (Priority: P1)

**Goal**: listar y marcar notificaciones, y generarlas desde los eventos.
**Independent Test**: generar un evento que produzca una notificación y verla en la sección del usuario correspondiente.
**Cubre**: FR-001–FR-004, SC-001–SC-002, US1-E1–E2, Edge 1.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /notificaciones` (orden, `leida`, paginación) en `backend/src/test/java/co/edu/napa/notificaciones/ListadoNotificacionesIT.java`
- [ ] T011 [P] [US1] Test de marcar como leída (propia y ajena) en `.../notificaciones/MarcarLeidaIT.java`
- [ ] T012 [P] [US1] Test de lista vacía en `.../notificaciones/ListadoVacioIT.java`
- [ ] T021 [P] [US1] Test de idempotencia del listener (mismo `eventId` dos veces) en `.../notificaciones/ListenerIdempotenteIT.java`
- [ ] T032 [P] [US1] Un test por evento del catálogo (SC-002): destinatario y tipo correctos, en `.../notificaciones/EventosNotificanIT.java`
- [ ] T040 [P] [US1] Test de latencia evento → notificación < 1 min en `.../notificaciones/LatenciaIT.java`

### Implementation

- [ ] T013 [US1] `ConsultarNotificacionesUseCase` en `notificaciones/application/usecase/`
- [ ] T014 [US1] `NotificacionController` (`GET /notificaciones`) en `.../in/web/`
- [ ] T015 [US1] Orden por fecha y filtro `leida` con validación de filtros desconocidos
- [ ] T016 [US1] `MarcarLeidaUseCase` y `POST /notificaciones/{id}/leer`
- [ ] T017 [US1] `DispositivoController` (`POST /dispositivos`)
- [ ] T020 [US1] Listener E01 `PedidoCreado` → vendedor en `.../in/event/PedidoCreadoListener.java`
- [ ] T022 [US1] Listeners E02, E03, E04 en `.../in/event/`
- [ ] T023 [US1] Listeners E05, E06 en `.../in/event/`
- [ ] T024 [US1] Listener E08 `DomicilioPublicado` → todos los domiciliarios activos
- [ ] T025 [US1] Listeners E09, E10, E11 en `.../in/event/`
- [ ] T026 [US1] Listener E12 `DomicilioEntregado` → vendedor y comprador
- [ ] T027 [US1] Listeners E14, E15 en `.../in/event/`
- [ ] T028 [US1] Listeners E16, E17 en `.../in/event/`
- [ ] T029 [US1] Listener E07 `PedidoEntregado` → comprador solo si `domicilioId` es nulo, en `.../in/event/`
- [ ] T037 [US1] Listener E18 `ReporteCreado` → todos los administradores activos, en `.../in/event/`
- [ ] T038 [P] [US1] Test de E07 (con y sin `domicilioId`; sin duplicar E12) y de E18 (llega a todos los administradores activos) en `.../notificaciones/NotificacionesE07E18IT.java`
- [ ] T033 [US1] No enviar push a cuentas `ELIMINADA`
- [ ] T035 [P] [US1] Test de la pantalla (orden, no leídas, lista vacía, marcar leída, lista desde caché sin conexión) en `frontend/__tests__/notificaciones/notificaciones.test.tsx`
- [ ] T036 [US1] Servicio de notificaciones y registro del token FCM en `frontend/src/services/` (reutiliza foundations F042)
- [ ] T034 [US1] Pantalla `frontend/src/app/(comun)/notificaciones.tsx`

**Checkpoint**: US1 funcional y probable por separado.

## Phase N: Polish & Cross-Cutting

- [ ] T041 Revisar privacidad: textos sin datos personales (RNF04)
- [ ] T042 Revisar índices y rendimiento del listado (RNF07)
- [ ] T043 Actualizar `requerimientos/trazabilidad.md` y `foundations/plan.md` F032 si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere el outbox (F011), RF-10 y los eventos que cada RF emita; los listeners de E02–E18 se activan a medida que se construyen los RF emisores (ola 3 en adelante).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| E08 va a todos los domiciliarios activos (decidido). Con muchos domiciliarios habrá más push y costo; revisar si crece | Volumen de push | Todos | Equipo |
| Retención del historial de notificaciones | Edge 2 | Ninguna | Equipo |
| Notificaciones críticas no silenciables | RF-39 | **Cerrado (foundations)**: no hay excepciones, se respetan las preferencias | Equipo |
| Notificaciones del administrador: E18 `ReporteCreado` las genera (decidido en la ola 6) | US1 | Todos los administradores activos reciben `REPORTE_CREADO` | Equipo |
| Preferencia deshabilitada: ¿se guarda la notificación sin push o no se genera? | RF-39 | **Cerrado**: no se genera (ni fila ni push); lo implementa RF-39 en `EnviarNotificacionUseCase` | Equipo |
| E07 (corrige un error de este plan, que decía "sin notificación" y contradecía eventos.md, RF-37 y RF-65): el comprador recibe el aviso de entrega con invitación a calificar solo fuera de domicilio; en domicilio lo hace E12 con el mismo texto. Vendedor y domiciliario no reciben invitación | FR-004 | Así | Equipo |

## Plan Review Checklist

*(Todo debe estar marcado antes de implementar.)*

- [ ] La tabla Spec Coverage no tiene filas vacías
- [ ] Todas las tareas de implementación y de testing están presentes
- [ ] La arquitectura es coherente con los módulos existentes (RNF13, RNF25); solo hay `usecase`, sin capa `service` de aplicación
- [ ] Entidades referenciadas a `spec-general.md` (sin redefinir el modelo); si hubo cambio de esquema, ya está en `spec-general.md`
- [ ] Contratos de API con path/query params, headers, códigos HTTP exitosos y de error, y matriz de permisos
- [ ] Eventos emitidos documentados en `eventos.md`; ningún listener planificado en este plan
- [ ] Actores (comprador, vendedor, domiciliario, administrador) identificados
- [ ] Errores y casos borde tienen comportamiento definido
- [ ] Estrategia de conectividad y concurrencia definida cuando aplica (RNF28, RNF08)
- [ ] Dependencias y librerías identificadas
- [ ] Cada tarea es granular y nombra el archivo que toca
- [ ] No se agregó comportamiento que no esté en la spec

## Notes

- `[USn]` en cada tarea enlaza con su historia de usuario.
- Hacer commit por tarea o grupo lógico.
- Evitar tareas vagas y conflictos en un mismo archivo. 
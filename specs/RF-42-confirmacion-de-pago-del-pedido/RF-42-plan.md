# Implementation Plan: Confirmación de pago del pedido

**Date**: 2026-10-21
**Spec**: [RF-42-confirmacion-de-pago-del-pedido.md](./RF-42-confirmacion-de-pago-del-pedido.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md) (E04)
**Requerimiento funcional**: RF-42
**Historias de usuario**: HU-66
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-53 (tabla `pago` y `Pago` en `PENDIENTE_CONFIRMACION`), RF-51 (detalle donde vive el botón y `ConsultarPagoPedidoPort`), RF-10
**Habilita a**: RF-37 y RF-65 (confirman el cobro por el puerto de este plan), RF-14 (publica solo con el pago confirmado), RF-43

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Confirmar el cobro de un pedido. Hay dos caminos y un solo núcleo:

1. **Domicilio**: el vendedor de punto fijo confirma, desde el detalle, que recibió la transferencia por el total (productos + tarifa), con `POST /pedidos/{id}/pago/confirmar`, solo con el pedido `ACEPTADO`. Esa confirmación habilita publicar el domicilio (RF-14).
2. **Entrega directa y recogida**: no hay endpoint propio. RF-37 y RF-65 confirman el cobro dentro de su misma transacción llamando al puerto `ConfirmarPagoPedidoPort` de `pagos`.

El `Pago` ya existe en `PENDIENTE_CONFIRMACION` desde que el vendedor aceptó (RF-53), con el medio que eligió el cliente; aquí solo pasa a `CONFIRMADO` con `confirmado_at` y se emite E04. El núcleo (`Pago.confirmar`) rechaza una segunda confirmación (`409 ALREADY_CONFIRMED`) y la fila se bloquea para que dos peticiones simultáneas no la confirmen dos veces.

## Estado actual del código

No existe `pagos` ni la tabla `pago`: la crean RF-53 (migración V9 y `RegistrarPagoPedidoPendientePort`). Este plan agrega la confirmación sobre ese módulo. El estado del pago se consulta por el puerto de RF-51.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Recibe la notificación (E04); no confirma ni cambia el medio |
| Vendedor (ambulante / punto fijo) | Confirma el cobro (punto fijo en domicilio por este plan; ambulante y punto fijo en entrega y retiro por RF-37 y RF-65) |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Confirmar el pago en < 10 s (SC-001): una llamada sin cuerpo.
- 0 % de pagos confirmados dos veces (SC-003): bloqueo `FOR UPDATE` del `Pago` y estado verificado en dominio; el único por pedido (RF-53) es la garantía final.
- La plataforma **no procesa dinero**: solo registra la confirmación del vendedor. No se guarda ningún dato de tarjetas ni de terceros (FR-006).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El vendedor confirma el pago de un pedido propio, por el medio que eligió el cliente | `Pago.confirmar` no recibe medio: conserva `Pedido.medio_pago` (copiado al crearse); no existe forma de cambiarlo | T010, T016, T020, T023 |
| FR-002 | Pedirlo al entregar o retirar; en domicilio, desde que está aceptado y antes de publicar | Puerto `ConfirmarPagoPedidoPort` usado por RF-37 y RF-65; en domicilio, endpoint con el pedido `ACEPTADO` | T016, T023 (y RF-37, RF-65) |
| FR-003 | Impedir una segunda confirmación | `Pago.confirmar` falla si ya está `CONFIRMADO` → `409 ALREADY_CONFIRMED`; bloqueo `FOR UPDATE` | T012, T015, T020 |
| FR-004 | Impedir confirmar pedidos ajenos | El caso de uso del pedido valida el dueño → `404` | T013 |
| FR-005 | Notificar al cliente | E04 `PagoPedidoConfirmadoEvent` en la misma transacción; el listener está en el plan de RF-38 | T010, T022 |
| FR-006 | Registrar cuenta bancaria; sin tarjetas ni billeteras | Ya cubierto por RF-63; este plan no almacena datos de pago | — (RF-63) |
| FR-007 | Domicilio solo por transferencia, por el total | `Pago` nace con `CUENTA_BANCARIA` y `monto = total` (RF-53); aquí se verifica en la respuesta | T010 |
| FR-008 | Publicar el domicilio solo con el pago confirmado | El estado `CONFIRMADO` es consultable por `ConsultarPagoPedidoPort`; RF-14 lo exige en su plan | T017 |
| SC-001 | < 10 s | Una llamada; botón en el detalle | T040 |
| SC-002 | 100 % de entregados con pago confirmado | Lo garantizan RF-37 y RF-65 con este puerto en la misma transacción | T016 (y RF-37, RF-65) |
| SC-003 | 0 % de segundas confirmaciones | Prueba de concurrencia | T012 |
| SC-004 | 100 % de domicilios con medio `CUENTA_BANCARIA` | Prueba de integración | T010 |
| SC-005 | 0 % de publicaciones sin pago confirmado | Consulta de estado; la regla es de RF-14 | T017 |
| US1-E1 | Cobro en efectivo en entrega directa o recogida | `ConfirmarPagoPedidoPort` con medio `EFECTIVO` | T016 (y RF-37, RF-65) |
| US1-E2 | Cobro por transferencia en entrega directa o recogida | Igual con `CUENTA_BANCARIA` | T016 (y RF-37, RF-65) |
| US1-E3 | Cobro de un domicilio antes de publicar | `POST /pedidos/{id}/pago/confirmar` | T010, T023 |
| US1-E4 | Domicilio pendiente | `409 INVALID_STATE`, mensaje "primero debe aceptarse" | T011 |
| US1-E5 | Confirmar dos veces | `409 ALREADY_CONFIRMED` y sin segundo registro | T012 |
| Edge 1 | Pedido cancelado o rechazado | `409 INVALID_STATE` con `estadoActual` | T014 |
| Edge 2 | Monto distinto al total | No se captura el monto recibido: se confirma el total (Open Questions) | — |
| Edge 3 | Pedido ajeno | `404` | T013 |
| Edge 4 | Domicilio aceptado que nunca se paga | Sin vencimiento (foundations): sigue `ACEPTADO` con pago pendiente | — |
| Edge 5 | Verificar que la transferencia corresponde | La verifica el vendedor en su banco; la app muestra el total y la cuenta registrada y exige marcar "Verifiqué que recibí el pago" | T030, T032 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Rol y pertenencia validados en el caso de uso |
| RNF04 Privacidad | ☑ | No se almacenan datos de tarjetas; E04 solo lleva identificadores, medio y monto |
| RNF08 Concurrencia | ☑ | `Pago` bloqueado `FOR UPDATE` |
| RNF15 Fiabilidad | ☑ | Confirmación y E04 en la misma transacción (outbox) |
| RNF16 Trazabilidad | ☑ | `confirmado_at` y `updated_at` |
| RNF23 Consistencia | ☑ | Estados del pago y del pedido por dominio |
| RNF24 Errores claros | ☑ | `INVALID_STATE` con `estadoActual`, `ALREADY_CONFIRMED` |
| RNF27 Integridad | ☑ | `Idempotency-Key` y único por pedido |
| RNF28 Conectividad | ☑ | Acción por el outbox del dispositivo |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pago | Lee / escribe (`estado`, `confirmado_at`) | Tipo `PEDIDO`; `PENDIENTE_CONFIRMACION` → `CONFIRMADO`; medio ya fijado; nunca `REGISTRADO` |
| Pedido | Lee | Dueño, modalidad y estado `ACEPTADO` en el camino de domicilio; el estado del pedido **no** cambia aquí |
| Emprendimiento | Lee (dueño) | Pertenencia |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Pago (tipo PEDIDO) | `PENDIENTE_CONFIRMACION` | `CONFIRMADO` | Vendedor confirma (domicilio) o RF-37 / RF-65 | Pedido propio; en domicilio, pedido `ACEPTADO` | `confirmado_at`; E04 |

**Transiciones inválidas**: pago ya `CONFIRMADO` → `409 ALREADY_CONFIRMED`; pedido en otro estado → `409 INVALID_STATE` con `details.estadoActual`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /pedidos/{id}/pago/confirmar` | VENDEDOR_PUNTO_FIJO | Pedido de su emprendimiento; si no, `404` |
| `ConfirmarPagoPedidoPort` (interno) | Lo invocan los casos de uso de RF-37 y RF-65 | Ellos validan al vendedor |

## API Contracts

### POST /api/v1/pedidos/{id}/pago/confirmar

- **Rol**: vendedor de punto fijo, solo pedidos de modalidad `DOMICILIO` (en entrega directa y recogida el cobro se confirma con RF-37 y RF-65) · **Path params**: `id` (UUID) · **Query params**: ninguno · **Body**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅, `Content-Type` no aplica.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{ "pedidoId": "…", "pago": { "id": "…", "estado": "CONFIRMADO", "medio": "CUENTA_BANCARIA", "monto": 14500, "confirmadoAt": "2026-10-21T15:00:00Z" } }
```

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` | — |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor de punto fijo | — |
| 404 | `NOT_FOUND` | Pedido inexistente o de otro emprendimiento | "El pedido no está disponible" |
| 409 | `INVALID_STATE` | Pedido `PENDIENTE`, `RECHAZADO`, `CANCELADO`, `ENTREGADO` o distinto de `ACEPTADO` | `details.estadoActual`; en `PENDIENTE`: "Primero debes aceptar el pedido" |
| 409 | `ALREADY_CONFIRMED` | El pago ya estaba confirmado | "El pago ya está confirmado" |
| 422 | `BUSINESS_RULE` | El pedido no es de domicilio | "En este pedido el cobro se confirma al registrar la entrega o el retiro" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

### Puerto `in` de `pagos` (no es endpoint)

`ConfirmarPagoPedidoPort.confirmar(pedidoId, compradorId, modalidad)` pasa el `Pago` `PEDIDO` del pedido a `CONFIRMADO` y devuelve `PagoConfirmado(id, medio, monto, confirmadoAt)`. Lo llaman, ya dentro de su transacción y con el pedido bloqueado: el caso de uso de domicilio de este plan, RF-37 y RF-65. No valida modalidad ni dueño (eso es del llamador): solo el estado del pago. `ALREADY_CONFIRMED` si ya lo estaba; error de integridad si el pago no existe (un pedido aceptado siempre lo tiene, RF-53).

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E04 `PagoPedidoConfirmadoEvent` (`pagos.pago-pedido-confirmado`) | `ConfirmarPagoPedidoUseCase` (`pagos`) | Al confirmar, en la misma transacción. Payload: `pagoId`, `pedidoId`, `compradorId`, `medio`, `monto`, `modalidad` (nuevo; el catálogo debe añadirlo) |

**Eventos que consume esta feature**: ninguno. El aviso al cliente es un listener del plan de RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la acción se escribe en el outbox de SQLite con su `Idempotency-Key` y se muestra "pendiente de envío"; al reconectar, si el servidor responde `ALREADY_CONFIRMED` o `INVALID_STATE`, la app actualiza el detalle y explica el motivo.
- **Concurrencia (RNF08)**: el caso de uso del pedido bloquea el `Pedido` y luego el `Pago` (`FOR UPDATE`), siempre en ese orden para no producir interbloqueos; de dos solicitudes simultáneas una confirma y la otra recibe `ALREADY_CONFIRMED`.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`; misma clave y mismo cuerpo repite la respuesta con `Idempotent-Replayed: true`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Domicilio pendiente | `409 INVALID_STATE` "Primero debes aceptar el pedido" | US1-E4 |
| Doble confirmación | `409 ALREADY_CONFIRMED`, sin nuevo registro | US1-E5 |
| Pedido cancelado o rechazado | `409 INVALID_STATE` | Edge 1 |
| Pedido ajeno | `404` | Edge 3 |
| Pedido que no es de domicilio | `422 BUSINESS_RULE` | FR-002 |
| Sin conexión | Outbox con reintento | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Pago.confirmar` (estados, `confirmado_at`, segunda confirmación) | FR-003 |
| Integración | Endpoint y puerto con Testcontainers: domicilio aceptado, pendiente, doble confirmación, ajeno, estados no válidos, modalidad distinta, E04 en el outbox, pago consultable como `CONFIRMADO` | US1-E1–E5, Edge 1, Edge 3, FR-008 |
| Concurrencia | Dos confirmaciones simultáneas: una gana, una sola fila confirmada, un solo E04 | SC-003 |
| Arquitectura | `pagos` no depende de `pedidos` (ver Structure Decision); `pedidos` usa `pagos` solo por sus puertos `in` | RNF13, RNF25 |
| Frontend | Jest: hoja de confirmación, casilla obligatoria, errores, outbox sin red | SC-001, Edge 5 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-42-confirmacion-de-pago-del-pedido/
├── RF-42-confirmacion-de-pago-del-pedido.md   # spec
└── plan.md                                    # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pagos/
├── domain/
│   ├── model/                    # Pago (confirmar), EstadoPago, MedioPago
│   ├── event/                    # PagoPedidoConfirmadoEvent (E04)
│   └── exception/                # PagoYaConfirmadoException
├── application/
│   ├── ports/in/                 # ConfirmarPagoPedidoPort, ConsultarPagoPedidoPort (RF-51)
│   ├── ports/out/                # PagoRepositoryPort (bloquearPorPedidoId), EventPublisherPort (shared)
│   └── usecase/                  # ConfirmarPagoPedidoUseCase
└── infrastructure/adapters/out/persistence/   # consulta con FOR UPDATE

backend/src/main/java/co/edu/napa/pedidos/
├── application/
│   ├── ports/in/                 # ConfirmarPagoDomicilioPort
│   ├── ports/out/                # PagoPedidoPort (se extiende con confirmar)
│   └── usecase/                  # ConfirmarPagoDomicilioUseCase
└── infrastructure/adapters/in/web/   # PedidoController (POST /pedidos/{id}/pago/confirmar)
backend/src/test/java/co/edu/napa/{pagos,pedidos}/

frontend/src/components/pagos/ConfirmarCobroSheet.tsx            # hoja compartida con RF-37 y RF-65: monto, medio y casilla
frontend/src/components/pedidos/AccionConfirmarPagoDomicilio.tsx # botón en el detalle del vendedor (RF-51)
frontend/src/services/pagos.ts                                   # confirmarPagoDomicilio() por el outbox
frontend/__tests__/pagos/confirmar-pago.test.tsx
```

**Structure Decision**: el núcleo (`Pago.confirmar`, E04, `ConfirmarPagoPedidoPort`) vive en `pagos`. El caso de uso del endpoint de domicilio vive en `pedidos` (`ConfirmarPagoDomicilioUseCase`), que ya conoce el pedido, su dueño, su modalidad y su estado, y llama al puerto de `pagos`. Así `pagos` no necesita leer `pedidos` y no se crea un ciclo entre los dos módulos (RF-53 y RF-51 ya hacen que `pedidos` dependa de `pagos`). `modalidad` viaja en E04 porque el aviso de pago solo se envía al cliente en domicilio (en entrega directa y recogida el pago y la entrega son el mismo acto y se avisa con E07). El catálogo de eventos asigna E04 a "RF-42 · `ConfirmarPagoPedidoUseCase`", que sigue siendo quien lo emite.

## Phase 1: Setup

- [ ] T001 Confirmar la tabla `pago` y `RegistrarPagoPedidoPendientePort` (RF-53, T002 y T003) y `ConsultarPagoPedidoPort` (RF-51, T003)

## Phase 2: Foundational

- [ ] T002 `PagoRepositoryPort.bloquearPorPedidoId` con `SELECT ... FOR UPDATE` y su adaptador en `pagos/application/ports/out/` y `.../infrastructure/adapters/out/persistence/`
- [ ] T003 Verificar `Clock` inyectable (`TimeConfiguration`) para `confirmado_at`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Vendedor confirma el cobro de un pedido (Priority: P1)

**Goal**: el cobro de cada pedido queda registrado una sola vez, y en domicilio habilita su publicación.
**Independent Test**: confirmar el pago de un domicilio aceptado y ver el pago `CONFIRMADO`; confirmar por el puerto el de una entrega directa y una recogida.
**Cubre**: FR-001–FR-008, SC-001–SC-005, US1-E1–E5, Edge 1–5.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /pedidos/{id}/pago/confirmar` en domicilio aceptado: pago `CONFIRMADO`, medio `CUENTA_BANCARIA`, monto = total, `confirmado_at`, E04 en el outbox, en `backend/src/test/java/co/edu/napa/pedidos/ConfirmarPagoDomicilioIT.java`
- [ ] T011 [P] [US1] Test de domicilio `PENDIENTE` (`INVALID_STATE` con mensaje de aceptar primero) en `.../pedidos/ConfirmarPagoDomicilioPendienteIT.java`
- [ ] T012 [P] [US1] Test de doble confirmación (`ALREADY_CONFIRMED`, una sola fila, un solo E04) y de dos peticiones simultáneas en `.../pedidos/ConfirmarPagoDomicilioConcurrenciaIT.java`
- [ ] T013 [P] [US1] Test de pedido ajeno (`404`), de rol cliente o ambulante (`403`) y de aislamiento entre vendedores en `.../pedidos/ConfirmarPagoDomicilioAjenoIT.java`
- [ ] T014 [P] [US1] Test de pedido `RECHAZADO`, `CANCELADO` y `ENTREGADO` (`INVALID_STATE` con `estadoActual`) y de modalidad distinta de domicilio (`422 BUSINESS_RULE`) en `.../pedidos/ConfirmarPagoDomicilioEstadosIT.java`
- [ ] T015 [P] [US1] Test unitario de `Pago.confirmar` en `backend/src/test/java/co/edu/napa/pagos/PagoTest.java`
- [ ] T016 [P] [US1] Test de integración de `ConfirmarPagoPedidoPort` con medio `EFECTIVO` y `CUENTA_BANCARIA` (conserva el medio del pedido, atómico con la transacción del llamador, E04) en `.../pagos/ConfirmarPagoPedidoPortIT.java`
- [ ] T017 [P] [US1] Test de que `ConsultarPagoPedidoPort` devuelve `CONFIRMADO` tras confirmar y `PENDIENTE_CONFIRMACION` antes (insumo de RF-14) en `.../pagos/ConsultarPagoPedidoConfirmadoIT.java`
- [ ] T018 [P] [US1] Regla ArchUnit: `pagos` no importa `pedidos`; `pedidos` solo usa `pagos.application.ports.in` y `pagos.domain.event` en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`
- [ ] T030 [P] [US1] Test de `ConfirmarCobroSheet` y de `AccionConfirmarPagoDomicilio` (casilla obligatoria, total y medio visibles, errores, outbox sin red) en `frontend/__tests__/pagos/confirmar-pago.test.tsx`

### Implementation

- [ ] T020 [US1] `Pago.confirmar(clock)` y `PagoYaConfirmadoException` en `pagos/domain/`
- [ ] T021 [US1] `ConfirmarPagoPedidoPort` y `ConfirmarPagoPedidoUseCase` (bloqueo del pago, confirmar, publicar E04) en `pagos/application/`
- [ ] T022 [US1] `PagoPedidoConfirmadoEvent` (E04) en `pagos/domain/event/` y publicación por `EventPublisherPort`
- [ ] T023 [US1] `ConfirmarPagoDomicilioUseCase` (bloqueo del pedido, dueño, modalidad, estado `ACEPTADO`, llamada a `PagoPedidoPort.confirmar`) y `POST /pedidos/{id}/pago/confirmar` en `PedidoController`
- [ ] T024 [US1] Extender `PagoPedidoPort` (de `pedidos`) con `confirmar` y su adaptador sobre `ConfirmarPagoPedidoPort`
- [ ] T031 [US1] Servicio `confirmarPagoDomicilio()` por el outbox en `frontend/src/services/pagos.ts`
- [ ] T032 [US1] `ConfirmarCobroSheet` (monto, medio, cuenta registrada si es transferencia, casilla "Verifiqué que recibí el pago") en `frontend/src/components/pagos/`
- [ ] T033 [US1] `AccionConfirmarPagoDomicilio` en `frontend/src/components/pedidos/` e integración en `frontend/src/app/(vendedor)/pedidos/[id].tsx` (visible solo en domicilio `ACEPTADO` con pago pendiente)

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir la confirmación en < 10 s (SC-001)
- [ ] T041 Revisar permisos y que no se guarden datos de pago más allá del registro (RNF03, RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` y la descripción de la ruta en spec-general §2.4 si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-53 y RF-51; el puerto de este plan lo usan RF-37 y RF-65, y RF-14 consulta el estado del pago.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ~~Endpoint de domicilio en `pedidos` y no en `pagos`~~ | Estructura (RNF13) | **Resuelto**: aprobado; evita el ciclo entre módulos | Equipo |
| Monto distinto al total (Edge 2): la spec no define qué hacer | Datos | No se captura el monto recibido: se confirma el total; si no coincide, el vendedor no confirma y lo resuelve fuera de la plataforma | Equipo |
| ~~Doble aviso al cliente (E04 y E07)~~ | UX de RF-38 | **Resuelto**: E04 se emite siempre (es un hecho), pero el listener de RF-38 solo envía el aviso de pago cuando `modalidad = DOMICILIO`; en entrega directa y recogida el cliente recibe solo el de entregado. Hay que añadir `modalidad` al payload de E04 en `eventos.md` | Equipo |
| Domicilio aceptado que nunca se paga: sin vencimiento ni cancelación del vendedor | Edge 4 | Sin vencimiento (foundations) | Equipo |
| Idempotencia por doble confirmación: `ALREADY_CONFIRMED` (409) en lugar de repetir la respuesta | Contrato | `409 ALREADY_CONFIRMED` (la spec pide informar que ya está confirmado) | Equipo |

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
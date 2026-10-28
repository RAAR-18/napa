# Implementation Plan: Respuesta del vendedor a un pedido

**Date**: 2026-10-21
**Spec**: [RF-53-respuesta-del-vendedor-a-un-pedido.md](./RF-53-respuesta-del-vendedor-a-un-pedido.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md) (E02, E03)
**Requerimiento funcional**: RF-53
**Historias de usuario**: HU-83, HU-84
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-45 (`Pedido`), RF-57 (`DescontarInventarioPort`), RF-63 (`ConsultarMediosPagoPort`), RF-10
**Habilita a**: RF-35, RF-36, RF-37, RF-64, RF-65, RF-42, RF-14 (todos parten de un pedido `ACEPTADO`)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor acepta o rechaza únicamente pedidos `PENDIENTE` de su emprendimiento. Aceptar ocurre en una sola transacción: bloquea el pedido, verifica la cuenta bancaria si es domicilio, descuenta el inventario de forma atómica solo si el pedido es inmediato (las reservas nunca validan ni descuentan), pasa el pedido a `ACEPTADO`, crea el `Pago` del pedido en `PENDIENTE_CONFIRMACION` con el medio que eligió el cliente y emite E02 por el outbox. Rechazar pasa a `RECHAZADO` y emite E03. El módulo `pedidos` es dueño de la máquina de estados; los demás módulos que mueven el pedido lo hacen por su puerto `in`.

## Estado actual del código

No existe `pedidos` ni la tabla `pago`. `DescontarInventarioPort` (RF-57) y `ConsultarMediosPagoPort` (RF-63) los definen sus planes. Este plan crea la tabla `pago` y el puerto `in` de `pagos` para registrar el pago pendiente; RF-42 lo extiende para confirmar.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Recibe el aviso (E02 o E03); en domicilio debe transferir el total |
| Vendedor (ambulante / punto fijo) | Acepta o rechaza |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Aceptar o rechazar en < 10 s (SC-001): una llamada, sin formularios.
- Respuesta visible para el cliente en < 1 min (SC-002): E02/E03 por el outbox (relay cada 500 ms).
- 0 % de pedidos inmediatos aceptados sin inventario (SC-003) y 0 % de domicilios aceptados sin cuenta (SC-004): reglas en el servidor dentro de la transacción.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Aceptar o rechazar solo pedidos propios en `PENDIENTE` | Bloqueo `SELECT ... FOR UPDATE`, verificación de dueño y de estado; `404` si no es suyo, `409 INVALID_STATE` si no está pendiente | T010, T013, T014 |
| FR-002 | Cambiar el estado y notificar al cliente | `Pedido.aceptar()` / `rechazar()`; E02 y E03 por `EventPublisherPort` | T011, T015, T016 |
| FR-003 | Validar y descontar inventario al aceptar un pedido inmediato, de forma atómica; las reservas nunca | `DescontarInventarioPort` (RF-57) dentro de la misma transacción, solo si `reserva` es nulo; `OUT_OF_STOCK` revierte todo | T012, T017 |
| FR-004 | Habilitar el siguiente paso según la modalidad | El estado `ACEPTADO` es la precondición que verifican RF-35, RF-64 y RF-14; aquí se prueba que cada modalidad queda en `ACEPTADO` (sin lógica extra) | T018 |
| FR-005 | Crear el `Pago` en `PENDIENTE_CONFIRMACION` con el medio elegido | Puerto `in` de `pagos` `RegistrarPagoPedidoPendientePort`; monto = `total`, medio = `Pedido.medio_pago`; único por pedido | T019, T020, T021 |
| FR-006 | Impedir aceptar un domicilio sin cuenta bancaria | `ConsultarMediosPagoPort.domicilioDisponible`; `409 BANK_ACCOUNT_REQUIRED` | T022 |
| FR-007 | Notificar al cliente que debe transferir el total (domicilio) | E02 con `requierePagoDigital = true` y `total`; el texto lo arma el listener de RF-38 | T016 |
| SC-001 | < 10 s | Una llamada sin cuerpo; botones en el detalle | T040 |
| SC-002 | < 1 min | E02/E03 + relay | T016 |
| SC-003 | 0 % sin inventario | Prueba de integración y de concurrencia | T012, T023 |
| SC-004 | 0 % de domicilios sin cuenta | Prueba de integración | T022 |
| US1-E1 | Aceptación de entrega directa | Estado `ACEPTADO` + `Pago` pendiente | T010 |
| US1-E2 | Aceptación de recogida | Igual | T010 |
| US1-E3 | Aceptación de domicilio | `ACEPTADO` + `Pago` + E02 con transferencia | T010, T016 |
| US1-E4 | Aceptación de reserva sin validar ni descontar | Rama de reserva sin llamar a inventario | T012 |
| US1-E5 | Domicilio sin cuenta bancaria | `BANK_ACCOUNT_REQUIRED` | T022 |
| US1-E6 | Inventario insuficiente | `OUT_OF_STOCK`; el pedido sigue `PENDIENTE` | T012 |
| US2-E1 | Rechazo de un pedido pendiente | `RECHAZADO` + E03 | T011 |
| US2-E2 | Rechazo de una reserva | Igual | T011 |
| US2-E3 | Rechazo de un pedido ya aceptado | `409 INVALID_STATE` con `estadoActual` | T014 |
| Edge 1 | Pedido sin respuesta | Sin vencimiento automático (foundations): nada que construir | — |
| Edge 2 | Motivo al rechazar | **Decisión pendiente**: el modelo de `Pedido` no tiene motivo | — |
| Edge 3 | Varios pedidos compiten por el mismo inventario | Descuento atómico (RF-57); uno se acepta y el otro recibe `OUT_OF_STOCK` | T023 |
| Edge 4 | Domicilio aceptado que nunca se paga | Permanece `ACEPTADO` con pago pendiente (foundations: sin vencimiento) | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el dueño del emprendimiento del pedido; la pertenencia se valida en el caso de uso |
| RNF07 Rendimiento | ☑ | Una transacción corta |
| RNF08 Concurrencia | ☑ | Bloqueo de fila del pedido y descuento atómico |
| RNF15 Fiabilidad | ☑ | Aceptación, descuento, pago y evento en una sola transacción |
| RNF16 Trazabilidad | ☑ | `updated_at` del pedido; `Pago.created_at` |
| RNF23 Consistencia | ☑ | Transiciones solo desde `PENDIENTE` |
| RNF24 Errores claros | ☑ | `OUT_OF_STOCK`, `BANK_ACCOUNT_REQUIRED`, `INVALID_STATE` |
| RNF27 Integridad | ☑ | `Idempotency-Key` y único del pago por pedido |
| RNF28 Conectividad | ☑ | Outbox en la app |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Lee / escribe (`estado`) | `PENDIENTE` → `ACEPTADO` o `RECHAZADO` |
| ItemPedido | Lee | Para el descuento de inventario |
| Producto | Escribe (vía `DescontarInventarioPort`) | Solo en pedidos inmediatos |
| Pago | Escribe | Un solo pago `PEDIDO` por pedido; `monto = total`; `medio = Pedido.medio_pago` |
| ConfiguracionPago | Lee (vía puerto) | `domicilioDisponible` |
| outbox_event | Escribe | E02 o E03 |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento |
|---|---|---|---|---|---|
| Pedido | `PENDIENTE` | `ACEPTADO` | Vendedor dueño | Inventario cubierto si es inmediato; cuenta bancaria si es domicilio | E02 `PedidoAceptado` |
| Pedido | `PENDIENTE` | `RECHAZADO` | Vendedor dueño | — | E03 `PedidoRechazado` |
| Pago | (nuevo) | `PENDIENTE_CONFIRMACION` | Al aceptar | Pedido aceptado | Ninguno |

**Transiciones inválidas**: cualquier estado distinto de `PENDIENTE` → `409 INVALID_STATE` con `details.estadoActual`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Aceptar o rechazar | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | El pedido es del emprendimiento del usuario autenticado; si no, `404 NOT_FOUND` (el pedido "no está disponible") |

## API Contracts

### POST /api/v1/pedidos/{id}/aceptar

- **Rol**: vendedor · **Path params**: `id` (UUID del pedido) · **Query params**: ninguno · **Body**: ninguno

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |

- **Respuestas exitosas**: `200` con `{ "id", "estado": "ACEPTADO", "modalidad", "reserva", "total", "pago": { "id", "estado": "PENDIENTE_CONFIRMACION", "medio", "monto" } }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor | — |
| 404 | `NOT_FOUND` | Pedido inexistente o de otro emprendimiento | "El pedido no está disponible" |
| 409 | `INVALID_STATE` | El pedido no está `PENDIENTE` | `details.estadoActual` |
| 409 | `OUT_OF_STOCK` | Pedido inmediato sin inventario suficiente | "Ajusta tu inventario o rechaza el pedido" |
| 409 | `BANK_ACCOUNT_REQUIRED` | Domicilio sin cuenta bancaria del vendedor | "Registra una cuenta bancaria primero" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

### POST /api/v1/pedidos/{id}/rechazar

- **Rol**: vendedor · **Path params**: `id` · **Body**: ninguno · **Cabeceras**: `Authorization`, `Idempotency-Key`.
- **Respuestas exitosas**: `200` con `{ "id", "estado": "RECHAZADO" }`.
- **Errores**: `401`, `403`, `404 NOT_FOUND`, `409 INVALID_STATE` (con `estadoActual`), `422 IDEMPOTENCY_KEY_REUSED`.

### Puerto `in` de `pagos` (no es endpoint)

`RegistrarPagoPedidoPendientePort.registrar(pedidoId, compradorId, vendedorId, monto, medio)` crea el `Pago` `PEDIDO` en `PENDIENTE_CONFIRMACION` y copia `comprador_id` y `vendedor_id` (copias físicas para el listado de RF-40/41, sin cambio de ER); falla si ya existe uno para ese pedido.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E02 `PedidoAceptadoEvent` (`pedidos.pedido-aceptado`) | `ResponderPedidoUseCase.aceptar` | Al confirmar la aceptación, en la misma transacción. Payload: `pedidoId`, `compradorId`, `modalidad`, `requierePagoDigital`, `total` |
| E03 `PedidoRechazadoEvent` (`pedidos.pedido-rechazado`) | `ResponderPedidoUseCase.rechazar` | Al rechazar. Payload: `pedidoId`, `compradorId` |

`requierePagoDigital` es `true` cuando la modalidad es DOMICILIO (el cliente debe transferir apenas se acepte). **Eventos que consume esta feature**: ninguno. Los listeners que avisan al cliente se planifican en RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: aceptar y rechazar van por el outbox con su `Idempotency-Key` ("pendiente de envío"); al sincronizar, si el servidor responde `OUT_OF_STOCK`, `INVALID_STATE` o `BANK_ACCOUNT_REQUIRED`, la app muestra el motivo y actualiza el pedido desde el servidor.
- **Concurrencia (RNF08)**: bloqueo del pedido con `SELECT ... FOR UPDATE`: dos respuestas simultáneas se serializan y la segunda recibe `INVALID_STATE`. El descuento usa `UPDATE ... WHERE cantidad_disponible >= :x` en orden por id (RF-57); si falla, se revierte toda la transacción y el pedido sigue `PENDIENTE`.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}` + único de `Pago` por pedido como garantía final.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido no pendiente | `409 INVALID_STATE` con el estado actual | US2-E3 |
| Sin inventario en pedido inmediato | `409 OUT_OF_STOCK`; sin cambios | US1-E6 |
| Domicilio sin cuenta | `409 BANK_ACCOUNT_REQUIRED`; sin cambios | US1-E5 |
| Falla al crear el pago | Se revierte la aceptación completa | RNF15 |
| Pedido de otro emprendimiento | `404 NOT_FOUND` | RNF03 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Pedido.aceptar()` / `rechazar()` y sus precondiciones | FR-001, FR-002 |
| Integración | Endpoints con Testcontainers: cada modalidad, reserva sin inventario, `Pago` creado, E02 y E03 en el outbox, reversión completa ante `OUT_OF_STOCK` | US1, US2, FR-003, FR-005 |
| Concurrencia | Dos aceptaciones por el mismo pedido y dos pedidos que compiten por la última unidad | Edge 3, SC-003 |
| Arquitectura | `pedidos` usa `productos` y `pagos` solo por sus puertos `in` | RNF13, RNF25 |
| Frontend | Jest: botones, estados, errores y outbox sin red | SC-001 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-53-respuesta-del-vendedor-a-un-pedido/
├── RF-53-respuesta-del-vendedor-a-un-pedido.md   # spec
└── plan.md                                       # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pedidos/
├── domain/
│   ├── model/                    # Pedido (aceptar, rechazar)
│   ├── event/                    # PedidoAceptadoEvent, PedidoRechazadoEvent
│   └── exception/
├── application/
│   ├── ports/in/                 # ResponderPedidoPort (aceptar, rechazar)
│   ├── ports/out/                # PedidoRepositoryPort (bloqueo), InventarioPort, PagoPedidoPort, MediosPagoVendedorPort, EventPublisherPort (shared)
│   └── usecase/                  # ResponderPedidoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # PedidoController (aceptar, rechazar)
    └── out/persistence/          # adaptadores a los puertos `in` de productos y pagos

backend/src/main/java/co/edu/napa/pagos/
├── domain/model/                 # Pago, TipoPago, MedioPago, EstadoPago
├── application/ports/in/         # RegistrarPagoPedidoPendientePort
├── application/usecase/          # RegistrarPagoPedidoPendienteUseCase
└── infrastructure/adapters/out/persistence/   # PagoJpaEntity, repositorio
backend/src/main/resources/db/migration/       # V9__pago.sql
backend/src/test/java/co/edu/napa/pedidos/

frontend/src/components/pedidos/AccionesRespuestaPedido.tsx     # botones Aceptar y Rechazar con confirmación
frontend/src/app/(vendedor)/pedidos/[id].tsx                    # (RF-51) los incluye en el detalle
frontend/src/services/pedidos.ts                                # aceptarPedido(), rechazarPedido() por el outbox
frontend/__tests__/pedidos/respuesta.test.tsx
```

**Structure Decision**: módulo `pedidos` es dueño de la máquina de estados. `pagos` crea el `Pago` por su puerto `in`; `productos` descuenta por el suyo. Ningún módulo cambia el estado de un pedido sin pasar por `pedidos`.

## Phase 1: Setup

- [ ] T001 Confirmar los puertos `DescontarInventarioPort` (RF-57), `ConsultarMediosPagoPort` (RF-63) y la tabla `pedido` (RF-45)

## Phase 2: Foundational

- [ ] T002 Migración `V9__pago.sql`: tabla `pago` (id, tipo, monto `bigint`, medio, estado, confirmado_at, pedido_id único nulo, domicilio_id único nulo, `comprador_id`, `vendedor_id`, `domiciliario_id` (nulos; copias físicas para RF-40/41, sin cambio de ER) con índices `(comprador_id, created_at DESC)`, `(vendedor_id, created_at DESC)` y `(domiciliario_id, created_at DESC)`, created_at, updated_at) con CHECK de que tiene exactamente uno de `pedido_id` o `domicilio_id`, en `backend/src/main/resources/db/migration/`
- [ ] T003 Dominio `Pago` y `RegistrarPagoPedidoPendienteUseCase` con su puerto `in` en `pagos/`
- [ ] T004 `PedidoRepositoryPort.bloquearPorId` con `SELECT ... FOR UPDATE`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Aceptar un pedido (Priority: P1)

**Goal**: comprometerse con un pedido y dejar listo el siguiente paso.
**Independent Test**: aceptar un pedido pendiente de cada modalidad y comprobar `ACEPTADO`, el pago pendiente y E02.
**Cubre**: FR-001–FR-007, SC-001–SC-004, US1-E1–E6, Edge 3–4.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `aceptar` por modalidad (estado, `Pago` pendiente con el medio del pedido, E02) en `backend/src/test/java/co/edu/napa/pedidos/AceptarPedidoIT.java`
- [ ] T012 [P] [US1] Test de inventario: inmediato descuenta; reserva no valida ni descuenta; `OUT_OF_STOCK` revierte todo, en `.../pedidos/AceptarPedidoInventarioIT.java`
- [ ] T018 [P] [US1] Test de que cada modalidad queda `ACEPTADO` y habilita su siguiente paso (precondición para RF-35, RF-64, RF-14) en `.../pedidos/AceptarPedidoModalidadIT.java`
- [ ] T022 [P] [US1] Test de domicilio con y sin cuenta bancaria (`BANK_ACCOUNT_REQUIRED`) en `.../pedidos/AceptarPedidoDomicilioIT.java`
- [ ] T023 [P] [US1] Test de concurrencia: dos aceptaciones simultáneas y dos pedidos por la última unidad en `.../pedidos/AceptarPedidoConcurrenciaIT.java`
- [ ] T024 [P] [US1] Test de que un fallo al crear el pago revierte la aceptación en `.../pedidos/AceptarPedidoAtomicidadIT.java`

### Implementation

- [ ] T013 [US1] `Pedido.aceptar()` con sus precondiciones en `pedidos/domain/model/Pedido.java`
- [ ] T014 [US1] Endpoint `POST /pedidos/{id}/aceptar` en `PedidoController`
- [ ] T015 [US1] `ResponderPedidoUseCase.aceptar` (bloqueo, dueño, estado)
- [ ] T017 [US1] Descuento de inventario solo si `reserva` es nulo, por `InventarioPort`
- [ ] T019 [US1] Creación del `Pago` por `PagoPedidoPort` con `monto = total` y `medio = medio_pago`
- [ ] T020 [US1] Validación de domicilio con `MediosPagoVendedorPort`
- [ ] T016 [US1] Evento `PedidoAceptadoEvent` y publicación por `EventPublisherPort`

**Checkpoint**: US1 funcional.

## Phase 4: User Story 2 - Rechazar un pedido (Priority: P1)

**Goal**: declinar un pedido sin cambios de inventario.
**Independent Test**: rechazar un pedido pendiente y verificar `RECHAZADO` y E03.
**Cubre**: US2-E1–E3, FR-002.

### Tests (antes de implementar)

- [ ] T011 [P] [US2] Test de integración de `rechazar` (inmediato, reserva, ya aceptado, ajeno, E03) en `.../pedidos/RechazarPedidoIT.java`

### Implementation

- [ ] T021 [US2] `Pedido.rechazar()` y `ResponderPedidoUseCase.rechazar`
- [ ] T025 [US2] Endpoint `POST /pedidos/{id}/rechazar` y evento `PedidoRechazadoEvent`

## Phase 5: Frontend (US1–US2)

- [ ] T030 [P] Test de `AccionesRespuestaPedido` (confirmación, errores `OUT_OF_STOCK`/`BANK_ACCOUNT_REQUIRED`, outbox sin red) en `frontend/__tests__/pedidos/respuesta.test.tsx`
- [ ] T031 Servicios `aceptarPedido()` y `rechazarPedido()` por el outbox en `frontend/src/services/pedidos.ts`
- [ ] T032 `AccionesRespuestaPedido` en `frontend/src/components/pedidos/` e integración en `frontend/src/app/(vendedor)/pedidos/[id].tsx`

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir aceptar/rechazar en < 10 s (SC-001) y la notificación en < 1 min (SC-002)
- [ ] T041 Revisar permisos (RNF03)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → Frontend → Polish.
- Dependencias entre RF: requiere RF-45, RF-57 y RF-63; habilita RF-35, RF-36, RF-37, RF-64, RF-65, RF-42 y RF-14.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿El vendedor puede indicar un motivo al rechazar? El modelo de `Pedido` no tiene ese campo | Edge 2 | Sin motivo | Equipo |
| Definición de `requierePagoDigital` en E02 | Payload del evento | `true` solo en DOMICILIO | Equipo |
| La tabla `pago` nace aquí; `domicilio_id` queda sin clave foránea hasta crear `domicilio` (RF-14) | Migraciones | Agregar la FK en la migración de RF-14 | Equipo |
| Pedido de otro vendedor: `404` (spec de pedidos: "no está disponible") frente a `403` (usado en RF-57 para productos) | Coherencia de la API | `404` en pedidos | Equipo |
| ¿Aceptar y rechazar por el outbox sin conexión? | RNF28 | Sí | Equipo |

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
# Implementation Plan: Confirmación de entrega directa

**Date**: 2026-10-21
**Spec**: [RF-37-confirmacion-de-entrega-directa.md](./RF-37-confirmacion-de-entrega-directa.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md) (E04, E07)
**Requerimiento funcional**: RF-37
**Historias de usuario**: HU-62
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-36 (pedido `EN_CAMINO` y `TransicionarPedidoPort`), RF-42 (`ConfirmarPagoPedidoPort`), RF-53 (`Pago` pendiente), RF-51 (donde vive el botón), RF-10
**Habilita a**: RF-01 (el pedido `ENTREGADO` se puede calificar), RF-60 (reporte si el cliente no recibe o no paga)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor ambulante cierra la entrega directa con `POST /pedidos/{id}/confirmar-entrega`. En **una sola transacción** el pedido pasa de `EN_CAMINO` a `ENTREGADO` y el pago del pedido pasa a `CONFIRMADO` con el medio que el cliente eligió al pedir (efectivo o cuenta bancaria); el vendedor no escoge el medio, solo confirma que cobró. El cuerpo exige `cobroConfirmado: true` (FR-002). Se emiten E04 (pago confirmado, lo publica `pagos`) y E07 (pedido entregado, lo publica `pedidos` al transicionar); las calificaciones se habilitan porque la elegibilidad se deriva del estado `ENTREGADO` (RF-01). Una segunda confirmación responde `409 INVALID_STATE`. Si el cliente no recibe o no paga, el pedido queda `EN_CAMINO` y la app ofrece crear un reporte (RF-60).

## Estado actual del código

No existe la transición ni la confirmación. Dependen de los planes de RF-36 (`TransicionarPedidoPort`, `EN_CAMINO`), RF-42 (`ConfirmarPagoPedidoPort`) y RF-53 (el `Pago` en `PENDIENTE_CONFIRMACION`).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Paga al recibir; recibe los avisos y luego puede calificar |
| Vendedor (ambulante) | Confirma la entrega y el cobro |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Confirmar entrega y cobro en < 15 s (SC-001): una llamada con una casilla.
- 100 % de los pedidos entregados por ambulantes con pago confirmado y medio igual al elegido (SC-002): pedido y pago cambian en la misma transacción; si algo falla, no cambia nada.
- Una sola confirmación por pedido (FR-004): bloqueo del pedido y estado verificado.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Confirmar solo pedidos `EN_CAMINO` | `Pedido.confirmarEntregaDirecta()` exige modalidad `ENTREGA_DIRECTA` y estado `EN_CAMINO` → `409 INVALID_STATE` con `estadoActual` | T010, T012, T015, T016 |
| FR-002 | Pedir la confirmación del cobro, por el medio que eligió el cliente | Cuerpo `cobroConfirmado: true` obligatorio; `ConfirmarPagoPedidoPort` (RF-42) conserva `Pedido.medio_pago`; el vendedor no manda el medio | T010, T011, T013 |
| FR-003 | `ENTREGADO` y habilitar calificaciones | Transición del pedido y E07 (publicado por `pedidos`); la elegibilidad de RF-01 se deriva del estado | T010, T016 |
| FR-004 | No confirmar dos veces | Bloqueo `FOR UPDATE` y `INVALID_STATE` (`ENTREGADO`); misma clave de idempotencia repite la respuesta | T014 |
| SC-001 | < 15 s | Una llamada; hoja con casilla | T040 |
| SC-002 | 100 % con pago confirmado y medio correcto | Atomicidad pedido + pago | T010, T011, T018 |
| US1-E1 | Entrega y cobro en efectivo | `ENTREGADO` + pago `CONFIRMADO` con `EFECTIVO` + E04 + E07 | T010 |
| US1-E2 | Entrega y cobro por transferencia | Igual con `CUENTA_BANCARIA` | T011 |
| US1-E3 | Confirmación fuera de secuencia | `409 INVALID_STATE` con el estado actual | T012 |
| US1-E4 | El cliente no recibe o no paga | El pedido queda `EN_CAMINO`; la app ofrece "Reportar un problema" (RF-60) | T019, T023 |
| Edge 1 | Monto distinto del total | No se captura monto; el vendedor no confirma el cobro y lo resuelve fuera (Open Questions) | — |
| Edge 2 | Entrega parcial | No existe en la spec ni en el modelo; no se ofrece (Open Questions) | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Rol y pertenencia validados en el caso de uso |
| RNF08 Concurrencia | ☑ | Pedido y pago bloqueados `FOR UPDATE` en ese orden |
| RNF15 Fiabilidad | ☑ | Pedido, pago, E04 y E07 en una sola transacción |
| RNF16 Trazabilidad | ☑ | `updated_at` y `confirmado_at` |
| RNF18 Notificaciones | ☑ | E04 y E07 |
| RNF23 Consistencia | ☑ | Estado del pedido y del pago coherentes |
| RNF24 Errores claros | ☑ | `INVALID_STATE` con `estadoActual` |
| RNF27 Integridad | ☑ | `Idempotency-Key` y único del pago por pedido |
| RNF28 Conectividad | ☑ | Acción por el outbox del dispositivo |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Lee / escribe (`estado`) | `ENTREGA_DIRECTA`, `EN_CAMINO` → `ENTREGADO` |
| Pago | Lee / escribe (`estado`, `confirmado_at`) | `PENDIENTE_CONFIRMACION` → `CONFIRMADO`, medio = `Pedido.medio_pago` |
| Emprendimiento | Lee (dueño) | Pertenencia |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Pedido (entrega directa) | `EN_CAMINO` | `ENTREGADO` | Vendedor ambulante confirma | Pedido propio; `cobroConfirmado = true` | E07; habilita calificar |
| Pago (tipo PEDIDO) | `PENDIENTE_CONFIRMACION` | `CONFIRMADO` | La misma confirmación | Pago existente y pendiente | `confirmado_at`; E04 |

**Transiciones inválidas**: pedido en otro estado (incluye `ENTREGADO`) → `409 INVALID_STATE` con `details.estadoActual`; pago ya confirmado con el pedido `EN_CAMINO` es un estado imposible por construcción y se trata como error de integridad.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /pedidos/{id}/confirmar-entrega` | VENDEDOR_AMBULANTE | Pedido de su emprendimiento; si no, `404` |

## API Contracts

### POST /api/v1/pedidos/{id}/confirmar-entrega

- **Rol**: vendedor ambulante · **Path params**: `id` (UUID) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅, `Content-Type: application/json`.
- **Body**:

```json
{ "cobroConfirmado": true }
```

- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "id": "…", "estado": "ENTREGADO", "modalidad": "ENTREGA_DIRECTA", "updatedAt": "…",
  "pago": { "id": "…", "estado": "CONFIRMADO", "medio": "EFECTIVO", "monto": 8750, "confirmadoAt": "…" }
}
```

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key`, falta `cobroConfirmado` o no es `true` | "Confirma que recibiste el pago" |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor ambulante | — |
| 404 | `NOT_FOUND` | Pedido inexistente o de otro emprendimiento | "El pedido no está disponible" |
| 409 | `INVALID_STATE` | El pedido no está `EN_CAMINO` (incluye ya `ENTREGADO`) | `details.estadoActual` |
| 422 | `BUSINESS_RULE` | El pedido no es de entrega directa | "Este pedido no es de entrega directa" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E04 `PagoPedidoConfirmadoEvent` (`pagos.pago-pedido-confirmado`) | `ConfirmarPagoPedidoUseCase` (`pagos`), llamado desde este caso de uso | Al confirmar el cobro, en la misma transacción |
| E07 `PedidoEntregadoEvent` (`pedidos.pedido-entregado`) | `TransicionarPedidoUseCase.confirmarEntregaDirecta` (`pedidos`) | Al pasar a `ENTREGADO`. Payload: `pedidoId`, `compradorId`, `vendedorId`, `domicilioId?` (nulo aquí) |

**Eventos que consume esta feature**: ninguno. Los avisos y la elegibilidad de calificar son listeners o derivaciones de otros planes (RF-38, RF-01).

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: es el caso crítico del ambulante en la calle. La acción se escribe en el outbox con su `Idempotency-Key` y la pantalla muestra "pendiente de envío". Al reconectar se envía; si el servidor responde `INVALID_STATE` (por ejemplo porque ya se había confirmado), la app actualiza el detalle y lo explica.
- **Concurrencia (RNF08)**: el caso de uso bloquea el pedido y luego el pago; dos solicitudes simultáneas: una confirma y la otra recibe `INVALID_STATE` con `ENTREGADO`.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`; el único de `Pago` por pedido es la garantía final.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido no `EN_CAMINO` | `409 INVALID_STATE` con el estado actual | US1-E3 |
| Doble confirmación | `409 INVALID_STATE` (`ENTREGADO`); con la misma clave, repite la respuesta | FR-004 |
| Sin confirmar el cobro | `400 VALIDATION_ERROR`; no cambia nada | FR-002 |
| Falla al confirmar el pago | Se revierte el cambio de estado del pedido | RNF15 |
| El cliente no recibe o no paga | El pedido sigue `EN_CAMINO`; "Reportar un problema" abre RF-60 | US1-E4 |
| Sin conexión | Outbox con reintento | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Pedido.confirmarEntregaDirecta` y la verificación de `cobroConfirmado` | FR-001, FR-002 |
| Integración | Endpoint con Testcontainers: efectivo y transferencia (pedido `ENTREGADO`, pago `CONFIRMADO` con el medio del pedido, E04 y E07 en el outbox), estados no válidos, modalidad distinta, ajeno, rol equivocado, cuerpo sin cobro, falla del pago que revierte todo | US1-E1–E3, SC-002 |
| Concurrencia | Dos confirmaciones simultáneas: una gana, un solo pago confirmado, un solo E07 | FR-004 |
| Arquitectura | `entregas` usa `pedidos` y `pagos` solo por sus puertos `in` | RNF13, RNF25 |
| Frontend | Jest: hoja de cobro por medio, casilla obligatoria, error por estado, outbox sin red, acceso a "Reportar un problema" | SC-001, US1-E4 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-37-confirmacion-de-entrega-directa/
├── RF-37-confirmacion-de-entrega-directa.md   # spec
└── plan.md                                    # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/entregas/
├── application/
│   ├── ports/in/                 # ConfirmarEntregaDirectaPort
│   ├── ports/out/                # TransicionPedidoPort (RF-36), CobroPedidoPort (hacia `pagos`)
│   └── usecase/                  # ConfirmarEntregaDirectaUseCase
└── infrastructure/adapters/
    ├── in/web/                   # EntregaDirectaController (POST /pedidos/{id}/confirmar-entrega), DTO con cobroConfirmado
    └── out/persistence/          # adaptadores sobre los puertos `in` de pedidos y pagos

backend/src/main/java/co/edu/napa/pedidos/
├── domain/event/                 # PedidoEntregadoEvent (E07)
├── domain/model/                 # Pedido.confirmarEntregaDirecta
└── application/ports/in/         # TransicionarPedidoPort.confirmarEntregaDirecta (se añade al de RF-36)
backend/src/test/java/co/edu/napa/{entregas,pedidos}/

frontend/src/components/entregas/AccionesEntregaDirecta.tsx   # se añade "Confirmar entrega" (RF-36 trae "Iniciar entrega")
frontend/src/components/pagos/ConfirmarCobroSheet.tsx         # (RF-42) hoja compartida: efectivo o transferencia
frontend/src/services/entregas.ts                             # confirmarEntrega() por el outbox
frontend/__tests__/entregas/confirmar-entrega.test.tsx
```

**Structure Decision**: el caso de uso vive en `entregas` y orquesta, dentro de una transacción, la transición del pedido (`TransicionarPedidoPort`, de `pedidos`) y el cobro (`ConfirmarPagoPedidoPort`, de `pagos`), (E07 lo publica `pedidos` al transicionar y E04 lo publica `pagos`; `entregas` no publica eventos en esta feature). El orden es siempre el mismo (pedido, luego pago) para evitar interbloqueos. RF-65 hace lo mismo para la recogida.

## Phase 1: Setup

- [ ] T001 Confirmar `TransicionarPedidoPort` (RF-36, T004), `ConfirmarPagoPedidoPort` (RF-42, T021) y el `Pago` pendiente (RF-53, T019)

## Phase 2: Foundational

- [ ] T002 `CobroPedidoPort` en `entregas/application/ports/out/` y su adaptador sobre `ConfirmarPagoPedidoPort`
- [ ] T003 `PedidoEntregadoEvent` (E07) en `pedidos/domain/event/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Vendedor cierra la entrega y cobra (Priority: P1)

**Goal**: la entrega queda cerrada y cobrada con el medio que eligió el cliente.
**Independent Test**: con un pedido `EN_CAMINO`, confirmar la entrega y el cobro en efectivo y por transferencia.
**Cubre**: FR-001–FR-004, SC-001–SC-002, US1-E1–E4, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /pedidos/{id}/confirmar-entrega` con pedido en efectivo (`ENTREGADO`, pago `CONFIRMADO` con `EFECTIVO`, E04 y E07 en el outbox) en `backend/src/test/java/co/edu/napa/entregas/ConfirmarEntregaEfectivoIT.java`
- [ ] T011 [P] [US1] Test con pedido por transferencia (pago `CONFIRMADO` con `CUENTA_BANCARIA`) y de cuerpo sin `cobroConfirmado` o en `false` (`400`, nada cambia) en `.../entregas/ConfirmarEntregaTransferenciaIT.java`
- [ ] T012 [P] [US1] Test de estados no válidos (`ACEPTADO`, `PENDIENTE`, `ENTREGADO`, `RECHAZADO`) con `INVALID_STATE` y `estadoActual`, y de modalidad distinta (`422`) en `.../entregas/ConfirmarEntregaEstadosIT.java`
- [ ] T013 [P] [US1] Test unitario de `Pedido.confirmarEntregaDirecta` en `backend/src/test/java/co/edu/napa/pedidos/PedidoConfirmarEntregaTest.java`
- [ ] T014 [P] [US1] Test de doble confirmación y de dos solicitudes simultáneas (un solo pago confirmado, un solo E07; misma clave repite la respuesta) en `.../entregas/ConfirmarEntregaConcurrenciaIT.java`
- [ ] T018 [P] [US1] Test de atomicidad: si falla la confirmación del pago, el pedido sigue `EN_CAMINO` y no se emite ningún evento, en `.../entregas/ConfirmarEntregaAtomicidadIT.java`
- [ ] T019 [P] [US1] Test de pedido ajeno (`404`), rol equivocado (`403`) y falta de `Idempotency-Key` (`400`) en `.../entregas/ConfirmarEntregaAutorizacionIT.java`
- [ ] T023 [P] [US1] Test de la pantalla (hoja de cobro según el medio, casilla obligatoria, error por estado, outbox sin red, acceso a "Reportar un problema") en `frontend/__tests__/entregas/confirmar-entrega.test.tsx`

### Implementation

- [ ] T015 [US1] `Pedido.confirmarEntregaDirecta()` en `pedidos/domain/model/Pedido.java`
- [ ] T016 [US1] `TransicionarPedidoPort.confirmarEntregaDirecta` y su caso de uso (bloqueo, dueño, modalidad, estado y publicación de E07) en `pedidos/application/`
- [ ] T017 [US1] `ConfirmarEntregaDirectaUseCase` (transición, cobro por `CobroPedidoPort`) y `POST /pedidos/{id}/confirmar-entrega` con su DTO en `entregas/`
- [ ] T020 [US1] Servicio `confirmarEntrega()` por el outbox en `frontend/src/services/entregas.ts`
- [ ] T021 [US1] Botón "Confirmar entrega" y uso de `ConfirmarCobroSheet` en `frontend/src/components/entregas/AccionesEntregaDirecta.tsx` (visible en entrega directa `EN_CAMINO`)
- [ ] T022 [US1] Acceso a "Reportar un problema" (RF-60) desde la misma pantalla en `frontend/src/app/(vendedor)/pedidos/[id].tsx`

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir la confirmación en < 15 s (SC-001)
- [ ] T041 Revisar permisos y que ningún evento lleve datos personales (RNF03, RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-36, RF-42 y RF-53; habilita RF-01; el reporte del problema (US1-E4) lo implementa RF-60 (ola 6).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: el cliente paga un monto distinto | Datos | No se captura el monto; el vendedor confirma el cobro solo si recibió el total | Equipo |
| Edge 2: entrega parcial; el modelo no tiene entregas parciales | Alcance | No se ofrece; se resuelve fuera de la plataforma o con un reporte | Equipo |
| Cuerpo `cobroConfirmado` en lugar de un `POST` sin cuerpo (la spec pide "solicitar la confirmación del cobro") | Contrato | Cuerpo con la casilla obligatoria; no se envía el medio | Equipo |
| ~~El cliente recibe E04 y E07 al mismo tiempo~~ | UX de RF-38 | **Resuelto**: se emiten ambos, pero el aviso de pago (E04) solo se envía en domicilio; en entrega directa el cliente recibe solo el de entregado (E07). E04 lleva `modalidad` en su payload; el listener está en RF-38 | Equipo |
| US1-E4: el reporte desde el pedido `EN_CAMINO` depende de RF-60 (ola 6) | Orden de construcción | Hasta entonces la app solo muestra el pedido sin entregar | Equipo |
| Pedido `EN_CAMINO` que nunca se confirma | Sin vencimiento (foundations) | Sigue `EN_CAMINO` | Equipo |

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
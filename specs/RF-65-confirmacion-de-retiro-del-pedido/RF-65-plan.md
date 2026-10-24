# Implementation Plan: Confirmación de retiro del pedido

**Date**: 2026-10-21
**Spec**: [RF-65-confirmacion-de-retiro-del-pedido.md](./RF-65-confirmacion-de-retiro-del-pedido.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md) (E04, E07)
**Requerimiento funcional**: RF-65
**Historias de usuario**: HU-98
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-64 (pedido `LISTO_PARA_RECOGER`), RF-36 (`TransicionarPedidoPort`), RF-42 (`ConfirmarPagoPedidoPort`), RF-53 (`Pago` pendiente), RF-51 (donde vive el botón), RF-10
**Habilita a**: RF-01 (el pedido `ENTREGADO` se puede calificar)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor de punto fijo cierra la recogida con `POST /pedidos/{id}/confirmar-retiro`. En **una sola transacción** el pedido pasa de `LISTO_PARA_RECOGER` a `ENTREGADO` y el pago del pedido pasa a `CONFIRMADO` con el medio que eligió el cliente al pedir. Igual que en RF-37, el cuerpo exige `cobroConfirmado: true` y el vendedor no escoge el medio. Se emiten E04 (lo publica `pagos`) y E07 (lo publica `pedidos` al transicionar); las calificaciones se habilitan por el estado `ENTREGADO`. Es la contraparte de RF-37 para la recogida y reutiliza `TransicionarPedidoPort` y `ConfirmarPagoPedidoPort`.

## Estado actual del código

No existe la confirmación del retiro. Dependen de los planes de RF-64 (`LISTO_PARA_RECOGER`), RF-36 (`TransicionarPedidoPort`, `PedidoEntregadoEvent` lo crea RF-37), RF-42 (`ConfirmarPagoPedidoPort`) y RF-53 (el `Pago` pendiente).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Retira y paga; recibe los avisos y luego puede calificar |
| Vendedor (punto fijo) | Confirma el retiro y el cobro |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Confirmar retiro y cobro en < 15 s (SC-001).
- 100 % de los pedidos retirados con pago confirmado y medio correcto (SC-002): pedido y pago cambian en la misma transacción.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Confirmar el retiro de un pedido `LISTO_PARA_RECOGER` | `Pedido.confirmarRetiro()` exige modalidad `RECOGIDA` y estado `LISTO_PARA_RECOGER` → `409 INVALID_STATE` con `estadoActual` | T010, T012, T014, T015 |
| FR-002 | Pedir la confirmación del cobro, por el medio que eligió el cliente | Cuerpo `cobroConfirmado: true` obligatorio; `ConfirmarPagoPedidoPort` conserva `Pedido.medio_pago` | T010, T011, T016 |
| FR-003 | `ENTREGADO` y habilitar calificaciones | Transición y E07 (publicado por `pedidos`); la elegibilidad de RF-01 se deriva del estado | T010, T015 |
| SC-001 | < 15 s | Una llamada; hoja con casilla | T040 |
| SC-002 | 100 % con pago confirmado y medio correcto | Atomicidad pedido + pago | T010, T011, T017 |
| US1-E1 | Retiro y cobro en efectivo | `ENTREGADO` + pago `CONFIRMADO` con `EFECTIVO` + E04 + E07 | T010 |
| US1-E2 | Retiro y cobro por transferencia | Igual con `CUENTA_BANCARIA` | T011 |
| US1-E3 | Pedido que aún no está listo | `409 INVALID_STATE` con el estado actual | T012 |
| Edge 1 | Retiro parcial | No existe en la spec ni en el modelo; no se ofrece (Open Questions) | — |
| Edge 2 | Pedido que el cliente nunca retira | Sin vencimiento ni cancelación (Open Questions de RF-64) | — |

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
| Pedido | Lee / escribe (`estado`) | `RECOGIDA`, `LISTO_PARA_RECOGER` → `ENTREGADO` |
| Pago | Lee / escribe (`estado`, `confirmado_at`) | `PENDIENTE_CONFIRMACION` → `CONFIRMADO`, medio = `Pedido.medio_pago` |
| Emprendimiento | Lee (dueño) | Pertenencia |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Pedido (recogida) | `LISTO_PARA_RECOGER` | `ENTREGADO` | Vendedor de punto fijo confirma | Pedido propio; `cobroConfirmado = true` | E07; habilita calificar |
| Pago (tipo PEDIDO) | `PENDIENTE_CONFIRMACION` | `CONFIRMADO` | La misma confirmación | Pago existente y pendiente | `confirmado_at`; E04 |

**Transiciones inválidas**: pedido en otro estado (incluye `ENTREGADO`) → `409 INVALID_STATE` con `details.estadoActual`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /pedidos/{id}/confirmar-retiro` | VENDEDOR_PUNTO_FIJO | Pedido de su emprendimiento; si no, `404` |

## API Contracts

### POST /api/v1/pedidos/{id}/confirmar-retiro

- **Rol**: vendedor de punto fijo · **Path params**: `id` (UUID) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅, `Content-Type: application/json`.
- **Body**:

```json
{ "cobroConfirmado": true }
```

- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "id": "…", "estado": "ENTREGADO", "modalidad": "RECOGIDA", "updatedAt": "…",
  "pago": { "id": "…", "estado": "CONFIRMADO", "medio": "CUENTA_BANCARIA", "monto": 12000, "confirmadoAt": "…" }
}
```

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key`, falta `cobroConfirmado` o no es `true` | "Confirma que recibiste el pago" |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor de punto fijo | — |
| 404 | `NOT_FOUND` | Pedido inexistente o de otro emprendimiento | "El pedido no está disponible" |
| 409 | `INVALID_STATE` | El pedido no está `LISTO_PARA_RECOGER` (incluye ya `ENTREGADO`) | `details.estadoActual` |
| 422 | `BUSINESS_RULE` | El pedido no es de recogida | "Este pedido no es de recogida" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E04 `PagoPedidoConfirmadoEvent` (`pagos.pago-pedido-confirmado`) | `ConfirmarPagoPedidoUseCase` (`pagos`), llamado desde este caso de uso | Al confirmar el cobro, en la misma transacción |
| E07 `PedidoEntregadoEvent` (`pedidos.pedido-entregado`) | `TransicionarPedidoUseCase.confirmarRetiro` (`pedidos`) | Al pasar a `ENTREGADO`. Payload: `pedidoId`, `compradorId`, `vendedorId`, `domicilioId?` (nulo aquí) |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la acción se escribe en el outbox con su `Idempotency-Key` y la pantalla muestra "pendiente de envío"; al reconectar, si el servidor responde `INVALID_STATE` la app actualiza el detalle y lo explica.
- **Concurrencia (RNF08)**: pedido y pago se bloquean `FOR UPDATE` (siempre en ese orden); dos solicitudes simultáneas: una confirma y la otra recibe `INVALID_STATE` con `ENTREGADO`.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`; el único de `Pago` por pedido es la garantía final.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido aún no listo | `409 INVALID_STATE` con el estado actual | US1-E3 |
| Doble confirmación | `409 INVALID_STATE` (`ENTREGADO`); con la misma clave, repite la respuesta | RNF27 |
| Sin confirmar el cobro | `400 VALIDATION_ERROR`; no cambia nada | FR-002 |
| Falla al confirmar el pago | Se revierte el cambio de estado del pedido | RNF15 |
| Sin conexión | Outbox con reintento | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Pedido.confirmarRetiro` | FR-001 |
| Integración | Endpoint con Testcontainers: efectivo y transferencia (pedido `ENTREGADO`, pago `CONFIRMADO` con el medio del pedido, E04 y E07), estados no válidos, modalidad distinta, ajeno, rol equivocado, cuerpo sin cobro, falla del pago que revierte todo | US1-E1–E3, SC-002 |
| Concurrencia | Dos confirmaciones simultáneas: una gana, un solo pago confirmado, un solo E07 | RNF08 |
| Arquitectura | `recogida` usa `pedidos` y `pagos` solo por sus puertos `in` | RNF13, RNF25 |
| Frontend | Jest: hoja de cobro por medio, casilla obligatoria, error por estado, outbox sin red | SC-001 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-65-confirmacion-de-retiro-del-pedido/
├── RF-65-confirmacion-de-retiro-del-pedido.md   # spec
└── plan.md                                      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/recogida/
├── application/
│   ├── ports/in/                 # ConfirmarRetiroPort
│   ├── ports/out/                # TransicionPedidoPort (RF-64), CobroPedidoPort (hacia `pagos`)
│   └── usecase/                  # ConfirmarRetiroUseCase
└── infrastructure/adapters/
    ├── in/web/                   # RecogidaController (POST /pedidos/{id}/confirmar-retiro), DTO con cobroConfirmado
    └── out/persistence/          # adaptadores sobre los puertos `in` de pedidos y pagos

backend/src/main/java/co/edu/napa/pedidos/
├── domain/model/                 # Pedido.confirmarRetiro
└── application/ports/in/         # TransicionarPedidoPort.confirmarRetiro (se añade al de RF-36)
backend/src/test/java/co/edu/napa/{recogida,pedidos}/

frontend/src/components/recogida/AccionesRecogida.tsx   # se añade "Confirmar retiro" (RF-64 trae "Marcar como listo")
frontend/src/components/pagos/ConfirmarCobroSheet.tsx   # (RF-42) hoja compartida: efectivo o transferencia
frontend/src/services/recogida.ts                       # confirmarRetiro() por el outbox
frontend/__tests__/recogida/confirmar-retiro.test.tsx
```

**Structure Decision**: igual que RF-37: el caso de uso vive en `recogida` y orquesta, en una transacción, la transición del pedido (`pedidos`) y el cobro (`pagos`); E07 lo publica `pedidos` al transicionar y `recogida` no publica eventos en esta feature. `PedidoEntregadoEvent` lo crea RF-37 en `pedidos/domain/event`; `CobroPedidoPort` (puerto `out` hacia `pagos`) se define de nuevo en este módulo para no depender de `entregas`.

## Phase 1: Setup

- [ ] T001 Confirmar `TransicionarPedidoPort` (RF-36, T004), `ConfirmarPagoPedidoPort` (RF-42, T021), `PedidoEntregadoEvent` (RF-37, T003) y el estado `LISTO_PARA_RECOGER` (RF-64)

## Phase 2: Foundational

- [ ] T002 `CobroPedidoPort` en `recogida/application/ports/out/` y su adaptador sobre `ConfirmarPagoPedidoPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Vendedor cierra la recogida y cobra (Priority: P1)

**Goal**: la recogida queda cerrada y cobrada con el medio que eligió el cliente.
**Independent Test**: con un pedido `LISTO_PARA_RECOGER`, confirmar el retiro y el cobro en efectivo y por transferencia.
**Cubre**: FR-001–FR-003, SC-001–SC-002, US1-E1–E3, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /pedidos/{id}/confirmar-retiro` con pedido en efectivo (`ENTREGADO`, pago `CONFIRMADO` con `EFECTIVO`, E04 y E07 en el outbox) en `backend/src/test/java/co/edu/napa/recogida/ConfirmarRetiroEfectivoIT.java`
- [ ] T011 [P] [US1] Test con pedido por transferencia (pago `CONFIRMADO` con `CUENTA_BANCARIA`) y de cuerpo sin `cobroConfirmado` o en `false` (`400`, nada cambia) en `.../recogida/ConfirmarRetiroTransferenciaIT.java`
- [ ] T012 [P] [US1] Test de estados no válidos (`PENDIENTE`, `ACEPTADO`, `ENTREGADO`, `RECHAZADO`) con `INVALID_STATE` y `estadoActual`, y de modalidad distinta (`422`) en `.../recogida/ConfirmarRetiroEstadosIT.java`
- [ ] T013 [P] [US1] Test unitario de `Pedido.confirmarRetiro` en `backend/src/test/java/co/edu/napa/pedidos/PedidoConfirmarRetiroTest.java`
- [ ] T017 [P] [US1] Test de atomicidad (si falla el pago, el pedido sigue `LISTO_PARA_RECOGER` y no hay eventos), de dos solicitudes simultáneas y de repetición con la misma clave en `.../recogida/ConfirmarRetiroAtomicidadIT.java`
- [ ] T018 [P] [US1] Test de pedido ajeno (`404`), rol equivocado (`403`) y falta de `Idempotency-Key` (`400`) en `.../recogida/ConfirmarRetiroAutorizacionIT.java`
- [ ] T022 [P] [US1] Test de la pantalla (hoja de cobro según el medio, casilla obligatoria, error por estado, outbox sin red) en `frontend/__tests__/recogida/confirmar-retiro.test.tsx`

### Implementation

- [ ] T014 [US1] `Pedido.confirmarRetiro()` en `pedidos/domain/model/Pedido.java`
- [ ] T015 [US1] `TransicionarPedidoPort.confirmarRetiro` y su caso de uso (bloqueo, dueño, modalidad, estado y publicación de E07) en `pedidos/application/`
- [ ] T016 [US1] `ConfirmarRetiroUseCase` (transición y cobro por `CobroPedidoPort`) y `POST /pedidos/{id}/confirmar-retiro` con su DTO en `recogida/`
- [ ] T020 [US1] Servicio `confirmarRetiro()` por el outbox en `frontend/src/services/recogida.ts`
- [ ] T021 [US1] Botón "Confirmar retiro" y uso de `ConfirmarCobroSheet` en `frontend/src/components/recogida/AccionesRecogida.tsx` (visible en recogida `LISTO_PARA_RECOGER`) e integración en `frontend/src/app/(vendedor)/pedidos/[id].tsx`

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir la confirmación en < 15 s (SC-001)
- [ ] T041 Revisar permisos y que ningún evento lleve datos personales (RNF03, RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-64, RF-36, RF-42, RF-53 y RF-51; habilita RF-01.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: retiro parcial; el modelo no tiene entregas parciales | Alcance | No se ofrece; se resuelve fuera de la plataforma | Equipo |
| Edge 2: pedido que nunca se retira y devolución al inventario | Reglas de negocio | Igual que RF-64: sin vencimiento ni cancelación | Equipo |
| ~~El cliente recibe E04 y E07 al mismo tiempo~~ | UX de RF-38 | **Resuelto**: en recogida solo se envía el aviso de entregado (E07); el de pago (E04) solo en domicilio (ver RF-42) | Equipo |
| Monto distinto al total | Datos | No se captura; el vendedor confirma solo si recibió el total (igual que RF-42 y RF-37) | Equipo |

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
# Implementation Plan: Recogida lista para retirar

**Date**: 2026-10-21
**Spec**: [RF-64-recogida-lista-para-recoger.md](./RF-64-recogida-lista-para-recoger.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md) (E06)
**Requerimiento funcional**: RF-64
**Historias de usuario**: HU-97
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-53 (pedido `ACEPTADO`), RF-36 (`TransicionarPedidoPort`, `VentanaReserva` y `reserva.margen_minutos`), RF-45 (`reserva`), RF-51 (donde vive el botón), RF-10
**Habilita a**: RF-65 (solo se confirma el retiro de un pedido `LISTO_PARA_RECOGER`), RF-48 (el cliente ve el estado)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor de punto fijo marca un pedido de **recogida** aceptado como listo con `POST /pedidos/{id}/marcar-listo`: el pedido pasa de `ACEPTADO` a `LISTO_PARA_RECOGER` y el cliente recibe el aviso (E06). En una reserva solo se permite desde 15 minutos antes de la fecha y hora acordadas; antes responde `409 RESERVATION_NOT_DUE` con la hora desde la que se puede. Es la contraparte de RF-36 para la recogida: reutiliza `TransicionarPedidoPort` y `VentanaReserva`, y vive en el módulo `recogida`.

## Estado actual del código

No existe `recogida` ni la transición. `TransicionarPedidoPort`, `VentanaReserva` y el parámetro `reserva.margen_minutos` los crea el plan de RF-36.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Recibe el aviso de que su pedido está listo (E06) |
| Vendedor (punto fijo) | Marca el pedido como listo |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Notificación al cliente en < 1 minuto (SC-001): E06 por el outbox.
- 100 % de los pedidos listos estuvieron previamente aceptados (SC-002): transición del dominio con bloqueo.
- Mismo margen de 15 minutos que RF-36 (`reserva.margen_minutos`), sin duplicar la regla.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El punto fijo marca como listo un pedido `ACEPTADO` de recogida, inmediato o reserva | `POST /pedidos/{id}/marcar-listo`; `Pedido.marcarListoParaRecoger()` | T010, T011, T014, T015 |
| FR-002 | Notificar al cliente | E06 `PedidoListoParaRecogerEvent` por `EventPublisherPort` en la misma transacción (el listener está en el plan de RF-38) | T010, T015 |
| FR-003 | Impedir marcar un pedido que no sea de recogida o no esté aceptado | Modalidad distinta → `422 BUSINESS_RULE`; estado distinto de `ACEPTADO` → `409 INVALID_STATE` | T012, T013 |
| FR-004 | En una reserva, solo desde 15 minutos antes | `VentanaReserva` (RF-36); `409 RESERVATION_NOT_DUE` con `details.disponibleDesde` | T011, T017 |
| SC-001 | < 1 min | E06 + relay | T040 |
| SC-002 | 100 % previamente aceptados | Estado verificado con bloqueo | T012 |
| US1-E1 | Pedido marcado como listo | `200`, `LISTO_PARA_RECOGER`, E06 | T010 |
| US1-E2 | Pedido no aceptado | `409 INVALID_STATE` con el estado actual ("debe estar aceptado") | T012 |
| US1-E3 | Pedido de otra modalidad | La app no ofrece la acción; si se llama a la API, `422 BUSINESS_RULE` | T013, T021 |
| US1-E4 | Reserva dentro de la ventana | `200` con `reserva − 15 min ≤ ahora` | T011 |
| US1-E5 | Reserva antes de tiempo | `409 RESERVATION_NOT_DUE` con la hora desde la que puede marcarse | T017 |
| Edge 1 | El cliente no llega a retirar | No hay vencimiento (foundations): sigue `LISTO_PARA_RECOGER` (Open Questions) | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Rol y pertenencia validados en el caso de uso |
| RNF08 Concurrencia | ☑ | Pedido bloqueado `FOR UPDATE` |
| RNF15 Fiabilidad | ☑ | Cambio de estado y E06 en la misma transacción |
| RNF16 Trazabilidad | ☑ | `updated_at` |
| RNF18 Notificaciones | ☑ | E06 |
| RNF23 Consistencia | ☑ | Estados y modalidad por dominio |
| RNF24 Errores claros | ☑ | `RESERVATION_NOT_DUE` con la hora; `INVALID_STATE` con el estado |
| RNF27 Integridad | ☑ | `Idempotency-Key` |
| RNF28 Conectividad | ☑ | Acción por el outbox del dispositivo |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Lee / escribe (`estado`) | `RECOGIDA`, `ACEPTADO` → `LISTO_PARA_RECOGER`; `reserva` nula o dentro de la ventana |
| Emprendimiento | Lee (dueño) | Pertenencia |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Pedido (recogida) | `ACEPTADO` | `LISTO_PARA_RECOGER` | Vendedor de punto fijo marca listo | Pedido propio; si es reserva, `ahora ≥ reserva − margen` | E06 |

**Transiciones inválidas**: estado distinto de `ACEPTADO` → `409 INVALID_STATE` con `details.estadoActual`; antes de la ventana → `409 RESERVATION_NOT_DUE`; modalidad distinta → `422 BUSINESS_RULE`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /pedidos/{id}/marcar-listo` | VENDEDOR_PUNTO_FIJO | Pedido de su emprendimiento; si no, `404` |

## API Contracts

### POST /api/v1/pedidos/{id}/marcar-listo

- **Rol**: vendedor de punto fijo · **Path params**: `id` (UUID) · **Query params**: ninguno · **Body**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body `{ "id": "…", "estado": "LISTO_PARA_RECOGER", "modalidad": "RECOGIDA", "reserva": null, "updatedAt": "…" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` | — |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor de punto fijo | — |
| 404 | `NOT_FOUND` | Pedido inexistente o de otro emprendimiento | "El pedido no está disponible" |
| 409 | `INVALID_STATE` | El pedido no está `ACEPTADO` (incluye ya `LISTO_PARA_RECOGER`) | `details.estadoActual`; "El pedido debe estar aceptado" |
| 409 | `RESERVATION_NOT_DUE` | Reserva con más de 15 minutos de anticipación | `details.disponibleDesde` (ISO-8601 UTC) |
| 422 | `BUSINESS_RULE` | El pedido no es de recogida | "Este pedido no es de recogida" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

### Puerto `in` de `pedidos` (no es endpoint)

`TransicionarPedidoPort.marcarListoParaRecoger(pedidoId, vendedorId)` (se añade al puerto de RF-36): bloquea el pedido, valida dueño, modalidad, estado y ventana de reserva, lo pasa a `LISTO_PARA_RECOGER` y devuelve `PedidoTransicionado`.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E06 `PedidoListoParaRecogerEvent` (`pedidos.pedido-listo-para-recoger`) | `TransicionarPedidoUseCase.marcarListoParaRecoger` (`pedidos`) | Al pasar a `LISTO_PARA_RECOGER`, en la misma transacción. Payload: `pedidoId`, `compradorId` |

**Eventos que consume esta feature**: ninguno. El aviso al cliente es un listener del plan de RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la acción se escribe en el outbox con su `Idempotency-Key` y la pantalla muestra "pendiente de envío"; al reconectar, los errores `RESERVATION_NOT_DUE` o `INVALID_STATE` se explican y se refresca el detalle.
- **Concurrencia (RNF08)**: el pedido se bloquea `FOR UPDATE`; dos solicitudes simultáneas: una transiciona y la otra recibe `INVALID_STATE` con `LISTO_PARA_RECOGER`.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido pendiente o rechazado | `409 INVALID_STATE` "debe estar aceptado" | US1-E2 |
| Pedido de otra modalidad | La app no muestra la acción; la API responde `422` | US1-E3 |
| Reserva antes de tiempo | `409 RESERVATION_NOT_DUE` con la hora | US1-E5 |
| Pedido ajeno | `404` | RNF03 |
| Sin conexión | Outbox con reintento | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Pedido.marcarListoParaRecoger`; reutiliza `VentanaReserva` (RF-36) | FR-003, FR-004 |
| Integración | Endpoint con Testcontainers: inmediato, reserva en la ventana, reserva anticipada, estados no válidos, modalidad distinta, ajeno, rol equivocado, E06 en el outbox | US1-E1–E5 |
| Concurrencia | Dos solicitudes simultáneas del mismo pedido | RNF08 |
| Arquitectura | `recogida` usa `pedidos` solo por sus puertos `in` y `domain/event` | RNF13, RNF25 |
| Frontend | Jest: la acción solo aparece en recogida `ACEPTADO`, mensaje con la hora, errores, outbox sin red | US1-E3, SC-001 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-64-recogida-lista-para-recoger/
├── RF-64-recogida-lista-para-recoger.md   # spec
└── plan.md                                # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/recogida/
├── application/
│   ├── ports/in/                 # MarcarListoParaRecogerPort
│   ├── ports/out/                # TransicionPedidoPort (hacia `pedidos`)
│   └── usecase/                  # MarcarListoParaRecogerUseCase
└── infrastructure/adapters/
    ├── in/web/                   # RecogidaController (POST /pedidos/{id}/marcar-listo)
    └── out/persistence/          # adaptador sobre TransicionarPedidoPort

backend/src/main/java/co/edu/napa/pedidos/
├── domain/event/                 # PedidoListoParaRecogerEvent (E06)
├── domain/model/                 # Pedido.marcarListoParaRecoger
└── application/ports/in/         # TransicionarPedidoPort.marcarListoParaRecoger (se añade al de RF-36)
backend/src/test/java/co/edu/napa/{recogida,pedidos}/

frontend/src/components/recogida/AccionesRecogida.tsx   # botones "Marcar como listo" y "Confirmar retiro" (RF-65)
frontend/src/services/recogida.ts                       # marcarListo() por el outbox
frontend/__tests__/recogida/marcar-listo.test.tsx
```

**Structure Decision**: `recogida` es el módulo de la recogida en punto fijo (RF-64 y RF-65). El caso de uso orquesta, pero E06 lo publica `pedidos` al transicionar (el evento es un hecho del pedido); la regla de la transición y de la ventana de reserva vive en `pedidos`, dueño de la máquina de estados. La clase `PedidoListoParaRecogerEvent` está en `pedidos/domain/event` según el catálogo.

## Phase 1: Setup

- [ ] T001 Crear el paquete `co.edu.napa.recogida` y su regla ArchUnit en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`; confirmar `TransicionarPedidoPort`, `VentanaReserva` y `reserva.margen_minutos` (RF-36, T002 y T004)

## Phase 2: Foundational

- [ ] T002 `PedidoListoParaRecogerEvent` (E06) en `pedidos/domain/event/`
- [ ] T003 `TransicionPedidoPort` en `recogida/application/ports/out/` y su adaptador sobre `TransicionarPedidoPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Vendedor avisa que el pedido está listo (Priority: P1)

**Goal**: el cliente sabe cuándo pasar por su pedido.
**Independent Test**: marcar como listo un pedido de recogida aceptado (inmediato y reserva) y ver `LISTO_PARA_RECOGER` y E06.
**Cubre**: FR-001–FR-004, SC-001–SC-002, US1-E1–E5, Edge 1.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /pedidos/{id}/marcar-listo` con recogida inmediata (`LISTO_PARA_RECOGER`, E06 en el outbox) en `backend/src/test/java/co/edu/napa/recogida/MarcarListoIT.java`
- [ ] T011 [P] [US1] Test de reserva dentro de la ventana (límite exacto `reserva − 15 min`) en `.../recogida/MarcarListoReservaIT.java`
- [ ] T012 [P] [US1] Test de estados no válidos (`PENDIENTE`, `RECHAZADO`, `LISTO_PARA_RECOGER`, `ENTREGADO`) con `INVALID_STATE` y `estadoActual` en `.../recogida/MarcarListoEstadosIT.java`
- [ ] T013 [P] [US1] Test de modalidad distinta (entrega directa y domicilio → `422 BUSINESS_RULE`), pedido ajeno (`404`) y rol equivocado (`403`) en `.../recogida/MarcarListoAutorizacionIT.java`
- [ ] T017 [P] [US1] Test de reserva anticipada (`RESERVATION_NOT_DUE` con `disponibleDesde`, sin cambio de estado ni E06) en `.../recogida/MarcarListoAnticipadoIT.java`
- [ ] T018 [P] [US1] Test de dos solicitudes simultáneas y de repetición con la misma `Idempotency-Key` en `.../recogida/MarcarListoConcurrenciaIT.java`
- [ ] T022 [P] [US1] Test de `AccionesRecogida` (solo aparece en recogida `ACEPTADO`, mensaje con la hora, errores, outbox sin red) en `frontend/__tests__/recogida/marcar-listo.test.tsx`

### Implementation

- [ ] T014 [US1] `Pedido.marcarListoParaRecoger(ahora, margen)` en `pedidos/domain/model/Pedido.java`
- [ ] T015 [US1] `TransicionarPedidoPort.marcarListoParaRecoger` y su caso de uso (bloqueo, dueño, modalidad, estado, ventana y publicación de E06) en `pedidos/application/`
- [ ] T016 [US1] `MarcarListoParaRecogerUseCase` y `POST /pedidos/{id}/marcar-listo` en `recogida/`
- [ ] T020 [US1] Servicio `marcarListo()` por el outbox en `frontend/src/services/recogida.ts`
- [ ] T021 [US1] `AccionesRecogida` (botón "Marcar como listo") en `frontend/src/components/recogida/` e integración en `frontend/src/app/(vendedor)/pedidos/[id].tsx` (solo recogida `ACEPTADO`)

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir el aviso al cliente en < 1 min (SC-001)
- [ ] T041 Revisar permisos (RNF03)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-36 (puerto y ventana de reserva), RF-53 y RF-51; RF-65 confirma desde `LISTO_PARA_RECOGER`.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: el cliente no llega a retirar; la spec pregunta si vence | Regla | Sin vencimiento (foundations): sigue `LISTO_PARA_RECOGER` | Equipo |
| No existe ninguna acción para cancelar un pedido de recogida que nunca se retira; el inventario descontado al aceptar un pedido inmediato no vuelve | Reglas de negocio e inventario | No se ofrece; el vendedor ajusta su inventario (RF-57) | Equipo |
| ~~Quién emite E06~~ | Estructura de eventos | **Resuelto**: lo publica `pedidos` al transicionar (ver RF-36) | Equipo |
| ¿La app muestra el botón antes de la ventana de la reserva? | UX | Sí, y el servidor responde con la hora (igual que RF-36) | Equipo |

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
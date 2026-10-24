# Implementation Plan: Inicio de entrega directa

**Date**: 2026-10-21
**Spec**: [RF-36-inicio-de-entrega-directa.md](./RF-36-inicio-de-entrega-directa.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md) (E05)
**Requerimiento funcional**: RF-36
**Historias de usuario**: HU-61
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-53 (pedido `ACEPTADO`), RF-45 (`reserva`), RF-35 (el vendedor ya sabe adónde ir), RF-51 (donde vive el botón), RF-10
**Habilita a**: RF-37 (solo se confirma un pedido `EN_CAMINO`), RF-48 (el cliente ve el estado)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor ambulante pasa un pedido de entrega directa de `ACEPTADO` a `EN_CAMINO` con `POST /pedidos/{id}/iniciar-entrega` y el cliente recibe el aviso (E05). Si el pedido es una reserva, solo puede iniciarse desde **15 minutos antes** de la fecha y hora acordadas (`reserva.margen_minutos`); antes responde `409 RESERVATION_NOT_DUE` con la hora desde la que se puede. La máquina de estados es de `pedidos`: `entregas` orquesta el caso de uso y pide la transición por el puerto `in` `TransicionarPedidoPort`, que este plan crea y que RF-37, RF-64 y RF-65 amplían.

## Estado actual del código

No existe `entregas` (lo crea RF-35) ni la transición. `Pedido` lo crea RF-45 y RF-53 lo lleva a `ACEPTADO`. No existe el parámetro `reserva.margen_minutos` en `application.yml`.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Recibe el aviso de que van en camino (E05) |
| Vendedor (ambulante) | Inicia la entrega |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Notificación al cliente en < 1 minuto (SC-001): E05 por el outbox (commit → handler ≤ 1 s p95, foundations).
- 100 % de los pedidos `EN_CAMINO` estuvieron `ACEPTADO` (SC-002): la transición es del dominio y se bloquea el pedido.
- 0 % de reservas iniciadas antes de `reserva − 15 min` (SC-003): regla de dominio con `Clock` inyectable.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El ambulante inicia la entrega de un pedido `ACEPTADO` | `POST /pedidos/{id}/iniciar-entrega`; `Pedido.iniciarEntrega()` exige modalidad `ENTREGA_DIRECTA` y estado `ACEPTADO` | T010, T013, T015, T016 |
| FR-002 | Una reserva solo desde 15 minutos antes | `VentanaReserva` (pura) con `reserva.margen_minutos`; si no ha llegado, `409 RESERVATION_NOT_DUE` con `details.disponibleDesde` | T011, T012, T014 |
| FR-003 | Cambiar a `EN_CAMINO` y notificar al cliente | `Pedido.iniciarEntrega()`; E05 por `EventPublisherPort` en la misma transacción (el listener está en el plan de RF-38) | T010, T017 |
| SC-001 | < 1 min | E05 + relay | T040 |
| SC-002 | 100 % previamente aceptados | Estado verificado con bloqueo `FOR UPDATE` | T013 |
| SC-003 | 0 % antes de tiempo | Prueba de los límites de la ventana | T011, T012 |
| US1-E1 | Inicio de entrega inmediata | `200`, `EN_CAMINO`, E05 | T010 |
| US1-E2 | Inicio de una reserva dentro de la ventana | `200` con `reserva − 15 min ≤ ahora` | T011 |
| US1-E3 | Inicio anticipado | `409 RESERVATION_NOT_DUE` con la hora desde la que puede iniciarse | T012 |
| Edge 1 | Varios pedidos aceptados iniciados a la vez | Cada uno es independiente; no hay límite en la spec (Open Questions) | T018 |
| Edge 2 | Revertir el inicio | No existe la acción: el estado no retrocede y el endpoint repetido responde `INVALID_STATE` | T013, T019 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Rol y pertenencia validados en el caso de uso |
| RNF08 Concurrencia | ☑ | Pedido bloqueado `FOR UPDATE` al transicionar |
| RNF15 Fiabilidad | ☑ | Cambio de estado y E05 en la misma transacción |
| RNF16 Trazabilidad | ☑ | `updated_at` del pedido |
| RNF18 Notificaciones | ☑ | E05 |
| RNF23 Consistencia | ☑ | Estados y modalidad por dominio |
| RNF24 Errores claros | ☑ | `RESERVATION_NOT_DUE` con la hora; `INVALID_STATE` con el estado |
| RNF27 Integridad | ☑ | `Idempotency-Key` |
| RNF28 Conectividad | ☑ | Acción por el outbox del dispositivo |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Lee / escribe (`estado`) | `ENTREGA_DIRECTA`, `ACEPTADO` → `EN_CAMINO`; `reserva` nula o dentro de la ventana |
| Emprendimiento | Lee (dueño) | Pertenencia |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Pedido (entrega directa) | `ACEPTADO` | `EN_CAMINO` | Vendedor ambulante inicia | Pedido propio; si es reserva, `ahora ≥ reserva − margen` | E05 |

**Transiciones inválidas**: cualquier estado distinto de `ACEPTADO` → `409 INVALID_STATE` con `details.estadoActual`; antes de la ventana → `409 RESERVATION_NOT_DUE`; modalidad distinta → `422 BUSINESS_RULE`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /pedidos/{id}/iniciar-entrega` | VENDEDOR_AMBULANTE | Pedido de su emprendimiento; si no, `404` |

## API Contracts

### POST /api/v1/pedidos/{id}/iniciar-entrega

- **Rol**: vendedor ambulante · **Path params**: `id` (UUID) · **Query params**: ninguno · **Body**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body `{ "id": "…", "estado": "EN_CAMINO", "modalidad": "ENTREGA_DIRECTA", "reserva": null, "updatedAt": "…" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` | — |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor ambulante | — |
| 404 | `NOT_FOUND` | Pedido inexistente o de otro emprendimiento | "El pedido no está disponible" |
| 409 | `INVALID_STATE` | El pedido no está `ACEPTADO` (incluye ya `EN_CAMINO`) | `details.estadoActual` |
| 409 | `RESERVATION_NOT_DUE` | Reserva con más de 15 minutos de anticipación | `details.disponibleDesde` (ISO-8601 UTC); "Podrás iniciar la entrega desde las HH:mm" |
| 422 | `BUSINESS_RULE` | El pedido no es de entrega directa | "Este pedido no es de entrega directa" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

### Puerto `in` de `pedidos` (no es endpoint)

`TransicionarPedidoPort.iniciarEntregaDirecta(pedidoId, vendedorId)`: bloquea el pedido, valida dueño, modalidad, estado y ventana de reserva, lo pasa a `EN_CAMINO` y devuelve `PedidoTransicionado(pedidoId, compradorId, vendedorId, estado, reserva)`. RF-37, RF-64 y RF-65 añaden sus métodos al mismo puerto. Cada método publica el evento de `pedidos` que le corresponde (E06 en `marcarListoParaRecoger`, E07 en `confirmarEntregaDirecta` y `confirmarRetiro`); E05 lo publica `entregas` (ver Structure Decision).

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E05 `EntregaDirectaIniciadaEvent` (`entregas.entrega-directa-iniciada`) | `IniciarEntregaDirectaUseCase` (`entregas`) | Al pasar a `EN_CAMINO`, en la misma transacción. Payload: `pedidoId`, `compradorId` |

**Eventos que consume esta feature**: ninguno. El aviso al cliente es un listener del plan de RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: "Iniciar entrega" se escribe en el outbox con su `Idempotency-Key` y la pantalla muestra "pendiente de envío"; al reconectar, si hay `RESERVATION_NOT_DUE` o `INVALID_STATE` la app lo explica y refresca el detalle.
- **Concurrencia (RNF08)**: el pedido se bloquea `FOR UPDATE`; dos solicitudes simultáneas: una pasa a `EN_CAMINO` y la otra recibe `INVALID_STATE` con `estadoActual = EN_CAMINO` (o el mismo resultado repetido si llevan la misma clave).
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Reserva antes de tiempo | `409 RESERVATION_NOT_DUE` con la hora desde la que se puede | US1-E3 |
| Pedido no aceptado o ya en camino | `409 INVALID_STATE` con `estadoActual` | FR-001 |
| Pedido de otra modalidad | `422 BUSINESS_RULE` | FR-001 |
| Pedido ajeno | `404` | RNF03 |
| Sin conexión | Outbox con reintento | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `VentanaReserva` (límites exactos: `reserva − 15 min` y un segundo antes), `Pedido.iniciarEntrega` | FR-002, SC-003 |
| Integración | Endpoint con Testcontainers: inmediato, reserva en la ventana, reserva anticipada, estados no válidos, modalidad distinta, ajeno, rol equivocado, E05 en el outbox | US1-E1–E3, FR-001 |
| Concurrencia | Dos inicios simultáneos del mismo pedido | RNF08 |
| Arquitectura | `entregas` usa `pedidos` solo por sus puertos `in` y `domain/event` | RNF13, RNF25 |
| Frontend | Jest: botón, mensaje con la hora, errores, outbox sin red | SC-001 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-36-inicio-de-entrega-directa/
├── RF-36-inicio-de-entrega-directa.md   # spec
└── plan.md                              # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/entregas/
├── domain/event/                 # EntregaDirectaIniciadaEvent (E05)
├── application/
│   ├── ports/in/                 # IniciarEntregaDirectaPort
│   ├── ports/out/                # TransicionPedidoPort (hacia `pedidos`), EventPublisherPort (shared)
│   └── usecase/                  # IniciarEntregaDirectaUseCase
└── infrastructure/adapters/
    ├── in/web/                   # EntregaDirectaController (POST /pedidos/{id}/iniciar-entrega)
    └── out/persistence/          # adaptador sobre TransicionarPedidoPort

backend/src/main/java/co/edu/napa/pedidos/
├── domain/model/                 # Pedido.iniciarEntrega, VentanaReserva
├── domain/exception/             # ReservaNoVigenteException (RESERVATION_NOT_DUE)
├── application/ports/in/         # TransicionarPedidoPort
├── application/usecase/          # TransicionarPedidoUseCase
└── infrastructure/config/        # ReservaProperties (reserva.margen_minutos)
backend/src/main/resources/application.yml   # reserva.margen_minutos: 15
backend/src/test/java/co/edu/napa/{entregas,pedidos}/

frontend/src/components/entregas/AccionesEntregaDirecta.tsx   # botones "Iniciar entrega" y "Confirmar entrega" (RF-37)
frontend/src/services/entregas.ts                             # iniciarEntrega() por el outbox
frontend/src/domain/errores-pedido.ts                         # mensaje de RESERVATION_NOT_DUE con la hora local
frontend/__tests__/entregas/iniciar-entrega.test.tsx
```

**Structure Decision**: el caso de uso vive en `entregas` y emite E05 (catálogo: "RF-36"; es un evento del módulo `entregas`). Los eventos E06 y E07, que son hechos del pedido, los publica `pedidos` al transicionar (decisión cerrada). La transición y sus reglas viven en `pedidos`, dueño de la máquina de estados, detrás de `TransicionarPedidoPort`; la ventana de reserva es una regla pura del dominio (sin `domain/service`) que también usarán RF-64 y RF-14. El margen se lee de `reserva.margen_minutos`, que ya define spec-general.

## Phase 1: Setup

- [ ] T001 Confirmar `entregas` (RF-35, T001) y el estado `ACEPTADO` de RF-53
- [ ] T002 Parámetro `reserva.margen_minutos: 15` y `ReservaProperties` en `backend/src/main/resources/application.yml` y `pedidos/infrastructure/config/`

## Phase 2: Foundational

- [ ] T003 `PedidoRepositoryPort.bloquearPorId` (ya definido en RF-53, T004) y `Clock` inyectable: confirmar su uso aquí
- [ ] T004 `TransicionarPedidoPort` y su caso de uso base en `pedidos/application/` (solo la estructura; este plan añade `iniciarEntregaDirecta`)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Vendedor avisa que va en camino (Priority: P1)

**Goal**: el cliente sabe que su pedido va en camino y el estado avanza.
**Independent Test**: iniciar la entrega de un pedido aceptado y ver `EN_CAMINO` y E05.
**Cubre**: FR-001–FR-003, SC-001–SC-003, US1-E1–E3, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /pedidos/{id}/iniciar-entrega` con pedido inmediato (`EN_CAMINO`, E05 en el outbox) en `backend/src/test/java/co/edu/napa/entregas/IniciarEntregaIT.java`
- [ ] T011 [P] [US1] Test de reserva dentro de la ventana (límite exacto `reserva − 15 min`) y unitario de `VentanaReserva` en `.../entregas/IniciarEntregaReservaIT.java` y `backend/src/test/java/co/edu/napa/pedidos/VentanaReservaTest.java`
- [ ] T012 [P] [US1] Test de reserva anticipada (`RESERVATION_NOT_DUE` con `disponibleDesde`, sin cambio de estado ni E05) en `.../entregas/IniciarEntregaAnticipadaIT.java`
- [ ] T013 [P] [US1] Test de estados no válidos (`PENDIENTE`, `RECHAZADO`, `EN_CAMINO`, `ENTREGADO`) con `INVALID_STATE` y `estadoActual`, y de modalidad distinta (`422`) en `.../entregas/IniciarEntregaEstadosIT.java`
- [ ] T018 [P] [US1] Test de varios pedidos iniciados seguidos por el mismo vendedor y de dos solicitudes simultáneas del mismo pedido en `.../entregas/IniciarEntregaConcurrenciaIT.java`
- [ ] T019 [P] [US1] Test de pedido ajeno (`404`), rol equivocado (`403`), falta de `Idempotency-Key` (`400`) y repetición con la misma clave en `.../entregas/IniciarEntregaAutorizacionIT.java`
- [ ] T022 [P] [US1] Test de `AccionesEntregaDirecta` (botón, mensaje con la hora local, errores, outbox sin red) en `frontend/__tests__/entregas/iniciar-entrega.test.tsx`

### Implementation

- [ ] T014 [US1] `VentanaReserva` y `ReservaNoVigenteException` en `pedidos/domain/` (usa `Clock` y el margen)
- [ ] T015 [US1] `Pedido.iniciarEntrega(ahora, margen)` en `pedidos/domain/model/Pedido.java`
- [ ] T016 [US1] `TransicionarPedidoPort.iniciarEntregaDirecta` y su caso de uso en `pedidos/application/`
- [ ] T017 [US1] `EntregaDirectaIniciadaEvent` (E05), `IniciarEntregaDirectaUseCase` y `POST /pedidos/{id}/iniciar-entrega` en `entregas/`
- [ ] T020 [US1] Servicio `iniciarEntrega()` por el outbox en `frontend/src/services/entregas.ts` y mensaje de `RESERVATION_NOT_DUE` en `frontend/src/domain/errores-pedido.ts`
- [ ] T021 [US1] `AccionesEntregaDirecta` (botón "Iniciar entrega") en `frontend/src/components/entregas/` e integración en `frontend/src/app/(vendedor)/pedidos/[id].tsx` (visible en entrega directa `ACEPTADO`)

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir el aviso al cliente en < 1 min (SC-001)
- [ ] T041 Revisar permisos y la zona horaria de los mensajes (America/Bogota solo en la app; el servidor usa UTC)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-53, RF-45 y RF-51; RF-37 confirma desde `EN_CAMINO`.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: varios pedidos iniciados a la vez; no hay límite en la spec | Regla | Sin límite; cada pedido es independiente | Equipo |
| Edge 2: revertir el inicio; la spec no lo define y no hay transición `EN_CAMINO` → `ACEPTADO` | Alcance | No se ofrece | Equipo |
| Hora de la reserva en el mensaje de `RESERVATION_NOT_DUE` | UX | El servidor devuelve UTC en `disponibleDesde`; la app la muestra en hora local | Equipo |
| ~~Quién emite E05, E06 y E07~~ | Estructura de eventos | **Resuelto**: E05 lo publica `entregas` (RF-36); E06 y E07 los publica `pedidos` al transicionar el pedido. Hay que actualizar la columna "Lo emite" de E06 y E07 en `eventos.md` | Equipo |
| ¿El botón se deshabilita antes de la ventana de la reserva? Requeriría que el detalle (RF-51) exponga `disponibleDesde` | UX | No: el botón queda habilitado y el servidor responde con la hora | Equipo |

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
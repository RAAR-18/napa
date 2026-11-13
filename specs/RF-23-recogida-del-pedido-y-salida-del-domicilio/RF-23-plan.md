# Implementation Plan: Recogida del pedido y salida del domicilio

**Date**: 2026-10-25
**Spec**: [RF-23-recogida-del-pedido-y-salida-del-domiciliario.md](./RF-23-recogida-del-pedido-y-salida-del-domiciliario.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md) (E10, E11)
**Requerimiento funcional**: RF-23
**Historias de usuario**: HU-46, HU-35
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-19 (domicilio asignado), RF-14, RF-10
**Habilita a**: RF-24, RF-26

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El traspaso del pedido tiene dos pasos con dos actores. El domiciliario asignado confirma que recogió el pedido con `POST /domicilios/{id}/confirmar-recogida` (`ASIGNADO` → `RECOGIDO`, avisa al vendedor, E10). Luego el vendedor de punto fijo dueño del pedido confirma la salida con `POST /domicilios/{id}/confirmar-salida` (`RECOGIDO` → `EN_CAMINO`, avisa al cliente, E11). Cada cambio es una actualización condicional por estado, inserta su evento de trazabilidad con su autor en la misma transacción y el estado del pedido no cambia (sigue `ACEPTADO` mientras el domicilio avanza). Fuera de secuencia responde `409 INVALID_STATE` con el estado actual.

## Estado actual del código

El domicilio `ASIGNADO` lo produce RF-19 y la tabla `evento_trazabilidad` la crea RF-14. No existen las transiciones ni las acciones en las pantallas del domiciliario y del vendedor.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Recibe el aviso de "en camino" (E11) |
| Vendedor (punto fijo) | Recibe el aviso de recogida (E10) y confirma la salida |
| Domiciliario | Confirma la recogida |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- 100 % de los cambios con su evento de trazabilidad (SC-001): se insertan en la misma transacción.
- Aviso "en camino" al cliente en < 1 min (SC-002): E11 por el outbox.
- 100 % de las confirmaciones fuera de secuencia rechazadas (SC-003): transición condicional por estado.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El domiciliario asignado confirma la recogida solo desde `ASIGNADO` | `POST /domicilios/{id}/confirmar-recogida`; `Domicilio.confirmarRecogida()` | T010, T013, T014 |
| FR-002 | `RECOGIDO` y notificar al vendedor | Transición y E10 por `EventPublisherPort` en la misma transacción (el listener está en RF-38) | T010, T015 |
| FR-003 | El vendedor confirma que va en camino solo desde `RECOGIDO` | `POST /domicilios/{id}/confirmar-salida`; `Domicilio.confirmarSalida()` | T011, T016, T017 |
| FR-004 | `EN_CAMINO`, trazabilidad y notificar al cliente | Transición, evento de trazabilidad y E11 | T011, T018 |
| SC-001 | 100 % con trazabilidad | Prueba de los dos eventos con autor y hora | T010, T011 |
| SC-002 | Aviso < 1 min | E11 + relay | T040 |
| SC-003 | 100 % de rechazos fuera de secuencia | Pruebas por estado | T012 |
| US1-E1 | Confirmación de recogida | `200`, `RECOGIDO`, E10 | T010 |
| US1-E2 | Recogida fuera de secuencia | `409 INVALID_STATE` con `estadoActual` | T012 |
| US2-E1 | Confirmación de salida | `200`, `EN_CAMINO`, trazabilidad, E11 | T011 |
| US2-E2 | Salida antes de la recogida | `409 INVALID_STATE` "Primero el domiciliario debe confirmar la recogida" | T012 |
| Edge 1 | El vendedor no confirma la salida | Sin vencimiento: sigue `RECOGIDO` (Open Questions) | — |
| Edge 2 | Desacuerdo entre las partes | Sin mecanismo en la spec; intervención del administrador (RF-13, P2) o reporte (RF-60) | — |
| Edge 3 | El pedido entregado no corresponde | Fuera de alcance de la spec | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Recogida: domiciliario asignado; salida: vendedor dueño del pedido |
| RNF08 Concurrencia | ☑ | Actualización condicional por estado |
| RNF15 Fiabilidad | ☑ | Transición, trazabilidad y evento en una transacción |
| RNF16 Trazabilidad | ☑ | Evento con estado, hora y usuario |
| RNF18 Notificaciones | ☑ | E10 y E11 |
| RNF23 Consistencia | ☑ | Secuencia `ASIGNADO` → `RECOGIDO` → `EN_CAMINO` en el dominio |
| RNF24 Errores claros | ☑ | `INVALID_STATE` con `estadoActual` y mensaje por paso |
| RNF27 Integridad | ☑ | `Idempotency-Key` |
| RNF28 Conectividad | ☑ | Ambas acciones pueden ir por el outbox |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Lee / escribe (`estado`) | `ASIGNADO` → `RECOGIDO` → `EN_CAMINO` |
| EventoTrazabilidad | Escribe | `RECOGIDO` (autor: domiciliario) y `EN_CAMINO` (autor: vendedor) |
| Pedido | Lee (vía puerto) | Dueño, comprador; no cambia de estado |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Domicilio | `ASIGNADO` | `RECOGIDO` | Domiciliario asignado confirma la recogida | Asignado a él | Trazabilidad; E10 |
| Domicilio | `RECOGIDO` | `EN_CAMINO` | Vendedor dueño confirma la salida | Pedido propio | Trazabilidad; E11 |

**Transiciones inválidas**: cualquier otro estado → `409 INVALID_STATE` con `details.estadoActual`. El pedido permanece `ACEPTADO`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /domicilios/{id}/confirmar-recogida` | DOMICILIARIO | Domicilio asignado a él; si es de otro, `403` |
| `POST /domicilios/{id}/confirmar-salida` | VENDEDOR_PUNTO_FIJO | Pedido de su emprendimiento; si no, `404` |

## API Contracts

### POST /api/v1/domicilios/{id}/confirmar-recogida

- **Rol**: domiciliario asignado · **Path params**: `id` (UUID) · **Query params**: ninguno · **Body**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body `{ "id": "…", "estado": "RECOGIDO", "updatedAt": "…" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` | — |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de domiciliario o domicilio asignado a otro | — |
| 404 | `NOT_FOUND` | El domicilio no existe | "El domicilio no está disponible" |
| 409 | `INVALID_STATE` | El domicilio no está `ASIGNADO` | `details.estadoActual` |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

### POST /api/v1/domicilios/{id}/confirmar-salida

- **Rol**: vendedor de punto fijo · **Path params**: `id` (UUID) · **Query params**: ninguno · **Body**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body `{ "id": "…", "estado": "EN_CAMINO", "updatedAt": "…" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` | — |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor de punto fijo | — |
| 404 | `NOT_FOUND` | Domicilio inexistente o de un pedido de otro emprendimiento | "El domicilio no está disponible" |
| 409 | `INVALID_STATE` | El domicilio no está `RECOGIDO` | `details.estadoActual`; en `ASIGNADO`: "Primero el domiciliario debe confirmar la recogida" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E10 `DomicilioRecogidoEvent` (`domicilios.domicilio-recogido`) | `ConfirmarRecogidaUseCase` | Al pasar a `RECOGIDO`. Payload: `domicilioId`, `vendedorId` |
| E11 `DomicilioEnCaminoEvent` (`domicilios.domicilio-en-camino`) | `ConfirmarSalidaUseCase` | Al pasar a `EN_CAMINO`. Payload: `domicilioId`, `compradorId` |

**Eventos que consume esta feature**: ninguno. Los avisos son listeners del plan de RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la recogida del domiciliario y la salida del vendedor se escriben en el outbox con su `Idempotency-Key` y se muestran como "pendiente de envío"; al reconectar, un `INVALID_STATE` se explica y se refresca el domicilio.
- **Concurrencia (RNF08)**: la actualización condicional `WHERE estado = ...` garantiza que dos solicitudes iguales produzcan una sola transición y un solo evento.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Recogida fuera de secuencia | `409 INVALID_STATE` con el estado actual | US1-E2 |
| Salida antes de la recogida | `409 INVALID_STATE` con el mensaje del paso previo | US2-E2 |
| Domicilio de otro domiciliario o de otro vendedor | `403` / `404` | RNF03 |
| Sin conexión | Outbox con reintento | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Domicilio.confirmarRecogida` y `confirmarSalida` (secuencia y errores) | FR-001, FR-003 |
| Integración | Endpoints con Testcontainers: ambas transiciones, eventos de trazabilidad con autor, E10 y E11 en el outbox, el pedido sigue `ACEPTADO`, fuera de secuencia, domicilio de otro, rol equivocado, repetición con la misma clave | US1-E1–E2, US2-E1–E2 |
| Concurrencia | Dos recogidas simultáneas: una transición y un solo E10 | RNF08 |
| Frontend | Jest: botones por rol y estado, mensajes de secuencia, outbox sin red | SC-002 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-23-recogida-del-pedido-y-salida-del-domicilio/
├── RF-23-recogida-del-pedido-y-salida-del-domiciliario.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── domain/
│   ├── model/                    # Domicilio.confirmarRecogida, confirmarSalida
│   └── event/                    # DomicilioRecogidoEvent (E10), DomicilioEnCaminoEvent (E11)
├── application/
│   ├── ports/in/                 # ConfirmarRecogidaPort, ConfirmarSalidaPort
│   ├── ports/out/                # DomicilioRepositoryPort (transicionarAtomico), PedidoParaDomicilioPort, EventPublisherPort (shared)
│   └── usecase/                  # ConfirmarRecogidaUseCase, ConfirmarSalidaUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (POST confirmar-recogida y confirmar-salida)
    └── out/persistence/          # UPDATE condicional y evento de trazabilidad
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/components/domicilios/AccionesDomiciliarioEnCurso.tsx   # "Ya recogí el pedido" (y "Confirmar entrega", RF-24)
frontend/src/components/domicilios/AccionConfirmarSalida.tsx         # "El domiciliario ya salió" en el domicilio del vendedor
frontend/src/app/(domiciliario)/domicilios/[id].tsx                  # (RF-18) acciones del domicilio propio
frontend/src/app/(vendedor)/domicilios/[id].tsx                      # (RF-26) estado y confirmar salida
frontend/src/services/domicilios.ts                                  # confirmarRecogida(), confirmarSalida() por el outbox
frontend/__tests__/domicilios/recogida-salida.test.tsx
```

**Structure Decision**: módulo `domicilios`, dueño de la máquina de estados del domicilio. Cada transición es una actualización condicional y deja su evento de trazabilidad con autor (RNF16), tal como pide `EventoTrazabilidad` ("el caso de uso lo inserta en la misma transacción"). El pedido no se toca hasta la entrega (RF-24).

## Phase 1: Setup

- [ ] T001 Confirmar `asignarAtomico` y `transicionarAtomico` del repositorio (RF-14, T004; RF-19, T002) y el evento de trazabilidad (RF-14, T003)

## Phase 2: Foundational

- [ ] T002 `DomicilioRepositoryPort.transicionarAtomico(id, desde, hacia)` con `UPDATE ... WHERE estado = :desde` (si RF-14 no lo dejó completo) y su adaptador
- [ ] T003 Ampliar `PedidoParaDomicilioPort` con el dueño del pedido para validar al vendedor de la salida

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Domiciliario confirma que recogió el pedido (Priority: P1)

**Goal**: dejar constancia del traspaso desde el lado del domiciliario.
**Independent Test**: con un domicilio `ASIGNADO`, confirmar la recogida y ver `RECOGIDO` y E10.
**Cubre**: FR-001–FR-002, SC-001, SC-003, US1-E1–E2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /domicilios/{id}/confirmar-recogida` (`RECOGIDO`, evento de trazabilidad con el domiciliario, E10 en el outbox) en `backend/src/test/java/co/edu/napa/domicilios/ConfirmarRecogidaIT.java`
- [ ] T012 [P] [US1] Test de estados fuera de secuencia (`DISPONIBLE`, `RECOGIDO`, `EN_CAMINO`, `ENTREGADO`, `CANCELADO`) en ambas acciones, con `estadoActual` y el mensaje del paso previo, en `.../domicilios/ConfirmarFueraDeSecuenciaIT.java`
- [ ] T019 [P] [US1] Test de domicilio de otro domiciliario (`403`), rol equivocado, falta de `Idempotency-Key`, dos recogidas simultáneas y repetición con la misma clave en `.../domicilios/ConfirmarRecogidaAutorizacionIT.java`
- [ ] T020 [P] [US1] Test unitario de `Domicilio.confirmarRecogida` y `confirmarSalida` en `backend/src/test/java/co/edu/napa/domicilios/DomicilioSecuenciaTest.java`
- [ ] T030 [P] [US1] Test de las acciones del domiciliario y del vendedor (visibilidad por estado, mensajes, outbox sin red) en `frontend/__tests__/domicilios/recogida-salida.test.tsx`

### Implementation

- [ ] T013 [US1] `Domicilio.confirmarRecogida()` y `Domicilio.confirmarSalida()` en `domicilios/domain/model/Domicilio.java`
- [ ] T014 [US1] `ConfirmarRecogidaUseCase` y `POST /domicilios/{id}/confirmar-recogida` en `domicilios/`
- [ ] T015 [US1] `DomicilioRecogidoEvent` (E10) y su publicación por `EventPublisherPort`
- [ ] T021 [US1] Evento de trazabilidad `RECOGIDO` (autor: domiciliario)
- [ ] T031 [US1] Servicio `confirmarRecogida()` por el outbox en `frontend/src/services/domicilios.ts`
- [ ] T032 [US1] `AccionesDomiciliarioEnCurso` ("Ya recogí el pedido") e integración en `frontend/src/app/(domiciliario)/domicilios/[id].tsx` (visible con el domicilio propio `ASIGNADO`)

## Phase 4: User Story 2 - Vendedor confirma que el domiciliario salió (Priority: P1)

**Goal**: completar el traspaso con la conformidad del vendedor y avisar al cliente.
**Independent Test**: con un domicilio `RECOGIDO`, confirmar la salida y ver `EN_CAMINO`, trazabilidad y E11.
**Cubre**: FR-003–FR-004, SC-001–SC-003, US2-E1–E2.

### Tests (antes de implementar)

- [ ] T011 [P] [US2] Test de integración de `POST /domicilios/{id}/confirmar-salida` (`EN_CAMINO`, evento de trazabilidad con el vendedor, E11 en el outbox, pedido sigue `ACEPTADO`) en `backend/src/test/java/co/edu/napa/domicilios/ConfirmarSalidaIT.java`
- [ ] T022 [P] [US2] Test de domicilio de un pedido de otro vendedor (`404`), rol equivocado (`403`) y dos salidas simultáneas en `.../domicilios/ConfirmarSalidaAutorizacionIT.java`

### Implementation

- [ ] T016 [US2] `ConfirmarSalidaUseCase` y `POST /domicilios/{id}/confirmar-salida` en `domicilios/`
- [ ] T017 [US2] Evento de trazabilidad `EN_CAMINO` (autor: vendedor)
- [ ] T018 [US2] `DomicilioEnCaminoEvent` (E11) y su publicación por `EventPublisherPort`
- [ ] T033 [US2] Servicio `confirmarSalida()` por el outbox en `frontend/src/services/domicilios.ts`
- [ ] T034 [US2] `AccionConfirmarSalida` en `frontend/src/components/domicilios/` e integración en `frontend/src/app/(vendedor)/domicilios/[id].tsx` (visible con el domicilio `RECOGIDO`)

**Checkpoint**: US1 y US2 funcionales.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir el aviso "en camino" al cliente en < 1 min (SC-002)
- [ ] T041 Revisar permisos y autoría de los eventos de trazabilidad (RNF03, RNF16)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → Polish.
- Dependencias entre RF: requiere RF-19 y RF-14; habilita RF-24 (entrega desde `EN_CAMINO`) y RF-26 (la ubicación se comparte desde `EN_CAMINO`).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: el vendedor no confirma la salida | Regla | Sin vencimiento: sigue `RECOGIDO` y el domiciliario no puede avanzar | Equipo |
| Edge 2: desacuerdo entre vendedor y domiciliario | Regla | Sin mecanismo en la spec; reporte (RF-60) y cancelación del administrador (RF-13, P2) | Equipo |
| El domiciliario necesita volver a su domicilio activo y el vendedor a sus domicilios: dependen de RF-15/RF-12 (P2) | Orden de construcción | El vendedor llega desde el bloque `domicilio` del pedido (RF-51); el domiciliario, desde el id guardado en el teléfono | Equipo |
| El vendedor confirma la salida sin que la spec indique desde qué pantalla; no hay listado de domicilios del vendedor en P1 | UX | Desde el detalle del pedido, vía el bloque `domicilio`, que abre `(vendedor)/domicilios/[id]` | Equipo |
| Confirmaciones por el outbox: si el vendedor está sin conexión, el domiciliario no puede avanzar hasta que se envíe | Operación | Aceptado | Equipo |

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
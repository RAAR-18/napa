# Implementation Plan: Cancelación de domicilio por administrador

**Date**: 2026-10-27
**Spec**: [RF-13-cancelacion-de-domicilio-por-administrador.md](./RF-13-cancelacion-de-domicilio-por-administrador.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-13
**Historias de usuario**: HU-52
**Prioridad (la mayor de la spec)**: P3
**Depende de**: RF-12, RF-14, RF-19, RF-24, RF-26, RF-38, RF-10
**Habilita a**: RF-62 (los reportes de mal uso se pueden resolver tras cancelar)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador cancela un domicilio con `POST /domicilios/{id}/cancelar`, indicando un motivo obligatorio (5 a 200 caracteres). Funciona con el domicilio en cualquier estado salvo `ENTREGADO` (y no sobre uno ya `CANCELADO`). En una sola transacción el domicilio pasa a `CANCELADO` con su motivo, el pedido asociado pasa a `CANCELADO`, se registra el evento de trazabilidad con el administrador como autor, el código de confirmación queda sin uso y se publica E15 para avisar al cliente, al vendedor y al domiciliario si ya había uno. Si el domiciliario tenía el domicilio en curso, queda libre para tomar otro. El dinero ya pagado se devuelve fuera de la plataforma: Ñapa no lo procesa ni cambia el estado del pago.

## Estado actual del código

RF-14 creó `domicilio` con `motivo_cancelacion`, `evento_trazabilidad` y el índice parcial que excluye los `CANCELADO` del límite de un domicilio activo por pedido y por domiciliario. RF-26 ya muestra el motivo a las partes y espera que RF-13 borre la ubicación en vivo (T003 de RF-26). E15 ya está en `eventos.md` y RF-38 ya lo escucha. Falta la acción del administrador y el puerto de `pedidos` para cancelar el pedido en la misma transacción. RF-12 entrega el detalle desde el que el administrador cancela.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Recibe el aviso de cancelación |
| Vendedor | Recibe el aviso de cancelación |
| Domiciliario | Recibe el aviso si ya estaba asignado y queda libre |
| Administrador | Cancela el domicilio con un motivo |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- 100 % de cancelaciones con motivo y avisadas en < 1 minuto (SC-001): el motivo se valida antes de tocar la base y E15 va por el outbox en la misma transacción; el relay de foundations corre cada pocos segundos.
- 100 % de rechazos de domicilios entregados (SC-002): la transición se hace con `UPDATE … WHERE estado NOT IN ('ENTREGADO','CANCELADO')` y bloqueo de fila, así una entrega concurrente de RF-24 no se pisa.
- Orden de bloqueo único: domicilio → pedido → pago (el de RF-14, RF-24 y RF-43).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Cancelar cualquier domicilio salvo los entregados | `POST /domicilios/{id}/cancelar`; estados `DISPONIBLE`, `ASIGNADO`, `RECOGIDO`, `EN_CAMINO` | T010, T011, T020, T021 |
| FR-002 | Motivo obligatorio | Validación de 5 a 200 caracteres; `motivo_cancelacion` | T012, T022 |
| FR-003 | Notificar a cliente, vendedor y domiciliario (si existe) | Evento E15 en el outbox; lo escucha RF-38 | T013, T023 |
| FR-004 | El pedido pasa a cancelado en la misma operación | `CancelarPedidoPorDomicilioPort` de `pedidos` dentro de la transacción | T014, T024 |
| SC-001 | Cancelaciones con motivo y avisadas en < 1 min | Outbox y relay | T013, T015 |
| SC-002 | 100 % de entregados rechazados | `409 INVALID_STATE` con el estado actual | T016 |
| US1-E1 | Cancelación de un domicilio problemático | `200` con el domicilio cancelado | T010 |
| US1-E2 | Cancelación sin motivo | `400 VALIDATION_ERROR`; la app no habilita confirmar | T012, T030 |
| Edge 1 | Domicilio ya entregado | `409 INVALID_STATE` | T016 |
| Edge 2 | Domicilio con pago confirmado | Se cancela igual; el pago no cambia y el dinero se devuelve fuera de la plataforma; la app lo advierte antes de confirmar | T017, T031 |
| Edge 3 | Domiciliario ya en camino | Se cancela; el domiciliario recibe el aviso, se borra su ubicación en vivo y queda libre | T018, T025 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR |
| RNF07 Rendimiento ≤ 3 s | ☑ | Una transacción corta |
| RNF08 Concurrencia | ☑ | Bloqueo de fila y `UPDATE` condicionado al estado |
| RNF15 Fiabilidad | ☑ | Domicilio, pedido, trazabilidad y outbox en una transacción |
| RNF16 Trazabilidad | ☑ | `EventoTrazabilidad` con el administrador como autor y motivo guardado |
| RNF23 Consistencia | ☑ | Transiciones inválidas dan `INVALID_STATE` |
| RNF27 Idempotencia | ☑ | `Idempotency-Key` obligatoria |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Lee / escribe (`estado`, `motivo_cancelacion`, `codigo_confirmacion`) | Cancelable salvo `ENTREGADO`; el código deja de servir (restricción 9) |
| EventoTrazabilidad | Escribe | Nuevo evento `CANCELADO` con el administrador como autor; inmutable |
| Pedido | Escribe (puerto) | Pasa a `CANCELADO` en la misma transacción (restricción 12) |
| Pago | No se toca | La devolución ocurre fuera de la plataforma (spec-general) |

## State Transitions

| Entidad | Desde | Hacia | Acción | Condición |
|---|---|---|---|---|
| Domicilio | `DISPONIBLE`, `ASIGNADO`, `RECOGIDO` o `EN_CAMINO` | `CANCELADO` | `POST /domicilios/{id}/cancelar` | Motivo válido; no `ENTREGADO` ni ya `CANCELADO` |
| Pedido | `ACEPTADO` (modalidad domicilio) | `CANCELADO` | La misma acción | En la misma transacción |

`ENTREGADO` y `CANCELADO` son terminales: cancelar desde ellos responde `409 INVALID_STATE` con `details.estadoActual`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /domicilios/{id}/cancelar` | ADMINISTRADOR | Cualquier domicilio |

## API Contracts

### POST /api/v1/domicilios/{id}/cancelar

- **Rol**: administrador · **Path params**: `id` (UUID del domicilio) · **Query params**: ninguno
- **Body**: `{ "motivo": "El cliente reportó que el domiciliario no llegó" }` (5 a 200 caracteres)

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `200` con `X-Request-Id` y body `{ "id", "estado": "CANCELADO", "motivoCancelacion", "pedido": { "id", "estado": "CANCELADO" } }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Motivo ausente, vacío, de menos de 5 o de más de 200 caracteres | `details.campo = "motivo"` |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de administrador | — |
| 404 | `NOT_FOUND` | El domicilio no existe | "El domicilio no existe" |
| 409 | `INVALID_STATE` | El domicilio ya está `ENTREGADO` o `CANCELADO` | `details.estadoActual` |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E15 `DomicilioCanceladoEvent` (`domicilioId`, `pedidoId`, `compradorId`, `vendedorId`, `domiciliarioId?`) | `CancelarDomicilioUseCase` | En la misma transacción de la cancelación (outbox) |

**Eventos que consume esta feature**: ninguno. Quién avisa a las partes (RF-38) se planifica en el plan de RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: es una acción administrativa y no se encola: la app exige conexión y muestra el error si falla.
- **Concurrencia (RNF08)**: `SELECT … FOR UPDATE` sobre el domicilio y luego el pedido. Si el domiciliario confirma la entrega (RF-24) a la vez, gana quien tome el bloqueo primero: el segundo recibe `409 INVALID_STATE`. Un domiciliario que aceptaba (RF-19) justo antes de la cancelación termina con el domicilio `CANCELADO` y queda libre.
- **Idempotencia (RNF27)**: la misma clave devuelve el mismo `200` sin repetir la cancelación; con otra clave y el domicilio ya cancelado llega `409 INVALID_STATE`.
- **Efectos fuera de la transacción**: borrar `loc:{domicilioId}` y los contadores del código (`code-att`, `code-lock`) en Redis se hace después del commit; si falla, vencen solos por su TTL.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Sin motivo | `400` y la app no deja confirmar | US1-E2 |
| Domicilio entregado | `409 INVALID_STATE` | Edge 1 |
| Domicilio ya cancelado | `409 INVALID_STATE` | RNF23 |
| Pago ya confirmado | Se cancela; el dinero se devuelve fuera de Ñapa | Edge 2 |
| Falla el outbox o el pedido | Se revierte todo | RNF15 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Cancelación desde `DISPONIBLE`, `ASIGNADO`, `RECOGIDO` y `EN_CAMINO`: estado, motivo, pedido `CANCELADO`, evento de trazabilidad con el autor, código sin uso, E15 en el outbox | FR-001, FR-003, FR-004, US1-E1 |
| Integración | Motivo ausente, corto y largo; `ENTREGADO` y ya `CANCELADO` (`409`); inexistente; rol distinto; sin sesión | FR-002, SC-002, US1-E2, Edge 1 |
| Integración | Con pago `CONFIRMADO` y con `PENDIENTE_CONFIRMACION`: el pago no cambia | Edge 2 |
| Integración | Domiciliario asignado: recibe E15 y puede aceptar otro domicilio después | Edge 3, FR-003 |
| Concurrencia | Cancelar y confirmar entrega (RF-24) a la vez, y cancelar dos veces | RNF08 |
| Atomicidad | Si falla el cambio del pedido o el outbox se revierte todo | RNF15 |
| Frontend | Jest: motivo obligatorio, aviso de pago, confirmación y errores | US1-E2, Edge 2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-13-cancelacion-de-domicilio-por-administrador/
├── RF-13-cancelacion-de-domicilio-por-administrador.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── domain/event/                 # DomicilioCanceladoEvent (E15)
├── application/
│   ├── ports/in/                 # CancelarDomicilioPort
│   ├── ports/out/                # DomicilioRepositoryPort, EventoTrazabilidadRepositoryPort, CancelarPedidoPorDomicilioPort (pedidos), UbicacionEnVivoPort, EventPublisherPort
│   └── usecase/                  # CancelarDomicilioUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (POST /domicilios/{id}/cancelar)
    └── out/                      # adaptador de CancelarPedidoPorDomicilioPort sobre el puerto in de pedidos

backend/src/main/java/co/edu/napa/pedidos/application/ports/in/   # CancelarPedidoPorDomicilioPort
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/components/domicilios/CancelarDomicilioSheet.tsx     # motivo, aviso de pago y confirmación
frontend/src/services/domiciliosAdmin.ts                          # cancelarDomicilio()
frontend/src/app/(administrador)/domicilios/[id].tsx              # (RF-12) incluye la acción
frontend/__tests__/domicilios/cancelar-domicilio.test.tsx
```

**Structure Decision**: módulo `domicilios`, dueño de `Domicilio` y de su trazabilidad. El pedido se cancela por un puerto `in` de `pedidos` dentro de la misma transacción (la regla 12 de spec-general) y no al revés, para no crear un ciclo. Es el mismo patrón y el mismo orden de bloqueo de RF-24 y RF-43.

## Phase 1: Setup

- [ ] T001 Confirmar `motivo_cancelacion` y los índices parciales de `domicilio` (RF-14 T002) y el evento E15 en `eventos.md`

## Phase 2: Foundational

- [ ] T002 `CancelarPedidoPorDomicilioPort` en `pedidos` (pasa el pedido a `CANCELADO` si está `ACEPTADO` y modalidad `DOMICILIO`, dentro de la transacción del llamador)
- [ ] T003 `DomicilioCanceladoEvent` (E15) en `domicilios/domain/event/` y su publicación por `EventPublisherPort`
- [ ] T004 Método `cancelarAtomico(id)` en `DomicilioRepositoryPort` (bloqueo y `UPDATE` condicionado a no `ENTREGADO` ni `CANCELADO`)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Cancelar un domicilio problemático (Priority: P3)

**Goal**: el administrador resuelve un caso excepcional y todas las partes quedan informadas.
**Independent Test**: cancelar un domicilio en curso con un motivo y ver el domicilio y el pedido cancelados, y a las partes avisadas.
**Cubre**: FR-001–FR-004, SC-001, SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /domicilios/{id}/cancelar` desde `DISPONIBLE`, `ASIGNADO`, `RECOGIDO` y `EN_CAMINO` en `backend/src/test/java/co/edu/napa/domicilios/CancelarDomicilioIT.java`
- [ ] T011 [P] [US1] Test de la trazabilidad (evento `CANCELADO` con el administrador como autor) y de que el código de confirmación queda sin uso en `.../domicilios/CancelarDomicilioTrazabilidadIT.java`
- [ ] T012 [P] [US1] Test de motivo ausente, vacío, corto y largo (`400`) en `.../domicilios/CancelarDomicilioMotivoIT.java`
- [ ] T013 [P] [US1] Test de que E15 queda en el outbox con el payload correcto y se entrega en < 1 minuto en `.../domicilios/CancelarDomicilioEventoIT.java`
- [ ] T014 [P] [US1] Test de que el pedido pasa a `CANCELADO` en la misma transacción y de que si falla el pedido se revierte todo en `.../domicilios/CancelarDomicilioAtomicidadIT.java`
- [ ] T015 [P] [US1] Test del relay de notificaciones con la cancelación (RF-38 recibe E15) en `.../domicilios/CancelarDomicilioNotificacionIT.java`
- [ ] T016 [P] [US1] Test de `ENTREGADO` y ya `CANCELADO` (`409 INVALID_STATE` con `estadoActual`) en `.../domicilios/CancelarDomicilioEstadoIT.java`
- [ ] T017 [P] [US1] Test de cancelación con pago `CONFIRMADO` y `PENDIENTE_CONFIRMACION` (el pago no cambia) en `.../domicilios/CancelarDomicilioPagoIT.java`
- [ ] T018 [P] [US1] Test de domiciliario en camino: queda libre para aceptar otro y su ubicación en vivo se borra, en `.../domicilios/CancelarDomicilioDomiciliarioIT.java`
- [ ] T019 [P] [US1] Test de concurrencia (cancelar y confirmar entrega a la vez; dos cancelaciones) y de rol distinto, sin sesión e inexistente, en `.../domicilios/CancelarDomicilioConcurrenciaIT.java`
- [ ] T030 [P] [US1] Test de `CancelarDomicilioSheet` (motivo obligatorio, aviso de pago, errores) en `frontend/__tests__/domicilios/cancelar-domicilio.test.tsx`

### Implementation

- [ ] T020 [US1] `CancelarDomicilioPort` y `CancelarDomicilioUseCase`: bloquea domicilio y pedido, valida el estado, cancela, registra la trazabilidad y publica E15
- [ ] T021 [US1] `POST /domicilios/{id}/cancelar` y su DTO en `DomicilioController`
- [ ] T022 [US1] Validación del motivo (5 a 200 caracteres) y su persistencia en `motivo_cancelacion`
- [ ] T023 [US1] Publicación de E15 con `domiciliarioId` cuando existe
- [ ] T024 [US1] Adaptador de `CancelarPedidoPorDomicilioPort` y `cancelarAtomico` en los repositorios
- [ ] T025 [US1] Borrado post-commit de `loc:{domicilioId}`, `code-att` y `code-lock` con `UbicacionEnVivoPort`
- [ ] T031 [US1] `CancelarDomicilioSheet` y `cancelarDomicilio()` con `Idempotency-Key` en `frontend/src/`
- [ ] T032 [US1] Acción "Cancelar" en el detalle del domicilio del administrador (RF-12), visible solo si no está `ENTREGADO` ni `CANCELADO`

**Checkpoint**: el administrador cancela y todos quedan informados.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Confirmar el orden de bloqueo domicilio → pedido → pago con las demás transiciones
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-14, RF-19, RF-24 (concurrencia), RF-26 (ubicación en vivo y trazabilidad), RF-12 (detalle) y el listener de E15 de RF-38; RF-62 puede cerrar el reporte que originó la cancelación, sin ligar ambos.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Se repone el inventario de un pedido inmediato cancelado? | Regla | No: spec-general (restricción 14) deja el inventario en manos del vendedor | Equipo |
| ¿Qué pasa con un pago `PENDIENTE_CONFIRMACION` de un pedido cancelado? | Datos | Queda así; el administrador lo ve en RF-41 con su filtro de estado | Equipo |
| ¿Los avisos de E15 incluyen el motivo? | Contrato | No: el payload no lo trae; RF-26 muestra `motivoCancelacion` a las partes | Equipo |
| La ruta no está en spec-general (`POST /domicilios/{id}/cancelar`) | Contrato | Se agrega a spec-general | Equipo |
| ¿Se cancela también el reporte relacionado? | Alcance | No: el administrador lo resuelve aparte con RF-62 | Equipo |
| Longitud del motivo (5 a 200) | Validación | Igual que RF-06 y RF-62 | Equipo |

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
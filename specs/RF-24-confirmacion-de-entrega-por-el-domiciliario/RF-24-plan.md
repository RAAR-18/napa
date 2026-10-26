# Implementation Plan: Confirmación de entrega por el domiciliario

**Date**: 2026-10-25
**Spec**: [RF-24-confirmacion-de-entrega-por-el-domiciliario.md](./RF-24-confirmacion-de-entrega-por-el-domiciliario.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md) (E07, E12)
**Requerimiento funcional**: RF-24
**Historias de usuario**: HU-47
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-23 (`EN_CAMINO`), RF-19 (código), RF-36 y RF-37 (`TransicionarPedidoPort`, E07), RF-10
**Habilita a**: RF-43, RF-01, RF-60

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El domiciliario cierra el domicilio con `POST /domicilios/{id}/confirmar-entrega`, enviando el código de 6 dígitos que le dice el cliente. Solo funciona con el domicilio `EN_CAMINO` y asignado a él. Si el código coincide, en **una sola transacción**: el domicilio pasa a `ENTREGADO`, el pedido pasa a `ENTREGADO` (por el puerto `TransicionarPedidoPort` de `pedidos`, que publica E07 con el `domicilioId`), se inserta el evento de trazabilidad con el domiciliario como autor y se emite E12 (avisa al vendedor que debe pagar la tarifa y al cliente que su pedido fue entregado). Si no coincide, responde `400 CODE_INVALID` con los intentos restantes. Tras 5 fallos por domicilio, bloquea la confirmación 15 minutos (`429 CODE_ATTEMPTS_LOCKED` con `Retry-After`). La entrega no puede confirmarse dos veces.

## Estado actual del código

El domicilio `EN_CAMINO` y su código los producen RF-19 y RF-23; `TransicionarPedidoPort` y `PedidoEntregadoEvent` los crean RF-36 y RF-37. Los contadores de intentos viven en Redis, que ya está en el proyecto (idempotencia y versión de sesión). No existe la confirmación de entrega del domicilio.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Dice su código al domiciliario y recibe el aviso de entrega |
| Vendedor (punto fijo) | Recibe el aviso para pagar la tarifa (RF-43) |
| Domiciliario | Ingresa el código y cierra la entrega |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Avisos al vendedor y al cliente en < 1 min (SC-002): E12 por el outbox.
- 0 % de entregas sin código válido o repetidas (SC-003): código comparado en tiempo constante, 5 intentos por domicilio y actualización condicional por estado.
- Con 6 dígitos y 5 intentos, la probabilidad de adivinar es de 5 en un millón por domicilio; el bloqueo es obligatorio, no un detalle de experiencia.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Confirmar solo con el domicilio `EN_CAMINO` y asignado al domiciliario | `Domicilio.confirmarEntrega()`; `403` si es de otro; `409 INVALID_STATE` si no está `EN_CAMINO` | T010, T012, T015 |
| FR-002 | Exigir el código y rechazar si no coincide | Cuerpo `{ "codigo": "123456" }` obligatorio (6 dígitos); comparación en tiempo constante; `400 CODE_INVALID` con `intentosRestantes` | T011, T016, T017 |
| FR-003 | Código válido: domicilio y pedido `ENTREGADO`, trazabilidad, avisar al vendedor y al cliente | Una transacción: transición del domicilio, `TransicionarPedidoPort.confirmarEntregaDomicilio`, evento de trazabilidad y E12; E07 lo publica `pedidos` | T010, T018, T019, T020 |
| FR-004 | No confirmar dos veces | Estado `ENTREGADO` → `409 INVALID_STATE`; actualización condicional | T012, T021 |
| FR-005 | Habilitar el pago de la tarifa (RF-43) y las calificaciones (RF-01) desde `ENTREGADO` | Se derivan del estado `ENTREGADO` del domicilio y del pedido; no requieren lógica adicional | T010 |
| FR-006 | 5 fallos por domicilio y bloqueo de 15 minutos | Contadores en Redis `code-att:{domicilioId}` y `code-lock:{domicilioId}`; `429 CODE_ATTEMPTS_LOCKED` | T013, T014, T022 |
| SC-001 | 100 % con fecha, hora y validación en la trazabilidad | Evento `ENTREGADO` con el domiciliario y la hora; solo existe si el código fue válido | T010 |
| SC-002 | Avisos < 1 min | E12 + relay | T040 |
| SC-003 | 0 % sin código o dos veces | Pruebas de código inválido, bloqueo y repetición | T011, T012, T014 |
| US1-E1 | Código válido | `200`, ambos `ENTREGADO`, trazabilidad, E12 y E07 | T010 |
| US1-E2 | Código incorrecto | `400 CODE_INVALID` con `intentosRestantes` | T011 |
| US1-E3 | Intentos agotados | `429 CODE_ATTEMPTS_LOCKED` con `Retry-After` y la sugerencia de crear un reporte (RF-60) | T014 |
| US1-E4 | Fuera de secuencia | `409 INVALID_STATE` con `estadoActual` | T012 |
| US1-E5 | Habilitación del pago de la tarifa | El estado `ENTREGADO` es la precondición que verifica RF-43 | T010 |
| Edge 1 | El cliente no tiene su código | No hay vía de respaldo en la spec; reporte (RF-60) o cancelación del administrador (RF-13, P2); el cliente lo cachea en su teléfono (RF-25) | — |
| Edge 2 | ¿Validar cercanía al punto B? | No se valida (Open Questions) | — |
| Edge 3 | El cliente dice no haber recibido pero el domiciliario tiene el código | Reporte del cliente (RF-60); el domicilio ya está `ENTREGADO` | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el domiciliario asignado |
| RNF04 Privacidad | ☑ | El código no se registra en logs ni eventos |
| RNF08 Concurrencia | ☑ | Actualización condicional `WHERE estado = 'EN_CAMINO'`; contadores atómicos en Redis |
| RNF15 Fiabilidad | ☑ | Domicilio, pedido, trazabilidad, E07 y E12 en una transacción |
| RNF16 Trazabilidad | ☑ | Evento `ENTREGADO` con el domiciliario |
| RNF18 Notificaciones | ☑ | E12 (y E07, que RF-38 no usa en domicilio) |
| RNF23 Consistencia | ☑ | Domicilio y pedido `ENTREGADO` juntos o ninguno |
| RNF24 Errores claros | ☑ | `CODE_INVALID`, `CODE_ATTEMPTS_LOCKED` con `Retry-After` |
| RNF27 Integridad | ☑ | `Idempotency-Key`; los fallos no se cuentan dos veces |
| RNF28 Conectividad | ☑ | Exige conexión: el domiciliario necesita la respuesta inmediata |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Lee / escribe (`estado`) | `EN_CAMINO` → `ENTREGADO`; el código se valida una sola vez |
| EventoTrazabilidad | Escribe | `ENTREGADO`, autor = domiciliario |
| Pedido | Escribe (vía puerto, `estado`) | `ACEPTADO` → `ENTREGADO` en la misma transacción |
| Contadores en Redis | Escribe | `code-att:{domicilioId}`, `code-lock:{domicilioId}` (no son entidades del modelo) |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Domicilio | `EN_CAMINO` | `ENTREGADO` | Domiciliario ingresa el código correcto | Asignado a él; sin bloqueo | Trazabilidad; E12 |
| Pedido (domicilio) | `ACEPTADO` | `ENTREGADO` | La misma confirmación | Domicilio activo del pedido | E07 con `domicilioId`; habilita calificar |

**Transiciones inválidas**: domicilio en otro estado → `409 INVALID_STATE` con `details.estadoActual`; código incorrecto → no cambia nada y suma un intento.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /domicilios/{id}/confirmar-entrega` | DOMICILIARIO | Domicilio asignado a él; si es de otro, `403` |

## API Contracts

### POST /api/v1/domicilios/{id}/confirmar-entrega

- **Rol**: domiciliario asignado · **Path params**: `id` (UUID) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅, `Content-Type: application/json`.
- **Body**: `{ "codigo": "123456" }` (exactamente 6 dígitos; puede empezar por cero).
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body `{ "id": "…", "estado": "ENTREGADO", "pedido": { "id": "…", "estado": "ENTREGADO" }, "updatedAt": "…" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` o `codigo` no tiene 6 dígitos | Indica el campo (no cuenta como intento) |
| 400 | `CODE_INVALID` | El código no coincide | "El código no es válido"; `details.intentosRestantes` |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de domiciliario o domicilio asignado a otro | — |
| 404 | `NOT_FOUND` | El domicilio no existe | "El domicilio no está disponible" |
| 409 | `INVALID_STATE` | El domicilio no está `EN_CAMINO` (incluye ya `ENTREGADO`) | `details.estadoActual` |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |
| 429 | `CODE_ATTEMPTS_LOCKED` | 5 fallos acumulados | `Retry-After` en segundos; "Superaste los intentos. Puedes crear un reporte" |
| 503 | `EXTERNAL_SERVICE_UNAVAILABLE` | Redis no responde (se rechaza en lugar de dejar pasar sin límite) | `Retry-After` |

`CODE_INVALID` no está en spec-general §2.2.1 (Open Questions).

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E12 `DomicilioEntregadoEvent` (`domicilios.domicilio-entregado`) | `ConfirmarEntregaDomicilioUseCase` | Al validar el código, en la misma transacción. Payload: `domicilioId`, `pedidoId`, `compradorId`, `vendedorId`, `domiciliarioId` |
| E07 `PedidoEntregadoEvent` (`pedidos.pedido-entregado`) | `TransicionarPedidoUseCase.confirmarEntregaDomicilio` (`pedidos`) | Al pasar el pedido a `ENTREGADO`. Payload: `pedidoId`, `compradorId`, `vendedorId`, `domicilioId` |

**Eventos que consume esta feature**: ninguno. RF-38 avisa con E12 al vendedor y al cliente y no reacciona a E07 en domicilio, para no duplicar el aviso.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: confirmar la entrega exige conexión y respuesta inmediata (los intentos y el bloqueo dependen del servidor); sin conexión la app lo explica y no encola el código. Es la acción más sensible del flujo y no va por el outbox.
- **Concurrencia (RNF08)**: contadores con `INCR` atómico en Redis; la transición del domicilio es condicional por estado; dos solicitudes simultáneas con el código correcto producen una sola entrega.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`. Una respuesta de error (`CODE_INVALID`) no se guarda como definitiva: repetir la misma clave con el mismo código no vuelve a contar como intento ni devuelve un resultado distinto. Repetir la clave tras un éxito devuelve el mismo éxito.
- **Redis**: `code-att` expira a los 15 minutos desde el último fallo; `code-lock` expira a los 15 minutos desde el quinto fallo; un éxito borra ambos.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Código incorrecto | `400 CODE_INVALID` con intentos restantes | US1-E2 |
| Quinto fallo y siguiente intento | `429 CODE_ATTEMPTS_LOCKED` durante 15 min; sugerir reporte | US1-E3 |
| Domicilio fuera de secuencia | `409 INVALID_STATE` | US1-E4 |
| Doble confirmación | `409 INVALID_STATE` (`ENTREGADO`) | FR-004 |
| Falla al actualizar el pedido | Se revierte el domicilio completo; sin eventos | RNF15 |
| Redis caído | `503`; no se acepta el código sin control de intentos | FR-006 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Domicilio.confirmarEntrega`, comparación del código en tiempo constante, política de intentos y bloqueo | FR-002, FR-006 |
| Integración | Endpoint con Testcontainers (PostGIS y Redis): código válido (ambos `ENTREGADO`, trazabilidad, E12 y E07), incorrecto con intentos restantes, quinto fallo y bloqueo con `Retry-After`, expiración del bloqueo, éxito que borra los contadores, fuera de secuencia, doble confirmación, otro domiciliario, formato inválido que no cuenta, Redis caído | US1-E1–E4, FR-001–FR-006 |
| Atomicidad | Falla del pedido revierte el domicilio y no emite eventos | RNF15 |
| Concurrencia | Dos solicitudes con el código correcto: una entrega; fallos simultáneos que no superan el límite de 5 | RNF08 |
| Idempotencia | La repetición de una petición fallida no cuenta doble | RNF27 |
| Frontend | Jest: campo de 6 dígitos, intentos restantes, bloqueo con cuenta regresiva, acceso a "Reportar un problema", sin conexión | US1-E2, US1-E3 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-24-confirmacion-de-entrega-por-el-domiciliario/
├── RF-24-confirmacion-de-entrega-por-el-domiciliario.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── domain/
│   ├── model/                    # Domicilio.confirmarEntrega, CodigoConfirmacion.coincide, PoliticaIntentosCodigo
│   └── event/                    # DomicilioEntregadoEvent (E12)
├── application/
│   ├── ports/in/                 # ConfirmarEntregaDomicilioPort
│   ├── ports/out/                # DomicilioRepositoryPort, IntentosCodigoPort (Redis), PedidoEntregaPort (hacia `pedidos`), EventPublisherPort (shared)
│   └── usecase/                  # ConfirmarEntregaDomicilioUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (POST /domicilios/{id}/confirmar-entrega), DTO con codigo
    └── out/
        ├── persistence/          # UPDATE condicional y evento de trazabilidad
        └── cache/                # IntentosCodigoRedisAdapter (code-att, code-lock)

backend/src/main/java/co/edu/napa/pedidos/
└── application/ports/in/         # TransicionarPedidoPort.confirmarEntregaDomicilio (se añade al de RF-36)
backend/src/main/resources/application.yml   # domicilio.codigo.max_intentos: 5, domicilio.codigo.bloqueo_minutos: 15
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/components/domicilios/ConfirmarEntregaConCodigo.tsx   # campo de 6 dígitos, intentos, bloqueo
frontend/src/components/domicilios/AccionesDomiciliarioEnCurso.tsx # (RF-23) se añade "Confirmar entrega"
frontend/src/services/domicilios.ts                                # confirmarEntrega() con conexión (sin outbox)
frontend/__tests__/domicilios/confirmar-entrega.test.tsx
```

**Structure Decision**: módulo `domicilios`. El código vive en el agregado (D13) y se compara en tiempo constante; la política de intentos es una regla pura de dominio y su estado (contadores) está detrás de `IntentosCodigoPort`, implementado en Redis con expiración. La entrega y el pedido cambian en una sola transacción: primero el domicilio (condicional), luego el pedido por `TransicionarPedidoPort`, que publica E07; el orden de bloqueo es domicilio → pedido, el mismo que usará la cancelación del administrador (RF-13).

## Phase 1: Setup

- [ ] T001 Confirmar `EN_CAMINO` (RF-23), el código guardado (RF-19, T018) y `TransicionarPedidoPort` y `PedidoEntregadoEvent` (RF-36 T004; RF-37 T003)
- [ ] T002 Parámetros `domicilio.codigo.max_intentos: 5` y `domicilio.codigo.bloqueo_minutos: 15` en `backend/src/main/resources/application.yml`

## Phase 2: Foundational

- [ ] T003 `IntentosCodigoPort` y `IntentosCodigoRedisAdapter` (`code-att:{domicilioId}`, `code-lock:{domicilioId}`) en `domicilios/application/ports/out/` y `.../infrastructure/adapters/out/cache/`
- [ ] T004 `PoliticaIntentosCodigo` (regla pura) y `CodigoConfirmacion.coincide` (tiempo constante) en `domicilios/domain/model/`
- [ ] T005 `TransicionarPedidoPort.confirmarEntregaDomicilio(pedidoId, domicilioId)` en `pedidos/application/` (sin la lógica de cobro; el pago del pedido ya está confirmado)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Domiciliario cierra la entrega con el código del cliente (Priority: P1)

**Goal**: la entrega queda cerrada solo con el código del cliente, con control de intentos.
**Independent Test**: con un domicilio `EN_CAMINO`, ingresar el código y ver domicilio y pedido `ENTREGADO`.
**Cubre**: FR-001–FR-006, SC-001–SC-003, US1-E1–E5, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /domicilios/{id}/confirmar-entrega` con código válido (domicilio y pedido `ENTREGADO`, trazabilidad con el domiciliario, E12 y E07 en el outbox, respuesta sin el código) en `backend/src/test/java/co/edu/napa/domicilios/ConfirmarEntregaIT.java`
- [ ] T011 [P] [US1] Test de código incorrecto (`CODE_INVALID`, `intentosRestantes` 4, 3, 2, 1, 0) y de formato inválido que no cuenta intento en `.../domicilios/ConfirmarEntregaCodigoInvalidoIT.java`
- [ ] T012 [P] [US1] Test de estados fuera de secuencia, doble confirmación y domicilio de otro domiciliario (`403`) en `.../domicilios/ConfirmarEntregaEstadosIT.java`
- [ ] T013 [P] [US1] Test unitario de `PoliticaIntentosCodigo` y de la comparación en tiempo constante en `backend/src/test/java/co/edu/napa/domicilios/PoliticaIntentosCodigoTest.java`
- [ ] T014 [P] [US1] Test de bloqueo: quinto fallo, `429 CODE_ATTEMPTS_LOCKED` con `Retry-After`, un código correcto durante el bloqueo sigue bloqueado, y desbloqueo a los 15 minutos (reloj controlado) en `.../domicilios/ConfirmarEntregaBloqueoIT.java`
- [ ] T021 [P] [US1] Test de concurrencia (dos solicitudes con el código correcto; fallos simultáneos) e idempotencia (repetir una petición fallida no cuenta doble) en `.../domicilios/ConfirmarEntregaConcurrenciaIT.java`
- [ ] T022 [P] [US1] Test de atomicidad (falla del pedido revierte el domicilio) y de Redis caído (`503`) en `.../domicilios/ConfirmarEntregaAtomicidadIT.java`
- [ ] T023 [P] [US1] Test de que el código nunca aparece en logs, eventos ni respuestas distintas a RF-25 en `.../domicilios/CodigoNoExpuestoIT.java`
- [ ] T030 [P] [US1] Test de la pantalla (campo de 6 dígitos, intentos, cuenta regresiva del bloqueo, "Reportar un problema", sin conexión) en `frontend/__tests__/domicilios/confirmar-entrega.test.tsx`

### Implementation

- [ ] T015 [US1] `Domicilio.confirmarEntrega(codigo)` en `domicilios/domain/model/Domicilio.java`
- [ ] T016 [US1] `ConfirmarEntregaDomicilioUseCase` (bloqueo, asignación, política de intentos, código, transición) y `POST /domicilios/{id}/confirmar-entrega` con su DTO
- [ ] T017 [US1] Mapeo de `CODE_INVALID` (400) y `CODE_ATTEMPTS_LOCKED` (429 con `Retry-After`) en el manejador de errores
- [ ] T018 [US1] Cambio del pedido a `ENTREGADO` por `TransicionarPedidoPort.confirmarEntregaDomicilio` y publicación de E07 desde `pedidos`
- [ ] T019 [US1] Evento de trazabilidad `ENTREGADO` (autor: domiciliario)
- [ ] T020 [US1] `DomicilioEntregadoEvent` (E12) y publicación por `EventPublisherPort`
- [ ] T031 [US1] Servicio `confirmarEntrega()` con conexión en `frontend/src/services/domicilios.ts`
- [ ] T032 [US1] `ConfirmarEntregaConCodigo` y su integración en `AccionesDomiciliarioEnCurso` (visible con el domicilio propio `EN_CAMINO`) y acceso a "Reportar un problema" (RF-60)

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir los avisos en < 1 min (SC-002)
- [ ] T041 Revisar que el código no salga en logs y la política de bloqueo con el equipo de seguridad (RNF03, RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` y agregar `CODE_INVALID` a spec-general §2.2.1

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-23, RF-19 y RF-36/RF-37 (puerto de pedidos y evento); habilita RF-43 (pago de la tarifa) y RF-01 (calificar); el reporte del problema es RF-60 (ola 6).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| El error de código incorrecto no tiene `code` en spec-general §2.2.1 (solo `OTP_INVALID`, que es del celular) | Catálogo | Agregar `CODE_INVALID` (400, con `details.intentosRestantes`) | Equipo |
| Edge 1: el cliente no tiene su código | Regla | Sin vía de respaldo; reporte (RF-60) o cancelación del administrador (RF-13, P2); el cliente puede tener el código guardado en su teléfono (RF-25) | Equipo |
| Edge 2: validar que el domiciliario esté cerca del punto B | Regla | No se valida; el código del cliente es la única confirmación (spec-general) | Equipo |
| El bloqueo se cuenta por domicilio, no por domiciliario | Seguridad | Así (spec-general: 5 intentos por domicilio) | Equipo |
| Si Redis no responde se rechaza (`503`) en lugar de dejar intentar sin límite | Disponibilidad vs seguridad | Rechazar | Equipo |
| Repetir con la misma `Idempotency-Key` una petición con código incorrecto: el filtro de idempotencia no debe fijar el fallo como respuesta definitiva | Integración con F012 | Verificar el comportamiento del filtro; si guarda también los errores, excluir este endpoint | Equipo |
| El pedido ya tiene el pago confirmado antes de publicar; el domicilio entregado solo habilita la tarifa del domiciliario (RF-43) | Modelo | Sin acción adicional | Equipo |

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
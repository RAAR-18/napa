# Implementation Plan: Aceptación de domicilio

**Date**: 2026-10-25
**Spec**: [RF-19-aceptacion-de-domicilio.md](./RF-19-aceptacion-de-domicilio.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md) (E09)
**Requerimiento funcional**: RF-19
**Historias de usuario**: HU-40
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-14, RF-16, RF-18, RF-10
**Habilita a**: RF-22, RF-23, RF-24, RF-25

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El domiciliario acepta un domicilio `DISPONIBLE` con `POST /domicilios/{id}/aceptar`, con la tarifa publicada (no se envía valor). La asignación es exclusiva y atómica: `UPDATE domicilio SET estado='ASIGNADO', domiciliario_id=:d, codigo_confirmacion=:c WHERE id=:id AND estado='DISPONIBLE'`; si la fila afectada es 1 ganó, si es 0 responde `409 NOT_AVAILABLE`. En la misma transacción se genera el código de confirmación (6 dígitos aleatorios, guardado tal cual, D13), se inserta el evento de trazabilidad `ASIGNADO` con el domiciliario como autor y se emite E09 para avisar al cliente y al vendedor. La respuesta no incluye el código: solo lo ve el cliente (RF-25).

## Estado actual del código

El domicilio `DISPONIBLE` lo crea RF-14 y la pantalla de información (con el botón Aceptar) RF-18. No existe la columna de código generado (la tabla `domicilio` ya tiene `codigo_confirmacion` nulo, V10).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Recibe el aviso de asignación (E09) y luego ve su código (RF-25) |
| Vendedor (punto fijo) | Recibe el aviso de asignación (E09) |
| Domiciliario | Acepta el domicilio |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Aceptar en < 10 s desde la información (SC-003): una llamada sin cuerpo.
- 100 % de los domicilios aceptados con un único domiciliario (SC-001): actualización condicional atómica; dos solicitudes simultáneas nunca asignan a dos.
- Avisos al cliente y al vendedor en < 1 min (SC-002): E09 por el outbox.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Aceptar con la tarifa publicada; el domiciliario no puede modificarla | `POST /domicilios/{id}/aceptar` sin cuerpo; cualquier cuerpo con un valor se ignora y se rechaza si contiene campos desconocidos | T010, T013, T014 |
| FR-002 | Asignación exclusiva ante aceptaciones simultáneas | `UPDATE ... WHERE estado = 'DISPONIBLE'` y `409 NOT_AVAILABLE` para quien pierde | T011, T015 |
| FR-003 | Estado `ASIGNADO` y avisar al cliente y al vendedor | `Domicilio.asignar()`, evento de trazabilidad y E09 por `EventPublisherPort` (el listener está en el plan de RF-38) | T010, T016, T017 |
| FR-004 | Habilitar la ruta en el mapa (RF-22) | La respuesta trae `rutaDisponible: true`; la app navega a la ruta | T010, T024 |
| FR-005 | Generar el código de confirmación para el cliente | `CodigoConfirmacion.generar()` (6 dígitos con `SecureRandom`), guardado en `domicilio.codigo_confirmacion` | T012, T018 |
| SC-001 | 100 % con un único domiciliario | Prueba de concurrencia | T011 |
| SC-002 | Avisos < 1 min | E09 + relay | T040 |
| SC-003 | < 10 s | Una llamada | T040 |
| US1-E1 | Aceptación exitosa | `200`, `ASIGNADO`, código generado, trazabilidad y E09 | T010, T012 |
| US1-E2 | Dos domiciliarios al mismo tiempo | Uno gana; el otro `409 NOT_AVAILABLE` | T011 |
| Edge 1 | Límite de domicilios simultáneos por domiciliario | Sin límite en la spec (Open Questions) | — |
| Edge 2 | Cancelado justo antes de aceptar | La actualización condicional no afecta filas: `409 NOT_AVAILABLE` | T019 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo DOMICILIARIO activo |
| RNF04 Privacidad | ☑ | El código no se devuelve al domiciliario ni va en el evento |
| RNF08 Concurrencia | ☑ | Actualización condicional atómica |
| RNF15 Fiabilidad | ☑ | Asignación, código, trazabilidad y E09 en una transacción |
| RNF16 Trazabilidad | ☑ | Evento `ASIGNADO` con su autor |
| RNF18 Notificaciones | ☑ | E09 |
| RNF23 Consistencia | ☑ | Transición `DISPONIBLE` → `ASIGNADO` por dominio |
| RNF24 Errores claros | ☑ | `NOT_AVAILABLE` "El domicilio ya fue tomado por otro domiciliario" |
| RNF27 Integridad | ☑ | `Idempotency-Key` |
| RNF28 Conectividad | ☑ | Exige conexión (carrera competitiva): no va por el outbox |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Lee / escribe (`estado`, `domiciliario_id`, `codigo_confirmacion`) | `DISPONIBLE` → `ASIGNADO`; el domiciliario no se reasigna |
| EventoTrazabilidad | Escribe | `ASIGNADO`, autor = domiciliario |
| Pedido | Lee (vía puerto) | Comprador y vendedor para el evento; no cambia de estado (sigue `ACEPTADO`) |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Domicilio | `DISPONIBLE` | `ASIGNADO` | Domiciliario acepta | Domicilio disponible | Código de confirmación; evento de trazabilidad; E09 |

**Transiciones inválidas**: cualquier otro estado (asignado a otro, cancelado) → `409 NOT_AVAILABLE`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /domicilios/{id}/aceptar` | DOMICILIARIO | Cualquier domiciliario activo con el domicilio disponible |

## API Contracts

### POST /api/v1/domicilios/{id}/aceptar

- **Rol**: domiciliario · **Path params**: `id` (UUID) · **Query params**: ninguno · **Body**: ninguno (la tarifa no se negocia)
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body `{ "id": "…", "estado": "ASIGNADO", "ganancia": 4500, "rutaDisponible": true, "updatedAt": "…" }`. No incluye el código de confirmación.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` o el cuerpo trae campos | — |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de domiciliario | — |
| 404 | `NOT_FOUND` | El domicilio no existe | "El domicilio no está disponible" |
| 409 | `NOT_AVAILABLE` | Ya tomado por otro, cancelado o en otro estado | "El domicilio ya no está disponible" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E09 `DomicilioAsignadoEvent` (`domicilios.domicilio-asignado`) | `AceptarDomicilioUseCase` | Al asignar, en la misma transacción. Payload: `domicilioId`, `domiciliarioId`, `compradorId`, `vendedorId` |

**Eventos que consume esta feature**: ninguno. El aviso al cliente y al vendedor es un listener del plan de RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: aceptar exige conexión y respuesta inmediata, porque varios domiciliarios compiten por el mismo domicilio; sin conexión el botón queda deshabilitado con un aviso. No va por el outbox.
- **Concurrencia (RNF08)**: la actualización condicional decide el ganador; el perdedor recibe `NOT_AVAILABLE` y la app refresca la lista.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`; repetir la misma clave devuelve la misma asignación. Una clave distinta del mismo domiciliario sobre un domicilio que ya es suyo responde `NOT_AVAILABLE`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Otro lo tomó primero | `409 NOT_AVAILABLE`; la app vuelve a la lista | US1-E2 |
| Cancelado antes de aceptar | `409 NOT_AVAILABLE` | Edge 2 |
| Sin conexión | Botón deshabilitado con aviso | RNF28 |
| Falla al guardar | Se revierte todo; sin evento | RNF15 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Domicilio.asignar`, `CodigoConfirmacion.generar` (6 dígitos, con ceros a la izquierda) | FR-005 |
| Integración | Endpoint con Testcontainers: aceptación (estado, domiciliario, código guardado y no devuelto, evento de trazabilidad, E09), ya asignado, cancelado, cuerpo con valor, rol equivocado, repetición con la misma clave | US1-E1, FR-001, FR-003 |
| Concurrencia | N domiciliarios aceptan a la vez: exactamente uno gana y un solo código y evento | US1-E2, SC-001 |
| Frontend | Jest: botón, navegación a la ruta, error de no disponible, sin conexión | SC-003 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-19-aceptacion-de-domicilio/
├── RF-19-aceptacion-de-domicilio.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── domain/
│   ├── model/                    # Domicilio.asignar, CodigoConfirmacion
│   └── event/                    # DomicilioAsignadoEvent (E09)
├── application/
│   ├── ports/in/                 # AceptarDomicilioPort
│   ├── ports/out/                # DomicilioRepositoryPort (asignarAtomico), PedidoParaDomicilioPort, EventPublisherPort (shared)
│   └── usecase/                  # AceptarDomicilioUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (POST /domicilios/{id}/aceptar)
    └── out/persistence/          # UPDATE condicional
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/components/domicilios/AccionesDomicilioDisponible.tsx   # (RF-18) se completa el botón Aceptar
frontend/src/services/domicilios.ts                                  # aceptarDomicilio() con conexión
frontend/src/database/schema/domicilio_activo.ts                     # id del domicilio activo del domiciliario
frontend/__tests__/domicilios/aceptar.test.tsx
```

**Structure Decision**: módulo `domicilios`. La exclusividad se resuelve en la base de datos con una actualización condicional (RNF08, foundations), sin bloqueos largos. El código de confirmación se genera en el dominio (`CodigoConfirmacion`) con `SecureRandom` y se guarda tal cual (D13). La app guarda el id del domicilio activo en SQLite para volver a él, mientras no exista el listado propio del domiciliario (Open Questions).

## Phase 1: Setup

- [ ] T001 Confirmar el estado `DISPONIBLE` (RF-14) y la pantalla de información (RF-18, T022)

## Phase 2: Foundational

- [ ] T002 `DomicilioRepositoryPort.asignarAtomico(id, domiciliarioId, codigo)` con `UPDATE ... WHERE estado = 'DISPONIBLE'` y su adaptador
- [ ] T003 `CodigoConfirmacion` (value object, 6 dígitos, `SecureRandom`) en `domicilios/domain/model/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Domiciliario se compromete con un domicilio (Priority: P1)

**Goal**: el domicilio queda asignado a un único domiciliario con su código listo.
**Independent Test**: aceptar un domicilio disponible y verlo asignado y fuera de la lista de los demás.
**Cubre**: FR-001–FR-005, SC-001–SC-003, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /domicilios/{id}/aceptar` (`ASIGNADO`, domiciliario, evento de trazabilidad, E09 en el outbox, respuesta sin código) en `backend/src/test/java/co/edu/napa/domicilios/AceptarDomicilioIT.java`
- [ ] T011 [P] [US1] Test de concurrencia: N aceptaciones simultáneas y exactamente una gana en `.../domicilios/AceptarDomicilioConcurrenciaIT.java`
- [ ] T012 [P] [US1] Test de que el código es de 6 dígitos, se guarda en la fila y no aparece en la respuesta ni en E09 en `.../domicilios/AceptarDomicilioCodigoIT.java` y unitario `backend/src/test/java/co/edu/napa/domicilios/CodigoConfirmacionTest.java`
- [ ] T019 [P] [US1] Test de domicilio ya asignado, cancelado y entregado (`409 NOT_AVAILABLE`), cuerpo con valor (`400`), rol equivocado (`403`), inexistente (`404`) y repetición con la misma clave en `.../domicilios/AceptarDomicilioEstadosIT.java`
- [ ] T020 [P] [US1] Test de la acción (aceptar, navegación a la ruta, `NOT_AVAILABLE` con regreso a la lista, sin conexión) en `frontend/__tests__/domicilios/aceptar.test.tsx`

### Implementation

- [ ] T013 [US1] `Domicilio.asignar(domiciliarioId, codigo)` en `domicilios/domain/model/Domicilio.java`
- [ ] T014 [US1] `AceptarDomicilioUseCase` y `POST /domicilios/{id}/aceptar` en `domicilios/`
- [ ] T015 [US1] Asignación atómica por el repositorio y mapeo de 0 filas a `NOT_AVAILABLE`
- [ ] T016 [US1] Evento de trazabilidad `ASIGNADO` con el domiciliario como autor
- [ ] T017 [US1] `DomicilioAsignadoEvent` (E09) y publicación por `EventPublisherPort`
- [ ] T018 [US1] Guardado del código en `domicilio.codigo_confirmacion` (sin exponerlo en el DTO de salida)
- [ ] T021 [US1] Servicio `aceptarDomicilio()` en `frontend/src/services/domicilios.ts` y guardado del domicilio activo en `frontend/src/database/schema/domicilio_activo.ts`
- [ ] T022 [US1] Botón "Aceptar" en `AccionesDomicilioDisponible` (RF-18) con estados de carga y error
- [ ] T024 [US1] Navegación a `frontend/src/app/(domiciliario)/domicilios/[id]/ruta.tsx` (RF-22) tras aceptar

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir aceptar en < 10 s y el aviso en < 1 min (SC-002, SC-003)
- [ ] T041 Revisar permisos y que el código no salga en logs ni eventos (RNF03, RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-14, RF-16 y RF-18; habilita RF-22, RF-23, RF-24 y RF-25.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: límite de domicilios simultáneos por domiciliario (punto abierto en spec-general) | Regla | Sin límite | Equipo |
| ¿Puede aceptar un domicilio que él mismo rechazó antes (RF-17)? El rechazo es "irreversible" pero solo filtra la lista | Regla | No se valida (RF-17 no existe todavía) | Equipo |
| El domiciliario necesita volver a su domicilio activo si cierra la app; el listado propio (RF-15) no es P1 | Orden de construcción | Guardar el id del domicilio activo en SQLite; incluir RF-15 en el orden | Equipo |
| `domiciliario activo` (cuenta ACTIVA) como condición para aceptar | Regla | Lo garantiza el filtro de autenticación (cuentas eliminadas o sin verificar no inician sesión) | Equipo |

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
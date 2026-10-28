# Implementation Plan: Rechazo de domicilios

**Date**: 2026-10-26
**Spec**: [RF-17-rechazo-de-domicilios.md](./RF-17-rechazo-de-domicilios.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-17
**Historias de usuario**: HU-37
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-14, RF-16, RF-18, RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El domiciliario descarta un domicilio `DISPONIBLE` con `POST /domicilios/{id}/rechazar`: se inserta una fila `domicilio_rechazo` (domiciliario, domicilio, fecha) que RF-16 ya usa para excluirlo para siempre de **su** lista, y el domicilio sigue disponible para los demás. El rechazo es personal: no cambia el estado del domicilio ni del pedido, no emite eventos ni notifica a nadie, no penaliza, no se puede revertir y no hay lista de rechazados. Si el domicilio ya fue tomado o cancelado, responde `409 NOT_AVAILABLE` y no registra nada. Repetir el rechazo del mismo domicilio no hace nada. La app agrega "Rechazar" (con confirmación) en la lista y en la información del domicilio y lo quita de la vista al confirmarse.

## Estado actual del código

La tabla `domicilio_rechazo` (clave compuesta domiciliario + domicilio) la crea RF-14 (V10) y RF-16 la usa para excluir los rechazados. RF-18 oculta el botón "Rechazar" hasta que exista este endpoint.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene ni se entera |
| Vendedor de punto fijo | No interviene ni se entera |
| Domiciliario | Rechaza domicilios |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Rechazo visible de inmediato y para siempre (SC-001): inserción confirmada antes de responder y exclusión en la consulta de RF-16.
- 0 % de impacto en los demás (SC-002): no se escribe en `domicilio`, `pedido` ni en nada de otros usuarios.
- Rechazar en < 5 s (SC-003): una sola llamada corta.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Rechazar un domicilio disponible y retirarlo para siempre de la lista propia | Fila `domicilio_rechazo` y exclusión de RF-16 | T010, T011, T020 |
| FR-002 | El domicilio sigue disponible para los demás | No se modifica el domicilio; los demás lo siguen viendo | T012 |
| FR-003 | Acción personal, sin notificar ni penalizar | Sin eventos, sin notificaciones, sin contadores | T013 |
| FR-004 | Sin lista de rechazados ni restauración | No existe endpoint de consulta ni de borrado | T014 |
| SC-001 | 100 % desaparecen de inmediato y para siempre | Inserción síncrona y consulta de RF-16 | T011 |
| SC-002 | 0 % de impacto en los demás | Prueba con dos domiciliarios | T012 |
| SC-003 | < 5 s | Una llamada; confirmación de un toque | T033 |
| US1-E1 | Rechazo de un disponible | `204`, fila creada | T010 |
| US1-E2 | Sin notificación ni penalización | Sin eventos | T013 |
| US1-E3 | Sin opción de revertir | Sin endpoints de consulta o borrado | T014 |
| US1-E4 | El domicilio ya fue tomado por otro | `409 NOT_AVAILABLE`, sin fila | T015 |
| Edge 1 | Límite de rechazos por período | Sin límite en el MVP (Open Questions) | — |
| Edge 2 | El administrador cancela después del rechazo | La fila queda; no tiene efecto visible | T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo DOMICILIARIO; el domiciliario sale de la sesión |
| RNF08 Concurrencia | ☑ | Inserción condicionada al estado `DISPONIBLE` en una sola sentencia |
| RNF23 Consistencia | ☑ | Clave compuesta: no hay duplicados |
| RNF24 Errores claros | ☑ | `409 NOT_AVAILABLE`, `404` |
| RNF27 Integridad | ☑ | `Idempotency-Key` y clave compuesta |
| RNF28 Conectividad | ☑ | Requiere conexión (Open Questions) |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| DomicilioRechazo | Escribe | PK (domiciliario, domicilio); irreversible; sin `updated_at`; no consultable |
| Domicilio | Lee | Debe estar `DISPONIBLE` al rechazar; no se modifica |

## State Transitions

El rechazo no cambia el estado del domicilio ni del pedido: `DISPONIBLE` sigue `DISPONIBLE`. Lo único que se crea es la relación (domiciliario, domicilio) en `domicilio_rechazo`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /domicilios/{id}/rechazar` | DOMICILIARIO | Cualquier domicilio `DISPONIBLE`; el domiciliario es el de la sesión |

## API Contracts

### POST /api/v1/domicilios/{id}/rechazar

- **Rol**: domiciliario · **Path params**: `id` (UUID del domicilio) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅. **Body**: ninguno.
- **Respuestas exitosas**: `204` sin cuerpo, con `X-Request-Id`. Rechazar de nuevo un domicilio ya rechazado por el mismo domiciliario, mientras siga `DISPONIBLE`, también responde `204` sin cambios.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` | — |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de domiciliario | — |
| 404 | `NOT_FOUND` | El domicilio no existe | "El domicilio no está disponible" |
| 409 | `NOT_AVAILABLE` | El domicilio ya está `ASIGNADO`, `RECOGIDO`, `EN_CAMINO`, `ENTREGADO` o `CANCELADO` | "Este domicilio ya no está disponible" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otra petición | — |

No existe ninguna ruta para listar o deshacer rechazos.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El rechazo es personal: no se notifica ni se publica evento (FR-003) |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la acción requiere respuesta del servidor: el domicilio puede haber sido tomado, y el domiciliario necesita saberlo. Sin conexión, el botón muestra el error y no oculta nada. Tras `204`, `409` o `404`, la app quita el domicilio de la lista y de la caché local de RF-16.
- **Concurrencia (RNF08)**: la inserción es `INSERT … SELECT … WHERE EXISTS (domicilio DISPONIBLE)` más `ON CONFLICT DO NOTHING`. Si otro domiciliario lo acepta antes, no inserta y responde `409`. Si lo acepta justo después, el rechazo queda registrado y no tiene efecto.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}` y la clave compuesta de la tabla.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Domicilio ya tomado o cancelado | `409 NOT_AVAILABLE`, sin fila | US1-E4 |
| Domicilio inexistente | `404` | — |
| Rechazo repetido | `204` sin cambios | FR-001 |
| Sin conexión | Error visible, nada cambia | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: rechazo de un disponible (fila creada, `204`), exclusión en RF-16 solo para ese domiciliario, el otro domiciliario lo sigue viendo, sin eventos ni notificaciones, domicilio tomado, cancelado e inexistente, rechazo repetido, roles, idempotencia | FR-001–FR-003, US1-E1–E4 |
| Concurrencia | Aceptar y rechazar a la vez: nunca queda un domicilio asignado afectado por el rechazo | RNF08 |
| Arquitectura | No existe ningún endpoint ni puerto de consulta o borrado de `domicilio_rechazo` | FR-004 |
| Frontend | Jest: confirmación, quitar de la lista, errores `409`, sin lista de rechazados | SC-003 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-17-rechazo-de-domicilios/
├── RF-17-rechazo-de-domicilios.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── application/
│   ├── ports/in/                 # RechazarDomicilioPort
│   ├── ports/out/                # DomicilioRechazoRepositoryPort (solo inserción)
│   └── usecase/                  # RechazarDomicilioUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (POST /domicilios/{id}/rechazar)
    └── out/persistence/          # inserción condicionada al estado DISPONIBLE
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/components/domicilios/AccionesDomicilioDisponible.tsx   # se habilita "Rechazar" (RF-18)
frontend/src/components/domicilios/TarjetaDomicilioDisponible.tsx    # acción de rechazar en la lista (RF-16)
frontend/src/components/domicilios/ConfirmarRechazo.tsx              # "No podrás deshacerlo"
frontend/src/services/domicilios.ts                                  # rechazarDomicilio()
frontend/__tests__/domicilios/rechazar.test.tsx
```

**Structure Decision**: módulo `domicilios` (dueño de `DomicilioRechazo`). El puerto de salida solo inserta: no hay método de lectura ni de borrado para que el rechazo sea irreversible por construcción. La exclusión de la lista la hace la consulta de RF-16, que ya existe.

## Phase 1: Setup

- [ ] T001 Confirmar la tabla `domicilio_rechazo` (RF-14 T002) y la exclusión en la consulta de RF-16

## Phase 2: Foundational

- [ ] T002 `DomicilioRechazoRepositoryPort` con un único método de inserción condicionada (`INSERT … SELECT … WHERE EXISTS … ON CONFLICT DO NOTHING`) y su adaptador

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Descartar domicilios que no me interesan (Priority: P2)

**Goal**: el domiciliario depura su lista sin afectar a nadie.
**Independent Test**: rechazar un domicilio y comprobar que no vuelve a su lista pero sigue en la del otro domiciliario.
**Cubre**: FR-001–FR-004, SC-001–SC-003, US1-E1–E4, Edge 2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /domicilios/{id}/rechazar` con un domicilio `DISPONIBLE` (`204`, fila con fecha) en `backend/src/test/java/co/edu/napa/domicilios/RechazarDomicilioIT.java`
- [ ] T011 [P] [US1] Test de integración: el domicilio rechazado desaparece de `GET /domicilios/disponibles` del domiciliario de inmediato y nunca vuelve en `.../domicilios/RechazarExcluyeDeListaIT.java`
- [ ] T012 [P] [US1] Test de integración: otro domiciliario lo sigue viendo y puede aceptarlo; el domicilio no cambió de estado en `.../domicilios/RechazarNoAfectaAOtrosIT.java`
- [ ] T013 [P] [US1] Test de integración: sin eventos en el outbox y sin notificaciones al vendedor, al cliente ni al domiciliario en `.../domicilios/RechazarSinNotificacionesIT.java`
- [ ] T014 [P] [US1] Test de arquitectura: no existe puerto, endpoint ni consulta que lea o borre `domicilio_rechazo` fuera de la exclusión de RF-16 en `.../domicilios/RechazoIrreversibleArchTest.java`
- [ ] T015 [P] [US1] Test de integración: domicilio `ASIGNADO`, `RECOGIDO`, `EN_CAMINO`, `ENTREGADO` o `CANCELADO` da `409 NOT_AVAILABLE` sin fila; inexistente `404` en `.../domicilios/RechazarNoDisponibleIT.java`
- [ ] T016 [P] [US1] Test de integración: cancelación posterior del administrador no afecta ni revela el rechazo, y rechazo repetido (`204` sin duplicar) en `.../domicilios/RechazarRepetidoIT.java`
- [ ] T017 [P] [US1] Test de idempotencia (misma clave, clave reutilizada `422`, falta de clave `400`) y de rol no domiciliario (`403`) en `.../domicilios/RechazarIdempotenciaIT.java`
- [ ] T018 [P] [US1] Test de concurrencia: aceptar y rechazar a la vez sobre el mismo domicilio en `.../domicilios/RechazarConcurrenciaIT.java`
- [ ] T030 [P] [US1] Test de la confirmación, del retiro de la tarjeta, del manejo de `409` y `404`, y de que no hay lista de rechazados en `frontend/__tests__/domicilios/rechazar.test.tsx`

### Implementation

- [ ] T020 [US1] `RechazarDomicilioUseCase` y `RechazarDomicilioPort` en `domicilios/application/`
- [ ] T021 [US1] `POST /domicilios/{id}/rechazar` en `DomicilioController` con `204`
- [ ] T022 [US1] Traducción de "0 filas insertadas con domicilio no disponible" a `NOT_AVAILABLE` y de "domicilio inexistente" a `NOT_FOUND`
- [ ] T031 [US1] `rechazarDomicilio()` en `frontend/src/services/domicilios.ts` (sin outbox, requiere conexión)
- [ ] T032 [US1] `ConfirmarRechazo` y acción "Rechazar" en `TarjetaDomicilioDisponible` y `AccionesDomicilioDisponible` (se quita el ocultamiento de RF-18)
- [ ] T033 [US1] Retiro del domicilio de la lista y de la caché local de RF-16 tras `204`, `409` o `404`

**Checkpoint**: el rechazo funciona de extremo a extremo.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir el tiempo de rechazo con domiciliarios del piloto (SC-003)
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-14 (tabla), RF-16 (lista que excluye) y RF-18 (pantalla); RF-19 sigue aceptando por `UPDATE … WHERE estado = 'DISPONIBLE'` sin consultar los rechazos.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: la spec pregunta si debe haber un límite de rechazos por período | Regla | Sin límite en el MVP | Equipo |
| La ruta `POST /domicilios/{id}/rechazar` no está en spec-general | Contrato | Agregarla (domiciliario, con `Idempotency-Key`) | Equipo |
| Rechazar con `204` sin cuerpo, en vez de `200` con cuerpo | Contrato | `204` | Equipo |
| El rechazo no tiene efecto si el domiciliario ya aceptó otro domicilio: no puede rechazar uno `ASIGNADO` | Regla | `409 NOT_AVAILABLE` | Equipo |
| El rechazo requiere conexión (no usa el outbox) para informar si el domicilio ya fue tomado | RNF28 | Requiere conexión | Equipo |
| Un domiciliario con un domicilio activo puede rechazar otros (RF-16 le deja ver la lista) | Regla | Permitido | Equipo |

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
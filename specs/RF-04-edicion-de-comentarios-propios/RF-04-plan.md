# Implementation Plan: Edición de comentarios propios

**Date**: 2026-10-26
**Spec**: [RF-04-edicion-de-comentarios-propios.md](./RF-04-edicion-de-comentarios-propios.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-04
**Historias de usuario**: HU-21
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-01, RF-02, RF-10
**Habilita a**: RF-05 y RF-06 (comparten estados)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El autor de una calificación la corrige con `PATCH /calificaciones/{id}`: puede cambiar el puntaje (1–5), el comentario (no vacío, hasta 300 caracteres) o ambos. Solo el autor puede hacerlo (a cualquier otro, incluido el administrador, `403`) y solo mientras la calificación esté `ACTIVA`: si el administrador la eliminó o el autor la retiró, responde `409`. El cambio se ve de inmediato para los demás: el promedio de RF-02 se calcula en cada consulta, así que un cambio de puntaje lo mueve al instante. No se guarda historial de ediciones. La app muestra "Editar" en las calificaciones propias (el listado de RF-02 las marca con `esMia`) y reutiliza el formulario de RF-01 con los valores actuales.

## Estado actual del código

RF-01 crea la calificación y su formulario; RF-02 la lista y marca las propias con `esMia` (se agrega con este plan); RF-06 permite al administrador editar el texto y eliminar. Esta es la edición del propio autor, que la spec define como `Comentario`/`Calificacion` editable solo por su autor.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Edita sus calificaciones |
| Vendedor ambulante y de punto fijo | Editan sus calificaciones |
| Domiciliario | Edita sus calificaciones |
| Administrador | No edita las de otros por esta vía (usa RF-06) |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Cambios visibles para otros en < 5 s (SC-002): escritura directa y lectura sin caché del servidor; solo el caché local de la app puede retrasarlo.
- 100 % de las ediciones ajenas rechazadas (SC-001): la pertenencia se verifica en el caso de uso con el autor guardado, nunca con datos del cliente.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Editar únicamente lo que el usuario publicó | Verificación de autor en el caso de uso | T013, T020 |
| FR-002 | Validar contenido no vacío y de hasta 300 caracteres | Bean Validation y regla de `Calificacion.editar` | T012, T017 |
| FR-003 | Reflejar de inmediato los cambios | Escritura directa; RF-02 lee sin caché | T010, T011 |
| SC-001 | 100 % de ediciones ajenas rechazadas | `403 FORBIDDEN` | T013 |
| SC-002 | Cambios visibles en < 5 s | Lectura directa y revalidación en la app | T010, T035 |
| US1-E1 | Edición exitosa del texto o la calificación | `200` con los valores nuevos | T010, T011 |
| US1-E2 | Intento de editar un comentario ajeno | `403` | T013 |
| Edge 1 | Editar uno eliminado por un administrador | `409 INVALID_STATE` con `estadoActual` | T014 |
| Edge 2 | ¿Se conserva historial de ediciones? | No; solo el estado más reciente (Open Questions) | — |
| Edge 3 | La edición deja el comentario vacío | `400`; quien no quiere comentario no lo envía | T012 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el autor |
| RNF08 Concurrencia | ☑ | Bloqueo de la fila frente a una moderación o eliminación simultánea |
| RNF23 Consistencia | ☑ | Solo se edita en estado `ACTIVA` |
| RNF24 Errores claros | ☑ | `400`, `403`, `409` con mensaje |
| RNF28 Conectividad | ☑ | Envío por el outbox |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Calificacion | Escribe | Solo `ACTIVA`; `puntaje` 1–5; `comentario` no vacío y ≤ 300; el destino y el pedido no cambian; `updated_at` |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Calificacion | `ACTIVA` | `ACTIVA` (valores nuevos) | El autor edita | Autor; al menos un cambio válido | Promedio de RF-02 recalculado al consultar |

Desde `ELIMINADA_AUTOR` o `ELIMINADA_MODERACION` no se edita (`409 INVALID_STATE`).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `PATCH /calificaciones/{id}` | CLIENTE, VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO, DOMICILIARIO | El autor de la calificación; a otro usuario, incluido el administrador, `403` |

## API Contracts

### PATCH /api/v1/calificaciones/{id}

- **Rol**: autor de la calificación · **Path params**: `id` · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Content-Type: application/json`. No exige `Idempotency-Key` (spec-general §2.4 solo la exige en los `POST`): repetir la misma edición es inocuo.
- **Body**: al menos uno de los dos campos:

```json
{ "puntaje": 4, "comentario": "Llegó bien, pero tarde" }
```

Un campo ausente no se modifica. `comentario` nulo o vacío se rechaza.

- **Respuestas exitosas**: `200` con `X-Request-Id` y body `{ "id": "…", "puntaje": 4, "comentario": "…", "estado": "ACTIVA", "updatedAt": "…" }`. Si los valores enviados son iguales a los actuales, responde `200` sin cambios y sin tocar `updatedAt`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo sin campos; puntaje nulo, fuera de 1–5 o no entero; comentario nulo, vacío, en blanco o de más de 300 | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | La calificación es de otro usuario (o el administrador) | "Solo puedes editar tus calificaciones" |
| 404 | `NOT_FOUND` | La calificación no existe | "No encontramos la calificación" |
| 409 | `INVALID_STATE` | La calificación no está `ACTIVA` (`details.estadoActual`) | "Esta calificación ya fue eliminada" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El catálogo no define evento de edición |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la edición va por el outbox (es idempotente por naturaleza); la pantalla muestra el valor nuevo marcado "pendiente de envío". Si el servidor responde `409`, el outbox la descarta y la app avisa que la calificación ya no existe.
- **Concurrencia (RNF08)**: `SELECT … FOR UPDATE` sobre la calificación; si el administrador la elimina a la vez, gana quien bloquea primero y el otro recibe `409` o ve el estado final.
- **Idempotencia (RNF27)**: la edición con los mismos valores es un no-op `200`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Calificación ajena | `403` | FR-001, US1-E2 |
| Eliminada por un administrador o por el autor | `409 INVALID_STATE` | Edge 1 |
| Comentario vacío | `400` | Edge 3, FR-002 |
| Más de 300 caracteres | `400` | FR-002 |
| Mismos valores | `200` sin cambios | Open Questions |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Calificacion.editar` (puntaje, comentario, estado, sin cambios) | FR-002 |
| Integración | Endpoint con Testcontainers: cambiar texto, cambiar puntaje, ambos; el promedio de RF-02 cambia al instante; validaciones; ajena, administrador e inexistente; estados no `ACTIVA`; sin cambios; concurrencia con la moderación | FR-001–FR-003, US1-E1–E2, Edge 1–3 |
| Arquitectura | `comentarios` no depende de otros módulos para editar | RNF13 |
| Frontend | Jest: formulario en modo edición con valores actuales, menú solo en las propias, outbox sin red, `409` | US1 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-04-edicion-de-comentarios-propios/
├── RF-04-edicion-de-comentarios-propios.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/comentarios/
├── domain/
│   ├── model/                    # Calificacion.editar
│   └── exception/                # CalificacionNoEditableException
├── application/
│   ├── ports/in/                 # EditarCalificacionPort
│   ├── ports/out/                # CalificacionRepositoryPort (bloquearPorId)
│   └── usecase/                  # EditarCalificacionUseCase
└── infrastructure/adapters/in/web/   # CalificacionController (PATCH /calificaciones/{id}), DTO
backend/src/test/java/co/edu/napa/comentarios/

frontend/src/app/(comun)/calificaciones/[id]/editar.tsx            # ruta Expo Router: edición de mi calificación
frontend/src/components/calificaciones/FormularioCalificacion.tsx  # (RF-01) con modo edición
frontend/src/components/calificaciones/ItemCalificacion.tsx        # (RF-02) menú Editar / Eliminar cuando esMia
frontend/src/services/calificaciones.ts                            # editarCalificacion() por el outbox
frontend/__tests__/calificaciones/editar-calificacion.test.tsx
```

**Structure Decision**: módulo `comentarios`: la edición es una operación del agregado `Calificacion`, que ya concentra sus reglas de estado. `PATCH /calificaciones/{id}` comparte la ruta base con RF-06 (`moderar-texto` es una acción distinta del administrador). El listado de RF-02 agrega `esMia` para que la app sepa cuándo ofrecer editar y eliminar sin una consulta extra.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `comentarios` y que `GET /calificaciones` (RF-02) devuelve `esMia` en cada elemento

## Phase 2: Foundational

- [ ] T002 Método `Calificacion.editar(puntaje?, comentario?)` y excepción `CalificacionNoEditableException` en `comentarios/domain/`
- [ ] T003 `CalificacionRepositoryPort.bloquearPorId` y su adaptador (`FOR UPDATE`)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - El usuario corrige una calificación publicada (Priority: P2)

**Goal**: el autor mantiene al día lo que publicó.
**Independent Test**: calificar, editar el texto y el puntaje, y ver el cambio y el nuevo promedio en RF-02.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1, Edge 3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `PATCH /calificaciones/{id}` cambiando el texto (`200`, y `GET /calificaciones` lo muestra de inmediato) en `backend/src/test/java/co/edu/napa/comentarios/EditarCalificacionIT.java`
- [ ] T011 [P] [US1] Test de integración: cambiar el puntaje (y ambos) mueve el promedio de RF-02 en la siguiente consulta en `.../comentarios/EditarCalificacionPromedioIT.java`
- [ ] T012 [P] [US1] Test parametrizado de cuerpo sin campos, puntaje nulo, 0, 6 y decimal, comentario nulo, vacío, en blanco y de 301 caracteres (`400`) en `.../comentarios/EditarCalificacionValidacionIT.java`
- [ ] T013 [P] [US1] Test de calificación de otro usuario (`403`), administrador (`403`), inexistente (`404`) y sin sesión (`401`) en `.../comentarios/EditarCalificacionAutorizacionIT.java`
- [ ] T014 [P] [US1] Test de calificación `ELIMINADA_MODERACION` y `ELIMINADA_AUTOR` (`409 INVALID_STATE` con `estadoActual`) en `.../comentarios/EditarCalificacionEstadoIT.java`
- [ ] T015 [P] [US1] Test de edición con los mismos valores (`200`, `updatedAt` intacto) y de un comentario previamente editado por el administrador (se puede volver a editar) en `.../comentarios/EditarCalificacionSinCambiosIT.java`
- [ ] T016 [P] [US1] Test de concurrencia: el autor edita mientras el administrador elimina en `.../comentarios/EditarCalificacionConcurrenciaIT.java`
- [ ] T017 [P] [US1] Test unitario de `Calificacion.editar` en `.../comentarios/CalificacionEditarTest.java`
- [ ] T030 [P] [US1] Test del formulario en modo edición (valores actuales, validación, outbox sin red, `409`) en `frontend/__tests__/calificaciones/editar-calificacion.test.tsx`
- [ ] T031 [P] [US1] Test de que el menú "Editar" solo aparece en las calificaciones con `esMia` en `frontend/__tests__/calificaciones/item-calificacion-menu.test.tsx`

### Implementation

- [ ] T020 [US1] `EditarCalificacionUseCase` y `EditarCalificacionPort` (bloqueo, autor, estado, edición) en `comentarios/application/`
- [ ] T021 [US1] `PATCH /calificaciones/{id}` y su DTO en `CalificacionController`
- [ ] T032 [US1] Menú "Editar" en `ItemCalificacion` cuando `esMia`
- [ ] T033 [US1] Modo edición de `FormularioCalificacion` y pantalla `frontend/src/app/(comun)/calificaciones/[id]/editar.tsx`
- [ ] T034 [US1] `editarCalificacion()` por el outbox en `frontend/src/services/calificaciones.ts`
- [ ] T035 [US1] Invalidación de la caché local de RF-02 tras editar

**Checkpoint**: el autor edita sus calificaciones.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir que el cambio se refleja en menos de 5 segundos (SC-002)
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-01 (calificación y formulario) y RF-02 (`esMia` y el listado); convive con RF-05 y RF-06, que comparten estados y bloqueo.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 2: la spec pregunta si se conserva historial de ediciones | Datos | No: solo el estado más reciente y `updated_at`; el modelo no tiene tabla de historial | Equipo |
| Editar los mismos valores responde `200` sin cambios (en RF-06 un texto igual al actual es `400`, porque allí cada acción crea un registro de moderación) | Contrato | `200` no-op, para que el reintento del outbox sea inocuo | Equipo |
| ¿Hay un plazo máximo para editar? | Regla | Sin plazo | Equipo |
| Editar el puntaje cambia el promedio de la entidad (la spec lo permite: "el texto o la calificación") | Regla | Sí, al instante, porque el promedio es derivado | Equipo |
| El autor puede volver a editar un comentario que el administrador editó (RF-06) | Regla | Sí; el administrador puede moderarlo otra vez | Equipo |
| `PATCH /calificaciones/{id}` no está en spec-general §2.4 | Contrato | Agregarlo (autor, sin `Idempotency-Key`) | Equipo |
| `esMia` en el listado de RF-02 es un campo nuevo | Contrato | Agregarlo al contrato de RF-02 (este plan ya lo incluye allí) | Equipo |
| `403` para calificaciones ajenas (en RF-60 los recursos ajenos dan `404`), porque los ids de calificaciones son públicos | Consistencia | `403` | Equipo |

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
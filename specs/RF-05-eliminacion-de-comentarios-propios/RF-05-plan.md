# Implementation Plan: Eliminación de comentarios propios

**Date**: 2026-10-26
**Spec**: [RF-05-eliminacion-de-comentarios-propios.md](./RF-05-eliminacion-de-comentarios-propios.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-05
**Historias de usuario**: HU-20
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-01, RF-02, RF-04, RF-10
**Habilita a**: RF-06 (T019 de la prueba de moderación sobre `ELIMINADA_AUTOR`)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El autor retira su calificación con `POST /calificaciones/{id}/eliminar`: pasa a `ELIMINADA_AUTOR`, deja de verse en la vista pública y sale del promedio (que es derivado, así que se recalcula en la siguiente consulta de RF-02). Es una eliminación lógica: la fila, con su puntaje y su comentario, se conserva para auditoría y queda con la fecha del cambio. Solo el autor puede hacerlo (a cualquier otro, `403`) y solo mientras esté `ACTIVA`: si ya la eliminó un administrador o el propio autor, responde `409`. La calificación eliminada no se puede volver a hacer sobre el mismo pedido y destino (el único de RF-01 cuenta todos los estados). La app muestra "Eliminar" con confirmación en las calificaciones propias y lo envía por el outbox.

## Estado actual del código

El estado `ELIMINADA_AUTOR` existe en el modelo pero nada lo produce todavía. RF-02 ya excluye las eliminadas del listado y del promedio; RF-06 elimina por moderación con `ELIMINADA_MODERACION`; RF-04 edita.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Elimina sus calificaciones |
| Vendedor ambulante y de punto fijo | Eliminan sus calificaciones |
| Domiciliario | Elimina sus calificaciones |
| Administrador | No elimina las de otros por esta vía (usa RF-06) |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Una calificación eliminada deja de verse en < 5 s (SC-002): cambio de estado confirmado y lectura sin caché del servidor.
- 100 % de las eliminaciones ajenas rechazadas (SC-001): autor verificado en el caso de uso.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Eliminar únicamente lo que el usuario publicó | Verificación de autor en el caso de uso | T013, T020 |
| FR-002 | Recalcular el promedio tras eliminar | La eliminada sale del promedio derivado de RF-02 | T011 |
| FR-003 | Registrar la eliminación para auditoría | Eliminación lógica: estado `ELIMINADA_AUTOR`, fila y texto conservados, `updated_at` | T012 |
| SC-001 | 100 % de eliminaciones ajenas rechazadas | `403 FORBIDDEN` | T013 |
| SC-002 | < 5 s para dejar de verse | Cambio de estado y lectura directa | T010, T033 |
| US1-E1 | Eliminación exitosa | `200`, `ELIMINADA_AUTOR`, fuera de la vista pública | T010 |
| US1-E2 | Intento de eliminar una ajena | `403` | T013 |
| Edge 1 | Efecto en el promedio cuando incluía puntaje | Se retira la calificación completa, puntaje incluido | T011 |
| Edge 2 | ¿Se conserva el comentario para auditoría? | Sí, en la misma fila | T012 |
| Edge 3 | Eliminar una ya eliminada por un administrador | `409 INVALID_STATE` con `estadoActual` | T014 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el autor |
| RNF08 Concurrencia | ☑ | Bloqueo de la fila frente a edición o moderación simultánea |
| RNF16 Trazabilidad | ☑ | La fila se conserva con su estado y fecha |
| RNF21 Auditoría | ☑ | Nunca se borra físicamente |
| RNF23 Consistencia | ☑ | Solo se elimina en estado `ACTIVA` |
| RNF24 Errores claros | ☑ | `403`, `409` con mensaje |
| RNF27 Integridad | ☑ | `Idempotency-Key` |
| RNF28 Conectividad | ☑ | Envío por el outbox |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Calificacion | Escribe | `ACTIVA` → `ELIMINADA_AUTOR`; el puntaje y el comentario no se modifican ni se borran; `updated_at` |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Calificacion | `ACTIVA` | `ELIMINADA_AUTOR` | El autor elimina | Autor | Sale de la lista y del promedio de RF-02 |

Desde `ELIMINADA_AUTOR` o `ELIMINADA_MODERACION` no hay transición (`409 INVALID_STATE`). No hay forma de reactivarla.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /calificaciones/{id}/eliminar` | CLIENTE, VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO, DOMICILIARIO | El autor de la calificación; a otro usuario, incluido el administrador, `403` |

## API Contracts

### POST /api/v1/calificaciones/{id}/eliminar

- **Rol**: autor de la calificación · **Path params**: `id` · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅. **Body**: ninguno.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body `{ "id": "…", "estado": "ELIMINADA_AUTOR" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` | — |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | La calificación es de otro usuario (o el administrador) | "Solo puedes eliminar tus calificaciones" |
| 404 | `NOT_FOUND` | La calificación no existe | "No encontramos la calificación" |
| 409 | `INVALID_STATE` | No está `ACTIVA` (`details.estadoActual`) | "Esta calificación ya fue eliminada" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otra petición | — |

El reintento con la misma `Idempotency-Key` devuelve la respuesta original.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El catálogo no define evento de eliminación por el autor y no hay a quién avisar |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la eliminación va por el outbox con su `Idempotency-Key`; la pantalla oculta la calificación de inmediato y, si el servidor responde `409`, la deja oculta (ya estaba eliminada).
- **Concurrencia (RNF08)**: bloqueo de la fila; si el administrador la elimina o el autor la edita a la vez, gana quien bloquea primero.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`; un segundo intento sin clave igual responde `409 INVALID_STATE`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Calificación ajena | `403` | FR-001, US1-E2 |
| Ya eliminada por el administrador o por el autor | `409 INVALID_STATE` | Edge 3 |
| Calificación inexistente | `404` | — |
| Sin conexión | Outbox | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Calificacion.eliminarPorAutor` (estados, puntaje y comentario intactos) | FR-003 |
| Integración | Endpoint con Testcontainers: eliminar; desaparece de `GET /calificaciones`; el promedio se recalcula; la fila se conserva con su texto; ajena, administrador e inexistente; ya eliminada por el administrador y por el autor; idempotencia; no se puede volver a calificar el mismo destino | FR-001–FR-003, US1-E1–E2, Edge 1–3 |
| Concurrencia | Eliminar mientras el administrador modera o el autor edita | RNF08 |
| Frontend | Jest: confirmación, menú solo en las propias, desaparición inmediata, `409`, outbox sin red | US1 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-05-eliminacion-de-comentarios-propios/
├── RF-05-eliminacion-de-comentarios-propios.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/comentarios/
├── domain/
│   └── model/                    # Calificacion.eliminarPorAutor
├── application/
│   ├── ports/in/                 # EliminarCalificacionPropiaPort
│   └── usecase/                  # EliminarCalificacionPropiaUseCase
└── infrastructure/adapters/in/web/   # CalificacionController (POST /calificaciones/{id}/eliminar)
backend/src/test/java/co/edu/napa/comentarios/

frontend/src/components/calificaciones/ItemCalificacion.tsx        # (RF-02) acción Eliminar cuando esMia
frontend/src/components/calificaciones/ConfirmarEliminarCalificacion.tsx
frontend/src/services/calificaciones.ts                            # eliminarCalificacion() por el outbox
frontend/__tests__/calificaciones/eliminar-calificacion.test.tsx
```

**Structure Decision**: módulo `comentarios`, junto a la edición y la moderación, que comparten el bloqueo de la fila y los estados. La eliminación es lógica: no hay tabla de auditoría adicional porque el propio estado y la fila conservada cumplen FR-003 (Open Questions).

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `comentarios`, `CalificacionRepositoryPort.bloquearPorId` (RF-04 T003) y `esMia` en RF-02

## Phase 2: Foundational

- [ ] T002 Método `Calificacion.eliminarPorAutor` en `comentarios/domain/model/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - El usuario retira una calificación propia (Priority: P2)

**Goal**: el autor controla lo que publicó.
**Independent Test**: calificar, eliminar y verificar que no se ve y que el promedio se recalcula.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /calificaciones/{id}/eliminar` (`200`, `ELIMINADA_AUTOR`, ya no aparece en `GET /calificaciones`) en `backend/src/test/java/co/edu/napa/comentarios/EliminarCalificacionPropiaIT.java`
- [ ] T011 [P] [US1] Test de integración: el promedio y el total de RF-02 se recalculan sin la calificación eliminada, incluida la que tenía puntaje y comentario, en `.../comentarios/EliminarRecalculaPromedioIT.java`
- [ ] T012 [P] [US1] Test de integración: la fila, el puntaje y el comentario se conservan con la fecha del cambio y no se exponen a otros roles en `.../comentarios/EliminarConservaAuditoriaIT.java`
- [ ] T013 [P] [US1] Test de calificación de otro usuario (`403`), administrador (`403`), inexistente (`404`) y sin sesión (`401`) en `.../comentarios/EliminarCalificacionAutorizacionIT.java`
- [ ] T014 [P] [US1] Test de calificación ya `ELIMINADA_MODERACION` o `ELIMINADA_AUTOR` (`409 INVALID_STATE` con `estadoActual`) en `.../comentarios/EliminarCalificacionEstadoIT.java`
- [ ] T015 [P] [US1] Test de idempotencia (misma clave repite la respuesta, clave reutilizada `422`, falta de clave `400`) y de que no se puede volver a calificar el mismo destino y pedido (`409 CONFLICT` de RF-01) en `.../comentarios/EliminarCalificacionIdempotenciaIT.java`
- [ ] T016 [P] [US1] Test de concurrencia: eliminar mientras el administrador modera, y mientras el autor edita en `.../comentarios/EliminarCalificacionConcurrenciaIT.java`
- [ ] T017 [P] [US1] Test unitario de `Calificacion.eliminarPorAutor` en `.../comentarios/CalificacionEliminarTest.java`
- [ ] T030 [P] [US1] Test de la confirmación, de la desaparición inmediata, del menú solo en las propias y del outbox sin red en `frontend/__tests__/calificaciones/eliminar-calificacion.test.tsx`

### Implementation

- [ ] T020 [US1] `EliminarCalificacionPropiaUseCase` y su puerto (bloqueo, autor, estado) en `comentarios/application/`
- [ ] T021 [US1] `POST /calificaciones/{id}/eliminar` en `CalificacionController`
- [ ] T031 [US1] `eliminarCalificacion()` por el outbox en `frontend/src/services/calificaciones.ts`
- [ ] T032 [US1] `ConfirmarEliminarCalificacion` y acción "Eliminar" en `ItemCalificacion` cuando `esMia`
- [ ] T033 [US1] Retiro inmediato de la calificación de la lista y de la caché local de RF-02

**Checkpoint**: el autor puede retirar sus calificaciones.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir que deja de verse en menos de 5 segundos (SC-002)
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-01 y RF-02; reutiliza el bloqueo de RF-04; la prueba T019 de RF-06 depende de este plan.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| FR-003 pide "registrar la eliminación para fines de auditoría"; el modelo no tiene tabla para eliminaciones del autor (`ModeracionComentario` es solo del administrador) | Modelo | El estado `ELIMINADA_AUTOR`, la fila conservada y `updated_at` bastan. Si se quiere quién y cuándo en una tabla aparte, hay que agregarla al modelo | Equipo |
| Tras eliminar, el autor no puede volver a calificar el mismo destino en ese pedido (el único de RF-01 cuenta todos los estados) | Regla | Se mantiene, porque RF-01 FR-006 pide una sola calificación por interacción. Si prefieres permitir recalificar, hay que cambiar el único y RF-01 | Equipo |
| Edge 1: eliminar retira el puntaje junto con el comentario (no se puede quitar solo el comentario) | Regla | Calificación completa. Quitar solo el comentario no existe en la spec (RF-04 rechaza un comentario vacío) | Equipo |
| `POST /calificaciones/{id}/eliminar` no está en spec-general §2.4 | Contrato | Agregarlo (autor, con `Idempotency-Key`) | Equipo |
| Sin evento ni notificación al calificado cuando se elimina | Catálogo | Ninguno | Equipo |

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
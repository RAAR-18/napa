# Implementation Plan: Moderación de comentarios

**Date**: 2026-10-27
**Spec**: [RF-06-moderacion-de-comentarios.md](./RF-06-moderacion-de-comentarios.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-06
**Historias de usuario**: HU-22, HU-23
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-01, RF-02, RF-38 (listener E17), RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador modera las calificaciones con comentario desde la app: lista (`GET /calificaciones` en modo administrador, que RF-02 deja preparado), **elimina** una calificación (`POST /calificaciones/{id}/eliminar-moderacion`, pasa a `ELIMINADA_MODERACION` y sale del promedio) o **edita solo el texto** del comentario (`PATCH /calificaciones/{id}/moderar-texto`, el puntaje y el promedio no cambian). Cada acción exige un **motivo** (por qué se moderó) y crea un registro `ModeracionComentario` (acción, administrador, fecha, motivo y el texto anterior) en la misma transacción, lo que distingue la moderación de cualquier acción del autor, y emite E17 `ComentarioModeradoEvent` con el motivo para avisar al autor (el aviso le dice qué se hizo y por qué) (el listener ya lo planea RF-38). La app del administrador incluye la pantalla de moderación con filtros por estado, entidad y texto.

## Estado actual del código

No existen `ModeracionComentario`, los endpoints de moderación ni el modo administrador de `GET /calificaciones`. RF-01 crea `calificacion` y RF-02 el endpoint de consulta para los demás roles. El listener de E17 en `notificaciones` está planeado en RF-38 (T028).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Administrador | Lista, elimina y edita comentarios |
| Autor del comentario | Recibe la notificación de la acción (E17) |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- 100 % de las acciones registradas con administrador, fecha y tipo (SC-001): el registro se inserta en la misma transacción del cambio.
- Comentario moderado visible en < 5 s (SC-002): la consulta lee la base directamente; solo el caché local de la app puede retrasarlo.
- 100 % de los autores notificados (SC-003): E17 se publica con el outbox en la misma transacción y el listener es idempotente (RF-38).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El administrador elimina cualquier comentario publicado | `POST /calificaciones/{id}/eliminar-moderacion` con `motivo` | T010, T020, T021 |
| FR-002 | El administrador edita solo el texto | `PATCH /calificaciones/{id}/moderar-texto`; el puntaje no se toca | T040, T042, T043 |
| FR-003 | Recalcular el promedio solo tras eliminar | Estado `ELIMINADA_MODERACION` sale de la consulta de RF-02; la edición deja `ACTIVA` | T011, T041 |
| FR-004 | Registrar que fue una acción de moderación | `ModeracionComentario` (con administrador, fecha, `motivo` y texto anterior) y estado propio de la calificación | T012, T022 |
| FR-005 | Notificar al autor | E17 con el outbox; el aviso incluye la acción y el `motivo` | T013, T023 |
| SC-001 | 100 % registradas con responsable, fecha y tipo | Registro en la misma transacción | T012 |
| SC-002 | Reflejado en < 5 s | Consulta directa; revalidación en la app | T014, T032 |
| SC-003 | 100 % notificados | E17 y listener de RF-38 | T013 |
| US1-E1 | Eliminar un comentario que incumple | `200`, estado `ELIMINADA_MODERACION` | T010 |
| US2-E1 | Editar un comentario | `200`, texto nuevo, mismo puntaje | T040 |
| Edge 1 | Eliminar un comentario que el autor editó | RF-04 permite la edición del autor; el administrador modera sobre el texto vigente | T019 |
| Edge 2 | ¿Se notifica al autor? | Sí, E17 | T013 |
| Edge 3 | Distinguir eliminación del autor de la de moderación | Estados `ELIMINADA_AUTOR` y `ELIMINADA_MODERACION`, y `ModeracionComentario` | T012 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR |
| RNF16 Trazabilidad | ☑ | `ModeracionComentario` con el texto anterior |
| RNF21 Auditoría | ☑ | Administrador, fecha y acción de cada moderación |
| RNF18 Notificaciones | ☑ | E17 al autor |
| RNF23 Consistencia | ☑ | Cambio, registro y evento en una sola transacción |
| RNF24 Errores claros | ☑ | `409 INVALID_STATE` con `estadoActual`, `400`, `404` |
| RNF27 Integridad | ☑ | `Idempotency-Key` en la eliminación |
| RNF08 Concurrencia | ☑ | Bloqueo de la fila de la calificación |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Calificacion | Escribe | Solo `ACTIVA` se puede moderar; el texto cambia en edición; el puntaje nunca |
| ModeracionComentario | Escribe | `accion` EDITADO o ELIMINADO; `motivo` obligatorio; `contenido_anterior`; administrador; sin `updated_at` |
| Usuario | Lee | Administrador que modera y autor que se notifica |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Calificacion | `ACTIVA` | `ELIMINADA_MODERACION` | `eliminar-moderacion` | Tiene comentario | Sale del promedio y de las listas; registro `ELIMINADO`; E17 |
| Calificacion | `ACTIVA` | `ACTIVA` (texto nuevo) | `moderar-texto` | Tiene comentario; texto distinto | Promedio igual; registro `EDITADO`; E17 |

Desde `ELIMINADA_MODERACION` o `ELIMINADA_AUTOR` no se puede moderar (`409 INVALID_STATE`).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /calificaciones` (modo administrador) | ADMINISTRADOR | Sin restricción |
| `POST /calificaciones/{id}/eliminar-moderacion` | ADMINISTRADOR | Sin restricción |
| `PATCH /calificaciones/{id}/moderar-texto` | ADMINISTRADOR | Sin restricción |

Los otros roles reciben `403` en los dos endpoints de moderación y el modo administrador de la consulta no se activa para ellos.

## API Contracts

### GET /api/v1/calificaciones (modo administrador)

- **Rol**: administrador · **Query params**: `tipoEntidad`, `entidadId`, `estado` (ACTIVA, ELIMINADA_AUTOR, ELIMINADA_MODERACION), `page`, `size`, `sort`, todos opcionales; sin `estado` se devuelven solo las `ACTIVA` (RF-03). Para los demás roles `estado` no es un filtro válido (`400`) y `tipoEntidad`/`entidadId` siguen siendo obligatorios (RF-02).
- **Respuesta**: `200` con `{ content, page, size, totalElements }`; cada elemento lleva `id`, `puntaje`, `comentario`, `estado`, `tipoEntidad`, `entidadId`, `pedidoId`, `autor { id, nombre }` y `createdAt`. Sin `resumen` cuando no hay `tipoEntidad` y `entidadId`.

### POST /api/v1/calificaciones/{id}/eliminar-moderacion

- **Rol**: administrador · **Path params**: `id` · **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅. **Body**: `{ "motivo": "Lenguaje ofensivo" }` (de 5 a 200 caracteres).
- **Respuesta**: `200` con `{ "id": "…", "estado": "ELIMINADA_MODERACION" }`.

### PATCH /api/v1/calificaciones/{id}/moderar-texto

- **Rol**: administrador · **Path params**: `id` · **Cabeceras**: `Authorization` ✅, `Content-Type: application/json`.
- **Body**: `{ "comentario": "Texto corregido", "motivo": "Se quitó un insulto" }` (`comentario` de 1 a 300 caracteres; `motivo` de 5 a 200).
- **Respuesta**: `200` con `{ "id": "…", "estado": "ACTIVA", "comentario": "Texto corregido" }`.

- **Errores (ambos)**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` (eliminar); `motivo` ausente, en blanco, de menos de 5 o más de 200 caracteres; texto vacío, de más de 300 o igual al actual (editar) | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | No es administrador | — |
| 404 | `NOT_FOUND` | La calificación no existe | "No encontramos esa calificación" |
| 409 | `INVALID_STATE` | La calificación no está `ACTIVA` (`details.estadoActual`) | "Esta calificación ya fue eliminada" |
| 422 | `BUSINESS_RULE` | La calificación no tiene comentario | "No hay comentario que moderar" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otra petición | — |

`PATCH` no necesita `Idempotency-Key` (spec-general §2.4 solo la exige en el `POST`); repetir la misma edición responde `400` por texto igual al actual.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E17 `calificaciones.comentario-moderado` (`ComentarioModeradoEvent`: `calificacionId`, `autorId`, `accion`, `motivo`) | `EliminarCalificacionModeracionUseCase` y `EditarTextoModeracionUseCase` | En la misma transacción del cambio (outbox) |

**Eventos que consume esta feature**: ninguno. El listener de E17 y su notificación `COMENTARIO_MODERADO` están en RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: las acciones de moderación no van por el outbox: requieren respuesta inmediata del servidor y la pantalla muestra el error si no hay red. La lista se guarda en caché para consultarla sin conexión.
- **Concurrencia (RNF08)**: `SELECT … FOR UPDATE` sobre la calificación; si dos administradores actúan a la vez, el segundo recibe `409 INVALID_STATE` (eliminar) o edita sobre el texto ya cambiado (editar).
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}` en la eliminación.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Calificación ya eliminada | `409 INVALID_STATE` | FR-001, Edge 3 |
| Sin comentario | `422 BUSINESS_RULE` | FR-001, FR-002 |
| Texto vacío, largo o igual | `400` | FR-002 |
| Calificación inexistente | `404` | — |
| Usuario no administrador | `403` | FR-001, FR-002 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Calificacion.eliminarPorModeracion` y `editarTexto` (estados, puntaje intacto, texto anterior) | FR-002, FR-003 |
| Integración | Endpoints con Testcontainers: eliminar y editar; promedio tras cada uno; registro con administrador, fecha, motivo y texto anterior; E17 con el motivo en el outbox; estados no `ACTIVA`; sin comentario; roles; modo administrador del listado | FR-001–FR-005, SC-001–SC-003 |
| Concurrencia | Dos administradores sobre la misma calificación | RNF08 |
| Migración | V14 aplica sobre V13 | FR-004 |
| Arquitectura | `comentarios` publica E17 solo con `domain/event` | RNF13 |
| Frontend | Jest: pantalla de moderación, confirmación, edición y errores | US1, US2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-06-moderacion-de-comentarios/
├── RF-06-moderacion-de-comentarios.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/comentarios/
├── domain/
│   ├── model/                    # ModeracionComentario, AccionModeracion
│   └── event/                    # ComentarioModeradoEvent (E17)
├── application/
│   ├── ports/in/                 # EliminarCalificacionModeracionPort, EditarTextoModeracionPort, ListarCalificacionesAdminPort
│   ├── ports/out/                # ModeracionRepositoryPort
│   └── usecase/                  # EliminarCalificacionModeracionUseCase, EditarTextoModeracionUseCase, ListarCalificacionesAdminUseCase
└── infrastructure/adapters/
    ├── in/web/                   # rama administrador de CalificacionController, endpoints de moderación y DTO
    └── out/persistence/          # ModeracionJpaEntity y consulta administrativa

backend/src/main/resources/db/migration/   # V14__moderacion_comentario.sql
backend/src/test/java/co/edu/napa/comentarios/

frontend/src/app/(administrador)/calificaciones/index.tsx         # lista con filtros
frontend/src/app/(administrador)/calificaciones/[id].tsx          # detalle y acciones
frontend/src/components/calificaciones/ItemModeracion.tsx
frontend/src/components/calificaciones/DialogoEditarComentario.tsx
frontend/src/services/moderacion.ts
frontend/__tests__/calificaciones/
```

**Structure Decision**: módulo `comentarios`: la moderación vive junto a `Calificacion` porque cambia su estado y su promedio en la misma transacción. E17 se publica con el mecanismo de eventos de los demás módulos; `notificaciones` lo escucha (RF-38). La lista del administrador comparte la ruta de RF-02 con un caso de uso propio, para no mezclar reglas de visibilidad con las de administración.

## Phase 1: Setup

- [ ] T001 Confirmar que `comentarios` puede publicar `ComentarioModeradoEvent` y que el listener de RF-38 (T028) existe

## Phase 2: Foundational

- [ ] T002 Migración `V14__moderacion_comentario.sql` en `backend/src/main/resources/db/migration/`: tabla `moderacion_comentario` (id, calificacion_id, administrador_id, accion, motivo, contenido_anterior, created_at) con índice `(calificacion_id, created_at)`
- [ ] T003 Dominio `ModeracionComentario`, `AccionModeracion` y `ComentarioModeradoEvent` (E17) en `comentarios/domain/`, y métodos `eliminarPorModeracion` y `editarTexto` en `Calificacion`
- [ ] T004 `ModeracionRepositoryPort` y su adaptador

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Eliminar un comentario (Priority: P1)

**Goal**: el administrador retira lo que incumple las reglas.
**Independent Test**: eliminar una calificación y ver que sale del promedio, queda registrada y el autor es notificado.
**Cubre**: FR-001, FR-003, FR-004, FR-005, SC-001–SC-003, US1-E1, Edge 2–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `eliminar-moderacion` con `motivo` (`200`, estado, puntaje fuera del promedio) en `backend/src/test/java/co/edu/napa/comentarios/EliminarModeracionIT.java`
- [ ] T011 [P] [US1] Test de integración: el promedio de RF-02 se recalcula tras eliminar en `.../comentarios/EliminarRecalculaPromedioIT.java`
- [ ] T012 [P] [US1] Test de integración: registro `ELIMINADO` con administrador, fecha, `motivo` y `contenido_anterior`; distinto de `ELIMINADA_AUTOR` en `.../comentarios/ModeracionRegistroIT.java`
- [ ] T013 [P] [US1] Test de integración: E17 en el outbox con `calificacionId`, `autorId`, `accion` y `motivo`, y notificación al autor con la acción y el motivo con el listener de RF-38 en `.../comentarios/ModeracionNotificacionIT.java`
- [ ] T014 [P] [US1] Test de integración: la calificación eliminada ya no aparece en `GET /calificaciones` de los demás roles en `.../comentarios/ModeracionVisibilidadIT.java`
- [ ] T015 [P] [US1] Test de `motivo` ausente, en blanco, corto y largo (`400`), de estados no `ACTIVA` (`409`), sin comentario (`422`), inexistente (`404`), no administrador (`403`), reintento con la misma `Idempotency-Key`, clave reutilizada (`422`), falta de clave (`400`) en `.../comentarios/EliminarModeracionErroresIT.java`
- [ ] T016 [P] [US1] Test de concurrencia: dos administradores eliminan la misma calificación (uno `200`, otro `409`) en `.../comentarios/ModeracionConcurrenciaIT.java`
- [ ] T017 [P] [US1] Test de migración V14 en `.../comentarios/MigracionV14Test.java`
- [ ] T018 [P] [US1] Test de integración del modo administrador de `GET /calificaciones` (sin filtros, `estado`, `tipoEntidad`, orden; otros roles con `estado` dan `400`) en `.../comentarios/ListarAdminIT.java`
- [ ] T019 [P] [US1] Test de integración: moderar (eliminar y editar) sobre el texto vigente tras una edición del autor, y calificación `ELIMINADA_AUTOR` no moderable (`409`); se deshabilita hasta que existan RF-04 y RF-05, en `.../comentarios/ModeracionTrasAutorIT.java`
- [ ] T030 [P] [US1] Test de la lista y de la acción de eliminar con confirmación y motivo obligatorio en `frontend/__tests__/calificaciones/moderacion-eliminar.test.tsx`

### Implementation

- [ ] T020 [US1] `EliminarCalificacionModeracionUseCase` y su puerto (bloqueo de fila, estado, registro, evento) en `comentarios/application/`
- [ ] T021 [US1] `POST /calificaciones/{id}/eliminar-moderacion`
- [ ] T022 [US1] Adaptador de persistencia de `ModeracionComentario`
- [ ] T023 [US1] Publicación de E17 en el caso de uso
- [ ] T024 [US1] `ListarCalificacionesAdminUseCase` y rama administrador de `GET /calificaciones`
- [ ] T031 [US1] Servicio `moderacion.ts` (listar y eliminar)
- [ ] T032 [US1] Pantalla `(administrador)/calificaciones/index.tsx` con filtros, `ItemModeracion` y revalidación al volver a la lista
- [ ] T033 [US1] Detalle `(administrador)/calificaciones/[id].tsx` con la acción de eliminar, el campo de motivo y la confirmación

## Phase 4: User Story 2 - Editar el texto de un comentario (Priority: P2)

**Goal**: el administrador corrige el texto sin tocar el puntaje.
**Independent Test**: editar un comentario y ver el texto nuevo, el mismo promedio y el aviso al autor.
**Cubre**: FR-002, FR-003 (edición), FR-004, FR-005, US2-E1.

### Tests (antes de implementar)

- [ ] T040 [P] [US2] Test de integración de `moderar-texto` (`200`, texto nuevo, puntaje igual) en `.../comentarios/EditarModeracionIT.java`
- [ ] T041 [P] [US2] Test de integración: el promedio no cambia tras editar en `.../comentarios/EditarNoCambiaPromedioIT.java`
- [ ] T044 [P] [US2] Test de integración: registro `EDITADO` con el `motivo` y el texto anterior (también en ediciones sucesivas), E17 con `accion=EDITADO` y `motivo`, y errores (motivo ausente o corto, vacío, 301 caracteres, igual al actual, estado no `ACTIVA`, sin comentario, `403`) en `.../comentarios/EditarModeracionErroresIT.java`
- [ ] T045 [P] [US2] Test del diálogo de edición con motivo obligatorio en `frontend/__tests__/calificaciones/moderacion-editar.test.tsx`

### Implementation

- [ ] T042 [US2] `EditarTextoModeracionUseCase` y su puerto
- [ ] T043 [US2] `PATCH /calificaciones/{id}/moderar-texto`
- [ ] T046 [US2] `DialogoEditarComentario` (texto y motivo) y la acción de editar en el detalle del administrador

**Checkpoint**: US1 y US2 funcionales.

## Phase N: Polish & Cross-Cutting

- [ ] T050 Medir que una moderación se refleja en menos de 5 segundos (SC-002)
- [ ] T051 Revisar permisos y auditoría (RNF03, RNF21)
- [ ] T052 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → Polish.
- Dependencias entre RF: comparte estados con RF-04 y RF-05 (ola 7); requiere RF-01 (tabla y estados), RF-02 (consulta que excluye lo eliminado), RF-38 (listener E17) y RF-10 (sesión de administrador).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Deleción: FR-003 dice que eliminar recalcula el promedio, por lo que se elimina la calificación completa (puntaje incluido), no solo el texto | Regla | La eliminación retira el puntaje del promedio. Si se prefiere conservarlo, hay que decirlo: cambiaría FR-003 | Equipo |
| Moderar una calificación sin comentario | Regla | `422 BUSINESS_RULE`: no hay comentario que moderar | Equipo |
| Editar a un texto vacío | Regla | `400`; para quitar el comentario se elimina | Equipo |
| Editar con el mismo texto | Regla | `400`, así no se crean registros vacíos | Equipo |
| Edge 1: RF-04 y RF-05 (P2, ola 7) dejan al autor editar y eliminar; RF-05 fija `ELIMINADA_AUTOR` | Regla | El administrador modera sobre el texto vigente; solo se modera `ACTIVA`. El autor puede editar un comentario ya editado por el administrador (RF-04 lo confirmará) y no uno eliminado por moderación | Equipo |
| El administrador indica un motivo (por qué moderó), decidido en la ola 7a. Requiere agregar `motivo` a `ModeracionComentario` en spec-general §3.2, a FR-004 y FR-005 de la spec de RF-06 y al payload de E17 en `eventos.md` | Modelo, spec y catálogo | Obligatorio, de 5 a 200 caracteres, guardado en el registro y enviado al autor en la notificación | Equipo |
| No hay endpoint ni pantalla para ver el historial de moderación de una calificación | Alcance | Solo se guarda (SC-001); si se necesita verlo hay que agregar una ruta | Equipo |
| `PATCH` sin `Idempotency-Key` | Contrato | spec-general §2.4 solo la exige en el `POST` | Equipo |
| La lista del administrador sin `tipoEntidad`/`entidadId` no lleva `resumen` | Contrato | Sin `resumen` | Equipo |
| El contenido de una calificación eliminada por moderación se conserva en `calificacion.comentario` y en `contenido_anterior` | Privacidad | Se conserva para auditoría y no se expone a otros roles | Equipo |

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
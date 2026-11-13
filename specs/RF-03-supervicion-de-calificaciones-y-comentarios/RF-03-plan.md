# Implementation Plan: Supervisión de calificaciones y comentarios

**Date**: 2026-10-26
**Spec**: [RF-03-supervision-de-calificaciones-y-comentarios.md](./RF-03-supervision-de-calificaciones-y-comentarios.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-03
**Historias de usuario**: HU-19
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-06, RF-01, RF-02, RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador supervisa las calificaciones y comentarios de toda la plataforma con el modo administrador de `GET /calificaciones`, que RF-06 ya deja construido. Este plan completa lo que la supervisión necesita: por defecto lista las calificaciones `ACTIVA` (con `estado` puede ver también las eliminadas por el autor o por moderación) y cada fila trae autor (nombre y rol), entidad calificada (tipo, id y nombre), puntaje, comentario, fecha y si fue moderada. Se filtra por tipo de entidad (emprendimiento, producto o usuario) y, dentro del tipo, por una entidad concreta; se pagina y se ordena por fecha o puntaje. No crea endpoints nuevos. La pantalla de supervisión es la misma lista de moderación de RF-06, con filtros y un toque sobre el nombre de la entidad para ver solo lo suyo.

## Estado actual del código

RF-06 (T024) crea `ListarCalificacionesAdminUseCase` y la rama administrador de `GET /calificaciones` con los filtros `tipoEntidad`, `entidadId` y `estado`, y la pantalla `(administrador)/calificaciones`. RF-02 define los puertos de lectura de emprendimientos, productos y usuarios. Falta el enriquecimiento de las filas (nombres y rol), el estado por defecto y los índices para este uso.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Administrador | Supervisa calificaciones y comentarios |
| Los demás roles | No interviene (usan RF-02 con sus restricciones) |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Localizar cualquier comentario en < 30 s con los filtros (SC-001): filtros por tipo y entidad, orden por fecha o puntaje y acceso directo desde el nombre de la entidad.
- 100 % de los comentarios activos visibles (SC-002): sin filtros devuelve todas las `ACTIVA`, sin omisiones ni caché.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El administrador consulta el listado completo de calificaciones y comentarios | Modo administrador de `GET /calificaciones`, `ACTIVA` por defecto y `estado` para las demás | T010, T012, T020 |
| FR-002 | Filtrar por entidad (emprendimiento, producto, domiciliario o cliente) | `tipoEntidad` (EMPRENDIMIENTO, PRODUCTO, USUARIO) y `entidadId` | T011 |
| FR-003 | Mostrar autor, entidad, calificación y fecha | Fila con `autor`, `entidad`, `puntaje`, `createdAt` y `comentario` | T010, T021 |
| SC-001 | Localizar un comentario en < 30 s | Filtros, orden y acceso desde la entidad | T011, T033 |
| SC-002 | 100 % de los activos visibles | Consulta sin omisiones | T010, T013 |
| US1-E1 | Consulta general de comentarios recientes | `200` con autor, entidad y fecha, más recientes primero | T010 |
| US1-E2 | Filtro por una entidad concreta | Solo las de esa entidad | T011 |
| Edge 1 | Eliminado por su autor antes de la revisión | No aparece por defecto; con `estado=ELIMINADA_AUTOR` sí | T012 |
| Edge 2 | Identificar lenguaje potencialmente ofensivo | Sin detección automática; el administrador lee y modera (Open Questions) | — |
| Edge 3 | Volumen alto | Paginación y orden | T013 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR |
| RNF04 Privacidad | ☑ | Nombre y rol de los autores; sin celular ni correo |
| RNF07 Rendimiento | ☑ | Índice y nombres resueltos en lote |
| RNF24 Errores claros | ☑ | `400` con el parámetro |
| RNF28 Conectividad | ☑ | Caché local de la última lista |
| RNF31 Gama baja | ☑ | Lista paginada |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Calificacion | Lee | Todos los estados; `ACTIVA` por defecto |
| ModeracionComentario | Lee | Para marcar `moderada` |
| Emprendimiento, Producto, Usuario | Lee (puertos) | Nombres en lote; rol del usuario |

## State Transitions

Esta feature es de solo lectura: no cambia estados.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /calificaciones` (modo administrador) | ADMINISTRADOR | Todas las calificaciones |

## API Contracts

### GET /api/v1/calificaciones (modo administrador)

La ruta y la rama del administrador las crea RF-06; este plan define su contenido de supervisión.

- **Rol**: administrador · **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| tipoEntidad | enum | ☐ | `PRODUCTO` | `EMPRENDIMIENTO`, `PRODUCTO` o `USUARIO` (cliente o domiciliario) |
| entidadId | UUID | ☐ | `…` | Requiere `tipoEntidad` |
| estado | enum | ☐ | `ELIMINADA_AUTOR` | Por defecto `ACTIVA` |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `createdAt,desc` | `sort` admite `createdAt` y `puntaje`; por defecto `createdAt,desc` |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id`, `Cache-Control: no-store` y body:

```json
{
  "content": [
    { "id": "…", "puntaje": 2, "comentario": "…", "estado": "ACTIVA", "moderada": false,
      "autor": { "id": "…", "nombre": "María", "rol": "CLIENTE" },
      "entidad": { "tipo": "PRODUCTO", "id": "…", "nombre": "Mango" },
      "pedidoId": "…", "createdAt": "…", "updatedAt": "…" }
  ],
  "page": 0, "size": 20, "totalElements": 1,
  "resumen": { "promedio": 3.8, "total": 12 }
}
```

`resumen` solo aparece cuando se filtra por una entidad concreta (`tipoEntidad` y `entidadId`) y cuenta las `ACTIVA`. `moderada` es `true` si el administrador editó el texto.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido, `entidadId` sin `tipoEntidad`, valores inválidos, `size` mayor que 100 | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de administrador en esta rama | — |
| 404 | `NOT_FOUND` | `entidadId` no existe para el `tipoEntidad` | "No encontramos esa entidad" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda la primera página sin filtros y la muestra con "datos guardados"; los filtros requieren conexión.
- **Concurrencia (RNF08)**: no aplica; el estado cambia por RF-04, RF-05 y RF-06 y se ve al refrescar.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Comentario eliminado por su autor | No sale por defecto; sí con `estado=ELIMINADA_AUTOR` | Edge 1 |
| Filtro sin resultados | `200` con lista vacía | FR-002 |
| `entidadId` sin `tipoEntidad` | `400` | FR-002 |
| Volumen alto | Paginación | Edge 3 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Modo administrador con Testcontainers: lista por defecto (solo `ACTIVA`) con autor, entidad, puntaje y fecha; filtro por cada tipo y por entidad concreta; `estado` para las eliminadas; `moderada`; `resumen` con entidad; filtros inválidos; roles | FR-001–FR-003, US1-E1–E2, Edge 1 |
| Rendimiento | 100 000 calificaciones: primera página en < 2 s y nombres resueltos en lote (sin una consulta por fila) | Edge 3 |
| Arquitectura | `comentarios` obtiene nombres solo por puertos `in` | RNF13 |
| Frontend | Jest: filtros, acceso desde el nombre de la entidad, filas moderadas y eliminadas, vacío, caché | SC-001 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-03-supervicion-de-calificaciones-y-comentarios/
├── RF-03-supervision-de-calificaciones-y-comentarios.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/comentarios/
├── application/
│   ├── ports/out/                # ResolverNombresEntidadPort (en lote), CalificacionRepositoryPort (listarAdmin)
│   └── usecase/                  # ListarCalificacionesAdminUseCase (RF-06, ampliado)
└── infrastructure/adapters/
    ├── in/web/                   # CalificacionController (rama administrador), DTO enriquecido
    └── out/persistence/          # consulta administrativa y adaptadores de nombres

backend/src/main/java/co/edu/napa/{emprendimientos,productos,cuentas}/application/ports/in/   # consulta de nombres en lote
backend/src/test/java/co/edu/napa/comentarios/

frontend/src/app/(administrador)/calificaciones/index.tsx          # (RF-06) pantalla de supervisión con filtros
frontend/src/components/calificaciones/FiltrosSupervision.tsx      # tipo, estado, orden
frontend/src/components/calificaciones/ItemModeracion.tsx          # (RF-06) con entidad, autor, rol, moderada
frontend/src/services/moderacion.ts                                # (RF-06) listar con filtros
frontend/__tests__/calificaciones/supervision.test.tsx
```

**Structure Decision**: módulo `comentarios`, sin endpoints nuevos: la supervisión es el mismo `ListarCalificacionesAdminUseCase` de RF-06 con el estado por defecto, las filas enriquecidas y los índices de esta lectura. Los nombres de las entidades y de los autores se piden en lote a los módulos dueños por sus puertos `in`.

## Phase 1: Setup

- [ ] T001 Confirmar `ListarCalificacionesAdminUseCase` y la rama administrador de `GET /calificaciones` (RF-06 T024)

## Phase 2: Foundational

- [ ] T002 Índice `(estado, created_at)` sobre `calificacion` en la siguiente migración libre de `backend/src/main/resources/db/migration/`
- [ ] T003 Puertos `in` de nombres en lote en `emprendimientos`, `productos` y `cuentas` (y rol del usuario) y `ResolverNombresEntidadPort` con su adaptador
- [ ] T004 Consulta administrativa en `CalificacionRepositoryPort` con estado por defecto `ACTIVA`, filtros y marca `moderada`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Administrador supervisa comentarios publicados (Priority: P2)

**Goal**: el administrador revisa lo publicado y encuentra rápido lo problemático.
**Independent Test**: con calificaciones de varias entidades, listar, filtrar por tipo y por entidad, y ver las eliminadas con `estado`.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1, Edge 3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración del modo administrador sin filtros: solo `ACTIVA`, con autor (nombre y rol), entidad (tipo, id y nombre), puntaje, comentario y fecha, más recientes primero, en `backend/src/test/java/co/edu/napa/comentarios/SupervisionListadoIT.java`
- [ ] T011 [P] [US1] Test de filtro por `tipoEntidad` (cada tipo) y por `entidadId`, con `resumen` y `entidadId` sin `tipoEntidad` (`400`), en `.../comentarios/SupervisionFiltroEntidadIT.java`
- [ ] T012 [P] [US1] Test de filtro `estado`: una calificación eliminada por su autor no sale por defecto y sí con `estado=ELIMINADA_AUTOR` o `ELIMINADA_MODERACION`; marca `moderada` tras editar el texto, en `.../comentarios/SupervisionEstadosIT.java`
- [ ] T013 [P] [US1] Test de paginación y orden por `createdAt` y `puntaje`, sin omisiones entre páginas, y de rendimiento con 100 000 calificaciones (< 2 s y nombres en lote) en `.../comentarios/SupervisionRendimientoIT.java`
- [ ] T014 [P] [US1] Test de roles (`403`), sin sesión (`401`), filtros desconocidos y `entidadId` inexistente (`404`) en `.../comentarios/SupervisionAutorizacionIT.java`
- [ ] T030 [P] [US1] Test de la pantalla: filtros, estado, filas moderadas y eliminadas, lista vacía, caché sin red en `frontend/__tests__/calificaciones/supervision.test.tsx`
- [ ] T031 [P] [US1] Test del acceso directo: tocar el nombre de la entidad filtra por ella en `frontend/__tests__/calificaciones/supervision-entidad.test.tsx`

### Implementation

- [ ] T020 [US1] Estado por defecto `ACTIVA`, filtros y orden en `ListarCalificacionesAdminUseCase`
- [ ] T021 [US1] DTO enriquecido (autor, entidad, `moderada`) y `resumen` con entidad concreta en `CalificacionController`
- [ ] T022 [US1] Adaptadores de nombres en lote sobre los puertos `in` de `emprendimientos`, `productos` y `cuentas`
- [ ] T032 [US1] `FiltrosSupervision` y marcas de estado y moderación en `ItemModeracion`
- [ ] T033 [US1] Acceso directo desde el nombre de la entidad y filtros en `(administrador)/calificaciones/index.tsx`

**Checkpoint**: la supervisión funciona de extremo a extremo.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir con administradores del piloto el tiempo para localizar un comentario (SC-001)
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-06 (endpoint y pantalla), RF-01, RF-02 (puertos) y RF-10; RF-04 y RF-05 producen los estados que se supervisan.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 2: identificar lenguaje ofensivo | Alcance | Sin detección automática ni filtro por palabras; el administrador filtra, lee y modera. Mejora posible: búsqueda por texto | Equipo |
| El estado por defecto es `ACTIVA` (SC-002 habla de comentarios activos); RF-06 deja `estado` opcional y se ajusta a este valor | Regla | `ACTIVA` por defecto | Equipo |
| FR-002 pide filtrar por "domiciliario o cliente"; el filtro usa `tipoEntidad=USUARIO` y no distingue el rol | Contrato | La fila muestra el rol del usuario; no hay filtro por rol | Equipo |
| El administrador necesita el id de la entidad para filtrar; no hay buscador por nombre | UX | Se llega desde la fila (toque en el nombre) o desde RF-69/RF-66 cuando existan | Equipo |
| `resumen` también sale en el modo administrador cuando se filtra por una entidad | Contrato | Sí, igual que en RF-02 | Equipo |
| Sin filtros por autor, fecha ni "solo con comentario" | Alcance | No están en la spec | Equipo |

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
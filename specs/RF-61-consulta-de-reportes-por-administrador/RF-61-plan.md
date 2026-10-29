# Implementation Plan: Consulta de reportes por administrador

**Date**: 2026-10-26
**Spec**: [RF-61-consulta-de-reportes-por-administrador.md](./RF-61-consulta-de-reportes-por-administrador.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-61
**Historias de usuario**: HU-94
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-60, RF-45, RF-14, RF-02, RF-10
**Habilita a**: RF-62

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador ve los reportes que llegan desde RF-60. `GET /reportes` los lista paginados con estado, motivo, autor (nombre y rol), la entidad relacionada (tipo e id) y la fecha, con filtro por `estado` (`PENDIENTE`, `EN_REVISION`, `RESUELTO`); por defecto los más antiguos primero, para atender primero lo que lleva más tiempo esperando. `GET /reportes/{id}` devuelve el detalle: descripción completa, nota de resolución si existe y un resumen de la entidad relacionada (del pedido, del domicilio o del usuario reportado) que traen los módulos dueños por sus puertos. Es solo para el administrador. La app del administrador agrega la lista con filtros y la pantalla de detalle, donde RF-62 ubica la acción de resolver.

## Estado actual del código

RF-60 crea la tabla `reporte` (V12) y el módulo `reportes`; su plan dejó sin definir el `GET`. Los puertos de lectura de `pedidos`, `domicilios` y `cuentas` existen en parte (RF-60 T003, RF-02 T002) y se amplían aquí. No existe ninguna pantalla del administrador para reportes.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador, vendedor, domiciliario | No interviene (solo crea reportes en RF-60) |
| Administrador | Lista y consulta reportes |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Listado completo en < 2 s (SC-001): consulta paginada con el índice `(estado, created_at)` de RF-60 (V12) y nombres de autor en lote.
- 100 % de los reportes consultables (SC-002): sin filtro devuelve todos los estados; no hay reportes ocultos.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El administrador consulta el listado de reportes | `GET /reportes` para el rol ADMINISTRADOR | T010, T020, T021 |
| FR-002 | Detalle con autor, motivo, descripción y entidad relacionada | `GET /reportes/{id}` con resumen de la entidad por los puertos de cada módulo | T011, T012, T022, T023 |
| FR-003 | Filtrar por estado | Parámetro `estado` con validación | T013 |
| SC-001 | < 2 s | Índice y paginación | T014 |
| SC-002 | 100 % consultables | Sin filtro devuelve todos | T010 |
| US1-E1 | Listado con estado, autor y motivo | `200` con esos campos | T010 |
| US1-E2 | Detalle con descripción y entidad relacionada | `200` con `entidad` | T011, T012 |
| Edge 1 | Priorizar los más antiguos o urgentes | Orden por defecto `createdAt asc` y antigüedad visible en la app; sin campo de urgencia | T010, T031 |
| Edge 2 | Filtrar por un estado sin reportes | `200` con lista vacía | T013 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR |
| RNF04 Privacidad | ☑ | `Cache-Control: no-store`; solo nombre y rol de terceros, sin celular |
| RNF07 Rendimiento ≤ 3 s | ☑ | Índice, paginación y lecturas en lote |
| RNF24 Errores claros | ☑ | `400` con el filtro; `404` |
| RNF28 Conectividad | ☑ | Caché de lectura de la última lista |
| RNF15 Fiabilidad | ☑ | Si falla el módulo de una entidad, el detalle responde con `entidad.disponible = false` |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Reporte | Lee | Todos los estados |
| Usuario | Lee (puerto) | Nombre y rol del autor y del usuario reportado |
| Pedido | Lee (puerto) | Resumen: estado, modalidad, emprendimiento, cliente y total |
| Domicilio | Lee (puerto) | Resumen: estado, pedido y domiciliario |

## State Transitions

Esta feature es de solo lectura: no cambia estados. Muestra `PENDIENTE`, `EN_REVISION` y `RESUELTO`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /reportes` | ADMINISTRADOR | Todos los reportes |
| `GET /reportes/{id}` | ADMINISTRADOR | Cualquier reporte |

## API Contracts

### GET /api/v1/reportes

- **Rol**: administrador · **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| estado | enum | ☐ | `PENDIENTE` | `PENDIENTE`, `EN_REVISION` o `RESUELTO` |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `createdAt,asc` | Por defecto `createdAt,asc`; `sort` admite `createdAt` |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id`, `Cache-Control: no-store` y body:

```json
{
  "content": [
    { "id": "…", "estado": "PENDIENTE", "motivo": "Cliente no estaba en la dirección",
      "autor": { "id": "…", "nombre": "Carlos", "rol": "DOMICILIARIO" },
      "entidad": { "tipo": "DOMICILIO", "id": "…" }, "createdAt": "…" }
  ],
  "page": 0, "size": 20, "totalElements": 1
}
```

`entidad` es `null` en un reporte sin entidad relacionada.

### GET /api/v1/reportes/{id}

- **Rol**: administrador · **Path params**: `id` (UUID del reporte)
- **Respuestas exitosas**: `200` con `Cache-Control: no-store` y body:

```json
{
  "id": "…", "estado": "RESUELTO", "motivo": "…", "descripcion": "Texto completo…",
  "autor": { "id": "…", "nombre": "Carlos", "rol": "DOMICILIARIO" },
  "createdAt": "…", "updatedAt": "…",
  "notaResolucion": "Se contactó al cliente…", "resueltoPor": { "id": "…", "nombre": "Admin" },
  "entidad": { "tipo": "DOMICILIO", "id": "…", "disponible": true,
               "resumen": { "estado": "EN_CAMINO", "pedidoId": "…", "domiciliario": { "id": "…", "nombre": "Carlos" } } }
}
```

`resumen` depende del tipo: PEDIDO (`estado`, `modalidad`, `emprendimiento`, `cliente`, `total`), DOMICILIO (`estado`, `pedidoId`, `domiciliario`) o USUARIO (`nombre`, `rol`, `estado`). `notaResolucion` y `resueltoPor` son `null` mientras no esté resuelto.

- **Errores (ambos)**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido, `estado` inválido, `sort` inválido, `size` mayor que 100 | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de administrador | — |
| 404 | `NOT_FOUND` | El reporte no existe (solo en el detalle) | "No encontramos el reporte" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda la última página de la lista y la muestra con "datos guardados"; el detalle requiere conexión porque muestra información de terceros (no se guarda en el teléfono).
- **Concurrencia (RNF08)**: no aplica a la lectura; otro administrador puede resolver mientras se mira, lo cual RF-62 resuelve con `409`.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Filtro de estado sin reportes | `200` con lista vacía | Edge 2 |
| Reporte inexistente | `404` | FR-002 |
| Entidad relacionada eliminada o no disponible | `entidad.disponible = false`, sin `resumen` | FR-002 |
| Usuario que no es administrador | `403` | RNF03 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoints con Testcontainers: lista con todos los estados, orden por defecto, filtro por cada estado, lista vacía, detalle con cada tipo de entidad y sin ella, entidad no disponible, reporte inexistente, filtros inválidos, roles | FR-001–FR-003, US1-E1–E2, Edge 1–2 |
| Rendimiento | 10 000 reportes: primera página en < 2 s | SC-001 |
| Arquitectura | `reportes` lee de `pedidos`, `domicilios` y `cuentas` solo por puertos `in` | RNF13 |
| Frontend | Jest: lista con chips de estado, antigüedad, detalle por tipo de entidad, vacío, caché | US1, Edge 1 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-61-consulta-de-reportes-por-administrador/
├── RF-61-consulta-de-reportes-por-administrador.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/reportes/
├── application/
│   ├── ports/in/                 # ListarReportesPort, ConsultarReportePort
│   ├── ports/out/                # ReporteRepositoryPort (listado con filtro), ResumenEntidadReportablePort, NombresUsuarioPort (en lote)
│   └── usecase/                  # ListarReportesUseCase, ConsultarReporteUseCase
└── infrastructure/adapters/
    ├── in/web/                   # ReporteController (GET /reportes, GET /reportes/{id}), DTO de fila y de detalle
    └── out/persistence/          # consulta paginada y adaptadores de resumen

backend/src/main/java/co/edu/napa/pedidos/application/ports/in/      # ConsultarResumenPedidoPort
backend/src/main/java/co/edu/napa/domicilios/application/ports/in/   # ConsultarResumenDomicilioPort
backend/src/main/java/co/edu/napa/cuentas/application/ports/in/      # ConsultarUsuarioReputablePort (RF-02), ampliado con nombres en lote
backend/src/test/java/co/edu/napa/reportes/

frontend/src/app/(administrador)/reportes/index.tsx        # ruta Expo Router: lista con filtro por estado
frontend/src/app/(administrador)/reportes/[id].tsx         # ruta Expo Router: detalle
frontend/src/components/reportes/ItemReporte.tsx           # estado, motivo, autor, antigüedad
frontend/src/components/reportes/ResumenEntidadReportada.tsx
frontend/src/components/reportes/FiltroEstadoReporte.tsx
frontend/src/services/reportes.ts                          # listarReportes(), consultarReporte()
frontend/__tests__/reportes/consulta-admin.test.tsx
```

**Structure Decision**: módulo `reportes` (dueño de `Reporte`). El resumen de la entidad lo pide cada módulo por su puerto `in`, según el tipo del reporte, para que `reportes` no lea tablas ajenas; si el módulo falla o la entidad ya no existe, el detalle degrada a `disponible: false`. Los nombres de autores se piden en lote para no consultar por fila.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `reportes`, la tabla `reporte` (RF-60 T002) y su regla ArchUnit

## Phase 2: Foundational

- [ ] T002 Puertos `in` de resumen: `ConsultarResumenPedidoPort` en `pedidos` y `ConsultarResumenDomicilioPort` en `domicilios`; ampliar `ConsultarUsuarioReputablePort` (RF-02) con nombres en lote
- [ ] T003 `ResumenEntidadReportablePort` y `NombresUsuarioPort` (puertos `out`) con sus adaptadores
- [ ] T004 Consulta paginada con filtro por estado en `ReporteRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Revisar reportes informados por los usuarios (Priority: P2)

**Goal**: el administrador encuentra y entiende cada problema reportado.
**Independent Test**: crear reportes con RF-60 y verlos en lista y detalle como administrador.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /reportes` (todos los estados, campos, orden por defecto `createdAt asc`, paginación) en `backend/src/test/java/co/edu/napa/reportes/ListarReportesIT.java`
- [ ] T011 [P] [US1] Test de integración de `GET /reportes/{id}` con reporte de pedido, de domicilio y de usuario (descripción y resumen) en `.../reportes/ConsultarReporteIT.java`
- [ ] T012 [P] [US1] Test de integración del detalle sin entidad, con entidad eliminada y con el módulo de la entidad caído (`disponible: false`) en `.../reportes/ConsultarReporteEntidadIT.java`
- [ ] T013 [P] [US1] Test de filtro por cada estado, estado sin reportes (`200` vacío), estado, `sort` y filtro inválidos (`400`) en `.../reportes/ListarReportesFiltrosIT.java`
- [ ] T014 [P] [US1] Test de rendimiento con 10 000 reportes (< 2 s) en `.../reportes/ListarReportesRendimientoIT.java`
- [ ] T015 [P] [US1] Test de roles (`403` para cliente, vendedor y domiciliario), sin sesión (`401`), reporte inexistente (`404`) y `Cache-Control: no-store` en `.../reportes/ConsultarReporteAutorizacionIT.java`
- [ ] T030 [P] [US1] Test de la lista (chips de estado, antigüedad, vacío, caché) y del detalle por tipo de entidad en `frontend/__tests__/reportes/consulta-admin.test.tsx`

### Implementation

- [ ] T020 [US1] `ListarReportesUseCase` y `ListarReportesPort` en `reportes/application/`
- [ ] T022 [US1] `ConsultarReporteUseCase` y `ConsultarReportePort` (resumen por tipo con degradación)
- [ ] T021 [US1] `GET /reportes` y su DTO en `ReporteController`
- [ ] T023 [US1] `GET /reportes/{id}` y su DTO de detalle
- [ ] T031 [US1] `ItemReporte` (antigüedad, estado, motivo), `FiltroEstadoReporte` y `ResumenEntidadReportada` en `frontend/src/components/reportes/`
- [ ] T032 [US1] Servicios `listarReportes()` (con caché SQLite) y `consultarReporte()` en `frontend/src/services/reportes.ts`
- [ ] T033 [US1] Pantallas `(administrador)/reportes/index.tsx` y `[id].tsx`

**Checkpoint**: el administrador consulta todos los reportes.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` con datos del piloto
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-60, RF-45, RF-14, RF-02 (puerto de usuarios) y RF-10; habilita RF-62 (resolver desde el detalle).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| `EN_REVISION` existe en spec-general pero ningún RF define quién lo asigna | Estado | No hay transición a `EN_REVISION` en esta ola; los reportes pasan de `PENDIENTE` a `RESUELTO` (RF-62 acepta ambos orígenes). Decidir si se necesita una acción "tomar reporte" | Equipo |
| Edge 1: "más antiguos o urgentes"; la spec no define urgencia | UX | Orden por defecto más antiguo primero y antigüedad visible; sin campo de urgencia ni alertas | Equipo |
| El administrador ve nombre y rol de los involucrados, no su celular | Privacidad | Así; el contacto está en RF-66 (consulta de usuario por administrador) | Equipo |
| `GET /reportes/{id}` no estaba en el catálogo de spec-general §2.4 | Contrato | Agregarlo junto con `GET /reportes` (RF-61). RF-60 sigue sin devolver `Location`: quien crea el reporte no puede consultarlo | Equipo |
| El detalle del reporte de un usuario reportado no muestra sus otros reportes | Alcance | Fuera de la spec | Equipo |
| Sin búsqueda por texto, autor ni fecha | Alcance | Solo filtro por estado (FR-003) | Equipo |

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
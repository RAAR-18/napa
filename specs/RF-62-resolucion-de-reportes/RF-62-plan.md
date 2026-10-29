# Implementation Plan: Resolución de reportes

**Date**: 2026-10-26
**Spec**: [RF-62-resolucion-de-reportes.md](./RF-62-resolucion-de-reportes.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-62
**Historias de usuario**: HU-95
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-60, RF-61, RF-38, RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador cierra un reporte con `POST /reportes/{id}/resolver`, enviando una nota de resolución obligatoria que describe la acción tomada. El reporte pasa a `RESUELTO` (desde `PENDIENTE` o `EN_REVISION`), queda con la nota, el administrador responsable y la fecha, y se publica E16 `reporte-resuelto` en la misma transacción para avisar al autor (el listener lo planea RF-38). Resolver un reporte ya resuelto responde `409`. No se puede reabrir. La acción vive en el detalle del reporte que construye RF-61: un cuadro de texto para la nota y un botón de confirmar.

## Estado actual del código

RF-60 crea el reporte con las columnas `estado`, `nota_resolucion` y `resuelto_por_id` (V12) y RF-61 lo consulta. El listener de E16 en `notificaciones` está planeado en RF-38 (T028). No existe aún ninguna acción de resolver.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Administrador | Resuelve reportes |
| Autor del reporte | Recibe la notificación de la resolución |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Notificar al autor en < 1 min (SC-001): E16 por el outbox en la misma transacción y listener de RF-38.
- 100 % de los reportes resueltos con nota (SC-002): la nota es obligatoria en el dominio y en el borde; un `CHECK` impide `RESUELTO` sin nota.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El administrador marca un reporte como resuelto | `POST /reportes/{id}/resolver` | T010, T020, T021 |
| FR-002 | Exigir una nota o acción de resolución | Campo `nota` obligatorio, de 5 a 1 000 caracteres | T011 |
| FR-003 | Notificar al autor | E16 en el outbox y listener de RF-38 | T012 |
| FR-004 | No resolver un reporte ya resuelto | `409 INVALID_STATE` con `estadoActual` | T013 |
| SC-001 | Aviso en < 1 min | E16 en la misma transacción | T012 |
| SC-002 | 100 % de resueltos con nota | Dominio, borde y CHECK | T011, T016 |
| US1-E1 | Resolución desde `PENDIENTE` o `EN_REVISION` | `200`, `RESUELTO`, nota y responsable | T010 |
| US1-E2 | Intento sobre un reporte resuelto | `409`, sin cambios | T013 |
| Edge 1 | Reabrir un reporte cerrado por error | No se permite (Open Questions) | — |
| Edge 2 | Información mínima al resolver | Nota, administrador responsable y fecha (`updated_at`) | T010, T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR |
| RNF08 Concurrencia | ☑ | `SELECT … FOR UPDATE` sobre el reporte |
| RNF15 Fiabilidad | ☑ | Cambio de estado y E16 en una transacción |
| RNF16 Trazabilidad | ☑ | Responsable, nota y fecha |
| RNF18 Notificaciones | ☑ | E16 al autor |
| RNF21 Auditoría | ☑ | Quién resolvió y con qué nota |
| RNF24 Errores claros | ☑ | `409`, `400`, `404` |
| RNF27 Integridad | ☑ | `Idempotency-Key` |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Reporte | Escribe | `estado` → `RESUELTO`; `nota_resolucion` obligatoria; `resuelto_por_id` = administrador; `updated_at` |
| Usuario | Lee | Autor del reporte (para E16) |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Reporte | `PENDIENTE` o `EN_REVISION` | `RESUELTO` | El administrador resuelve | Nota válida | Guarda nota, responsable y fecha; E16 |

Desde `RESUELTO` no hay transición (`409 INVALID_STATE`).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /reportes/{id}/resolver` | ADMINISTRADOR | Cualquier reporte |

## API Contracts

### POST /api/v1/reportes/{id}/resolver

- **Rol**: administrador · **Path params**: `id` (UUID del reporte) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅, `Content-Type: application/json`.
- **Body**: `{ "nota": "Se contactó al cliente y se acordó una nueva entrega." }` (de 5 a 1 000 caracteres, sin espacios sobrantes).
- **Respuestas exitosas**: `200` con `X-Request-Id` y body `{ "id": "…", "estado": "RESUELTO", "notaResolucion": "…", "resueltoPor": { "id": "…", "nombre": "…" }, "updatedAt": "…" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key`; nota vacía, en blanco, de menos de 5 o más de 1 000 caracteres | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de administrador | — |
| 404 | `NOT_FOUND` | El reporte no existe | "No encontramos el reporte" |
| 409 | `INVALID_STATE` | El reporte ya está `RESUELTO` (`details.estadoActual`) | "Este reporte ya fue resuelto" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

La ruta usa `POST` con verbo de acción por la convención de spec-general §2.1.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E16 `reportes.reporte-resuelto` (`ReporteResueltoEvent`: `reporteId`, `autorId`) | `ResolverReporteUseCase` | En la misma transacción de la resolución (outbox) |

**Eventos que consume esta feature**: ninguno. El listener de E16 y la notificación `REPORTE_RESUELTO` están en RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la resolución requiere respuesta inmediata (otro administrador pudo resolverlo antes), así que no usa el outbox; sin red se muestra el error y la nota escrita se conserva en pantalla.
- **Concurrencia (RNF08)**: bloqueo de la fila del reporte; si dos administradores resuelven a la vez, el segundo recibe `409 INVALID_STATE`.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`; el reintento con la misma clave devuelve la misma respuesta.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Reporte ya resuelto | `409 INVALID_STATE` | FR-004, US1-E2 |
| Nota vacía o muy corta | `400` con el campo | FR-002 |
| Reporte inexistente | `404` | — |
| Dos administradores a la vez | Uno `200`, otro `409` | RNF08 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Reporte.resolver` (estados de origen, nota, responsable) | FR-001, FR-002, FR-004 |
| Integración | Endpoint con Testcontainers: resolver desde `PENDIENTE` y desde `EN_REVISION`; nota vacía, en blanco, corta y larga; ya resuelto; inexistente; roles; E16 en el outbox y notificación al autor; idempotencia | FR-001–FR-004, US1-E1–E2 |
| Concurrencia | Dos administradores sobre el mismo reporte | RNF08 |
| Migración | CHECK de nota obligatoria cuando el estado es `RESUELTO` | SC-002 |
| Frontend | Jest: diálogo con nota, validación, errores, estado actualizado en el detalle | US1 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-62-resolucion-de-reportes/
├── RF-62-resolucion-de-reportes.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/reportes/
├── domain/
│   ├── event/                    # ReporteResueltoEvent (E16)
│   └── model/                    # Reporte.resolver
├── application/
│   ├── ports/in/                 # ResolverReportePort
│   ├── ports/out/                # ReporteRepositoryPort (bloquear), EventPublisherPort (shared)
│   └── usecase/                  # ResolverReporteUseCase
└── infrastructure/adapters/in/web/   # ReporteController (POST /reportes/{id}/resolver)
backend/src/main/resources/db/migration/   # siguiente migración libre: CHECK de nota en RESUELTO
backend/src/test/java/co/edu/napa/reportes/

frontend/src/components/reportes/DialogoResolverReporte.tsx    # nota obligatoria y confirmar
frontend/src/services/reportes.ts                              # resolverReporte()
frontend/src/app/(administrador)/reportes/[id].tsx             # (RF-61) botón "Resolver"
frontend/__tests__/reportes/resolver-reporte.test.tsx
```

**Structure Decision**: módulo `reportes`, el mismo de RF-60 y RF-61. `Reporte.resolver` concentra la regla de estados y la nota; el caso de uso bloquea la fila, resuelve y publica E16 en la misma transacción. El diálogo se integra en la pantalla de detalle de RF-61.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `reportes`, las columnas de V12 y que RF-38 (T028) escucha E16

## Phase 2: Foundational

- [ ] T002 `ReporteResueltoEvent` (E16) y excepción `ReporteYaResueltoException` en `reportes/domain/`
- [ ] T003 CHECK en la siguiente migración libre: `estado <> 'RESUELTO' OR (nota_resolucion IS NOT NULL AND resuelto_por_id IS NOT NULL)`
- [ ] T004 `ReporteRepositoryPort.bloquearPorId`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Cerrar un reporte con una solución (Priority: P2)

**Goal**: el administrador deja constancia de la solución y el autor se entera.
**Independent Test**: resolver un reporte pendiente y ver `RESUELTO`, la nota y la notificación.
**Cubre**: FR-001–FR-004, SC-001, SC-002, US1-E1–E2, Edge 2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /reportes/{id}/resolver` desde `PENDIENTE` y desde `EN_REVISION` (`200`, nota, responsable, `updatedAt`) en `backend/src/test/java/co/edu/napa/reportes/ResolverReporteIT.java`
- [ ] T011 [P] [US1] Test parametrizado de nota ausente, vacía, en blanco, de menos de 5 y de más de 1 000 caracteres (`400`) en `.../reportes/ResolverReporteNotaIT.java`
- [ ] T012 [P] [US1] Test de integración: E16 en el outbox con `reporteId` y `autorId` y notificación `REPORTE_RESUELTO` al autor con el listener de RF-38 en `.../reportes/ResolverReporteEventoIT.java`
- [ ] T013 [P] [US1] Test de reporte ya resuelto (`409 INVALID_STATE` con `estadoActual`, sin cambios ni evento) en `.../reportes/ResolverReporteYaResueltoIT.java`
- [ ] T014 [P] [US1] Test de concurrencia: dos administradores resuelven a la vez (uno `200`, otro `409`, un solo E16) en `.../reportes/ResolverReporteConcurrenciaIT.java`
- [ ] T015 [P] [US1] Test de roles (`403`), sin sesión (`401`), reporte inexistente (`404`) e idempotencia (misma clave, clave reutilizada `422`, falta de clave `400`) en `.../reportes/ResolverReporteAutorizacionIT.java`
- [ ] T016 [P] [US1] Test unitario de `Reporte.resolver` y test de migración del CHECK de nota en `.../reportes/ReporteResolverTest.java`
- [ ] T030 [P] [US1] Test del diálogo (nota obligatoria, errores, estado actualizado en el detalle) en `frontend/__tests__/reportes/resolver-reporte.test.tsx`

### Implementation

- [ ] T020 [US1] `Reporte.resolver` y `ResolverReporteUseCase` con `ResolverReportePort` en `reportes/`
- [ ] T021 [US1] `POST /reportes/{id}/resolver` y su DTO en `ReporteController`
- [ ] T022 [US1] Publicación de E16 en el caso de uso
- [ ] T031 [US1] `resolverReporte()` en `frontend/src/services/reportes.ts` (sin outbox, requiere conexión)
- [ ] T032 [US1] `DialogoResolverReporte` y botón "Resolver" en `frontend/src/app/(administrador)/reportes/[id].tsx` para reportes no resueltos

**Checkpoint**: el ciclo del reporte queda cerrado.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir el aviso al autor (< 1 min, SC-001)
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-60, RF-61 (pantalla), RF-38 (listener E16) y RF-10.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: reabrir un reporte cerrado por error | Regla | No se permite; si se necesita, hay que definir un RF | Equipo |
| La nota tiene entre 5 y 1 000 caracteres (la spec no fija longitudes) | Datos | Esos valores | Equipo |
| El autor no puede leer la nota de resolución: no hay endpoint para él y E16 solo lleva `reporteId` y `autorId` | UX | La notificación dice que su reporte fue resuelto, sin la nota. Mejora posible: incluir la nota en E16 o permitir al autor consultar sus reportes | Equipo |
| No hay `resuelto_at`; se usa `updated_at` (que no cambia después de resolver) | Modelo | `updated_at` como fecha de resolución | Equipo |
| La ruta se llama `resolver` (acción) en vez de `PATCH /reportes/{id}/resolucion` del plan genérico anterior del repo | Convención | Spec-general §2.1: las acciones son `POST /{recurso}/{id}/{accion}` | Equipo |
| La ruta no está en spec-general §2.4 | Contrato | Agregarla (administrador, con `Idempotency-Key`) | Equipo |
| Resolver sin pasar por `EN_REVISION` | Flujo | Permitido | Equipo |

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
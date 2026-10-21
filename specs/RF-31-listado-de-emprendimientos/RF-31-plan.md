# Implementation Plan: Listado de emprendimientos

**Date**: 2026-10-21
**Spec**: [RF-31-listado-de-emprendimientos.md](./RF-31-listado-de-emprendimientos.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-31
**Historias de usuario**: HU-56
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-28 (tabla `emprendimiento`), RF-10, foundations (PostGIS, índice GiST)
**Habilita a**: RF-32, RF-33, RF-45

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Listado paginado de emprendimientos `ACTIVO` para el cliente, con nombre, categoría, tipo de vendedor (derivado de `Usuario.rol`), ubicación y si está abierto ahora (calculado con `hora_apertura` y `hora_cierre` en hora local de Colombia, incluyendo horarios que cruzan la medianoche). Se puede filtrar por tipo de vendedor y ordenar por cercanía con PostGIS (`ST_DWithin` / `ST_Distance` sobre el índice GiST). La app guarda el último listado en SQLite para verlo sin señal.

## Estado actual del código

No existe el módulo `emprendimientos` ni la consulta. Existen `ubicacion.punto` con índice GiST (V3) y `TimeConfiguration` en `shared/infrastructure/config`.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Explora el listado |
| Vendedor (ambulante / punto fijo) | No interviene |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Carga del listado en < 3 s (SC-001) y consultas PostGIS ≤ 100 ms p95 (índice GiST).
- 100 % de los emprendimientos activos visibles (SC-002): sin filtros ocultos salvo los pedidos por el cliente.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Consultar el listado de emprendimientos activos | `GET /emprendimientos`; `ListarEmprendimientosUseCase` | T010, T013, T014 |
| FR-002 | Mostrar nombre, categoría, tipo de vendedor y ubicación | Ítem de respuesta con esos campos más `abierto` y `distanciaMetros` | T010, T015 |
| FR-003 | Excluir inactivos y eliminados | `estado = 'ACTIVO'` y vendedor con cuenta `ACTIVA` | T011 |
| FR-004 | Ordenar por cercanía y filtrar por tipo de vendedor | `orden=cercania` con `lat`/`lng`; filtros `tipoVendedor`, `radioMetros` | T012, T016, T017 |
| SC-001 | < 3 s | Índice GiST + paginación | T030 |
| SC-002 | 100 % visibles | Prueba de integración | T010 |
| US1-E1 | Listado de activos | Endpoint | T010 |
| US1-E2 | Sin emprendimientos | `200` con `content: []`; mensaje en la app | T018, T023 |
| Edge 1 | Eliminado mientras el cliente tiene el listado abierto | El detalle responde `404` (RF-32) y la app refresca la lista | T024 |
| Edge 2 | Fuera de horario | El ítem trae `abierto: false` y la app lo muestra como "Cerrado" | T019 |
| Edge 3 | Gran cantidad de emprendimientos | Paginación obligatoria (`size` ≤ 100, por defecto 20) | T010 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo cliente |
| RNF04 Privacidad | ☑ | La posición del cliente no se guarda; solo se usa para la consulta |
| RNF07 Rendimiento | ☑ | Índice GiST y paginación |
| RNF22 Geografía | ☑ | `ST_DWithin` / `ST_Distance` sobre `geography` |
| RNF23 Consistencia | ☑ | Estados del enum |
| RNF24 Errores claros | ☑ | Filtro desconocido → `400` |
| RNF28 Conectividad | ☑ | Caché de lectura SQLite |
| RNF31 Gama baja | ☑ | Lista paginada y sin imágenes |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Emprendimiento | Lee | Solo `ACTIVO` |
| Ubicacion | Lee | `punto` con índice GiST |
| Usuario | Lee (rol, estado) | Tipo de vendedor = rol; excluye cuentas no `ACTIVA` |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Listar emprendimientos | CLIENTE | Ninguna: son públicos entre usuarios autenticados |

## API Contracts

### GET /api/v1/emprendimientos

- **Rol**: cliente
- **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| tipoVendedor | enum | ☐ | `AMBULANTE` | `AMBULANTE` (rol `VENDEDOR_AMBULANTE`) o `PUNTO_FIJO` (rol `VENDEDOR_PUNTO_FIJO`) |
| lat, lng | decimal | ☐ (obligatorios con `orden` o `radioMetros`) | `11.24`, `-74.21` | Posición del cliente |
| radioMetros | int | ☐ | `2000` | Solo los emprendimientos dentro del radio |
| orden | enum | ☐ | `cercania` | Ordena por distancia ascendente |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `nombre,asc` | Paginación; orden por defecto si no hay `orden` (Open Questions) |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `{ "content": [{ "id", "nombre", "categoria", "tipoVendedor", "abierto", "horaApertura", "horaCierre", "distanciaMetros", "ubicacion": { "latitud", "longitud", "direccion" } }], "page", "size", "totalElements" }` y `X-Request-Id`. `distanciaMetros` solo si se enviaron `lat`/`lng`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido, `orden=cercania` o `radioMetros` sin `lat`/`lng`, coordenadas fuera de rango | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de cliente | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda la última página consultada en SQLite y la muestra con "Última actualización: hh:mm" si no hay red; al reconectar refresca. El servidor es la autoridad.
- **Concurrencia (RNF08)**: no aplica (lectura).
- **Idempotencia (RNF27)**: no aplica.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Sin emprendimientos | `200` + `content: []` y mensaje "No hay emprendimientos disponibles" | US1-E2 |
| `orden=cercania` sin posición | `400`; la app pide el permiso de ubicación o usa el orden por defecto | FR-004 |
| Permiso de ubicación denegado | La app ofrece el listado sin orden por cercanía | RNF04 |
| Emprendimiento ya eliminado | El detalle responde `404` (RF-32) | Edge 1 |
| Sin conexión | Caché con la hora de actualización | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Cálculo de `abierto` (horario normal y que cruza la medianoche, límites exactos) | Edge 2 |
| Integración | Endpoint con PostGIS en Testcontainers: exclusión de inactivos/eliminados, filtro por tipo, radio, orden por cercanía, paginación | FR-001–FR-004, SC-002 |
| Rendimiento | Consulta con 10 000 emprendimientos de prueba ≤ 100 ms p95 | SC-001 |
| Frontend | Jest: estado vacío, filtros, caché sin red, permiso de ubicación denegado | US1-E2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-31-listado-de-emprendimientos/
├── RF-31-listado-de-emprendimientos.md   # spec
└── plan.md                               # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/emprendimientos/
├── domain/model/                 # HorarioAtencion (calcula `abierto`), TipoVendedor
├── application/
│   ├── ports/in/                 # ListarEmprendimientosPort
│   ├── ports/out/                # EmprendimientoRepositoryPort (consulta espacial y paginada)
│   └── usecase/                  # ListarEmprendimientosUseCase
└── infrastructure/adapters/
    ├── in/web/                   # EmprendimientoController (GET), DTOs
    └── out/persistence/          # consulta PostGIS (ST_DWithin / ST_Distance)
backend/src/test/java/co/edu/napa/emprendimientos/

frontend/src/app/(cliente)/emprendimientos/index.tsx          # ruta Expo Router: listado
frontend/src/components/emprendimiento/TarjetaEmprendimiento.tsx
frontend/src/components/emprendimiento/FiltrosEmprendimientos.tsx
frontend/src/services/emprendimientos.ts                      # listarEmprendimientos()
frontend/src/hooks/useLocation.ts                             # permiso y posición del cliente
frontend/src/database/schema/emprendimientos_cache.ts         # caché de lectura
frontend/__tests__/emprendimientos/listado.test.tsx
```

**Structure Decision**: módulo `emprendimientos`. La consulta espacial vive en el adaptador de persistencia; el dominio solo conoce `HorarioAtencion`. La hora actual sale del `Clock` configurado en `TimeConfiguration` (zona America/Bogota) para poder probar `abierto`.

## Phase 1: Setup

- [ ] T001 Confirmar la tabla `emprendimiento` (RF-28, T002) y el índice GiST `ix_ubicacion_punto`

## Phase 2: Foundational

- [ ] T002 Consulta espacial paginada en `EmprendimientoRepositoryPort` y su adaptador en `emprendimientos/application/ports/out/` y `.../out/persistence/`
- [ ] T003 `Clock` inyectable (zona America/Bogota) usado por `HorarioAtencion` en `shared/infrastructure/config/TimeConfiguration.java`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Explorar emprendimientos disponibles (Priority: P1)

**Goal**: el cliente descubre vendedores y cuáles están cerca.
**Independent Test**: registrar un emprendimiento activo y verlo en el listado del cliente.
**Cubre**: FR-001–FR-004, SC-001–SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración del listado (activos, paginación, tamaño máximo) en `backend/src/test/java/co/edu/napa/emprendimientos/ListarEmprendimientosIT.java`
- [ ] T011 [P] [US1] Test de exclusión de inactivos, eliminados y de vendedores con cuenta no activa en `.../emprendimientos/ListarEmprendimientosExclusionIT.java`
- [ ] T012 [P] [US1] Test de filtro por `tipoVendedor`, `radioMetros` y `orden=cercania` en `.../emprendimientos/ListarEmprendimientosEspacialIT.java`
- [ ] T019 [P] [US1] Test unitario de `abierto` (horario normal, cruza medianoche, igual a la hora de apertura y de cierre) en `.../emprendimientos/HorarioAtencionTest.java`
- [ ] T018 [P] [US1] Test de listado vacío y de parámetros inválidos (`400`) en `.../emprendimientos/ListarEmprendimientosVacioIT.java`
- [ ] T023 [P] [US1] Test de la pantalla (vacío, filtros, caché sin red, permiso denegado) en `frontend/__tests__/emprendimientos/listado.test.tsx`

### Implementation

- [ ] T013 [US1] `ListarEmprendimientosUseCase` en `emprendimientos/application/usecase/`
- [ ] T014 [US1] `GET /emprendimientos` en `EmprendimientoController`
- [ ] T015 [US1] DTO de ítem con `abierto` y `distanciaMetros`
- [ ] T016 [US1] Filtros y validación de parámetros (incluye `400` ante desconocidos)
- [ ] T017 [US1] Orden por cercanía con `ST_Distance` y radio con `ST_DWithin`
- [ ] T020 [US1] Servicio `listarEmprendimientos()` con caché SQLite en `frontend/src/services/emprendimientos.ts` y `frontend/src/database/schema/emprendimientos_cache.ts`
- [ ] T021 [US1] `TarjetaEmprendimiento` y `FiltrosEmprendimientos` en `frontend/src/components/emprendimiento/`
- [ ] T022 [US1] Pantalla `frontend/src/app/(cliente)/emprendimientos/index.tsx` con scroll paginado, estado vacío y orden por cercanía si hay permiso
- [ ] T024 [US1] Al recibir `404` desde el detalle (RF-32), quitar el emprendimiento de la lista y refrescar

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Medir el listado < 3 s y la consulta ≤ 100 ms p95 (SC-001)
- [ ] T031 Revisar privacidad de la posición del cliente (RNF04)
- [ ] T032 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-28; RF-32 y RF-45 parten de esta lista.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Orden por defecto sin `orden=cercania` | UX | `created_at` descendente (los más nuevos primero) | Equipo |
| ¿Los emprendimientos cerrados se muestran al final o mezclados? | Edge 2 | Mezclados, con etiqueta "Cerrado" | Equipo |
| ¿Se excluyen emprendimientos de vendedores con cuenta no `ACTIVA`? | FR-003 | Sí | Equipo |
| ¿Radio máximo permitido en `radioMetros`? | Rendimiento | Sin tope en el MVP | Equipo |
| ¿Pueden listar otros roles (vendedor, domiciliario)? El catálogo dice "cliente" | RNF03 | Solo cliente | Equipo |
| La ubicación del ambulante es su zona habitual, no su posición en vivo: la cercanía es aproximada | FR-004 | Así | Equipo |

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
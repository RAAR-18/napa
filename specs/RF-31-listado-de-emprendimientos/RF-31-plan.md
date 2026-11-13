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

RF-28 implementa el módulo `emprendimientos`, su tabla y endpoint de creación; existen `ubicacion.punto` con índice GiST (V3) y un `Clock` global UTC en `shared/infrastructure/config`. RF-31 ya cuenta con consulta paginada PostGIS, endpoint autenticado de cliente, filtros, pantalla Expo Router, permiso de ubicación bajo demanda y caché SQLite. El cálculo de horario convierte el instante inyectado a `America/Bogota` sin cambiar la zona del reloj global.

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

frontend/src/app/emprendimientos.tsx                           # ruta Expo Router: listado
frontend/src/lib/emprendimientos.ts                            # servicio y tipos del listado
frontend/src/lib/emprendimientos-cache.ts                      # caché SQLite de lectura
frontend/src/app/__tests__/emprendimientos.test.tsx            # filtros, vacío, offline, ubicación
frontend/src/lib/emprendimientos-cache.test.ts                 # privacidad y validación de caché
```

**Structure Decision**: módulo `emprendimientos`. La consulta espacial vive en el adaptador de persistencia; el dominio solo conoce `HorarioAtencion`. La hora actual sale del `Clock` configurado en `TimeConfiguration` (zona America/Bogota) para poder probar `abierto`. La pantalla reutiliza `OptionButton` compartido y mantiene sus tarjetas y filtros inline; no se crean componentes de presentación triviales.

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

- [x] T010 [P] [US1] Test de integración del listado (activos, paginación, tamaño máximo) en `backend/src/test/java/co/edu/napa/emprendimientos/infrastructure/persistence/EmprendimientoPersistenceIntegrationTest.java`
- [x] T011 [P] [US1] Test de exclusión de inactivos, eliminados y vendedores con cuenta no activa en `EmprendimientoPersistenceIntegrationTest.java`
- [x] T012 [P] [US1] Test de filtro por `tipoVendedor`, `radioMetros` y `orden=cercania` en `EmprendimientoPersistenceIntegrationTest.java`
- [x] T019 [P] [US1] Test unitario de `abierto` (incluye medianoche, apertura y cierre) en `backend/src/test/java/co/edu/napa/emprendimientos/domain/model/HorarioAtencionTest.java`
- [x] T018 [P] [US1] Test de listado vacío y parámetros inválidos (`400`) en los tests de controller y persistencia
- [x] T023 [P] [US1] Test de pantalla (vacío, filtros, caché sin red, permiso denegado y radio) en `frontend/src/app/__tests__/emprendimientos.test.tsx`

### Implementation

- [x] T013 [US1] `ListarEmprendimientosUseCase` en `backend/src/main/java/co/edu/napa/emprendimientos/application/usecase/`
- [x] T014 [US1] `GET /emprendimientos` para el rol cliente en `EmprendimientoController`
- [x] T015 [US1] DTO de ítem con `abierto` y `distanciaMetros`
- [x] T016 [US1] Filtros y validación de parámetros (incluye `400` ante desconocidos)
- [x] T017 [US1] Orden por cercanía con `ST_Distance` y radio con `ST_DWithin`
- [x] T020 [US1] Servicio y caché SQLite en `frontend/src/lib/emprendimientos.ts` y `emprendimientos-cache.ts`
- [x] T021 [US1] Filtros con `OptionButton` compartido y tarjetas accesibles en la pantalla de listado
- [x] T022 [US1] Pantalla `frontend/src/app/emprendimientos.tsx` con paginación, estado vacío y orden por cercanía bajo permiso
- [ ] T024 [US1] Al recibir `404` desde el detalle (RF-32), quitar el emprendimiento de la lista y refrescar

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Medir el listado < 3 s y la consulta ≤ 100 ms p95 (SC-001)
- [x] T031 Revisar privacidad de la posición del cliente (RNF04); la app no guarda coordenadas y elimina distancias antes de persistir la respuesta en caché
- [x] T032 Revisar trazabilidad: no existe `requerimientos/trazabilidad.md`; se actualizó el catálogo de rutas y filtros en `spec-general.md`

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

- [x] La tabla Spec Coverage no tiene filas vacías
- [x] Todas las tareas de implementación y de testing están presentes
- [x] La arquitectura es coherente con los módulos existentes (RNF13, RNF25); solo hay `usecase`, sin capa `service` de aplicación
- [x] Entidades referenciadas a `spec-general.md` (sin redefinir el modelo); no se requiere cambio de esquema
- [x] Contratos de API con path/query params, headers, códigos HTTP exitosos y de error, y matriz de permisos
- [x] Eventos emitidos documentados en `eventos.md`; no hay listeners planificados
- [x] Actores (comprador, vendedor, domiciliario, administrador) identificados
- [x] Errores y casos borde tienen comportamiento definido
- [x] Estrategia de conectividad y concurrencia definida cuando aplica (RNF28, RNF08)
- [x] Dependencias identificadas: `expo-location` para pedir la posición y `expo-sqlite` para caché de lectura
- [x] Cada tarea es granular y nombra el archivo que toca
- [x] No se agregó comportamiento que no esté en la spec

## Notes

- `[USn]` en cada tarea enlaza con su historia de usuario.
- Hacer commit por tarea o grupo lógico.
- Evitar tareas vagas y conflictos en un mismo archivo.
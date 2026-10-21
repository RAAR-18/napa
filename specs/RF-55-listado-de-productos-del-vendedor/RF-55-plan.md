# Implementation Plan: Listado de productos del vendedor

**Date**: 2026-10-21
**Spec**: [RF-55-listado-de-productos-del-vendedor.md](./RF-55-listado-de-productos-del-vendedor.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-55
**Historias de usuario**: HU-86
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-54 (tabla `producto`), RF-28
**Habilita a**: RF-56, RF-57, RF-58 (punto de partida de la gestión del catálogo)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Listado paginado de los productos del emprendimiento `ACTIVO` del vendedor autenticado, con precio, unidad, cantidad y estado (`DISPONIBLE` / `AGOTADO`), filtrable por estado. Los productos `ELIMINADO` no aparecen. Sin productos devuelve una lista vacía y la app muestra un estado vacío. La app guarda una caché de lectura en SQLite para que el vendedor vea su catálogo aun sin señal.

## Estado actual del código

La tabla `producto` la crea el plan de RF-54 (migración V5). No hay código de lectura todavía.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene (usa RF-33) |
| Vendedor (ambulante / punto fijo) | Consulta su listado |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Listado en < 2 s (SC-001): índice `(emprendimiento_id, estado)`, paginación por defecto de 20.
- 100 % de los productos publicados visibles con precio y disponibilidad actualizados (SC-002): lectura directa de la tabla, sin caché en servidor.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Consultar el listado completo de los productos publicados | `GET /productos`; `ListarProductosVendedorUseCase` | T010, T013, T014 |
| FR-002 | Mostrar precio y disponibilidad actual | Cada ítem trae `precio`, `unidad`, `cantidadDisponible`, `estado` | T010, T015 |
| FR-003 | Distinguir visualmente los agotados | La API envía `estado: AGOTADO`; la app los muestra con etiqueta y estilo atenuado | T011, T021 |
| SC-001 | < 2 s | Índice + paginación | T030 |
| SC-002 | 100 % con datos actualizados | Prueba de integración tras registrar y actualizar | T010 |
| US1-E1 | Listado con productos | Endpoint | T010 |
| US1-E2 | Listado sin productos | `200` con `content: []`; estado vacío en la app | T012, T022 |
| Edge 1 | Cómo se muestran los agotados | `AGOTADO` visible, filtrable con `estado=AGOTADO` | T011 |
| Edge 2 | Productos en más de un emprendimiento | Solo hay un emprendimiento `ACTIVO` por vendedor (restricción 1): se lista el de ese emprendimiento (Open Questions) | T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo roles vendedor; el emprendimiento sale del JWT |
| RNF07 Rendimiento | ☑ | Índice + paginación |
| RNF16 Trazabilidad | ☑ | Datos con `updated_at` |
| RNF23 Consistencia | ☑ | Los estados vienen del enum del dominio |
| RNF24 Errores claros | ☑ | Filtro desconocido → `400` |
| RNF28 Conectividad | ☑ | Caché de lectura SQLite |
| RNF31 Gama baja | ☑ | `FlatList` paginada, sin imágenes |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Producto | Lee | Excluye `ELIMINADO` |
| Emprendimiento | Lee (puerto `in`) | El `ACTIVO` del vendedor |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Listar productos | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | Solo los del emprendimiento del usuario autenticado |

## API Contracts

### GET /api/v1/productos

- **Rol**: vendedor
- **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| estado | enum | ☐ | `AGOTADO` | `DISPONIBLE` o `AGOTADO` (spec-general §2.3) |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `nombre,asc` | Paginación y orden; por defecto `nombre,asc` |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `{ "content": [{ "id", "nombre", "descripcion", "unidad", "precio", "cantidadDisponible", "estado", "updatedAt" }], "page", "size", "totalElements" }` y `X-Request-Id`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido o valor de `estado` inválido (incluye `ELIMINADO`) | Indica el filtro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor | — |

Un vendedor sin emprendimiento activo recibe `200` con lista vacía.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda el último listado en SQLite (`database/schema`) y lo muestra con "Última actualización: hh:mm" cuando no hay red; al reconectar refresca. El servidor es la autoridad.
- **Concurrencia (RNF08)**: no aplica (lectura).
- **Idempotencia (RNF27)**: no aplica.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Sin productos | `200` + `content: []` y estado vacío | US1-E2 |
| Filtro desconocido | `400` | spec-general §2.3 |
| Sin conexión | Se muestra la caché con la hora de actualización | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Mapeo de producto a ítem de listado | FR-002 |
| Integración | Endpoint, filtro por estado, exclusión de `ELIMINADO`, paginación, aislamiento entre vendedores | US1-E1, US1-E2, Edge 1 |
| Frontend | Jest: estado vacío, etiqueta de agotado, uso de caché sin red | FR-003, US1-E2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-55-listado-de-productos-del-vendedor/
├── RF-55-listado-de-productos-del-vendedor.md   # spec
└── plan.md                                      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/productos/
├── application/ports/in/         # ListarProductosVendedorPort
├── application/ports/out/        # ProductoRepositoryPort (consulta paginada por emprendimiento y estado)
├── application/usecase/          # ListarProductosVendedorUseCase
└── infrastructure/adapters/
    ├── in/web/                   # ProductoController (GET), DTO de ítem
    └── out/persistence/          # consulta paginada en el repositorio
backend/src/test/java/co/edu/napa/productos/

frontend/src/app/(vendedor)/productos/index.tsx          # ruta Expo Router: "Mis productos"
frontend/src/components/productos/ItemProducto.tsx       # fila con precio, cantidad y etiqueta "Agotado"
frontend/src/components/productos/FiltroEstado.tsx
frontend/src/services/productos.ts                       # listarProductos()
frontend/src/database/schema/productos_cache.ts          # caché de lectura
frontend/__tests__/productos/listado.test.tsx
```

**Structure Decision**: módulo `productos` (mismo que RF-54). La consulta usa el emprendimiento activo del vendedor por `EmprendimientoActivoPort` (definido en RF-54).

## Phase 1: Setup

- [ ] T001 Confirmar la tabla `producto` y el índice `(emprendimiento_id, estado)` de RF-54 (T002)

## Phase 2: Foundational

- [ ] T002 Consulta paginada y filtrada en `ProductoRepositoryPort` y su adaptador en `productos/application/ports/out/` y `.../out/persistence/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Consultar mis productos publicados (Priority: P1)

**Goal**: el vendedor ve su catálogo completo.
**Independent Test**: registrar un producto y verlo en "Mis productos" con precio y disponibilidad.
**Cubre**: FR-001–FR-003, SC-001–SC-002, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración del listado (datos, paginación, orden, aislamiento entre vendedores) en `backend/src/test/java/co/edu/napa/productos/ListarProductosVendedorIT.java`
- [ ] T011 [P] [US1] Test del filtro `estado` y de productos agotados en `.../productos/ListarProductosFiltroIT.java`
- [ ] T012 [P] [US1] Test de listado vacío y de vendedor sin emprendimiento activo en `.../productos/ListarProductosVacioIT.java`
- [ ] T016 [P] [US1] Test de que no aparecen productos `ELIMINADO` ni de otros emprendimientos en `.../productos/ListarProductosExclusionIT.java`
- [ ] T023 [P] [US1] Test de la pantalla (estado vacío, etiqueta de agotado, caché sin red) en `frontend/__tests__/productos/listado.test.tsx`

### Implementation

- [ ] T013 [US1] `ListarProductosVendedorUseCase` en `productos/application/usecase/`
- [ ] T014 [US1] `GET /productos` en `ProductoController`
- [ ] T015 [US1] DTO de ítem y validación de filtros (`400` ante desconocidos)
- [ ] T020 [US1] Servicio `listarProductos()` con caché SQLite en `frontend/src/services/productos.ts` y `frontend/src/database/schema/productos_cache.ts`
- [ ] T021 [US1] `ItemProducto` y `FiltroEstado` en `frontend/src/components/productos/`
- [ ] T022 [US1] Pantalla `frontend/src/app/(vendedor)/productos/index.tsx` con paginación, estado vacío y actualización al deslizar

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Medir el listado < 2 s (SC-001)
- [ ] T031 Revisar permisos (RNF03)
- [ ] T032 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-54; RF-56, RF-57 y RF-58 parten de esta pantalla.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 2 "más de un emprendimiento": la restricción 1 permite un solo `ACTIVO` por vendedor, pero pueden existir `INACTIVO`/`ELIMINADO`. ¿Se listan solo los productos del `ACTIVO`? | Alcance del listado | Solo los del emprendimiento `ACTIVO` | Equipo |
| Orden por defecto | UX | `nombre,asc` | Equipo |
| ¿La lista muestra primero los disponibles y luego los agotados? | UX | No: el orden es el elegido | Equipo |

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
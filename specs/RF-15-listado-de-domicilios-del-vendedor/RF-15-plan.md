# Implementation Plan: Listado de domicilios del vendedor

**Date**: 2026-10-27
**Spec**: [RF-15-listado-de-domicilios-del-vendedor.md](./RF-15-listado-de-domicilios-del-vendedor.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-15
**Historias de usuario**: HU-32
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-14, RF-26, RF-10
**Habilita a**: RF-23 y RF-43 (acceso desde la lista)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor de punto fijo lista los domicilios de sus pedidos con `GET /domicilios`: cada fila muestra el pedido, el estado actual del domicilio (`DISPONIBLE`, `ASIGNADO`, `RECOGIDO`, `EN_CAMINO`, `ENTREGADO` o `CANCELADO`), la tarifa y la fecha de creación; se filtra por estado y por rango de fechas, se pagina y se ordena del más reciente al más antiguo. Es el seguimiento de sus envíos y la entrada a la trazabilidad (RF-26), a la salida del domicilio (RF-23) y al pago de la tarifa (RF-43). Un vendedor sin domicilios recibe lista vacía. La app guarda la última página en SQLite.

## Estado actual del código

El módulo `domicilios` y su tabla `domicilio` los crea RF-14 (V10). RF-16 usa `GET /domicilios/disponibles` y RF-18 `GET /domicilios/{id}`; esta lista es otra ruta (`GET /domicilios`) sin parámetros de ruta. La tarifa se lee del pedido por `ConsultarPedidoParaDomicilioPort` (RF-14).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene (ve su domicilio en RF-26) |
| Vendedor ambulante | No interviene (no publica domicilios) |
| Vendedor de punto fijo | Lista los domicilios de sus pedidos |
| Domiciliario | No interviene |
| Administrador | La consulta del administrador es RF-12 |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Listado filtrado en < 3 s (SC-001): consulta paginada por `vendedor_id` con índice `(vendedor_id, created_at)`.
- 100 % de los domicilios que cumplen el filtro aparecen (SC-002): el filtro se aplica en la base, no en la app, y la paginación usa orden estable (`createdAt desc`, `id`).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El vendedor de punto fijo consulta sus domicilios | `GET /domicilios` para VENDEDOR_PUNTO_FIJO, solo los de sus pedidos | T010, T011, T020, T021 |
| FR-002 | Filtrar por estado (seis estados) | Parámetro `estado` con los seis valores | T012 |
| FR-003 | Filtrar por rango de fechas | `desde` y `hasta` sobre `created_at` | T012 |
| SC-001 | < 3 s | Índice y paginación | T014 |
| SC-002 | Sin omisiones al filtrar | Filtros en la consulta y orden estable | T012, T013 |
| US1-E1 | Listado sin filtros | `200` con todos sus domicilios y su estado | T010 |
| US1-E2 | Filtro por estado o fecha | Solo los que cumplen | T012 |
| Edge 1 | Vendedor sin domicilios | `200` con lista vacía y mensaje | T015, T030 |
| Edge 2 | Filtro sin resultados | `200` con lista vacía y "sin resultados" | T012 |
| Edge 3 | Filtros de estado y fecha combinados sin resultados | Igual | T012 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Rol VENDEDOR_PUNTO_FIJO y pedidos propios |
| RNF04 Privacidad | ☑ | La fila no lleva datos del cliente ni del domiciliario |
| RNF07 Rendimiento ≤ 3 s | ☑ | Índice y paginación |
| RNF24 Errores claros | ☑ | `400` con el filtro |
| RNF28 Conectividad | ☑ | Caché local de lectura |
| RNF31 Gama baja | ☑ | Lista paginada |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Lee | Solo los del vendedor autenticado, por `domicilio.vendedor_id` (lo fija RF-14 al publicar) |
| Pedido | Lee (puerto) | Solo la tarifa, en lote |

## State Transitions

Esta feature es de solo lectura: no cambia estados.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /domicilios` | VENDEDOR_PUNTO_FIJO | `domicilio.vendedor_id` = vendedor autenticado |

## API Contracts

### GET /api/v1/domicilios

- **Rol**: vendedor de punto fijo · **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| estado | enum | ☐ | `EN_CAMINO` | `DISPONIBLE`, `ASIGNADO`, `RECOGIDO`, `EN_CAMINO`, `ENTREGADO` o `CANCELADO` |
| desde, hasta | fecha ISO-8601 | ☐ | `2026-10-01` | Rango sobre la fecha de creación del domicilio |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `createdAt,desc` | Paginación y orden |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id`, `Cache-Control: no-store` y body:

```json
{
  "content": [ { "id": "…", "pedidoId": "…", "estado": "EN_CAMINO", "tarifa": 5000, "createdAt": "…", "updatedAt": "…" } ],
  "page": 0, "size": 20, "totalElements": 1
}
```

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido, `estado` inválido, `desde` mayor que `hasta`, `size` mayor que 100 | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor de punto fijo | — |

Un vendedor sin domicilios recibe `200` con lista vacía. La ruta se declara antes que `GET /domicilios/{id}` y `GET /domicilios/disponibles` para que no se confundan.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda la primera página y la muestra con "datos guardados" sin red; los filtros requieren conexión.
- **Concurrencia (RNF08)**: no aplica; el estado se actualiza al refrescar (la lista se revalida al volver a primer plano y cada 30 s con la pantalla abierta).
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Vendedor sin domicilios | `200`, lista vacía y mensaje | Edge 1 |
| Filtro sin resultados | `200`, lista vacía y "sin resultados" | Edge 2–3 |
| Filtro desconocido o inválido | `400` | FR-002, FR-003 |
| Rol que no es punto fijo | `403` | FR-001 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: seis estados, rango de fechas, combinación, vacío, aislamiento entre dos vendedores, rol ambulante y cliente (`403`), filtros inválidos, paginación y orden | FR-001–FR-003, US1-E1–E2, Edge 1–3 |
| Rendimiento | 5 000 domicilios del vendedor: primera página en < 3 s | SC-001 |
| Arquitectura | `domicilios` lee la tarifa del pedido solo por el puerto `in` de `pedidos` | RNF13 |
| Frontend | Jest: filtros, estado vacío, caché sin red, navegación a RF-26 | US1 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-15-listado-de-domicilios-del-vendedor/
├── RF-15-listado-de-domicilios-del-vendedor.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── application/
│   ├── ports/in/                 # ListarDomiciliosVendedorPort
│   ├── ports/out/                # DomicilioRepositoryPort (consulta por vendedor con filtros), PedidoParaDomicilioPort (tarifa en lote)
│   └── usecase/                  # ListarDomiciliosVendedorUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (GET /domicilios), DTO de fila
    └── out/persistence/          # consulta por vendedor_id con filtros
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/app/(vendedor)/domicilios/index.tsx           # ruta Expo Router: mis domicilios
frontend/src/components/domicilios/ItemDomicilioVendedor.tsx
frontend/src/components/domicilios/FiltrosDomicilios.tsx   # estado y fechas
frontend/src/services/domicilios.ts                        # listarDomiciliosVendedor() con caché local
frontend/src/database/schema/domicilios_vendedor_cache.ts
frontend/__tests__/domicilios/listado-vendedor.test.tsx
```

**Structure Decision**: módulo `domicilios`. El domicilio guarda `vendedor_id` (lo fija RF-14 al publicar), así que la consulta es un filtro directo sin leer tablas de `pedidos`; la tarifa de cada fila se pide a `pedidos` en lote. El controlador solo llama al puerto `in`.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `domicilios` (RF-14) y su regla ArchUnit

## Phase 2: Foundational

- [ ] T002 Confirmar `vendedor_id` y el índice `(vendedor_id, created_at)` en V10 (RF-14 T002); si faltan, agregarlos en la siguiente migración libre de `backend/src/main/resources/db/migration/`
- [ ] T003 Consulta por `vendedor_id` con filtros en `DomicilioRepositoryPort`
- [ ] T004 Método en lote de tarifa en `PedidoParaDomicilioPort` (si RF-16 no lo trae ya)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Hacer seguimiento a los envíos propios (Priority: P2)

**Goal**: el vendedor de punto fijo ve y filtra sus domicilios.
**Independent Test**: un vendedor con domicilios en varios estados los lista y filtra; otro vendedor no ve los suyos.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /domicilios` sin filtros (campos, orden, tarifa) en `backend/src/test/java/co/edu/napa/domicilios/ListarDomiciliosVendedorIT.java`
- [ ] T011 [P] [US1] Test de aislamiento entre dos vendedores y de rol ambulante, cliente y domiciliario (`403`) en `.../domicilios/ListarDomiciliosVendedorAutorizacionIT.java`
- [ ] T012 [P] [US1] Test de filtros por cada uno de los seis estados, por rango de fechas y combinados (con y sin resultados) en `.../domicilios/ListarDomiciliosVendedorFiltrosIT.java`
- [ ] T013 [P] [US1] Test de que ningún domicilio que cumple el filtro se omite con paginación y orden estable en `.../domicilios/ListarDomiciliosVendedorPaginacionIT.java`
- [ ] T014 [P] [US1] Test de rendimiento con 5 000 domicilios (< 3 s) en `.../domicilios/ListarDomiciliosVendedorRendimientoIT.java`
- [ ] T015 [P] [US1] Test de vendedor sin domicilios y de filtros inválidos (`400`) en `.../domicilios/ListarDomiciliosVendedorVacioIT.java`
- [ ] T030 [P] [US1] Test de la pantalla (filtros, vacío, sin resultados, caché sin red, navegación) en `frontend/__tests__/domicilios/listado-vendedor.test.tsx`

### Implementation

- [ ] T020 [US1] `ListarDomiciliosVendedorUseCase` y `ListarDomiciliosVendedorPort` en `domicilios/application/`
- [ ] T021 [US1] `GET /domicilios` y su DTO en `DomicilioController`, declarado sin chocar con `/disponibles` y `/{id}`
- [ ] T022 [US1] Consulta JPA con filtros, orden por `createdAt` desc y `id`
- [ ] T031 [US1] `ItemDomicilioVendedor` y `FiltrosDomicilios` en `frontend/src/components/domicilios/`
- [ ] T032 [US1] `listarDomiciliosVendedor()` con caché SQLite en `frontend/src/services/domicilios.ts`
- [ ] T033 [US1] Pantalla `frontend/src/app/(vendedor)/domicilios/index.tsx` con revalidación cada 30 s en primer plano y navegación a `(vendedor)/domicilios/[id]` (RF-26)

**Checkpoint**: el vendedor hace seguimiento de sus domicilios.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` con datos del piloto
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-14, RF-26 (pantalla de destino) y RF-10; habilita el acceso a RF-23 y RF-43 desde la lista.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| El domicilio guarda `vendedor_id` (decidido en la ola 7a; spec-general §3.2 decía "sin vendedor_id" y debe aclararse que es una copia física; el ER no cambia) | Modelo | `vendedor_id` fijado por RF-14 al publicar | Equipo |
| La fila no muestra cliente ni domiciliario | Privacidad | Solo `pedidoId`, estado, tarifa y fechas; el detalle y el seguimiento están en RF-26 | Equipo |
| Rango de fechas sobre `created_at` del domicilio (no sobre la reserva del pedido) | Filtro | Fecha de creación | Equipo |
| El vendedor ambulante no ve esta pantalla | Rol | `403` (solo punto fijo, como dice la spec) | Equipo |
| El administrador consulta todos los domicilios en RF-12 (P3) | Alcance | Fuera de esta ola | Equipo |
| spec-general §2.3 ya lista `estado`, `desde` y `hasta` para esta ruta (el administrador añade `vendedorId` y `domiciliarioId` en RF-12) | Contrato | Sin cambios | Equipo |

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
# Implementation Plan: Listado de productos de un emprendimiento

**Date**: 2026-10-26
**Spec**: [RF-33-listado-de-productos-de-un-emprendimiento.md](./RF-33-listado-de-productos-de-un-emprendimiento.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-33
**Historias de usuario**: HU-58
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-54, RF-55, RF-28, RF-32, RF-10
**Habilita a**: RF-45 (pantalla de armar el pedido)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El cliente abre el catálogo de un emprendimiento con `GET /emprendimientos/{id}/productos` y ve, paginados, sus productos `DISPONIBLE` y `AGOTADO` con nombre, descripción, unidad, precio y cantidad disponible. Los agotados llevan la marca `soloReserva: true` y la app los muestra con la etiqueta "solo reserva" (solo pueden pedirse como reserva, RF-45). Los productos `ELIMINADO` no aparecen. Si el emprendimiento no está `ACTIVO` (inactivo, eliminado o inexistente) responde `404`. La pantalla es el catálogo desde el que el cliente arma su pedido (RF-45); guarda la última lista en SQLite para verla sin conexión y se actualiza si un producto se agota mientras se mira.

## Estado actual del código

No existe aún la consulta del cliente. El módulo `productos`, la tabla `producto` y el listado del vendedor (`GET /productos`) vienen de RF-54 y RF-55; el detalle del emprendimiento y su estado, de RF-28 y RF-32. El plan de RF-45 ya depende de este catálogo para su pantalla de armar el pedido.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Consulta el catálogo de un emprendimiento |
| Vendedor | No interviene (usa RF-55) |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Catálogo en < 3 s (SC-001): consulta paginada por `emprendimiento_id` y estado con índice `(emprendimiento_id, estado, nombre)`.
- 100 % de los agotados con la etiqueta (SC-002): el servidor calcula `soloReserva` desde el estado; la app no lo deduce de la cantidad.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El cliente consulta el catálogo de un emprendimiento | `GET /emprendimientos/{id}/productos` para el rol CLIENTE | T010, T020, T021 |
| FR-002 | Mostrar también los agotados con la etiqueta "solo reserva" | Incluye `AGOTADO` con `soloReserva: true`; excluye `ELIMINADO` | T011, T013 |
| FR-003 | Mostrar precio y cantidad disponible | DTO con `precio`, `unidad` y `cantidadDisponible` | T010 |
| SC-001 | < 3 s | Índice y paginación | T012 |
| SC-002 | 100 % de agotados con etiqueta | `soloReserva` calculado en el servidor y componente de la app | T011, T030 |
| US1-E1 | Catálogo con disponibles y agotados | `200` con ambos grupos y la marca | T010, T011 |
| US1-E2 | Emprendimiento sin productos | `200` con lista vacía y mensaje en la app | T014, T030 |
| Edge 1 | Un producto se agota mientras se mira | Sigue visible; la app refresca al volver al primer plano y marca "solo reserva" | T031 |
| Edge 2 | Un producto del pedido se agota antes de confirmar | Lo resuelve RF-45 (`PRODUCT_UNAVAILABLE`); aquí solo se refresca el catálogo | T031 |
| Edge 3 | Orden del catálogo | Disponibles primero, agotados al final, y `nombre,asc` dentro de cada grupo (Open Questions) | T015 |
| Edge 4 | Emprendimiento eliminado mientras se consulta | `404` | T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo CLIENTE |
| RNF07 Rendimiento ≤ 3 s | ☑ | Índice y paginación |
| RNF23 Consistencia | ☑ | `soloReserva` derivado del estado guardado |
| RNF24 Errores claros | ☑ | `404`, `400` con el filtro |
| RNF28 Conectividad | ☑ | Caché local de lectura |
| RNF31 Gama baja | ☑ | Lista paginada y virtualizada |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Producto | Lee | Estados `DISPONIBLE` y `AGOTADO`; nunca `ELIMINADO` |
| Emprendimiento | Lee (puerto) | Debe estar `ACTIVO` |

## State Transitions

Esta feature es de solo lectura: no cambia estados.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /emprendimientos/{id}/productos` | CLIENTE | Cualquier emprendimiento `ACTIVO` (catálogo público para el cliente) |

## API Contracts

### GET /api/v1/emprendimientos/{id}/productos

- **Rol**: cliente · **Path params**: `id` (UUID del emprendimiento)
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| page, size, sort | int, int, string | ☐ | `0`, `20`, `nombre,asc` | `sort` admite `nombre` y `precio` (spec-general §2.3); por defecto `nombre,asc` |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body:

```json
{
  "content": [
    { "id": "…", "nombre": "Mango", "descripcion": "…", "unidad": "KG", "precio": 4500, "cantidadDisponible": 12.5, "estado": "DISPONIBLE", "soloReserva": false },
    { "id": "…", "nombre": "Papaya", "descripcion": "…", "unidad": "UNIDAD", "precio": 3000, "cantidadDisponible": 0, "estado": "AGOTADO", "soloReserva": true }
  ],
  "page": 0, "size": 20, "totalElements": 2
}
```

Los `DISPONIBLE` van primero y los `AGOTADO` después; dentro de cada grupo rige `sort`.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `sort` o filtro desconocido, `size` mayor que 100 | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de cliente | — |
| 404 | `NOT_FOUND` | El emprendimiento no existe, está inactivo o eliminado | "Este emprendimiento ya no está disponible" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda en SQLite la primera página de cada catálogo consultado y la muestra con "datos guardados" si no hay red. Al volver a primer plano o recuperar la conexión, se refresca; la cantidad y la marca "solo reserva" se actualizan.
- **Concurrencia (RNF08)**: no aplica a la lectura; si un producto se agota después de listarlo, RF-45 lo detecta al confirmar.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Emprendimiento sin productos | `200` con lista vacía | US1-E2 |
| Emprendimiento inactivo o eliminado | `404` "ya no está disponible" | Edge 4 |
| Producto agotado | Visible con `soloReserva: true` | FR-002 |
| Sin red | Caché local | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: disponibles y agotados con la marca, `ELIMINADO` oculto, lista vacía, emprendimiento inactivo, eliminado e inexistente, rol distinto de cliente, orden y paginación | FR-001–FR-003, US1-E1–E2, Edge 3–4 |
| Rendimiento | 5 000 productos en un emprendimiento: primera página en < 3 s | SC-001 |
| Arquitectura | `productos` lee el estado del emprendimiento solo por el puerto `in` de `emprendimientos` | RNF13 |
| Frontend | Jest: etiqueta "solo reserva", lista vacía, caché sin red, refresco al volver a primer plano | SC-002, Edge 1 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-33-listado-de-productos-de-un-emprendimiento/
├── RF-33-listado-de-productos-de-un-emprendimiento.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/productos/
├── application/
│   ├── ports/in/                 # ListarCatalogoEmprendimientoPort
│   ├── ports/out/                # ProductoRepositoryPort (listado por emprendimiento), EmprendimientoActivoPort
│   └── usecase/                  # ListarCatalogoEmprendimientoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # ProductoController (GET /emprendimientos/{id}/productos), DTO de catálogo
    └── out/persistence/          # consulta paginada con orden por estado y luego por el campo elegido

backend/src/main/java/co/edu/napa/emprendimientos/application/ports/in/   # ConsultarEmprendimientoActivoPort (si RF-32 no lo expone ya)
backend/src/test/java/co/edu/napa/productos/

frontend/src/app/(cliente)/emprendimientos/[id]/productos.tsx     # ruta Expo Router: catálogo
frontend/src/components/productos/TarjetaProductoCatalogo.tsx     # nombre, precio, cantidad y etiqueta "solo reserva"
frontend/src/components/productos/EtiquetaSoloReserva.tsx
frontend/src/services/productos.ts                                # listarCatalogo() con caché local
frontend/src/database/schema/catalogo_cache.ts
frontend/__tests__/productos/catalogo.test.tsx
```

**Structure Decision**: módulo `productos` (dueño de `Producto`), con una ruta anidada bajo `emprendimientos` como define spec-general §2.3. El estado del emprendimiento se pregunta por un puerto `in` de `emprendimientos` para no leer su tabla. El orden "disponibles primero" se resuelve en la consulta, no en la app, para que la paginación sea coherente. La pantalla es la misma desde la que RF-45 arma el pedido (`armar.tsx`), que reutiliza `TarjetaProductoCatalogo`.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `productos` y su regla ArchUnit

## Phase 2: Foundational

- [ ] T002 Índice `(emprendimiento_id, estado, nombre)` sobre `producto` en la siguiente migración libre de `backend/src/main/resources/db/migration/` (si RF-54 o RF-55 no lo crearon)
- [ ] T003 `EmprendimientoActivoPort` (puerto `out`) y su adaptador sobre un puerto `in` de `emprendimientos`
- [ ] T004 Método de listado por emprendimiento en `ProductoRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Ver el catálogo de un emprendimiento (Priority: P2)

**Goal**: el cliente ve qué puede pedir, con precio y cantidad.
**Independent Test**: abrir el catálogo de un emprendimiento con productos disponibles y agotados.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–4.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /emprendimientos/{id}/productos` (campos, precio, unidad y cantidad) en `backend/src/test/java/co/edu/napa/productos/CatalogoIT.java`
- [ ] T011 [P] [US1] Test de integración: los `AGOTADO` salen con `soloReserva: true` y los `DISPONIBLE` con `false`; los `ELIMINADO` no salen, en `.../productos/CatalogoAgotadosIT.java`
- [ ] T012 [P] [US1] Test de rendimiento con 5 000 productos (< 3 s) en `.../productos/CatalogoRendimientoIT.java`
- [ ] T013 [P] [US1] Test de integración: un producto que se agota entre dos consultas pasa a `soloReserva` sin salir del catálogo en `.../productos/CatalogoCambioEstadoIT.java`
- [ ] T014 [P] [US1] Test de emprendimiento sin productos (`200`, lista vacía) en `.../productos/CatalogoVacioIT.java`
- [ ] T015 [P] [US1] Test de orden y paginación: disponibles primero, `nombre` o `precio` dentro de cada grupo, `sort` inválido (`400`), `size` > 100 en `.../productos/CatalogoOrdenIT.java`
- [ ] T016 [P] [US1] Test de emprendimiento inactivo, eliminado e inexistente (`404`), rol distinto de cliente (`403`) y sin sesión (`401`) en `.../productos/CatalogoAutorizacionIT.java`
- [ ] T030 [P] [US1] Test de `TarjetaProductoCatalogo`, de la etiqueta "solo reserva" y de la pantalla (lista vacía, caché sin red) en `frontend/__tests__/productos/catalogo.test.tsx`
- [ ] T031 [P] [US1] Test del refresco del catálogo al volver a primer plano en `frontend/__tests__/productos/catalogo-refresco.test.tsx`

### Implementation

- [ ] T020 [US1] `ListarCatalogoEmprendimientoUseCase` y `ListarCatalogoEmprendimientoPort`: valida el emprendimiento y consulta
- [ ] T021 [US1] `GET /emprendimientos/{id}/productos` y su DTO en `ProductoController`
- [ ] T022 [US1] Consulta JPA con orden por estado y luego por `nombre` o `precio`, y `soloReserva` calculado
- [ ] T032 [US1] `EtiquetaSoloReserva` y `TarjetaProductoCatalogo` en `frontend/src/components/productos/`
- [ ] T033 [US1] `listarCatalogo()` con caché SQLite en `frontend/src/services/productos.ts`
- [ ] T034 [US1] Pantalla `frontend/src/app/(cliente)/emprendimientos/[id]/productos.tsx`, enlazada desde el detalle (RF-32) y como punto de partida de RF-45

**Checkpoint**: el catálogo funciona de extremo a extremo.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` de la consulta con datos de piloto
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-54, RF-55, RF-28, RF-32 y RF-10; habilita la pantalla de armar el pedido de RF-45.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 3: la spec pregunta cómo se ordena y si los agotados van al final | UX | Disponibles primero y agotados después; dentro de cada grupo `nombre,asc` o `precio` | Equipo |
| El catálogo del vendedor (RF-55) mantiene el orden elegido, sin agrupar; aquí se agrupa por estado | Coherencia | Así, porque el cliente decide qué comprar ahora | Equipo |
| Emprendimiento fuera de su horario (cerrado): la spec no dice si se oculta el catálogo | Regla | Se muestra igual; RF-31 ya etiqueta "Cerrado" | Equipo |
| Sin buscador ni filtro por nombre o estado | Alcance | No está en la spec | Equipo |
| El catálogo del ambulante muestra la cantidad que declaró, que puede cambiar mientras vende | Datos | Se muestra la guardada; se actualiza al refrescar | Equipo |
| spec-general §2.3 lista el filtro de esta ruta como solo `sort` | Contrato | `sort` por `nombre` o `precio`; ningún otro filtro | Equipo |

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
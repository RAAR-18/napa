# Implementation Plan: Edición de producto

**Date**: 2026-10-27
**Spec**: [RF-56-edicion-de-producto.md](./RF-56-edicion-de-producto.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-56
**Historias de usuario**: HU-87
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-54, RF-57, RF-10
**Habilita a**: RF-33 y RF-55 (muestran lo editado)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor corrige un producto publicado con `PATCH /productos/{id}`: nombre, precio y descripción (uno o varios campos). Los valores se validan antes de aplicarlos y, si alguno es inválido, no se guarda nada y se conserva el valor anterior. El cambio se refleja de inmediato en el catálogo del cliente (RF-33) y en el listado del vendedor (RF-55). Un pedido ya creado no cambia: cada ítem guardó el precio con el que se compró (`ItemPedido.precio_unitario`), así que un ajuste de precio solo afecta a los pedidos nuevos. La unidad, el inventario y el estado no se editan aquí (el inventario es de RF-57).

## Estado actual del código

El módulo `productos`, la tabla `producto` y el registro vienen de RF-54; el catálogo del cliente (RF-33) y el listado del vendedor (RF-55) leen la misma fila. RF-57 actualiza el inventario con un `UPDATE` condicionado y define las respuestas para producto ajeno (`403`) y `ELIMINADO` (`409 INVALID_STATE`); este plan usa las mismas.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Ve el producto corregido en el catálogo; no interviene |
| Vendedor | Edita sus productos |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Cambios visibles en < 5 s (SC-001): sin caché entre la escritura y las lecturas de RF-33 y RF-55.
- 100 % de ediciones inválidas rechazadas antes de guardar (SC-002): validación en el DTO y en el dominio `Producto`, y se aplica todo o nada en una transacción.
- Pedidos existentes intactos: el precio vive copiado en `ItemPedido`; este plan no toca esa tabla.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Editar nombre, precio y descripción | `PATCH /productos/{id}` (parcial) | T010, T020, T021 |
| FR-002 | Validar los valores antes de aplicarlos | Bean Validation y reglas de `Producto`; todo o nada | T011, T012, T022 |
| FR-003 | Reflejar los cambios en el catálogo de inmediato | RF-33 y RF-55 leen la misma fila | T013 |
| SC-001 | Cambios visibles en < 5 s | Sin caché intermedia | T013 |
| SC-002 | 100 % de ediciones inválidas rechazadas | Pruebas parametrizadas | T011 |
| US1-E1 | Edición exitosa | `200` con el producto actualizado | T010 |
| US1-E2 | Precio negativo | `400 VALIDATION_ERROR` y el valor anterior intacto | T011 |
| Edge 1 | Producto con pedidos pendientes al precio anterior | Los ítems conservan su precio; solo cambian los pedidos nuevos | T014 |
| Edge 2 | Edición simultánea desde dos sesiones | Versión optimista: el choque real da `409 CONFLICT` y la app reintenta | T015 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el vendedor dueño del producto (`403` en otro caso, como RF-57) |
| RNF07 Rendimiento ≤ 3 s | ☑ | Una fila |
| RNF08 Concurrencia | ☑ | `@Version` en `producto` |
| RNF23 Consistencia | ☑ | Se aplican todos los campos o ninguno; el producto `ELIMINADO` no se edita |
| RNF24 Errores claros | ☑ | `details.campo` en cada `400` |
| RNF28 Conectividad | ☑ | Las ediciones se encolan en la outbox y se envían al volver la red |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Producto | Lee / escribe (`nombre`, `precio`, `descripcion`) | `nombre` único por emprendimiento entre los no `ELIMINADO`; `precio` > 0; `unidad`, `cantidad_disponible` y `estado` no se tocan |
| ItemPedido | No se toca | Conserva el `precio_unitario` con el que se compró |

## State Transitions

No cambia estados: `DISPONIBLE` y `AGOTADO` se conservan. Un producto `ELIMINADO` no se edita y responde `409 INVALID_STATE` (igual que RF-57).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `PATCH /productos/{id}` | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | El producto pertenece al emprendimiento del vendedor; el de otro vendedor responde `403` (decisión de RF-57) |

## API Contracts

### PATCH /api/v1/productos/{id}

- **Rol**: vendedor · **Path params**: `id` (UUID del producto) · **Query params**: ninguno
- **Body** (parcial; al menos un campo): `{ "nombre": "Mango de azúcar", "precio": 4000, "descripcion": "Del día" }`

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Content-Type | ✅ | `application/json` |

`PATCH` no exige `Idempotency-Key` (spec-general). Repetir el mismo cuerpo deja el mismo resultado.

- **Respuestas exitosas**: `200` con `X-Request-Id` y body `{ "id", "nombre", "precio", "unidad", "descripcion", "cantidadDisponible", "estado" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo vacío; nombre vacío o de más de 100 caracteres; precio ≤ 0 o no entero; descripción de más de 500 caracteres; campo no editable (`unidad`, `cantidadDisponible`, `estado`) | `details.campo` |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Producto de otro vendedor o rol distinto de vendedor | — |
| 404 | `NOT_FOUND` | El producto no existe | "El producto no está disponible" |
| 409 | `CONFLICT` | Otro producto del emprendimiento ya tiene ese nombre, o dos escrituras simultáneas | "Ya tienes un producto con ese nombre" |
| 409 | `INVALID_STATE` | El producto está `ELIMINADO` | `details.estadoActual` |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Editar un producto no tiene reacciones |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la edición se guarda en la outbox local y se envía al recuperar la red; mientras tanto la app muestra el valor nuevo como "pendiente de enviar". Si al enviarla llega `409` o `400`, la app avisa y restaura el valor del servidor.
- **Concurrencia (RNF08)**: `@Version` en `producto`. Un choque entre dos `PATCH`, o entre un `PATCH` y un ajuste de inventario de RF-57, devuelve `409 CONFLICT` y la app reintenta una vez con los datos frescos.
- **Idempotencia (RNF27)**: `PATCH` es idempotente por naturaleza.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Precio negativo o cero | `400` y se conserva el valor anterior | US1-E2 |
| Nombre duplicado | `409 CONFLICT` | Modelo de datos |
| Producto eliminado | `409 INVALID_STATE` | RF-57 |
| Producto de otro vendedor | `403` | RF-57 |
| Choque de escrituras | `409 CONFLICT`; la app reintenta | Edge 2 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitario | Reglas de `Producto.editar` | FR-002 |
| Integración | Endpoint con Testcontainers: edición de un campo y de varios, precio y nombre inválidos (todo o nada), nombre duplicado, descripción, campos no editables, `ELIMINADO`, producto ajeno, inexistente, rol distinto, sin sesión | FR-001, FR-002, US1-E1–E2 |
| Integración | El catálogo de RF-33 y el listado de RF-55 reflejan el cambio de inmediato | FR-003, SC-001 |
| Integración | Un pedido pendiente conserva su precio tras cambiar el del producto | Edge 1 |
| Integración | Dos `PATCH` simultáneos, y un `PATCH` junto con un ajuste de inventario de RF-57 | Edge 2 |
| Frontend | Jest: formulario de edición, valor pendiente de enviar, error del servidor | US1-E2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-56-edicion-de-producto/
├── RF-56-edicion-de-producto.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/productos/
├── domain/model/                 # Producto.editar(nombre, precio, descripcion)
├── application/
│   ├── ports/in/                 # EditarProductoPort
│   ├── ports/out/                # ProductoRepositoryPort
│   └── usecase/                  # EditarProductoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # ProductoController (PATCH /productos/{id}), DTO parcial
    └── out/persistence/          # @Version en ProductoJpaEntity

backend/src/test/java/co/edu/napa/productos/

frontend/src/app/(vendedor)/productos/[id]/editar.tsx    # formulario de edición
frontend/src/components/productos/FormularioProducto.tsx # compartido con RF-54
frontend/src/services/productos.ts                       # editarProducto() con la outbox
frontend/__tests__/productos/editar-producto.test.tsx
```

**Structure Decision**: módulo `productos`, dueño de `Producto`. `PATCH` parcial porque la spec edita campos sueltos y no es una transición de estado. La pertenencia se resuelve por el emprendimiento del vendedor, igual que RF-57. El formulario es el mismo de RF-54.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `productos` y su regla ArchUnit (RF-54)

## Phase 2: Foundational

- [ ] T002 Columna `version` y `@Version` en `ProductoJpaEntity` en la siguiente migración libre de `backend/src/main/resources/db/migration/`; el `UPDATE` de inventario de RF-57 debe incrementarla
- [ ] T003 Método de búsqueda del producto con su emprendimiento y consulta de nombre duplicado en `ProductoRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Corregir o actualizar un producto (Priority: P2)

**Goal**: el vendedor ajusta nombre, precio o descripción y los clientes lo ven de inmediato.
**Independent Test**: cambiar el precio de un producto y verlo en el catálogo del cliente.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `PATCH /productos/{id}` con uno y con varios campos en `backend/src/test/java/co/edu/napa/productos/EditarProductoIT.java`
- [ ] T011 [P] [US1] Test parametrizado de datos inválidos (cuerpo vacío, nombre vacío o largo, precio negativo, cero o decimal, descripción larga, campos no editables) con el valor anterior intacto, en `.../productos/EditarProductoValidacionIT.java`
- [ ] T012 [P] [US1] Test unitario de `Producto.editar` en `.../productos/ProductoEdicionTest.java`
- [ ] T013 [P] [US1] Test de que RF-33 y RF-55 muestran el cambio de inmediato en `.../productos/EditarProductoVisibilidadIT.java`
- [ ] T014 [P] [US1] Test de un pedido pendiente que conserva el precio anterior en `.../productos/EditarProductoPedidosIT.java`
- [ ] T015 [P] [US1] Test de dos `PATCH` simultáneos y de un `PATCH` junto con un ajuste de inventario (`409 CONFLICT`, sin pérdida) en `.../productos/EditarProductoConcurrenciaIT.java`
- [ ] T016 [P] [US1] Test de nombre duplicado (`409`), producto `ELIMINADO` (`409 INVALID_STATE`), ajeno (`403`), inexistente (`404`), rol distinto y sin sesión en `.../productos/EditarProductoAutorizacionIT.java`
- [ ] T030 [P] [US1] Test del formulario de edición y del valor pendiente de enviar en `frontend/__tests__/productos/editar-producto.test.tsx`

### Implementation

- [ ] T020 [US1] `Producto.editar` con sus reglas en `productos/domain/model/Producto.java`
- [ ] T021 [US1] `EditarProductoPort` y `EditarProductoUseCase`: valida pertenencia y estado, aplica todo o nada
- [ ] T022 [US1] `PATCH /productos/{id}` y su DTO parcial en `ProductoController`
- [ ] T023 [US1] Mapeo de `OptimisticLockException` a `409 CONFLICT` y reintento único
- [ ] T031 [US1] `editarProducto()` con la outbox en `frontend/src/services/productos.ts`
- [ ] T032 [US1] Pantalla `productos/[id]/editar.tsx` con `FormularioProducto` compartido con RF-54

**Checkpoint**: el vendedor corrige sus productos.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Confirmar que `UPDATE` de inventario (RF-57) e `INSERT` de pedido (RF-45) no se bloquean con la edición
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-54 (producto) y RF-10; RF-57 debe incrementar `version`; las pantallas de RF-33 y RF-55 muestran lo editado.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: ¿el pedido pendiente conserva el precio anterior? | Regla | Sí: `ItemPedido.precio_unitario` es una copia (spec-general) y el total del pedido está congelado | Equipo |
| La ruta no está en spec-general (`PATCH /productos/{id}`) | Contrato | Se agrega a spec-general | Equipo |
| FR-001 no incluye la unidad: cambiarla con inventario existente cambiaría su significado | Alcance | `unidad` no se edita | Equipo |
| El inventario cambia con RF-57 y el estado `DISPONIBLE`/`AGOTADO` lo deriva ese RF; aquí no se toca | Alcance | Así | Equipo |
| `PATCH` y `UPDATE` de inventario sobre la misma fila | Concurrencia | `version` incrementada por ambos; RF-57 debe sumarla (T002) | Equipo |
| Producto ajeno responde `403` pero el pedido ajeno responde `404` en otros RF | Coherencia | Se mantiene el `403` de RF-57 para productos | Equipo |
| Límites de nombre (100) y descripción (500) | Validación | Los de RF-54 | Equipo |

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
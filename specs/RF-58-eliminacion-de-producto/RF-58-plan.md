# Implementation Plan: Eliminación de producto

**Date**: 2026-10-27
**Spec**: [RF-58-eliminacion-de-producto.md](./RF-58-eliminacion-de-producto.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-58
**Historias de usuario**: HU-89
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-54, RF-45, RF-51, RF-10 
**Habilita a**: RF-33 y RF-55 (dejan de mostrarlo)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor retira un producto que ya no vende con `POST /productos/{id}/eliminar` (cuerpo `{ "confirmar": true }`, `204`). Si el producto está en algún pedido pendiente (pedido en `PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER` o `EN_CAMINO`, reservas incluidas), se rechaza con `409 HAS_PENDING_ORDERS` y la cantidad de pedidos. Si no, pasa a `ELIMINADO` (borrado lógico) y deja de verse de inmediato en el catálogo del cliente (RF-33) y en el listado del vendedor (RF-55). Los pedidos ya terminados siguen mostrando el producto con la marca "producto eliminado" (RF-51) y el nombre queda libre para un producto nuevo.

## Estado actual del código

RF-54 define `producto.estado` con `ELIMINADO`; RF-33 y RF-55 ya lo excluyen y RF-51 ya muestra `productoEliminado: true` en los ítems. Falta la acción y la consulta de pedidos pendientes por producto, que vive en `pedidos`. RF-57 responde `409 INVALID_STATE` sobre un producto `ELIMINADO`.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Deja de ver el producto; no interviene |
| Vendedor | Elimina sus productos |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Producto invisible en < 5 s (SC-001): el estado cambia en una transacción y las lecturas no usan caché.
- 100 % de rechazos con pedidos pendientes (SC-002): la verificación y el cambio ocurren con el producto bloqueado (`FOR UPDATE`), en la misma transacción.
- Sin joins entre módulos: `productos` pregunta a `pedidos` por un puerto `in`.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Eliminar un producto sin pedidos pendientes | `POST /productos/{id}/eliminar` → `ELIMINADO` | T010, T020, T021 |
| FR-002 | Impedir la eliminación con pedidos pendientes | `ConsultarPedidosPendientesDeProductoPort` y `409 HAS_PENDING_ORDERS` | T011, T022 |
| FR-003 | Solicitar confirmación explícita | Cuadro de confirmación en la app y `confirmar: true` exigido por el servidor | T012, T031 |
| FR-004 | Retirar el producto del catálogo de inmediato | Estado `ELIMINADO`, que RF-33 y RF-55 ya excluyen | T013 |
| SC-001 | Invisible en < 5 s | Sin caché | T013 |
| SC-002 | 100 % de rechazos con pendientes | Pruebas por cada estado pendiente | T011 |
| US1-E1 | Eliminación exitosa | `204` | T010 |
| US1-E2 | Producto con pedido pendiente | `409` con la cantidad | T011 |
| Edge 1 | Historial de pedidos terminados con el producto | Se conserva; RF-51 marca `productoEliminado` | T014 |
| Edge 2 | Cancelar antes de confirmar | Es solo de interfaz: el cuadro de confirmación; sin `confirmar: true` no se elimina | T012, T031 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el vendedor dueño (`403` en otro caso, como RF-57) |
| RNF07 Rendimiento ≤ 3 s | ☑ | Una consulta indexada por producto |
| RNF08 Concurrencia | ☑ | `SELECT … FOR UPDATE` del producto; orden de bloqueo producto → pedido |
| RNF13 Arquitectura | ☑ | Solo el puerto `in` de `pedidos` |
| RNF15 Fiabilidad | ☑ | Verificación y cambio en una transacción |
| RNF23 Consistencia | ☑ | `DISPONIBLE`/`AGOTADO` → `ELIMINADO`; repetirlo da `INVALID_STATE` |
| RNF27 Idempotencia | ☑ | `Idempotency-Key` obligatoria |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Producto | Lee / escribe (`estado`) | Pasa a `ELIMINADO`; la fila no se borra; el nombre queda libre (único entre los no `ELIMINADO`) |
| Pedido / ItemPedido | Lee (puerto) | Pendiente: `PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER`, `EN_CAMINO` |
| Calificacion | No se toca | Las del producto se conservan |

## State Transitions

| Entidad | Desde | Hacia | Acción | Condición |
|---|---|---|---|---|
| Producto | `DISPONIBLE` o `AGOTADO` | `ELIMINADO` | `POST /productos/{id}/eliminar` | Sin pedidos pendientes |

`ELIMINADO` es terminal. Eliminar un producto ya `ELIMINADO` responde `409 INVALID_STATE`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /productos/{id}/eliminar` | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | El producto es del emprendimiento del vendedor; otro vendedor recibe `403` |

## API Contracts

### POST /api/v1/productos/{id}/eliminar

- **Rol**: vendedor · **Path params**: `id` (UUID del producto) · **Query params**: ninguno
- **Body**: `{ "confirmar": true }`

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `204` sin cuerpo y con `X-Request-Id`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `confirmar` o no es `true` | "Confirma la eliminación" |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Producto de otro vendedor o rol distinto | — |
| 404 | `NOT_FOUND` | El producto no existe | "El producto no está disponible" |
| 409 | `HAS_PENDING_ORDERS` | El producto está en pedidos pendientes | "Este producto tiene pedidos pendientes"; `details.pedidosPendientes` |
| 409 | `INVALID_STATE` | El producto ya está `ELIMINADO` | `details.estadoActual` |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Eliminar un producto no tiene reacciones |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: es una acción destructiva y no se encola: la app exige conexión y muestra el error si falla. Al recibir `204` quita el producto de la lista local.
- **Concurrencia (RNF08)**: el caso de uso bloquea el producto con `FOR UPDATE` antes de contar los pedidos pendientes. Para que un pedido nuevo no se cuele, RF-45 debe leer los productos con bloqueo compartido (`FOR SHARE`) al crear el pedido (Open Questions); el orden de bloqueo es producto → pedido.
- **Idempotencia (RNF27)**: la misma clave devuelve el mismo `204`; con otra clave y el producto ya eliminado llega `409 INVALID_STATE`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Producto con pedidos pendientes | `409 HAS_PENDING_ORDERS` con la cantidad | US1-E2 |
| Sin `confirmar: true` | `400` | FR-003 |
| Producto ya eliminado | `409 INVALID_STATE` | RF-57 |
| Producto de otro vendedor | `403` | RF-57 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Eliminación sin pedidos, con pedido en cada estado pendiente (incluida una reserva), con pedidos terminados o rechazados (se permite), repetición, sin `confirmar` | FR-001–FR-003, US1-E1–E2 |
| Integración | Después de eliminar: RF-33 y RF-55 no lo muestran, RF-51 marca `productoEliminado`, el nombre se puede reutilizar | FR-004, SC-001, Edge 1 |
| Integración | Eliminación y creación de pedido simultáneas (no queda un pedido pendiente con producto `ELIMINADO`) | SC-002 |
| Arquitectura | `productos` usa solo el puerto `in` de `pedidos` | RNF13 |
| Frontend | Jest: cuadro de confirmación, aviso de pedidos pendientes, quita el producto de la lista | FR-003, US1-E2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-58-eliminacion-de-producto/
├── RF-58-eliminacion-de-producto.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/productos/
├── application/
│   ├── ports/in/                 # EliminarProductoPort
│   ├── ports/out/                # ProductoRepositoryPort (con bloqueo), PedidosPendientesDeProductoPort
│   └── usecase/                  # EliminarProductoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # ProductoController (POST /productos/{id}/eliminar)
    └── out/                      # adaptador de PedidosPendientesDeProductoPort sobre el puerto in de pedidos

backend/src/main/java/co/edu/napa/pedidos/application/ports/in/   # ConsultarPedidosPendientesDeProductoPort
backend/src/test/java/co/edu/napa/productos/

frontend/src/components/productos/ConfirmarEliminarProducto.tsx   # cuadro de confirmación
frontend/src/services/productos.ts                                # eliminarProducto()
frontend/src/app/(vendedor)/productos/index.tsx                   # (RF-55) acción "Eliminar" por producto
frontend/__tests__/productos/eliminar-producto.test.tsx
```

**Structure Decision**: módulo `productos`, dueño de `Producto`, con la acción como `POST …/eliminar` (spec-general). La pregunta por pedidos pendientes va por un puerto `in` de `pedidos`. El borrado es lógico (`ELIMINADO`) como en el resto del proyecto, lo que conserva el historial de pedidos y calificaciones.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `productos` y su regla ArchUnit

## Phase 2: Foundational

- [ ] T002 `ConsultarPedidosPendientesDeProductoPort` en `pedidos` (cuenta pedidos en `PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER` o `EN_CAMINO` que contienen el producto) con índice sobre `item_pedido(producto_id)` en la siguiente migración libre de `backend/src/main/resources/db/migration/` (si RF-45 no lo creó)
- [ ] T003 Consulta con bloqueo `FOR UPDATE` del producto en `ProductoRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Retirar un producto del catálogo (Priority: P2)

**Goal**: el vendedor quita un producto sin dejar pedidos colgados.
**Independent Test**: eliminar un producto sin pedidos y comprobar que desaparece del catálogo; intentarlo con un pedido pendiente.
**Cubre**: FR-001–FR-004, SC-001, SC-002, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /productos/{id}/eliminar` sin pedidos (`204`, estado `ELIMINADO`, repetición con la misma clave) en `backend/src/test/java/co/edu/napa/productos/EliminarProductoIT.java`
- [ ] T011 [P] [US1] Test de rechazo con pedido en cada estado pendiente (incluida una reserva) y de que los pedidos terminados o rechazados no bloquean, en `.../productos/EliminarProductoPendientesIT.java`
- [ ] T012 [P] [US1] Test de que sin `confirmar: true` no se elimina (`400`) en `.../productos/EliminarProductoConfirmacionIT.java`
- [ ] T013 [P] [US1] Test de que RF-33 y RF-55 dejan de mostrarlo de inmediato (< 5 s) en `.../productos/EliminarProductoVisibilidadIT.java`
- [ ] T014 [P] [US1] Test de que los pedidos terminados siguen mostrando el producto marcado y de que el nombre se puede reutilizar, en `.../productos/EliminarProductoHistorialIT.java`
- [ ] T015 [P] [US1] Test de eliminación y creación de pedido simultáneas en `.../productos/EliminarProductoConcurrenciaIT.java`
- [ ] T016 [P] [US1] Test de producto ya `ELIMINADO` (`409 INVALID_STATE`), ajeno (`403`), inexistente (`404`), rol distinto y sin sesión en `.../productos/EliminarProductoAutorizacionIT.java`
- [ ] T030 [P] [US1] Test de `ConfirmarEliminarProducto` (confirmar, cancelar, aviso de pendientes) en `frontend/__tests__/productos/eliminar-producto.test.tsx`

### Implementation

- [ ] T020 [US1] `EliminarProductoPort` y `EliminarProductoUseCase`: bloquea el producto, consulta pendientes y cambia el estado en una transacción
- [ ] T021 [US1] `POST /productos/{id}/eliminar` y su DTO en `ProductoController`
- [ ] T022 [US1] Adaptador de `PedidosPendientesDeProductoPort` y error `HAS_PENDING_ORDERS` con `details.pedidosPendientes`
- [ ] T031 [US1] `ConfirmarEliminarProducto` y `eliminarProducto()` en `frontend/src/`
- [ ] T032 [US1] Acción "Eliminar" en el listado de productos del vendedor (RF-55) con el aviso de pendientes

**Checkpoint**: el producto se retira o se explica por qué no.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` de la consulta de pendientes por producto
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-54, RF-45 (pedidos con ítems), RF-33 y RF-55 (que ya excluyen `ELIMINADO`), RF-51 (marca del producto eliminado) y RF-10.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| RF-45 debe leer los productos con bloqueo compartido (`FOR SHARE`) al crear el pedido; hoy no lo dice | Concurrencia | Pedido a RF-45 como tarea nueva; si no, queda una ventana mínima | Equipo |
| `confirmar: true` en el cuerpo (la spec pide confirmación explícita pero solo en la interfaz) | Contrato | Se exige también en el servidor | Equipo |
| Código nuevo `HAS_PENDING_ORDERS` (409) | Contrato | Se agrega a spec-general | Equipo |
| La ruta no está en spec-general (`POST /productos/{id}/eliminar`) | Contrato | Se agrega a spec-general | Equipo |
| ¿Un pedido `RECHAZADO` o `CANCELADO` cuenta como pendiente? | Regla | No: son terminales | Equipo |
| ¿Se puede restaurar un producto eliminado? | Alcance | No: el vendedor crea uno nuevo con el nombre liberado | Equipo |
| Producto ajeno responde `403` y no `404` | Coherencia | Igual que RF-57 | Equipo |

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
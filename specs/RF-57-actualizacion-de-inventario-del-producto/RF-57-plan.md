# Implementation Plan: Actualización de inventario del producto

**Date**: 2026-10-21
**Spec**: [RF-57-actualizacion-de-inventario-del-producto.md](./RF-57-actualizacion-de-inventario-del-producto.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-57
**Historias de usuario**: HU-88
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-54 (tabla `producto`), RF-55 (pantalla de origen)
**Habilita a**: RF-45 (validar productos agotados), RF-53 (descuento al aceptar), RF-33

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor fija la cantidad disponible de un producto (decimal ≥ 0). Si queda en 0 el producto pasa a `AGOTADO` automáticamente; si vuelve a ser mayor que 0 pasa a `DISPONIBLE`. El módulo `productos` también expone, para los pedidos, dos puertos `in`: uno para descontar inventario de forma atómica cuando el vendedor acepta un pedido inmediato (FR-006) y otro para consultar disponibilidad al crear un pedido (FR-003). Las reservas nunca descuentan inventario.

## Estado actual del código

No existe código de productos. La tabla `producto` (con `cantidad_disponible numeric(10,2)` y CHECK `>= 0`) la crea RF-54.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Indirecto: no puede hacer pedidos inmediatos de un producto agotado (RF-45) |
| Vendedor (ambulante / punto fijo) | Actualiza la cantidad; al aceptar un pedido se descuenta su inventario (RF-53) |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- `AGOTADO` en < 2 s (SC-001): el estado se calcula en la misma transacción de la actualización.
- 0 % de sobreventa (SC-002): descuento con `UPDATE ... WHERE cantidad_disponible >= :x` atómico.
- Actualización por el vendedor en < 15 s (SC-003): acción rápida desde el listado, con el outbox para no esperar a la red.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Actualizar manualmente la cantidad | `POST /productos/{id}/inventario` (fija la cantidad); `ActualizarInventarioUseCase` | T010, T013, T014 |
| FR-002 | `AGOTADO` automático al llegar a cero | Dominio `Producto`: cantidad 0 → `AGOTADO`; > 0 → `DISPONIBLE` | T011, T015 |
| FR-003 | Impedir pedidos inmediatos sobre agotados; solo reserva | Puerto `in` `ConsultarDisponibilidadPort` que usa RF-45 (`PRODUCT_UNAVAILABLE`); se prueba aquí el contrato del puerto | T012, T016 |
| FR-004 | Rechazar cantidades negativas | `400 VALIDATION_ERROR` + CHECK en BD | T011 |
| FR-005 | Controlar actualizaciones concurrentes | Bloqueo de fila (`SELECT ... FOR UPDATE`) en la actualización manual y `UPDATE` condicional en el descuento | T017, T018 |
| FR-006 | Descontar al aceptar un pedido inmediato; rechazar si no alcanza; las reservas no descuentan | Puerto `in` `DescontarInventarioPort` (lo invoca RF-53 en su misma transacción); `OUT_OF_STOCK` si no alcanza | T019, T020 |
| FR-007 | Cantidad decimal con unidad | `numeric(10,2)`; unidad del producto; validación de decimales según unidad (Open Questions) | T011 |
| SC-001 | < 2 s | Transacción única | T030 |
| SC-002 | 0 % de sobreventa | Prueba de concurrencia sobre el descuento | T018 |
| SC-003 | < 15 s | Acción rápida en la app | T031 |
| US1-E1 | Actualización manual | Endpoint | T010 |
| US1-E2 | Agotado automático (manual o por consumo) | Misma regla del dominio en actualización y en descuento | T011, T019 |
| Edge 1 | Dos clientes piden la última unidad | Descuento atómico: uno acepta, el otro recibe `OUT_OF_STOCK` | T018 |
| Edge 2 | Cantidad negativa | FR-004 | T011 |
| Edge 3 | Agotado visible con etiqueta "solo reserva" | La API devuelve `estado: AGOTADO` (RF-33 lo muestra) | T012 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el dueño del emprendimiento del producto |
| RNF07 Rendimiento | ☑ | Una fila por operación |
| RNF08 Concurrencia | ☑ | Bloqueo de fila y `UPDATE` condicional |
| RNF15 Fiabilidad | ☑ | Descuento dentro de la transacción de aceptación del pedido |
| RNF16 Trazabilidad | ☑ | `updated_at` |
| RNF23 Consistencia | ☑ | Estado derivado siempre de la cantidad |
| RNF24 Errores claros | ☑ | `OUT_OF_STOCK`, `VALIDATION_ERROR` |
| RNF27 Integridad | ☑ | `Idempotency-Key` en la actualización |
| RNF28 Conectividad | ☑ | Outbox en la app |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Producto | Escribe (`cantidad_disponible`, `estado`) | `cantidad_disponible ≥ 0`; `AGOTADO` ⇔ cantidad 0 |
| ItemPedido | Lee (por el puerto de descuento, desde RF-53) | — |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento |
|---|---|---|---|---|---|
| Producto | `DISPONIBLE` | `AGOTADO` | Vendedor (cantidad 0) o descuento al aceptar un pedido | Cantidad resultante 0 | Ninguno |
| Producto | `AGOTADO` | `DISPONIBLE` | Vendedor (cantidad > 0) | — | Ninguno |

**Transiciones inválidas**: producto `ELIMINADO` → `409 INVALID_STATE`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Actualizar inventario | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | El producto pertenece al emprendimiento del usuario autenticado; si no, `403 FORBIDDEN` |

## API Contracts

### POST /api/v1/productos/{id}/inventario

- **Rol**: vendedor
- **Path params**: `id` (UUID del producto)
- **Query params**: ninguno
- **Body**: `{ "cantidadDisponible": 0 }` (decimal ≥ 0, hasta 2 decimales)

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `200` con `{ "id", "nombre", "unidad", "cantidadDisponible", "estado" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cantidad negativa, no numérica o con más de 2 decimales | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Producto de otro vendedor o rol no permitido | — |
| 404 | `NOT_FOUND` | Producto inexistente | — |
| 409 | `INVALID_STATE` | Producto `ELIMINADO` | `details.estadoActual` |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

### Puertos para otros módulos (no son endpoints)

| Puerto `in` | Lo usa | Comportamiento |
|---|---|---|
| `ConsultarDisponibilidadPort.consultar(productoIds)` | RF-45 al crear el pedido | Devuelve precio, unidad, estado, cantidad y emprendimiento de cada producto; RF-45 decide `PRODUCT_UNAVAILABLE` para un pedido inmediato con agotados |
| `DescontarInventarioPort.descontar(items)` | RF-53 al aceptar un pedido inmediato, en su misma transacción | Descuenta cada ítem con `UPDATE ... WHERE cantidad_disponible >= :x` ordenado por id; si alguno no alcanza lanza `OUT_OF_STOCK` (`409`) y la transacción completa se revierte; no se llama para reservas |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El catálogo no tiene eventos de inventario |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la actualización entra al outbox ("pendiente de envío"). Como el valor se **fija** (no se suma), un reenvío es idempotente; si varias actualizaciones del mismo producto quedan en cola, se aplican en orden.
- **Concurrencia (RNF08)**: la actualización manual toma la fila con `SELECT ... FOR UPDATE`; el descuento usa `UPDATE` condicional por ítem, siempre ordenados por id para evitar interbloqueos. Un descuento y una actualización manual simultáneos se serializan.
- **Idempotencia (RNF27)**: `idem:{userId}:{key}`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Cantidad negativa | `400` | FR-004 |
| Inventario insuficiente al aceptar | `409 OUT_OF_STOCK`; el pedido sigue `PENDIENTE` | FR-006 |
| Dos aceptaciones por la última unidad | Una acepta y la otra recibe `OUT_OF_STOCK` | Edge 1 |
| Producto de otro vendedor | `403 FORBIDDEN` | RNF03 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Dominio `Producto`: estado según cantidad; validación de decimales | FR-002, FR-004, FR-007 |
| Integración | Endpoint; pertenencia; idempotencia | US1-E1, US1-E2 |
| Concurrencia | N descuentos simultáneos sobre una cantidad limitada (sin sobreventa) y descuento vs. actualización manual | FR-005, SC-002, Edge 1 |
| Contrato de puertos | `DescontarInventarioPort` (atomicidad y reversión) y `ConsultarDisponibilidadPort` | FR-003, FR-006 |
| Frontend | Jest: acción rápida, marcar agotado, encolado en outbox | SC-003 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-57-actualizacion-de-inventario-del-producto/
├── RF-57-actualizacion-de-inventario-del-producto.md   # spec
└── plan.md                                             # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/productos/
├── domain/
│   ├── model/                    # Producto (cantidad → estado), CantidadDisponible
│   └── exception/                # InventarioInsuficienteException (OUT_OF_STOCK)
├── application/
│   ├── ports/in/                 # ActualizarInventarioPort, DescontarInventarioPort, ConsultarDisponibilidadPort
│   ├── ports/out/                # ProductoRepositoryPort (bloqueo de fila, descuento condicional)
│   └── usecase/                  # ActualizarInventarioUseCase, DescontarInventarioUseCase, ConsultarDisponibilidadUseCase
└── infrastructure/adapters/
    ├── in/web/                   # ProductoController (POST inventario)
    └── out/persistence/          # consultas con bloqueo y UPDATE condicional
backend/src/test/java/co/edu/napa/productos/

frontend/src/components/productos/EditorInventario.tsx          # hoja inferior con cantidad y botón "Marcar agotado"
frontend/src/app/(vendedor)/productos/index.tsx                  # (RF-55) abre el editor desde cada fila
frontend/src/services/productos.ts                               # actualizarInventario() escribe en el outbox
frontend/__tests__/productos/inventario.test.tsx
```

**Structure Decision**: módulo `productos`. Los dos puertos `in` de descuento y consulta son la única forma en que `pedidos` toca el inventario (ArchUnit).

## Phase 1: Setup

- [ ] T001 Confirmar `producto` (RF-54) y definir la excepción `InventarioInsuficienteException` mapeada a `OUT_OF_STOCK` en `shared/infrastructure/web/ApiExceptionHandler.java`

## Phase 2: Foundational

- [ ] T002 Métodos `bloquearPorId`, `fijarCantidad` y `descontarSiAlcanza` en `ProductoRepositoryPort` y su adaptador en `productos/application/ports/out/` y `.../out/persistence/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Actualizar disponibilidad de un producto (Priority: P1)

**Goal**: el vendedor mantiene su inventario al día y el sistema nunca vende de más.
**Independent Test**: poner un producto en cero y verificar que queda agotado y que no admite pedidos inmediatos.
**Cubre**: FR-001–FR-007, SC-001–SC-003, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de actualización manual (válida, pertenencia, idempotencia) en `backend/src/test/java/co/edu/napa/productos/ActualizarInventarioIT.java`
- [ ] T011 [P] [US1] Test unitario del dominio (0 → `AGOTADO`, > 0 → `DISPONIBLE`, negativos y decimales) en `.../productos/ProductoInventarioTest.java`
- [ ] T012 [P] [US1] Test del contrato de `ConsultarDisponibilidadPort` con producto agotado en `.../productos/ConsultarDisponibilidadIT.java`
- [ ] T018 [P] [US1] Test de concurrencia: N descuentos simultáneos sobre la última unidad y descuento vs. actualización manual en `.../productos/InventarioConcurrenciaIT.java`
- [ ] T020 [P] [US1] Test del contrato de `DescontarInventarioPort` (descuento total, `OUT_OF_STOCK` con reversión, orden por id) en `.../productos/DescontarInventarioIT.java`
- [ ] T023 [P] [US1] Test del editor de inventario y del encolado sin red en `frontend/__tests__/productos/inventario.test.tsx`

### Implementation

- [ ] T013 [US1] `ActualizarInventarioUseCase` en `productos/application/usecase/`
- [ ] T014 [US1] `POST /productos/{id}/inventario` en `ProductoController`
- [ ] T015 [US1] Regla `Producto.fijarCantidad()` que deriva el estado en `productos/domain/model/Producto.java`
- [ ] T016 [US1] `ConsultarDisponibilidadUseCase` y su puerto `in`
- [ ] T017 [US1] Bloqueo de fila en la actualización manual
- [ ] T019 [US1] `DescontarInventarioUseCase` y su puerto `in` (atómico, ordenado por id)
- [ ] T021 [US1] Servicio `actualizarInventario()` con outbox en `frontend/src/services/productos.ts`
- [ ] T022 [US1] `EditorInventario` y conexión con `frontend/src/app/(vendedor)/productos/index.tsx`

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Medir `AGOTADO` en < 2 s (SC-001)
- [ ] T031 Medir la actualización en < 15 s con un vendedor (SC-003)
- [ ] T032 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-54; RF-45 usa `ConsultarDisponibilidadPort` y RF-53 usa `DescontarInventarioPort`; esos planes deben llamarlos, no duplicar la lógica.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| La spec no dice qué pasa al subir un producto `AGOTADO` a una cantidad > 0 | FR-002 | Vuelve a `DISPONIBLE` | Equipo |
| ¿`UNIDAD` y `PORCION` solo admiten enteros? (misma pregunta que RF-54) | FR-007 | Enteros para `UNIDAD` y `PORCION`; decimales para `KG`, `LIBRA`, `LITRO` | Equipo |
| Producto de otro vendedor: `403` (pertenencia, spec-general §2.2) o `404` (no visible) | RNF03 | `403` | Equipo |
| La actualización masiva (varios productos a la vez) no está en la spec | UX | No se incluye | Equipo |
| El vendedor no ve el historial de movimientos de inventario | RNF16 | No se guarda historial en el MVP | Equipo |

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
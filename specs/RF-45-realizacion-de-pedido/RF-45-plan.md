# Implementation Plan: Realización de pedido

**Date**: 2026-10-21
**Spec**: [RF-45-realizacion-de-pedido.md](./RF-45-realizacion-de-pedido.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados; §3.4 tarifa) · [eventos](../general/eventos.md) (E01)
**Requerimiento funcional**: RF-45
**Historias de usuario**: HU-72, HU-73, HU-74, HU-76
**Prioridad (la mayor de la spec)**: P1 (la historia de reserva, US3, es P2)
**Depende de**: RF-32 (modalidades), RF-57 (`ConsultarDisponibilidadPort`), RF-63 (`ConsultarMediosPagoPort`), RF-44 (piso de tarifa), RF-28, RF-54, RF-10, `RoutingPort` (foundations F013)
**Habilita a**: RF-48, RF-50, RF-51, RF-53 y todo el ciclo de entrega y pago

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El cliente arma un pedido con productos y cantidades de un emprendimiento, elige la modalidad que ese vendedor ofrece (ambulante: entrega directa; punto fijo: recogida y, si tiene cuenta bancaria, domicilio), indica la ubicación de entrega cuando aplica, marca opcionalmente una reserva (fecha y hora futuras) y elige el medio de pago entre los habilitados. El servidor valida, congela precios y total, calcula la tarifa del domicilio por ruta con piso (§3.4) y registra el pedido en `PENDIENTE`, emitiendo E01 por el outbox. El inventario no se descuenta aquí: se descuenta al aceptar (RF-53). Un `POST /pedidos/cotizacion` sin efectos permite mostrar el resumen y el total antes de confirmar.

## Estado actual del código

El módulo `pedidos` no existe. Ya existen `RoutingPort.computeRoute(GeoPoint, GeoPoint)` (adaptador Google Maps), `GeoPoint`, `EventPublisherPort`, el outbox y el filtro de idempotencia en `shared`. No hay parámetros `tarifa.*` en `application.yml`.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Arma, cotiza y confirma el pedido |
| Vendedor (ambulante / punto fijo) | Recibe la notificación (E01); decide en RF-53 |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Completar un pedido en < 3 min (SC-001): flujo de tres pantallas y borrador persistente.
- 100 % de pedidos con ítems, modalidad, medio de pago y, cuando aplica, ubicación y reserva (SC-002); 0 % de pedidos inmediatos sin inventario (SC-003).
- Vendedor notificado en < 1 min (SC-004): E01 por el outbox (relay cada 500 ms).
- 0 % de pedidos de domicilio con efectivo (SC-005); 100 % informan el total y el momento del pago (SC-006).
- Operaciones ≤ 3 s (RNF07); la ruta se cachea en Redis (foundations) y hay respaldo de línea recta.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Seleccionar uno o más productos con cantidad | `items[]` en el body; cada ítem es un `ItemPedido`; sin repetir producto | T010, T013, T014 |
| FR-002 | Validar inventario en pedidos inmediatos; agotados solo en reserva | `ConsultarDisponibilidadPort` (RF-57): inmediato con agotado → `PRODUCT_UNAVAILABLE`; con cantidad insuficiente → `OUT_OF_STOCK`; en reserva no se valida cantidad | T011, T015 |
| FR-003 | Modalidades según tipo de vendedor; domicilio solo con cuenta bancaria | Dominio `ModalidadesDisponibles` (rol del dueño + `domicilioDisponible` de `ConsultarMediosPagoPort`); `BANK_ACCOUNT_REQUIRED` si piden DOMICILIO sin cuenta | T012, T016 |
| FR-004 | Pedir ubicación en entrega directa y domicilio; no en recogida | Validación por modalidad; se guarda una `Ubicacion` propia del pedido | T017, T018 |
| FR-005 | Marcar cualquier pedido como reserva con fecha y hora futuras | Campo `reserva` (ISO-8601 UTC) mayor que la hora actual; no altera la modalidad | T019, T020 |
| FR-006 | Total con tarifa por ruta y piso de RF-44 | `TarifaDomicilio.calcular` (dominio puro) + `RoutingPort` + respaldo de línea recta + `ConsultarTarifaMinimaPort`; total congelado | T021, T022, T023 |
| FR-007 | Registrar en `PENDIENTE` y notificar al vendedor | Estado inicial `PENDIENTE`; evento E01 por `EventPublisherPort` en la misma transacción (el listener está en el plan de RF-38) | T010a, T031 |
| FR-008 | Impedir confirmar si falta algo | Bean Validation + reglas de dominio → `400 VALIDATION_ERROR` con el campo | T025 |
| FR-009 | Medios de pago habilitados; transferencia en domicilio; medio no cambia | `medioPago` validado con `ConsultarMediosPagoPort`; en domicilio solo `CUENTA_BANCARIA`; sin endpoint para cambiarlo | T026, T027 |
| FR-010 | Informar el pago del domicilio | La cotización y la respuesta traen `pagoDomicilio: { medio: CUENTA_BANCARIA, momento: "AL_SER_ACEPTADO", total }`; la app lo muestra antes de confirmar | T028 |
| SC-001 | < 3 min | Flujo corto + borrador | T041 |
| SC-002 | 100 % completos | Prueba parametrizada de campos faltantes | T025 |
| SC-003 | 0 % inmediatos sin inventario | Prueba de integración | T011 |
| SC-004 | Notificación < 1 min | E01 + relay | T024 |
| SC-005 | 0 % de domicilios con efectivo | Prueba de integración | T026 |
| SC-006 | 100 % informan total y momento | Prueba de contrato de la cotización | T028 |
| US1-E1 | Modalidad de ambulante | `ModalidadesDisponibles` → `ENTREGA_DIRECTA` | T012 |
| US1-E2 | Modalidades de punto fijo con cuenta | `RECOGIDA` y `DOMICILIO` | T012 |
| US1-E3 | Pedido con varios ítems | Cálculo de subtotal por ítem y total en la cotización y en la creación | T013 |
| US1-E4 | Agotado en pedido inmediato | `409 PRODUCT_UNAVAILABLE` | T011 |
| US1-E5 | Punto fijo sin cuenta | Solo `RECOGIDA` | T012 |
| US1-E6 | Agotado en reserva | Se acepta sin validar cantidad; queda `PENDIENTE` | T011 |
| US2-E1 | Ubicación para una entrega | `Ubicacion` del pedido | T017 |
| US2-E2 | Recogida sin ubicación | No se solicita; si se envía, `400` (Open Questions) | T017 |
| US2-E3 | Ubicación faltante o inválida | `400 VALIDATION_ERROR` | T025 |
| US3-E1 | Pedido marcado como reserva | Campo `reserva` | T019 |
| US3-E2 | Fecha u hora inválida | `400` si es pasada o vacía | T020 |
| US4-E1 | Creación de entrega directa o recogida | `201`, `PENDIENTE`, total y E01 | T010a |
| US4-E2 | Pedido de domicilio | Cotización con tarifa, total, solo transferencia y aviso | T021, T028 |
| US4-E3 | Datos incompletos | FR-008 | T025 |
| Edge 1 | Confirmar sin productos | `400` (mínimo un ítem) | T025 |
| Edge 2 | Cambio de precio o disponibilidad entre selección y confirmación | El servidor usa precios vigentes; `totalEsperado` opcional devuelve `409 CONFLICT` con el total actual (Open Questions) | T029 |
| Edge 3 | Cliente demasiado lejos de un ambulante | **Decisión pendiente** (RF-53 permite al vendedor rechazar) | — |
| Edge 4 | Pierde conexión antes de confirmar | Borrador local en SQLite; entrega directa y recogida pueden confirmarse por el outbox; domicilio exige conexión | T036, T037 |
| Edge 5 | Domicilio aceptado y cliente nunca paga | Sin plazo ni vencimiento en el MVP (foundations): fuera de este plan | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo `CLIENTE`; el comprador sale del JWT |
| RNF04 Privacidad | ☑ | La ubicación de entrega solo se expone después a vendedor/domiciliario (RF-35) |
| RNF07 Rendimiento | ☑ | Una transacción; ruta con caché |
| RNF08 Concurrencia | ☑ | El pedido no compite por inventario (no descuenta) |
| RNF15 Fiabilidad | ☑ | E01 por outbox en la misma transacción |
| RNF16 Trazabilidad | ☑ | Precios y total congelados; fechas |
| RNF19 Interoperabilidad | ☑ | Rutas tras `RoutingPort` con respaldo |
| RNF22 Geografía | ☑ | `Ubicacion` con `geography`; distancia en línea recta por PostGIS |
| RNF23 Consistencia | ☑ | Estado inicial único `PENDIENTE` |
| RNF24 Errores claros | ☑ | Códigos `PRODUCT_UNAVAILABLE`, `OUT_OF_STOCK`, `BANK_ACCOUNT_REQUIRED` |
| RNF27 Integridad | ☑ | `Idempotency-Key` obligatoria |
| RNF28 Conectividad | ☑ | Borrador y outbox |
| RNF29 No procesar dinero | ☑ | Solo registra el medio elegido |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Escribe | `modalidad`, `estado = PENDIENTE`, `reserva` nulo o futura, `medio_pago`, `tarifa_domicilio` solo en DOMICILIO, `total` congelado |
| ItemPedido | Escribe | `cantidad > 0`, `precio_unitario` copiado del producto |
| Ubicacion | Escribe | Una fila propia por pedido; no aplica en RECOGIDA |
| Producto | Lee (puerto de RF-57) | Estado y cantidad |
| Emprendimiento, Usuario (rol del dueño) | Lee (puerto de `emprendimientos`) | Solo `ACTIVO` |
| ConfiguracionPago | Lee (puerto de RF-63) | Medios y `domicilioDisponible` |
| TarifaMinima | Lee (puerto de RF-44) | Piso |
| outbox_event | Escribe | E01 |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento |
|---|---|---|---|---|---|
| Pedido | (nuevo) | `PENDIENTE` | Cliente | Validaciones de FR-002 a FR-009 | E01 `PedidoCreado` |

**Transiciones inválidas**: no aplica (creación).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Cotizar y crear pedido | CLIENTE | El pedido queda a nombre del usuario autenticado; el emprendimiento debe estar `ACTIVO` |

## API Contracts

### POST /api/v1/pedidos/cotizacion

> **Ruta nueva fuera del catálogo de spec-general §2.4** (Open Questions). No cambia estado ni dinero, por eso no exige `Idempotency-Key`.

- **Rol**: cliente · **Query params**: ninguno
- **Body**: `{ "emprendimientoId", "modalidad", "items": [{ "productoId", "cantidad" }], "ubicacionEntrega"?: { "latitud", "longitud", "direccion", "referencia"? }, "reserva"?: "2026-10-23T15:00:00Z" }`
- **Cabeceras**: `Authorization` ✅, `Content-Type` ✅.
- **Respuestas**: `200` con `{ "items": [{ "productoId", "nombre", "unidad", "cantidad", "precioUnitario", "subtotal", "estadoProducto" }], "subtotal", "tarifaDomicilio"?, "total", "modalidadesDisponibles": [...], "mediosPagoDisponibles": ["EFECTIVO", "CUENTA_BANCARIA"], "pagoDomicilio"?: { "medio": "CUENTA_BANCARIA", "momento": "AL_SER_ACEPTADO", "total" }, "advertencias": [...] }`.
- **Errores**: `400`, `401`, `403`, `404`, `409 PRODUCT_UNAVAILABLE`, `409 OUT_OF_STOCK`, `409 BANK_ACCOUNT_REQUIRED`, `422 BUSINESS_RULE`.

### POST /api/v1/pedidos

- **Rol**: cliente · **Path/Query params**: ninguno
- **Body**: el de la cotización más `"medioPago": "EFECTIVO" | "CUENTA_BANCARIA"` y `"totalEsperado"?` (entero)

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID generado al tocar "Confirmar" |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `201` con `Location: /api/v1/pedidos/{id}` y body `{ "id", "estado": "PENDIENTE", "modalidad", "reserva", "medioPago", "items": [...], "subtotal", "tarifaDomicilio"?, "total", "ubicacionEntrega"?, "pagoDomicilio"?, "createdAt" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta ítem, modalidad, medio de pago, ubicación (si aplica) o reserva válida; producto repetido; cantidad ≤ 0; ubicación enviada en RECOGIDA | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de cliente | — |
| 404 | `NOT_FOUND` | Emprendimiento inexistente, inactivo o eliminado; producto que no es de ese emprendimiento | "No está disponible" |
| 409 | `PRODUCT_UNAVAILABLE` | Producto agotado en un pedido inmediato | `details.productoId` |
| 409 | `OUT_OF_STOCK` | Cantidad pedida mayor que la disponible en un pedido inmediato | `details.productoId`, `disponible` |
| 409 | `BANK_ACCOUNT_REQUIRED` | DOMICILIO con un vendedor sin cuenta bancaria | "Este vendedor no ofrece domicilio" |
| 409 | `CONFLICT` | `totalEsperado` distinto del total actual | `details.totalActual` |
| 422 | `BUSINESS_RULE` | Modalidad que el tipo de vendedor no ofrece; medio no habilitado; efectivo en domicilio | `details.motivo` |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E01 `PedidoCreadoEvent` (`pedidos.pedido-creado`) | `RealizarPedidoUseCase` | Al guardar el pedido en `PENDIENTE`, en la misma transacción. Payload: `pedidoId`, `compradorId`, `vendedorId`, `modalidad`, `reserva?` |

**Eventos que consume esta feature**: ninguno. El listener que avisa al vendedor se planifica en RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el carrito se guarda como borrador en SQLite; entrega directa y recogida se envían por el outbox con la misma `Idempotency-Key` ("pendiente de envío"); si al sincronizar el servidor responde `PRODUCT_UNAVAILABLE`, `OUT_OF_STOCK` o `CONFLICT`, la app muestra el motivo y devuelve al cliente a su carrito. El domicilio exige conexión porque depende de la cotización por ruta.
- **Concurrencia (RNF08)**: crear un pedido no descuenta inventario, así que no compite por existencias; la protección contra duplicados es la `Idempotency-Key`.
- **Idempotencia (RNF27)**: `idem:{userId}:{key}`; el reintento devuelve el `201` guardado con `Idempotent-Replayed: true`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Producto agotado en pedido inmediato | `409 PRODUCT_UNAVAILABLE`; la app pide ajustar | US1-E4 |
| Cantidad mayor al inventario en pedido inmediato | `409 OUT_OF_STOCK` | FR-002 |
| Agotado en reserva | Se acepta | US1-E6 |
| Domicilio sin cuenta | `409 BANK_ACCOUNT_REQUIRED` | FR-003 |
| Efectivo en domicilio | `422 BUSINESS_RULE` | FR-009 |
| Servicio de rutas caído | Respaldo: línea recta × `tarifa.factor_linea_recta`, sin tiempo | spec-general §3.4 |
| Faltan datos | `400` con el campo | FR-008 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `TarifaDomicilio` (piso, redondeo a 100, respaldo), `ModalidadesDisponibles`, totales | FR-003, FR-006, US1-E3 |
| Integración | `POST /pedidos` y `/cotizacion` con Testcontainers (PostGIS, Redis): cada modalidad, reserva, agotados, medios, idempotencia, E01 en el outbox | US1–US4, SC-002–SC-005 |
| Contrato | Respuesta de cotización con `pagoDomicilio` | SC-006 |
| Arquitectura | `pedidos` solo usa puertos `in` de otros módulos | RNF13, RNF25 |
| Frontend | Jest: flujo de tres pantallas, borrador, outbox sin red, errores 409 | Edge 4 |
| E2E | Cliente arma pedido, vendedor lo ve pendiente | SC-004 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-45-realizacion-de-pedido/
├── RF-45-realizacion-de-pedido.md   # spec
└── plan.md                          # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pedidos/
├── domain/
│   ├── model/                    # Pedido, ItemPedido, ModalidadEntrega, EstadoPedido, TarifaDomicilio, ModalidadesDisponibles
│   ├── event/                    # PedidoCreadoEvent
│   └── exception/                # ProductoNoDisponibleException, InventarioInsuficienteException (reutiliza la de productos), ...
├── application/
│   ├── ports/in/                 # RealizarPedidoPort, CotizarPedidoPort
│   ├── ports/out/                # PedidoRepositoryPort, EmprendimientoParaPedidoPort, DisponibilidadProductosPort,
│   │                             #   MediosPagoVendedorPort, TarifaMinimaPort, UbicacionRepositoryPort, DistanciaLineaRectaPort
│   └── usecase/                  # RealizarPedidoUseCase, CotizarPedidoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # PedidoController (POST /pedidos, POST /pedidos/cotizacion), DTOs
    └── out/persistence/          # PedidoJpaEntity, ItemPedidoJpaEntity, repositorios, adaptadores a los puertos `in` de otros módulos
backend/src/main/resources/db/migration/   # V8__pedido.sql
backend/src/main/resources/application.yml # tarifa.base, tarifa.valor_por_km, tarifa.valor_por_min, tarifa.factor_linea_recta
backend/src/test/java/co/edu/napa/pedidos/

frontend/src/app/(cliente)/emprendimientos/[id]/pedido/armar.tsx      # productos y cantidades
frontend/src/app/(cliente)/emprendimientos/[id]/pedido/entrega.tsx    # modalidad, ubicación y reserva
frontend/src/app/(cliente)/emprendimientos/[id]/pedido/resumen.tsx    # total, medio de pago y confirmar
frontend/src/components/pedidos/                                      # ItemCarrito, SelectorModalidad, SelectorReserva, ResumenPedido
frontend/src/components/emprendimiento/SelectorUbicacion.tsx          # reutiliza el de RF-28
frontend/src/hooks/useCarrito.ts                                      # carrito y borrador
frontend/src/services/pedidos.ts                                      # cotizar() y crearPedido() (outbox)
frontend/src/domain/pedido.ts
frontend/src/database/schema/borrador_pedido.ts                       # borrador en SQLite
frontend/__tests__/pedidos/realizar.test.tsx
```

**Structure Decision**: módulo `pedidos` (dueño de `Pedido`, `ItemPedido` y la máquina de estados). Toca otros módulos solo por sus puertos `in` (productos, emprendimientos, pagos); `RoutingPort` viene de `shared`. La tarifa es una regla pura en `domain/model/TarifaDomicilio`, sin `domain/service`.

## Phase 1: Setup

- [ ] T001 Crear el paquete `co.edu.napa.pedidos` y su regla ArchUnit en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`
- [ ] T002 Parámetros `tarifa.base`, `tarifa.valor_por_km`, `tarifa.valor_por_min`, `tarifa.factor_linea_recta` y su clase `TarifaProperties` en `backend/src/main/resources/application.yml` y `pedidos/infrastructure/config/`

## Phase 2: Foundational

- [ ] T003 Migración `V8__pedido.sql`: tablas `pedido` (id, usuario_id, emprendimiento_id, modalidad, estado, reserva_at `timestamptz` nulo, medio_pago, tarifa_domicilio `bigint` nulo, total `bigint`, ubicacion_entrega_id único nulo, created_at, updated_at) e `item_pedido` (id, pedido_id, producto_id, cantidad `numeric(10,2)` > 0, precio_unitario `bigint`, único `(pedido_id, producto_id)`), con CHECK de modalidad/tarifa/medio/ubicación e índices `(emprendimiento_id, created_at desc)`, `(usuario_id, created_at desc)`, `(emprendimiento_id, estado)`, en `backend/src/main/resources/db/migration/`
- [ ] T004 Puertos `in` auxiliares: `ConsultarEmprendimientoParaPedidoPort` en `emprendimientos` (id, estado, dueño, rol del dueño, ubicación del punto A)
- [ ] T005 `UbicacionRepositoryPort` compartido y `DistanciaLineaRectaPort` con PostGIS (`ST_Distance` sobre `geography`) en `shared/application/ports/out/` y su adaptador

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Armar el pedido con productos y modalidad (Priority: P1)

**Goal**: cotizar el pedido con ítems y modalidad correctos.
**Independent Test**: cotizar con un ambulante y con un punto fijo y ver las modalidades correctas.
**Cubre**: FR-001–FR-003, SC-003, US1-E1–E6, Edge 2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /pedidos/cotizacion` (ambulante, punto fijo con y sin cuenta, varios ítems) en `backend/src/test/java/co/edu/napa/pedidos/CotizarPedidoIT.java`
- [ ] T011 [P] [US1] Test de inventario: inmediato con agotado (`PRODUCT_UNAVAILABLE`), cantidad insuficiente (`OUT_OF_STOCK`), reserva con agotado aceptada, en `.../pedidos/PedidoInventarioIT.java`
- [ ] T012 [P] [US1] Test unitario de `ModalidadesDisponibles` en `.../pedidos/ModalidadesDisponiblesTest.java`
- [ ] T029 [P] [US1] Test de `totalEsperado` distinto del actual → `409 CONFLICT` en `.../pedidos/PedidoTotalEsperadoIT.java`

### Implementation

- [ ] T013 [US1] Dominio `Pedido`, `ItemPedido` y cálculo de subtotales y total (redondeo a pesos) en `pedidos/domain/model/`
- [ ] T015 [US1] Validación de disponibilidad con `DisponibilidadProductosPort` (adaptador sobre `ConsultarDisponibilidadPort` de RF-57)
- [ ] T016 [US1] `ModalidadesDisponibles` y validación de la modalidad (adaptador sobre `ConsultarMediosPagoPort` de RF-63 y el rol del dueño)
- [ ] T014 [US1] `CotizarPedidoUseCase` y `PedidoController` (`POST /pedidos/cotizacion`)

**Checkpoint**: US1 funcional.

## Phase 4: User Story 2 - Indicar dónde recibiré el pedido (Priority: P1)

**Goal**: registrar la ubicación cuando la modalidad la exige.
**Independent Test**: crear pedidos de entrega directa, recogida y domicilio y verificar quién lleva ubicación.
**Cubre**: FR-004, US2-E1–E3.

### Tests (antes de implementar)

- [ ] T017 [P] [US2] Test de ubicación por modalidad (obligatoria en directa y domicilio; rechazada en recogida) en `.../pedidos/PedidoUbicacionIT.java`
- [ ] T025 [P] [US2] Test parametrizado de datos faltantes o inválidos (SC-002, FR-008, Edge 1) en `.../pedidos/PedidoValidacionIT.java`

### Implementation

- [ ] T018 [US2] Guardar `Ubicacion` propia del pedido con la columna `geography`

## Phase 5: User Story 3 - Marcar el pedido como reserva (Priority: P2)

**Goal**: pedidos anticipados sin cambiar la modalidad.
**Independent Test**: crear una reserva futura en cada modalidad.
**Cubre**: FR-005, US3-E1–E2.

### Tests (antes de implementar)

- [ ] T019 [P] [US3] Test de reserva futura en cada modalidad en `.../pedidos/PedidoReservaIT.java`
- [ ] T020 [P] [US3] Test de reserva pasada, vacía o mal formada en `.../pedidos/PedidoReservaInvalidaIT.java`

### Implementation

- [ ] T030 [US3] Campo `reserva` (UTC) y su validación contra la hora actual con `Clock` inyectable en `Pedido`

## Phase 6: User Story 4 - Confirmar el pedido y elegir cómo pagar (Priority: P1)

**Goal**: registrar el pedido, notificar y mostrar el pago del domicilio.
**Independent Test**: confirmar un pedido en cada modalidad y ver `PENDIENTE`, medio, total y E01.
**Cubre**: FR-006–FR-010, SC-001–SC-006, US4-E1–E3, Edge 4.

### Tests (antes de implementar)

- [ ] T021 [P] [US4] Test unitario de `TarifaDomicilio` (fórmula §3.4, redondeo a 100, piso, respaldo sin tiempo) en `.../pedidos/TarifaDomicilioTest.java`
- [ ] T022 [P] [US4] Test de la tarifa con `RoutingPort` y con el servicio caído (respaldo) en `.../pedidos/TarifaDomicilioRutaIT.java`
- [ ] T010a [P] [US4] Test de integración de `POST /pedidos` por modalidad (estado `PENDIENTE`, total, idempotencia) en `.../pedidos/RealizarPedidoIT.java`
- [ ] T024 [P] [US4] Test de que E01 queda en el outbox en la misma transacción y no se emite si falla el guardado, en `.../pedidos/PedidoCreadoEventoIT.java`
- [ ] T026 [P] [US4] Test de medios: habilitados, no habilitado, efectivo en domicilio en `.../pedidos/PedidoMedioPagoIT.java`
- [ ] T028 [P] [US4] Test de contrato del aviso `pagoDomicilio` en la cotización y la respuesta en `.../pedidos/PagoDomicilioAvisoIT.java`

### Implementation

- [ ] T023 [US4] `TarifaDomicilio` en `pedidos/domain/model/` y adaptadores a `RoutingPort`, `DistanciaLineaRectaPort` y `ConsultarTarifaMinimaPort` (RF-44, Fase 3)
- [ ] T027 [US4] Validación del `medioPago` en el caso de uso
- [ ] T031 [US4] `RealizarPedidoUseCase` y `POST /pedidos` en `PedidoController`
- [ ] T032 [US4] Evento `PedidoCreadoEvent` y publicación por `EventPublisherPort`

### Frontend (US1–US4)

- [ ] T033 [P] Test del flujo de tres pantallas (carrito, modalidades, ubicación, reserva, resumen, errores 409) en `frontend/__tests__/pedidos/realizar.test.tsx`
- [ ] T034 Servicio `cotizar()` y `crearPedido()` (outbox para directa y recogida) en `frontend/src/services/pedidos.ts`
- [ ] T035 `useCarrito` y componentes de `frontend/src/components/pedidos/`
- [ ] T036 Borrador persistente en `frontend/src/database/schema/borrador_pedido.ts`
- [ ] T037 Pantallas `frontend/src/app/(cliente)/emprendimientos/[id]/pedido/armar.tsx`, `entrega.tsx` y `resumen.tsx`; al `201` navega a `(cliente)/pedidos/[id]` (RF-48)

**Checkpoint**: pedido completo de extremo a extremo.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar privacidad de la ubicación y permisos (RNF03, RNF04)
- [ ] T041 Medir el tiempo de creación (SC-001) en el piloto
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` y spec-general §2.4 (ruta de cotización) y §2.2.1 (`OUT_OF_STOCK` también al crear) si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → US4 → US3 (P2) → Polish.
- Dependencias entre RF: requiere RF-44 (Fase 3), RF-57, RF-63, RF-28, RF-32 y RF-54; **la pantalla de armar el pedido necesita el listado de productos del emprendimiento (RF-33, P2)**; RF-48, RF-50, RF-51 y RF-53 dependen de este plan.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| RF-33 (listado de productos de un emprendimiento) es P2, pero el cliente lo necesita para armar el pedido | Alcance de la ola | Incluir RF-33 en este orden de construcción | Equipo |
| `POST /pedidos/cotizacion` no está en el catálogo de spec-general §2.4; sin él la app no puede mostrar el total ni la tarifa antes de confirmar (FR-006, FR-010) | Convención de API | Agregarlo, sin `Idempotency-Key` | Equipo |
| `totalEsperado` opcional para detectar cambios de precio entre cotizar y confirmar (Edge 2) | UX y consistencia | Incluirlo | Equipo |
| Redondeo del subtotal con cantidades decimales (p. ej. 2,5 kg × precio) | Totales | Redondear a pesos por ítem, mitad hacia arriba | Equipo |
| Ubicación enviada en una recogida: ¿se ignora o se rechaza? | US2-E2 | Rechazar (`400`) | Equipo |
| Cliente demasiado lejos de un ambulante (Edge 3) | Regla de negocio | Sin validación; el vendedor puede rechazar en RF-53 | Equipo |
| Antelación mínima de una reserva | US3-E2 | Solo que sea posterior a la hora actual | Equipo |
| Domicilio sin conexión | Edge 4 | Exige conexión | Equipo |
| Valores de `tarifa.base`, `tarifa.valor_por_km`, `tarifa.valor_por_min`, `tarifa.factor_linea_recta` | FR-006 | Los define el equipo | Equipo |
| El proveedor de rutas aparece como OpenRouteService en D9 y como Google Maps en foundations F013 y el código; este plan solo depende de `RoutingPort` | Costos y cuotas | Corregir `foundations/plan.md` | Equipo |
| Límites: máximo de ítems por pedido y de cantidad por ítem | Validación | Definir | Equipo |
| Varios pedidos pendientes del mismo cliente al mismo vendedor | Regla de negocio | Permitidos | Equipo |
| `OUT_OF_STOCK` en spec-general §2.2.1 dice "al aceptar"; aquí también se usa al crear un pedido inmediato (FR-002) | Catálogo de errores | Ajustar el texto | Equipo |

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
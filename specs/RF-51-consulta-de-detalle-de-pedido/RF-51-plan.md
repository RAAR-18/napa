# Implementation Plan: Consulta de detalle de pedido

**Date**: 2026-10-21
**Spec**: [RF-51-consulta-de-detalle-de-pedido.md](./RF-51-consulta-de-detalle-de-pedido.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-51
**Historias de usuario**: HU-81
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-45 (`Pedido`, `ItemPedido`), RF-50 (acceso desde el listado), RF-53 (`Pago` pendiente)
**Habilita a**: RF-35, RF-36, RF-37, RF-64, RF-65, RF-42, RF-14 (los vendedores operan desde esta pantalla), RF-48 (comparte la ruta)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Detalle completo de un pedido para el vendedor dueño del emprendimiento: ítems con cantidad y precio de compra, subtotal y total, modalidad, reserva, ubicación de entrega (cuando aplica y el pedido sigue activo), medio de pago y estado del pago, estado del pedido y datos básicos del cliente. Se sirve por `GET /pedidos/{id}`, ruta compartida con RF-48 (cliente): el caso de uso responde según el rol. Un pedido de otro emprendimiento responde `404`. La pantalla del detalle aloja los botones de las acciones del vendedor (aceptar, rechazar y las de RF-35 a RF-65).

## Estado actual del código

`pedido`, `item_pedido` y `pago` los crean los planes de RF-45 y RF-53. No hay consulta de detalle todavía.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene (su vista es RF-48) |
| Vendedor (ambulante / punto fijo) | Consulta el detalle |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Detalle en < 3 s (SC-001): una consulta con joins por clave primaria.
- 100 % de detalles consistentes con el pedido original (SC-002): precios y total salen de lo congelado en `ItemPedido`/`Pedido`, no del catálogo actual.
- 0 % de accesos a pedidos de otro emprendimiento (SC-003).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Consultar el detalle de un pedido del emprendimiento | `GET /pedidos/{id}` rama vendedor; `ConsultarPedidoVendedorUseCase` | T010, T013, T014 |
| FR-002 | Mostrar productos, cantidades, total, medio y estado del pago, modalidad, ubicación y estado | DTO de detalle con `items[]`, `total`, `medioPago`, `pago.estado`, `modalidad`, `reserva`, `ubicacionEntrega`, `estado` | T010, T015 |
| FR-003 | Datos básicos del cliente | `cliente: { nombre }` (Open Questions sobre qué más) | T011, T016 |
| FR-004 | Impedir ver pedidos de otro emprendimiento | La consulta exige que el emprendimiento del pedido sea del usuario; si no, `404` | T012 |
| SC-001 | < 3 s | Consulta por clave primaria | T030 |
| SC-002 | 100 % consistente | Precios congelados; prueba tras cambiar el precio del producto | T017 |
| SC-003 | 0 % de accesos ajenos | Prueba de aislamiento | T012 |
| US1-E1 | Consulta exitosa | Endpoint | T010 |
| US1-E2 | Pedido de otro emprendimiento | `404 NOT_FOUND` "El pedido no está disponible" | T012 |
| Edge 1 | Producto eliminado del catálogo | El producto queda con `estado ELIMINADO` (borrado lógico), así que el ítem conserva su nombre; la app lo marca "ya no está en tu catálogo" | T017, T022 |
| Edge 2 | Pedido cancelado antes de ser confirmado | Se muestra el `estado` tal cual (`CANCELADO`); la ubicación se oculta (inactivo) | T018 |
| Edge 3 | Domicilio con estado desactualizado | Esta feature no incluye el estado del domicilio; RF-14 añade `domicilio` al detalle por el mismo DTO (Open Questions) | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Pertenencia por emprendimiento validada en el caso de uso |
| RNF04 Privacidad | ☑ | La ubicación de entrega solo se devuelve mientras el pedido está activo (RF-35 FR-003); no se devuelve el celular del cliente sin definición (Open Questions) |
| RNF07 Rendimiento | ☑ | Consulta por id |
| RNF16 Trazabilidad | ☑ | Fechas y datos congelados |
| RNF23 Consistencia | ☑ | Estado del pago derivado del registro `Pago` |
| RNF24 Errores claros | ☑ | `404` "no disponible" |
| RNF28 Conectividad | ☑ | Caché de lectura SQLite |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Lee | De un emprendimiento del vendedor |
| ItemPedido | Lee | `precio_unitario` congelado |
| Producto | Lee (nombre y unidad) | Puede estar `ELIMINADO` |
| Pago | Lee | Puede no existir aún (pedido `PENDIENTE`) |
| Ubicacion | Lee | Ubicación de entrega, solo con el pedido activo |
| Usuario (cliente) | Lee (nombre) | Datos básicos |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Consultar el detalle (rama vendedor) | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | El pedido es del emprendimiento del usuario autenticado; si no, `404` |

## API Contracts

### GET /api/v1/pedidos/{id}

- **Rol**: vendedor (la rama del cliente es RF-48) · **Path params**: `id` (UUID) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "id": "…", "estado": "ACEPTADO", "modalidad": "ENTREGA_DIRECTA", "reserva": null, "createdAt": "…", "updatedAt": "…",
  "cliente": { "nombre": "Ana Pérez" },
  "items": [{ "productoId": "…", "nombre": "Mango", "unidad": "KG", "cantidad": 2.5, "precioUnitario": 3500, "subtotal": 8750, "productoEliminado": false }],
  "subtotal": 8750, "tarifaDomicilio": null, "total": 8750,
  "medioPago": "EFECTIVO", "pago": { "estado": "PENDIENTE_CONFIRMACION" },
  "ubicacionEntrega": { "latitud": 11.24, "longitud": -74.21, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" }
}
```

`pago` es `null` mientras el pedido está `PENDIENTE` (el pago se crea al aceptar). `ubicacionEntrega` es `null` en recogida y cuando el pedido ya no está activo.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol no habilitado en esta rama | — |
| 404 | `NOT_FOUND` | Pedido inexistente o de otro emprendimiento | "El pedido no está disponible" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda el último detalle visto en SQLite y lo muestra con la hora de actualización; las acciones del vendedor sobre el pedido se encolan en el outbox (RF-53 y siguientes).
- **Concurrencia (RNF08)**: no aplica (lectura); el detalle refleja el estado al consultar y la app refresca al volver a la pantalla.
- **Idempotencia (RNF27)**: no aplica.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido de otro emprendimiento | `404` "no disponible" | US1-E2 |
| Producto eliminado | El ítem se muestra con su nombre y la marca de eliminado | Edge 1 |
| Pedido cancelado | Se muestra `CANCELADO` sin ubicación | Edge 2 |
| Sin conexión | Caché con hora de actualización | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Mapeo a DTO: subtotales, `pago` nulo, ubicación oculta cuando no está activo | FR-002 |
| Integración | Endpoint con Testcontainers: cada modalidad, producto eliminado, cambio de precio posterior, pedido ajeno, pedido cancelado | US1-E1–E2, Edge 1–2, SC-002–SC-003 |
| Frontend | Jest: secciones del detalle, marca de producto eliminado, caché sin red | FR-002 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-51-consulta-de-detalle-de-pedido/
├── RF-51-consulta-de-detalle-de-pedido.md   # spec
└── plan.md                                  # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pedidos/
├── application/
│   ├── ports/in/                 # ConsultarPedidoVendedorPort
│   ├── ports/out/                # PedidoRepositoryPort (detalle con items), PagoDelPedidoPort, ClienteNombrePort
│   └── usecase/                  # ConsultarPedidoVendedorUseCase
└── infrastructure/adapters/
    ├── in/web/                   # PedidoController (GET /pedidos/{id}), DTO de detalle del vendedor
    └── out/persistence/          # consulta de detalle con items y adaptador de `pagos`
backend/src/test/java/co/edu/napa/pedidos/

frontend/src/app/(vendedor)/pedidos/[id].tsx                    # ruta Expo Router: detalle del pedido
frontend/src/components/pedidos/DetallePedidoVendedor.tsx       # secciones: cliente, ítems, entrega, pago, estado
frontend/src/components/pedidos/MapaUbicacionEntrega.tsx        # lo reutiliza RF-35
frontend/src/services/pedidos.ts                                # obtenerPedidoVendedor()
frontend/src/database/schema/pedido_detalle_cache.ts
frontend/__tests__/pedidos/detalle-vendedor.test.tsx
```

**Structure Decision**: módulo `pedidos`. `GET /pedidos/{id}` es una sola ruta (RF-48 y RF-51): el controlador delega según el rol a `ConsultarPedidoVendedorPort` o al de RF-48, cada uno con su DTO, y devuelve `404` cuando el usuario no es parte del pedido. El estado del pago llega por un puerto `out` hacia `pagos`.

## Phase 1: Setup

- [ ] T001 Confirmar `pedido`, `item_pedido` y `pago` (RF-45 T003, RF-53 T002)

## Phase 2: Foundational

- [ ] T002 Consulta de detalle con items y producto (incluye eliminados) en `PedidoRepositoryPort` y su adaptador en `pedidos/application/ports/out/` y `.../out/persistence/`
- [ ] T003 Puerto `in` de `pagos` para consultar el estado del pago de un pedido y adaptador `PagoDelPedidoPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Vendedor consulta el detalle completo de un pedido (Priority: P1)

**Goal**: el vendedor ve todo lo necesario para atender el pedido.
**Independent Test**: abrir un pedido del listado y ver su información completa.
**Cubre**: FR-001–FR-004, SC-001–SC-003, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración del detalle por modalidad en `backend/src/test/java/co/edu/napa/pedidos/ConsultarPedidoVendedorIT.java`
- [ ] T011 [P] [US1] Test de los datos del cliente expuestos (solo los definidos) en `.../pedidos/ConsultarPedidoClienteDatosIT.java`
- [ ] T012 [P] [US1] Test de pedido ajeno e inexistente (`404`) y de aislamiento entre vendedores en `.../pedidos/ConsultarPedidoAjenoIT.java`
- [ ] T017 [P] [US1] Test de consistencia: producto eliminado y cambio de precio posterior no alteran el detalle en `.../pedidos/ConsultarPedidoConsistenciaIT.java`
- [ ] T018 [P] [US1] Test de pedido `CANCELADO` y de ocultamiento de la ubicación cuando no está activo en `.../pedidos/ConsultarPedidoUbicacionIT.java`
- [ ] T021 [P] [US1] Test de la pantalla (secciones, producto eliminado, sin pago, caché sin red) en `frontend/__tests__/pedidos/detalle-vendedor.test.tsx`

### Implementation

- [ ] T013 [US1] `ConsultarPedidoVendedorUseCase` en `pedidos/application/usecase/`
- [ ] T014 [US1] `GET /pedidos/{id}` con despacho por rol en `PedidoController`
- [ ] T015 [US1] DTO de detalle del vendedor (items, subtotales, pago, ubicación)
- [ ] T016 [US1] Datos básicos del cliente por `ClienteNombrePort`
- [ ] T019 [US1] Servicio `obtenerPedidoVendedor()` con caché en `frontend/src/services/pedidos.ts` y `frontend/src/database/schema/pedido_detalle_cache.ts`
- [ ] T020 [US1] `DetallePedidoVendedor` y `MapaUbicacionEntrega` en `frontend/src/components/pedidos/`
- [ ] T022 [US1] Pantalla `frontend/src/app/(vendedor)/pedidos/[id].tsx` con espacio para las acciones del vendedor (RF-53 y siguientes)

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Medir el detalle en < 3 s (SC-001)
- [ ] T031 Revisar privacidad de la ubicación y de los datos del cliente (RNF04)
- [ ] T032 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-45 y RF-53; RF-48 añade la rama del cliente en la misma ruta; RF-14 añadirá el estado del domicilio al detalle.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Qué son los "datos básicos del cliente" (FR-003)? Nombre sí; celular y foto afectan la privacidad, y el contacto está en RF-49 | RNF04 | Solo el nombre | Equipo |
| RF-51 FR-002 pide mostrar la ubicación de entrega, pero RF-35 FR-003 la comparte solo mientras el pedido esté activo y RF-35 FR-001 la limita a pedidos aceptados de entrega directa | Contradicción entre specs | Mostrarla mientras el pedido esté `PENDIENTE`, `ACEPTADO` o `EN_CAMINO`, y ocultarla en `ENTREGADO`, `RECHAZADO` y `CANCELADO` (el vendedor la necesita para decidir al aceptar) | Equipo |
| El detalle no incluye el estado del domicilio (Edge 3) | Dependencia de RF-14 | RF-14 añade `domicilio` (id y estado) al DTO | Equipo |
| Antes de aceptar no existe `Pago`: `pago` es `null` y se muestra solo el medio elegido | FR-002 | Así | Equipo |
| El nombre de un producto eliminado se sigue mostrando | Edge 1 | Sí, con la marca `productoEliminado` | Equipo |
| `GET /pedidos/{id}` compartido con RF-48 | Contrato | Despacho por rol; `404` si no es parte del pedido | Equipo |

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
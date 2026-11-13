# Implementation Plan: Consulta del estado del pedido

**Date**: 2026-10-21
**Spec**: [RF-48-consulta-del-estado-del-pedido.md](./RF-48-consulta-del-estado-del-pedido.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-48
**Historias de usuario**: HU-78
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-45 (`Pedido`), RF-53 (`Pago` pendiente y estados), RF-51 (comparte `GET /pedidos/{id}`), RF-10
**Habilita a**: RF-26 (la trazabilidad del domicilio se abre desde esta pantalla), RF-25 (código de confirmación), RF-49

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Vista del cliente sobre su pedido: estado actual, coherente con la modalidad, fecha y hora de la última actualización, reserva (si la hay), total, medio de pago y, en domicilio aceptado, si el pago está pendiente o confirmado. Se sirve por `GET /pedidos/{id}`, la misma ruta de RF-51: el caso de uso responde con el DTO del cliente si el pedido es suyo y `404` si no lo es. La app muestra una línea de estados por modalidad, refresca cada 30 s mientras la pantalla está abierta y al recibir una notificación, y conserva una caché de lectura.

## Estado actual del código

`pedido` y `pago` los crean los planes de RF-45 y RF-53. No existe la consulta del cliente ni el listado del cliente (RF-47, P2); desde el flujo de RF-45 se llega a esta pantalla al crear el pedido o desde una notificación.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Consulta el estado de sus pedidos |
| Vendedor (ambulante / punto fijo) | No interviene (su vista es RF-51) |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Consulta en < 3 s (SC-001).
- Cambios de estado visibles en < 1 min (SC-002): notificación push (E02, E03, E04...) que dispara un refresco, más sondeo cada 30 s con la pantalla abierta.
- 0 % de accesos a pedidos ajenos (SC-003).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Consultar el estado actualizado de cualquier pedido propio | `GET /pedidos/{id}` rama cliente; `ConsultarPedidoClienteUseCase` | T010, T013, T014 |
| FR-002 | Estados coherentes con la modalidad | La API devuelve `estado` y `modalidad`; la app arma la línea de estados por modalidad (directa, recogida, domicilio) | T011, T020, T021 |
| FR-003 | Mostrar la fecha y hora de la última actualización y la reserva | `updatedAt` y `reserva` en la respuesta | T012, T015 |
| FR-004 | Impedir consultar pedidos ajenos | El pedido debe ser del comprador autenticado; si no, `404` | T016 |
| FR-005 | Indicar si el pago está pendiente o confirmado en domicilio aceptado | `pago.estado` (`PENDIENTE_CONFIRMACION` o `CONFIRMADO`) | T017 |
| SC-001 | < 3 s | Consulta por clave primaria | T030 |
| SC-002 | Cambios visibles < 1 min | Refresco por notificación y sondeo de 30 s | T022, T023 |
| SC-003 | 0 % de accesos ajenos | Prueba de aislamiento | T016 |
| US1-E1 | Estado de una entrega directa | `PENDIENTE`, `ACEPTADO`, `EN_CAMINO`, `ENTREGADO`, `RECHAZADO` | T011 |
| US1-E2 | Estado de una recogida | `PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER`, `ENTREGADO`, `RECHAZADO` | T011 |
| US1-E3 | Domicilio aceptado pendiente de pago | `pago.estado = PENDIENTE_CONFIRMACION` y `total` | T017 |
| US1-E4 | Domicilio publicado | Estado del pedido y del domicilio con enlace a la trazabilidad (RF-26); el bloque `domicilio` lo completa RF-14 | T018 |
| US1-E5 | Pedido marcado como reserva | `reserva` con fecha y hora; la app muestra "Reserva" | T012 |
| US1-E6 | Pedido rechazado | `estado = RECHAZADO` | T011 |
| Edge 1 | Pendiente por mucho tiempo | Sin vencimiento (foundations); la app muestra "Esperando respuesta del vendedor" | T020 |
| Edge 2 | Pedido ajeno | `404` | T016 |
| Edge 3 | Reserva con fecha ya vencida sin respuesta | Se mantiene el estado y se muestra la fecha acordada y que sigue pendiente | T020 |
| Edge 4 | Domicilio aceptado sin pago por mucho tiempo | Se muestra el pago pendiente y el total; sin vencimiento | T017 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el comprador del pedido |
| RNF04 Privacidad | ☑ | La respuesta del cliente no incluye datos del vendedor más allá de lo necesario |
| RNF07 Rendimiento | ☑ | Consulta por id |
| RNF16 Trazabilidad | ☑ | `updatedAt` visible |
| RNF18 Notificaciones | ☑ | El refresco se dispara con las notificaciones |
| RNF23 Consistencia | ☑ | Estados del enum y estado del pago desde `Pago` |
| RNF24 Errores claros | ☑ | `404` "no disponible" |
| RNF28 Conectividad | ☑ | Caché de lectura SQLite |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Lee | Del comprador autenticado |
| Pago | Lee | Solo existe desde que el vendedor acepta |
| Domicilio | Lee (cuando exista, RF-14) | Un solo activo por pedido |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Consultar el estado (rama cliente) | CLIENTE | El pedido es del usuario autenticado; si no, `404` |

## API Contracts

### GET /api/v1/pedidos/{id}

- **Rol**: cliente (la rama del vendedor es RF-51) · **Path params**: `id` (UUID) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "id": "…", "estado": "ACEPTADO", "modalidad": "DOMICILIO", "reserva": null,
  "createdAt": "…", "updatedAt": "…", "total": 14500, "medioPago": "CUENTA_BANCARIA",
  "pago": { "estado": "PENDIENTE_CONFIRMACION" },
  "domicilio": null
}
```

`pago` es `null` mientras el pedido está `PENDIENTE`. `domicilio` (`{ "id", "estado" }`) aparece cuando RF-14 publica el domicilio. `updatedAt` es la última actualización del pedido o, si existe, de su domicilio.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol no habilitado en esta rama | — |
| 404 | `NOT_FOUND` | Pedido inexistente o ajeno | "El pedido no está disponible" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno (la app recibe las notificaciones de RF-38 y refresca).

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda el último estado en SQLite y lo muestra con la hora de actualización; al reconectar refresca.
- **Concurrencia (RNF08)**: no aplica (lectura); el servidor es la autoridad.
- **Idempotencia (RNF27)**: no aplica.
- **Refresco**: sondeo cada 30 s solo con la pantalla en primer plano, y refresco inmediato al recibir una notificación relacionada con el pedido.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido ajeno | `404` | FR-004 |
| Pedido pendiente mucho tiempo | Sigue `PENDIENTE` con el mensaje de espera | Edge 1 |
| Sin conexión | Caché con la hora de actualización | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Mapeo del DTO del cliente; cálculo de `updatedAt` | FR-003 |
| Integración | Endpoint con Testcontainers: estados por modalidad, reserva, rechazado, domicilio con pago pendiente y confirmado, pedido ajeno | US1-E1–E6, FR-004, FR-005 |
| Frontend | Jest: línea de estados por modalidad, mensajes de espera, pago pendiente, refresco por notificación y sondeo, caché sin red | FR-002, SC-002 |
| E2E | El vendedor acepta y el cliente ve el cambio en < 1 min | SC-002 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-48-consulta-del-estado-del-pedido/
├── RF-48-consulta-del-estado-del-pedido.md   # spec
└── plan.md                                   # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pedidos/
├── application/
│   ├── ports/in/                 # ConsultarPedidoClientePort
│   ├── ports/out/                # PedidoRepositoryPort, PagoDelPedidoPort
│   └── usecase/                  # ConsultarPedidoClienteUseCase
└── infrastructure/adapters/
    ├── in/web/                   # PedidoController (GET /pedidos/{id}, rama cliente), DTO de estado
    └── out/persistence/          # reutiliza la consulta de RF-51
backend/src/test/java/co/edu/napa/pedidos/

frontend/src/app/(cliente)/pedidos/[id].tsx                    # ruta Expo Router: estado del pedido
frontend/src/components/pedidos/LineaEstadoPedido.tsx          # pasos por modalidad (directa, recogida, domicilio)
frontend/src/components/pedidos/AvisoPagoPendiente.tsx         # total y mensaje de transferencia en domicilio
frontend/src/hooks/useRefrescoPedido.ts                        # sondeo de 30 s y refresco por notificación
frontend/src/services/pedidos.ts                               # obtenerPedidoCliente()
frontend/src/database/schema/pedido_detalle_cache.ts
frontend/__tests__/pedidos/estado-cliente.test.tsx
```

**Structure Decision**: módulo `pedidos`. Se reutilizan la consulta y el puerto de pago de RF-51; solo cambia el DTO y la regla de pertenencia (comprador en lugar de dueño del emprendimiento). Los textos por modalidad viven en la app (la API devuelve estados, no frases).

## Phase 1: Setup

- [ ] T001 Confirmar `GET /pedidos/{id}` con despacho por rol (RF-51, T014) y `PagoDelPedidoPort`

## Phase 2: Foundational

- [ ] T002 Verificar `PedidoRepositoryPort` para consultar por id y comprador (RF-51, T002)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Cliente consulta el avance de su pedido (Priority: P1)

**Goal**: el cliente conoce el estado de su compra sin contactar al vendedor.
**Independent Test**: crear un pedido en cada modalidad, avanzarlo desde el vendedor y verlo actualizado.
**Cubre**: FR-001–FR-005, SC-001–SC-003, US1-E1–E6, Edge 1–4.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /pedidos/{id}` del cliente en `backend/src/test/java/co/edu/napa/pedidos/ConsultarPedidoClienteIT.java`
- [ ] T011 [P] [US1] Test de estados por modalidad (directa, recogida, rechazado) en `.../pedidos/ConsultarPedidoClienteModalidadIT.java`
- [ ] T012 [P] [US1] Test de reserva y de `updatedAt` en `.../pedidos/ConsultarPedidoClienteReservaIT.java`
- [ ] T016 [P] [US1] Test de pedido ajeno (`404`) y aislamiento entre clientes en `.../pedidos/ConsultarPedidoClienteAjenoIT.java`
- [ ] T017 [P] [US1] Test de domicilio aceptado con pago pendiente y confirmado en `.../pedidos/ConsultarPedidoClientePagoIT.java`
- [ ] T018 [P] [US1] Test del bloque `domicilio` cuando exista (RF-14) en `.../pedidos/ConsultarPedidoClienteDomicilioIT.java`
- [ ] T024 [P] [US1] Test de la pantalla (línea de estados por modalidad, mensajes de espera, pago pendiente, caché sin red) en `frontend/__tests__/pedidos/estado-cliente.test.tsx`

### Implementation

- [ ] T013 [US1] `ConsultarPedidoClienteUseCase` en `pedidos/application/usecase/`
- [ ] T014 [US1] Rama de cliente en `GET /pedidos/{id}` de `PedidoController`
- [ ] T015 [US1] DTO de estado con `updatedAt` (máximo entre pedido y domicilio) y `reserva`
- [ ] T019 [US1] Servicio `obtenerPedidoCliente()` con caché en `frontend/src/services/pedidos.ts`
- [ ] T020 [US1] `LineaEstadoPedido` y `AvisoPagoPendiente` en `frontend/src/components/pedidos/`
- [ ] T021 [US1] Etiquetas de estado por modalidad en `frontend/src/domain/pedido.ts`
- [ ] T022 [US1] Hook `useRefrescoPedido` (sondeo de 30 s y refresco por notificación) en `frontend/src/hooks/`
- [ ] T023 [US1] Pantalla `frontend/src/app/(cliente)/pedidos/[id].tsx`

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Medir la consulta en < 3 s y el cambio visible en < 1 min (SC-001, SC-002)
- [ ] T031 Revisar permisos (RNF03)
- [ ] T032 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-45, RF-53 y RF-51 (ruta y consulta compartidas); RF-14 añade el bloque `domicilio` y RF-26 la trazabilidad.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| El cliente no tiene listado de pedidos hasta RF-47 (P2); solo llega al estado desde la confirmación del pedido o una notificación | Acceso a la pantalla | Incluir RF-47 en el orden de construcción | Equipo |
| `updatedAt` debe considerar el domicilio, porque el pedido permanece `ACEPTADO` mientras el domicilio avanza | FR-003 | Máximo entre ambos | Equipo |
| Intervalo de sondeo (30 s) y su impacto en datos y batería en gama baja | RNF28, RNF31 | 30 s solo en primer plano | Equipo |
| Los textos de cada estado por modalidad viven en la app | UX | Así | Equipo |
| ¿Se muestran los datos del vendedor (nombre del emprendimiento) en esta vista? La spec no lo pide | UX | Solo el nombre del emprendimiento | Equipo |

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
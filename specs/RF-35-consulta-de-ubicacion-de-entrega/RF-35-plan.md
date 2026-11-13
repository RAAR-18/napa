# Implementation Plan: Consulta de ubicación de entrega

**Date**: 2026-10-21
**Spec**: [RF-35-consulta-de-ubicacion-de-entrega.md](./RF-35-consulta-de-ubicacion-de-entrega.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-35
**Historias de usuario**: HU-60
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-45 (`Pedido` y `Ubicacion` de entrega), RF-53 (pedido `ACEPTADO`), RF-51 (detalle y `MapaUbicacionEntrega`), RF-10
**Habilita a**: RF-36 (el vendedor se dirige al cliente antes de iniciar la entrega)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor ambulante abre un mapa con la ubicación que el cliente indicó al hacer el pedido y su propia posición, para llegar hasta él. El backend expone `GET /pedidos/{id}/ubicacion-entrega`, que solo responde si el pedido es del emprendimiento del vendedor, es de **entrega directa** y está activo (`ACEPTADO` o `EN_CAMINO`); en cualquier otro caso responde `404` "el pedido no está disponible". La posición del vendedor se obtiene en el dispositivo (expo-location) y no viaja al servidor. La dirección en texto acompaña siempre al mapa como respaldo (RNF19).

## Estado actual del código

No existe `entregas` ni la consulta. El pedido y su `Ubicacion` de entrega los crea RF-45; el estado `ACEPTADO` lo produce RF-53. El componente `MapaUbicacionEntrega` lo crea RF-51 y este plan lo amplía.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene (su ubicación se comparte solo con el vendedor del pedido) |
| Vendedor (ambulante) | Consulta la ubicación |
| Domiciliario | No interviene (la ubicación del domicilio es RF-18) |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Ubicación visible en el mapa en < 5 s (SC-001): una consulta por clave primaria y el mapa de `react-native-maps` con el punto ya recibido.
- 0 % de acceso a vendedores ajenos (SC-002) y respuesta con `Cache-Control: no-store` (RNF04).
- La posición del vendedor no se envía al servidor ni se guarda.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Solo pedidos aceptados de entrega directa, inmediatos o reservas | `ConsultarUbicacionEntregaUseCase` exige modalidad `ENTREGA_DIRECTA` y estado `ACEPTADO` o `EN_CAMINO`; sirve igual a inmediatos y reservas | T010, T011, T012, T013 |
| FR-002 | Mapa interactivo con la ubicación del cliente y la del vendedor, con la dirección en texto como respaldo | La API devuelve latitud, longitud, dirección y referencia; la app dibuja ambos puntos y siempre muestra el texto | T020, T021, T022 |
| FR-003 | Compartirla solo con el vendedor del pedido y mientras esté activo | Pertenencia por emprendimiento, estados activos, `no-store` y limpieza de la caché local al salir del estado activo | T012, T014, T015, T023 |
| SC-001 | < 5 s | Consulta por id y el mapa recibe el punto directo | T040 |
| SC-002 | 0 % de vendedores ajenos | Prueba de aislamiento | T012 |
| US1-E1 | Ubicación de un pedido inmediato | Endpoint y mapa con ambos puntos | T010, T020 |
| US1-E2 | Ubicación de una reserva del día | Igual; la respuesta incluye `reserva` | T011 |
| US1-E3 | Pedido pendiente o ajeno | `404 NOT_FOUND` "El pedido no está disponible" | T012, T013 |
| Edge 1 | Cliente demasiado lejos | La app muestra la distancia en línea recta calculada en el dispositivo; no bloquea la acción (Open Questions) | T020 |
| Edge 2 | Reserva con la ubicación indicada hace mucho | La respuesta trae `indicadaEn` (fecha del pedido); la app lo muestra junto a la dirección | T011, T020 |
| Edge 3 | Sin señal o sin permiso de ubicación | Sin permiso: el mapa muestra solo al cliente y el texto; sin señal: último dato en caché local de un pedido activo | T021, T022, T023 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Rol y pertenencia validados en el caso de uso |
| RNF04 Privacidad | ☑ | Solo el vendedor del pedido, solo mientras está activo, `Cache-Control: no-store`; la posición del vendedor nunca sale del dispositivo; la caché local se borra al terminar |
| RNF07 Rendimiento | ☑ | Consulta por id |
| RNF19 Interoperabilidad | ☑ | La dirección en texto es el respaldo si el mapa no carga |
| RNF22 Geografía | ☑ | Latitud y longitud de la `Ubicacion` (`geography(Point,4326)` derivada) |
| RNF24 Errores claros | ☑ | `404` "no disponible" |
| RNF28 Conectividad | ☑ | Caché local del pedido activo |
| RNF31 Gama baja | ☑ | Un solo mapa, sin rutas ni seguimiento continuo |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Lee | `ENTREGA_DIRECTA`, estado `ACEPTADO` o `EN_CAMINO`, del emprendimiento del vendedor |
| Ubicacion | Lee | La de entrega del pedido; no se modifica |
| Emprendimiento | Lee (dueño) | Pertenencia |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /pedidos/{id}/ubicacion-entrega` | VENDEDOR_AMBULANTE | Pedido de su emprendimiento; si no, `404` |

## API Contracts

### GET /api/v1/pedidos/{id}/ubicacion-entrega

- **Rol**: vendedor ambulante · **Path params**: `id` (UUID) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "pedidoId": "…", "estado": "ACEPTADO", "reserva": null, "indicadaEn": "2026-10-21T14:00:00Z",
  "ubicacion": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" }
}
```

`reserva` es `null` en un pedido inmediato; `indicadaEn` es la fecha de creación del pedido.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor ambulante | — |
| 404 | `NOT_FOUND` | Pedido inexistente, de otro emprendimiento, de otra modalidad, `PENDIENTE` o ya no activo | "El pedido no está disponible" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: al consultar con éxito, la app guarda la respuesta en SQLite solo para ese pedido activo. Sin señal muestra el último dato guardado con la hora. La entrada se borra cuando el pedido pasa a `ENTREGADO` (RF-37), cuando una consulta responde `404` y al cerrar sesión.
- **Concurrencia (RNF08)**: no aplica (lectura).
- **Idempotencia (RNF27)**: no aplica.
- **Posición del vendedor**: `expo-location` en primer plano, una lectura al abrir la pantalla y otra al pulsar "Actualizar mi posición"; sin seguimiento continuo (RNF31).

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido pendiente, ajeno o de otra modalidad | `404` "El pedido no está disponible" | US1-E3 |
| Sin permiso de ubicación | Se muestra solo el punto del cliente, la dirección y un aviso para habilitar el permiso | Edge 3 |
| Sin señal | Último dato local del pedido activo | Edge 3 |
| Mapa que no carga | La dirección en texto y la referencia siguen visibles | RNF19 |
| Cliente muy lejos | Distancia visible; sin bloqueo | Edge 1 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `UbicacionEntregaVisible` (modalidad y estados activos) | FR-001, FR-003 |
| Integración | Endpoint con Testcontainers: inmediato, reserva, pendiente, `EN_CAMINO`, `ENTREGADO`, otra modalidad, ajeno, rol equivocado, cabecera `no-store` | US1-E1–E3 |
| Frontend | Jest: mapa con y sin permiso, texto de respaldo, distancia, `indicadaEn`, caché sin red y su borrado | FR-002, Edge 1–3 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-35-consulta-de-ubicacion-de-entrega/
├── RF-35-consulta-de-ubicacion-de-entrega.md   # spec
└── plan.md                                     # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/entregas/
├── application/
│   ├── ports/in/                 # ConsultarUbicacionEntregaPort
│   ├── ports/out/                # PedidoParaEntregaPort (lectura de pedidos)
│   └── usecase/                  # ConsultarUbicacionEntregaUseCase
└── infrastructure/adapters/
    ├── in/web/                   # EntregaDirectaController (GET /pedidos/{id}/ubicacion-entrega), DTO
    └── out/persistence/          # adaptador sobre el puerto `in` de pedidos

backend/src/main/java/co/edu/napa/pedidos/
├── application/ports/in/         # ConsultarPedidoParaEntregaPort (id, dueño, modalidad, estado, reserva, createdAt, ubicación)
└── domain/model/                 # regla UbicacionEntregaVisible
backend/src/test/java/co/edu/napa/{entregas,pedidos}/

frontend/src/app/(vendedor)/pedidos/[id]/ubicacion.tsx            # ruta Expo Router: mapa de entrega
frontend/src/components/pedidos/MapaUbicacionEntrega.tsx          # (RF-51) se amplía con el marcador del vendedor y la distancia
frontend/src/hooks/usePosicionActual.ts                           # permiso y lectura única con expo-location
frontend/src/services/entregas.ts                                 # obtenerUbicacionEntrega() con caché local
frontend/src/database/schema/ubicacion_entrega_cache.ts           # solo pedidos activos; se borra al terminar
frontend/__tests__/entregas/ubicacion-entrega.test.tsx
```

**Structure Decision**: `entregas` es el módulo de la entrega del vendedor ambulante (RF-35 a RF-37). La consulta pasa por un puerto `in` de solo lectura de `pedidos` (`ConsultarPedidoParaEntregaPort`); `entregas` nunca lee las tablas de `pedidos`. En Expo Router el archivo `[id].tsx` (RF-51) y la carpeta `[id]/` conviven, por lo que el detalle no se mueve.

## Phase 1: Setup

- [ ] T001 Crear el paquete `co.edu.napa.entregas` y su regla ArchUnit en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`

## Phase 2: Foundational

- [ ] T002 `ConsultarPedidoParaEntregaPort` en `pedidos/application/ports/in/` y su implementación de solo lectura (id, dueño, modalidad, estado, reserva, createdAt y `Ubicacion` de entrega)
- [ ] T003 `PedidoParaEntregaPort` en `entregas/application/ports/out/` y su adaptador sobre el puerto de `pedidos`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Vendedor ambulante ve dónde entregar (Priority: P1)

**Goal**: llegar hasta donde está el cliente.
**Independent Test**: aceptar un pedido de entrega directa y abrir su ubicación en el mapa.
**Cubre**: FR-001–FR-003, SC-001–SC-002, US1-E1–E3, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración del endpoint con pedido inmediato `ACEPTADO` y `EN_CAMINO` (cuerpo y `Cache-Control: no-store`) en `backend/src/test/java/co/edu/napa/entregas/ConsultarUbicacionEntregaIT.java`
- [ ] T011 [P] [US1] Test con pedido marcado como reserva (campo `reserva` e `indicadaEn`) en `.../entregas/ConsultarUbicacionReservaIT.java`
- [ ] T012 [P] [US1] Test de pedido ajeno, rol equivocado y aislamiento entre vendedores en `.../entregas/ConsultarUbicacionAjenaIT.java`
- [ ] T013 [P] [US1] Test de pedido `PENDIENTE`, `RECHAZADO`, `ENTREGADO`, `CANCELADO` y de modalidad distinta de entrega directa (`404`) en `.../entregas/ConsultarUbicacionEstadosIT.java`
- [ ] T014 [P] [US1] Test unitario de `UbicacionEntregaVisible` en `backend/src/test/java/co/edu/napa/pedidos/UbicacionEntregaVisibleTest.java`
- [ ] T023 [P] [US1] Test de la pantalla (mapa con y sin permiso, texto de respaldo, distancia, `indicadaEn`, caché sin red y su borrado) en `frontend/__tests__/entregas/ubicacion-entrega.test.tsx`

### Implementation

- [ ] T015 [US1] Regla `UbicacionEntregaVisible` (modalidad y estados activos) en `pedidos/domain/model/`
- [ ] T016 [US1] `ConsultarUbicacionEntregaUseCase` (dueño → `404`, regla de visibilidad → `404`) en `entregas/application/usecase/`
- [ ] T017 [US1] `GET /pedidos/{id}/ubicacion-entrega` y su DTO en `EntregaDirectaController`, con `Cache-Control: no-store`
- [ ] T020 [US1] Ampliar `MapaUbicacionEntrega` con el marcador del vendedor, la distancia en línea recta y la dirección en texto en `frontend/src/components/pedidos/`
- [ ] T021 [US1] Hook `usePosicionActual` (permiso y lectura única) en `frontend/src/hooks/`
- [ ] T022 [US1] Servicio `obtenerUbicacionEntrega()` con caché en `frontend/src/services/entregas.ts` y `frontend/src/database/schema/ubicacion_entrega_cache.ts`
- [ ] T024 [US1] Pantalla `frontend/src/app/(vendedor)/pedidos/[id]/ubicacion.tsx` y acceso desde el detalle del pedido (RF-51) para entrega directa activa

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir la carga del mapa en < 5 s en un dispositivo de gama baja (SC-001)
- [ ] T041 Revisar privacidad: sin registros de la ubicación del cliente en logs, borrado de la caché local (RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-45, RF-53 y RF-51; RF-36 y RF-37 actúan sobre el mismo pedido.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ~~Ubicación duplicada entre RF-35 y el detalle de RF-51~~ | Contrato y privacidad | **Resuelto**: se mantienen ambas | Equipo |
| "En su fecha acordada" (funcionales) y US1-E2 ("hoy") sugieren limitar la consulta de una reserva a su día; FR-001 no lo exige | Alcance | Sin restricción de fecha: se puede consultar desde que se acepta (la restricción de 15 minutos aplica a iniciar la entrega, RF-36) | Equipo |
| La spec dice "aceptados"; el vendedor también la necesita con el pedido `EN_CAMINO` y FR-003 dice "mientras esté activo" | Alcance | Visible en `ACEPTADO` y `EN_CAMINO` | Equipo |
| Edge 1: cliente demasiado lejos; no hay distancia máxima definida | Regla | Solo se muestra la distancia; no se bloquea | Equipo |
| Edge 3: sin señal; guardar la ubicación del cliente en el teléfono del vendedor | RNF04 | Caché local solo de pedidos activos, borrada al entregar, al responder `404` y al cerrar sesión | Equipo |
| El `404` en lugar de `403` para un pedido pendiente (la spec dice "deniega el acceso e informa que no está disponible") | Contrato | `404` con el mensaje de la spec, según la convención de spec-general §2.2 | Equipo |

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
# Implementation Plan: Listado de pedidos del cliente

**Date**: 2026-10-27
**Spec**: [RF-47-listado-de-pedidos-del-cliente.md](./RF-47-listado-de-pedidos-del-cliente.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-47
**Historias de usuario**: HU-77
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-45, RF-48, RF-50, RF-10
**Habilita a**: RF-48, RF-25 y RF-01 (acceso desde la lista)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El cliente lista sus pedidos con `GET /pedidos`, la misma ruta que usa el vendedor en RF-50: el caso de uso se elige por rol y el cliente solo ve los suyos. Cada fila trae emprendimiento, modalidad, fecha de creación, estado, total y, si es reserva, la fecha acordada; se ordena del más reciente al más antiguo, es paginada y admite filtros por `estado` y por rango de fechas (`desde`, `hasta`). Un cliente sin pedidos recibe lista vacía y la app muestra el mensaje. La pantalla es la puerta de entrada al estado del pedido (RF-48), a su ubicación de entrega, al código del domicilio (RF-25) y a "Calificar" (RF-01), y guarda la última página en SQLite para verla sin conexión.

## Estado actual del código

RF-50 ya implementa `GET /pedidos` para el vendedor y responde `403` a los demás roles hasta que exista esta rama. La tabla `pedido`, sus estados y `GET /pedidos/{id}` (RF-48) vienen de RF-45 y RF-48. El nombre del emprendimiento se lee por un puerto de `emprendimientos`.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Lista sus pedidos |
| Vendedor | Usa la otra rama de la misma ruta (RF-50) |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Listado en < 3 s (SC-001): consulta paginada con índice `(comprador_id, created_at desc)`.
- 100 % de los pedidos mostrados son del cliente autenticado (SC-002): el filtro por `comprador_id` sale de la sesión, nunca de un parámetro.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El cliente consulta el listado de sus pedidos | `GET /pedidos` con rol CLIENTE | T010, T020, T021 |
| FR-002 | Mostrar emprendimiento, modalidad, fecha, estado y total | DTO de fila con esos campos más `reserva` | T010, T022 |
| FR-003 | Filtrar por estado y por fecha | Parámetros `estado`, `desde`, `hasta` (más `modalidad` y `reserva`, que spec-general §2.3 comparte con RF-50) con validación de filtros desconocidos | T012, T013 |
| FR-004 | Un cliente no ve pedidos de otros | Filtro por el `comprador_id` de la sesión | T011 |
| SC-001 | < 3 s | Índice y paginación | T014 |
| SC-002 | 100 % de los pedidos son del cliente | Prueba con varios clientes | T011 |
| US1-E1 | Listado de pedidos del más reciente al más antiguo | Orden `createdAt desc`, `id` de desempate | T010 |
| US1-E2 | Cliente sin pedidos | `200` con lista vacía y mensaje en la app | T015, T030 |
| Edge 1 | Reservas programadas para una fecha futura | La fila lleva `reserva` y la app muestra "Reserva para {fecha}" | T010, T031 |
| Edge 2 | Volumen alto: paginar o filtrar | Paginación y filtros por estado y fecha | T012, T014 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Rol CLIENTE; los pedidos salen de la sesión |
| RNF04 Privacidad | ☑ | Solo datos del propio cliente y el nombre del emprendimiento |
| RNF07 Rendimiento ≤ 3 s | ☑ | Índice y paginación |
| RNF24 Errores claros | ☑ | `400` con el filtro desconocido |
| RNF28 Conectividad | ☑ | Caché local de lectura |
| RNF31 Gama baja | ☑ | Lista virtualizada y paginada |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Lee | Solo los del cliente autenticado |
| Emprendimiento | Lee (puerto) | Nombre, aunque esté inactivo o eliminado |

## State Transitions

Esta feature es de solo lectura: no cambia estados.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /pedidos` (rama cliente) | CLIENTE | `comprador_id` = usuario autenticado |

## API Contracts

### GET /api/v1/pedidos (rama del cliente)

- **Rol**: cliente (la rama del vendedor es RF-50) · **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| estado | enum | ☐ | `ENTREGADO` | Cualquier estado de pedido |
| modalidad | enum | ☐ | `DOMICILIO` | `ENTREGA_DIRECTA`, `RECOGIDA` o `DOMICILIO` |
| reserva | boolean | ☐ | `true` | `true`: solo reservas; `false`: solo inmediatos |
| desde, hasta | fecha ISO-8601 | ☐ | `2026-10-01` | Rango sobre la fecha de creación (`desde` inclusive, `hasta` inclusive hasta el final del día) |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `createdAt,desc` | Paginación y orden |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id`, `Cache-Control: no-store` y body:

```json
{
  "content": [
    { "id": "…", "emprendimiento": { "id": "…", "nombre": "Frutas Doña Ana" }, "modalidad": "DOMICILIO",
      "createdAt": "…", "estado": "ACEPTADO", "reserva": null, "total": 18500 }
  ],
  "page": 0, "size": 20, "totalElements": 1
}
```

`reserva` es la fecha y hora acordadas, o `null` en un pedido inmediato.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido, `estado` inválido, `desde` mayor que `hasta`, `size` mayor que 100 | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol que no es cliente ni vendedor | — |

Un cliente sin pedidos recibe `200` con lista vacía.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda la primera página en SQLite y la muestra con "datos guardados" sin red. Los filtros solo funcionan en línea.
- **Concurrencia (RNF08)**: no aplica; un cambio de estado se ve al refrescar (al volver a primer plano o con el gesto de arrastrar).
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Cliente sin pedidos | `200` con lista vacía | US1-E2 |
| Filtro desconocido o inválido | `400` con el parámetro | FR-003 |
| Pedidos de otro cliente | No aparecen; no hay forma de pedirlos | FR-004 |
| Sin red | Caché local | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: orden, campos, reserva, filtros por estado y fechas (combinados y sin resultados), lista vacía, paginación, filtro desconocido, aislamiento entre dos clientes, roles | FR-001–FR-004, US1-E1–E2, Edge 1–2 |
| Rendimiento | 5 000 pedidos de un cliente: primera página en < 3 s | SC-001 |
| Arquitectura | `pedidos` lee nombres de emprendimiento solo por el puerto `in` de `emprendimientos` | RNF13 |
| Frontend | Jest: fila con reserva, filtros, lista vacía, caché sin red, navegación al detalle | US1, Edge 1 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-47-listado-de-pedidos-del-cliente/
├── RF-47-listado-de-pedidos-del-cliente.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pedidos/
├── application/
│   ├── ports/in/                 # ListarPedidosClientePort
│   ├── ports/out/                # PedidoRepositoryPort (consulta por comprador), EmprendimientoNombrePort (en lote)
│   └── usecase/                  # ListarPedidosClienteUseCase
└── infrastructure/adapters/
    ├── in/web/                   # PedidoController (rama cliente de GET /pedidos), DTO de fila
    └── out/persistence/          # consulta por comprador con filtros y orden

backend/src/main/java/co/edu/napa/emprendimientos/application/ports/in/   # ConsultarNombresEmprendimientoPort (en lote)
backend/src/test/java/co/edu/napa/pedidos/

frontend/src/app/(cliente)/pedidos/index.tsx                # ruta Expo Router: mis pedidos
frontend/src/components/pedidos/ItemPedidoCliente.tsx       # fila: emprendimiento, modalidad, fecha, estado, total, reserva
frontend/src/components/pedidos/FiltrosPedidos.tsx          # compartido con RF-50: estado y fechas
frontend/src/services/pedidos.ts                            # listarPedidosCliente() con caché local
frontend/src/database/schema/pedidos_cliente_cache.ts
frontend/__tests__/pedidos/listado-cliente.test.tsx
```

**Structure Decision**: módulo `pedidos`. `GET /pedidos` sigue siendo una sola ruta con una rama por rol (RF-50 ya dejó el despacho en el controlador, que solo llama al puerto `in` que corresponde al rol). Los nombres de emprendimiento se piden en lote para no hacer una consulta por fila. `FiltrosPedidos` se comparte con el vendedor; el cliente muestra estado, fechas y, opcionalmente, modalidad y reserva.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `pedidos`, el despacho por rol de `GET /pedidos` (RF-50) y su regla ArchUnit

## Phase 2: Foundational

- [ ] T002 Índice `(comprador_id, created_at desc)` sobre `pedido` en la siguiente migración libre de `backend/src/main/resources/db/migration/` (si RF-45 no lo creó)
- [ ] T003 `ConsultarNombresEmprendimientoPort` (en lote) en `emprendimientos/application/ports/in/` y su adaptador `EmprendimientoNombrePort`
- [ ] T004 Consulta por comprador con filtros en `PedidoRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Ver el historial de mis compras (Priority: P2)

**Goal**: el cliente encuentra sus pedidos y entra a su estado.
**Independent Test**: un cliente con pedidos ve su lista ordenada y filtrable; otro cliente no ve los suyos.
**Cubre**: FR-001–FR-004, SC-001, SC-002, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de la rama del cliente de `GET /pedidos` (campos, orden `createdAt desc`, reserva) en `backend/src/test/java/co/edu/napa/pedidos/ListarPedidosClienteIT.java`
- [ ] T011 [P] [US1] Test de aislamiento: dos clientes con pedidos, cada uno ve solo los suyos; el vendedor sigue recibiendo su rama (RF-50) en `.../pedidos/ListarPedidosClienteAislamientoIT.java`
- [ ] T012 [P] [US1] Test de filtros por `estado`, `modalidad`, `reserva`, por `desde`/`hasta` y combinados (con y sin resultados) en `.../pedidos/ListarPedidosClienteFiltrosIT.java`
- [ ] T013 [P] [US1] Test de filtro desconocido, `estado` inválido, `desde` > `hasta` y `size` > 100 (`400`), rol no habilitado (`403`) y sin sesión (`401`) en `.../pedidos/ListarPedidosClienteValidacionIT.java`
- [ ] T014 [P] [US1] Test de rendimiento con 5 000 pedidos (< 3 s) y de paginación en `.../pedidos/ListarPedidosClienteRendimientoIT.java`
- [ ] T015 [P] [US1] Test de cliente sin pedidos (`200`, lista vacía) en `.../pedidos/ListarPedidosClienteVacioIT.java`
- [ ] T030 [P] [US1] Test de la pantalla (lista vacía, filtros, caché sin red, navegación al estado del pedido) en `frontend/__tests__/pedidos/listado-cliente.test.tsx`
- [ ] T031 [P] [US1] Test de `ItemPedidoCliente` con y sin reserva en `frontend/__tests__/pedidos/item-pedido-cliente.test.tsx`

### Implementation

- [ ] T020 [US1] `ListarPedidosClienteUseCase` y `ListarPedidosClientePort` (filtros, comprador de la sesión, nombres en lote) en `pedidos/application/`
- [ ] T021 [US1] Rama del cliente en `PedidoController` (`GET /pedidos`), reemplazando el `403` de RF-50 para ese rol
- [ ] T022 [US1] DTO de fila y consulta JPA con orden y desempate por `id`
- [ ] T032 [US1] `ItemPedidoCliente` y reutilización de `FiltrosPedidos` en `frontend/src/components/pedidos/`
- [ ] T033 [US1] `listarPedidosCliente()` con caché SQLite en `frontend/src/services/pedidos.ts`
- [ ] T034 [US1] Pantalla `frontend/src/app/(cliente)/pedidos/index.tsx` con scroll paginado, estado vacío y navegación a `(cliente)/pedidos/[id]` (RF-48)

**Checkpoint**: el cliente tiene su historial.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` con datos del piloto
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-45, RF-48 (detalle) y RF-50 (despacho por rol); habilita el acceso de RF-48, RF-25 y RF-01 desde la lista.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| La spec de RF-47 solo pide filtros por estado y fecha, pero spec-general §2.3 lista `modalidad` y `reserva` también para RF-47 | Contrato | Se incluyen (spec-general manda) | Equipo |
| Edge 1: las reservas futuras se ordenan por fecha de creación, no por la fecha de la reserva | UX | Así, con la fecha de la reserva visible en la fila | Equipo |
| `desde` y `hasta` se aplican a la fecha de creación (igual que RF-50) | Filtro | Fecha de creación | Equipo |
| El cliente no puede cancelar ni repetir un pedido desde la lista (no hay RF) | Alcance | Fuera de la ola | Equipo |
| La fila no muestra si el pedido ya tiene calificación (RF-01 devuelve `409 CONFLICT` al repetir) | UX | Sin marca; mejora posible en RF-01 | Equipo |
| El emprendimiento eliminado se sigue mostrando por nombre | Datos | Sí | Equipo |

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
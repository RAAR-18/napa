# Implementation Plan: Listado de pedidos del vendedor

**Date**: 2026-10-21
**Spec**: [RF-50-listado-de-pedidos-del-vendedor.md](./RF-50-listado-de-pedidos-del-vendedor.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados; §2.3 filtros de `GET /pedidos`) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-50
**Historias de usuario**: HU-80
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-45 (tabla `pedido`), RF-28, RF-10
**Habilita a**: RF-51, RF-53 (se accede desde el listado), RF-47 (comparte la ruta `GET /pedidos`)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Listado paginado de los pedidos recibidos por el emprendimiento del vendedor autenticado, con cliente (nombre), fecha, estado, total, modalidad y reserva (fecha y hora acordadas), filtrable por estado, modalidad, reserva y rango de fechas. Se sirve por `GET /pedidos`, ruta compartida con RF-47 (cliente): el caso de uso elige la rama según el rol. Orden por defecto: más reciente primero. La app guarda una caché de lectura en SQLite y se refresca cuando llega la notificación de un pedido nuevo.

## Estado actual del código

`pedidos` y la tabla `pedido` los crea el plan de RF-45 (con índices `(emprendimiento_id, created_at desc)` y `(emprendimiento_id, estado)`). No hay consulta de listado todavía.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene (su listado es RF-47) |
| Vendedor (ambulante / punto fijo) | Consulta sus pedidos |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Listado en < 3 s (SC-001): índices y paginación por defecto de 20.
- 100 % de los pedidos mostrados son del emprendimiento del vendedor (SC-002): el filtro por emprendimiento se aplica en la consulta, no en la app.
- Estado y modalidad legibles sin abrir el detalle (SC-003).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Consultar todos los pedidos recibidos | `GET /pedidos` rama vendedor; `ListarPedidosVendedorUseCase` | T010, T013, T014 |
| FR-002 | Mostrar cliente, fecha, estado y total | Ítem con `cliente.nombre`, `createdAt`, `estado`, `total` | T010, T015 |
| FR-003 | Filtrar por estado | Filtro `estado` | T011, T016 |
| FR-004 | No ver pedidos de otros emprendimientos | La consulta siempre filtra por el emprendimiento del usuario (sale del JWT) | T012 |
| FR-005 | Mostrar modalidad y reserva; filtrar por modalidad y por reserva | Campos `modalidad` y `reserva`; filtros `modalidad` y `reserva=true|false` | T011, T016 |
| SC-001 | < 3 s | Índices y paginación | T030 |
| SC-002 | 100 % del vendedor | Prueba de aislamiento entre vendedores | T012 |
| SC-003 | Estado y modalidad visibles | Campos en el ítem y etiquetas en la app | T022 |
| US1-E1 | Listado con datos resumidos | Endpoint | T010 |
| US1-E2 | Filtro por estado | `estado` | T011 |
| US1-E3 | Filtro por reserva | `reserva=true` con fecha y hora | T011 |
| Edge 1 | Sin pedidos | `200` con `content: []`; estado vacío en la app | T017, T021 |
| Edge 2 | Orden con el mismo estado | `createdAt` descendente y `id` como desempate | T018 |
| Edge 3 | Un pedido cambia de estado mientras consulta | El listado refleja el estado al consultar; la app refresca al deslizar y al recibir una notificación | T023 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo roles vendedor; filtro por emprendimiento del JWT |
| RNF04 Privacidad | ☑ | El listado solo expone el nombre del cliente |
| RNF07 Rendimiento | ☑ | Índices y paginación |
| RNF16 Trazabilidad | ☑ | `createdAt` del pedido |
| RNF23 Consistencia | ☑ | Estados del enum |
| RNF24 Errores claros | ☑ | Filtro desconocido → `400` |
| RNF28 Conectividad | ☑ | Caché de lectura SQLite |
| RNF31 Gama baja | ☑ | Lista paginada sin imágenes |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Lee | Solo del emprendimiento del vendedor |
| Usuario (cliente) | Lee (nombre) | Solo el nombre |
| Emprendimiento | Lee (puerto `in`) | El `ACTIVO` del vendedor |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Listar pedidos recibidos | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | Pedidos del emprendimiento del usuario autenticado |

## API Contracts

### GET /api/v1/pedidos

- **Rol**: vendedor (la rama del cliente es RF-47) · **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| estado | enum | ☐ | `PENDIENTE` | Cualquier estado de pedido |
| modalidad | enum | ☐ | `DOMICILIO` | `ENTREGA_DIRECTA`, `RECOGIDA` o `DOMICILIO` |
| reserva | boolean | ☐ | `true` | `true`: solo reservas; `false`: solo inmediatos |
| desde, hasta | fecha ISO-8601 | ☐ | `2026-10-01` | Rango sobre la fecha de creación |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `createdAt,desc` | Paginación y orden |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `{ "content": [{ "id", "cliente": { "nombre" }, "createdAt", "estado", "modalidad", "reserva", "total" }], "page", "size", "totalElements" }` y `X-Request-Id`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido o valor inválido | Indica el filtro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol no habilitado en esta rama | — |

Un vendedor sin emprendimiento activo recibe `200` con lista vacía.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno (el listener de E01 en RF-38 notifica al vendedor; la app refresca el listado).

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda la última página en SQLite y la muestra con "Última actualización: hh:mm" si no hay red; al reconectar o al recibir una notificación de pedido, refresca.
- **Concurrencia (RNF08)**: no aplica (lectura).
- **Idempotencia (RNF27)**: no aplica.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Sin pedidos | `200` + `content: []` y mensaje de lista vacía | Edge 1 |
| Filtro desconocido | `400` | spec-general §2.3 |
| Sin conexión | Caché con la hora de actualización | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Mapeo de pedido a ítem de listado | FR-002 |
| Integración | Endpoint con Testcontainers: filtros (estado, modalidad, reserva, fechas), orden, paginación, aislamiento entre vendedores, listado vacío | US1-E1–E3, SC-002, Edge 1–2 |
| Rendimiento | 10 000 pedidos de prueba en < 3 s | SC-001 |
| Frontend | Jest: filtros, estado vacío, etiquetas de modalidad y reserva, caché sin red | SC-003 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-50-listado-de-pedidos-del-vendedor/
├── RF-50-listado-de-pedidos-del-vendedor.md   # spec
└── plan.md                                    # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pedidos/
├── application/
│   ├── ports/in/                 # ListarPedidosVendedorPort
│   ├── ports/out/                # PedidoRepositoryPort (consulta paginada con filtros), ClienteNombrePort
│   └── usecase/                  # ListarPedidosVendedorUseCase
└── infrastructure/adapters/
    ├── in/web/                   # PedidoController (GET /pedidos), DTO de ítem
    └── out/persistence/          # consulta con filtros y orden
backend/src/test/java/co/edu/napa/pedidos/

frontend/src/app/(vendedor)/pedidos/index.tsx             # ruta Expo Router: listado de pedidos
frontend/src/components/pedidos/ItemPedidoVendedor.tsx    # fila: cliente, fecha, estado, modalidad, reserva, total
frontend/src/components/pedidos/FiltrosPedidos.tsx
frontend/src/services/pedidos.ts                          # listarPedidosVendedor()
frontend/src/database/schema/pedidos_cache.ts             # caché de lectura
frontend/__tests__/pedidos/listado-vendedor.test.tsx
```

**Structure Decision**: módulo `pedidos`. `GET /pedidos` es una sola ruta para cliente (RF-47) y vendedor (RF-50); el caso de uso por rol evita filtrar en el controlador. El nombre del cliente se obtiene por un puerto `out` hacia `cuentas`.

## Phase 1: Setup

- [ ] T001 Confirmar la tabla `pedido` y sus índices (RF-45, T003)

## Phase 2: Foundational

- [ ] T002 Consulta paginada con filtros (estado, modalidad, reserva, fechas) en `PedidoRepositoryPort` y su adaptador en `pedidos/application/ports/out/` y `.../out/persistence/`
- [ ] T003 Puerto `out` `ClienteNombrePort` con el puerto `in` correspondiente de `cuentas`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Vendedor consulta sus pedidos recibidos (Priority: P1)

**Goal**: el vendedor ve y filtra los pedidos de su emprendimiento.
**Independent Test**: abrir "Pedidos" y ver solo los de su emprendimiento.
**Cubre**: FR-001–FR-005, SC-001–SC-003, US1-E1–E3, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración del listado (datos, paginación) en `backend/src/test/java/co/edu/napa/pedidos/ListarPedidosVendedorIT.java`
- [ ] T011 [P] [US1] Test de filtros `estado`, `modalidad`, `reserva` y rango de fechas en `.../pedidos/ListarPedidosFiltrosIT.java`
- [ ] T012 [P] [US1] Test de aislamiento entre dos vendedores en `.../pedidos/ListarPedidosAislamientoIT.java`
- [ ] T017 [P] [US1] Test de listado vacío y de filtros inválidos (`400`) en `.../pedidos/ListarPedidosVacioIT.java`
- [ ] T018 [P] [US1] Test de orden y desempate con el mismo estado en `.../pedidos/ListarPedidosOrdenIT.java`
- [ ] T021 [P] [US1] Test de la pantalla (vacío, filtros, etiquetas, caché sin red) en `frontend/__tests__/pedidos/listado-vendedor.test.tsx`

### Implementation

- [ ] T013 [US1] `ListarPedidosVendedorUseCase` en `pedidos/application/usecase/`
- [ ] T014 [US1] Rama de vendedor en `GET /pedidos` de `PedidoController`
- [ ] T015 [US1] DTO de ítem con `cliente.nombre`, `modalidad` y `reserva`
- [ ] T016 [US1] Validación de filtros (`400` ante desconocidos)
- [ ] T019 [US1] Servicio `listarPedidosVendedor()` con caché en `frontend/src/services/pedidos.ts` y `frontend/src/database/schema/pedidos_cache.ts`
- [ ] T020 [US1] `ItemPedidoVendedor` y `FiltrosPedidos` en `frontend/src/components/pedidos/`
- [ ] T022 [US1] Pantalla `frontend/src/app/(vendedor)/pedidos/index.tsx` con paginación, estado vacío y etiquetas de estado, modalidad y reserva
- [ ] T023 [US1] Refrescar el listado al deslizar y al recibir la notificación de pedido nuevo

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Medir el listado en < 3 s (SC-001)
- [ ] T031 Revisar privacidad y permisos (RNF03, RNF04)
- [ ] T032 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-45; RF-51 y RF-53 se alcanzan desde este listado; RF-47 añade la rama del cliente en el mismo endpoint.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Orden por defecto del listado (Edge 2) | UX | `createdAt` descendente, `id` como desempate | Equipo |
| El listado solo muestra el nombre del cliente (no su celular) | RNF04 | Solo el nombre; "datos básicos" completos van en el detalle (RF-51) | Equipo |
| `desde` y `hasta` se aplican sobre la fecha de creación del pedido (no sobre la reserva) | FR-005 | Fecha de creación | Equipo |
| ¿Se destacan los pedidos `PENDIENTE` o las reservas próximas? | UX | No | Equipo |
| La rama del cliente de `GET /pedidos` la implementa RF-47 (P2) | Contrato compartido | RF-50 responde `403` a otros roles hasta entonces | Equipo |

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
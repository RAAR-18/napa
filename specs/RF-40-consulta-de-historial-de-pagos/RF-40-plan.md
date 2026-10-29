# Implementation Plan: Consulta de historial de pagos

**Date**: 2026-10-27
**Spec**: [RF-40-consulta-de-historial-de-pagos.md](./RF-40-consulta-de-historial-de-pagos.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-40
**Historias de usuario**: HU-65, HU-67, HU-69
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-53, RF-42, RF-43, RF-10
**Habilita a**: RF-41 (comparte la ruta y las columnas)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Cada usuario consulta su historial de pagos con `GET /pagos`, paginado y sin ningún identificador de usuario: el servidor decide qué filas le tocan según su rol. El cliente ve los pagos de sus pedidos (en domicilio, el monto es productos más tarifa). El vendedor ve los cobros de los pedidos de su emprendimiento y, en los domicilios, las tarifas que pagó a domiciliarios. El domiciliario ve las tarifas de los domicilios que repartió. Cada fila trae fecha, monto, medio (`EFECTIVO` o `CUENTA_BANCARIA`), tipo, estado y el pedido o domicilio asociado, y el vendedor además la modalidad del pedido. Se filtra por medio, estado, tipo y fechas. Un cliente sin pagos recibe una lista vacía.

## Estado actual del código

La tabla `pago` y los pagos de tipo `PEDIDO` vienen de RF-53 y RF-42, y los de tipo `TARIFA_DOMICILIO` de RF-43. El `pago` no guarda a qué usuarios pertenece: se deduce de su pedido o domicilio (spec-general). Listar por usuario obligaría a unir con `pedido`, `domicilio` y `emprendimiento`, que son de otros módulos, así que este plan agrega a `pago` tres columnas de copia (`comprador_id`, `vendedor_id`, `domiciliario_id`), con el mismo criterio que `domicilio.vendedor_id`: son copias físicas, no cambian el ER (Open Questions). RF-41 reutiliza la misma ruta para el administrador.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Consulta los pagos de sus pedidos |
| Vendedor (ambulante / punto fijo) | Consulta los cobros de sus pedidos y las tarifas que pagó (solo punto fijo) |
| Domiciliario | Consulta las tarifas de sus domicilios |
| Administrador | Usa la rama de RF-41 |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Historial en < 3 s (SC-002): consulta paginada con un índice por usuario y fecha (`(comprador_id, created_at desc)`, `(vendedor_id, created_at desc)`, `(domiciliario_id, created_at desc)`); la modalidad del pedido se pide en lote para la página.
- 100 % propios y 0 % ajenos (SC-001, SC-003): la ruta no recibe un id de usuario; el filtro por el usuario del JWT lo pone el caso de uso y no se puede quitar desde los parámetros.
- Dinero como entero en pesos (spec-general).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El cliente ve solo los pagos de sus pedidos | Rama CLIENTE: `comprador_id` = usuario del JWT | T010, T020, T021 |
| FR-002 | El vendedor ve los pagos de sus pedidos y las tarifas que pagó | Rama VENDEDOR: `vendedor_id` = usuario del JWT, tipos `PEDIDO` y `TARIFA_DOMICILIO` | T011, T022 |
| FR-003 | El domiciliario ve solo las tarifas de sus domicilios | Rama DOMICILIARIO: `domiciliario_id` = usuario del JWT, tipo `TARIFA_DOMICILIO` | T012, T023 |
| FR-004 | Mostrar fecha, monto, medio, entidad relacionada y estado | Campos de cada fila | T010, T024 |
| FR-005 | Impedir ver el historial de otro usuario | No hay id de usuario en la ruta; el filtro lo fija el servidor | T013 |
| FR-006 | Filtrar por medio de pago | Parámetro `medio` (y `estado`, `tipo`, `desde`, `hasta`) | T014, T025 |
| SC-001 | 100 % propios | Prueba con varios usuarios | T013 |
| SC-002 | < 3 s | Índices y paginación | T015 |
| SC-003 | 0 % de acceso a pagos ajenos | Prueba cruzada | T013 |
| US1-E1 | Historial del cliente | `200` con sus pagos | T010 |
| US1-E2 | Cliente sin pagos | `200` con lista vacía | T016 |
| US2-E1 | Historial del vendedor con modalidad y tarifas | `200` con cobros y tarifas pagadas | T011 |
| US3-E1 | Historial del domiciliario | `200` con sus tarifas y su estado | T012 |
| Edge 1 | Consultar pagos que no le pertenecen | No es posible pedirlos; ninguna ruta por id | T013 |
| Edge 2 | Pago pendiente tras la entrega | Se muestra con su estado `PENDIENTE_CONFIRMACION` o `REGISTRADO` | T017 |
| Edge 3 | Volumen alto | Paginación (máximo 100) y filtros por medio, estado, tipo y fechas | T014, T015 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Filtro por el usuario del JWT según su rol; el administrador usa RF-41 |
| RNF04 Datos personales | ☑ | `Cache-Control: no-store`; sin datos de otras personas en las filas |
| RNF07 Rendimiento ≤ 3 s | ☑ | Índices por usuario y fecha, y lote para la modalidad |
| RNF13 Arquitectura | ☑ | `pagos` pide la modalidad a `pedidos` por un puerto `in` |
| RNF16 Trazabilidad | ☑ | Fechas de creación y confirmación en cada fila |
| RNF24 Errores claros | ☑ | `400` con el parámetro inválido |
| RNF28 Conectividad | ☑ | Caché local de la primera página |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pago | Lee | Con las copias `comprador_id`, `vendedor_id` y `domiciliario_id` (nulas según el tipo) |
| Pedido | Lee (puerto) | Solo la modalidad, en lote |

## State Transitions

Esta feature es de solo lectura: no cambia estados. Muestra `PENDIENTE_CONFIRMACION`, `REGISTRADO` y `CONFIRMADO` tal como están.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /pagos` | CLIENTE | Pagos `PEDIDO` con `comprador_id` = usuario del JWT |
| `GET /pagos` | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | Pagos con `vendedor_id` = usuario del JWT (cobros de sus pedidos y tarifas que pagó) |
| `GET /pagos` | DOMICILIARIO | Pagos `TARIFA_DOMICILIO` con `domiciliario_id` = usuario del JWT |
| `GET /pagos` | ADMINISTRADOR | Rama de RF-41 |

## API Contracts

### GET /api/v1/pagos

- **Rol**: cliente, vendedor o domiciliario (el administrador es RF-41) · **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| medio | enum | ☐ | `EFECTIVO` | `EFECTIVO` o `CUENTA_BANCARIA` |
| estado | enum | ☐ | `CONFIRMADO` | `PENDIENTE_CONFIRMACION`, `REGISTRADO` o `CONFIRMADO` |
| tipo | enum | ☐ | `TARIFA_DOMICILIO` | `PEDIDO` o `TARIFA_DOMICILIO` (útil para el vendedor) |
| desde, hasta | fecha ISO-8601 | ☐ | `2026-10-01` | Rango sobre la fecha de creación |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `createdAt,desc` | `sort` admite `createdAt` y `monto`; por defecto `createdAt,desc` |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "content": [
    { "id": "…", "tipo": "PEDIDO", "monto": 14500, "medio": "CUENTA_BANCARIA", "estado": "CONFIRMADO",
      "createdAt": "…", "confirmadoAt": "…", "pedidoId": "…", "domicilioId": null, "modalidad": "DOMICILIO" },
    { "id": "…", "tipo": "TARIFA_DOMICILIO", "monto": 5000, "medio": "CUENTA_BANCARIA", "estado": "REGISTRADO",
      "createdAt": "…", "confirmadoAt": null, "pedidoId": null, "domicilioId": "…", "modalidad": null }
  ],
  "page": 0, "size": 20, "totalElements": 2
}
```

`modalidad` la entrega solo en los pagos de pedido y solo al vendedor; para el cliente y el domiciliario es `null`. En una tarifa `pedidoId` es `null` y `domicilioId` identifica el domicilio. `monto` del pedido en domicilio es el total (productos más tarifa).

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido, `medio`, `estado` o `tipo` inválidos, `desde` mayor que `hasta`, `size` mayor que 100, `sort` inválido | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |

Un usuario sin pagos recibe `200` con lista vacía.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda en SQLite la primera página del historial y la muestra con "datos guardados" sin red; al recuperar la conexión se refresca.
- **Concurrencia (RNF08)**: no aplica a la lectura; un pago que cambia de estado mientras se mira aparece actualizado al refrescar.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Sin pagos | `200` con lista vacía | US1-E2 |
| Filtro inválido | `400` con el parámetro | RNF24 |
| Pago pendiente de confirmación | Se muestra con su estado | Edge 2 |
| Intento de ver pagos ajenos | No existe forma: no hay id en la ruta | Edge 1 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Cliente: solo sus pagos, en efectivo y por cuenta, con domicilio al total; vendedor: cobros y tarifas pagadas con modalidad; domiciliario: solo sus tarifas | FR-001–FR-004, US1–US3 |
| Integración | Con varios usuarios de cada rol, nadie ve filas de otro (prueba cruzada) | FR-005, SC-001, SC-003, Edge 1 |
| Integración | Filtros por medio, estado, tipo y fechas, combinados; paginación y orden; filtros inválidos | FR-006, Edge 3 |
| Integración | Sin pagos; pago pendiente tras la entrega | US1-E2, Edge 2 |
| Rendimiento | 100 000 pagos: primera página y filtros en < 3 s | SC-002 |
| Arquitectura | `pagos` pide la modalidad solo por el puerto `in` de `pedidos` | RNF13 |
| Frontend | Jest: lista y filtros por rol, estado vacío, caché sin red | US1–US3 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-40-consulta-de-historial-de-pagos/
├── RF-40-consulta-de-historial-de-pagos.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pagos/
├── application/
│   ├── ports/in/                 # ListarMisPagosPort
│   ├── ports/out/                # PagoRepositoryPort (consulta por usuario y rol con filtros), ModalidadPedidosPort (lote)
│   └── usecase/                  # ListarMisPagosUseCase
└── infrastructure/adapters/
    ├── in/web/                   # PagoController (GET /pagos, despacha por rol), PagoHistorialResponse
    └── out/persistence/          # consulta paginada con filtros

backend/src/main/java/co/edu/napa/pedidos/application/ports/in/   # ConsultarModalidadPedidosPort (ids → modalidad, en lote)
backend/src/main/resources/db/migration/                           # columnas e índices de pago (ver T002)
backend/src/test/java/co/edu/napa/pagos/

frontend/src/app/(comun)/pagos/index.tsx                           # historial con filtros
frontend/src/components/pagos/FilaPago.tsx
frontend/src/components/pagos/FiltrosPagos.tsx
frontend/src/services/pagos.ts                                     # listarPagos() con caché
frontend/__tests__/pagos/historial.test.tsx
```

**Structure Decision**: módulo `pagos`, dueño de `Pago`. La ruta `GET /pagos` es una sola con una rama por rol (como `GET /calificaciones`). Las tres columnas de usuario son copias que se fijan al crear el pago, para filtrar y paginar sin unir con otros módulos; la modalidad del pedido, que es solo de lectura para el vendedor, se pide en lote.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `pagos`, su regla ArchUnit y las ramas de `GET /pagos`

## Phase 2: Foundational

- [ ] T002 Columnas `comprador_id`, `vendedor_id` y `domiciliario_id` (nulas según el tipo) e índices `(comprador_id, created_at DESC)`, `(vendedor_id, created_at DESC)` y `(domiciliario_id, created_at DESC)` sobre `pago`: ya incluidas en `V9__pago.sql` (RF-53 T002); si V9 ya se aplicó, una migración nueva con relleno desde pedido y domicilio
- [ ] T003 `RegistrarPagoPedidoPendientePort` (RF-53) y `RegistrarPagoTarifaUseCase` (RF-43) fijan esas columnas al crear el pago
- [ ] T004 `ConsultarModalidadPedidosPort` en `pedidos` (ids a modalidad, en lote) y `ModalidadPedidosPort` en `pagos`
- [ ] T005 Método de consulta por usuario, rol y filtros en `PagoRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - El cliente consulta sus pagos (Priority: P2)

**Goal**: el cliente revisa lo que ha pagado.
**Independent Test**: con pagos en efectivo y por cuenta, ver solo los propios.
**Cubre**: FR-001, FR-004–FR-006, SC-001–SC-003, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /pagos` como cliente (campos, efectivo y cuenta, domicilio al total) en `backend/src/test/java/co/edu/napa/pagos/HistorialClienteIT.java`
- [ ] T013 [P] [US1] Test cruzado con varios usuarios de cada rol: nadie ve pagos de otro, en `.../pagos/HistorialAislamientoIT.java`
- [ ] T014 [P] [US1] Test de filtros por medio, estado, tipo y fechas, paginación, orden y filtros inválidos en `.../pagos/HistorialFiltrosIT.java`
- [ ] T015 [P] [US1] Test de rendimiento con 100 000 pagos en `.../pagos/HistorialRendimientoIT.java`
- [ ] T016 [P] [US1] Test de cliente sin pagos (`200`, lista vacía) en `.../pagos/HistorialVacioIT.java`
- [ ] T017 [P] [US1] Test de pagos pendientes tras la entrega (se muestran con su estado) en `.../pagos/HistorialPendientesIT.java`
- [ ] T030 [P] [US1] Test de la pantalla de historial (lista, filtros, vacío, caché sin red) en `frontend/__tests__/pagos/historial.test.tsx`

### Implementation

- [ ] T020 [US1] `ListarMisPagosPort` y `ListarMisPagosUseCase`: fija el filtro por el usuario y el rol, valida los parámetros y arma la página
- [ ] T021 [US1] `GET /pagos` en `PagoController` con la rama del cliente
- [ ] T024 [US1] `PagoHistorialResponse` con fecha, monto, medio, tipo, estado y entidad relacionada
- [ ] T025 [US1] Filtros por medio, estado, tipo y fechas en la consulta
- [ ] T031 [US1] `listarPagos()` con caché SQLite, `FilaPago` y `FiltrosPagos` en `frontend/src/`
- [ ] T032 [US1] Pantalla `(comun)/pagos/index.tsx`, enlazada desde el perfil

**Checkpoint**: el cliente ve su historial.

## Phase 4: User Story 2 - El vendedor consulta lo cobrado y pagado (Priority: P2)

**Goal**: el vendedor concilia su caja y las tarifas pagadas.
**Independent Test**: ver los cobros de sus pedidos con la modalidad y las tarifas que pagó a domiciliarios.
**Cubre**: FR-002, US2-E1.

### Tests (antes de implementar)

- [ ] T011 [P] [US2] Test de `GET /pagos` como vendedor (cobros con modalidad y tarifas pagadas; solo de su emprendimiento) en `.../pagos/HistorialVendedorIT.java`

### Implementation

- [ ] T022 [US2] Rama del vendedor en el caso de uso, con la modalidad de los pedidos en lote

**Checkpoint**: el vendedor ve cobros y tarifas.

## Phase 5: User Story 3 - El domiciliario consulta sus ingresos (Priority: P2)

**Goal**: el domiciliario revisa las tarifas que cobró.
**Independent Test**: ver solo las tarifas de sus domicilios con su estado.
**Cubre**: FR-003, US3-E1.

### Tests (antes de implementar)

- [ ] T012 [P] [US3] Test de `GET /pagos` como domiciliario (solo tarifas propias, siempre por cuenta bancaria, con su estado) en `.../pagos/HistorialDomiciliarioIT.java`

### Implementation

- [ ] T023 [US3] Rama del domiciliario en el caso de uso

**Checkpoint**: los tres roles consultan su historial.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` de las tres consultas por usuario con datos de piloto
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → US3 → Polish. US2 y US3 pueden ir en paralelo con US1 una vez hecha la fase 2.
- Dependencias entre RF: requiere RF-53 (creación del pago), RF-42, RF-43 (pago de la tarifa), RF-10; RF-41 comparte la ruta y las columnas.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| **`pago` no guarda a qué usuarios pertenece** (spec-general: "se deduce"); listar por usuario sin unir módulos exige `comprador_id`, `vendedor_id` y `domiciliario_id` en la tabla | Modelo | Copias físicas fijadas al crear el pago, sin cambiar el ER (igual que `domicilio.vendedor_id`). Hay que ajustar V9 en RF-53 y la creación en RF-43. **Confirmar** | Equipo |
| El vendedor de punto fijo ve las tarifas que pagó; el ambulante no tiene domicilios | Alcance | Sus filas de tarifa serán siempre cero | Equipo |
| `GET /pagos` comparte ruta con RF-41 (administrador) | Contrato | Una rama por rol, cada una con su caso de uso | Equipo |
| FR-006 pide filtrar por medio; spec-general ya lista también `estado`, `tipo`, `desde` y `hasta` para esta ruta | Contrato | Los cinco filtros valen para todos los roles | Equipo |
| ¿La "fecha" es la de creación o la de confirmación? | Datos | Se muestran las dos; el orden y el filtro usan la de creación | Equipo |
| El cliente solo ve pagos de tipo `PEDIDO` | Regla | Así: la tarifa del domicilio no es suya (la paga el vendedor) | Equipo |
| La ruta `GET /pagos` ya está en spec-general §2.3 pero no en §2.4 | Contrato | Se agrega a spec-general §2.4 | Equipo |

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
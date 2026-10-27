# Implementation Plan: Consultar calificaciones y comentarios

**Date**: 2026-10-25
**Spec**: [RF-02-consultar-calificaciones-y-comentarios.md](./RF-02-consultar-calificaciones-y-comentarios.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-02
**Historias de usuario**: HU-08 a HU-18
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-01, RF-10, RF-31/32, RF-60 (T003), RF-18, RF-26
**Habilita a**: RF-06

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

`GET /calificaciones?tipoEntidad=…&entidadId=…` devuelve, para una entidad (emprendimiento, producto o usuario), su **promedio** y el **listado paginado** de calificaciones y comentarios. El promedio no se guarda: se calcula en cada consulta sobre las calificaciones `ACTIVA` del destino, así que una eliminada por moderación sale del promedio y una editada (solo cambia el texto) no lo afecta. Quién puede ver qué sigue la spec: el cliente ve emprendimientos, productos, domiciliarios y otros clientes (US1, US2); el vendedor ve su emprendimiento, sus productos, los clientes de sus pedidos y, si es de punto fijo, los domiciliarios de sus pedidos (US3); el domiciliario ve emprendimientos, otros domiciliarios y clientes (US4). La respuesta lleva el promedio en un campo `resumen` junto al listado. El modo administrador de este mismo endpoint lo agrega RF-06. La app muestra estrellas y comentarios en el detalle del emprendimiento, en el panel de reputación del vendedor y en las pantallas de domicilio, con caché local para ver lo último sin red.

## Estado actual del código

No existe aún la consulta. Las calificaciones y la tabla `calificacion` con sus índices las crea RF-01 (V13). Los puertos de lectura de emprendimientos, productos y usuarios no existen todavía (T002).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Consulta emprendimientos, productos, domiciliarios y clientes |
| Vendedor ambulante | Consulta su emprendimiento, sus productos y los clientes de sus pedidos |
| Vendedor de punto fijo | Lo anterior y los domiciliarios de sus pedidos |
| Domiciliario | Consulta emprendimientos, otros domiciliarios y clientes |
| Administrador | Lo consulta en RF-06 |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Promedio de una entidad en < 2 s (SC-001): consulta agregada con índice `(destino, estado, created_at)` de V13; sin caché del servidor.
- Promedio consistente con los valores individuales (SC-002): se calcula con la misma tabla en cada llamada; no hay valor guardado que pueda desfasarse.
- Un comentario moderado se refleja en < 5 s (RF-06 SC-002): consulta directa a la base.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El cliente consulta emprendimientos y productos | `GET /calificaciones` con `tipoEntidad` EMPRENDIMIENTO o PRODUCTO | T010, T011, T020, T021 |
| FR-002 | El cliente consulta domiciliarios y otros clientes | `tipoEntidad=USUARIO` sobre usuarios con rol DOMICILIARIO o CLIENTE | T040, T042 |
| FR-003 | El vendedor consulta lo suyo, los clientes de sus pedidos y (punto fijo) los domiciliarios | Reglas de visibilidad por rol y por pedidos en común | T050, T052 |
| FR-004 | El domiciliario consulta emprendimientos, domiciliarios y clientes | Rama del domiciliario | T060, T062 |
| FR-005 | Promedio por entidad junto con el listado | `resumen` calculado en la consulta y `content` paginado | T010, T014, T023 |
| SC-001 | Promedio en < 2 s | Índice y prueba con volumen | T017 |
| SC-002 | Promedio consistente | Cálculo en cada consulta | T014 |
| SC-003 | 90 % considera clara la reputación | Encuesta en el piloto | T071 |
| US1-E1 | Cliente ve promedio y comentarios del emprendimiento | `200` con `resumen` y `content` | T010 |
| US1-E2 | Cliente ve los del producto | Igual | T011 |
| US2-E1 | Cliente ve reputación del domiciliario | `200` | T040 |
| US3-E1 | Vendedor ve las de su emprendimiento | `200` | T050 |
| US4-E1 | Domiciliario ve las de un emprendimiento | `200` | T060 |
| Edge 1 | Entidad sin calificaciones | `resumen.promedio` nulo y `total` 0, lista vacía | T012 |
| Edge 2 | Comentarios eliminados o moderados en el promedio | Solo `ACTIVA` cuentan; la edición no cambia el promedio | T013 |
| Edge 3 | Entidad inactiva o eliminada | Inactiva `200`; eliminada o inexistente `404` | T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF07 Rendimiento | ☑ | Consulta agregada con índice; paginación |
| RNF03 Autorización | ☑ | Visibilidad por rol y por pedidos en común |
| RNF04 Privacidad | ☑ | El autor se muestra solo por su nombre de pila |
| RNF23 Consistencia | ☑ | Promedio derivado, nunca guardado |
| RNF24 Errores claros | ☑ | `400` con el filtro; `403`/`404` |
| RNF28 Conectividad | ☑ | Caché local de la última consulta |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Calificacion | Lee | Solo estado `ACTIVA` en el listado y en el promedio |
| Emprendimiento | Lee (puerto) | Estado (ACTIVO, INACTIVO, ELIMINADO) y dueño |
| Producto | Lee (puerto) | Estado y emprendimiento (y su dueño) |
| Usuario | Lee (puerto) | Rol, estado y nombre del destino y de los autores |
| Pedido | Lee (puerto) | Pedidos en común entre vendedor y cliente o domiciliario |

## State Transitions

Esta feature es de solo lectura: no cambia estados.

## Authorization

| `tipoEntidad` | CLIENTE | VENDEDOR_AMBULANTE | VENDEDOR_PUNTO_FIJO | DOMICILIARIO |
|---|---|---|---|---|
| EMPRENDIMIENTO | Cualquiera | Solo el suyo | Solo el suyo | Cualquiera |
| PRODUCTO | Cualquiera | Solo los suyos | Solo los suyos | Cualquiera |
| USUARIO con rol CLIENTE | Cualquiera | Con pedido a su emprendimiento | Con pedido a su emprendimiento | Cualquiera |
| USUARIO con rol DOMICILIARIO | Cualquiera | `403` | Con domicilio de un pedido suyo | Cualquiera |
| USUARIO con otro rol (vendedor, administrador) | `404` | `404` | `404` | `404` |

El administrador lo consulta en RF-06. Un destino no permitido para el rol responde `403`; un destino que no existe o no es consultable, `404`.

## API Contracts

### GET /api/v1/calificaciones

- **Rol**: cliente, vendedor o domiciliario · **Path params**: ninguno
- **Query params**: `tipoEntidad` ✅ (EMPRENDIMIENTO, PRODUCTO, USUARIO), `entidadId` ✅, `page`, `size` (20 por defecto, máximo 100), `sort` (`createdAt` o `puntaje`, por defecto `createdAt,desc`). Cualquier otro filtro responde `400`.
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body:

```json
{
  "content": [ { "id": "…", "puntaje": 5, "comentario": "Muy frescas", "autorNombre": "María", "createdAt": "…" } ],
  "page": 0, "size": 20, "totalElements": 1,
  "resumen": { "promedio": 4.6, "total": 12 }
}
```

`promedio` va con un decimal y es `null` si no hay calificaciones; `comentario` puede ser `null`.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `tipoEntidad` o `entidadId`, valor inválido, filtro desconocido, `size` mayor que 100 | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | El rol no puede ver ese destino (tabla de autorización) | — |
| 404 | `NOT_FOUND` | La entidad no existe, está eliminada o no es consultable | "No encontramos esa entidad" |

`resumen` es un campo extra del envelope de listados de spec-general §2.1 (Open Questions).

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda en SQLite la primera página y el `resumen` de cada entidad consultada y la muestra con la marca "datos guardados" si no hay red; al volver la conexión se refresca. Los comentarios moderados pueden verse hasta ese refresco.
- **Concurrencia (RNF08)**: no aplica; la lectura siempre refleja lo confirmado en la base.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Entidad sin calificaciones | `200` con promedio nulo, total 0 y lista vacía | Edge 1 |
| Calificaciones eliminadas | No cuentan en el promedio ni aparecen | Edge 2 |
| Entidad inactiva | `200` | Edge 3 |
| Entidad eliminada o inexistente | `404` | Edge 3 |
| Destino no permitido al rol | `403` | FR-003 |
| Sin red | Caché local | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: cada celda de la tabla de autorización; paginación y orden; filtros inválidos; sin calificaciones; eliminadas y editadas; entidad inactiva, eliminada e inexistente | FR-001–FR-005, Edge 1–3 |
| Consistencia | El promedio devuelto coincide con el promedio de los puntajes `ACTIVA` leídos de la tabla, con altas y eliminaciones intercaladas | SC-002 |
| Rendimiento | 10 000 calificaciones de un destino: < 2 s | SC-001 |
| Arquitectura | `comentarios` consulta `emprendimientos`, `productos`, `usuarios` y `pedidos` solo por puertos `in` | RNF13, RNF25 |
| Frontend | Jest: estrellas, lista paginada, caché sin red, vistas por rol | US1–US4 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-02-consultar-calificaciones-y-comentarios/
├── RF-02-consultar-calificaciones-y-comentarios.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/comentarios/
├── domain/
│   └── model/                    # ResumenCalificaciones
├── application/
│   ├── ports/in/                 # ConsultarCalificacionesPort
│   ├── ports/out/                # CalificacionRepositoryPort (listarActivas, resumen), DestinoCalificableLecturaPort, VisibilidadReputacionPort
│   └── usecase/                  # ConsultarCalificacionesUseCase
└── infrastructure/adapters/
    ├── in/web/                   # CalificacionController (GET /calificaciones), DTO
    └── out/persistence/          # consulta agregada y adaptadores de lectura

backend/src/main/java/co/edu/napa/emprendimientos/application/ports/in/   # ConsultarEmprendimientoReputablePort
backend/src/main/java/co/edu/napa/productos/application/ports/in/         # ConsultarProductoReputablePort
backend/src/main/java/co/edu/napa/usuarios/application/ports/in/          # ConsultarUsuarioReputablePort
backend/src/test/java/co/edu/napa/comentarios/

frontend/src/app/(cliente)/emprendimientos/[id]/calificaciones.tsx     # reputación del emprendimiento
frontend/src/app/(vendedor)/reputacion.tsx                             # panel del vendedor
frontend/src/app/(domiciliario)/reputacion/[tipo]/[id].tsx             # reputación de un emprendimiento, domiciliario o cliente
frontend/src/components/calificaciones/ResumenCalificaciones.tsx
frontend/src/components/calificaciones/ListaCalificaciones.tsx
frontend/src/services/calificaciones.ts                               # consultarCalificaciones() con caché SQLite
frontend/__tests__/calificaciones/
```

**Structure Decision**: módulo `comentarios`; una sola ruta `GET /calificaciones` con dos casos de uso: este (roles de la app) y el del administrador de RF-06. La visibilidad se decide en el caso de uso con la tabla de autorización, consultando puertos `in` de los otros módulos; el promedio y el listado salen de `CalificacionRepositoryPort` con una consulta agregada, sin guardar nada.

## Phase 1: Setup

- [ ] T001 Confirmar el paquete `comentarios`, su regla ArchUnit y los índices de destino de V13 (RF-01 T002)

## Phase 2: Foundational

- [ ] T002 Puertos `in` de lectura: `ConsultarEmprendimientoReputablePort` (estado, dueño), `ConsultarProductoReputablePort` (estado, emprendimiento y dueño) y `ConsultarUsuarioReputablePort` (rol, estado, nombre)
- [ ] T003 `ResumenCalificaciones` en `comentarios/domain/model/`
- [ ] T004 Métodos `listarActivas` y `resumen` en `CalificacionRepositoryPort` y los puertos de salida de lectura y visibilidad

**Checkpoint**: base lista.

## Phase 3: User Story 1 - El cliente consulta emprendimientos y productos (Priority: P1)

**Goal**: el cliente decide con la reputación a la vista.
**Independent Test**: consultar un emprendimiento y un producto con calificaciones y ver promedio y comentarios.
**Cubre**: FR-001, FR-005, SC-001, SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /calificaciones` del emprendimiento (resumen, comentarios, paginación y orden) en `backend/src/test/java/co/edu/napa/comentarios/ConsultarEmprendimientoIT.java`
- [ ] T011 [P] [US1] Test de integración del producto en `.../comentarios/ConsultarProductoIT.java`
- [ ] T012 [P] [US1] Test de entidad sin calificaciones (promedio nulo, total 0, lista vacía) en `.../comentarios/ConsultarSinCalificacionesIT.java`
- [ ] T013 [P] [US1] Test de calificaciones eliminadas por moderación o por el autor (fuera del promedio y de la lista) y editadas (siguen) en `.../comentarios/ConsultarModeradasIT.java`
- [ ] T014 [P] [US1] Test de consistencia: el promedio coincide con el de los puntajes `ACTIVA` tras altas y eliminaciones en `.../comentarios/PromedioConsistenteIT.java`
- [ ] T015 [P] [US1] Test de filtros faltantes, desconocidos o inválidos y `size` > 100 (`400`), y sin sesión (`401`) en `.../comentarios/ConsultarFiltrosIT.java`
- [ ] T016 [P] [US1] Test de entidad inactiva (`200`), eliminada e inexistente (`404`) en `.../comentarios/ConsultarEntidadEstadoIT.java`
- [ ] T017 [P] [US1] Test de rendimiento con 10 000 calificaciones (< 2 s) en `.../comentarios/ConsultarRendimientoIT.java`
- [ ] T030 [P] [US1] Test de `ResumenCalificaciones`, `ListaCalificaciones` y la pantalla del cliente (paginación, vacío, caché sin red) en `frontend/__tests__/calificaciones/consultar-cliente.test.tsx`

### Implementation

- [ ] T020 [US1] `ConsultarCalificacionesUseCase` y `ConsultarCalificacionesPort` (validación de filtros, existencia y estado del destino, visibilidad, consulta) en `comentarios/application/`
- [ ] T021 [US1] `GET /calificaciones` y su DTO en `CalificacionController`
- [ ] T022 [US1] Adaptadores de los puertos de lectura de emprendimientos, productos y usuarios
- [ ] T023 [US1] Consulta JPA agregada (promedio con un decimal y total) y listado paginado con orden permitido
- [ ] T031 [US1] `consultarCalificaciones()` con caché SQLite en `frontend/src/services/calificaciones.ts`
- [ ] T032 [US1] `ResumenCalificaciones` y `ListaCalificaciones` en `frontend/src/components/calificaciones/`
- [ ] T033 [US1] Pantalla `(cliente)/emprendimientos/[id]/calificaciones.tsx` y su entrada desde el detalle del emprendimiento (RF-32) y desde cada producto

## Phase 4: User Story 2 - El cliente consulta domiciliarios y clientes (Priority: P2)

**Goal**: el cliente conoce a quien le entrega o compra.
**Independent Test**: consultar un domiciliario y otro cliente con calificaciones.
**Cubre**: FR-002, US2-E1.

### Tests (antes de implementar)

- [ ] T040 [P] [US2] Test de integración: el cliente consulta USUARIO con rol DOMICILIARIO y con rol CLIENTE; con rol vendedor o administrador da `404` en `.../comentarios/ConsultarUsuarioClienteIT.java`
- [ ] T041 [P] [US2] Test de la reputación del domiciliario en las pantallas del cliente en `frontend/__tests__/calificaciones/consultar-domiciliario.test.tsx`

### Implementation

- [ ] T042 [US2] Rama USUARIO en `ConsultarCalificacionesUseCase` y `VisibilidadReputacionPort` para el cliente
- [ ] T043 [US2] Adaptador de `ConsultarUsuarioReputablePort`
- [ ] T044 [US2] Enlace a la reputación del domiciliario desde el seguimiento del domicilio del cliente (RF-26 y RF-25)

## Phase 5: User Story 3 - El vendedor consulta su reputación (Priority: P2)

**Goal**: el vendedor ve cómo lo valoran y a quién atiende.
**Independent Test**: el vendedor consulta su emprendimiento, un producto, un cliente de sus pedidos y (punto fijo) un domiciliario.
**Cubre**: FR-003, US3-E1.

### Tests (antes de implementar)

- [ ] T050 [P] [US3] Test de integración: el vendedor ve lo suyo, clientes de sus pedidos y, de punto fijo, domiciliarios de sus pedidos; emprendimiento ajeno, cliente sin pedido y domiciliario siendo ambulante dan `403` en `.../comentarios/ConsultarVendedorIT.java`
- [ ] T051 [P] [US3] Test del panel de reputación del vendedor en `frontend/__tests__/calificaciones/panel-vendedor.test.tsx`

### Implementation

- [ ] T052 [US3] Rama del vendedor en el caso de uso, con `ConsultarInteraccionEntreUsuariosPort` (RF-60)
- [ ] T053 [US3] Pantalla `frontend/src/app/(vendedor)/reputacion.tsx`

## Phase 6: User Story 4 - El domiciliario consulta reputación (Priority: P2)

**Goal**: el domiciliario decide con información.
**Independent Test**: consultar un emprendimiento, otro domiciliario y un cliente.
**Cubre**: FR-004, US4-E1.

### Tests (antes de implementar)

- [ ] T060 [P] [US4] Test de integración: el domiciliario consulta emprendimientos, domiciliarios y clientes; productos también; USUARIO vendedor `404` en `.../comentarios/ConsultarDomiciliarioIT.java`
- [ ] T061 [P] [US4] Test de la pantalla del domiciliario en `frontend/__tests__/calificaciones/reputacion-domiciliario.test.tsx`

### Implementation

- [ ] T062 [US4] Rama del domiciliario en el caso de uso
- [ ] T063 [US4] Pantalla `(domiciliario)/reputacion/[tipo]/[id].tsx` y enlaces desde la información del domicilio (RF-18)

**Checkpoint**: las cuatro historias funcionales.

## Phase N: Polish & Cross-Cutting

- [ ] T070 Revisar índices con `EXPLAIN` sobre datos del piloto
- [ ] T071 Encuesta de claridad de la reputación en el piloto (SC-003)
- [ ] T072 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2, US3, US4 → Polish.
- Dependencias entre RF: requiere RF-01 (tabla `calificacion` y datos), RF-10, RF-31/32 (detalle del emprendimiento), RF-60 (T003, pedidos en común), RF-18 y RF-26 (enlaces). RF-06 amplía este mismo endpoint con el modo administrador y el estado de moderación.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| `resumen` no está en el envelope de listados de spec-general §2.1 | Contrato | Agregarlo como campo extra y anotarlo en spec-general | Equipo |
| Spec-general no define si el promedio va redondeado | Presentación | Un decimal, media aritmética de los puntajes `ACTIVA` | Equipo |
| Edge 2: la spec no dice cómo cuentan los comentarios eliminados | Regla | Los eliminados (autor o moderación) salen de lista y promedio; los editados siguen. Concuerda con RF-06 FR-003 | Equipo |
| Edge 3: no define entidad inactiva o eliminada | Regla | Inactiva: `200`; eliminada o inexistente: `404` | Equipo |
| Privacidad: se muestra el nombre de pila del autor; la spec no lo define | Privacidad | Nombre de pila | Equipo |
| FR-002 y FR-004 permiten a cualquier cliente o domiciliario ver la reputación de cualquier cliente, sin pedido en común | Privacidad | Se sigue la spec al pie de la letra. Mejora posible: limitar a quien comparta un pedido | Equipo |
| FR-003 no dice si el vendedor ve solo los domiciliarios y clientes de sus pedidos o todos | Alcance | Solo los de sus pedidos (FR-003: "involucrados en sus pedidos") | Equipo |
| Los listados incluyen calificaciones sin comentario (`comentario` nulo) porque aportan al promedio | UX | Se incluyen | Equipo |
| El vendedor solo ve su propio emprendimiento y productos; la spec no menciona los ajenos | Alcance | Solo los suyos | Equipo |
| Los roles administrador y vendedor como `tipoEntidad=USUARIO` no son consultables | Regla | `404` | Equipo |
| Destino no permitido para el rol: `403` (en RF-60 es `404`) | Consistencia | **Resuelto**: RF-60 devuelve `404` porque no debe revelar si existe un pedido o domicilio ajeno; aquí los datos de reputación son públicos y `403` es más claro | Equipo |
| El administrador recibe `403` en este endpoint hasta que se construya RF-06 | Orden | RF-06 agrega su rama | Equipo |

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
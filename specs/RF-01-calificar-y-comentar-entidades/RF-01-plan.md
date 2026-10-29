# Implementation Plan: Calificar y comentar entidades

**Date**: 2026-10-26
**Spec**: [RF-01-calificar-y-comentar-entidades.md](./RF-01-calificar-y-comentar-entidades.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-01
**Historias de usuario**: HU-01 a HU-07
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-45, RF-48/51, RF-24, RF-37, RF-65, RF-60 (puertos T003), RF-10
**Habilita a**: RF-02, RF-06

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Con `POST /calificaciones` un usuario califica (puntaje entero de 1 a 5) y, si quiere, comenta (hasta 300 caracteres) a una de las partes de un **pedido ENTREGADO**: una calificación por llamada y exactamente un destino (emprendimiento, producto o usuario). La interacción se **deriva del pedido** (no hay listener ni evento): el caso de uso consulta a `pedidos` quiénes participaron, en qué estado está y qué productos tuvo, y rechaza lo que no encaje. Quién puede calificar a quién: el cliente, al emprendimiento, a los productos del pedido (US1, P1) y al domiciliario que entregó el domicilio (US2); el vendedor, al cliente y, si es de punto fijo, al domiciliario (US3); el domiciliario, al emprendimiento y al cliente de un domicilio que realizó (US3). El mismo autor no puede calificar dos veces el mismo destino en el mismo pedido (índice único). La app ofrece "Calificar" desde un pedido entregado y manda la calificación por el outbox.

## Estado actual del código

No existen el módulo `comentarios` ni la tabla `calificacion`. El estado `ENTREGADO` del pedido lo fijan RF-24 (domicilio), RF-37 (entrega directa) y RF-65 (retiro), ya planeados. Los puertos de lectura de participantes los define RF-60 (T003), que se amplían aquí si falta algún dato.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Califica al emprendimiento, a sus productos y al domiciliario |
| Vendedor ambulante | Califica al cliente |
| Vendedor de punto fijo | Califica al cliente y al domiciliario |
| Domiciliario | Califica al emprendimiento y al cliente |
| Administrador | No califica |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- 100 % de las calificaciones ligadas a una interacción real (SC-002): el pedido ENTREGADO y la pertenencia se verifican en el servidor, nunca con datos del cliente.
- 100 % de las duplicadas rechazadas (SC-003): índice único en la base; la validación previa solo mejora el mensaje.
- ≥ 60 % de los pedidos entregados calificados en 48 h (SC-001): es una meta de adopción, no de código (Open Questions).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El cliente califica emprendimiento y productos de un pedido entregado | `POST /calificaciones` con `tipoEntidad` EMPRENDIMIENTO o PRODUCTO | T010, T011, T020, T021 |
| FR-002 | El cliente califica al domiciliario que entregó | Destino USUARIO = domiciliario del domicilio del pedido | T040, T042 |
| FR-003 | El vendedor de punto fijo califica al domiciliario | Rol VENDEDOR_PUNTO_FIJO y domiciliario del pedido | T050, T052 |
| FR-004 | El domiciliario califica emprendimiento y cliente | Domicilio que realizó, pedido ENTREGADO | T051, T052 |
| FR-005 | Cada calificación ligada a un pedido entregado | `pedido_id` obligatorio; estado y participantes leídos de `pedidos` | T013, T020, T022 |
| FR-006 | No más de una calificación por interacción y entidad | Único (autor, pedido, destino) | T014, T023 |
| FR-007 | El vendedor (ambulante o punto fijo) califica al cliente | Destino USUARIO = cliente del pedido | T050, T052 |
| FR-008 | Puntaje 1–5; comentario opcional de hasta 300 | Bean Validation, dominio y CHECK | T012, T015 |
| SC-001 | 60 % en 48 h | Medición con datos del piloto | T060 |
| SC-002 | 100 % asociadas a interacción real | Verificación en servidor | T013, T017 |
| SC-003 | 100 % de duplicadas rechazadas | Índice único y `409 CONFLICT` | T014 |
| US1-E1 | El cliente califica el emprendimiento | `201` | T010 |
| US1-E2 | El cliente califica un producto del pedido | `201`; el producto debe estar en los ítems | T011 |
| US2-E1 | El cliente califica al domiciliario | `201` en pedido de DOMICILIO entregado | T040 |
| US3-E1 | El vendedor califica al domiciliario | `201` | T050 |
| US3-E2 | El domiciliario califica emprendimiento y cliente | `201` | T051 |
| US3-E3 | El vendedor califica al cliente | `201` | T050 |
| Edge 1 | Calificar un pedido que no se ha entregado | `409 INVALID_STATE` con `estadoActual` | T013 |
| Edge 2 | Calificar dos veces lo mismo | `409 CONFLICT` | T014 |
| Edge 3 | Calificar sin interacción | `404 NOT_FOUND` | T017 |
| Edge 4 | Puntaje fuera de escala | `400 VALIDATION_ERROR` | T015 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Rol y pertenencia al pedido validados en el caso de uso |
| RNF04 Privacidad | ☑ | La respuesta no devuelve datos de terceros |
| RNF16 Trazabilidad | ☑ | Autor, pedido, destino y fecha guardados |
| RNF23 Consistencia | ☑ | CHECK de un solo destino y único (autor, pedido, destino) |
| RNF24 Errores claros | ☑ | `400`, `404`, `409` con mensaje |
| RNF27 Integridad | ☑ | `Idempotency-Key` |
| RNF28 Conectividad | ☑ | Envío por el outbox |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Calificacion | Escribe | `puntaje` 1–5; `comentario` ≤ 300; estado inicial `ACTIVA`; exactamente un destino; único (autor, pedido, destino) |
| Pedido | Lee (puerto) | Estado `ENTREGADO`; cliente, dueño del emprendimiento, emprendimiento, ítems y domiciliario del domicilio |
| ItemPedido / Producto | Lee (puerto) | El producto debe estar en el pedido |
| Usuario | Lee (puerto) | Rol del autor y del destino |

`created_at` y `updated_at` no figuran en la entidad Calificacion de spec-general; se agregan como columnas de auditoría (Open Questions).

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Calificacion | (no existe) | `ACTIVA` | El usuario califica | Pedido `ENTREGADO`; autor y destino válidos; sin calificación previa igual | Entra al promedio del destino (RF-02) |

`ELIMINADA_MODERACION` la fija RF-06. `ELIMINADA_AUTOR` la fija RF-05 (ola 7).

## Authorization

| Autor (rol) | Puede calificar a | Regla |
|---|---|---|
| CLIENTE | EMPRENDIMIENTO del pedido; PRODUCTO de un ítem; USUARIO domiciliario | Es el cliente del pedido; el domiciliario existe solo si el pedido tuvo domicilio |
| VENDEDOR_AMBULANTE | USUARIO cliente | Es dueño del emprendimiento del pedido |
| VENDEDOR_PUNTO_FIJO | USUARIO cliente; USUARIO domiciliario | Es dueño del emprendimiento del pedido |
| DOMICILIARIO | EMPRENDIMIENTO del pedido; USUARIO cliente | Es el domiciliario del domicilio del pedido |
| ADMINISTRADOR | — | `403` |

Cualquier otra combinación responde `404` (no hay interacción) o `403` si el rol no está en la tabla.

## API Contracts

### POST /api/v1/calificaciones

- **Rol**: cliente, vendedor o domiciliario · **Path params**: ninguno · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅, `Content-Type: application/json`.
- **Body**:

```json
{ "pedidoId": "…", "tipoEntidad": "PRODUCTO", "entidadId": "…", "puntaje": 5, "comentario": "Muy frescas" }
```

`tipoEntidad`: EMPRENDIMIENTO, PRODUCTO o USUARIO. `comentario` es opcional.

- **Respuestas exitosas**: `201` con `X-Request-Id` y body `{ "id": "…", "pedidoId": "…", "tipoEntidad": "…", "entidadId": "…", "puntaje": 5, "comentario": "…", "estado": "ACTIVA", "createdAt": "…" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key`; puntaje nulo, menor que 1, mayor que 5 o no entero; comentario de más de 300; destino o tipo faltante | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol administrador | — |
| 404 | `NOT_FOUND` | El pedido no existe o no es suyo; el destino no pertenece al pedido; el rol no puede calificar ese destino | "No encontramos lo que quieres calificar" |
| 409 | `INVALID_STATE` | El pedido no está `ENTREGADO` (`details.estadoActual`) | "Solo puedes calificar pedidos entregados" |
| 409 | `CONFLICT` | Ya calificaste ese destino en este pedido | "Ya calificaste esto" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

Los listados de calificaciones son RF-02 (`GET /calificaciones`).

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El catálogo no define un evento de calificación creada |

**Eventos que consume esta feature**: ninguno; la elegibilidad se lee del estado del pedido (E07 solo habilita la pantalla; la invitación a calificar la manda RF-38 al comprador).

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la calificación se guarda en el outbox con su `Idempotency-Key` y la pantalla la muestra como enviada pendiente. Si el servidor responde `409 CONFLICT`, el outbox la descarta y la pantalla marca el destino como "ya calificado".
- **Concurrencia (RNF08)**: dos envíos simultáneos del mismo destino (dos dispositivos): el índice único deja pasar uno y el otro recibe `409 CONFLICT`.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`; el reenvío devuelve la misma respuesta con `Idempotent-Replayed: true`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido no entregado | `409 INVALID_STATE` | Edge 1 |
| Calificación repetida | `409 CONFLICT` | Edge 2, FR-006 |
| Sin interacción o destino ajeno al pedido | `404` | Edge 3, FR-005 |
| Puntaje inválido o comentario largo | `400` | Edge 4, FR-008 |
| Sin conexión | Outbox | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Calificacion.crear` (puntaje, comentario, estado inicial, un solo destino) | FR-008 |
| Integración | Cada combinación de la tabla de autorización; pedido no entregado en cada estado; destino fuera del pedido; duplicada; carrera de dos envíos; idempotencia | FR-001–FR-008, Edge 1–4 |
| Migración | V13 aplica sobre V12: CHECKs y único | FR-006, FR-008 |
| Arquitectura | `comentarios` usa `pedidos`, `domicilios` y `cuentas` solo por sus puertos `in` | RNF13, RNF25 |
| Frontend | Jest: estrellas, formulario, destinos según el rol, outbox, `409` | US1–US3 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-01-calificar-y-comentar-entidades/
├── RF-01-calificar-y-comentar-entidades.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/comentarios/
├── domain/
│   ├── model/                    # Calificacion, EstadoCalificacion, TipoEntidadCalificable
│   └── exception/                # CalificacionInvalidaException
├── application/
│   ├── ports/in/                 # CalificarPort
│   ├── ports/out/                # CalificacionRepositoryPort, PedidoCalificablePort
│   └── usecase/                  # CalificarUseCase
└── infrastructure/adapters/
    ├── in/web/                   # CalificacionController (POST /calificaciones), DTO
    └── out/persistence/          # CalificacionJpaEntity, adaptadores de repositorio y de PedidoCalificablePort

backend/src/main/java/co/edu/napa/pedidos/application/ports/in/   # ConsultarParticipantesPedidoPort (ampliado: estado, domiciliario, ítems)
backend/src/main/resources/db/migration/                           # V13__calificacion.sql
backend/src/test/java/co/edu/napa/comentarios/

frontend/src/app/(comun)/calificar/[pedidoId].tsx          # ruta Expo Router: destinos calificables del pedido según el rol
frontend/src/components/calificaciones/SelectorEstrellas.tsx
frontend/src/components/calificaciones/FormularioCalificacion.tsx
frontend/src/services/calificaciones.ts                    # calificar() por el outbox
frontend/__tests__/calificaciones/
```

**Structure Decision**: módulo `comentarios` (dueño de `Calificacion` y, desde RF-06, de `ModeracionComentario`). La elegibilidad la decide `CalificarUseCase` con una consulta al puerto de `pedidos` (cliente, dueño, emprendimiento, ítems, estado y domiciliario); no se guarda ninguna copia del estado. La tabla de destinos por rol vive en el caso de uso (no hay `domain/service`). La pantalla va en `(comun)` porque la usan los tres roles.

## Phase 1: Setup

- [ ] T001 Crear el paquete `co.edu.napa.comentarios` y su regla ArchUnit en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`

## Phase 2: Foundational

- [ ] T002 Migración `V13__calificacion.sql` en `backend/src/main/resources/db/migration/`: tabla `calificacion` (id, autor_id, pedido_id, emprendimiento_id nulo, producto_id nulo, usuario_id nulo, puntaje, comentario nulo, estado, created_at, updated_at); CHECK de exactamente un destino, de puntaje 1–5 y de comentario ≤ 300; único `(autor_id, pedido_id, COALESCE(emprendimiento_id, cero), COALESCE(producto_id, cero), COALESCE(usuario_id, cero))`; índices `(emprendimiento_id, estado, created_at)`, `(producto_id, estado, created_at)` y `(usuario_id, estado, created_at)` que usará RF-02
- [ ] T003 Ampliar `ConsultarParticipantesPedidoPort` (RF-60 T003) con estado, productos de los ítems y domiciliario del domicilio, en `pedidos/application/ports/in/`
- [ ] T004 Dominio `Calificacion`, `EstadoCalificacion` y `TipoEntidadCalificable` en `comentarios/domain/model/`
- [ ] T005 Puertos `CalificacionRepositoryPort` y `PedidoCalificablePort` y su adaptador sobre el puerto `in` de `pedidos`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - El cliente califica el emprendimiento y los productos (Priority: P1)

**Goal**: el cliente deja puntaje y comentario sobre lo que recibió.
**Independent Test**: calificar un emprendimiento y un producto de un pedido entregado y ver `201`.
**Cubre**: FR-001, FR-005, FR-006, FR-008, SC-002, SC-003, US1-E1–E2, Edge 1–4.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración: el cliente califica el emprendimiento de un pedido `ENTREGADO` en `backend/src/test/java/co/edu/napa/comentarios/CalificarEmprendimientoIT.java`
- [ ] T011 [P] [US1] Test de integración: el cliente califica un producto del pedido; un producto que no está en el pedido da `404` en `.../comentarios/CalificarProductoIT.java`
- [ ] T012 [P] [US1] Test unitario de `Calificacion.crear` (puntaje 1–5, comentario ≤ 300, estado `ACTIVA`) en `.../comentarios/CalificacionTest.java`
- [ ] T013 [P] [US1] Test de integración: pedido en cada estado distinto de `ENTREGADO` da `409 INVALID_STATE` con `estadoActual`; cada ruta de entrega (domicilio, directa, retiro) habilita al llegar a `ENTREGADO` en `.../comentarios/CalificarEstadoPedidoIT.java`
- [ ] T014 [P] [US1] Test de integración: calificación repetida (`409 CONFLICT`) y dos envíos simultáneos con claves distintas (uno `201` y otro `409`) en `.../comentarios/CalificarDuplicadaIT.java`
- [ ] T015 [P] [US1] Test parametrizado de puntaje nulo, 0, 6 y decimal, comentario de 301 caracteres, `tipoEntidad` o `entidadId` faltante (`400`) en `.../comentarios/CalificarValidacionIT.java`
- [ ] T016 [P] [US1] Test de repetición con la misma `Idempotency-Key`, clave reutilizada con otro cuerpo (`422`) y falta de clave (`400`) en `.../comentarios/CalificarIdempotenciaIT.java`
- [ ] T017 [P] [US1] Test de integración: pedido ajeno o inexistente (`404`), sin interacción (`404`) y administrador (`403`) en `.../comentarios/CalificarAutorizacionIT.java`
- [ ] T018 [P] [US1] Test de migración V13 (CHECKs y único con las tres columnas de destino) en `.../comentarios/MigracionV13Test.java`
- [ ] T030 [P] [US1] Test del selector de estrellas, del formulario y de la pantalla del cliente (destinos, `409`, outbox sin red) en `frontend/__tests__/calificaciones/calificar-cliente.test.tsx`

### Implementation

- [ ] T020 [US1] `CalificarUseCase` y `CalificarPort`: valida estado, participantes, destino y rol, y crea la calificación, en `comentarios/application/`
- [ ] T021 [US1] `POST /calificaciones` y su DTO en `CalificacionController`
- [ ] T022 [US1] Adaptador de `PedidoCalificablePort` sobre `ConsultarParticipantesPedidoPort`
- [ ] T023 [US1] Adaptador de persistencia que traduce la violación del índice único a `CONFLICT`
- [ ] T031 [US1] Servicio `calificar()` por el outbox en `frontend/src/services/calificaciones.ts`
- [ ] T032 [US1] `SelectorEstrellas` y `FormularioCalificacion` en `frontend/src/components/calificaciones/`
- [ ] T033 [US1] Pantalla `frontend/src/app/(comun)/calificar/[pedidoId].tsx` con los destinos del cliente (emprendimiento y productos)
- [ ] T034 [US1] Botón "Calificar" en el detalle de un pedido entregado del cliente (RF-48 y RF-51)

## Phase 4: User Story 2 - El cliente califica al domiciliario (Priority: P2)

**Goal**: el cliente valora la entrega.
**Independent Test**: calificar al domiciliario de un pedido de domicilio entregado.
**Cubre**: FR-002, US2-E1.

### Tests (antes de implementar)

- [ ] T040 [P] [US2] Test de integración: el cliente califica al domiciliario; en un pedido sin domicilio da `404` en `.../comentarios/CalificarDomiciliarioIT.java`
- [ ] T041 [P] [US2] Test de la pantalla: aparece el destino "domiciliario" solo si el pedido fue de domicilio en `frontend/__tests__/calificaciones/calificar-domiciliario.test.tsx`

### Implementation

- [ ] T042 [US2] Rama USUARIO-domiciliario del cliente en `CalificarUseCase`
- [ ] T043 [US2] Destino "domiciliario" en la pantalla de calificar

## Phase 5: User Story 3 - Vendedor y domiciliario califican (Priority: P2)

**Goal**: las otras partes también valoran.
**Independent Test**: el vendedor califica al cliente y al domiciliario; el domiciliario, al emprendimiento y al cliente.
**Cubre**: FR-003, FR-004, FR-007, US3-E1–E3.

### Tests (antes de implementar)

- [ ] T050 [P] [US3] Test de integración: vendedor ambulante y de punto fijo califican al cliente; solo el de punto fijo califica al domiciliario (ambulante `404`) en `.../comentarios/CalificarVendedorIT.java`
- [ ] T051 [P] [US3] Test de integración: el domiciliario califica emprendimiento y cliente de un domicilio que realizó; de otro domicilio da `404` en `.../comentarios/CalificarDomiciliarioRolIT.java`
- [ ] T053 [P] [US3] Test de las pantallas del vendedor y del domiciliario en `frontend/__tests__/calificaciones/calificar-vendedor-domiciliario.test.tsx`

### Implementation

- [ ] T052 [US3] Filas de vendedor y domiciliario de la tabla de destinos en `CalificarUseCase`
- [ ] T054 [US3] Botón "Calificar" en el detalle de pedido del vendedor y en el domicilio entregado del domiciliario (`(vendedor)` y `(domiciliario)`)

**Checkpoint**: las tres historias funcionales.

## Phase N: Polish & Cross-Cutting

- [ ] T060 Medir el porcentaje de pedidos calificados en 48 h con el piloto (SC-001)
- [ ] T061 Revisar permisos y privacidad (RNF03, RNF04)
- [ ] T062 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → US3 → Polish.
- Dependencias entre RF: requiere RF-45, RF-48/51 (donde va el botón) y los RF que llevan el pedido a `ENTREGADO` (RF-24, RF-37, RF-65); reutiliza los puertos de RF-60 (T003). Habilita a RF-02 (consulta) y RF-06 (moderación).
- Los tipos de usuario de la tabla de autorización dependen de que RF-14 (domicilio) esté construido para US2 y US3.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Spec-general solo define 3 relaciones de destino y no dice el nombre de los campos del body | Contrato | `tipoEntidad`/`entidadId`, iguales a los filtros de RF-02 | Equipo |
| `Calificacion` no tiene `created_at` en spec-general y RF-02 necesita ordenar por fecha | Modelo | Agregar `created_at` y `updated_at` como auditoría | Equipo |
| Una calificación por llamada; el cliente puede querer calificar emprendimiento y 3 productos de una vez | UX | Una por llamada, cada una con su `Idempotency-Key`; la app envía varias por el outbox | Equipo |
| `409 CONFLICT` por duplicado: la app no sabe qué destinos ya calificó (en otro dispositivo o sesión) | UX | Marcar "ya calificado" al recibir el `409`. Mejora posible: que el detalle del pedido (RF-51) devuelva qué destinos ya calificó el usuario | Equipo |
| SC-001 (60 % en 48 h) necesita una invitación a calificar | Meta | **Resuelto**: RF-38 invita al comprador con el aviso de entrega (E07 fuera de domicilio; E12 en domicilio). Vendedor y domiciliario no reciben invitación | Equipo |
| spec-general §2.1.1 pide `Location` en todo `201`, pero la calificación no tiene `GET` propio | Contrato | **Resuelto**: no se devuelve `Location` (la nota de §2.1.1 es "cuando el recurso tiene GET propio") | Equipo |
| RF-04 (editar) y RF-05 (eliminar), ambos P2 de la ola 7, permiten al autor cambiar su calificación; RF-05 fija `ELIMINADA_AUTOR` | Alcance | Fuera de este plan. El único (autor, pedido, destino) incluye todos los estados, así que tras eliminar no podría volver a calificar; RF-05 debe confirmarlo | Equipo |
| Un pedido de DOMICILIO que se cancela después de ENTREGADO, o una cuenta eliminada del destino | Datos | Se puede calificar mientras el pedido esté `ENTREGADO`, aunque el destino esté inactivo | Equipo |
| FR-003 limita calificar al domiciliario al vendedor de punto fijo; el ambulante no tiene domicilios | Regla | Rol VENDEDOR_PUNTO_FIJO exigido | Equipo |
| Spec-general no tiene `ALREADY_RATED`; se usa el genérico `CONFLICT` | Catálogo | `CONFLICT` | Equipo |
| US2 y US3 son P2 pero van en este plan | Orden | Se construyen después de US1 en la misma ola, o se aplazan | Equipo |

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
# Implementation Plan: Publicación de domicilio

**Date**: 2026-10-25
**Spec**: [RF-14-publicacion-de-domicilio.md](./RF-14-publicacion-de-domicilio.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md) (E08)
**Requerimiento funcional**: RF-14
**Historias de usuario**: HU-31
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-53 (pedido `ACEPTADO` y tabla `pago`), RF-42 (pago confirmado), RF-45 (tarifa, ubicación y punto fijo), RF-36 (`VentanaReserva`), RF-51 (donde vive el botón), RF-10
**Habilita a**: RF-16, RF-18, RF-19, RF-22, RF-23, RF-24, RF-25, RF-26

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor de punto fijo publica un domicilio para un pedido propio de modalidad `DOMICILIO`, `ACEPTADO` y con el pago confirmado (RF-42), con `POST /pedidos/{id}/domicilio`. Se crea el `Domicilio` en `DISPONIBLE`: punto A = copia de la ubicación del punto fijo, punto B = ubicación de entrega del pedido (no se copia), tarifa = `Pedido.tarifa_domicilio`, receptor = el cliente del pedido. El primer evento de trazabilidad (`DISPONIBLE`) se inserta en la misma transacción y se emite E08 para avisar a los domiciliarios activos. Un pedido solo puede tener un domicilio activo (índice único parcial) y el domicilio no se edita nunca. En una reserva solo se publica desde 15 minutos antes de la fecha acordada.

Este plan además crea la base de todo el ciclo: la migración V10 (`domicilio`, `evento_trazabilidad`, `domicilio_rechazo` y la clave foránea pendiente de `pago.domicilio_id`), el módulo `domicilios` y el agregado `Domicilio`.

## Estado actual del código

No existe el módulo `domicilios` ni sus tablas. La tabla `pago` (RF-53, V9) tiene `domicilio_id` sin clave foránea; la agrega este plan. Del pedido se reutilizan `Pedido.tarifa_domicilio`, la `Ubicacion` de entrega (RF-45), `VentanaReserva` y `reserva.margen_minutos` (RF-36), `ConsultarPagoPedidoPort` (RF-51 y RF-42) y `ConsultarEmprendimientoParaPedidoPort` (RF-45, T004: punto fijo y dueño).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Es el receptor; no interviene |
| Vendedor (punto fijo) | Publica el domicilio |
| Domiciliario | Recibe el aviso (E08) y lo ve en su lista (RF-16) |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Domicilio visible para los domiciliarios en < 5 s (SC-001): la fila se confirma en la transacción y RF-16 consulta la base, sin caché.
- 0 % de publicaciones sin pago confirmado, duplicadas o editables (SC-002, SC-003, SC-004): reglas de dominio, índice único parcial y ausencia de endpoints de edición.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Publicar solo pedido propio, de domicilio, aceptado y con pago confirmado | `PublicarDomicilioUseCase` valida dueño (`404`), modalidad (`422 BUSINESS_RULE`), estado (`409 INVALID_STATE`) y pago por `ConsultarPagoPedidoPort` (`409 PAYMENT_NOT_CONFIRMED`) | T010, T011, T012, T015 |
| FR-002 | Punto A = copia del punto fijo; punto B = ubicación de entrega; receptor = cliente | `Ubicacion` nueva copiada del emprendimiento (`ubicacion_origen_id`); el punto B se lee del pedido y nunca se copia | T013, T016 |
| FR-003 | La tarifa publicada es la del pedido | El domicilio no guarda tarifa; se lee de `Pedido.tarifa_domicilio` (RF-45) | T013 |
| FR-004 | Crear `DISPONIBLE`, ponerlo a disposición y notificar a todos los domiciliarios activos | Fila `DISPONIBLE`, evento de trazabilidad inicial y E08 por `EventPublisherPort` en la misma transacción (el listener está en el plan de RF-38) | T010, T017, T018 |
| FR-005 | Un solo domicilio activo por pedido | Índice único parcial `(pedido_id) WHERE estado <> 'CANCELADO'` y `409 ALREADY_PUBLISHED` | T014 |
| FR-006 | Un domicilio publicado no se edita | No existen `PUT`/`PATCH` sobre `/domicilios/{id}`: `405 METHOD_NOT_ALLOWED`; dominio sin métodos de edición | T019 |
| FR-007 | No publicar sin pago confirmado, indicando el motivo | `409 PAYMENT_NOT_CONFIRMED` "Primero debes confirmar el pago del pedido" | T012 |
| FR-008 | Reserva: solo desde 15 minutos antes | `reservaDisponibleDesde` del pedido contra el `Clock`; `409 RESERVATION_NOT_DUE` con `details.disponibleDesde` | T020 |
| SC-001 | < 5 s en la lista | Misma transacción; RF-16 consulta la tabla | T040 |
| SC-002 | 100 % de rechazos de pedidos no elegibles | Pruebas parametrizadas de modalidad y estado | T011 |
| SC-003 | 0 % editables | Prueba de `405` y de que no existe API de edición | T019 |
| SC-004 | 0 % sin pago confirmado | Prueba de integración | T012 |
| US1-E1 | Publicación de un pedido aceptado y pagado | `201 Created` + `Location` + `DISPONIBLE` + E08 | T010 |
| US1-E2 | Pedido que no es de domicilio o no está aceptado | `422 BUSINESS_RULE` / `409 INVALID_STATE` | T011 |
| US1-E3 | Sin pago confirmado | `409 PAYMENT_NOT_CONFIRMED` | T012 |
| US1-E4 | Publicar dos veces | `409 ALREADY_PUBLISHED` | T014 |
| US1-E5 | Domicilio publicado no editable | `405` | T019 |
| US1-E6 | Reserva publicada antes de tiempo | `409 RESERVATION_NOT_DUE` | T020 |
| Edge 1 | Domicilio cancelado por el administrador antes de ser tomado | El índice parcial excluye `CANCELADO`: el pedido podría volver a publicarse solo si sigue `ACEPTADO` (RF-13 pasa el pedido a `CANCELADO`, así que no) | T014 |
| Edge 2 | Ubicación incompleta o fuera de zona | La ubicación ya se valida al crear el pedido (RF-45); no hay "zona atendida" definida (Open Questions) | — |
| Edge 3 | Nadie toma el domicilio | Sin vencimiento ni aviso adicional (foundations) | — |
| Edge 4 | Publicado antes de tener el pedido preparado | El vendedor decide cuándo publicar; la spec no pide un estado de preparación | — |
| Edge 5 | Pago que nunca se hace | No se puede publicar; sin vencimiento (foundations) | T012 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Rol y pertenencia validados en el caso de uso |
| RNF04 Privacidad | ☑ | E08 sin datos personales; el punto B solo se expone a domiciliarios por RF-16 y RF-18 |
| RNF08 Concurrencia | ☑ | Índice único parcial como garantía final ante dos publicaciones simultáneas |
| RNF15 Fiabilidad | ☑ | Domicilio, evento de trazabilidad y E08 en una transacción |
| RNF16 Trazabilidad | ☑ | `evento_trazabilidad` desde el primer estado |
| RNF22 Geografía | ☑ | Copia de `Ubicacion` con `geography(Point,4326)` |
| RNF23 Consistencia | ☑ | Reglas de pedido, pago y domicilio verificadas por dominio |
| RNF24 Errores claros | ☑ | `PAYMENT_NOT_CONFIRMED`, `ALREADY_PUBLISHED`, `RESERVATION_NOT_DUE` |
| RNF27 Integridad | ☑ | `Idempotency-Key` y único parcial |
| RNF28 Conectividad | ☑ | La publicación exige conexión (no va por el outbox) para ver el resultado al instante |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Escribe | `DISPONIBLE`; un activo por pedido (`estado <> 'CANCELADO'`); `pedido` y punto A inmutables |
| Ubicacion | Escribe (copia del punto fijo) | Una fila propia por domicilio (punto A) |
| EventoTrazabilidad | Escribe | Inmutable; sin `updated_at`; con el usuario autor |
| DomicilioRechazo | Crea la tabla | La puebla RF-17 (P2); la filtra RF-16 |
| Pedido | Lee | `DOMICILIO`, `ACEPTADO`, dueño, tarifa, ubicación de entrega, comprador, reserva; no cambia de estado |
| Pago | Lee (vía puerto) | `CONFIRMADO`; añade la clave foránea `pago.domicilio_id` |
| Emprendimiento | Lee (vía puerto) | Punto fijo y dueño |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Domicilio | (no existe) | `DISPONIBLE` | Vendedor publica | Pedido propio, `DOMICILIO`, `ACEPTADO`, pago `CONFIRMADO`, sin domicilio activo, reserva en ventana | Evento de trazabilidad `DISPONIBLE`; E08 |

**Transiciones inválidas**: pedido en otro estado → `409 INVALID_STATE` con `details.estadoActual`; modalidad distinta → `422 BUSINESS_RULE`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /pedidos/{id}/domicilio` | VENDEDOR_PUNTO_FIJO | Pedido de su emprendimiento; si no, `404` |

## API Contracts

### POST /api/v1/pedidos/{id}/domicilio

- **Rol**: vendedor de punto fijo · **Path params**: `id` (UUID del pedido) · **Query params**: ninguno · **Body**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅.
- **Respuestas exitosas**: `201` con `Location: /api/v1/domicilios/{id}`, `X-Request-Id` y body:

```json
{
  "id": "…", "pedidoId": "…", "estado": "DISPONIBLE", "ganancia": 4500, "createdAt": "…",
  "origen": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Carrera 5 # 10-20", "referencia": null },
  "destino": { "latitud": 11.2501, "longitud": -74.2002, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" }
}
```

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` | — |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor de punto fijo | — |
| 404 | `NOT_FOUND` | Pedido inexistente o de otro emprendimiento | "El pedido no está disponible" |
| 409 | `INVALID_STATE` | El pedido no está `ACEPTADO` | `details.estadoActual` |
| 409 | `PAYMENT_NOT_CONFIRMED` | Pago del pedido sin confirmar | "Primero debes confirmar el pago del pedido" |
| 409 | `ALREADY_PUBLISHED` | El pedido ya tiene un domicilio activo | "Este pedido ya tiene un domicilio activo" |
| 409 | `RESERVATION_NOT_DUE` | Reserva con más de 15 minutos de anticipación | `details.disponibleDesde` |
| 422 | `BUSINESS_RULE` | El pedido no es de domicilio | "Solo se publican pedidos de domicilio aceptados y pagados" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

`PUT`, `PATCH` y `DELETE` sobre `/domicilios/{id}` responden `405 METHOD_NOT_ALLOWED`.

### Puertos `in` (no son endpoints)

- `pedidos`: `ConsultarPedidoParaDomicilioPort.consultar(pedidoId)` devuelve dueño, comprador, modalidad, estado, `tarifaDomicilio`, `Ubicacion` de entrega y `reservaDisponibleDesde` (nulo si no es reserva; lo calcula `VentanaReserva`).
- `domicilios`: `ConsultarDomicilioDePedidoPort.porPedido(pedidoId)` devuelve `{id, estado}` del domicilio activo o `null`; completa el bloque `domicilio` de RF-51 y RF-48.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E08 `DomicilioPublicadoEvent` (`domicilios.domicilio-publicado`) | `PublicarDomicilioUseCase` | Al crear el domicilio, en la misma transacción. Payload: `domicilioId`, `pedidoId`, `vendedorId`, `tarifa` |

**Eventos que consume esta feature**: ninguno. El aviso a los domiciliarios activos es un listener del plan de RF-38.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: publicar requiere conexión; sin ella la app indica que no se puede publicar y conserva el pedido. No se encola en el outbox porque el resultado condiciona lo que el vendedor ve al instante (estado, domicilio activo).
- **Concurrencia (RNF08)**: dos publicaciones simultáneas del mismo pedido: una inserta y la otra viola el índice único parcial y recibe `ALREADY_PUBLISHED`. Un pedido cancelado o rechazado entre la lectura y la inserción no se detecta con candado (Open Questions).
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`; el índice único parcial es la garantía final.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido de otra modalidad o no aceptado | `422` / `409 INVALID_STATE` | US1-E2 |
| Pago sin confirmar | `409 PAYMENT_NOT_CONFIRMED` | US1-E3 |
| Publicación duplicada | `409 ALREADY_PUBLISHED` | US1-E4 |
| Intento de editar | `405` | US1-E5 |
| Reserva anticipada | `409 RESERVATION_NOT_DUE` | US1-E6 |
| Pedido ajeno | `404` | RNF03 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Domicilio.publicar` (estado inicial, evento de trazabilidad) | FR-004 |
| Integración | Endpoint con Testcontainers y PostGIS: publicación completa (A copiado, B del pedido, ganancia = tarifa, evento de trazabilidad, E08), modalidad y estados no válidos, pago sin confirmar, duplicado, concurrencia, reserva en y fuera de la ventana, ajeno, rol equivocado, `405` | US1-E1–E6, FR-001–FR-008 |
| Migración | V10 aplica sobre V9: índice parcial, claves foráneas y la de `pago.domicilio_id` | FR-005 |
| Arquitectura | `domicilios` usa otros módulos solo por `ports/in` y `domain/event`; ciclo `pedidos` ↔ `domicilios` limitado a los puertos de este plan | RNF13, RNF25 |
| Frontend | Jest: botón visible solo en domicilio aceptado y pagado, errores por código, mensaje de reserva con la hora | US1-E3, US1-E6 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-14-publicacion-de-domicilio/
├── RF-14-publicacion-de-domicilio.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── domain/
│   ├── model/                    # Domicilio, EstadoDomicilio, EventoTrazabilidad
│   ├── event/                    # DomicilioPublicadoEvent (E08)
│   └── exception/                # DomicilioYaPublicadoException, PagoNoConfirmadoException
├── application/
│   ├── ports/in/                 # PublicarDomicilioPort, ConsultarDomicilioDePedidoPort
│   ├── ports/out/                # DomicilioRepositoryPort, EventoTrazabilidadRepositoryPort, PedidoParaDomicilioPort,
│   │                             #   PagoDelPedidoPort, PuntoFijoPort, UbicacionRepositoryPort (shared), EventPublisherPort (shared)
│   └── usecase/                  # PublicarDomicilioUseCase, ConsultarDomicilioDePedidoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (POST /pedidos/{id}/domicilio), DTO
    └── out/persistence/          # DomicilioJpaEntity, EventoTrazabilidadJpaEntity, adaptadores a puertos `in` de pedidos, pagos y emprendimientos
backend/src/main/java/co/edu/napa/pedidos/application/ports/in/   # ConsultarPedidoParaDomicilioPort
backend/src/main/resources/db/migration/                           # V10__domicilio.sql
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/components/domicilios/AccionPublicarDomicilio.tsx   # botón en el detalle del vendedor (RF-51)
frontend/src/services/domicilios.ts                              # publicarDomicilio() (con conexión)
frontend/src/domain/errores-pedido.ts                            # mensajes por código (PAYMENT_NOT_CONFIRMED, ALREADY_PUBLISHED, RESERVATION_NOT_DUE)
frontend/__tests__/domicilios/publicar.test.tsx
```

**Structure Decision**: módulo `domicilios` (dueño de `Domicilio`, `EventoTrazabilidad` y `DomicilioRechazo`). Usa `pedidos`, `pagos` y `emprendimientos` solo por sus puertos `in`. `pedidos` usa `domicilios` solo por `ConsultarDomicilioDePedidoPort` (para el bloque `domicilio` del detalle y el estado del pedido), lo que crea una dependencia en ambos sentidos entre los dos módulos; es inevitable porque el domicilio nace de un pedido y el detalle del pedido expone su domicilio (Open Questions). El bloqueo siempre va en el orden domicilio → pedido → pago.

## Phase 1: Setup

- [ ] T001 Crear el paquete `co.edu.napa.domicilios` y su regla ArchUnit (incluida la excepción del ciclo con `pedidos`) en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`; confirmar `VentanaReserva` (RF-36), `ConsultarPagoPedidoPort` (RF-42) y `ConsultarEmprendimientoParaPedidoPort` (RF-45, T004)

## Phase 2: Foundational

- [ ] T002 Migración `V10__domicilio.sql` en `backend/src/main/resources/db/migration/`: `domicilio` (id, pedido_id, estado, codigo_confirmacion nulo, motivo_cancelacion nulo, ubicacion_origen_id, domiciliario_id nulo, version, created_at, updated_at; índice único parcial `(pedido_id) WHERE estado <> 'CANCELADO'`), `evento_trazabilidad` (id, domicilio_id, estado, ocurrido_at, usuario_id; sin `updated_at`; índice `(domicilio_id, ocurrido_at)`), `domicilio_rechazo` (PK `(domiciliario_id, domicilio_id)`, created_at) y la FK `pago.domicilio_id → domicilio.id` pendiente de RF-53
- [ ] T003 Dominio `Domicilio`, `EstadoDomicilio` y `EventoTrazabilidad` en `domicilios/domain/model/` (sin métodos de edición)
- [ ] T004 Persistencia: entidades JPA y `DomicilioRepositoryPort` (guardar, buscar, `transicionarAtomico(id, desde, hacia)`) y `EventoTrazabilidadRepositoryPort`
- [ ] T005 `ConsultarPedidoParaDomicilioPort` en `pedidos/application/ports/in/` (con `reservaDisponibleDesde` calculado por `VentanaReserva`) y su adaptador `PedidoParaDomicilioPort` en `domicilios`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Vendedor pone un pedido a disposición de los domiciliarios (Priority: P1)

**Goal**: el domicilio queda disponible con origen, destino y tarifa correctos, una sola vez.
**Independent Test**: aceptar un pedido de domicilio, confirmar su pago (RF-42), publicarlo y verlo en la lista de un domiciliario.
**Cubre**: FR-001–FR-008, SC-001–SC-004, US1-E1–E6, Edge 1, 5.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /pedidos/{id}/domicilio` (201, `Location`, `DISPONIBLE`, A copiado del punto fijo, B del pedido, ganancia = tarifa, evento de trazabilidad, E08 en el outbox) en `backend/src/test/java/co/edu/napa/domicilios/PublicarDomicilioIT.java`
- [ ] T011 [P] [US1] Test parametrizado de pedido de otra modalidad (`422`) y en `PENDIENTE`, `RECHAZADO`, `CANCELADO`, `ENTREGADO` (`409 INVALID_STATE`) en `.../domicilios/PublicarDomicilioPedidoNoElegibleIT.java`
- [ ] T012 [P] [US1] Test de pago sin confirmar (`PAYMENT_NOT_CONFIRMED`) y de pago confirmado después en `.../domicilios/PublicarDomicilioPagoIT.java`
- [ ] T013 [P] [US1] Test unitario de `Domicilio.publicar` y de que el punto A es una copia (cambiar el punto fijo después no altera el domicilio) en `backend/src/test/java/co/edu/napa/domicilios/DomicilioTest.java`
- [ ] T014 [P] [US1] Test de duplicado (`ALREADY_PUBLISHED`), de dos publicaciones simultáneas y de republicación tras un domicilio `CANCELADO` en `.../domicilios/PublicarDomicilioDuplicadoIT.java`
- [ ] T019 [P] [US1] Test de `PUT`/`PATCH`/`DELETE` sobre `/domicilios/{id}` (`405`) en `.../domicilios/DomicilioNoEditableIT.java`
- [ ] T020 [P] [US1] Test de reserva en y fuera de la ventana (`RESERVATION_NOT_DUE` con `disponibleDesde`) en `.../domicilios/PublicarDomicilioReservaIT.java`
- [ ] T021 [P] [US1] Test de pedido ajeno (`404`), rol equivocado (`403`), falta de `Idempotency-Key` (`400`) y repetición con la misma clave en `.../domicilios/PublicarDomicilioAutorizacionIT.java`
- [ ] T022 [P] [US1] Test de `ConsultarDomicilioDePedidoPort` (devuelve el activo o `null`) y de la migración V10 en `.../domicilios/ConsultarDomicilioDePedidoIT.java`
- [ ] T030 [P] [US1] Test de `AccionPublicarDomicilio` (visibilidad, errores por código, mensaje con la hora, sin conexión) en `frontend/__tests__/domicilios/publicar.test.tsx`

### Implementation

- [ ] T015 [US1] `PublicarDomicilioUseCase` (dueño, modalidad, estado, pago, ventana de reserva) y `PublicarDomicilioPort` en `domicilios/application/`
- [ ] T016 [US1] Copia de la `Ubicacion` del punto fijo (punto A) por `UbicacionRepositoryPort` y `PuntoFijoPort`
- [ ] T017 [US1] Inserción del evento de trazabilidad `DISPONIBLE` en la misma transacción
- [ ] T018 [US1] `DomicilioPublicadoEvent` (E08) y publicación por `EventPublisherPort`
- [ ] T023 [US1] `POST /pedidos/{id}/domicilio` en `DomicilioController` y respuestas `405` para métodos de edición
- [ ] T024 [US1] `ConsultarDomicilioDePedidoUseCase` y su integración en el bloque `domicilio` de RF-51 y RF-48 (`pedidos/application/usecase/`)
- [ ] T031 [US1] Servicio `publicarDomicilio()` en `frontend/src/services/domicilios.ts` y mensajes por código en `frontend/src/domain/errores-pedido.ts`
- [ ] T032 [US1] `AccionPublicarDomicilio` en `frontend/src/components/domicilios/` e integración en `frontend/src/app/(vendedor)/pedidos/[id].tsx` (visible en domicilio `ACEPTADO` con pago confirmado y sin domicilio activo)

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir que el domicilio aparece en la lista en < 5 s (SC-001)
- [ ] T041 Revisar permisos y que E08 no lleve datos personales (RNF03, RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-53, RF-42, RF-45 y RF-36 (ventana de reserva); habilita RF-16, RF-18, RF-19, RF-22, RF-23, RF-24, RF-25 y RF-26; completa el bloque `domicilio` de RF-51 y RF-48.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| El domicilio nace de un pedido y el detalle del pedido expone su domicilio: `pedidos` y `domicilios` dependen uno del otro por puertos `in` | Estructura (RNF13) | Aceptar el ciclo limitado a `ConsultarPedidoParaDomicilioPort`, `TransicionarPedidoPort` y `ConsultarDomicilioDePedidoPort`, con regla ArchUnit | Equipo |
| `PAYMENT_NOT_CONFIRMED` y `ALREADY_PUBLISHED` ya están en spec-general §2.2.1 como "(ya listado)" | Catálogo | Usar los códigos tal cual | Equipo |
| Edge 2: "zona atendida" no está definida en ninguna spec | Regla | No se valida; la ubicación se validó al hacer el pedido | Equipo |
| Edge 3: nadie toma el domicilio | Regla | Sin vencimiento ni aviso adicional (foundations) | Equipo |
| La publicación no bloquea el pedido: un pedido cancelado entre la lectura y la inserción podría publicarse (RF-13 es P2) | Concurrencia | Se asume el riesgo hasta RF-13; el único parcial evita duplicados | Equipo |
| `domicilio_rechazo` se crea aquí pero lo puebla RF-17 (P2, no está en el catálogo de rutas) | Alcance | Crear la tabla ya; el endpoint de rechazo queda fuera de la ola | Equipo |
| El estado "preparado" del pedido no existe (Edge 4) | Alcance | No se agrega | Equipo |

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
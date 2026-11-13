# Implementation Plan: Código de confirmación del cliente

**Date**: 2026-10-25
**Spec**: [RF-25-codigo-de-confirmacion-del-cliente.md](./RF-25-codigo-de-confirmacion-del-cliente.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-25
**Historias de usuario**: HU-50
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-19 (código generado), RF-14, RF-26 (pantalla del domicilio del cliente), RF-10
**Habilita a**: RF-24 (el domiciliario lo ingresa), RF-60

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El cliente consulta el código de confirmación de su domicilio con `GET /domicilios/{id}/codigo-confirmacion`, desde que un domiciliario lo acepta (`ASIGNADO`, `RECOGIDO` o `EN_CAMINO`) hasta que se entrega o cancela. Solo el cliente del pedido lo recibe; el domiciliario nunca lo obtiene del sistema (ningún DTO suyo lo contiene). Antes de la asignación responde `409 INVALID_STATE` con el mensaje "estará disponible cuando un domiciliario lo acepte". La app lo muestra en el detalle del domicilio, lo guarda en el teléfono del cliente solo mientras el domicilio está activo (por si no hay señal al recibir) y ofrece "Reportar un problema" (RF-60) si hay desacuerdo en la entrega; el domicilio sigue sin entregar.

## Estado actual del código

El código lo genera RF-19 al asignar y lo valida RF-24. No existe la consulta del cliente. La pantalla del domicilio del cliente (trazabilidad) la crea RF-26; este plan añade el componente del código a esa pantalla.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Consulta su código y se lo dice al domiciliario |
| Vendedor (punto fijo) | No interviene (no ve el código) |
| Domiciliario | No recibe el código del sistema; lo escucha del cliente |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Código visible en < 3 s desde el detalle del domicilio (SC-001): consulta por id.
- 0 % de usuarios distintos al cliente con acceso (SC-002): pertenencia por el comprador del pedido y prueba de que ninguna otra respuesta lo contiene.
- Respuesta con `Cache-Control: no-store`.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Mostrar el código desde la asignación hasta la entrega o cancelación | `GET /domicilios/{id}/codigo-confirmacion` devuelve el código solo en `ASIGNADO`, `RECOGIDO` y `EN_CAMINO` | T010, T011, T013 |
| FR-002 | Restringirlo al cliente; el domiciliario nunca lo recibe | Pertenencia por el comprador del pedido (`404` para los demás); ningún otro endpoint ni evento lo incluye | T012, T014 |
| FR-003 | Informar un desacuerdo con un reporte (RF-60), manteniendo el domicilio sin entregar | El domicilio no cambia por el reporte; la app ofrece "Reportar un problema" desde la pantalla del código | T022 |
| SC-001 | < 3 s | Consulta por id | T040 |
| SC-002 | 0 % de otros usuarios | Prueba de aislamiento y de contrato | T012, T014 |
| US1-E1 | Consulta del código de confirmación | Endpoint y componente | T010, T020 |
| US1-E2 | Código aún no disponible | `409 INVALID_STATE` con "estará disponible cuando un domiciliario lo acepte" | T011 |
| US1-E3 | Desacuerdo con la entrega | El domicilio sigue `EN_CAMINO`; acceso a RF-60 | T022 |
| US1-E4 | Acceso de otro usuario | `404 NOT_FOUND` | T012 |
| Edge 1 | Regenerar el código si lo compartió por error | No existe en la spec ni en el catálogo; no se ofrece (Open Questions) | — |
| Edge 2 | Ver el código sin conexión | Caché local del código mientras el domicilio está activo | T021, T023 |
| Edge 3 | El cliente nunca entrega su código | El domicilio sigue `EN_CAMINO`; sin vencimiento (foundations) | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el cliente del pedido |
| RNF04 Privacidad | ☑ | `Cache-Control: no-store`; sin registro en logs; guardado local solo del cliente y borrado al terminar |
| RNF07 Rendimiento | ☑ | Consulta por id |
| RNF24 Errores claros | ☑ | Mensajes por estado |
| RNF28 Conectividad | ☑ | Código en SQLite mientras está activo |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Lee (`codigo_confirmacion`, `estado`) | El código solo se sirve en `ASIGNADO`, `RECOGIDO` y `EN_CAMINO` |
| Pedido | Lee (vía puerto) | El comprador es el único autorizado |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /domicilios/{id}/codigo-confirmacion` | CLIENTE | El comprador del pedido del domicilio; si no, `404` |

## API Contracts

### GET /api/v1/domicilios/{id}/codigo-confirmacion

- **Rol**: cliente · **Path params**: `id` (UUID) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body `{ "domicilioId": "…", "estado": "EN_CAMINO", "codigo": "048213" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de cliente | — |
| 404 | `NOT_FOUND` | Domicilio inexistente o de un pedido de otro cliente | "El domicilio no está disponible" |
| 409 | `INVALID_STATE` | `DISPONIBLE` (aún sin asignar), `ENTREGADO` o `CANCELADO` | `details.estadoActual`; en `DISPONIBLE`: "Tu código estará disponible cuando un domiciliario acepte el domicilio" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno (el aviso de asignación, E09, lo envía RF-38 y la app refresca al recibirlo).

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: al consultar con éxito, la app guarda el código en SQLite junto con el id del domicilio; así el cliente puede mostrarlo sin señal en el momento de la entrega. La entrada se borra cuando la consulta devuelve `ENTREGADO` o `CANCELADO`, cuando responde `404` y al cerrar sesión.
- **Concurrencia (RNF08)**: no aplica (lectura).
- **Idempotencia (RNF27)**: no aplica.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Domicilio sin asignar | `409 INVALID_STATE` con el mensaje de espera | US1-E2 |
| Domicilio de otro cliente | `404` | US1-E4 |
| Domicilio entregado o cancelado | `409 INVALID_STATE`; el código deja de servir | FR-001 |
| Sin conexión | Código guardado localmente | Edge 2 |
| Desacuerdo | "Reportar un problema" (RF-60); el domicilio no cambia | US1-E3 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: cada estado (`DISPONIBLE`, `ASIGNADO`, `RECOGIDO`, `EN_CAMINO`, `ENTREGADO`, `CANCELADO`), cliente del pedido, otro cliente, domiciliario, vendedor, cabecera `no-store` | US1-E1, US1-E2, US1-E4, FR-001, FR-002 |
| Contrato | Ninguna respuesta de domiciliario, vendedor, eventos ni logs contiene el código | FR-002, SC-002 |
| Frontend | Jest: código visible, mensaje de espera, caché sin red y su borrado, "Reportar un problema" | US1-E3, Edge 2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-25-codigo-de-confirmacion-del-cliente/
├── RF-25-codigo-de-confirmacion-del-cliente.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── application/
│   ├── ports/in/                 # ConsultarCodigoConfirmacionPort
│   ├── ports/out/                # DomicilioRepositoryPort, PedidoParaDomicilioPort (comprador)
│   └── usecase/                  # ConsultarCodigoConfirmacionUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (GET /domicilios/{id}/codigo-confirmacion), DTO
    └── out/persistence/          # lectura del domicilio
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/app/(cliente)/pedidos/[id]/domicilio.tsx             # (RF-26) pantalla del domicilio del cliente
frontend/src/components/domicilios/CodigoConfirmacion.tsx         # código grande, mensaje de espera y "Reportar un problema"
frontend/src/services/domicilios.ts                               # obtenerCodigoConfirmacion() con caché local
frontend/src/database/schema/codigo_confirmacion_cache.ts         # solo domicilios activos del cliente
frontend/__tests__/domicilios/codigo-confirmacion.test.tsx
```

**Structure Decision**: módulo `domicilios`. El código se sirve únicamente por este endpoint, con su propio DTO: el resto de los DTO (domiciliario, vendedor, trazabilidad, eventos) no tienen ese campo. La pertenencia se resuelve por el comprador del pedido (`PedidoParaDomicilioPort`).

## Phase 1: Setup

- [ ] T001 Confirmar el código guardado (RF-19, T018) y `PedidoParaDomicilioPort` con el comprador (RF-14, T005)

## Phase 2: Foundational

- [ ] T002 Verificar que `DomicilioRepositoryPort` lee `codigo_confirmacion` solo desde el caso de uso de este plan y el de RF-24 (regla ArchUnit sobre el campo)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Cliente entrega su código al recibir el pedido (Priority: P1)

**Goal**: que el cliente pueda decir su código al domiciliario, solo él.
**Independent Test**: con un domicilio asignado, consultar el código como cliente y verificar que nadie más puede verlo.
**Cubre**: FR-001–FR-003, SC-001–SC-002, US1-E1–E4, Edge 2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /domicilios/{id}/codigo-confirmacion` en `ASIGNADO`, `RECOGIDO` y `EN_CAMINO` (código y `no-store`) en `backend/src/test/java/co/edu/napa/domicilios/ConsultarCodigoIT.java`
- [ ] T011 [P] [US1] Test de domicilio `DISPONIBLE` (`INVALID_STATE` con el mensaje de espera), `ENTREGADO` y `CANCELADO` en `.../domicilios/ConsultarCodigoEstadosIT.java`
- [ ] T012 [P] [US1] Test de otro cliente (`404`), domiciliario y vendedor (`403`) en `.../domicilios/ConsultarCodigoAccesoIT.java`
- [ ] T014 [P] [US1] Test de contrato: ningún otro DTO, evento ni log contiene el código en `.../domicilios/CodigoNoExpuestoContratoIT.java`
- [ ] T020 [P] [US1] Test del componente (código visible, mensaje de espera, "Reportar un problema") en `frontend/__tests__/domicilios/codigo-confirmacion.test.tsx`

### Implementation

- [ ] T013 [US1] `ConsultarCodigoConfirmacionUseCase` (comprador, estados que lo sirven) y su puerto
- [ ] T015 [US1] `GET /domicilios/{id}/codigo-confirmacion` con `Cache-Control: no-store` y su DTO
- [ ] T021 [US1] Servicio `obtenerCodigoConfirmacion()` con caché en `frontend/src/services/domicilios.ts` y `frontend/src/database/schema/codigo_confirmacion_cache.ts` (borrado al terminar, al responder `404` y al cerrar sesión)
- [ ] T022 [US1] `CodigoConfirmacion` con acceso a "Reportar un problema" (RF-60) en `frontend/src/components/domicilios/`
- [ ] T023 [US1] Integración del componente en `frontend/src/app/(cliente)/pedidos/[id]/domicilio.tsx` (RF-26) y prueba de que se muestra sin red

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir la consulta en < 3 s (SC-001)
- [ ] T041 Revisar que el código no se registre en logs ni se comparta (RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-19 y RF-14; se integra en la pantalla de RF-26; RF-24 es quien valida el código; el reporte es RF-60 (ola 6).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: regenerar el código si se compartió por error | Seguridad | No se ofrece (no está en la spec ni en el catálogo); el bloqueo de intentos de RF-24 limita el abuso | Equipo |
| Guardar el código en el teléfono del cliente para verlo sin señal | RNF04 | Caché local solo de domicilios activos, borrada al terminar o al cerrar sesión | Equipo |
| El código se guarda en claro en la base (D13) | Seguridad | Decisión cerrada en foundations | Equipo |
| `409 INVALID_STATE` para un domicilio aún sin asignar y no `404`, porque el cliente es parte del pedido | Contrato | Así, con el mensaje de la spec | Equipo |
| Ruta de la pantalla del cliente: el catálogo usa el id del domicilio, que el cliente obtiene del bloque `domicilio` del detalle del pedido (RF-48) | UX | Pantalla bajo `(cliente)/pedidos/[id]/domicilio` | Equipo |
| El desacuerdo (US1-E3) depende de RF-60 (ola 6) | Orden de construcción | Hasta entonces la app solo mantiene el domicilio sin entregar | Equipo |

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
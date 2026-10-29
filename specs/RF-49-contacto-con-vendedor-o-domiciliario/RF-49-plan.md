# Implementation Plan: Contacto con vendedor o domiciliario

**Date**: 2026-10-27
**Spec**: [RF-49-contacto-con-vendedor-o-domiciliario.md](./RF-49-contacto-con-vendedor-o-domiciliario.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-49
**Historias de usuario**: HU-79
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-27, RF-19, RF-48, RF-51, RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Desde el detalle de su pedido, el cliente contacta al vendedor o al domiciliario con `POST /pedidos/{id}/contactar` (cuerpo `{ "destinatario": "VENDEDOR" | "DOMICILIARIO", "tipo": "MENSAJE" | "LLAMADA" }`). El sistema valida que el pedido es del cliente y que el pedido y el destinatario están en un estado que permite el contacto, registra un `CanalContacto` (tipo, destinatario y fecha, sin contenido de conversaciones) y devuelve un enlace listo para abrir en el teléfono (`tel:` para llamada, `https://wa.me/…` para mensaje). La opción de contactar al domiciliario solo existe mientras haya un domiciliario con el domicilio activo; con un pedido terminado, rechazado o cancelado no se puede contactar.

## Estado actual del código

No existe aún ningún contacto. `CanalContacto` está en spec-general (id, tipo, destinatario, created_at) y solo falta su tabla. RF-27 deja disponible `ConsultarDomiciliarioAsignadoPort` y RF-51/RF-48 muestran el detalle del pedido donde se ubican los botones. La comunicación en sí (mensaje o llamada) ocurre fuera de Ñapa, en las apps del teléfono.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Inicia el contacto desde el detalle de su pedido |
| Vendedor | Es el destinatario; no interviene |
| Domiciliario | Es el destinatario; no interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Contacto en < 10 s desde el detalle (SC-001): una llamada al backend con tres lecturas indexadas y un `INSERT`; la app abre el enlace al recibir la respuesta.
- 100 % de contactos al destinatario correcto (SC-002): el número se obtiene en el servidor a partir del pedido, nunca lo envía la app.
- 0 % de contactos con un domiciliario no asignado (SC-003): se valida contra el domicilio vigente en el servidor, no solo en la interfaz.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El cliente inicia contacto con el vendedor de su pedido | `POST /pedidos/{id}/contactar` con `destinatario = VENDEDOR` | T010, T020, T021 |
| FR-002 | Contactar al domiciliario asignado | Mismo endpoint con `destinatario = DOMICILIARIO`; el número sale del domiciliario del domicilio vigente | T011, T022 |
| FR-003 | Impedir el contacto con un domiciliario no asignado | `409 INVALID_STATE` y opción oculta en la app | T012, T030 |
| FR-004 | Registrar el canal habilitado para trazabilidad | Fila de `CanalContacto` (tipo y destinatario) en la misma transacción | T013, T023 |
| SC-001 | < 10 s | Una sola llamada | T014 |
| SC-002 | 100 % al destinatario correcto | Número obtenido en el servidor | T010, T011 |
| SC-003 | 0 % de contactos con un domiciliario sin asignar | Validación en el servidor | T012 |
| US1-E1 | Contacto con el vendedor | `200` con el enlace y el registro | T010 |
| US1-E2 | Contacto con el domiciliario asignado | `200` con el enlace y el registro | T011 |
| Edge 1 | Contactar un domiciliario cuando no hay uno | `409 INVALID_STATE` con `details.estadoActual` | T012 |
| Edge 2 | Falta de respuesta del destinatario | Fuera de alcance: la conversación ocurre fuera de Ñapa; el cliente puede reportar con RF-60 (Open Questions) | — |
| Edge 3 | Pedido finalizado o cancelado | `409 INVALID_STATE` con el estado del pedido | T015 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el cliente que hizo el pedido |
| RNF04 Datos personales | ☑ | El número del destinatario solo se entrega cuando el contacto es válido, con `Cache-Control: no-store`; la app no lo guarda |
| RNF07 Rendimiento ≤ 3 s | ☑ | Lecturas indexadas |
| RNF13 Arquitectura | ☑ | `domicilios` usa solo puertos `in` de `pedidos` y `cuentas` |
| RNF23 Consistencia | ☑ | El estado del pedido y del domicilio se validan en el momento |
| RNF27 Idempotencia | ☑ | `Idempotency-Key` evita registrar dos veces un toque repetido |
| RNF28 Conectividad | ☑ | Necesita conexión; sin red la app lo indica |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| CanalContacto | Escribe | tipo `MENSAJE`/`LLAMADA`, destinatario `VENDEDOR`/`DOMICILIARIO`; sin contenido de conversaciones |
| Pedido | Lee (puerto) | Estado `PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER` o `EN_CAMINO` |
| Domicilio | Lee | Para `DOMICILIARIO`: estado `ASIGNADO`, `RECOGIDO` o `EN_CAMINO` |
| Usuario | Lee (puerto) | Solo el celular del destinatario |

## State Transitions

No cambia estados de pedido ni de domicilio; solo agrega una fila de `CanalContacto`. Contacta únicamente con el pedido en `PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER` o `EN_CAMINO`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /pedidos/{id}/contactar` | CLIENTE | El pedido debe ser del cliente; otro caso responde `404` |

## API Contracts

### POST /api/v1/pedidos/{id}/contactar

- **Rol**: cliente · **Path params**: `id` (UUID del pedido) · **Query params**: ninguno
- **Body**: `{ "destinatario": "VENDEDOR", "tipo": "LLAMADA" }`

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{ "id": "…", "destinatario": "VENDEDOR", "tipo": "LLAMADA", "enlace": "tel:+573001234567" }
```

Para `MENSAJE` el enlace es `https://wa.me/573001234567`. No hay `Location`: el canal no tiene consulta propia.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `destinatario` o `tipo` ausentes o con valor inválido | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de cliente | — |
| 404 | `NOT_FOUND` | Pedido inexistente o de otro cliente | "El pedido no está disponible" |
| 409 | `INVALID_STATE` | Pedido terminado, rechazado o cancelado; o destinatario `DOMICILIARIO` sin domiciliario con domicilio activo | `details.estadoActual` (del pedido o del domicilio) |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Registrar un contacto no tiene reacciones |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: no se encola: el número se pide al servidor en el momento y sin red la app muestra "Necesitas conexión para contactar". No se guarda el número en el dispositivo.
- **Concurrencia (RNF08)**: si el domicilio se cancela justo antes del toque, el servidor responde `409` y la app oculta la opción.
- **Idempotencia (RNF27)**: la misma `Idempotency-Key` devuelve el mismo enlace sin registrar un segundo canal.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Domiciliario aún no asignado | `409 INVALID_STATE`; la app no muestra el botón | FR-003, Edge 1 |
| Pedido terminado o cancelado | `409 INVALID_STATE` | Edge 3 |
| Pedido de otro cliente | `404` | RNF03 |
| El destinatario no responde | Fuera de Ñapa; el cliente puede reportar (RF-60) | Edge 2 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: contacto con vendedor y con domiciliario en `MENSAJE` y `LLAMADA`, el registro en `canal_contacto`, repetición con la misma clave | FR-001, FR-002, FR-004, US1-E1–E2 |
| Integración | Domiciliario sin asignar o con domicilio `DISPONIBLE`, `ENTREGADO` o `CANCELADO`; pedido `ENTREGADO`, `RECHAZADO` y `CANCELADO`; pedido ajeno; rol distinto | FR-003, SC-003, Edge 1, Edge 3 |
| Rendimiento | Respuesta < 10 s de extremo a extremo (objetivo del backend < 1 s) | SC-001 |
| Arquitectura | `domicilios` usa `ConsultarContactoUsuarioPort` de `cuentas` y los puertos de `pedidos` solo por `ports/in` | RNF13 |
| Frontend | Jest: botones por destinatario, ocultar al domiciliario sin asignar, apertura del enlace, aviso sin red | FR-003, US1-E2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-49-contacto-con-vendedor-o-domiciliario/
├── RF-49-contacto-con-vendedor-o-domiciliario.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── application/
│   ├── ports/in/                 # ContactarPedidoPort
│   ├── ports/out/                # PedidoParaDomicilioPort, ContactoUsuarioPort, CanalContactoPort (registra en pedidos), DomicilioRepositoryPort
│   └── usecase/                  # ContactarPedidoUseCase
└── infrastructure/adapters/
    └── in/web/                   # ContactoPedidoController (POST /pedidos/{id}/contactar)

backend/src/main/java/co/edu/napa/pedidos/                           # dueño de CanalContacto
├── domain/model/                 # CanalContacto, TipoContacto, DestinatarioContacto
├── application/ports/in/         # RegistrarCanalContactoPort
└── infrastructure/adapters/out/persistence/   # CanalContactoJpaEntity y su repositorio
backend/src/main/java/co/edu/napa/cuentas/application/ports/in/      # ConsultarContactoUsuarioPort (celular del destinatario)
backend/src/main/resources/db/migration/                              # siguiente migración libre: canal_contacto
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/components/pedidos/BotonesContacto.tsx                  # llamar o escribir al vendedor y al domiciliario
frontend/src/services/contacto.ts                                    # contactar() y Linking.openURL
frontend/src/app/(cliente)/pedidos/[id].tsx                          # (RF-48) incluye los botones
frontend/__tests__/pedidos/botones-contacto.test.tsx
```

**Structure Decision**: el caso de uso vive en `domicilios` porque necesita el domicilio vigente y su domiciliario, y `domicilios` ya depende de `pedidos` (hacerlo al revés crearía un ciclo; mismo criterio que RF-42 y RF-43). La tabla `canal_contacto` es de `pedidos` (spec-general: "Pedido habilita CanalContacto") y se escribe por un puerto `in`. El número del destinatario se obtiene de `cuentas` y nunca viaja desde la app.

## Phase 1: Setup

- [ ] T001 Confirmar los módulos `domicilios`, `pedidos` y `cuentas` y sus reglas ArchUnit

## Phase 2: Foundational

- [ ] T002 Migración `canal_contacto` en la siguiente migración libre de `backend/src/main/resources/db/migration/`: id, pedido_id, tipo, destinatario, created_at, con CHECK de tipo y destinatario e índice `(pedido_id, created_at)`
- [ ] T003 `CanalContacto` (dominio) y `RegistrarCanalContactoPort` en `pedidos`
- [ ] T004 `ConsultarContactoUsuarioPort` en `cuentas` (devuelve solo el celular del usuario activo)
- [ ] T005 `ConsultarDomiciliarioAsignadoPort` de RF-27 (si RF-27 aún no se hizo, crearlo aquí)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Contactar al vendedor o al domiciliario (Priority: P2)

**Goal**: el cliente resuelve dudas de su pedido hablando con quien corresponde.
**Independent Test**: desde el detalle de un pedido en curso, contactar al vendedor y, con domicilio aceptado, al domiciliario.
**Cubre**: FR-001–FR-004, SC-001–SC-003, US1-E1–E2, Edge 1 y 3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /pedidos/{id}/contactar` al vendedor (`MENSAJE` y `LLAMADA`, enlace correcto, registro en `canal_contacto`, repetición con la misma clave) en `backend/src/test/java/co/edu/napa/domicilios/ContactarVendedorIT.java`
- [ ] T011 [P] [US1] Test de integración al domiciliario con domicilio `ASIGNADO`, `RECOGIDO` y `EN_CAMINO` en `.../domicilios/ContactarDomiciliarioIT.java`
- [ ] T012 [P] [US1] Test de domiciliario no asignado: sin domicilio, `DISPONIBLE`, `ENTREGADO` y `CANCELADO` (`409 INVALID_STATE`) en `.../domicilios/ContactarDomiciliarioNoAsignadoIT.java`
- [ ] T013 [P] [US1] Test de que cada contacto crea exactamente una fila y de que un rechazo no crea ninguna en `.../domicilios/ContactarRegistroIT.java`
- [ ] T014 [P] [US1] Test de rendimiento del endpoint (< 1 s en backend) en `.../domicilios/ContactarRendimientoIT.java`
- [ ] T015 [P] [US1] Test de pedido `ENTREGADO`, `RECHAZADO` y `CANCELADO` (`409`), pedido ajeno (`404`), rol distinto (`403`) y cuerpo inválido (`400`) en `.../domicilios/ContactarAutorizacionIT.java`
- [ ] T030 [P] [US1] Test de `BotonesContacto` (oculta al domiciliario sin asignar, abre el enlace, aviso sin red) en `frontend/__tests__/pedidos/botones-contacto.test.tsx`

### Implementation

- [ ] T020 [US1] `ContactarPedidoPort` y `ContactarPedidoUseCase`: valida pertenencia y estados, obtiene el celular, registra el canal y arma el enlace
- [ ] T021 [US1] `POST /pedidos/{id}/contactar` y su DTO en `ContactoPedidoController`
- [ ] T022 [US1] Resolución del destinatario `DOMICILIARIO` con `ConsultarDomiciliarioAsignadoPort`
- [ ] T023 [US1] Adaptador de `RegistrarCanalContactoPort` y repositorio de `canal_contacto` en `pedidos`
- [ ] T031 [US1] `contactar()` y la apertura del enlace con `Linking.openURL` en `frontend/src/services/contacto.ts`
- [ ] T032 [US1] `BotonesContacto` y su integración en el detalle del pedido del cliente (RF-48), mostrando la opción del domiciliario solo si `domicilio.estado` es `ASIGNADO`, `RECOGIDO` o `EN_CAMINO`

**Checkpoint**: el cliente contacta a quien necesita y queda registrado.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Confirmar que los logs no guardan el número del destinatario
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-27 (domiciliario asignado), RF-19, RF-48 y RF-51 (detalle donde van los botones) y RF-10.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| **RF-27 FR-003 pide ocultar los datos de contacto directo del domiciliario y RF-49 permite llamarlo**: el enlace `tel:` o `wa.me` revela su celular al cliente | Privacidad | Se entrega solo cuando el contacto es válido (pedido activo, domicilio activo) y sin guardarlo en la app. La alternativa es un número enmascarado (por ejemplo un proxy de Twilio), que añade costo y otro proveedor. **Necesita decisión del equipo** | Equipo |
| La ruta no está en spec-general (`POST /pedidos/{id}/contactar`) | Contrato | Se agrega a Spec-general | Equipo |
| Edge 2: ¿qué pasa si el destinatario no responde? | Alcance | Fuera de Ñapa, porque la conversación ocurre en el teléfono; el cliente puede reportar con RF-60 | Equipo |
| ¿Se contacta con el pedido `PENDIENTE` (el vendedor aún no responde)? | Regla | Sí; la spec no lo excluye | Equipo |
| No hay límite de contactos por pedido | Abuso | Sin límite; el rate limit general de la API ya protege | Equipo |
| El vendedor no puede iniciar contacto con el cliente (solo el cliente, según la spec) | Alcance | RF-52 le entrega el celular al vendedor para esa necesidad | Equipo |
| El canal `MENSAJE` abre WhatsApp, y quien no lo tenga no podrá usarlo | UX | Se ofrece también la llamada | Equipo |

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
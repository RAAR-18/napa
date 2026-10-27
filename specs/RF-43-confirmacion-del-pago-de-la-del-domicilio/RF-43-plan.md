# Implementation Plan: Confirmación del pago de la tarifa del domicilio

**Date**: 2026-10-27
**Spec**: [RF-43-confirmacion-del-pago-de-la-del-domicilio.md](./RF-43-confirmacion-del-pago-de-la-del-domicilio.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-43
**Historias de usuario**: HU-104, HU-68
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-24, RF-14, RF-53, RF-42, RF-26, RF-18, RF-38, RF-60
**Habilita a**: RF-40, RF-41

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Cuando el domicilio está `ENTREGADO`, el vendedor de punto fijo registra el pago de la tarifa al domiciliario con `POST /domicilios/{id}/pago-tarifa` (medio siempre `CUENTA_BANCARIA`, monto = la tarifa del pedido; sin cuerpo). Se crea el `Pago` de tipo `TARIFA_DOMICILIO` en estado `REGISTRADO` y se publica E14 para avisar al domiciliario (RF-38). El domiciliario asignado confirma que recibió la transferencia con `POST /domicilios/{id}/pago-tarifa/confirmar`, que lo pasa a `CONFIRMADO`. Cada paso ocurre una sola vez por domicilio. `GET /domicilios/{id}/pago-tarifa` devuelve el estado a ambas partes (`SIN_REGISTRAR`, `REGISTRADO` o `CONFIRMADO`). Para que el vendedor sepa a dónde transferir, el domiciliario registra su cuenta bancaria (`PUT /domiciliarios/me/configuracion-pago`, con las mismas reglas de RF-63) y el vendedor la ve en `GET /domicilios/{id}/pago-tarifa` cuando el domicilio está `ENTREGADO`. La app agrega las dos acciones, la pantalla de la cuenta del domiciliario y un acceso a "Reportar un problema" (RF-60) para cuando la transferencia no llegó o el monto no coincide. La plataforma no mueve dinero: solo lleva el registro (RNF29).

## Estado actual del código

La tabla `pago` y el pago de tipo `PEDIDO` vienen de RF-53 y RF-42; RF-14 agrega la clave foránea `pago.domicilio_id`. Nada crea todavía el pago `TARIFA_DOMICILIO`: nace aquí, ya `REGISTRADO`. Tampoco existe la cuenta bancaria del domiciliario: la spec y spec-general solo contemplan `ConfiguracionPago` para el vendedor, así que su registro es un cambio de spec que este plan incorpora (Open Questions). RF-24 deja el domicilio `ENTREGADO` y publica E12, que avisa al vendedor de que debe pagar la tarifa; el listener de E14 está planeado en RF-38 (T027).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene |
| Vendedor de punto fijo | Registra el pago de la tarifa |
| Vendedor ambulante | No interviene (no tiene domicilios) |
| Domiciliario | Registra su cuenta bancaria, confirma que recibió su tarifa y recibe el aviso |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Registrar y confirmar en < 10 s cada uno (SC-001): una llamada corta con un toque de confirmación.
- 0 % de segundas confirmaciones (SC-002) y 100 % de registros con medio cuenta bancaria (SC-003): único parcial en la base, estado verificado con bloqueo y medio fijado por el dominio.
- Aviso al domiciliario en < 1 min (SC-004): E14 por el outbox en la misma transacción.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El vendedor de punto fijo registra el pago solo con el domicilio `ENTREGADO` | `POST /domicilios/{id}/pago-tarifa` con precondición de estado | T010, T011, T020, T021 |
| FR-002 | Medio "cuenta bancaria" y valor de la tarifa publicada | `Pago.registrarTarifa` fija el medio y el monto sale de `Pedido.tarifa_domicilio` | T010, T015 |
| FR-003 | El domiciliario asignado confirma solo después del registro | `POST /domicilios/{id}/pago-tarifa/confirmar` con precondición `REGISTRADO` | T012, T013, T022 |
| FR-004 | No registrar ni confirmar dos veces | Único parcial, bloqueo y `CONFLICT` / `ALREADY_CONFIRMED` | T014 |
| FR-005 | Solo el domiciliario asignado confirma y solo el vendedor dueño registra | Validación de pertenencia con `404` | T016 |
| FR-006 | Sin opción de efectivo | El endpoint no recibe medio; un `medio` en el cuerpo da `400` | T015 |
| FR-007 | Notificar al domiciliario al registrar | E14 en el outbox y listener de RF-38 | T017 |
| FR-008 (nuevo; hay que agregarlo a la spec) | El domiciliario registra su cuenta bancaria y el vendedor la ve al pagar la tarifa | `PUT/GET /domiciliarios/me/configuracion-pago` y campo `cuentaDomiciliario` en `GET /domicilios/{id}/pago-tarifa` | T060, T061, T062, T064, T065 |
| SC-001 | < 10 s cada paso | Una llamada | T030 |
| SC-002 | 0 % de segundas confirmaciones | Prueba de doble confirmación y concurrencia | T014, T018 |
| SC-003 | 100 % con cuenta bancaria | Constructor del dominio y CHECK | T015 |
| SC-004 | Aviso en < 1 min | E14 en la misma transacción | T017 |
| US1-E1 | Registro con domicilio entregado | `201`, `REGISTRADO`, `CUENTA_BANCARIA`, monto de la tarifa | T010 |
| US1-E2 | Registro antes de la entrega | `409 INVALID_STATE` con `estadoActual` | T011 |
| US1-E3 | Registro duplicado | `409 CONFLICT`, sin segunda fila | T014 |
| US2-E1 | El domiciliario confirma la recepción | `200`, `CONFIRMADO`, `confirmadoAt` | T012 |
| US2-E2 | Confirmación sin pago registrado | `409 FEE_NOT_REGISTERED` | T013 |
| US2-E3 | Confirmación duplicada | `409 ALREADY_CONFIRMED` | T014 |
| Edge 1 | El domiciliario no recibió o el monto difiere | Acceso a "Reportar un problema" (RF-60) con `domicilioId` | T031, T034 |
| Edge 2 | El vendedor no registra en un tiempo razonable | Sin recordatorio ni escalamiento (Open Questions) | — |
| Edge 3 | Domicilio cancelado tras el pago del pedido | No está `ENTREGADO`: `409 INVALID_STATE`; la devolución es externa | T011 |
| Edge 4 | Confirmar un domicilio que no le fue asignado | `404` | T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Rol y pertenencia validados en el caso de uso |
| RNF04 Privacidad | ☑ | El número de cuenta del domiciliario va cifrado, fuera de los logs, con `no-store`, y solo lo ve el vendedor del domicilio entregado |
| RNF08 Concurrencia | ☑ | Bloqueo domicilio → pago y único parcial |
| RNF15 Fiabilidad | ☑ | Pago y E14 en una sola transacción |
| RNF16 Trazabilidad | ☑ | `created_at` (registro) y `confirmado_at` |
| RNF18 Notificaciones | ☑ | E14 al domiciliario |
| RNF23 Consistencia | ☑ | Estados `REGISTRADO` → `CONFIRMADO` verificados |
| RNF24 Errores claros | ☑ | Códigos específicos con mensaje |
| RNF27 Integridad | ☑ | `Idempotency-Key` en los dos `POST` |
| RNF28 Conectividad | ☑ | Los dos pasos van por el outbox |
| RNF29 No procesar dinero | ☑ | Solo registro; la transferencia es externa |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pago | Escribe | Tipo `TARIFA_DOMICILIO`; medio `CUENTA_BANCARIA`; estados `REGISTRADO` → `CONFIRMADO`; un solo pago de este tipo por domicilio |
| Domicilio | Lee (bloqueo) | Estado `ENTREGADO`; domiciliario asignado; vendedor = `domicilio.vendedor_id` (RF-14) |
| Pedido | Lee (puerto) | `tarifa_domicilio` |
| ConfiguracionPago | Escribe / lee | Una por domiciliario (nueva); solo `banco`, `numero_cuenta` y `tipo_cuenta`, juntos; `acepta_efectivo` no aplica |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Pago (tarifa) | (no existe) | `REGISTRADO` | El vendedor registra | Domicilio `ENTREGADO`; vendedor dueño del pedido | Se crea con monto y medio; E14 |
| Pago (tarifa) | `REGISTRADO` | `CONFIRMADO` | El domiciliario confirma | Domiciliario asignado al domicilio | `confirmado_at` |

`PENDIENTE_CONFIRMACION` no se usa en la tarifa (Open Questions). No cambia el estado del domicilio ni del pedido.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /domicilios/{id}/pago-tarifa` | VENDEDOR_PUNTO_FIJO | `domicilio.vendedor_id` es el vendedor autenticado; si no, `404` |
| `POST /domicilios/{id}/pago-tarifa/confirmar` | DOMICILIARIO | Es el domiciliario asignado; si no, `404` |
| `GET /domicilios/{id}/pago-tarifa` | VENDEDOR_PUNTO_FIJO, DOMICILIARIO | Parte del domicilio; si no, `404`. `cuentaDomiciliario` solo para el vendedor y solo con el domicilio `ENTREGADO` |
| `PUT` y `GET /domiciliarios/me/configuracion-pago` | DOMICILIARIO | Su propia cuenta |

## API Contracts

### POST /api/v1/domicilios/{id}/pago-tarifa

- **Rol**: vendedor de punto fijo · **Path params**: `id` (UUID del domicilio) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅. **Body**: ninguno (un `medio` o `monto` en el cuerpo responde `400`).
- **Respuestas exitosas**: `201` con `X-Request-Id` y body `{ "pagoId": "…", "estado": "REGISTRADO", "monto": 5000, "medio": "CUENTA_BANCARIA", "registradoAt": "…" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key`, o el cuerpo trae `medio` o `monto` | "El pago de la tarifa es solo por cuenta bancaria" |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor de punto fijo | — |
| 404 | `NOT_FOUND` | El domicilio no existe o no es de un pedido suyo | "No encontramos el domicilio" |
| 409 | `INVALID_STATE` | El domicilio no está `ENTREGADO` (`details.estadoActual`) | "Primero debe confirmarse la entrega" |
| 409 | `CONFLICT` | Ya registraste el pago de este domicilio | "Ya registraste el pago de la tarifa" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otra petición | — |

### POST /api/v1/domicilios/{id}/pago-tarifa/confirmar

- **Rol**: domiciliario · **Path params**: `id` · **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅. **Body**: ninguno.
- **Respuestas exitosas**: `200` con `{ "pagoId": "…", "estado": "CONFIRMADO", "monto": 5000, "confirmadoAt": "…" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key` | — |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de domiciliario | — |
| 404 | `NOT_FOUND` | El domicilio no existe o no es suyo | "No encontramos el domicilio" |
| 409 | `FEE_NOT_REGISTERED` | El vendedor todavía no registró el pago | "El vendedor aún no registró el pago" |
| 409 | `ALREADY_CONFIRMED` | Ya confirmaste la recepción | "Ya confirmaste este pago" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otra petición | — |

### GET /api/v1/domicilios/{id}/pago-tarifa

- **Rol**: vendedor de punto fijo o domiciliario del domicilio · **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store` y body `{ "estado": "SIN_REGISTRAR" | "REGISTRADO" | "CONFIRMADO", "monto": 5000, "medio": "CUENTA_BANCARIA", "registradoAt": null, "confirmadoAt": null, "cuentaDomiciliario": { "banco": "Bancolombia", "numeroCuenta": "12345678901", "tipoCuenta": "AHORROS" } }`. El monto sale de la tarifa del pedido aunque aún no haya registro. `cuentaDomiciliario` solo se envía al vendedor con el domicilio `ENTREGADO`, y es `null` si el domiciliario no registró su cuenta; al domiciliario nunca se le envía.
- **Errores**: `401`, `403` (otro rol), `404` (no es parte del domicilio).

### PUT /api/v1/domiciliarios/me/configuracion-pago

- **Rol**: domiciliario · **Cabeceras**: `Authorization` ✅, `Content-Type: application/json`. **Body**: `{ "banco": "Bancolombia", "numeroCuenta": "12345678901", "tipoCuenta": "AHORROS" }` (solo dígitos, de 6 a 20; `tipoCuenta` `AHORROS` o `CORRIENTE`; los tres campos juntos, igual que RF-63).
- **Respuestas exitosas**: `200` con `Cache-Control: no-store` y el mismo cuerpo, con el número de cuenta enmascarado salvo los últimos 4 dígitos.
- **Errores**: `400 VALIDATION_ERROR` (formato o campos incompletos), `401`, `403` (otro rol).

### GET /api/v1/domiciliarios/me/configuracion-pago

- **Rol**: domiciliario · **Respuestas exitosas**: `200` con la cuenta enmascarada, o `{ "banco": null, "numeroCuenta": null, "tipoCuenta": null }` si nunca la registró. **Errores**: `401`, `403`.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E14 `pagos.pago-tarifa-registrado` (`PagoTarifaRegistradoEvent`: `pagoId`, `domicilioId`, `domiciliarioId`, `monto`) | `RegistrarPagoTarifaUseCase` (`pagos`) | En la misma transacción del registro (outbox) |

La confirmación del domiciliario no publica evento: el catálogo no define uno y no hay a quién avisar (queda en el historial, RF-40).

**Eventos que consume esta feature**: ninguno. E12 solo avisa al vendedor de que debe pagar (RF-38).

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: registrar y confirmar van por el outbox con su `Idempotency-Key`. La pantalla muestra "pendiente de envío" y, si el servidor responde con un error de negocio (por ejemplo `FEE_NOT_REGISTERED`), lo muestra y descarta la acción.
- **Concurrencia (RNF08)**: orden de bloqueo domicilio → pago (RF-14). Dos registros simultáneos: el único parcial deja pasar uno y el otro recibe `CONFLICT`. Dos confirmaciones: la segunda recibe `ALREADY_CONFIRMED`.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}` más el único por domicilio.
- La cuenta del domiciliario se guarda con conexión (no usa el outbox).

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Registrar antes de la entrega | `409 INVALID_STATE` | US1-E2 |
| Registrar dos veces | `409 CONFLICT` | US1-E3 |
| Confirmar sin registro | `409 FEE_NOT_REGISTERED` | US2-E2 |
| Confirmar dos veces | `409 ALREADY_CONFIRMED` | US2-E3 |
| Domicilio ajeno | `404` | FR-005 |
| Intentar efectivo | `400` | FR-006 |
| Transferencia no llegó o monto distinto | "Reportar un problema" (RF-60) | Edge 1 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Pago.registrarTarifa` (medio, monto, estado) y `Pago.confirmarTarifa` | FR-002, FR-003 |
| Integración | Endpoints con Testcontainers: registro con domicilio `ENTREGADO`; en cada estado distinto; duplicado; confirmación con y sin registro; doble confirmación; domicilio ajeno; roles; medio en el cuerpo; E14 en el outbox; domicilio cancelado | FR-001–FR-007, US1–US2, Edge 3–4 |
| Concurrencia | Dos registros y dos confirmaciones simultáneas | FR-004, SC-002 |
| Migración | Único parcial de un pago `TARIFA_DOMICILIO` por domicilio | FR-004 |
| Arquitectura | `domicilios` usa `pagos` solo por puertos `in`; `pagos` no importa `domicilios` | RNF13 |
| Frontend | Jest: acciones por rol y estado, outbox sin red, errores, acceso a reportar | US1, US2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-43-confirmacion-del-pago-de-la-del-domicilio/
├── RF-43-confirmacion-del-pago-de-la-del-domicilio.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pagos/
├── domain/
│   ├── model/                    # Pago (registrarTarifa, confirmarTarifa)
│   ├── event/                    # PagoTarifaRegistradoEvent (E14)
│   └── exception/                # TarifaYaRegistradaException, TarifaNoRegistradaException
├── application/
│   ├── ports/in/                 # RegistrarPagoTarifaPort, ConfirmarPagoTarifaPort, ConsultarPagoTarifaPort
│   ├── ports/out/                # PagoRepositoryPort (bloquearPorDomicilioId), EventPublisherPort (shared)
│   └── usecase/                  # RegistrarPagoTarifaUseCase, ConfirmarPagoTarifaUseCase, ConsultarPagoTarifaUseCase
├── infrastructure/adapters/in/web/    # ConfiguracionPagoDomiciliarioController (PUT/GET /domiciliarios/me/configuracion-pago)
└── infrastructure/adapters/out/persistence/

backend/src/main/java/co/edu/napa/domicilios/
├── application/
│   ├── ports/in/                 # GestionarPagoTarifaPort
│   ├── ports/out/                # PagoTarifaPort (hacia los puertos `in` de pagos), PedidoParaDomicilioPort
│   └── usecase/                  # PagarTarifaUseCase (registrar, confirmar y consultar: valida domicilio, pertenencia y estado)
└── infrastructure/adapters/in/web/   # DomicilioController (tres rutas de pago-tarifa)
backend/src/test/java/co/edu/napa/{pagos,domicilios}/

frontend/src/components/pagos/RegistrarPagoTarifaSheet.tsx          # monto, "transfiere a la cuenta del domiciliario" y confirmar
frontend/src/components/pagos/ConfirmarTarifaRecibidaSheet.tsx      # confirmar recepción y "Reportar un problema"
frontend/src/components/pagos/EstadoPagoTarifa.tsx
frontend/src/components/pagos/CuentaDomiciliario.tsx                 # banco, tipo y número con botón "copiar"
frontend/src/components/pagos/FormularioCuentaBancaria.tsx          # compartido con RF-63
frontend/src/app/(domiciliario)/configuracion/cuenta.tsx            # ruta Expo Router: mi cuenta bancaria
frontend/src/services/pagos.ts                                      # registrarPagoTarifa(), confirmarPagoTarifa(), consultarPagoTarifa() por el outbox
frontend/src/app/(vendedor)/domicilios/[id].tsx                     # (RF-26) acción en el domicilio entregado
frontend/src/app/(domiciliario)/domicilios/[id].tsx                 # (RF-18) acción en el domicilio entregado
frontend/__tests__/pagos/pago-tarifa.test.tsx
```

**Structure Decision**: el pago vive en `pagos` (dueño de `Pago`, de E14 y de los puertos), pero los casos de uso de los endpoints viven en `domicilios`, que ya conoce el domicilio, su estado, el domiciliario y el pedido; así `pagos` no depende de `domicilios` y no hay ciclo (mismo patrón que RF-42 con `pedidos`). Orden de bloqueo: domicilio → pago. El monto sale de `Pedido.tarifa_domicilio` por el puerto de `pedidos` ya usado por RF-14, y el vendedor sale de `domicilio.vendedor_id`. La cuenta del domiciliario reutiliza la entidad `ConfiguracionPago`, el value object `CuentaBancaria` y el cifrado de RF-63 en el módulo `pagos`; `domicilios` la pide por un puerto `in` de `pagos`.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `pagos`, `pago.domicilio_id` (RF-14) y las reglas ArchUnit (`domicilios` → `pagos` solo por `ports/in`)

## Phase 2: Foundational

- [ ] T002 Confirmar o crear el único parcial `(domicilio_id) WHERE tipo = 'TARIFA_DOMICILIO'` y un CHECK de medio `CUENTA_BANCARIA` para ese tipo, en la siguiente migración libre de `backend/src/main/resources/db/migration/`
- [ ] T003 Dominio: `Pago.registrarTarifa` y `Pago.confirmarTarifa`, `PagoTarifaRegistradoEvent` (E14) y excepciones en `pagos/domain/`
- [ ] T004 `PagoRepositoryPort.bloquearPorDomicilioId` y puertos `in` de `pagos` (registrar, confirmar, consultar)
- [ ] T005 `PagoTarifaPort` (puerto `out` de `domicilios`) y su adaptador sobre los puertos `in` de `pagos`; `PedidoParaDomicilioPort` devuelve la tarifa del pedido

**Checkpoint**: base lista.

## Phase 3: User Story 1 - El vendedor registra el pago de la tarifa (Priority: P2)

**Goal**: el vendedor deja constancia de que pagó al domiciliario y este es avisado.
**Independent Test**: con un domicilio `ENTREGADO`, registrar el pago y ver `REGISTRADO`, E14 y la notificación.
**Cubre**: FR-001, FR-002, FR-004 (registro), FR-005 (vendedor), FR-006, FR-007, SC-001, SC-003, SC-004, US1-E1–E3, Edge 3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /domicilios/{id}/pago-tarifa` con domicilio `ENTREGADO` (`201`, `REGISTRADO`, `CUENTA_BANCARIA`, monto de la tarifa del pedido) en `backend/src/test/java/co/edu/napa/domicilios/RegistrarPagoTarifaIT.java`
- [ ] T011 [P] [US1] Test de integración: domicilio en cada estado distinto de `ENTREGADO` (incluido `CANCELADO`) da `409 INVALID_STATE` con `estadoActual` en `.../domicilios/RegistrarPagoTarifaEstadoIT.java`
- [ ] T014 [P] [US1] Test de integración: segundo registro (`409 CONFLICT`, una sola fila), segunda confirmación (`409 ALREADY_CONFIRMED`) y concurrencia de dos registros y de dos confirmaciones en `.../domicilios/PagoTarifaDuplicadoIT.java`
- [ ] T015 [P] [US1] Test unitario de `Pago.registrarTarifa` (medio, monto, estado) y test de integración con `medio` o `monto` en el cuerpo (`400`) en `backend/src/test/java/co/edu/napa/pagos/PagoTarifaTest.java`
- [ ] T016 [P] [US1] Test de integración: domicilio de otro vendedor y de otro domiciliario (`404`), vendedor ambulante, cliente y administrador (`403`) en `.../domicilios/PagoTarifaAutorizacionIT.java`
- [ ] T017 [P] [US1] Test de integración: E14 en el outbox con `pagoId`, `domicilioId`, `domiciliarioId` y `monto` en la misma transacción, y notificación al domiciliario con el listener de RF-38 en `.../domicilios/PagoTarifaEventoIT.java`
- [ ] T018 [P] [US1] Test de idempotencia (misma clave repite la respuesta, clave reutilizada `422`, falta de clave `400`) y de la consulta `GET /domicilios/{id}/pago-tarifa` (`SIN_REGISTRAR`, `REGISTRADO`, `CONFIRMADO`, ajeno `404`) en `.../domicilios/PagoTarifaIdempotenciaYConsultaIT.java`
- [ ] T019 [P] [US1] Test de migración del único parcial y del CHECK de medio en `.../pagos/MigracionPagoTarifaTest.java`
- [ ] T030 [P] [US1] Test de `RegistrarPagoTarifaSheet` y de la acción en el domicilio entregado del vendedor (outbox sin red, errores) en `frontend/__tests__/pagos/pago-tarifa-vendedor.test.tsx`

### Implementation

- [ ] T020 [US1] `RegistrarPagoTarifaUseCase` (en `pagos`) y `PagarTarifaUseCase.registrar` (en `domicilios`: bloqueo, vendedor, estado, monto)
- [ ] T021 [US1] `POST /domicilios/{id}/pago-tarifa` y `GET /domicilios/{id}/pago-tarifa` en `DomicilioController`
- [ ] T023 [US1] Publicación de E14 desde `RegistrarPagoTarifaUseCase`
- [ ] T031 [US1] Servicios `registrarPagoTarifa()` y `consultarPagoTarifa()` por el outbox en `frontend/src/services/pagos.ts`
- [ ] T032 [US1] `RegistrarPagoTarifaSheet` y `EstadoPagoTarifa` en `frontend/src/components/pagos/`
- [ ] T033 [US1] Acción "Registrar pago de la tarifa" en `frontend/src/app/(vendedor)/domicilios/[id].tsx` cuando el domicilio está `ENTREGADO`

## Phase 4: User Story 2 - El domiciliario confirma que recibió su tarifa (Priority: P2)

**Goal**: el domiciliario cierra el cobro de su servicio.
**Independent Test**: con el pago `REGISTRADO`, confirmar y ver `CONFIRMADO`.
**Cubre**: FR-003, FR-004 (confirmación), FR-005 (domiciliario), SC-002, US2-E1–E3, Edge 1, Edge 4.

### Tests (antes de implementar)

- [ ] T012 [P] [US2] Test de integración de `POST /domicilios/{id}/pago-tarifa/confirmar` (`200`, `CONFIRMADO`, `confirmadoAt`) en `.../domicilios/ConfirmarPagoTarifaIT.java`
- [ ] T013 [P] [US2] Test de integración: confirmar sin registro da `409 FEE_NOT_REGISTERED` y no crea nada en `.../domicilios/ConfirmarPagoTarifaSinRegistroIT.java`
- [ ] T034 [P] [US2] Test de `ConfirmarTarifaRecibidaSheet`, del acceso a "Reportar un problema" con `domicilioId` y de la acción del domiciliario (outbox sin red, `FEE_NOT_REGISTERED`) en `frontend/__tests__/pagos/pago-tarifa-domiciliario.test.tsx`

### Implementation

- [ ] T022 [US2] `ConfirmarPagoTarifaUseCase` (en `pagos`) y `PagarTarifaUseCase.confirmar` (en `domicilios`: bloqueo, domiciliario asignado, registro previo)
- [ ] T035 [US2] Ruta `POST /domicilios/{id}/pago-tarifa/confirmar` en `DomicilioController`
- [ ] T036 [US2] Servicio `confirmarPagoTarifa()` por el outbox y `ConfirmarTarifaRecibidaSheet` con el acceso a `BotonReportarProblema` (RF-60)
- [ ] T037 [US2] Acción "Confirmé que recibí mi tarifa" en `frontend/src/app/(domiciliario)/domicilios/[id].tsx` cuando el pago está `REGISTRADO`

**Checkpoint**: las dos historias funcionales.

## Phase 5: User Story 3 - El domiciliario registra su cuenta bancaria (Priority: P2, nueva)

**Goal**: el vendedor sabe a dónde transferir la tarifa.
**Independent Test**: el domiciliario registra su cuenta y el vendedor la ve en el domicilio entregado.
**Cubre**: FR-008 (pendiente de agregar a la spec).

### Tests (antes de implementar)

- [ ] T060 [P] [US3] Test de integración de `PUT` y `GET /domiciliarios/me/configuracion-pago` (guarda, enmascara, vacío, otro rol `403`) en `backend/src/test/java/co/edu/napa/pagos/CuentaDomiciliarioIT.java`
- [ ] T061 [P] [US3] Test de integración: `cuentaDomiciliario` llega solo al vendedor del domicilio y solo con el domicilio `ENTREGADO`; es `null` sin cuenta; no llega al domiciliario ni a otro vendedor en `.../domicilios/PagoTarifaCuentaVisibleIT.java`
- [ ] T062 [P] [US3] Test de validación (solo dígitos, 6 a 20, tipo `AHORROS` o `CORRIENTE`, campos juntos), de cifrado en columna y de que el número no aparece en los logs en `.../pagos/CuentaDomiciliarioValidacionIT.java`
- [ ] T063 [P] [US3] Test del formulario de cuenta del domiciliario y de `CuentaDomiciliario` con "copiar" en `frontend/__tests__/pagos/cuenta-domiciliario.test.tsx`

### Implementation

- [ ] T064 [US3] Casos de uso y `ConfiguracionPagoDomiciliarioController` (`PUT` y `GET`), reutilizando `ConfiguracionPago`, `CuentaBancaria` y el cifrado de RF-63, en `pagos/`
- [ ] T065 [US3] Campo `cuentaDomiciliario` en `GET /domicilios/{id}/pago-tarifa` (puerto `in` de `pagos` hacia `domicilios`, solo vendedor y `ENTREGADO`)
- [ ] T066 [US3] `FormularioCuentaBancaria` (compartido con RF-63), pantalla `(domiciliario)/configuracion/cuenta.tsx` y `CuentaDomiciliario` en la hoja de registro del vendedor

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar permisos y privacidad (RNF03, RNF04)
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → Polish.
- Dependencias entre RF: requiere RF-24 (domicilio `ENTREGADO`), RF-14, RF-53 y RF-42 (tabla y tipo `Pago`), RF-26 y RF-18 (pantallas), RF-38 (listener E14), RF-60 y RF-63 (cuenta bancaria, cifrado y formulario); habilita RF-40 y RF-41 (historial de pagos).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| El domiciliario debe poder registrar su cuenta para que le paguen (decidido en la ola 7a). Requiere cambiar la spec: spec-general §3.2 y §3.3 (restricción 1) y agregar FR-008 a la spec de RF-43 | Modelo y spec | Plan incluido como US3, con las rutas `/domiciliarios/me/configuracion-pago`; sin tocar la spec primero, no se implementa | Equipo |
| ¿Debe exigirse la cuenta para aceptar un domicilio (RF-19)? | Regla | No: sin cuenta, el vendedor ve `cuentaDomiciliario: null` y coordina con el domiciliario; exigirla sería otro cambio en RF-19 | Equipo |
| El vendedor ve el número de cuenta completo del domiciliario (lo necesita para transferir) | Privacidad | Solo con el domicilio `ENTREGADO` y solo el vendedor de ese domicilio | Equipo |
| El pago de la tarifa nace `REGISTRADO` al registrar; no existe una fila `PENDIENTE_CONFIRMACION` previa | Modelo | Sin fila hasta registrar; `GET` devuelve `SIN_REGISTRAR` | Equipo |
| Código nuevo `FEE_NOT_REGISTERED` (409) para confirmar sin registro; el registro duplicado usa `CONFLICT` | Catálogo | Agregar `FEE_NOT_REGISTERED` a spec-general §2.2.1 | Equipo |
| Rutas nuevas fuera de spec-general §2.4: dos `POST` y un `GET` de `pago-tarifa` | Contrato | Agregarlas | Equipo |
| Edge 2: el vendedor no registra a tiempo; no hay recordatorio ni escalamiento | Operación | Sin automatismos (decisión del MVP); el domiciliario puede reportar (RF-60). Mejora posible: recordatorio al vendedor | Equipo |
| La confirmación del domiciliario no emite evento: el catálogo no tiene uno | Catálogo | Sin evento | Equipo |
| El monto se toma de `Pedido.tarifa_domicilio`; si el vendedor transfirió otro valor, la plataforma no lo sabe | Datos | Solo se registra el monto publicado (RNF29) | Equipo |
| Los endpoints viven en `domicilios` y el núcleo en `pagos` (la spec asignaba el módulo `pagos`) | Estructura | Evita el ciclo `pagos` ↔ `domicilios`, como RF-42 | Equipo |

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
# Implementation Plan: Configuración de métodos de pago aceptados por el vendedor

**Date**: 2026-10-21
**Spec**: [RF-63-configuracion-de-metodos-de-pago-aceptados.md](./RF-63-configuracion-de-metodos-de-pago-aceptados.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-63
**Historias de usuario**: HU-103
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-07, RF-10
**Habilita a**: RF-32 (`ofreceDomicilio`), RF-45 (medios que ve el cliente), RF-53 (`BANK_ACCOUNT_REQUIRED`)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor indica si acepta efectivo y, opcionalmente, registra una cuenta bancaria (banco, tipo y número). Es una fila `ConfiguracionPago` por vendedor, que se crea o reemplaza con un `PUT`. El módulo `pagos` expone un puerto `in` de consulta que usan RF-32, RF-45 y RF-53: medios habilitados para entrega directa y recogida (efectivo y/o cuenta) y si el vendedor puede ofrecer domicilio (solo vendedor de punto fijo con cuenta registrada). Ñapa no procesa transferencias ni pagos: solo registra los medios configurados y las confirmaciones de las partes.

## Estado actual del código

El backend de RF-63 implementa configuración GET/PUT, validación de cuenta, consulta interna de medios, migración V10 y cifrado AES-GCM con nonce aleatorio y AAD ligado al vendedor. El frontend ya permite consultar, editar y quitar la cuenta bancaria y configurar efectivo desde la sesión del vendedor; valida los datos antes del envío, conserva el borrador ante errores y muestra si el punto fijo puede ofrecer domicilios. Los detalles de cuenta se cargan con la sesión autenticada y nunca se registran en logs. La copia de cuenta en Domicilio sigue pendiente; se congela al publicar el domicilio en RF-14. RF-42, RF-43 y RF-40/41 extenderán después el módulo `pagos`.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Indirecto: ve solo los medios habilitados (RF-45) |
| Vendedor (ambulante / punto fijo) | Configura sus medios de pago |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Configurar en < 1 minuto (SC-001): una pantalla con un interruptor y tres campos.
- 100 % de pedidos de domicilio de vendedores con cuenta (SC-002) y 0 % de clientes ven medios no habilitados (SC-003): la regla se aplica en el servidor con el puerto de consulta, no en la app.
- Datos de cuenta: no se registran en logs; respuesta con `Cache-Control: no-store` (RNF04).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Indicar si acepta efectivo | Campo `aceptaEfectivo` de `PUT /vendedores/me/configuracion-pago` | T010, T013, T014 |
| FR-002 | Registrar una cuenta bancaria | Campo `cuentaBancaria` (banco, tipoCuenta, numeroCuenta; los tres juntos o ninguno) | T010, T011, T015 |
| FR-003 | Ofrecer al cliente solo los medios habilitados | Puerto `in` `ConsultarMediosPagoPort`; lo usa RF-45 al listar opciones y al validar `medio_pago` | T012, T016 |
| FR-004 | Exigir cuenta bancaria para domicilio en punto fijo | Mismo puerto: `domicilioDisponible` = punto fijo **y** cuenta registrada; RF-53 responde `BANK_ACCOUNT_REQUIRED` y RF-32 expone `ofreceDomicilio` | T012, T017 |
| FR-005 | No guardar tarjetas ni billeteras | El DTO solo admite los tres campos de cuenta y rechaza propiedades desconocidas (`400`); sin columnas para otros datos | T011, T018 |
| SC-001 | < 1 min | Pantalla única | T031 |
| SC-002 | 100 % de domicilios con cuenta | Regla en servidor (T017) y prueba con RF-53 | T017 |
| SC-003 | 0 % ve un medio no habilitado | Prueba de contrato del puerto | T012 |
| US1-E1 | Efectivo y cuenta | `mediosHabilitados` incluye ambos | T010 |
| US1-E2 | Solo efectivo | Sin cuenta, solo `EFECTIVO` | T012 |
| US1-E3 | Punto fijo con domicilios | La respuesta incluye `avisoDomicilio` "El domicilio siempre exige cuenta bancaria" | T019 |
| US1-E4 | Domicilio sin cuenta | `domicilioDisponible = false`; la aceptación se rechaza en RF-53 | T017 |
| Edge 1 | Validación del número de cuenta | Banco como texto libre (1–100 caracteres), tipo `AHORROS`/`CORRIENTE`, cuenta de 4–20 dígitos | T011 |
| Edge 2 | Desactivar efectivo con pedidos pendientes | Los pedidos ya creados conservan su `medio_pago`; se permite (decisión provisional) | T020 |
| Edge 3 | Cambiar la cuenta con domicilios en curso | Al publicar un domicilio, congelar una copia de la cuenta en el domicilio; los cambios posteriores no la alteran | RF-14 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF01 Seguridad | ☑ | Solo el dueño lee y modifica su configuración |
| RNF03 Autorización | ☑ | Solo roles vendedor |
| RNF04 Privacidad | ☑ | `numeroCuenta` fuera de los logs; respuesta `no-store`; cifrado AES-GCM en la aplicación |
| RNF16 Trazabilidad | ☑ | `created_at`, `updated_at` |
| RNF23 Consistencia | ☑ | CHECK: los tres campos de cuenta van juntos o ninguno |
| RNF24 Errores claros | ☑ | `400` con el campo inválido |
| RNF29 No procesar dinero | ☑ | Solo se guarda el número de cuenta |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| ConfiguracionPago | Escribe / lee | Una por usuario vendedor; `banco`, `numero_cuenta`, `tipo_cuenta` opcionales y juntos |
| Usuario | Lee (rol) | `VENDEDOR_AMBULANTE` o `VENDEDOR_PUNTO_FIJO` |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Configurar / consultar la configuración | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | Solo la propia (`/vendedores/me`) |
| Consultar medios de un vendedor (puerto `in`) | Otros módulos (RF-32, RF-45, RF-53) | Uso interno, sin endpoint público |

## API Contracts

### PUT /api/v1/vendedores/me/configuracion-pago

- **Rol**: vendedor
- **Path params**: ninguno · **Query params**: ninguno
- **Body**: `{ "aceptaEfectivo": true, "cuentaBancaria": { "banco": "Bancolombia", "tipoCuenta": "AHORROS", "numeroCuenta": "12345678901" } }` (`cuentaBancaria` puede ser `null` para quitarla)

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Content-Type | ✅ | `application/json` |

`Idempotency-Key` no se exige: un `PUT` que reemplaza el recurso es idempotente por naturaleza (spec-general §2.4).

- **Respuestas exitosas**: `200` con `Cache-Control: no-store` y body `{ "aceptaEfectivo", "cuentaBancaria": { "banco", "tipoCuenta", "numeroCuenta" } | null, "mediosHabilitados": ["EFECTIVO", "CUENTA_BANCARIA"], "domicilioDisponible": false, "avisoDomicilio": "El domicilio siempre exige cuenta bancaria registrada" }`. `avisoDomicilio` y `domicilioDisponible` solo aparecen para vendedor de punto fijo.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Datos de cuenta incompletos, formato inválido, o propiedades desconocidas (p. ej. un campo de tarjeta) | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor | — |
| 422 | `BUSINESS_RULE` | El vendedor no tiene medio habilitado para el pedido | `NO_PAYMENT_METHOD_CONFIGURED` |

### GET /api/v1/vendedores/me/configuracion-pago

- **Rol**: vendedor · **Respuestas**: `200` con el mismo cuerpo del `PUT`; si nunca configuró, `aceptaEfectivo: false`, `cuentaBancaria: null` y `mediosHabilitados: []`. **Errores**: `401`, `403`.
- Esta ruta **no está en el catálogo de spec-general §2.4**: hay que agregarla para que la app muestre el estado actual (Open Questions).

### Puerto `in` para otros módulos (no es endpoint)

`ConsultarMediosPagoPort.consultar(vendedorId)` devuelve `{ aceptaEfectivo, tieneCuenta, domicilioDisponible }`. Lo usan RF-45 (opciones y validación del `medio_pago` elegido), RF-32 (`ofreceDomicilio`) y RF-53 (`BANK_ACCOUNT_REQUIRED`).

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El catálogo no tiene eventos de configuración de pago |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: configurar el pago requiere conexión en el MVP (se hace una vez); la pantalla conserva lo escrito y el `PUT` se puede repetir sin riesgo.
- **Concurrencia (RNF08)**: `PUT` con reemplazo; el último gana. Fila única por usuario (`UNIQUE usuario_id`).
- **Idempotencia (RNF27)**: natural del `PUT`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Datos de cuenta incompletos | `400` | FR-002 |
| Campo de tarjeta o billetera en el body | `400` (propiedad desconocida) | FR-005 |
| Domicilio sin cuenta | `domicilioDisponible = false`; RF-53 rechaza | FR-004 |
| Desactivar efectivo con pedidos pendientes | Los pedidos conservan su `medio_pago` | Edge 2 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Reglas de `ConfiguracionPago` (medios habilitados, domicilio según rol y cuenta) | FR-003, FR-004 |
| Integración | `PUT`/`GET` con Testcontainers; pertenencia; rechazo de campos desconocidos | US1-E1–E4, FR-005 |
| Contrato de puerto | `ConsultarMediosPagoPort` para ambulante, punto fijo con y sin cuenta | SC-002, SC-003 |
| Frontend | Jest del formulario y del aviso de domicilio | US1-E3 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-63-configuracion-de-metodos-de-pago-aceptados/
├── RF-63-configuracion-de-metodos-de-pago-aceptados.md   # spec
└── plan.md                                               # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pagos/
├── domain/
│   ├── model/                    # ConfiguracionPago, MedioPago (EFECTIVO, CUENTA_BANCARIA), CuentaBancaria
│   └── exception/
├── application/
│   ├── ports/in/                 # ConfigurarPagoVendedorPort, ConsultarConfiguracionPagoPort, ConsultarMediosPagoPort
│   ├── ports/out/                # ConfiguracionPagoRepositoryPort, UsuarioRolPort
│   └── usecase/                  # ConfigurarPagoVendedorUseCase, ConsultarConfiguracionPagoUseCase, ConsultarMediosPagoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # ConfiguracionPagoController, DTOs
    └── out/persistence/          # ConfiguracionPagoJpaEntity, repositorio, adaptador
backend/src/main/resources/db/migration/   # V10__configuracion_pago.sql (V9 pertenece a RF-44)
backend/src/test/java/co/edu/napa/pagos/

frontend/src/app/configuracion-pagos.tsx
frontend/src/lib/pagos.ts
frontend/src/app/__tests__/configuracion-pagos.test.tsx
frontend/src/lib/pagos.test.ts
```

La estructura sigue las rutas Expo Router planas y el patrón actual de clientes HTTP en `lib/`. El formulario permanece en la pantalla porque no tiene otro consumidor ni requiere un componente adicional.

**Structure Decision**: módulo `pagos`, donde viven los medios y los registros de pago (RF-40 a RF-43). El rol del usuario se obtiene por un puerto `out` que llama a `cuentas` por su puerto `in`.

## Phase 1: Setup

- [x] T001 Crear el paquete `co.edu.napa.pagos` y su regla ArchUnit en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`

## Phase 2: Foundational

- [x] T002 Migración `V10__configuracion_pago.sql`: tabla `configuracion_pago` (id, usuario_id único, acepta_efectivo, banco, numero_cuenta_cifrada, tipo_cuenta, created_at, updated_at) con CHECK de que banco, número y tipo van juntos o ninguno, en `backend/src/main/resources/db/migration/`
- [x] T003 Rechazar propiedades JSON desconocidas en `ConfiguracionPagoController` sin cambiar el comportamiento de Jackson para los demás endpoints

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Definir cómo me pueden pagar (Priority: P1)

**Goal**: el vendedor define sus medios de pago y el sistema los aplica.
**Independent Test**: activar efectivo, registrar una cuenta y verificar que ambos medios se ofrecen al cliente.
**Cubre**: FR-001–FR-005, SC-001–SC-003, US1-E1–E4, Edge 1–3.

### Tests (antes de implementar)

- [x] T010 [P] [US1] Test de integración de `PUT`/`GET` (efectivo + cuenta, solo efectivo, quitar la cuenta) en `backend/src/test/java/co/edu/napa/pagos/ConfiguracionPagoIntegrationTest.java`
- [x] T011 [P] [US1] Test de validación (formato, propiedades desconocidas tipo tarjeta) en `backend/src/test/java/co/edu/napa/pagos/ConfiguracionPagoIntegrationTest.java`
- [x] T012 [P] [US1] Test de contrato de `ConsultarMediosPagoPort` (ambulante, punto fijo con y sin cuenta, sin configuración) en `backend/src/test/java/co/edu/napa/pagos/application/usecase/ConsultarMediosPagoUseCaseTest.java`
- [ ] T020 [P] [US1] Test de que desactivar efectivo no altera pedidos ya creados (cuando exista RF-45) en `.../pagos/DesactivarEfectivoIT.java`
- [x] T021 [P] [US1] Pruebas del formulario, validación de cuenta, permisos y aviso de domicilio en `frontend/src/app/__tests__/configuracion-pagos.test.tsx`; contrato de API en `frontend/src/lib/pagos.test.ts`

### Implementation

- [x] T013 [US1] Dominio `ConfiguracionPago` y `ConfigurarPagoVendedorUseCase` en `pagos/domain/model/` y `pagos/application/usecase/`
- [x] T014 [US1] `PUT /vendedores/me/configuracion-pago` en `ConfiguracionPagoController`
- [x] T015 [US1] Validación de `cuentaBancaria` (tres campos juntos)
- [x] T016 [US1] `ConsultarMediosPagoUseCase` y su puerto `in`
- [x] T017 [US1] Regla `domicilioDisponible` (punto fijo y cuenta registrada)
- [x] T018 [US1] Persistencia cifrada AES-GCM sin columnas de tarjeta ni billetera y sin registrar el número en logs
- [x] T019 [US1] `GET /vendedores/me/configuracion-pago` con `avisoDomicilio` para punto fijo
- [x] T022 [US1] Cliente autenticado `guardarConfiguracionPago()` y `obtenerConfiguracionPago()` en `frontend/src/lib/pagos.ts`
- [x] T023 [US1] Formulario de configuración con validación local y gestión de cuenta en `frontend/src/app/configuracion-pagos.tsx`; se mantiene dentro de la pantalla siguiendo el patrón existente
- [x] T024 [US1] Pantalla `/configuracion-pagos` enlazada desde la sesión del vendedor

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [x] T030 Revisar privacidad del número de cuenta (RNF04)
- [ ] T031 Medir la configuración en < 1 min (SC-001)
- [x] T032 Actualizar `requerimientos/trazabilidad.md` y spec-general §2.4 (ruta `GET`) si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: RF-32, RF-45 y RF-53 usan `ConsultarMediosPagoPort`; RF-42 y RF-43 reutilizan el módulo `pagos`.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión acordada | Responsable |
|---|---|---|---|
| Validación de banco y número de cuenta (Edge 1) | FR-002 | Banco como texto libre de 1–100 caracteres; cuenta solo dígitos, 4–20; sin lista de bancos | Equipo |
| Valores de `tipoCuenta` | FR-002 | `AHORROS`, `CORRIENTE` | Equipo |
| ¿Un vendedor nuevo tiene efectivo habilitado por defecto? Sin configuración, la API devuelve ningún medio y el cliente no podría pedir | FR-003 | Ningún medio hasta configurar | Equipo |
| ¿Se exige al menos un medio habilitado (efectivo o cuenta)? | FR-003 | Se permite guardar sin medios; los pedidos se rechazan con `NO_PAYMENT_METHOD_CONFIGURED` | Equipo |
| El cliente necesita ver la cuenta del vendedor para transferir en domicilio y en pagos por transferencia; ninguna spec define quién la muestra ni cuándo | RF-45, RF-51, RF-53 | A definir en el plan de RF-45 o RF-51 | Equipo |
| Cifrado del número de cuenta (dato sensible) | RNF04 | AES-GCM en aplicación; clave Base64 de 32 bytes en `NAPA_PAYMENT_ACCOUNT_ENCRYPTION_KEY`; ciphertext versionado `v1` | Equipo |
| Rotación de la clave de cifrado | RNF04 | Re-cifrar configuraciones y copias vigentes con la clave nueva antes de reemplazar la anterior | Equipo |
| `GET /vendedores/me/configuracion-pago` no está en el catálogo de rutas | spec-general §2.4 | Agregado al catálogo | Equipo |
| Cambiar la cuenta con domicilios en curso (Edge 3) | RF-14 | Congelar la cuenta al publicar el domicilio | Equipo |
| ¿Configurar el pago puede hacerse sin conexión (outbox)? | RNF28 | No | Equipo |

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
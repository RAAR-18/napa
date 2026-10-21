# Implementation Plan: Verificación de celular por WhatsApp (OTP)

**Date**: 2026-10-20
**Spec**: [spec.md](./RF-70-verificacion-de-celular-por-whatsapp.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [specs/general/spec-general.md](../general/spec-general.md) · [eventos](../general/eventos.md) 
**Requerimiento funcional**: RF-70
**Historias de usuario**: HU-105
**Prioridad (la mayor de la spec)**: P1
**Depende de**: foundations F0–F2 (Redis, Usuario, Sesion, JWT), F022 (cuenta Twilio Verify), RF-07
**Habilita a**: RF-07 (activación), RF-09 (recuperación), RF-08 (cambio de celular)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**: si algo cambia de comportamiento, se corrige primero la spec.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Puerto `VerificarCelularPort` (`iniciar` / `confirmar`) con adaptador `TwilioVerifyAdapter` (WhatsApp con respaldo SMS) y `ConsolaVerificacionAdapter` para desarrollo; Twilio genera, entrega y valida el código; Ñapa aplica sus propios límites y contadores en Redis y registra cada envío como `Mensaje` sin el código. Sirve a tres propósitos: registro, recuperación de contraseña y cambio de celular.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Verifica su celular al registrarse, recupera contraseña, cambia celular |
| Vendedor (ambulante / punto fijo) | Igual |
| Domiciliario | Igual |
| Administrador | No participa (no verifica celular) |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Solicitar el código responde ≤ 1,5 s p95 (SC-001); la entrega depende de Twilio y no tiene SLO propio.
- ≥ 90 % de los códigos llega en < 30 s (SC-002, meta a validar con datos reales).
- Dependencias: Twilio Verify (WhatsApp con respaldo SMS), Redis (límites y contadores), Spring Data JPA (`mensaje`).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Código de 6 dígitos, un solo uso, ≤ 10 min | Lo garantiza Twilio Verify; test de contrato que verifica la configuración del servicio (longitud y vigencia) | T020, T021 |
| FR-002 | WhatsApp con plantilla aprobada y SMS de respaldo | `TwilioVerifyAdapter` (canal `whatsapp`, respaldo `sms`); `GET /auth/verify-phone/mensajes/{id}` ofrece SMS si falló | T022, T023, T026 |
| FR-003 | 5 intentos por código; invalidar al agotar | Contador `verif-att:{celular}:{proposito}` en Redis; `OTP_ATTEMPTS_EXHAUSTED` | T012, T024 |
| FR-004 | 1/min y 5/hora por celular y por IP; `429` + `Retry-After` | `verif-rl:{celular}` y `verif-rl-ip:{ip}` en Redis | T011, T025 |
| FR-005 | Un código nuevo invalida el anterior | Reenvío vía Verify; si Verify no cancela la verificación previa, cancelarla explícitamente (pregunta abierta de foundations) | T027 |
| FR-006 | Misma respuesta con celular registrado o no (recuperación) | `SolicitarVerificacionUseCase` responde `202` idéntico y no llama a Twilio si no existe cuenta | T013, T028 |
| FR-007 | Activar la cuenta e iniciar sesión al verificar el registro | `ConfirmarVerificacionUseCase` → `ActivarCuentaPort` (RF-07) → emite tokens | T029 |
| FR-008 | Cambio de celular aplicado solo tras verificar el nuevo | Propósito `CAMBIO`; el celular anterior sigue vigente hasta confirmar (lo expone a RF-08) | T030 |
| FR-009 | Registrar cada envío como `Mensaje` sin código | Entidad `Mensaje` (canal, propósito, destino, estado, `proveedor_id`) | T014, T031 |
| FR-010 | Nunca guardar el código en claro (BD, logs, outbox) | El código no se persiste; se enmascara en logs y en la capa de Axios del servidor | T032 |
| FR-011 | Actualizar estado del `Mensaje` según el proveedor | `POST /webhooks/twilio/status` si Verify lo ofrece; si no, estado de la respuesta de Twilio (ver Open Questions) | T033 |
| SC-001 | Solicitud ≤ 1,5 s p95 | Envío asíncrono; respuesta 202 inmediata | T040 |
| SC-002 | ≥ 90 % < 30 s | Medición en piloto con datos del proveedor | T041 |
| SC-003 | 0 % reutilizable o vencido | Lo garantiza Twilio; prueba de contrato | T021 |
| SC-004 | 0 % en claro | Prueba que escanea BD, logs y `outbox_event` | T032 |
| SC-005 | 100 % de envíos con `Mensaje` | Prueba de integración | T031 |
| US1-E1 | Código correcto | Confirmación → cuenta activa y sesión | T010 |
| US1-E2 | Código incorrecto | `OTP_INVALID` + `intentosRestantes` | T010 |
| US1-E3 | Código vencido | `OTP_EXPIRED` y opción de pedir uno nuevo | T010 |
| US1-E4 | Intentos agotados | `OTP_ATTEMPTS_EXHAUSTED` | T012 |
| US2-E1 | Recuperación exitosa | Propósito `RECUPERACION`; la contraseña nueva la aplica RF-09 | T034 |
| US2-E2 | Celular no registrado | Respuesta idéntica sin revelar existencia | T013 |
| US3-E1 | Cambio verificado | Propósito `CAMBIO` | T030 |
| US3-E2 | Cambio sin verificar | El celular anterior se conserva | T030 |
| US4-E1 | Reenvío permitido | ≥ 1 min desde el último envío | T011 |
| US4-E2 | Reenvío excedido | `429` con `Retry-After` | T011 |
| US4-E3 | Falla de WhatsApp | Se ofrece SMS | T026 |
| Edge 1 | Celular sin WhatsApp | Respaldo SMS (misma lógica que US4-E3) | T026 |
| Edge 2 | Cambiar a un celular ya registrado | `PHONE_ALREADY_REGISTERED` antes de pedir el código | T030 |
| Edge 3 | Formato internacional distinto del colombiano | Normalización E.164 (`Celular`, plan RF-07); decisión sobre países soportados pendiente | T035 |
| Edge 4 | Mensaje con demora y código ya vencido | `OTP_EXPIRED` + reenvío | T010 |
| Edge 5 | Solicitudes masivas | Límites por celular e IP; bloqueo temporal | T011 |
| Edge 6 | Costo de mensajes y techo mensual | Decisión pendiente (F022 fija tope y alerta) | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF01 Seguridad | ☑ | El código no se guarda; límites contra fuerza bruta |
| RNF02 Autenticación | ☑ | Verificación de la titularidad del celular |
| RNF04 Privacidad | ☑ | `Mensaje` guarda destino pero no el código; destino enmascarado en logs |
| RNF07 Rendimiento | ☑ | Solicitud ≤ 1,5 s p95 |
| RNF16 Trazabilidad | ☑ | `Mensaje` con fechas y estado |
| RNF19 Interoperabilidad | ☑ | Puerto `VerificarCelularPort` detrás de Twilio |
| RNF21 Auditoría | ☑ | Cada envío queda como `Mensaje` |
| RNF24 Errores claros | ☑ | Códigos `OTP_*` con `intentosRestantes` |
| RNF28 Conectividad | ☑ | Reenvío manual y canal SMS de respaldo |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Usuario | Lee / escribe (`estado`, `celular_verificado_at`, `celular` en cambio) | `celular` único |
| Mensaje (infraestructura) | Escribe | Sin código |
| Sesion | Escribe (al verificar el registro) | Vía RF-10/foundations |
| Redis | Escribe | `verif-rl:*`, `verif-rl-ip:*`, `verif-att:*` |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento |
|---|---|---|---|---|---|
| Usuario | `PENDIENTE_VERIFICACION` | `ACTIVA` | Código de registro correcto | Código vigente, intentos disponibles | Ninguno |
| Mensaje | `ENVIADO` | `ENTREGADO` / `FALLIDO` | Proveedor | Estado del proveedor | Ninguno |

**Transiciones inválidas**: `INVALID_STATE`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Solicitar / confirmar (registro) | Público | Celular de una cuenta `PENDIENTE_VERIFICACION` |
| Consultar estado del mensaje | Público | `mensajeId` (UUID no adivinable) |
| Webhook de estado | Twilio | Firma del proveedor validada; sin JWT |

## API Contracts

### POST /api/v1/auth/verify-phone/request

- **Rol**: público
- **Query params**: ninguno
- **Body**: `{ "celular": "+57...", "proposito": "REGISTRO", "canal": "WHATSAPP" | "SMS" }` (`canal` opcional; por defecto WhatsApp)

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| X-Device-Id | ✅ | Identificador de instalación |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `202` con `{ "mensajeId", "canal", "reenvioDisponibleEn" }` y `Cache-Control: no-store`. Misma respuesta si el celular no existe (FR-006).
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Celular o propósito inválido | Indica el campo |
| 409 | `PHONE_ALREADY_REGISTERED` | Propósito `CAMBIO` con celular de otra cuenta | "Ese celular ya está registrado" |
| 429 | `RATE_LIMITED` | Límite por celular o IP | Incluye `Retry-After` |
| 503 | `EXTERNAL_SERVICE_UNAVAILABLE` | Twilio no responde | "No pudimos enviar el código; intenta por SMS" |

### POST /api/v1/auth/verify-phone/confirm

- **Rol**: público
- **Body**: `{ "celular": "+57...", "codigo": "123456" }` (propósito `REGISTRO`; recuperación y cambio usan sus propios endpoints, que invocan el mismo caso de uso)
- **Cabeceras**: `X-Device-Id` obligatoria.
- **Respuestas exitosas**: `200` con `{ "accessToken", "refreshToken", "usuario": { "id", "rol" } }` y `Cache-Control: no-store`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `OTP_INVALID` | Código incorrecto | "Código incorrecto. Te quedan N intentos" |
| 400 | `OTP_EXPIRED` | Código vencido | "El código venció. Pide uno nuevo" |
| 409 | `OTP_ATTEMPTS_EXHAUSTED` | 5 fallos | "Pide un código nuevo" |
| 404 | `NOT_FOUND` | No hay verificación pendiente | "No hay una verificación pendiente" |
| 429 | `RATE_LIMITED` | Demasiadas solicitudes | `Retry-After` |

### GET /api/v1/auth/verify-phone/mensajes/{mensajeId}

- **Rol**: público · **Path params**: `mensajeId` (UUID).
- **Respuestas**: `200` con `{ "canal", "estado", "smsDisponible" }`; `404 NOT_FOUND` si no existe. `Cache-Control: no-store`.

### POST /api/v1/webhooks/twilio/status

- **Rol**: Twilio (firma) · **Respuestas**: `204`; `403 FORBIDDEN` si la firma no es válida. Solo si Verify ofrece notificaciones de estado.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | La verificación no usa eventos ni outbox (eventos.md, regla 8) |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: si el código no llega, el usuario pulsa "reenviar" y puede elegir SMS; no hay cola offline.
- **Concurrencia (RNF08)**: contadores en Redis con operaciones atómicas (`INCR` + `EXPIRE`); un segundo envío concurrente dentro del minuto recibe 429.
- **Idempotencia (RNF27)**: no aplica `Idempotency-Key`; el límite de envío cumple ese papel.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Código incorrecto | `OTP_INVALID` con intentos restantes | US1-E2 |
| Código vencido | `OTP_EXPIRED` | US1-E3 |
| Intentos agotados | `OTP_ATTEMPTS_EXHAUSTED` | FR-003 |
| Reenvío excesivo | 429 | FR-004 |
| Twilio caído | 503 y se ofrece SMS | RNF19 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Contadores, límites, normalización, enmascarado de logs | FR-003, FR-004, FR-010 |
| Integración | Endpoints con `ConsolaVerificacionAdapter`, Redis y PostGIS en Testcontainers | US1, US2, US4 |
| Contrato | `TwilioVerifyAdapter` contra un doble del proveedor | FR-001, SC-003 |
| E2E | Registro completo y recuperación | US1-E1, US2-E1 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-70-verificacion-de-celular-por-whatsapp/
├── RF-70-verificacion-de-celular-por-whatsapp.md   # spec
└── plan.md                                          # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/cuenta/
├── domain/model/                 # PropositoVerificacion, CanalVerificacion, Mensaje, EstadoMensaje
├── application/ports/in/         # SolicitarVerificacionPort, ConfirmarVerificacionPort
├── application/ports/out/        # VerificarCelularPort, MensajeRepositoryPort, LimitadorVerificacionPort
├── application/usecase/          # SolicitarVerificacionUseCase, ConfirmarVerificacionUseCase
└── infrastructure/adapters/
    ├── in/web/                   # VerificacionController, TwilioWebhookController
    ├── out/messaging/            # TwilioVerifyAdapter, ConsolaVerificacionAdapter
    ├── out/cache/                # Redis: verif-rl, verif-rl-ip, verif-att
    └── out/persistence/          # MensajeEntity
backend/src/main/resources/db/migration/   # V?__mensaje.sql
frontend/src/app/(auth)/verificar-celular.tsx   # ruta Expo Router: ingreso del código, reenvío y opción SMS
frontend/src/services/auth/verificacion.ts      # solicitar, confirmar y consultar estado del mensaje
frontend/src/hooks/useCuentaAtras.ts            # cuenta regresiva de `reenvioDisponibleEn`
frontend/__tests__/auth/verificar-celular.test.tsx
```

**Structure Decision**: módulo `cuenta` (RF-70 es parte de Gestionar cuenta). El puerto aísla a Twilio; cambiar de proveedor es cambiar un adaptador.

## Phase 1: Setup

- [ ] T001 Variables de entorno y configuración de Twilio (sin secretos en el repo) en `backend/src/main/resources/application.yml`

## Phase 2: Foundational

- [ ] T002 Migración de `mensaje` en `backend/src/main/resources/db/migration/`
- [ ] T003 `LimitadorVerificacionPort` y adaptador Redis en `cuenta/infrastructure/adapters/out/cache/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Verificar mi celular al registrarme (Priority: P1)

**Goal**: activar la cuenta y entregar sesión.
**Independent Test**: registrar, recibir el código, ingresarlo y verificar cuenta activa y sesión.
**Cubre**: FR-001, FR-003, FR-007, SC-003, US1-E1–E4.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de confirmación correcta, incorrecta y vencida en `backend/src/test/java/co/edu/napa/cuenta/VerificacionRegistroIT.java`
- [ ] T011 [P] [US1] Test de límites 1/min y 5/hora por celular e IP en `.../cuenta/VerificacionLimitesIT.java`
- [ ] T012 [P] [US1] Test de 5 intentos agotados en `.../cuenta/VerificacionIntentosIT.java`
- [ ] T021 [P] [US1] Test de contrato del adaptador (longitud y vigencia) en `.../cuenta/TwilioVerifyContractTest.java`

### Implementation

- [ ] T020 [US1] `VerificarCelularPort` + `ConsolaVerificacionAdapter` en `cuenta/application/ports/out/` y `.../out/messaging/`
- [ ] T022 [US1] `TwilioVerifyAdapter` (WhatsApp, respaldo SMS) en `.../out/messaging/TwilioVerifyAdapter.java`
- [ ] T024 [US1] Contador de intentos y mapeo a `OTP_INVALID` / `OTP_ATTEMPTS_EXHAUSTED` en `ConfirmarVerificacionUseCase`
- [ ] T025 [US1] Límites de envío y `Retry-After` en `SolicitarVerificacionUseCase`
- [ ] T029 [US1] Activar cuenta y emitir tokens vía `ActivarCuentaPort` (RF-07) en `ConfirmarVerificacionUseCase`
- [ ] T014 [US1] Registrar `Mensaje` en cada envío en `cuenta/application/usecase/`
- [ ] T031 [US1] Test y verificación de `Mensaje` por envío (SC-005) en `.../cuenta/MensajeIT.java`
- [ ] T032 [US1] Enmascarar el código en logs y prueba de SC-004 en `.../cuenta/CodigoNoPersistidoIT.java`
- [ ] T036 [P] [US1] Test de la pantalla de verificación (código correcto, `OTP_INVALID` con intentos restantes, `OTP_EXPIRED`, cuenta regresiva de reenvío) en `frontend/__tests__/auth/verificar-celular.test.tsx`
- [ ] T037 [US1] Servicio de verificación en `frontend/src/services/auth/verificacion.ts` y hook `useCuentaAtras` en `frontend/src/hooks/`
- [ ] T038 [US1] Pantalla `frontend/src/app/(auth)/verificar-celular.tsx` (6 dígitos, reenviar, "recibir por SMS" cuando `smsDisponible`)

**Checkpoint**: US1 funcional y probable por separado.

## Phase 4: User Story 2 - Recuperar mi contraseña (Priority: P1)

**Goal**: pedir y confirmar el código para definir contraseña nueva (aplicada por RF-09).
**Independent Test**: solicitar recuperación con celular verificado y confirmar el código.
**Cubre**: FR-006, US2-E1–E2.

### Tests (antes de implementar)

- [ ] T013 [P] [US2] Test de respuesta idéntica con celular registrado y no registrado, sin llamar a Twilio en el segundo caso, en `.../cuenta/RecuperacionEnumeracionIT.java`
- [ ] T034 [P] [US2] Test de recuperación exitosa (coordinado con RF-09) en `.../cuenta/RecuperacionIT.java`

### Implementation

- [ ] T028 [US2] Propósito `RECUPERACION` y respuesta uniforme en `SolicitarVerificacionUseCase`

**Checkpoint**: US2 funcional.

## Phase 5: User Story 3 - Cambiar mi celular (Priority: P2)

**Goal**: verificar el celular nuevo antes de reemplazar el anterior.
**Independent Test**: cambiar el celular, verificar el nuevo y comprobar que el anterior sigue vigente hasta entonces.
**Cubre**: FR-008, US3-E1–E2, Edge 2.

### Tests (antes de implementar)

- [ ] T030a [P] [US3] Test de cambio verificado y sin verificar en `.../cuenta/CambioCelularIT.java`

### Implementation

- [ ] T030 [US3] Propósito `CAMBIO` y rechazo de celular duplicado en `ConfirmarVerificacionUseCase` (consumido por RF-08)

## Phase 6: User Story 4 - Reenviar el código o recibirlo por SMS (Priority: P2)

**Goal**: reenvío y respaldo por SMS.
**Independent Test**: simular fallo de WhatsApp y comprobar que se ofrece SMS.
**Cubre**: FR-002, FR-005, FR-011, US4-E1–E3, Edge 1.

### Tests (antes de implementar)

- [ ] T026a [P] [US4] Test de fallo de WhatsApp y oferta de SMS en `.../cuenta/RespaldoSmsIT.java`
- [ ] T027a [P] [US4] Test de que el código anterior queda invalidado en `.../cuenta/ReenvioInvalidaAnteriorIT.java`

### Implementation

- [ ] T026 [US4] `GET /auth/verify-phone/mensajes/{id}` con `smsDisponible` en `VerificacionController`
- [ ] T027 [US4] Invalidar la verificación anterior al reenviar (cancelación explícita si Verify no lo hace) en `TwilioVerifyAdapter`
- [ ] T033 [US4] `POST /webhooks/twilio/status` con validación de firma en `TwilioWebhookController` (solo si Verify lo ofrece)
- [ ] T023 [US4] Fallback automático a SMS cuando el envío por WhatsApp falla

## Phase N: Polish & Cross-Cutting

- [ ] T035 Normalización E.164 y lista de países soportados
- [ ] T040 Medir solicitud ≤ 1,5 s p95
- [ ] T041 Medir tiempo de entrega en el piloto (SC-002)
- [ ] T042 Revisar privacidad y permisos (RNF03, RNF04)
- [ ] T043 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 (P1) → US2 (P1) → US3/US4 (P2) → Polish.
- Dependencias entre RF: F022 (Twilio) antes de T022; RF-07 aporta `ActivarCuentaPort`; RF-09 y RF-08 consumen `ConfirmarVerificacionPort`.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Twilio Verify ofrece notificaciones de estado por webhook? | FR-011, T033 | Si no, estado tomado de la respuesta síncrona | Equipo |
| ¿Verify cancela la verificación previa al pedir otra? | FR-005 | Si no, cancelarla explícitamente | Equipo |
| Costo mensual y tope de gasto | Edge 6 | F022 fija tope y alerta | Equipo |
| Países soportados (formato internacional) | Edge 3 | Solo Colombia hasta decidir | Equipo |
| Corregir RF-70 FR-001, FR-003 y Key Entities (el código lo genera Twilio) | Coherencia de la spec | Aplicar `cambios/specs-cambios.md` antes de implementar | Equipo |
| Si el celular está en una cuenta `PENDIENTE_VERIFICACION` y se registra otra persona | RF-07 | Rechazar (409) | Equipo |

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
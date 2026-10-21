# Implementation Plan: Cambio de contraseña

**Date**: 2026-10-20
**Spec**: [spec.md](./RF-09-cambio-de-contrasena.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [specs/general/spec-general.md](../general/spec-general.md) · [eventos](../general/eventos.md) 
**Requerimiento funcional**: RF-09
**Historias de usuario**: HU-26, HU-30
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-10 (sesiones, `sv`), RF-70 (código de recuperación), RF-07
**Habilita a**: ninguno

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Cambio de contraseña con la actual (usuarios y administrador) y recuperación con código por WhatsApp/SMS (solo usuarios, vía RF-70). Al completarse incrementa la versión de sesión (`sv`), revoca los demás refresh tokens y, en el cambio con contraseña actual, devuelve tokens nuevos para la sesión en curso.

## Estado actual del código

`AuthController.cambiarContrasena` (`/auth/cambio-contrasena`) y `recuperarContrasena` (`/auth/recuperar-contrasena`) y sus casos de uso ya existen. Este plan **renombra** las rutas a `/auth/password/change` y `/auth/password/recover/request|confirm` (spec-general §2.4), separa solicitar y confirmar la recuperación y completa pruebas. Decisión ya cerrada: contraseña 8–128 caracteres sin reglas de composición; el cambio o la recuperación revocan las demás sesiones (D14).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Cambia o recupera su contraseña |
| Vendedor (ambulante / punto fijo) | Igual |
| Domiciliario | Igual |
| Administrador | Solo cambia con la contraseña actual; no recupera con código |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Cierre inmediato del 100 % de las demás sesiones (SC-001): incremento de `sv` + invalidación en Redis.
- Operaciones ≤ 3 s (RNF07).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Cambiar con la actual; usuarios pueden además recuperar con código | `POST /auth/password/change`; `POST /auth/password/recover/request` y `/confirm` | T010, T013, T014, T016 |
| FR-002 | Validar requisitos mínimos de la nueva contraseña | `PasswordPolicy` compartida con RF-07 (D14: 8–128 caracteres, sin reglas de composición) | T011, T015 |
| FR-003 | Cerrar las demás sesiones | Incremento de `sv` y revocación de refresh excepto el actual | T012, T017 |
| FR-004 | Rechazar si la actual no coincide | `INVALID_CURRENT_PASSWORD` | T010 |
| SC-001 | 100 % de sesiones cerradas | Prueba de integración | T012 |
| SC-002 | 100 % rechaza contraseñas débiles | Prueba parametrizada | T011 |
| US1-E1 | Cambio exitoso | Tokens nuevos para la sesión actual | T010 |
| US1-E2 | Contraseña actual incorrecta | 400, sin modificar la vigente | T010 |
| US1-E3 | Recuperación con código | Solicitud + confirmación con RF-70; cierra todas las sesiones | T018 |
| US2-E1 | Cambio administrativo | Mismo endpoint de cambio con rol `ADMINISTRADOR`; la recuperación no aplica | T019 |
| Edge 1 | Nueva contraseña no cumple requisitos | 400 `VALIDATION_ERROR` | T011 |
| Edge 2 | Recuperar sin conocer la actual | Flujo de recuperación (no pide la actual) | T018 |
| Edge 3 | Reutilizar la contraseña actual | Rechazar con `PASSWORD_REUSED` (decisión provisional) | T011 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF01 Seguridad | ☑ | Hash de la nueva contraseña; nunca en logs |
| RNF02 Autenticación | ☑ | Exige la actual o un código verificado |
| RNF03 Autorización | ☑ | El administrador no usa recuperación |
| RNF04 Privacidad | ☑ | Recuperación con respuesta uniforme (RF-70 FR-006) |
| RNF07 Rendimiento | ☑ | ≤ 3 s |
| RNF15 Fiabilidad | ☑ | Cambio y revocación en una transacción |
| RNF21 Auditoría | ☑ | Decisión pendiente: registrar el cambio de contraseña |
| RNF24 Errores claros | ☑ | Códigos específicos |
| RNF27 Integridad | ☑ | `Idempotency-Key` en las acciones que cambian la contraseña |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Usuario | Escribe (`password_hash`) | — |
| Sesion | Escribe (`revoked_at`) | Excepto la actual |
| Redis | Escribe | `sv:{userId}` |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Cambiar con la actual | Cliente, vendedores, domiciliario, administrador | Cuenta propia |
| Solicitar / confirmar recuperación | Público | Celular verificado de una cuenta no administradora |

## API Contracts

### POST /api/v1/auth/password/change

- **Rol**: autenticado (todos).

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| X-Device-Id | ✅ | Identificador de instalación |

- **Body**: `{ "passwordActual": "...", "passwordNueva": "..." }`
- **Respuestas**: `200` con `{ "accessToken", "refreshToken" }` nuevos y `Cache-Control: no-store`.
- **Errores**:

| HTTP | `code` | Cuándo | Mensaje |
|---|---|---|---|
| 400 | `INVALID_CURRENT_PASSWORD` | La actual no coincide | "La contraseña actual es incorrecta" |
| 400 | `VALIDATION_ERROR` | No cumple los requisitos | Indica los requisitos |
| 400 | `PASSWORD_REUSED` | Igual a la actual | "Elige una contraseña distinta" |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Clave repetida con otro cuerpo | — |
| 429 | `RATE_LIMITED` | Muchos intentos | `Retry-After` |

### POST /api/v1/auth/password/recover/request

- **Rol**: público · **Cabecera**: `X-Device-Id` · **Body**: `{ "celular": "+57..." }`
- **Respuestas**: `202` idéntica exista o no la cuenta. **Errores**: `400 VALIDATION_ERROR`, `429 RATE_LIMITED`, `503 EXTERNAL_SERVICE_UNAVAILABLE`.

### POST /api/v1/auth/password/recover/confirm

- **Rol**: público · **Cabeceras**: `X-Device-Id`, `Idempotency-Key`.
- **Body**: `{ "celular": "+57...", "codigo": "123456", "passwordNueva": "..." }`
- **Respuestas**: `200` con tokens nuevos (cierra las demás sesiones). **Errores**: `OTP_INVALID`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXHAUSTED` (RF-70), `VALIDATION_ERROR`, `422 IDEMPOTENCY_KEY_REUSED`, `429 RATE_LIMITED`.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El cambio de contraseña no emite eventos de dominio |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el reintento con la misma `Idempotency-Key` devuelve la respuesta guardada; no hay cola offline (requiere conexión).
- **Concurrencia (RNF08)**: dos cambios simultáneos: el segundo vuelve a validar la actual y falla; `sv` se incrementa de forma atómica.
- **Idempotencia (RNF27)**: Redis + comparación del hash.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Actual incorrecta | 400, sin cambios | FR-004 |
| Nueva inválida | 400 con requisitos | FR-002 |
| Código inválido o vencido | Errores `OTP_*` | RF-70 |
| Redis caído al invalidar sesiones | El cambio falla (no se confirma sin invalidar) — decisión pendiente | RNF15 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `PasswordPolicy`, reutilización | FR-002, Edge 3 |
| Integración | Endpoints, `sv`, revocación | US1, US2, SC-001 |
| E2E | Cambio con la actual y recuperación con código | US1-E1, US1-E3 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-09-cambio-de-contrasena/
├── RF-09-cambio-de-contrasena.md   # spec
└── plan.md                         # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/cuentas/
├── application/ports/in/         # CambiarContrasenaPort, SolicitarRecuperacionPort, ConfirmarRecuperacionPort
├── application/usecase/          # CambiarContrasenaUseCase, SolicitarRecuperacionUseCase, ConfirmarRecuperacionUseCase
└── infrastructure/adapters/in/web/   # PasswordController
frontend/src/app/(auth)/recuperar-contrasena.tsx       # solicitar código y fijar contraseña nueva (sin sesión)
frontend/src/app/(comun)/cambiar-contrasena.tsx        # grupo (comun): pantallas compartidas por todos los roles autenticados
frontend/src/services/auth/contrasena.ts               # change, recover/request, recover/confirm
frontend/__tests__/auth/contrasena.test.tsx
```

**Structure Decision**: módulo `cuentas`; reutiliza `PasswordPolicy` (RF-07), `SesionRepositoryPort` (RF-10) y `ConfirmarVerificacionPort` (RF-70).

## Phase 1: Setup

- [ ] T001 Confirmar `PasswordPolicy`, `sv` y `ConfirmarVerificacionPort`
- [ ] T00A Renombrar `/auth/cambio-contrasena` y `/auth/recuperar-contrasena` a `/auth/password/change` y `/auth/password/recover/request|confirm`; separar solicitar y confirmar la recuperación

## Phase 2: Foundational

- [ ] T002 Método de revocación de sesiones excepto la actual en `SesionRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Usuario cambia su contraseña (Priority: P1)

**Goal**: cambiar con la actual o recuperar con código y cerrar las demás sesiones.
**Independent Test**: cambiar con la actual y verificar que solo la nueva inicia sesión.
**Cubre**: FR-001–FR-004, SC-001–SC-002, US1-E1–E3, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de cambio exitoso y con actual incorrecta en `backend/src/test/java/co/edu/napa/cuentas/CambioContrasenaIT.java`
- [ ] T011 [P] [US1] Test parametrizado de requisitos mínimos y reutilización en `.../cuentas/PasswordPolicyTest.java`
- [ ] T012 [P] [US1] Test de que las demás sesiones quedan cerradas (SC-001) en `.../cuentas/CierreSesionesIT.java`
- [ ] T018a [P] [US1] Test de recuperación con código en `.../cuentas/RecuperacionContrasenaIT.java`

### Implementation

- [ ] T013 [US1] `CambiarContrasenaUseCase` en `cuentas/application/usecase/CambiarContrasenaUseCase.java`
- [ ] T014 [US1] Endpoint `POST /auth/password/change` en `.../in/web/PasswordController.java`
- [ ] T015 [US1] Validaciones y manejo de errores
- [ ] T016 [US1] `SolicitarRecuperacionUseCase` y endpoint `/recover/request`
- [ ] T017 [US1] Incrementar `sv`, revocar refresh excepto el actual y emitir tokens nuevos
- [ ] T018 [US1] `ConfirmarRecuperacionUseCase` y endpoint `/recover/confirm` (cierra todas las sesiones)
- [ ] T021 [P] [US1] Test de las pantallas (requisitos, errores `INVALID_CURRENT_PASSWORD` y `OTP_*`, reemplazo de tokens tras el cambio) en `frontend/__tests__/auth/contrasena.test.tsx`
- [ ] T022 [US1] Servicio `frontend/src/services/auth/contrasena.ts` (envía `Idempotency-Key`)
- [ ] T020 [US1] Pantallas `frontend/src/app/(comun)/cambiar-contrasena.tsx` y `frontend/src/app/(auth)/recuperar-contrasena.tsx`

**Checkpoint**: US1 funcional.

## Phase 4: User Story 2 - Administrador cambia su contraseña (Priority: P1)

**Goal**: cambio de la contraseña administrativa.
**Independent Test**: cambiar la contraseña como administrador y verificar que sus sesiones previas quedan invalidadas.
**Cubre**: US2-E1.

### Tests (antes de implementar)

- [ ] T019a [P] [US2] Test del cambio administrativo y de que el administrador no puede recuperar con código en `.../cuentas/CambioContrasenaAdminIT.java`

### Implementation

- [ ] T019 [US2] Regla en `SolicitarRecuperacionUseCase`: no enviar código a cuentas administradoras (respuesta uniforme)

## Phase N: Polish & Cross-Cutting

- [ ] T030 Revisar privacidad y permisos (RNF03, RNF04)
- [ ] T031 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → Polish.
- Dependencias entre RF: requiere RF-10, RF-70 y RF-07.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Requisitos de la contraseña | FR-002 | **Cerrado (D14)**: 8–128 caracteres, sin reglas de composición | Equipo |
| ¿Se rechaza reutilizar la contraseña actual? | Edge 3 | Rechazar | Equipo |
| ¿Se registra el cambio de contraseña para auditoría? | RNF21 | Pendiente | Equipo |
| Si Redis cae al invalidar sesiones | RNF15 | Pendiente | Equipo |

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
# Implementation Plan: Registro de cuenta

**Date**: 2026-10-20
**Spec**: [spec.md](./RF-07-registro-de-cuenta.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [specs/general/spec-general.md](../general/spec-general.md) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-07
**Historias de usuario**: HU-24
**Prioridad (la mayor de la spec)**: P1
**Depende de**: foundations F0–F2 (Usuario, Sesion, JWT, Redis), RF-70
**Habilita a**: RF-10, RF-09, RF-08 y todos los flujos autenticados

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Registro de una persona con rol (cliente, vendedor ambulante, vendedor de punto fijo o domiciliario): se crea la cuenta en `PENDIENTE_VERIFICACION`, se solicita la verificación del celular a RF-70 y la cuenta pasa a `ACTIVA` (con sesión iniciada) cuando RF-70 confirma el código.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Puede registrarse con rol `CLIENTE` |
| Vendedor (ambulante / punto fijo) | Puede registrarse con su rol de vendedor |
| Domiciliario | Puede registrarse con rol `DOMICILIARIO` |
| Administrador | No se registra por la API: se siembra desde la BD |

## Technical Context

Heredado de `specs/templates/plan-template.md` (stack fijo del proyecto). Objetivos propios:

- Registro responde ≤ 1,5 s p95 (el envío del código es asíncrono respecto a la respuesta; SC-001 de RF-70).
- El flujo completo de registro (formulario + código) en < 2 min (SC-001).
- Contraseña con hash (RNF01); Argon2id o BCrypt: **decisión pendiente** (foundations lista ambos).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Registrar con nombre, celular, contraseña, correo opcional y rol | `POST /auth/register`, `RegistrarCuentaUseCase`, validación de roles permitidos | T010, T013, T014 |
| FR-002 | Celular (y correo) no registrados | Únicos en BD + chequeo previo; `PHONE_ALREADY_REGISTERED` / `EMAIL_ALREADY_REGISTERED` | T011, T013, T015 |
| FR-003 | Confirmación de contraseña coincide | Campo `confirmPassword` validado en el caso de uso (decisión provisional: se valida en servidor) | T010, T015 |
| FR-004 | Sesión inmediata tras verificar | La emite RF-70 al confirmar el código (`ActivarCuentaPort`); este plan expone el puerto | T016 |
| FR-005 | Funcionalidades según rol | `usuario.rol` es la fuente; RNF03 lo aplica en cada RF | T013 |
| FR-006 | Celular obligatorio y verificado; cuenta activa solo tras verificar | Estado `PENDIENTE_VERIFICACION`; solicitud a `VerificarCelularPort` vía RF-70 | T013, T016 |
| SC-001 | Registro en < 2 min | Formulario de 5 campos (RNF09); respuesta ≤ 1,5 s p95 | T020, T030 |
| SC-002 | 100 % rechaza duplicados | Únicos en BD + prueba | T011, T011a |
| US1-E1 | Registro exitoso | Escenario completo registro → código → sesión | T012a, T031 |
| US1-E2 | Dato ya usado | 409 con código específico | T012b |
| US1-E3 | Cuenta sin verificar no inicia sesión | Lo aplica RF-10 (`ACCOUNT_NOT_VERIFIED`); aquí se prueba el estado | T012c |
| Edge 1 | Contraseña y confirmación no coinciden | 400 `VALIDATION_ERROR` | T015 |
| Edge 2 | Sin rol | 400 `VALIDATION_ERROR` | T015 |
| Edge 3 | Registro interrumpido | La cuenta solo se crea al enviar el formulario completo (una sola llamada); no hay datos parciales en servidor | T013 |
| Edge 4 | Cuenta que nunca verifica el celular | Decisión pendiente (ver Open Questions) | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF01 Seguridad | ☑ | Hash de contraseña; nunca se registra en logs |
| RNF02 Autenticación | ☑ | La cuenta no inicia sesión hasta verificar |
| RNF03 Autorización por rol | ☑ | Solo 4 roles permitidos; `ADMINISTRADOR` rechazado con `VALIDATION_ERROR` |
| RNF04 Privacidad | ☑ | Ley 1581: el formulario muestra el aviso de tratamiento de datos (decisión pendiente) |
| RNF07 Rendimiento ≤ 3 s | ☑ | Respuesta ≤ 1,5 s p95 |
| RNF09 Usabilidad | ☑ | Formulario corto, rol elegido con 4 opciones |
| RNF16 Trazabilidad | ☑ | `created_at`/`updated_at` |
| RNF24 Errores claros | ☑ | Códigos de error con mensaje en español |
| RNF27 Integridad | ☑ | Únicos en BD como garantía final |
| RNF28 Conectividad | ☑ | `Idempotency-Key` + `X-Device-Id`; reintento seguro tras corte de red |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende |
|---|---|---|
| Usuario | Escribe | `celular` único; `correo` único opcional; `rol` enumerado; `estado` |
| Sesion | Escribe (al activar, vía RF-70) | `device_id`, `token_hash` |
| Mensaje | Escribe (vía RF-70) | Sin código |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento |
|---|---|---|---|---|---|
| Usuario | (nuevo) | `PENDIENTE_VERIFICACION` | Persona que se registra | Datos válidos y no duplicados | Ninguno (la verificación no usa outbox) |
| Usuario | `PENDIENTE_VERIFICACION` | `ACTIVA` | RF-70 al confirmar el código | Código correcto | Ninguno |

**Transiciones inválidas**: se rechazan con `INVALID_STATE`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Registrarse | Público (sin sesión) | Rol solicitado ∈ {CLIENTE, VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO, DOMICILIARIO} |

## API Contracts

### POST /api/v1/auth/register

- **Rol**: público
- **Path params**: ninguno
- **Query params**: ninguno

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| X-Device-Id | ✅ | Identificador de instalación |
| Idempotency-Key | ✅ | UUID (clave por dispositivo, sin sesión) |
| Content-Type | ✅ | `application/json` |

- **Body**:
  ```json
  {
    "nombre": "Juan Pérez",
    "celular": "+573001234567",
    "correo": "juan@ejemplo.com",
    "password": "********",
    "confirmPassword": "********",
    "rol": "VENDEDOR_AMBULANTE"
  }
  ```
  `correo` es opcional.
- **Respuestas exitosas**:

| HTTP | Cuándo | Headers | Body |
|---|---|---|---|
| 201 | Cuenta creada pendiente de verificación | `Location: /api/v1/usuarios/me`, `X-Request-Id`, `Cache-Control: no-store` | `{ "usuarioId", "estado": "PENDIENTE_VERIFICACION", "verificacion": { "mensajeId", "canal", "reenvioDisponibleEn" } }` |
| 200 + `Idempotent-Replayed: true` | Misma clave y mismo cuerpo | igual | respuesta guardada |

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje al usuario |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Campo vacío, formato inválido, contraseñas distintas, rol faltante o no permitido, contraseña débil | Indica el campo inválido |
| 409 | `PHONE_ALREADY_REGISTERED` | Celular ya usado | "Este celular ya está registrado" |
| 409 | `EMAIL_ALREADY_REGISTERED` | Correo ya usado | "Este correo ya está registrado" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave, cuerpo distinto | "Solicitud repetida con datos distintos" |
| 429 | `RATE_LIMITED` | Demasiados registros desde la IP/dispositivo o límite de envío de RF-70 | Indica `Retry-After` |
| 503 | `EXTERNAL_SERVICE_UNAVAILABLE` | Fallo de Twilio al solicitar el código | "No pudimos enviar el código; intenta de nuevo" (la cuenta queda creada) |

`401` y `403` no aplican (endpoint público); `404` no aplica.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El registro y la verificación del celular no usan eventos (eventos.md, regla 8) |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Operaciones que deben tolerar mala conexión (RNF28)**: el registro es la primera operación y ocurre con conexión; si se corta tras enviar, el reintento con la misma `Idempotency-Key` devuelve la respuesta guardada.
- **Sincronización / reintento**: la app reutiliza la clave generada al tocar "Registrarme"; no se encola offline (requiere conexión para recibir el código).
- **Concurrencia (RNF08)**: dos registros simultáneos con el mismo celular: gana el primero; el segundo recibe 409 por la restricción única.
- **Idempotencia (RNF27)**: Redis `idem:{deviceId}:{key}` + únicos de BD.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Datos inválidos / contraseñas distintas / sin rol | 400 con el campo inválido | FR-001, FR-003, Edge 1–2 |
| Celular o correo en uso | 409 específico | FR-002 |
| Twilio caído al pedir el código | Cuenta creada; respuesta 201 con `verificacion.canal` en estado fallido y opción de reenviar (RF-70) | RNF19 |
| Rol `ADMINISTRADOR` solicitado | 400 | spec-general §1 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Validación de rol, contraseña, coincidencia, normalización del celular | FR-001, FR-003, Edge 1–2 |
| Integración | Endpoint, únicos en BD, idempotencia, estado inicial | FR-002, SC-002, US1-E1, E2 |
| E2E | Registro → código (adaptador consola) → sesión activa → panel | US1-E1, E3 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-07-registro-de-cuenta/
├── RF-07-registro-de-cuenta.md   # spec (QUÉ)
└── plan.md                       # este archivo (CÓMO)
```

### Source Code

```text
backend/src/main/java/co/edu/napa/cuenta/
├── domain/model/                 # Usuario, Rol, EstadoCuenta, Celular (value object), PasswordPolicy
├── domain/exception/             # CelularYaRegistradoException, CorreoYaRegistradoException
├── application/ports/in/         # RegistrarCuentaPort, ActivarCuentaPort
├── application/ports/out/        # UsuarioRepositoryPort, PasswordHasherPort, VerificarCelularPort (RF-70)
├── application/usecase/          # RegistrarCuentaUseCase, ActivarCuentaUseCase
└── infrastructure/adapters/
    ├── in/web/                   # AuthController (register), DTOs
    └── out/persistence/          # UsuarioEntity, repositorio JPA
backend/src/main/resources/db/migration/   # V2__usuario.sql (si no la creó foundations F020)
frontend/src/screens/                       # Registro (pantalla compartida por los 4 roles)
```

**Structure Decision**: módulo `cuenta`. `ActivarCuentaPort` lo invoca RF-70 al confirmar el código, sin que `cuenta` conozca a Twilio. Los controladores solo llaman puertos `in`.

## Phase 1: Setup

- [ ] T001 Confirmar que `Usuario` y `Sesion` existen (foundations F020); si no, migración `V2__usuario.sql`

## Phase 2: Foundational

- [ ] T002 `PasswordPolicy` (reglas mínimas: decisión pendiente) y `PasswordHasherPort` + adaptador en `cuenta/infrastructure/adapters/out/`
- [ ] T003 Normalización de celular (E.164, Colombia por defecto) en `cuenta/domain/model/Celular.java`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Persona crea una cuenta (Priority: P1)

**Goal**: Cuenta creada y activada tras verificar el celular.
**Independent Test**: registrar, recibir el código, confirmarlo y verificar que la cuenta es `ACTIVA` y se inicia sesión.
**Cubre**: FR-001–FR-006, SC-001–SC-002, US1-E1–E3, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test unitario de validación (rol, contraseñas, formato) en `backend/src/test/java/co/edu/napa/cuenta/RegistrarCuentaUseCaseTest.java`
- [ ] T011 [P] [US1] Test de integración de únicos de celular y correo en `.../cuenta/UsuarioUniquenessIT.java`
- [ ] T011a [P] [US1] Test de que el 100 % de duplicados se rechaza (SC-002) en `.../cuenta/RegistroDuplicadoIT.java`
- [ ] T012a [P] [US1] Test de integración del escenario 1 (registro → verificación → sesión) en `.../cuenta/RegistroE2EIT.java`
- [ ] T012b [P] [US1] Test de integración del escenario 2 (dato ya usado) en `.../cuenta/RegistroDuplicadoIT.java`
- [ ] T012c [P] [US1] Test del escenario 3 (cuenta `PENDIENTE_VERIFICACION` no activa) en `.../cuenta/EstadoInicialIT.java`

### Implementation

- [ ] T013 [US1] `Usuario` (dominio) y `RegistrarCuentaUseCase` en `cuenta/domain/model/Usuario.java` y `cuenta/application/usecase/RegistrarCuentaUseCase.java`
- [ ] T014 [US1] Endpoint `POST /auth/register` en `cuenta/infrastructure/adapters/in/web/AuthController.java`
- [ ] T015 [US1] Validaciones y manejo de errores (400/409) en `cuenta/infrastructure/adapters/in/web/`
- [ ] T016 [US1] `ActivarCuentaUseCase` y `ActivarCuentaPort` (invocado por RF-70) en `cuenta/application/`
- [ ] T017 [US1] Idempotencia por `X-Device-Id` en `shared/infrastructure/idempotency/` (extender `IdempotencyFilter`)
- [ ] T020 [US1] Pantalla de registro en `frontend/src/screens/Registro.tsx`

**Checkpoint**: US1 funcional y probable por separado.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Medir respuesta ≤ 1,5 s p95 (SC-001)
- [ ] T031 Prueba en un móvil de gama baja (RNF31)
- [ ] T032 Revisar privacidad y permisos (RNF03, RNF04)
- [ ] T033 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-70 (verificación) para activar la cuenta; lo habilita RF-10.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Requisitos mínimos de la contraseña (longitud, complejidad); la spec solo dice "definidos" | FR-003 de RF-09, FR-001 | Ninguna: definirlos antes de T002 | Equipo |
| Argon2id o BCrypt | RNF01 | Pendiente (foundations lista ambos) | Equipo |
| Cuenta que nunca verifica el celular: ¿se borra tras X días y libera el celular? | Edge 4; celular único bloqueado | Ninguna | Equipo |
| Si el celular está en una cuenta `PENDIENTE_VERIFICACION`, ¿se rechaza (409) o se reenvía el código? | FR-002 | Rechazar (409) | Equipo |
| Idempotencia sin sesión: clave por `X-Device-Id` | spec-general §2.1 | `idem:{deviceId}:{key}` | Equipo |
| ¿La app muestra el aviso de tratamiento de datos (Ley 1581) en el registro? | RNF04 | Pendiente | Equipo |
| `confirmPassword` se valida también en servidor (la spec solo pide validar que coincidan) | FR-003 | Sí | Equipo |

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
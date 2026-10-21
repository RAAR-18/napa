# Implementation Plan: Inicio de sesión

**Date**: 2026-10-20
**Spec**: [spec.md](./RF-10-inicio-de-sesion.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [specs/general/spec-general.md](../general/spec-general.md) · [eventos](../general/eventos.md) 
**Requerimiento funcional**: RF-10
**Historias de usuario**: HU-27, HU-29
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-07, RF-70, foundations F2 (JWT, `sv`, Redis)
**Habilita a**: todos los flujos autenticados

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Inicio de sesión con celular o correo y contraseña: valida credenciales, limita intentos fallidos con contadores en Redis, rechaza cuentas no verificadas, entrega JWT de acceso (15 min, claim `sv`) y refresh rotativo por dispositivo, e indica el panel según el rol. El administrador (sembrado desde la BD) entra con correo y contraseña.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Inicia sesión |
| Vendedor (ambulante / punto fijo) | Inicia sesión |
| Domiciliario | Inicia sesión |
| Administrador | Inicia sesión solo con correo y contraseña |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Llegar al panel en < 5 s (SC-001); verificación de JWT ≤ 10 ms p95 (incluye `GET` a Redis por `sv`).
- Acceso 15 min; refresh opaco guardado con hash por dispositivo.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Login con celular/correo y contraseña | `POST /auth/login`, `IniciarSesionUseCase` | T010, T013, T014 |
| FR-002 | Validar credenciales antes de dar acceso | Verificación del hash en el caso de uso | T013 |
| FR-003 | Redirigir al panel según rol | La respuesta incluye `rol`; la app navega al panel del rol | T014, T020 |
| FR-004 | Limitar intentos fallidos (también OTP) | Contador `login-att:{identificador}` y por IP en Redis; el límite OTP lo cubre RF-70 | T011, T015 |
| FR-005 | Bloquear cuenta con celular sin verificar; admin solo con contraseña | `ACCOUNT_NOT_VERIFIED` (403); el admin no pasa por verificación | T012, T013 |
| SC-001 | Panel en < 5 s | Medición en flujo completo | T030 |
| SC-002 | 100 % rechaza credenciales incorrectas | Prueba de integración | T010 |
| US1-E1 | Login exitoso | Tokens + rol | T010 |
| US1-E2 | Contraseña incorrecta con mensaje genérico | `INVALID_CREDENTIALS` sin indicar el dato | T010 |
| US1-E3 | Celular sin verificar | `ACCOUNT_NOT_VERIFIED` y envío a la verificación (RF-70) | T012 |
| US2-E1 | Login administrativo | Mismo endpoint, rol `ADMINISTRADOR`, correo | T016 |
| Edge 1 | Varios intentos fallidos | Bloqueo temporal (N y duración: decisión pendiente) | T011 |
| Edge 2 | Dispositivo o ubicación no habitual | Decisión pendiente | — |
| Edge 3 | Cuenta eliminada o inhabilitada | `INVALID_CREDENTIALS` (genérico) | T017 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF01 Seguridad | ☑ | Verificación de hash en tiempo constante |
| RNF02 Autenticación | ☑ | Validación de credenciales antes de dar acceso |
| RNF03 Autorización | ☑ | El claim `role` del JWT alimenta `@PreAuthorize` |
| RNF04 Privacidad | ☑ | Mensaje genérico; no revela existencia de la cuenta |
| RNF07 Rendimiento | ☑ | Login ≤ 3 s; JWT ≤ 10 ms p95 |
| RNF16 Trazabilidad | ☑ | `Sesion` con `created_at`, `expires_at`, `revoked_at` |
| RNF24 Errores claros | ☑ | Mensajes en español |
| RNF28 Conectividad | ☑ | Refresh rotativo permite reanudar tras corte |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Usuario | Lee | `estado`, `rol`, `password_hash`; el administrador también tiene `celular` y `correo` (se cargan al sembrarlo en la BD) |
| Sesion | Escribe | `token_hash`, `device_id`, `expires_at`, `revoked_at` |
| Redis | Lee/escribe | `login-att:*`, `sv:{userId}` |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte. Crea `Sesion`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Iniciar sesión | Público | Cuenta `ACTIVA` |
| Refrescar sesión | Autenticado (refresh válido) | El refresh pertenece al `X-Device-Id` |
| Cerrar sesión | Autenticado | Sesión propia |

## API Contracts

### POST /api/v1/auth/login

- **Rol**: público · **Query params**: ninguno.

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| X-Device-Id | ✅ | Identificador de instalación |
| Content-Type | ✅ | `application/json` |

- **Body**: `{ "identificador": "+573001234567 | correo@dominio", "password": "********" }`
- **Respuestas exitosas**: `200`, `Cache-Control: no-store`, `X-Request-Id`; body `{ "accessToken", "refreshToken", "expiresIn", "usuario": { "id", "nombre", "rol" } }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Campos vacíos | Indica el campo |
| 401 | `INVALID_CREDENTIALS` | Credenciales incorrectas, cuenta inexistente o eliminada | "Datos incorrectos" (sin indicar cuál) |
| 403 | `ACCOUNT_NOT_VERIFIED` | Celular sin verificar | "Verifica tu celular para continuar" |
| 429 | `RATE_LIMITED` | Intentos fallidos agotados | `Retry-After` |

### POST /api/v1/auth/refresh

- **Rol**: autenticado con refresh · **Body**: `{ "refreshToken" }` · **Cabecera**: `X-Device-Id`.
- **Respuestas**: `200` con tokens nuevos (rotación). **Errores**: `401 UNAUTHORIZED` / `TOKEN_EXPIRED` (refresh inválido, vencido o ya usado).

### POST /api/v1/auth/logout

- **Rol**: autenticado · **Cabecera**: `Authorization`, `X-Device-Id`. **Respuestas**: `204`. Revoca la sesión del dispositivo.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El inicio de sesión no emite eventos de dominio |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el refresh rotativo permite reanudar; el login requiere conexión. Idempotencia no se exige (exento en spec-general, a confirmar).
- **Concurrencia (RNF08)**: dos refresh simultáneos con el mismo token: el primero rota, el segundo recibe 401 (rotación atómica en BD con `UPDATE ... WHERE revoked_at IS NULL`).
- **Fuerza bruta**: contador atómico en Redis (`INCR` + `EXPIRE`).

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Credenciales incorrectas | 401 genérico | US1-E2 |
| Celular sin verificar | 403 y camino a verificación | US1-E3, FR-005 |
| Intentos agotados | 429 con `Retry-After` | FR-004 |
| Redis caído | La versión de sesión cae a la BD (foundations D8); el límite de intentos falla cerrado (decisión pendiente) | RNF15 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Comparación de hash, contador de intentos | FR-002, FR-004 |
| Integración | Endpoints, `Sesion`, `sv`, Redis | US1, US2, SC-002 |
| Seguridad | Fuerza bruta, enumeración de cuentas | Edge 1, Edge 3 |
| E2E | Login hasta el panel por rol | US1-E1, US2-E1 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-10-inicio-de-sesion/
├── RF-10-inicio-de-sesion.md   # spec
└── plan.md                     # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/cuenta/
├── domain/model/                 # Sesion, TokenPair
├── application/ports/in/         # IniciarSesionPort, RefrescarSesionPort, CerrarSesionPort
├── application/ports/out/        # SesionRepositoryPort, TokenPort, LimitadorLoginPort
├── application/usecase/          # IniciarSesionUseCase, RefrescarSesionUseCase, CerrarSesionUseCase
└── infrastructure/adapters/
    ├── in/web/                   # AuthController (login, refresh, logout)
    ├── out/persistence/          # SesionEntity
    └── out/cache/                # contadores y `sv` en Redis
backend/src/main/java/co/edu/napa/shared/infrastructure/security/   # JwtFilter, SessionVersionFilter
frontend/src/screens/Login.tsx · frontend/src/services/auth/
```

**Structure Decision**: módulo `cuenta`; el filtro JWT vive en `shared` y es compartido por todos los módulos.

## Phase 1: Setup

- [ ] T001 Confirmar `Usuario`, `Sesion` y claves JWT (foundations F020, F024)

## Phase 2: Foundational

- [ ] T002 `LimitadorLoginPort` y adaptador Redis en `cuenta/infrastructure/adapters/out/cache/`
- [ ] T003 Sembrado de la cuenta administradora con correo y contraseña (variable de entorno o migración) en `backend/src/main/resources/db/migration/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Usuario inicia sesión (Priority: P1)

**Goal**: autenticar y entregar sesión con el panel del rol.
**Independent Test**: login con una cuenta activa y verificación de la redirección según rol.
**Cubre**: FR-001–FR-005, SC-001–SC-002, US1-E1–E3, Edge 1, Edge 3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de login exitoso e incorrecto (SC-002) en `backend/src/test/java/co/edu/napa/cuenta/LoginIT.java`
- [ ] T011 [P] [US1] Test de bloqueo tras N fallos en `.../cuenta/LoginFuerzaBrutaIT.java`
- [ ] T012 [P] [US1] Test de cuenta sin verificar (403) en `.../cuenta/LoginNoVerificadaIT.java`
- [ ] T017 [P] [US1] Test de cuenta eliminada y de no enumeración de cuentas en `.../cuenta/LoginEnumeracionIT.java`

### Implementation

- [ ] T013 [US1] `IniciarSesionUseCase` en `cuenta/application/usecase/IniciarSesionUseCase.java`
- [ ] T014 [US1] Endpoint `POST /auth/login` en `cuenta/infrastructure/adapters/in/web/AuthController.java`
- [ ] T015 [US1] Límite de intentos fallidos y `429` con `Retry-After`
- [ ] T018 [US1] `RefrescarSesionUseCase` y `CerrarSesionUseCase` con endpoints `POST /auth/refresh` y `POST /auth/logout`
- [ ] T019 [US1] `JwtFilter` y `SessionVersionFilter` en `shared/infrastructure/security/` (si no los entregó foundations F024)
- [ ] T020 [US1] Pantalla de login y navegación por rol en `frontend/src/screens/Login.tsx`

**Checkpoint**: US1 funcional.

## Phase 4: User Story 2 - Administrador inicia sesión (Priority: P1)

**Goal**: acceso al panel administrativo.
**Independent Test**: login administrativo con correo y contraseña y acceso al panel.
**Cubre**: US2-E1, FR-005 (administrador).

### Tests (antes de implementar)

- [ ] T016a [P] [US2] Test de login administrativo con correo en `.../cuenta/LoginAdminIT.java`

### Implementation

- [ ] T016 [US2] Rama del caso de uso para `ADMINISTRADOR` sin verificación de celular

## Phase N: Polish & Cross-Cutting

- [ ] T030 Medir login hasta panel < 5 s (SC-001)
- [ ] T031 Revisar privacidad y permisos (RNF03, RNF04)
- [ ] T032 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → Polish.
- Dependencias entre RF: requiere RF-07 (cuentas) y RF-70 (estado verificado); lo habilita todo lo autenticado.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Número de intentos fallidos y duración del bloqueo | FR-004 | Ninguna | Equipo |
| Si Redis cae, ¿el límite de intentos falla abierto o cerrado? | Seguridad vs. disponibilidad | Pendiente | Equipo |
| Inicio desde dispositivo o ubicación no habitual | Edge 2 | Sin tratamiento especial en el MVP | Equipo |
| Login exento de `Idempotency-Key` | spec-general §2.1 | Exento (propuesta) | Equipo |
| Cuenta administradora: se siembra con `celular` y `correo` (decidido); entra con correo y contraseña | Ninguno | Cerrada | Equipo |

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
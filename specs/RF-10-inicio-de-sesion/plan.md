# Implementation Plan: Inicio de sesión

**Date**: 2026-10-20
**Spec**: [RF-10-inicio-de-sesion.md](./RF-10-inicio-de-sesion.md) (fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos.md](../general/eventos.md)
**Requerimiento funcional**: RF-10
**Historias de usuario**: HU-27, HU-29
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-07, RF-70
**Habilita a**: ninguno declarado

> Este documento define **CÓMO** implementar la spec, sin redefinir su comportamiento, el modelo compartido ni los eventos.
> Si una decisión pendiente cambia el comportamiento, corregir primero la spec. No iniciar implementación mientras el Plan Review Checklist tenga elementos pendientes.

## Summary

Implementar Inicio de sesión para todos los usuarios y el administrador en el módulo cuentas, con validaciones de credenciales/estado, contrato REST y persistencia conforme a la spec y los fundamentos compartidos.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene directamente. |
| Vendedor (ambulante / punto fijo) | No interviene directamente. |
| Domiciliario | No interviene directamente. |
| Administrador | Actor directo según la spec. |
| Usuario (rol base) | No interviene directamente. |

## Technical Context

**Language/Version**: Java 21 LTS · Spring Boot 4.1.x · TypeScript 5 · React Native + Expo SDK ≥ 54.
**Primary Dependencies**: Backend: Spring Web, Spring Security 7 + JWT, Spring Data JPA + Hibernate Spatial, Flyway, Spring Data Redis, Jackson, Firebase Admin SDK (FCM), Twilio Verify (solo RF-70) y ArchUnit. Frontend: Expo development build, expo-sqlite, expo-notifications, react-native-maps y Axios. Rutas por `RoutingPort`.
**Storage**: PostgreSQL 16 + PostGIS; Redis 7 (idempotencia, rate-limit, cachés y ubicación en vivo cuando aplique); SQLite local (outbox y caché de lectura).
**Testing**: JUnit 5, Mockito, Testcontainers (PostGIS/Redis), ArchUnit; Jest + React Native Testing Library.
**Target Platform**: Android/iOS y API REST Linux; priorizar móviles de gama baja (RNF31).
**Project Type**: Monorepo planificado `backend/` (hexagonal y modular) + `frontend/`.
**Performance Goals**: operaciones principales ≤ 3 s (RNF07); JWT ≤ 10 ms p95; solicitud de verificación celular ≤ 1,5 s p95 (RF-70); consultas PostGIS ≤ 100 ms p95 con GiST; cumplir además cada SC de esta spec.
**Constraints**: conectividad intermitente con outbox + `Idempotency-Key` (RNF28); pagos solo como confirmación y medios según RNF29; privacidad conforme a Ley 1581 de 2012; mapas/rutas por servicio externo detrás de `RoutingPort`; flujos breves y móviles de gama baja.
**Scale/Scope**: MVP Santa Marta, estimación compartida ≤ 500 vendedores, ≤ 2 000 pedidos/día y ≤ 100 domicilios simultáneos.

## Spec Coverage

| Spec | Descripción corta | Cubierto por (componente / endpoint / regla) | Tareas |
|---|---|---|---|
| FR-001 | El sistema MUST permitir a los usuarios y al administrador iniciar sesión mediante correo/teléfono y contraseña. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/login. | T004, T005 |
| FR-002 | El sistema MUST validar las credenciales antes de conceder acceso a la cuenta. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/login. | T004, T005 |
| FR-003 | El sistema MUST redirigir al usuario autenticado al panel correspondiente a su rol (cliente, vendedor, domiciliario o administrador). | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/login. | T004, T005 |
| FR-004 | El sistema MUST limitar los intentos fallidos consecutivos de inicio de sesión para mitigar ataques de fuerza bruta. Después de 5 fallos dentro de 15 minutos por identificador normalizado y por dirección IP, debe responder `429 RATE_LIMITED` con `Retry-After`. El límite aplica también a los intentos de código de un solo uso (RF-70). | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/login. | T007, T008 |
| FR-005 | El sistema MUST impedir el inicio de sesión de un usuario cuyo celular no esté verificado (RF-70). El administrador inicia sesión solo con contraseña. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/login. | T007, T008 |
| SC-001 | Los usuarios pueden iniciar sesión y llegar a su panel en menos de 5 segundos bajo condiciones normales. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T009 |
| SC-002 | El sistema rechaza el 100% de los intentos de inicio de sesión con credenciales incorrectas. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T009 |
| US1: Inicio de sesión exitoso | el sistema me autentica y me redirige a mi panel según mi rol | Prueba de aceptación aislada de US1. | T003 |
| US1: Intento de inicio de sesión con credenciales incorrectas | el sistema rechaza el acceso y muestra un mensaje de error sin indicar cuál dato es incorrecto | Prueba de aceptación aislada de US1. | T003 |
| US1: Cuenta con el celular sin verificar | el sistema no inicia mi sesión y me lleva a completar la verificación (RF-70) | Prueba de aceptación aislada de US1. | T003 |
| US2: Inicio de sesión administrativo exitoso | el sistema me autentica y me redirige al panel administrativo | Prueba de aceptación aislada de US2. | T006 |
| Edge case 1 | ¿Qué ocurre tras varios intentos consecutivos fallidos de inicio de sesión sobre la misma cuenta? | Tras 5 fallos en 15 minutos por identificador normalizado y por IP, responder `429 RATE_LIMITED` con `Retry-After`. | Decisión acordada |
| Edge case 2 | ¿Cómo maneja el sistema el inicio de sesión desde un dispositivo o ubicación no habitual? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 3 | ¿Qué sucede si la cuenta fue eliminada o inhabilitada y se intenta iniciar sesión con sus credenciales? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización por rol | Sí | Sí · rol y pertenencia se validan en el caso de uso. |
| RNF04 Privacidad / ubicación | Sí | Sí · minimizar datos personales; limitar datos de ubicación a participantes autorizados y solo cuando la feature los maneje. |
| RNF07 Rendimiento ≤ 3 s | Sí | Sí · medir el endpoint y cubrir el SC de latencia de la spec. |
| RNF08 Concurrencia | No | No aplica una escritura concurrente crítica; mantener lectura consistente. |
| RNF16 Trazabilidad | Sí | Sí · timestamps comunes y, en cambios de estado, EventoTrazabilidad transaccional. |
| RNF18 Notificaciones | No | No emite notificaciones salvo que el catálogo compartido lo indique. |
| RNF21 Auditoría | No | No añade auditoría de cambios; respetar timestamps y registros del modelo compartido. |
| RNF23 Consistencia de estados | No | No altera estados de Pedido, Domicilio, Pago o Reporte. |
| RNF24 Mensajes de error claros | Sí | Sí · código común y mensaje español con traceId; no exponer datos sensibles. |
| RNF27 Integridad de pagos | No | No manipula pagos. |
| RNF28 Conectividad intermitente | Sí | Sí · acción reintentable con outbox local e Idempotency-Key; servidor autoritativo. |
| RNF29 Medios de pago | No | No aplica. |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende la feature |
|---|---|---|
| Sesion | Lee / escribe según el caso de uso | Representa el acceso autenticado de un usuario o administrador, vinculada a su cuenta y su rol. |

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Ejecutar Inicio de sesión | Público | La cuenta se identifica mediante correo/celular y contraseña; las cuentas pendientes o eliminadas no reciben sesión. |

## API Contracts

### POST /api/v1/auth/login

**Rol**: Administrador.
**Path params**: Ninguno.
**Query params**: Ninguno salvo los definidos por la spec.

| Parámetro | Tipo | Obligatorio | Valores / ejemplo | Descripción |
|---|---|:---:|---|---|
| page, size, sort | int, int, string | Para listados | 0, 20, fecha,desc | Convención compartida; máximo size 100. |
| filtros de la feature | Según spec | Según spec | Solo valores documentados | Filtro desconocido: 400. |

**Headers de request**:

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | Según operación | La solicitud pública de registro/login/OTP no exige sesión; confirmar un cambio/recuperación aplica los requisitos de su propósito y las demás rutas requieren JWT. |
| Idempotency-Key | No | UUID; obligatoria según las convenciones comunes para mutaciones. |
| X-Device-Id | Sí | Identificador estable de instalación asociado a la sesión creada. |
| X-Request-Id | No | Identificador opcional, devuelto siempre como `X-Request-Id`. |

**Body**: DTO con los datos estrictamente requeridos por FR-001, FR-002, FR-003, FR-004, FR-005; validar en el caso de uso y no incluir datos no definidos por la fuente funcional.

**Respuestas exitosas**:

| HTTP | Cuándo | Headers de response | Body |
|---|---|---|---|
| 200 / 201 / 202 | 201 si crea recurso; 200/202 si confirma o inicia acción según convención común. | `X-Request-Id`; `Location` en 201; `Cache-Control: no-store` para datos sensibles. | Representación definida por la spec o sin cuerpo en 204. |

**Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje al usuario |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Campo inválido, filtro desconocido o header obligatorio ausente. | Indicar el dato que debe corregirse. |
| 401 | `UNAUTHORIZED` | Sesión ausente, expirada o revocada. | Solicitar iniciar sesión nuevamente. |
| 403 | `FORBIDDEN` | Rol o pertenencia no autorizados. | No se puede realizar esta acción. |
| 404 | `NOT_FOUND` | Recurso inexistente o no visible según regla común. | No se encontró el recurso. |
| 409 | `INVALID_STATE` / `ALREADY_CONFIRMED` / `CONFLICT` | Estado incompatible o duplicado de negocio. | Informar estado actual cuando aplique. |
| 422 | `IDEMPOTENCY_KEY_REUSED` / `BUSINESS_RULE` | Clave reutilizada con otro cuerpo o regla de negocio incumplida. | Explicar el conflicto sin revelar datos privados. |
| 429 | `RATE_LIMITED` | Límite especificado por la feature o plataforma. | Indicar espera con `Retry-After`. |

## Events

| Evento emitido (eventos.md) | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | No aplica | Esta feature no emite evento según eventos.md; RF-70 usa llamada directa al puerto de verificación. |

**Eventos que consume esta feature**: ninguno salvo que la spec y eventos.md indiquen lo contrario.

Los listeners no se planifican en el módulo emisor; pertenecen al módulo que reacciona (especialmente RF-38).

## Offline & Concurrency Strategy

- **Operaciones con mala conexión (RNF28)**: encolar mutaciones reintentables en SQLite y mostrar estado pendiente.
- **Sincronización / reintento**: `Idempotency-Key` estable por operación, backoff y revalidación de autorización/estado en servidor; servidor como fuente de verdad.
- **Concurrencia (RNF08)**: aplicar control optimista y restricciones únicas cuando haya escrituras; las lecturas usan paginación/índices comunes.
- **Idempotencia (RNF27)**: No crear efectos secundarios en consultas; proteger cualquier mutación asociada.

## Error Handling

| Escenario | Comportamiento esperado | Fuente (spec) |
|---|---|---|
| Datos inválidos | 400 VALIDATION_ERROR con los campos que la spec define; no persistir cambios. | FR de validación |
| Acceso a recurso ajeno | 403 FORBIDDEN o 404 NOT_FOUND según la política de visibilidad común; validar pertenencia en el caso de uso. | Reglas de autorización |
| Proveedor geográfico no disponible | Degradar con EXTERNAL_SERVICE_UNAVAILABLE sin inventar ruta ni tarifa. | RNF19 / edge case |

## Testing Strategy

| Tipo | Qué cubre | Escenarios de la spec |
|---|---|---|
| Unitarias | Reglas de cuentas, validaciones, pertenencia, estados y límites. | FR asociados a cada US. |
| Integración | Endpoint, persistencia real con Testcontainers, permisos, transacciones, concurrencia e idempotencia. | Todos los FR y edge cases definidos. |
| E2E | Recorrido de pantalla/API por rol, incluyendo errores recuperables. | US1, US2. |

**Cobertura mínima esperada**: todos los 5 FR, 2 SC y 4 escenarios de aceptación tienen pruebas trazables; cada edge case abierto queda bloqueado como decisión pendiente o se convierte en escenario tras corregir la spec.

## Project Structure

### Documentation (this feature)

```text
specs/RF-10-inicio-de-sesion/
├── RF-10-inicio-de-sesion.md  # especificación funcional (QUÉ)
└── plan.md
```

### Source Code (repository root)

```text
backend/src/main/java/co/edu/napa/cuentas/
├── domain/model/                 # reglas y agregados sin Spring/JPA
├── domain/event/                 # solo los eventos del catálogo que emite la feature
├── application/ports/in/         # contrato del caso de uso
├── application/ports/out/        # repositorio, reloj y EventPublisherPort si aplica
├── application/usecase/          # caso(s) de uso; sin capa Service
└── infrastructure/adapters/in/web/ # controlador, DTOs y mappers
backend/src/test/java/co/edu/napa/cuentas/
frontend/src/screens/{cliente,vendedor,domiciliario,administrador}/
frontend/__tests__/
```

**Structure Decision**: módulo `cuentas`; dominio aislado, los controladores llaman solo a puertos de entrada. No se crea ni modifica esquema fuera de spec-general; migraciones Flyway solo si el modelo compartido ya fue actualizado.

## Phase 1: Setup

**Purpose**: comprobar el alcance de la feature y las fundaciones; no duplicar tareas ya definidas en foundations/plan.md.

- [ ] T001 Revisar spec, spec-general, eventos y dependencias antes de abrir implementación.

## Phase 2: Foundational

**Purpose**: puertos, acceso autorizado y persistencia requeridos antes de las historias.

- [ ] T002

**Checkpoint**: contrato de aplicación y restricciones compartidas listos; sin cambios de comportamiento fuera de la spec.

## Phase 3: User Story 1 - Usuario inicia sesión en su cuenta (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US1 en la spec.
**Independent Test**: Puede probarse ingresando credenciales válidas de una cuenta existente y verificando la redirección al panel correspondiente al rol del usuario.
**Cubre**: FR-001, FR-002, FR-003; Inicio de sesión exitoso, Intento de inicio de sesión con credenciales incorrectas, Cuenta con el celular sin verificar.

### Tests

- [ ] T003 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T004 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T005 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US1 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 4: User Story 2 - Administrador inicia sesión en su cuenta (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US2 en la spec.
**Independent Test**: Puede probarse ingresando credenciales administrativas válidas y verificando el acceso al panel de administración.
**Cubre**: FR-004, FR-005; Inicio de sesión administrativo exitoso.

### Tests

- [ ] T006 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T007 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T008 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US2 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 5: Polish & Cross-Cutting

- [ ] T009 Ejecutar pruebas de rendimiento para SC y revisar privacidad/autorización.
- [ ] Actualizar requerimientos/trazabilidad.md solo ante cambio aprobado de alcance.
- [ ] Actualizar la documentación técnica derivada de decisiones aprobadas.

## Dependencies & Execution Order

- Fundaciones transversales → RF-07 / RF-70 → US en orden P1 → P2 → P3 → Polish.
- Dentro de cada historia: pruebas/contrato → dominio y caso de uso → adaptador REST / UI → integración.
- Solo paralelizar tareas que trabajen en archivos independientes y no compartan cambios de esquema.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Qué ocurre tras varios intentos consecutivos fallidos de inicio de sesión sobre la misma cuenta? | Puede alterar validaciones, estado, privacidad o respuesta de API. | Tras 5 fallos en 15 minutos por identificador normalizado y por IP, responder `429 RATE_LIMITED` con `Retry-After`. | Decisión acordada |
| ¿Cómo maneja el sistema el inicio de sesión desde un dispositivo o ubicación no habitual? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué sucede si la cuenta fue eliminada o inhabilitada y se intenta iniciar sesión con sus credenciales? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |

## Plan Review Checklist

- [x] La tabla Spec Coverage enumera cada FR, SC y escenario de aceptación; edge cases se identifican como decisión pendiente.
- [x] Hay tareas de prueba e implementación asociadas a cada historia.
- [x] Arquitectura modular hexagonal coherente con cuentas; usecase, sin capa service.
- [x] Entidades referencian spec-general; no se redefine el modelo.
- [x] Contrato API, cabeceras, respuestas, errores y autorización descritos conforme a convenciones comunes.
- [x] Eventos emitidos verificados contra eventos.md; no se planifican listeners en el emisor.
- [x] Actores identificados según la spec.
- [ ] Errores y casos borde tienen comportamiento definido; quedan decisiones explícitamente bloqueantes en Open Questions.
- [x] Conectividad/concurrencia e idempotencia consideradas.
- [x] Dependencias tecnológicas se heredan de la plantilla/fundaciones.
- [x] Tareas identifican componentes/rutas y son trazables.
- [x] No se añade comportamiento funcional que no esté en la spec.

## Notes

- El nombre real del archivo de spec se conserva en el enlace; este repositorio todavía no tiene backend ni frontend implementados.
- Los endpoints propuestos concretan el contrato técnico sin ampliar el alcance; revisar contra spec-general antes de construir.
- `[USn]` y los IDs de Spec Coverage vinculan pruebas y tareas a la historia/requirement original.

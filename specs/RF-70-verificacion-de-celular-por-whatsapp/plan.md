# Implementation Plan: Verificación de celular por WhatsApp (OTP)

**Date**: 2026-10-20
**Spec**: [RF-70-verificacion-de-celular-por-whatsapp.md](./RF-70-verificacion-de-celular-por-whatsapp.md) (fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos.md](../general/eventos.md)
**Requerimiento funcional**: RF-70
**Historias de usuario**: HU-105
**Prioridad (la mayor de la spec)**: P1
**Depende de**: fundaciones transversales
**Habilita a**: RF-07, RF-08, RF-09, RF-10

> Este documento define **CÓMO** implementar la spec, sin redefinir su comportamiento, el modelo compartido ni los eventos.
> Si una decisión pendiente cambia el comportamiento, corregir primero la spec. No iniciar implementación mientras el Plan Review Checklist tenga elementos pendientes.

## Summary

Implementar Verificación de celular por WhatsApp (OTP) para Usuario en el módulo cuentas, con validaciones de pertenencia/estado, contrato REST y persistencia conforme a la spec y los fundamentos compartidos.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene directamente. |
| Vendedor (ambulante / punto fijo) | No interviene directamente. |
| Domiciliario | No interviene directamente. |
| Administrador | No interviene directamente. |
| Usuario (rol base) | Actor directo según la spec. |

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
| FR-001 | El sistema DEBE emitir un código de 6 dígitos de un solo uso, con vigencia limitada (no mayor a 10 minutos). | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T004, T005 |
| FR-002 | El sistema DEBE enviar el código por WhatsApp con una plantilla aprobada y ofrecer SMS como respaldo si el envío falla. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T004, T005 |
| FR-003 | El sistema DEBE permitir hasta 5 intentos por código y invalidarlo al agotarlos. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T004, T005 |
| FR-004 | El sistema DEBE limitar los envíos por celular (1 por minuto, 5 por hora) y por IP, respondiendo `429` con `Retry-After`. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T007, T008 |
| FR-005 | El sistema DEBE invalidar el código anterior cuando se genera uno nuevo para el mismo celular y propósito. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T007, T008 |
| FR-006 | El sistema DEBE responder igual ante celulares registrados y no registrados en la recuperación de contraseña. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T007, T008 |
| FR-007 | El sistema DEBE activar la cuenta e iniciar sesión al verificarse el código de registro (RF-07 FR-004). | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T010, T011 |
| FR-008 | El sistema DEBE aplicar un cambio de celular solo después de verificar el número nuevo (RF-08). | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T010, T011 |
| FR-009 | El sistema DEBE registrar cada envío como `Mensaje` con canal, propósito, destino, estado e identificador del proveedor, **sin incluir el código**. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T010, T011 |
| FR-010 | El sistema NO DEBE almacenar ni registrar el código en claro en base de datos, logs ni outbox. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T013, T014 |
| FR-011 | El sistema DEBE actualizar el estado del `Mensaje` (enviado, entregado, fallido) cuando el proveedor ofrezca notificaciones de estado. | Regla de dominio/caso de uso en cuentas; contrato POST /api/v1/auth/verificaciones-celular. | T013, T014 |
| FR-012 | El sistema DEBE aceptar celulares colombianos de 10 dígitos y números internacionales E.164, normalizando ambos formatos a E.164 antes de persistirlos o enviarlos al proveedor. | Normalización de celular compartida por registro, inicio de sesión y verificación. | T004, T005 |
| SC-001 | La solicitud de un código responde en menos de 1,5 s (p95); el envío es asíncrono. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| SC-002 | Al menos el 90 % de los códigos llega al usuario en menos de 30 s *(meta a validar con datos reales del proveedor)*. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| SC-003 | El 0 % de los códigos puede usarse dos veces o después de vencer. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| SC-004 | El 0 % de los códigos queda en claro en base de datos, logs u outbox. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| SC-005 | El 100 % de los envíos queda registrado como `Mensaje` con su estado. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| US1: Código correcto | el sistema activa mi cuenta e inicia mi sesión | Prueba de aceptación aislada de US1. | T003 |
| US1: Código incorrecto | el sistema rechaza el intento e indica cuántos intentos me quedan | Prueba de aceptación aislada de US1. | T003 |
| US1: Código vencido | el sistema indica que venció y me permite pedir uno nuevo | Prueba de aceptación aislada de US1. | T003 |
| US1: Intentos agotados | el sistema invalida el código y exige solicitar uno nuevo | Prueba de aceptación aislada de US1. | T003 |
| US2: Recuperación exitosa | el sistema me permite definir una contraseña nueva y cierra las demás sesiones | Prueba de aceptación aislada de US2. | T006 |
| US2: Celular no registrado | el sistema responde igual que si existiera, sin revelar si la cuenta existe | Prueba de aceptación aislada de US2. | T006 |
| US3: Cambio verificado | el sistema reemplaza el celular y registra la verificación | Prueba de aceptación aislada de US3. | T009 |
| US3: Cambio sin verificar | el sistema conserva el celular anterior | Prueba de aceptación aislada de US3. | T009 |
| US4: Reenvío permitido | el sistema envía un código nuevo e invalida el anterior | Prueba de aceptación aislada de US4. | T012 |
| US4: Reenvío demasiado pronto o excedido | el sistema rechaza con el tiempo de espera y no envía mensaje | Prueba de aceptación aislada de US4. | T012 |
| US4: Falla de WhatsApp | el sistema me ofrece recibir el código por SMS | Prueba de aceptación aislada de US4. | T012 |
| Edge case 1 | ¿Qué ocurre si el celular no tiene WhatsApp instalado? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 2 | ¿Qué pasa si el usuario cambia de celular a uno ya registrado por otra cuenta? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 3 | ¿Cómo se trata un celular con formato internacional distinto del colombiano? | Aceptar celulares colombianos de 10 dígitos y números internacionales E.164, normalizando a E.164. | Decisión acordada |
| Edge case 4 | ¿Qué sucede si el proveedor entrega el mensaje con demora y el código ya venció? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 5 | ¿Qué hacer si un mismo celular recibe solicitudes masivas (abuso)? (límites por celular e IP; bloqueo temporal) | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 6 | ¿Quién cubre el costo de los mensajes y qué techo mensual se fija? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización por rol | Sí | Sí · rol y pertenencia se validan en el caso de uso. |
| RNF04 Privacidad / ubicación | Sí | Sí · minimizar datos personales; limitar datos de ubicación a participantes autorizados y solo cuando la feature los maneje. |
| RNF07 Rendimiento ≤ 3 s | Sí | Sí · medir el endpoint y cubrir el SC de latencia de la spec. |
| RNF08 Concurrencia | Sí | Sí · transición atómica / bloqueo optimista y prueba de solicitudes concurrentes. |
| RNF16 Trazabilidad | Sí | Sí · timestamps comunes y, en cambios de estado, EventoTrazabilidad transaccional. |
| RNF18 Notificaciones | No | No emite notificaciones salvo que el catálogo compartido lo indique. |
| RNF21 Auditoría | Sí | Sí · registrar actor y cambio conforme a la entidad/EventoTrazabilidad definida. |
| RNF23 Consistencia de estados | No | No altera estados de Pedido, Domicilio, Pago o Reporte. |
| RNF24 Mensajes de error claros | Sí | Sí · código común y mensaje español con traceId; no exponer datos sensibles. |
| RNF27 Integridad de pagos | No | No manipula pagos. |
| RNF28 Conectividad intermitente | Sí | Sí · acción reintentable con outbox local e Idempotency-Key; servidor autoritativo. |
| RNF29 Medios de pago | No | No aplica. |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende la feature |
|---|---|---|
| Mensaje | Lee / escribe según el caso de uso | Envío saliente por un canal externo; ver `../general/spec-general.md` §3.2. |
| Código OTP | Lee / escribe según el caso de uso | Dato efímero en Redis (hash, intentos, vigencia); no es entidad persistente. |
| Usuario | Lee / escribe según el caso de uso | Su celular y la fecha de verificación (`celular_verificado_at`). |

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Ejecutar Verificación de celular por WhatsApp (OTP) | Usuario | Recurso propio del usuario autenticado, salvo permiso administrativo. |

## API Contracts

### POST /api/v1/auth/verificaciones-celular

**Rol**: Usuario.
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
| X-Request-Id | No | Identificador opcional, devuelto siempre como `X-Request-Id`. |

**Body**: DTO con los datos estrictamente requeridos por FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009, FR-010, FR-011; validar en el caso de uso y no incluir datos no definidos por la fuente funcional. No incluir el código OTP en base de datos, logs ni outbox; Twilio Verify queda detrás de VerificarCelularPort.

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
| Fallo de notificación | Persistir el resultado del caso de uso; la entrega de push no revierte la transacción. | RNF18 / foundations |

## Testing Strategy

| Tipo | Qué cubre | Escenarios de la spec |
|---|---|---|
| Unitarias | Reglas de cuentas, validaciones, pertenencia, estados y límites. | FR asociados a cada US. |
| Integración | Endpoint, persistencia real con Testcontainers, permisos, transacciones, concurrencia e idempotencia. | Todos los FR y edge cases definidos. |
| E2E | Recorrido de pantalla/API por rol, incluyendo errores recuperables. | US1, US2, US3, US4. |

**Cobertura mínima esperada**: todos los 11 FR, 5 SC y 11 escenarios de aceptación tienen pruebas trazables; cada edge case abierto queda bloqueado como decisión pendiente o se convierte en escenario tras corregir la spec.

## Project Structure

### Documentation (this feature)

```text
specs/RF-70-verificacion-de-celular-por-whatsapp/
├── RF-70-verificacion-de-celular-por-whatsapp.md  # especificación funcional (QUÉ)
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

## Phase 3: User Story 1 - Verificar mi celular al registrarme (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US1 en la spec.
**Independent Test**: Registrar una cuenta, recibir el código, ingresarlo y verificar que la cuenta pasa a activa y se inicia sesión.
**Cubre**: FR-001, FR-002, FR-003; Código correcto, Código incorrecto, Código vencido, Intentos agotados.

### Tests

- [ ] T003 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T004 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T005 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US1 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 4: User Story 2 - Recuperar mi contraseña con un código (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US2 en la spec.
**Independent Test**: Solicitar recuperación con un celular verificado, ingresar el código y definir una contraseña nueva.
**Cubre**: FR-004, FR-005, FR-006; Recuperación exitosa, Celular no registrado.

### Tests

- [ ] T006 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T007 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T008 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US2 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 5: User Story 3 - Cambiar mi celular (Priority: P2)

**Goal**: Implementar exclusivamente el comportamiento de US3 en la spec.
**Independent Test**: Cambiar el celular, verificar el nuevo y comprobar que el anterior sigue vigente hasta entonces.
**Cubre**: FR-007, FR-008, FR-009; Cambio verificado, Cambio sin verificar.

### Tests

- [ ] T009 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T010 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T011 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US3 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 6: User Story 4 - Reenviar el código o recibirlo por SMS (Priority: P2)

**Goal**: Implementar exclusivamente el comportamiento de US4 en la spec.
**Independent Test**: Simular fallo de WhatsApp y comprobar que se ofrece SMS.
**Cubre**: FR-010, FR-011; Reenvío permitido, Reenvío demasiado pronto o excedido, Falla de WhatsApp.

### Tests

- [ ] T012 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T013 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T014 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US4 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 7: Polish & Cross-Cutting

- [ ] T015 Ejecutar pruebas de rendimiento para SC y revisar privacidad/autorización.
- [ ] Actualizar requerimientos/trazabilidad.md solo ante cambio aprobado de alcance.
- [ ] Actualizar la documentación técnica derivada de decisiones aprobadas.

## Dependencies & Execution Order

- Fundaciones transversales → US en orden P1 → P2 → P3 → Polish.
- Dentro de cada historia: pruebas/contrato → dominio y caso de uso → adaptador REST / UI → integración.
- Solo paralelizar tareas que trabajen en archivos independientes y no compartan cambios de esquema.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Qué ocurre si el celular no tiene WhatsApp instalado? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué pasa si el usuario cambia de celular a uno ya registrado por otra cuenta? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Cómo se trata un celular con formato internacional distinto del colombiano? | Puede alterar validaciones, estado, privacidad o respuesta de API. | Aceptar móviles colombianos de 10 dígitos y teléfonos internacionales E.164; persistir siempre en E.164. | Decisión acordada |
| ¿Qué sucede si el proveedor entrega el mensaje con demora y el código ya venció? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué hacer si un mismo celular recibe solicitudes masivas (abuso)? (límites por celular e IP; bloqueo temporal) | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Quién cubre el costo de los mensajes y qué techo mensual se fija? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |

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

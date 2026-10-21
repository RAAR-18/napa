# Implementation Plan: Rechazo de domicilios

**Date**: 2026-10-20
**Spec**: [RF-17-rechazo-de-domicilios.md](./RF-17-rechazo-de-domicilios.md) (fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos.md](../general/eventos.md)
**Requerimiento funcional**: RF-17
**Historias de usuario**: HU-37
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-16
**Habilita a**: ninguno declarado

> Este documento define **CÓMO** implementar la spec, sin redefinir su comportamiento, el modelo compartido ni los eventos.
> Si una decisión pendiente cambia el comportamiento, corregir primero la spec. No iniciar implementación mientras el Plan Review Checklist tenga elementos pendientes.

## Summary

Implementar Rechazo de domicilios para Domiciliario en el módulo domicilios, con validaciones de pertenencia/estado, contrato REST y persistencia conforme a la spec y los fundamentos compartidos.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene directamente. |
| Vendedor (ambulante / punto fijo) | No interviene directamente. |
| Domiciliario | Actor directo según la spec. |
| Administrador | No interviene directamente. |
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
| FR-001 | El sistema DEBE permitir al domiciliario rechazar un domicilio disponible, retirándolo de forma **definitiva** de su lista de disponibles. | Regla de dominio/caso de uso en domicilios; contrato POST /api/v1/domicilios/{id}/rechazos. | T004, T005 |
| FR-002 | El sistema DEBE mantener el domicilio rechazado disponible para los demás domiciliarios. | Regla de dominio/caso de uso en domicilios; contrato POST /api/v1/domicilios/{id}/rechazos. | T004, T005 |
| FR-003 | El sistema DEBE tratar el rechazo como una acción personal del domiciliario, sin notificar ni penalizar a otros usuarios. | Regla de dominio/caso de uso en domicilios; contrato POST /api/v1/domicilios/{id}/rechazos. | T004, T005 |
| FR-004 | El sistema NO DEBE ofrecer una lista de domicilios rechazados ni una acción de restauración: el rechazo no es reversible. | Regla de dominio/caso de uso en domicilios; contrato POST /api/v1/domicilios/{id}/rechazos. | T004, T005 |
| SC-001 | El 100% de los domicilios rechazados por un domiciliario dejan de aparecer en su lista de disponibles de inmediato y de forma permanente. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| SC-002 | El 0% de los rechazos afecta la disponibilidad del domicilio para otros domiciliarios. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| SC-003 | El domiciliario puede rechazar un domicilio en menos de 5 segundos. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| US1: Rechazo de un domicilio disponible | el sistema lo retira de forma permanente de su lista de disponibles y lo mantiene disponible para los demás domiciliarios | Prueba de aceptación aislada de US1. | T003 |
| US1: El rechazo no notifica ni penaliza | el sistema no notifica ni penaliza al vendedor, al cliente ni al domiciliario, porque rechazar es una acción personal, no un incumplimiento | Prueba de aceptación aislada de US1. | T003 |
| US1: El rechazo es definitivo | el sistema no ofrece ninguna opción para revertir el rechazo ni una lista de "rechazados" que consultar | Prueba de aceptación aislada de US1. | T003 |
| US1: Rechazo de un domicilio ya tomado por otro | el sistema informa que el domicilio ya no está disponible y lo retira de su lista sin registrar el rechazo | Prueba de aceptación aislada de US1. | T003 |
| Edge case 1 | ¿Existe un límite de rechazos por domiciliario en un período de tiempo, para evitar que un domiciliario "filtre" agresivamente sin aceptar nunca? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 2 | ¿Qué ocurre si el domicilio es cancelado por el administrador después de que el domiciliario lo rechazó? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |

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
| RNF23 Consistencia de estados | Sí | Sí · transición permitida, atómica e idempotente; demás estados responden INVALID_STATE. |
| RNF24 Mensajes de error claros | Sí | Sí · código común y mensaje español con traceId; no exponer datos sensibles. |
| RNF27 Integridad de pagos | No | No manipula pagos. |
| RNF28 Conectividad intermitente | Sí | Sí · acción reintentable con outbox local e Idempotency-Key; servidor autoritativo. |
| RNF29 Medios de pago | No | No aplica. |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende la feature |
|---|---|---|
| Rechazo de domicilio | Lee / escribe según el caso de uso | Relación definitiva entre un domiciliario y un domicilio que este decidió descartar; incluye fecha de rechazo. No es editable ni reversible. |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento de trazabilidad / notificación |
|---|---|---|---|---|---|
| DomicilioRechazo | no existe para el par | registrado (rechazo irreversible) | Domiciliario | Según precondiciones de FR; validar estado actual en la misma operación. | Filtra futuras listas; sin evento catalogado |

**Transiciones inválidas**: responder `409 INVALID_STATE` e incluir `details.estadoActual`, según spec-general §2.2.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Ejecutar Rechazo de domicilios | Domiciliario | El domicilio está asignado al usuario autenticado. |

## API Contracts

### POST /api/v1/domicilios/{id}/rechazos

**Rol**: Domiciliario.
**Path params**: {id}: UUID del recurso.
**Query params**: Ninguno salvo los definidos por la spec.

| Parámetro | Tipo | Obligatorio | Valores / ejemplo | Descripción |
|---|---|:---:|---|---|
| page, size, sort | int, int, string | Para listados | 0, 20, fecha,desc | Convención compartida; máximo size 100. |
| filtros de la feature | Según spec | Según spec | Solo valores documentados | Filtro desconocido: 400. |

**Headers de request**:

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | Sí | Bearer JWT, según spec-general §2.1. |
| Idempotency-Key | Sí para mutación de estado/dinero | UUID; obligatoria según las convenciones comunes para mutaciones. |
| X-Request-Id | No | Identificador opcional, devuelto siempre como `X-Request-Id`. |

**Body**: DTO con los datos estrictamente requeridos por FR-001, FR-002, FR-003, FR-004; validar en el caso de uso y no incluir datos no definidos por la fuente funcional.

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
- **Concurrencia (RNF08)**: transición condicional atómica / control optimista; probar dos solicitudes simultáneas.
- **Idempotencia (RNF27)**: Redis + restricción única de base de datos cuando el recurso lo permita; una repetición no duplica cambios ni eventos.

## Error Handling

| Escenario | Comportamiento esperado | Fuente (spec) |
|---|---|---|
| Datos inválidos | 400 VALIDATION_ERROR con los campos que la spec define; no persistir cambios. | FR de validación |
| Acceso a recurso ajeno | 403 FORBIDDEN o 404 NOT_FOUND según la política de visibilidad común; validar pertenencia en el caso de uso. | Reglas de autorización |
| Fuera de secuencia | 409 INVALID_STATE e incluir estado actual según spec-general §2.2. | FR de transición |
| Fallo de notificación | Persistir el resultado del caso de uso; la entrega de push no revierte la transacción. | RNF18 / foundations |

## Testing Strategy

| Tipo | Qué cubre | Escenarios de la spec |
|---|---|---|
| Unitarias | Reglas de domicilios, validaciones, pertenencia, estados y límites. | FR asociados a cada US. |
| Integración | Endpoint, persistencia real con Testcontainers, permisos, transacciones, concurrencia e idempotencia. | Todos los FR y edge cases definidos. |
| E2E | Recorrido de pantalla/API por rol, incluyendo errores recuperables. | US1. |

**Cobertura mínima esperada**: todos los 4 FR, 3 SC y 4 escenarios de aceptación tienen pruebas trazables; cada edge case abierto queda bloqueado como decisión pendiente o se convierte en escenario tras corregir la spec.

## Project Structure

### Documentation (this feature)

```text
specs/RF-17-rechazo-de-domicilios/
├── RF-17-rechazo-de-domicilios.md  # especificación funcional (QUÉ)
└── plan.md
```

### Source Code (repository root)

```text
backend/src/main/java/co/edu/napa/domicilios/
├── domain/model/                 # reglas y agregados sin Spring/JPA
├── domain/event/                 # solo los eventos del catálogo que emite la feature
├── application/ports/in/         # contrato del caso de uso
├── application/ports/out/        # repositorio, reloj y EventPublisherPort si aplica
├── application/usecase/          # caso(s) de uso; sin capa Service
└── infrastructure/adapters/in/web/ # controlador, DTOs y mappers
backend/src/test/java/co/edu/napa/domicilios/
frontend/src/screens/{cliente,vendedor,domiciliario,administrador}/
frontend/__tests__/
```

**Structure Decision**: módulo `domicilios`; dominio aislado, los controladores llaman solo a puertos de entrada. No se crea ni modifica esquema fuera de spec-general; migraciones Flyway solo si el modelo compartido ya fue actualizado.

## Phase 1: Setup

**Purpose**: comprobar el alcance de la feature y las fundaciones; no duplicar tareas ya definidas en foundations/plan.md.

- [ ] T001 Revisar spec, spec-general, eventos y dependencias antes de abrir implementación.

## Phase 2: Foundational

**Purpose**: puertos, acceso autorizado y persistencia requeridos antes de las historias.

- [ ] T002

**Checkpoint**: contrato de aplicación y restricciones compartidas listos; sin cambios de comportamiento fuera de la spec.

## Phase 3: User Story 1 - Descartar domicilios que no me interesan (Priority: P2)

**Goal**: Implementar exclusivamente el comportamiento de US1 en la spec.
**Independent Test**: Puede probarse rechazando un domicilio disponible y verificando que desaparece permanentemente de la lista de ese domiciliario pero sigue visible para otro.
**Cubre**: FR-001, FR-002, FR-003, FR-004; Rechazo de un domicilio disponible, El rechazo no notifica ni penaliza, El rechazo es definitivo, Rechazo de un domicilio ya tomado por otro.

### Tests

- [ ] T003 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T004 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T005 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US1 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 4: Polish & Cross-Cutting

- [ ] T006 Ejecutar pruebas de rendimiento para SC y revisar privacidad/autorización.
- [ ] Actualizar requerimientos/trazabilidad.md solo ante cambio aprobado de alcance.
- [ ] Actualizar la documentación técnica derivada de decisiones aprobadas.

## Dependencies & Execution Order

- Fundaciones transversales → RF-16 → US en orden P1 → P2 → P3 → Polish.
- Dentro de cada historia: pruebas/contrato → dominio y caso de uso → adaptador REST / UI → integración.
- Solo paralelizar tareas que trabajen en archivos independientes y no compartan cambios de esquema.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Existe un límite de rechazos por domiciliario en un período de tiempo, para evitar que un domiciliario "filtre" agresivamente sin aceptar nunca? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué ocurre si el domicilio es cancelado por el administrador después de que el domiciliario lo rechazó? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |

## Plan Review Checklist

- [x] La tabla Spec Coverage enumera cada FR, SC y escenario de aceptación; edge cases se identifican como decisión pendiente.
- [x] Hay tareas de prueba e implementación asociadas a cada historia.
- [x] Arquitectura modular hexagonal coherente con domicilios; usecase, sin capa service.
- [x] Entidades referencian spec-general; no se redefine el modelo.
- [x] Contrato API, cabeceras, respuestas, errores y autorización descritos conforme a convenciones comunes.
- [x] Eventos emitidos verificados contra eventos.md; no se planifican listeners en el emisor.
- [x] Actores identificados según la spec.
- [ ] Errores y casos borde tienen comportamiento definido; quedan decisiones explícitamente bloqueantes en Open Questions.
- [x] Conectividad/concurrencia e idempotencia consideradas para la transición de estado.
- [x] Dependencias tecnológicas se heredan de la plantilla/fundaciones.
- [x] Tareas identifican componentes/rutas y son trazables.
- [x] No se añade comportamiento funcional que no esté en la spec.

## Notes

- El nombre real del archivo de spec se conserva en el enlace; este repositorio todavía no tiene backend ni frontend implementados.
- Los endpoints propuestos concretan el contrato técnico sin ampliar el alcance; revisar contra spec-general antes de construir.
- `[USn]` y los IDs de Spec Coverage vinculan pruebas y tareas a la historia/requirement original.

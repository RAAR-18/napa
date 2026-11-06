# Implementation Plan: Configuración de preferencias de notificación

**Date**: 2026-10-20
**Spec**: [RF-39-configuracion-de-preferencias-de-notificacion.md](./RF-39-configuracion-de-preferencias-de-notificacion.md) (fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos.md](../general/eventos.md)
**Requerimiento funcional**: RF-39
**Historias de usuario**: HU-64
**Prioridad (la mayor de la spec)**: P3
**Depende de**: RF-07
**Habilita a**: ninguno declarado

> Este documento define **CÓMO** implementar la spec, sin redefinir su comportamiento, el modelo compartido ni los eventos.
> Si una decisión pendiente cambia el comportamiento, corregir primero la spec. No iniciar implementación mientras el Plan Review Checklist tenga elementos pendientes.

## Summary

Implementar Configuración de preferencias de notificación para todos los usuarios autenticados en el módulo notificaciones, con preferencias propias por usuario, contrato REST y persistencia conforme a la spec y los fundamentos compartidos.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Configura sus propias preferencias. |
| Vendedor (ambulante / punto fijo) | Configura sus propias preferencias. |
| Domiciliario | Configura sus propias preferencias. |
| Administrador | Configura sus propias preferencias. |
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
| FR-001 | El sistema DEBE permitir a los usuarios configurar qué tipos de notificación desean recibir. | Regla de dominio/caso de uso en notificaciones; contratos GET/PATCH /api/v1/notificaciones/preferencias. | T004, T005 |
| FR-002 | El sistema DEBE respetar las preferencias configuradas al generar nuevas notificaciones. | Aplicar el valor efectivo de preferencia, habilitado por defecto, al crear cada notificación nueva. | T004, T005 |
| FR-003 | El sistema DEBE permitir al usuario modificar sus preferencias en cualquier momento. | Regla de dominio/caso de uso en notificaciones; contrato PATCH /api/v1/notificaciones/preferencias. | T004, T005 |
| SC-001 | Un cambio en las preferencias de notificación se aplica a los eventos generados a partir de ese momento en el 100% de los casos. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| SC-002 | El usuario puede modificar sus preferencias de notificación en menos de 1 minuto. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| US1: Desactivar un tipo de notificación | el sistema deja de enviarme notificaciones de ese tipo, conservando las demás | Prueba de aceptación aislada de US1. | T003 |
| US1: Reactivar un tipo de notificación previamente desactivado | el sistema vuelve a generarme notificaciones de ese tipo ante nuevos eventos | Prueba de aceptación aislada de US1. | T003 |
| Edge case 1 | Si el usuario desactiva un tipo de notificación crítico, se respeta igualmente su preferencia; no existen tipos no silenciables. | RF-39 FR-002; aplicar la preferencia al generar cada notificación, también para domicilios en curso. | T003 |
| Edge case 2 | ¿Se conservan las preferencias configuradas si el usuario cambia de rol o dispositivo? | Sí. La preferencia pertenece al usuario, no al rol ni al dispositivo (`spec-general` §3.2). | Resuelto por modelo compartido |
| Edge case 3 | ¿Qué ocurre con las notificaciones ya generadas antes de desactivar un tipo? | Se conservan; la preferencia se aplica a eventos que generen notificaciones a partir del cambio (FR-002 y SC-001). | Resuelto por RF-39 |
| Edge case 4 | ¿Se muestran tipos sin una preferencia persistida? | Sí; el tipo se muestra habilitado por defecto. | Decisión aprobada por usuario |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización por rol | Sí | Sí · rol y pertenencia se validan en el caso de uso. |
| RNF04 Privacidad / ubicación | Sí | Sí · minimizar datos personales; limitar datos de ubicación a participantes autorizados y solo cuando la feature los maneje. |
| RNF07 Rendimiento ≤ 3 s | Sí | Sí · medir el endpoint y cubrir el SC de latencia de la spec. |
| RNF08 Concurrencia | No | No aplica una escritura concurrente crítica; mantener lectura consistente. |
| RNF16 Trazabilidad | Sí | Sí · timestamps comunes y, en cambios de estado, EventoTrazabilidad transaccional. |
| RNF18 Notificaciones | Sí | Sí · emitir/consumir solo los eventos y preferencias definidos; listeners se implementan en RF-38. |
| RNF21 Auditoría | No | No añade auditoría de cambios; respetar timestamps y registros del modelo compartido. |
| RNF23 Consistencia de estados | No | No altera estados de Pedido, Domicilio, Pago o Reporte. |
| RNF24 Mensajes de error claros | Sí | Sí · código común y mensaje español con traceId; no exponer datos sensibles. |
| RNF27 Integridad de pagos | No | No manipula pagos. |
| RNF28 Conectividad intermitente | Sí | Sí · acción reintentable con outbox local e Idempotency-Key; servidor autoritativo. |
| RNF29 Medios de pago | No | No aplica. |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende la feature |
|---|---|---|
| Preferencia de notificación | Lee / escribe según el caso de uso | Representa la configuración de un usuario sobre qué tipos de notificación desea recibir. |

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Ejecutar Configuración de preferencias de notificación | Cliente, vendedor ambulante, vendedor de punto fijo, domiciliario, administrador | Cada usuario solo modifica sus propias preferencias. |

## API Contracts

### GET /api/v1/notificaciones/preferencias

**Rol**: Todos los usuarios autenticados.
**Path params / query params**: Ninguno.

Devuelve todos los tipos que producen notificaciones según `eventos.md`, con el estado efectivo del usuario autenticado. Una preferencia no persistida se devuelve como habilitada.

### PATCH /api/v1/notificaciones/preferencias

**Rol**: Todos los usuarios autenticados.
**Path params / query params**: Ninguno.

**Headers de request**:

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | Sí | Bearer JWT, según spec-general §2.1. |
| Idempotency-Key | Sí | UUID para esta mutación reintentable. |
| X-Request-Id | No | Identificador opcional, devuelto siempre como `X-Request-Id`. |

**Body**: `{"preferencias":[{"tipo":"pedidos.pedido-creado","habilitada":false}]}`. `tipo` es un tipo de evento del catálogo que genere notificaciones. Se modifican solo los tipos incluidos; los omitidos conservan su estado. No se permiten tipos duplicados en una solicitud.

**Respuestas exitosas**:

| HTTP | Cuándo | Headers de response | Body |
|---|---|---|---|
| 200 | Consulta o actualización de preferencias. | `X-Request-Id`; `Cache-Control: no-store`. | Lista completa de los tipos con su estado efectivo; una actualización devuelve el estado posterior. |

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
| Unitarias | Reglas de notificaciones, validaciones, pertenencia, estados y límites. | FR asociados a cada US. |
| Integración | Endpoint, persistencia real con Testcontainers, permisos, transacciones, concurrencia e idempotencia. | Todos los FR y edge cases definidos. |
| E2E | Recorrido de pantalla/API por rol, incluyendo errores recuperables. | US1. |

**Cobertura mínima esperada**: todos los 3 FR, 2 SC y 2 escenarios de aceptación tienen pruebas trazables; cada edge case abierto queda bloqueado como decisión pendiente o se convierte en escenario tras corregir la spec.

## Project Structure

### Documentation (this feature)

```text
specs/RF-39-configuracion-de-preferencias-de-notificacion/
├── RF-39-configuracion-de-preferencias-de-notificacion.md  # especificación funcional (QUÉ)
└── plan.md
```

### Source Code (repository root)

```text
backend/src/main/java/co/edu/napa/notificaciones/
├── domain/model/                 # reglas y agregados sin Spring/JPA
├── domain/event/                 # solo los eventos del catálogo que emite la feature
├── application/ports/in/         # contrato del caso de uso
├── application/ports/out/        # repositorio, reloj y EventPublisherPort si aplica
├── application/usecase/          # caso(s) de uso; sin capa Service
└── infrastructure/adapters/in/web/ # controlador, DTOs y mappers
backend/src/test/java/co/edu/napa/notificaciones/
frontend/src/screens/{cliente,vendedor,domiciliario,administrador}/
frontend/__tests__/
```

**Structure Decision**: módulo `notificaciones`; dominio aislado, los controladores llaman solo a puertos de entrada. No se crea ni modifica esquema fuera de spec-general; migraciones Flyway solo si el modelo compartido ya fue actualizado.

## Phase 1: Setup

**Purpose**: comprobar el alcance de la feature y las fundaciones; no duplicar tareas ya definidas en foundations/plan.md.

- [x] T001 Revisar spec, spec-general, eventos y dependencias antes de abrir implementación.

## Phase 2: Foundational

**Purpose**: puertos, acceso autorizado y persistencia requeridos antes de las historias.

- [x] T002 Confirmar preferencias por usuario, valores predeterminados habilitados, tipos siempre visibles, PATCH parcial que conserva tipos omitidos y alcance para todos los roles.

**Checkpoint**: contrato de aplicación y restricciones compartidas listos; sin cambios de comportamiento fuera de la spec.

## Phase 3: User Story 1 - Decidir qué notificaciones recibir (Priority: P3)

**Goal**: Implementar exclusivamente el comportamiento de US1 en la spec.
**Independent Test**: Puede probarse de forma independiente desactivando un tipo de notificación desde la configuración y verificando que ese tipo deja de generarse para el usuario, mientras los demás tipos continúan llegando.
**Cubre**: FR-001, FR-002, FR-003; Desactivar un tipo de notificación, Reactivar un tipo de notificación previamente desactivado.

### Tests

- [ ] T003 Pruebas unitarias de preferencias y eventos pasan; la prueba Testcontainers de persistencia/upsert está pendiente de ejecución en Docker CI y E2E móvil queda para F042.

### Implementation

- [x] T004 Mantener reglas de negocio en el caso de uso; sin lógica de negocio en controller.
- [ ] T005 API REST, validación, autorización por propietario autenticado y mapper implementados; falta integrar la pantalla móvil en F042.

**Checkpoint**: US1 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 4: Polish & Cross-Cutting

- [ ] T006 Ejecutar pruebas de rendimiento para SC y revisar privacidad/autorización.
- [ ] Actualizar requerimientos/trazabilidad.md solo ante cambio aprobado de alcance.
- [ ] Actualizar la documentación técnica derivada de decisiones aprobadas.

## Dependencies & Execution Order

- Fundaciones transversales → RF-07 → US en orden P1 → P2 → P3 → Polish.
- Dentro de cada historia: pruebas/contrato → dominio y caso de uso → adaptador REST / UI → integración.
- Solo paralelizar tareas que trabajen en archivos independientes y no compartan cambios de esquema.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|

## Plan Review Checklist

- [x] La tabla Spec Coverage enumera cada FR, SC y escenario de aceptación; edge cases se identifican como decisión pendiente o resuelta.
- [x] Hay tareas de prueba e implementación asociadas a cada historia.
- [x] Arquitectura modular hexagonal coherente con notificaciones; usecase, sin capa service.
- [x] Entidades referencian spec-general; no se redefine el modelo.
- [x] Contrato API, cabeceras, respuestas, errores y autorización descritos conforme a convenciones comunes.
- [x] Eventos emitidos verificados contra eventos.md; no se planifican listeners en el emisor.
- [x] Actores identificados según la spec.
- [x] Errores y casos borde tienen comportamiento definido; las preferencias omitidas, tipos sin fila y notificaciones existentes están definidos.
- [x] Conectividad/concurrencia e idempotencia consideradas.
- [x] Dependencias tecnológicas se heredan de la plantilla/fundaciones.
- [x] Tareas identifican componentes/rutas y son trazables.
- [x] No se añade comportamiento funcional que no esté en la spec.

## Notes

- El backend de RF-39 está implementado. La pantalla queda pendiente de F042 porque el frontend todavía no cuenta con autenticación, navegación ni cliente HTTP.
- Los endpoints propuestos concretan el contrato técnico sin ampliar el alcance; revisar contra spec-general antes de construir.
- `[USn]` y los IDs de Spec Coverage vinculan pruebas y tareas a la historia/requirement original.

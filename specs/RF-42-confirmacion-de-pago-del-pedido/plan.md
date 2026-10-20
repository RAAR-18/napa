# Implementation Plan: Confirmación de pago del pedido

**Date**: 2026-10-20
**Spec**: [RF-42-confirmacion-de-pago-del-pedido.md](./RF-42-confirmacion-de-pago-del-pedido.md) (fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos.md](../general/eventos.md)
**Requerimiento funcional**: RF-42
**Historias de usuario**: HU-66
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-45, RF-53
**Habilita a**: RF-14, RF-37, RF-40, RF-41

> Este documento define **CÓMO** implementar la spec, sin redefinir su comportamiento, el modelo compartido ni los eventos.
> Si una decisión pendiente cambia el comportamiento, corregir primero la spec. No iniciar implementación mientras el Plan Review Checklist tenga elementos pendientes.

## Summary

Implementar Confirmación de pago del pedido para Vendedor (ambulante / punto fijo) en el módulo pagos, con validaciones de pertenencia/estado, contrato REST y persistencia conforme a la spec y los fundamentos compartidos.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene directamente. |
| Vendedor (ambulante / punto fijo) | Actor directo según la spec. |
| Domiciliario | No interviene directamente. |
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
| FR-001 | El sistema DEBE permitir al vendedor (ambulante o de punto fijo) confirmar que recibió el pago de un pedido de su emprendimiento, indicando el medio utilizado (efectivo o cuenta bancaria). | Regla de dominio/caso de uso en pagos; contrato POST /api/v1/pedidos/{id}/pago/confirmacion. | T004, T005 |
| FR-002 | El sistema DEBE solicitar esta confirmación al registrar la entrega directa (RF-37) o el retiro del pedido (RF-65); en los pedidos de domicilio, DEBE permitirla únicamente a partir de que el pedido esté aceptado y antes de publicar el domicilio (RF-14). | Regla de dominio/caso de uso en pagos; contrato POST /api/v1/pedidos/{id}/pago/confirmacion. | T004, T005 |
| FR-003 | El sistema DEBE impedir que el pago de un mismo pedido se confirme más de una vez. | Regla de dominio/caso de uso en pagos; contrato POST /api/v1/pedidos/{id}/pago/confirmacion. | T004, T005 |
| FR-004 | El sistema DEBE impedir que un vendedor confirme el pago de un pedido que no pertenece a su emprendimiento. | Regla de dominio/caso de uso en pagos; contrato POST /api/v1/pedidos/{id}/pago/confirmacion. | T004, T005 |
| FR-005 | El sistema DEBE notificar al cliente cuando el pago de su pedido sea confirmado. | Regla de dominio/caso de uso en pagos; contrato POST /api/v1/pedidos/{id}/pago/confirmacion. | T004, T005 |
| FR-006 | El sistema DEBE permitir al vendedor registrar una cuenta bancaria para recibir pagos digitales cuando decida aceptarlos (RF-63), sin procesar ni almacenar datos de tarjetas ni billeteras electrónicas de terceros. | Regla de dominio/caso de uso en pagos; contrato POST /api/v1/pedidos/{id}/pago/confirmacion. | T004, T005 |
| FR-007 | El sistema DEBE exigir pago por transferencia (nunca efectivo) en los pedidos con modalidad de domicilio, por el total del pedido: productos más tarifa del domicilio. | Regla de dominio/caso de uso en pagos; contrato POST /api/v1/pedidos/{id}/pago/confirmacion. | T004, T005 |
| FR-008 | El sistema DEBE habilitar la publicación del domicilio (RF-14) únicamente cuando el pago del pedido de domicilio esté confirmado. | Regla de dominio/caso de uso en pagos; contrato POST /api/v1/pedidos/{id}/pago/confirmacion. | T004, T005 |
| SC-001 | El vendedor puede confirmar el pago de un pedido en menos de 10 segundos. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| SC-002 | El 100% de los pedidos entregados quedan con el pago confirmado o pendiente de confirmación, sin estados intermedios. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| SC-003 | El 0% de los pagos ya confirmados permite una segunda confirmación. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| SC-004 | El 100% de los pedidos de domicilio confirmados quedan registrados con medio "cuenta bancaria", nunca "efectivo". | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| SC-005 | El 0% de los pedidos de domicilio habilita su publicación sin el pago confirmado. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| US1: Cobro en efectivo en una entrega directa o recogida | el sistema registra el pago como confirmado, indicando el medio "efectivo" | Prueba de aceptación aislada de US1. | T003 |
| US1: Cobro por transferencia en una entrega directa o recogida | el sistema registra el pago como confirmado, indicando el medio "cuenta bancaria" | Prueba de aceptación aislada de US1. | T003 |
| US1: Cobro de un pedido de domicilio, antes de publicarlo | el sistema registra el pago como confirmado por transferencia por el total del pedido y me habilita a publicar el domicilio (RF-14); el domicilio no admite cobro en efectivo | Prueba de aceptación aislada de US1. | T003 |
| US1: Confirmación de pago de un domicilio antes de aceptarlo | el sistema rechaza la operación e indica que el pedido primero debe aceptarse | Prueba de aceptación aislada de US1. | T003 |
| US1: Intento de confirmar dos veces | el sistema informa que ya está confirmado y no genera un nuevo registro | Prueba de aceptación aislada de US1. | T003 |
| Edge case 1 | ¿Qué sucede si el vendedor intenta confirmar el pago de un pedido cancelado o rechazado? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 2 | ¿Qué ocurre si el monto recibido es distinto al total del pedido? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 3 | ¿Qué pasa si el vendedor confirma el pago de un pedido que no le pertenece? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 4 | ¿Qué ocurre si el pedido de domicilio fue aceptado y el cliente nunca transfiere? (no hay plazo ni cancelación automática definidos: el domicilio simplemente no puede publicarse; punto pendiente de validar) | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 5 | ¿Cómo se verifica que la transferencia recibida corresponde al pedido y al monto correctos, si la plataforma no procesa el dinero? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización por rol | Sí | Sí · rol y pertenencia se validan en el caso de uso. |
| RNF04 Privacidad / ubicación | Sí | Sí · minimizar datos personales; limitar datos de ubicación a participantes autorizados y solo cuando la feature los maneje. |
| RNF07 Rendimiento ≤ 3 s | Sí | Sí · medir el endpoint y cubrir el SC de latencia de la spec. |
| RNF08 Concurrencia | Sí | Sí · transición atómica / bloqueo optimista y prueba de solicitudes concurrentes. |
| RNF16 Trazabilidad | Sí | Sí · timestamps comunes y, en cambios de estado, EventoTrazabilidad transaccional. |
| RNF18 Notificaciones | Sí | Sí · emitir/consumir solo los eventos y preferencias definidos; listeners se implementan en RF-38. |
| RNF21 Auditoría | Sí | Sí · registrar actor y cambio conforme a la entidad/EventoTrazabilidad definida. |
| RNF23 Consistencia de estados | Sí | Sí · transición permitida, atómica e idempotente; demás estados responden INVALID_STATE. |
| RNF24 Mensajes de error claros | Sí | Sí · código común y mensaje español con traceId; no exponer datos sensibles. |
| RNF27 Integridad de pagos | Sí | Sí · no se procesa dinero; confirmación única con Idempotency-Key. |
| RNF28 Conectividad intermitente | Sí | Sí · acción reintentable con outbox local e Idempotency-Key; servidor autoritativo. |
| RNF29 Medios de pago | Sí | Sí · aceptar solo los medios establecidos; no guardar tarjetas ni procesar fondos. |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende la feature |
|---|---|---|
| Pago | Lee / escribe según el caso de uso | Registro asociado a un pedido; estados: pendiente de confirmación y confirmado. Atributo adicional: medio (efectivo o cuenta bancaria). En domicilio, el monto es el total de productos más tarifa. Es distinto del pago de la tarifa al domiciliario (RF-43). |
| Pedido | Lee / escribe según el caso de uso | Su estado de pago se actualiza al confirmarse el cobro. |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento de trazabilidad / notificación |
|---|---|---|---|---|---|
| Pago | PENDIENTE_CONFIRMACION | CONFIRMADO | Vendedor dueño del emprendimiento | Según precondiciones de FR; validar estado actual en la misma operación. | E04 |

**Transiciones inválidas**: responder `409 INVALID_STATE` e incluir `details.estadoActual`, según spec-general §2.2.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Ejecutar Confirmación de pago del pedido | Vendedor (ambulante / punto fijo) | El recurso pertenece al emprendimiento del usuario autenticado y respeta su subtipo. |

## API Contracts

### POST /api/v1/pedidos/{id}/pago/confirmacion

**Rol**: Vendedor (ambulante / punto fijo).
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

**Body**: DTO con los datos estrictamente requeridos por FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008; validar en el caso de uso y no incluir datos no definidos por la fuente funcional.

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
| E04 · pagos.pago-pedido-confirmado | Caso de uso de RF-42 | Dentro de la misma transacción que el cambio, mediante EventPublisherPort. |

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
| Reintento o duplicado | Aplicar Idempotency-Key y restricción única de base de datos; responder de forma idempotente según spec-general. | FR de unicidad |
| Fallo de notificación | Persistir el resultado del caso de uso; la entrega de push no revierte la transacción. | RNF18 / foundations |

## Testing Strategy

| Tipo | Qué cubre | Escenarios de la spec |
|---|---|---|
| Unitarias | Reglas de pagos, validaciones, pertenencia, estados y límites. | FR asociados a cada US. |
| Integración | Endpoint, persistencia real con Testcontainers, permisos, transacciones, concurrencia e idempotencia. | Todos los FR y edge cases definidos. |
| E2E | Recorrido de pantalla/API por rol, incluyendo errores recuperables. | US1. |

**Cobertura mínima esperada**: todos los 8 FR, 5 SC y 5 escenarios de aceptación tienen pruebas trazables; cada edge case abierto queda bloqueado como decisión pendiente o se convierte en escenario tras corregir la spec.

## Project Structure

### Documentation (this feature)

```text
specs/RF-42-confirmacion-de-pago-del-pedido/
├── RF-42-confirmacion-de-pago-del-pedido.md  # especificación funcional (QUÉ)
└── plan.md
```

### Source Code (repository root)

```text
backend/src/main/java/co/edu/napa/pagos/
├── domain/model/                 # reglas y agregados sin Spring/JPA
├── domain/event/                 # solo los eventos del catálogo que emite la feature
├── application/ports/in/         # contrato del caso de uso
├── application/ports/out/        # repositorio, reloj y EventPublisherPort si aplica
├── application/usecase/          # caso(s) de uso; sin capa Service
└── infrastructure/adapters/in/web/ # controlador, DTOs y mappers
backend/src/test/java/co/edu/napa/pagos/
frontend/src/screens/{cliente,vendedor,domiciliario,administrador}/
frontend/__tests__/
```

**Structure Decision**: módulo `pagos`; dominio aislado, los controladores llaman solo a puertos de entrada. No se crea ni modifica esquema fuera de spec-general; migraciones Flyway solo si el modelo compartido ya fue actualizado.

## Phase 1: Setup

**Purpose**: comprobar el alcance de la feature y las fundaciones; no duplicar tareas ya definidas en foundations/plan.md.

- [ ] T001 Revisar spec, spec-general, eventos y dependencias antes de abrir implementación.

## Phase 2: Foundational

**Purpose**: puertos, acceso autorizado y persistencia requeridos antes de las historias.

- [ ] T002

**Checkpoint**: contrato de aplicación y restricciones compartidas listos; sin cambios de comportamiento fuera de la spec.

## Phase 3: User Story 1 - Vendedor confirma que recibió el pago de un pedido (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US1 en la spec.
**Independent Test**: Puede probarse confirmando el pago de un pedido con cada medio habilitado y verificando que el estado del pago cambia y queda en el historial.
**Cubre**: FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008; Cobro en efectivo en una entrega directa o recogida, Cobro por transferencia en una entrega directa o recogida, Cobro de un pedido de domicilio, antes de publicarlo, Confirmación de pago de un domicilio antes de aceptarlo, Intento de confirmar dos veces.

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

- Fundaciones transversales → RF-45 / RF-53 → US en orden P1 → P2 → P3 → Polish.
- Dentro de cada historia: pruebas/contrato → dominio y caso de uso → adaptador REST / UI → integración.
- Solo paralelizar tareas que trabajen en archivos independientes y no compartan cambios de esquema.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Qué sucede si el vendedor intenta confirmar el pago de un pedido cancelado o rechazado? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué ocurre si el monto recibido es distinto al total del pedido? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué pasa si el vendedor confirma el pago de un pedido que no le pertenece? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué ocurre si el pedido de domicilio fue aceptado y el cliente nunca transfiere? (no hay plazo ni cancelación automática definidos: el domicilio simplemente no puede publicarse; punto pendiente de validar) | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Cómo se verifica que la transferencia recibida corresponde al pedido y al monto correctos, si la plataforma no procesa el dinero? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |

## Plan Review Checklist

- [x] La tabla Spec Coverage enumera cada FR, SC y escenario de aceptación; edge cases se identifican como decisión pendiente.
- [x] Hay tareas de prueba e implementación asociadas a cada historia.
- [x] Arquitectura modular hexagonal coherente con pagos; usecase, sin capa service.
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

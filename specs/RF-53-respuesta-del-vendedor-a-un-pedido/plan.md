# Implementation Plan: Respuesta del vendedor a un pedido

**Date**: 2026-10-20
**Spec**: [RF-53-respuesta-del-vendedor-a-un-pedido.md](./RF-53-respuesta-del-vendedor-a-un-pedido.md) (fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos.md](../general/eventos.md)
**Requerimiento funcional**: RF-53
**Historias de usuario**: HU-83, HU-84
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-45
**Habilita a**: RF-01, RF-14, RF-36, RF-42, RF-50, RF-64

> Este documento define **CÓMO** implementar la spec, sin redefinir su comportamiento, el modelo compartido ni los eventos.
> Si una decisión pendiente cambia el comportamiento, corregir primero la spec. No iniciar implementación mientras el Plan Review Checklist tenga elementos pendientes.

## Summary

Implementar Respuesta del vendedor a un pedido para Vendedor (ambulante / punto fijo) en el módulo pedidos, con validaciones de pertenencia/estado, contrato REST y persistencia conforme a la spec y los fundamentos compartidos.

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
| FR-001 | El sistema DEBE permitir al vendedor aceptar o rechazar únicamente pedidos de su emprendimiento que estén en estado "pendiente". | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos/{id}/respuesta. | T004, T005 |
| FR-002 | El sistema DEBE cambiar el estado del pedido a "aceptado" o "rechazado" y notificar al cliente. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos/{id}/respuesta. | T004, T005 |
| FR-003 | El sistema DEBE validar, al aceptar un pedido inmediato, que el inventario actual cubra el pedido, y descontarlo en ese mismo momento, de forma atómica con la aceptación; si no alcanza, NO DEBE aceptarlo. En los pedidos marcados como reserva NO DEBE validar automáticamente el inventario: el vendedor decide con su propio criterio. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos/{id}/respuesta. | T004, T005 |
| FR-004 | El sistema DEBE habilitar, tras la aceptación, el siguiente paso propio de la modalidad: ubicación de entrega (entrega directa, inmediata o marcada como reserva), marcar como listo para recoger (recogida, inmediata o marcada como reserva), o esperar la confirmación del pago y luego publicar el domicilio (domicilio). | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos/{id}/respuesta. | T004, T005 |
| FR-005 | El sistema DEBE liberar las cantidades comprometidas cuando un pedido inmediato sea rechazado. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos/{id}/respuesta. | T007, T008 |
| FR-006 | El sistema DEBE impedir que un vendedor de punto fijo sin cuenta bancaria registrada acepte un pedido de domicilio (RF-63). | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos/{id}/respuesta. | T007, T008 |
| FR-007 | El sistema DEBE notificar al cliente, al aceptarse un pedido de domicilio, que debe transferir el total para que el domicilio pueda publicarse. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos/{id}/respuesta. | T007, T008 |
| SC-001 | El vendedor puede aceptar o rechazar un pedido en menos de 10 segundos. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T009 |
| SC-002 | El cliente recibe la respuesta del vendedor en menos de 1 minuto. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T009 |
| SC-003 | El 0% de los pedidos inmediatos se acepta sin cobertura de inventario. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T009 |
| SC-004 | El 0% de los pedidos de domicilio se acepta sin una cuenta bancaria registrada del vendedor. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T009 |
| US1: Aceptación de una entrega directa | el sistema cambia el pedido a aceptado, notifica al cliente y me habilita la ubicación de entrega (RF-35) | Prueba de aceptación aislada de US1. | T003 |
| US1: Aceptación de una recogida en punto fijo | el sistema cambia el pedido a aceptado, notifica al cliente y me habilita a marcarlo como listo para recoger (RF-64) | Prueba de aceptación aislada de US1. | T003 |
| US1: Aceptación de un pedido de domicilio | el sistema cambia el pedido a aceptado, notifica al cliente que debe transferir el total (productos más domicilio) y queda a la espera del pago; solo podré publicar el domicilio (RF-14) cuando confirme ese pago (RF-42) | Prueba de aceptación aislada de US1. | T003 |
| US1: Aceptación de una reserva según mi criterio | el sistema cambia el pedido a aceptado y notifica al cliente, sin validar automáticamente el inventario para esa fecha, y el pedido queda para atenderse en la fecha acordada con el siguiente paso propio de su modalidad | Prueba de aceptación aislada de US1. | T003 |
| US1: Domicilio sin cuenta bancaria registrada | el sistema me impide aceptarlo e indica que debo registrar una cuenta bancaria primero (RF-63) | Prueba de aceptación aislada de US1. | T003 |
| US1: Inventario insuficiente al aceptar un pedido inmediato | el sistema me advierte de la insuficiencia y no lo acepta hasta que ajuste el inventario o lo rechace | Prueba de aceptación aislada de US1. | T003 |
| US2: Rechazo de un pedido pendiente | el sistema cambia el pedido a rechazado, libera las cantidades comprometidas y notifica al cliente | Prueba de aceptación aislada de US2. | T006 |
| US2: Rechazo de una reserva según mi criterio | el sistema cambia el pedido a rechazado y notifica al cliente | Prueba de aceptación aislada de US2. | T006 |
| US2: Rechazo de un pedido ya aceptado | el sistema rechaza la operación e indica que el pedido ya no está pendiente | Prueba de aceptación aislada de US2. | T006 |
| Edge case 1 | Si el vendedor no responde a un pedido inmediato o reserva, el pedido permanece pendiente sin vencimiento ni transición automática en el MVP. | Mantener PENDIENTE y permitir consulta hasta una acción explícita autorizada. | T003 |
| Edge case 2 | ¿Puede el vendedor indicar un motivo al rechazar un pedido? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 3 | Si varios pedidos inmediatos compiten por el mismo inventario, cada aceptación valida y descuenta existencias de forma atómica; solo se acepta el pedido cubierto por el inventario restante (FR-003). | La actualización condicional de inventario y la transición del pedido deben confirmar en una única transacción. | T003, T004 |
| Edge case 4 | Si el vendedor acepta un pedido de domicilio y el cliente nunca paga, el pedido permanece aceptado y pendiente de pago, sin vencimiento automático; el domicilio no puede publicarse hasta que el pago se confirme. | Mantener ACEPTADO con pago pendiente; bloquear publicación del domicilio mientras no se confirme. | T003 |

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
| RNF27 Integridad de pagos | No | No manipula pagos. |
| RNF28 Conectividad intermitente | Sí | Sí · acción reintentable con outbox local e Idempotency-Key; servidor autoritativo. |
| RNF29 Medios de pago | No | No aplica. |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende la feature |
|---|---|---|
| Pedido | Lee / escribe según el caso de uso | Transiciones de esta funcionalidad: pendiente → aceptado y pendiente → rechazado. Puede estar marcado como reserva. |
| Vendedor | Lee / escribe según el caso de uso | Ambulante o de punto fijo; decide sobre los pedidos recibidos con su propio criterio. |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento de trazabilidad / notificación |
|---|---|---|---|---|---|
| Pedido | PENDIENTE | ACEPTADO o RECHAZADO | Vendedor dueño del emprendimiento | Según precondiciones de FR; validar estado actual en la misma operación. | E02 o E03 |

**Transiciones inválidas**: responder `409 INVALID_STATE` e incluir `details.estadoActual`, según spec-general §2.2.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Ejecutar Respuesta del vendedor a un pedido | Vendedor (ambulante / punto fijo) | El recurso pertenece al emprendimiento del usuario autenticado y respeta su subtipo. |

## API Contracts

### POST /api/v1/pedidos/{id}/respuesta

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

**Body**: DTO con los datos estrictamente requeridos por FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007; validar en el caso de uso y no incluir datos no definidos por la fuente funcional.

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
| E02 · pedidos.pedido-aceptado | Caso de uso de RF-53 | Dentro de la misma transacción que el cambio, mediante EventPublisherPort. |
| E03 · pedidos.pedido-rechazado | Caso de uso de RF-53 | Dentro de la misma transacción que el cambio, mediante EventPublisherPort. |

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
| Unitarias | Reglas de pedidos, validaciones, pertenencia, estados y límites. | FR asociados a cada US. |
| Integración | Endpoint, persistencia real con Testcontainers, permisos, transacciones, concurrencia e idempotencia. | Todos los FR y edge cases definidos. |
| E2E | Recorrido de pantalla/API por rol, incluyendo errores recuperables. | US1, US2. |

**Cobertura mínima esperada**: todos los 7 FR, 4 SC y 9 escenarios de aceptación tienen pruebas trazables; cada edge case abierto queda bloqueado como decisión pendiente o se convierte en escenario tras corregir la spec.

## Project Structure

### Documentation (this feature)

```text
specs/RF-53-respuesta-del-vendedor-a-un-pedido/
├── RF-53-respuesta-del-vendedor-a-un-pedido.md  # especificación funcional (QUÉ)
└── plan.md
```

### Source Code (repository root)

```text
backend/src/main/java/co/edu/napa/pedidos/
├── domain/model/                 # reglas y agregados sin Spring/JPA
├── domain/event/                 # solo los eventos del catálogo que emite la feature
├── application/ports/in/         # contrato del caso de uso
├── application/ports/out/        # repositorio, reloj y EventPublisherPort si aplica
├── application/usecase/          # caso(s) de uso; sin capa Service
└── infrastructure/adapters/in/web/ # controlador, DTOs y mappers
backend/src/test/java/co/edu/napa/pedidos/
frontend/src/screens/{cliente,vendedor,domiciliario,administrador}/
frontend/__tests__/
```

**Structure Decision**: módulo `pedidos`; dominio aislado, los controladores llaman solo a puertos de entrada. No se crea ni modifica esquema fuera de spec-general; migraciones Flyway solo si el modelo compartido ya fue actualizado.

## Phase 1: Setup

**Purpose**: comprobar el alcance de la feature y las fundaciones; no duplicar tareas ya definidas en foundations/plan.md.

- [ ] T001 Revisar spec, spec-general, eventos y dependencias antes de abrir implementación.

## Phase 2: Foundational

**Purpose**: puertos, acceso autorizado y persistencia requeridos antes de las historias.

- [ ] T002

**Checkpoint**: contrato de aplicación y restricciones compartidas listos; sin cambios de comportamiento fuera de la spec.

## Phase 3: User Story 1 - Aceptar un pedido (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US1 en la spec.
**Independent Test**: Puede probarse aceptando un pedido pendiente de cada modalidad y verificando que pasa a aceptado y habilita el siguiente paso propio de la modalidad.
**Cubre**: FR-001, FR-002, FR-003, FR-004; Aceptación de una entrega directa, Aceptación de una recogida en punto fijo, Aceptación de un pedido de domicilio, Aceptación de una reserva según mi criterio, Domicilio sin cuenta bancaria registrada, Inventario insuficiente al aceptar un pedido inmediato.

### Tests

- [ ] T003 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T004 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T005 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US1 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 4: User Story 2 - Rechazar un pedido (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US2 en la spec.
**Independent Test**: Puede probarse rechazando un pedido pendiente y verificando que pasa a rechazado y el cliente es notificado.
**Cubre**: FR-005, FR-006, FR-007; Rechazo de un pedido pendiente, Rechazo de una reserva según mi criterio, Rechazo de un pedido ya aceptado.

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

- Fundaciones transversales → RF-45 → US en orden P1 → P2 → P3 → Polish.
- Dentro de cada historia: pruebas/contrato → dominio y caso de uso → adaptador REST / UI → integración.
- Solo paralelizar tareas que trabajen en archivos independientes y no compartan cambios de esquema.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Puede el vendedor indicar un motivo al rechazar un pedido? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |

## Plan Review Checklist

- [x] La tabla Spec Coverage enumera cada FR, SC y escenario de aceptación; edge cases se identifican como decisión pendiente.
- [x] Hay tareas de prueba e implementación asociadas a cada historia.
- [x] Arquitectura modular hexagonal coherente con pedidos; usecase, sin capa service.
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

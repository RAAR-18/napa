# Implementation Plan: Confirmación de entrega directa

**Date**: 2026-10-20
**Spec**: [RF-37-confirmacion-de-entrega-directa.md](./RF-37-confirmacion-de-entrega-directa.md) (fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos.md](../general/eventos.md)
**Requerimiento funcional**: RF-37
**Historias de usuario**: HU-62
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-36, RF-42
**Habilita a**: RF-01

> Este documento define **CÓMO** implementar la spec, sin redefinir su comportamiento, el modelo compartido ni los eventos.
> Si una decisión pendiente cambia el comportamiento, corregir primero la spec. No iniciar implementación mientras el Plan Review Checklist tenga elementos pendientes.

## Summary

Implementar Confirmación de entrega directa para Vendedor (ambulante / punto fijo) en el módulo entregas, con validaciones de pertenencia/estado, contrato REST y persistencia conforme a la spec y los fundamentos compartidos.

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
| FR-001 | El sistema DEBE permitir al vendedor ambulante confirmar la entrega únicamente de pedidos en estado "en camino". | Regla de dominio/caso de uso en entregas; contrato POST /api/v1/pedidos/{id}/entrega-directa/confirmacion. | T004, T005 |
| FR-002 | El sistema DEBE solicitar al vendedor la confirmación del cobro al registrar la entrega, indicando el medio utilizado (efectivo o cuenta bancaria) entre los habilitados por el vendedor (RF-42, RF-63). | Regla de dominio/caso de uso en entregas; contrato POST /api/v1/pedidos/{id}/entrega-directa/confirmacion. | T004, T005 |
| FR-003 | El sistema DEBE cambiar el estado del pedido a "entregado" y habilitar las calificaciones entre las partes (RF-01). | Regla de dominio/caso de uso en entregas; contrato POST /api/v1/pedidos/{id}/entrega-directa/confirmacion. | T004, T005 |
| FR-004 | El sistema DEBE impedir que la entrega de un mismo pedido se confirme más de una vez. | Regla de dominio/caso de uso en entregas; contrato POST /api/v1/pedidos/{id}/entrega-directa/confirmacion. | T004, T005 |
| SC-001 | El vendedor puede confirmar la entrega y el cobro en menos de 15 segundos. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| SC-002 | El 100% de los pedidos entregados por vendedores ambulantes tienen su pago confirmado, con su medio correctamente registrado. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| US1: Confirmación de entrega y cobro en efectivo | el sistema cambia el estado del pedido a entregado, registra el pago como confirmado con medio "efectivo" (RF-42) y habilita las calificaciones | Prueba de aceptación aislada de US1. | T003 |
| US1: Confirmación de entrega y cobro por transferencia | el sistema cambia el estado del pedido a entregado y registra el pago como confirmado con medio "cuenta bancaria" | Prueba de aceptación aislada de US1. | T003 |
| US1: Confirmación fuera de secuencia | el sistema rechaza la operación e indica el estado actual | Prueba de aceptación aislada de US1. | T003 |
| US1: El cliente no recibe o no paga | el sistema me permite crear un reporte (RF-60) y mantiene el pedido sin entregar | Prueba de aceptación aislada de US1. | T003 |
| Edge case 1 | ¿Qué sucede si el cliente paga un monto distinto al total del pedido? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 2 | ¿Cómo se maneja una entrega parcial en la que el vendedor solo pudo llevar algunos ítems? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización por rol | Sí | Sí · rol y pertenencia se validan en el caso de uso. |
| RNF04 Privacidad / ubicación | Sí | Sí · minimizar datos personales; limitar datos de ubicación a participantes autorizados y solo cuando la feature los maneje. |
| RNF07 Rendimiento ≤ 3 s | Sí | Sí · medir el endpoint y cubrir el SC de latencia de la spec. |
| RNF08 Concurrencia | Sí | Sí · transición atómica / bloqueo optimista y prueba de solicitudes concurrentes. |
| RNF16 Trazabilidad | Sí | Sí · timestamps comunes y, en cambios de estado, EventoTrazabilidad transaccional. |
| RNF18 Notificaciones | Sí | Sí · emitir/consumir solo los eventos y preferencias definidos; listeners se implementan en RF-38. |
| RNF21 Auditoría | No | No añade auditoría de cambios; respetar timestamps y registros del modelo compartido. |
| RNF23 Consistencia de estados | Sí | Sí · transición permitida, atómica e idempotente; demás estados responden INVALID_STATE. |
| RNF24 Mensajes de error claros | Sí | Sí · código común y mensaje español con traceId; no exponer datos sensibles. |
| RNF27 Integridad de pagos | No | No manipula pagos. |
| RNF28 Conectividad intermitente | Sí | Sí · acción reintentable con outbox local e Idempotency-Key; servidor autoritativo. |
| RNF29 Medios de pago | No | No aplica. |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende la feature |
|---|---|---|
| Pedido | Lee / escribe según el caso de uso | Transición de esta funcionalidad: en camino → entregado. |
| Pago | Lee / escribe según el caso de uso | Registro asociado al pedido, confirmado junto con la entrega, con su medio. |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento de trazabilidad / notificación |
|---|---|---|---|---|---|
| Pedido | EN_CAMINO | ENTREGADO | Vendedor ambulante | Según precondiciones de FR; validar estado actual en la misma operación. | EventoTrazabilidad + E07 |

**Transiciones inválidas**: responder `409 INVALID_STATE` e incluir `details.estadoActual`, según spec-general §2.2.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Ejecutar Confirmación de entrega directa | Vendedor (ambulante / punto fijo) | El recurso pertenece al emprendimiento del usuario autenticado y respeta su subtipo. |

## API Contracts

### POST /api/v1/pedidos/{id}/entrega-directa/confirmacion

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
| E07 · pedidos.pedido-entregado | Caso de uso de RF-37 | Dentro de la misma transacción que el cambio, mediante EventPublisherPort. |

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

## Testing Strategy

| Tipo | Qué cubre | Escenarios de la spec |
|---|---|---|
| Unitarias | Reglas de entregas, validaciones, pertenencia, estados y límites. | FR asociados a cada US. |
| Integración | Endpoint, persistencia real con Testcontainers, permisos, transacciones, concurrencia e idempotencia. | Todos los FR y edge cases definidos. |
| E2E | Recorrido de pantalla/API por rol, incluyendo errores recuperables. | US1. |

**Cobertura mínima esperada**: todos los 4 FR, 2 SC y 4 escenarios de aceptación tienen pruebas trazables; cada edge case abierto queda bloqueado como decisión pendiente o se convierte en escenario tras corregir la spec.

## Project Structure

### Documentation (this feature)

```text
specs/RF-37-confirmacion-de-entrega-directa/
├── RF-37-confirmacion-de-entrega-directa.md  # especificación funcional (QUÉ)
└── plan.md
```

### Source Code (repository root)

```text
backend/src/main/java/co/edu/napa/entregas/
├── domain/model/                 # reglas y agregados sin Spring/JPA
├── domain/event/                 # solo los eventos del catálogo que emite la feature
├── application/ports/in/         # contrato del caso de uso
├── application/ports/out/        # repositorio, reloj y EventPublisherPort si aplica
├── application/usecase/          # caso(s) de uso; sin capa Service
└── infrastructure/adapters/in/web/ # controlador, DTOs y mappers
backend/src/test/java/co/edu/napa/entregas/
frontend/src/screens/{cliente,vendedor,domiciliario,administrador}/
frontend/__tests__/
```

**Structure Decision**: módulo `entregas`; dominio aislado, los controladores llaman solo a puertos de entrada. No se crea ni modifica esquema fuera de spec-general; migraciones Flyway solo si el modelo compartido ya fue actualizado.

## Phase 1: Setup

**Purpose**: comprobar el alcance de la feature y las fundaciones; no duplicar tareas ya definidas en foundations/plan.md.

- [ ] T001 Revisar spec, spec-general, eventos y dependencias antes de abrir implementación.

## Phase 2: Foundational

**Purpose**: puertos, acceso autorizado y persistencia requeridos antes de las historias.

- [ ] T002

**Checkpoint**: contrato de aplicación y restricciones compartidas listos; sin cambios de comportamiento fuera de la spec.

## Phase 3: User Story 1 - Cerrar la entrega y cobrar (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US1 en la spec.
**Independent Test**: Puede probarse con un pedido en camino, confirmando la entrega y el cobro (efectivo o transferencia) y verificando que el pedido pasa a entregado y su pago queda confirmado.
**Cubre**: FR-001, FR-002, FR-003, FR-004; Confirmación de entrega y cobro en efectivo, Confirmación de entrega y cobro por transferencia, Confirmación fuera de secuencia, El cliente no recibe o no paga.

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

- Fundaciones transversales → RF-36 / RF-42 → US en orden P1 → P2 → P3 → Polish.
- Dentro de cada historia: pruebas/contrato → dominio y caso de uso → adaptador REST / UI → integración.
- Solo paralelizar tareas que trabajen en archivos independientes y no compartan cambios de esquema.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Qué sucede si el cliente paga un monto distinto al total del pedido? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Cómo se maneja una entrega parcial en la que el vendedor solo pudo llevar algunos ítems? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |

## Plan Review Checklist

- [x] La tabla Spec Coverage enumera cada FR, SC y escenario de aceptación; edge cases se identifican como decisión pendiente.
- [x] Hay tareas de prueba e implementación asociadas a cada historia.
- [x] Arquitectura modular hexagonal coherente con entregas; usecase, sin capa service.
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

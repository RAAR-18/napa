# Implementation Plan: Realización de pedido

**Date**: 2026-10-20
**Spec**: [RF-45-realizacion-de-pedido.md](./RF-45-realizacion-de-pedido.md) (fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos.md](../general/eventos.md)
**Requerimiento funcional**: RF-45
**Historias de usuario**: HU-72, HU-73, HU-74, HU-76
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-28, RF-44, RF-54, RF-63
**Habilita a**: RF-01, RF-35, RF-36, RF-42, RF-47, RF-48, RF-49, RF-50, RF-51, RF-52, RF-53, RF-59, RF-64

> Este documento define **CÓMO** implementar la spec, sin redefinir su comportamiento, el modelo compartido ni los eventos.
> Si una decisión pendiente cambia el comportamiento, corregir primero la spec. No iniciar implementación mientras el Plan Review Checklist tenga elementos pendientes.

## Summary

Implementar Realización de pedido para Cliente en el módulo pedidos, con validaciones de pertenencia/estado, contrato REST y persistencia conforme a la spec y los fundamentos compartidos.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Actor directo según la spec. |
| Vendedor (ambulante / punto fijo) | No interviene directamente. |
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
| FR-001 | El sistema DEBE permitir al cliente seleccionar uno o más productos de un emprendimiento con la cantidad deseada de cada uno; cada producto seleccionado es un ítem del pedido. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos. | T004, T005 |
| FR-002 | El sistema DEBE validar la disponibilidad de inventario actual de cada ítem antes de confirmar un pedido inmediato; en los pedidos marcados como reserva no valida las cantidades contra una disponibilidad futura, porque el vendedor decide con su propio criterio al aceptarlos o rechazarlos (RF-53). | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos. | T004, T005 |
| FR-003 | El sistema DEBE ofrecer las modalidades de entrega según el tipo de vendedor: entrega directa para el vendedor ambulante; recogida en el punto fijo y domicilio para el vendedor de punto fijo. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos. | T004, T005 |
| FR-004 | El sistema DEBE solicitar la ubicación de entrega en la entrega directa y el domicilio, y no solicitarla en la recogida en punto fijo. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos. | T007, T008 |
| FR-005 | El sistema DEBE permitir marcar cualquier pedido como reserva, indicando fecha y hora futuras acordadas con el vendedor; la reserva es un atributo del pedido y no altera su modalidad de entrega. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos. | T007, T008 |
| FR-006 | El sistema DEBE calcular y mostrar el total del pedido, incluyendo el costo del domicilio cuando aplique, calculado según la distancia y sin ser inferior a la tarifa mínima (RF-44). | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos. | T010, T011 |
| FR-007 | El sistema DEBE registrar el pedido en estado "pendiente" y notificar al vendedor. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos. | T010, T011 |
| FR-008 | El sistema DEBE impedir la confirmación del pedido si falta algún ítem, la modalidad, el medio de pago, la ubicación cuando esta aplique, o la fecha y hora cuando se marque como reserva. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos. | T010, T011 |
| FR-009 | El sistema DEBE ofrecer como medio de pago, en entrega directa y recogida, únicamente los que el vendedor tenga habilitados (RF-63), y exigir pago por transferencia en el domicilio. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos. | T013, T014 |
| FR-010 | El sistema DEBE informar al cliente, en un pedido de domicilio, que el total (productos más domicilio) deberá pagarse por transferencia apenas el vendedor acepte el pedido. | Regla de dominio/caso de uso en pedidos; contrato POST /api/v1/pedidos. | T013, T014 |
| SC-001 | El cliente puede completar un pedido (ítems, modalidad, ubicación y pago) en menos de 3 minutos. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| SC-002 | El 100% de los pedidos confirmados cuentan con ítems, modalidad, medio de pago y, cuando aplica, ubicación y fecha y hora de reserva. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| SC-003 | El 0% de los pedidos inmediatos se confirma con productos sin disponibilidad de inventario. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| SC-004 | El vendedor recibe la notificación de un nuevo pedido pendiente en menos de 1 minuto. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| SC-005 | El 0% de los pedidos de domicilio se confirma con pago en efectivo. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| SC-006 | El 100% de los pedidos de domicilio informan al cliente el total a pagar por transferencia y el momento del pago antes de confirmarse. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T015 |
| US1: Modalidad de un vendedor ambulante | el sistema ofrece entrega directa | Prueba de aceptación aislada de US1. | T003 |
| US1: Modalidades de un vendedor de punto fijo | el sistema ofrece recogida en el punto fijo y domicilio | Prueba de aceptación aislada de US1. | T003 |
| US1: Pedido con varios ítems | el sistema acumula los ítems del pedido con su subtotal y su total | Prueba de aceptación aislada de US1. | T003 |
| US1: Producto sin disponibilidad | el sistema informa que el producto ya no está disponible y me pide ajustar la selección | Prueba de aceptación aislada de US1. | T003 |
| US2: Ubicación para una entrega | el sistema la registra como punto de entrega del pedido | Prueba de aceptación aislada de US2. | T006 |
| US2: Recogida en punto fijo | el sistema no solicita ubicación, porque retiro el pedido en el punto fijo | Prueba de aceptación aislada de US2. | T006 |
| US2: Ubicación faltante o inválida | el sistema me impide continuar e indica el dato faltante | Prueba de aceptación aislada de US2. | T006 |
| US3: Pedido marcado como reserva | el sistema registra el pedido con el atributo de reserva, en estado pendiente de la respuesta del vendedor, quien decide con su propio criterio si puede cumplirlo (RF-53) | Prueba de aceptación aislada de US3. | T009 |
| US3: Fecha u hora inválida | el sistema me impide continuar e indica el dato inválido | Prueba de aceptación aislada de US3. | T009 |
| US4: Creación exitosa de un pedido de entrega directa o recogida | el sistema lo registra en estado pendiente, muestra el total a pagar y notifica al vendedor | Prueba de aceptación aislada de US4. | T012 |
| US4: Pedido de domicilio | el sistema muestra el costo del domicilio y el total (productos más domicilio), exige pago por transferencia por ese total, no ofrece efectivo e informa que deberé pagarlo apenas el vendedor acepte el pedido | Prueba de aceptación aislada de US4. | T012 |
| US4: Datos incompletos | el sistema me impide continuar e indica los datos faltantes | Prueba de aceptación aislada de US4. | T012 |
| Edge case 1 | ¿Qué sucede si el cliente intenta confirmar un pedido sin ningún producto seleccionado? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 2 | ¿Cómo maneja el sistema un cambio de precio o de disponibilidad entre la selección y la confirmación? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 3 | ¿Qué ocurre si el cliente está demasiado lejos de un vendedor ambulante para una entrega directa? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 4 | ¿Qué pasa si el cliente pierde conexión después de armar el pedido y antes de confirmarlo (RNF28)? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 5 | ¿Qué ocurre si el vendedor deshabilita un medio de pago mientras el cliente arma su pedido? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 6 | ¿Puede reservarse un producto que hoy figura como agotado, si el vendedor lo tendrá en la fecha acordada? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 7 | Si el cliente nunca realiza la transferencia de un pedido de domicilio aceptado, el pedido permanece aceptado y pendiente de pago, sin vencimiento automático en el MVP. | Mantener estado ACEPTADO y pago pendiente; no publicar el domicilio hasta confirmar el pago. | T012 |

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
| RNF27 Integridad de pagos | Sí | Sí · no se procesa dinero; confirmación única con Idempotency-Key. |
| RNF28 Conectividad intermitente | Sí | Sí · acción reintentable con outbox local e Idempotency-Key; servidor autoritativo. |
| RNF29 Medios de pago | Sí | Sí · aceptar solo los medios establecidos; no guardar tarjetas ni procesar fondos. |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende la feature |
|---|---|---|
| Pedido | Lee / escribe según el caso de uso | Compra del cliente a un emprendimiento; incluye ítems, modalidad de entrega, ubicación de entrega cuando aplique, medio de pago, reserva (fecha y hora acordadas; vacía si el pedido es inmediato), total, estado (pendiente, aceptado, rechazado, listo para recoger, en camino, entregado, cancelado) y fecha. El receptor del pedido es siempre el cliente que lo hace. |
| Ítem de pedido | Lee / escribe según el caso de uso | Producto con su cantidad y su precio al momento de la compra. |
| Modalidad de entrega | Lee / escribe según el caso de uso | Entrega directa (vendedor ambulante), recogida en punto fijo o domicilio (punto fijo). |
| Ubicación de entrega | Lee / escribe según el caso de uso | Punto indicado por el cliente donde se le entregará el pedido (punto B en un domicilio). |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento de trazabilidad / notificación |
|---|---|---|---|---|---|
| Pedido | no existe | PENDIENTE | Cliente autenticado | Según precondiciones de FR; validar estado actual en la misma operación. | E01 |

**Transiciones inválidas**: responder `409 INVALID_STATE` e incluir `details.estadoActual`, según spec-general §2.2.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Ejecutar Realización de pedido | Cliente | El pedido/interacción pertenece al cliente autenticado. |

## API Contracts

### POST /api/v1/pedidos

**Rol**: Cliente.
**Path params**: Ninguno.
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

**Body**: DTO con los datos estrictamente requeridos por FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009, FR-010; validar en el caso de uso y no incluir datos no definidos por la fuente funcional.

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
| E01 · pedidos.pedido-creado | Caso de uso de RF-45 | Dentro de la misma transacción que el cambio, mediante EventPublisherPort. |

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
| Proveedor geográfico no disponible | Degradar con EXTERNAL_SERVICE_UNAVAILABLE sin inventar ruta ni tarifa. | RNF19 / edge case |
| Fallo de notificación | Persistir el resultado del caso de uso; la entrega de push no revierte la transacción. | RNF18 / foundations |

## Testing Strategy

| Tipo | Qué cubre | Escenarios de la spec |
|---|---|---|
| Unitarias | Reglas de pedidos, validaciones, pertenencia, estados y límites. | FR asociados a cada US. |
| Integración | Endpoint, persistencia real con Testcontainers, permisos, transacciones, concurrencia e idempotencia. | Todos los FR y edge cases definidos. |
| E2E | Recorrido de pantalla/API por rol, incluyendo errores recuperables. | US1, US2, US3, US4. |

**Cobertura mínima esperada**: todos los 10 FR, 6 SC y 12 escenarios de aceptación tienen pruebas trazables; cada edge case abierto queda bloqueado como decisión pendiente o se convierte en escenario tras corregir la spec.

## Project Structure

### Documentation (this feature)

```text
specs/RF-45-realizacion-de-pedido/
├── RF-45-realizacion-de-pedido.md  # especificación funcional (QUÉ)
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

## Phase 3: User Story 1 - Armar el pedido con productos y modalidad de entrega (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US1 en la spec.
**Independent Test**: Puede probarse eligiendo productos de un emprendimiento ambulante y de uno de punto fijo, y verificando que se ofrecen las modalidades que corresponden a cada tipo de vendedor.
**Cubre**: FR-001, FR-002, FR-003; Modalidad de un vendedor ambulante, Modalidades de un vendedor de punto fijo, Pedido con varios ítems, Producto sin disponibilidad.

### Tests

- [ ] T003 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T004 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T005 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US1 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 4: User Story 2 - Indicar dónde recibiré el pedido (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US2 en la spec.
**Independent Test**: Puede probarse creando pedidos de entrega directa, recogida y domicilio, verificando que en entrega directa y domicilio se registra la ubicación y en recogida no se solicita.
**Cubre**: FR-004, FR-005; Ubicación para una entrega, Recogida en punto fijo, Ubicación faltante o inválida.

### Tests

- [ ] T006 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T007 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T008 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US2 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 5: User Story 3 - Marcar el pedido como reserva (Priority: P2)

**Goal**: Implementar exclusivamente el comportamiento de US3 en la spec.
**Independent Test**: Puede probarse creando un pedido marcado como reserva con fecha y hora futuras en cada modalidad y verificando que el atributo se registra sin cambiar la modalidad elegida.
**Cubre**: FR-006, FR-007, FR-008; Pedido marcado como reserva, Fecha u hora inválida.

### Tests

- [ ] T009 Pruebas unitarias, integración/contrato y aceptación para los escenarios listados.

### Implementation

- [ ] T010 Mantener reglas de negocio en domain/usecase; sin lógica en controller.
- [ ] T011 Aplicar contrato REST, validación, autorización y mapper; la UI solo si la historia la requiere.

**Checkpoint**: US3 pasa sus pruebas y no rompe las historias ya completadas.

## Phase 6: User Story 4 - Confirmar el pedido y elegir cómo pagar (Priority: P1)

**Goal**: Implementar exclusivamente el comportamiento de US4 en la spec.
**Independent Test**: Puede probarse confirmando un pedido completo en cada modalidad y verificando que queda en estado pendiente, con el medio de pago permitido y que el vendedor es notificado.
**Cubre**: FR-009, FR-010; Creación exitosa de un pedido de entrega directa o recogida, Pedido de domicilio, Datos incompletos.

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

- Fundaciones transversales → RF-28 / RF-44 / RF-54 / RF-63 → US en orden P1 → P2 → P3 → Polish.
- Dentro de cada historia: pruebas/contrato → dominio y caso de uso → adaptador REST / UI → integración.
- Solo paralelizar tareas que trabajen en archivos independientes y no compartan cambios de esquema.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Qué sucede si el cliente intenta confirmar un pedido sin ningún producto seleccionado? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Cómo maneja el sistema un cambio de precio o de disponibilidad entre la selección y la confirmación? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué ocurre si el cliente está demasiado lejos de un vendedor ambulante para una entrega directa? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué pasa si el cliente pierde conexión después de armar el pedido y antes de confirmarlo (RNF28)? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué ocurre si el vendedor deshabilita un medio de pago mientras el cliente arma su pedido? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Puede reservarse un producto que hoy figura como agotado, si el vendedor lo tendrá en la fecha acordada? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |

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

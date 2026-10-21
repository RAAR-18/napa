# Implementation Plan: Consulta de información del cliente

**Date**: 2026-10-20
**Spec**: [RF-52-consulta-de-informacion-del-cliente.md](./RF-52-consulta-de-informacion-del-cliente.md) (fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos.md](../general/eventos.md)
**Requerimiento funcional**: RF-52
**Historias de usuario**: HU-82
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-45
**Habilita a**: ninguno declarado

> Este documento define **CÓMO** implementar la spec, sin redefinir su comportamiento, el modelo compartido ni los eventos.
> Si una decisión pendiente cambia el comportamiento, corregir primero la spec. No iniciar implementación mientras el Plan Review Checklist tenga elementos pendientes.

## Summary

Implementar Consulta de información del cliente para Cliente, Vendedor (ambulante / punto fijo) en el módulo cuentas, con validaciones de pertenencia/estado, contrato REST y persistencia conforme a la spec y los fundamentos compartidos.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Actor directo según la spec. |
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
| FR-001 | El sistema DEBE permitir al vendedor consultar la información del cliente asociado a un pedido de su emprendimiento. | Regla de dominio/caso de uso en cuentas; contrato GET /api/v1/usuarios/{id}/cliente. | T004, T005 |
| FR-002 | El sistema DEBE limitar la información mostrada del cliente a los datos necesarios para gestionar el pedido (nombre, contacto, dirección de entrega cuando aplique). | Regla de dominio/caso de uso en cuentas; contrato GET /api/v1/usuarios/{id}/cliente. | T004, T005 |
| FR-003 | El sistema DEBE impedir que el vendedor consulte información de un cliente con el que no tiene un pedido asociado. | Regla de dominio/caso de uso en cuentas; contrato GET /api/v1/usuarios/{id}/cliente. | T004, T005 |
| SC-001 | El vendedor puede consultar la información del cliente de un pedido en menos de 3 segundos. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| SC-002 | El 100% de las consultas exitosas corresponden a clientes con al menos un pedido asociado al emprendimiento del vendedor. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| SC-003 | El 0% de los intentos de consultar información de un cliente sin pedido asociado resulta exitoso. | Métrica comprobable en pruebas automatizadas/observabilidad del flujo. | T003, T006 |
| US1: Consulta exitosa de la información del cliente | el sistema muestra los datos del cliente necesarios para gestionar el pedido (nombre, contacto y dirección de entrega cuando aplique) | Prueba de aceptación aislada de US1. | T003 |
| US1: Intento de consultar información de un cliente sin pedido asociado | el sistema deniega el acceso e informa que no existe relación entre el cliente y el emprendimiento | Prueba de aceptación aislada de US1. | T003 |
| Edge case 1 | ¿Qué sucede si el cliente ha actualizado su información personal después de haber realizado el pedido? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 2 | ¿Cómo se maneja la consulta si el cliente eliminó su cuenta después de completar el pedido? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |
| Edge case 3 | ¿Qué datos del cliente se ocultan por privacidad si no son necesarios para gestionar el pedido? | Decisión pendiente: no definir comportamiento distinto al de la spec sin actualizarla. | Decisión pendiente |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización por rol | Sí | Sí · rol y pertenencia se validan en el caso de uso. |
| RNF04 Privacidad / ubicación | Sí | Sí · minimizar datos personales; limitar datos de ubicación a participantes autorizados y solo cuando la feature los maneje. |
| RNF07 Rendimiento ≤ 3 s | Sí | Sí · medir el endpoint y cubrir el SC de latencia de la spec. |
| RNF08 Concurrencia | No | No aplica una escritura concurrente crítica; mantener lectura consistente. |
| RNF16 Trazabilidad | Sí | Sí · preservar created_at/updated_at del modelo al consultar. |
| RNF18 Notificaciones | No | No emite notificaciones salvo que el catálogo compartido lo indique. |
| RNF21 Auditoría | No | No añade auditoría de cambios; respetar timestamps y registros del modelo compartido. |
| RNF23 Consistencia de estados | No | No altera estados de Pedido, Domicilio, Pago o Reporte. |
| RNF24 Mensajes de error claros | Sí | Sí · código común y mensaje español con traceId; no exponer datos sensibles. |
| RNF27 Integridad de pagos | No | No manipula pagos. |
| RNF28 Conectividad intermitente | No | Lecturas con caché segura cuando aplique; mostrar que el dato puede estar desactualizado. |
| RNF29 Medios de pago | No | No aplica. |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende la feature |
|---|---|---|
| Cliente | Lee | Usuario cuya información básica de contacto y entrega es consultada por el vendedor en el contexto de un pedido. |
| Pedido | Lee | Relación que habilita la consulta de la información del cliente por parte del vendedor. |

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Ejecutar Consulta de información del cliente | Cliente | El pedido/interacción pertenece al cliente autenticado. |
| Ejecutar Consulta de información del cliente | Vendedor (ambulante / punto fijo) | El recurso pertenece al emprendimiento del usuario autenticado y respeta su subtipo. |

## API Contracts

### GET /api/v1/usuarios/{id}/cliente

**Rol**: Cliente, Vendedor (ambulante / punto fijo).
**Path params**: {id}: UUID del recurso.
**Query params**: Filtros funcionales de la spec; page, size y sort para listados (spec-general §2.3).

| Parámetro | Tipo | Obligatorio | Valores / ejemplo | Descripción |
|---|---|:---:|---|---|
| page, size, sort | int, int, string | Para listados | 0, 20, fecha,desc | Convención compartida; máximo size 100. |
| filtros de la feature | Según spec | Según spec | Solo valores documentados | Filtro desconocido: 400. |

**Headers de request**:

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | Sí | Bearer JWT, según spec-general §2.1. |
| Idempotency-Key | No | UUID; obligatoria según las convenciones comunes para mutaciones. |
| X-Request-Id | No | Identificador opcional, devuelto siempre como `X-Request-Id`. |

**Body**: Ninguno. Los filtros y parámetros se limitan a lo definido por spec-general §2.3 y la spec de esta feature.

**Respuestas exitosas**:

| HTTP | Cuándo | Headers de response | Body |
|---|---|---|---|
| 200 | 200; 204 si la acción no devuelve representación, según convención común. | `X-Request-Id`; `Location` en 201; `Cache-Control: no-store` para datos sensibles. | Representación definida por la spec o sin cuerpo en 204. |

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

- **Operaciones con mala conexión (RNF28)**: lecturas cacheables solo si los datos no son sensibles y se etiqueta la antigüedad; consultas de datos sensibles siempre requieren autorización vigente.
- **Sincronización / reintento**: `Idempotency-Key` estable por operación, backoff y revalidación de autorización/estado en servidor; servidor como fuente de verdad.
- **Concurrencia (RNF08)**: aplicar control optimista y restricciones únicas cuando haya escrituras; las lecturas usan paginación/índices comunes.
- **Idempotencia (RNF27)**: No crear efectos secundarios en consultas; proteger cualquier mutación asociada.

## Error Handling

| Escenario | Comportamiento esperado | Fuente (spec) |
|---|---|---|
| Datos inválidos | 400 VALIDATION_ERROR con los campos que la spec define; no persistir cambios. | FR de validación |
| Acceso a recurso ajeno | 403 FORBIDDEN o 404 NOT_FOUND según la política de visibilidad común; validar pertenencia en el caso de uso. | Reglas de autorización |

## Testing Strategy

| Tipo | Qué cubre | Escenarios de la spec |
|---|---|---|
| Unitarias | Reglas de cuentas, validaciones, pertenencia, estados y límites. | FR asociados a cada US. |
| Integración | Endpoint, persistencia real con Testcontainers, permisos, transacciones, concurrencia e idempotencia. | Todos los FR y edge cases definidos. |
| E2E | Recorrido de pantalla/API por rol, incluyendo errores recuperables. | US1. |

**Cobertura mínima esperada**: todos los 3 FR, 3 SC y 2 escenarios de aceptación tienen pruebas trazables; cada edge case abierto queda bloqueado como decisión pendiente o se convierte en escenario tras corregir la spec.

## Project Structure

### Documentation (this feature)

```text
specs/RF-52-consulta-de-informacion-del-cliente/
├── RF-52-consulta-de-informacion-del-cliente.md  # especificación funcional (QUÉ)
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

## Phase 3: User Story 1 - Vendedor consulta los datos del cliente de un pedido (Priority: P2)

**Goal**: Implementar exclusivamente el comportamiento de US1 en la spec.
**Independent Test**: Puede probarse desde el detalle de un pedido, accediendo a la información del cliente asociado y verificando que se muestran únicamente los datos necesarios para la gestión del pedido.
**Cubre**: FR-001, FR-002, FR-003; Consulta exitosa de la información del cliente, Intento de consultar información de un cliente sin pedido asociado.

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

- Fundaciones transversales → RF-45 → US en orden P1 → P2 → P3 → Polish.
- Dentro de cada historia: pruebas/contrato → dominio y caso de uso → adaptador REST / UI → integración.
- Solo paralelizar tareas que trabajen en archivos independientes y no compartan cambios de esquema.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Qué sucede si el cliente ha actualizado su información personal después de haber realizado el pedido? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Cómo se maneja la consulta si el cliente eliminó su cuenta después de completar el pedido? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |
| ¿Qué datos del cliente se ocultan por privacidad si no son necesarios para gestionar el pedido? | Puede alterar validaciones, estado, privacidad o respuesta de API. | No asumir una solución; documentar la decisión y corregir primero la spec si cambia comportamiento. | Equipo de producto |

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

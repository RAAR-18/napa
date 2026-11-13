# Implementation Plan: [NOMBRE DE LA FEATURE]

**Date**: [AAAA-MM-DD]
**Spec**: [spec.md](./spec.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [specs/general/spec-general.md](../general/spec-general.md) (roles, convenciones, modelo de datos) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-NN
**Historias de usuario**: HU-NN, HU-NN
**Prioridad (la mayor de la spec)**: P1 | P2 | P3
**Depende de**: [RF-NN, RF-NN o "ninguno"]
**Habilita a**: [RF-NN, RF-NN o "ninguno"]

> Este documento define **CÓMO** se implementa la spec. No redefine el **QUÉ**: si algo del comportamiento cambia, se corrige primero la spec.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

[Una o dos frases: requerimiento principal de la spec + enfoque técnico elegido.]

## Actors

<!-- Roles definidos en spec-general.md. Indicar quién interviene en esta feature. -->

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | [ ] |
| Vendedor (ambulante / punto fijo) | [ ] |
| Domiciliario | [ ] |
| Administrador | [ ] |

## Technical Context

<!--
  Los valores "Fijo del proyecto" salen de requerimientos/no-funcionales.md y de las
  decisiones de diseño del informe. No se cambian por feature.
  Los demás se completan una vez y se heredan; si algo es ambiguo, escribir NEEDS CLARIFICATION.
-->

**Language/Version**: Java 21 LTS (backend, Spring Boot 4.1.x; verificar parche vigente) · TypeScript 5 (frontend, React Native + Expo SDK vigente ≥ 54)
**Primary Dependencies**:
- Backend: Spring Web, Spring Security 7 + JWT, Spring Data JPA + Hibernate Spatial (PostGIS), Flyway, Spring Data Redis, Jackson, Firebase Admin SDK (FCM), Twilio Verify (verificación del celular y recuperación de contraseña: WhatsApp con respaldo SMS), ArchUnit
- Frontend: Expo (development build, no Expo Go), expo-sqlite (cola de salida / caché), expo-notifications (token FCM nativo), react-native-maps, Axios
- Mapas/rutas: servicio externo detrás de un puerto `RoutingPort` (RNF19)
  **Storage**: PostgreSQL 16 + PostGIS · Redis 7 (rate-limit, idempotencia, versión de sesión, caché de rutas, última ubicación del domiciliario) · SQLite en el dispositivo (outbox y caché de lectura)
  **Testing**: JUnit 5 + Mockito + Testcontainers (PostGIS, Redis) + ArchUnit · Jest + React Native Testing Library
  **Target Platform**: Android/iOS (React Native) con prioridad en móviles de gama baja (RNF31) + API REST en contenedor Linux. Web responsive (RNF11) queda fuera del MVP salvo que el equipo lo decida.
  **Project Type**: Monorepo `backend/` (hexagonal, modular por dominio, eventos de dominio con outbox) + `frontend/`
  **Performance Goals**: Operaciones principales ≤ 3 s (RNF07) · verificación de JWT ≤ 10 ms p95 · solicitud de verificación del celular responde ≤ 1,5 s p95 (la entrega del código depende de Twilio y no tiene SLO propio) · consultas PostGIS ≤ 100 ms p95 con índice GiST · evento confirmado → handler iniciado ≤ 1 s p95 · [objetivo propio de la feature, tomado de sus SC-NNN]
  **Constraints** *(fijo del proyecto)*:
- Conectividad intermitente en vendedores y domiciliarios; sincronizar al recuperar señal (RNF28): outbox local + cabecera `Idempotency-Key`
- Medios de pago: efectivo o transferencia a la cuenta del vendedor; el domicilio es solo digital. La plataforma no procesa dinero ni guarda tarjetas/billeteras; solo registra confirmaciones de cobro (RNF29)
- Datos personales y ubicación conforme a la Ley 1581 de 2012 (RNF04)
- Mapas y rutas vía servicio externo (RNF19)
- Flujos cortos y sin formularios extensos (RNF09)
- Optimizado para redes 3G y dispositivos de gama baja (RNF28, RNF31)
  **Scale/Scope**: Supuesto MVP Santa Marta (a validar): ≤ 500 vendedores, ≤ 2 000 pedidos/día, ≤ 100 domicilios simultáneos

## Spec Coverage

<!--
  OBLIGATORIO. Cada FR, SC y escenario de aceptación de la spec debe aparecer aquí
  con el elemento de diseño que lo cubre y las tareas que lo implementan.
  Una fila vacía en "Cubierto por" bloquea el inicio de la implementación.
-->

| Spec | Descripción corta | Cubierto por (componente / endpoint / regla) | Tareas |
|---|---|---|---|
| FR-001 | | | T0XX |
| FR-002 | | | T0XX |
| SC-001 | | | T0XX |
| US1-E1 | Escenario 1 de la User Story 1 | | T0XX |
| Edge case 1 | | | T0XX o "Decisión pendiente" |

## Non-Functional Requirements Check

<!-- Marcar solo los RNF que aplican a esta feature y decir cómo se cumplen. -->

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización por rol | ☐ | |
| RNF04 Privacidad / ubicación | ☐ | |
| RNF07 Rendimiento ≤ 3 s | ☐ | |
| RNF08 Concurrencia | ☐ | |
| RNF16 Trazabilidad | ☐ | |
| RNF18 Notificaciones | ☐ | |
| RNF21 Auditoría | ☐ | |
| RNF23 Consistencia de estados | ☐ | |
| RNF24 Mensajes de error claros | ☐ | |
| RNF27 Integridad de pagos | ☐ | |
| RNF28 Conectividad intermitente | ☐ | |
| RNF29 Medios de pago | ☐ | |

## Entities Involved

<!--
  El modelo de datos NO se define aquí: vive en specs/general/spec-general.md (con su diagrama ER).
  Listar solo las entidades que la feature lee o escribe. Si necesita un cambio de esquema,
  corregir primero spec-general.md y dejar aquí solo la tarea de migración.
-->

| Entidad (spec-general) | Lee / escribe | Restricciones de las que depende la feature |
|---|---|---|
| | | |

## State Transitions

<!-- Solo si la feature cambia el estado de un pedido, domicilio, pago o reporte (RNF23). Borrar si no aplica. -->

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento de trazabilidad / notificación |
|---|---|---|---|---|---|
| | | | | | |

**Transiciones inválidas**: se rechazan con el error `INVALID_STATE` e indican el estado actual.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| [acción] | [cliente / vendedor ambulante / vendedor de punto fijo / domiciliario / administrador] | [ej. el pedido pertenece al emprendimiento del vendedor autenticado] |

## API Contracts

<!-- Convenciones comunes (cabeceras, códigos, paginación, formato de error) en spec-general.md §2. Aquí solo lo propio de cada endpoint. -->

### [MÉTODO] /api/v1/ruta

- **Rol**:
- **Path params**:
- **Query params** (filtros, orden y paginación; "ninguno" si no aplica):

| Parámetro | Tipo | Obligatorio | Valores / ejemplo | Descripción |
|---|---|:---:|---|---|
| page, size, sort | int, int, string | ☐ | `0`, `20`, `fecha,desc` | Paginación y orden |
| [filtro] | | ☐ | | |

- **Headers de request**:

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ☐ | UUID (obligatoria en POST que cambian estado o dinero) |

- **Body**:
  ```json
  {}
  ```
- **Respuestas exitosas**:

| HTTP | Cuándo | Headers de response | Body |
|---|---|---|---|
| 200 / 201 / 202 / 204 | | `Location` (201), `X-Request-Id` | |

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje al usuario |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | | |
| 401 | `UNAUTHORIZED` | Sin sesión o token vencido | |
| 403 | `FORBIDDEN` | | |
| 404 | `NOT_FOUND` | | |
| 409 | `INVALID_STATE` / `ALREADY_CONFIRMED` / `CONFLICT` | | |
| 422 | `IDEMPOTENCY_KEY_REUSED` | | |
| 429 | `RATE_LIMITED` | | |

## Events

<!--
  Catálogo y reglas en specs/general/eventos.md.
  Aquí solo se declara qué evento EMITE el caso de uso. Los listeners NO se planifican en este plan:
  van en el plan del módulo que reacciona (p. ej. notificaciones en el plan de RF-38).
-->

| Evento emitido (eventos.md) | Lo emite el caso de uso | Momento |
|---|---|---|
| | | En la misma transacción, vía `EventPublisherPort` |

**Eventos que consume esta feature** (solo si es el módulo que reacciona): [ninguno]

## Offline & Concurrency Strategy

- **Operaciones que deben tolerar mala conexión (RNF28)**: [ ]
- **Estrategia de sincronización / reintento**: [ej. cola local + reenvío con clave de idempotencia]
- **Operaciones concurrentes (RNF08)**: [ej. bloqueo optimista en la aceptación del domicilio]
- **Idempotencia (RNF27)**: [ej. un pago se confirma una sola vez]

## Error Handling

| Escenario | Comportamiento esperado | Fuente (spec) |
|---|---|---|
| Datos inválidos | | FR-NNN |
| Acceso a recurso ajeno | | FR-NNN |
| Fuera de secuencia de estados | | FR-NNN |
| Servicio externo caído (mapas, notificaciones) | | RNF19 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios de la spec |
|---|---|---|
| Unitarias | Reglas de negocio y validaciones | |
| Integración | Endpoints, base de datos, permisos | |
| E2E | Recorrido completo de la historia | |

**Cobertura mínima esperada**: [ej. todos los escenarios de aceptación Given/When/Then de la spec tienen al menos un test]

## Project Structure

### Documentation (this feature)

```text
specs/RF-NN-nombre/
├── spec.md      # especificación funcional (QUÉ) — fuente de verdad
├── plan.md      # este archivo (CÓMO)
```

Compartidos: `specs/general/` (roles, convenciones, modelo de datos, eventos) y `specs/foundations/plan.md` (infraestructura).

### Source Code (repository root)

<!--
  Reemplazar por las rutas reales cuando se defina el stack.
  Organizar por módulo de dominio (los 12 módulos de requerimientos/funcionales.md) para cumplir RNF13 y RNF25.
-->

```text
backend/src/main/java/co/edu/napa/
├── shared/                          # núcleo compartido
│   ├── domain/                      # DomainEvent, AggregateRoot, DomainException, value objects comunes (Ubicacion, Dinero)
│   └── infrastructure/              # outbox + relay, idempotencia, seguridad/JWT, manejador de errores, config
└── <modulo>/                        # cuenta, emprendimientos, productos, pedidos, domicilios, pagos, ...
    ├── domain/                      # NÚCLEO: sin anotaciones de Spring/JPA
    │   ├── model/                   # entidades, agregados y value objects (aquí viven las reglas de negocio puras)
    │   ├── event/                   # eventos de dominio (records)
    │   └── exception/               # excepciones de dominio
    ├── application/
    │   ├── ports/
    │   │   ├── in/                  # puertos de entrada (interfaces): PublicarDomicilioPort
    │   │   └── out/                 # puertos de salida: DomicilioRepositoryPort, EventPublisherPort
    │   └── usecase/                 # ÚNICA implementación de la lógica de aplicación: PublicarDomicilioUseCase
    └── infrastructure/
        └── adapters/
            ├── in/web/              # controladores REST, DTOs, mappers
            ├── in/event/            # listeners de eventos de otros módulos (sin lógica: llaman a un puerto in)
            ├── out/persistence/     # entidades JPA, repositorios Spring Data, PostGIS, mappers
            ├── out/messaging/       # FCM y verificación del celular (Twilio Verify)
            ├── out/cache/           # Redis: idempotencia, límites de verificación, ubicación
            └── out/external/        # mapas y rutas
backend/src/main/resources/db/migration/        # Flyway
backend/src/test/java/co/edu/napa/<modulo>/     # unit, integration (Testcontainers), ArchUnit

frontend/src/
├── domain/                          # modelos y tipos TypeScript
├── components/                      # UI reutilizable
├── screens/{cliente,vendedor,domiciliario,administrador}/
├── navigation/
├── services/{http,auth,push}/       # Axios, sesión, FCM
├── database/{schema,outbox}/        # SQLite y cola de salida offline
└── hooks/                           # useLocation, useOfflineSync, ...
frontend/__tests__/
```

**Structure Decision**: [Módulo(s) tocados y justificación breve]. Reglas: el dominio no importa Spring/JPA; un módulo solo usa de otro sus `domain/event` y sus `ports/in`; los controladores y listeners solo llaman puertos `in`.

## Phase 1: Setup

**Purpose**: Preparación específica de la feature (omitir si el proyecto ya está inicializado)

- [ ] T001 [descripción + ruta de archivo]

## Phase 2: Foundational

**Purpose**: Prerrequisitos que bloquean todas las historias de esta feature (modelos base, migraciones, permisos)

- [ ] T002 [descripción + ruta de archivo]

**Checkpoint**: Base lista; las historias pueden comenzar.

## Phase 3: User Story 1 - [Título] (Priority: P1)

**Goal**: [Qué entrega esta historia]
**Independent Test**: [Copiar la prueba independiente de la spec]
**Cubre**: [FR-NNN, SC-NNN, escenarios]

### Tests

- [ ] T010 [P] [US1] Test de contrato de [endpoint] en tests/integration/...
- [ ] T011 [P] [US1] Test de integración del escenario [N] en tests/integration/...

### Implementation

- [ ] T012 [P] [US1] Crear modelo [Entidad] en `co/edu/napa/<modulo>/domain/model/...`
- [ ] T013 [US1] Implementar caso de uso [Accion]UseCase en application/usecase (depende de T012)
- [ ] T014 [US1] Implementar endpoint [MÉTODO /ruta]
- [ ] T015 [US1] Validaciones y manejo de errores
- [ ] T016 [US1] Emitir el evento de dominio y registrar trazabilidad (si aplica). Los listeners van en el plan del módulo que reacciona

**Checkpoint**: US1 funcional y probable por separado.

## Phase 4: User Story 2 - [Título] (Priority: P2)

[Repetir la estructura de la Phase 3 por cada User Story de la spec, en orden de prioridad.]

## Phase N: Polish & Cross-Cutting

- [ ] TXXX Revisar rendimiento contra los SC de la spec
- [ ] TXXX Revisar privacidad y permisos (RNF03, RNF04)
- [ ] TXXX Actualizar `requerimientos/trazabilidad.md` si cambió algo
- [ ] TXXX Actualizar documentación

## Dependencies & Execution Order

- Setup → Foundational → User Stories (P1 → P2 → P3) → Polish
- Dependencias entre RF: [ej. RF-14 requiere RF-42 y RF-53 implementados]
- Dentro de cada historia: modelos → servicios → endpoints → integración
- Tareas `[P]` pueden ejecutarse en paralelo (archivos distintos, sin dependencia)

## Open Questions

<!-- Copiar de los "Edge Cases" de la spec y de "Puntos pendientes" del informe lo que afecte a esta feature. -->

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| | | | |

## Plan Review Checklist

*(Basado en el manual SDD. Todo debe estar marcado antes de implementar.)*

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

- `[USn]` en cada tarea enlaza con su historia de usuario para trazabilidad
- Cada historia debe poder completarse y probarse de forma independiente
- Hacer commit por tarea o grupo lógico
- Evitar tareas vagas, conflictos en un mismo archivo y dependencias entre historias que rompan su independencia
# Implementation Plan: Creación de reportes

**Date**: 2026-10-26
**Spec**: [RF-60-creacion-de-reportes.md](./RF-60-creacion-de-reportes.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-60
**Historias de usuario**: HU-91, HU-92, HU-93
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-45 (pedidos), RF-14 (domicilios), RF-10
**Habilita a**: RF-24, RF-25 y RF-37 (accesos a "Reportar un problema"), RF-61, RF-62

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Cualquier cliente, vendedor o domiciliario crea un reporte con `POST /reportes`: un motivo, una descripción y, opcionalmente, **una** entidad relacionada (un pedido, un domicilio o un usuario; el reporte puede no tener ninguna). El reporte nace en `PENDIENTE`, queda asociado a su autor (su rol se deduce de la cuenta) y disponible de inmediato para el administrador. Se valida que el autor sea parte de la entidad reportada: el cliente y el vendedor, de sus pedidos y de los domicilios de sus pedidos; el domiciliario, de los domicilios que tiene asignados; y un usuario solo puede reportar a otro con quien compartió al menos un pedido. La app ofrece "Reportar un problema" desde los pedidos y domicilios (incluidos los flujos de RF-24, RF-25 y RF-37 que dependían de este plan) y puede enviar el reporte por el outbox si no hay conexión.

## Estado actual del código

No existen el módulo `reportes` ni la tabla `reporte`. Los pedidos (RF-45) y domicilios (RF-14) existen en planes anteriores. La consulta de reportes por el administrador (RF-61) y su resolución (RF-62) no son P1 (Open Questions).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Crea reportes |
| Vendedor (ambulante / punto fijo) | Crea reportes |
| Domiciliario | Crea reportes |
| Administrador | Los recibe (RF-61); no los crea |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Crear un reporte en < 1 min (SC-001): formulario de dos campos y una llamada.
- 100 % de los reportes disponibles para el administrador de inmediato (SC-002): se confirman en la base en la misma petición; no hay cola intermedia.
- 100 % de los reportes sin motivo o descripción rechazados (SC-003): validación de borde y del dominio.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El cliente crea un reporte con motivo y descripción | `POST /reportes` con el rol CLIENTE | T010, T013, T014 |
| FR-002 | El vendedor crea un reporte | Igual con VENDEDOR_AMBULANTE y VENDEDOR_PUNTO_FIJO | T011 |
| FR-003 | El domiciliario crea un reporte | Igual con DOMICILIARIO, referido a un domicilio asignado | T012 |
| FR-004 | Todo reporte nace `PENDIENTE` | `Reporte.crear()` fija el estado; el autor viene de la sesión | T010, T015 |
| FR-005 | Validar motivo y descripción antes de registrar | Bean Validation y reglas de `Reporte`: no vacíos ni en blanco, con longitud máxima | T016 |
| SC-001 | < 1 min | Formulario corto | T040 |
| SC-002 | Disponible de inmediato | Persistencia síncrona; consulta del administrador es RF-61 | T010 |
| Decisión del equipo | Avisar al administrador cuando llega un reporte | E18 en el outbox y listener de RF-38 | T020 |
| SC-003 | 100 % de rechazos sin motivo o descripción | Prueba parametrizada | T016 |
| US1-E1 | Reporte del cliente | `201`, `PENDIENTE`, entidad relacionada del cliente | T010 |
| US1-E2 | Reporte del vendedor | `201` asociado a su cuenta | T011 |
| US2-E1 | Reporte del domiciliario asociado a su domicilio | `201` con `domicilioId` | T012 |
| Edge 1 | Reporte sin descripción o motivo | `400 VALIDATION_ERROR` con el campo | T016 |
| Edge 2 | Reportes duplicados sobre el mismo incidente | Los reintentos del mismo envío no duplican (`Idempotency-Key`); varios reportes distintos sobre lo mismo se permiten (Open Questions) | T017 |
| Edge 3 | ¿Puede el usuario ver el estado de sus reportes? | No hay endpoint ni RF en el catálogo; fuera de esta ola (Open Questions) | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Roles y pertenencia de la entidad reportada validados en el caso de uso |
| RNF04 Privacidad | ☑ | No se devuelven datos de terceros en la respuesta |
| RNF16 Trazabilidad | ☑ | `created_at`, autor y estado |
| RNF23 Consistencia | ☑ | A lo sumo una entidad por reporte (CHECK en la base) |
| RNF24 Errores claros | ☑ | `400` con el campo; `404` para entidades que no son suyas |
| RNF27 Integridad | ☑ | `Idempotency-Key` |
| RNF28 Conectividad | ☑ | Envío por el outbox |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Reporte | Escribe | `PENDIENTE`; autor obligatorio; resolutor y nota nulos; como máximo una de las tres relaciones (pedido, domicilio, usuario reportado) |
| Pedido | Lee (vía puerto) | El autor es su cliente o el dueño del emprendimiento |
| Domicilio | Lee (vía puerto) | El autor es el cliente o el vendedor del pedido, o el domiciliario asignado |
| Usuario | Lee (vía puerto) | Debe existir y haber compartido un pedido con el autor |

## State Transitions

| Entidad | Desde | Hacia | Disparador | Precondiciones | Efectos |
|---|---|---|---|---|---|
| Reporte | (no existe) | `PENDIENTE` | El usuario crea el reporte | Rol habilitado; entidad propia o relacionada; motivo y descripción válidos | Disponible para el administrador |

`EN_REVISION` y `RESUELTO` los gestionan RF-61 y RF-62 (fuera de esta ola).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /reportes` | CLIENTE, VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO, DOMICILIARIO | Cliente y vendedor: sus pedidos y los domicilios de sus pedidos; domiciliario: sus domicilios; usuario reportado: con un pedido en común. En otro caso, `404` |

## API Contracts

### POST /api/v1/reportes

- **Rol**: cliente, vendedor o domiciliario · **Path params**: ninguno · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Idempotency-Key` ✅, `Content-Type: application/json`.
- **Body**:

```json
{ "motivo": "Cliente no estaba en la dirección", "descripcion": "Esperé 15 minutos en la dirección indicada y no contestó.", "domicilioId": "…" }
```

`pedidoId`, `domicilioId` y `usuarioId` son opcionales y mutuamente excluyentes (a lo sumo uno). Sin ninguno, el reporte es sobre la plataforma.

- **Respuestas exitosas**: `201` con `X-Request-Id` y body `{ "id": "…", "estado": "PENDIENTE", "motivo": "…", "descripcion": "…", "createdAt": "…" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `Idempotency-Key`, motivo o descripción vacíos o en blanco o demasiado largos, más de una entidad relacionada | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol administrador | — |
| 404 | `NOT_FOUND` | La entidad no existe o no es suya | "No encontramos lo que quieres reportar" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

No se devuelve `Location`: el recurso no tiene `GET` propio en el catálogo (Open Questions).

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| E18 `reportes.reporte-creado` (`ReporteCreadoEvent`: `reporteId`, `autorId`) | `CrearReporteUseCase` | En la misma transacción de la creación (outbox). `notificaciones` avisa a los administradores activos (RF-38 T037) |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el reporte se escribe en el outbox de SQLite con su `Idempotency-Key` y se muestra "pendiente de envío"; el texto escrito no se pierde. Es especialmente útil para el domiciliario en la calle.
- **Concurrencia (RNF08)**: no aplica.
- **Idempotencia (RNF27)**: Redis `idem:{userId}:{key}`; reenviar el mismo envío devuelve el mismo reporte con `Idempotent-Replayed: true`, sin duplicarlo.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Sin motivo o descripción | `400 VALIDATION_ERROR` con el campo | Edge 1 |
| Entidad ajena o inexistente | `404` | FR-001–FR-003 |
| Dos entidades en el mismo reporte | `400 VALIDATION_ERROR` | spec-general §3.3 (restricción 5) |
| Reenvío del mismo reporte | Misma respuesta, sin duplicado | Edge 2 |
| Sin conexión | Outbox con reintento | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `Reporte.crear` (estado inicial, motivo y descripción, una sola entidad) | FR-004, FR-005 |
| Integración | Endpoint con Testcontainers: cliente, vendedor ambulante y de punto fijo, y domiciliario; con cada tipo de entidad y sin ella; entidad ajena; usuario sin pedido en común; administrador (`403`); campos vacíos o en blanco; dos entidades; repetición con la misma clave | US1-E1–E2, US2-E1, FR-001–FR-005 |
| Migración | V12 aplica sobre V11: CHECK de una sola entidad | Restricción 5 |
| Arquitectura | `reportes` usa `pedidos`, `domicilios` y `cuentas` solo por sus puertos `in` | RNF13, RNF25 |
| Frontend | Jest: formulario, validación, entrada desde pedido y domicilio, outbox sin red | SC-001, SC-003 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-60-creacion-de-reportes/
├── RF-60-creacion-de-reportes.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/reportes/
├── domain/
│   ├── event/                    # ReporteCreadoEvent (E18)
│   ├── model/                    # Reporte, EstadoReporte
│   └── exception/                # ReporteInvalidoException
├── application/
│   ├── ports/in/                 # CrearReportePort
│   ├── ports/out/                # ReporteRepositoryPort, EntidadReportablePort (pedido, domicilio, usuario), InteraccionEntreUsuariosPort
│   └── usecase/                  # CrearReporteUseCase
└── infrastructure/adapters/
    ├── in/web/                   # ReporteController (POST /reportes), DTO
    └── out/persistence/          # ReporteJpaEntity y adaptadores a los puertos `in` de pedidos, domicilios y cuentas

backend/src/main/java/co/edu/napa/pedidos/application/ports/in/      # ConsultarParticipantesPedidoPort, ConsultarInteraccionEntreUsuariosPort
backend/src/main/java/co/edu/napa/domicilios/application/ports/in/   # ConsultarParticipantesDomicilioPort
backend/src/main/resources/db/migration/                              # V12__reporte.sql
backend/src/test/java/co/edu/napa/reportes/

frontend/src/app/(comun)/reportes/nuevo.tsx                    # ruta Expo Router: formulario (parámetros: tipo y id opcionales)
frontend/src/components/reportes/FormularioReporte.tsx
frontend/src/components/reportes/BotonReportarProblema.tsx     # se usa en detalle de pedido, domicilio y código de entrega
frontend/src/services/reportes.ts                              # crearReporte() por el outbox
frontend/__tests__/reportes/crear-reporte.test.tsx
```

**Structure Decision**: módulo `reportes` (dueño de `Reporte`). Para validar que el autor es parte de la entidad consulta los puertos `in` de `pedidos` y `domicilios`, que devuelven los participantes (cliente, dueño, domiciliario); los puertos de participantes los reutilizará `comentarios` en RF-01. La pantalla del formulario va en el grupo compartido `(comun)` porque la usan los tres roles.

## Phase 1: Setup

- [ ] T001 Crear el paquete `co.edu.napa.reportes` y su regla ArchUnit en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`

## Phase 2: Foundational

- [ ] T002 Migración `V12__reporte.sql` en `backend/src/main/resources/db/migration/`: tabla `reporte` (id, autor_id, motivo, descripcion, estado, nota_resolucion nulo, resuelto_por_id nulo, pedido_id nulo, domicilio_id nulo, usuario_reportado_id nulo, created_at, updated_at) con CHECK de a lo sumo una entidad y índices `(estado, created_at)` y `(autor_id, created_at)`
- [ ] T003 Puertos `in` de lectura de participantes: `ConsultarParticipantesPedidoPort` (cliente, dueño, emprendimiento, ítems) y `ConsultarInteraccionEntreUsuariosPort` en `pedidos`, y `ConsultarParticipantesDomicilioPort` (cliente, dueño, domiciliario, estado) en `domicilios`
- [ ] T004 Dominio `Reporte`, `EstadoReporte` y `ReporteCreadoEvent` (E18) en `reportes/domain/`, y `EntidadReportablePort` e `InteraccionEntreUsuariosPort` con sus adaptadores

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Cliente o vendedor reportan un problema (Priority: P1)

**Goal**: el administrador recibe los problemas de pedidos, domicilios y cuentas.
**Independent Test**: crear un reporte como cliente y como vendedor y verlo `PENDIENTE`.
**Cubre**: FR-001, FR-002, FR-004, FR-005, SC-001–SC-003, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /reportes` como cliente (con pedido, con domicilio de su pedido, con usuario, sin entidad) en `backend/src/test/java/co/edu/napa/reportes/CrearReporteClienteIT.java`
- [ ] T011 [P] [US1] Test de integración como vendedor ambulante y de punto fijo (pedido propio, domicilio de su pedido, usuario con pedido en común) en `.../reportes/CrearReporteVendedorIT.java`
- [ ] T013 [P] [US1] Test unitario de `Reporte.crear` en `backend/src/test/java/co/edu/napa/reportes/ReporteTest.java`
- [ ] T016 [P] [US1] Test parametrizado de motivo o descripción vacíos, en blanco o demasiado largos, y de dos entidades (`400`), en `.../reportes/CrearReporteValidacionIT.java`
- [ ] T017 [P] [US1] Test de repetición con la misma `Idempotency-Key` (sin duplicado), clave reutilizada con otro cuerpo (`422`), falta de clave (`400`) y reportes distintos sobre lo mismo (se permiten) en `.../reportes/CrearReporteIdempotenciaIT.java`
- [ ] T018 [P] [US1] Test de entidad ajena o inexistente (`404`), usuario sin pedido en común (`404`) y administrador (`403`) en `.../reportes/CrearReporteAutorizacionIT.java`
- [ ] T020 [P] [US1] Test de integración: E18 queda en el outbox con `reporteId` y `autorId` en la misma transacción (y no se publica si la creación falla) en `.../reportes/CrearReporteEventoIT.java`
- [ ] T030 [P] [US1] Test del formulario y de `BotonReportarProblema` (validación, parámetros por origen, outbox sin red) en `frontend/__tests__/reportes/crear-reporte.test.tsx`

### Implementation

- [ ] T014 [US1] `CrearReporteUseCase` (rol, pertenencia de la entidad, creación y publicación de E18) y `CrearReportePort` en `reportes/application/`
- [ ] T015 [US1] `POST /reportes`, su DTO y `Location` en `ReporteController`
- [ ] T019 [US1] Adaptadores de `EntidadReportablePort` e `InteraccionEntreUsuariosPort` sobre los puertos de `pedidos` y `domicilios`
- [ ] T031 [US1] Servicio `crearReporte()` por el outbox en `frontend/src/services/reportes.ts`
- [ ] T032 [US1] `FormularioReporte` y pantalla `frontend/src/app/(comun)/reportes/nuevo.tsx`
- [ ] T033 [US1] `BotonReportarProblema` integrado en el detalle del pedido (cliente y vendedor), en la pantalla del código de confirmación (RF-25) y en la de confirmación de entrega (RF-24 y RF-37)

## Phase 4: User Story 2 - Domiciliario reporta un problema durante la entrega (Priority: P1)

**Goal**: el administrador se entera de los incidentes de la entrega.
**Independent Test**: crear un reporte asociado a un domicilio en curso como domiciliario.
**Cubre**: FR-003, US2-E1.

### Tests (antes de implementar)

- [ ] T012 [P] [US2] Test de integración como domiciliario (domicilio asignado, sin entidad, usuario con pedido en común; domicilio de otro `404`) en `.../reportes/CrearReporteDomiciliarioIT.java`
- [ ] T034 [P] [US2] Test del botón de reporte en la pantalla del domicilio activo del domiciliario en `frontend/__tests__/reportes/reporte-domiciliario.test.tsx`

### Implementation

- [ ] T035 [US2] Rama del domiciliario en `CrearReporteUseCase` (domicilio asignado a él) y `BotonReportarProblema` en `frontend/src/app/(domiciliario)/domicilios/[id].tsx`

**Checkpoint**: US1 y US2 funcionales.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir que crear un reporte toma menos de 1 minuto con usuarios del piloto (SC-001)
- [ ] T041 Revisar permisos y privacidad (RNF03, RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → Polish.
- Dependencias entre RF: requiere RF-45 y RF-14 (pedidos y domicilios); completa los accesos de RF-24, RF-25 y RF-37; el administrador los consulta con RF-61 y los resuelve con RF-62 (P2, fuera de esta ola).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| El administrador necesita RF-61 (listar reportes) para ver lo que se crea aquí; no es P1 | Valor del flujo (SC-002) | Incluir RF-61 y RF-62 en el orden de construcción | Equipo |
| "Motivo" no es un enumerado en spec-general; la spec no define las opciones ni longitudes | Datos | Texto libre: motivo de 3 a 100 caracteres y descripción de 10 a 1000 | Equipo |
| ¿Puede reportarse a cualquier usuario? La spec solo dice "usuario" | Abuso | Solo con quien se compartió al menos un pedido (cliente, dueño del emprendimiento o domiciliario del domicilio) | Equipo |
| El domiciliario solo puede referirse a domicilios asignados a él (y a usuarios con quienes compartió un pedido) | Alcance | Así | Equipo |
| Edge 2: duplicados | Regla | Se permiten reportes distintos sobre lo mismo; solo se evita la duplicación por reenvío | Equipo |
| Edge 3: el usuario no puede ver el estado de sus reportes (no hay RF ni ruta) | Alcance | Fuera de la ola | Equipo |
| Aviso al administrador: se agrega el evento E18 `reportes.reporte-creado` al catálogo (decidido) | Catálogo | E18 → notificaciones a todos los administradores `ACTIVA` (RF-38 T037); hay que agregarlo a `eventos.md` | Equipo |
| spec-general §2.1.1 pide `Location` en todo `201`, pero este recurso no tiene `GET` propio | Contrato | **Resuelto**: no se devuelve `Location`; §2.1.1 debe decir "cuando el recurso tiene GET propio". Si RF-61 agrega `GET /reportes/{id}`, se incluye | Equipo |

## Plan Review Checklist

*(Todo debe estar marcado antes de implementar.)*

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

- `[USn]` en cada tarea enlaza con su historia de usuario.
- Hacer commit por tarea o grupo lógico.
- Evitar tareas vagas y conflictos en un mismo archivo.
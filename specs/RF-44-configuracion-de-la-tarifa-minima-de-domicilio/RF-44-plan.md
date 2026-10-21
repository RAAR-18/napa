# Implementation Plan: Configuración de tarifa mínima de domicilio

**Date**: 2026-10-21
**Spec**: [RF-44-configuracion-de-tarifa-minima-de-domicilio.md](./RF-44-configuracion-de-tarifa-minima-de-domicilio.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados; §3.4 tarifa) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-44
**Historias de usuario**: HU-71
**Prioridad (la mayor de la spec)**: P3. Este plan entrega primero el **mínimo** que necesita RF-45 (piso y valor por defecto) y deja la pantalla del administrador como fase posterior, como permite FR-006.
**Depende de**: RF-10 (rol administrador), foundations (parámetros de configuración)
**Habilita a**: RF-45 (piso de la tarifa del domicilio)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

La tarifa mínima es una tabla de solo inserción: cada cambio del administrador es una fila nueva con fecha y la vigente es la última. Mientras no haya filas rige el parámetro `tarifa.minima_defecto`. El módulo `pagos` expone el puerto `in` `ConsultarTarifaMinimaPort` que usa RF-45 como piso. La tarifa de un pedido se congela al crearlo (`Pedido.tarifa_domicilio`), así que un cambio no afecta pedidos ni domicilios existentes. La pantalla y el endpoint del administrador se construyen en la fase posterior (P3).

## Estado actual del código

El módulo `pagos` no existe todavía (lo crea RF-63). No hay tabla `tarifa_minima` ni parámetros `tarifa.*` en `application.yml`.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Indirecto: su tarifa nunca baja del piso |
| Vendedor (ambulante / punto fijo) | No interviene |
| Domiciliario | Indirecto: cobra una tarifa que respeta el piso |
| Administrador | Define y consulta la tarifa mínima (fase P3) |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Consulta de la tarifa vigente ≤ 10 ms (una fila por índice `created_at desc`), porque se usa en cada pedido de domicilio.
- Actualizar en < 30 s (SC-001), con una pantalla de un campo.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El administrador define y actualiza el mínimo | Fase P3: `POST /tarifa-minima`; `DefinirTarifaMinimaUseCase` | T020, T023, T024 |
| FR-002 | Validar valor positivo mayor a cero | Dominio `TarifaMinima` + CHECK `valor > 0` | T010, T012 |
| FR-003 | Aplicar como piso a todo domicilio nuevo | Puerto `in` `ConsultarTarifaMinimaPort.vigente()`; RF-45 aplica `max(...)` (§3.4) | T011, T013, T014 |
| FR-004 | Conservar la tarifa de los domicilios ya asignados | La tarifa se congela en `Pedido.tarifa_domicilio` al crear el pedido (RF-45); aquí se prueba que un cambio no la toca | T015 |
| FR-005 | Registro de cambios con fecha; el valor anterior es la fila previa | Tabla `tarifa_minima` de solo inserción (`created_at`); `GET /tarifa-minima` calcula `valorAnterior` | T012, T021 |
| FR-006 | Tarifa mínima por defecto como parámetro desde el primer despliegue | Parámetro `tarifa.minima_defecto`; sin filas, el puerto devuelve ese valor | T011, T013 |
| SC-001 | < 30 s | Pantalla de un campo | T031 |
| SC-002 | 100 % de tarifas posteriores cumplen el nuevo valor | El puerto lee siempre la fila vigente; prueba de integración con RF-45 | T014 |
| SC-003 | 0 % de tarifas por debajo del piso | Prueba unitaria de `max` y de integración | T014 |
| US1-E1 | Configuración de la tarifa mínima | Endpoint P3 | T020 |
| US1-E2 | Tarifa calculada por debajo del piso | `TarifaDomicilio` aplica el piso (RF-45) | T014 |
| US1-E3 | Cambio con domicilios ya asignados | Tarifa congelada | T015 |
| Edge 1 | Valor menor o igual a cero | `400 VALIDATION_ERROR` | T010, T012 |
| Edge 2 | Pedidos de domicilio ya hechos y sin publicar | Conservan su tarifa congelada (spec-general) | T015 |
| Edge 3 | Dos administradores a la vez | Ambas filas quedan registradas y gana la más reciente por `created_at` (desempate por id) | T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo `ADMINISTRADOR` define y consulta el historial |
| RNF16 Trazabilidad | ☑ | Cada cambio queda con fecha y autor |
| RNF21 Auditoría | ☑ | Historial inmutable |
| RNF23 Consistencia | ☑ | Valor siempre > 0 (dominio y CHECK) |
| RNF27 Integridad | ☑ | `Idempotency-Key` en el `POST` |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| TarifaMinima | Escribe / lee | `valor > 0`; cada cambio es una fila nueva; sin relación con Pedido ni Domicilio |
| Usuario (administrador) | Lee | Autor del cambio |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Definir tarifa mínima (P3) | ADMINISTRADOR | Ninguna |
| Consultar vigente e historial (P3) | ADMINISTRADOR | Ninguna |
| Consultar el valor vigente (puerto `in`) | Otros módulos (RF-45) | Uso interno |

## API Contracts

> Las rutas de esta feature **no están en el catálogo de spec-general §2.4**: hay que agregarlas (ver Open Questions). Solo se construyen en la fase P3.

### POST /api/v1/tarifa-minima

- **Rol**: administrador · **Path/Query params**: ninguno
- **Body**: `{ "valor": 4000 }` (pesos, entero)

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `201` con `{ "id", "valor", "valorAnterior", "createdAt" }`.
- **Errores**: `400 VALIDATION_ERROR` (valor ≤ 0 o no entero), `401 UNAUTHORIZED`, `403 FORBIDDEN`, `422 IDEMPOTENCY_KEY_REUSED`.

### GET /api/v1/tarifa-minima

- **Rol**: administrador · **Query params**: `page`, `size`, `sort` (por defecto `createdAt,desc`).
- **Respuestas**: `200` con `{ "vigente": { "valor", "createdAt" | null, "origen": "REGISTRO" | "PARAMETRO" }, "historial": { "content": [{ "id", "valor", "valorAnterior", "createdAt" }], "page", "size", "totalElements" } }`.
- **Errores**: `401`, `403`.

### Puerto `in` para otros módulos (no es endpoint)

`ConsultarTarifaMinimaPort.vigente()` devuelve el valor de la última fila o, si no hay, `tarifa.minima_defecto`.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El catálogo no tiene eventos de tarifa mínima |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la pantalla del administrador requiere conexión; el cliente nunca consulta este valor directamente.
- **Concurrencia (RNF08)**: inserción sin actualizar filas previas; dos administradores simultáneos generan dos filas y rige la más reciente (Edge 3). El `valorAnterior` se calcula por consulta, no se guarda.
- **Idempotencia (RNF27)**: `idem:{userId}:{key}` en el `POST`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Valor ≤ 0 | `400 VALIDATION_ERROR` | FR-002, Edge 1 |
| Sin filas registradas | Rige `tarifa.minima_defecto` | FR-006 |
| Cambio con pedidos existentes | No cambia su tarifa congelada | FR-004 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | `TarifaMinima` (valor > 0) y vigente con y sin filas | FR-002, FR-005, FR-006 |
| Integración | Puerto con Testcontainers: default, última fila, desempate; endpoints P3 | US1-E1, Edge 3 |
| Integración con RF-45 | Un cambio no altera la tarifa de pedidos existentes; la nueva aplica a los siguientes | FR-004, SC-002, SC-003 |
| Frontend (P3) | Jest de la pantalla del administrador | SC-001 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-44-configuracion-de-la-tarifa-minima-de-domicilio/
├── RF-44-configuracion-de-tarifa-minima-de-domicilio.md   # spec
└── plan.md                                                # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pagos/
├── domain/model/                 # TarifaMinima
├── application/
│   ├── ports/in/                 # ConsultarTarifaMinimaPort, DefinirTarifaMinimaPort (P3)
│   ├── ports/out/                # TarifaMinimaRepositoryPort, TarifaMinimaDefectoPort
│   └── usecase/                  # ConsultarTarifaMinimaUseCase, DefinirTarifaMinimaUseCase (P3)
└── infrastructure/adapters/
    ├── in/web/                   # TarifaMinimaController (P3)
    └── out/persistence/          # TarifaMinimaJpaEntity, repositorio, adaptador; lectura de `tarifa.minima_defecto`
backend/src/main/resources/db/migration/   # V7__tarifa_minima.sql
backend/src/main/resources/application.yml # tarifa.minima_defecto
backend/src/test/java/co/edu/napa/pagos/

frontend/src/app/(administrador)/tarifa-minima.tsx     # fase P3
frontend/src/services/tarifas.ts                       # fase P3
frontend/__tests__/tarifas/tarifa-minima.test.tsx      # fase P3
```

**Structure Decision**: módulo `pagos` (RF-44 está en "Gestionar pagos"). El mínimo es solo el puerto de consulta, el parámetro y la tabla; no hay controlador ni pantalla hasta la fase P3.

## Phase 1: Setup

- [ ] T001 Agregar `tarifa.minima_defecto` a `backend/src/main/resources/application.yml` (valor definido por el equipo) y a las variables de entorno de despliegue

## Phase 2: Foundational

- [ ] T002 Migración `V7__tarifa_minima.sql`: tabla `tarifa_minima` (id, valor `bigint` con CHECK `> 0`, usuario_id nullable, created_at) e índice `(created_at desc, id desc)` en `backend/src/main/resources/db/migration/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 (mínimo) - Piso de tarifa disponible para RF-45 (Priority: P3 en spec, necesario para RF-45)

**Goal**: que RF-45 pueda calcular la tarifa con su piso antes de existir la pantalla del administrador.
**Independent Test**: sin filas, el puerto devuelve el parámetro; con filas, devuelve la última.
**Cubre**: FR-002, FR-003, FR-004, FR-005, FR-006, SC-002, SC-003, US1-E2, US1-E3, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test unitario de `TarifaMinima` (valor > 0; cero y negativo rechazados) en `backend/src/test/java/co/edu/napa/pagos/TarifaMinimaTest.java`
- [ ] T011 [P] [US1] Test de integración de `ConsultarTarifaMinimaPort` (sin filas → parámetro; con filas → última; desempate) en `.../pagos/ConsultarTarifaMinimaIT.java`
- [ ] T014 [P] [US1] Test de que la tarifa calculada nunca baja del piso, cuando exista `TarifaDomicilio` (RF-45), en `.../pedidos/TarifaDomicilioPisoTest.java`
- [ ] T015 [P] [US1] Test de que un cambio de la mínima no altera la tarifa de pedidos ya creados (cuando exista RF-45) en `.../pedidos/TarifaCongeladaIT.java`
- [ ] T016 [P] [US1] Test de dos inserciones concurrentes: ambas quedan y rige la más reciente en `.../pagos/TarifaMinimaConcurrenciaIT.java`

### Implementation

- [ ] T012 [US1] Dominio `TarifaMinima` y repositorio de solo inserción en `pagos/domain/model/` y `pagos/infrastructure/adapters/out/persistence/`
- [ ] T013 [US1] `ConsultarTarifaMinimaUseCase` y su puerto `in` en `pagos/application/`

**Checkpoint**: RF-45 puede usar el piso.

## Phase 4: User Story 1 (completa) - Administrador define la tarifa mínima (Priority: P3)

**Goal**: el administrador cambia el valor sin tocar la configuración.
**Independent Test**: definir un nuevo valor y ver que los pedidos nuevos lo respetan.
**Cubre**: FR-001, FR-005, SC-001, US1-E1.

### Tests (antes de implementar)

- [ ] T020 [P] [US1] Test de integración de `POST` y `GET /tarifa-minima` (permisos, `valorAnterior`, idempotencia) en `backend/src/test/java/co/edu/napa/pagos/TarifaMinimaApiIT.java`
- [ ] T025 [P] [US1] Test de la pantalla del administrador en `frontend/__tests__/tarifas/tarifa-minima.test.tsx`

### Implementation

- [ ] T021 [US1] `GET /tarifa-minima` con historial paginado y `valorAnterior` en `TarifaMinimaController`
- [ ] T023 [US1] `DefinirTarifaMinimaUseCase`
- [ ] T024 [US1] `POST /tarifa-minima` en `TarifaMinimaController`
- [ ] T026 [US1] Servicio `frontend/src/services/tarifas.ts` y pantalla `frontend/src/app/(administrador)/tarifa-minima.tsx`

## Phase N: Polish & Cross-Cutting

- [ ] T030 Revisar permisos (RNF03)
- [ ] T031 Medir la actualización en < 30 s (SC-001)
- [ ] T032 Agregar las rutas a spec-general §2.4 y actualizar `requerimientos/trazabilidad.md`

## Dependencies & Execution Order

- Setup → Foundational → Fase 3 (mínimo) → [RF-45] → Fase 4 (P3) → Polish.
- Dependencias entre RF: la Fase 3 debe estar antes que RF-45; la Fase 4 puede esperar.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Valor de `tarifa.minima_defecto` | FR-006 | Lo define el equipo | Equipo |
| Las rutas `POST/GET /tarifa-minima` no están en el catálogo de spec-general §2.4 | Convención de API | Agregarlas | Equipo |
| ¿La Fase 3 (mínimo) se entrega con RF-45 aunque la spec sea P3? | Alcance | Sí | Equipo |
| ¿Tope máximo de la tarifa mínima? | FR-002 | Sin tope | Equipo |

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
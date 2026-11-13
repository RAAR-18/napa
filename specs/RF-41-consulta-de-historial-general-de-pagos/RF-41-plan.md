# Implementation Plan: Consulta de historial general de pagos

**Date**: 2026-10-27
**Spec**: [RF-41-consulta-de-historial-general-de-pagos.md](./RF-41-consulta-de-historial-general-de-pagos.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-41
**Historias de usuario**: HU-70
**Prioridad (la mayor de la spec)**: P3
**Depende de**: RF-40, RF-12, RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador supervisa todos los pagos de la plataforma con `GET /pagos` (rama del administrador): cada fila trae fecha, monto, medio, tipo (pago de pedido o de tarifa de domicilio), estado y los usuarios involucrados (comprador, vendedor y domiciliario, con nombre). Se filtra por rango de fechas, estado, medio y tipo, y se pagina. Para ver los pagos que llevan mucho tiempo pendientes basta filtrar por estado y ordenar por fecha ascendente. Ningún otro rol puede usar esta rama: cada uno ve solo lo suyo (RF-40). Un filtro sin resultados devuelve una lista vacía.

## Estado actual del código

RF-40 define `GET /pagos` para cliente, vendedor y domiciliario y agrega a `pago` las columnas de copia `comprador_id`, `vendedor_id` y `domiciliario_id` con sus índices; este plan solo agrega la rama del administrador y los nombres de los usuarios, que salen de `cuentas` en lote. No hay detalle de un pago por id en la spec.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador, Vendedor, Domiciliario | Son los usuarios involucrados; no intervienen |
| Administrador | Consulta y filtra el historial general |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Historial en < 3 s (SC-001): consulta paginada con índices por `created_at` y por los filtros (`estado`, `medio`, `tipo`); los nombres de la página se piden en lote.
- 100 % de las transacciones visibles (SC-002): la rama del administrador no aplica ningún filtro por usuario.
- 0 % de acceso de otros roles (SC-003): la rama se elige por el rol del JWT; un rol distinto de administrador nunca llega a este caso de uso.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Consultar el historial de todos los pagos | `GET /pagos` rama ADMINISTRADOR | T010, T020, T021 |
| FR-002 | Mostrar fecha, monto, medio, tipo, estado y usuarios involucrados | Campos de la fila con comprador, vendedor y domiciliario | T010, T022 |
| FR-003 | Filtrar por fecha, estado, medio o tipo | Parámetros `desde`, `hasta`, `estado`, `medio`, `tipo` | T011, T023 |
| FR-004 | Restringir el acceso al administrador | Despacho por rol; el resto va a RF-40 | T013 |
| SC-001 | < 3 s | Índices, paginación y lote | T014 |
| SC-002 | 100 % de transacciones visibles | Sin filtro por usuario | T010 |
| SC-003 | 0 % de acceso sin ser administrador | Prueba de aislamiento por rol | T013 |
| US1-E1 | Consulta general con todos los datos | `200` con `content` paginado | T010 |
| US1-E2 | Filtrado | Solo los pagos que cumplen los filtros | T011 |
| Edge 1 | Volumen alto | Paginación por defecto de 20 y máximo 100 | T014 |
| Edge 2 | Pagos pendientes por mucho tiempo | Filtro `estado` con orden por fecha ascendente; la app resalta el pendiente de más de un umbral (Open Questions) | T012 |
| Edge 3 | Filtro sin resultados | `200` con lista vacía | T015 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR en esta rama |
| RNF04 Datos personales | ☑ | `no-store`; nombres e ids sin contacto |
| RNF07 Rendimiento ≤ 3 s | ☑ | Índices, paginación y lotes |
| RNF13 Arquitectura | ☑ | `pagos` pide nombres y modalidad por puertos `in` |
| RNF24 Errores claros | ☑ | `400` con el parámetro inválido |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pago | Lee | Todos, con las columnas de copia de RF-40 |
| Usuario | Lee (puerto) | Solo `id` y `nombre` |

## State Transitions

Esta feature es de solo lectura: no cambia estados.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /pagos` | ADMINISTRADOR (rama de esta feature) | Todos los pagos; las otras ramas son de RF-40 |

## API Contracts

### GET /api/v1/pagos (rama del administrador)

- **Rol**: administrador · **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| desde, hasta | fecha ISO-8601 | ☐ | `2026-10-01` | Rango sobre la fecha de creación |
| estado | enum | ☐ | `PENDIENTE_CONFIRMACION` | `PENDIENTE_CONFIRMACION`, `REGISTRADO` o `CONFIRMADO` |
| medio | enum | ☐ | `CUENTA_BANCARIA` | `EFECTIVO` o `CUENTA_BANCARIA` |
| tipo | enum | ☐ | `PEDIDO` | `PEDIDO` o `TARIFA_DOMICILIO` |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `createdAt,desc` | `sort` admite `createdAt` y `monto`; por defecto `createdAt,desc` |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "content": [
    { "id": "…", "tipo": "PEDIDO", "monto": 14500, "medio": "CUENTA_BANCARIA", "estado": "CONFIRMADO",
      "createdAt": "…", "confirmadoAt": "…", "pedidoId": "…", "domicilioId": null,
      "comprador": { "id": "…", "nombre": "Ana Pérez" }, "vendedor": { "id": "…", "nombre": "Rosa Martínez" }, "domiciliario": null }
  ],
  "page": 0, "size": 20, "totalElements": 1
}
```

En un pago de tarifa `comprador` es `null` y `domiciliario` trae a quien cobra; en un pago de pedido `domiciliario` es `null`.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido, valores inválidos, `desde` mayor que `hasta`, `size` mayor que 100, `sort` inválido | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |

Los roles distintos de administrador no reciben `403`: la misma ruta les devuelve su propio historial (RF-40).

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el panel del administrador exige conexión y no guarda datos de terceros en SQLite; sin red muestra el error con reintento.
- **Concurrencia (RNF08)**: no aplica a la lectura.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Filtro sin resultados | `200` con lista vacía | Edge 3 |
| Filtro inválido | `400` con el parámetro | RNF24 |
| Pago pendiente por mucho tiempo | Visible con filtro por estado | Edge 2 |
| Rol distinto de administrador | Ve su propio historial (RF-40), nunca el general | FR-004 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Todos los pagos de todos los usuarios con medio, tipo, estado y usuarios involucrados, de pedido y de tarifa | FR-001, FR-002, SC-002, US1-E1 |
| Integración | Filtros por fechas, estado, medio y tipo, combinados; paginación y orden; filtros inválidos; resultado vacío | FR-003, US1-E2, Edge 1, Edge 3 |
| Integración | Cliente, vendedor y domiciliario no reciben el historial general, solo el propio | FR-004, SC-003 |
| Integración | Pagos pendientes viejos: filtro por estado y orden ascendente | Edge 2 |
| Rendimiento | 100 000 pagos: primera página y filtros en < 3 s | SC-001 |
| Frontend | Jest: lista con filtros, resalte de pendientes viejos, vacío, error con reintento | US1-E1–E2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-41-consulta-de-historial-general-de-pagos/
├── RF-41-consulta-de-historial-general-de-pagos.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pagos/
├── application/
│   ├── ports/in/                 # ListarPagosAdminPort
│   ├── ports/out/                # PagoRepositoryPort (consulta global con filtros), NombresUsuariosPort
│   └── usecase/                  # ListarPagosAdminUseCase
└── infrastructure/adapters/
    └── in/web/                   # PagoController: despacha por rol (administrador → este caso de uso), PagoAdminResponse

backend/src/main/java/co/edu/napa/cuentas/application/ports/in/   # ConsultarNombresUsuariosPort (RF-12)
backend/src/main/resources/db/migration/                           # índices por estado, medio y tipo si faltan
backend/src/test/java/co/edu/napa/pagos/

frontend/src/app/(administrador)/pagos/index.tsx                   # historial general con filtros
frontend/src/components/pagos/FilaPagoAdmin.tsx                    # incluye usuarios y resalte de pendientes viejos
frontend/src/services/pagosAdmin.ts
frontend/__tests__/pagos/historial-admin.test.tsx
```

**Structure Decision**: módulo `pagos`, en la misma ruta que RF-40 con una rama por rol y un caso de uso propio. Los nombres de los usuarios involucrados salen de `cuentas` en lote para la página, igual que RF-12.

## Phase 1: Setup

- [ ] T001 Confirmar las columnas de copia de `pago` y el despacho por rol de `GET /pagos` (RF-40)

## Phase 2: Foundational

- [ ] T002 Índices por `(estado, created_at DESC)`, `(medio, created_at DESC)` y `(tipo, created_at DESC)` en la siguiente migración libre de `backend/src/main/resources/db/migration/`
- [ ] T003 `NombresUsuariosPort` en `pagos` sobre `ConsultarNombresUsuariosPort` de `cuentas` (RF-12 T003)
- [ ] T004 Método de consulta global con filtros en `PagoRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Supervisar las transacciones (Priority: P3)

**Goal**: el administrador ve y filtra todos los pagos.
**Independent Test**: entrar como administrador, ver pagos de varios usuarios y filtrar por estado y medio.
**Cubre**: FR-001–FR-004, SC-001–SC-003, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /pagos` como administrador (todos los pagos, campos y usuarios involucrados) en `backend/src/test/java/co/edu/napa/pagos/HistorialAdminIT.java`
- [ ] T011 [P] [US1] Test de filtros por fechas, estado, medio y tipo, combinados, paginación y orden, en `.../pagos/HistorialAdminFiltrosIT.java`
- [ ] T012 [P] [US1] Test de pagos pendientes viejos (filtro por estado y orden ascendente) en `.../pagos/HistorialAdminPendientesIT.java`
- [ ] T013 [P] [US1] Test de que los demás roles reciben solo su historial y no el general, en `.../pagos/HistorialAdminAislamientoIT.java`
- [ ] T014 [P] [US1] Test de rendimiento con 100 000 pagos en `.../pagos/HistorialAdminRendimientoIT.java`
- [ ] T015 [P] [US1] Test de filtro sin resultados y de filtros inválidos en `.../pagos/HistorialAdminVacioIT.java`
- [ ] T030 [P] [US1] Test de la pantalla de historial del administrador (filtros, resalte, vacío, error) en `frontend/__tests__/pagos/historial-admin.test.tsx`

### Implementation

- [ ] T020 [US1] `ListarPagosAdminPort` y `ListarPagosAdminUseCase`: valida los filtros y arma la página
- [ ] T021 [US1] Despacho por rol en `GET /pagos` de `PagoController`
- [ ] T022 [US1] `PagoAdminResponse` con los usuarios involucrados (nombres en lote)
- [ ] T023 [US1] Filtros por fechas, estado, medio y tipo en la consulta global
- [ ] T031 [US1] `pagosAdmin.ts` y `FilaPagoAdmin` en `frontend/src/`
- [ ] T032 [US1] Pantalla `(administrador)/pagos/index.tsx` con filtros

**Checkpoint**: el administrador supervisa todos los pagos.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` del listado con filtros combinados
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-40 (ruta, columnas y despacho por rol), RF-12 (puerto de nombres) y RF-10.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 2: ¿desde cuándo un pago pendiente es "mucho tiempo"? | Regla | La app resalta los pendientes de más de 48 horas (valor del equipo); el servidor no decide | Equipo |
| La spec dice "usuarios involucrados" | Alcance | Comprador, vendedor y domiciliario con su nombre e id; sin contacto | Equipo |
| ¿Un rol distinto de administrador recibe `403` en esta rama? | Contrato | No: la misma ruta le devuelve su historial (RF-40) | Equipo |
| No hay detalle de un pago ni exportación | Alcance | La spec no los pide | Equipo |
| La ruta ya está en spec-general §2.3 pero no en §2.4 | Contrato | Se agrega a §2.4 con RF-40 | Equipo |
| El historial no cambia estados ni permite corregir pagos | Alcance | Solo lectura | Equipo |

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
# Implementation Plan: Edición de emprendimiento

**Date**: 2026-10-27
**Spec**: [RF-29-edicion-de-emprendimiento.md](./RF-29-edicion-de-emprendimiento.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-29
**Historias de usuario**: HU-54
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-28, RF-10
**Habilita a**: RF-31 y RF-32 (muestran lo editado)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor edita su emprendimiento con `PATCH /emprendimientos/me`: descripción, hora de apertura, hora de cierre y ubicación (uno o varios campos). Un vendedor ambulante actualiza su ubicación de venta con el mismo endpoint cada vez que cambia de zona, desde un botón "Estoy aquí" que toma el GPS del teléfono. `GET /emprendimientos/me` devuelve su emprendimiento activo para precargar el formulario (y cierra la pregunta abierta de RF-28 sobre cómo saber si ya tiene uno). Los cambios se validan antes de guardar y los ven los clientes de inmediato, porque RF-31 y RF-32 leen la misma fila. El nombre y la categoría no se editan.

## Estado actual del código

RF-28 crea el emprendimiento y su ubicación (una fila propia, 1:1) y dejó abierta la ruta `GET /emprendimientos/me`. RF-31 y RF-32 leen `emprendimiento` y `ubicacion` directamente, sin caché, así que no hay nada que invalidar. El domicilio guarda una copia del punto A al publicarse (spec-general), por lo que mover la ubicación no altera domicilios ya publicados.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Ve los cambios (RF-31, RF-32); no interviene |
| Vendedor (ambulante / punto fijo) | Edita su emprendimiento; el ambulante actualiza su zona durante la jornada |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Cambios visibles en < 3 s (SC-001): la actualización es de una o dos filas y las lecturas de los clientes no usan caché.
- 100 % de ediciones inválidas rechazadas con mensaje claro (SC-002): validación en el DTO y en el dominio `Emprendimiento`, con `details.campo`.
- Ubicación que se actualiza muchas veces en la jornada: se actualiza la fila existente (`UPDATE`), con el punto PostGIS recalculado, sin crear filas nuevas.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Editar ubicación, apertura, cierre y descripción | `PATCH /emprendimientos/me` (parcial) | T010, T020, T021 |
| FR-002 | Validar los datos antes de guardar | Bean Validation y reglas de `Emprendimiento` | T011, T012, T022 |
| FR-003 | Reflejar los cambios en la vista pública de inmediato | RF-31 y RF-32 leen la misma fila | T013 |
| FR-004 | El ambulante actualiza su ubicación en cada cambio de zona | Mismo endpoint, solo `ubicacion`; botón "Estoy aquí" con el GPS y cola local sin red | T014, T032, T033 |
| SC-001 | Cambios visibles en < 3 s | Sin caché intermedia | T013 |
| SC-002 | 100 % de ediciones inválidas rechazadas | Pruebas parametrizadas | T011, T012 |
| US1-E1 | Edición exitosa de un campo | `200` con el emprendimiento actualizado | T010 |
| US1-E2 | Datos inválidos | `400 VALIDATION_ERROR` con el campo | T011, T012 |
| Edge 1 | Cambiar la ubicación con pedidos o domicilios en curso | Se permite; los domicilios ya publicados conservan su punto A y los pedidos su ubicación de entrega (Open Questions) | T015 |
| Edge 2 | Edición concurrente desde dos sesiones | Última escritura gana por campo; el choque real de dos escrituras simultáneas da `409 CONFLICT` por versión | T016 |
| Edge 3 | Editar un emprendimiento eliminado | `404`: `/me` solo resuelve el emprendimiento `ACTIVO` | T017 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el vendedor y solo su emprendimiento (`/me`) |
| RNF07 Rendimiento ≤ 3 s | ☑ | Una sola fila de emprendimiento y una de ubicación |
| RNF08 Concurrencia | ☑ | Versión optimista y `409 CONFLICT` en un choque real |
| RNF13 Arquitectura | ☑ | El módulo `emprendimientos` usa el puerto de ubicación compartido |
| RNF23 Consistencia | ☑ | Emprendimiento y ubicación se actualizan en la misma transacción |
| RNF24 Errores claros | ☑ | `details.campo` en cada `400` |
| RNF28 Conectividad | ☑ | La ubicación del ambulante se encola en la outbox y se envía al volver la red |
| RNF31 Gama baja | ☑ | El GPS se pide una vez por toque |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Emprendimiento | Lee / escribe (`descripcion`, `hora_apertura`, `hora_cierre`) | Solo el `ACTIVO` del vendedor; `nombre` y `categoria` no se editan |
| Ubicacion | Escribe (en su lugar) | Una fila propia por emprendimiento; el punto fijo exige `direccion` (como RF-28) |

## State Transitions

No cambia estados: el emprendimiento sigue `ACTIVO`. Solo se edita el emprendimiento `ACTIVO` del vendedor; uno `ELIMINADO` o `INACTIVO` no se resuelve con `/me`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /emprendimientos/me` | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | El emprendimiento `ACTIVO` del usuario del JWT |
| `PATCH /emprendimientos/me` | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | Igual |

## API Contracts

### GET /api/v1/emprendimientos/me

- **Rol**: vendedor · **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body igual al de `POST /emprendimientos` (RF-28): `{ "id", "nombre", "categoria", "descripcion", "tipoVendedor", "horaApertura", "horaCierre", "estado": "ACTIVO", "ubicacion": {…} }`.
- **Errores**: `401 UNAUTHORIZED`, `403 FORBIDDEN` (rol distinto de vendedor), `404 NOT_FOUND` ("Aún no tienes un emprendimiento activo").

### PATCH /api/v1/emprendimientos/me

- **Rol**: vendedor (ambulante o de punto fijo) · **Path params**: ninguno · **Query params**: ninguno
- **Body** (parcial; al menos un campo):

```json
{
  "descripcion": "Frutas frescas y jugos",
  "horaApertura": "06:00",
  "horaCierre": "19:00",
  "ubicacion": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Calle 15 # 3-20", "referencia": "Frente al parque" }
}
```

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Content-Type | ✅ | `application/json` |

`PATCH` no exige `Idempotency-Key` (spec-general). Repetir el mismo cuerpo deja el mismo resultado.

- **Respuestas exitosas**: `200` con el emprendimiento completo (mismo cuerpo del `GET`) y `X-Request-Id`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo vacío; descripción de más de 500 caracteres; hora con formato inválido o apertura igual al cierre; latitud o longitud fuera de rango; dirección vacía en punto fijo; campo no editable (`nombre`, `categoria`, `estado`) | `details.campo` |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor | — |
| 404 | `NOT_FOUND` | El vendedor no tiene un emprendimiento `ACTIVO` (no existe o fue eliminado) | "Aún no tienes un emprendimiento activo" |
| 409 | `CONFLICT` | Dos escrituras simultáneas sobre el mismo emprendimiento | "Tu emprendimiento cambió; vuelve a intentarlo" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Editar el emprendimiento no tiene reacciones en otros módulos |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el ambulante cambia de zona con poca señal. La app guarda la ubicación en la outbox local con una sola entrada por emprendimiento (una posterior reemplaza a la anterior) y la envía al recuperar la red; mientras tanto muestra "Ubicación pendiente de enviar". Las demás ediciones también se encolan.
- **Concurrencia (RNF08)**: dos sesiones del mismo vendedor: se aplica cada campo recibido y gana la última; una colisión real en la misma fila se detecta con la versión y la app reintenta una vez.
- **Idempotencia (RNF27)**: `PATCH` es idempotente por naturaleza.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Campo inválido | `400` con el campo | US1-E2 |
| Apertura igual al cierre | `400` | RF-28 |
| Sin emprendimiento activo o eliminado | `404` | Edge 3 |
| Choque de escrituras | `409 CONFLICT`; la app reintenta | Edge 2 |
| Sin red | Se encola la última ubicación | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitario | Reglas de `Emprendimiento` al editar (horario que cruza la medianoche, apertura igual al cierre, dirección obligatoria en punto fijo) | FR-002 |
| Integración | Endpoints con Testcontainers: edición de un campo y de varios, datos inválidos, sin cambios, sin emprendimiento, eliminado, rol distinto, sin sesión | FR-001, FR-002, US1-E1–E2, Edge 3 |
| Integración | Lo editado aparece de inmediato en `GET /emprendimientos` y `GET /emprendimientos/{id}` (RF-31, RF-32), incluida la distancia nueva | FR-003, SC-001 |
| Integración | Cambio de ubicación con un domicilio publicado y un pedido en curso: no los altera | Edge 1 |
| Integración | Dos `PATCH` simultáneos | Edge 2 |
| Frontend | Jest: formulario, botón "Estoy aquí", cola local con una sola entrada pendiente | FR-004 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-29-edicion-de-emprendimiento/
├── RF-29-edicion-de-emprendimiento.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/emprendimientos/
├── domain/model/                 # Emprendimiento.editar(...)
├── application/
│   ├── ports/in/                 # ConsultarMiEmprendimientoPort, EditarEmprendimientoPort
│   ├── ports/out/                # EmprendimientoRepositoryPort, UbicacionRepositoryPort
│   └── usecase/                  # ConsultarMiEmprendimientoUseCase, EditarEmprendimientoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # EmprendimientoVendedorController (GET y PATCH /emprendimientos/me)
    └── out/persistence/          # @Version en EmprendimientoJpaEntity y actualización de la ubicación en su lugar

backend/src/test/java/co/edu/napa/emprendimientos/

frontend/src/app/(vendedor)/emprendimiento/editar.tsx                 # formulario de edición
frontend/src/components/emprendimientos/FormularioEmprendimiento.tsx  # compartido con RF-28
frontend/src/components/emprendimientos/BotonEstoyAqui.tsx            # GPS y envío de la ubicación (ambulante)
frontend/src/services/emprendimientos.ts                              # obtenerMiEmprendimiento(), editar()
frontend/src/database/outbox/ubicacion_emprendimiento.ts              # una entrada pendiente por emprendimiento
frontend/__tests__/emprendimientos/
```

**Structure Decision**: módulo `emprendimientos`. La ruta es `/emprendimientos/me` porque el vendedor tiene un solo emprendimiento `ACTIVO` y así no necesita un `id` ni hay riesgo de editar el de otro; `GET /me` cierra la pregunta abierta de RF-28. La ubicación se actualiza en su lugar para que el ambulante pueda hacerlo muchas veces al día sin llenar la tabla. El formulario es el mismo de RF-28.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `emprendimientos` y su regla ArchUnit (RF-28)

## Phase 2: Foundational

- [ ] T002 Columna `version` y `@Version` en `EmprendimientoJpaEntity` en la siguiente migración libre de `backend/src/main/resources/db/migration/` (si RF-28 no la creó)
- [ ] T003 Método de actualización en su lugar de la ubicación en `UbicacionRepositoryPort` (compartido con RF-08), recalculando el punto PostGIS
- [ ] T004 Método de búsqueda del emprendimiento `ACTIVO` por vendedor en `EmprendimientoRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Mantener actualizado mi emprendimiento (Priority: P2)

**Goal**: el vendedor corrige o ajusta su información y los clientes la ven al instante.
**Independent Test**: editar la descripción y la ubicación y verlas en el detalle público.
**Cubre**: FR-001–FR-004, SC-001, SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET` y `PATCH /emprendimientos/me` con uno y con varios campos en `backend/src/test/java/co/edu/napa/emprendimientos/EditarEmprendimientoIT.java`
- [ ] T011 [P] [US1] Test parametrizado de datos inválidos (cuerpo vacío, descripción larga, hora inválida, apertura igual al cierre, coordenadas fuera de rango, dirección vacía en punto fijo, campo no editable) en `.../emprendimientos/EditarEmprendimientoValidacionIT.java`
- [ ] T012 [P] [US1] Test unitario de las reglas de edición de `Emprendimiento` en `.../emprendimientos/EmprendimientoEdicionTest.java`
- [ ] T013 [P] [US1] Test de que el cambio aparece de inmediato en RF-31 y RF-32, con la distancia nueva, en `.../emprendimientos/EditarEmprendimientoVisibilidadIT.java`
- [ ] T014 [P] [US1] Test de actualizar solo la ubicación varias veces seguidas (una sola fila de ubicación) en `.../emprendimientos/ActualizarUbicacionIT.java`
- [ ] T015 [P] [US1] Test de cambio de ubicación con un domicilio publicado y un pedido en curso en `.../emprendimientos/EditarUbicacionConPendientesIT.java`
- [ ] T016 [P] [US1] Test de dos `PATCH` simultáneos (`409 CONFLICT` en el choque, sin pérdida de campos distintos) en `.../emprendimientos/EditarEmprendimientoConcurrenciaIT.java`
- [ ] T017 [P] [US1] Test de sin emprendimiento, eliminado, rol distinto y sin sesión en `.../emprendimientos/EditarEmprendimientoAutorizacionIT.java`
- [ ] T030 [P] [US1] Test del formulario de edición precargado y de su validación en `frontend/__tests__/emprendimientos/editar-emprendimiento.test.tsx`
- [ ] T031 [P] [US1] Test de `BotonEstoyAqui` y de la cola local con una sola entrada pendiente en `frontend/__tests__/emprendimientos/estoy-aqui.test.tsx`

### Implementation

- [ ] T020 [US1] `Emprendimiento.editar` con sus reglas en `emprendimientos/domain/model/Emprendimiento.java`
- [ ] T021 [US1] `ConsultarMiEmprendimientoUseCase` y `EditarEmprendimientoUseCase` con sus puertos `in`
- [ ] T022 [US1] `GET` y `PATCH /emprendimientos/me` y su DTO parcial en `EmprendimientoVendedorController`
- [ ] T023 [US1] Reintento único ante `409 CONFLICT` en el caso de uso y mapeo del error
- [ ] T032 [US1] `FormularioEmprendimiento` compartido con RF-28 y pantalla `emprendimiento/editar.tsx`
- [ ] T033 [US1] `BotonEstoyAqui` (GPS), `services/emprendimientos.ts` y la cola `ubicacion_emprendimiento` con envío al recuperar la red

**Checkpoint**: el vendedor edita su emprendimiento y los clientes lo ven.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` de la actualización de la ubicación y de RF-31 tras muchas actualizaciones
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-28 (emprendimiento y ubicación) y RF-10; los datos editados los muestran RF-31 y RF-32; aporta `GET /emprendimientos/me` que RF-28 dejó pendiente.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 1: la spec pregunta qué pasa si cambia la ubicación con pedidos o domicilios en curso | Regla | Se permite sin aviso: el domicilio conserva su punto A y el pedido su ubicación de entrega. Un cliente con un pedido de recogida pendiente verá la ubicación nueva. **Confirmar**, o bloquear al punto fijo con pedidos de recogida en curso | Equipo |
| FR-001 no incluye el nombre ni la categoría | Alcance | No se editan | Equipo |
| Ruta `/emprendimientos/me` (no listada en spec-general) y `GET /me` | Contrato | Se agregan a spec-general | Equipo |
| Un emprendimiento `INACTIVO` (¿quién lo produce?) no se resuelve con `/me` | Estado | Solo el `ACTIVO`; revisar con RF-30 | Equipo |
| La ubicación del ambulante no guarda historial | Datos | Se actualiza en su lugar | Equipo |
| Descripción máxima de 500 caracteres | Validación | Igual que RF-28 | Equipo |
| Edición concurrente | Concurrencia | Última escritura por campo; versión solo para el choque real | Equipo |

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
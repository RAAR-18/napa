# Implementation Plan: Creación de emprendimiento

**Date**: 2026-10-21
**Spec**: [RF-28-creacion-de-emprendimiento.md](./RF-28-creacion-de-emprendimiento.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-28
**Historias de usuario**: HU-53
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-07, RF-10 (sesión y rol), foundations F020 (`ubicacion`, PostGIS)
**Habilita a**: RF-29, RF-30, RF-31, RF-32, RF-45, RF-54, RF-63

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**: si algo cambia de comportamiento, se corrige primero la spec.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Un vendedor (ambulante o de punto fijo) crea su emprendimiento con nombre, categoría de una lista fija, descripción, hora de apertura y de cierre y una ubicación. Se crea `ACTIVO` y visible de inmediato. Un índice único parcial en BD garantiza un solo emprendimiento `ACTIVO` por vendedor, incluso con dos solicitudes simultáneas. El tipo de vendedor no se guarda: sale de `Usuario.rol`.

## Estado actual del código

El módulo `emprendimientos` no existe todavía. Ya existen la tabla `ubicacion` (con `punto` PostGIS e índice GiST, migración V3), `GeoPoint` en `shared/domain/geo` y la autenticación con roles en `cuentas`. `UbicacionRepositoryPort` hoy vive en `cuentas` (ver Open Questions).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene (consume el resultado en RF-31/RF-32) |
| Vendedor (ambulante / punto fijo) | Crea su emprendimiento |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md` (stack fijo del proyecto). Objetivos propios, tomados de la spec:

- Crear el emprendimiento en < 3 minutos de uso (SC-001): formulario de una pantalla, sin campos extra.
- 100 % de emprendimientos válidos visibles de inmediato (SC-002): `estado = ACTIVO` desde la creación, sin paso de aprobación.
- Operación principal ≤ 3 s (RNF07).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Crear con nombre, categoría (lista fija), ubicación, descripción, hora de apertura y de cierre | `POST /emprendimientos`; `CrearEmprendimientoUseCase`; enum `CategoriaEmprendimiento` | T010, T013, T014 |
| FR-002 | Validar campos obligatorios | Bean Validation en el DTO + validaciones de dominio (`Emprendimiento`) | T011, T015 |
| FR-003 | Impedir un segundo emprendimiento activo | Consulta previa + índice único parcial `uq_emprendimiento_activo_por_usuario` → `409 CONFLICT` | T012, T016 |
| FR-004 | Visible de inmediato | `estado = ACTIVO` al crear; RF-31/RF-32 lo leen sin paso extra | T010 |
| FR-005 | Interpretar la ubicación según el rol | Dominio: punto fijo exige `direccion`; ambulante usa la zona habitual (punto + referencia) | T017, T018 |
| FR-006 | Asociar el tipo de vendedor sin pedirlo | No se recibe en el body: se deduce de `Usuario.rol` del JWT; la respuesta lo devuelve como `tipoVendedor` | T013 |
| SC-001 | < 3 min | Pantalla única de formulario; prueba de usabilidad en piloto | T031 |
| SC-002 | 100 % visibles | Prueba de integración: crear y listar | T010 |
| US1-E1 | Creación exitosa | Endpoint + persistencia | T010 |
| US1-E2 | Segundo emprendimiento activo | `409 CONFLICT` con mensaje "Ya tienes un emprendimiento activo" | T012 |
| Edge 1 | Campos obligatorios vacíos | `400 VALIDATION_ERROR` indicando el campo | T011 |
| Edge 2 | Ubicación inválida o fuera de cobertura | Validación de rango lat/lng; la cobertura es **Decisión pendiente** (Open Questions) | T011 |
| Edge 3 | Nombre ya usado en la misma zona | **Decisión pendiente**: el modelo no define unicidad de nombre | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF01 Seguridad | ☑ | Rol y propietario salen del JWT, nunca del body |
| RNF03 Autorización | ☑ | `@PreAuthorize` solo para los dos roles de vendedor |
| RNF04 Privacidad | ☑ | La zona del ambulante es un punto aproximado elegido por él |
| RNF07 Rendimiento | ☑ | Una transacción corta (inserta `ubicacion` y `emprendimiento`) |
| RNF08 Concurrencia | ☑ | Índice único parcial resuelve la carrera |
| RNF09 Flujos cortos | ☑ | Formulario de una pantalla |
| RNF16 Trazabilidad | ☑ | `created_at`, `updated_at` |
| RNF22 Geografía | ☑ | `geography(Point,4326)` derivada de latitud y longitud |
| RNF23 Consistencia | ☑ | Estados solo `ACTIVO`/`INACTIVO`/`ELIMINADO` |
| RNF24 Errores claros | ☑ | Mensajes en español con el campo inválido |
| RNF27 Integridad | ☑ | `Idempotency-Key` obligatoria |
| RNF28 Conectividad | ☑ | La creación requiere conexión en el MVP (ver Open Questions) |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Emprendimiento | Escribe | Solo uno `ACTIVO` por usuario (índice único parcial); sin `tipo_vendedor` |
| Ubicacion | Escribe | Cada uso crea su propia fila; `Emprendimiento` ↔ `Ubicacion` 1:1 |
| Usuario | Lee (rol, estado) | Solo roles vendedor |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento |
|---|---|---|---|---|---|
| Emprendimiento | (nuevo) | `ACTIVO` | Vendedor | Sin otro `ACTIVO` | Ninguno |

**Transiciones inválidas**: crear con otro `ACTIVO` → `409 CONFLICT`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Crear emprendimiento | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | El emprendimiento queda a nombre del usuario autenticado |

## API Contracts

### POST /api/v1/emprendimientos

- **Rol**: vendedor (ambulante o de punto fijo)
- **Path params**: ninguno · **Query params**: ninguno
- **Body**:

```json
{
  "nombre": "Frutas Doña Rosa",
  "categoria": "FRUTAS_VERDURAS",
  "descripcion": "Frutas frescas del día",
  "horaApertura": "06:00",
  "horaCierre": "18:00",
  "ubicacion": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Calle 15 # 3-20", "referencia": "Frente al parque" }
}
```

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `201` con `Location: /api/v1/emprendimientos/{id}`, `X-Request-Id` y body `{ "id", "nombre", "categoria", "descripcion", "tipoVendedor", "horaApertura", "horaCierre", "estado": "ACTIVO", "ubicacion": {...} }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Campo obligatorio vacío, categoría fuera de la lista, hora con formato inválido, latitud/longitud fuera de rango | Indica el campo (`details`) |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor | "No tienes permiso" |
| 409 | `CONFLICT` | Ya existe un emprendimiento `ACTIVO` del vendedor | "Ya tienes un emprendimiento activo" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |
| 422 | `BUSINESS_RULE` | Ubicación fuera de cobertura (si el equipo la define) | Decisión pendiente |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El catálogo (`eventos.md`) no tiene eventos de emprendimiento |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la creación del emprendimiento se hace una sola vez y exige conexión; si falla, el formulario conserva lo escrito y se reintenta con la misma `Idempotency-Key`. No entra al outbox en el MVP (decisión provisional).
- **Concurrencia (RNF08)**: dos creaciones simultáneas: una tiene éxito y la otra viola el índice único parcial y recibe `409 CONFLICT`.
- **Idempotencia (RNF27)**: `idem:{userId}:{key}` en Redis; el reintento con la misma clave y cuerpo devuelve el `201` guardado con `Idempotent-Replayed: true`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Segundo emprendimiento activo | `409 CONFLICT` | US1-E2, FR-003 |
| Campos vacíos | `400 VALIDATION_ERROR` con el campo | Edge 1 |
| Ubicación inválida | `400 VALIDATION_ERROR` | Edge 2 |
| Carrera de dos creaciones | La segunda recibe `409` por el índice único | RNF08 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Reglas de `Emprendimiento` (horas, dirección según rol) | FR-002, FR-005 |
| Integración | Endpoint con PostGIS en Testcontainers; índice único; idempotencia | US1-E1, US1-E2, SC-002 |
| Arquitectura | ArchUnit del módulo `emprendimientos` | RNF13, RNF25 |
| Frontend | Jest + React Native Testing Library del formulario | Edge 1, US1-E2 |
| E2E | Registrar vendedor, crear emprendimiento, verlo en el listado (RF-31) | SC-002 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-28-creacion-de-emprendimiento/
├── RF-28-creacion-de-emprendimiento.md   # spec
└── plan.md                               # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/emprendimientos/
├── domain/
│   ├── model/                    # Emprendimiento, CategoriaEmprendimiento, EstadoEmprendimiento, HorarioAtencion
│   └── exception/                # EmprendimientoActivoExistenteException
├── application/
│   ├── ports/in/                 # CrearEmprendimientoPort
│   ├── ports/out/                # EmprendimientoRepositoryPort, UbicacionRepositoryPort
│   └── usecase/                  # CrearEmprendimientoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # EmprendimientoController, DTOs
    └── out/persistence/          # EmprendimientoJpaEntity, repositorio Spring Data, adaptador
backend/src/main/resources/db/migration/   # V4__emprendimiento.sql
backend/src/test/java/co/edu/napa/emprendimientos/

frontend/src/app/(vendedor)/emprendimiento/crear.tsx   # ruta Expo Router (formulario)
frontend/src/components/emprendimiento/                # FormularioEmprendimiento, SelectorCategoria, SelectorHorario, SelectorUbicacion
frontend/src/services/emprendimientos.ts               # cliente Axios
frontend/src/domain/emprendimiento.ts                  # tipos y enum de categorías
frontend/src/hooks/useLocation.ts                      # expo-location (permiso y posición actual)
frontend/__tests__/emprendimientos/crear.test.tsx
```

**Structure Decision**: módulo `emprendimientos`. Dominio sin Spring/JPA; el controlador solo llama a `CrearEmprendimientoPort`. `SelectorUbicacion` usa `react-native-maps` para fijar el punto y `expo-location` para proponer la posición actual.

## Phase 1: Setup

- [ ] T001 Crear el paquete `co.edu.napa.emprendimientos` y la regla ArchUnit del módulo en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`

## Phase 2: Foundational

- [ ] T002 Migración `V4__emprendimiento.sql`: tabla `emprendimiento` (id, usuario_id, ubicacion_id único, nombre, categoria, descripcion, hora_apertura `time`, hora_cierre `time`, estado, created_at, updated_at) e índice único parcial `uq_emprendimiento_activo_por_usuario` donde `estado = 'ACTIVO'` en `backend/src/main/resources/db/migration/`
- [ ] T003 Resolver quién es dueño de `UbicacionRepositoryPort` (hoy en `cuentas`) según la decisión pendiente, en `shared/application/ports/out/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Publicar un emprendimiento en la plataforma (Priority: P1)

**Goal**: el vendedor crea su emprendimiento y queda visible.
**Independent Test**: registrar un vendedor, crear el emprendimiento y verlo en el listado sin productos ni pedidos.
**Cubre**: FR-001–FR-006, SC-001–SC-002, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de creación exitosa y visibilidad inmediata en `backend/src/test/java/co/edu/napa/emprendimientos/CrearEmprendimientoIT.java`
- [ ] T011 [P] [US1] Test de validaciones (campos vacíos, categoría, horas, rango de lat/lng, dirección según rol) en `.../emprendimientos/CrearEmprendimientoValidacionIT.java`
- [ ] T012 [P] [US1] Test de segundo emprendimiento y de carrera de dos creaciones en `.../emprendimientos/EmprendimientoActivoUnicoIT.java`
- [ ] T017 [P] [US1] Test unitario de reglas de `Emprendimiento` (horario que cruza medianoche, dirección obligatoria en punto fijo) en `.../emprendimientos/EmprendimientoTest.java`
- [ ] T019 [P] [US1] Test del formulario (campos obligatorios, error 409, envío con `Idempotency-Key`) en `frontend/__tests__/emprendimientos/crear.test.tsx`

### Implementation

- [ ] T013 [US1] Dominio `Emprendimiento` y `CrearEmprendimientoUseCase` en `emprendimientos/domain/model/` y `emprendimientos/application/usecase/`
- [ ] T014 [US1] `EmprendimientoController` (`POST /emprendimientos`) en `emprendimientos/infrastructure/adapters/in/web/`
- [ ] T015 [US1] Validaciones del DTO y mapeo de errores a `VALIDATION_ERROR`
- [ ] T016 [US1] Adaptador de persistencia y traducción de la violación del índice único a `CONFLICT` en `.../out/persistence/`
- [ ] T018 [US1] Guardar `Ubicacion` con la columna `geography` derivada de latitud y longitud
- [ ] T020 [US1] Servicio `crearEmprendimiento()` en `frontend/src/services/emprendimientos.ts`
- [ ] T021 [US1] Componentes del formulario en `frontend/src/components/emprendimiento/` y `frontend/src/hooks/useLocation.ts`
- [ ] T022 [US1] Pantalla `frontend/src/app/(vendedor)/emprendimiento/crear.tsx`; al `201` navega al panel del vendedor

**Checkpoint**: US1 funcional y probable por separado.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Revisar privacidad de la ubicación y permisos (RNF03, RNF04)
- [ ] T031 Medir el tiempo de creación en el piloto (SC-001)
- [ ] T032 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-07 y RF-10; habilita RF-29, RF-30, RF-31, RF-32, RF-54 y RF-63.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Cobertura del servicio (Edge 2): ¿qué área es válida (p. ej. Santa Marta)? | Validación de ubicación | Solo validar rango de latitud/longitud | Equipo |
| Nombre repetido (Edge 3): el modelo no pide unicidad de nombre ni define "zona" | Duplicados | Permitir nombres repetidos | Equipo |
| `direccion` obligatoria para punto fijo (RF-32 la muestra) y opcional para ambulante (solo zona y referencia) | FR-005 | Así | Equipo |
| `horaApertura` igual a `horaCierre` | Horario | Rechazar (400) | Equipo |
| Límites de longitud de nombre (100) y descripción (500) | Validación | Esos valores | Equipo |
| ¿Quién es dueño de `UbicacionRepositoryPort` (hoy en `cuentas`)? | Dependencia entre módulos | Moverlo a `shared/application/ports/out` | Equipo |
| La app necesita saber si el vendedor ya tiene emprendimiento al iniciar sesión; no hay endpoint (`GET /emprendimientos/me` sería de RF-29) | Navegación | Detectarlo por el `409` al crear | Equipo |
| ¿Crear el emprendimiento va por el outbox offline? | RNF28 | No, requiere conexión | Equipo |

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
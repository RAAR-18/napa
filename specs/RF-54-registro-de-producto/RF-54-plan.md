# Implementation Plan: Registro de producto

**Date**: 2026-10-21
**Spec**: [RF-54-registro-de-producto.md](./RF-54-registro-de-producto.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-54
**Historias de usuario**: HU-85
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-28 (emprendimiento activo), RF-10
**Habilita a**: RF-55, RF-56, RF-57, RF-58, RF-33, RF-45

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor registra un producto con nombre, precio en pesos, unidad (UNIDAD, KG, LIBRA, LITRO, PORCION), descripción y cantidad disponible mayor que 0. El producto se asocia al emprendimiento `ACTIVO` del vendedor autenticado, nace `DISPONIBLE` y es visible de inmediato. El nombre es único por emprendimiento entre productos no eliminados (índice único parcial). El registro puede hacerse sin conexión: la app lo guarda en el outbox y lo envía con `Idempotency-Key` al recuperar señal.

## Estado actual del código

El módulo `productos` no existe todavía. Este plan crea la tabla `producto` y el módulo; RF-55, RF-57 y los demás RF de productos lo extienden.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene (ve el producto en RF-33) |
| Vendedor (ambulante / punto fijo) | Registra el producto |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Registrar un producto en < 1 minuto (SC-001): formulario corto de cinco campos.
- 100 % visible de inmediato (SC-002) y 100 % de rechazos de precio o cantidad inválidos (SC-003).
- Dinero como entero en pesos (`bigint`); cantidad `numeric(10,2)`.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Registrar con nombre, precio, unidad, descripción y cantidad | `POST /productos`; `RegistrarProductoUseCase`; enum `UnidadProducto` | T010, T013, T014 |
| FR-002 | Asociar al emprendimiento del vendedor autenticado | El caso de uso consulta el emprendimiento `ACTIVO` del usuario por el puerto `in` de `emprendimientos` | T012, T015 |
| FR-003 | Validar nombre, precio, unidad y cantidad; cantidad inicial > 0 | Bean Validation + reglas del dominio `Producto` | T011, T016 |
| FR-004 | Publicar de inmediato en el catálogo | `estado = DISPONIBLE` al crear; lo lee RF-33/RF-55 sin paso extra | T010 |
| FR-005 | Rechazar nombre duplicado en el mismo emprendimiento (409 CONFLICT) | Índice único parcial por `(emprendimiento_id, lower(nombre))` donde `estado <> 'ELIMINADO'` | T017, T018 |
| SC-001 | < 1 min | Formulario corto + outbox para no bloquear | T033 |
| SC-002 | 100 % visibles | Prueba de integración: crear y listar | T010 |
| SC-003 | 100 % de rechazos | Prueba parametrizada de datos inválidos | T011 |
| US1-E1 | Registro exitoso | Endpoint + persistencia | T010 |
| US1-E2 | Datos inválidos | `400 VALIDATION_ERROR` con el campo | T011 |
| Edge 1 | Registrar sin emprendimiento | `422 BUSINESS_RULE` "Primero crea tu emprendimiento" | T012 |
| Edge 2 | Nombre duplicado | Resuelto por FR-005 | T017 |
| Edge 3 | Cantidad igual a cero | Resuelto por FR-003 (rechazo) | T011 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo roles vendedor; el emprendimiento sale del JWT, no del body |
| RNF07 Rendimiento | ☑ | Inserción simple |
| RNF08 Concurrencia | ☑ | Índice único resuelve dos registros simultáneos del mismo nombre |
| RNF16 Trazabilidad | ☑ | `created_at`, `updated_at` |
| RNF23 Consistencia | ☑ | Estados `DISPONIBLE`, `AGOTADO`, `ELIMINADO` |
| RNF24 Errores claros | ☑ | Mensaje con el campo inválido |
| RNF27 Integridad | ☑ | `Idempotency-Key` obligatoria |
| RNF28 Conectividad | ☑ | Outbox SQLite en la app |
| RNF31 Gama baja | ☑ | Formulario liviano, sin fotos (el producto no tiene foto) |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Producto | Escribe | Nombre único por emprendimiento entre no `ELIMINADO`; `precio > 0`; `cantidad_disponible ≥ 0` (al crear, `> 0`) |
| Emprendimiento | Lee (puerto `in`) | Debe existir uno `ACTIVO` del vendedor |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento |
|---|---|---|---|---|---|
| Producto | (nuevo) | `DISPONIBLE` | Vendedor | Cantidad inicial > 0 | Ninguno |

**Transiciones inválidas**: no aplica (creación).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Registrar producto | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | El producto va al emprendimiento `ACTIVO` del usuario autenticado |

## API Contracts

### POST /api/v1/productos

- **Rol**: vendedor
- **Path params**: ninguno · **Query params**: ninguno
- **Body**: `{ "nombre": "Mango", "precio": 3500, "unidad": "KG", "descripcion": "Mango de azúcar", "cantidadDisponible": 12.5 }` (`descripcion` opcional)

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID generado en el dispositivo |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `201` con `Location: /api/v1/productos/{id}` y body `{ "id", "nombre", "precio", "unidad", "descripcion", "cantidadDisponible", "estado": "DISPONIBLE" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Nombre vacío, precio ≤ 0, cantidad ≤ 0 o no numérica, unidad inválida | Indica el campo (`details`) |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor | — |
| 409 | `CONFLICT` | Nombre duplicado en el emprendimiento | "Ya tienes un producto con ese nombre" |
| 422 | `BUSINESS_RULE` | El vendedor no tiene emprendimiento activo | "Primero crea tu emprendimiento" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El catálogo no tiene eventos de productos |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el formulario escribe en `outbox` (método, ruta, cuerpo, `Idempotency-Key`); la UI muestra "pendiente de envío". Al reenviar, el servidor devuelve la respuesta guardada si ya lo había procesado. Si al sincronizar responde `409` (nombre duplicado) o `422`, la app muestra el error y deja el registro editable.
- **Concurrencia (RNF08)**: índice único parcial; dos registros simultáneos del mismo nombre → uno `201`, otro `409`.
- **Idempotencia (RNF27)**: `idem:{userId}:{key}` + índice único como respaldo.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Precio negativo o cantidad ≤ 0 / no numérica | `400` con el campo | US1-E2, FR-003 |
| Nombre duplicado | `409 CONFLICT` | FR-005 |
| Sin emprendimiento | `422 BUSINESS_RULE` | Edge 1 |
| Reenvío tras corte | Respuesta guardada con `Idempotent-Replayed: true` | RNF27 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Reglas de `Producto` (precio, cantidad, nombre normalizado) | FR-003 |
| Integración | Endpoint con Testcontainers; índice único; idempotencia | US1-E1, US1-E2, FR-005 |
| Arquitectura | ArchUnit del módulo `productos` | RNF13, RNF25 |
| Frontend | Jest del formulario y del encolado en outbox sin red | RNF28 |
| E2E | Crear emprendimiento, registrar producto y verlo en el listado | SC-002 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-54-registro-de-producto/
├── RF-54-registro-de-producto.md   # spec
└── plan.md                         # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/productos/
├── domain/
│   ├── model/                    # Producto, UnidadProducto, EstadoProducto
│   └── exception/                # ProductoDuplicadoException, SinEmprendimientoActivoException
├── application/
│   ├── ports/in/                 # RegistrarProductoPort
│   ├── ports/out/                # ProductoRepositoryPort, EmprendimientoActivoPort
│   └── usecase/                  # RegistrarProductoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # ProductoController, DTOs
    └── out/persistence/          # ProductoJpaEntity, repositorio, adaptador de EmprendimientoActivoPort
backend/src/main/resources/db/migration/   # V5__producto.sql
backend/src/test/java/co/edu/napa/productos/

frontend/src/app/(vendedor)/productos/nuevo.tsx       # ruta Expo Router
frontend/src/components/productos/FormularioProducto.tsx
frontend/src/services/productos.ts                    # cliente; escribe en el outbox
frontend/src/domain/producto.ts
frontend/src/database/outbox/                         # ya definido en foundations F041
frontend/__tests__/productos/registro.test.tsx
```

**Structure Decision**: módulo `productos`. Para el emprendimiento activo del vendedor, `productos` usa el puerto `in` de `emprendimientos` a través de su propio puerto `out` (`EmprendimientoActivoPort`), como exige ArchUnit.

## Phase 1: Setup

- [ ] T001 Crear el paquete `co.edu.napa.productos` y su regla ArchUnit en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`

## Phase 2: Foundational

- [ ] T002 Migración `V5__producto.sql`: tabla `producto` (id, emprendimiento_id, nombre, descripcion, unidad, precio `bigint`, cantidad_disponible `numeric(10,2)`, estado, created_at, updated_at), CHECK `precio > 0` y `cantidad_disponible >= 0`, índice único parcial `(emprendimiento_id, lower(nombre))` donde `estado <> 'ELIMINADO'`, e índice `(emprendimiento_id, estado)` en `backend/src/main/resources/db/migration/`
- [ ] T003 Puerto `in` de `emprendimientos` para consultar el emprendimiento `ACTIVO` de un usuario en `emprendimientos/application/ports/in/ConsultarEmprendimientoActivoPort.java`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Añadir un producto al catálogo (Priority: P1)

**Goal**: el vendedor registra un producto visible de inmediato.
**Independent Test**: crear un emprendimiento, registrar un producto válido y verlo en el catálogo.
**Cubre**: FR-001–FR-005, SC-001–SC-003, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de registro exitoso y visibilidad en `backend/src/test/java/co/edu/napa/productos/RegistrarProductoIT.java`
- [ ] T011 [P] [US1] Test parametrizado de datos inválidos (precio ≤ 0, cantidad ≤ 0 o no numérica, unidad, nombre vacío) en `.../productos/RegistrarProductoValidacionIT.java`
- [ ] T012 [P] [US1] Test de vendedor sin emprendimiento activo en `.../productos/RegistrarProductoSinEmprendimientoIT.java`
- [ ] T017 [P] [US1] Test de nombre duplicado, con mayúsculas/espacios distintos, y de carrera de dos registros en `.../productos/ProductoNombreUnicoIT.java`
- [ ] T019 [P] [US1] Test del formulario y del encolado en outbox sin red en `frontend/__tests__/productos/registro.test.tsx`

### Implementation

- [ ] T013 [US1] Dominio `Producto` y `RegistrarProductoUseCase` en `productos/domain/model/` y `productos/application/usecase/`
- [ ] T014 [US1] `ProductoController` (`POST /productos`) en `productos/infrastructure/adapters/in/web/`
- [ ] T015 [US1] Adaptador `EmprendimientoActivoPort` que llama al puerto `in` de `emprendimientos`
- [ ] T016 [US1] Validaciones del DTO y mapeo de errores
- [ ] T018 [US1] Adaptador de persistencia y traducción de la violación del índice único a `CONFLICT`
- [ ] T020 [US1] Servicio `registrarProducto()` que escribe en el outbox en `frontend/src/services/productos.ts`
- [ ] T021 [US1] `FormularioProducto` (nombre, precio, unidad, descripción, cantidad) en `frontend/src/components/productos/`
- [ ] T022 [US1] Pantalla `frontend/src/app/(vendedor)/productos/nuevo.tsx` con estado "pendiente de envío" y manejo de errores al sincronizar

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Revisar permisos (RNF03)
- [ ] T033 Medir el tiempo de registro (SC-001) en el piloto
- [ ] T034 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-28; RF-55, RF-56, RF-57, RF-58, RF-33 y RF-45 dependen de la tabla `producto` creada aquí.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Unicidad del nombre sin distinguir mayúsculas ni espacios sobrantes? | FR-005 | Sí (`lower(trim(nombre))`) | Equipo |
| ¿`UNIDAD` y `PORCION` solo admiten cantidades enteras (no 2,5 unidades)? | FR-003, RF-57 | Sí: enteros para `UNIDAD` y `PORCION`; decimales para `KG`, `LIBRA`, `LITRO` | Equipo |
| Límites de longitud (nombre 100, descripción 500) y tope de precio | Validación | Esos valores | Equipo |
| Sin emprendimiento: `422 BUSINESS_RULE` | Edge 1 | Así | Equipo |
| Conflictos al sincronizar un registro hecho sin conexión | RNF28 | La app muestra el error y permite corregir | Equipo |

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
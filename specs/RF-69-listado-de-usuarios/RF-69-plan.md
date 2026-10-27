# Implementation Plan: Listado de usuarios

**Date**: 2026-10-27
**Spec**: [RF-69-listado-de-usuarios.md](./RF-69-listado-de-usuarios.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-69
**Historias de usuario**: HU-102
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-07, RF-10
**Habilita a**: RF-66 (detalle desde el listado)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador lista las cuentas registradas con `GET /usuarios`, paginado, con nombre, rol y estado de cada una. Se puede filtrar por rol y por estado, y buscar por texto (`q`, sobre nombre, celular y correo). Por defecto incluye todos los estados, también las cuentas eliminadas, para tener la visión completa; el filtro `estado` las separa. El orden por defecto es el más reciente primero. Cada fila lleva al detalle de RF-66. La respuesta no incluye datos que no hacen falta para reconocer a la persona (no trae correo ni ubicación).

## Estado actual del código

No existe aún ninguna consulta de usuarios para el administrador. spec-general ya lista `GET /usuarios` con los filtros `rol` y `estado` para RF-69, y los listados de otros RF (RF-61, RF-15, RF-47) siguen la misma convención de paginación y de filtro desconocido. El modelo `Usuario` ya tiene `rol`, `estado` y `created_at`.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador, Vendedor, Domiciliario | Son los usuarios listados; no intervienen |
| Administrador | Lista, filtra y busca usuarios |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Listado en < 3 s (SC-001): consulta paginada con índice `(rol, estado, created_at desc)`.
- Tiempos aceptables con crecimiento (SC-002): paginación obligatoria (`size` máximo 100) y búsqueda con índice `pg_trgm` sobre el nombre y búsqueda por prefijo sobre celular y correo; prueba de rendimiento con 50 000 usuarios.
- Sin consulta de conteo costoso innecesaria: se usa `totalElements` de la paginación estándar.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Consultar el listado completo de usuarios | `GET /usuarios` paginado | T010, T020, T021 |
| FR-002 | Mostrar el rol y el estado de cada usuario | Campos `rol` y `estado` en cada fila | T010, T022 |
| FR-003 | Filtrar el listado por rol | Parámetro `rol` (y `estado`, `q`) | T011, T012, T023 |
| SC-001 | Listado en < 3 s | Índice y paginación | T014 |
| SC-002 | Tiempos aceptables con crecimiento | Prueba con 50 000 usuarios y `EXPLAIN` | T014, T040 |
| US1-E1 | Listado general con nombre, rol y estado | `200` con `content` paginado | T010 |
| US1-E2 | Filtro por rol | Solo los usuarios del rol | T011 |
| Edge 1 | Paginación con miles de usuarios | `page`, `size` (máximo 100) y `sort` | T013 |
| Edge 2 | ¿Incluir las cuentas eliminadas? | Se incluyen por defecto; el filtro `estado` las separa (Open Questions) | T012 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR |
| RNF04 Datos personales | ☑ | La fila trae el mínimo; `no-store` en la respuesta |
| RNF07 Rendimiento ≤ 3 s | ☑ | Índices y paginación |
| RNF24 Errores claros | ☑ | `400` ante un filtro o valor desconocido |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Usuario | Lee | Todos los estados; nunca `password_hash` |

## State Transitions

Esta feature es de solo lectura: no cambia estados.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /usuarios` | ADMINISTRADOR | Todos los usuarios, incluidos los administradores |

## API Contracts

### GET /api/v1/usuarios

- **Rol**: administrador · **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| rol | enum | ☐ | `VENDEDOR_PUNTO_FIJO` | `CLIENTE`, `VENDEDOR_AMBULANTE`, `VENDEDOR_PUNTO_FIJO`, `DOMICILIARIO`, `ADMINISTRADOR`; también `VENDEDOR` (los dos tipos de vendedor) |
| estado | enum | ☐ | `ACTIVA` | `PENDIENTE_VERIFICACION`, `ACTIVA`, `ELIMINADA`; sin valor trae todos |
| q | string | ☐ | `ana` | Texto de al menos 2 caracteres: coincide con el nombre, o con el inicio del celular o del correo |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `createdAt,desc` | `sort` admite `nombre` y `createdAt`; por defecto `createdAt,desc` |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "content": [
    { "id": "…", "nombre": "Ana Pérez", "rol": "CLIENTE", "estado": "ACTIVA", "createdAt": "2026-09-01T15:00:00Z" }
  ],
  "page": 0, "size": 20, "totalElements": 1
}
```

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido, valor de `rol` o `estado` inválido, `q` de menos de 2 caracteres, `size` mayor que 100, `sort` inválido | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de administrador | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el panel del administrador exige conexión y no guarda en SQLite datos de terceros; sin red muestra el error y un botón de reintento.
- **Concurrencia (RNF08)**: no aplica a la lectura. Si se crea o elimina una cuenta mientras se pagina, puede repetirse o faltar una fila entre páginas; la app refresca al volver al primer plano.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Sin usuarios que cumplan | `200` con lista vacía | US1-E2 |
| Filtro o valor inválido | `400` con el parámetro | RNF24 |
| Búsqueda de menos de 2 caracteres | `400` | Rendimiento |
| Rol distinto de administrador | `403` | RNF03 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: listado general, filtro por rol (incluido el alias `VENDEDOR`), por estado, `q`, combinaciones, lista vacía, paginación y orden, cuentas eliminadas incluidas por defecto | FR-001–FR-003, US1-E1–E2, Edge 1–2 |
| Integración | Filtros y valores inválidos, `q` corta, `size` > 100, rol distinto, sin sesión | RNF24 |
| Contrato | Cada fila trae solo `id`, `nombre`, `rol`, `estado` y `createdAt` | RNF04 |
| Rendimiento | 50 000 usuarios: primera página, filtro por rol y búsqueda en < 3 s | SC-001, SC-002 |
| Frontend | Jest: lista con filtros y búsqueda, desplazamiento infinito, estado vacío, error con reintento | US1-E1–E2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-69-listado-de-usuarios/
├── RF-69-listado-de-usuarios.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/cuentas/
├── application/
│   ├── ports/in/                 # ListarUsuariosPort
│   ├── ports/out/                # UsuarioRepositoryPort (listado con filtros)
│   └── usecase/                  # ListarUsuariosUseCase
└── infrastructure/adapters/
    ├── in/web/                   # UsuarioAdminController (GET /usuarios), UsuarioResumenResponse
    └── out/persistence/          # consulta paginada con filtros y búsqueda

backend/src/main/resources/db/migration/   # siguiente migración libre: índices del listado y pg_trgm
backend/src/test/java/co/edu/napa/cuentas/

frontend/src/app/(administrador)/usuarios/index.tsx          # listado con filtros y búsqueda
frontend/src/components/usuarios/FilaUsuario.tsx
frontend/src/components/usuarios/FiltrosUsuarios.tsx         # rol, estado y búsqueda
frontend/src/services/usuariosAdmin.ts                       # listarUsuarios()
frontend/__tests__/usuarios/listado-usuarios.test.tsx
```

**Structure Decision**: módulo `cuentas`, dueño de `Usuario`, en el mismo controlador de administrador que RF-66. El filtro `rol` acepta el alias `VENDEDOR` porque la spec filtra por "vendedor" y el modelo tiene dos roles de vendedor. La búsqueda `q` se agrega porque RF-66 pide "buscar" y a miles de cuentas no se llega a un usuario solo paginando.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `cuentas` y su regla ArchUnit

## Phase 2: Foundational

- [ ] T002 Migración en la siguiente migración libre de `backend/src/main/resources/db/migration/`: índice `(rol, estado, created_at DESC)` sobre `usuario`, extensión `pg_trgm` e índice GIN sobre `lower(nombre)`, e índices por prefijo sobre `celular` y `correo`
- [ ] T003 Método de listado con filtros, búsqueda y paginación en `UsuarioRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Ver las cuentas registradas (Priority: P2)

**Goal**: el administrador tiene una visión general y puede acotarla.
**Independent Test**: abrir el listado, filtrar por rol y buscar por nombre.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /usuarios` (nombre, rol y estado por fila, todos los estados) en `backend/src/test/java/co/edu/napa/cuentas/ListarUsuariosIT.java`
- [ ] T011 [P] [US1] Test de filtro por rol (los cinco valores y el alias `VENDEDOR`) en `.../cuentas/ListarUsuariosRolIT.java`
- [ ] T012 [P] [US1] Test de filtro por estado, de que las eliminadas aparecen por defecto y de `q` (nombre, prefijo de celular y de correo) en `.../cuentas/ListarUsuariosEstadoBusquedaIT.java`
- [ ] T013 [P] [US1] Test de paginación y orden (`nombre`, `createdAt`), `size` > 100, filtro desconocido, valores inválidos y `q` corta en `.../cuentas/ListarUsuariosPaginacionIT.java`
- [ ] T014 [P] [US1] Test de rendimiento con 50 000 usuarios en `.../cuentas/ListarUsuariosRendimientoIT.java`
- [ ] T015 [P] [US1] Test de contrato de la fila (sin correo, celular ni ubicación) y de rol distinto y sin sesión en `.../cuentas/ListarUsuariosAutorizacionIT.java`
- [ ] T030 [P] [US1] Test de la pantalla de listado (filtros, búsqueda, desplazamiento, vacío, error con reintento) en `frontend/__tests__/usuarios/listado-usuarios.test.tsx`

### Implementation

- [ ] T020 [US1] `ListarUsuariosPort` y `ListarUsuariosUseCase`: valida filtros y arma la consulta
- [ ] T021 [US1] `GET /usuarios` en `UsuarioAdminController`
- [ ] T022 [US1] `UsuarioResumenResponse` con `id`, `nombre`, `rol`, `estado` y `createdAt`
- [ ] T023 [US1] Consulta paginada con filtro por rol (con el alias `VENDEDOR`), estado y búsqueda `q`
- [ ] T031 [US1] `listarUsuarios()` con paginación en `frontend/src/services/usuariosAdmin.ts`
- [ ] T032 [US1] `FiltrosUsuarios`, `FilaUsuario` y pantalla `(administrador)/usuarios/index.tsx`, con enlace al detalle de RF-66

**Checkpoint**: el administrador lista, filtra y busca usuarios.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` del listado y de la búsqueda con datos de piloto
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-07 (cuentas) y RF-10 (rol administrador); lleva al detalle de RF-66.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 2: ¿se incluyen las cuentas eliminadas? | Regla | Sí por defecto, para la visión completa; `estado=ACTIVA` las excluye | Equipo |
| `q` no está en spec-general (solo `rol` y `estado`) | Contrato | Se agrega `q` para que RF-66 pueda "buscar". **Confirmar** | Equipo |
| La spec filtra por "cliente, vendedor, domiciliario" y el modelo tiene dos roles de vendedor | Contrato | `rol` acepta los cinco valores del modelo y el alias `VENDEDOR` | Equipo |
| ¿Se listan los administradores? | Alcance | Sí, con `rol=ADMINISTRADOR` o sin filtro | Equipo |
| La fila solo trae `nombre`, `rol`, `estado`, `id` y `createdAt` | Privacidad | Lo que pide la spec; el resto va en RF-66 | Equipo |
| Rango de fechas de registro | Alcance | No está en spec-general ni en la spec | Equipo |
| Se agrega `pg_trgm` a la base | Infraestructura | Disponible en PostgreSQL; si no se quiere, se busca solo por prefijo | Equipo |

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
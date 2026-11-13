# Implementation Plan: Consulta de usuario por administrador

**Date**: 2026-10-27
**Spec**: [RF-66-consulta-de-usuario-por-administrador.md](./RF-66-consulta-de-usuario-por-administrador.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-66
**Historias de usuario**: HU-99
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-69, RF-08, RF-10
**Habilita a**: RF-67 y RF-68 (parten de este detalle)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador consulta un usuario con `GET /usuarios/{id}`, al que llega desde el listado de RF-69. La respuesta trae su información personal (nombre, celular, correo, foto, ubicación registrada), su rol, el estado de su cuenta (`PENDIENTE_VERIFICACION`, `ACTIVA` o `ELIMINADA`) y su fecha de registro. Una cuenta eliminada también se puede consultar y se ve como `ELIMINADA`. Nunca se devuelve la contraseña ni sus derivados. El historial de actividad (pedidos, domicilios, reportes) no se incluye en esta feature.

## Estado actual del código

El modelo `Usuario` (spec-general) ya tiene todos los campos que pide la spec. RF-08 crea `GET /usuarios/me` con el mismo DTO de perfil, y RF-69 entrega el listado desde el que se llega aquí. No existe aún ninguna ruta de consulta de usuarios para el administrador.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador, Vendedor, Domiciliario | Son los usuarios consultados; no interviene |
| Administrador | Consulta la información de un usuario |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Localizar y consultar en < 10 s (SC-001): el listado de RF-69 con filtros y búsqueda más una lectura por id.
- Rol y estado siempre actuales (SC-002): se leen de la fila en el momento, sin caché.
- Datos personales: la respuesta lleva `Cache-Control: no-store` y la consulta queda en el log con el administrador y el id consultado (sin el contenido).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Buscar y consultar la información de un usuario | La búsqueda es el filtro `q` de RF-69 (Open Questions) y la consulta es `GET /usuarios/{id}` | T010, T020, T021 |
| FR-002 | Mostrar el rol y el estado de la cuenta | Campos `rol` y `estado` leídos de la fila | T011, T022 |
| SC-001 | < 10 s para localizar y consultar | Búsqueda indexada y lectura por id | T014 |
| SC-002 | 100 % con rol y estado actuales | Sin caché | T011 |
| US1-E1 | Consulta exitosa | `200` con información personal, rol y estado | T010 |
| Edge 1 | Usuario eliminado | Se devuelve con estado `ELIMINADA`; no es `404` | T012 |
| Edge 2 | Historial de actividad del usuario | No se incluye en esta feature (Open Questions) | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR |
| RNF04 Datos personales | ☑ | `no-store`, sin contraseña y consulta registrada en el log |
| RNF07 Rendimiento ≤ 3 s | ☑ | Lectura por clave primaria |
| RNF24 Errores claros | ☑ | `404` para un id inexistente, `400` para un id mal formado |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Usuario | Lee | Todos los estados; nunca `password_hash` |
| Ubicacion | Lee | La del perfil del usuario (0..1) |

## State Transitions

Esta feature es de solo lectura: no cambia estados.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /usuarios/{id}` | ADMINISTRADOR | Cualquier usuario, incluidos los eliminados y otros administradores |

## API Contracts

### GET /api/v1/usuarios/{id}

- **Rol**: administrador · **Path params**: `id` (UUID del usuario) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "id": "…", "nombre": "Ana Pérez", "celular": "+573001234567", "correo": null,
  "fotoUrl": null, "rol": "CLIENTE", "estado": "ACTIVA",
  "celularVerificado": true, "createdAt": "2026-09-01T15:00:00Z",
  "ubicacion": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" }
}
```

`correo`, `fotoUrl`, `celular` (el administrador puede no tenerlo) y `ubicacion` son `null` cuando no existen. El cuerpo coincide con el de `GET /usuarios/me` (RF-08) y no incluye contraseña, `session_version` ni tokens.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | El `id` no es un UUID | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de administrador | — |
| 404 | `NOT_FOUND` | El usuario no existe | "El usuario no existe" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el panel del administrador exige conexión; no guarda datos de usuarios en SQLite (datos personales de terceros).
- **Concurrencia (RNF08)**: no aplica a la lectura.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Usuario eliminado | `200` con `estado: ELIMINADA` | Edge 1 |
| Id inexistente | `404` | FR-001 |
| Id mal formado | `400` | RNF24 |
| Rol distinto de administrador | `403` | RNF03 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: un usuario de cada rol, cuenta `PENDIENTE_VERIFICACION` y `ELIMINADA`, administrador consultado, inexistente, id inválido, rol distinto, sin sesión | FR-001, FR-002, US1-E1, Edge 1 |
| Contrato | La respuesta no contiene contraseña ni campos internos y coincide con el perfil de RF-08 | RNF04 |
| Integración | El estado se actualiza apenas cambia (por ejemplo, tras RF-11) | SC-002 |
| Frontend | Jest: pantalla de detalle con campos nulos, estado `ELIMINADA` marcado y error `404` | US1-E1, Edge 1 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-66-consulta-de-usuario-por-administrador/
├── RF-66-consulta-de-usuario-por-administrador.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/cuentas/
├── application/
│   ├── ports/in/                 # ConsultarUsuarioAdminPort
│   ├── ports/out/                # UsuarioRepositoryPort, UbicacionRepositoryPort
│   └── usecase/                  # ConsultarUsuarioAdminUseCase
└── infrastructure/adapters/
    └── in/web/                   # UsuarioAdminController (GET /usuarios/{id}), reutiliza el DTO de perfil de RF-08

backend/src/test/java/co/edu/napa/cuentas/

frontend/src/app/(administrador)/usuarios/[id].tsx          # detalle del usuario
frontend/src/components/usuarios/DetalleUsuario.tsx         # campos, rol y estado con distintivo
frontend/src/services/usuariosAdmin.ts                      # obtenerUsuario()
frontend/__tests__/usuarios/detalle-usuario.test.tsx
```

**Structure Decision**: módulo `cuentas`, dueño de `Usuario`. La ruta `GET /usuarios/{id}` convive con `GET /usuarios/me` de RF-08 (la ruta literal `me` tiene prioridad). Reutiliza el DTO de perfil para que ambos muestren lo mismo. La búsqueda no es una ruta aparte: es el filtro `q` del listado de RF-69.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `cuentas` y su regla ArchUnit

## Phase 2: Foundational

- [ ] T002 Reutilizar el DTO de perfil de RF-08 como `UsuarioDetalleResponse`, sin `password_hash` ni datos internos
- [ ] T003 Registro en el log de la consulta (administrador e id consultado) sin datos personales

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Revisar la información de un usuario (Priority: P2)

**Goal**: el administrador ve quién es un usuario, qué rol tiene y en qué estado está su cuenta.
**Independent Test**: abrir un usuario desde el listado y ver sus datos, rol y estado.
**Cubre**: FR-001, FR-002, SC-001, SC-002, US1-E1, Edge 1.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /usuarios/{id}` para un usuario de cada rol en `backend/src/test/java/co/edu/napa/cuentas/ConsultarUsuarioAdminIT.java`
- [ ] T011 [P] [US1] Test de que el rol y el estado reflejan los cambios al instante y de que el cuerpo no contiene contraseña ni campos internos, en `.../cuentas/ConsultarUsuarioAdminContratoIT.java`
- [ ] T012 [P] [US1] Test de usuario `ELIMINADA` y `PENDIENTE_VERIFICACION` (se devuelven) en `.../cuentas/ConsultarUsuarioAdminEstadosIT.java`
- [ ] T013 [P] [US1] Test de id inexistente (`404`), mal formado (`400`), rol distinto (`403`) y sin sesión (`401`) en `.../cuentas/ConsultarUsuarioAdminAutorizacionIT.java`
- [ ] T014 [P] [US1] Test de rendimiento de la lectura (< 3 s) en `.../cuentas/ConsultarUsuarioAdminRendimientoIT.java`
- [ ] T030 [P] [US1] Test de la pantalla de detalle (campos nulos, distintivo `ELIMINADA`, error `404`) en `frontend/__tests__/usuarios/detalle-usuario.test.tsx`

### Implementation

- [ ] T020 [US1] `ConsultarUsuarioAdminPort` y `ConsultarUsuarioAdminUseCase`
- [ ] T021 [US1] `GET /usuarios/{id}` en `UsuarioAdminController` con la autorización de administrador
- [ ] T022 [US1] Mapeo al DTO de detalle con rol, estado y fecha de registro
- [ ] T031 [US1] `obtenerUsuario()` en `frontend/src/services/usuariosAdmin.ts`
- [ ] T032 [US1] `DetalleUsuario` y pantalla `(administrador)/usuarios/[id].tsx`, enlazada desde el listado de RF-69

**Checkpoint**: el administrador consulta cualquier usuario.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Confirmar que el log no guarda datos personales
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-69 (listado y búsqueda), RF-08 (DTO de perfil) y RF-10 (rol administrador).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| FR-001 dice "buscar" pero la spec no define cómo; spec-general solo da `rol` y `estado` para `GET /usuarios` | Contrato | Se agrega el filtro `q` (nombre, celular o correo) a RF-69; la búsqueda no es una ruta aparte. **Confirmar** | Equipo |
| Edge 2: ¿se muestra el historial de actividad (pedidos, domicilios, reportes)? | Alcance | No: la spec lo pregunta y FR-001/FR-002 no lo piden. RF-12 y RF-61 ya dan esas vistas | Equipo |
| El administrador puede ver a otros administradores | Alcance | Sí | Equipo |
| La ruta no está en spec-general (`GET /usuarios/{id}`) | Contrato | Se agrega a spec-general | Equipo |
| El administrador ve el celular y el correo completos | Privacidad | Sí; es soporte. Queda el log de consulta | Equipo |

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
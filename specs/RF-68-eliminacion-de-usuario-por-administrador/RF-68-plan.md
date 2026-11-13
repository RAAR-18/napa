# Implementation Plan: Eliminación de usuario por administrador

**Date**: 2026-10-27
**Spec**: [RF-68-eliminacion-de-usuario-por-administrador.md](./RF-68-eliminacion-de-usuario-por-administrador.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-68
**Historias de usuario**: HU-101
**Prioridad (la mayor de la spec)**: P3
**Depende de**: RF-11, RF-66, RF-30, RF-34, RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador elimina la cuenta de un usuario con `POST /usuarios/{id}/eliminar`. Si el usuario tiene actividad en curso (pedidos pendientes, domicilios activos, tarifas sin pagar o, si es vendedor, un emprendimiento activo), la primera llamada responde `409 HAS_PENDING_OPERATIONS` con el detalle, que es la advertencia; el administrador decide y repite la llamada con `{ "confirmar": true, "forzar": true }`. Sin actividad basta `{ "confirmar": true }`. La cuenta pasa a `ELIMINADA` (borrado lógico), se cierran todas sus sesiones y deja de poder iniciar sesión. Si era un vendedor, su emprendimiento activo se elimina con él para que deje de verse. Las cuentas de administrador y la propia no se eliminan por esta ruta.

## Estado actual del código

RF-11 ya define la eliminación de la propia cuenta: `OperacionesPendientesPort`, la revocación de sesiones con `sv` y el código `HAS_PENDING_OPERATIONS`; RF-10 ya rechaza el ingreso de una cuenta `ELIMINADA` y RF-38 no le envía push. RF-66 es el detalle desde el que se llega. Este plan comparte con RF-11 el método de dominio y los puertos, y agrega el modo forzado, la restricción sobre administradores y la eliminación del emprendimiento del vendedor, que RF-30 solo hace a petición del propio vendedor.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador, Vendedor, Domiciliario | Es el usuario eliminado; pierde su acceso |
| Administrador | Elimina la cuenta, tras ver la advertencia si hay actividad |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Sin acceso inmediato tras eliminar (SC-001): se revocan los refresh tokens y se incrementa `sv:{userId}`, así que el access token vigente deja de servir.
- 100 % de advertencias con actividad en curso (SC-002): el servidor rechaza la eliminación con actividad si no llega `forzar: true`; la app solo envía ese indicador después de mostrar el detalle.
- Todo en una transacción: estado de la cuenta, sesiones y emprendimiento.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Eliminar la cuenta de un usuario | `POST /usuarios/{id}/eliminar` | T010, T020, T021 |
| FR-002 | Advertir si hay pedidos o domicilios en curso | `409 HAS_PENDING_OPERATIONS` con conteos; solo procede con `forzar: true` | T011, T012, T022 |
| FR-003 | Impedir el inicio de sesión | Estado `ELIMINADA`, revocación de sesiones y `sv` | T013, T023 |
| FR-004 | Solicitar confirmación explícita | Cuadro de confirmación en la app y `confirmar: true` exigido por el servidor | T014, T030 |
| SC-001 | Sin acceso de inmediato | Sesiones revocadas | T013 |
| SC-002 | 100 % de advertencias con actividad en curso | Prueba por cada tipo de actividad | T011, T012 |
| US1-E1 | Eliminación exitosa | `204` y la cuenta no inicia sesión | T010, T013 |
| US1-E2 | Usuario con actividad activa | Advertencia (`409` con detalle) antes de confirmar | T011, T012 |
| Edge 1 | Emprendimientos, productos e historial del usuario eliminado | El emprendimiento activo se elimina con él y el resto se conserva; los pedidos pendientes quedan como están (Open Questions) | T015, T024 |
| Edge 2 | Período de gracia | Ninguno: eliminación inmediata y sin reactivación, igual que RF-11 | T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR |
| RNF04 Datos personales | ☑ | Se conserva el registro lógico; no se exponen datos tras eliminar |
| RNF07 Rendimiento ≤ 3 s | ☑ | Consultas indexadas y una actualización |
| RNF13 Arquitectura | ☑ | Pendientes y emprendimiento por puertos `in` |
| RNF15 Fiabilidad | ☑ | Cuenta, sesiones y emprendimiento en una transacción |
| RNF23 Consistencia | ☑ | `ACTIVA` o `PENDIENTE_VERIFICACION` → `ELIMINADA`; repetir da `INVALID_STATE` |
| RNF27 Idempotencia | ☑ | `Idempotency-Key` obligatoria |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Usuario | Lee / escribe (`estado`) | Pasa a `ELIMINADA`; no se borra; `updated_at` es la fecha |
| Sesion | Escribe (`revoked_at`) | Todas las del usuario |
| Emprendimiento | Escribe (puerto) | El `ACTIVO` del vendedor pasa a `ELIMINADO` |
| Pedido, Domicilio, Pago | Lee (puertos) | Para la advertencia; no se modifican |

## State Transitions

| Entidad | Desde | Hacia | Acción | Condición |
|---|---|---|---|---|
| Usuario | `ACTIVA` o `PENDIENTE_VERIFICACION` | `ELIMINADA` | `POST /usuarios/{id}/eliminar` | Confirmación; con actividad en curso, además `forzar` |
| Emprendimiento (del vendedor) | `ACTIVO` | `ELIMINADO` | La misma acción | En la misma transacción |

`ELIMINADA` es terminal.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /usuarios/{id}/eliminar` | ADMINISTRADOR | Cualquier usuario que no sea administrador ni él mismo |

## API Contracts

### POST /api/v1/usuarios/{id}/eliminar

- **Rol**: administrador · **Path params**: `id` (UUID del usuario) · **Query params**: ninguno
- **Body**: `{ "confirmar": true, "forzar": false }` (`forzar` es opcional; solo con `true` se elimina un usuario con actividad en curso)

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `204` sin cuerpo y con `X-Request-Id`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `confirmar` o no es `true` | "Confirma la eliminación" |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de administrador | — |
| 404 | `NOT_FOUND` | El usuario no existe | "El usuario no existe" |
| 409 | `HAS_PENDING_OPERATIONS` | Hay actividad en curso y no llegó `forzar: true`: es la advertencia | `details`: `{ "pedidosPendientes", "domiciliosActivos", "tarifasPendientes", "emprendimientoActivo" }` (solo los distintos de cero) |
| 409 | `INVALID_STATE` | La cuenta ya está `ELIMINADA` | `details.estadoActual` |
| 422 | `BUSINESS_RULE` | El usuario es administrador o es el mismo que lo elimina | "Esta cuenta no se puede eliminar aquí" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | RF-38 ya omite los push a cuentas `ELIMINADA` |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: es una acción administrativa y destructiva: no se encola, la app exige conexión.
- **Concurrencia (RNF08)**: bloqueo `FOR UPDATE` del usuario antes de contar la actividad. Un pedido que llegue entre la advertencia y la confirmación forzada se incluye en el conteo de la segunda llamada solo si no se forzó; con `forzar` se elimina igual.
- **Idempotencia (RNF27)**: la misma clave devuelve el mismo `204`; con otra clave y la cuenta ya eliminada llega `409 INVALID_STATE`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Usuario con actividad en curso | `409` con el detalle; se repite con `forzar: true` | US1-E2 |
| Sin `confirmar: true` | `400` | FR-004 |
| Administrador o uno mismo | `422` | Seguridad |
| Cuenta ya eliminada | `409 INVALID_STATE` | RNF23 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Eliminación sin actividad (`204`, estado `ELIMINADA`, repetición con la misma clave) | FR-001, US1-E1 |
| Integración | Advertencia por pedido pendiente, domicilio activo, tarifa sin pagar y emprendimiento activo; con `forzar` procede | FR-002, SC-002, US1-E2 |
| Integración | Después de eliminar: login, access token y refresh fallan | FR-003, SC-001 |
| Integración | Vendedor: su emprendimiento activo pasa a `ELIMINADO` y deja de verse en RF-31, RF-32, RF-33 y RF-34; historial y calificaciones se conservan | Edge 1 |
| Integración | Sin `confirmar`, administrador, uno mismo, inexistente, ya eliminada, rol distinto, sin sesión; cuenta `PENDIENTE_VERIFICACION` | FR-004, Edge 2 |
| Arquitectura | `cuentas` usa solo puertos `in` de otros módulos | RNF13 |
| Frontend | Jest: advertencia con el detalle, segunda confirmación, limpieza de la lista | FR-002, FR-004 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-68-eliminacion-de-usuario-por-administrador/
├── RF-68-eliminacion-de-usuario-por-administrador.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/cuentas/
├── domain/model/                 # Usuario.eliminar() (compartido con RF-11)
├── application/
│   ├── ports/in/                 # EliminarUsuarioAdminPort
│   ├── ports/out/                # UsuarioRepositoryPort, SesionRepositoryPort, OperacionesPendientesPort (RF-11), EliminarEmprendimientoDeVendedorPort
│   └── usecase/                  # EliminarUsuarioAdminUseCase
└── infrastructure/adapters/
    └── in/web/                   # UsuarioAdminController (POST /usuarios/{id}/eliminar)

backend/src/main/java/co/edu/napa/emprendimientos/application/ports/in/   # EliminarEmprendimientoDeVendedorPort (sin comprobar pedidos: ya lo advirtió el administrador)
backend/src/test/java/co/edu/napa/cuentas/

frontend/src/components/usuarios/EliminarUsuarioSheet.tsx          # advertencia con el detalle y confirmación
frontend/src/services/usuariosAdmin.ts                             # eliminarUsuario()
frontend/src/app/(administrador)/usuarios/[id].tsx                 # (RF-66) incluye la acción
frontend/__tests__/usuarios/eliminar-usuario.test.tsx
```

**Structure Decision**: módulo `cuentas`, con la misma regla de dominio de RF-11 y dos casos de uso (el del propio usuario y el del administrador), sin una capa de servicio compartida. La advertencia de la spec se hace cumplir en el servidor con `forzar`, no solo en la pantalla. Eliminar también el emprendimiento de un vendedor evita que quede visible un negocio sin dueño.

## Phase 1: Setup

- [ ] T001 Confirmar `Usuario.eliminar()`, `OperacionesPendientesPort` y `revocarTodas` de RF-11 (T005, T006)

## Phase 2: Foundational

- [ ] T002 `EliminarEmprendimientoDeVendedorPort` en `emprendimientos` (pasa el `ACTIVO` del vendedor a `ELIMINADO`, sin comprobar pedidos)
- [ ] T003 Registro en el log de cada eliminación (administrador, usuario y si fue forzada)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Retirar una cuenta de la plataforma (Priority: P3)

**Goal**: el administrador saca una cuenta, con la advertencia previa si tiene actividad.
**Independent Test**: eliminar una cuenta sin actividad y comprobar que no inicia sesión; intentarlo con un pedido en curso.
**Cubre**: FR-001–FR-004, SC-001, SC-002, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /usuarios/{id}/eliminar` sin actividad (`204`, `ELIMINADA`, repetición con la misma clave) en `backend/src/test/java/co/edu/napa/cuentas/EliminarUsuarioAdminIT.java`
- [ ] T011 [P] [US1] Test de la advertencia con pedido pendiente, domicilio activo, tarifa sin pagar y emprendimiento activo (`409` con conteos) en `.../cuentas/EliminarUsuarioAdminAdvertenciaIT.java`
- [ ] T012 [P] [US1] Test de que con `forzar: true` procede y de que la cuenta queda `ELIMINADA` con la actividad intacta, en `.../cuentas/EliminarUsuarioAdminForzadoIT.java`
- [ ] T013 [P] [US1] Test de que, eliminada la cuenta, el login, el access token anterior y el refresh fallan, en `.../cuentas/EliminarUsuarioAdminSesionesIT.java`
- [ ] T014 [P] [US1] Test de que sin `confirmar: true` no se elimina (`400`) en `.../cuentas/EliminarUsuarioAdminConfirmacionIT.java`
- [ ] T015 [P] [US1] Test de vendedor: su emprendimiento activo se elimina y deja de verse; historial y calificaciones se conservan, en `.../cuentas/EliminarUsuarioAdminVendedorIT.java`
- [ ] T016 [P] [US1] Test de que no hay reactivación ni período de gracia y de cuenta `PENDIENTE_VERIFICACION` en `.../cuentas/EliminarUsuarioAdminEstadosIT.java`
- [ ] T017 [P] [US1] Test de administrador, uno mismo, inexistente, ya eliminada, rol distinto y sin sesión en `.../cuentas/EliminarUsuarioAdminAutorizacionIT.java`
- [ ] T030 [P] [US1] Test de `EliminarUsuarioSheet` (advertencia con detalle, segunda confirmación, errores) en `frontend/__tests__/usuarios/eliminar-usuario.test.tsx`

### Implementation

- [ ] T020 [US1] `EliminarUsuarioAdminPort` y `EliminarUsuarioAdminUseCase`: bloquea el usuario, valida el destino, consulta la actividad, exige `forzar` si la hay y cambia el estado
- [ ] T021 [US1] `POST /usuarios/{id}/eliminar` y su DTO en `UsuarioAdminController`
- [ ] T022 [US1] Respuesta `409 HAS_PENDING_OPERATIONS` con `details` como advertencia
- [ ] T023 [US1] Revocación de sesiones e incremento de `sv:{userId}`
- [ ] T024 [US1] Eliminación del emprendimiento del vendedor en la misma transacción con `EliminarEmprendimientoDeVendedorPort`
- [ ] T031 [US1] `eliminarUsuario()` con `Idempotency-Key` en `frontend/src/services/usuariosAdmin.ts`
- [ ] T032 [US1] `EliminarUsuarioSheet` (primero la advertencia, luego la confirmación forzada) y la acción en el detalle (RF-66)

**Checkpoint**: el administrador retira cuentas con advertencia previa.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Confirmar que el log no guarda datos personales
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-11 (dominio, pendientes y sesiones), RF-66 (detalle), RF-30 y RF-34 (que ya excluyen el emprendimiento `ELIMINADO`) y RF-10; RF-13 permite cancelar antes los domicilios en curso.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| **Forzar la eliminación de un usuario con actividad en curso deja pedidos y domicilios a medias** (un domiciliario eliminado con un domicilio `ASIGNADO`; un cliente con un pedido `ACEPTADO`) | Regla | Se permite tras la advertencia, como pide la spec, y la actividad queda como está. La app recomienda cancelar antes los domicilios con RF-13. **Confirmar**; la alternativa es bloquear siempre, sin `forzar` | Equipo |
| ¿Se elimina también el emprendimiento activo del vendedor? | Regla | Sí, en la misma transacción, para que no quede visible un negocio sin dueño; sus productos y pedidos se conservan | Equipo |
| El administrador no puede eliminar a otro administrador ni a sí mismo | Seguridad | `422`; las cuentas de administrador se gestionan desde la base | Equipo |
| No hay registro de quién eliminó la cuenta (spec-general no tiene tabla de auditoría) | Auditoría | Solo el log con el administrador y el usuario, y `updated_at`; una tabla de auditoría sería cambio de modelo | Equipo |
| Edge 2: ¿período de gracia? | Regla | Ninguno, igual que RF-11 | Equipo |
| `forzar` y `confirmar` en el cuerpo, y la ruta `POST /usuarios/{id}/eliminar` | Contrato | Se agrega a §2.4 de spec-general | Equipo |
| El usuario eliminado no recibe aviso | Alcance | No: la spec no lo pide | Equipo |

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
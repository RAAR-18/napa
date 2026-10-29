# Implementation Plan: Edición de usuario por administrador

**Date**: 2026-10-27
**Spec**: [RF-67-edicion-de-usuario-por-administrador.md](./RF-67-edicion-de-usuario-por-administrador.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-67
**Historias de usuario**: HU-100
**Prioridad (la mayor de la spec)**: P3
**Depende de**: RF-66, RF-69, RF-11, RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador corrige los datos de un usuario con `PATCH /usuarios/{id}`: nombre, celular, correo, rol y ubicación (uno o varios). Antes de guardar valida que el celular y el correo no pertenezcan a otra cuenta (`409 PHONE_ALREADY_REGISTERED` o `EMAIL_ALREADY_REGISTERED`). El celular lo fija el administrador directamente, sin código de verificación. Cambiar el rol solo se permite si el usuario no tiene operaciones pendientes ni un emprendimiento activo, y no se puede asignar ni quitar el rol de administrador; tras un cambio de rol se cierran sus sesiones para que su próximo token traiga el rol nuevo. Solo se editan cuentas `ACTIVA` que no sean de administrador. La respuesta trae el perfil actualizado y el cambio se ve de inmediato.

## Estado actual del código

RF-66 devuelve el detalle de un usuario (el mismo DTO que `GET /usuarios/me`) y RF-69 el listado desde el que se llega. RF-11 ya tiene `OperacionesPendientesPort` (pedidos, domicilios, tarifas sin pagar y emprendimiento activo) y la revocación de sesiones con `sv`; este plan los reutiliza para el cambio de rol. RF-08 es la edición del propio perfil (sin correo, sin rol, celular con código); aquí el administrador tiene más alcance. El modelo tiene un CHECK por rol (por ejemplo, el administrador exige correo).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador, Vendedor, Domiciliario | Son los usuarios editados; el cambio de rol cierra sus sesiones |
| Administrador | Corrige la información de un usuario |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Cambios visibles en < 5 s (SC-001): la respuesta trae el perfil y no hay caché.
- 100 % de duplicados rechazados (SC-002): validación previa y respaldo con el índice único de `celular` y de `correo` en la base, que cubre la carrera entre dos ediciones.
- Rol coherente con el resto del sistema: el JWT lleva el rol, así que un cambio de rol incrementa `sv` y revoca sesiones.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El administrador edita la información de un usuario | `PATCH /usuarios/{id}` (parcial) | T010, T011, T020, T021 |
| FR-002 | Validar que correo y teléfono no dupliquen otra cuenta | `409 EMAIL_ALREADY_REGISTERED` y `409 PHONE_ALREADY_REGISTERED`, con respaldo en los índices únicos | T012, T022 |
| FR-003 | Reflejar los cambios de inmediato | La respuesta y `GET /usuarios/{id}` (RF-66) los muestran | T010, T013 |
| SC-001 | Cambios visibles en < 5 s | Sin caché | T013 |
| SC-002 | 100 % de duplicados rechazados | Pruebas parametrizadas y de carrera | T012 |
| US1-E1 | Edición exitosa de uno o varios datos | `200` con el perfil actualizado | T010, T011 |
| Edge 1 | Cambiar el rol, y qué validaciones aplican | Sin operaciones pendientes ni emprendimiento activo; no hacia ni desde administrador; sesiones cerradas | T014, T015, T023 |
| Edge 2 | Correo o teléfono ya usados por otra cuenta | `409` con el código específico | T012 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR |
| RNF04 Datos personales | ☑ | `no-store`; cada edición queda en el log con el administrador, el usuario y los campos cambiados (sin los valores) |
| RNF07 Rendimiento ≤ 3 s | ☑ | Una fila |
| RNF08 Concurrencia | ☑ | `@Version` en `usuario` y índices únicos |
| RNF13 Arquitectura | ☑ | Las operaciones pendientes se consultan por puertos `in` (RF-11) |
| RNF23 Consistencia | ☑ | Solo se edita una cuenta `ACTIVA`; cambio de rol y revocación de sesiones en una transacción |
| RNF24 Errores claros | ☑ | Códigos específicos y `details.campo` |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Usuario | Lee / escribe (`nombre`, `celular`, `correo`, `rol`, `celular_verificado_at`) | `celular` y `correo` únicos; CHECK por rol; el rol de administrador no se asigna ni se quita |
| Ubicacion | Escribe (en su lugar) | La del perfil del usuario |
| Sesion | Escribe (`revoked_at`) | Solo si cambia el rol |

## State Transitions

No cambia el estado de la cuenta (`ACTIVA`). Una cuenta `PENDIENTE_VERIFICACION` o `ELIMINADA` responde `409 INVALID_STATE`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `PATCH /usuarios/{id}` | ADMINISTRADOR | Cualquier usuario `ACTIVA` que no sea administrador; las cuentas de administrador responden `422 BUSINESS_RULE` |

## API Contracts

### PATCH /api/v1/usuarios/{id}

- **Rol**: administrador · **Path params**: `id` (UUID del usuario) · **Query params**: ninguno
- **Body** (parcial; al menos un campo):

```json
{
  "nombre": "Ana María Pérez",
  "celular": "+573001234567",
  "correo": "ana@example.com",
  "rol": "VENDEDOR_PUNTO_FIJO",
  "ubicacion": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" }
}
```

`rol` admite `CLIENTE`, `VENDEDOR_AMBULANTE`, `VENDEDOR_PUNTO_FIJO` y `DOMICILIARIO`.

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Content-Type | ✅ | `application/json` |

`PATCH` no exige `Idempotency-Key` (spec-general). Repetir el mismo cuerpo deja el mismo resultado.

- **Respuestas exitosas**: `200` con el perfil completo (mismo cuerpo de `GET /usuarios/{id}`), `Cache-Control: no-store` y `X-Request-Id`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo vacío; nombre vacío o largo; celular o correo con formato inválido; rol desconocido; coordenadas fuera de rango; campo no editable (`estado`, `password`, `fotoUrl`) | `details.campo` |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de administrador | — |
| 404 | `NOT_FOUND` | El usuario no existe | "El usuario no existe" |
| 409 | `PHONE_ALREADY_REGISTERED` | El celular es de otra cuenta | "Ese celular ya está registrado" |
| 409 | `EMAIL_ALREADY_REGISTERED` | El correo es de otra cuenta | "Ese correo ya está registrado" |
| 409 | `HAS_PENDING_OPERATIONS` | Cambio de rol con pedidos, domicilios, tarifas sin pagar o emprendimiento activo | `details` con los conteos (el de RF-11) |
| 409 | `INVALID_STATE` | La cuenta no está `ACTIVA` | `details.estadoActual` |
| 409 | `CONFLICT` | Dos escrituras simultáneas sobre la misma cuenta | "La cuenta cambió; vuelve a intentarlo" |
| 422 | `BUSINESS_RULE` | El usuario es administrador, o el rol pedido es `ADMINISTRADOR` | "Las cuentas de administrador no se editan aquí" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Editar un usuario no tiene reacciones |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: es una acción administrativa y no se encola: la app exige conexión.
- **Concurrencia (RNF08)**: `@Version` en `usuario`: una colisión devuelve `409 CONFLICT` y la app reintenta con los datos frescos. Dos ediciones que asignan el mismo celular o correo: una gana y la otra recibe el `409` del índice único.
- **Idempotencia (RNF27)**: `PATCH` es idempotente por naturaleza.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Celular o correo duplicado | `409` con el código específico | Edge 2, FR-002 |
| Cambio de rol con pendientes | `409 HAS_PENDING_OPERATIONS` | Edge 1 |
| Rol de administrador | `422 BUSINESS_RULE` | Edge 1 |
| Cuenta eliminada o pendiente de verificación | `409 INVALID_STATE` | RF-11 |
| Choque de escrituras | `409 CONFLICT` | RNF08 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Edición de cada campo y de varios a la vez, y que `GET /usuarios/{id}` los refleja | FR-001, FR-003, US1-E1, SC-001 |
| Integración | Celular y correo duplicados, con la carrera entre dos ediciones, y propios sin cambio | FR-002, SC-002, Edge 2 |
| Integración | Cambio de rol permitido (sin pendientes) con sesiones cerradas; bloqueado con pedido, domicilio, tarifa sin pagar y emprendimiento activo; hacia o desde administrador | Edge 1 |
| Integración | Datos inválidos, campos no editables, cuenta `ELIMINADA` y `PENDIENTE_VERIFICACION`, administrador, inexistente, rol distinto, sin sesión | RNF24 |
| Concurrencia | Dos `PATCH` simultáneos | RNF08 |
| Frontend | Jest: formulario precargado, errores de duplicado, advertencia del cambio de rol | US1-E1, Edge 1–2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-67-edicion-de-usuario-por-administrador/
├── RF-67-edicion-de-usuario-por-administrador.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/cuentas/
├── domain/model/                 # Usuario.corregirDatos(...), cambiarRol(...)
├── application/
│   ├── ports/in/                 # EditarUsuarioAdminPort
│   ├── ports/out/                # UsuarioRepositoryPort, UbicacionRepositoryPort, SesionRepositoryPort, OperacionesPendientesPort (RF-11)
│   └── usecase/                  # EditarUsuarioAdminUseCase
└── infrastructure/adapters/
    └── in/web/                   # UsuarioAdminController (PATCH /usuarios/{id}), DTO parcial

backend/src/test/java/co/edu/napa/cuentas/

frontend/src/app/(administrador)/usuarios/[id]/editar.tsx          # formulario de edición
frontend/src/components/usuarios/FormularioUsuarioAdmin.tsx
frontend/src/services/usuariosAdmin.ts                             # editarUsuario()
frontend/__tests__/usuarios/editar-usuario.test.tsx
```

**Structure Decision**: módulo `cuentas`, en el mismo controlador de administrador de RF-66 y RF-69. El cambio de rol reutiliza la consulta de operaciones pendientes de RF-11 y su revocación de sesiones. Las cuentas de administrador quedan fuera porque se siembran desde la base y editarlas por la API sería una vía para tomar otra cuenta de administrador.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `cuentas`, el CHECK por rol de `usuario` y los índices únicos de `celular` y `correo`

## Phase 2: Foundational

- [ ] T002 Columna `version` y `@Version` en `UsuarioJpaEntity` en la siguiente migración libre de `backend/src/main/resources/db/migration/` (si no existe)
- [ ] T003 Reutilizar `OperacionesPendientesPort` y `SesionRepositoryPort.revocarTodas` de RF-11 (T005, T006)
- [ ] T004 Registro en el log de cada edición (administrador, usuario y nombres de los campos cambiados, sin valores)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Corregir la información de un usuario (Priority: P3)

**Goal**: el administrador corrige datos erróneos sin crear inconsistencias.
**Independent Test**: corregir el celular y el rol de un usuario y verlo reflejado en su perfil.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `PATCH /usuarios/{id}` con cada campo y con varios, y respuesta con el perfil, en `backend/src/test/java/co/edu/napa/cuentas/EditarUsuarioAdminIT.java`
- [ ] T011 [P] [US1] Test de datos inválidos y campos no editables (`400`) en `.../cuentas/EditarUsuarioAdminValidacionIT.java`
- [ ] T012 [P] [US1] Test de celular y correo duplicados, y de la carrera entre dos ediciones, en `.../cuentas/EditarUsuarioAdminDuplicadoIT.java`
- [ ] T013 [P] [US1] Test de que `GET /usuarios/{id}` (RF-66) refleja el cambio de inmediato en `.../cuentas/EditarUsuarioAdminReflejoIT.java`
- [ ] T014 [P] [US1] Test de cambio de rol permitido (sesiones cerradas y token nuevo con el rol nuevo) en `.../cuentas/EditarUsuarioAdminRolIT.java`
- [ ] T015 [P] [US1] Test de cambio de rol bloqueado por pedido, domicilio, tarifa sin pagar o emprendimiento activo, y de roles de administrador (`422`), en `.../cuentas/EditarUsuarioAdminRolBloqueadoIT.java`
- [ ] T016 [P] [US1] Test de cuenta `ELIMINADA`, `PENDIENTE_VERIFICACION` y administrador, usuario inexistente, rol distinto y sin sesión, en `.../cuentas/EditarUsuarioAdminAutorizacionIT.java`
- [ ] T017 [P] [US1] Test de dos `PATCH` simultáneos en `.../cuentas/EditarUsuarioAdminConcurrenciaIT.java`
- [ ] T030 [P] [US1] Test del formulario (precargado, duplicados, advertencia de cambio de rol) en `frontend/__tests__/usuarios/editar-usuario.test.tsx`

### Implementation

- [ ] T020 [US1] `Usuario.corregirDatos` y `Usuario.cambiarRol` con sus reglas en `cuentas/domain/model/Usuario.java`
- [ ] T021 [US1] `EditarUsuarioAdminPort` y `EditarUsuarioAdminUseCase`: valida estado y destino, aplica todo o nada
- [ ] T022 [US1] Validación de duplicados con mapeo de los índices únicos a `PHONE_ALREADY_REGISTERED` y `EMAIL_ALREADY_REGISTERED`
- [ ] T023 [US1] Regla del cambio de rol: operaciones pendientes, administrador y revocación de sesiones con incremento de `sv`
- [ ] T024 [US1] `PATCH /usuarios/{id}` y su DTO parcial en `UsuarioAdminController`
- [ ] T031 [US1] `editarUsuario()` en `frontend/src/services/usuariosAdmin.ts`
- [ ] T032 [US1] `FormularioUsuarioAdmin` y pantalla `usuarios/[id]/editar.tsx`, enlazada desde el detalle (RF-66)

**Checkpoint**: el administrador corrige los datos de un usuario.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Confirmar que el log no guarda los valores personales
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-66 (detalle), RF-69, RF-11 (operaciones pendientes y revocación de sesiones) y RF-10.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| El celular lo fija el administrador sin código de verificación y queda `celular_verificado_at = ahora` | Regla | Así: la spec lo trata como corrección de soporte y no hay otra vía. El riesgo es registrar a alguien con un número que no controla. **Confirmar** | Equipo |
| Edge 1: ¿se puede cambiar el rol? ¿qué validaciones? | Regla | Sí (el test independiente lo pide), solo sin operaciones pendientes ni emprendimiento activo, nunca hacia o desde administrador, y con las sesiones cerradas | Equipo |
| El administrador no puede editar a otros administradores | Seguridad | `422`; sus datos se cambian desde la base (RF-08 para él mismo) | Equipo |
| Si cambia de rol de vendedor o domiciliario a otro, su `ConfiguracionPago` queda sin uso | Datos | Se conserva sin efecto (spec-general restricción 1) | Equipo |
| No se pueden cambiar el estado, la contraseña ni la foto | Alcance | El estado lo cambian RF-11 y RF-68 | Equipo |
| El usuario no recibe un aviso de que el administrador cambió sus datos | Alcance | No: la spec no lo pide | Equipo |
| La ruta no está en spec-general (`PATCH /usuarios/{id}`) | Contrato | Se agrega a spec-general | Equipo |

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
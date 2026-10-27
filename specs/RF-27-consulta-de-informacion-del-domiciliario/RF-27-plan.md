# Implementation Plan: Eliminación de cuenta

**Date**: 2026-10-27
**Spec**: [RF-11-eliminacion-de-cuenta.md](./RF-11-eliminacion-de-cuenta.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-11
**Historias de usuario**: HU-28
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-10, RF-09, RF-30, RF-45, RF-53, RF-14, RF-19
**Habilita a**: RF-68 (eliminación de usuario por administrador, que puede reutilizar las mismas reglas)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El usuario elimina su propia cuenta con `POST /usuarios/me/eliminar` (cuerpo `{ "confirmar": true }`, 204). Antes de eliminar, el sistema revisa que no tenga operaciones pendientes: pedidos en curso (como comprador o como vendedor), domicilios activos (como vendedor o como domiciliario) y, si es vendedor, un emprendimiento activo. Si las tiene, responde `409 HAS_PENDING_OPERATIONS` con el detalle de qué debe resolver. Si no, la cuenta pasa a `ELIMINADA` (borrado lógico: la fila y su historial se conservan), se revocan todas sus sesiones y no puede volver a iniciar sesión. Las calificaciones, los pedidos y los domicilios ya terminados siguen existiendo.

## Estado actual del código

`Usuario` ya tiene el estado `ELIMINADA` en spec-general y RF-10 responde `401 INVALID_CREDENTIALS` para esas cuentas. RF-09 ya revoca sesiones e incrementa la versión de sesión (`sv:{userId}` en Redis); este plan reutiliza ese mecanismo. RF-38 ya decidió no enviar push a cuentas `ELIMINADA`. No existe aún ninguna consulta de operaciones pendientes en los módulos `pedidos`, `domicilios` ni `emprendimientos`: este plan agrega un puerto `in` en cada uno.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Elimina su cuenta; bloquea un pedido en curso |
| Vendedor (ambulante / punto fijo) | Igual; bloquea un pedido en curso, un domicilio activo o un emprendimiento activo |
| Domiciliario | Igual; bloquea un domicilio activo |
| Administrador | No puede eliminarse por esta ruta (`403`); lo trata RF-68 si aplica |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Rechazar el 100 % de las eliminaciones con pendientes (SC-001): la verificación y el cambio de estado ocurren en una sola transacción.
- Ninguna sesión sobrevive a la eliminación (SC-002): se revocan los refresh tokens y se incrementa `sv:{userId}`, así el access token vigente deja de servir de inmediato.
- Cada módulo responde por lo suyo: `cuentas` pregunta por puertos `in` a `pedidos`, `domicilios` y `emprendimientos`; no lee sus tablas.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El usuario solicita eliminar su cuenta | `POST /usuarios/me/eliminar` | T010, T020, T021 |
| FR-002 | Verificar que no haya pedidos, emprendimientos o domicilios pendientes | `ConsultarOperacionesPendientesPort` de cada módulo y `409 HAS_PENDING_OPERATIONS` con `details` | T011, T012, T022, T023, T024 |
| FR-003 | Impedir el inicio de sesión | Estado `ELIMINADA`, revocación de sesiones y `sv` | T013, T025 |
| SC-001 | 100 % de rechazos con pendientes | Pruebas por cada tipo de pendiente | T011, T012 |
| SC-002 | 100 % de intentos posteriores rechazados | Login y access token anteriores fallan | T013 |
| US1-E1 | Eliminación sin pendientes | `204` y cuenta `ELIMINADA` | T010 |
| US1-E2 | Eliminación con pendientes | `409` indicando cuáles | T011, T012 |
| Edge 1 | Emprendimientos, productos e historial de una cuenta eliminada | Se conservan; el emprendimiento activo bloquea la eliminación, y los pedidos terminados siguen visibles para la otra parte (Open Questions) | T012, T014 |
| Edge 2 | Período de arrepentimiento | No hay período de gracia ni reactivación: el borrado lógico conserva los datos sin purga (Open Questions) | T026 |
| Edge 3 | Calificaciones y comentarios de la cuenta | Se conservan y siguen contando en el promedio; RF-02 muestra el nombre | T014 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo sobre la propia cuenta (`/me`) |
| RNF04 Datos personales | ☑ | Se conserva el registro lógico; no se exponen datos tras eliminar |
| RNF07 Rendimiento ≤ 3 s | ☑ | Tres consultas indexadas y una actualización |
| RNF13 Arquitectura | ☑ | Solo puertos `in` entre módulos (ArchUnit) |
| RNF15 Fiabilidad | ☑ | Verificación, cambio de estado y revocación de sesiones en una transacción |
| RNF23 Consistencia | ☑ | Transición `ACTIVA → ELIMINADA` única; repetirla da `INVALID_STATE` |
| RNF27 Idempotencia | ☑ | `Idempotency-Key` obligatoria |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Usuario | Lee / escribe (`estado`) | `ACTIVA → ELIMINADA`; la fila no se borra; `updated_at` queda como fecha de la eliminación |
| Sesion | Escribe (`revoked_at`) | Se revocan todas las del usuario |
| Pedido | Lee (puerto) | Pendiente: `PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER` o `EN_CAMINO` |
| Domicilio | Lee (puerto) | Activo: `DISPONIBLE`, `ASIGNADO`, `RECOGIDO` o `EN_CAMINO` |
| Emprendimiento | Lee (puerto) | Pendiente si está `ACTIVO` |

## State Transitions

| Entidad | Desde | Hacia | Acción | Condición |
|---|---|---|---|---|
| Usuario | `ACTIVA` | `ELIMINADA` | `POST /usuarios/me/eliminar` | Sin operaciones pendientes |

Una cuenta `ELIMINADA` es terminal: no se reactiva. Eliminar una cuenta que ya no está `ACTIVA` responde `409 INVALID_STATE`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /usuarios/me/eliminar` | CLIENTE, VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO, DOMICILIARIO | Solo la propia cuenta; el administrador recibe `403` |

## API Contracts

### POST /api/v1/usuarios/me/eliminar

- **Rol**: cliente, vendedores y domiciliario · **Path params**: ninguno · **Query params**: ninguno
- **Body**: `{ "confirmar": true }` (la app lo envía solo tras el cuadro de confirmación explícita)

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `204` sin cuerpo y con `X-Request-Id`. Desde ese momento los tokens del usuario dejan de servir.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta `confirmar` o no es `true` | "Confirma la eliminación" |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Es administrador | "Esta cuenta no se puede eliminar aquí" |
| 409 | `HAS_PENDING_OPERATIONS` | Hay pedidos, domicilios o un emprendimiento pendientes | "Resuelve tus operaciones pendientes antes de eliminar la cuenta"; `details`: `{ "pedidosPendientes": 2, "domiciliosActivos": 1, "emprendimientoActivo": true }` |
| 409 | `INVALID_STATE` | La cuenta no está `ACTIVA` | `details.estadoActual` |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

`details` solo incluye los conteos que no son cero, para que la app arme los avisos ("Tienes 2 pedidos pendientes").

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | RF-38 ya omite los push a cuentas `ELIMINADA`, así que no hace falta un evento |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: es una acción destructiva y no se encola: la app exige conexión y muestra el error si falla. Al recibir `204`, borra el SQLite, los tokens y vuelve a la pantalla de ingreso.
- **Concurrencia (RNF08)**: la verificación y el cambio ocurren con `SELECT … FOR UPDATE` sobre el usuario. Un pedido que llegue justo entre la verificación y el commit es una ventana mínima; como la sesión se revoca, el usuario no puede crear más pedidos (Open Questions).
- **Idempotencia (RNF27)**: repetir la misma `Idempotency-Key` devuelve el mismo `204`; con otra clave y la cuenta ya eliminada llega `401`, porque el token ya no sirve.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido o domicilio pendiente | `409 HAS_PENDING_OPERATIONS` con conteos | US1-E2 |
| Vendedor con emprendimiento activo | `409` con `emprendimientoActivo: true` | FR-002 |
| Sin `confirmar: true` | `400` | FR-001 |
| Administrador | `403` | Alcance |
| Cuenta que intenta ingresar después | `401 INVALID_CREDENTIALS` (RF-10) | FR-003 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Eliminación sin pendientes; con pedido pendiente como comprador y como vendedor; con domicilio activo como vendedor y como domiciliario; vendedor con emprendimiento activo; administrador; sin `confirmar`; repetición | FR-001, FR-002, US1-E1–E2 |
| Integración | Después de eliminar: login falla, el access token anterior y el refresh fallan, los datos históricos de otros usuarios no cambian | FR-003, SC-002, Edge 1, Edge 3 |
| Arquitectura | `cuentas` solo usa puertos `in` de `pedidos`, `domicilios` y `emprendimientos` | RNF13 |
| Frontend | Jest: cuadro de confirmación, avisos con los conteos, limpieza local tras eliminar | FR-001, US1-E2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-11-eliminacion-de-cuenta/
├── RF-11-eliminacion-de-cuenta.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/cuentas/
├── application/
│   ├── ports/in/                 # EliminarCuentaPort
│   ├── ports/out/                # UsuarioRepositoryPort, SesionRepositoryPort, OperacionesPendientesPort (agrega las de otros módulos)
│   └── usecase/                  # EliminarCuentaUseCase
└── infrastructure/adapters/
    ├── in/web/                   # CuentaController (POST /usuarios/me/eliminar)
    └── out/                      # adaptador de OperacionesPendientesPort que llama a los puertos in de los otros módulos

backend/src/main/java/co/edu/napa/pedidos/application/ports/in/         # ConsultarPedidosPendientesDeUsuarioPort
backend/src/main/java/co/edu/napa/domicilios/application/ports/in/      # ConsultarDomiciliosActivosDeUsuarioPort
backend/src/main/java/co/edu/napa/emprendimientos/application/ports/in/ # ConsultarEmprendimientoActivoDeVendedorPort
backend/src/test/java/co/edu/napa/cuentas/

frontend/src/app/(comun)/perfil/eliminar-cuenta.tsx        # aviso, pendientes y confirmación
frontend/src/components/perfil/ListaPendientes.tsx
frontend/src/services/perfil.ts                            # eliminarCuenta()
frontend/__tests__/perfil/eliminar-cuenta.test.tsx
```

**Structure Decision**: módulo `cuentas`, dueño de `Usuario`, con la acción como `POST /usuarios/me/eliminar` (spec-general: las acciones de estado son `POST`). Cada módulo con datos pendientes expone un puerto `in` de solo lectura y `cuentas` los agrega; así no hay joins entre módulos. El borrado es lógico, igual que en el resto del proyecto (RF-05): el estado y `updated_at` son la huella.

## Phase 1: Setup

- [ ] T001 Confirmar `Usuario.estado = ELIMINADA` y que RF-10 y el filtro del JWT rechazan esas cuentas

## Phase 2: Foundational

- [ ] T002 `ConsultarPedidosPendientesDeUsuarioPort` en `pedidos` (como comprador o como dueño del emprendimiento; estados `PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER`, `EN_CAMINO`)
- [ ] T003 `ConsultarDomiciliosActivosDeUsuarioPort` en `domicilios` (como vendedor por `vendedor_id` o como domiciliario; estados `DISPONIBLE`, `ASIGNADO`, `RECOGIDO`, `EN_CAMINO`)
- [ ] T004 `ConsultarEmprendimientoActivoDeVendedorPort` en `emprendimientos`
- [ ] T005 `OperacionesPendientesPort` en `cuentas` y su adaptador, que agrega los tres conteos
- [ ] T006 Métodos `revocarTodas` en `SesionRepositoryPort` y bloqueo de la fila en `UsuarioRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Eliminar mi cuenta (Priority: P2)

**Goal**: el usuario sale de la plataforma sin dejar operaciones sin resolver.
**Independent Test**: eliminar una cuenta sin pendientes y comprobar que no inicia sesión; intentarlo con un pedido pendiente.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /usuarios/me/eliminar` sin pendientes (`204`, estado `ELIMINADA`, repetición con la misma clave) en `backend/src/test/java/co/edu/napa/cuentas/EliminarCuentaIT.java`
- [ ] T011 [P] [US1] Test de rechazo con pedido pendiente como comprador y como vendedor, y con domicilio activo como vendedor y como domiciliario, en `.../cuentas/EliminarCuentaPendientesIT.java`
- [ ] T012 [P] [US1] Test de vendedor con emprendimiento activo y de que `details` solo trae conteos distintos de cero, en `.../cuentas/EliminarCuentaEmprendimientoIT.java`
- [ ] T013 [P] [US1] Test de que, eliminada la cuenta, el login, el access token anterior y el refresh fallan, en `.../cuentas/EliminarCuentaSesionesIT.java`
- [ ] T014 [P] [US1] Test de que los pedidos terminados, las calificaciones y los domicilios entregados de la cuenta se conservan y siguen visibles a la otra parte, en `.../cuentas/EliminarCuentaHistorialIT.java`
- [ ] T015 [P] [US1] Test de administrador (`403`), sin `confirmar` (`400`), sin sesión (`401`) y cuenta ya eliminada (`INVALID_STATE`), en `.../cuentas/EliminarCuentaAutorizacionIT.java`
- [ ] T016 [P] [US1] Test de arquitectura de los tres puertos `in` en `.../cuentas/ArquitecturaEliminarCuentaTest.java`
- [ ] T030 [P] [US1] Test de la pantalla `eliminar-cuenta` (confirmación, conteos de pendientes, limpieza local) en `frontend/__tests__/perfil/eliminar-cuenta.test.tsx`

### Implementation

- [ ] T020 [US1] `EliminarCuentaPort` y `EliminarCuentaUseCase`: bloquea el usuario, consulta pendientes, cambia el estado y revoca sesiones en una transacción
- [ ] T021 [US1] `POST /usuarios/me/eliminar` y su DTO en `CuentaController`
- [ ] T022 [US1] Adaptador de `ConsultarPedidosPendientesDeUsuarioPort` en `pedidos`
- [ ] T023 [US1] Adaptador de `ConsultarDomiciliosActivosDeUsuarioPort` en `domicilios`
- [ ] T024 [US1] Adaptador de `ConsultarEmprendimientoActivoDeVendedorPort` en `emprendimientos`
- [ ] T025 [US1] Incremento de `sv:{userId}` en Redis y revocación de refresh tokens
- [ ] T026 [US1] Error `HAS_PENDING_OPERATIONS` con `details` en el manejador de errores y en `INVALID_STATE` para una cuenta no activa
- [ ] T031 [US1] `eliminarCuenta()` en `frontend/src/services/perfil.ts` y limpieza de SQLite y tokens
- [ ] T032 [US1] Pantalla `eliminar-cuenta.tsx` con `ListaPendientes` y el cuadro de confirmación

**Checkpoint**: la cuenta se elimina o se explica por qué no.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` de las tres consultas de pendientes con datos de piloto
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-10 (sesiones), RF-09 (`sv`), y los datos de RF-45/RF-53 (pedidos), RF-14/RF-19 (domicilios) y RF-28 (emprendimiento); un vendedor debe antes eliminar su emprendimiento con RF-30.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| FR-002 dice "emprendimientos pendientes": ¿un emprendimiento activo bloquea la eliminación o se elimina junto con la cuenta? | Regla | Lo bloquea; el vendedor lo elimina antes con RF-30. **Confirmar** | Equipo |
| ¿Una tarifa de domicilio entregado sin pagar (vendedor que debe, domiciliario que cobra) bloquea la eliminación? | Regla | No bloquea; la spec solo habla de domicilios pendientes. **Confirmar** | Equipo |
| Edge 2: período de arrepentimiento | Regla | Ninguno: eliminación inmediata, sin reactivación y sin purga automática; el celular y el correo quedan reservados y no se pueden volver a registrar | Equipo |
| `confirmar: true` en el cuerpo y sin pedir la contraseña | Seguridad | Solo el cuerpo; pedir la contraseña sería comportamiento nuevo | Equipo |
| Código nuevo `HAS_PENDING_OPERATIONS` (409) | Contrato | Se agrega al catálogo de spec-general | Equipo |
| Ventana de carrera: un pedido entra entre la verificación y el commit | Concurrencia | Aceptada; la sesión revocada impide nuevos pedidos del comprador, y el vendedor ya no tiene emprendimiento activo | Equipo |
| La fecha de eliminación no tiene columna propia | Auditoría | `updated_at`, porque `ELIMINADA` es terminal | Equipo |
| El administrador no puede eliminarse por esta ruta | Alcance | `403` | Equipo |

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
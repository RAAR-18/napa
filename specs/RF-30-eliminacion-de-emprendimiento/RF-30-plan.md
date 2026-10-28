# Implementation Plan: Eliminación de emprendimiento

**Date**: 2026-10-27
**Spec**: [RF-30-eliminacion-de-emprendimiento.md](./RF-30-eliminacion-de-emprendimiento.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-30
**Historias de usuario**: HU-55
**Prioridad (la mayor de la spec)**: P3
**Depende de**: RF-28, RF-29, RF-11, RF-45, RF-10
**Habilita a**: RF-11 (cuenta del vendedor), RF-68

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor elimina su emprendimiento con `POST /emprendimientos/me/eliminar` (cuerpo `{ "confirmar": true }`, `204`). Solo se permite cuando el emprendimiento no tiene pedidos pendientes, reservas incluidas (`PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER` o `EN_CAMINO`); si los tiene responde `409 HAS_PENDING_ORDERS` con la cantidad. Al eliminarlo pasa a `ELIMINADO` (borrado lógico) y desaparece al instante del listado y del catálogo públicos (RF-31, RF-32, RF-33). El historial de pedidos, las calificaciones y los productos se conservan. El vendedor puede crear un emprendimiento nuevo enseguida.

## Estado actual del código

RF-28 ya define el estado `ELIMINADO` y el índice único parcial por vendedor sobre `ACTIVO`; RF-29 resuelve `/emprendimientos/me` solo para el emprendimiento `ACTIVO`. RF-31, RF-32 y RF-33 ya responden `404` o excluyen los que no están `ACTIVO`. RF-11 depende de esta feature: un vendedor con emprendimiento activo no puede eliminar su cuenta. El puerto `ConsultarPedidosPendientesDeUsuarioPort` de `pedidos` lo crea RF-11 (T002).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Deja de ver el emprendimiento; no interviene |
| Vendedor (ambulante / punto fijo) | Elimina su emprendimiento |
| Domiciliario | No interviene |
| Administrador | No interviene (RF-68 lo elimina cuando borra a un vendedor) |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Bloqueo del 100 % con pedidos pendientes (SC-001): la verificación y el cambio de estado ocurren con el emprendimiento bloqueado (`FOR UPDATE`) en la misma transacción.
- Invisible en < 3 s (SC-002): el estado cambia en una transacción y RF-31, RF-32 y RF-33 lo leen sin caché.
- Sin cascadas costosas: los productos no se tocan; quedan ocultos porque su emprendimiento ya no está `ACTIVO`.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Eliminar solo sin pedidos pendientes, reservas incluidas | `POST /emprendimientos/me/eliminar` y `ConsultarPedidosPendientesDeUsuarioPort`; `409 HAS_PENDING_ORDERS` | T010, T011, T020, T021 |
| FR-002 | Confirmación explícita | Cuadro de confirmación en la app y `confirmar: true` exigido por el servidor | T012, T030, T031 |
| FR-003 | Ocultar el emprendimiento y su catálogo de inmediato | Estado `ELIMINADO`; RF-31, RF-32 y RF-33 ya lo excluyen | T013 |
| SC-001 | 100 % de intentos con pendientes bloqueados | Prueba por cada estado pendiente | T011 |
| SC-002 | Invisible en < 3 s | Sin caché | T013 |
| US1-E1 | Eliminación sin pedidos pendientes | `204` | T010 |
| US1-E2 | Eliminación con pedidos pendientes | `409` con la cantidad | T011 |
| Edge 1 | Domicilios en curso sin pedidos pendientes | No puede darse: un domicilio en curso pertenece a un pedido `ACEPTADO`, que es pendiente | T014 |
| Edge 2 | Historial de pedidos y calificaciones | Se conservan; nada se borra | T015 |
| Edge 3 | Crear un emprendimiento nuevo enseguida | Permitido: el índice único solo aplica a `ACTIVO` | T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el vendedor sobre su propio emprendimiento (`/me`) |
| RNF07 Rendimiento ≤ 3 s | ☑ | Una consulta indexada y una actualización |
| RNF08 Concurrencia | ☑ | `FOR UPDATE` del emprendimiento; RF-45 debe leerlo con `FOR SHARE` (Open Questions) |
| RNF13 Arquitectura | ☑ | Solo el puerto `in` de `pedidos` |
| RNF15 Fiabilidad | ☑ | Verificación y cambio en una transacción |
| RNF23 Consistencia | ☑ | `ACTIVO → ELIMINADO` único; repetirlo da `INVALID_STATE` |
| RNF27 Idempotencia | ☑ | `Idempotency-Key` obligatoria |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Emprendimiento | Lee / escribe (`estado`) | `ACTIVO → ELIMINADO`; la fila no se borra |
| Pedido | Lee (puerto) | Pendiente: `PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER`, `EN_CAMINO` |
| Producto, Calificacion | No se tocan | Se conservan; el catálogo queda oculto por el estado del emprendimiento |

## State Transitions

| Entidad | Desde | Hacia | Acción | Condición |
|---|---|---|---|---|
| Emprendimiento | `ACTIVO` | `ELIMINADO` | `POST /emprendimientos/me/eliminar` | Sin pedidos pendientes |

`ELIMINADO` es terminal. Sin emprendimiento `ACTIVO` la ruta responde `404`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `POST /emprendimientos/me/eliminar` | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | Su emprendimiento `ACTIVO` |

## API Contracts

### POST /api/v1/emprendimientos/me/eliminar

- **Rol**: vendedor · **Path params**: ninguno · **Query params**: ninguno
- **Body**: `{ "confirmar": true }`

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
| 403 | `FORBIDDEN` | Rol distinto de vendedor | — |
| 404 | `NOT_FOUND` | No tiene un emprendimiento `ACTIVO` | "Aún no tienes un emprendimiento activo" |
| 409 | `HAS_PENDING_ORDERS` | Hay pedidos pendientes | "Resuelve tus pedidos pendientes antes de eliminar tu emprendimiento"; `details.pedidosPendientes` |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Eliminar el emprendimiento no tiene reacciones |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: es una acción destructiva y no se encola: la app exige conexión. Al recibir `204`, limpia el emprendimiento y su catálogo del SQLite y lleva al vendedor a crear uno nuevo.
- **Concurrencia (RNF08)**: bloqueo `FOR UPDATE` del emprendimiento antes de contar los pedidos pendientes; RF-45 debe leerlo con `FOR SHARE` al crear un pedido para que uno nuevo no se cuele. Orden de bloqueo: emprendimiento → pedido.
- **Idempotencia (RNF27)**: la misma clave devuelve el mismo `204`; con otra clave y el emprendimiento ya eliminado llega `404`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedidos pendientes, reservas incluidas | `409 HAS_PENDING_ORDERS` con la cantidad | US1-E2 |
| Sin `confirmar: true` | `400` | FR-002 |
| Sin emprendimiento activo | `404` | RF-29 |
| Cliente con el catálogo abierto | Recibe `404` al refrescar (RF-33) | FR-003 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Eliminación sin pendientes; con pedido en cada estado pendiente (incluidas reservas); con pedidos terminados o rechazados (se permite); repetición; sin `confirmar` | FR-001, FR-002, US1-E1–E2 |
| Integración | Después de eliminar: RF-31, RF-32 y RF-33 no lo muestran, el vendedor crea otro, RF-29 responde `404` | FR-003, SC-002, Edge 3 |
| Integración | Un pedido con domicilio en curso siempre cuenta como pendiente | Edge 1 |
| Integración | El historial de pedidos y las calificaciones siguen intactos | Edge 2 |
| Concurrencia | Eliminar y crear un pedido a la vez | SC-001 |
| Frontend | Jest: confirmación, aviso de pendientes, limpieza local | FR-002 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-30-eliminacion-de-emprendimiento/
├── RF-30-eliminacion-de-emprendimiento.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/emprendimientos/
├── application/
│   ├── ports/in/                 # EliminarEmprendimientoPort
│   ├── ports/out/                # EmprendimientoRepositoryPort (con bloqueo), PedidosPendientesPort (RF-29)
│   └── usecase/                  # EliminarEmprendimientoUseCase
└── infrastructure/adapters/
    └── in/web/                   # EmprendimientoVendedorController (POST /emprendimientos/me/eliminar)

backend/src/test/java/co/edu/napa/emprendimientos/

frontend/src/components/emprendimientos/ConfirmarEliminarEmprendimiento.tsx
frontend/src/services/emprendimientos.ts                          # eliminarEmprendimiento()
frontend/src/app/(vendedor)/emprendimiento/editar.tsx             # (RF-29) acción "Eliminar emprendimiento"
frontend/__tests__/emprendimientos/eliminar-emprendimiento.test.tsx
```

**Structure Decision**: módulo `emprendimientos`, dueño de `Emprendimiento`, con la acción como `POST …/eliminar` sobre `/me` (como RF-29). Reutiliza el puerto de pedidos pendientes de RF-11 y el código `HAS_PENDING_ORDERS` de RF-58. El borrado es lógico y no recorre los productos: el catálogo queda oculto porque RF-33 exige un emprendimiento `ACTIVO`.

## Phase 1: Setup

- [ ] T001 Confirmar el estado `ELIMINADO` y el índice único parcial de RF-28 (T002)

## Phase 2: Foundational

- [ ] T002 `PedidosPendientesPort` (si RF-29 T005 aún no existe) sobre `ConsultarPedidosPendientesDeUsuarioPort` de `pedidos` (RF-11 T002)
- [ ] T003 Consulta con bloqueo `FOR UPDATE` del emprendimiento `ACTIVO` del vendedor en `EmprendimientoRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Retirar mi emprendimiento (Priority: P3)

**Goal**: el vendedor cierra su operación sin dejar pedidos colgados.
**Independent Test**: eliminar un emprendimiento sin pedidos pendientes y verlo desaparecer del listado público.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `POST /emprendimientos/me/eliminar` sin pendientes (`204`, estado `ELIMINADO`, repetición con la misma clave) en `backend/src/test/java/co/edu/napa/emprendimientos/EliminarEmprendimientoIT.java`
- [ ] T011 [P] [US1] Test de rechazo con pedido en cada estado pendiente (incluida una reserva) y de que los pedidos terminados o rechazados no bloquean, en `.../emprendimientos/EliminarEmprendimientoPendientesIT.java`
- [ ] T012 [P] [US1] Test de que sin `confirmar: true` no se elimina (`400`) en `.../emprendimientos/EliminarEmprendimientoConfirmacionIT.java`
- [ ] T013 [P] [US1] Test de que RF-31, RF-32 y RF-33 dejan de mostrarlo de inmediato (< 3 s) en `.../emprendimientos/EliminarEmprendimientoVisibilidadIT.java`
- [ ] T014 [P] [US1] Test de un pedido con domicilio en curso (cuenta como pendiente) en `.../emprendimientos/EliminarEmprendimientoDomicilioIT.java`
- [ ] T015 [P] [US1] Test de que pedidos, calificaciones y productos se conservan en `.../emprendimientos/EliminarEmprendimientoHistorialIT.java`
- [ ] T016 [P] [US1] Test de crear un emprendimiento nuevo justo después de eliminar y de rol distinto, sin sesión y sin emprendimiento (`404`), en `.../emprendimientos/EliminarEmprendimientoNuevoIT.java`
- [ ] T017 [P] [US1] Test de eliminar y crear un pedido a la vez en `.../emprendimientos/EliminarEmprendimientoConcurrenciaIT.java`
- [ ] T030 [P] [US1] Test de `ConfirmarEliminarEmprendimiento` (confirmar, cancelar, aviso de pendientes, limpieza local) en `frontend/__tests__/emprendimientos/eliminar-emprendimiento.test.tsx`

### Implementation

- [ ] T020 [US1] `EliminarEmprendimientoPort` y `EliminarEmprendimientoUseCase`: bloquea, consulta pendientes y cambia el estado en una transacción
- [ ] T021 [US1] `POST /emprendimientos/me/eliminar` y su DTO en `EmprendimientoVendedorController`
- [ ] T022 [US1] Error `HAS_PENDING_ORDERS` con `details.pedidosPendientes`
- [ ] T031 [US1] `ConfirmarEliminarEmprendimiento` y `eliminarEmprendimiento()` con limpieza de SQLite en `frontend/src/`
- [ ] T032 [US1] Acción "Eliminar emprendimiento" en la pantalla de edición (RF-29)

**Checkpoint**: el emprendimiento se elimina o se explica por qué no.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Confirmar el orden de bloqueo emprendimiento → pedido con RF-45 y RF-58
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-28, RF-29, RF-11 (T002, puerto de pedidos pendientes), RF-45 y RF-10; habilita que RF-11 permita eliminar la cuenta de un vendedor.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| RF-45 debe leer el emprendimiento con bloqueo compartido (`FOR SHARE`) al crear el pedido, igual que con los productos (RF-58) | Concurrencia | Tarea nueva en RF-45; si no, queda una ventana mínima | Equipo |
| ¿Los productos pasan a `ELIMINADO` junto con el emprendimiento? | Regla | No: quedan ocultos por el estado del emprendimiento y se conservan para soporte; un emprendimiento nuevo empieza con el catálogo vacío | Equipo |
| `confirmar: true` en el cuerpo (la spec pide confirmación explícita en la interfaz) | Contrato | Se exige también en el servidor, igual que RF-58 y RF-11 | Equipo |
| La ruta no está en spec-general (`POST /emprendimientos/me/eliminar`) | Contrato | Se agrega a spec-general | Equipo |
| Un domicilio entregado con la tarifa sin pagar no bloquea eliminar el emprendimiento (sí bloquea eliminar la cuenta, RF-11) | Regla | El vendedor sigue pudiendo pagar la tarifa, porque RF-43 usa `domicilio.vendedor_id` | Equipo |
| Edge 1: la spec pregunta por domicilios en curso sin pedidos pendientes | Regla | No existe ese caso | Equipo |

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
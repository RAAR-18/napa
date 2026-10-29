# Implementation Plan: Configuración de preferencias de notificación

**Date**: 2026-10-27
**Spec**: [RF-39-configuracion-de-preferencias-de-notificacion.md](./RF-39-configuracion-de-preferencias-de-notificacion.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-39
**Historias de usuario**: HU-64
**Prioridad (la mayor de la spec)**: P3
**Depende de**: RF-38, RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Cada usuario decide qué tipos de notificación recibe. `GET /notificaciones/preferencias` lista los tipos que le corresponden según su rol (por ejemplo, el domiciliario ve "Nuevos domicilios disponibles" y el cliente "Tu pedido fue aceptado"), cada uno con su estado `habilitada`. `PUT /notificaciones/preferencias/{tipo}` con `{ "habilitada": false }` lo desactiva y con `true` lo reactiva. Si no hay preferencia guardada, el tipo está habilitado. Cuando un tipo está desactivado, el sistema deja de generar esa notificación para ese usuario: no se guarda en su bandeja ni se envía push. Las notificaciones que ya existían se conservan. No hay tipos que no se puedan silenciar. El cambio aplica a los eventos que se procesan desde ese momento.

## Estado actual del código

RF-38 ya creó el módulo `notificaciones`, `EnviarNotificacionUseCase` (T002) que consulta `PreferenciaRepositoryPort` antes de guardar y enviar, y los 18 tipos del catálogo de eventos; la tabla `PreferenciaNotificacion` (clave `(usuario, tipo)`) ya existe y sin filas todo está habilitado. Lo único abierto era qué hacer con una preferencia desactivada (RF-38: "se guarda sin push o no se genera"); este plan lo resuelve: no se genera. foundations ya cerró que no hay tipos no silenciables.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente), Vendedor, Domiciliario, Administrador | Configuran qué tipos de notificación reciben |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Un cambio aplica desde ese momento en el 100 % de los casos (SC-001): `EnviarNotificacionUseCase` consulta las preferencias en el momento de procesar cada evento y las filas se escriben con confirmación inmediata, sin caché.
- Modificar en < 1 minuto (SC-002): una pantalla con interruptores y guardado inmediato.
- Sin costo en el camino caliente: para un evento con muchos destinatarios (E08 llega a todos los domiciliarios) se hace una sola consulta de los desactivados entre los destinatarios, no una por persona.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Configurar qué tipos recibir | `GET` y `PUT /notificaciones/preferencias` | T010, T011, T020, T021 |
| FR-002 | Respetar las preferencias al generar notificaciones | `EnviarNotificacionUseCase` descarta los tipos desactivados sin guardar ni enviar push | T012, T013, T022 |
| FR-003 | Modificar en cualquier momento | `PUT` idempotente sobre el mismo tipo | T011 |
| SC-001 | El cambio aplica desde ese momento | Consulta en el momento de procesar el evento, sin caché | T012, T014 |
| SC-002 | Modificar en < 1 minuto | Interruptores con guardado inmediato | T030 |
| US1-E1 | Desactivar un tipo | `habilitada: false`; los demás tipos siguen llegando | T011, T012 |
| US1-E2 | Reactivar un tipo | `habilitada: true`; vuelve a generarse con los nuevos eventos | T011, T013 |
| Edge 1 | Tipo crítico desactivado | Se respeta igual: no hay tipos no silenciables | T012 |
| Edge 2 | Preferencias al cambiar de rol o de dispositivo | Se guardan por usuario: persisten; solo se listan los tipos del rol actual | T015 |
| Edge 3 | Notificaciones ya generadas antes de desactivar | Se conservan en la bandeja (RF-38) | T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Cada usuario solo ve y cambia las suyas (`/preferencias` sin id de usuario) |
| RNF07 Rendimiento ≤ 3 s | ☑ | Filas por clave primaria; consulta de desactivados en lote |
| RNF13 Arquitectura | ☑ | El módulo `notificaciones` es dueño de la preferencia |
| RNF23 Consistencia | ☑ | Upsert por `(usuario, tipo)` |
| RNF24 Errores claros | ☑ | `404` para un tipo que no corresponde al rol |
| RNF28 Conectividad | ☑ | La app guarda las preferencias en SQLite y encola los cambios sin red |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| PreferenciaNotificacion | Lee / escribe | Clave `(usuario, tipo)`; sin fila = habilitada; solo se escribe al cambiar |
| Notificacion | No se toca | Las existentes se conservan; las nuevas se omiten si el tipo está desactivado |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte. La preferencia solo tiene el valor `habilitada` (sí/no).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /notificaciones/preferencias` | Todos los autenticados | Solo las del usuario del JWT; solo los tipos de su rol |
| `PUT /notificaciones/preferencias/{tipo}` | Todos los autenticados | Igual; un tipo que no corresponde a su rol responde `404` |

## API Contracts

### GET /api/v1/notificaciones/preferencias

- **Rol**: autenticado · **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body:

```json
{
  "content": [
    { "tipo": "DOMICILIO_DISPONIBLE", "titulo": "Nuevos domicilios disponibles", "habilitada": true },
    { "tipo": "PAGO_TARIFA_REGISTRADO", "titulo": "Te registraron el pago de una tarifa", "habilitada": false }
  ]
}
```

La lista no es paginada: son pocos tipos (los de su rol). Orden fijo por tipo.

### PUT /api/v1/notificaciones/preferencias/{tipo}

- **Rol**: autenticado · **Path params**: `tipo` (valor de `TipoNotificacion`) · **Body**: `{ "habilitada": false }`

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Content-Type | ✅ | `application/json` |

`PUT` fija el valor y no exige `Idempotency-Key`: repetirlo deja el mismo resultado (spec-general no lo pide para configuración).

- **Respuestas exitosas**: `200` con `{ "tipo", "titulo", "habilitada" }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `habilitada` ausente o no booleana | `details.campo` |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 404 | `NOT_FOUND` | El tipo no existe o no corresponde al rol del usuario | "Ese tipo de notificación no está disponible para tu cuenta" |

**Tipos por rol** (los de RF-38): CLIENTE: `PEDIDO_ACEPTADO`, `PEDIDO_RECHAZADO`, `PAGO_PEDIDO_CONFIRMADO`, `ENTREGA_DIRECTA_INICIADA`, `PEDIDO_LISTO_PARA_RECOGER`, `PEDIDO_ENTREGADO`, `DOMICILIO_ASIGNADO`, `DOMICILIO_EN_CAMINO`, `DOMICILIO_ENTREGADO_CLIENTE`, `DOMICILIO_CANCELADO`, `REPORTE_RESUELTO`, `COMENTARIO_MODERADO`. VENDEDOR_AMBULANTE: `NUEVO_PEDIDO`, `REPORTE_RESUELTO`, `COMENTARIO_MODERADO`. VENDEDOR_PUNTO_FIJO: los del ambulante más `DOMICILIO_ASIGNADO`, `DOMICILIO_RECOGIDO`, `DOMICILIO_ENTREGADO_VENDEDOR`, `DOMICILIO_CANCELADO`. DOMICILIARIO: `DOMICILIO_DISPONIBLE`, `PAGO_TARIFA_REGISTRADO`, `DOMICILIO_CANCELADO`, `REPORTE_RESUELTO`, `COMENTARIO_MODERADO`. ADMINISTRADOR: `REPORTE_CREADO`.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Cambiar una preferencia no tiene reacciones |

**Eventos que consume esta feature**: ninguno. La preferencia la usa `EnviarNotificacionUseCase` de RF-38 al procesar cualquier evento.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda las preferencias en SQLite y muestra el cambio al instante; si no hay red, el `PUT` queda en la outbox y se envía al volver (es idempotente; gana el último valor por tipo).
- **Concurrencia (RNF08)**: dos dispositivos cambiando el mismo tipo: gana la última escritura. Un evento que se procesa mientras el usuario cambia el interruptor usa el valor vigente en ese instante.
- **Idempotencia (RNF27)**: `PUT` es idempotente por naturaleza.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Tipo que no corresponde al rol | `404` | RNF24 |
| Tipo desconocido | `404` | RNF24 |
| Evento con el tipo desactivado | No se guarda ni se envía | FR-002 |
| Usuario con cuenta `ELIMINADA` | No recibe nada (RF-38) | RF-11 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | `GET` por cada rol (tipos correctos y por defecto habilitados); `PUT` desactivar, reactivar, repetir; tipo ajeno al rol y desconocido; cuerpo inválido; sin sesión | FR-001, FR-003, US1-E1–E2 |
| Integración | Con un tipo desactivado no se crea la notificación ni se llama al push, y los demás tipos sí llegan | FR-002, SC-001, Edge 1 |
| Integración | Reactivado, el siguiente evento sí genera la notificación | US1-E2 |
| Integración | Evento con muchos destinatarios (E08): solo se omiten los que lo desactivaron, con una consulta | SC-001 |
| Integración | Las preferencias persisten si el usuario cambia de rol (RF-67) y se listan los tipos del nuevo rol | Edge 2 |
| Integración | Las notificaciones anteriores a desactivar siguen en la bandeja | Edge 3 |
| Frontend | Jest: interruptores, cambio inmediato, cola sin red, caché | SC-002 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-39-configuracion-de-preferencias-de-notificacion/
├── RF-39-configuracion-de-preferencias-de-notificacion.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/notificaciones/
├── domain/model/                 # TipoNotificacion (con el catálogo de roles que lo reciben y su título), PreferenciaNotificacion
├── application/
│   ├── ports/in/                 # ListarPreferenciasPort, ActualizarPreferenciaPort
│   ├── ports/out/                # PreferenciaRepositoryPort (listar, upsert, desactivadosEntre(usuarioIds, tipo))
│   └── usecase/                  # ListarPreferenciasUseCase, ActualizarPreferenciaUseCase; EnviarNotificacionUseCase (RF-38, usa el puerto)
└── infrastructure/adapters/
    ├── in/web/                   # PreferenciaController (/notificaciones/preferencias)
    └── out/persistence/          # PreferenciaJpaEntity y su repositorio

backend/src/test/java/co/edu/napa/notificaciones/

frontend/src/app/(comun)/notificaciones/preferencias.tsx           # lista de interruptores
frontend/src/components/notificaciones/FilaPreferencia.tsx
frontend/src/services/preferencias.ts                              # obtener y cambiar, con la outbox
frontend/src/database/schema/preferencias_cache.ts
frontend/__tests__/notificaciones/preferencias.test.tsx
```

**Structure Decision**: módulo `notificaciones`, dueño de `PreferenciaNotificacion`. El catálogo de tipos y los roles que los reciben viven en el enum `TipoNotificacion`, para que RF-38 (quién recibe qué) y RF-39 (qué se puede configurar) no se desfasen. Descartar la notificación desactivada, y no solo el push, es lo que dice la spec ("deja de generarse") y evita una bandeja llena de avisos que el usuario no quiere.

## Phase 1: Setup

- [ ] T001 Confirmar la tabla `PreferenciaNotificacion` y `PreferenciaRepositoryPort` de RF-38 (T001, T002)

## Phase 2: Foundational

- [ ] T002 `TipoNotificacion` con el título de cada tipo y los roles que lo reciben (los 18 de RF-38) en `notificaciones/domain/model/`
- [ ] T003 Métodos `listar`, `guardar` (upsert) y `desactivadosEntre(usuarioIds, tipo)` en `PreferenciaRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Decidir qué notificaciones recibir (Priority: P3)

**Goal**: cada usuario silencia o reactiva los tipos que quiera.
**Independent Test**: desactivar un tipo, provocar su evento y comprobar que no llega, mientras los otros tipos sí.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /notificaciones/preferencias` por cada rol (tipos y valor por defecto) en `backend/src/test/java/co/edu/napa/notificaciones/ListarPreferenciasIT.java`
- [ ] T011 [P] [US1] Test de `PUT` (desactivar, reactivar, repetir, tipo ajeno al rol, desconocido, cuerpo inválido, sin sesión) en `.../notificaciones/ActualizarPreferenciaIT.java`
- [ ] T012 [P] [US1] Test de que con el tipo desactivado no se crea la notificación ni se envía push, y los demás tipos llegan, incluido un tipo "crítico" como `DOMICILIO_CANCELADO`, en `.../notificaciones/PreferenciaRespetadaIT.java`
- [ ] T013 [P] [US1] Test de reactivación: el siguiente evento sí genera la notificación en `.../notificaciones/PreferenciaReactivadaIT.java`
- [ ] T014 [P] [US1] Test de un evento con muchos destinatarios (E08) con una sola consulta de desactivados y de que un cambio se aplica al siguiente evento procesado en `.../notificaciones/PreferenciaLoteIT.java`
- [ ] T015 [P] [US1] Test de persistencia al cambiar de rol y de dispositivo en `.../notificaciones/PreferenciaRolIT.java`
- [ ] T016 [P] [US1] Test de que las notificaciones anteriores se conservan al desactivar el tipo en `.../notificaciones/PreferenciaHistorialIT.java`
- [ ] T030 [P] [US1] Test de la pantalla de preferencias (interruptores, cambio inmediato, cola sin red) en `frontend/__tests__/notificaciones/preferencias.test.tsx`

### Implementation

- [ ] T020 [US1] `ListarPreferenciasUseCase` y `ListarPreferenciasPort`: tipos del rol con su valor o el predeterminado
- [ ] T021 [US1] `ActualizarPreferenciaUseCase` y `PUT /notificaciones/preferencias/{tipo}` en `PreferenciaController`
- [ ] T022 [US1] En `EnviarNotificacionUseCase` (RF-38), excluir a los destinatarios con el tipo desactivado (consulta en lote) antes de guardar y enviar
- [ ] T031 [US1] `preferencias.ts` con caché SQLite y cola de cambios en la outbox
- [ ] T032 [US1] Pantalla `notificaciones/preferencias.tsx` con `FilaPreferencia`, enlazada desde los ajustes y la bandeja

**Checkpoint**: cada usuario controla sus notificaciones.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` de `desactivadosEntre` con 10 000 destinatarios
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-38 (módulo, tipos y `EnviarNotificacionUseCase`) y RF-10; los tipos por rol dependen del catálogo de eventos E01–E18.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| RF-38 dejó abierto si una preferencia desactivada "se guarda sin push o no se genera" | Regla | **No se genera**: ni fila en la bandeja ni push (lo pide FR-002 y la spec). Hay que cerrar esa pregunta en el plan de RF-38 | Equipo |
| Las rutas no están en spec-general (`GET /notificaciones/preferencias`, `PUT /notificaciones/preferencias/{tipo}`) | Contrato | Se agregan a spec-general | Equipo |
| Un cambio aplica "a partir de ese momento": un evento ya publicado y aún sin procesar usa la preferencia vigente al procesarse (segundos de diferencia) | Regla | Aceptado | Equipo |
| Hay 18 tipos y se muestran uno por uno (sin agruparlos) | UX | Así, con los títulos del enum; agrupar es cosa de la app | Equipo |
| Un tipo desactivado que el rol deja de recibir (cambio de rol) | Datos | La fila se conserva y no se muestra; si el rol vuelve, reaparece | Equipo |
| Silenciar todo de una vez no está en la spec | Alcance | No se ofrece | Equipo |

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
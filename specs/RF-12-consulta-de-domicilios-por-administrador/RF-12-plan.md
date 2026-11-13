# Implementation Plan: Consulta de domicilios por administrador

**Date**: 2026-10-27
**Spec**: [RF-12-consulta-de-domicilios-por-administrador.md](./RF-12-consulta-de-domicilios-por-administrador.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-12
**Historias de usuario**: HU-51
**Prioridad (la mayor de la spec)**: P3
**Depende de**: RF-14, RF-15, RF-18, RF-26, RF-43, RF-10
**Habilita a**: RF-13 (cancelación desde el detalle)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El administrador supervisa todos los domicilios de la plataforma. `GET /domicilios` (rama del administrador) devuelve, paginado, cada domicilio con su estado, origen, destino, tarifa, vendedor, cliente y domiciliario asignado, y se filtra por estado, fechas, vendedor y domiciliario. `GET /domicilios/{id}` (rama del administrador) devuelve el detalle completo: lo mismo más el pedido asociado, la cronología de estados con sus autores, el motivo de cancelación si existe y el estado del pago de la tarifa. Un domicilio que ningún domiciliario ha tomado muestra cuánto lleva esperando. El código de confirmación del cliente nunca se muestra, ni siquiera al administrador.

## Estado actual del código

RF-15 ya dejó `GET /domicilios` para el vendedor de punto fijo y RF-18 `GET /domicilios/{id}` para el domiciliario; RF-26 guarda la cronología en `evento_trazabilidad`. spec-general ya prevé para esta ruta los filtros `vendedorId` y `domiciliarioId` del administrador. Este plan agrega la rama del administrador en las dos rutas, cada una con su propio caso de uso (el mismo patrón de RF-06 y RF-03 en `GET /calificaciones`). El domicilio tiene `vendedor_id` (decidido en RF-14), por lo que el filtro por vendedor no cruza módulos.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador, Vendedor, Domiciliario | Son los involucrados; no intervienen |
| Administrador | Lista y consulta el detalle de cualquier domicilio |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Listado en < 3 s (SC-001): consulta paginada con los índices `(estado, created_at)` (RF-16), `(vendedor_id, created_at)` (RF-14) y `(domiciliario_id, created_at)`; nombres, destino y tarifa se piden en lote para la página (máximo 100 filas), sin una consulta por fila.
- 100 % de los domicilios visibles (SC-002): sin filtro implícito de rol ni de estado, incluidos los `CANCELADO` y los `ENTREGADO`.
- Secreto del código: el DTO del administrador no tiene el campo `codigoConfirmacion`, y un test lo verifica.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Listar todos los domicilios sin importar vendedor o domiciliario | `GET /domicilios` rama ADMINISTRADOR | T010, T011, T020, T021 |
| FR-002 | Mostrar estado, origen, destino y tarifa | Campos de cada fila, con destino y tarifa tomados del pedido en lote | T010, T022 |
| FR-003 | Acceder al detalle completo desde el listado | `GET /domicilios/{id}` rama ADMINISTRADOR | T012, T023, T024 |
| SC-001 | Listado en < 3 s | Índices, paginación y lecturas en lote | T014 |
| SC-002 | 100 % de domicilios visibles | Sin filtros implícitos | T010, T011 |
| US1-E1 | Listado general con su información básica | `200` con `content` paginado | T010 |
| US1-E2 | Detalle de un domicilio | `200` con el detalle completo | T012 |
| Edge 1 | No hay domicilios | `200` con lista vacía y mensaje en la app | T013 |
| Edge 2 | Domicilio cancelado o que nadie ha tomado | Se muestra con su estado y `motivoCancelacion`; el `DISPONIBLE` lleva `minutosSinTomar` y la app lo resalta pasado un umbral (Open Questions) | T015, T016 |
| Edge 3 | Domicilio "eliminado" | No existe borrado de domicilios: un id inexistente da `404` | T017 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo ADMINISTRADOR en esta rama |
| RNF04 Datos personales | ☑ | `no-store`; solo nombres e ids, y sin el código de confirmación |
| RNF07 Rendimiento ≤ 3 s | ☑ | Índices, paginación y lotes |
| RNF13 Arquitectura | ☑ | `domicilios` pide pedido y nombres por puertos `in` de `pedidos` y `cuentas` |
| RNF16 Trazabilidad | ☑ | La cronología se lee de `evento_trazabilidad` |
| RNF24 Errores claros | ☑ | `400` ante filtros inválidos |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Lee | Todos los estados; nunca `codigo_confirmacion` |
| EventoTrazabilidad | Lee | Cronología ordenada por fecha |
| Ubicacion | Lee | Origen (punto A) del domicilio |
| Pedido | Lee (puerto) | Destino, tarifa, modalidad y cliente |
| Pago | Lee (puerto) | Estado del pago de la tarifa, si existe |
| Usuario | Lee (puerto) | Solo nombres |

## State Transitions

Esta feature es de solo lectura: no cambia estados.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /domicilios` | ADMINISTRADOR (rama de esta feature) | Todos los domicilios; las otras ramas son RF-15 y RF-16 |
| `GET /domicilios/{id}` | ADMINISTRADOR (rama de esta feature) | Cualquier domicilio; la rama del domiciliario es RF-18 |

## API Contracts

### GET /api/v1/domicilios (rama del administrador)

- **Rol**: administrador · **Path params**: ninguno
- **Query params**:

| Parámetro | Tipo | Obligatorio | Ejemplo | Descripción |
|---|---|:---:|---|---|
| estado | enum | ☐ | `DISPONIBLE` | `DISPONIBLE`, `ASIGNADO`, `RECOGIDO`, `EN_CAMINO`, `ENTREGADO` o `CANCELADO` |
| desde, hasta | fecha ISO-8601 | ☐ | `2026-10-01` | Rango sobre la fecha de creación |
| vendedorId | UUID | ☐ | `…` | Solo domicilios de ese vendedor |
| domiciliarioId | UUID | ☐ | `…` | Solo domicilios de ese domiciliario |
| page, size, sort | int, int, string | ☐ | `0`, `20`, `createdAt,desc` | `sort` admite `createdAt` y `updatedAt`; por defecto `createdAt,desc` |

- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "content": [
    {
      "id": "…", "pedidoId": "…", "estado": "DISPONIBLE", "tarifa": 5000, "minutosSinTomar": 42,
      "origen": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Carrera 5 # 10-20" },
      "destino": { "latitud": 11.2501, "longitud": -74.2002, "direccion": "Calle 15 # 3-20" },
      "vendedor": { "id": "…", "nombre": "Frutas Doña Rosa" }, "cliente": { "id": "…", "nombre": "Ana Pérez" },
      "domiciliario": null, "createdAt": "…", "updatedAt": "…"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1
}
```

`minutosSinTomar` solo viene en los `DISPONIBLE` (en el resto es `null`); `domiciliario` es `null` hasta que alguien lo acepta.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Filtro desconocido, `estado` o UUID inválidos, `desde` mayor que `hasta`, `size` mayor que 100, `sort` inválido | Indica el parámetro |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol sin rama en esta ruta | — |

### GET /api/v1/domicilios/{id} (rama del administrador)

- **Rol**: administrador · **Path params**: `id` (UUID) · **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body: todos los campos de la fila anterior más:

```json
{
  "pedido": { "id": "…", "modalidad": "DOMICILIO", "estado": "ACEPTADO", "total": 14500, "cantidadItems": 3 },
  "motivoCancelacion": null,
  "pagoTarifa": { "estado": "REGISTRADO", "monto": 5000 },
  "trazabilidad": [ { "estado": "DISPONIBLE", "ocurridoAt": "…", "autor": { "id": "…", "nombre": "Frutas Doña Rosa" } } ]
}
```

`pagoTarifa` es `null` mientras no exista el pago. Nunca se incluye `codigoConfirmacion`.

- **Errores**: `401 UNAUTHORIZED`, `403 FORBIDDEN` (rol sin rama), `404 NOT_FOUND` ("El domicilio no existe"), `400 VALIDATION_ERROR` (id mal formado).

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el panel del administrador exige conexión y no guarda datos de terceros en SQLite; sin red muestra el error con reintento.
- **Concurrencia (RNF08)**: no aplica a la lectura; si un domicilio cambia de estado mientras se mira, el refresco lo actualiza. La lista se actualiza al volver a primer plano.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Sin domicilios | `200` con lista vacía | Edge 1 |
| Domicilio cancelado | Estado `CANCELADO` con su motivo | Edge 2 |
| Domicilio sin tomar | `minutosSinTomar` para resaltarlo | Edge 2 |
| Id inexistente | `404` | Edge 3 |
| Filtro inválido | `400` | RNF24 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | `GET /domicilios`: todos los estados y vendedores, filtros (estado, fechas, vendedor, domiciliario, combinados), paginación y orden, lista vacía, nombres, destino y tarifa en lote | FR-001, FR-002, US1-E1, Edge 1 |
| Integración | `GET /domicilios/{id}`: detalle completo, cancelado con motivo, `DISPONIBLE` con `minutosSinTomar`, con y sin pago de tarifa, inexistente | FR-003, US1-E2, Edge 2–3 |
| Contrato | Ningún campo `codigoConfirmacion` en listado ni detalle | RNF04 |
| Integración | Las ramas de vendedor (RF-15), domiciliario (RF-16, RF-18) y administrador conviven en la misma ruta sin pisarse | FR-001 |
| Rendimiento | 50 000 domicilios: primera página y filtros en < 3 s | SC-001 |
| Frontend | Jest: filtros, resalte de "sin tomar", detalle con cronología, vacío y error | US1-E1–E2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-12-consulta-de-domicilios-por-administrador/
├── RF-12-consulta-de-domicilios-por-administrador.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── application/
│   ├── ports/in/                 # ListarDomiciliosAdminPort, ConsultarDomicilioAdminPort
│   ├── ports/out/                # DomicilioRepositoryPort (consulta global con filtros), EventoTrazabilidadRepositoryPort, PedidoParaDomicilioPort (lote), PagoTarifaLecturaPort, NombresUsuariosPort
│   └── usecase/                  # ListarDomiciliosAdminUseCase, ConsultarDomicilioAdminUseCase
└── infrastructure/adapters/
    └── in/web/                   # DomicilioController: despacha por rol a cada caso de uso; DomicilioAdminResponse (sin código)

backend/src/main/java/co/edu/napa/cuentas/application/ports/in/   # ConsultarNombresUsuariosPort (ids → nombres, en lote; compartido con RF-41)
backend/src/main/resources/db/migration/                          # índice (domiciliario_id, created_at) si falta
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/app/(administrador)/domicilios/index.tsx             # listado con filtros
frontend/src/app/(administrador)/domicilios/[id].tsx              # detalle y cronología
frontend/src/components/domicilios/FilaDomicilioAdmin.tsx         # incluye el resalte "sin tomar hace N min"
frontend/src/components/domicilios/FiltrosDomiciliosAdmin.tsx
frontend/src/services/domiciliosAdmin.ts
frontend/__tests__/domicilios/
```

**Structure Decision**: módulo `domicilios`, dueño de `Domicilio`. Se agrega una rama por rol en las mismas rutas (como `GET /calificaciones`), cada una con su caso de uso, para que las reglas de visibilidad de cada rol no se mezclen. Destino, tarifa y cliente salen de `pedidos` en lote y los nombres de `cuentas` en lote; así no hay joins entre módulos ni consultas por fila.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `domicilios`, su regla ArchUnit y las ramas existentes de `GET /domicilios` (RF-15, RF-16) y `GET /domicilios/{id}` (RF-18)

## Phase 2: Foundational

- [ ] T002 Índice `(domiciliario_id, created_at)` sobre `domicilio` en la siguiente migración libre de `backend/src/main/resources/db/migration/` (si RF-19 no lo creó)
- [ ] T003 `ConsultarNombresUsuariosPort` en `cuentas` (ids a nombres, en lote) y `NombresUsuariosPort` en `domicilios`
- [ ] T004 Ampliar `PedidoParaDomicilioPort` (lote) con destino, tarifa, cliente, modalidad y total, y `PagoTarifaLecturaPort` sobre `pagos`
- [ ] T005 Método de consulta global con filtros en `DomicilioRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Supervisar los domicilios (Priority: P3)

**Goal**: el administrador ve qué pasa con cada domicilio.
**Independent Test**: entrar como administrador, listar con filtros y abrir el detalle de un domicilio.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /domicilios` como administrador (todos los estados y vendedores, campos de la fila) en `backend/src/test/java/co/edu/napa/domicilios/ListarDomiciliosAdminIT.java`
- [ ] T011 [P] [US1] Test de filtros por estado, fechas, vendedor y domiciliario, combinados, y de paginación y orden, en `.../domicilios/ListarDomiciliosAdminFiltrosIT.java`
- [ ] T012 [P] [US1] Test de `GET /domicilios/{id}` como administrador (detalle, pedido, cronología, pago de la tarifa) en `.../domicilios/ConsultarDomicilioAdminIT.java`
- [ ] T013 [P] [US1] Test de lista vacía y de filtros que no devuelven nada en `.../domicilios/ListarDomiciliosAdminVacioIT.java`
- [ ] T014 [P] [US1] Test de rendimiento con 50 000 domicilios en `.../domicilios/ListarDomiciliosAdminRendimientoIT.java`
- [ ] T015 [P] [US1] Test de domicilio `CANCELADO` con su motivo y de `ENTREGADO` en `.../domicilios/DomicilioAdminEstadosIT.java`
- [ ] T016 [P] [US1] Test de `minutosSinTomar` solo en `DISPONIBLE` en `.../domicilios/DomicilioAdminSinTomarIT.java`
- [ ] T017 [P] [US1] Test de id inexistente (`404`), filtros inválidos (`400`), de contrato sin `codigoConfirmacion`, y de rol sin rama (`403`), en `.../domicilios/DomicilioAdminContratoIT.java`
- [ ] T030 [P] [US1] Test del listado y del detalle de la app (filtros, resalte, cronología, vacío, error) en `frontend/__tests__/domicilios/domicilios-admin.test.tsx`

### Implementation

- [ ] T020 [US1] `ListarDomiciliosAdminPort` y `ListarDomiciliosAdminUseCase`: valida filtros y arma la página
- [ ] T021 [US1] Despacho por rol en `GET /domicilios` de `DomicilioController`
- [ ] T022 [US1] `DomicilioAdminResponse` (fila) con origen, destino, tarifa, nombres y `minutosSinTomar`
- [ ] T023 [US1] `ConsultarDomicilioAdminPort` y `ConsultarDomicilioAdminUseCase`
- [ ] T024 [US1] Despacho por rol en `GET /domicilios/{id}` y DTO de detalle con cronología y pago de la tarifa
- [ ] T031 [US1] `domiciliosAdmin.ts` y `FiltrosDomiciliosAdmin`
- [ ] T032 [US1] Pantallas `(administrador)/domicilios/index.tsx` y `[id].tsx` con `FilaDomicilioAdmin`

**Checkpoint**: el administrador supervisa los domicilios.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` del listado con filtros combinados
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-14 (domicilio y `vendedor_id`), RF-15 y RF-18 (las otras ramas de las rutas), RF-26 (cronología), RF-43 (pago de la tarifa) y RF-10; el detalle es el punto de partida de RF-13 (cancelar).
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Edge 2: ¿desde cuándo se considera "tiempo prolongado" sin tomar? | Regla | Resalte en la app a partir de 30 minutos (valor del equipo); el servidor solo entrega `minutosSinTomar` | Equipo |
| La spec dice "origen, destino, estado, tarifa" en la lista; se agregan vendedor, cliente y domiciliario porque Key Entities los incluye | Alcance | Nombres e ids, sin contacto | Equipo |
| El administrador no ve el código de confirmación | Seguridad | Nunca (restricción 9 de spec-general: solo el cliente) | Equipo |
| Edge 3: no hay domicilios eliminados | Modelo | `404` para un id inexistente | Equipo |
| La cronología muestra el autor de cada evento (spec-general: el domiciliario o el vendedor) | Privacidad | Solo para el administrador; RF-26 lo oculta a las partes | Equipo |
| Las rutas ya existen para otros roles | Contrato | Se agrega la rama del administrador sin rutas nuevas | Equipo |

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
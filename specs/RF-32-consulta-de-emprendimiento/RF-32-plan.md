# Implementation Plan: Consulta de emprendimiento

**Date**: 2026-10-21
**Spec**: [RF-32-consulta-de-emprendimiento.md](./RF-32-consulta-de-emprendimiento.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-32
**Historias de usuario**: HU-57
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-28, RF-31, RF-63 (`ConsultarMediosPagoPort` para `ofreceDomicilio`)
**Habilita a**: RF-33, RF-45 (modalidades disponibles), RF-48 (ubicación de retiro)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Detalle de un emprendimiento para el cliente: nombre, categoría, tipo de vendedor, descripción, ubicación, horario (con `abierto` calculado) y modalidades de entrega disponibles. Para un punto fijo se incluyen dirección, horario y un mapa (react-native-maps) que sirve también para retirar un pedido de recogida. `ofreceDomicilio` es verdadero solo si el vendedor de punto fijo tiene cuenta bancaria, información que `emprendimientos` obtiene del puerto `in` del módulo `pagos`. Un emprendimiento eliminado o inactivo responde que ya no está disponible.

## Estado actual del código

No existe el módulo `emprendimientos`. Este plan añade el caso de uso de detalle sobre la tabla de RF-28 y consume `ConsultarMediosPagoPort` de RF-63.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Consulta el detalle antes de comprar o para retirar |
| Vendedor (ambulante / punto fijo) | No interviene |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Detalle en < 3 s (SC-001): una consulta por id más una consulta al puerto de pagos.
- 100 % de los activos exponen su información completa (SC-002) y 100 % de los de punto fijo muestran dirección, horario y mapa (SC-003).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Consultar el detalle desde el listado | `GET /emprendimientos/{id}`; `ConsultarEmprendimientoUseCase` | T010, T013, T014 |
| FR-002 | Mostrar nombre, categoría, tipo de vendedor, ubicación, horario, descripción y modalidades | Respuesta con esos campos y `modalidadesEntrega` | T010, T015 |
| FR-003 | Informar si ya no está disponible | `404 NOT_FOUND` "El emprendimiento ya no está disponible" para eliminado, inactivo o inexistente | T011 |
| FR-004 | Punto fijo: dirección, horario y mapa | La respuesta incluye `ubicacion` completa y `horaApertura`/`horaCierre`; la app muestra `MapaPuntoFijo` | T012, T021 |
| FR-005 | Exponer `ofreceDomicilio` solo con cuenta bancaria | `ConsultarMediosPagoPort.domicilioDisponible` | T016, T017 |
| SC-001 | < 3 s | Consulta por clave primaria | T030 |
| SC-002 | 100 % con información completa | Prueba de integración | T010 |
| SC-003 | 100 % de punto fijo con dirección, horario y mapa | Prueba de contrato de la respuesta y de la pantalla | T012, T022 |
| US1-E1 | Detalle exitoso | Endpoint | T010 |
| US1-E2 | Ubicación de punto fijo para retirar | Misma respuesta (FR-004) | T012 |
| US1-E3 | Emprendimiento eliminado | `404` + pantalla "ya no está disponible" | T011, T023 |
| Edge 1 | Fuera de horario al consultar | `abierto: false` calculado con la hora local | T018 |
| Edge 2 | Sin descripción | `descripcion: null`; la app oculta la sección | T019 |
| Edge 3 | Ubicación no resoluble en el mapa | La app muestra la dirección y coordenadas como texto si el mapa falla | T024 |
| Edge 4 | Cambia la ubicación u horario con pedido de recogida aceptado | El detalle siempre devuelve la ubicación vigente (se lee de la tabla en cada consulta) | T010 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo cliente |
| RNF04 Privacidad | ☑ | Se expone ubicación solo del emprendimiento, no datos personales del vendedor |
| RNF07 Rendimiento | ☑ | Consulta por id |
| RNF19 Interoperabilidad | ☑ | Mapa tras un componente reemplazable |
| RNF22 Geografía | ☑ | Latitud y longitud derivadas de `ubicacion` |
| RNF24 Errores claros | ☑ | Mensaje "ya no está disponible" |
| RNF28 Conectividad | ☑ | Caché de lectura SQLite del detalle |
| RNF31 Gama baja | ☑ | El mapa se carga solo al abrir la sección de ubicación |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Emprendimiento | Lee | Solo `ACTIVO` es consultable |
| Ubicacion | Lee | 1:1 con el emprendimiento |
| Usuario | Lee (rol) | Tipo de vendedor y modalidades |
| ConfiguracionPago | Lee (vía puerto de `pagos`) | `ofreceDomicilio` |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Consultar el detalle | CLIENTE | Ninguna |

## API Contracts

### GET /api/v1/emprendimientos/{id}

- **Rol**: cliente
- **Path params**: `id` (UUID del emprendimiento) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body:

```json
{
  "id": "…", "nombre": "Frutas Doña Rosa", "categoria": "FRUTAS_VERDURAS", "tipoVendedor": "PUNTO_FIJO",
  "descripcion": null, "horaApertura": "06:00", "horaCierre": "18:00", "abierto": true,
  "ubicacion": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Calle 15 # 3-20", "referencia": "Frente al parque" },
  "modalidadesEntrega": ["RECOGIDA", "DOMICILIO"],
  "ofreceDomicilio": true
}
```

Modalidades: vendedor ambulante → `["ENTREGA_DIRECTA"]` y `ofreceDomicilio: false`; punto fijo → `["RECOGIDA"]` más `"DOMICILIO"` si `ofreceDomicilio`.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de cliente | — |
| 404 | `NOT_FOUND` | Inexistente, inactivo o eliminado | "El emprendimiento ya no está disponible" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda el último detalle visto en SQLite y lo muestra con la hora de actualización si no hay red; la ubicación de recogida de un pedido aceptado queda disponible. Al reconectar, refresca y, si responde `404`, informa que ya no está disponible.
- **Concurrencia (RNF08)**: no aplica (lectura); el detalle refleja siempre el estado vigente.
- **Idempotencia (RNF27)**: no aplica.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Emprendimiento eliminado o enlace guardado antiguo | `404` y pantalla "ya no está disponible" | US1-E3, FR-003 |
| Mapa no se resuelve | Se muestra la dirección y las coordenadas como texto | Edge 3 |
| `pagos` no responde | Se omite `ofreceDomicilio` como `false` y se registra (no se bloquea el detalle) | RNF15 |
| Sin conexión | Caché con hora de actualización | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Cálculo de modalidades según rol y `ofreceDomicilio` | FR-002, FR-005 |
| Integración | Endpoint con Testcontainers: ambulante, punto fijo con y sin cuenta, eliminado, ubicación vigente tras cambio | US1-E1–E3, Edge 4 |
| Contrato | Forma de la respuesta para punto fijo (dirección, horario, coordenadas) | FR-004, SC-003 |
| Frontend | Jest: sección de mapa solo en punto fijo, `404`, sin descripción, fallo de mapa | US1-E2, US1-E3, Edge 2–3 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-32-consulta-de-emprendimiento/
├── RF-32-consulta-de-emprendimiento.md   # spec
└── plan.md                               # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/emprendimientos/
├── domain/model/                 # ModalidadEntrega (ENTREGA_DIRECTA, RECOGIDA, DOMICILIO)
├── application/
│   ├── ports/in/                 # ConsultarEmprendimientoPort
│   ├── ports/out/                # EmprendimientoRepositoryPort, MediosPagoVendedorPort (llama al puerto in de `pagos`)
│   └── usecase/                  # ConsultarEmprendimientoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # EmprendimientoController (GET por id), DTO de detalle
    └── out/persistence/          # consulta por id con ubicación y rol del dueño
backend/src/test/java/co/edu/napa/emprendimientos/

frontend/src/app/(cliente)/emprendimientos/[id].tsx          # ruta Expo Router: detalle
frontend/src/components/emprendimiento/DetalleEmprendimiento.tsx
frontend/src/components/emprendimiento/MapaPuntoFijo.tsx     # react-native-maps; muestra dirección si falla
frontend/src/services/emprendimientos.ts                     # obtenerEmprendimiento()
frontend/src/database/schema/emprendimiento_detalle_cache.ts
frontend/__tests__/emprendimientos/detalle.test.tsx
```

**Structure Decision**: módulo `emprendimientos`. La información de pagos entra por `MediosPagoVendedorPort`, que llama al puerto `in` de `pagos` (RF-63). `MapaPuntoFijo` se reutiliza para mostrar el punto de retiro en el detalle de un pedido de recogida (RF-48/RF-51).

## Phase 1: Setup

- [ ] T001 Confirmar `ConsultarMediosPagoPort` (RF-63, T016) y la ruta de listado (RF-31)

## Phase 2: Foundational

- [ ] T002 Consulta por id con ubicación y rol del dueño en `EmprendimientoRepositoryPort` y su adaptador en `emprendimientos/application/ports/out/` y `.../out/persistence/`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Ver el detalle de un emprendimiento (Priority: P1)

**Goal**: el cliente conoce la oferta y el punto antes de comprar o retirar.
**Independent Test**: seleccionar un emprendimiento del listado y ver su información completa, sin depender de productos.
**Cubre**: FR-001–FR-005, SC-001–SC-003, US1-E1–E3, Edge 1–4.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración del detalle (campos completos, ubicación vigente tras editar) en `backend/src/test/java/co/edu/napa/emprendimientos/ConsultarEmprendimientoIT.java`
- [ ] T011 [P] [US1] Test de emprendimiento eliminado, inactivo e inexistente → `404` en `.../emprendimientos/ConsultarEmprendimientoNoDisponibleIT.java`
- [ ] T012 [P] [US1] Test de contrato del punto fijo (dirección, horario, coordenadas) en `.../emprendimientos/DetallePuntoFijoIT.java`
- [ ] T016 [P] [US1] Test de `ofreceDomicilio` y modalidades (ambulante, punto fijo con y sin cuenta) en `.../emprendimientos/ModalidadesEntregaIT.java`
- [ ] T018 [P] [US1] Test de `abierto` fuera de horario en el detalle en `.../emprendimientos/DetalleAbiertoIT.java`
- [ ] T019 [P] [US1] Test de emprendimiento sin descripción en `.../emprendimientos/DetalleSinDescripcionIT.java`
- [ ] T023 [P] [US1] Test de la pantalla (mapa solo en punto fijo, `404`, sin descripción, fallo de mapa, caché sin red) en `frontend/__tests__/emprendimientos/detalle.test.tsx`

### Implementation

- [ ] T013 [US1] `ConsultarEmprendimientoUseCase` en `emprendimientos/application/usecase/`
- [ ] T014 [US1] `GET /emprendimientos/{id}` en `EmprendimientoController`
- [ ] T015 [US1] DTO de detalle y cálculo de `modalidadesEntrega`
- [ ] T017 [US1] Adaptador `MediosPagoVendedorPort` hacia `pagos` en `.../out/` (con tolerancia a fallo)
- [ ] T020 [US1] Servicio `obtenerEmprendimiento()` con caché SQLite en `frontend/src/services/emprendimientos.ts`
- [ ] T021 [US1] `DetalleEmprendimiento` y `MapaPuntoFijo` en `frontend/src/components/emprendimiento/`
- [ ] T022 [US1] Pantalla `frontend/src/app/(cliente)/emprendimientos/[id].tsx`
- [ ] T024 [US1] Alternativa de texto cuando el mapa no se resuelve y pantalla "ya no está disponible" para `404`

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T030 Medir el detalle < 3 s (SC-001)
- [ ] T031 Revisar privacidad y permisos (RNF03, RNF04)
- [ ] T032 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-28, RF-31 y RF-63; RF-33, RF-45, RF-48 y RF-51 reutilizan esta información y `MapaPuntoFijo`.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Emprendimiento `INACTIVO`: ¿se informa "no disponible" igual que el eliminado? | FR-003 | Sí, `404` con el mismo mensaje | Equipo |
| `404` frente a otro código para "ya no disponible" (la convención no define `410`) | RNF24 | `404 NOT_FOUND` | Equipo |
| ¿Se muestra la ubicación del ambulante en el detalle? La spec pide mapa solo para punto fijo | FR-004 | Se muestra la zona habitual como texto, sin mapa | Equipo |
| ¿Qué hace el detalle si el módulo `pagos` falla? | RNF15 | `ofreceDomicilio = false` | Equipo |
| ¿Se muestran los medios de pago aceptados en el detalle? La spec no lo pide | UX | No | Equipo |

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
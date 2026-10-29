# Implementation Plan: Consulta de información del cliente

**Date**: 2026-10-27
**Spec**: [RF-52-consulta-de-informacion-del-cliente.md](./RF-52-consulta-de-informacion-del-cliente.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-52
**Historias de usuario**: HU-82
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-51, RF-45, RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El vendedor consulta los datos de quien le hizo un pedido con `GET /pedidos/{id}/cliente`: nombre, celular y, cuando aplica, la dirección de entrega. Solo funciona para un pedido de su emprendimiento (cualquier otro responde `404`, como RF-51). El celular y la dirección se entregan mientras el pedido está en curso; con el pedido terminado, rechazado o cancelado queda solo el nombre. La respuesta no incluye correo, foto, ubicación del perfil del cliente ni ningún dato que no sirva para gestionar el pedido. Si el cliente eliminó su cuenta, se muestra "Cliente eliminado" sin contacto.

## Estado actual del código

RF-51 ya devuelve `cliente { nombre }` dentro del detalle del pedido del vendedor y `ubicacionEntrega` (solo si el pedido está activo y no es recogida). Esta feature agrega el contacto en una ruta propia, para que el celular no viaje en cada lectura del detalle y quede un único lugar donde se decide qué dato del cliente se muestra.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Es la persona consultada; no interviene |
| Vendedor (ambulante / punto fijo) | Consulta los datos del cliente de un pedido de su emprendimiento |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Consulta en < 3 s (SC-001): una lectura del pedido (con su emprendimiento) y una del usuario.
- 100 % de consultas con pedido asociado y 0 % sin pedido (SC-002, SC-003): la pertenencia se valida en el servidor con el emprendimiento del pedido; no existe una consulta de cliente sin pedido.
- Mínimo necesario (FR-002): DTO de lista cerrada.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El vendedor consulta al cliente de un pedido de su emprendimiento | `GET /pedidos/{id}/cliente` | T010, T020, T021 |
| FR-002 | Mostrar solo los datos necesarios | DTO cerrado: nombre, celular y dirección de entrega cuando aplica | T011, T012, T022 |
| FR-003 | Impedir consultar a un cliente sin pedido asociado | Pertenencia por emprendimiento; `404` en otro caso | T013 |
| SC-001 | < 3 s | Dos lecturas indexadas | T014 |
| SC-002 | 100 % de consultas con pedido | No hay ruta por cliente, solo por pedido | T013 |
| SC-003 | 0 % de consultas sin pedido | Prueba con pedido de otro emprendimiento | T013 |
| US1-E1 | Consulta exitosa | `200` con nombre, celular y dirección | T010 |
| US1-E2 | Cliente sin pedido asociado | `404` "El pedido no está disponible" (Open Questions) | T013 |
| Edge 1 | El cliente actualizó sus datos después del pedido | Se muestran los datos actuales de su cuenta; la dirección es la del pedido | T015 |
| Edge 2 | El cliente eliminó su cuenta | `nombre: "Cliente eliminado"`, sin celular | T016 |
| Edge 3 | Datos que se ocultan por privacidad | Correo, foto, ubicación del perfil, estado de la cuenta y cualquier dato de otros pedidos | T012 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo vendedores con pedido de su emprendimiento |
| RNF04 Datos personales | ☑ | Lista cerrada de campos, `Cache-Control: no-store` y el celular solo con el pedido en curso |
| RNF07 Rendimiento ≤ 3 s | ☑ | Lecturas indexadas |
| RNF13 Arquitectura | ☑ | `pedidos` lee al usuario por un puerto `in` de `cuentas` |
| RNF24 Errores claros | ☑ | `404` con mensaje del pedido |
| RNF28 Conectividad | ☑ | La app no guarda el celular en SQLite; solo el nombre |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Pedido | Lee | Su emprendimiento debe ser del vendedor autenticado |
| Usuario (cliente) | Lee (puerto) | Solo nombre y celular; la cuenta puede estar `ELIMINADA` |
| Ubicacion | Lee | La de entrega del pedido, no la del perfil; no aplica en `RECOGIDA` |

## State Transitions

Esta feature es de solo lectura: no cambia estados. El pedido en `PENDIENTE`, `ACEPTADO`, `LISTO_PARA_RECOGER` o `EN_CAMINO` entrega nombre, celular y dirección; en `ENTREGADO`, `RECHAZADO` o `CANCELADO` entrega solo el nombre.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /pedidos/{id}/cliente` | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | El pedido es de su emprendimiento; otro caso responde `404` |

## API Contracts

### GET /api/v1/pedidos/{id}/cliente

- **Rol**: vendedor (ambulante o de punto fijo) · **Path params**: `id` (UUID del pedido) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "nombre": "Ana Pérez",
  "celular": "+573001234567",
  "direccionEntrega": { "latitud": 11.24, "longitud": -74.21, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" }
}
```

`celular` y `direccionEntrega` son `null` cuando el pedido ya no está en curso, cuando es de recogida (sin dirección) o cuando el cliente eliminó su cuenta; en ese último caso `nombre` es `"Cliente eliminado"`.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor | — |
| 404 | `NOT_FOUND` | El pedido no existe o no es de un emprendimiento del vendedor | "El pedido no está disponible" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda solo el nombre en SQLite (junto al detalle del pedido); el celular y la dirección se piden en cada apertura. Sin red muestra el nombre y "Conéctate para ver el contacto".
- **Concurrencia (RNF08)**: no aplica a la lectura. Si el pedido pasa a un estado terminado mientras se mira, el siguiente refresco ya no trae el contacto.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Pedido de otro emprendimiento o inexistente | `404` | US1-E2, FR-003 |
| Pedido terminado | Solo el nombre | Edge 3 |
| Pedido de recogida | Sin dirección de entrega | RF-51 |
| Cliente con cuenta eliminada | "Cliente eliminado", sin contacto | Edge 2 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: pedido en curso (nombre, celular, dirección), recogida, terminado, rechazado y cancelado, pedido ajeno e inexistente, rol distinto, sin sesión | FR-001, FR-003, US1-E1–E2 |
| Contrato | La respuesta contiene solo `nombre`, `celular` y `direccionEntrega`; no hay correo, foto ni datos del perfil | FR-002, Edge 3 |
| Integración | Cliente que cambió su nombre o eliminó su cuenta | Edge 1, Edge 2 |
| Rendimiento | Respuesta < 3 s | SC-001 |
| Arquitectura | `pedidos` lee de `cuentas` solo por `ConsultarContactoUsuarioPort` | RNF13 |
| Frontend | Jest: tarjeta del cliente con y sin contacto, nombre guardado sin red | US1-E1, Edge 2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-52-consulta-de-informacion-del-cliente/
├── RF-52-consulta-de-informacion-del-cliente.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/pedidos/
├── application/
│   ├── ports/in/                 # ConsultarClienteDelPedidoPort
│   ├── ports/out/                # PedidoRepositoryPort, EmprendimientoDelVendedorPort (pertenencia, ya usado por RF-51), ContactoUsuarioPort
│   └── usecase/                  # ConsultarClienteDelPedidoUseCase
└── infrastructure/adapters/
    ├── in/web/                   # PedidoVendedorController (GET /pedidos/{id}/cliente), DTO cerrado
    └── out/                      # adaptador de ContactoUsuarioPort sobre el puerto in de cuentas

backend/src/main/java/co/edu/napa/cuentas/application/ports/in/    # ConsultarContactoUsuarioPort (nombre, celular, estado; si RF-49 no lo creó)
backend/src/test/java/co/edu/napa/pedidos/

frontend/src/components/pedidos/TarjetaCliente.tsx                 # nombre, celular y dirección
frontend/src/services/pedidosVendedor.ts                           # obtenerClienteDelPedido()
frontend/src/app/(vendedor)/pedidos/[id].tsx                       # (RF-51) muestra la tarjeta
frontend/__tests__/pedidos/tarjeta-cliente.test.tsx
```

**Structure Decision**: módulo `pedidos`, con la misma pertenencia que RF-51 (el emprendimiento del pedido es del vendedor). El nombre y el celular vienen de un puerto `in` de `cuentas`. El contacto va en una ruta aparte del detalle para que el dato personal tenga una sola puerta y una sola regla; el detalle de RF-51 sigue mostrando solo el nombre.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `pedidos` y su regla ArchUnit

## Phase 2: Foundational

- [ ] T002 `ConsultarContactoUsuarioPort` en `cuentas` (nombre, celular y estado; si RF-49 T004 ya lo creó, reutilizarlo) y `ContactoUsuarioPort` en `pedidos`
- [ ] T003 Reutilizar la pertenencia por emprendimiento de RF-51 en `PedidoRepositoryPort`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - El vendedor conoce al cliente de un pedido (Priority: P2)

**Goal**: el vendedor tiene los datos que necesita para gestionar la entrega.
**Independent Test**: abrir un pedido en curso y ver el nombre, el celular y la dirección.
**Cubre**: FR-001–FR-003, SC-001–SC-003, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /pedidos/{id}/cliente` en un pedido en curso (nombre, celular y dirección) en `backend/src/test/java/co/edu/napa/pedidos/ClienteDelPedidoIT.java`
- [ ] T011 [P] [US1] Test de recogida (sin dirección) y de pedido `ENTREGADO`, `RECHAZADO` y `CANCELADO` (solo nombre) en `.../pedidos/ClienteDelPedidoEstadosIT.java`
- [ ] T012 [P] [US1] Test de contrato: la respuesta solo tiene los campos permitidos y nada del perfil del cliente en `.../pedidos/ClienteDelPedidoPrivacidadIT.java`
- [ ] T013 [P] [US1] Test de pedido de otro emprendimiento, inexistente, rol distinto y sin sesión en `.../pedidos/ClienteDelPedidoAutorizacionIT.java`
- [ ] T014 [P] [US1] Test de rendimiento (< 3 s) en `.../pedidos/ClienteDelPedidoRendimientoIT.java`
- [ ] T015 [P] [US1] Test de cliente que cambió su nombre después del pedido (se ve el actual) en `.../pedidos/ClienteDelPedidoActualizadoIT.java`
- [ ] T016 [P] [US1] Test de cliente con cuenta `ELIMINADA` ("Cliente eliminado", sin celular) en `.../pedidos/ClienteDelPedidoEliminadoIT.java`
- [ ] T030 [P] [US1] Test de `TarjetaCliente` (con y sin contacto, nombre guardado sin red) en `frontend/__tests__/pedidos/tarjeta-cliente.test.tsx`

### Implementation

- [ ] T020 [US1] `ConsultarClienteDelPedidoPort` y `ConsultarClienteDelPedidoUseCase`: valida la pertenencia, decide qué datos entregar según el estado del pedido y la cuenta
- [ ] T021 [US1] `GET /pedidos/{id}/cliente` en `PedidoVendedorController`
- [ ] T022 [US1] DTO cerrado de la respuesta y mapeo desde el pedido y la ubicación de entrega
- [ ] T023 [US1] Adaptador de `ContactoUsuarioPort`
- [ ] T031 [US1] `TarjetaCliente` y `obtenerClienteDelPedido()` en `frontend/src/`
- [ ] T032 [US1] Mostrar la tarjeta en el detalle del pedido del vendedor (RF-51)

**Checkpoint**: el vendedor ve los datos del cliente de cada pedido.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Confirmar que los logs no guardan el celular ni la dirección
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-51 (detalle y pertenencia), RF-45 (pedido con ubicación de entrega) y RF-10.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| US1-E2 dice "deniega el acceso e informa que no existe relación"; RF-51 y RF-53 responden `404` para no revelar pedidos ajenos | Contrato | `404` con "El pedido no está disponible". **Confirmar** | Equipo |
| ¿El celular y la dirección se ven también con el pedido terminado? | Privacidad | No: solo en curso; después queda el nombre. **Confirmar** | Equipo |
| La ruta no está en spec-general (`GET /pedidos/{id}/cliente`) | Contrato | Se agrega a Spec-general | Equipo |
| Edge 1: ¿datos actuales o los del momento del pedido? | Datos | Actuales para nombre y celular; la dirección es la del pedido (inmutable) | Equipo |
| Cliente con cuenta eliminada | Datos | "Cliente eliminado" sin contacto | Equipo |
| RF-51 sigue devolviendo `cliente.nombre`; los dos pueden diferir por un instante | Coherencia | Aceptado | Equipo |

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
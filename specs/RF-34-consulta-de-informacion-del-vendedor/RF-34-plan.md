# Implementation Plan: Consulta de información del vendedor

**Date**: 2026-10-27
**Spec**: [RF-34-consulta-de-informacion-del-vendedor.md](./RF-34-consulta-de-informacion-del-vendedor.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-34
**Historias de usuario**: HU-59
**Prioridad (la mayor de la spec)**: P3
**Depende de**: RF-32, RF-27, RF-08, RF-10
**Habilita a**: —

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El cliente que mira un emprendimiento consulta quién lo dirige con `GET /emprendimientos/{id}/vendedor`. La respuesta trae solo datos públicos: nombre, foto, tipo de vendedor (ambulante o de punto fijo) y desde cuándo está en Ñapa. Si el vendedor no tiene foto responde igual, con ese campo en `null`, sin error. No incluye celular, correo ni ubicación de su perfil: el contacto con un vendedor ocurre después de pedir, con RF-49. Si el emprendimiento no está `ACTIVO` o el dueño tiene la cuenta eliminada responde `404`.

## Estado actual del código

RF-32 ya devuelve el detalle del emprendimiento al cliente, con su ubicación y su calificación; RF-27 deja `ConsultarPerfilPublicoPort` en `cuentas` (nombre y foto) y RF-08 permite subir la foto. El modelo no tiene ningún campo con el que el vendedor "autorice" qué datos son públicos (spec FR-002), así que este plan define un conjunto público fijo y deja la autorización como pregunta abierta.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Consulta los datos públicos del vendedor de un emprendimiento |
| Vendedor | Es la persona consultada; no interviene |
| Domiciliario, Administrador | No intervienen |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Respuesta en < 3 s (SC-001): una lectura del emprendimiento y una del perfil público.
- 100 % de los datos privados ocultos (SC-002): el DTO es una lista cerrada de campos y un test verifica que no aparece ningún otro dato del usuario.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El cliente consulta la información pública del vendedor de un emprendimiento | `GET /emprendimientos/{id}/vendedor` | T010, T020, T021 |
| FR-002 | Mostrar solo lo que el vendedor haya autorizado como público | Conjunto público fijo (nombre, foto, tipo, miembro desde); la autorización por campo queda en Open Questions | T011, T022 |
| SC-001 | < 3 s | Dos lecturas indexadas | T012 |
| SC-002 | 100 % de datos privados ocultos | DTO cerrado y test de contrato | T011 |
| US1-E1 | Consulta exitosa | `200` con nombre, foto, tipo y fecha | T010 |
| US1-E2 | Vendedor sin datos adicionales | `fotoUrl: null` y `200`, sin error | T013 |
| Edge 1 | Qué es público y qué es privado | Público: nombre, foto, tipo, miembro desde. Privado: celular, correo, ubicación del perfil, estado de la cuenta | T011 |
| Edge 2 | Vendedor con más de un emprendimiento | No ocurre: un solo `ACTIVO` por vendedor; la ruta es por el emprendimiento | T014 |
| Edge 3 | Cuenta del vendedor eliminada con el emprendimiento visible | `404`; RF-68 elimina también su emprendimiento | T015 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo CLIENTE |
| RNF04 Datos personales | ☑ | Lista cerrada de campos públicos |
| RNF07 Rendimiento ≤ 3 s | ☑ | Lecturas indexadas |
| RNF13 Arquitectura | ☑ | `emprendimientos` lee el perfil por `ConsultarPerfilPublicoPort` de `cuentas` |
| RNF28 Conectividad | ☑ | La app guarda la última respuesta por emprendimiento |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Emprendimiento | Lee | Debe estar `ACTIVO` |
| Usuario (vendedor) | Lee (puerto) | Solo `nombre`, `foto_url`, `rol` y `created_at`; la cuenta debe estar `ACTIVA` |

## State Transitions

Esta feature es de solo lectura: no cambia estados.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /emprendimientos/{id}/vendedor` | CLIENTE | Cualquier emprendimiento `ACTIVO` |

## API Contracts

### GET /api/v1/emprendimientos/{id}/vendedor

- **Rol**: cliente · **Path params**: `id` (UUID del emprendimiento) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `X-Request-Id` y body:

```json
{ "nombre": "Rosa Martínez", "fotoUrl": "https://…/perfiles/….jpg", "tipoVendedor": "VENDEDOR_PUNTO_FIJO", "miembroDesde": "2026-09-01" }
```

`fotoUrl` es `null` si no tiene foto.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de cliente | — |
| 404 | `NOT_FOUND` | El emprendimiento no existe, no está `ACTIVO`, o su dueño tiene la cuenta eliminada | "Este emprendimiento ya no está disponible" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Es de solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda la última respuesta por emprendimiento en SQLite y la muestra con "datos guardados" sin red.
- **Concurrencia (RNF08)**: no aplica a la lectura.
- **Idempotencia (RNF27)**: no aplica a un `GET`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Vendedor sin foto | `200` con `fotoUrl: null` | US1-E2 |
| Emprendimiento inactivo, eliminado o inexistente | `404` | RF-32 |
| Dueño con la cuenta eliminada | `404` | Edge 3 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: con y sin foto, ambos tipos de vendedor, emprendimiento inactivo, eliminado e inexistente, dueño con cuenta eliminada, rol distinto, sin sesión | FR-001, US1-E1–E2, Edge 2–3 |
| Contrato | La respuesta contiene solo `nombre`, `fotoUrl`, `tipoVendedor` y `miembroDesde` | FR-002, SC-002, Edge 1 |
| Rendimiento | Respuesta < 3 s | SC-001 |
| Arquitectura | `emprendimientos` usa solo el puerto `in` de `cuentas` | RNF13 |
| Frontend | Jest: tarjeta con y sin foto, caché sin red | US1-E1–E2 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-34-consulta-de-informacion-del-vendedor/
├── RF-34-consulta-de-informacion-del-vendedor.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/emprendimientos/
├── application/
│   ├── ports/in/                 # ConsultarVendedorDelEmprendimientoPort
│   ├── ports/out/                # EmprendimientoRepositoryPort, PerfilPublicoUsuarioPort
│   └── usecase/                  # ConsultarVendedorDelEmprendimientoUseCase
└── infrastructure/adapters/
    └── in/web/                   # EmprendimientoController (GET /emprendimientos/{id}/vendedor), DTO cerrado

backend/src/main/java/co/edu/napa/cuentas/application/ports/in/   # ConsultarPerfilPublicoPort (RF-27; agrega rol y created_at)
backend/src/test/java/co/edu/napa/emprendimientos/

frontend/src/components/emprendimientos/TarjetaVendedor.tsx       # foto, nombre, tipo y "en Ñapa desde"
frontend/src/services/emprendimientos.ts                          # obtenerVendedor() con caché
frontend/src/app/(cliente)/emprendimientos/[id].tsx               # (RF-32) incluye la tarjeta
frontend/__tests__/emprendimientos/tarjeta-vendedor.test.tsx
```

**Structure Decision**: módulo `emprendimientos`, porque la ruta cuelga del emprendimiento y es él quien conoce a su dueño. Los datos públicos vienen de `cuentas` por el mismo puerto de perfil público de RF-27. No devuelve la calificación: la reputación del vendedor es la de su emprendimiento (RF-32), no la de su usuario.

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `emprendimientos` y su regla ArchUnit

## Phase 2: Foundational

- [ ] T002 Ampliar `ConsultarPerfilPublicoPort` de `cuentas` (RF-27 T002) con `rol`, `created_at` y estado de la cuenta, y `PerfilPublicoUsuarioPort` en `emprendimientos`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Conocer al vendedor (Priority: P3)

**Goal**: el cliente sabe quién está detrás del emprendimiento antes de comprar.
**Independent Test**: abrir un emprendimiento y ver los datos públicos de su vendedor.
**Cubre**: FR-001, FR-002, SC-001, SC-002, US1-E1–E2, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /emprendimientos/{id}/vendedor` (nombre, foto, tipo y fecha) en `backend/src/test/java/co/edu/napa/emprendimientos/VendedorDelEmprendimientoIT.java`
- [ ] T011 [P] [US1] Test de contrato: la respuesta solo trae los campos públicos y nunca celular, correo ni ubicación, en `.../emprendimientos/VendedorDelEmprendimientoPrivacidadIT.java`
- [ ] T012 [P] [US1] Test de rendimiento (< 3 s) en `.../emprendimientos/VendedorDelEmprendimientoRendimientoIT.java`
- [ ] T013 [P] [US1] Test de vendedor sin foto (`200`, `fotoUrl: null`) en `.../emprendimientos/VendedorDelEmprendimientoSinFotoIT.java`
- [ ] T014 [P] [US1] Test de ambos tipos de vendedor y de que un vendedor con un emprendimiento eliminado y otro activo muestra el activo, en `.../emprendimientos/VendedorDelEmprendimientoTipoIT.java`
- [ ] T015 [P] [US1] Test de emprendimiento inactivo, eliminado e inexistente, dueño con cuenta eliminada, rol distinto y sin sesión, en `.../emprendimientos/VendedorDelEmprendimientoNoDisponibleIT.java`
- [ ] T030 [P] [US1] Test de `TarjetaVendedor` (con y sin foto, caché sin red) en `frontend/__tests__/emprendimientos/tarjeta-vendedor.test.tsx`

### Implementation

- [ ] T020 [US1] `ConsultarVendedorDelEmprendimientoPort` y `ConsultarVendedorDelEmprendimientoUseCase`: valida el emprendimiento `ACTIVO` y la cuenta `ACTIVA` y arma el DTO
- [ ] T021 [US1] `GET /emprendimientos/{id}/vendedor` en `EmprendimientoController`
- [ ] T022 [US1] DTO cerrado `VendedorPublicoResponse`
- [ ] T031 [US1] `TarjetaVendedor` y `obtenerVendedor()` con caché SQLite en `frontend/src/`
- [ ] T032 [US1] Mostrar la tarjeta en el detalle del emprendimiento (RF-32)

**Checkpoint**: el cliente ve al vendedor.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Revisar `EXPLAIN` de la lectura
- [ ] T041 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-32 (detalle donde se muestra), RF-27 (puerto de perfil público), RF-08 (foto) y RF-10.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| **FR-002 y el escenario dicen que el vendedor autoriza qué datos son públicos, y también "datos de contacto habilitados para clientes", pero el modelo no tiene ningún campo de autorización ni de contacto público** | Modelo | Conjunto público fijo (nombre, foto, tipo, miembro desde) sin contacto; el contacto va por RF-49 al hacer un pedido. Las alternativas: que el vendedor marque su celular como público (campo nuevo en `Usuario`) o un botón de contacto sin pedido. **Necesita decisión del equipo** | Equipo |
| La ruta no está en spec-general (`GET /emprendimientos/{id}/vendedor`) | Contrato | Se agrega a Spec-general | Equipo |
| RF-32 ya muestra el nombre del emprendimiento y su ubicación: esta tarjeta repite poco | Alcance | Aporta la persona (nombre y foto) | Equipo |
| Edge 3: dueño con cuenta eliminada | Regla | `404`; RF-68 oculta el emprendimiento al eliminar un vendedor, y RF-11 no deja eliminar la cuenta con emprendimiento activo | Equipo |
| El tipo de vendedor se muestra con el valor del rol | UX | La app lo traduce a "Ambulante" o "Punto fijo" | Equipo |

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
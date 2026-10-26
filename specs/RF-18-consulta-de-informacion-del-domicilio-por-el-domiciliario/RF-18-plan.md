# Implementation Plan: Consulta de información del domicilio por el domiciliario

**Date**: 2026-10-25
**Spec**: [RF-18-consulta-de-informacion-del-domicilio-por-el-domiciliario.md](./RF-18-consulta-de-informacion-del-domicilio-por-el-domiciliario.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-18
**Historias de usuario**: HU-39
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-14, RF-16, RF-10
**Habilita a**: RF-19, RF-22, RF-23, RF-24

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El domiciliario abre la información de un domicilio con `GET /domicilios/{id}`: punto A, punto B completo, nombre del cliente que recibe, cantidad de ítems del pedido y ganancia (la tarifa publicada); nunca datos de pago ni el código de confirmación. Si el domicilio está `DISPONIBLE` puede aceptarlo (RF-19) o rechazarlo (RF-17, P2); si ya es suyo y está activo, la respuesta indica que tiene la ruta disponible (RF-22). Si fue tomado por otro o cancelado, responde `409 NOT_AVAILABLE` "El domicilio ya no está disponible".

## Estado actual del código

No existe la consulta. Los datos salen de `domicilio` (RF-14) y, por puertos `in`, de `pedidos` (tarifa, ubicación de entrega, comprador, ítems) y de `usuarios` (nombre del receptor).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Es el receptor; solo se muestra su nombre |
| Vendedor (punto fijo) | No interviene |
| Domiciliario | Consulta la información |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Información en < 3 s (SC-001): consulta por clave primaria con joins por puertos.
- 100 % de las consultas con origen, destino, receptor y ganancia (SC-002).
- Ningún dato de pago ni el código de confirmación en la respuesta (FR-002, RF-25 FR-002): se prueba con un test de contrato.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Mostrar origen, destino, receptor y ganancia | `GET /domicilios/{id}`; DTO `DomicilioParaDomiciliario` | T010, T013, T014 |
| FR-002 | Resumen de ítems (cantidad), sin datos de pago | `resumenItems: { cantidadItems }`; el DTO no incluye medio, monto del pedido, cuenta ni código | T011 |
| FR-003 | Aceptar (RF-19) o rechazar (RF-17) desde la información | La app muestra "Aceptar" (RF-19) y "Rechazar" solo cuando RF-17 exista | T020, T021 |
| FR-004 | Destino y receptor completos desde antes de la asignación | La respuesta los trae para cualquier domiciliario con el domicilio `DISPONIBLE` | T010 |
| FR-005 | Informar si ya no está disponible | `409 NOT_AVAILABLE` con el mensaje de la spec | T012 |
| SC-001 | < 3 s | Consulta por id | T040 |
| SC-002 | 100 % con los cuatro datos | Prueba de contrato | T010 |
| US1-E1 | Información de un domicilio disponible | Endpoint y pantalla | T010, T021 |
| US1-E2 | Acciones aceptar o rechazar | Botones en la pantalla | T021 |
| US1-E3 | Domicilio ya no disponible | `409 NOT_AVAILABLE` | T012 |
| US1-E4 | Domicilio propio en curso | Los mismos datos y `rutaDisponible: true` con acceso a RF-22 | T015, T022 |
| Edge 1 | Destino que no se resuelve en el mapa | La dirección en texto y la referencia siempre se muestran; el mapa es opcional (RNF19) | T021 |
| Edge 2 | Cancelado mientras se revisa | El siguiente refresco o acción responde `NOT_AVAILABLE`; el domicilio propio cancelado se muestra con su estado y motivo | T012, T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo DOMICILIARIO; visibilidad según el estado y la asignación |
| RNF04 Privacidad | ☑ | Sin datos de pago ni código; `Cache-Control: no-store` |
| RNF07 Rendimiento | ☑ | Consulta por id |
| RNF19 Interoperabilidad | ☑ | Dirección en texto como respaldo del mapa |
| RNF24 Errores claros | ☑ | `NOT_AVAILABLE` con mensaje claro |
| RNF28 Conectividad | ☑ | Última información en SQLite |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Lee | Visible si `DISPONIBLE` o asignado al domiciliario |
| Ubicacion | Lee | Punto A (copia) y punto B (la del pedido) |
| Pedido / ItemPedido | Lee (vía puerto) | Solo la cantidad de ítems y la tarifa |
| Usuario (cliente) | Lee (vía puerto) | Solo el nombre |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /domicilios/{id}` | DOMICILIARIO | `DISPONIBLE`, o asignado a él en cualquier estado; en otro caso `409 NOT_AVAILABLE` |

## API Contracts

### GET /api/v1/domicilios/{id}

- **Rol**: domiciliario (el catálogo lo lista como "domiciliario / partes"; para el cliente y el vendedor ver Open Questions) · **Path params**: `id` (UUID) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "id": "…", "estado": "DISPONIBLE", "ganancia": 4500, "asignadoAMi": false, "rutaDisponible": false, "motivoCancelacion": null,
  "origen":  { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Carrera 5 # 10-20", "referencia": null },
  "destino": { "latitud": 11.2501, "longitud": -74.2002, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" },
  "receptor": { "nombre": "Ana Pérez" },
  "resumenItems": { "cantidadItems": 3 }
}
```

`rutaDisponible` es `true` cuando el domicilio es suyo y está activo (`ASIGNADO`, `RECOGIDO` o `EN_CAMINO`).

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de domiciliario | — |
| 404 | `NOT_FOUND` | El domicilio no existe | "El domicilio no está disponible" |
| 409 | `NOT_AVAILABLE` | Asignado a otro domiciliario, cancelado o entregado y no es suyo | "El domicilio ya no está disponible" |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda la última información vista en SQLite con la hora; aceptar y rechazar exigen conexión.
- **Concurrencia (RNF08)**: no aplica (lectura); la carrera por aceptar la resuelve RF-19.
- **Idempotencia (RNF27)**: no aplica.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Domicilio tomado o cancelado | `409 NOT_AVAILABLE` "El domicilio ya no está disponible" | US1-E3 |
| Domicilio propio cancelado | Se muestra con estado `CANCELADO` y su motivo | Edge 2 |
| Destino sin resolver en el mapa | Se muestran la dirección y la referencia | Edge 1 |
| Sin conexión | Última información guardada | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers: disponible, propio en curso, tomado por otro, cancelado, entregado, inexistente, rol equivocado | US1-E1–E4, FR-005 |
| Contrato | La respuesta nunca contiene datos de pago ni `codigoConfirmacion` (inspección del JSON en todos los estados) | FR-002 |
| Frontend | Jest: secciones, acciones por estado, mensaje de no disponible, caché sin red | FR-003, US1-E3 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-18-consulta-de-informacion-del-domicilio-por-el-domiciliario/
├── RF-18-consulta-de-informacion-del-domicilio-por-el-domiciliario.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── application/
│   ├── ports/in/                 # ConsultarDomicilioDomiciliarioPort
│   ├── ports/out/                # DomicilioRepositoryPort, PedidoParaDomicilioPort (cantidad de ítems), ClienteNombrePort
│   └── usecase/                  # ConsultarDomicilioDomiciliarioUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (GET /domicilios/{id}), DTO sin datos de pago
    └── out/persistence/          # adaptadores a los puertos `in` de pedidos y usuarios
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/app/(domiciliario)/domicilios/[id].tsx              # ruta Expo Router: información del domicilio y acciones
frontend/src/components/domicilios/InfoDomicilio.tsx             # origen, destino, receptor, ítems, ganancia
frontend/src/components/domicilios/AccionesDomicilioDisponible.tsx   # Aceptar (RF-19) y Rechazar (cuando exista RF-17)
frontend/src/services/domicilios.ts                              # obtenerDomicilio() con caché local
frontend/src/database/schema/domicilio_detalle_cache.ts
frontend/__tests__/domicilios/info-domicilio.test.tsx
```

**Structure Decision**: módulo `domicilios`. La respuesta del domiciliario es un DTO propio que solo contiene lo que la spec permite mostrar; el código de confirmación y los datos de pago no existen en ese DTO, de modo que no pueden filtrarse por error. El nombre del receptor y la cantidad de ítems llegan por puertos `in` de `usuarios` y `pedidos`.

## Phase 1: Setup

- [ ] T001 Confirmar `DomicilioRepositoryPort` (RF-14, T004) y `PedidoParaDomicilioPort` (RF-14, T005)

## Phase 2: Foundational

- [ ] T002 Ampliar `PedidoParaDomicilioPort` con cantidad de ítems y comprador, y definir `ClienteNombrePort` sobre el puerto `in` de `usuarios` (nombre del cliente)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Domiciliario conoce los datos del domicilio (Priority: P1)

**Goal**: decidir si toma un domicilio y saber cómo realizarlo.
**Independent Test**: abrir desde la lista un domicilio disponible y ver A, B, receptor y ganancia.
**Cubre**: FR-001–FR-005, SC-001–SC-002, US1-E1–E4, Edge 1–2.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /domicilios/{id}` de un domicilio disponible (origen, destino, receptor, cantidad de ítems, ganancia) en `backend/src/test/java/co/edu/napa/domicilios/ConsultarDomicilioIT.java`
- [ ] T011 [P] [US1] Test de contrato: ningún dato de pago ni código de confirmación en ningún estado, en `.../domicilios/ConsultarDomicilioContratoIT.java`
- [ ] T012 [P] [US1] Test de domicilio tomado por otro, cancelado y entregado (`409 NOT_AVAILABLE`), e inexistente (`404`) en `.../domicilios/ConsultarDomicilioNoDisponibleIT.java`
- [ ] T015 [P] [US1] Test de domicilio propio activo (`asignadoAMi`, `rutaDisponible`) en `.../domicilios/ConsultarDomicilioPropioIT.java`
- [ ] T016 [P] [US1] Test de domicilio propio cancelado (estado y motivo) y de rol equivocado (`403`) en `.../domicilios/ConsultarDomicilioPropioCanceladoIT.java`
- [ ] T020 [P] [US1] Test de la pantalla (secciones, acciones solo en disponible, acceso a la ruta en propio, mensaje de no disponible, caché sin red) en `frontend/__tests__/domicilios/info-domicilio.test.tsx`

### Implementation

- [ ] T013 [US1] `ConsultarDomicilioDomiciliarioUseCase` (visibilidad por estado y asignación) y su puerto en `domicilios/application/`
- [ ] T014 [US1] `GET /domicilios/{id}` y el DTO `DomicilioParaDomiciliario` en `DomicilioController`
- [ ] T017 [US1] Mapeo de `NOT_AVAILABLE` con el mensaje de la spec en el manejador de errores de `domicilios`
- [ ] T021 [US1] `InfoDomicilio` y `AccionesDomicilioDisponible` en `frontend/src/components/domicilios/` (Rechazar oculto hasta que exista RF-17)
- [ ] T022 [US1] Pantalla `frontend/src/app/(domiciliario)/domicilios/[id].tsx` con enlace a la ruta (RF-22) cuando `rutaDisponible`
- [ ] T023 [US1] Servicio `obtenerDomicilio()` con caché en `frontend/src/services/domicilios.ts` y `frontend/src/database/schema/domicilio_detalle_cache.ts`

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir la consulta en < 3 s (SC-001)
- [ ] T041 Revisar permisos y privacidad (RNF03, RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-14 y RF-16; RF-19 acepta desde esta pantalla y RF-22, RF-23 y RF-24 parten de ella.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| El catálogo de rutas dice "domiciliario / partes" para `GET /domicilios/{id}`, pero la spec solo describe la vista del domiciliario; el cliente y el vendedor ya tienen RF-25 y RF-26 | Contrato | Solo domiciliario (`403` para otros); las partes usan `/trazabilidad` y `/codigo-confirmacion` | Equipo |
| Un domicilio ajeno ya tomado responde `409 NOT_AVAILABLE` y no `404`, porque el domiciliario ya lo vio en su lista | Contrato | Así, con el mensaje de la spec | Equipo |
| Rechazar (RF-17) es P2 y no está en el catálogo de rutas | Alcance | Botón oculto hasta implementarlo | Equipo |
| Los nombres de los receptores y las direcciones exactas son visibles para cualquier domiciliario con el domicilio disponible (FR-004) | RNF04 | Se cumple la spec; sugerencia: reevaluar | Equipo |
| ¿Se muestra el detalle de los ítems? La spec pide solo el resumen (cantidad de ítems) | UX | Solo la cantidad | Equipo |

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
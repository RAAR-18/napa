# Implementation Plan: Consulta de domicilios disponibles

**Date**: 2026-10-25
**Spec**: [RF-16-consulta-de-domicilios-disponibles.md](./RF-16-consulta-de-domicilios-disponibles.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-16
**Historias de usuario**: HU-36
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-14 (domicilios publicados), RF-45 (ubicación y tarifa), RF-10
**Habilita a**: RF-18, RF-19

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El domiciliario consulta los domicilios `DISPONIBLE` con `GET /domicilios/disponibles`: cada elemento muestra origen (A), destino completo (B), distancia aproximada A→B y la ganancia (la tarifa publicada). La lista excluye los que el propio domiciliario rechazó (`domicilio_rechazo`, que puebla RF-17) y deja de mostrar los asignados o cancelados. Se puede ordenar por cercanía (a su posición) o por ganancia, con paginación. La app refresca la lista cada 15 s con la pantalla abierta y cuando llega la notificación de un domicilio nuevo (E08), de modo que un domicilio tomado por otro desaparece en menos de 30 s (SC-003).

## Estado actual del código

El módulo `domicilios` y las tablas `domicilio` y `domicilio_rechazo` los crea el plan de RF-14 (V10). La tabla `ubicacion` y `DistanciaLineaRectaPort` (PostGIS) vienen de RF-45. No existe aún ninguna pantalla del domiciliario.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene |
| Vendedor (punto fijo) | No interviene |
| Domiciliario | Consulta la lista |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Lista en < 3 s (SC-001): una consulta paginada con índice `(estado, created_at)` y distancias con PostGIS.
- 100 % de los domicilios disponibles no rechazados aparecen (SC-002).
- Domicilios asignados o cancelados fuera de la lista en < 30 s (SC-003): la lista se calcula siempre sobre el estado actual; el límite lo pone el sondeo de la app (15 s) más la notificación.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Consultar domicilios `DISPONIBLE` | `GET /domicilios/disponibles`; `ConsultarDomiciliosDisponiblesUseCase` | T010, T013, T014 |
| FR-002 | Mostrar origen, destino completo, distancia y ganancia (la tarifa publicada) | DTO con `origen`, `destino`, `distanciaMetros` (A→B) y `ganancia` | T010, T015 |
| FR-003 | Excluir los rechazados por el propio domiciliario | `NOT EXISTS` sobre `domicilio_rechazo` del usuario autenticado | T011 |
| FR-004 | Retirar los que dejan de estar disponibles | Filtro `estado = 'DISPONIBLE'` en cada consulta; la app sondea cada 15 s | T012, T022, T023 |
| FR-005 | Ordenar por cercanía o ganancia | Parámetros `orden`, `lat`, `lng` (spec-general §2.3) | T016 |
| FR-006 | Abrir la información detallada (RF-18) | La app navega a `(domiciliario)/domicilios/[id]` | T024 |
| SC-001 | < 3 s | Consulta indexada | T040 |
| SC-002 | 100 % de los disponibles no rechazados | Prueba de integración | T010, T011 |
| SC-003 | 0 % asignados o cancelados > 30 s | Sondeo de 15 s y prueba del estado en cada consulta | T012, T023 |
| US1-E1 | Consulta de domicilios disponibles | Endpoint y lista | T010, T020 |
| US1-E2 | Lista sin domicilios | `200` con `content: []`; la app muestra "No hay domicilios disponibles por ahora" | T017, T021 |
| US1-E3 | Los rechazados no aparecen | Exclusión por domiciliario; siguen visibles para los demás | T011 |
| US1-E4 | Un domicilio tomado por otro deja de aparecer | Filtro por estado y actualización de la lista | T012, T023 |
| Edge 1 | Abrir uno tomado segundos antes | Lo resuelve RF-18 (`409 NOT_AVAILABLE`) | — (RF-18) |
| Edge 2 | Cancelado por el administrador mientras aparece | Igual que el anterior: desaparece en el siguiente refresco | T012 |
| Edge 3 | ¿Ver domicilios lejanos o solo cercanos? | Se muestran todos; el orden por cercanía los acerca; no hay radio máximo (Open Questions) | T016 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo DOMICILIARIO; la exclusión usa el usuario autenticado |
| RNF04 Privacidad | ☑ | La spec pide el destino completo y el receptor desde antes de la asignación (RF-18 FR-004); `Cache-Control: no-store`; sin datos de pago (Open Questions) |
| RNF07 Rendimiento | ☑ | Índices y paginación (máx. 100) |
| RNF22 Geografía | ☑ | `ST_Distance` sobre `geography(Point,4326)` |
| RNF24 Errores claros | ☑ | `400` ante filtros desconocidos o `orden=cercania` sin `lat`/`lng` |
| RNF28 Conectividad | ☑ | Última lista guardada en SQLite con la hora |
| RNF30 Datos móviles | ☑ | Sondeo de 15 s solo en primer plano |
| RNF31 Gama baja | ☑ | Lista paginada y sin mapas en la lista |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Lee | `estado = DISPONIBLE` |
| DomicilioRechazo | Lee | Excluye los del domiciliario autenticado |
| Ubicacion | Lee | Origen (punto A, copia) y destino (la del pedido) |
| Pedido | Lee (vía puerto) | Tarifa y ubicación de entrega |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /domicilios/disponibles` | DOMICILIARIO | Ve los domicilios disponibles que no rechazó |

## API Contracts

### GET /api/v1/domicilios/disponibles

- **Rol**: domiciliario · **Path params**: ninguno
- **Query params**: `orden` (`cercania` o `ganancia`), `lat` y `lng` (posición del domiciliario; obligatorios si `orden=cercania`), `page`, `size`. Cualquier otro parámetro → `400` (spec-general §2.3). Sin `orden`: más recientes primero.
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "content": [{
    "id": "…", "ganancia": 4500, "distanciaMetros": 1850, "distanciaAlOrigenMetros": 620, "createdAt": "…",
    "origen":  { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Carrera 5 # 10-20", "referencia": null },
    "destino": { "latitud": 11.2501, "longitud": -74.2002, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" }
  }],
  "page": 0, "size": 20, "totalElements": 1
}
```

`distanciaMetros` es la distancia en línea recta de A a B (aproximada); `distanciaAlOrigenMetros` solo aparece si se enviaron `lat` y `lng`. `orden=ganancia` ordena de mayor a menor; `orden=cercania` por `distanciaAlOrigenMetros` ascendente.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Parámetro desconocido, `orden` inválido, `orden=cercania` sin `lat`/`lng`, coordenadas fuera de rango | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de domiciliario | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno. La app refresca la lista al recibir la notificación de E08 (RF-38).

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda la última lista en SQLite con la hora de actualización y la muestra sin conexión con un aviso de que puede estar desactualizada; las acciones (aceptar) exigen conexión (RF-19).
- **Concurrencia (RNF08)**: no aplica (lectura); un domicilio tomado entre la lista y el toque lo resuelve RF-19.
- **Idempotencia (RNF27)**: no aplica.
- **Refresco**: sondeo cada 15 s solo con la pantalla en primer plano y refresco inmediato al recibir una notificación de domicilio.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Sin domicilios disponibles | `200` con lista vacía y mensaje en la app | US1-E2 |
| Parámetro desconocido o `cercania` sin posición | `400 VALIDATION_ERROR` | spec-general §2.3 |
| Sin permiso de ubicación | La app ordena por ganancia o por más recientes y lo explica | RNF28 |
| Sin conexión | Última lista guardada con su hora | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoint con Testcontainers y PostGIS: lista, vacía, rechazados excluidos, asignados y cancelados fuera, orden por cercanía y por ganancia, paginación, filtros desconocidos, rol equivocado | US1-E1–E4, FR-001–FR-005 |
| Rendimiento | Lista con miles de domicilios y rechazos en < 3 s | SC-001 |
| Frontend | Jest: lista, mensaje vacío, orden, sondeo de 15 s, refresco por notificación, caché sin red | US1-E2, SC-003 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-16-consulta-de-domicilios-disponibles/
├── RF-16-consulta-de-domicilios-disponibles.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── application/
│   ├── ports/in/                 # ConsultarDomiciliosDisponiblesPort
│   ├── ports/out/                # DomicilioRepositoryPort (consulta de disponibles), PedidoParaDomicilioPort (tarifa y destino)
│   └── usecase/                  # ConsultarDomiciliosDisponiblesUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (GET /domicilios/disponibles), DTO
    └── out/persistence/          # consulta paginada con NOT EXISTS y ST_Distance
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/app/(domiciliario)/disponibles/index.tsx          # ruta Expo Router: lista de disponibles
frontend/src/components/domicilios/TarjetaDomicilioDisponible.tsx
frontend/src/components/domicilios/SelectorOrdenDomicilios.tsx # cercanía o ganancia
frontend/src/hooks/useListaDisponibles.ts                      # sondeo de 15 s y refresco por notificación
frontend/src/services/domicilios.ts                            # listarDisponibles() con caché local
frontend/src/database/schema/domicilios_disponibles_cache.ts
frontend/__tests__/domicilios/disponibles.test.tsx
```

**Structure Decision**: módulo `domicilios`. La tarifa y el destino salen de `pedidos` en lote por el puerto `PedidoParaDomicilioPort`, para no hacer una consulta por fila; el origen es la copia propia del domicilio. La distancia de la lista es en línea recta (PostGIS) porque calcular rutas por cada elemento excedería la cuota de `RoutingPort` y la meta de 3 s (Open Questions).

## Phase 1: Setup

- [ ] T001 Confirmar el módulo `domicilios`, V10 (`domicilio`, `domicilio_rechazo`) y `DistanciaLineaRectaPort` (RF-14 T002; RF-45 T005)

## Phase 2: Foundational

- [ ] T002 Índice `(estado, created_at)` sobre `domicilio` en una migración `V11__indices_domicilio.sql` en `backend/src/main/resources/db/migration/`
- [ ] T003 Ampliar `PedidoParaDomicilioPort` con una consulta en lote de tarifa y destino por lista de pedidos (puerto `in` de `pedidos`)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Domiciliario elige un domicilio para realizar (Priority: P1)

**Goal**: ver los domicilios que puede tomar con su ganancia y ordenarlos.
**Independent Test**: publicar domicilios y verlos en la lista de un domiciliario, con rechazos excluidos.
**Cubre**: FR-001–FR-006, SC-001–SC-003, US1-E1–E4, Edge 2–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /domicilios/disponibles` (campos de origen, destino, distancia y ganancia) en `backend/src/test/java/co/edu/napa/domicilios/ListarDisponiblesIT.java`
- [ ] T011 [P] [US1] Test de rechazados excluidos solo para el domiciliario que rechazó en `.../domicilios/ListarDisponiblesRechazadosIT.java`
- [ ] T012 [P] [US1] Test de que `ASIGNADO`, `RECOGIDO`, `EN_CAMINO`, `ENTREGADO` y `CANCELADO` no aparecen en `.../domicilios/ListarDisponiblesEstadosIT.java`
- [ ] T016 [P] [US1] Test de orden por cercanía (con `lat`/`lng`), por ganancia, sin orden, paginación y errores `400` en `.../domicilios/ListarDisponiblesOrdenIT.java`
- [ ] T017 [P] [US1] Test de lista vacía y de rol equivocado (`403`) en `.../domicilios/ListarDisponiblesVaciaIT.java`
- [ ] T020 [P] [US1] Test de la lista (tarjetas, orden, estado vacío, sondeo de 15 s, refresco por notificación, caché sin red) en `frontend/__tests__/domicilios/disponibles.test.tsx`

### Implementation

- [ ] T013 [US1] `ConsultarDomiciliosDisponiblesUseCase` y su puerto en `domicilios/application/`
- [ ] T014 [US1] Consulta paginada con `NOT EXISTS` y `ST_Distance` en el adaptador de persistencia
- [ ] T015 [US1] DTO de la lista (origen, destino completo, distancia, ganancia)
- [ ] T018 [US1] `GET /domicilios/disponibles` en `DomicilioController` con validación de parámetros
- [ ] T021 [US1] `TarjetaDomicilioDisponible`, `SelectorOrdenDomicilios` y mensaje de lista vacía en `frontend/src/components/domicilios/`
- [ ] T022 [US1] Servicio `listarDisponibles()` con caché en `frontend/src/services/domicilios.ts` y `frontend/src/database/schema/domicilios_disponibles_cache.ts`
- [ ] T023 [US1] Hook `useListaDisponibles` (sondeo de 15 s y refresco por notificación) en `frontend/src/hooks/`
- [ ] T024 [US1] Pantalla `frontend/src/app/(domiciliario)/disponibles/index.tsx` con navegación a la información del domicilio (RF-18)

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir la lista en < 3 s y la desaparición de un domicilio tomado en < 30 s (SC-001, SC-003)
- [ ] T041 Revisar permisos y privacidad del destino (RNF03, RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-14; la exclusión de rechazados espera a RF-17 (P2) para tener datos, aunque la consulta ya la aplica; RF-18 y RF-19 se abren desde la lista.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| FR-003 y US1-E3 dependen de RF-17 (rechazar), que es P2 y no está en el catálogo de rutas | Alcance | La consulta ya excluye los rechazos y la tabla existe; el endpoint de rechazo queda fuera de la ola | Equipo |
| "Distancia" de la lista: la spec no dice cuál | FR-002 | Línea recta A→B (aproximada); la distancia por ruta solo en la tarifa y en RF-22 | Equipo |
| `orden=cercania` mide la distancia del domiciliario al origen (A); no está definido | FR-005 | Así | Equipo |
| Edge 3: ¿radio máximo? | Regla | No hay radio; se ven todos | Equipo |
| RF-18 FR-004 exige mostrar el destino completo y el nombre del receptor desde antes de la asignación: cualquier domiciliario activo ve la dirección exacta del cliente | RNF04 | Se cumple la spec; se sugiere reevaluar mostrar solo el barrio hasta la asignación | Equipo |
| Sin permiso de ubicación del domiciliario | UX | Orden por ganancia o por más recientes | Equipo |

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
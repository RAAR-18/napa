# Implementation Plan: Ruta en mapa interactivo

**Date**: 2026-10-25
**Spec**: [RF-22-ruta-en-mapa-interactivo.md](./RF-22-ruta-en-mapa-interactivo.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-22
**Historias de usuario**: HU-45
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-19 (domicilio asignado), RF-18, `RoutingPort` (foundations F013), RF-10
**Habilita a**: RF-23, RF-24, RF-26

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El domiciliario con un domicilio asignado abre el mapa con `GET /domicilios/{id}/ruta`. El servidor calcula con `RoutingPort` el tramo pendiente: en `ASIGNADO`, de su posición al punto A y de A al punto B; en `RECOGIDO` y `EN_CAMINO`, de su posición al punto B. Si el servicio de rutas falla o supera la cuota, responde igual con la distancia en línea recta y marca el tramo como aproximado, sin polilínea. La app dibuja su posición (expo-location), los puntos y la ruta con `react-native-maps`; si no hay permiso de ubicación o conexión, muestra un mensaje claro y las direcciones en texto. Solo el domiciliario asignado y mientras el domicilio esté activo.

## Estado actual del código

`RoutingPort.computeRoute(GeoPoint, GeoPoint)` devuelve `RouteResult(distanceMeters, duration, encodedPolyline)`; ya existen `GeoPoint`, el adaptador de rutas y el caché en Redis planificado en foundations (F013). El domicilio asignado lo produce RF-19. No existe la consulta de la ruta ni el mapa del domiciliario.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene |
| Vendedor (punto fijo) | No interviene |
| Domiciliario | Consulta el mapa y la ruta |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Mapa con la ruta en < 5 s tras la asignación (SC-001): hasta dos llamadas a `RoutingPort` en paralelo, con caché en Redis por coordenadas redondeadas y tiempo límite corto antes del respaldo.
- 0 % de domiciliarios sin asignación con acceso (SC-002).
- Dependencia solo de `RoutingPort`: el proveedor concreto (OpenRouteService en D9 o Google Maps en F013) no cambia este plan.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Mapa con su ubicación, punto A, punto B y ruta | `GET /domicilios/{id}/ruta` devuelve A, B y los tramos; la posición viene de `lat`/`lng` y del GPS del dispositivo | T010, T013, T014, T022 |
| FR-002 | Restringir al domiciliario asignado y solo con el domicilio activo | Asignado → `403` si es de otro; `ENTREGADO` o `CANCELADO` → `409 INVALID_STATE` | T011 |
| FR-003 | Direcciones en texto como respaldo | La respuesta siempre lleva `direccion` y `referencia`; la app las muestra si el mapa no carga | T020, T021 |
| FR-004 | Servicio externo de mapas con conectividad intermitente | `RoutingPort` con caché y respaldo de línea recta; la app guarda la última ruta en SQLite | T012, T015, T023 |
| SC-001 | < 5 s | Llamadas en paralelo, caché y tiempo límite | T040 |
| SC-002 | 0 % sin asignación | Prueba de aislamiento | T011 |
| US1-E1 | Mapa de un domicilio asignado | Endpoint y pantalla | T010, T022 |
| US1-E2 | Acceso denegado a un domicilio ajeno | `403 FORBIDDEN` | T011 |
| US1-E3 | Sin ubicación o sin conexión | Mensaje claro y direcciones en texto; caché local | T020, T021 |
| Edge 1 | Punto B ambiguo sin resolver | Se muestra la dirección y la referencia; el punto sigue siendo el de las coordenadas guardadas | T020 |
| Edge 2 | Pierde la señal en el trayecto | El mapa mantiene la última ruta y la posición; al reconectar refresca | T023 |
| Edge 3 | ¿Ruta completa o tramo pendiente? | Tramo pendiente según el estado (Open Questions) | T010, T014 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo el domiciliario asignado |
| RNF04 Privacidad | ☑ | La posición del domiciliario viaja solo en la consulta y no se guarda; `Cache-Control: no-store` |
| RNF07 Rendimiento | ☑ | Caché en Redis y llamadas en paralelo |
| RNF19 Interoperabilidad | ☑ | `RoutingPort`; respaldo de línea recta |
| RNF22 Geografía | ☑ | Coordenadas de las `Ubicacion` |
| RNF24 Errores claros | ☑ | `FORBIDDEN` y `INVALID_STATE` |
| RNF28 Conectividad | ☑ | Última ruta en SQLite |
| RNF31 Gama baja | ☑ | Un solo mapa; sin seguimiento continuo en esta pantalla |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Domicilio | Lee | Asignado al domiciliario; `ASIGNADO`, `RECOGIDO` o `EN_CAMINO` |
| Ubicacion | Lee | Punto A (copia) y punto B (la del pedido) |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (solo lectura).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /domicilios/{id}/ruta` | DOMICILIARIO | Domicilio asignado a él; si es de otro, `403` |

## API Contracts

### GET /api/v1/domicilios/{id}/ruta

- **Rol**: domiciliario asignado · **Path params**: `id` (UUID)
- **Query params**: `lat` y `lng` (su posición actual; opcionales: sin ellos solo se devuelve el tramo A→B). Cualquier otro parámetro → `400`.
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "domicilioId": "…", "estado": "ASIGNADO",
  "origen":  { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Carrera 5 # 10-20", "referencia": null },
  "destino": { "latitud": 11.2501, "longitud": -74.2002, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" },
  "tramos": [
    { "tipo": "HACIA_ORIGEN",  "distanciaMetros": 620,  "duracionSegundos": 140, "polilinea": "…", "aproximado": false },
    { "tipo": "HACIA_DESTINO", "distanciaMetros": 2100, "duracionSegundos": 420, "polilinea": null, "aproximado": true }
  ]
}
```

`HACIA_ORIGEN` solo aparece en `ASIGNADO` y con `lat`/`lng`. En `RECOGIDO` y `EN_CAMINO` el tramo `HACIA_DESTINO` parte de la posición enviada (o de A si no se envió). Cuando el servicio de rutas no responde, el tramo trae `aproximado: true`, `polilinea: null`, la distancia en línea recta × `tarifa.factor_linea_recta` y sin duración.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `lat`/`lng` fuera de rango, solo uno de los dos o parámetro desconocido | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de domiciliario o domicilio asignado a otro (o a nadie) | "No tienes acceso a este domicilio" |
| 404 | `NOT_FOUND` | El domicilio no existe | "El domicilio no está disponible" |
| 409 | `INVALID_STATE` | Domicilio `DISPONIBLE` sin asignar a él, `ENTREGADO` o `CANCELADO` | `details.estadoActual` |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Solo lectura |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda en SQLite la última ruta del domicilio activo y la sigue mostrando sin señal, con la hora de la última actualización; las direcciones en texto no dependen del mapa. La caché se borra cuando el domicilio termina o se cierra sesión.
- **Concurrencia (RNF08)**: no aplica (lectura).
- **Idempotencia (RNF27)**: no aplica.
- **Posición**: `expo-location` en primer plano, una lectura al abrir la pantalla y otra al pulsar "Actualizar mi posición"; el seguimiento periódico hacia el cliente es de RF-26, no de esta pantalla.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Domicilio de otro domiciliario | `403 FORBIDDEN` | US1-E2 |
| Sin permiso de ubicación | Mensaje claro, marcadores de A y B y direcciones en texto | US1-E3 |
| Sin conexión | Última ruta guardada y direcciones | US1-E3 |
| Servicio de rutas caído | `200` con tramos aproximados | FR-004 |
| Domicilio terminado | `409 INVALID_STATE` | FR-002 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Selección de tramos por estado (`ASIGNADO`, `RECOGIDO`, `EN_CAMINO`) | Edge 3 |
| Integración | Endpoint con Testcontainers y `RoutingPort` simulado: cada estado, con y sin posición, servicio caído (aproximado), caché, otro domiciliario, domicilio sin asignar, terminado, rol equivocado | US1-E1–E3, FR-002, FR-004 |
| Frontend | Jest: mapa con posición, puntos y ruta, sin permiso, sin conexión, direcciones en texto, caché | FR-003, US1-E3 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-22-ruta-en-mapa-interativo/
├── RF-22-ruta-en-mapa-interactivo.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── application/
│   ├── ports/in/                 # ConsultarRutaDomicilioPort
│   ├── ports/out/                # DomicilioRepositoryPort, RoutingPort (shared), DistanciaLineaRectaPort (shared)
│   └── usecase/                  # ConsultarRutaDomicilioUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (GET /domicilios/{id}/ruta), DTO
    └── out/persistence/          # lectura del domicilio y sus ubicaciones
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/app/(domiciliario)/domicilios/[id]/ruta.tsx        # ruta Expo Router: mapa de la ruta
frontend/src/components/domicilios/MapaRutaDomicilio.tsx         # react-native-maps: posición, A, B, polilínea
frontend/src/components/domicilios/DireccionesDomicilio.tsx      # respaldo en texto
frontend/src/hooks/usePosicionActual.ts                          # (RF-35) permiso y lectura con expo-location
frontend/src/services/domicilios.ts                              # obtenerRuta() con caché local
frontend/src/database/schema/ruta_domicilio_cache.ts
frontend/__tests__/domicilios/ruta.test.tsx
```

**Structure Decision**: módulo `domicilios`, que usa `RoutingPort` de `shared` sin conocer el proveedor. El respaldo reutiliza la misma regla de la tarifa (línea recta × `tarifa.factor_linea_recta`, spec-general §3.4), de modo que mapa y tarifa degradan igual. Los dos tramos de `ASIGNADO` se calculan en paralelo. El mapa y la posición son del dispositivo; el servidor no guarda la posición del domiciliario en esta consulta.

## Phase 1: Setup

- [ ] T001 Confirmar `RoutingPort`, `GeoPoint`, el caché de rutas en Redis (foundations F013) y el parámetro `tarifa.factor_linea_recta` (RF-45, T002)

## Phase 2: Foundational

- [ ] T002 `RoutingPort` envuelto con un adaptador de caché (clave con coordenadas redondeadas) y tiempo límite en `shared/infrastructure/geo/` si aún no existe

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Domiciliario llega a la recogida y a la entrega (Priority: P1)

**Goal**: guiarse sin perderse.
**Independent Test**: asignar un domicilio y ver el mapa con su posición, A, B y la ruta.
**Cubre**: FR-001–FR-004, SC-001–SC-002, US1-E1–E3, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /domicilios/{id}/ruta` en `ASIGNADO`, `RECOGIDO` y `EN_CAMINO` (tramos y orden) en `backend/src/test/java/co/edu/napa/domicilios/ConsultarRutaIT.java`
- [ ] T011 [P] [US1] Test de domicilio de otro domiciliario o sin asignar (`403`), terminado (`409`), inexistente (`404`) y rol equivocado en `.../domicilios/ConsultarRutaAccesoIT.java`
- [ ] T012 [P] [US1] Test de servicio de rutas caído o sin cuota (`aproximado: true`) y del caché en `.../domicilios/ConsultarRutaRespaldoIT.java`
- [ ] T016 [P] [US1] Test de validación de `lat`/`lng` y parámetros desconocidos en `.../domicilios/ConsultarRutaParametrosIT.java`
- [ ] T020 [P] [US1] Test de la pantalla (mapa con posición, A, B y ruta; sin permiso; sin conexión con direcciones en texto) en `frontend/__tests__/domicilios/ruta.test.tsx`

### Implementation

- [ ] T013 [US1] `ConsultarRutaDomicilioUseCase` (acceso por asignación y estado, selección de tramos) y su puerto en `domicilios/application/`
- [ ] T014 [US1] `GET /domicilios/{id}/ruta` y su DTO en `DomicilioController`
- [ ] T015 [US1] Respaldo de línea recta con `DistanciaLineaRectaPort` y `tarifa.factor_linea_recta`
- [ ] T021 [US1] `DireccionesDomicilio` y mensajes de permiso y conexión en `frontend/src/components/domicilios/`
- [ ] T022 [US1] `MapaRutaDomicilio` y pantalla `frontend/src/app/(domiciliario)/domicilios/[id]/ruta.tsx`
- [ ] T023 [US1] Servicio `obtenerRuta()` con caché en `frontend/src/services/domicilios.ts` y `frontend/src/database/schema/ruta_domicilio_cache.ts`

**Checkpoint**: US1 funcional.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir el mapa con la ruta en < 5 s tras la asignación en un dispositivo de gama baja (SC-001)
- [ ] T041 Revisar permisos y que la posición del domiciliario no se registre (RNF03, RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-19 (asignación) y RF-18 (acceso); RF-23 y RF-24 se ejecutan mientras el domiciliario sigue la ruta; RF-26 envía la posición al cliente.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Proveedor de rutas: D9 de foundations dice OpenRouteService, F013, el código y la tabla de preguntas abiertas dicen Google Maps | Costos, cuotas y claves | Este plan solo depende de `RoutingPort`; hay que corregir `foundations/plan.md` | Equipo |
| Edge 3: ¿ruta completa o tramo pendiente? | UX | Tramo pendiente: `ASIGNADO` posición→A→B; `RECOGIDO` y `EN_CAMINO` posición→B | Equipo |
| El acceso de otro domiciliario responde `403` y no `404`, porque la spec dice "deniega el acceso" y los domiciliarios ya ven los domicilios en su lista | Contrato | `403` | Equipo |
| La posición del domiciliario viaja como `lat`/`lng` en la consulta; podría calcularse solo con A y B | Privacidad | Opcional: sin posición solo se devuelve el tramo pendiente desde A | Equipo |
| Cuota del servicio de rutas: hasta dos llamadas por consulta y por domiciliario | Costo | Caché de 5 min por coordenadas redondeadas a ~100 m | Equipo |

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
# Implementation Plan: Estado y trazabilidad del domicilio

**Date**: 2026-10-25
**Spec**: [RF-26-estado-y-trazabilidad-del-domicilio.md](./RF-26-estado-y-trazabilidad-del-domicilio.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-26
**Historias de usuario**: HU-48, HU-33
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-14, RF-19, RF-23, RF-24 (eventos de trazabilidad), RF-48 (acceso desde el pedido), RF-10
**Habilita a**: RF-25 (comparte la pantalla del cliente), RF-23 (pantalla del vendedor)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Seguimiento del domicilio para las partes. El cliente o el vendedor del pedido consultan `GET /domicilios/{id}/trazabilidad`: estado actual, la cronología de eventos con fecha y hora (publicado, asignado, recogido, en camino, entregado o cancelado con su motivo) y, solo para el cliente y mientras el domicilio está `EN_CAMINO`, la ubicación aproximada del domiciliario. Esa ubicación la envía la app del domiciliario cada 15 s con `PUT /domicilios/{id}/ubicacion-domiciliario` y vive únicamente en Redis (`loc:{domicilioId}`, expira a los 2 minutos); no se guarda en PostGIS ni en la base (RNF04) y se descarta al entregar o cancelar. La app del cliente sondea cada 15 s con el domicilio en camino y cada 30 s en los demás estados, además de refrescar con las notificaciones, de modo que el cambio de estado se refleja en menos de 30 s.

## Estado actual del código

`domicilio` y `evento_trazabilidad` los crea RF-14 (V10) y cada transición de RF-14, RF-19, RF-23 y RF-24 inserta su evento. Este plan crea la consulta, el envío de la ubicación y las pantallas del cliente y del vendedor. El bloque `domicilio` del detalle de pedido (RF-51 y RF-48) lo completa RF-14 y de ahí se llega a estas pantallas.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Consulta la cronología y la ubicación aproximada del domiciliario |
| Vendedor (punto fijo) | Consulta el estado y la cronología (P2) |
| Domiciliario | Envía su ubicación mientras el domicilio está en camino |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Estado visible al cliente y al vendedor en < 30 s tras el cambio (SC-001): sondeo de 15 s con el domicilio en camino y de 30 s en los demás casos, más refresco por notificación.
- 100 % de los domicilios en curso consultables sin errores (SC-002).
- La ubicación del domiciliario no se persiste: Redis con expiración de 2 minutos (foundations, RNF04); el cliente la consulta cada 15–30 s (RNF30).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | El cliente consulta en cualquier momento la trazabilidad de sus domicilios | `GET /domicilios/{id}/trazabilidad` rama cliente | T010, T013, T014 |
| FR-002 | Cronología con fecha y hora de cada evento | `eventos[]` ordenados por `ocurridoAt` desde `evento_trazabilidad` | T010, T015 |
| FR-003 | Ubicación aproximada del domiciliario mientras está en camino | `PUT /domicilios/{id}/ubicacion-domiciliario` guarda en Redis `loc:{id}` (TTL 2 min); la consulta la devuelve solo al cliente y solo en `EN_CAMINO`, con coordenadas redondeadas | T011, T016, T017 |
| FR-004 | El vendedor de punto fijo consulta el estado de sus domicilios | Misma ruta, rama vendedor: estado y cronología, sin ubicación en vivo | T012, T018 |
| FR-005 | Reflejar cada cambio en un tiempo oportuno (RNF17) | Sondeo de 15/30 s y refresco por notificación | T030, T032 |
| FR-006 | Restringir a las partes involucradas | Cliente del pedido o vendedor dueño; otros → `404`; el envío de ubicación solo del domiciliario asignado | T019 |
| SC-001 | < 30 s | Sondeo + notificación | T040 |
| SC-002 | 100 % consultables | Prueba de integración por cada estado | T010 |
| US1-E1 | Cronología del domicilio | `eventos[]` | T010 |
| US1-E2 | Ubicación durante el trayecto | `ubicacionDomiciliario` en `EN_CAMINO` | T011 |
| US1-E3 | Domicilio cancelado | `estado: CANCELADO` con `motivoCancelacion` | T020 |
| US2-E1 | Consulta del estado de un domicilio por el vendedor | Rama vendedor | T012 |
| Edge 1 | El domiciliario aún no actualiza su avance | `ubicacionDomiciliario: null` con "Sin ubicación reciente"; la cronología sigue visible | T011, T031 |
| Edge 2 | El domiciliario desactiva la ubicación | La clave de Redis expira a los 2 minutos y la consulta devuelve `null` | T011 |
| Edge 3 | ¿Hasta cuándo se puede consultar un domicilio entregado? | Sin vencimiento: la cronología se conserva (Open Questions) | T021 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Solo las partes; envío de ubicación solo del domiciliario asignado |
| RNF04 Privacidad | ☑ | Ubicación solo en Redis con TTL, solo al cliente, solo en camino y redondeada; se borra al terminar; `Cache-Control: no-store` |
| RNF16 Trazabilidad | ☑ | Cronología inmutable |
| RNF17 Oportunidad | ☑ | Sondeo y notificaciones |
| RNF22 Geografía | ☑ | Coordenadas del domiciliario en Redis, sin PostGIS |
| RNF24 Errores claros | ☑ | `INVALID_STATE` si se envía la ubicación fuera de `EN_CAMINO` |
| RNF28 Conectividad | ☑ | Última cronología en SQLite |
| RNF30 Datos móviles | ☑ | Mensajes pequeños cada 15 s solo en camino |
| RNF31 Gama baja | ☑ | Ubicación solo en primer plano |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| EventoTrazabilidad | Lee | Cronología ordenada; el autor se guarda pero no se expone |
| Domicilio | Lee | Estado y motivo de cancelación |
| Pedido | Lee (vía puerto) | Comprador y dueño para la pertenencia |
| Ubicación del domiciliario | Escribe y lee en Redis `loc:{domicilioId}` (no es una entidad del modelo) | TTL 2 min; se elimina al entregar o cancelar |

## State Transitions

No cambia estados de pedido, domicilio, pago ni reporte (la ubicación en vivo no es un estado).

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /domicilios/{id}/trazabilidad` | CLIENTE; VENDEDOR_PUNTO_FIJO | Cliente: comprador del pedido; vendedor: dueño del pedido; otros, `404` |
| `PUT /domicilios/{id}/ubicacion-domiciliario` | DOMICILIARIO | Domicilio asignado a él; si es de otro, `403` |

## API Contracts

### GET /api/v1/domicilios/{id}/trazabilidad

- **Rol**: cliente o vendedor de punto fijo (partes del pedido) · **Path params**: `id` (UUID) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "domicilioId": "…", "estado": "EN_CAMINO", "motivoCancelacion": null, "updatedAt": "…",
  "eventos": [
    { "estado": "DISPONIBLE", "ocurridoAt": "2026-10-25T14:00:00Z" },
    { "estado": "ASIGNADO",   "ocurridoAt": "2026-10-25T14:03:10Z" },
    { "estado": "RECOGIDO",   "ocurridoAt": "2026-10-25T14:12:40Z" },
    { "estado": "EN_CAMINO",  "ocurridoAt": "2026-10-25T14:13:05Z" }
  ],
  "ubicacionDomiciliario": { "latitud": 11.245, "longitud": -74.206, "actualizadaAt": "2026-10-25T14:20:10Z" }
}
```

`ubicacionDomiciliario` solo aparece para el cliente y con el domicilio `EN_CAMINO`; es `null` si la clave expiró o no se ha enviado. Las coordenadas se redondean a 3 decimales (≈ 110 m, "aproximada"). La respuesta no incluye ni el código de confirmación ni el usuario autor de cada evento.

- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de cliente o vendedor de punto fijo | — |
| 404 | `NOT_FOUND` | Domicilio inexistente o de un pedido que no es suyo | "El domicilio no está disponible" |

### PUT /api/v1/domicilios/{id}/ubicacion-domiciliario

- **Rol**: domiciliario asignado · **Path params**: `id` (UUID) · **Query params**: ninguno
- **Cabeceras**: `Authorization` ✅, `Content-Type: application/json`. Sin `Idempotency-Key`: reemplaza un dato efímero (spec-general §2.4).
- **Body**: `{ "latitud": 11.2453, "longitud": -74.2061 }`
- **Respuestas exitosas**: `204` sin cuerpo.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Coordenadas ausentes o fuera de rango | Indica el campo |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de domiciliario o domicilio asignado a otro | — |
| 404 | `NOT_FOUND` | El domicilio no existe | "El domicilio no está disponible" |
| 409 | `INVALID_STATE` | El domicilio no está `EN_CAMINO` | `details.estadoActual` |
| 429 | `RATE_LIMITED` | Más de un envío cada 5 s | `Retry-After` |
| 503 | `EXTERNAL_SERVICE_UNAVAILABLE` | Redis no responde | `Retry-After` |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | La trazabilidad se alimenta con filas insertadas por cada caso de uso de transición (RF-14, RF-19, RF-23, RF-24), no por eventos |

**Eventos que consume esta feature**: ninguno. La app refresca la pantalla al recibir las notificaciones de E09 a E12 (RF-38).

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: el cliente y el vendedor ven la última cronología guardada en SQLite con la hora de actualización; la ubicación en vivo no se guarda localmente. La app del domiciliario descarta las lecturas que no pudo enviar (no las reenvía en lote) y retoma con la siguiente.
- **Concurrencia (RNF08)**: no aplica (el último envío sobrescribe la clave de Redis).
- **Idempotencia (RNF27)**: no aplica en el `PUT`; el límite de un envío cada 5 s protege contra ráfagas.
- **Refresco del cliente**: 15 s con el domicilio `EN_CAMINO` y la pantalla abierta, 30 s en otros estados y refresco inmediato ante notificaciones de domicilio.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Domicilio cancelado | Estado `CANCELADO` con su motivo | US1-E3 |
| Domiciliario sin ubicación reciente | `ubicacionDomiciliario: null`; la app muestra "Sin ubicación reciente" | Edge 1–2 |
| Usuario ajeno | `404` | FR-006 |
| Envío de ubicación fuera de `EN_CAMINO` | `409 INVALID_STATE` | RNF04 |
| Redis caído | La consulta devuelve la cronología sin ubicación; el `PUT` responde `503` | RNF15 |
| Sin conexión | Última cronología guardada | RNF28 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Integración | Endpoints con Testcontainers (PostGIS y Redis): cronología por cada estado, cliente con ubicación en camino, ubicación nula sin envío o expirada, cancelado con motivo, vendedor sin ubicación, usuarios ajenos, `PUT` (válido, fuera de estado, otro domiciliario, límite de envío, coordenadas inválidas), borrado de la clave al entregar o cancelar | US1-E1–E3, US2-E1, FR-001–FR-006 |
| Unitarias | Redondeo de coordenadas y armado del DTO | FR-003 |
| Contrato | La respuesta no contiene el código ni el usuario autor | RNF04 |
| Frontend | Jest: línea de tiempo, mapa con el domiciliario, "Sin ubicación reciente", sondeo de 15/30 s, envío de ubicación cada 15 s solo en camino, caché sin red | SC-001, FR-005 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-26-estado-y-trazabilidad-del-domicilio/
├── RF-26-estado-y-trazabilidad-del-domicilio.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/domicilios/
├── application/
│   ├── ports/in/                 # ConsultarTrazabilidadPort, ActualizarUbicacionDomiciliarioPort
│   ├── ports/out/                # EventoTrazabilidadRepositoryPort, UbicacionEnVivoPort (Redis), PedidoParaDomicilioPort
│   └── usecase/                  # ConsultarTrazabilidadUseCase, ActualizarUbicacionDomiciliarioUseCase
└── infrastructure/adapters/
    ├── in/web/                   # DomicilioController (GET trazabilidad, PUT ubicacion-domiciliario), DTO
    └── out/
        ├── persistence/          # lectura de evento_trazabilidad
        └── cache/                # UbicacionEnVivoRedisAdapter (loc:{domicilioId}, TTL 2 min)
backend/src/main/resources/application.yml   # domicilio.ubicacion.ttl_segundos: 120, domicilio.ubicacion.min_intervalo_segundos: 5
backend/src/test/java/co/edu/napa/domicilios/

frontend/src/app/(cliente)/pedidos/[id]/domicilio.tsx            # ruta Expo Router: trazabilidad, mapa y código (RF-25)
frontend/src/app/(vendedor)/domicilios/[id].tsx                  # ruta Expo Router: estado del domicilio y "confirmar salida" (RF-23)
frontend/src/components/domicilios/LineaTiempoDomicilio.tsx      # cronología de estados
frontend/src/components/domicilios/MapaDomiciliario.tsx          # ubicación aproximada del domiciliario
frontend/src/hooks/useRefrescoDomicilio.ts                       # sondeo de 15/30 s y refresco por notificación
frontend/src/hooks/useEnvioUbicacionDomiciliario.ts              # envía la posición cada 15 s solo con el domicilio EN_CAMINO
frontend/src/services/domicilios.ts                              # obtenerTrazabilidad(), enviarUbicacion()
frontend/src/database/schema/trazabilidad_cache.ts
frontend/__tests__/domicilios/trazabilidad.test.tsx
```

**Structure Decision**: módulo `domicilios`. La ubicación en vivo no es una entidad: es un valor efímero en Redis detrás de `UbicacionEnVivoPort` (foundations, geografía). La cronología se lee de `evento_trazabilidad`, que cada caso de uso de transición llena en su propia transacción; este plan no escribe en ella. El vendedor y el cliente comparten el endpoint y el caso de uso, que decide por rol qué campos devuelve.

## Phase 1: Setup

- [ ] T001 Confirmar `evento_trazabilidad` y los eventos que insertan RF-14, RF-19, RF-23 y RF-24; parámetros `domicilio.ubicacion.ttl_segundos: 120` y `domicilio.ubicacion.min_intervalo_segundos: 5` en `backend/src/main/resources/application.yml`

## Phase 2: Foundational

- [ ] T002 `UbicacionEnVivoPort` y `UbicacionEnVivoRedisAdapter` (`loc:{domicilioId}`, TTL, límite de envío) en `domicilios/application/ports/out/` y `.../infrastructure/adapters/out/cache/`
- [ ] T003 Eliminación de `loc:{domicilioId}` al entregar (RF-24) y al cancelar (RF-13) por `UbicacionEnVivoPort.eliminar`

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Cliente sigue su domicilio en todo momento (Priority: P1)

**Goal**: el cliente sabe en qué etapa va su pedido y dónde está el domiciliario.
**Independent Test**: avanzar un domicilio por sus estados y ver la cronología y la ubicación actualizadas.
**Cubre**: FR-001–FR-003, FR-005–FR-006, SC-001–SC-002, US1-E1–E3, Edge 1–3.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /domicilios/{id}/trazabilidad` del cliente en cada estado (cronología ordenada, sin código ni autor) en `backend/src/test/java/co/edu/napa/domicilios/ConsultarTrazabilidadIT.java`
- [ ] T011 [P] [US1] Test de la ubicación del domiciliario: presente en `EN_CAMINO` con coordenadas redondeadas, nula sin envío o expirada (reloj controlado), ausente en otros estados, en `.../domicilios/TrazabilidadUbicacionIT.java`
- [ ] T020 [P] [US1] Test de domicilio `CANCELADO` con motivo y de entregado (cronología completa) en `.../domicilios/TrazabilidadCanceladoEntregadoIT.java`
- [ ] T021 [P] [US1] Test de que la cronología de un domicilio entregado sigue consultable en `.../domicilios/TrazabilidadEntregadoIT.java`
- [ ] T019 [P] [US1] Test de usuarios ajenos (`404`), roles no habilitados (`403`) y aislamiento entre clientes en `.../domicilios/TrazabilidadAccesoIT.java`
- [ ] T022 [P] [US1] Test de `PUT /domicilios/{id}/ubicacion-domiciliario` (`204`, TTL, fuera de `EN_CAMINO` `409`, otro domiciliario `403`, coordenadas inválidas `400`, límite de envío `429`, Redis caído `503`) y de borrado de la clave al entregar y cancelar en `.../domicilios/ActualizarUbicacionIT.java`
- [ ] T030 [P] [US1] Test de la pantalla del cliente (línea de tiempo, mapa, "Sin ubicación reciente", cancelado, caché sin red) en `frontend/__tests__/domicilios/trazabilidad.test.tsx`
- [ ] T031 [P] [US1] Test del hook de envío (cada 15 s solo con el domicilio en camino y en primer plano, descarta lecturas fallidas) y del sondeo de 15/30 s en `frontend/__tests__/domicilios/envio-ubicacion.test.tsx`

### Implementation

- [ ] T013 [US1] `ConsultarTrazabilidadUseCase` (pertenencia por rol, campos por rol) y su puerto en `domicilios/application/`
- [ ] T014 [US1] `GET /domicilios/{id}/trazabilidad` y su DTO en `DomicilioController` con `Cache-Control: no-store`
- [ ] T015 [US1] Armado de la cronología desde `EventoTrazabilidadRepositoryPort` (orden por `ocurridoAt`; sin autor)
- [ ] T016 [US1] `ActualizarUbicacionDomiciliarioUseCase` y `PUT /domicilios/{id}/ubicacion-domiciliario` (asignado, `EN_CAMINO`, límite de envío)
- [ ] T017 [US1] Redondeo de coordenadas y lectura de la ubicación de Redis en la consulta del cliente
- [ ] T032 [US1] Servicio `obtenerTrazabilidad()` con caché en `frontend/src/services/domicilios.ts` y `frontend/src/database/schema/trazabilidad_cache.ts`; hook `useRefrescoDomicilio` en `frontend/src/hooks/`
- [ ] T033 [US1] `LineaTiempoDomicilio` y `MapaDomiciliario` en `frontend/src/components/domicilios/` y pantalla `frontend/src/app/(cliente)/pedidos/[id]/domicilio.tsx`
- [ ] T034 [US1] Hook `useEnvioUbicacionDomiciliario` y `enviarUbicacion()` integrados en `frontend/src/app/(domiciliario)/domicilios/[id].tsx` (solo con el domicilio propio `EN_CAMINO` y la app en primer plano)

## Phase 4: User Story 2 - Vendedor de punto fijo sigue lo que despachó (Priority: P2)

**Goal**: el vendedor conoce el estado y el último cambio de sus domicilios.
**Independent Test**: consultar el estado de un domicilio en curso como vendedor.
**Cubre**: FR-004, US2-E1.

### Tests (antes de implementar)

- [ ] T012 [P] [US2] Test de integración de la rama del vendedor (estado y cronología, sin ubicación en vivo, `404` si el pedido es de otro emprendimiento) en `.../domicilios/ConsultarTrazabilidadVendedorIT.java`
- [ ] T023 [P] [US2] Test de la pantalla del vendedor (estado, último cambio, acción de confirmar salida de RF-23) en `frontend/__tests__/domicilios/trazabilidad-vendedor.test.tsx`

### Implementation

- [ ] T018 [US2] Rama de vendedor en `ConsultarTrazabilidadUseCase` (dueño del pedido; sin ubicación)
- [ ] T035 [US2] Pantalla `frontend/src/app/(vendedor)/domicilios/[id].tsx` con la línea de tiempo y la acción de RF-23

**Checkpoint**: US1 y US2 funcionales.

## Phase N: Polish & Cross-Cutting

- [ ] T040 Medir que el cambio de estado llega al cliente y al vendedor en < 30 s (SC-001)
- [ ] T041 Revisar privacidad de la ubicación: solo en Redis, solo al cliente y solo en camino (RNF04)
- [ ] T042 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → Polish.
- Dependencias entre RF: requiere RF-14, RF-19, RF-23 y RF-24 (insertan los eventos); la pantalla del cliente aloja el código de RF-25 (se construye primero esta pantalla); US2 es P2 y no bloquea el ciclo del domicilio.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| "Ubicación aproximada" (FR-003): la spec no dice qué tan aproximada | Privacidad | Redondeo a 3 decimales (≈ 110 m) | Equipo |
| El vendedor no ve la ubicación en vivo (la spec solo la pide para el cliente) | Alcance | Solo estado y cronología | Equipo |
| El envío de la ubicación solo funciona con la app en primer plano; en segundo plano se detiene (el seguimiento en segundo plano exige un servicio en primer plano y más batería en gama baja) | RNF31 | Solo primer plano en el MVP | Equipo |
| Edge 3: retención de la cronología de un domicilio entregado | Datos | Sin vencimiento | Equipo |
| `PUT /ubicacion-domiciliario` limita a un envío cada 5 s (`RATE_LIMITED`) | Abuso | Así | Equipo |
| El cliente llega a la pantalla desde el bloque `domicilio` del detalle del pedido (RF-48); el vendedor, desde el detalle (RF-51); el listado de domicilios del vendedor (RF-12/15) es P2 | UX | Solo desde el detalle | Equipo |
| Se unifican las pantallas del cliente (trazabilidad y código) en `(cliente)/pedidos/[id]/domicilio` | Estructura del frontend | Así | Equipo |

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
# Implementation Plan: Creación de emprendimiento

**Date**: 2026-10-21
**Spec**: [RF-28-creacion-de-emprendimiento.md](./RF-28-creacion-de-emprendimiento.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-28
**Historias de usuario**: HU-53
**Prioridad (la mayor de la spec)**: P1
**Depende de**: RF-07, RF-10 (sesión y rol), foundations F020 (`ubicacion`, PostGIS)
**Habilita a**: RF-29, RF-30, RF-31, RF-32, RF-45, RF-54, RF-63

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**: si algo cambia de comportamiento, se corrige primero la spec.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

Un vendedor (ambulante o de punto fijo) crea su emprendimiento con nombre, categoría de una lista fija, descripción, hora de apertura y de cierre y una ubicación. La dirección es obligatoria para punto fijo y opcional para ambulante, cuya ubicación es una zona aproximada con referencia. Se crea `ACTIVO` y visible de inmediato. Un índice único parcial en BD garantiza un solo emprendimiento `ACTIVO` por vendedor, incluso con dos solicitudes simultáneas. El tipo de vendedor no se guarda: sale de `Usuario.rol`.

## Estado actual del código

El backend de RF-28 ya tiene dominio, caso de uso, endpoint protegido, persistencia y migración V6. El frontend incluye servicio autenticado, validación de formulario, selección de categoría, horario, coordenadas y dirección condicional por rol; las pruebas del formulario y del contrato HTTP pasan. Las pruebas PostgreSQL/PostGIS de Testcontainers siguen pendientes de ejecución porque el motor Docker no está disponible. El listado público de RF-31 sigue pendiente. El modelo `Ubicacion` y su puerto de persistencia están en `shared`; el adaptador JPA existente en `cuentas` los implementa.

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | No interviene (consume el resultado en RF-31/RF-32) |
| Vendedor (ambulante / punto fijo) | Crea su emprendimiento |
| Domiciliario | No interviene |
| Administrador | No interviene |

## Technical Context

Heredado de `specs/templates/plan-template.md` (stack fijo del proyecto). Objetivos propios, tomados de la spec:

- Crear el emprendimiento en < 3 minutos de uso (SC-001): formulario de una pantalla, sin campos extra.
- 100 % de emprendimientos válidos visibles de inmediato (SC-002): `estado = ACTIVO` desde la creación, sin paso de aprobación.
- Operación principal ≤ 3 s (RNF07).

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Crear con nombre, categoría (lista fija), ubicación, descripción, hora de apertura y de cierre | `POST /emprendimientos`; `CrearEmprendimientoUseCase`; enum `CategoriaEmprendimiento` | T010, T013, T014 |
| FR-002 | Validar campos obligatorios | Bean Validation en el DTO + validaciones de dominio (`Emprendimiento`) | T011, T015 |
| FR-003 | Impedir un segundo emprendimiento activo | Consulta previa + índice único parcial `uq_emprendimiento_activo_por_usuario` → `409 CONFLICT` | T012, T016 |
| FR-004 | Visible de inmediato | `estado = ACTIVO` al crear; RF-31/RF-32 lo leen sin paso extra | T010 |
| FR-005 | Interpretar la ubicación según el rol | Punto fijo exige `direccion`; ambulante permite zona habitual (punto + referencia) sin dirección | T011, T017, T018 |
| FR-006 | Asociar el tipo de vendedor sin pedirlo | No se recibe en el body: se deduce de `Usuario.rol` del JWT; la respuesta lo devuelve como `tipoVendedor` | T013 |
| SC-001 | < 3 min | Pantalla única de formulario; prueba de usabilidad en piloto | T031 |
| SC-002 | 100 % visibles | Prueba de integración: crear y listar | T010 |
| US1-E1 | Creación exitosa | Endpoint + persistencia | T010 |
| US1-E2 | Segundo emprendimiento activo | `409 CONFLICT` con mensaje "Ya tienes un emprendimiento activo" | T012 |
| Edge 1 | Campos obligatorios vacíos | `400 VALIDATION_ERROR` indicando el campo | T011 |
| Edge 2 | Ubicación inválida o fuera de cobertura | Validación de rango lat/lng; la cobertura es **Decisión pendiente** (Open Questions) | T011 |
| Edge 3 | Nombre ya usado en la misma zona | **Decisión pendiente**: el modelo no define unicidad de nombre | — |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF01 Seguridad | ☑ | Rol y propietario salen del JWT, nunca del body |
| RNF03 Autorización | ☑ | `@PreAuthorize` solo para los dos roles de vendedor |
| RNF04 Privacidad | ☑ | La zona del ambulante es un punto aproximado elegido por él |
| RNF07 Rendimiento | ☑ | Una transacción corta (inserta `ubicacion` y `emprendimiento`) |
| RNF08 Concurrencia | ☑ | Índice único parcial resuelve la carrera |
| RNF09 Flujos cortos | ☑ | Formulario de una pantalla |
| RNF16 Trazabilidad | ☑ | `created_at`, `updated_at` |
| RNF22 Geografía | ☑ | `geography(Point,4326)` derivada de latitud y longitud |
| RNF23 Consistencia | ☑ | Estados solo `ACTIVO`/`INACTIVO`/`ELIMINADO` |
| RNF24 Errores claros | ☑ | Mensajes en español con el campo inválido |
| RNF27 Integridad | ☑ | `Idempotency-Key` obligatoria |
| RNF28 Conectividad | ☑ | La creación requiere conexión en el MVP (ver Open Questions) |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Emprendimiento | Escribe | Solo uno `ACTIVO` por usuario (índice único parcial); sin `tipo_vendedor` |
| Ubicacion | Escribe | Cada uso crea su propia fila; `Emprendimiento` ↔ `Ubicacion` 1:1 |
| Usuario | Lee (rol, estado) | Solo roles vendedor |

## State Transitions

| Entidad | Desde | Hacia | Quién lo dispara | Precondiciones | Evento |
|---|---|---|---|---|---|
| Emprendimiento | (nuevo) | `ACTIVO` | Vendedor | Sin otro `ACTIVO` | Ninguno |

**Transiciones inválidas**: crear con otro `ACTIVO` → `409 CONFLICT`.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| Crear emprendimiento | VENDEDOR_AMBULANTE, VENDEDOR_PUNTO_FIJO | El emprendimiento queda a nombre del usuario autenticado |

## API Contracts

### POST /api/v1/emprendimientos

- **Rol**: vendedor (ambulante o de punto fijo)
- **Path params**: ninguno · **Query params**: ninguno
- **Body**:

```json
{
  "nombre": "Frutas Doña Rosa",
  "categoria": "FRUTAS_VERDURAS",
  "descripcion": "Frutas frescas del día",
  "horaApertura": "06:00",
  "horaCierre": "18:00",
  "ubicacion": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Calle 15 # 3-20", "referencia": "Frente al parque" }
}
```

`ubicacion.direccion` es obligatoria para `VENDEDOR_PUNTO_FIJO`; para `VENDEDOR_AMBULANTE` puede omitirse o ser `null`.

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `201` con `Location: /api/v1/emprendimientos/{id}`, `X-Request-Id` y body `{ "id", "nombre", "categoria", "descripcion", "tipoVendedor", "horaApertura", "horaCierre", "estado": "ACTIVO", "ubicacion": {...} }`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Campo obligatorio vacío, categoría fuera de la lista, hora con formato inválido, latitud/longitud fuera de rango | Indica el campo (`details`) |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Rol distinto de vendedor | "No tienes permiso" |
| 409 | `CONFLICT` | Ya existe un emprendimiento `ACTIVO` del vendedor | "Ya tienes un emprendimiento activo" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con cuerpo distinto | — |

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | El catálogo (`eventos.md`) no tiene eventos de emprendimiento |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la creación del emprendimiento se hace una sola vez y exige conexión; si falla, el formulario conserva lo escrito y se reintenta con la misma `Idempotency-Key`. No entra al outbox en el MVP (decisión provisional).
- **Concurrencia (RNF08)**: dos creaciones simultáneas: una tiene éxito y la otra viola el índice único parcial y recibe `409 CONFLICT`.
- **Idempotencia (RNF27)**: `idem:{userId}:{key}` en Redis; el reintento con la misma clave y cuerpo devuelve el `201` guardado con `Idempotent-Replayed: true`.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Segundo emprendimiento activo | `409 CONFLICT` | US1-E2, FR-003 |
| Campos vacíos | `400 VALIDATION_ERROR` con el campo | Edge 1 |
| Ubicación inválida | `400 VALIDATION_ERROR` | Edge 2 |
| Carrera de dos creaciones | La segunda recibe `409` por el índice único | RNF08 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitarias | Reglas de `Emprendimiento` (horas, dirección según rol) | FR-002, FR-005 |
| Integración | Endpoint con PostGIS en Testcontainers; índice único; idempotencia | US1-E1, US1-E2, SC-002 |
| Arquitectura | ArchUnit del módulo `emprendimientos` | RNF13, RNF25 |
| Frontend | Jest + React Native Testing Library del formulario | Edge 1, US1-E2 |
| E2E | Registrar vendedor, crear emprendimiento, verlo en el listado (RF-31) | SC-002 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-28-creacion-de-emprendimiento/
├── RF-28-creacion-de-emprendimiento.md   # spec
└── RF-28-plan.md                         # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/emprendimientos/
├── domain/
│   └── model/                    # Emprendimiento, CategoriaEmprendimiento, EstadoEmprendimiento
├── application/
│   ├── ports/in/                 # CrearEmprendimientoPort
│   ├── ports/out/                # EmprendimientoRepositoryPort
│   └── usecase/                  # CrearEmprendimientoUseCase, command y result
└── infrastructure/adapters/
    ├── in/web/                   # EmprendimientoController, DTOs
    └── out/persistence/          # EmprendimientoJpaEntity, repositorio Spring Data, adaptador
backend/src/main/resources/db/migration/   # V6__emprendimiento.sql
backend/src/test/java/co/edu/napa/emprendimientos/
backend/src/main/java/co/edu/napa/shared/domain/geo/Ubicacion.java
backend/src/main/java/co/edu/napa/shared/application/ports/out/UbicacionRepositoryPort.java

frontend/src/app/(vendedor)/emprendimiento/crear.tsx   # ruta Expo Router (formulario)
frontend/src/components/emprendimiento/                # FormularioEmprendimiento, SelectorCategoria, SelectorHorario, SelectorUbicacion
frontend/src/services/emprendimientos.ts               # cliente Axios
frontend/src/domain/emprendimiento.ts                  # tipos y enum de categorías
frontend/src/hooks/useLocation.ts                      # expo-location (permiso y posición actual)
frontend/__tests__/emprendimientos/crear.test.tsx
```

**Structure Decision**: módulo `emprendimientos`. Dominio sin Spring/JPA; el controlador solo llama a `CrearEmprendimientoPort`. `SelectorUbicacion` usa `react-native-maps` para fijar el punto y `expo-location` para proponer la posición actual.

## Phase 1: Setup

- [x] T001 Crear el paquete `co.edu.napa.emprendimientos` y la regla ArchUnit del módulo en `backend/src/test/java/co/edu/napa/ArchitectureTest.java`

## Phase 2: Foundational

- [x] T002 Migración `V6__emprendimiento.sql`: volver nullable `ubicacion.direccion`; crear tabla `emprendimiento` (id, usuario_id, ubicacion_id único, nombre, categoria, descripcion, hora_apertura `time`, hora_cierre `time`, estado, created_at, updated_at) e índice único parcial `uq_emprendimiento_activo_por_usuario` donde `estado = 'ACTIVO'` en `backend/src/main/resources/db/migration/`
- [x] T003 Mover `Ubicacion` a `shared/domain/geo` y `UbicacionRepositoryPort` a `shared/application/ports/out`; adaptar imports y hacer que el adaptador JPA existente implemente el puerto compartido.

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Publicar un emprendimiento en la plataforma (Priority: P1)

**Goal**: el vendedor crea su emprendimiento y queda visible.
**Independent Test**: registrar un vendedor, crear el emprendimiento y verlo en el listado sin productos ni pedidos.
**Cubre**: FR-001–FR-006, SC-001–SC-002, US1-E1–E2, Edge 1–2.

### Tests (antes de implementar)

- [x] T010 [P] [US1] Testcontainers para migración, persistencia activa y ubicación sin dirección en `backend/src/test/java/co/edu/napa/emprendimientos/infrastructure/persistence/EmprendimientoPersistenceIntegrationTest.java`; consultar el listado público queda condicionado a RF-31
- [x] T011 [P] [US1] Pruebas de validación del DTO, coordenadas y dirección por rol en `backend/src/test/java/co/edu/napa/emprendimientos/infrastructure/adapters/in/web/EmprendimientoControllerTest.java` y `backend/src/test/java/co/edu/napa/emprendimientos/application/usecase/CrearEmprendimientoUseCaseTest.java`
- [x] T012 [P] [US1] Pruebas de segundo emprendimiento activo y carrera concurrente en `backend/src/test/java/co/edu/napa/emprendimientos/infrastructure/persistence/EmprendimientoPersistenceIntegrationTest.java`
- [x] T017 [P] [US1] Test unitario de reglas de `Emprendimiento` (horario que cruza medianoche y rechazo de horas iguales) en `backend/src/test/java/co/edu/napa/emprendimientos/domain/model/EmprendimientoTest.java`
- [x] T019 [P] [US1] Pruebas del formulario (campos obligatorios, error 409, solicitud y denegación de permiso de ubicación del dispositivo) y del servicio (envío con `Idempotency-Key`) en `frontend/src/app/__tests__/emprendimiento.test.tsx`, `frontend/src/lib/location.test.ts` y `frontend/src/lib/emprendimientos.test.ts`

### Implementation

- [x] T013 [US1] Dominio `Emprendimiento` y `CrearEmprendimientoUseCase` en `emprendimientos/domain/model/` y `emprendimientos/application/usecase/`
- [x] T014 [US1] `EmprendimientoController` (`POST /emprendimientos`) en `emprendimientos/infrastructure/adapters/in/web/`
- [x] T015 [US1] Validaciones del DTO y mapeo de errores a `VALIDATION_ERROR`
- [x] T016 [US1] Adaptador de persistencia y traducción de la violación del índice único a `CONFLICT` en `emprendimientos/infrastructure/adapters/out/persistence/`
- [x] T018 [US1] Guardar `Ubicacion` con la columna `geography` derivada de latitud y longitud
- [x] T020 [US1] Servicio `createEmprendimiento()` autenticado y con `Idempotency-Key` en `frontend/src/lib/emprendimientos.ts`
- [x] T021 [US1] Formulario accesible con validación de campos, categoría, horario, solicitud de permiso de ubicación del dispositivo (con fallback manual) en `frontend/src/app/emprendimiento.tsx` y servicio reutilizable en `frontend/src/lib/location.ts`
- [x] T022 [US1] Pantalla de creación de emprendimiento y acceso desde la sesión del vendedor; presenta el resultado `201` y permite volver al panel en `frontend/src/app/emprendimiento.tsx` y `frontend/src/app/session.tsx`

**Checkpoint**: US1 funcional y probable por separado.

## Phase N: Polish & Cross-Cutting

- [x] T030 Revisar privacidad de la ubicación aproximada de ambulantes y autorización exclusiva de vendedores (RNF03, RNF04)
- [ ] T031 Medir el tiempo de creación en el piloto (SC-001)
- [x] T032 Actualizar el enlace de RF-28 en `requerimientos/trazabilidad.md`

## Dependencies & Execution Order

- Setup → Foundational → US1 → Polish.
- Dependencias entre RF: requiere RF-07 y RF-10; habilita RF-29, RF-30, RF-31, RF-32, RF-54 y RF-63.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| Cobertura del servicio (Edge 2): ¿qué área es válida (p. ej. Santa Marta)? | Validación de ubicación | Solo validar rango de latitud/longitud | Equipo |
| Nombre repetido (Edge 3): el modelo no pide unicidad de nombre ni define "zona" | Duplicados | Permitir nombres repetidos | Equipo |
| `direccion` obligatoria para punto fijo (RF-32 la muestra) y opcional para ambulante (solo zona y referencia) | FR-005 | Aprobado: requerirla solo para punto fijo; cambiar el modelo y esquema compartidos para permitir `NULL` | Usuario |
| `horaApertura` igual a `horaCierre` | Horario | Rechazar (400) | Equipo |
| Límites de longitud de nombre (100) y descripción (500) | Validación | Esos valores | Equipo |
| ¿Quién es dueño de `UbicacionRepositoryPort` (hoy en `cuentas`)? | Dependencia entre módulos | Aprobado: mover el modelo `Ubicacion` y su puerto juntos a `shared`; el adaptador JPA de `cuentas` implementa el puerto compartido | Usuario |
| La app necesita saber si el vendedor ya tiene emprendimiento al iniciar sesión; no hay endpoint (`GET /emprendimientos/me` sería de RF-29) | Navegación | Detectarlo por el `409` al crear | Equipo |
| ¿Crear el emprendimiento va por el outbox offline? | RNF28 | No, requiere conexión | Equipo |

## Plan Review Checklist

*(Todo debe estar marcado antes de implementar.)*

- [x] La tabla Spec Coverage no tiene filas vacías
- [x] Todas las tareas de implementación y de testing están presentes
- [x] La arquitectura es coherente con los módulos existentes (RNF13, RNF25); solo hay `usecase`, sin capa `service` de aplicación
- [x] Entidades referenciadas a `spec-general.md` (sin redefinir el modelo); la opcionalidad de dirección y el cambio de esquema compartido están documentados
- [x] Contratos de API con path/query params, headers, códigos HTTP exitosos y de error, y matriz de permisos
- [x] Eventos emitidos documentados en `eventos.md`; ningún listener planificado en este plan
- [x] Actores (comprador, vendedor, domiciliario, administrador) identificados
- [x] Errores y casos borde tienen comportamiento definido
- [x] Estrategia de conectividad y concurrencia definida cuando aplica (RNF28, RNF08)
- [x] Dependencias y librerías identificadas
- [x] Cada tarea es granular y nombra el archivo que toca
- [x] No se agregó comportamiento que no esté en la spec

## Notes

- `[USn]` en cada tarea enlaza con su historia de usuario.
- Hacer commit por tarea o grupo lógico.
- Evitar tareas vagas y conflictos en un mismo archivo.
## Corrección de auditoría P0-03 (2026-11-24)

- La creación usa el componente compartido `frontend/src/components/TimeField.tsx`: teclado numérico, separador automático, rechazo de letras y de horas fuera de 00:00 a 23:59, y aviso al abandonar una hora incompleta.
- Se conserva la validación del formulario antes del envío y el contrato HH:mm. No se impone que apertura sea anterior a cierre: los horarios nocturnos no cambian de alcance.
- La edición RF-29 reutiliza el mismo control. Se cubren medianoche, 23:59, pegado, borrado y valores inválidos mediante pruebas del componente y de ambas pantallas.

## Categoría personalizada en Otros (P2-01, 2026-11-24)

- Campo opcional `categoriaPersonalizada`, 1–80 caracteres tras strip, exclusivo de OTROS. Se conserva el enum y los contratos antiguos que omiten el campo.
- Migración nueva V21 agrega `emprendimiento.categoria_personalizada`, nullable y con CHECK de coherencia; no modifica V1–V20. La validación de dominio impide combinaciones inválidas. JPA y la proyección JDBC conservan el campo; la API lo devuelve en creación/consulta propia/listado/detalle.
- Frontend: campo condicional al crear y presentación en consultas, con fallback a Otros para registros y cachés previos. No se habilita edición de categoría.
- Pruebas acotadas: dominio y preservación al editar; persistencia y listado con PostgreSQL/Testcontainers; formulario y regresiones del módulo. La migración se verifica en una base de prueba aislada, sin tocar la base local del usuario.


## Acuerdo posterior al testing en vivo (2026-11-25)

El usuario autoriza revisar las reglas anteriores según el tester: (1) el ambulante no registra dirección/referencia ni coordenadas manuales de negocio; publica su posición GPS con permiso explícito y actualiza su punto mientras usa la app, con control para pausar. No se guarda historial de recorridos ni se promete ubicación con app cerrada. (2) se elimina el límite comercial de 20 productos y 20 unidades; se conservan cantidad positiva, dos decimales, precisión NUMERIC(10,2), stock inmediato, total representable y productos únicos. Nueva migración incremental elimina el CHECK <=20, sin reescribir V11. (3) autocompletar dirección desde borrador no vacío, última entrega y perfil vigente, en ese orden, sin reemplazar lo escrito ni usar GPS como dirección del cliente. (4) reservas aceptadas con transferencia permiten confirmación anticipada por el vendedor antes de entrega/recogida; no procesa dinero. Una entrega posterior reutiliza el pago confirmado sin duplicar evento/cobro. Reservas en efectivo conservan cobro presencial. (5) avisos flotantes globales dentro de la app al llegar notificaciones nuevas, por sondeo activo de bandeja más push existente. No marcar como leídas al avisar, no mostrar historial inicial como novedad, separar sesiones y detener consultas al salir. Push con app cerrada sigue dependiendo de configuración nativa/Firebase.


## Ubicación voluntaria en segundo plano (2026-11-25, base 4130e4a)

El usuario autoriza continuar actualizando la posición del ambulante sin tener Ñapa en primer plano y poder activarla/desactivarla. Consentimiento separado: crear el negocio conserva solo primer plano; desde Mi emprendimiento se solicita explícitamente permiso en segundo plano. Android muestra servicio/notificación persistente e iOS indicador de ubicación. No se pide permiso desde una tarea ni se reactiva un consentimiento revocado. Pausar/cerrar sesión invalida primero la preferencia y detiene la tarea. Sesión, cuenta, rol y negocio deben coincidir en cada envío; no publicar lotes históricos ni posiciones de más de dos minutos. No se promete funcionamiento tras forzar detención/cierre o restricciones del sistema. Web no admite este modo. Requiere expo-task-manager compatible SDK58 y nuevo build nativo; Expo Go no valida segundo plano.

Diseño: preferencia y publicación sin React compartidas por el bucle visible y una tarea TaskManager definida en módulo global/importada desde _layout. La tarea procesa solo el punto más reciente, sin cola histórica. Configuración CNG habilita permisos/modos nativos y servicio. SecureStore mantiene cifradas la sesión y preferencia, disponibles tras el primer desbloqueo del dispositivo para permitir la tarea con pantalla bloqueada; solo esas claves usan AFTER_FIRST_UNLOCK. Escrituras nativas serializadas evitan que la adaptación de accesibilidad sobrescriba una renovación de sesión concurrente. Validar permisos denegados, activación/pausa/logout, otra cuenta, puntos antiguos, serialización, tipos/lint/config resuelta y exportación web; probar el APK en dispositivo por separado.

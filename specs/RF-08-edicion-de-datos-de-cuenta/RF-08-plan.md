# Implementation Plan: Edición de datos de cuenta

**Date**: 2026-10-27
**Spec**: [RF-08-edicion-de-datos-de-cuenta.md](./RF-08-edicion-de-datos-de-cuenta.md) (misma carpeta; es la fuente de verdad)
**Spec general**: [spec-general.md](../general/spec-general.md) (roles, API, modelo y estados) · [eventos](../general/eventos.md)
**Requerimiento funcional**: RF-08
**Historias de usuario**: HU-25
**Prioridad (la mayor de la spec)**: P2
**Depende de**: RF-07, RF-10, RF-70
**Habilita a**: RF-27 (foto del domiciliario), RF-52 (contacto del cliente)

> Define **CÓMO** se implementa la spec. No redefine el **QUÉ**.
> No se implementa nada hasta cumplir la sección "Plan Review Checklist".

## Summary

El usuario consulta y edita su perfil. `GET /usuarios/me` devuelve su perfil; `PATCH /usuarios/me` cambia su nombre y su ubicación; `PUT /usuarios/me/foto` sube la foto de perfil (JPEG, PNG o WebP, máximo 2 MB); y el celular se cambia en dos pasos: `POST /usuarios/me/celular/solicitar-cambio` envía un código al número nuevo por RF-70 y `POST /usuarios/me/celular/confirmar` lo valida y recién ahí reemplaza el celular. Mientras no se confirme, sigue vigente el anterior. Los datos se validan antes de guardar y el perfil actualizado se devuelve en la misma respuesta. El correo, el rol y el estado no se editan aquí. El administrador puede editar nombre, foto y ubicación, pero no cambiar el celular (no verifica celular, restricción 18).

## Estado actual del código

`cuentas` ya tiene `Usuario`, `UsuarioJpaEntity`, `UbicacionJpaEntity` y, por RF-07 y RF-70, el registro y `VerificarCelularPort`/`ConfirmarVerificacionPort` con el propósito `CAMBIO`. RF-07 devuelve `Location: /api/v1/usuarios/me` al registrar, pero esa ruta todavía no existe (no hay ningún `GET` de perfil); este plan la crea. No hay almacenamiento de imágenes en el stack (foundations): la foto se guarda en un volumen local detrás de un puerto nuevo (decisión del equipo; Open Questions).

## Actors

| Actor | Participación en esta feature |
|---|---|
| Comprador (Cliente) | Edita su perfil y cambia su celular |
| Vendedor (ambulante / punto fijo) | Igual |
| Domiciliario | Igual |
| Administrador | Edita nombre, foto y ubicación; no cambia el celular |

## Technical Context

Heredado de `specs/templates/plan-template.md`. Objetivos propios:

- Cambios visibles en < 5 s (SC-001): la respuesta del `PATCH` ya trae el perfil nuevo y la app actualiza su caché local al recibirla.
- 100 % de datos inválidos rechazados (SC-002): validación de formato en el DTO y de reglas en el dominio, antes de tocar la base.
- Cambio de celular: el número nuevo se guarda solo en Redis (`cambio-cel:{usuarioId}`, TTL 10 min) hasta confirmar; la tabla `usuario` no cambia antes.

## Spec Coverage

| Spec | Descripción corta | Cubierto por | Tareas |
|---|---|---|---|
| FR-001 | Editar nombre, teléfono, foto y ubicación | `PATCH /usuarios/me` (nombre, ubicación), `PUT /usuarios/me/foto`, y el flujo de celular de US2 | T011, T013, T021, T023, T024 |
| FR-002 | Validar el formato antes de guardar | Bean Validation en el DTO y reglas en `Usuario`; la foto se valida por tipo real y tamaño | T012, T014, T016, T020 |
| FR-003 | Reflejar los cambios de inmediato | La respuesta trae el perfil nuevo y `GET /usuarios/me` lo confirma; la app actualiza SQLite | T010, T011, T022, T032 |
| FR-004 | Verificar el celular nuevo antes de aplicarlo | `solicitar-cambio` + `confirmar`; el celular anterior sigue vigente hasta confirmar | T040, T041, T047, T048 |
| SC-001 | Cambios visibles en < 5 s | Respuesta con el perfil nuevo | T011, T060 |
| SC-002 | 100 % de datos inválidos rechazados | Pruebas parametrizadas | T012, T014 |
| US1-E1 | Edición exitosa | `200` con el perfil actualizado | T011 |
| US1-E2 | Dato con formato inválido | `400 VALIDATION_ERROR` indicando el campo | T012 |
| US1-E3 | Cambio de celular con código | Flujo de dos pasos; solo se reemplaza al confirmar | T040, T041 |
| Edge 1 | Celular o correo ya usado por otra cuenta | Celular: `409 PHONE_ALREADY_REGISTERED` al solicitar y al confirmar; el correo no se edita aquí (Open Questions) | T043, T012 |
| Edge 2 | Foto con formato o tamaño no soportado | `400 VALIDATION_ERROR` con `details.campo = foto`; la app comprime antes de subir | T014, T034 |
| Edge 3 | El usuario no confirma el código | La clave de Redis vence y el celular anterior sigue vigente | T042 |
| Edge 4 | Ubicación que el proveedor de mapas no valida | No se consulta al proveedor: solo se valida el rango de latitud y longitud, igual que RF-28 (Open Questions) | T012 |

## Non-Functional Requirements Check

| RNF | Aplica | Cómo se cumple |
|---|:---:|---|
| RNF03 Autorización | ☑ | Todo opera sobre el usuario del JWT (`/me`); no hay `id` en la ruta |
| RNF04 Datos personales | ☑ | `Cache-Control: no-store` en el perfil; no se registran datos personales en logs |
| RNF07 Rendimiento ≤ 3 s | ☑ | Operaciones de una sola fila |
| RNF19 Interoperabilidad | ☑ | El código lo envía RF-70 detrás de `VerificarCelularPort` |
| RNF23 Consistencia | ☑ | Celular y `celular_verificado_at` cambian en una sola transacción |
| RNF24 Errores claros | ☑ | `details.campo` en cada `400` |
| RNF27 Idempotencia | ☑ | `Idempotency-Key` en los dos `POST` del celular |
| RNF28 Conectividad | ☑ | Perfil en caché local; las ediciones se reintentan, y el cambio de celular exige conexión |
| RNF31 Gama baja | ☑ | La foto se reduce y comprime en el dispositivo antes de subirla |

## Entities Involved

| Entidad (spec-general) | Lee / escribe | Restricciones |
|---|---|---|
| Usuario | Lee / escribe (`nombre`, `foto_url`, `celular`, `celular_verificado_at`) | `celular` único; el administrador no cambia celular (restricción 18) |
| Ubicacion | Lee / escribe | Una fila propia por usuario; se actualiza en su lugar (no se comparte) |

## State Transitions

No cambia estados: `Usuario.estado` no se toca. Solo edita una cuenta `ACTIVA`; una cuenta `PENDIENTE_VERIFICACION` o `ELIMINADA` no tiene sesión, así que no llega aquí.

## Authorization

| Acción | Roles permitidos | Regla de pertenencia |
|---|---|---|
| `GET /usuarios/me`, `PATCH /usuarios/me`, `PUT /usuarios/me/foto` | Todos los autenticados | Siempre sobre el usuario del JWT |
| `POST /usuarios/me/celular/solicitar-cambio` y `.../confirmar` | CLIENTE, vendedores, DOMICILIARIO | El administrador recibe `403` |

## API Contracts

### GET /api/v1/usuarios/me

- **Rol**: autenticado · **Cabeceras**: `Authorization` ✅.
- **Respuestas exitosas**: `200` con `Cache-Control: no-store`, `X-Request-Id` y body:

```json
{
  "id": "…", "nombre": "Ana Pérez", "celular": "+573001234567", "correo": null,
  "fotoUrl": "https://…/perfiles/….jpg", "rol": "CLIENTE", "estado": "ACTIVA",
  "celularVerificado": true, "createdAt": "2026-09-01T15:00:00Z",
  "ubicacion": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" }
}
```

`fotoUrl` y `ubicacion` son `null` si no se han configurado. Errores: `401 UNAUTHORIZED`.

### PATCH /api/v1/usuarios/me

- **Rol**: autenticado · **Body** (parcial; al menos un campo):

```json
{ "nombre": "Ana María Pérez", "ubicacion": { "latitud": 11.2408, "longitud": -74.2110, "direccion": "Calle 15 # 3-20", "referencia": "Casa azul" } }
```

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Content-Type | ✅ | `application/json` |

`PATCH` no exige `Idempotency-Key` (spec-general: no es una transición de estado). Aplicar el mismo cuerpo dos veces deja el mismo resultado.

- **Respuestas exitosas**: `200` con el perfil completo (mismo cuerpo del `GET`) y `Cache-Control: no-store`.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo vacío; nombre vacío o de más de 100 caracteres; latitud o longitud fuera de rango; dirección vacía; campo no editable aquí (`celular`, `correo`, `rol`, `estado`, `fotoUrl`) | `details.campo` |
| 401 | `UNAUTHORIZED` | Sin sesión | — |

### PUT /api/v1/usuarios/me/foto

- **Rol**: autenticado · **Content-Type**: `multipart/form-data` con la parte `foto` (JPEG, PNG o WebP, máximo 2 MB).
- **Respuestas exitosas**: `200` con `{ "fotoUrl": "https://…" }`. Reemplaza la foto anterior.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falta la parte `foto`, el tipo real del archivo (por su contenido, no por la extensión) no es JPEG, PNG ni WebP, o pesa más de 2 MB | `details.campo = "foto"` |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 503 | `EXTERNAL_SERVICE_UNAVAILABLE` | El almacenamiento no responde | "No pudimos guardar la foto; intenta de nuevo" |

### POST /api/v1/usuarios/me/celular/solicitar-cambio

- **Rol**: CLIENTE, vendedores, DOMICILIARIO · **Body**: `{ "celular": "+573009876543", "canal": "WHATSAPP" }` (`canal` opcional; `WHATSAPP` o `SMS`).

| Cabecera | Obligatoria | Contenido |
|---|:---:|---|
| Authorization | ✅ | `Bearer <JWT>` |
| Idempotency-Key | ✅ | UUID |
| Content-Type | ✅ | `application/json` |

- **Respuestas exitosas**: `202` con `{ "mensajeId", "canal", "reenvioDisponibleEn" }` y `Cache-Control: no-store`. El celular de la cuenta no cambia.
- **Errores**:

| HTTP | `code` | Cuándo ocurre | Mensaje |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Formato inválido, o es el mismo celular actual | `details.campo` |
| 401 | `UNAUTHORIZED` | Sin sesión | — |
| 403 | `FORBIDDEN` | Es administrador | "Tu cuenta no usa verificación de celular" |
| 409 | `PHONE_ALREADY_REGISTERED` | El número pertenece a otra cuenta | "Ese celular ya está registrado" |
| 422 | `IDEMPOTENCY_KEY_REUSED` | Misma clave con otro cuerpo | — |
| 429 | `RATE_LIMITED` | Límite de RF-70 | `Retry-After` |
| 503 | `EXTERNAL_SERVICE_UNAVAILABLE` | El proveedor del código no responde | "No pudimos enviar el código; intenta por SMS" |

### POST /api/v1/usuarios/me/celular/confirmar

- **Rol**: igual que el anterior · **Body**: `{ "celular": "+573009876543", "codigo": "123456" }` · **Cabeceras**: las mismas, con `Idempotency-Key` ✅.
- **Respuestas exitosas**: `200` con el perfil completo y el celular ya reemplazado.
- **Errores**: `400 OTP_INVALID` (`details.intentosRestantes`), `400 OTP_EXPIRED`, `409 OTP_ATTEMPTS_EXHAUSTED`, `409 PHONE_ALREADY_REGISTERED` (otra cuenta lo registró mientras tanto), `404 NOT_FOUND` (no hay un cambio pendiente para ese número), `403 FORBIDDEN`, `401 UNAUTHORIZED`, `422 IDEMPOTENCY_KEY_REUSED`, `429 RATE_LIMITED`.

## Events

| Evento emitido | Lo emite el caso de uso | Momento |
|---|---|---|
| Ninguno | — | Editar el perfil no tiene reacciones en otros módulos |

**Eventos que consume esta feature**: ninguno.

## Offline & Concurrency Strategy

- **Tolerancia a mala conexión (RNF28)**: la app guarda el perfil en SQLite y lo muestra sin red. Las ediciones de nombre y ubicación sin conexión quedan en la outbox local y se envían al volver la red (la última edición gana). La foto y el cambio de celular exigen conexión; la pantalla lo dice.
- **Concurrencia (RNF08)**: dos dispositivos editando a la vez: gana la última escritura por campo. No hay valor financiero ni de inventario en juego. El cambio de celular guarda un solo pendiente por usuario: una solicitud nueva reemplaza la anterior.
- **Idempotencia (RNF27)**: `Idempotency-Key` en los dos `POST` del celular; `PATCH` y `PUT` son idempotentes por naturaleza.

## Error Handling

| Escenario | Comportamiento esperado | Fuente |
|---|---|---|
| Dato con formato inválido | `400 VALIDATION_ERROR` con el campo | US1-E2 |
| Celular nuevo ya registrado | `409 PHONE_ALREADY_REGISTERED` | Edge 1 |
| Foto no soportada o pesada | `400` con `details.campo = foto` | Edge 2 |
| No confirma el código | Vence en 10 min; el celular anterior sigue | Edge 3 |
| Proveedor de códigos caído | `503`; el celular no cambia | RNF19 |
| Administrador pide cambio de celular | `403` | Restricción 18 |

## Testing Strategy

| Tipo | Qué cubre | Escenarios |
|---|---|---|
| Unitario | Reglas de `Usuario` (nombre, ubicación) | FR-002 |
| Integración | Endpoints con Testcontainers: lectura, edición válida e inválida, foto válida e inválida, solicitud y confirmación de celular, vencimiento, duplicado, administrador | FR-001–FR-004, US1-E1–E3, Edge 1–3 |
| Contrato | Respuesta del perfil con los mismos campos en `GET`, `PATCH` y `confirmar` | FR-003 |
| Arquitectura | `cuentas` usa `VerificarCelularPort` y el almacenamiento solo por puertos | RNF13 |
| Frontend | Jest: formulario, selector de foto (compresión y rechazo), pantalla de código con reenvío | Edge 2, FR-004 |

**Cobertura mínima**: todos los escenarios Given/When/Then tienen al menos un test.

## Project Structure

### Documentation (this feature)

```text
specs/RF-08-edicion-de-datos-de-cuenta/
├── RF-08-edicion-de-datos-de-cuenta.md   # spec
└── plan.md      # este archivo
```

### Source Code

```text
backend/src/main/java/co/edu/napa/cuentas/
├── domain/model/                 # Usuario.actualizarPerfil(), Celular (value object de RF-07)
├── application/
│   ├── ports/in/                 # ConsultarPerfilPort, EditarPerfilPort, SubirFotoPerfilPort, SolicitarCambioCelularPort, ConfirmarCambioCelularPort
│   ├── ports/out/                # UsuarioRepositoryPort, UbicacionRepositoryPort, FotoPerfilStoragePort, CambioCelularPendientePort (Redis), VerificarCelularPort (RF-70)
│   └── usecase/                  # ConsultarPerfilUseCase, EditarPerfilUseCase, SubirFotoPerfilUseCase, SolicitarCambioCelularUseCase, ConfirmarCambioCelularUseCase
└── infrastructure/adapters/
    ├── in/web/                   # PerfilController (/usuarios/me, /foto, /celular/*) y DTO
    └── out/                      # persistencia, FotoPerfilStorageAdapter, CambioCelularRedisAdapter

backend/src/test/java/co/edu/napa/cuentas/

frontend/src/app/(comun)/perfil/index.tsx            # ver perfil
frontend/src/app/(comun)/perfil/editar.tsx           # nombre, foto y ubicación
frontend/src/app/(comun)/perfil/cambiar-celular.tsx  # número nuevo y código
frontend/src/components/perfil/FormularioPerfil.tsx
frontend/src/components/perfil/SelectorFoto.tsx      # expo-image-picker + expo-image-manipulator
frontend/src/services/perfil.ts                      # obtener, editar, subirFoto, cambiar celular; caché SQLite
frontend/__tests__/perfil/
```

**Structure Decision**: módulo `cuentas` (dueño de `Usuario`). La ruta es `/usuarios/me`, como define spec-general, y el perfil solo se lee y edita sobre el usuario del JWT. El cambio de celular reutiliza `VerificarCelularPort` y `ConfirmarVerificacionPort` de RF-70 con el propósito `CAMBIO`: este módulo solo guarda el número pendiente en Redis y lo aplica al confirmar. La foto pasa por un puerto de almacenamiento para no atar el módulo a un proveedor.

## Phase 1: Setup

- [ ] T001 Confirmar que existen `Usuario`, `UbicacionRepositoryPort` (en su ubicación definitiva tras RF-28 T003) y el propósito `CAMBIO` de RF-70 (T030 de RF-70)

## Phase 2: Foundational

- [ ] T002 `FotoPerfilStoragePort` (guardar y reemplazar) y su adaptador en `cuentas/infrastructure/adapters/out/storage/` (volumen local, decisión del equipo)
- [ ] T003 `CambioCelularPendientePort` y su adaptador Redis con la clave `cambio-cel:{usuarioId}` y TTL de 10 minutos
- [ ] T004 Método de actualización en su lugar en `UbicacionRepositoryPort` (compartido con RF-29)

**Checkpoint**: base lista.

## Phase 3: User Story 1 - Editar mis datos personales (Priority: P2)

**Goal**: el usuario ve y mantiene su nombre, foto y ubicación.
**Independent Test**: editar el nombre y la ubicación, subir una foto y ver los cambios en el perfil.
**Cubre**: FR-001–FR-003, SC-001, SC-002, US1-E1–E2, Edge 1–2 y 4.

### Tests (antes de implementar)

- [ ] T010 [P] [US1] Test de integración de `GET /usuarios/me` (campos, `null` sin foto ni ubicación, `no-store`) en `backend/src/test/java/co/edu/napa/cuentas/PerfilConsultaIT.java`
- [ ] T011 [P] [US1] Test de integración de `PATCH /usuarios/me` con nombre y ubicación, y de que el `GET` posterior los refleja, en `.../cuentas/PerfilEdicionIT.java`
- [ ] T012 [P] [US1] Test parametrizado de datos inválidos (cuerpo vacío, nombre vacío o largo, coordenadas fuera de rango, dirección vacía, campos no editables) en `.../cuentas/PerfilValidacionIT.java`
- [ ] T013 [P] [US1] Test de `PUT /usuarios/me/foto` válida (JPEG, PNG, WebP) y de reemplazo de la anterior en `.../cuentas/PerfilFotoIT.java`
- [ ] T014 [P] [US1] Test de foto inválida: tipo falso con extensión `.jpg`, más de 2 MB, sin parte `foto`, en `.../cuentas/PerfilFotoInvalidaIT.java`
- [ ] T015 [P] [US1] Test del administrador (puede editar nombre, foto y ubicación) y de acceso sin sesión (`401`) en `.../cuentas/PerfilAutorizacionIT.java`
- [ ] T016 [P] [US1] Test unitario de `Usuario.actualizarPerfil` en `.../cuentas/UsuarioPerfilTest.java`
- [ ] T030 [P] [US1] Test del formulario de perfil (validación y envío) en `frontend/__tests__/perfil/formulario-perfil.test.tsx`
- [ ] T031 [P] [US1] Test de `SelectorFoto` (reduce y comprime; rechaza un tipo no soportado) en `frontend/__tests__/perfil/selector-foto.test.tsx`

### Implementation

- [ ] T020 [US1] `Usuario.actualizarPerfil` y las reglas de nombre y ubicación en `cuentas/domain/model/Usuario.java`
- [ ] T021 [US1] `EditarPerfilUseCase` y `EditarPerfilPort`
- [ ] T022 [US1] `ConsultarPerfilUseCase` y `GET /usuarios/me` en `PerfilController`
- [ ] T023 [US1] `PATCH /usuarios/me` con un DTO que rechaza campos desconocidos
- [ ] T024 [US1] `SubirFotoPerfilUseCase` y `PUT /usuarios/me/foto` (tipo real por contenido y tamaño)
- [ ] T025 [US1] Adaptador `FotoPerfilStorageAdapter`
- [ ] T032 [US1] `perfil.ts` con caché SQLite y reintento de las ediciones por la outbox en `frontend/src/services/`
- [ ] T033 [US1] Pantallas `perfil/index.tsx` y `perfil/editar.tsx` con `FormularioPerfil`
- [ ] T034 [US1] `SelectorFoto` con `expo-image-picker` y `expo-image-manipulator` (JPEG, lado mayor 1024 px)

**Checkpoint**: el perfil se consulta y se edita.

## Phase 4: User Story 2 - Cambiar mi celular con verificación (Priority: P2)

**Goal**: el celular nuevo solo reemplaza al anterior cuando el usuario confirma el código.
**Independent Test**: pedir el cambio, confirmar el código y ver el celular nuevo; pedirlo y no confirmar y ver el anterior.
**Cubre**: FR-004, US1-E3, Edge 1 (celular) y Edge 3.

### Tests (antes de implementar)

- [ ] T040 [P] [US2] Test de `solicitar-cambio`: responde `202`, envía el código por el puerto y el celular de la cuenta no cambia, en `.../cuentas/CambioCelularSolicitudIT.java`
- [ ] T041 [P] [US2] Test de `confirmar` correcto: el celular y `celular_verificado_at` cambian en una transacción y se limpia la clave de Redis, en `.../cuentas/CambioCelularConfirmacionIT.java`
- [ ] T042 [P] [US2] Test de que sin confirmar la clave vence y el celular anterior sigue vigente, en `.../cuentas/CambioCelularVencimientoIT.java`
- [ ] T043 [P] [US2] Test de celular de otra cuenta, al solicitar y al confirmar con la carrera, y de confirmar un número distinto del solicitado (`404`), en `.../cuentas/CambioCelularDuplicadoIT.java`
- [ ] T044 [P] [US2] Test de código incorrecto, vencido y de 5 intentos fallidos, y del mismo celular actual, en `.../cuentas/CambioCelularCodigoIT.java`
- [ ] T045 [P] [US2] Test del administrador (`403`) y de idempotencia (misma clave repite la respuesta; otro cuerpo `422`), en `.../cuentas/CambioCelularAutorizacionIT.java`
- [ ] T050 [P] [US2] Test de la pantalla `cambiar-celular` (número, código, reenvío y errores) en `frontend/__tests__/perfil/cambiar-celular.test.tsx`

### Implementation

- [ ] T047 [US2] `SolicitarCambioCelularUseCase`: valida, revisa duplicado, inicia el código con `VerificarCelularPort` y guarda el pendiente
- [ ] T048 [US2] `ConfirmarCambioCelularUseCase`: valida el código con `ConfirmarVerificacionPort`, revisa duplicado y aplica el cambio en una transacción
- [ ] T049 [US2] Endpoints `solicitar-cambio` y `confirmar` en `PerfilController`
- [ ] T051 [US2] Pantalla `perfil/cambiar-celular.tsx` y sus llamadas en `perfil.ts`

**Checkpoint**: el celular se cambia de forma verificada.

## Phase N: Polish & Cross-Cutting

- [ ] T060 Medir que el perfil se actualiza y se refleja en < 5 s (SC-001)
- [ ] T061 Actualizar `requerimientos/trazabilidad.md` si cambió algo

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → Polish. US2 puede ir en paralelo con US1 una vez hecha la fase 2.
- Dependencias entre RF: requiere RF-07, RF-10 y RF-70 (T030, propósito `CAMBIO`); su foto la muestran RF-27 y las pantallas de reputación.
- Tareas `[P]` pueden ejecutarse en paralelo.

## Open Questions

| Pregunta | Impacto | Decisión provisional | Responsable |
|---|---|---|---|
| ¿Dónde se guardan las fotos? El stack no tiene almacenamiento de objetos (foundations) y RF-54 dice "sin foto" para productos | Infraestructura | **Decidido: volumen local** servido por la API, detrás de `FotoPerfilStoragePort`. Si el despliegue gratuito tiene disco efímero (se borra al redesplegar), hay que montar un volumen persistente o cambiar el adaptador por un servicio gratuito de objetos, sin tocar el resto | Equipo |
| spec-general no tiene `GET /usuarios/me` (RF-07 ya lo usa en `Location`) ni `PUT /usuarios/me/foto` | Contrato | Se agregan a Spec general| Equipo |
| El correo no se edita (FR-001 no lo lista; el administrador inicia sesión con él) | Alcance | No editable en esta feature | Equipo |
| Edge 4: la spec pregunta por una ubicación que el proveedor de mapas no valida | Regla | Solo se valida el rango de coordenadas, como RF-28; no se llama al proveedor | Equipo |
| Al cambiar el celular las sesiones activas siguen abiertas y el login pasa a usar el número nuevo | Seguridad | No se revocan sesiones; se confirma con el código del número nuevo | Equipo |
| Dos dispositivos editando a la vez: última escritura gana por campo | Concurrencia | Sin versión ni `If-Match` | Equipo |
| Límites de nombre (100) y de la foto (2 MB; JPEG, PNG, WebP) | Validación | Esos valores, iguales a los de RF-28 donde aplican | Equipo |
| No hay forma de quitar la foto ni la ubicación (la spec solo habla de editar) | Alcance | No se ofrece | Equipo |

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
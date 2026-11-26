# Feature Specification: Creación de emprendimiento

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-28
**Historias de usuario relacionadas**: HU-53

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Publicar un emprendimiento en la plataforma (Priority: P1)

Como vendedor, quiero crear mi emprendimiento indicando nombre, categoría, ubicación y descripción, para que los clientes puedan encontrarme en la plataforma.

**Why this priority**: Sin un emprendimiento creado, el vendedor no puede publicar productos ni recibir pedidos; es el punto de entrada obligatorio para todo el flujo de ventas.

**Independent Test**: Puede probarse de forma independiente registrando un usuario con rol vendedor, completando el formulario de creación de emprendimiento y verificando que el emprendimiento queda visible en el listado público, sin depender de que existan productos o pedidos.

**Acceptance Scenarios**:

1. **Scenario**: Creación exitosa de emprendimiento
   - **Given** estoy registrado como vendedor y no tengo un emprendimiento activo
   - **When** completo el formulario de creación con nombre, categoría, ubicación y descripción válidos
   - **Then** el sistema publica el emprendimiento y lo hace visible para los clientes

2. **Scenario**: Intento de crear un segundo emprendimiento
   - **Given** ya tengo un emprendimiento activo creado
   - **When** intento crear otro emprendimiento
   - **Then** el sistema impide la creación y me informa que ya cuento con un emprendimiento activo

### Edge Cases

- ¿Qué sucede si el vendedor deja campos obligatorios (nombre, ubicación) vacíos?
- ¿Cómo maneja el sistema una ubicación inválida o fuera de la cobertura del servicio?
- ¿Qué ocurre si el vendedor intenta crear un emprendimiento con un nombre ya usado por otro emprendimiento en la misma zona?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a un usuario con rol vendedor crear un emprendimiento indicando nombre, categoría (de una lista fija), ubicación y descripción, hora de apertura y hora de cierre.
- **FR-002**: El sistema DEBE validar que los campos obligatorios estén completos antes de publicar el emprendimiento.
- **FR-003**: El sistema DEBE impedir que un vendedor cree un nuevo emprendimiento mientras ya tenga uno activo.
- **FR-004**: El sistema DEBE publicar el emprendimiento como visible para los clientes inmediatamente después de su creación.
- **FR-005**: El sistema DEBE interpretar la ubicación según el rol del vendedor: exigir y guardar la dirección del punto fijo para un vendedor de punto fijo; para un vendedor ambulante, permitir una zona aproximada con referencia sin exigir dirección.
- **FR-006**: El sistema DEBE asociar al emprendimiento el tipo de vendedor (ambulante o de punto fijo) definido en su cuenta, sin solicitarlo de nuevo en el formulario.

### Key Entities

- **Emprendimiento**: Representa el negocio del vendedor en la plataforma; incluye nombre, categoría (de una lista fija), ubicación, descripción, tipo de vendedor (ambulante o de punto fijo) y estado (activo/inactivo).
- **Vendedor**: Usuario propietario de un emprendimiento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un vendedor puede completar la creación de su emprendimiento en menos de 3 minutos.
- **SC-002**: El 100% de los emprendimientos creados con datos válidos quedan visibles para los clientes de forma inmediata.

## Aclaración de auditoría P2-01 (2026-11-24)

FR-001 mantiene la lista fija de categorías. Si el vendedor elige OTROS, puede indicar opcionalmente `categoriaPersonalizada` (texto no vacío tras normalizar, de hasta 80 caracteres). El texto complementa OTROS; no crea un nuevo valor del catálogo y solo se admite con esa categoría. Las consultas de creación, negocio propio, listado y detalle muestran el texto cuando existe; los registros anteriores continúan mostrando Otros. El nombre y la categoría, incluido este complemento, siguen siendo inmutables tras crear el negocio según RF-29.

Escenarios: OTROS con texto válido conserva y muestra su texto; OTROS sin complemento sigue siendo válido por compatibilidad; un complemento de más de 80 caracteres o enviado para otra categoría se rechaza con un error específico. Las ediciones de horario, descripción o ubicación no deben borrar el complemento.


## Acuerdo posterior al testing en vivo (2026-11-25)

El usuario autoriza revisar las reglas anteriores según el tester: (1) el ambulante no registra dirección/referencia ni coordenadas manuales de negocio; publica su posición GPS con permiso explícito y actualiza su punto mientras usa la app, con control para pausar. No se guarda historial de recorridos ni se promete ubicación con app cerrada. (2) se elimina el límite comercial de 20 productos y 20 unidades; se conservan cantidad positiva, dos decimales, precisión NUMERIC(10,2), stock inmediato, total representable y productos únicos. Nueva migración incremental elimina el CHECK <=20, sin reescribir V11. (3) autocompletar dirección desde borrador no vacío, última entrega y perfil vigente, en ese orden, sin reemplazar lo escrito ni usar GPS como dirección del cliente. (4) reservas aceptadas con transferencia permiten confirmación anticipada por el vendedor antes de entrega/recogida; no procesa dinero. Una entrega posterior reutiliza el pago confirmado sin duplicar evento/cobro. Reservas en efectivo conservan cobro presencial. (5) avisos flotantes globales dentro de la app al llegar notificaciones nuevas, por sondeo activo de bandeja más push existente. No marcar como leídas al avisar, no mostrar historial inicial como novedad, separar sesiones y detener consultas al salir. Push con app cerrada sigue dependiendo de configuración nativa/Firebase.


## Ubicación voluntaria en segundo plano (2026-11-25, base 4130e4a)

El usuario autoriza continuar actualizando la posición del ambulante sin tener Ñapa en primer plano y poder activarla/desactivarla. Consentimiento separado: crear el negocio conserva solo primer plano; desde Mi emprendimiento se solicita explícitamente permiso en segundo plano. Android muestra servicio/notificación persistente e iOS indicador de ubicación. No se pide permiso desde una tarea ni se reactiva un consentimiento revocado. Pausar/cerrar sesión invalida primero la preferencia y detiene la tarea. Sesión, cuenta, rol y negocio deben coincidir en cada envío; no publicar lotes históricos ni posiciones de más de dos minutos. No se promete funcionamiento tras forzar detención/cierre o restricciones del sistema. Web no admite este modo. Requiere expo-task-manager compatible SDK58 y nuevo build nativo; Expo Go no valida segundo plano.

Diseño: preferencia y publicación sin React compartidas por el bucle visible y una tarea TaskManager definida en módulo global/importada desde _layout. La tarea procesa solo el punto más reciente, sin cola histórica. Configuración CNG habilita permisos/modos nativos y servicio. SecureStore mantiene cifradas la sesión y preferencia, disponibles tras el primer desbloqueo del dispositivo para permitir la tarea con pantalla bloqueada; solo esas claves usan AFTER_FIRST_UNLOCK. Escrituras nativas serializadas evitan que la adaptación de accesibilidad sobrescriba una renovación de sesión concurrente. Validar permisos denegados, activación/pausa/logout, otra cuenta, puntos antiguos, serialización, tipos/lint/config resuelta y exportación web; probar el APK en dispositivo por separado.

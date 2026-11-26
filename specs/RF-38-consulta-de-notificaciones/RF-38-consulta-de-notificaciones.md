# Feature Specification: Consulta de notificaciones

**Created**: 2026-09-11
**Actualizado**: 2026-10-14
**Requerimiento funcional**: RF-38
**Historias de usuario relacionadas**: HU-63

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conocer novedades relacionadas con mi cuenta (Priority: P1)

Como usuario (cliente, vendedor, domiciliario o administrador), quiero consultar mis notificaciones, para conocer las novedades y eventos relacionados con mi cuenta.

**Why this priority**: Mantiene informado al usuario sobre el avance de pedidos, domicilios, pagos y reportes, y casi todos los flujos P1 (pedidos, domicilios, pagos) dependen de ella para avisar a las partes; por eso se clasifica como P1, aunque no sea un flujo transaccional en sí mismo.

**Independent Test**: Puede probarse de forma independiente generando un evento que produzca una notificación (por ejemplo, un cambio de estado de pedido) y verificando que aparece en la sección de notificaciones del usuario correspondiente.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de notificaciones pendientes
   - **Given** tengo notificaciones generadas por eventos de la plataforma
   - **When** ingreso a la sección de notificaciones
   - **Then** el sistema muestra el listado de notificaciones ordenado por fecha, indicando cuáles no he leído

2. **Scenario**: Sin notificaciones registradas
   - **Given** no se ha generado ninguna notificación para mi cuenta
   - **When** ingreso a la sección de notificaciones
   - **Then** el sistema muestra un mensaje indicando que no hay notificaciones

3. **Scenario**: Abrir una notificación
   - **Given** tengo una notificación no leída
   - **When** abro su detalle
   - **Then** el sistema la marca como leída y muestra su contenido

### Edge Cases

- Si el mismo evento se vuelve a entregar por reintentos del outbox, se genera como máximo una notificación por destinatario; los consumidores deduplican por `eventId` según `eventos.md`.
- Las notificaciones se conservan durante 30 días desde su creación y luego se eliminan, estén leídas o no.
- Al marcar una cuenta como `ELIMINADA`, se eliminan sus notificaciones.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a los usuarios (cliente, vendedor, domiciliario, administrador) consultar sus notificaciones.
- **FR-002**: El sistema DEBE mostrar las notificaciones ordenadas por fecha, distinguiendo las leídas de las no leídas.
- **FR-003**: El sistema DEBE generar notificaciones ante eventos relevantes de pedidos, reservas, domicilios, pagos y reportes.
- **FR-004**: El sistema DEBE notificar, como mínimo, los siguientes eventos: nuevo pedido recibido (al vendedor, incluida la entrega directa al vendedor ambulante), pedido aceptado o rechazado (al cliente; en un domicilio, la aceptación incluye el aviso de que debe transferir el total), pago de un pedido confirmado (al cliente), inicio de la entrega directa (al cliente), pedido listo para recoger (al cliente), nuevo domicilio disponible (a todos los domiciliarios activos), pedido recogido por el domiciliario (al vendedor de punto fijo), domiciliario en camino (al cliente), entrega confirmada con el código por el domiciliario (al vendedor de punto fijo, para que pague la tarifa, y al cliente, como aviso de entrega), pago de la tarifa registrado por el vendedor (al domiciliario), cancelación de un domicilio (a las partes), resolución de un reporte (a quien lo creó), comentario editado o eliminado por un administrador (a su autor) y nuevo reporte pendiente (a todos los administradores activos).
- **FR-005**: El sistema DEBE marcar una notificación como leída al abrir su detalle; la operación solo puede afectar una notificación del usuario autenticado.

### Key Entities

- **Notificación**: Representa un evento informado al usuario; incluye tipo, fecha, estado de lectura y usuario destinatario.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Las notificaciones generadas por un evento son visibles para el usuario en menos de 1 minuto.
- **SC-002**: El 100% de los eventos relevantes definidos por la plataforma generan una notificación correspondiente.


## Acuerdo posterior al testing en vivo (2026-11-25)

El usuario autoriza revisar las reglas anteriores según el tester: (1) el ambulante no registra dirección/referencia ni coordenadas manuales de negocio; publica su posición GPS con permiso explícito y actualiza su punto mientras usa la app, con control para pausar. No se guarda historial de recorridos ni se promete ubicación con app cerrada. (2) se elimina el límite comercial de 20 productos y 20 unidades; se conservan cantidad positiva, dos decimales, precisión NUMERIC(10,2), stock inmediato, total representable y productos únicos. Nueva migración incremental elimina el CHECK <=20, sin reescribir V11. (3) autocompletar dirección desde borrador no vacío, última entrega y perfil vigente, en ese orden, sin reemplazar lo escrito ni usar GPS como dirección del cliente. (4) reservas aceptadas con transferencia permiten confirmación anticipada por el vendedor antes de entrega/recogida; no procesa dinero. Una entrega posterior reutiliza el pago confirmado sin duplicar evento/cobro. Reservas en efectivo conservan cobro presencial. (5) avisos flotantes globales dentro de la app al llegar notificaciones nuevas, por sondeo activo de bandeja más push existente. No marcar como leídas al avisar, no mostrar historial inicial como novedad, separar sesiones y detener consultas al salir. Push con app cerrada sigue dependiendo de configuración nativa/Firebase.

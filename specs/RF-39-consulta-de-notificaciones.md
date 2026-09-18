# Feature Specification: Consulta de notificaciones

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-39
**Historias de usuario relacionadas**: HU-64

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente consulta sus notificaciones (Priority: P2)

Como cliente, quiero consultar mis notificaciones, para conocer el avance de mis pedidos, reservas y domicilios y las ofertas de precio que recibo.

**Why this priority**: Mantiene informado al cliente sobre el avance de su compra, pero no es un flujo transaccional en sí mismo; se prioriza después de las funciones core de cada módulo.

**Independent Test**: Puede probarse generando un cambio de estado en un pedido del cliente y verificando que aparece en su sección de notificaciones.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de notificaciones del cliente
   - **Given** tengo notificaciones generadas por mis pedidos, reservas o domicilios
   - **When** ingreso a la sección de notificaciones
   - **Then** el sistema muestra el listado ordenado por fecha, indicando cuáles no he leído

2. **Scenario**: Cliente sin notificaciones
   - **Given** no se ha generado ninguna notificación para mi cuenta
   - **When** ingreso a la sección de notificaciones
   - **Then** el sistema muestra un mensaje indicando que no hay notificaciones

---

### User Story 2 - Vendedor consulta sus notificaciones (Priority: P2)

Como vendedor (ambulante o de punto fijo), quiero consultar mis notificaciones, para enterarme de los nuevos pedidos, de las reservas del día y del avance de mis domicilios.

**Why this priority**: El vendedor no puede estar pendiente de la pantalla mientras vende; las notificaciones le avisan de los pedidos que debe atender.

**Independent Test**: Puede probarse creando un pedido para un vendedor y verificando que le llega la notificación de nuevo pedido.

**Acceptance Scenarios**:

1. **Scenario**: Notificación de un nuevo pedido
   - **Given** un cliente realizó un pedido en mi emprendimiento
   - **When** ingreso a la sección de notificaciones
   - **Then** el sistema muestra la notificación del nuevo pedido con su modalidad (entrega directa, reserva o domicilio)

2. **Scenario**: Vendedor sin notificaciones
   - **Given** no se ha generado ninguna notificación para mi cuenta
   - **When** ingreso a la sección de notificaciones
   - **Then** el sistema muestra un mensaje indicando que no hay notificaciones

---

### User Story 3 - Domiciliario consulta sus notificaciones (Priority: P2)

Como domiciliario, quiero consultar mis notificaciones, para enterarme de los nuevos domicilios disponibles y de la respuesta del cliente a mis ofertas.

**Why this priority**: El domiciliario depende de las notificaciones para no perder domicilios disponibles y conocer el resultado de sus ofertas.

**Independent Test**: Puede probarse publicando un domicilio y verificando que los domiciliarios reciben la notificación de nuevo domicilio disponible.

**Acceptance Scenarios**:

1. **Scenario**: Notificación de un domicilio disponible
   - **Given** un vendedor de punto fijo publicó un domicilio
   - **When** ingreso a la sección de notificaciones
   - **Then** el sistema muestra la notificación del nuevo domicilio disponible

2. **Scenario**: Notificación sobre una oferta
   - **Given** un cliente aceptó o rechazó mi oferta de precio
   - **When** ingreso a la sección de notificaciones
   - **Then** el sistema muestra el resultado de mi oferta

---

### User Story 4 - Administrador consulta sus notificaciones (Priority: P3)

Como administrador, quiero consultar mis notificaciones, para enterarme de los reportes nuevos y de los domicilios que requieren mi atención.

**Why this priority**: Apoya la supervisión de la plataforma, pero no es necesaria para el flujo transaccional, por lo que se clasifica como P3.

**Independent Test**: Puede probarse creando un reporte y verificando que el administrador recibe la notificación.

**Acceptance Scenarios**:

1. **Scenario**: Notificación de un reporte nuevo
   - **Given** un usuario creó un reporte
   - **When** ingreso a la sección de notificaciones
   - **Then** el sistema muestra la notificación del reporte nuevo

### Edge Cases

- **¿Qué sucede si se generan múltiples notificaciones para un mismo evento (ej. reintentos de asignación de domicilio)?** Se genera una sola notificación por evento y destinatario; los reintentos no la duplican.
- **¿Cómo se maneja el historial de notificaciones muy antiguas? ¿existe un límite de retención?** Se conservan 90 días y luego se eliminan.
- **¿Qué ocurre si el usuario elimina su cuenta teniendo notificaciones pendientes?** Sus notificaciones pendientes se eliminan junto con la cuenta.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a los usuarios (cliente, vendedor, domiciliario, administrador) consultar sus notificaciones.
- **FR-002**: El sistema DEBE mostrar las notificaciones ordenadas por fecha, distinguiendo las leídas de las no leídas.
- **FR-003**: El sistema DEBE generar notificaciones ante eventos relevantes de pedidos, reservas, domicilios, pagos y reportes.
- **FR-004**: El sistema DEBE notificar, como mínimo, los siguientes eventos: nuevo pedido recibido (al vendedor, incluida la entrega directa al vendedor ambulante), pedido aceptado o rechazado (al cliente), inicio de la entrega directa (al cliente), reserva lista para recoger (al cliente), nuevo domicilio disponible (a los domiciliarios), nueva oferta de precio (al cliente), oferta aceptada o rechazada (al domiciliario), pedido recogido por el domiciliario (al vendedor de punto fijo), domiciliario en camino (al cliente), entrega confirmada por el domiciliario (al cliente), cancelación de un domicilio (a las partes) y resolución de un reporte (a quien lo creó).

### Key Entities

- **Notificación**: Representa un evento informado al usuario; incluye tipo, fecha, estado de lectura y usuario destinatario.

### Data Rules

**Datos que ingresa el usuario**: Ninguno.

**Datos que se muestran o filtran**

- Tipo, texto, fecha y estado de lectura, ordenadas por fecha.
- Se conservan 90 días.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Las notificaciones generadas por un evento son visibles para el usuario en menos de 1 minuto.
- **SC-002**: El 100% de los eventos relevantes definidos por la plataforma generan una notificación correspondiente.

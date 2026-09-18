# Feature Specification: Consulta de notificaciones

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-38
**Historias de usuario relacionadas**: HU-63

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conocer novedades relacionadas con mi cuenta (Priority: P2)

Como usuario (cliente, vendedor, domiciliario o administrador), quiero consultar mis notificaciones, para conocer las novedades y eventos relacionados con mi cuenta.

**Why this priority**: Mantiene informado al usuario sobre el avance de pedidos, domicilios, pagos y reportes, pero no es un flujo transaccional en sí mismo; se prioriza después de las funciones core de cada módulo.

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

### Edge Cases

- ¿Qué sucede si se generan múltiples notificaciones para un mismo evento (ej. reintentos de asignación de domicilio)?
- ¿Cómo se maneja el historial de notificaciones muy antiguas? ¿existe un límite de retención?
- ¿Qué ocurre si el usuario elimina su cuenta teniendo notificaciones pendientes?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a los usuarios (cliente, vendedor, domiciliario, administrador) consultar sus notificaciones.
- **FR-002**: El sistema DEBE mostrar las notificaciones ordenadas por fecha, distinguiendo las leídas de las no leídas.
- **FR-003**: El sistema DEBE generar notificaciones ante eventos relevantes de pedidos, reservas, domicilios, pagos y reportes.
- **FR-004**: El sistema DEBE notificar, como mínimo, los siguientes eventos: nuevo pedido recibido (al vendedor, incluida la entrega directa al vendedor ambulante), pedido aceptado o rechazado (al cliente), inicio de la entrega directa (al cliente), reserva lista para recoger (al cliente), nuevo domicilio disponible (a los domiciliarios), nueva oferta de precio (al cliente), oferta aceptada o rechazada (al domiciliario), pedido recogido por el domiciliario (al vendedor de punto fijo), domiciliario en camino (al cliente), entrega confirmada por el domiciliario (al cliente), cancelación de un domicilio (a las partes) y resolución de un reporte (a quien lo creó).

### Key Entities

- **Notificación**: Representa un evento informado al usuario; incluye tipo, fecha, estado de lectura y usuario destinatario.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Las notificaciones generadas por un evento son visibles para el usuario en menos de 1 minuto.
- **SC-002**: El 100% de los eventos relevantes definidos por la plataforma generan una notificación correspondiente.

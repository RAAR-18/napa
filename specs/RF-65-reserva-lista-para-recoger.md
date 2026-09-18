# Feature Specification: Reserva lista para recoger

**Created**: 2026-09-18
**Requerimiento funcional**: RF-65
**Historias de usuario relacionadas**: HU-99

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Avisar que la reserva ya está lista (Priority: P1)

Como vendedor de punto fijo, quiero marcar una reserva como lista para recoger, para avisar al cliente que ya puede pasar por ella.

**Why this priority**: Coordina el momento del retiro y evita que el cliente llegue antes de que el pedido esté preparado, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con una reserva aceptada, marcándola como lista y verificando que el cliente recibe la notificación.

**Acceptance Scenarios**:

1. **Scenario**: Reserva marcada como lista
   - **Given** tengo una reserva aceptada con retiro en mi punto fijo
   - **When** la marco como lista para recoger
   - **Then** el sistema cambia su estado a listo para recoger y notifica al cliente

2. **Scenario**: Reserva no aceptada
   - **Given** la reserva está pendiente o fue rechazada
   - **When** intento marcarla como lista
   - **Then** el sistema rechaza la operación e indica que la reserva debe estar aceptada

3. **Scenario**: Pedido de otra modalidad
   - **Given** el pedido es de domicilio
   - **When** intento marcarlo como lista para recoger
   - **Then** el sistema no ofrece la acción, porque los domicilios se publican para los domiciliarios (RF-14)

### Edge Cases

- **¿Puede marcarse una reserva como lista antes de la fecha programada?** No: solo puede marcarse como lista desde el día programado.
- **¿Qué ocurre si el cliente no llega a retirar una reserva marcada como lista? ¿Vence?** La reserva sigue lista hasta el cierre del día programado; luego se cancela y las unidades vuelven al inventario.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor de punto fijo marcar como "lista para recoger" una reserva aceptada de su emprendimiento.
- **FR-002**: El sistema DEBE notificar al cliente cuando su reserva esté lista.
- **FR-003**: El sistema DEBE impedir marcar como lista un pedido que no sea una reserva aceptada.

### Key Entities

- **Reserva**: Pedido con modalidad de reserva; transición de esta funcionalidad: aceptado → listo para recoger.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. El vendedor de punto fijo marca una reserva aceptada como lista, desde el día programado.

**Datos que asigna el sistema**

- Estado "listo para recoger", fecha y hora; notificación al cliente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente recibe la notificación de reserva lista en menos de 1 minuto.
- **SC-002**: El 100% de las reservas listas para recoger estuvieron previamente aceptadas.

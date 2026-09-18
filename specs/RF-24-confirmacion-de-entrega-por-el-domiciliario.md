# Feature Specification: Confirmación de entrega por el domiciliario

**Created**: 2026-09-18
**Requerimiento funcional**: RF-24
**Historias de usuario relacionadas**: HU-47

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar que el pedido llegó al destino (Priority: P1)

Como domiciliario, quiero confirmar la entrega del pedido, para registrar que lo llevé al punto de destino.

**Why this priority**: Cierra la parte operativa del domiciliario y habilita la confirmación del cliente y el cobro en efectivo, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un domicilio en camino, confirmando la entrega y verificando que el estado pasa a entregado y que el cliente es notificado para confirmar la llegada.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de entrega
   - **Given** el domicilio está en camino y llegué al punto de destino
   - **When** confirmo la entrega del pedido
   - **Then** el sistema cambia el estado a entregado, lo registra en la trazabilidad y notifica al cliente para que confirme la llegada

2. **Scenario**: Confirmación fuera de secuencia
   - **Given** el domicilio no está en estado en camino
   - **When** intento confirmar la entrega
   - **Then** el sistema rechaza la operación e indica el estado actual

3. **Scenario**: Registro del cobro posterior
   - **Given** confirmé la entrega del pedido
   - **When** reviso las acciones disponibles
   - **Then** el sistema me permite registrar el pago en efectivo del domicilio (RF-43)

### Edge Cases

- ¿Se valida que la ubicación del domiciliario esté cerca del punto B al confirmar la entrega?
- ¿Qué ocurre si el cliente no se encuentra en el punto de destino?
- ¿Cómo se maneja una doble confirmación enviada por reintentos de conexión?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario asignado confirmar la entrega únicamente cuando el domicilio esté en estado "en camino".
- **FR-002**: El sistema DEBE cambiar el estado del domicilio a "entregado", registrar el evento en la trazabilidad y notificar al cliente.
- **FR-003**: El sistema DEBE impedir que la entrega se confirme más de una vez.
- **FR-004**: El sistema DEBE habilitar el registro del pago en efectivo del domicilio a partir del estado "entregado" (RF-43).

### Key Entities

- **Domicilio**: Transición de esta funcionalidad: en camino → entregado (pendiente de confirmación del cliente).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las entregas confirmadas quedan registradas con fecha y hora en la trazabilidad.
- **SC-002**: El cliente recibe la solicitud de confirmar la llegada en menos de 1 minuto.
- **SC-003**: El 0% de las entregas puede confirmarse dos veces.

# Feature Specification: Recogida lista para retirar

**Created**: 2026-09-18
**Requerimiento funcional**: RF-64
**Historias de usuario relacionadas**: HU-97

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Avisar que el pedido ya está listo (Priority: P1)

Como vendedor de punto fijo, quiero marcar un pedido de recogida como listo para retirar, para avisar al cliente que ya puede pasar por él.

**Why this priority**: Coordina el momento del retiro y evita que el cliente llegue antes de que el pedido esté preparado, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un pedido de recogida aceptado (marcado o no como reserva), marcándolo como listo y verificando que el cliente recibe la notificación.

**Acceptance Scenarios**:

1. **Scenario**: Pedido marcado como listo
   - **Given** tengo un pedido aceptado con modalidad de recogida en mi punto fijo
   - **When** lo marco como listo para retirar
   - **Then** el sistema cambia su estado a listo para recoger y notifica al cliente

2. **Scenario**: Pedido no aceptado
   - **Given** el pedido está pendiente o fue rechazado
   - **When** intento marcarlo como listo
   - **Then** el sistema rechaza la operación e indica que el pedido debe estar aceptado

3. **Scenario**: Pedido de otra modalidad
   - **Given** el pedido es de entrega directa o de domicilio
   - **When** intento marcarlo como listo para retirar
   - **Then** el sistema no ofrece la acción, porque esa modalidad no implica recogida en el punto fijo

4. **Scenario**: Recogida marcada como reserva para una fecha futura
   - **Given** tengo un pedido de recogida aceptado marcado como reserva
   - **When** llega la fecha acordada y preparo el pedido
   - **Then** puedo marcarlo como listo para retirar igual que un pedido de recogida inmediato

### Edge Cases

- ¿Puede marcarse un pedido de recogida como listo antes de la fecha y hora acordadas, si es una reserva?
- ¿Qué ocurre si el cliente no llega a retirar un pedido marcado como listo? ¿Vence?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor de punto fijo marcar como "listo para recoger" un pedido aceptado de modalidad recogida de su emprendimiento, sea inmediato o marcado como reserva.
- **FR-002**: El sistema DEBE notificar al cliente cuando su pedido esté listo.
- **FR-003**: El sistema DEBE impedir marcar como listo un pedido que no sea de modalidad recogida y esté aceptado.

### Key Entities

- **Pedido**: Modalidad recogida; transición de esta funcionalidad: aceptado → listo para recoger. Puede o no estar marcado como reserva.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente recibe la notificación de pedido listo en menos de 1 minuto.
- **SC-002**: El 100% de los pedidos listos para recoger estuvieron previamente aceptados.
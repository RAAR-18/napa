# Feature Specification: Inicio de entrega directa

**Created**: 2026-09-18
**Requerimiento funcional**: RF-36
**Historias de usuario relacionadas**: HU-61

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Avisar al cliente que voy en camino (Priority: P1)

Como vendedor ambulante, quiero iniciar la entrega de un pedido, para avisar al cliente que voy en camino.

**Why this priority**: Da visibilidad al cliente sobre el avance de su pedido y marca el inicio operativo de la entrega, por lo que se clasifica como P1.

**Independent Test**: Puede probarse iniciando la entrega de un pedido aceptado y verificando que su estado pasa a en camino y el cliente es notificado.

**Acceptance Scenarios**:

1. **Scenario**: Inicio de entrega directa
   - **Given** tengo un pedido de entrega directa aceptado
   - **When** inicio la entrega
   - **Then** el sistema cambia el estado del pedido a en camino y notifica al cliente

2. **Scenario**: Inicio de la entrega de una reserva
   - **Given** tengo una reserva aceptada cuya fecha programada es hoy
   - **When** inicio la entrega
   - **Then** el sistema cambia el estado de la reserva a en camino y notifica al cliente

3. **Scenario**: Inicio anticipado de una reserva
   - **Given** la fecha programada de la reserva aún no llega
   - **When** intento iniciar la entrega
   - **Then** el sistema rechaza la operación e indica la fecha programada

### Edge Cases

- ¿Qué ocurre si el vendedor ambulante tiene varios pedidos aceptados y los inicia todos a la vez?
- ¿Puede el vendedor ambulante revertir el inicio de una entrega si cambia de idea?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor ambulante iniciar la entrega de un pedido en estado "aceptado".
- **FR-002**: El sistema DEBE permitir iniciar la entrega de una reserva únicamente en su fecha programada.
- **FR-003**: El sistema DEBE cambiar el estado del pedido a "en camino" y notificar al cliente.

### Key Entities

- **Pedido**: Transición de esta funcionalidad: aceptado → en camino.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente recibe la notificación de inicio de entrega en menos de 1 minuto.
- **SC-002**: El 100% de los pedidos en camino estuvieron previamente aceptados.

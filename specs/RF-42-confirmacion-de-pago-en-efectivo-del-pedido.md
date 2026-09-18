# Feature Specification: Confirmación de pago en efectivo del pedido

**Created**: 2026-09-18
**Requerimiento funcional**: RF-42
**Historias de usuario relacionadas**: HU-66

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Vendedor confirma que recibió el efectivo de un pedido (Priority: P1)

Como vendedor, quiero confirmar el pago en efectivo de un pedido, para registrar que el pedido fue pagado.

**Why this priority**: Formaliza el cobro de cada pedido en cualquiera de las modalidades; sin la confirmación no hay registro del ingreso, por lo que se clasifica como P1.

**Independent Test**: Puede probarse confirmando el pago de un pedido entregado y verificando que el estado del pago cambia y queda en el historial.

**Acceptance Scenarios**:

1. **Scenario**: Cobro en una entrega directa o reserva con entrega
   - **Given** soy vendedor ambulante y entregué un pedido al cliente
   - **When** confirmo que recibí el efectivo al confirmar la entrega
   - **Then** el sistema registra el pago como confirmado

2. **Scenario**: Cobro en el retiro de una reserva
   - **Given** soy vendedor de punto fijo y el cliente retira su reserva
   - **When** confirmo que recibí el efectivo al confirmar el retiro
   - **Then** el sistema registra el pago como confirmado

3. **Scenario**: Cobro de un pedido de domicilio
   - **Given** soy vendedor de punto fijo y el pedido fue entregado por un domiciliario
   - **When** confirmo que recibí el efectivo correspondiente al pedido
   - **Then** el sistema registra el pago como confirmado

4. **Scenario**: Intento de confirmar dos veces
   - **Given** el pago de un pedido ya fue confirmado
   - **When** intento confirmarlo nuevamente
   - **Then** el sistema informa que ya está confirmado y no genera un nuevo registro

### Edge Cases

- ¿Cómo se liquida el dinero del pedido cuando el cliente paga en efectivo al domiciliario en un domicilio?
- ¿Qué sucede si el vendedor intenta confirmar el pago de un pedido cancelado o rechazado?
- ¿Qué ocurre si el monto recibido es distinto al total del pedido?
- ¿Qué pasa si el vendedor confirma el pago de un pedido que no le pertenece?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor (ambulante o de punto fijo) confirmar que recibió el pago en efectivo de un pedido de su emprendimiento.
- **FR-002**: El sistema DEBE solicitar esta confirmación al registrar la entrega directa (RF-37) o el retiro de la reserva (RF-65), y permitirla para los pedidos de domicilio una vez entregados.
- **FR-003**: El sistema DEBE impedir que el pago de un mismo pedido se confirme más de una vez.
- **FR-004**: El sistema DEBE impedir que un vendedor confirme el pago de un pedido que no pertenece a su emprendimiento.
- **FR-005**: El sistema DEBE notificar al cliente cuando el pago de su pedido sea confirmado.
- **FR-006**: El sistema NO DEBE solicitar ni almacenar datos de tarjetas, cuentas ni billeteras: el único medio de pago es el efectivo.

### Key Entities

- **Pago**: Registro en efectivo asociado a un pedido; estados: pendiente de confirmación y confirmado.
- **Pedido**: Su estado de pago se actualiza al confirmarse el cobro.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede confirmar el pago de un pedido en menos de 10 segundos.
- **SC-002**: El 100% de los pedidos entregados quedan con el pago confirmado o pendiente de confirmación, sin estados intermedios.
- **SC-003**: El 0% de los pagos ya confirmados permite una segunda confirmación.

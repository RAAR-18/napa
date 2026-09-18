# Feature Specification: Confirmación de entrega directa

**Created**: 2026-09-18
**Requerimiento funcional**: RF-37
**Historias de usuario relacionadas**: HU-62

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cerrar la entrega y cobrar en efectivo (Priority: P1)

Como vendedor ambulante, quiero confirmar la entrega del pedido, para registrar que fue entregado al cliente y cobrado en efectivo.

**Why this priority**: Cierra el ciclo de la entrega directa y de la reserva con entrega, y registra el cobro, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un pedido en camino, confirmando la entrega y el cobro y verificando que el pedido pasa a entregado y su pago queda confirmado.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de entrega y cobro
   - **Given** un pedido mío está en camino y ya se lo entregué al cliente
   - **When** confirmo la entrega y el cobro en efectivo
   - **Then** el sistema cambia el estado del pedido a entregado, registra el pago como confirmado (RF-42) y habilita las calificaciones

2. **Scenario**: Confirmación fuera de secuencia
   - **Given** el pedido no está en camino
   - **When** intento confirmar la entrega
   - **Then** el sistema rechaza la operación e indica el estado actual

3. **Scenario**: El cliente no recibe o no paga
   - **Given** el cliente no se encuentra o no paga el pedido
   - **When** informo el problema
   - **Then** el sistema me permite crear un reporte (RF-60) y mantiene el pedido sin entregar

### Edge Cases

- ¿Qué sucede si el cliente paga un monto distinto al total del pedido?
- ¿Cómo se maneja una entrega parcial en la que el vendedor solo pudo llevar algunos ítems?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor ambulante confirmar la entrega únicamente de pedidos en estado "en camino".
- **FR-002**: El sistema DEBE solicitar al vendedor la confirmación del cobro en efectivo al registrar la entrega (RF-42).
- **FR-003**: El sistema DEBE cambiar el estado del pedido a "entregado" y habilitar las calificaciones entre las partes (RF-01).
- **FR-004**: El sistema DEBE impedir que la entrega de un mismo pedido se confirme más de una vez.

### Key Entities

- **Pedido**: Transición de esta funcionalidad: en camino → entregado.
- **Pago**: Registro en efectivo asociado al pedido, confirmado junto con la entrega.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede confirmar la entrega y el cobro en menos de 15 segundos.
- **SC-002**: El 100% de los pedidos entregados por vendedores ambulantes tienen su pago confirmado.

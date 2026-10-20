# Feature Specification: Confirmación de entrega directa

**Created**: 2026-09-18
**Requerimiento funcional**: RF-37
**Historias de usuario relacionadas**: HU-62

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cerrar la entrega y cobrar (Priority: P1)

Como vendedor ambulante, quiero confirmar la entrega del pedido, para registrar que fue entregado al cliente y pagado.

**Why this priority**: Cierra el ciclo de la entrega directa y registra el cobro, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un pedido en camino, confirmando la entrega y el cobro (efectivo o transferencia) y verificando que el pedido pasa a entregado y su pago queda confirmado.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de entrega y cobro en efectivo
   - **Given** un pedido mío está en camino, acepto efectivo, y ya se lo entregué al cliente que paga en efectivo
   - **When** confirmo la entrega y el cobro
   - **Then** el sistema cambia el estado del pedido a entregado, registra el pago como confirmado con medio "efectivo" (RF-42) y habilita las calificaciones

2. **Scenario**: Confirmación de entrega y cobro por transferencia
   - **Given** un pedido mío está en camino y el cliente paga por transferencia a mi cuenta registrada
   - **When** confirmo la entrega y verifico la transferencia
   - **Then** el sistema cambia el estado del pedido a entregado y registra el pago como confirmado con medio "cuenta bancaria"

3. **Scenario**: Confirmación fuera de secuencia
   - **Given** el pedido no está en camino
   - **When** intento confirmar la entrega
   - **Then** el sistema rechaza la operación e indica el estado actual

4. **Scenario**: El cliente no recibe o no paga
   - **Given** el cliente no se encuentra o no paga el pedido
   - **When** informo el problema
   - **Then** el sistema me permite crear un reporte (RF-60) y mantiene el pedido sin entregar

### Edge Cases

- ¿Qué sucede si el cliente paga un monto distinto al total del pedido?
- ¿Cómo se maneja una entrega parcial en la que el vendedor solo pudo llevar algunos ítems?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor ambulante confirmar la entrega únicamente de pedidos en estado "en camino".
- **FR-002**: El sistema DEBE solicitar al vendedor la confirmación del cobro al registrar la entrega, indicando el medio utilizado (efectivo o cuenta bancaria) entre los habilitados por el vendedor (RF-42, RF-63).
- **FR-003**: El sistema DEBE cambiar el estado del pedido a "entregado" y habilitar las calificaciones entre las partes (RF-01).
- **FR-004**: El sistema DEBE impedir que la entrega de un mismo pedido se confirme más de una vez.

### Key Entities

- **Pedido**: Transición de esta funcionalidad: en camino → entregado.
- **Pago**: Registro asociado al pedido, confirmado junto con la entrega, con su medio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede confirmar la entrega y el cobro en menos de 15 segundos.
- **SC-002**: El 100% de los pedidos entregados por vendedores ambulantes tienen su pago confirmado, con su medio correctamente registrado.
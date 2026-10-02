# Feature Specification: Confirmación de retiro del pedido

**Created**: 2026-09-18
**Requerimiento funcional**: RF-65
**Historias de usuario relacionadas**: HU-98

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cerrar la recogida cuando el cliente retira su pedido (Priority: P1)

Como vendedor de punto fijo, quiero confirmar el retiro de un pedido, para registrar que el cliente lo recogió y lo pagó.

**Why this priority**: Cierra el ciclo de la recogida en punto fijo y registra el cobro, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un pedido listo para recoger, confirmando el retiro y el cobro (efectivo o transferencia) y verificando que el pedido pasa a entregado.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de retiro y cobro en efectivo
   - **Given** un pedido mío está listo para recoger, acepto efectivo, y el cliente lo retira y paga en efectivo
   - **When** confirmo el retiro y el cobro
   - **Then** el sistema cambia el estado del pedido a entregado, registra el pago como confirmado con medio "efectivo" (RF-42) y habilita las calificaciones

2. **Scenario**: Confirmación de retiro y cobro por transferencia
   - **Given** un pedido mío está listo para recoger y el cliente paga por transferencia a mi cuenta registrada
   - **When** confirmo el retiro y verifico la transferencia
   - **Then** el sistema cambia el estado del pedido a entregado y registra el pago como confirmado con medio "cuenta bancaria" (RF-42)

3. **Scenario**: Pedido que aún no está listo
   - **Given** el pedido no está en estado listo para recoger
   - **When** intento confirmar su retiro
   - **Then** el sistema rechaza la operación e indica el estado actual

### Edge Cases

- ¿Qué sucede si el cliente retira solo una parte del pedido?
- ¿Cómo se maneja un pedido que el cliente nunca retira? ¿Se cancela y el producto vuelve al inventario?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor de punto fijo confirmar el retiro de un pedido en estado "listo para recoger".
- **FR-002**: El sistema DEBE solicitar la confirmación del cobro al registrar el retiro, indicando el medio utilizado (efectivo o cuenta bancaria) entre los habilitados por el vendedor (RF-42, RF-63).
- **FR-003**: El sistema DEBE cambiar el estado del pedido a "entregado" y habilitar las calificaciones (RF-01).

### Key Entities

- **Pedido**: Modalidad recogida; transición de esta funcionalidad: listo para recoger → entregado.
- **Pago**: Registro asociado al pedido, confirmado junto con el retiro, con su medio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede confirmar el retiro y el cobro en menos de 15 segundos.
- **SC-002**: El 100% de los pedidos retirados tienen su pago confirmado, con su medio correctamente registrado.
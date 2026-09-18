# Feature Specification: Confirmación de retiro de la reserva

**Created**: 2026-09-18
**Requerimiento funcional**: RF-65
**Historias de usuario relacionadas**: HU-98

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cerrar la reserva cuando el cliente la recoge (Priority: P1)

Como vendedor de punto fijo, quiero confirmar el retiro de la reserva, para registrar que el cliente recogió su pedido y lo pagó en efectivo.

**Why this priority**: Cierra el ciclo de la reserva con retiro y registra el cobro, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con una reserva lista para recoger, confirmando el retiro y el cobro y verificando que el pedido pasa a entregado.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de retiro y cobro
   - **Given** una reserva mía está lista para recoger y el cliente la retira
   - **When** confirmo el retiro y el cobro en efectivo
   - **Then** el sistema cambia el estado del pedido a entregado, registra el pago como confirmado (RF-42) y habilita las calificaciones

2. **Scenario**: Reserva que aún no está lista
   - **Given** la reserva no está en estado listo para recoger
   - **When** intento confirmar su retiro
   - **Then** el sistema rechaza la operación e indica el estado actual

### Edge Cases

- ¿Qué sucede si el cliente retira solo una parte de la reserva?
- ¿Cómo se maneja una reserva que el cliente nunca retira? ¿Se cancela y el producto vuelve al inventario?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor de punto fijo confirmar el retiro de una reserva en estado "listo para recoger".
- **FR-002**: El sistema DEBE solicitar la confirmación del cobro en efectivo al registrar el retiro (RF-42).
- **FR-003**: El sistema DEBE cambiar el estado del pedido a "entregado" y habilitar las calificaciones (RF-01).

### Key Entities

- **Reserva**: Transición de esta funcionalidad: listo para recoger → entregado.
- **Pago**: Registro en efectivo asociado al pedido, confirmado junto con el retiro.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede confirmar el retiro y el cobro en menos de 15 segundos.
- **SC-002**: El 100% de las reservas retiradas tienen su pago confirmado.

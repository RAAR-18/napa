# Feature Specification: Confirmación de pago de pedido

**Created**: 2026-09-11
**Requerimiento funcional**: RF-38
**Historias de usuario relacionadas**: HU-58

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Vendedor confirma el pago de un pedido (Priority: P2)

Como vendedor, quiero confirmar el pago de un pedido, para registrar que el pedido ha sido pagado.

**Why this priority**: Formaliza el estado de pago del pedido y habilita su procesamiento posterior; es importante para la operación del vendedor, aunque el pedido ya existe antes de esta confirmación, por lo que se clasifica como P2.

**Independent Test**: Puede probarse seleccionando un pedido pendiente de pago, marcándolo como pagado desde el panel del vendedor y verificando que el estado del pago cambia y queda reflejado en el historial.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación exitosa del pago de un pedido
   - **Given** el vendedor tiene un pedido con pago pendiente de confirmación
   - **When** el vendedor confirma que el pago fue recibido
   - **Then** el sistema registra el pago como confirmado y actualiza el estado del pedido

2. **Scenario**: Intento de confirmar el pago de un pedido ya confirmado
   - **Given** el pago de un pedido ya fue confirmado previamente
   - **When** el vendedor intenta confirmarlo nuevamente
   - **Then** el sistema informa que el pago ya se encuentra confirmado y no genera un nuevo registro

### Edge Cases

- ¿Qué sucede si el vendedor intenta confirmar el pago de un pedido que fue cancelado?
- ¿Cómo se maneja la confirmación si el proveedor de pago reporta un pago rechazado después de que el vendedor lo marcó como confirmado?
- ¿Qué ocurre si el vendedor confirma el pago de un pedido que no le pertenece?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor confirmar el pago de un pedido asociado a su emprendimiento.
- **FR-002**: El sistema DEBE actualizar el estado del pago y del pedido al registrarse la confirmación.
- **FR-003**: El sistema DEBE impedir que se confirme más de una vez el pago de un mismo pedido.
- **FR-004**: El sistema DEBE impedir que un vendedor confirme el pago de un pedido que no pertenece a su emprendimiento.
- **FR-005**: El sistema DEBE notificar al cliente cuando el pago de su pedido es confirmado.

### Key Entities

- **Pago**: Representa la transacción asociada a un pedido; atributos clave: monto, estado (pendiente, confirmado, rechazado), fecha de confirmación.
- **Pedido**: Entidad cuyo estado de pago se actualiza tras la confirmación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede confirmar el pago de un pedido en menos de 10 segundos.
- **SC-002**: El 100% de los pedidos con pago confirmado reflejan el estado actualizado de forma inmediata para el cliente.
- **SC-003**: El 0% de los pagos ya confirmados permite una segunda confirmación duplicada.

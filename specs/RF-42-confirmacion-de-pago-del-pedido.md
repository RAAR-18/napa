# Feature Specification: Confirmación de pago del pedido

**Created**: 2026-09-18
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-42
**Historias de usuario relacionadas**: HU-66

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Vendedor confirma que recibió el pago de un pedido (Priority: P1)

Como vendedor, quiero confirmar el pago de un pedido, en efectivo o por transferencia a mi cuenta bancaria, para registrar que el pedido fue pagado.

**Why this priority**: Formaliza el cobro de cada pedido en cualquiera de las modalidades y, en domicilio, es la condición para poder publicarlo; sin la confirmación no hay registro del ingreso, por lo que se clasifica como P1.

**Independent Test**: Puede probarse confirmando el pago de un pedido con cada medio habilitado y verificando que el estado del pago cambia y queda en el historial.

**Acceptance Scenarios**:

1. **Scenario**: Cobro en efectivo en una entrega directa o recogida
   - **Given** soy vendedor ambulante o de punto fijo, acepto efectivo, y entregué o el cliente retiró su pedido
   - **When** confirmo que recibí el efectivo al confirmar la entrega (RF-37) o el retiro (RF-65)
   - **Then** el sistema registra el pago como confirmado, indicando el medio "efectivo"

2. **Scenario**: Cobro por transferencia en una entrega directa o recogida
   - **Given** acepto pagos digitales y tengo una cuenta bancaria registrada (RF-63)
   - **When** confirmo que recibí la transferencia al confirmar la entrega o el retiro
   - **Then** el sistema registra el pago como confirmado, indicando el medio "cuenta bancaria"

3. **Scenario**: Cobro de un pedido de domicilio, antes de publicarlo
   - **Given** soy vendedor de punto fijo, acepté un pedido de domicilio y el cliente transfirió el total (productos más tarifa del domicilio) a mi cuenta
   - **When** confirmo que recibí la transferencia
   - **Then** el sistema registra el pago como confirmado por transferencia por el total del pedido y me habilita a publicar el domicilio (RF-14); el domicilio no admite cobro en efectivo

4. **Scenario**: Confirmación de pago de un domicilio antes de aceptarlo
   - **Given** un pedido de domicilio sigue en estado pendiente
   - **When** intento confirmar su pago
   - **Then** el sistema rechaza la operación e indica que el pedido primero debe aceptarse

5. **Scenario**: Intento de confirmar dos veces
   - **Given** el pago de un pedido ya fue confirmado
   - **When** intento confirmarlo nuevamente
   - **Then** el sistema informa que ya está confirmado y no genera un nuevo registro

### Edge Cases

- ¿Qué sucede si el vendedor intenta confirmar el pago de un pedido cancelado o rechazado?
- ¿Qué ocurre si el monto recibido es distinto al total del pedido?
- ¿Qué pasa si el vendedor confirma el pago de un pedido que no le pertenece?
- ¿Qué ocurre si el pedido de domicilio fue aceptado y el cliente nunca transfiere? (no hay plazo ni cancelación automática definidos: el domicilio simplemente no puede publicarse; punto pendiente de validar)
- ¿Cómo se verifica que la transferencia recibida corresponde al pedido y al monto correctos, si la plataforma no procesa el dinero?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor (ambulante o de punto fijo) confirmar que recibió el pago de un pedido de su emprendimiento, indicando el medio utilizado (efectivo o cuenta bancaria).
- **FR-002**: El sistema DEBE solicitar esta confirmación al registrar la entrega directa (RF-37) o el retiro del pedido (RF-65); en los pedidos de domicilio, DEBE permitirla únicamente a partir de que el pedido esté aceptado y antes de publicar el domicilio (RF-14).
- **FR-003**: El sistema DEBE impedir que el pago de un mismo pedido se confirme más de una vez.
- **FR-004**: El sistema DEBE impedir que un vendedor confirme el pago de un pedido que no pertenece a su emprendimiento.
- **FR-005**: El sistema DEBE notificar al cliente cuando el pago de su pedido sea confirmado.
- **FR-006**: El sistema DEBE permitir al vendedor registrar una cuenta bancaria para recibir pagos digitales cuando decida aceptarlos (RF-63), sin procesar ni almacenar datos de tarjetas ni billeteras electrónicas de terceros.
- **FR-007**: El sistema DEBE exigir pago por transferencia (nunca efectivo) en los pedidos con modalidad de domicilio, por el total del pedido: productos más tarifa del domicilio.
- **FR-008**: El sistema DEBE habilitar la publicación del domicilio (RF-14) únicamente cuando el pago del pedido de domicilio esté confirmado.

### Key Entities

- **Pago**: Registro asociado a un pedido; estados: pendiente de confirmación y confirmado. Atributo adicional: medio (efectivo o cuenta bancaria). En domicilio, el monto es el total de productos más tarifa. Es distinto del pago de la tarifa al domiciliario (RF-43).
- **Pedido**: Su estado de pago se actualiza al confirmarse el cobro.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede confirmar el pago de un pedido en menos de 10 segundos.
- **SC-002**: El 100% de los pedidos entregados quedan con el pago confirmado o pendiente de confirmación, sin estados intermedios.
- **SC-003**: El 0% de los pagos ya confirmados permite una segunda confirmación.
- **SC-004**: El 100% de los pedidos de domicilio confirmados quedan registrados con medio "cuenta bancaria", nunca "efectivo".
- **SC-005**: El 0% de los pedidos de domicilio habilita su publicación sin el pago confirmado.
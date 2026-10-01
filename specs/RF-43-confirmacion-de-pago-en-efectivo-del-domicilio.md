# Feature Specification: Confirmación de pago digital del domicilio

**Created**: 2026-09-18
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-43
**Historias de usuario relacionadas**: HU-68

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Domiciliario confirma que el vendedor recibió el pago digital (Priority: P2)

Como domiciliario, quiero confirmar que el pago digital del domicilio fue recibido por el vendedor, para registrar el cobro por el servicio de entrega.

**Why this priority**: Registra el ingreso del vendedor por el pedido y del domiciliario por su servicio, pero depende de que la entrega ya se haya realizado, por lo que se clasifica como P2.

**Independent Test**: Puede probarse con un domicilio entregado, confirmando que la transferencia llegó y verificando que el pago queda registrado en el historial del domiciliario y del vendedor.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación del cobro del domicilio
   - **Given** confirmé la entrega de un domicilio (con el código validado) y el cliente pagó por transferencia
   - **When** confirmo el pago del domicilio
   - **Then** el sistema registra el pago como confirmado, con medio "cuenta bancaria" y el valor de la tarifa publicada del domicilio

2. **Scenario**: Confirmación antes de la entrega
   - **Given** el domicilio aún no está entregado
   - **When** intento confirmar el pago
   - **Then** el sistema rechaza la operación e indica que primero debe confirmarse la entrega

3. **Scenario**: Confirmación duplicada
   - **Given** el pago del domicilio ya fue confirmado
   - **When** intento confirmarlo otra vez
   - **Then** el sistema informa que ya está confirmado y no crea otro registro

### Edge Cases

- ¿Qué ocurre si el cliente paga un monto distinto a la tarifa publicada del domicilio?
- ¿Cómo se maneja el caso en que la transferencia del cliente no se acredita a tiempo?
- ¿Qué sucede si el domiciliario confirma el pago de un domicilio que no le fue asignado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario asignado confirmar el pago digital del domicilio a partir del estado "entregado".
- **FR-002**: El sistema DEBE registrar el pago con medio "cuenta bancaria" y el valor de la tarifa publicada del domicilio.
- **FR-003**: El sistema DEBE impedir que el pago de un mismo domicilio se confirme más de una vez.
- **FR-004**: El sistema DEBE impedir que un domiciliario confirme el pago de un domicilio que no tiene asignado.
- **FR-005**: El sistema NO DEBE ofrecer la opción de registrar el pago del domicilio en efectivo: el domicilio exige exclusivamente pago digital.

### Key Entities

- **Pago**: Registro digital asociado a un domicilio; estados: pendiente de confirmación y confirmado.
- **Domicilio**: Su tarifa publicada determina el monto del pago.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El domiciliario puede confirmar el pago de un domicilio en menos de 10 segundos.
- **SC-002**: El 0% de los pagos de domicilio ya confirmados permite una segunda confirmación.
- **SC-003**: El 100% de los pagos de domicilio quedan registrados con medio "cuenta bancaria".
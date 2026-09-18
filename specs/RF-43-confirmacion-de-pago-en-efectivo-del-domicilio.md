# Feature Specification: Confirmación de pago en efectivo del domicilio

**Created**: 2026-09-18
**Requerimiento funcional**: RF-43
**Historias de usuario relacionadas**: HU-68

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Domiciliario confirma que cobró su servicio (Priority: P2)

Como domiciliario, quiero confirmar el pago en efectivo del domicilio, para registrar el cobro por el servicio de entrega.

**Why this priority**: Registra el ingreso del domiciliario por cada servicio, pero depende de que la entrega ya se haya realizado, por lo que se clasifica como P2.

**Independent Test**: Puede probarse con un domicilio entregado, confirmando el cobro y verificando que el pago queda registrado en el historial del domiciliario.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación del cobro del domicilio
   - **Given** confirmé la entrega de un domicilio y el cliente me pagó en efectivo
   - **When** confirmo el pago del domicilio
   - **Then** el sistema registra el pago como confirmado con el valor vigente del domicilio (tarifa publicada o valor de la oferta aceptada)

2. **Scenario**: Confirmación antes de la entrega
   - **Given** el domicilio aún no está entregado
   - **When** intento confirmar el pago
   - **Then** el sistema rechaza la operación e indica que primero debe confirmarse la entrega

3. **Scenario**: Confirmación duplicada
   - **Given** el pago del domicilio ya fue confirmado
   - **When** intento confirmarlo otra vez
   - **Then** el sistema informa que ya está confirmado y no crea otro registro

### Edge Cases

- ¿Qué ocurre si el cliente paga un monto distinto al valor vigente del domicilio?
- ¿Cómo se maneja el caso en que el cliente se niega a pagar el domicilio?
- ¿Qué sucede si el domiciliario confirma el pago de un domicilio que no le fue asignado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario asignado confirmar el pago en efectivo del domicilio a partir del estado "entregado".
- **FR-002**: El sistema DEBE registrar el pago con el valor vigente del domicilio: la tarifa publicada o el valor de la oferta aceptada.
- **FR-003**: El sistema DEBE impedir que el pago de un mismo domicilio se confirme más de una vez.
- **FR-004**: El sistema DEBE impedir que un domiciliario confirme el pago de un domicilio que no tiene asignado.

### Key Entities

- **Pago**: Registro en efectivo asociado a un domicilio; estados: pendiente de confirmación y confirmado.
- **Domicilio**: Su valor vigente determina el monto del pago.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El domiciliario puede confirmar el pago de un domicilio en menos de 10 segundos.
- **SC-002**: El 0% de los pagos de domicilio ya confirmados permite una segunda confirmación.

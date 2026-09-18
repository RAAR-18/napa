# Feature Specification: Confirmación de pago del pedido

**Created**: 2026-09-18
**Requerimiento funcional**: RF-43
**Historias de usuario relacionadas**: HU-67

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Vendedor confirma que recibió el pago de un pedido (Priority: P1)

Como vendedor, quiero confirmar el pago de un pedido (efectivo o transferencia), para registrar que el pedido fue pagado.

**Why this priority**: Formaliza el cobro de cada pedido en cualquiera de las modalidades; sin la confirmación no hay registro del ingreso, por lo que se clasifica como P1.

**Independent Test**: Puede probarse confirmando el pago de un pedido entregado y verificando que el estado del pago cambia y queda en el historial.

**Acceptance Scenarios**:

1. **Scenario**: Cobro en una entrega directa o reserva con entrega
   - **Given** soy vendedor ambulante y entregué un pedido al cliente
   - **When** confirmo que recibí el pago (efectivo o transferencia) al confirmar la entrega
   - **Then** el sistema registra el pago como confirmado con su método

2. **Scenario**: Cobro en el retiro de una reserva
   - **Given** soy vendedor de punto fijo y el cliente retira su reserva
   - **When** confirmo que recibí el pago (efectivo o transferencia) al confirmar el retiro
   - **Then** el sistema registra el pago como confirmado con su método

3. **Scenario**: Cobro de un pedido de domicilio
   - **Given** soy vendedor de punto fijo y el pedido de domicilio, que solo admite transferencia, fue entregado por un domiciliario
   - **When** confirmo que recibí la transferencia del valor de los productos e ingreso su referencia
   - **Then** el sistema registra el pago como confirmado

4. **Scenario**: Intento de confirmar dos veces
   - **Given** el pago de un pedido ya fue confirmado
   - **When** intento confirmarlo nuevamente
   - **Then** el sistema informa que ya está confirmado y no genera un nuevo registro

### Edge Cases

- **¿Cómo se comprueba una transferencia si la plataforma no procesa el pago?** La transferencia se hace fuera de la aplicación; el vendedor la verifica en su banco o billetera y registra su referencia al confirmar el pago.
- **¿Qué sucede si el vendedor intenta confirmar el pago de un pedido cancelado o rechazado?** No lo permite: solo se confirma el pago de pedidos entregados.
- **¿Qué ocurre si el monto recibido es distinto al total del pedido?** El vendedor registra el monto realmente recibido; si difiere del total, el pago queda marcado con diferencia para revisión.
- **¿Qué pasa si el vendedor confirma el pago de un pedido que no le pertenece?** Se rechaza la operación.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor (ambulante o de punto fijo) confirmar que recibió el pago de un pedido de su emprendimiento, con el método elegido por el cliente.
- **FR-002**: El sistema DEBE solicitar esta confirmación al registrar la entrega directa (RF-38) o el retiro de la reserva (RF-66), y permitirla para los pedidos de domicilio una vez entregados.
- **FR-003**: El sistema DEBE solicitar la referencia de la transferencia cuando el método de pago sea transferencia.
- **FR-004**: El sistema DEBE impedir que el pago de un mismo pedido se confirme más de una vez.
- **FR-005**: El sistema DEBE impedir que un vendedor confirme el pago de un pedido que no pertenece a su emprendimiento.
- **FR-006**: El sistema DEBE notificar al cliente cuando el pago de su pedido sea confirmado.
- **FR-007**: El sistema NO DEBE procesar ni intermediar el dinero ni almacenar datos de tarjetas o cuentas: el pago se realiza fuera de la plataforma y esta solo registra el método y la confirmación (RNF29).

### Key Entities

- **Pago**: Registro asociado a un pedido; incluye monto, método (efectivo o transferencia), referencia de la transferencia cuando aplica y estado (pendiente de confirmación o confirmado).
- **Pedido**: Su estado de pago se actualiza al confirmarse el cobro.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Pago recibido | Sí | Confirmación de que recibió el pago del pedido. |
| Monto recibido | Sí | Entero en pesos colombianos (COP); se propone el total del pedido y puede ajustarse (una diferencia queda marcada para revisión). |
| Referencia de la transferencia | Condicional | Obligatoria si el método de pago es transferencia; de 4 a 40 caracteres. |

**Datos que asigna el sistema**

- Vendedor, fecha y hora de la confirmación.
- Estado del pago "confirmado" y método tomado del pedido.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede confirmar el pago de un pedido en menos de 10 segundos.
- **SC-002**: El 100% de los pedidos entregados quedan con el pago confirmado o pendiente de confirmación, sin estados intermedios.
- **SC-003**: El 0% de los pagos ya confirmados permite una segunda confirmación.

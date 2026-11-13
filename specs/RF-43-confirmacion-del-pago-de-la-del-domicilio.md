# Feature Specification: Confirmación del pago de la tarifa del domicilio

**Created**: 2026-09-18
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-43
**Historias de usuario relacionadas**: HU-104, HU-68

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Vendedor paga y registra la tarifa del domiciliario (Priority: P2)

Como vendedor de punto fijo, quiero registrar y confirmar el pago de la tarifa al domiciliario una vez entregado el pedido, para cumplir con el pago de su servicio.

**Why this priority**: El cliente ya pagó al vendedor el total (productos más tarifa del domicilio) antes de la publicación; este paso traslada la tarifa al domiciliario y deja constancia del pago. Depende de que la entrega ya se haya realizado, por lo que se clasifica como P2.

**Independent Test**: Puede probarse con un domicilio entregado, registrando como vendedor el pago de la tarifa y verificando que queda pendiente de confirmación del domiciliario.

**Acceptance Scenarios**:

1. **Scenario**: Registro del pago de la tarifa
   - **Given** el domiciliario confirmó la entrega del domicilio (con el código validado) y el cliente ya había pagado el total del pedido
   - **When** el vendedor transfiere la tarifa al domiciliario y confirma el pago
   - **Then** el sistema registra el pago de la tarifa con medio "cuenta bancaria" y el valor de la tarifa publicada del domicilio, lo deja pendiente de confirmación del domiciliario y lo notifica

2. **Scenario**: Registro antes de la entrega
   - **Given** el domicilio aún no está entregado
   - **When** el vendedor intenta registrar el pago de la tarifa
   - **Then** el sistema rechaza la operación e indica que primero debe confirmarse la entrega

3. **Scenario**: Registro duplicado
   - **Given** el vendedor ya registró el pago de la tarifa de ese domicilio
   - **When** intenta registrarlo otra vez
   - **Then** el sistema informa que ya está registrado y no crea otro registro

---

### User Story 2 - Domiciliario confirma que recibió su tarifa (Priority: P2)

Como domiciliario, quiero confirmar que recibí el pago de mi tarifa por el domicilio, para registrar el cobro por el servicio de entrega.

**Why this priority**: Cierra el pago del servicio con la conformidad de quien lo recibe y alimenta su historial de ingresos; depende de que el vendedor ya haya registrado el pago, por lo que se clasifica como P2.

**Independent Test**: Puede probarse con un pago de tarifa registrado por el vendedor, confirmando la recepción como domiciliario y verificando que el pago queda confirmado en el historial de ambos.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de recepción de la tarifa
   - **Given** el vendedor registró el pago de la tarifa de un domicilio que realicé
   - **When** confirmo que recibí la transferencia
   - **Then** el sistema marca el pago como confirmado y lo refleja en el historial del domiciliario y del vendedor

2. **Scenario**: Confirmación sin pago registrado por el vendedor
   - **Given** el vendedor aún no registró el pago de la tarifa
   - **When** intento confirmar la recepción
   - **Then** el sistema rechaza la operación e indica que el vendedor todavía no registró el pago

3. **Scenario**: Confirmación duplicada
   - **Given** ya confirmé la recepción de la tarifa
   - **When** intento confirmarla otra vez
   - **Then** el sistema informa que ya está confirmada y no crea otro registro

### Edge Cases

- ¿Qué ocurre si el domiciliario no recibió la transferencia, o recibió un monto distinto a la tarifa publicada? (se recomienda permitir crear un reporte, RF-60)
- ¿Qué sucede si el vendedor no registra el pago de la tarifa en un tiempo razonable tras la entrega? ¿Se notifica al domiciliario o se escala al administrador?
- ¿Qué ocurre si el domicilio es cancelado por el administrador después de que el cliente pagó el total del pedido?
- ¿Qué sucede si el domiciliario intenta confirmar el pago de un domicilio que no le fue asignado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor de punto fijo registrar y confirmar el pago de la tarifa al domiciliario únicamente a partir del estado "entregado" del domicilio.
- **FR-002**: El sistema DEBE registrar el pago de la tarifa con medio "cuenta bancaria" y el valor de la tarifa publicada del domicilio.
- **FR-003**: El sistema DEBE permitir al domiciliario asignado confirmar la recepción de la tarifa únicamente después de que el vendedor haya registrado el pago.
- **FR-004**: El sistema DEBE impedir que el pago de la tarifa de un mismo domicilio se registre o se confirme más de una vez.
- **FR-005**: El sistema DEBE impedir que un domiciliario confirme el pago de un domicilio que no tiene asignado, y que un vendedor registre el pago de un domicilio que no es suyo.
- **FR-006**: El sistema NO DEBE ofrecer la opción de registrar la tarifa en efectivo: el domicilio exige exclusivamente pago digital.
- **FR-007**: El sistema DEBE notificar al domiciliario cuando el vendedor registre el pago de su tarifa.

### Key Entities

- **Pago de la tarifa**: Registro digital asociado a un domicilio; atributos clave: monto (tarifa publicada), medio ("cuenta bancaria"), fecha y estado (pendiente de registro, registrado por el vendedor, confirmado por el domiciliario). Es distinto del pago del pedido (RF-42), que el cliente hace al vendedor por productos más tarifa.
- **Domicilio**: Su tarifa publicada determina el monto del pago.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede registrar el pago de la tarifa, y el domiciliario confirmarlo, en menos de 10 segundos cada uno.
- **SC-002**: El 0% de los pagos de tarifa ya confirmados permite una segunda confirmación.
- **SC-003**: El 100% de los pagos de tarifa quedan registrados con medio "cuenta bancaria".
- **SC-004**: El domiciliario recibe la notificación del pago registrado por el vendedor en menos de 1 minuto.
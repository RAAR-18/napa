# Feature Specification: Confirmación de pago del domicilio

**Created**: 2026-09-18
**Requerimiento funcional**: RF-44
**Historias de usuario relacionadas**: HU-69

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Domiciliario confirma que recibió el pago de su servicio (Priority: P2)

Como domiciliario, quiero confirmar el pago del domicilio, para registrar el cobro por el servicio de entrega.

**Why this priority**: Registra el ingreso del domiciliario por cada servicio, pero depende de que el domicilio ya esté asignado, por lo que se clasifica como P2.

**Independent Test**: Puede probarse con un domicilio asignado, confirmando el pago y verificando que queda registrado en el historial del domiciliario.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación del pago del domicilio
   - **Given** tengo un domicilio asignado y el cliente me transfirió el valor del domicilio
   - **When** confirmo el pago del domicilio e ingreso la referencia de la transferencia
   - **Then** el sistema registra el pago como confirmado con el valor vigente del domicilio (tarifa publicada o valor de la oferta aceptada)

2. **Scenario**: Confirmación sin domicilio asignado
   - **Given** el domicilio aún no está asignado a mí
   - **When** intento confirmar el pago
   - **Then** el sistema rechaza la operación e indica que el domicilio no me pertenece

3. **Scenario**: Confirmación duplicada
   - **Given** el pago del domicilio ya fue confirmado
   - **When** intento confirmarlo otra vez
   - **Then** el sistema informa que ya está confirmado y no crea otro registro

### Edge Cases

- **¿Qué ocurre si el cliente paga un monto distinto al valor vigente del domicilio?** El domiciliario registra el monto realmente recibido; si difiere del valor vigente, el pago queda marcado con diferencia para revisión.
- **¿Cómo se maneja el caso en que el cliente se niega a pagar el domicilio?** El domiciliario no confirma el pago y crea un reporte (RF-61) para que el administrador decida.
- **¿Qué sucede si el domiciliario confirma el pago de un domicilio que no le fue asignado?** Se rechaza la operación: el domicilio no le pertenece.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario asignado confirmar el pago del domicilio a partir del estado "asignado".
- **FR-002**: El sistema DEBE registrar el pago con el valor vigente del domicilio: la tarifa publicada o el valor de la oferta aceptada.
- **FR-003**: El sistema DEBE solicitar la referencia de la transferencia, único método permitido para los domicilios.
- **FR-004**: El sistema DEBE impedir que el pago de un mismo domicilio se confirme más de una vez.
- **FR-005**: El sistema DEBE impedir que un domiciliario confirme el pago de un domicilio que no tiene asignado.

### Key Entities

- **Pago**: Registro asociado a un domicilio; método transferencia, referencia y estado (pendiente de confirmación o confirmado).
- **Domicilio**: Su valor vigente determina el monto del pago.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Pago recibido | Sí | Confirmación de que recibió el pago del domicilio. |
| Monto recibido | Sí | Entero en pesos colombianos (COP); se propone el valor vigente del domicilio y puede ajustarse. |
| Referencia de la transferencia | Sí | De 4 a 40 caracteres; los domicilios solo admiten transferencia. |

**Datos que asigna el sistema**

- Domiciliario, fecha y hora de la confirmación.
- Estado del pago "confirmado".

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El domiciliario puede confirmar el pago de un domicilio en menos de 10 segundos.
- **SC-002**: El 0% de los pagos de domicilio ya confirmados permite una segunda confirmación.

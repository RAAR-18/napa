# Feature Specification: Confirmación de pago de domicilio

**Created**: 2026-09-11
**Requerimiento funcional**: RF-39
**Historias de usuario relacionadas**: HU-60

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Domiciliario confirma el pago del servicio de entrega (Priority: P2)

Como domiciliario, quiero confirmar el pago del domicilio, para registrar el pago correspondiente al servicio de entrega.

**Why this priority**: Formaliza el cierre económico del servicio de entrega; es relevante para la operación del domiciliario pero no bloquea el ciclo de vida del pedido en sí, por lo que se clasifica como P2.

**Independent Test**: Puede probarse seleccionando un domicilio entregado con pago pendiente, confirmando el pago desde el panel del domiciliario y verificando que el estado del pago cambia correctamente.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación exitosa del pago de un domicilio
   - **Given** el domiciliario completó la entrega de un domicilio con pago pendiente
   - **When** el domiciliario confirma que recibió el pago del servicio de entrega
   - **Then** el sistema registra el pago del domicilio como confirmado

2. **Scenario**: Intento de confirmar el pago de un domicilio no entregado
   - **Given** un domicilio aún no ha sido marcado como entregado
   - **When** el domiciliario intenta confirmar su pago
   - **Then** el sistema impide la confirmación e indica que la entrega debe completarse primero

### Edge Cases

- ¿Qué sucede si el domiciliario intenta confirmar el pago de un domicilio que no le fue asignado?
- ¿Cómo se maneja la confirmación si el pago se realizó por un medio digital y el proveedor de pago aún no lo reporta como recibido?
- ¿Qué ocurre si el domiciliario confirma el pago de un domicilio cuyo monto no coincide con la tarifa registrada?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario confirmar el pago de un domicilio que le fue asignado y entregado.
- **FR-002**: El sistema DEBE impedir la confirmación del pago de un domicilio que aún no ha sido marcado como entregado.
- **FR-003**: El sistema DEBE impedir que un domiciliario confirme el pago de un domicilio que no le fue asignado.
- **FR-004**: El sistema DEBE registrar el monto y la fecha de confirmación del pago del domicilio.

### Key Entities

- **Pago**: Representa la transacción asociada a un servicio de entrega; atributos clave: monto, estado, fecha de confirmación.
- **Domicilio**: Servicio de entrega cuyo pago se confirma; debe estar en estado "entregado" para habilitar la confirmación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El domiciliario puede confirmar el pago de un domicilio en menos de 10 segundos.
- **SC-002**: El 100% de los domicilios con pago confirmado corresponden a entregas previamente completadas.
- **SC-003**: El 0% de los domiciliarios logra confirmar el pago de un domicilio no asignado a ellos.

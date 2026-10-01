# Feature Specification: Confirmación de entrega por el domiciliario

**Created**: 2026-09-18
**Requerimiento funcional**: RF-24
**Historias de usuario relacionadas**: HU-47

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar que el pedido llegó al destino (Priority: P1)

Como domiciliario, quiero confirmar la entrega del pedido ingresando el código de confirmación que me da el cliente, para registrar que lo llevé al punto de destino.

**Why this priority**: Cierra la parte operativa del domiciliario y habilita la confirmación del cliente y el cobro, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un domicilio en camino, ingresando el código de confirmación que el cliente le muestra al domiciliario y verificando que el estado pasa a entregado y que el cliente es notificado para confirmar la llegada.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de entrega con código válido
   - **Given** el domicilio está en camino, llegué al punto de destino y el cliente me entrega su código de confirmación
   - **When** ingreso el código y confirmo la entrega
   - **Then** el sistema valida el código, cambia el estado a entregado, lo registra en la trazabilidad y notifica al cliente para que confirme la llegada

2. **Scenario**: Código incorrecto
   - **Given** estoy confirmando la entrega de un domicilio
   - **When** ingreso un código que no corresponde al domicilio
   - **Then** el sistema rechaza la confirmación, indica que el código no es válido y me permite intentar de nuevo

3. **Scenario**: Confirmación fuera de secuencia
   - **Given** el domicilio no está en estado en camino
   - **When** intento confirmar la entrega
   - **Then** el sistema rechaza la operación e indica el estado actual

4. **Scenario**: Registro del cobro posterior
   - **Given** confirmé la entrega del pedido con el código correcto
   - **When** reviso las acciones disponibles
   - **Then** el sistema me permite registrar el pago digital del domicilio (RF-43)

### Edge Cases

- ¿Cuántos intentos de código incorrecto se permiten antes de bloquear temporalmente la confirmación o alertar a soporte?
- ¿Qué ocurre si el cliente no tiene o no encuentra su código (ej. sin conexión)? ¿Existe una vía de respaldo con intervención del administrador o un reporte (RF-60)?
- ¿Se valida que la ubicación del domiciliario esté cerca del punto B al confirmar la entrega, además del código?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario asignado confirmar la entrega únicamente cuando el domicilio esté en estado "en camino".
- **FR-002**: El sistema DEBE exigir al domiciliario el ingreso del código de confirmación del domicilio como parte de la confirmación de entrega, y rechazar la confirmación si el código no coincide.
- **FR-003**: El sistema DEBE cambiar el estado del domicilio a "entregado", registrar el evento en la trazabilidad y notificar al cliente, una vez validado el código.
- **FR-004**: El sistema DEBE impedir que la entrega se confirme más de una vez.
- **FR-005**: El sistema DEBE habilitar el registro del pago digital del domicilio a partir del estado "entregado" (RF-43).

### Key Entities

- **Domicilio**: Transición de esta funcionalidad: en camino → entregado (pendiente de confirmación del cliente). Incluye un código de confirmación único generado al asignarse.
- **Código de confirmación**: Valor asociado al domicilio, visible para el cliente (RF-25) y exigido al domiciliario para cerrar la entrega. Refuerza, no reemplaza, la doble confirmación (domiciliario + cliente).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las entregas confirmadas quedan registradas con fecha, hora y validación del código en la trazabilidad.
- **SC-002**: El cliente recibe la solicitud de confirmar la llegada en menos de 1 minuto.
- **SC-003**: El 0% de las entregas puede confirmarse sin un código válido o dos veces.
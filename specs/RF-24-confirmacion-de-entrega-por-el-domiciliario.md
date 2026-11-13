# Feature Specification: Confirmación de entrega por el domiciliario

**Created**: 2026-09-18
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-24
**Historias de usuario relacionadas**: HU-47

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar que el pedido llegó al destino (Priority: P1)

Como domiciliario, quiero confirmar la entrega del pedido ingresando el código de confirmación que me da el cliente, para registrar que lo llevé al punto de destino.

<<<<<<< HEAD
**Why this priority**: Cierra la parte operativa del domiciliario y habilita la confirmación de llegada del cliente, por lo que se clasifica como P1.
=======
**Why this priority**: Cierra la parte operativa del domiciliario y habilita la confirmación del cliente y el pago de su tarifa, por lo que se clasifica como P1.
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b

**Independent Test**: Puede probarse con un domicilio en camino, ingresando el código de confirmación que el cliente le muestra al domiciliario y verificando que el estado pasa a entregado y que el cliente y el vendedor son notificados.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de entrega con código válido
   - **Given** el domicilio está en camino, llegué al punto de destino y el cliente me entrega su código de confirmación
   - **When** ingreso el código y confirmo la entrega
   - **Then** el sistema valida el código, cambia el estado a entregado, lo registra en la trazabilidad, notifica al cliente para que confirme la llegada y notifica al vendedor de punto fijo para que pague mi tarifa

2. **Scenario**: Código incorrecto
   - **Given** estoy confirmando la entrega de un domicilio
   - **When** ingreso un código que no corresponde al domicilio
   - **Then** el sistema rechaza la confirmación, indica que el código no es válido y me permite intentar de nuevo

3. **Scenario**: Confirmación fuera de secuencia
   - **Given** el domicilio no está en estado en camino
   - **When** intento confirmar la entrega
   - **Then** el sistema rechaza la operación e indica el estado actual

<<<<<<< HEAD
3. **Scenario**: Recordatorio del pago del domicilio
   - **Given** confirmé la entrega del pedido y el pago del domicilio sigue pendiente
   - **When** reviso las acciones disponibles
   - **Then** el sistema me recuerda confirmar el pago del domicilio (RF-44)

### Edge Cases

- **¿Se valida que la ubicación del domiciliario esté cerca del punto B al confirmar la entrega?** Sí, como advertencia: si está a más de 300 m del punto B el sistema avisa, no bloquea (el GPS puede ser impreciso) y deja registro.
- **¿Qué ocurre si el cliente no se encuentra en el punto de destino?** El domiciliario intenta contactarlo; si no responde en 10 minutos puede reportar el problema (RF-61) para que el administrador decida.
- **¿Cómo se maneja una doble confirmación enviada por reintentos de conexión?** La segunda confirmación se ignora e informa que la entrega ya fue confirmada.
=======
4. **Scenario**: Habilitación del pago de la tarifa
   - **Given** confirmé la entrega del pedido con el código correcto
   - **When** el sistema registra el estado entregado
   - **Then** el vendedor de punto fijo puede registrar el pago de mi tarifa y yo podré confirmar su recepción (RF-43)

### Edge Cases

- ¿Cuántos intentos de código incorrecto se permiten antes de bloquear temporalmente la confirmación o alertar a soporte?
- ¿Qué ocurre si el cliente no tiene o no encuentra su código (ej. sin conexión)? ¿Existe una vía de respaldo con intervención del administrador o un reporte (RF-60)?
- ¿Se valida que la ubicación del domiciliario esté cerca del punto B al confirmar la entrega, además del código?
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario asignado confirmar la entrega únicamente cuando el domicilio esté en estado "en camino".
<<<<<<< HEAD
- **FR-002**: El sistema DEBE cambiar el estado del domicilio a "entregado", registrar el evento en la trazabilidad y notificar al cliente.
- **FR-003**: El sistema DEBE impedir que la entrega se confirme más de una vez.
- **FR-004**: El sistema DEBE recordar al domiciliario confirmar el pago del domicilio si aún está pendiente (RF-44).
=======
- **FR-002**: El sistema DEBE exigir al domiciliario el ingreso del código de confirmación del domicilio como parte de la confirmación de entrega, y rechazar la confirmación si el código no coincide.
- **FR-003**: El sistema DEBE cambiar el estado del domicilio a "entregado", registrar el evento en la trazabilidad y notificar al cliente y al vendedor de punto fijo, una vez validado el código.
- **FR-004**: El sistema DEBE impedir que la entrega se confirme más de una vez.
- **FR-005**: El sistema DEBE habilitar el registro y la confirmación del pago de la tarifa del domicilio a partir del estado "entregado" (RF-43).
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b

### Key Entities

- **Domicilio**: Transición de esta funcionalidad: en camino → entregado (pendiente de confirmación del cliente). Incluye un código de confirmación único generado al asignarse.
- **Código de confirmación**: Valor asociado al domicilio, visible para el cliente (RF-25) y exigido al domiciliario para cerrar la entrega. Refuerza, no reemplaza, la doble confirmación (domiciliario + cliente).

### Data Rules

**Datos que ingresa el usuario**: Ninguno. El domiciliario confirma la entrega en el punto de destino.

**Datos que asigna el sistema**

- Estado "entregado", fecha, hora y ubicación del dispositivo (advertencia si está a más de 300 m del punto B).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las entregas confirmadas quedan registradas con fecha, hora y validación del código en la trazabilidad.
- **SC-002**: El cliente recibe la solicitud de confirmar la llegada en menos de 1 minuto.
- **SC-003**: El 0% de las entregas puede confirmarse sin un código válido o dos veces.
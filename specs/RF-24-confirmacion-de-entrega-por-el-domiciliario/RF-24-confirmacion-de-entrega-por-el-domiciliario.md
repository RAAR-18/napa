# Feature Specification: Confirmación de entrega por el domiciliario

**Created**: 2026-09-18
**Actualizado**: 2026-10-14
**Requerimiento funcional**: RF-24
**Historias de usuario relacionadas**: HU-47
**Relacionado con**: RF-25 (código del cliente), RF-43, RF-01, RF-60

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cerrar la entrega con el código del cliente (Priority: P1)

Como domiciliario, quiero confirmar la entrega del pedido ingresando el código de confirmación que me da el cliente, para registrar que lo llevé al punto de destino y cerrar el domicilio.

**Why this priority**: El código del cliente es la única confirmación de la entrega: cierra el domicilio y el pedido, habilita el pago de mi tarifa y las calificaciones, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un domicilio en camino, ingresando el código que el cliente le dice al domiciliario y verificando que el domicilio y el pedido pasan a entregado y que el vendedor y el cliente son notificados.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de entrega con código válido
   - **Given** el domicilio está en camino, llegué al punto de destino y el cliente me dice su código de confirmación
   - **When** ingreso el código y confirmo la entrega
   - **Then** el sistema valida el código, cambia el domicilio y su pedido a entregado, lo registra en la trazabilidad, notifica al vendedor de punto fijo para que pague mi tarifa y notifica al cliente que su pedido fue entregado

2. **Scenario**: Código incorrecto
   - **Given** estoy confirmando la entrega de un domicilio
   - **When** ingreso un código que no corresponde al domicilio
   - **Then** el sistema rechaza la confirmación, indica que el código no es válido y cuántos intentos me quedan

3. **Scenario**: Intentos agotados
   - **Given** fallé 5 veces al ingresar el código de un domicilio
   - **When** intento ingresarlo otra vez
   - **Then** el sistema bloquea la confirmación durante 15 minutos e indica que puedo crear un reporte (RF-60)

4. **Scenario**: Confirmación fuera de secuencia
   - **Given** el domicilio no está en estado en camino
   - **When** intento confirmar la entrega
   - **Then** el sistema rechaza la operación e indica el estado actual

5. **Scenario**: Habilitación del pago de la tarifa
   - **Given** confirmé la entrega con el código correcto
   - **When** el sistema registra el estado entregado
   - **Then** el vendedor de punto fijo puede registrar el pago de mi tarifa y yo podré confirmar su recepción (RF-43)

### Edge Cases

- ¿Qué ocurre si el cliente no tiene o no encuentra su código (por ejemplo, sin conexión)? ¿Existe una vía de respaldo con intervención del administrador o un reporte (RF-60)?
- ¿Se valida que la ubicación del domiciliario esté cerca del punto B al confirmar la entrega, además del código?
- ¿Qué ocurre si el cliente dice no haber recibido el pedido pero el domiciliario tiene el código? (el cliente puede crear un reporte, RF-60)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario asignado confirmar la entrega únicamente cuando el domicilio esté en estado "en camino".
- **FR-002**: El sistema DEBE exigir al domiciliario el ingreso del código de confirmación del domicilio y rechazar la confirmación si el código no coincide.
- **FR-003**: El sistema DEBE, una vez validado el código, cambiar el estado del domicilio y de su pedido a "entregado", registrar el evento en la trazabilidad y notificar al vendedor de punto fijo y al cliente.
- **FR-004**: El sistema DEBE impedir que la entrega se confirme más de una vez.
- **FR-005**: El sistema DEBE habilitar el registro y la confirmación del pago de la tarifa del domicilio (RF-43) y las calificaciones (RF-01) a partir del estado "entregado".
- **FR-006**: El sistema DEBE limitar a 5 los intentos fallidos de código por domicilio y bloquear la confirmación durante 15 minutos al agotarlos.

### Key Entities

- **Domicilio**: Transición de esta funcionalidad: en camino → entregado. Incluye el código de confirmación, generado al asignarse (RF-19).
- **Pedido**: En un domicilio, pasa a entregado en la misma operación.
- **Código de confirmación**: Valor de 6 dígitos que solo ve el cliente (RF-25) y que el domiciliario ingresa; es la única confirmación de la entrega.
- **Evento de trazabilidad**: Registro del estado entregado, con el domiciliario como usuario que lo originó.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las entregas confirmadas quedan registradas con fecha, hora y validación del código en la trazabilidad.
- **SC-002**: El vendedor y el cliente reciben la notificación de entrega en menos de 1 minuto.
- **SC-003**: El 0% de las entregas puede confirmarse sin un código válido o dos veces.
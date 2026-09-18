# Feature Specification: Recogida del pedido y salida del domiciliario

**Created**: 2026-09-18
**Requerimiento funcional**: RF-23
**Historias de usuario relacionadas**: HU-46, HU-35

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Domiciliario confirma que recogió el pedido (Priority: P1)

Como domiciliario, quiero confirmar que recogí el pedido en el punto fijo, para iniciar el trayecto hacia el cliente.

**Why this priority**: Deja constancia de que el pedido cambió de manos y es el primer paso del traspaso entre vendedor y domiciliario, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un domicilio asignado, confirmando la recogida desde el domiciliario y verificando que el estado pasa a recogido y que el vendedor es notificado.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de recogida
   - **Given** tengo un domicilio asignado y estoy en el punto fijo
   - **When** confirmo que recogí el pedido
   - **Then** el sistema cambia el estado del domicilio a recogido y notifica al vendedor de punto fijo para que confirme la salida

2. **Scenario**: Confirmación fuera de secuencia
   - **Given** el domicilio no está en estado asignado
   - **When** intento confirmar la recogida
   - **Then** el sistema rechaza la operación e indica el estado actual del domicilio

---

### User Story 2 - Vendedor confirma que el domiciliario va en camino (Priority: P1)

Como vendedor de punto fijo, quiero confirmar que el domiciliario ya va en camino, para dejar constancia de que recibió el pedido y salió hacia el cliente.

**Why this priority**: Completa el traspaso con la conformidad del vendedor y habilita el seguimiento del cliente, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un domicilio en estado recogido, confirmando la salida desde el vendedor y verificando que el estado pasa a en camino y el cliente es notificado.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de salida
   - **Given** el domiciliario confirmó que recogió el pedido
   - **When** confirmo que ya va en camino
   - **Then** el sistema cambia el estado del domicilio a en camino, lo registra en la trazabilidad y notifica al cliente

2. **Scenario**: Confirmación antes de la recogida
   - **Given** el domiciliario aún no confirmó la recogida
   - **When** intento confirmar que va en camino
   - **Then** el sistema rechaza la operación e indica que primero el domiciliario debe confirmar la recogida

### Edge Cases

- **¿Qué sucede si el vendedor no confirma la salida del domiciliario en un tiempo razonable?** A los 10 minutos se le envía un recordatorio; a los 15 minutos el domicilio pasa automáticamente a "en camino" y se registra como confirmación automática, salvo que exista un reporte abierto sobre el domicilio.
- **¿Cómo se resuelve un desacuerdo, por ejemplo si el domiciliario dice haber recogido el pedido y el vendedor lo niega?** Cualquiera de las partes crea un reporte (RF-61) y el administrador puede cancelar el domicilio (RF-13); mientras tanto el domicilio no avanza.
- **¿Qué ocurre si el domiciliario confirma la recogida pero el pedido entregado no corresponde?** El domiciliario no debe confirmar la recogida: reporta el problema y el domicilio permanece en "asignado" hasta que se resuelva o se cancele.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario asignado confirmar la recogida del pedido únicamente cuando el domicilio esté en estado "asignado".
- **FR-002**: El sistema DEBE cambiar el estado a "recogido" y notificar al vendedor de punto fijo al confirmarse la recogida.
- **FR-003**: El sistema DEBE permitir al vendedor de punto fijo confirmar que el domiciliario va en camino únicamente cuando el domicilio esté en estado "recogido".
- **FR-004**: El sistema DEBE cambiar el estado a "en camino", registrar el evento en la trazabilidad y notificar al cliente al confirmarse la salida.

### Key Entities

- **Domicilio**: Transiciones de esta funcionalidad: asignado → recogido → en camino.
- **Evento de trazabilidad**: Registro con estado, fecha, hora y usuario que lo originó.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. El domiciliario confirma la recogida y, después, el vendedor de punto fijo confirma la salida.

**Datos que asigna el sistema**

- Estados "recogido" y "en camino", con fecha, hora y usuario que confirma cada uno.
- Confirmación automática a los 15 minutos si el vendedor no responde y no hay reporte abierto.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los cambios de estado de recogida y salida quedan registrados en la trazabilidad con fecha y hora.
- **SC-002**: El cliente recibe la notificación de "en camino" en menos de 1 minuto.
- **SC-003**: El sistema rechaza el 100% de las confirmaciones realizadas fuera de la secuencia definida.

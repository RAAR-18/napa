# Feature Specification: Gestión de ofertas de precio por el cliente

**Created**: 2026-09-18
**Requerimiento funcional**: RF-21
**Historias de usuario relacionadas**: HU-42, HU-43, HU-44

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente revisa las ofertas recibidas (Priority: P2)

Como cliente, quiero consultar las ofertas de precio de los domiciliarios sobre mi domicilio, para decidir con quién y a qué valor recibo mi pedido.

**Why this priority**: Sin esta pantalla el cliente no puede decidir sobre las ofertas; es el punto de entrada de la negociación del domicilio, por lo que se clasifica como P2.

**Independent Test**: Puede probarse generando una oferta de un domiciliario sobre un domicilio y verificando que el cliente la ve en su lista de ofertas.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de ofertas recibidas
   - **Given** mi domicilio tiene una o más ofertas pendientes
   - **When** ingreso a las ofertas de mi domicilio
   - **Then** el sistema muestra cada oferta con el domiciliario (nombre y calificación), el valor ofertado y su diferencia frente a la tarifa publicada

2. **Scenario**: Domicilio sin ofertas
   - **Given** mi domicilio no ha recibido ofertas
   - **When** ingreso a las ofertas
   - **Then** el sistema informa que no hay ofertas y que el domicilio sigue disponible

---

### User Story 2 - Cliente acepta la oferta de un domiciliario (Priority: P1)

Como cliente, quiero aceptar la oferta de un domiciliario, para asignarle mi domicilio al valor propuesto.

**Why this priority**: Aceptar una oferta es lo que asigna el domicilio cuando no se toma con la tarifa publicada; sin ello el domicilio con ofertas no avanza, por lo que se clasifica como P1.

**Independent Test**: Puede probarse aceptando una oferta y verificando que el domicilio queda asignado a ese domiciliario con el nuevo valor y que las demás ofertas quedan vencidas.

**Acceptance Scenarios**:

1. **Scenario**: Aceptación de una oferta
   - **Given** mi domicilio tiene una oferta pendiente
   - **When** la acepto
   - **Then** el sistema asigna el domicilio al domiciliario, actualiza la ganancia y el costo del domicilio al valor ofertado, marca las demás ofertas como vencidas y notifica al domiciliario, quien ve el mapa con la ruta

2. **Scenario**: Aceptación de una oferta de un domicilio ya asignado
   - **Given** otro domiciliario tomó el domicilio con la tarifa publicada antes de mi decisión
   - **When** intento aceptar una oferta
   - **Then** el sistema informa que el domicilio ya fue asignado y que la oferta venció

---

### User Story 3 - Cliente rechaza la oferta de un domiciliario (Priority: P2)

Como cliente, quiero rechazar la oferta de un domiciliario, para mantener mi domicilio disponible para otros domiciliarios.

**Why this priority**: Da al cliente control sobre el valor que está dispuesto a pagar, pero no bloquea el flujo principal, por lo que se clasifica como P2.

**Independent Test**: Puede probarse rechazando una oferta y verificando que el domicilio sigue disponible y que el domiciliario es notificado.

**Acceptance Scenarios**:

1. **Scenario**: Rechazo de una oferta
   - **Given** mi domicilio tiene una oferta pendiente
   - **When** la rechazo
   - **Then** el sistema marca la oferta como rechazada, mantiene el domicilio disponible y notifica al domiciliario

### Edge Cases

- **¿Qué sucede si el cliente acepta dos ofertas casi al mismo tiempo desde dos dispositivos?** Solo la primera solicitud procesada es válida (RNF08); la otra oferta queda vencida.
- **¿Cuánto tiempo permanece pendiente una oferta si el cliente no responde?** 10 minutos; después vence y se notifica al domiciliario.
- **¿Cómo se informa al cliente que el costo del domicilio cambió al aceptar una oferta distinta a la tarifa publicada?** Antes de confirmar, la pantalla muestra el nuevo costo del domicilio y el nuevo total; el cliente debe confirmar la aceptación viendo ese valor.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar las ofertas pendientes de su domicilio, mostrando domiciliario, calificación y valor ofertado.
- **FR-002**: El sistema DEBE permitir al cliente aceptar una oferta, asignando el domicilio al domiciliario que la propuso.
- **FR-003**: El sistema DEBE actualizar la ganancia del domiciliario y el costo del domicilio al valor de la oferta aceptada.
- **FR-004**: El sistema DEBE marcar como vencidas las demás ofertas pendientes al aceptarse una.
- **FR-005**: El sistema DEBE permitir al cliente rechazar una oferta, manteniendo el domicilio disponible y notificando al domiciliario.
- **FR-006**: El sistema DEBE permitir solo una oferta aceptada por domicilio, controlando decisiones simultáneas (RNF08).

### Key Entities

- **Oferta de precio**: Cambia de estado según la decisión del cliente: aceptada, rechazada o vencida.
- **Domicilio**: Pasa a "asignado" cuando el cliente acepta una oferta; su tarifa vigente es la ofertada.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. El cliente selecciona una oferta y la acepta o la rechaza.

**Datos que se muestran o filtran**

- Domiciliario (nombre y calificación), valor ofertado, diferencia frente a la tarifa publicada y tiempo restante.
- Al aceptar: nuevo costo del domicilio y nuevo total del pedido.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios con una oferta aceptada quedan asignados a un único domiciliario.
- **SC-002**: El domiciliario recibe la notificación de aceptación o rechazo en menos de 1 minuto.
- **SC-003**: El 0% de los domicilios tiene más de una oferta aceptada.

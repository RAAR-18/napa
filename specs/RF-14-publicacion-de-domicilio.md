# Feature Specification: Publicación de domicilio

**Created**: 2026-09-18
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-14
**Historias de usuario relacionadas**: HU-31

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Poner un pedido a disposición de los domiciliarios (Priority: P1)

Como vendedor de punto fijo, quiero publicar un domicilio para un pedido que ya acepté con modalidad de domicilio y cuyo pago ya confirmé, para que los domiciliarios puedan llevarlo al cliente sin que yo deje mi punto de venta.

**Why this priority**: Es el punto de partida de todo el ciclo del domicilio; sin publicación no existen domicilios que los domiciliarios puedan ver o aceptar, por lo que se clasifica como P1.

**Independent Test**: Puede probarse aceptando un pedido con modalidad de domicilio, confirmando su pago (RF-42), publicándolo y verificando que aparece en la lista de disponibles de un domiciliario con origen (punto A) en el punto fijo, destino (punto B) en la ubicación del cliente y la tarifa calculada.

**Acceptance Scenarios**:

1. **Scenario**: Publicación de un domicilio para un pedido aceptado y pagado
   - **Given** tengo un pedido con modalidad de domicilio en estado aceptado y con el pago confirmado
   - **When** publico el domicilio
   - **Then** el sistema crea el domicilio en estado disponible, con origen en mi punto fijo, destino en la ubicación indicada por el cliente y la tarifa calculada al hacer el pedido, y lo pone a disposición de los domiciliarios

2. **Scenario**: Intento de publicar un pedido que no es de domicilio o no está aceptado
   - **Given** tengo un pedido de recogida o de entrega directa, o un pedido de domicilio que aún está pendiente o fue rechazado
   - **When** intento publicar un domicilio para ese pedido
   - **Then** el sistema rechaza la operación e indica que solo pueden publicarse pedidos de domicilio ya aceptados y pagados

3. **Scenario**: Intento de publicar sin pago confirmado
   - **Given** tengo un pedido de domicilio aceptado cuyo pago aún no he confirmado
   - **When** intento publicar el domicilio
   - **Then** el sistema rechaza la operación e indica que primero debe confirmarse el pago del pedido (RF-42)

4. **Scenario**: Intento de publicar dos veces el mismo pedido
   - **Given** ya publiqué un domicilio activo para un pedido
   - **When** intento publicar otro domicilio para ese mismo pedido
   - **Then** el sistema rechaza la operación e informa que el pedido ya tiene un domicilio activo

5. **Scenario**: Un domicilio publicado no puede editarse
   - **Given** ya publiqué un domicilio
   - **When** reviso las acciones disponibles sobre él
   - **Then** el sistema no ofrece ninguna opción para modificar su origen, destino, receptor ni tarifa

### Edge Cases

- ¿Qué sucede si el pedido es cancelado o el domicilio es cancelado por el administrador después de publicado y antes de ser tomado?
- ¿Cómo se comporta el sistema si la ubicación de entrega indicada por el cliente es incompleta o queda fuera de la zona atendida?
- ¿Qué ocurre si ningún domiciliario toma el domicilio en un tiempo razonable? ¿Se notifica al vendedor y al cliente?
- ¿Qué pasa si el vendedor publica el domicilio antes de tener el pedido preparado para entregar?
- ¿Qué ocurre si el pedido de domicilio fue aceptado y el cliente nunca paga? (no hay plazo ni cancelación automática definidos: el domicilio simplemente no puede publicarse; punto pendiente de validar)
- ¿Puede publicarse el domicilio de un pedido marcado como reserva antes de la fecha y hora acordadas?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor de punto fijo publicar un domicilio únicamente para un pedido propio, con modalidad de domicilio, en estado aceptado y con el pago confirmado (RF-42).
- **FR-002**: El sistema DEBE definir como punto A la ubicación del punto fijo y como punto B la ubicación de entrega indicada por el cliente en el pedido, e incluir la persona que recibirá el pedido.
- **FR-003**: El sistema DEBE asignar al domicilio la tarifa publicada calculada al realizar el pedido, que no puede ser inferior a la tarifa mínima vigente (RF-44).
- **FR-004**: El sistema DEBE crear el domicilio en estado "disponible", ponerlo a disposición de los domiciliarios y notificarlos.
- **FR-005**: El sistema DEBE impedir que un pedido tenga más de un domicilio activo al mismo tiempo.
- **FR-006**: El sistema DEBE impedir la edición de un domicilio una vez publicado (punto A, punto B, receptor, pedido y tarifa).
- **FR-007**: El sistema DEBE impedir la publicación de un pedido de domicilio cuyo pago no esté confirmado, indicando el motivo.

### Key Entities

- **Domicilio**: Servicio de llevar un pedido del punto A al punto B; nace en estado "disponible" con su tarifa publicada. Estados: disponible, asignado, recogido, en camino, entregado, finalizado y cancelado. No es editable.
- **Pedido**: Debe tener modalidad de domicilio, estar en estado "aceptado" y tener el pago confirmado; puede contener varios ítems.
- **Vendedor de punto fijo**: Actor que publica el domicilio desde su punto de venta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios publicados aparecen en la lista de disponibles de los domiciliarios en menos de 5 segundos.
- **SC-002**: El sistema rechaza el 100% de los intentos de publicar un domicilio para un pedido que no sea de domicilio o no esté aceptado.
- **SC-003**: El 0% de los domicilios publicados permite modificar su origen, destino o tarifa.
- **SC-004**: El 0% de los domicilios se publica sin el pago del pedido confirmado.
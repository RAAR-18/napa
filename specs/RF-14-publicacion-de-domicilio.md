# Feature Specification: Publicación de domicilio

**Created**: 2026-09-18
**Requerimiento funcional**: RF-14
**Historias de usuario relacionadas**: HU-31

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Poner un pedido a disposición de los domiciliarios (Priority: P1)

Como vendedor de punto fijo, quiero publicar un domicilio para un pedido que ya acepté con modalidad de domicilio, para que los domiciliarios puedan llevarlo al cliente sin que yo deje mi punto de venta.

**Why this priority**: Es el punto de partida de todo el ciclo del domicilio; sin publicación no existen domicilios que los domiciliarios puedan ver, aceptar u ofertar, por lo que se clasifica como P1.

**Independent Test**: Puede probarse aceptando un pedido con modalidad de domicilio, publicándolo y verificando que aparece en la lista de disponibles de un domiciliario con origen (punto A) en el punto fijo, destino (punto B) en la ubicación del cliente y la tarifa calculada.

**Acceptance Scenarios**:

1. **Scenario**: Publicación de un domicilio para un pedido aceptado
   - **Given** tengo un pedido con modalidad de domicilio en estado aceptado
   - **When** publico el domicilio
   - **Then** el sistema crea el domicilio en estado disponible, con origen en mi punto fijo, destino en la ubicación indicada por el cliente y la tarifa calculada al hacer el pedido, y lo pone a disposición de los domiciliarios

2. **Scenario**: Intento de publicar un pedido que no es de domicilio o no está aceptado
   - **Given** tengo un pedido de reserva, o un pedido de domicilio que aún está pendiente o fue rechazado
   - **When** intento publicar un domicilio para ese pedido
   - **Then** el sistema rechaza la operación e indica que solo pueden publicarse pedidos de domicilio ya aceptados

3. **Scenario**: Intento de publicar dos veces el mismo pedido
   - **Given** ya publiqué un domicilio activo para un pedido
   - **When** intento publicar otro domicilio para ese mismo pedido
   - **Then** el sistema rechaza la operación e informa que el pedido ya tiene un domicilio activo

4. **Scenario**: Un domicilio publicado no puede editarse
   - **Given** ya publiqué un domicilio
   - **When** reviso las acciones disponibles sobre él
   - **Then** el sistema no ofrece ninguna opción para modificar su origen, destino, receptor ni tarifa

### Edge Cases

- ¿Qué sucede si el pedido es cancelado o el domicilio es cancelado por el administrador después de publicado y antes de ser tomado?
- ¿Cómo se comporta el sistema si la ubicación de entrega indicada por el cliente es incompleta o queda fuera de la zona atendida?
- ¿Qué ocurre si ningún domiciliario toma el domicilio en un tiempo razonable? ¿Se notifica al vendedor y al cliente?
- ¿Qué pasa si el vendedor publica el domicilio antes de tener el pedido preparado para entregar?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor de punto fijo publicar un domicilio únicamente para un pedido propio, con modalidad de domicilio y en estado aceptado.
- **FR-002**: El sistema DEBE definir como punto A la ubicación del punto fijo y como punto B la ubicación de entrega indicada por el cliente en el pedido, e incluir la persona que recibirá el pedido.
- **FR-003**: El sistema DEBE asignar al domicilio la tarifa publicada calculada al realizar el pedido, que no puede ser inferior a la tarifa mínima vigente (RF-44).
- **FR-004**: El sistema DEBE crear el domicilio en estado "disponible", ponerlo a disposición de los domiciliarios y notificarlos.
- **FR-005**: El sistema DEBE impedir que un pedido tenga más de un domicilio activo al mismo tiempo.
- **FR-006**: El sistema DEBE impedir la edición de un domicilio una vez publicado (punto A, punto B, receptor, pedido y tarifa).

### Key Entities

- **Domicilio**: Servicio de llevar un pedido del punto A al punto B; nace en estado "disponible" con su tarifa publicada. Estados: disponible, asignado, recogido, en camino, entregado, finalizado y cancelado. No es editable.
- **Pedido**: Debe tener modalidad de domicilio y estar en estado "aceptado"; puede contener varios ítems.
- **Vendedor de punto fijo**: Actor que publica el domicilio desde su punto de venta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios publicados aparecen en la lista de disponibles de los domiciliarios en menos de 5 segundos.
- **SC-002**: El sistema rechaza el 100% de los intentos de publicar un domicilio para un pedido que no sea de domicilio o no esté aceptado.
- **SC-003**: El 0% de los domicilios publicados permite modificar su origen, destino o tarifa.

# Feature Specification: Respuesta del vendedor a un pedido

**Created**: 2026-09-18
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-53
**Historias de usuario relacionadas**: HU-83, HU-84

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Aceptar un pedido (Priority: P1)

Como vendedor, quiero aceptar un pedido, para comprometerme a atenderlo según su modalidad de entrega.

**Why this priority**: Sin la aceptación del vendedor ningún pedido avanza hacia su entrega, retiro o domicilio, por lo que se clasifica como P1.

**Independent Test**: Puede probarse aceptando un pedido pendiente de cada modalidad y verificando que pasa a aceptado y habilita el siguiente paso propio de la modalidad.

**Acceptance Scenarios**:

1. **Scenario**: Aceptación de una entrega directa
   - **Given** soy vendedor ambulante y recibí un pedido de entrega directa
   - **When** lo acepto
   - **Then** el sistema cambia el pedido a aceptado, notifica al cliente y me habilita la ubicación de entrega (RF-35)

2. **Scenario**: Aceptación de una recogida en punto fijo
   - **Given** soy vendedor de punto fijo y recibí un pedido de recogida
   - **When** lo acepto
   - **Then** el sistema cambia el pedido a aceptado, notifica al cliente y me habilita a marcarlo como listo para recoger (RF-64)

3. **Scenario**: Aceptación de un pedido de domicilio
   - **Given** soy vendedor de punto fijo con una cuenta bancaria registrada (RF-63) y recibí un pedido de domicilio
   - **When** lo acepto
   - **Then** el sistema cambia el pedido a aceptado, notifica al cliente que debe transferir el total (productos más domicilio) y queda a la espera del pago; solo podré publicar el domicilio (RF-14) cuando confirme ese pago (RF-42)

4. **Scenario**: Aceptación de una reserva según mi criterio
   - **Given** recibí un pedido marcado como reserva con fecha y hora acordadas
   - **When** lo acepto porque considero que puedo cumplirlo
   - **Then** el sistema cambia el pedido a aceptado y notifica al cliente, sin validar automáticamente el inventario para esa fecha, y el pedido queda para atenderse en la fecha acordada con el siguiente paso propio de su modalidad

5. **Scenario**: Domicilio sin cuenta bancaria registrada
   - **Given** soy vendedor de punto fijo sin cuenta bancaria registrada
   - **When** intento aceptar un pedido de domicilio
   - **Then** el sistema me impide aceptarlo e indica que debo registrar una cuenta bancaria primero (RF-63)

6. **Scenario**: Inventario insuficiente al aceptar un pedido inmediato
   - **Given** el inventario actual ya no cubre un pedido que no es una reserva
   - **When** intento aceptarlo
   - **Then** el sistema me advierte de la insuficiencia y no lo acepta hasta que ajuste el inventario o lo rechace

---

### User Story 2 - Rechazar un pedido (Priority: P1)

Como vendedor, quiero rechazar un pedido, para indicar que no puedo atenderlo.

**Why this priority**: Permite al vendedor declinar un pedido que no puede cumplir (por ejemplo, un cliente muy lejano o una reserva que no podrá preparar), evitando falsas expectativas, por lo que se clasifica como P1.

**Independent Test**: Puede probarse rechazando un pedido pendiente y verificando que pasa a rechazado y el cliente es notificado.

**Acceptance Scenarios**:

1. **Scenario**: Rechazo de un pedido pendiente
   - **Given** tengo un pedido pendiente que no puedo atender
   - **When** lo rechazo
   - **Then** el sistema cambia el pedido a rechazado, libera las cantidades comprometidas y notifica al cliente

2. **Scenario**: Rechazo de una reserva según mi criterio
   - **Given** tengo una reserva pendiente que no creo poder cumplir en la fecha acordada
   - **When** la rechazo
   - **Then** el sistema cambia el pedido a rechazado y notifica al cliente

3. **Scenario**: Rechazo de un pedido ya aceptado
   - **Given** el pedido ya fue aceptado y está en curso
   - **When** intento rechazarlo
   - **Then** el sistema rechaza la operación e indica que el pedido ya no está pendiente

### Edge Cases

- ¿Qué sucede si el vendedor ambulante no responde a un pedido de entrega directa en un tiempo razonable? ¿Vence el pedido?
- ¿Qué sucede si el vendedor no responde a una reserva antes de la fecha acordada?
- ¿Puede el vendedor indicar un motivo al rechazar un pedido?
- ¿Qué ocurre si dos pedidos inmediatos pendientes compiten por la misma cantidad de un producto?
- ¿Qué ocurre si el vendedor acepta un pedido de domicilio y el cliente nunca paga? (no hay plazo ni cancelación automática; el domicilio simplemente no puede publicarse)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor aceptar o rechazar únicamente pedidos de su emprendimiento que estén en estado "pendiente".
- **FR-002**: El sistema DEBE cambiar el estado del pedido a "aceptado" o "rechazado" y notificar al cliente.
- **FR-003**: El sistema DEBE validar, al aceptar un pedido inmediato, que el inventario actual cubra el pedido, y descontarlo en ese mismo momento, de forma atómica con la aceptación; si no alcanza, NO DEBE aceptarlo. En los pedidos marcados como reserva NO DEBE validar automáticamente el inventario: el vendedor decide con su propio criterio.
- **FR-004**: El sistema DEBE habilitar, tras la aceptación, el siguiente paso propio de la modalidad: ubicación de entrega (entrega directa, inmediata o marcada como reserva), marcar como listo para recoger (recogida, inmediata o marcada como reserva), o esperar la confirmación del pago y luego publicar el domicilio (domicilio).
- **FR-005**: El sistema DEBE liberar las cantidades comprometidas cuando un pedido inmediato sea rechazado.
- **FR-006**: El sistema DEBE impedir que un vendedor de punto fijo sin cuenta bancaria registrada acepte un pedido de domicilio (RF-63).
- **FR-007**: El sistema DEBE notificar al cliente, al aceptarse un pedido de domicilio, que debe transferir el total para que el domicilio pueda publicarse.

### Key Entities

- **Pedido**: Transiciones de esta funcionalidad: pendiente → aceptado y pendiente → rechazado. Puede estar marcado como reserva.
- **Vendedor**: Ambulante o de punto fijo; decide sobre los pedidos recibidos con su propio criterio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede aceptar o rechazar un pedido en menos de 10 segundos.
- **SC-002**: El cliente recibe la respuesta del vendedor en menos de 1 minuto.
- **SC-003**: El 0% de los pedidos inmediatos se acepta sin cobertura de inventario.
- **SC-004**: El 0% de los pedidos de domicilio se acepta sin una cuenta bancaria registrada del vendedor.
# Feature Specification: Consulta del estado del pedido

**Created**: 2026-09-18
**Requerimiento funcional**: RF-48
**Historias de usuario relacionadas**: HU-78

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente consulta el avance de su pedido (Priority: P1)

Como cliente, quiero consultar el estado de mi pedido, para conocer el avance de mi compra.

**Why this priority**: Es una necesidad constante tras comprar y reduce el contacto innecesario con el vendedor, por lo que se clasifica como P1.

**Independent Test**: Puede probarse creando un pedido en cada modalidad, avanzando su estado desde el vendedor o el domiciliario y verificando que el cliente lo ve actualizado.

**Acceptance Scenarios**:

1. **Scenario**: Estado de una entrega directa
   - **Given** mi pedido es de entrega directa
   - **When** consulto su estado
   - **Then** el sistema muestra el estado actual: pendiente, aceptado, en camino, entregado o rechazado

2. **Scenario**: Estado de una recogida en punto fijo
   - **Given** mi pedido es de recogida en punto fijo
   - **When** consulto su estado
   - **Then** el sistema muestra pendiente, aceptado, listo para recoger, entregado o rechazado

3. **Scenario**: Estado de un domicilio
   - **Given** mi pedido es de domicilio
   - **When** consulto su estado
   - **Then** el sistema muestra el estado del pedido y, una vez publicado, el estado del domicilio con acceso a su trazabilidad (RF-26)

4. **Scenario**: Pedido marcado como reserva
   - **Given** mi pedido está marcado como reserva
   - **When** consulto su estado
   - **Then** el sistema muestra, además del estado según su modalidad, que es una reserva y la fecha y hora acordadas

5. **Scenario**: Pedido rechazado
   - **Given** el vendedor rechazó mi pedido
   - **When** consulto su estado
   - **Then** el sistema muestra que fue rechazado

### Edge Cases

- ¿Cómo se informa al cliente si un pedido queda en estado pendiente durante un tiempo prolongado?
- ¿Qué ocurre si el cliente intenta consultar el estado de un pedido que no le pertenece?
- ¿Cómo se distingue en pantalla un pedido inmediato de uno marcado como reserva próxima a vencer si el vendedor no responde a tiempo?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar el estado actualizado de cualquier pedido que le pertenezca.
- **FR-002**: El sistema DEBE mostrar estados coherentes con la modalidad del pedido (entrega directa, recogida en punto fijo o domicilio).
- **FR-003**: El sistema DEBE mostrar, junto al estado, la fecha y hora de la última actualización y, cuando el pedido esté marcado como reserva, la fecha y hora acordadas.
- **FR-004**: El sistema DEBE impedir que un cliente consulte el estado de pedidos ajenos.

### Key Entities

- **Pedido**: Estados posibles según la modalidad: pendiente, aceptado, rechazado, listo para recoger, en camino, entregado y cancelado; en un domicilio se apoya en los estados del domicilio. Atributo adicional: reserva (sí/no, fecha y hora acordadas).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente puede consultar el estado de su pedido en menos de 3 segundos.
- **SC-002**: El 100% de los cambios de estado son visibles para el cliente en menos de 1 minuto.
- **SC-003**: El 0% de los intentos de consultar pedidos ajenos es exitoso.
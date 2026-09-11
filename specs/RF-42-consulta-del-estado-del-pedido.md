# Feature Specification: Consulta del estado del pedido

**Created**: 2026-09-11
**Requerimiento funcional**: RF-42
**Historias de usuario relacionadas**: HU-65

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente consulta el avance de su pedido (Priority: P1)

Como cliente, quiero consultar el estado de mi pedido, para conocer el avance de mi compra.

**Why this priority**: Es una necesidad constante del cliente tras realizar una compra y reduce la incertidumbre y el contacto innecesario con el vendedor; se clasifica como P1 por su relación directa con la experiencia posterior a la compra.

**Independent Test**: Puede probarse creando un pedido, avanzando su estado desde el panel del vendedor o del domiciliario, y verificando que el cliente ve reflejado el estado actualizado en tiempo razonable.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa del estado actual del pedido
   - **Given** el cliente tiene un pedido registrado en la plataforma
   - **When** el cliente consulta el detalle de su pedido
   - **Then** el sistema muestra el estado actualizado del pedido (pendiente, confirmado, en preparación, en camino, entregado o cancelado)

2. **Scenario**: Actualización del estado reflejada al cliente
   - **Given** el vendedor o el domiciliario actualiza el estado de un pedido
   - **When** el cliente vuelve a consultar su pedido
   - **Then** el sistema muestra el nuevo estado sin necesidad de que el cliente realice acciones adicionales

### Edge Cases

- ¿Qué sucede si el cliente consulta el estado de un pedido cancelado o rechazado por el vendedor?
- ¿Cómo se informa al cliente si el pedido queda estancado en un estado por un tiempo prolongado?
- ¿Qué ocurre si el cliente intenta consultar el estado de un pedido que no le pertenece?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar el estado actualizado de cualquier pedido que le pertenezca.
- **FR-002**: El sistema DEBE reflejar los cambios de estado del pedido (confirmación, preparación, envío, entrega, cancelación) en la consulta del cliente.
- **FR-003**: El sistema DEBE impedir que un cliente consulte el estado de un pedido que no le pertenece.
- **FR-004**: El sistema DEBE mostrar, junto al estado, la fecha y hora de la última actualización.

### Key Entities

- **Pedido**: Entidad cuyo estado es consultado por el cliente; atributos clave: estado, fecha de última actualización.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente puede consultar el estado de su pedido en menos de 3 segundos.
- **SC-002**: El 100% de los cambios de estado de un pedido son visibles para el cliente en menos de 1 minuto.
- **SC-003**: El 0% de los intentos de un cliente por consultar pedidos ajenos resulta exitoso.

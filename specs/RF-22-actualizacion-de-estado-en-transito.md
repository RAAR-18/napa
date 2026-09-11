# Feature Specification: Actualización de estado en tránsito

**Created**: 2026-09-11
**Requerimiento funcional**: RF-22
**Historias de usuario relacionadas**: HU-41

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Informar el avance de la entrega (Priority: P2)

Como domiciliario, quiero actualizar el estado de un domicilio que acepté (aceptado, pedido recogido, en camino), para que el cliente y el vendedor sepan en qué va la entrega.

**Why this priority**: Mejora la visibilidad del proceso para cliente y vendedor (RF-19), pero el domicilio puede completarse y confirmarse (RF-23) aunque no se registren estos estados intermedios, por lo que se clasifica como P2.

**Independent Test**: Puede probarse de forma independiente aceptando un domicilio, marcando su avance (recogido, en camino) y verificando que el estado se actualiza en tiempo real para el cliente y el vendedor.

**Acceptance Scenarios**:

1. **Scenario**: Marcar el pedido como recogido
   - **Given** el domiciliario tiene un domicilio asignado
   - **When** marca el pedido como recogido
   - **Then** el sistema actualiza el estado visible para el cliente y el vendedor en tiempo real

2. **Scenario**: Marcar el domicilio como en camino
   - **Given** el domiciliario ya recogió el pedido
   - **When** marca el domicilio como en camino
   - **Then** el sistema actualiza el estado visible para el cliente y el vendedor en tiempo real

### Edge Cases

- ¿Qué sucede si el domiciliario intenta marcar "en camino" sin haber marcado antes "recogido"?
- ¿Cómo se maneja una actualización de estado si el cliente canceló el domicilio en el mismo instante?
- ¿Qué ocurre si el domiciliario pierde conexión al intentar actualizar el estado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario actualizar el estado de un domicilio asignado a través de una secuencia válida de estados (aceptado → recogido → en camino).
- **FR-002**: El sistema DEBE impedir saltos de estado que no sigan la secuencia válida (por ejemplo, marcar "en camino" sin haber marcado "recogido").
- **FR-003**: El sistema DEBE reflejar cada actualización de estado para el cliente y el vendedor en tiempo real.

### Key Entities

- **Domicilio**: Máquina de estados con transiciones válidas: aceptado, recogido, en camino, entregado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las actualizaciones de estado se reflejan para el cliente y el vendedor en menos de 30 segundos.
- **SC-002**: El sistema bloquea el 100% de los intentos de transición de estado inválida.

# Feature Specification: Visualización del código de confirmación

**Created**: 2026-09-11
**Requerimiento funcional**: RF-25
**Historias de usuario relacionadas**: HU-46

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Entregar el código al domiciliario para validar la entrega (Priority: P2)

Como cliente, quiero ver el código de confirmación, para proporcionarlo al domiciliario al recibir mi pedido.

**Why this priority**: Es un habilitador necesario para que el domiciliario pueda cerrar la entrega (RF-23), pero la funcionalidad de consulta en sí no bloquea otros flujos del sistema, por lo que se clasifica como P2.

**Independent Test**: Puede probarse de forma independiente generando un domicilio, y verificando que el cliente puede visualizar el código de confirmación asociado desde el detalle de su pedido con domicilio en curso.

**Acceptance Scenarios**:

1. **Scenario**: Visualización del código en un domicilio en curso
   - **Given** el cliente tiene un pedido con domicilio aceptado por un domiciliario
   - **When** consulta el detalle del pedido
   - **Then** el sistema muestra el código de confirmación asociado a ese domicilio

2. **Scenario**: Código no disponible antes de la aceptación
   - **Given** el cliente tiene un pedido cuyo domicilio aún no ha sido aceptado
   - **When** consulta el detalle del pedido
   - **Then** el sistema indica que el código de confirmación estará disponible una vez el domicilio sea aceptado

### Edge Cases

- ¿Qué sucede si el cliente pierde o olvida el código de confirmación al momento de la entrega?
- ¿Puede el código de confirmación ser regenerado en caso de sospecha de uso indebido?
- ¿Qué ocurre si el domicilio es cancelado después de que el código ya fue generado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE generar un código de confirmación único para cada domicilio.
- **FR-002**: El sistema DEBE permitir al cliente visualizar el código de confirmación de su domicilio desde el detalle de su pedido.
- **FR-003**: El sistema DEBE mostrar el código únicamente cuando el domicilio se encuentre en un estado en el que pueda ser entregado (aceptado, recogido o en camino).

### Key Entities

- **Código de confirmación**: Código único vinculado a un domicilio, visible para el cliente y validado por el domiciliario en RF-23.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios aceptados generan un código de confirmación visible para el cliente.
- **SC-002**: El cliente puede visualizar su código de confirmación en menos de 3 segundos desde el detalle de su pedido.

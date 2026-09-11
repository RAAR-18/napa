# Feature Specification: Consulta de información del domicilio por el domiciliario

**Created**: 2026-09-11
**Requerimiento funcional**: RF-20
**Historias de usuario relacionadas**: HU-39

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Obtener los datos necesarios para realizar la entrega (Priority: P1)

Como domiciliario, quiero consultar la información del domicilio, para conocer los datos necesarios para realizar la entrega.

**Why this priority**: Sin esta información el domiciliario no puede ejecutar la entrega correctamente; es parte del flujo core de ejecución del domicilio, por lo que se clasifica como P1.

**Independent Test**: Puede probarse de forma independiente aceptando un domicilio y verificando que el domiciliario puede consultar el detalle completo (pedido, dirección, notas) desde su vista de domicilios asignados.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del detalle de un domicilio asignado
   - **Given** el domiciliario tiene un domicilio asignado
   - **When** consulta su información
   - **Then** el sistema muestra los datos del pedido, la dirección de entrega y las notas registradas por el vendedor

### Edge Cases

- ¿Qué sucede si la dirección de entrega fue editada por el vendedor después de que el domiciliario aceptó el domicilio?
- ¿Cómo se muestra la información si el domicilio no tiene notas de entrega registradas?
- ¿Qué ocurre si el domiciliario intenta consultar un domicilio que no le ha sido asignado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario consultar el detalle completo de un domicilio que le haya sido asignado, incluyendo pedido, dirección y notas de entrega.
- **FR-002**: El sistema DEBE restringir la consulta de detalle a los domicilios asignados al domiciliario autenticado.

### Key Entities

- **Domicilio**: Contiene la dirección de entrega, notas y referencia al pedido asociado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El domiciliario puede acceder al detalle completo de un domicilio asignado en menos de 3 segundos.
- **SC-002**: El 100% de los domiciliarios ven únicamente la información de los domicilios que tienen asignados.

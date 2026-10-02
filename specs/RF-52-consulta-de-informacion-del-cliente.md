# Feature Specification: Consulta de información del cliente

**Created**: 2026-09-11
**Requerimiento funcional**: RF-52
**Historias de usuario relacionadas**: HU-82

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Vendedor consulta los datos del cliente de un pedido (Priority: P2)

Como vendedor, quiero consultar la información del cliente, para conocer los datos necesarios para gestionar su pedido.

**Why this priority**: Facilita la gestión y comunicación del vendedor con el cliente durante el procesamiento del pedido, pero el pedido puede confirmarse y prepararse sin que el vendedor consulte estos datos de forma explícita; se clasifica como P2.

**Independent Test**: Puede probarse desde el detalle de un pedido, accediendo a la información del cliente asociado y verificando que se muestran únicamente los datos necesarios para la gestión del pedido.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa de la información del cliente
   - **Given** el vendedor tiene un pedido recibido con un cliente asociado
   - **When** el vendedor accede a la información del cliente desde el detalle del pedido
   - **Then** el sistema muestra los datos del cliente necesarios para gestionar el pedido (nombre, contacto y dirección de entrega cuando aplique)

2. **Scenario**: Intento de consultar información de un cliente sin pedido asociado
   - **Given** no existe un pedido del cliente asociado al emprendimiento del vendedor
   - **When** el vendedor intenta consultar la información de ese cliente
   - **Then** el sistema deniega el acceso e informa que no existe relación entre el cliente y el emprendimiento

### Edge Cases

- ¿Qué sucede si el cliente ha actualizado su información personal después de haber realizado el pedido?
- ¿Cómo se maneja la consulta si el cliente eliminó su cuenta después de completar el pedido?
- ¿Qué datos del cliente se ocultan por privacidad si no son necesarios para gestionar el pedido?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor consultar la información del cliente asociado a un pedido de su emprendimiento.
- **FR-002**: El sistema DEBE limitar la información mostrada del cliente a los datos necesarios para gestionar el pedido (nombre, contacto, dirección de entrega cuando aplique).
- **FR-003**: El sistema DEBE impedir que el vendedor consulte información de un cliente con el que no tiene un pedido asociado.

### Key Entities

- **Cliente**: Usuario cuya información básica de contacto y entrega es consultada por el vendedor en el contexto de un pedido.
- **Pedido**: Relación que habilita la consulta de la información del cliente por parte del vendedor.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede consultar la información del cliente de un pedido en menos de 3 segundos.
- **SC-002**: El 100% de las consultas exitosas corresponden a clientes con al menos un pedido asociado al emprendimiento del vendedor.
- **SC-003**: El 0% de los intentos de consultar información de un cliente sin pedido asociado resulta exitoso.

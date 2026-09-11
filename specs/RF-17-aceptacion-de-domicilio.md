# Feature Specification: Aceptación de domicilio

**Created**: 2026-09-11
**Requerimiento funcional**: RF-17
**Historias de usuario relacionadas**: HU-35

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Comprometerse a realizar un domicilio (Priority: P1)

Como domiciliario, quiero aceptar un domicilio disponible viendo antes la información del pedido y la dirección de entrega, para comprometerme a realizarlo con la información necesaria.

**Why this priority**: Es el paso que activa el ciclo de entrega; sin aceptación no hay asignación ni entrega posible, por lo que se clasifica como P1.

**Independent Test**: Puede probarse de forma independiente seleccionando un domicilio disponible, revisando su información y confirmando la aceptación, verificando que queda asignado al domiciliario.

**Acceptance Scenarios**:

1. **Scenario**: Aceptación exitosa de un domicilio disponible
   - **Given** existe un domicilio disponible
   - **When** el domiciliario consulta la información del pedido y la dirección de entrega y confirma la aceptación
   - **Then** el sistema le asigna el domicilio y notifica la aceptación al cliente y al vendedor

2. **Scenario**: Intento de aceptar un domicilio ya tomado
   - **Given** un domicilio disponible es aceptado por otro domiciliario en simultáneo
   - **When** el domiciliario intenta aceptarlo también
   - **Then** el sistema le informa que el domicilio ya no está disponible y lo retira de su lista

### Edge Cases

- ¿Qué sucede si dos domiciliarios intentan aceptar el mismo domicilio al mismo tiempo?
- ¿Cómo se maneja la aceptación si el domicilio fue cancelado justo antes de confirmarse?
- ¿Qué ocurre si el domiciliario pierde conexión durante el proceso de aceptación?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE mostrar al domiciliario la información del pedido y la dirección de entrega antes de confirmar la aceptación de un domicilio.
- **FR-002**: El sistema DEBE asignar el domicilio de forma exclusiva al primer domiciliario que confirme su aceptación.
- **FR-003**: El sistema DEBE notificar la aceptación al cliente y al vendedor inmediatamente después de asignarse el domicilio.
- **FR-004**: El sistema DEBE evitar que un domicilio ya asignado pueda ser aceptado por otro domiciliario (control de concurrencia).

### Key Entities

- **Domicilio**: Cambia de estado "disponible" a "aceptado" al asociarse a un domiciliario.
- **Domiciliario**: Usuario que acepta y queda responsable de la entrega.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios aceptados quedan asignados a un único domiciliario, sin duplicidad.
- **SC-002**: El cliente y el vendedor reciben la notificación de aceptación en menos de 1 minuto tras confirmarse.

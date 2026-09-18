# Feature Specification: Consulta de información del domiciliario

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-27
**Historias de usuario relacionadas**: HU-34, HU-49

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente conoce quién realizará su entrega (Priority: P2)

Como cliente, quiero consultar la información del domiciliario, para conocer quién realizará mi entrega.

**Why this priority**: Aporta confianza y seguridad al cliente durante la espera, pero no es indispensable para que la entrega se ejecute, por lo que se clasifica como P2.

**Independent Test**: Puede probarse de forma independiente aceptando un domicilio con un domiciliario y verificando que el cliente puede consultar su nombre y datos básicos desde el detalle de su pedido.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de datos del domiciliario asignado
   - **Given** el cliente tiene un pedido con domicilio aceptado por un domiciliario
   - **When** consulta la información del domiciliario
   - **Then** el sistema muestra sus datos básicos (nombre, foto, calificación)

### User Story 2 - Vendedor de punto fijo conoce al encargado de la entrega (Priority: P2)

Como vendedor de punto fijo, quiero consultar la información del domiciliario, para conocer los datos del encargado de la entrega.

**Why this priority**: Facilita la coordinación entre vendedor y domiciliario sin ser bloqueante para el flujo de entrega, por lo que se clasifica como P2.

**Independent Test**: Puede probarse de forma independiente verificando que el vendedor puede consultar la información del domiciliario asignado a uno de sus pedidos despachados.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de datos del domiciliario desde el pedido del vendedor
   - **Given** el vendedor tiene un pedido con domicilio aceptado por un domiciliario
   - **When** consulta la información del domiciliario asignado
   - **Then** el sistema muestra sus datos básicos

### Edge Cases

- **¿Qué sucede si se consulta la información del domiciliario antes de que un domicilio haya sido aceptado?** Muestra "Aún no hay domiciliario asignado".
- **¿Cómo se maneja la consulta si el domiciliario cambia (por ejemplo, tras una reasignación)?** No hay reasignación dentro de una misma entrega: se muestra el domiciliario asignado hasta que el domicilio finalice o se cancele.
- **¿Qué datos sensibles del domiciliario deben quedar ocultos al cliente y al vendedor?** Solo se muestran nombre, foto y calificación; el teléfono y el documento permanecen ocultos y la comunicación se hace por el canal de contacto de la plataforma (RF-50).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar los datos básicos del domiciliario asignado a su pedido.
- **FR-002**: El sistema DEBE permitir al vendedor de punto fijo consultar los datos básicos del domiciliario asignado a sus pedidos.
- **FR-003**: El sistema DEBE restringir la visibilidad de información sensible del domiciliario (por ejemplo, datos de contacto directo, según las reglas de privacidad definidas).

### Key Entities

- **Domiciliario**: Usuario con datos públicos (nombre, foto, calificación) visibles para cliente y vendedor una vez asignado a un domicilio.

### Data Rules

**Datos que ingresa el usuario**: Ninguno.

**Datos que se muestran o filtran**

- Nombre, foto y calificación del domiciliario asignado.
- Se ocultan teléfono y documento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente y el vendedor pueden consultar la información del domiciliario asignado en menos de 3 segundos.
- **SC-002**: El 100% de los datos sensibles del domiciliario permanecen protegidos frente a la consulta pública.

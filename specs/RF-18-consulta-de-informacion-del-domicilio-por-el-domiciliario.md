# Feature Specification: Consulta de información del domicilio por el domiciliario

**Created**: 2026-09-18
**Requerimiento funcional**: RF-18
**Historias de usuario relacionadas**: HU-39

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conocer los datos para decidir y realizar la entrega (Priority: P1)

Como domiciliario, quiero consultar la información de un domicilio (de dónde sale, a dónde va, quién lo recibe y cuánto ganaría), para decidir si lo tomo y saber cómo realizar la entrega.

**Why this priority**: Sin esta información el domiciliario no puede evaluar el domicilio ni ejecutarlo; es el paso previo a aceptar, ofertar o archivar, por lo que se clasifica como P1.

**Independent Test**: Puede probarse abriendo desde la lista un domicilio disponible y verificando que se muestran el punto A, el punto B, la persona que recibe y la ganancia.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de la información de un domicilio disponible
   - **Given** el domiciliario ve un domicilio en su lista de disponibles
   - **When** presiona el botón para ver la información del domicilio
   - **Then** el sistema muestra el punto de origen (A), el punto de destino (B), quién lo recibirá, un resumen de los ítems del pedido y la ganancia

2. **Scenario**: Acciones disponibles desde la información
   - **Given** el domiciliario está viendo la información de un domicilio disponible
   - **When** revisa las acciones
   - **Then** el sistema le permite aceptarlo, ofertar otro precio o archivarlo

3. **Scenario**: Consulta de un domicilio que ya no está disponible
   - **Given** el domicilio fue asignado a otro domiciliario o cancelado
   - **When** el domiciliario intenta ver su información
   - **Then** el sistema informa que el domicilio ya no está disponible

4. **Scenario**: Consulta de un domicilio propio en curso
   - **Given** el domiciliario tiene un domicilio asignado
   - **When** abre su información
   - **Then** el sistema muestra los mismos datos y le da acceso a la ruta en el mapa (RF-22)

### Edge Cases

- ¿Debe restringirse la dirección exacta y el nombre de quien recibe hasta que el domicilio sea asignado, por privacidad (RNF04)?
- ¿Cómo se muestra el destino si la ubicación del cliente no puede resolverse en el mapa?
- ¿Qué ocurre si el domicilio es cancelado mientras el domiciliario revisa su información?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE mostrar al domiciliario, para un domicilio, el punto de origen (A), el punto de destino (B), la persona que lo recibirá y la ganancia.
- **FR-002**: El sistema DEBE mostrar un resumen de los ítems del pedido (cantidad de ítems), sin exponer datos de pago distintos al monto a cobrar.
- **FR-003**: El sistema DEBE permitir, desde la información de un domicilio disponible, aceptarlo (RF-19), ofertar otro precio (RF-20) o archivarlo (RF-17).
- **FR-004**: El sistema DEBE restringir los datos personales del receptor a lo necesario para la entrega, según las reglas de privacidad definidas (RNF04).
- **FR-005**: El sistema DEBE informar cuando el domicilio consultado ya no esté disponible.

### Key Entities

- **Domicilio**: Expone punto A, punto B, receptor, ganancia y resumen del pedido.
- **Punto A / Punto B**: Ubicaciones de origen (punto fijo del vendedor) y de destino (ubicación indicada por el cliente).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La información de un domicilio se muestra al domiciliario en menos de 3 segundos.
- **SC-002**: El 100% de los domicilios consultados muestran origen, destino, receptor y ganancia.

# Feature Specification: Consulta de ubicación del punto de recogida

**Created**: 2026-09-18
**Requerimiento funcional**: RF-64
**Historias de usuario relacionadas**: HU-98

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Saber a dónde ir a retirar mi reserva (Priority: P2)

Como cliente, quiero ver la ubicación del punto fijo donde recogeré mi reserva, para saber a dónde ir a retirarla.

**Why this priority**: En una reserva con retiro es el cliente quien se desplaza al punto fijo, pero puede conocer su ubicación desde el emprendimiento, por lo que se clasifica como P2.

**Independent Test**: Puede probarse con una reserva aceptada y verificando que el cliente ve la dirección del punto fijo, su horario y un mapa.

**Acceptance Scenarios**:

1. **Scenario**: Ubicación del punto de recogida
   - **Given** tengo una reserva aceptada en un punto fijo
   - **When** abro la ubicación del punto de recogida
   - **Then** el sistema muestra la dirección, el horario de atención y un mapa del punto fijo

2. **Scenario**: Reserva aún no aceptada
   - **Given** mi reserva está pendiente de respuesta
   - **When** intento ver la ubicación de recogida
   - **Then** el sistema indica que la reserva debe ser aceptada antes de retirarla

### Edge Cases

- **¿Qué ocurre si el vendedor de punto fijo cambia su ubicación o su horario después de aceptar la reserva?** La reserva conserva la ubicación y el horario vigentes al aceptarla; si el vendedor los cambia, se notifica a los clientes con reservas aceptadas.
- **¿Cómo se muestra la ubicación si el punto fijo está temporalmente cerrado?** Se muestra el estado "cerrado" con una alerta al cliente; el vendedor debe rechazar o reprogramar por contacto directo las reservas afectadas.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar la ubicación, la dirección y el horario del punto fijo asociado a una reserva aceptada.
- **FR-002**: El sistema DEBE mostrar la ubicación en un mapa con la dirección en texto como respaldo.

### Key Entities

- **Punto fijo**: Ubicación y horario de atención del emprendimiento del vendedor de punto fijo.
- **Reserva**: Pedido con modalidad de reserva para el día siguiente, con retiro en el punto fijo.

### Data Rules

**Datos que ingresa el usuario**: Ninguno.

**Datos que se muestran o filtran**

- Dirección, horario de atención y mapa del punto fijo asociado a la reserva aceptada.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La ubicación del punto de recogida se muestra en menos de 3 segundos.
- **SC-002**: El 100% de las reservas aceptadas en un punto fijo muestran su ubicación y horario.

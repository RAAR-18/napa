# Feature Specification: Consulta de emprendimiento

**Created**: 2026-09-11
**Actualizado**: 2026-09-25
**Requerimiento funcional**: RF-32
**Historias de usuario relacionadas**: HU-57, HU-96

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver el detalle de un emprendimiento (Priority: P1)

Como cliente, quiero consultar un emprendimiento, para conocer su información y oferta antes de decidir comprar.

**Why this priority**: Es el paso intermedio obligatorio entre el listado de emprendimientos y la realización de un pedido; sin él el cliente no puede evaluar al vendedor antes de comprar.

**Independent Test**: Puede probarse de forma independiente seleccionando un emprendimiento del listado y verificando que se muestra su información completa, sin depender de que existan productos publicados.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa del detalle de un emprendimiento
   - **Given** selecciono un emprendimiento del listado
   - **When** ingreso a su detalle
   - **Then** el sistema muestra su información completa (nombre, categoría, tipo de vendedor, ubicación, horario, descripción y modalidades de entrega disponibles)

2. **Scenario**: Ubicación de un punto fijo, para retirar una recogida
   - **Given** consulto el emprendimiento de un vendedor de punto fijo
   - **When** reviso su ubicación
   - **Then** el sistema muestra la dirección, el horario de atención y un mapa del punto fijo, la misma información que usaré para retirar cualquier pedido de recogida que le haga

3. **Scenario**: Consulta de un emprendimiento ya eliminado
   - **Given** un emprendimiento fue eliminado por el vendedor
   - **When** intento consultar su detalle desde un enlace previamente guardado
   - **Then** el sistema informa que el emprendimiento ya no está disponible

### Edge Cases

- ¿Qué sucede si el emprendimiento está fuera de su horario de atención al momento de la consulta?
- ¿Cómo se muestra un emprendimiento sin descripción registrada?
- ¿Qué ocurre si la ubicación del emprendimiento no puede resolverse en el mapa?
- ¿Qué ocurre si el vendedor cambia su ubicación u horario mientras el cliente tiene un pedido de recogida aceptado? (se recomienda: el detalle del emprendimiento siempre refleja la ubicación vigente)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar el detalle de un emprendimiento específico desde el listado.
- **FR-002**: El sistema DEBE mostrar en el detalle el nombre, categoría, tipo de vendedor, ubicación, horario, descripción y modalidades de entrega disponibles del emprendimiento.
- **FR-003**: El sistema DEBE informar al cliente cuando el emprendimiento consultado ya no esté disponible.
- **FR-004**: El sistema DEBE mostrar, cuando el emprendimiento es de punto fijo, la dirección, el horario de atención y un mapa de su ubicación, como referencia tanto para decidir la compra como para retirar un pedido de recogida.

### Key Entities

- **Emprendimiento**: Entidad consultada; expone su información completa en la vista de detalle, incluyendo ubicación, dirección y horario cuando es de punto fijo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El detalle de un emprendimiento se muestra en menos de 3 segundos tras la selección.
- **SC-002**: El 100% de los emprendimientos activos exponen su información completa al ser consultados.
- **SC-003**: El 100% de los emprendimientos de punto fijo muestran dirección, horario y mapa.
# Feature Specification: Consulta de emprendimiento

**Created**: 2026-09-11
**Requerimiento funcional**: RF-30
**Historias de usuario relacionadas**: HU-51

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver el detalle de un emprendimiento (Priority: P1)

Como cliente, quiero consultar un emprendimiento, para conocer su información y oferta antes de decidir comprar.

**Why this priority**: Es el paso intermedio obligatorio entre el listado de emprendimientos y la realización de un pedido; sin él el cliente no puede evaluar al vendedor antes de comprar.

**Independent Test**: Puede probarse de forma independiente seleccionando un emprendimiento del listado y verificando que se muestra su información completa, sin depender de que existan productos publicados.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa del detalle de un emprendimiento
   - **Given** selecciono un emprendimiento del listado
   - **When** ingreso a su detalle
   - **Then** el sistema muestra su información completa (nombre, categoría, ubicación, horario y descripción)

2. **Scenario**: Consulta de un emprendimiento ya eliminado
   - **Given** un emprendimiento fue eliminado por el vendedor
   - **When** intento consultar su detalle desde un enlace previamente guardado
   - **Then** el sistema informa que el emprendimiento ya no está disponible

### Edge Cases

- ¿Qué sucede si el emprendimiento está fuera de su horario de atención al momento de la consulta?
- ¿Cómo se muestra un emprendimiento sin descripción registrada?
- ¿Qué ocurre si la ubicación del emprendimiento no puede resolverse en el mapa?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar el detalle de un emprendimiento específico desde el listado.
- **FR-002**: El sistema DEBE mostrar en el detalle el nombre, categoría, ubicación, horario y descripción del emprendimiento.
- **FR-003**: El sistema DEBE informar al cliente cuando el emprendimiento consultado ya no esté disponible.

### Key Entities

- **Emprendimiento**: Entidad consultada; expone su información completa en la vista de detalle.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El detalle de un emprendimiento se muestra en menos de 3 segundos tras la selección.
- **SC-002**: El 100% de los emprendimientos activos exponen su información completa al ser consultados.

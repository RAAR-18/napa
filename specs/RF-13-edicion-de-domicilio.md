# Feature Specification: Edición de domicilio

**Created**: 2026-09-11
**Requerimiento funcional**: RF-13
**Historias de usuario relacionadas**: HU-31

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Corregir datos de un domicilio antes de ser aceptado (Priority: P2)

Como vendedor, quiero editar la información de un domicilio que aún no ha sido aceptado por un domiciliario, para corregir errores en la dirección o en las notas de entrega antes de que alguien lo tome.

**Why this priority**: Es una función de corrección posterior a la publicación (RF-21); no bloquea el flujo principal de creación y asignación de domicilios, pero evita errores operativos, por lo que se prioriza como P2.

**Independent Test**: Puede probarse de forma independiente publicando un domicilio, editando uno de sus campos y verificando que el cambio se refleja para los domiciliarios sin que el domicilio haya sido aceptado.

**Acceptance Scenarios**:

1. **Scenario**: Edición exitosa de un domicilio pendiente
   - **Given** el vendedor registró un domicilio que aún no ha sido aceptado por ningún domiciliario
   - **When** modifica la dirección de entrega o las notas del domicilio
   - **Then** el sistema guarda los cambios y los refleja en la vista de domicilios disponibles para los domiciliarios

2. **Scenario**: Intento de edición de un domicilio ya aceptado
   - **Given** el domicilio ya fue aceptado por un domiciliario
   - **When** el vendedor intenta editar sus datos
   - **Then** el sistema impide la edición y muestra un mensaje indicando que el domicilio ya no puede modificarse

### Edge Cases

- ¿Qué sucede si el vendedor intenta editar un domicilio que fue cancelado?
- ¿Cómo se notifica a los domiciliarios que ya visualizaron el domicilio original cuando este es editado?
- ¿Qué pasa si el vendedor deja campos obligatorios vacíos durante la edición?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor editar la dirección de entrega y las notas de un domicilio, únicamente mientras este no haya sido aceptado por un domiciliario.
- **FR-002**: El sistema DEBE impedir la edición de un domicilio que ya fue aceptado, cancelado o entregado.
- **FR-003**: El sistema DEBE reflejar de inmediato los cambios del domicilio editado en la lista de domicilios disponibles para los domiciliarios.

### Key Entities

- **Domicilio**: Servicio de entrega con dirección, notas de entrega y estado; solo editable en estado "disponible" (no aceptado).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los cambios realizados sobre un domicilio no aceptado se reflejan en menos de 3 segundos para los domiciliarios.
- **SC-002**: El sistema bloquea el 100% de los intentos de edición sobre domicilios ya aceptados o cerrados.

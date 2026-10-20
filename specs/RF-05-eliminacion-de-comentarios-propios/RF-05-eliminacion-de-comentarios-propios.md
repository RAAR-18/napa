# Feature Specification: Eliminación de comentarios propios

**Created**: 2026-09-11
**Requerimiento funcional**: RF-05
**Historias de usuario relacionadas**: HU-20

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Usuario retira un comentario propio (Priority: P2)

Como usuario, quiero eliminar mis comentarios, para retirar contenido que ya no deseo mantener publicado.

**Why this priority**: Da control al usuario sobre su propio contenido, importante para la confianza en la plataforma, aunque no es crítico para el flujo transaccional.

**Independent Test**: Puede probarse publicando un comentario, eliminándolo y verificando que deje de ser visible para otros usuarios.

**Acceptance Scenarios**:

1. **Scenario**: Eliminación exitosa de un comentario propio
   - **Given** publiqué un comentario sobre una entidad
   - **When** confirmo su eliminación
   - **Then** el sistema retira el comentario de la vista pública de esa entidad

2. **Scenario**: Intento de eliminar un comentario ajeno
   - **Given** existe un comentario publicado por otro usuario
   - **When** intento eliminarlo
   - **Then** el sistema rechaza la operación

### Edge Cases

- ¿Qué ocurre con el promedio de calificación de una entidad cuando se elimina un comentario que incluía una calificación?
- ¿El sistema conserva el comentario eliminado para fines de auditoría aunque ya no sea visible públicamente?
- ¿Qué pasa si el usuario intenta eliminar un comentario que ya fue eliminado previamente por un administrador?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir a un usuario eliminar únicamente los comentarios que él mismo publicó.
- **FR-002**: El sistema MUST recalcular el promedio de calificación de la entidad afectada tras la eliminación de un comentario.
- **FR-003**: El sistema MUST registrar la eliminación para fines de auditoría, aunque el comentario deje de mostrarse públicamente.

### Key Entities

- **Comentario**: Contenido textual y calificación eliminable únicamente por su autor original.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los intentos de eliminación de comentarios ajenos son rechazados por el sistema.
- **SC-002**: Un comentario eliminado deja de ser visible públicamente en menos de 5 segundos.

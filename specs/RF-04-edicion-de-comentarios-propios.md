# Feature Specification: Edición de comentarios propios

**Created**: 2026-09-11
**Requerimiento funcional**: RF-04
**Historias de usuario relacionadas**: HU-20

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Usuario corrige un comentario publicado (Priority: P2)

Como usuario, quiero editar los comentarios que he publicado, para corregir o actualizar la información que compartí.

**Why this priority**: Mejora la calidad del contenido y la experiencia del usuario, pero no es un flujo crítico para la operación transaccional de la plataforma.

**Independent Test**: Puede probarse publicando un comentario, editándolo y verificando que el cambio se refleje inmediatamente para otros usuarios.

**Acceptance Scenarios**:

1. **Scenario**: Edición exitosa de un comentario propio
   - **Given** publiqué un comentario sobre una entidad
   - **When** modifico el texto o la calificación del comentario
   - **Then** el sistema guarda los cambios y los muestra actualizados a otros usuarios

2. **Scenario**: Intento de editar un comentario ajeno
   - **Given** existe un comentario publicado por otro usuario
   - **When** intento editarlo
   - **Then** el sistema rechaza la operación

### Edge Cases

- ¿Qué ocurre si el usuario intenta editar un comentario que ya fue eliminado por un administrador?
- ¿El sistema conserva un historial de ediciones o solo el estado más reciente?
- ¿Qué sucede si la edición deja el comentario vacío?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir a un usuario editar únicamente los comentarios que él mismo publicó.
- **FR-002**: El sistema MUST validar que el contenido editado cumpla las reglas mínimas de la plataforma (no vacío, dentro del límite de caracteres).
- **FR-003**: El sistema MUST reflejar de inmediato los cambios del comentario editado para el resto de los usuarios.

### Key Entities

- **Comentario**: Contenido textual y calificación editable únicamente por su autor original.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los intentos de edición de comentarios ajenos son rechazados por el sistema.
- **SC-002**: Los cambios de un comentario editado se reflejan a otros usuarios en menos de 5 segundos.

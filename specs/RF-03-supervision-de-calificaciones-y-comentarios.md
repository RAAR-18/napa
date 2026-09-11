# Feature Specification: Supervisión de calificaciones y comentarios

**Created**: 2026-09-11
**Requerimiento funcional**: RF-03
**Historias de usuario relacionadas**: HU-18

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrador supervisa comentarios publicados (Priority: P2)

Como administrador, quiero consultar las calificaciones y comentarios publicados en la plataforma, para supervisar la información publicada y detectar contenido problemático.

**Why this priority**: Es necesaria para mantener la calidad del contenido, pero depende de que ya existan calificaciones y comentarios generados por los usuarios (RF-01).

**Independent Test**: Puede probarse ingresando al panel de supervisión de comentarios como administrador y verificando que se listen todos los comentarios recientes con su autor y entidad calificada.

**Acceptance Scenarios**:

1. **Scenario**: Consulta general de comentarios recientes
   - **Given** existen comentarios registrados en la plataforma
   - **When** el administrador ingresa a la sección de supervisión de comentarios
   - **Then** el sistema muestra el listado de comentarios con autor, entidad calificada y fecha

2. **Scenario**: Filtrado de comentarios por entidad
   - **Given** existen comentarios asociados a distintas entidades
   - **When** el administrador filtra por un emprendimiento, producto, domiciliario o cliente específico
   - **Then** el sistema muestra únicamente los comentarios de esa entidad

### Edge Cases

- ¿Qué ocurre si un comentario fue eliminado por su autor antes de ser revisado por el administrador?
- ¿Cómo se identifican comentarios con lenguaje potencialmente ofensivo dentro del listado?
- ¿Qué sucede si el volumen de comentarios es muy alto y se requiere paginación?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al administrador consultar el listado completo de calificaciones y comentarios publicados en la plataforma.
- **FR-002**: El sistema MUST permitir al administrador filtrar comentarios por entidad calificada (emprendimiento, producto, domiciliario o cliente).
- **FR-003**: El sistema MUST mostrar, para cada comentario, el autor, la entidad calificada, la calificación asignada y la fecha de publicación.

### Key Entities

- **Comentario**: Contenido textual y calificación asociados a un autor y a una entidad, sujeto a supervisión administrativa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador puede localizar cualquier comentario publicado en menos de 30 segundos usando los filtros disponibles.
- **SC-002**: El 100% de los comentarios activos en la plataforma son visibles desde el panel de supervisión.

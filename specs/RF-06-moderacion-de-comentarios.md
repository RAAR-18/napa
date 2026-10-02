# Feature Specification: Moderación de comentarios

**Created**: 2026-09-11
**Requerimiento funcional**: RF-06
**Historias de usuario relacionadas**: HU-22, HU-23

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrador elimina un comentario que incumple las reglas (Priority: P1)

Como administrador, quiero eliminar comentarios, para retirar contenido que incumpla las reglas de la plataforma.

**Why this priority**: La moderación protege la reputación de la plataforma y a sus usuarios frente a contenido abusivo; es una función de control necesaria desde el lanzamiento.

**Independent Test**: Puede probarse identificando un comentario que incumple las reglas, eliminándolo desde el panel de administración y verificando que deje de mostrarse públicamente.

**Acceptance Scenarios**:

1. **Scenario**: Eliminación de un comentario reportado
   - **Given** identifico un comentario que incumple las reglas de la plataforma
   - **When** confirmo su eliminación desde el panel administrativo
   - **Then** el sistema retira el comentario de la vista pública y recalcula el promedio de calificación de la entidad afectada

---

### User Story 2 - Administrador edita un comentario que requiere corrección (Priority: P2)

Como administrador, quiero editar comentarios, para gestionar contenido que requiera modificaciones sin necesidad de eliminarlo por completo.

**Why this priority**: Ofrece una alternativa menos drástica que la eliminación para casos que solo requieren ajustes menores.

**Independent Test**: Puede probarse editando el texto de un comentario existente desde el panel administrativo y verificando que el cambio se refleje públicamente.

**Acceptance Scenarios**:

1. **Scenario**: Edición administrativa de un comentario
   - **Given** identifico un comentario que requiere modificación
   - **When** edito su contenido desde el panel administrativo
   - **Then** el sistema guarda el cambio y lo muestra actualizado a los usuarios

### Edge Cases

- ¿Qué ocurre si el administrador elimina un comentario cuyo autor ya lo había editado recientemente?
- ¿El sistema notifica al autor original cuando su comentario es editado o eliminado por un administrador?
- ¿Cómo se distingue, en el historial, entre una eliminación hecha por el autor y una hecha por moderación?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al administrador eliminar cualquier comentario publicado en la plataforma.
- **FR-002**: El sistema MUST permitir al administrador editar el contenido de cualquier comentario publicado.
- **FR-003**: El sistema MUST recalcular el promedio de calificación de la entidad afectada tras una eliminación o edición administrativa.
- **FR-004**: El sistema MUST registrar que una acción de moderación (edición o eliminación) fue realizada por un administrador, distinguiéndola de las acciones del autor original.
- **FR-005**: El sistema MUST notificar al autor del comentario cuando este sea editado o eliminado por moderación.

### Key Entities

- **Comentario**: Contenido sujeto a moderación administrativa además de la edición/eliminación por su propio autor.
- **Registro de moderación**: Evidencia de la acción administrativa realizada sobre un comentario (quién, cuándo, qué acción).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las acciones de moderación quedan registradas con administrador responsable, fecha y tipo de acción.
- **SC-002**: Un comentario moderado (editado o eliminado) se refleja para los usuarios en menos de 5 segundos.
- **SC-003**: El 100% de los autores reciben notificación cuando su comentario es moderado.

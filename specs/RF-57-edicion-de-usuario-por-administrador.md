# Feature Specification: Edición de usuario por administrador

**Created**: 2026-09-11
**Requerimiento funcional**: RF-57
**Historias de usuario relacionadas**: HU-85

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Corregir la información de un usuario (Priority: P3)

Como administrador, quiero editar un usuario, para actualizar o corregir su información.

**Why this priority**: Es una funcionalidad de soporte para resolver casos excepcionales (datos erróneos, disputas); no es parte del flujo diario de uso de la plataforma.

**Independent Test**: Puede probarse corrigiendo el teléfono o rol de un usuario existente desde el panel de administración y verificando que el cambio se guarda.

**Acceptance Scenarios**:

1. **Scenario**: Edición exitosa de un usuario
   - **Given** el administrador consulta la información de un usuario
   - **When** modifica uno o varios de sus datos
   - **Then** el sistema guarda los cambios y los refleja en el perfil del usuario

---

### Edge Cases

- ¿Puede el administrador cambiar el rol de un usuario (ej. de cliente a vendedor)? ¿Qué validaciones aplican en ese caso?
- ¿Qué ocurre si el administrador intenta asignar un correo o teléfono ya utilizado por otra cuenta?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al administrador editar la información de un usuario registrado.
- **FR-002**: El sistema MUST validar que los datos editados (correo, teléfono) no dupliquen los de otra cuenta existente.
- **FR-003**: El sistema MUST reflejar los cambios en el perfil del usuario de forma inmediata.

### Key Entities *(include if feature involves data)*

- **Usuario**: Ver definición en RF-56.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los cambios realizados por el administrador a un usuario se reflejan en el perfil en menos de 5 segundos.
- **SC-002**: El sistema rechaza el 100% de los intentos de asignar datos de contacto duplicados.

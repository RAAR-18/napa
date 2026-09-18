# Feature Specification: Eliminación de usuario por administrador

**Created**: 2026-09-11
**Requerimiento funcional**: RF-68
**Historias de usuario relacionadas**: HU-101

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Retirar una cuenta de la plataforma (Priority: P3)

Como administrador, quiero eliminar un usuario, para gestionar las cuentas que ya no deben permanecer en la plataforma.

**Why this priority**: Es una acción excepcional de moderación (fraude, incumplimiento de reglas), usada con baja frecuencia frente a las operaciones diarias de la plataforma.

**Independent Test**: Puede probarse eliminando una cuenta de prueba sin pedidos ni domicilios activos y verificando que deja de poder iniciar sesión.

**Acceptance Scenarios**:

1. **Scenario**: Eliminación exitosa de un usuario
   - **Given** el administrador identifica un usuario que debe ser retirado de la plataforma
   - **When** confirma la eliminación de la cuenta
   - **Then** el sistema deshabilita la cuenta y le impide iniciar sesión nuevamente

2. **Scenario**: Intento de eliminar un usuario con actividad activa
   - **Given** el usuario tiene pedidos o domicilios en curso
   - **When** el administrador intenta eliminar su cuenta
   - **Then** el sistema advierte sobre la actividad pendiente antes de confirmar la eliminación

---

### Edge Cases

- ¿Qué ocurre con los emprendimientos, productos o historial del usuario eliminado?
- ¿La eliminación es reversible dentro de algún periodo de gracia?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al administrador eliminar la cuenta de un usuario.
- **FR-002**: El sistema MUST advertir al administrador cuando el usuario a eliminar tenga pedidos o domicilios en curso.
- **FR-003**: El sistema MUST impedir el inicio de sesión de una cuenta eliminada.
- **FR-004**: El sistema MUST solicitar confirmación explícita antes de eliminar la cuenta de un usuario.

### Key Entities *(include if feature involves data)*

- **Usuario**: Ver definición en RF-66.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una cuenta eliminada por el administrador no puede volver a iniciar sesión de forma inmediata.
- **SC-002**: El 100% de las eliminaciones de usuarios con actividad en curso muestran una advertencia previa.

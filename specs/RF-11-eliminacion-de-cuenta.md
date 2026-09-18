# Feature Specification: Eliminación de cuenta

**Created**: 2026-09-11
**Requerimiento funcional**: RF-11
**Historias de usuario relacionadas**: HU-28

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Usuario elimina su cuenta (Priority: P2)

Como usuario registrado, quiero eliminar mi cuenta, para dejar de utilizar la plataforma.

**Why this priority**: Es un derecho del usuario sobre sus datos, pero de baja frecuencia de uso comparado con los flujos operativos diarios.

**Independent Test**: Puede probarse solicitando la eliminación de una cuenta sin operaciones pendientes y verificando que ya no sea posible iniciar sesión con ella.

**Acceptance Scenarios**:

1. **Scenario**: Eliminación exitosa de cuenta sin operaciones pendientes
   - **Given** tengo una cuenta activa sin pedidos, emprendimientos o domicilios pendientes
   - **When** confirmo la eliminación de mi cuenta
   - **Then** el sistema desactiva la cuenta y ya no permite iniciar sesión con ella

2. **Scenario**: Intento de eliminación con operaciones pendientes
   - **Given** tengo una cuenta con pedidos o domicilios pendientes
   - **When** intento eliminar mi cuenta
   - **Then** el sistema rechaza la eliminación e indica las operaciones pendientes que deben resolverse antes

### Edge Cases

- **¿Qué ocurre con los emprendimientos, productos o historial de pedidos asociados a una cuenta eliminada?** Con pedidos, reservas o domicilios pendientes no se permite eliminar; al eliminar, el emprendimiento y sus productos se ocultan y el historial de pedidos se conserva, anonimizado, para las otras partes.
- **¿El sistema conserva la información por un período antes de eliminarla definitivamente, para permitir arrepentimiento?** La cuenta se desactiva de inmediato y sus datos personales se anonimizan a los 30 días; durante ese plazo solo un administrador puede restaurarla. El usuario no puede reactivarla desde la aplicación.
- **¿Qué pasa con las calificaciones y comentarios publicados por una cuenta que se elimina?** Permanecen visibles con el autor como "Usuario eliminado", para no alterar la reputación de terceros.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir a un usuario solicitar la eliminación de su propia cuenta.
- **FR-002**: El sistema MUST verificar que no existan pedidos, emprendimientos o domicilios pendientes antes de completar la eliminación.
- **FR-003**: El sistema MUST impedir el inicio de sesión con una cuenta eliminada.

### Key Entities

- **Cuenta**: Registro del usuario cuyo ciclo de vida incluye un estado de eliminación/desactivación.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Contraseña actual | Sí | Debe coincidir con la almacenada. |
| Confirmación de eliminación | Sí | Aceptación expresa de que la acción desactiva la cuenta. |

**Datos que asigna el sistema**

- Fecha de desactivación y de anonimización (30 días después).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El sistema rechaza el 100% de las solicitudes de eliminación cuando existen operaciones pendientes asociadas a la cuenta.
- **SC-002**: Una cuenta eliminada no permite iniciar sesión en el 100% de los intentos posteriores a la eliminación.

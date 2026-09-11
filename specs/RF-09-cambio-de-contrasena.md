# Feature Specification: Cambio de contraseña

**Created**: 2026-09-11
**Requerimiento funcional**: RF-09
**Historias de usuario relacionadas**: HU-25, HU-29

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Usuario cambia su contraseña (Priority: P1)

Como usuario registrado, quiero poder cambiar mi contraseña, para mantener la seguridad de mi cuenta.

**Why this priority**: La gestión de contraseñas es un control de seguridad fundamental que protege el acceso a todas las demás funcionalidades.

**Independent Test**: Puede probarse ingresando la contraseña actual y una nueva contraseña válida, y verificando que la sesión pueda reiniciarse solo con la nueva contraseña.

**Acceptance Scenarios**:

1. **Scenario**: Cambio exitoso de contraseña
   - **Given** conozco mi contraseña actual o tengo acceso a recuperación
   - **When** ingreso la contraseña actual y una nueva contraseña válida
   - **Then** el sistema actualiza la contraseña y cierra las demás sesiones activas

2. **Scenario**: Intento de cambio con contraseña actual incorrecta
   - **Given** tengo una cuenta activa
   - **When** ingreso una contraseña actual incorrecta
   - **Then** el sistema rechaza el cambio y no modifica la contraseña vigente

---

### User Story 2 - Administrador cambia su contraseña (Priority: P1)

Como administrador, quiero cambiar mi contraseña, para mantener segura mi cuenta administrativa.

**Why this priority**: La cuenta administrativa tiene privilegios elevados; su seguridad es tan crítica como la de las cuentas de usuario regulares.

**Independent Test**: Puede probarse iniciando sesión como administrador, cambiando la contraseña y verificando que las sesiones administrativas previas queden invalidadas.

**Acceptance Scenarios**:

1. **Scenario**: Cambio exitoso de contraseña administrativa
   - **Given** conozco mi contraseña administrativa actual
   - **When** ingreso la contraseña actual y una nueva contraseña válida
   - **Then** el sistema actualiza la contraseña y cierra las demás sesiones administrativas activas

### Edge Cases

- ¿Qué ocurre si la nueva contraseña no cumple los requisitos mínimos de seguridad (longitud, complejidad)?
- ¿Cómo se maneja el cambio de contraseña mediante recuperación cuando el usuario no recuerda la contraseña actual?
- ¿Qué pasa si el usuario intenta reutilizar su contraseña actual como "nueva" contraseña?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir a los usuarios y al administrador cambiar su contraseña ingresando la contraseña actual o mediante un mecanismo de recuperación.
- **FR-002**: El sistema MUST validar que la nueva contraseña cumpla los requisitos mínimos de seguridad definidos.
- **FR-003**: El sistema MUST cerrar todas las demás sesiones activas de la cuenta al completarse el cambio de contraseña.
- **FR-004**: El sistema MUST rechazar el cambio si la contraseña actual ingresada no coincide con la almacenada.

### Key Entities

- **Cuenta**: Contiene la contraseña (almacenada de forma segura) y las sesiones activas asociadas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las sesiones activas distintas a la actual se cierran inmediatamente tras un cambio de contraseña exitoso.
- **SC-002**: El sistema rechaza el 100% de los cambios de contraseña que no cumplan los requisitos mínimos de seguridad.

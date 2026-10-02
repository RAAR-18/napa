# Feature Specification: Configuración de preferencias de notificación

**Created**: 2026-09-11
**Requerimiento funcional**: RF-39
**Historias de usuario relacionadas**: HU-64

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Decidir qué notificaciones recibir (Priority: P3)

Como usuario (cliente, vendedor, domiciliario o administrador), quiero configurar mis preferencias de notificación, para decidir qué tipos de notificaciones deseo recibir.

**Why this priority**: Es una función de personalización que mejora la experiencia pero no es indispensable para el funcionamiento de los flujos transaccionales de la plataforma.

**Independent Test**: Puede probarse de forma independiente desactivando un tipo de notificación desde la configuración y verificando que ese tipo deja de generarse para el usuario, mientras los demás tipos continúan llegando.

**Acceptance Scenarios**:

1. **Scenario**: Desactivar un tipo de notificación
   - **Given** tengo notificaciones habilitadas
   - **When** desactivo un tipo de notificación específico
   - **Then** el sistema deja de enviarme notificaciones de ese tipo, conservando las demás

2. **Scenario**: Reactivar un tipo de notificación previamente desactivado
   - **Given** tengo un tipo de notificación desactivado
   - **When** lo reactivo desde mis preferencias
   - **Then** el sistema vuelve a generarme notificaciones de ese tipo ante nuevos eventos

### Edge Cases

- ¿Qué sucede si el usuario desactiva un tipo de notificación crítico (ej. cambios de estado de un domicilio en curso)?
- ¿Se conservan las preferencias configuradas si el usuario cambia de rol o dispositivo?
- ¿Qué ocurre con las notificaciones ya generadas antes de desactivar un tipo?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a los usuarios configurar qué tipos de notificación desean recibir.
- **FR-002**: El sistema DEBE respetar las preferencias configuradas al generar nuevas notificaciones.
- **FR-003**: El sistema DEBE permitir al usuario modificar sus preferencias en cualquier momento.

### Key Entities

- **Preferencia de notificación**: Representa la configuración de un usuario sobre qué tipos de notificación desea recibir.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un cambio en las preferencias de notificación se aplica a los eventos generados a partir de ese momento en el 100% de los casos.
- **SC-002**: El usuario puede modificar sus preferencias de notificación en menos de 1 minuto.

# Feature Specification: Rechazo de domicilio

**Created**: 2026-09-11
**Requerimiento funcional**: RF-18
**Historias de usuario relacionadas**: HU-36

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Descartar un domicilio sin afectar a otros domiciliarios (Priority: P2)

Como domiciliario, quiero rechazar un domicilio que se me presentó como disponible, para que deje de aparecer en mi lista, sin afectar su disponibilidad para los demás domiciliarios.

**Why this priority**: Complementa el flujo de aceptación (RF-17) permitiendo descartar opciones no deseadas; no es indispensable para completar un domicilio pero mejora la experiencia del domiciliario, por lo que se clasifica como P2.

**Independent Test**: Puede probarse de forma independiente rechazando un domicilio disponible desde la cuenta de un domiciliario y verificando que desaparece de su lista mientras permanece visible para otros domiciliarios.

**Acceptance Scenarios**:

1. **Scenario**: Rechazo de un domicilio disponible
   - **Given** el domiciliario tiene un domicilio disponible en su lista
   - **When** lo rechaza
   - **Then** el sistema lo oculta únicamente de su lista y el domicilio permanece visible y disponible para el resto de los domiciliarios

2. **Scenario**: Verificación de disponibilidad para otros domiciliarios
   - **Given** un domiciliario rechazó un domicilio
   - **When** otro domiciliario consulta la lista de domicilios disponibles
   - **Then** el domicilio rechazado sigue apareciendo en su lista de disponibles

### Edge Cases

- ¿Qué sucede si todos los domiciliarios activos rechazan el mismo domicilio?
- ¿Puede un domiciliario revertir el rechazo de un domicilio?
- ¿Qué ocurre si el domicilio es cancelado por el vendedor después de ser rechazado por varios domiciliarios?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario rechazar un domicilio disponible desde su lista.
- **FR-002**: El sistema DEBE ocultar el domicilio rechazado únicamente de la lista del domiciliario que lo rechazó.
- **FR-003**: El sistema DEBE mantener el domicilio visible y disponible para el resto de los domiciliarios tras un rechazo.

### Key Entities

- **Domicilio**: Mantiene una lista de domiciliarios que lo han rechazado, sin cambiar su estado general de disponibilidad.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios rechazados por un domiciliario dejan de aparecer en su lista de forma inmediata.
- **SC-002**: El rechazo de un domiciliario no reduce la disponibilidad del domicilio para el resto en el 100% de los casos.

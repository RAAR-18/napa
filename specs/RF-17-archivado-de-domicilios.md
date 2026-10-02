# Feature Specification: Rechazo de domicilios

**Created**: 2026-09-11
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-17
**Historias de usuario relacionadas**: HU-37

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Descartar domicilios que no me interesan (Priority: P2)

Como domiciliario, quiero rechazar de forma definitiva un domicilio disponible que no me interesa, para que deje de aparecer en mi lista sin afectar a los demás domiciliarios.

**Why this priority**: Mantiene limpia la lista del domiciliario y reduce el ruido al elegir, pero no bloquea el ciclo del domicilio, por lo que se clasifica como P2.

**Independent Test**: Puede probarse rechazando un domicilio disponible y verificando que desaparece permanentemente de la lista de ese domiciliario pero sigue visible para otro.

**Acceptance Scenarios**:

1. **Scenario**: Rechazo de un domicilio disponible
   - **Given** el domiciliario ve un domicilio disponible que no le interesa
   - **When** lo rechaza
   - **Then** el sistema lo retira de forma permanente de su lista de disponibles y lo mantiene disponible para los demás domiciliarios

2. **Scenario**: El rechazo no notifica ni penaliza
   - **Given** el domiciliario rechaza un domicilio
   - **When** el sistema procesa la acción
   - **Then** el sistema no notifica ni penaliza al vendedor, al cliente ni al domiciliario, porque rechazar es una acción personal, no un incumplimiento

3. **Scenario**: El rechazo es definitivo
   - **Given** el domiciliario rechazó un domicilio
   - **When** revisa las acciones disponibles sobre ese domicilio
   - **Then** el sistema no ofrece ninguna opción para revertir el rechazo ni una lista de "rechazados" que consultar

4. **Scenario**: Rechazo de un domicilio ya tomado por otro
   - **Given** el domicilio fue tomado por otro domiciliario antes de que se confirme el rechazo
   - **When** el domiciliario intenta rechazarlo
   - **Then** el sistema informa que el domicilio ya no está disponible y lo retira de su lista sin registrar el rechazo

### Edge Cases

- ¿Existe un límite de rechazos por domiciliario en un período de tiempo, para evitar que un domiciliario "filtre" agresivamente sin aceptar nunca?
- ¿Qué ocurre si el domicilio es cancelado por el administrador después de que el domiciliario lo rechazó?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario rechazar un domicilio disponible, retirándolo de forma **definitiva** de su lista de disponibles.
- **FR-002**: El sistema DEBE mantener el domicilio rechazado disponible para los demás domiciliarios.
- **FR-003**: El sistema DEBE tratar el rechazo como una acción personal del domiciliario, sin notificar ni penalizar a otros usuarios.
- **FR-004**: El sistema NO DEBE ofrecer una lista de domicilios rechazados ni una acción de restauración: el rechazo no es reversible.

### Key Entities

- **Rechazo de domicilio**: Relación definitiva entre un domiciliario y un domicilio que este decidió descartar; incluye fecha de rechazo. No es editable ni reversible.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios rechazados por un domiciliario dejan de aparecer en su lista de disponibles de inmediato y de forma permanente.
- **SC-002**: El 0% de los rechazos afecta la disponibilidad del domicilio para otros domiciliarios.
- **SC-003**: El domiciliario puede rechazar un domicilio en menos de 5 segundos.
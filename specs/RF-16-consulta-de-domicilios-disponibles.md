# Feature Specification: Consulta de domicilios disponibles

**Created**: 2026-09-11
**Requerimiento funcional**: RF-16
**Historias de usuario relacionadas**: HU-34

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Elegir un domicilio para realizar (Priority: P1)

Como domiciliario, quiero ver los domicilios disponibles para tomar, para elegir cuál voy a realizar.

**Why this priority**: Es el punto de entrada del flujo de entregas para el domiciliario; sin esta función no puede iniciarse ningún domicilio, por lo que se clasifica como P1.

**Independent Test**: Puede probarse de forma independiente publicando uno o más domicilios y verificando que un domiciliario puede consultarlos en su lista de disponibles con su información básica.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de domicilios sin asignar
   - **Given** existen domicilios publicados sin asignar
   - **When** el domiciliario consulta la lista de domicilios disponibles
   - **Then** el sistema los muestra con su información básica (origen, destino y costo)

2. **Scenario**: Lista vacía de domicilios disponibles
   - **Given** no existen domicilios sin asignar en ese momento
   - **When** el domiciliario consulta la lista de disponibles
   - **Then** el sistema muestra un mensaje indicando que no hay domicilios disponibles actualmente

### Edge Cases

- ¿Qué sucede si un domicilio es aceptado por otro domiciliario mientras se está consultando la lista?
- ¿Cómo se muestran los domicilios que el propio domiciliario ya rechazó previamente?
- ¿Qué ocurre si un domicilio disponible es cancelado por el vendedor mientras aparece en la lista?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario consultar el listado de domicilios publicados que no han sido asignados a ningún domiciliario.
- **FR-002**: El sistema DEBE mostrar, para cada domicilio disponible, al menos su origen, destino y costo.
- **FR-003**: El sistema DEBE excluir de la lista los domicilios que el domiciliario ya rechazó previamente (ver RF-18).

### Key Entities

- **Domicilio**: Servicio de entrega en estado "disponible", visible para todos los domiciliarios salvo quienes lo hayan rechazado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El domiciliario puede visualizar la lista actualizada de domicilios disponibles en menos de 3 segundos.
- **SC-002**: El 100% de los domicilios publicados y sin asignar aparecen en la lista de disponibles para los domiciliarios elegibles.

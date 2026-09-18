# Feature Specification: Edición de emprendimiento

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-29
**Historias de usuario relacionadas**: HU-54

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Mantener actualizada la información del emprendimiento (Priority: P2)

Como vendedor, quiero editar la información de mi emprendimiento (ubicación, horario, descripción), para mantenerla actualizada según mi actividad diaria.

**Why this priority**: Es una función de mantenimiento sobre un emprendimiento ya creado; agrega valor continuo pero no bloquea el flujo inicial de publicación ni de venta.

**Independent Test**: Puede probarse de forma independiente creando un emprendimiento, modificando uno de sus campos y verificando que el cambio se refleja en la vista pública del emprendimiento, sin depender de otras funcionalidades.

**Acceptance Scenarios**:

1. **Scenario**: Edición exitosa de un campo del emprendimiento
   - **Given** tengo un emprendimiento creado
   - **When** modifico uno o varios campos (ubicación, horario, descripción)
   - **Then** el sistema guarda los cambios y los muestra a los clientes de inmediato

2. **Scenario**: Intento de edición con datos inválidos
   - **Given** tengo un emprendimiento creado
   - **When** intento guardar un cambio con un campo obligatorio vacío o inválido
   - **Then** el sistema rechaza el cambio y me indica el error específico

### Edge Cases

- ¿Qué sucede si el vendedor cambia la ubicación mientras tiene pedidos o domicilios en curso?
- ¿Cómo maneja el sistema una edición concurrente sobre el mismo emprendimiento desde dos sesiones del vendedor?
- ¿Qué ocurre si se intenta editar un emprendimiento que fue eliminado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor editar la ubicación, el horario y la descripción de su emprendimiento.
- **FR-002**: El sistema DEBE validar los datos modificados antes de guardar los cambios.
- **FR-003**: El sistema DEBE reflejar los cambios guardados en la vista pública del emprendimiento de forma inmediata.
- **FR-004**: El sistema DEBE permitir al vendedor ambulante actualizar su ubicación de venta cada vez que cambie de zona durante la jornada.

### Key Entities

- **Emprendimiento**: Representa el negocio del vendedor; los campos ubicación, horario y descripción son editables.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los cambios guardados sobre un emprendimiento se reflejan para los clientes en menos de 3 segundos.
- **SC-002**: El 100% de los intentos de edición con datos inválidos son rechazados con un mensaje claro para el vendedor.

# Feature Specification: Creación de emprendimiento

**Created**: 2026-09-11
**Requerimiento funcional**: RF-26
**Historias de usuario relacionadas**: HU-47

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Publicar un emprendimiento en la plataforma (Priority: P1)

Como vendedor, quiero crear mi emprendimiento indicando nombre, tipo de producto, ubicación y descripción, para que los clientes puedan encontrarme en la plataforma.

**Why this priority**: Sin un emprendimiento creado, el vendedor no puede publicar productos ni recibir pedidos; es el punto de entrada obligatorio para todo el flujo de ventas.

**Independent Test**: Puede probarse de forma independiente registrando un usuario con rol vendedor, completando el formulario de creación de emprendimiento y verificando que el emprendimiento queda visible en el listado público, sin depender de que existan productos o pedidos.

**Acceptance Scenarios**:

1. **Scenario**: Creación exitosa de emprendimiento
   - **Given** estoy registrado como vendedor y no tengo un emprendimiento activo
   - **When** completo el formulario de creación con nombre, tipo de producto, ubicación y descripción válidos
   - **Then** el sistema publica el emprendimiento y lo hace visible para los clientes

2. **Scenario**: Intento de crear un segundo emprendimiento
   - **Given** ya tengo un emprendimiento activo creado
   - **When** intento crear otro emprendimiento
   - **Then** el sistema impide la creación y me informa que ya cuento con un emprendimiento activo

### Edge Cases

- ¿Qué sucede si el vendedor deja campos obligatorios (nombre, ubicación) vacíos?
- ¿Cómo maneja el sistema una ubicación inválida o fuera de la cobertura del servicio?
- ¿Qué ocurre si el vendedor intenta crear un emprendimiento con un nombre ya usado por otro emprendimiento en la misma zona?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a un usuario con rol vendedor crear un emprendimiento indicando nombre, tipo de producto, ubicación y descripción.
- **FR-002**: El sistema DEBE validar que los campos obligatorios estén completos antes de publicar el emprendimiento.
- **FR-003**: El sistema DEBE impedir que un vendedor cree un nuevo emprendimiento mientras ya tenga uno activo.
- **FR-004**: El sistema DEBE publicar el emprendimiento como visible para los clientes inmediatamente después de su creación.

### Key Entities

- **Emprendimiento**: Representa el negocio del vendedor en la plataforma; incluye nombre, tipo de producto, ubicación, descripción y estado (activo/inactivo).
- **Vendedor**: Usuario propietario de un emprendimiento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un vendedor puede completar la creación de su emprendimiento en menos de 3 minutos.
- **SC-002**: El 100% de los emprendimientos creados con datos válidos quedan visibles para los clientes de forma inmediata.

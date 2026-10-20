# Feature Specification: Listado de emprendimientos

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-31
**Historias de usuario relacionadas**: HU-56

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Explorar emprendimientos disponibles (Priority: P1)

Como cliente, quiero ver el listado de emprendimientos disponibles en la plataforma, para explorar qué vendedores existen y cuáles están cerca de mí.

**Why this priority**: Es el punto de entrada principal del flujo de compra del cliente; sin esta función el cliente no puede descubrir a los vendedores.

**Independent Test**: Puede probarse de forma independiente registrando al menos un emprendimiento activo y verificando que aparece en el listado al ingresar como cliente a la sección de emprendimientos.

**Acceptance Scenarios**:

1. **Scenario**: Listado de emprendimientos activos
   - **Given** existen emprendimientos activos registrados
   - **When** ingreso a la sección de emprendimientos
   - **Then** el sistema muestra el listado con nombre, categoría, tipo de vendedor (ambulante o de punto fijo) y ubicación de cada uno

2. **Scenario**: Sin emprendimientos disponibles
   - **Given** no existe ningún emprendimiento activo registrado en la plataforma
   - **When** ingreso a la sección de emprendimientos
   - **Then** el sistema muestra un mensaje indicando que no hay emprendimientos disponibles

### Edge Cases

- ¿Qué sucede si un emprendimiento fue eliminado mientras el cliente tenía el listado abierto?
- ¿Cómo se muestra un emprendimiento que temporalmente está fuera de horario de atención?
- ¿Qué ocurre si hay una gran cantidad de emprendimientos registrados? ¿se pagina el resultado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar el listado de emprendimientos activos registrados en la plataforma.
- **FR-002**: El sistema DEBE mostrar, para cada emprendimiento del listado, su nombre, categoría, tipo de vendedor (ambulante o de punto fijo) y ubicación.
- **FR-003**: El sistema DEBE excluir del listado los emprendimientos inactivos o eliminados.
- **FR-004**: El sistema DEBE permitir ordenar el listado por cercanía y filtrarlo por tipo de vendedor.

### Key Entities

- **Emprendimiento**: Representa el negocio del vendedor; se muestra en el listado con nombre, categoría, tipo de vendedor (ambulante o de punto fijo) y ubicación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El listado de emprendimientos se carga en menos de 3 segundos bajo condiciones normales de operación.
- **SC-002**: El 100% de los emprendimientos activos son visibles en el listado del cliente.

# Feature Specification: Listado de usuarios

**Created**: 2026-09-11
**Requerimiento funcional**: RF-69
**Historias de usuario relacionadas**: HU-102

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Visión general de las cuentas registradas (Priority: P2)

Como administrador, quiero listar todos los usuarios, para tener una visión general de las cuentas registradas.

**Why this priority**: Es fundamental para las tareas administrativas y de soporte (búsqueda, moderación, supervisión), aunque es secundaria frente al flujo transaccional principal de clientes, vendedores y domiciliarios.

**Independent Test**: Puede probarse ingresando al panel de administración y verificando que se muestra el listado completo de usuarios con su rol y estado.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del listado general de usuarios
   - **Given** existen usuarios registrados en la plataforma
   - **When** el administrador ingresa a la sección de usuarios
   - **Then** el sistema muestra el listado completo con nombre, rol y estado de cada cuenta

2. **Scenario**: Filtrado del listado por rol
   - **Given** el administrador está en el listado de usuarios
   - **When** aplica un filtro por rol (cliente, vendedor, domiciliario)
   - **Then** el sistema muestra únicamente los usuarios que cumplen el filtro

---

### Edge Cases

- ¿Cómo se pagina el listado cuando existen miles de usuarios registrados?
- ¿Se incluyen en el listado las cuentas ya eliminadas o solo las activas?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al administrador consultar el listado completo de usuarios registrados.
- **FR-002**: El sistema MUST mostrar el rol y estado de cada usuario en el listado.
- **FR-003**: El sistema MUST permitir filtrar el listado de usuarios por rol.

### Key Entities *(include if feature involves data)*

- **Usuario**: Ver definición en RF-66.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador puede acceder al listado completo de usuarios en menos de 3 segundos.
- **SC-002**: El sistema mantiene tiempos de respuesta aceptables al listar usuarios incluso con crecimiento significativo de la base de cuentas.

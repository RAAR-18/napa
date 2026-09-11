# Feature Specification: Listado de domicilios del vendedor

**Created**: 2026-09-11
**Requerimiento funcional**: RF-15
**Historias de usuario relacionadas**: HU-33

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Hacer seguimiento a los envíos propios (Priority: P2)

Como vendedor, quiero ver el listado de mis domicilios con filtros por estado y por fecha, para hacer seguimiento a mis envíos.

**Why this priority**: Es una función de consulta que apoya la operación diaria del vendedor pero no es indispensable para que el ciclo de domicilio funcione, por lo que se clasifica como P2.

**Independent Test**: Puede probarse de forma independiente registrando varios domicilios en distintos estados y verificando que el vendedor puede listarlos y filtrarlos correctamente.

**Acceptance Scenarios**:

1. **Scenario**: Listado sin filtros
   - **Given** el vendedor tiene domicilios registrados en la plataforma
   - **When** ingresa a la sección de sus domicilios
   - **Then** el sistema muestra todos sus domicilios con su estado actual

2. **Scenario**: Filtrado por estado y fecha
   - **Given** el vendedor está en el listado de sus domicilios
   - **When** aplica un filtro de estado o de fecha
   - **Then** el sistema muestra únicamente los domicilios que cumplen el filtro seleccionado

### Edge Cases

- ¿Qué sucede cuando el vendedor no tiene ningún domicilio registrado?
- ¿Cómo se comporta el filtro cuando no existen domicilios que cumplan los criterios seleccionados?
- ¿Qué ocurre si se combinan filtros de estado y fecha simultáneamente sin resultados?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor consultar el listado completo de los domicilios asociados a sus pedidos.
- **FR-002**: El sistema DEBE permitir filtrar el listado de domicilios por estado (disponible, aceptado, en camino, entregado, cancelado).
- **FR-003**: El sistema DEBE permitir filtrar el listado de domicilios por rango de fechas.

### Key Entities

- **Domicilio**: Registro de entrega asociado a un pedido del vendedor, con estado y fecha de creación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede obtener el listado filtrado de sus domicilios en menos de 3 segundos.
- **SC-002**: El 100% de los domicilios que cumplen un filtro aplicado aparecen en el resultado, sin omisiones.

# Feature Specification: Edición de producto

**Created**: 2026-09-11
**Requerimiento funcional**: RF-56
**Historias de usuario relacionadas**: HU-87

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Corregir o actualizar un producto publicado (Priority: P2)

Como vendedor, quiero editar los datos de un producto ya publicado (precio, nombre, descripción), para corregir errores o ajustar precios.

**Why this priority**: Mantener la información correcta mejora la confianza del cliente, pero no bloquea el flujo principal de venta si se realiza con cierto retraso.

**Independent Test**: Puede probarse editando el precio de un producto existente y verificando que el cambio se refleja en el catálogo visible para los clientes.

**Acceptance Scenarios**:

1. **Scenario**: Edición exitosa de producto
   - **Given** el vendedor tiene un producto publicado
   - **When** modifica uno o varios campos del producto (nombre, precio, descripción)
   - **Then** el sistema actualiza la información visible para los clientes

2. **Scenario**: Edición con datos inválidos
   - **Given** el vendedor está editando un producto
   - **When** ingresa un precio negativo
   - **Then** el sistema rechaza el cambio y conserva el valor anterior

---

### Edge Cases

- ¿Qué ocurre si se edita un producto que tiene pedidos pendientes con el precio anterior?
- ¿Cómo maneja el sistema la edición simultánea del mismo producto desde dos sesiones del vendedor?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al vendedor editar el nombre, precio y descripción de un producto publicado.
- **FR-002**: El sistema MUST validar los nuevos valores antes de aplicar los cambios.
- **FR-003**: El sistema MUST reflejar los cambios en el catálogo visible para los clientes de forma inmediata.

### Key Entities *(include if feature involves data)*

- **Producto**: Ver definición en RF-54.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los cambios realizados a un producto se reflejan en el catálogo del cliente en menos de 5 segundos.
- **SC-002**: El 100% de las ediciones con datos inválidos son rechazadas antes de guardarse.

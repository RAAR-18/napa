# Feature Specification: Listado de productos del vendedor

**Created**: 2026-09-11
**Requerimiento funcional**: RF-55
**Historias de usuario relacionadas**: HU-86

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar mis productos publicados (Priority: P1)

Como vendedor, quiero ver el listado completo de mis productos publicados, para gestionarlos fácilmente desde un solo lugar.

**Why this priority**: Es la vista base para administrar el catálogo; sin ella el vendedor no puede ubicar, editar, actualizar inventario ni eliminar los productos que ya registró, por lo que es tan crítica como el propio registro de productos.

**Independent Test**: Puede probarse registrando al menos un producto y verificando que aparece en la sección "Mis productos" con su precio y disponibilidad.

**Acceptance Scenarios**:

1. **Scenario**: Listado con productos existentes
   - **Given** el vendedor tiene al menos un producto publicado
   - **When** ingresa a la sección de sus productos
   - **Then** el sistema muestra todos sus productos con precio y disponibilidad actual

2. **Scenario**: Listado sin productos
   - **Given** el vendedor no ha publicado ningún producto
   - **When** ingresa a la sección de sus productos
   - **Then** el sistema muestra un estado vacío indicando que no hay productos registrados

---

### Edge Cases

- ¿Cómo se muestran en el listado los productos marcados como agotados?
- ¿Qué ocurre si el vendedor tiene productos en más de un emprendimiento?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al vendedor consultar el listado completo de sus productos publicados.
- **FR-002**: El sistema MUST mostrar en el listado el precio y la disponibilidad actual de cada producto.
- **FR-003**: El sistema MUST distinguir visualmente los productos agotados de los disponibles.

### Key Entities *(include if feature involves data)*

- **Producto**: Ver definición en RF-54; en este requerimiento se consulta en forma de listado filtrado por vendedor.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede ver el listado completo de sus productos en menos de 2 segundos.
- **SC-002**: El 100% de los productos publicados por el vendedor aparecen en su listado con precio y disponibilidad actualizados.

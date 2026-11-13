# Feature Specification: Listado de productos de un emprendimiento

**Created**: 2026-09-11
**Requerimiento funcional**: RF-33
**Historias de usuario relacionadas**: HU-58

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver el catálogo de un emprendimiento (Priority: P2)

Como cliente, quiero ver los productos de un emprendimiento específico, con precio y cantidad, para decidir qué comprar.

**Why this priority**: Habilita directamente la decisión de compra, pero depende de que el cliente ya haya seleccionado un emprendimiento, por lo que se prioriza justo después de la consulta de emprendimiento.

**Independent Test**: Puede probarse de forma independiente creando un emprendimiento con al menos un producto disponible y verificando que aparece en su catálogo al ser consultado por un cliente.

**Acceptance Scenarios**:

1. **Scenario**: Catálogo con productos disponibles
   - **Given** selecciono un emprendimiento del listado
   - **When** ingreso a su catálogo de productos
   - **Then** el sistema muestra los productos publicados; los agotados aparecen con la etiqueta ‘solo reserva’, junto con su precio y cantidad

2. **Scenario**: Emprendimiento sin productos disponibles
   - **Given** un emprendimiento no tiene productos publicados
   - **When** ingreso a su catálogo
   - **Then** el sistema muestra un mensaje indicando que no hay productos disponibles en ese momento

### Edge Cases

- ¿Qué sucede si un producto se agota justo cuando el cliente está visualizando el catálogo? (el producto permanece visible y pasa a mostrarse con la etiqueta "solo reserva")
- ¿Qué ocurre si el cliente ya agregó un producto a un pedido inmediato y este se agota antes de confirmarlo? (el sistema lo informa y pide ajustar la selección o marcar el pedido como reserva, RF-45)
- ¿Cómo se ordenan los productos dentro del catálogo (precio, nombre, más vendidos)? ¿Los agotados van al final?
- ¿Qué ocurre si el emprendimiento fue eliminado mientras el cliente consultaba su catálogo?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar el catálogo de productos de un emprendimiento seleccionado.
- **FR-002**: El sistema DEBE mostrar también los productos agotados, con la etiqueta ‘solo reserva’.
- **FR-003**: El sistema DEBE mostrar el precio y la cantidad disponible de cada producto listado.

### Key Entities

- **Producto**: Artículo publicado por el vendedor; incluye nombre, precio, cantidad disponible y emprendimiento al que pertenece.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El catálogo de productos de un emprendimiento se carga en menos de 3 segundos.
- **SC-002**: El 100% de los productos agotados mostrados llevan la etiqueta ‘solo reserva’.

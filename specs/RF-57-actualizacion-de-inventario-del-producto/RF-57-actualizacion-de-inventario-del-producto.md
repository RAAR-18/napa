# Feature Specification: Actualización de inventario del producto

**Created**: 2026-09-11
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-57
**Historias de usuario relacionadas**: HU-88

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Actualizar disponibilidad de un producto (Priority: P1)

Como vendedor, quiero actualizar rápidamente la cantidad disponible de un producto o marcarlo como agotado, para evitar que los clientes pidan algo que ya no tengo.

**Why this priority**: Es crítico para productos perecederos: un inventario desactualizado genera pedidos que el vendedor no puede cumplir, dañando la confianza del cliente y generando pérdidas. Es una de las funcionalidades núcleo del negocio.

**Independent Test**: Puede probarse cambiando la cantidad disponible de un producto a cero y verificando que el sistema lo marca automáticamente como agotado y deja de estar disponible para pedidos.

**Acceptance Scenarios**:

1. **Scenario**: Actualización manual de cantidad
   - **Given** el vendedor tiene un producto publicado
   - **When** cambia la cantidad disponible a un nuevo valor válido
   - **Then** el sistema actualiza el estado del producto con la nueva cantidad

2. **Scenario**: Producto agotado automáticamente
   - **Given** el vendedor tiene un producto publicado
   - **When** la cantidad disponible llega a cero (manual o por consumo de pedidos)
   - **Then** el sistema marca el producto como agotado y lo marca como agotado; solo admite pedidos marcados como reserva

---

### Edge Cases

- ¿Qué ocurre si dos clientes intentan pedir simultáneamente la última unidad disponible de un producto?
- ¿Qué sucede si el vendedor intenta establecer una cantidad negativa?
- ¿El producto agotado permanece visible en el catálogo en modo "no disponible" o se oculta por completo? Permanece visible con la etiqueta ‘solo reserva’ (RF-33)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al vendedor actualizar manualmente la cantidad disponible actual de un producto.
- **FR-002**: El sistema MUST marcar automáticamente un producto como agotado cuando su cantidad disponible llegue a cero.
- **FR-003**: El sistema DEBE impedir pedidos inmediatos sobre productos agotados; un producto agotado solo admite pedidos marcados como reserva.
- **FR-004**: El sistema MUST rechazar valores de cantidad negativos.
- **FR-005**: El sistema MUST controlar actualizaciones concurrentes de inventario para evitar sobreventa de la misma unidad.
- **FR-006**: El sistema DEBE descontar de la cantidad disponible las unidades de los pedidos que no estén marcados como reserva en el momento en que el vendedor los acepta, y rechazar la aceptación si no alcanza. Las reservas no descuentan inventario en ningún momento; el vendedor gestiona su inventario con su propio criterio.
- **FR-007**: El sistema DEBE manejar la cantidad disponible como un valor decimal, expresada en una unidad (p. ej. kg, libra, unidad) definida por el vendedor.

### Key Entities *(include if feature involves data)*

- **Producto**: Ver definición en RF-54; el atributo `cantidad_disponible` (decimal) y unidad (cantidad actual declarada por el vendedor) y el estado `agotado` son el foco de este requerimiento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El estado de un producto se actualiza a "agotado" en menos de 2 segundos después de que su cantidad llega a cero.
- **SC-002**: El sistema previene el 100% de los intentos de sobreventa de unidades no disponibles.
- **SC-003**: El vendedor puede actualizar la cantidad de un producto en menos de 15 segundos.
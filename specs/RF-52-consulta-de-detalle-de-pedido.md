# Feature Specification: Consulta de detalle de pedido

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-52
**Historias de usuario relacionadas**: HU-83

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Vendedor consulta el detalle completo de un pedido (Priority: P1)

Como vendedor, quiero consultar un pedido, para conocer todos sus detalles.

**Why this priority**: El vendedor necesita el detalle completo de un pedido (productos, cantidades, cliente, entrega, pago) para poder confirmarlo y prepararlo correctamente; se clasifica como P1 por ser indispensable en la gestión operativa.

**Independent Test**: Puede probarse seleccionando un pedido desde el listado del vendedor y verificando que se muestra toda la información relevante del pedido.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa del detalle de un pedido
   - **Given** el vendedor tiene un pedido recibido en su listado
   - **When** el vendedor selecciona ese pedido para ver su detalle
   - **Then** el sistema muestra los productos y cantidades, el total, la modalidad de entrega (con la fecha y la ubicación de entrega cuando aplique), el método de pago, el monto a cobrar, el estado y los datos básicos del cliente

2. **Scenario**: Intento de consultar el detalle de un pedido de otro emprendimiento
   - **Given** existe un pedido que no pertenece al emprendimiento del vendedor autenticado
   - **When** el vendedor intenta acceder a su detalle
   - **Then** el sistema deniega el acceso e informa que el pedido no está disponible

### Edge Cases

- **¿Qué sucede si un producto del pedido fue eliminado del catálogo después de haberse registrado en el pedido?** El detalle conserva el nombre y el precio con que se compró y lo marca como "producto no disponible".
- **¿Cómo se muestra el detalle si el pedido fue cancelado antes de ser confirmado?** Se muestra en solo lectura con el estado (cancelado, rechazado o vencido), la fecha y el motivo si existe.
- **¿Qué ocurre si el detalle del pedido incluye un domicilio con estado desactualizado?** El estado del domicilio se consulta al abrir el detalle, con su hora de última actualización, y se refresca automáticamente.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor consultar el detalle completo de un pedido asociado a su emprendimiento.
- **FR-002**: El sistema DEBE mostrar en el detalle los productos, cantidades, método de pago, total a cobrar, modalidad de entrega, ubicación de entrega cuando aplique y estado del pedido.
- **FR-003**: El sistema DEBE mostrar en el detalle los datos básicos del cliente asociado al pedido.
- **FR-004**: El sistema DEBE impedir que el vendedor consulte el detalle de un pedido que no pertenece a su emprendimiento.

### Key Entities

- **Pedido**: Entidad consultada; incluye productos, cantidades, método de pago, total a cobrar, modalidad de entrega, ubicación de entrega, estado y referencia al cliente.
- **Producto**: Ítem incluido en el pedido con su cantidad y precio al momento de la compra.

### Data Rules

**Datos que ingresa el usuario**: Ninguno.

**Datos que se muestran o filtran**

- Ítems con cantidad y precio, total a cobrar, método de pago, modalidad, ubicación de entrega, estado y datos básicos del cliente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede acceder al detalle completo de un pedido en menos de 3 segundos.
- **SC-002**: El 100% de los detalles consultados muestran información completa y consistente con el pedido original.
- **SC-003**: El 0% de los intentos de un vendedor por consultar pedidos de otro emprendimiento resulta exitoso.

# Feature Specification: Realización de pedido

**Created**: 2026-09-11
**Requerimiento funcional**: RF-41
**Historias de usuario relacionadas**: HU-64, HU-69, HU-70, HU-71

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crear un pedido de punta a punta (Priority: P1)

Como cliente, quiero realizar un pedido seleccionando los productos y cantidades, la modalidad de entrega y el método de pago, para comprar productos de un emprendimiento y que mi pedido quede registrado para su confirmación por el vendedor.

**Why this priority**: Es el flujo central de la plataforma; sin la posibilidad de crear un pedido no existe transacción entre cliente y vendedor, por lo que se clasifica como P1.

**Independent Test**: Puede probarse de forma independiente seleccionando productos de un emprendimiento, eligiendo una modalidad de entrega y un método de pago, confirmando el pedido y verificando que queda registrado en estado pendiente.

**Acceptance Scenarios**:

1. **Scenario**: Creación exitosa de un pedido completo
   - **Given** el cliente está consultando el catálogo de un emprendimiento con productos disponibles
   - **When** el cliente selecciona uno o más productos con sus cantidades, elige una modalidad de entrega y un método de pago, y confirma el pedido
   - **Then** el sistema registra el pedido en estado pendiente y lo pone a disposición del vendedor para su confirmación

2. **Scenario**: Selección de productos sin definir modalidad de entrega ni método de pago
   - **Given** el cliente ya seleccionó los productos y cantidades que desea comprar
   - **When** el cliente intenta confirmar el pedido sin haber elegido una modalidad de entrega o un método de pago
   - **Then** el sistema le impide continuar e indica los datos faltantes

3. **Scenario**: Intento de pedir un producto sin disponibilidad
   - **Given** el cliente seleccionó un producto que se agotó antes de confirmar el pedido
   - **When** el cliente intenta confirmar el pedido
   - **Then** el sistema informa que el producto ya no está disponible y le solicita ajustar la selección

### Edge Cases

- ¿Qué sucede si el cliente intenta confirmar un pedido sin haber seleccionado ningún producto?
- ¿Cómo maneja el sistema un cambio de precio o disponibilidad de un producto entre su selección y la confirmación del pedido?
- ¿Qué ocurre si la modalidad de entrega seleccionada no está disponible para la ubicación del cliente?
- ¿Qué pasa si el cliente cierra la aplicación o pierde conexión después de seleccionar productos pero antes de confirmar el pedido?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente seleccionar uno o más productos de un emprendimiento junto con la cantidad deseada de cada uno.
- **FR-002**: El sistema DEBE validar la disponibilidad de inventario de cada producto seleccionado antes de confirmar el pedido.
- **FR-003**: El sistema DEBE permitir al cliente seleccionar una modalidad de entrega para el pedido (por ejemplo, domicilio o recogida).
- **FR-004**: El sistema DEBE permitir al cliente seleccionar un método de pago para el pedido.
- **FR-005**: El sistema DEBE impedir la confirmación del pedido si falta la selección de productos, modalidad de entrega o método de pago.
- **FR-006**: El sistema DEBE registrar el pedido en estado "pendiente" al momento de su creación.
- **FR-007**: El sistema DEBE notificar al vendedor sobre la creación de un nuevo pedido pendiente de confirmación.

### Key Entities

- **Pedido**: Representa la compra realizada por un cliente; atributos clave: productos y cantidades, modalidad de entrega, método de pago, estado (pendiente, confirmado, en proceso, entregado, cancelado), fecha de creación.
- **Producto**: Ítem del catálogo del vendedor con cantidad disponible en inventario.
- **Modalidad de entrega**: Forma en la que el cliente recibirá el pedido (domicilio o recogida en el emprendimiento).
- **Método de pago**: Forma de pago seleccionada por el cliente para el pedido.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente puede completar la creación de un pedido (productos, modalidad de entrega y método de pago) en menos de 3 minutos.
- **SC-002**: El 100% de los pedidos confirmados cuentan con productos, modalidad de entrega y método de pago definidos.
- **SC-003**: El 0% de los pedidos se confirma con productos sin disponibilidad de inventario.
- **SC-004**: El vendedor recibe la notificación de un nuevo pedido pendiente en menos de 1 minuto tras su creación.

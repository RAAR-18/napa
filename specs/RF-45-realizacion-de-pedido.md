# Feature Specification: Realización de pedido

**Created**: 2026-09-18
**Requerimiento funcional**: RF-45
**Historias de usuario relacionadas**: HU-72, HU-73, HU-74, HU-76

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Armar el pedido con productos y modalidad de entrega (Priority: P1)

Como cliente, quiero seleccionar los productos y las cantidades de un emprendimiento y elegir la modalidad de entrega que ese vendedor ofrece, para definir qué compro y cómo lo recibo.

**Why this priority**: Es el flujo central de la plataforma: sin ítems y modalidad no existe transacción entre cliente y vendedor, por lo que se clasifica como P1.

**Independent Test**: Puede probarse eligiendo productos de un emprendimiento ambulante y de uno de punto fijo, y verificando que se ofrecen las modalidades que corresponden a cada tipo de vendedor.

**Acceptance Scenarios**:

1. **Scenario**: Modalidades de un vendedor ambulante
   - **Given** consulto el emprendimiento de un vendedor ambulante
   - **When** elijo la modalidad de entrega
   - **Then** el sistema ofrece entrega directa y reserva

2. **Scenario**: Modalidades de un vendedor de punto fijo
   - **Given** consulto el emprendimiento de un vendedor de punto fijo
   - **When** elijo la modalidad de entrega
   - **Then** el sistema ofrece reserva (con retiro en el punto fijo) y domicilio

3. **Scenario**: Pedido con varios ítems
   - **Given** el catálogo tiene productos disponibles
   - **When** agrego varios productos con sus cantidades
   - **Then** el sistema acumula los ítems del pedido con su subtotal y su total

4. **Scenario**: Producto sin disponibilidad
   - **Given** seleccioné un producto que se agotó
   - **When** intento continuar con el pedido
   - **Then** el sistema informa que el producto ya no está disponible y me pide ajustar la selección

---

### User Story 2 - Indicar dónde recibiré el pedido (Priority: P1)

Como cliente, quiero indicar la ubicación donde recibiré mi pedido, para que el vendedor o el domiciliario sepan a dónde llevarlo.

**Why this priority**: Sin ubicación el vendedor ambulante o el domiciliario no pueden entregar, por lo que se clasifica como P1.

**Independent Test**: Puede probarse creando pedidos de entrega directa, reserva con vendedor ambulante y domicilio, y verificando que en todos se registra la ubicación, y que en la reserva con retiro no se solicita.

**Acceptance Scenarios**:

1. **Scenario**: Ubicación para una entrega
   - **Given** elegí entrega directa, reserva con vendedor ambulante o domicilio
   - **When** indico mi ubicación mediante el mapa o la ubicación del dispositivo
   - **Then** el sistema la registra como punto de entrega del pedido

2. **Scenario**: Reserva con retiro en punto fijo
   - **Given** elegí reserva con un vendedor de punto fijo
   - **When** avanzo con el pedido
   - **Then** el sistema no solicita ubicación, porque retiro el pedido en el punto fijo

3. **Scenario**: Ubicación faltante o inválida
   - **Given** la modalidad elegida requiere ubicación
   - **When** intento confirmar sin indicarla o con una inválida
   - **Then** el sistema me impide continuar e indica el dato faltante

---

### User Story 3 - Confirmar el pedido y pagar en efectivo (Priority: P1)

Como cliente, quiero realizar un pedido con los productos seleccionados, para comprarlos al vendedor y pagarlos en efectivo al recibirlos.

**Why this priority**: Registra el pedido y lo pone en manos del vendedor para su respuesta, por lo que se clasifica como P1.

**Independent Test**: Puede probarse confirmando un pedido completo y verificando que queda en estado pendiente y el vendedor es notificado.

**Acceptance Scenarios**:

1. **Scenario**: Creación exitosa de un pedido
   - **Given** seleccioné productos, modalidad y, cuando aplica, ubicación
   - **When** confirmo el pedido
   - **Then** el sistema lo registra en estado pendiente, muestra el total a pagar en efectivo y notifica al vendedor (en la entrega directa, al vendedor ambulante)

2. **Scenario**: Pedido de domicilio con costo de envío
   - **Given** elegí domicilio con un vendedor de punto fijo
   - **When** reviso el resumen antes de confirmar
   - **Then** el sistema muestra el costo del domicilio y el total (productos más domicilio) a pagar en efectivo

3. **Scenario**: Reserva para el día siguiente
   - **Given** elegí reserva
   - **When** confirmo el pedido
   - **Then** el sistema lo registra como reserva para el día siguiente, validando las cantidades contra la disponibilidad prevista (RF-46)

4. **Scenario**: Datos incompletos
   - **Given** falta seleccionar productos, modalidad o ubicación (cuando aplica)
   - **When** intento confirmar el pedido
   - **Then** el sistema me impide continuar e indica los datos faltantes

### Edge Cases

- ¿Qué sucede si el cliente intenta confirmar un pedido sin ningún producto seleccionado?
- ¿Cómo maneja el sistema un cambio de precio o de disponibilidad entre la selección y la confirmación?
- ¿Qué ocurre si el cliente está demasiado lejos de un vendedor ambulante para una entrega directa?
- ¿Qué pasa si el cliente pierde conexión después de armar el pedido y antes de confirmarlo (RNF28)?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente seleccionar uno o más productos de un emprendimiento con la cantidad deseada de cada uno; cada producto seleccionado es un ítem del pedido.
- **FR-002**: El sistema DEBE validar la disponibilidad de inventario de cada ítem antes de confirmar el pedido, salvo en las reservas, donde valida contra la disponibilidad prevista (RF-46).
- **FR-003**: El sistema DEBE ofrecer las modalidades de entrega según el tipo de vendedor: entrega directa y reserva para el vendedor ambulante; reserva y domicilio para el vendedor de punto fijo.
- **FR-004**: El sistema DEBE solicitar la ubicación de entrega en la entrega directa, la reserva con vendedor ambulante y el domicilio, y no solicitarla en la reserva con retiro en punto fijo.
- **FR-005**: El sistema DEBE programar las reservas para el día siguiente a la fecha del pedido.
- **FR-006**: El sistema DEBE calcular y mostrar el total del pedido en efectivo, incluyendo el costo del domicilio cuando aplique, calculado según la distancia y sin ser inferior a la tarifa mínima (RF-44).
- **FR-007**: El sistema DEBE registrar el pedido en estado "pendiente" y notificar al vendedor.
- **FR-008**: El sistema DEBE impedir la confirmación del pedido si falta algún ítem, la modalidad o la ubicación cuando esta aplique.
- **FR-009**: El sistema NO DEBE solicitar un método de pago: el pago es siempre en efectivo.

### Key Entities

- **Pedido**: Compra del cliente a un emprendimiento; incluye ítems, modalidad de entrega, ubicación de entrega cuando aplique, total en efectivo, estado (pendiente, aceptado, rechazado, listo para recoger, en camino, entregado, cancelado) y fecha.
- **Ítem de pedido**: Producto con su cantidad y su precio al momento de la compra; un pedido tiene uno o varios ítems.
- **Modalidad de entrega**: Entrega directa (vendedor ambulante), reserva (ambulante: con entrega al cliente; punto fijo: con retiro) o domicilio (punto fijo).
- **Ubicación de entrega**: Punto indicado por el cliente donde se le entregará el pedido (punto B en un domicilio).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente puede completar un pedido (ítems, modalidad y ubicación) en menos de 3 minutos.
- **SC-002**: El 100% de los pedidos confirmados cuentan con ítems, modalidad y, cuando aplica, ubicación.
- **SC-003**: El 0% de los pedidos se confirma con productos sin disponibilidad de inventario o de disponibilidad prevista.
- **SC-004**: El vendedor recibe la notificación de un nuevo pedido pendiente en menos de 1 minuto.

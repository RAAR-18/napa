# Feature Specification: Realización de pedido

**Created**: 2026-09-18
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-45
**Historias de usuario relacionadas**: HU-72, HU-73, HU-74, HU-76

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Armar el pedido con productos y modalidad de entrega (Priority: P1)

Como cliente, quiero seleccionar los productos y las cantidades de un emprendimiento y elegir la modalidad de entrega que ese vendedor ofrece, para definir qué compro y cómo lo recibo.

**Why this priority**: Es el flujo central de la plataforma: sin ítems y modalidad no existe transacción entre cliente y vendedor, por lo que se clasifica como P1.

**Independent Test**: Puede probarse eligiendo productos de un emprendimiento ambulante y de uno de punto fijo, y verificando que se ofrecen las modalidades que corresponden a cada tipo de vendedor.

**Acceptance Scenarios**:

1. **Scenario**: Modalidad de un vendedor ambulante
   - **Given** consulto el emprendimiento de un vendedor ambulante
   - **When** elijo la modalidad de entrega
   - **Then** el sistema ofrece entrega directa

2. **Scenario**: Modalidades de un vendedor de punto fijo
   - **Given** consulto el emprendimiento de un vendedor de punto fijo
   - **When** elijo la modalidad de entrega
   - **Then** el sistema ofrece recogida en el punto fijo y domicilio

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

Como cliente, quiero indicar la ubicación donde recibiré mi pedido cuando la modalidad lo requiera, para que el vendedor o el domiciliario sepan a dónde llevarlo.

**Why this priority**: Sin ubicación el vendedor ambulante o el domiciliario no pueden entregar, por lo que se clasifica como P1.

**Independent Test**: Puede probarse creando pedidos de entrega directa, recogida y domicilio, verificando que en entrega directa y domicilio se registra la ubicación y en recogida no se solicita.

**Acceptance Scenarios**:

1. **Scenario**: Ubicación para una entrega
   - **Given** elegí entrega directa o domicilio
   - **When** indico mi ubicación mediante el mapa o la ubicación del dispositivo
   - **Then** el sistema la registra como punto de entrega del pedido

2. **Scenario**: Recogida en punto fijo
   - **Given** elegí recogida con un vendedor de punto fijo
   - **When** avanzo con el pedido
   - **Then** el sistema no solicita ubicación, porque retiro el pedido en el punto fijo

3. **Scenario**: Ubicación faltante o inválida
   - **Given** la modalidad elegida requiere ubicación
   - **When** intento confirmar sin indicarla o con una inválida
   - **Then** el sistema me impide continuar e indica el dato faltante

---

### User Story 3 - Marcar el pedido como reserva (Priority: P2)

Como cliente, quiero marcar mi pedido como reserva indicando la fecha y hora que acordé con el vendedor, para pedir con anticipación sin importar la modalidad de entrega elegida.

**Why this priority**: Permite pedidos anticipados, valor diferencial de la plataforma, pero el pedido inmediato funciona sin ella, por lo que se clasifica como P2.

**Independent Test**: Puede probarse creando un pedido marcado como reserva con fecha y hora futuras en cada modalidad y verificando que el atributo se registra sin cambiar la modalidad elegida.

**Acceptance Scenarios**:

1. **Scenario**: Pedido marcado como reserva
   - **Given** elegí productos y modalidad
   - **When** marco el pedido como reserva e indico fecha y hora futuras
   - **Then** el sistema registra el pedido con el atributo de reserva, en estado pendiente de la respuesta del vendedor, quien decide con su propio criterio si puede cumplirlo (RF-53)

2. **Scenario**: Fecha u hora inválida
   - **Given** marqué el pedido como reserva
   - **When** indico una fecha u hora pasada o vacía
   - **Then** el sistema me impide continuar e indica el dato inválido

---

### User Story 4 - Confirmar el pedido y elegir cómo pagar (Priority: P1)

Como cliente, quiero realizar un pedido con los productos seleccionados y elegir el medio de pago disponible, para comprarlos al vendedor.

**Why this priority**: Registra el pedido y lo pone en manos del vendedor para su respuesta, por lo que se clasifica como P1.

**Independent Test**: Puede probarse confirmando un pedido completo en cada modalidad y verificando que queda en estado pendiente, con el medio de pago permitido y que el vendedor es notificado.

**Acceptance Scenarios**:

1. **Scenario**: Creación exitosa de un pedido de entrega directa o recogida
   - **Given** seleccioné productos, modalidad y, cuando aplica, ubicación
   - **When** elijo un medio de pago entre los que el vendedor tiene habilitados (efectivo o cuenta bancaria)
   - **Then** el sistema lo registra en estado pendiente, muestra el total a pagar y notifica al vendedor

2. **Scenario**: Pedido de domicilio
   - **Given** elegí domicilio con un vendedor de punto fijo
   - **When** reviso el resumen antes de confirmar
   - **Then** el sistema muestra el costo del domicilio y el total (productos más domicilio), exige pago por transferencia por ese total, no ofrece efectivo e informa que deberé pagarlo apenas el vendedor acepte el pedido

3. **Scenario**: Datos incompletos
   - **Given** falta seleccionar productos, modalidad, medio de pago o ubicación (cuando aplica)
   - **When** intento confirmar el pedido
   - **Then** el sistema me impide continuar e indica los datos faltantes

### Edge Cases

- ¿Qué sucede si el cliente intenta confirmar un pedido sin ningún producto seleccionado?
- ¿Cómo maneja el sistema un cambio de precio o de disponibilidad entre la selección y la confirmación?
- ¿Qué ocurre si el cliente está demasiado lejos de un vendedor ambulante para una entrega directa?
- ¿Qué pasa si el cliente pierde conexión después de armar el pedido y antes de confirmarlo (RNF28)?
- ¿Qué ocurre si el vendedor deshabilita un medio de pago mientras el cliente arma su pedido?
- ¿Puede reservarse un producto que hoy figura como agotado, si el vendedor lo tendrá en la fecha acordada?
- ¿Qué ocurre si el vendedor de punto fijo aceptó un pedido de domicilio y el cliente nunca realiza la transferencia? (no hay plazo definido; punto pendiente de validar)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente seleccionar uno o más productos de un emprendimiento con la cantidad deseada de cada uno; cada producto seleccionado es un ítem del pedido.
- **FR-002**: El sistema DEBE validar la disponibilidad de inventario actual de cada ítem antes de confirmar un pedido inmediato; en los pedidos marcados como reserva no valida las cantidades contra una disponibilidad futura, porque el vendedor decide con su propio criterio al aceptarlos o rechazarlos (RF-53).
- **FR-003**: El sistema DEBE ofrecer las modalidades de entrega según el tipo de vendedor: entrega directa para el vendedor ambulante; recogida en el punto fijo y domicilio para el vendedor de punto fijo.
- **FR-004**: El sistema DEBE solicitar la ubicación de entrega en la entrega directa y el domicilio, y no solicitarla en la recogida en punto fijo.
- **FR-005**: El sistema DEBE permitir marcar cualquier pedido como reserva, indicando fecha y hora futuras acordadas con el vendedor; la reserva es un atributo del pedido y no altera su modalidad de entrega.
- **FR-006**: El sistema DEBE calcular y mostrar el total del pedido, incluyendo el costo del domicilio cuando aplique, calculado según la distancia y sin ser inferior a la tarifa mínima (RF-44).
- **FR-007**: El sistema DEBE registrar el pedido en estado "pendiente" y notificar al vendedor.
- **FR-008**: El sistema DEBE impedir la confirmación del pedido si falta algún ítem, la modalidad, el medio de pago, la ubicación cuando esta aplique, o la fecha y hora cuando se marque como reserva.
- **FR-009**: El sistema DEBE ofrecer como medio de pago, en entrega directa y recogida, únicamente los que el vendedor tenga habilitados (RF-63), y exigir pago por transferencia en el domicilio.
- **FR-010**: El sistema DEBE informar al cliente, en un pedido de domicilio, que el total (productos más domicilio) deberá pagarse por transferencia apenas el vendedor acepte el pedido.

### Key Entities

- **Pedido**: Compra del cliente a un emprendimiento; incluye ítems, modalidad de entrega, ubicación de entrega cuando aplique, medio de pago, atributo de reserva (sí/no con fecha y hora acordadas), total, estado (pendiente, aceptado, rechazado, listo para recoger, en camino, entregado, cancelado) y fecha.
- **Ítem de pedido**: Producto con su cantidad y su precio al momento de la compra.
- **Modalidad de entrega**: Entrega directa (vendedor ambulante), recogida en punto fijo o domicilio (punto fijo).
- **Ubicación de entrega**: Punto indicado por el cliente donde se le entregará el pedido (punto B en un domicilio).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente puede completar un pedido (ítems, modalidad, ubicación y pago) en menos de 3 minutos.
- **SC-002**: El 100% de los pedidos confirmados cuentan con ítems, modalidad, medio de pago y, cuando aplica, ubicación y fecha y hora de reserva.
- **SC-003**: El 0% de los pedidos inmediatos se confirma con productos sin disponibilidad de inventario.
- **SC-004**: El vendedor recibe la notificación de un nuevo pedido pendiente en menos de 1 minuto.
- **SC-005**: El 0% de los pedidos de domicilio se confirma con pago en efectivo.
- **SC-006**: El 100% de los pedidos de domicilio informan al cliente el total a pagar por transferencia y el momento del pago antes de confirmarse.
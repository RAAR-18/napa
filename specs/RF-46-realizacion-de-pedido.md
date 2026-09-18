# Feature Specification: Realización de pedido

**Created**: 2026-09-18
**Requerimiento funcional**: RF-46
**Historias de usuario relacionadas**: HU-73, HU-74, HU-75, HU-77, HU-78

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

5. **Scenario**: Emprendimiento cerrado
   - **Given** el emprendimiento está cerrado
   - **When** intento hacer un pedido de entrega directa o de domicilio
   - **Then** el sistema no lo permite e informa que el emprendimiento está cerrado, y solo ofrece la reserva para el día siguiente

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

### User Story 3 - Elegir cómo pagaré el pedido (Priority: P1)

Como cliente, quiero seleccionar el método de pago (efectivo o transferencia, según el vendedor y la modalidad de entrega), para definir cómo pagaré mi pedido.

**Why this priority**: El método de pago determina cómo se cobra el pedido y qué confirmación debe registrar el vendedor, por lo que se clasifica como P1.

**Independent Test**: Puede probarse creando un pedido en cada combinación de vendedor y modalidad y verificando que solo se ofrecen los métodos permitidos.

**Acceptance Scenarios**:

1. **Scenario**: Métodos con un vendedor ambulante
   - **Given** elegí un vendedor ambulante, en entrega directa o reserva
   - **When** reviso los métodos de pago
   - **Then** el sistema ofrece efectivo y transferencia

2. **Scenario**: Métodos en una reserva con retiro en punto fijo
   - **Given** elegí reserva con un vendedor de punto fijo
   - **When** reviso los métodos de pago
   - **Then** el sistema ofrece efectivo y transferencia

3. **Scenario**: Método en un domicilio
   - **Given** elegí domicilio con un vendedor de punto fijo
   - **When** reviso los métodos de pago
   - **Then** el sistema ofrece únicamente transferencia

4. **Scenario**: Método sin seleccionar
   - **Given** no elegí ningún método de pago
   - **When** intento confirmar el pedido
   - **Then** el sistema me impide continuar e indica que debo elegir un método de pago

---

### User Story 4 - Confirmar el pedido (Priority: P1)

Como cliente, quiero realizar un pedido con los productos seleccionados, para comprarlos al vendedor y pagarlos con el método elegido.

**Why this priority**: Registra el pedido y lo pone en manos del vendedor para su respuesta, por lo que se clasifica como P1.

**Independent Test**: Puede probarse confirmando un pedido completo y verificando que queda en estado pendiente y el vendedor es notificado.

**Acceptance Scenarios**:

1. **Scenario**: Creación exitosa de un pedido
   - **Given** seleccioné productos, modalidad, método de pago y, cuando aplica, ubicación
   - **When** confirmo el pedido
   - **Then** el sistema lo registra en estado pendiente, muestra el total a pagar y notifica al vendedor (en la entrega directa, al vendedor ambulante)

2. **Scenario**: Pedido de domicilio con costo de envío
   - **Given** elegí domicilio con un vendedor de punto fijo
   - **When** reviso el resumen antes de confirmar
   - **Then** el sistema muestra el valor de los productos, el costo del domicilio y el total a pagar por transferencia

3. **Scenario**: Reserva para el día siguiente
   - **Given** elegí reserva
   - **When** confirmo el pedido
   - **Then** el sistema lo registra como reserva para el día siguiente, validando las cantidades contra la disponibilidad prevista (RF-47)

4. **Scenario**: Datos incompletos
   - **Given** falta seleccionar productos, modalidad, método de pago o ubicación (cuando aplica)
   - **When** intento confirmar el pedido
   - **Then** el sistema me impide continuar e indica los datos faltantes

### Edge Cases

- **¿Qué sucede si el cliente intenta confirmar un pedido sin ningún producto seleccionado?** El botón de confirmar permanece deshabilitado hasta que haya al menos un producto.
- **¿Cómo maneja el sistema un cambio de precio o de disponibilidad entre la selección y la confirmación?** Revalida al confirmar; si algo cambió, muestra la diferencia y pide al cliente confirmar de nuevo el pedido actualizado.
- **¿Qué ocurre si el emprendimiento está cerrado cuando el cliente quiere hacer el pedido?** No permite entrega directa ni domicilio y solo ofrece la reserva para el día siguiente (RF-30).
- **¿Qué ocurre si el cliente está demasiado lejos de un vendedor ambulante para una entrega directa?** El sistema muestra la distancia y crea el pedido; el vendedor decide si lo acepta o lo rechaza (RF-54).
- **¿Qué pasa si el cliente intenta usar un método de pago no permitido para la modalidad elegida (por ejemplo, efectivo en un domicilio)?** No puede: el sistema solo ofrece los métodos permitidos (en un domicilio, solo transferencia).
- **¿Qué pasa si el cliente pierde conexión después de armar el pedido y antes de confirmarlo (RNF28)?** El pedido no se registra hasta confirmarse; el borrador se conserva en el dispositivo y el cliente puede confirmarlo al recuperar la conexión.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente seleccionar uno o más productos de un emprendimiento con la cantidad deseada de cada uno; cada producto seleccionado es un ítem del pedido.
- **FR-002**: El sistema DEBE validar la disponibilidad de inventario de cada ítem antes de confirmar el pedido, salvo en las reservas, donde valida contra la disponibilidad prevista (RF-47).
- **FR-003**: El sistema DEBE ofrecer las modalidades de entrega según el tipo de vendedor: entrega directa y reserva para el vendedor ambulante; reserva y domicilio para el vendedor de punto fijo.
- **FR-004**: El sistema DEBE solicitar la ubicación de entrega en la entrega directa, la reserva con vendedor ambulante y el domicilio, y no solicitarla en la reserva con retiro en punto fijo.
- **FR-005**: El sistema DEBE programar las reservas para el día siguiente a la fecha del pedido.
- **FR-006**: El sistema DEBE ofrecer únicamente los métodos de pago permitidos: efectivo o transferencia con vendedor ambulante (entrega directa y reserva) y con vendedor de punto fijo en reserva con retiro, y solo transferencia con vendedor de punto fijo en domicilio.
- **FR-007**: El sistema DEBE exigir que el cliente elija un método de pago para confirmar el pedido.
- **FR-008**: El sistema DEBE calcular y mostrar el total del pedido, incluyendo el costo del domicilio cuando aplique, calculado según la distancia y sin ser inferior a la tarifa mínima (RF-45).
- **FR-009**: El sistema DEBE registrar el pedido en estado "pendiente" y notificar al vendedor.
- **FR-010**: El sistema DEBE impedir la confirmación del pedido si falta algún ítem, la modalidad, el método de pago o la ubicación cuando esta aplique.
- **FR-011**: El sistema DEBE impedir pedidos de entrega directa y de domicilio a un emprendimiento cerrado (RF-30), y permitir reservas.
- **FR-012**: El sistema NO DEBE procesar el pago: registra el método elegido y el pago se confirma después (RF-43).

### Key Entities

- **Pedido**: Compra del cliente a un emprendimiento; incluye ítems, modalidad de entrega, método de pago, ubicación de entrega cuando aplique, total, estado (pendiente, aceptado, rechazado, vencido, listo para recoger, en camino, entregado, cancelado) y fecha.
- **Ítem de pedido**: Producto con su cantidad y su precio al momento de la compra; un pedido tiene uno o varios ítems.
- **Modalidad de entrega**: Entrega directa (vendedor ambulante), reserva (ambulante: con entrega al cliente; punto fijo: con retiro) o domicilio (punto fijo).
- **Método de pago**: Efectivo o transferencia, según el vendedor y la modalidad; la plataforma solo lo registra.
- **Ubicación de entrega**: Punto indicado por el cliente donde se le entregará el pedido (punto B en un domicilio).

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Emprendimiento | Sí | Seleccionado por el cliente; para entrega directa y domicilio debe estar abierto. |
| Productos y cantidades (ítems) | Sí | Al menos un producto; cantidad mayor a 0 y no superior a la disponibilidad (o a la disponibilidad prevista en reservas). |
| Modalidad de entrega | Sí | Ambulante: entrega directa o reserva. Punto fijo: reserva o domicilio. |
| Ubicación de entrega | Condicional | Obligatoria en entrega directa, reserva con ambulante y domicilio: punto en el mapa o ubicación del dispositivo, con dirección de referencia. No aplica en reserva con retiro. |
| Nombre de quien recibe | Condicional | Solo en domicilio: texto de 2 a 60 caracteres; por defecto, el nombre del cliente. |
| Método de pago | Sí | Ambulante (ambas modalidades): efectivo o transferencia. Punto fijo: efectivo o transferencia en reserva; solo transferencia en domicilio. |
| Notas para el vendedor | No | Texto de hasta 200 caracteres. |

**Datos que asigna el sistema**

- Cliente (usuario en sesión), fecha y estado "pendiente".
- Precio unitario de cada ítem al momento de la compra y subtotal.
- Costo del domicilio (por distancia, no inferior a la tarifa mínima) y total.
- Fecha de entrega o retiro (día siguiente en reservas) y plazo de respuesta del vendedor.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente puede completar un pedido (ítems, modalidad, método de pago y ubicación) en menos de 3 minutos.
- **SC-002**: El 100% de los pedidos confirmados cuentan con ítems, modalidad, método de pago permitido y, cuando aplica, ubicación.
- **SC-003**: El 0% de los pedidos se confirma con productos sin disponibilidad de inventario o de disponibilidad prevista.
- **SC-004**: El 0% de los pedidos de domicilio se registra con un método de pago distinto a transferencia.
- **SC-005**: El vendedor recibe la notificación de un nuevo pedido pendiente en menos de 1 minuto.

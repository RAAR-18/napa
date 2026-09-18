# Feature Specification: Consulta de disponibilidad prevista para reservas

**Created**: 2026-09-18
**Requerimiento funcional**: RF-47
**Historias de usuario relacionadas**: HU-76

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reservar solo lo que el vendedor tendrá mañana (Priority: P1)

Como cliente, quiero consultar la disponibilidad prevista de los productos para el día siguiente, para reservar solo lo que el vendedor tendrá.

**Why this priority**: Una reserva solo es útil si el vendedor puede cumplirla; la disponibilidad prevista evita reservas imposibles y reduce el desperdicio de productos perecederos, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un vendedor que tenga historial de ventas, consultando la disponibilidad prevista de un producto y verificando que no se permite reservar más de lo previsto.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de la disponibilidad prevista
   - **Given** el vendedor tiene productos con historial de ventas
   - **When** consulto los productos para reservar
   - **Then** el sistema muestra por cada producto la cantidad prevista para el día siguiente, descontando lo ya comprometido en otras reservas

2. **Scenario**: Cantidad reservada superior a la prevista
   - **Given** la disponibilidad prevista de un producto es menor a la cantidad que quiero
   - **When** intento reservarla
   - **Then** el sistema me impide superar la cantidad prevista e indica el máximo que puedo reservar

3. **Scenario**: Producto sin historial suficiente
   - **Given** el producto no tiene historial suficiente para predecir
   - **When** consulto su disponibilidad
   - **Then** el sistema usa como referencia el inventario declarado por el vendedor y me avisa que la cantidad es aproximada

### Edge Cases

- **¿Qué sucede si dos clientes intentan reservar simultáneamente la última cantidad prevista (RNF08)?** El control de concurrencia (RNF08) asigna la cantidad a la primera confirmación; el segundo cliente ve la disponibilidad actualizada.
- **¿Cómo se comporta el sistema si el vendedor cambia su inventario después de que el cliente consultó la disponibilidad?** La cantidad se revalida al confirmar la reserva.
- **¿Qué ocurre con las reservas aceptadas si la predicción del día siguiente cambia?** Se respetan; la nueva predicción solo afecta a las reservas nuevas.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE mostrar al cliente, por cada producto, la disponibilidad prevista para el día siguiente al momento de reservar.
- **FR-002**: El sistema DEBE calcular la disponibilidad prevista a partir de la predicción de demanda (RF-60), el inventario y las reservas ya aceptadas.
- **FR-003**: El sistema DEBE impedir que la cantidad reservada de un producto supere su disponibilidad prevista.
- **FR-004**: El sistema DEBE indicar que la disponibilidad es aproximada cuando no haya historial suficiente para predecir.
- **FR-005**: El sistema DEBE controlar reservas simultáneas sobre la misma disponibilidad (RNF08).

### Key Entities

- **Disponibilidad prevista**: Cantidad estimada que un vendedor tendrá de un producto al día siguiente; atributos clave: producto, fecha, cantidad estimada, cantidad comprometida y nivel de confianza.
- **Reserva**: Pedido con modalidad de reserva para el día siguiente.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. El cliente selecciona los productos y cantidades de la reserva.

**Datos que se muestran o filtran**

- Por cada producto: disponibilidad prevista para el día siguiente y cantidad máxima reservable.
- Aviso cuando la cifra es aproximada por falta de historial.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La disponibilidad prevista se muestra al cliente en menos de 3 segundos.
- **SC-002**: El 0% de las reservas aceptadas supera la disponibilidad prevista al momento de crearse.
- **SC-003**: El 100% de las disponibilidades sin historial suficiente se identifican como aproximadas.

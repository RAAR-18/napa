# Feature Specification: Confirmación de entrega directa

**Created**: 2026-09-18
**Requerimiento funcional**: RF-38
**Historias de usuario relacionadas**: HU-63

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cerrar la entrega y confirmar el pago (Priority: P1)

Como vendedor ambulante, quiero confirmar la entrega del pedido, para registrar que fue entregado al cliente y que recibí el pago.

**Why this priority**: Cierra el ciclo de la entrega directa y de la reserva con entrega, y registra el pago, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un pedido en camino, confirmando la entrega y el pago y verificando que el pedido pasa a entregado y su pago queda confirmado.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de entrega con pago en efectivo
   - **Given** un pedido mío está en camino, ya se lo entregué al cliente y el método de pago es efectivo
   - **When** confirmo la entrega y el efectivo recibido
   - **Then** el sistema cambia el estado del pedido a entregado, registra el pago como confirmado (RF-43) y habilita las calificaciones

2. **Scenario**: Confirmación de entrega con pago por transferencia
   - **Given** un pedido mío está en camino, ya se lo entregué al cliente y el método de pago es transferencia
   - **When** confirmo la entrega e ingreso la referencia de la transferencia recibida
   - **Then** el sistema cambia el estado del pedido a entregado, registra el pago como confirmado y habilita las calificaciones

3. **Scenario**: Confirmación fuera de secuencia
   - **Given** el pedido no está en camino
   - **When** intento confirmar la entrega
   - **Then** el sistema rechaza la operación e indica el estado actual

4. **Scenario**: El cliente no recibe o no paga
   - **Given** el cliente no se encuentra o no paga el pedido
   - **When** informo el problema
   - **Then** el sistema me permite crear un reporte (RF-61) y mantiene el pedido sin entregar

### Edge Cases

- **¿Qué sucede si el cliente paga un monto distinto al total del pedido?** El vendedor registra el monto realmente recibido; si difiere del total, el pago queda marcado con diferencia para revisión.
- **¿Cómo se maneja una entrega parcial en la que el vendedor solo pudo llevar algunos ítems?** No se admiten entregas parciales: si no puede entregar todos los ítems, lo informa al cliente y crea un reporte; el pedido no se confirma como entregado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor ambulante confirmar la entrega únicamente de pedidos en estado "en camino".
- **FR-002**: El sistema DEBE solicitar al vendedor la confirmación del pago recibido, con el método elegido por el cliente, al registrar la entrega (RF-43).
- **FR-003**: El sistema DEBE cambiar el estado del pedido a "entregado" y habilitar las calificaciones entre las partes (RF-01).
- **FR-004**: El sistema DEBE impedir que la entrega de un mismo pedido se confirme más de una vez.

### Key Entities

- **Pedido**: Transición de esta funcionalidad: en camino → entregado.
- **Pago**: Registro asociado al pedido, con su método (efectivo o transferencia), confirmado junto con la entrega.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Pago recibido | Sí | Confirmación de que recibió el pago del pedido. |
| Monto recibido | Sí | Entero en pesos colombianos (COP); se propone el total del pedido y puede ajustarse. |
| Referencia de la transferencia | Condicional | Obligatoria si el método de pago es transferencia; de 4 a 40 caracteres. |

**Datos que asigna el sistema**

- Estado "entregado", fecha y hora.
- Pago confirmado con su método.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede confirmar la entrega y el pago en menos de 15 segundos.
- **SC-002**: El 100% de los pedidos entregados por vendedores ambulantes tienen su pago confirmado.

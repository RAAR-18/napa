# Feature Specification: Confirmación de retiro de la reserva

**Created**: 2026-09-18
**Requerimiento funcional**: RF-66
**Historias de usuario relacionadas**: HU-100

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cerrar la reserva cuando el cliente la recoge (Priority: P1)

Como vendedor de punto fijo, quiero confirmar el retiro de la reserva, para registrar que el cliente recogió su pedido y que recibí el pago.

**Why this priority**: Cierra el ciclo de la reserva con retiro y registra el pago, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con una reserva lista para recoger, confirmando el retiro y el pago y verificando que el pedido pasa a entregado.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de retiro con pago en efectivo
   - **Given** una reserva mía está lista para recoger, el cliente la retira y el método de pago es efectivo
   - **When** confirmo el retiro y el efectivo recibido
   - **Then** el sistema cambia el estado del pedido a entregado, registra el pago como confirmado (RF-43) y habilita las calificaciones

2. **Scenario**: Confirmación de retiro con pago por transferencia
   - **Given** una reserva mía está lista para recoger, el cliente la retira y el método de pago es transferencia
   - **When** confirmo el retiro e ingreso la referencia de la transferencia recibida
   - **Then** el sistema cambia el estado del pedido a entregado, registra el pago como confirmado y habilita las calificaciones

3. **Scenario**: Reserva que aún no está lista
   - **Given** la reserva no está en estado listo para recoger
   - **When** intento confirmar su retiro
   - **Then** el sistema rechaza la operación e indica el estado actual

### Edge Cases

- **¿Qué sucede si el cliente retira solo una parte de la reserva?** No se admiten retiros parciales: si no retira todo, el vendedor lo reporta y la reserva no se confirma como entregada.
- **¿Cómo se maneja una reserva que el cliente nunca retira? ¿Se cancela y el producto vuelve al inventario?** Al cierre del día programado la reserva se cancela y las unidades regresan al inventario.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor de punto fijo confirmar el retiro de una reserva en estado "listo para recoger".
- **FR-002**: El sistema DEBE solicitar la confirmación del pago recibido, con el método elegido por el cliente, al registrar el retiro (RF-43).
- **FR-003**: El sistema DEBE cambiar el estado del pedido a "entregado" y habilitar las calificaciones (RF-01).

### Key Entities

- **Reserva**: Pedido con modalidad de reserva; transición de esta funcionalidad: listo para recoger → entregado.
- **Pago**: Registro asociado al pedido, con su método (efectivo o transferencia), confirmado junto con el retiro.

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

- **SC-001**: El vendedor puede confirmar el retiro y el pago en menos de 15 segundos.
- **SC-002**: El 100% de las reservas retiradas tienen su pago confirmado.

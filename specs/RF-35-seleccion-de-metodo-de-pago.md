# Feature Specification: Selección de método de pago

**Created**: 2026-09-11
**Requerimiento funcional**: RF-35
**Historias de usuario relacionadas**: HU-56

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Elegir cómo pagar una compra (Priority: P1)

Como cliente, quiero seleccionar un método de pago, para elegir cómo pagar mis compras.

**Why this priority**: Sin la posibilidad de definir un método de pago no puede completarse ninguna compra en la plataforma; es un habilitador directo del flujo transaccional, por lo que se clasifica como P1.

**Independent Test**: Puede probarse de forma independiente mostrando al cliente los métodos de pago disponibles, permitiéndole seleccionar uno y verificando que la selección queda registrada y disponible para su uso en un pedido.

**Acceptance Scenarios**:

1. **Scenario**: Selección exitosa de un método de pago disponible
   - **Given** el cliente tiene acceso a la lista de métodos de pago habilitados por la plataforma
   - **When** el cliente selecciona uno de los métodos disponibles
   - **Then** el sistema registra el método seleccionado y lo asocia a la compra en curso

2. **Scenario**: Cambio de método de pago antes de confirmar
   - **Given** el cliente ya había seleccionado un método de pago
   - **When** el cliente elige un método distinto antes de confirmar la compra
   - **Then** el sistema actualiza el método de pago asociado a la compra en curso

### Edge Cases

- ¿Qué sucede si el cliente no selecciona ningún método de pago e intenta continuar?
- ¿Cómo maneja el sistema un método de pago que fue deshabilitado por el administrador mientras el cliente lo tenía preseleccionado?
- ¿Qué ocurre si el cliente selecciona un método de pago que requiere datos adicionales (por ejemplo, una tarjeta) y no los ha registrado previamente?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE mostrar al cliente los métodos de pago disponibles y habilitados en la plataforma.
- **FR-002**: El sistema DEBE permitir al cliente seleccionar un único método de pago para una compra.
- **FR-003**: El sistema DEBE permitir al cliente cambiar el método de pago seleccionado antes de confirmar la compra.
- **FR-004**: El sistema DEBE validar que exista un método de pago seleccionado antes de permitir continuar con la compra.

### Key Entities

- **Método de pago**: Representa una forma de pago disponible en la plataforma (por ejemplo, efectivo, tarjeta, transferencia); atributos clave: tipo, estado (habilitado/deshabilitado).
- **Cliente**: Usuario que selecciona el método de pago para sus compras.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente puede seleccionar un método de pago en menos de 15 segundos.
- **SC-002**: El 100% de las compras confirmadas cuentan con un método de pago válido asociado.
- **SC-003**: El sistema impide el 100% de los intentos de confirmar una compra sin método de pago seleccionado.

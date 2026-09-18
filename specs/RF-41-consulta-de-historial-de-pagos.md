# Feature Specification: Consulta de historial de pagos

**Created**: 2026-09-18
**Requerimiento funcional**: RF-41
**Historias de usuario relacionadas**: HU-66, HU-68, HU-70

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente consulta sus pagos (Priority: P2)

Como cliente, quiero consultar mi historial de pagos, para revisar los pagos que he realizado.

**Why this priority**: Da transparencia al cliente sobre sus compras, pero no bloquea el flujo transaccional, por lo que se clasifica como P2.

**Independent Test**: Puede probarse ingresando al historial del cliente y verificando que solo se listan pagos de sus propios pedidos y domicilios.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del historial del cliente
   - **Given** el cliente tiene pagos registrados
   - **When** accede a su historial de pagos
   - **Then** el sistema muestra la lista con fecha, monto, método de pago (efectivo o transferencia), pedido o domicilio asociado y estado (pendiente de confirmación o confirmado)

2. **Scenario**: Cliente sin pagos
   - **Given** el cliente no ha realizado compras
   - **When** accede a su historial
   - **Then** el sistema muestra un listado vacío indicando que no existen pagos registrados

---

### User Story 2 - Vendedor consulta lo cobrado en sus pedidos (Priority: P2)

Como vendedor, quiero consultar el historial de pagos, para revisar los cobros relacionados con mis pedidos.

**Why this priority**: Permite al vendedor (ambulante o de punto fijo) conciliar su caja diaria y sus transferencias recibidas, sin ser crítico para el flujo de compra, por lo que se clasifica como P2.

**Independent Test**: Puede probarse ingresando al historial del vendedor y verificando que solo aparecen pagos de pedidos de su emprendimiento.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del historial del vendedor
   - **Given** el vendedor tiene pedidos con pagos registrados
   - **When** accede a su historial de pagos
   - **Then** el sistema muestra los cobros con fecha, monto, método de pago, modalidad de entrega del pedido y estado

---

### User Story 3 - Domiciliario consulta lo cobrado por sus servicios (Priority: P2)

Como domiciliario, quiero consultar el historial de pagos, para revisar lo que he cobrado por mis servicios.

**Why this priority**: Permite al domiciliario conocer sus ingresos por domicilio, sin bloquear otros flujos, por lo que se clasifica como P2.

**Independent Test**: Puede probarse ingresando al historial del domiciliario y verificando que solo aparecen pagos de domicilios que él realizó.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del historial del domiciliario
   - **Given** el domiciliario realizó domicilios con pagos registrados
   - **When** accede a su historial de pagos
   - **Then** el sistema muestra los pagos de domicilio con fecha, monto (tarifa o valor de la oferta aceptada), método de pago y estado

### Edge Cases

- **¿Qué sucede si un usuario intenta consultar pagos que no le pertenecen?** El acceso se deniega: cada usuario solo ve sus propios pagos.
- **¿Cómo se muestra un pago que sigue pendiente de confirmación tras haberse entregado el pedido?** Se muestra como "pendiente de confirmación" con su antigüedad, y se recuerda a quien debe confirmarlo (vendedor o domiciliario).
- **¿Qué ocurre si el historial tiene un volumen alto de registros? ¿Se pagina o filtra por fecha?** Se pagina de a 20 y se puede filtrar por fecha, método y estado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar únicamente los pagos asociados a sus propios pedidos y domicilios.
- **FR-002**: El sistema DEBE permitir al vendedor consultar únicamente los pagos asociados a los pedidos de su emprendimiento.
- **FR-003**: El sistema DEBE permitir al domiciliario consultar únicamente los pagos de los domicilios que ha realizado.
- **FR-004**: El sistema DEBE mostrar en cada registro la fecha, el monto, el método de pago, la entidad relacionada (pedido o domicilio) y el estado del pago (pendiente de confirmación o confirmado).
- **FR-005**: El sistema DEBE impedir que un usuario acceda al historial de pagos de otro usuario.

### Key Entities

- **Pago**: Registro de un pago; atributos clave: monto, fecha, método (efectivo o transferencia), estado (pendiente de confirmación, confirmado), tipo (pedido o domicilio) y usuario asociado. La plataforma no procesa el dinero: solo registra el método elegido y la confirmación.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. Filtros opcionales: fecha, método de pago y estado.

**Datos que se muestran o filtran**

- Fecha, monto, método de pago, pedido o domicilio asociado y estado; paginado de a 20.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los registros mostrados en el historial de un usuario corresponden únicamente a sus propias transacciones.
- **SC-002**: El usuario puede acceder a su historial de pagos en menos de 3 segundos.
- **SC-003**: El 0% de los intentos de acceso a pagos de otro usuario es exitoso.

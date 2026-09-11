# Feature Specification: Consulta de historial de pagos

**Created**: 2026-09-11
**Requerimiento funcional**: RF-36
**Historias de usuario relacionadas**: HU-57, HU-59, HU-61

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente consulta su historial de pagos (Priority: P2)

Como cliente, quiero consultar mi historial de pagos, para revisar las transacciones realizadas.

**Why this priority**: Da transparencia y confianza al cliente sobre sus compras, pero no bloquea el flujo transaccional principal; se clasifica como P2.

**Independent Test**: Puede probarse ingresando al historial de pagos del cliente y verificando que se listan únicamente las transacciones asociadas a sus propios pedidos.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa del historial de pagos del cliente
   - **Given** el cliente ha realizado una o más compras con pagos registrados
   - **When** el cliente accede a su historial de pagos
   - **Then** el sistema muestra la lista de pagos realizados con fecha, monto, pedido asociado y estado

2. **Scenario**: Cliente sin pagos registrados
   - **Given** el cliente no ha realizado ninguna compra
   - **When** el cliente accede a su historial de pagos
   - **Then** el sistema muestra un listado vacío indicando que no existen pagos registrados

---

### User Story 2 - Vendedor consulta el historial de pagos de sus pedidos (Priority: P2)

Como vendedor, quiero consultar el historial de pagos, para revisar las transacciones relacionadas con mis pedidos.

**Why this priority**: Permite al vendedor conciliar sus ventas y pagos recibidos; es relevante para su operación pero no crítico para el flujo de compra en curso.

**Independent Test**: Puede probarse ingresando al historial de pagos del vendedor y verificando que solo se muestran los pagos correspondientes a pedidos de su emprendimiento.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa del historial de pagos del vendedor
   - **Given** el vendedor tiene pedidos con pagos registrados
   - **When** el vendedor accede a su historial de pagos
   - **Then** el sistema muestra la lista de pagos asociados a sus pedidos, con fecha, monto y estado

---

### User Story 3 - Domiciliario consulta el historial de pagos de sus servicios (Priority: P2)

Como domiciliario, quiero consultar el historial de pagos, para revisar los pagos asociados a mis servicios.

**Why this priority**: Permite al domiciliario verificar los pagos recibidos por sus entregas; relevante para su operación diaria, sin bloquear otros flujos.

**Independent Test**: Puede probarse ingresando al historial de pagos del domiciliario y verificando que solo se listan los pagos de domicilios que él realizó.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa del historial de pagos del domiciliario
   - **Given** el domiciliario ha realizado uno o más servicios de entrega con pagos registrados
   - **When** el domiciliario accede a su historial de pagos
   - **Then** el sistema muestra la lista de pagos de domicilio con fecha, monto y estado

### Edge Cases

- ¿Qué sucede si un usuario intenta consultar pagos que no le pertenecen (de otro cliente, vendedor o domiciliario)?
- ¿Cómo se muestra un pago que quedó en estado pendiente o rechazado dentro del historial?
- ¿Qué ocurre si el historial tiene un volumen alto de registros? ¿Se pagina o filtra por fecha?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar únicamente los pagos asociados a sus propios pedidos.
- **FR-002**: El sistema DEBE permitir al vendedor consultar únicamente los pagos asociados a los pedidos de su emprendimiento.
- **FR-003**: El sistema DEBE permitir al domiciliario consultar únicamente los pagos asociados a los servicios de entrega que ha realizado.
- **FR-004**: El sistema DEBE mostrar en cada registro del historial la fecha, el monto, la entidad relacionada (pedido o domicilio) y el estado del pago.
- **FR-005**: El sistema DEBE impedir que un usuario acceda al historial de pagos de otro usuario.

### Key Entities

- **Pago**: Representa una transacción registrada en la plataforma; atributos clave: monto, fecha, estado (pendiente, confirmado, rechazado), tipo (pedido o domicilio), usuario asociado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los registros mostrados en el historial de un usuario corresponden únicamente a sus propias transacciones.
- **SC-002**: El usuario puede acceder a su historial de pagos en menos de 3 segundos.
- **SC-003**: El 0% de los intentos de acceso a pagos de otro usuario es exitoso.

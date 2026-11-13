# Feature Specification: Consulta de historial de pagos

**Created**: 2026-09-18
<<<<<<< HEAD:specs/RF-41-consulta-de-historial-de-pagos.md
**Requerimiento funcional**: RF-41
**Historias de usuario relacionadas**: HU-66, HU-68, HU-70
=======
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-40
**Historias de usuario relacionadas**: HU-65, HU-67, HU-69
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-40-consulta-de-historial-de-pagos.md

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente consulta sus pagos (Priority: P2)

<<<<<<< HEAD:specs/RF-41-consulta-de-historial-de-pagos.md
Como cliente, quiero consultar mi historial de pagos, para revisar los pagos que he realizado.
=======
Como cliente, quiero consultar mi historial de pagos, para revisar los pagos que he realizado, en efectivo o por transferencia.
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-40-consulta-de-historial-de-pagos.md

**Why this priority**: Da transparencia al cliente sobre sus compras, pero no bloquea el flujo transaccional, por lo que se clasifica como P2.

**Independent Test**: Puede probarse ingresando al historial del cliente y verificando que solo se listan pagos de sus propios pedidos, con su medio correspondiente.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del historial del cliente
   - **Given** el cliente tiene pagos registrados
   - **When** accede a su historial de pagos
<<<<<<< HEAD:specs/RF-41-consulta-de-historial-de-pagos.md
   - **Then** el sistema muestra la lista con fecha, monto, método de pago (efectivo o transferencia), pedido o domicilio asociado y estado (pendiente de confirmación o confirmado)
=======
   - **Then** el sistema muestra la lista con fecha, monto, medio (efectivo o cuenta bancaria), pedido asociado y estado (pendiente de confirmación o confirmado); en un domicilio, el monto es el total de productos más tarifa
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-40-consulta-de-historial-de-pagos.md

2. **Scenario**: Cliente sin pagos
   - **Given** el cliente no ha realizado compras
   - **When** accede a su historial
   - **Then** el sistema muestra un listado vacío indicando que no existen pagos registrados

---

### User Story 2 - Vendedor consulta lo cobrado y pagado en sus pedidos (Priority: P2)

<<<<<<< HEAD:specs/RF-41-consulta-de-historial-de-pagos.md
Como vendedor, quiero consultar el historial de pagos, para revisar los cobros relacionados con mis pedidos.
=======
Como vendedor, quiero consultar el historial de pagos, para revisar los cobros en efectivo y digitales relacionados con mis pedidos y, en domicilios, las tarifas pagadas a los domiciliarios.
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-40-consulta-de-historial-de-pagos.md

**Why this priority**: Permite al vendedor (ambulante o de punto fijo) conciliar su caja diaria y sus transferencias recibidas, sin ser crítico para el flujo de compra, por lo que se clasifica como P2.

**Independent Test**: Puede probarse ingresando al historial del vendedor y verificando que solo aparecen pagos de pedidos de su emprendimiento, con su medio correspondiente.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del historial del vendedor
   - **Given** el vendedor tiene pedidos con pagos registrados
   - **When** accede a su historial de pagos
<<<<<<< HEAD:specs/RF-41-consulta-de-historial-de-pagos.md
   - **Then** el sistema muestra los cobros con fecha, monto, método de pago, modalidad de entrega del pedido y estado
=======
   - **Then** el sistema muestra los cobros con fecha, monto, medio, modalidad de entrega del pedido y estado, e incluye, en los domicilios, el pago de la tarifa al domiciliario
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-40-consulta-de-historial-de-pagos.md

---

### User Story 3 - Domiciliario consulta lo cobrado por sus servicios (Priority: P2)

Como domiciliario, quiero consultar el historial de pagos, para revisar las tarifas que he recibido por mis servicios.

**Why this priority**: Permite al domiciliario conocer sus ingresos por domicilio, sin bloquear otros flujos, por lo que se clasifica como P2.

**Independent Test**: Puede probarse ingresando al historial del domiciliario y verificando que solo aparecen pagos de tarifa de domicilios que él realizó.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del historial del domiciliario
   - **Given** el domiciliario realizó domicilios con pagos de tarifa registrados
   - **When** accede a su historial de pagos
<<<<<<< HEAD:specs/RF-41-consulta-de-historial-de-pagos.md
   - **Then** el sistema muestra los pagos de domicilio con fecha, monto (tarifa o valor de la oferta aceptada), método de pago y estado

### Edge Cases

- **¿Qué sucede si un usuario intenta consultar pagos que no le pertenecen?** El acceso se deniega: cada usuario solo ve sus propios pagos.
- **¿Cómo se muestra un pago que sigue pendiente de confirmación tras haberse entregado el pedido?** Se muestra como "pendiente de confirmación" con su antigüedad, y se recuerda a quien debe confirmarlo (vendedor o domiciliario).
- **¿Qué ocurre si el historial tiene un volumen alto de registros? ¿Se pagina o filtra por fecha?** Se pagina de a 20 y se puede filtrar por fecha, método y estado.
=======
   - **Then** el sistema muestra los pagos de tarifa (siempre por cuenta bancaria) con fecha, monto (la tarifa publicada) y estado

### Edge Cases

- ¿Qué sucede si un usuario intenta consultar pagos que no le pertenecen?
- ¿Cómo se muestra un pago que sigue pendiente de confirmación tras haberse entregado el pedido?
- ¿Qué ocurre si el historial tiene un volumen alto de registros? ¿Se pagina o filtra por fecha o por medio?
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-40-consulta-de-historial-de-pagos.md

## Requirements *(mandatory)*

### Functional Requirements

<<<<<<< HEAD:specs/RF-41-consulta-de-historial-de-pagos.md
- **FR-001**: El sistema DEBE permitir al cliente consultar únicamente los pagos asociados a sus propios pedidos y domicilios.
- **FR-002**: El sistema DEBE permitir al vendedor consultar únicamente los pagos asociados a los pedidos de su emprendimiento.
- **FR-003**: El sistema DEBE permitir al domiciliario consultar únicamente los pagos de los domicilios que ha realizado.
- **FR-004**: El sistema DEBE mostrar en cada registro la fecha, el monto, el método de pago, la entidad relacionada (pedido o domicilio) y el estado del pago (pendiente de confirmación o confirmado).
=======
- **FR-001**: El sistema DEBE permitir al cliente consultar únicamente los pagos asociados a sus propios pedidos.
- **FR-002**: El sistema DEBE permitir al vendedor consultar únicamente los pagos asociados a los pedidos de su emprendimiento y las tarifas que pagó a domiciliarios.
- **FR-003**: El sistema DEBE permitir al domiciliario consultar únicamente los pagos de tarifa de los domicilios que ha realizado.
- **FR-004**: El sistema DEBE mostrar en cada registro la fecha, el monto, el medio (efectivo o cuenta bancaria), la entidad relacionada (pedido o domicilio) y el estado del pago.
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-40-consulta-de-historial-de-pagos.md
- **FR-005**: El sistema DEBE impedir que un usuario acceda al historial de pagos de otro usuario.
- **FR-006**: El sistema DEBE permitir filtrar el historial por medio de pago.

### Key Entities

<<<<<<< HEAD:specs/RF-41-consulta-de-historial-de-pagos.md
- **Pago**: Registro de un pago; atributos clave: monto, fecha, método (efectivo o transferencia), estado (pendiente de confirmación, confirmado), tipo (pedido o domicilio) y usuario asociado. La plataforma no procesa el dinero: solo registra el método elegido y la confirmación.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. Filtros opcionales: fecha, método de pago y estado.

**Datos que se muestran o filtran**

- Fecha, monto, método de pago, pedido o domicilio asociado y estado; paginado de a 20.
=======
- **Pago**: Registro de un pago; atributos clave: monto, fecha, medio (efectivo o cuenta bancaria), estado, tipo (pago del pedido o pago de la tarifa del domicilio) y usuario asociado. La plataforma no procesa el dinero directamente: solo registra su confirmación.
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-40-consulta-de-historial-de-pagos.md

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los registros mostrados en el historial de un usuario corresponden únicamente a sus propias transacciones.
- **SC-002**: El usuario puede acceder a su historial de pagos en menos de 3 segundos.
- **SC-003**: El 0% de los intentos de acceso a pagos de otro usuario es exitoso.
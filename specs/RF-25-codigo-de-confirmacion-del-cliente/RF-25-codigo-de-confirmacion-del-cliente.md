# Feature Specification: Código de confirmación del cliente

**Created**: 2026-09-18
**Actualizado**: 2026-10-14
**Requerimiento funcional**: RF-25
**Historias de usuario relacionadas**: HU-50
**Relacionado con**: RF-24 (el domiciliario lo ingresa), RF-60

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Entregar mi código al recibir el pedido (Priority: P1)

Como cliente, quiero ver mi código de confirmación, para entregárselo al domiciliario cuando llegue mi pedido.

**Why this priority**: Sin el código el domiciliario no puede cerrar la entrega; habilita el cierre del domicilio, el pago de la tarifa y las calificaciones, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un domicilio asignado, consultando el código como cliente y verificando que ningún otro usuario puede verlo.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del código de confirmación
   - **Given** mi domicilio fue asignado a un domiciliario
   - **When** consulto el detalle de mi domicilio
   - **Then** el sistema me muestra mi código de confirmación, para decírselo al domiciliario en el momento de la entrega

2. **Scenario**: Código aún no disponible
   - **Given** mi domicilio todavía no fue asignado
   - **When** intento ver el código
   - **Then** el sistema indica que estará disponible cuando un domiciliario lo acepte

3. **Scenario**: Desacuerdo con la entrega
   - **Given** el domiciliario dice haber llegado pero no recibí mi pedido, o no quiero darle el código
   - **When** informo el problema
   - **Then** el sistema mantiene el domicilio en camino, sin entregar, y me permite crear un reporte (RF-60)

4. **Scenario**: Acceso de otro usuario
   - **Given** el domicilio pertenece al pedido de otro cliente
   - **When** intento ver su código
   - **Then** el sistema deniega el acceso

### Edge Cases

- ¿Puede el cliente regenerar su código si sospecha que lo compartió por error?
- ¿Cómo ve el código un cliente sin conexión en el momento de la entrega?
- ¿Qué ocurre si el cliente nunca entrega su código? (el domicilio sigue en camino; no hay vencimiento automático en el MVP)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE mostrar al cliente el código de confirmación de su domicilio desde que este queda asignado a un domiciliario hasta que se entregue o se cancele.
- **FR-002**: El sistema DEBE restringir el código al cliente del pedido; el domiciliario nunca lo recibe del sistema.
- **FR-003**: El sistema DEBE permitir al cliente informar un desacuerdo con la entrega mediante un reporte (RF-60), manteniendo el domicilio sin entregar.

### Key Entities

- **Código de confirmación**: Valor asociado al domicilio, visible solo para el cliente (RF-25) y exigido al domiciliario para cerrar la entrega (RF-24).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente puede ver su código en menos de 3 segundos desde el detalle de su domicilio.
- **SC-002**: El 0% de los usuarios distintos al cliente del pedido puede ver el código.
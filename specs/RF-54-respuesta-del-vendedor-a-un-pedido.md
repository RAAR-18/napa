# Feature Specification: Respuesta del vendedor a un pedido

**Created**: 2026-09-18
**Requerimiento funcional**: RF-54
**Historias de usuario relacionadas**: HU-85, HU-86

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Aceptar un pedido (Priority: P1)

Como vendedor, quiero aceptar un pedido, para comprometerme a atenderlo según su modalidad de entrega.

**Why this priority**: Sin la aceptación del vendedor ningún pedido avanza hacia su entrega, retiro o domicilio, por lo que se clasifica como P1.

**Independent Test**: Puede probarse aceptando un pedido pendiente de cada modalidad y verificando que pasa a aceptado y habilita el siguiente paso propio de la modalidad.

**Acceptance Scenarios**:

1. **Scenario**: Aceptación de una entrega directa
   - **Given** soy vendedor ambulante y recibí un pedido de entrega directa
   - **When** lo acepto
   - **Then** el sistema cambia el pedido a aceptado, notifica al cliente y me habilita la ubicación de entrega (RF-36)

2. **Scenario**: Aceptación de una reserva
   - **Given** recibí una reserva para el día siguiente
   - **When** la acepto
   - **Then** el sistema cambia el pedido a aceptado, descuenta las cantidades de la disponibilidad prevista y notifica al cliente

3. **Scenario**: Aceptación de un pedido de domicilio
   - **Given** soy vendedor de punto fijo y recibí un pedido de domicilio
   - **When** lo acepto
   - **Then** el sistema cambia el pedido a aceptado, notifica al cliente y me habilita a publicar el domicilio (RF-14)

4. **Scenario**: Inventario insuficiente al aceptar
   - **Given** el inventario o la disponibilidad prevista ya no cubre el pedido
   - **When** intento aceptarlo
   - **Then** el sistema me advierte de la insuficiencia y no lo acepta hasta que ajuste el inventario o lo rechace

---

### User Story 2 - Rechazar un pedido (Priority: P1)

Como vendedor, quiero rechazar un pedido, para indicar que no puedo atenderlo.

**Why this priority**: Permite al vendedor declinar un pedido que no puede cumplir (por ejemplo, un cliente muy lejano), evitando falsas expectativas, por lo que se clasifica como P1.

**Independent Test**: Puede probarse rechazando un pedido pendiente y verificando que pasa a rechazado y el cliente es notificado.

**Acceptance Scenarios**:

1. **Scenario**: Rechazo de un pedido pendiente
   - **Given** tengo un pedido pendiente que no puedo atender
   - **When** lo rechazo
   - **Then** el sistema cambia el pedido a rechazado, libera las cantidades comprometidas y notifica al cliente

2. **Scenario**: Rechazo de un pedido ya aceptado
   - **Given** el pedido ya fue aceptado y está en curso
   - **When** intento rechazarlo
   - **Then** el sistema rechaza la operación e indica que el pedido ya no está pendiente

### Edge Cases

- **¿Qué sucede si el vendedor ambulante no responde a un pedido de entrega directa en un tiempo razonable? ¿Vence el pedido?** El pedido vence sin respuesta (10 minutos en entrega directa, 30 en domicilio, 12 horas en reserva), pasa a "vencido" y se notifica al cliente.
- **¿Puede el vendedor indicar un motivo al rechazar un pedido?** Sí, de forma opcional: elige un motivo (sin existencias, muy lejos, cerrado u otro) y puede agregar un texto que ve el cliente.
- **¿Qué ocurre si dos pedidos pendientes compiten por la misma cantidad de un producto?** Se atienden por orden de aceptación; al aceptar el segundo, el sistema advierte que el inventario ya no alcanza.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor aceptar o rechazar únicamente pedidos de su emprendimiento que estén en estado "pendiente".
- **FR-002**: El sistema DEBE cambiar el estado del pedido a "aceptado" o "rechazado" y notificar al cliente.
- **FR-003**: El sistema DEBE validar, al aceptar, que el inventario (o la disponibilidad prevista, en reservas) cubra el pedido.
- **FR-004**: El sistema DEBE habilitar, tras la aceptación, el siguiente paso propio de la modalidad: ubicación de entrega (entrega directa o reserva ambulante), marcar como lista (reserva en punto fijo) o publicar el domicilio (domicilio).
- **FR-005**: El sistema DEBE liberar las cantidades comprometidas cuando un pedido sea rechazado.

### Key Entities

- **Pedido**: Transiciones de esta funcionalidad: pendiente → aceptado y pendiente → rechazado.
- **Vendedor**: Ambulante o de punto fijo; decide sobre los pedidos recibidos.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Decisión | Sí | Aceptar o rechazar el pedido pendiente. |
| Motivo del rechazo | No | Uno de: sin existencias, muy lejos, cerrado u otro. |
| Comentario para el cliente | No | Texto de hasta 200 caracteres, solo al rechazar. |

**Datos que asigna el sistema**

- Estado "aceptado" o "rechazado", fecha y hora.
- Descuento de cantidades comprometidas (o liberación al rechazar).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede aceptar o rechazar un pedido en menos de 10 segundos.
- **SC-002**: El cliente recibe la respuesta del vendedor en menos de 1 minuto.
- **SC-003**: El 0% de los pedidos se acepta sin cobertura de inventario o de disponibilidad prevista.

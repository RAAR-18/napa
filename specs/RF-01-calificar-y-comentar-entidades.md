# Feature Specification: Calificar y comentar entidades

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-01
**Historias de usuario relacionadas**: HU-01, HU-02, HU-03, HU-04, HU-05, HU-06, HU-07

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente califica emprendimiento y producto (Priority: P1)

Como cliente, quiero calificar y comentar un emprendimiento y los productos que compré, para expresar mi experiencia y ayudar a otros clientes a decidir.

**Why this priority**: Las calificaciones de clientes son la principal fuente de confianza de la plataforma; sin ellas, ningún otro flujo de reputación (RF-02, RF-03) tiene datos que mostrar.

**Independent Test**: Puede probarse completando un pedido, calificando el emprendimiento y un producto del pedido, y verificando que la calificación quede asociada a ambas entidades.

**Acceptance Scenarios**:

1. **Scenario**: Calificación de emprendimiento tras pedido finalizado
   - **Given** tengo un pedido finalizado con un emprendimiento
   - **When** asigno una calificación y un comentario a ese emprendimiento
   - **Then** el sistema registra la calificación y la asocia al emprendimiento y a mi cuenta

2. **Scenario**: Calificación de un producto comprado
   - **Given** compré un producto dentro de un pedido finalizado
   - **When** asigno una calificación y comentario a ese producto
   - **Then** el sistema registra la calificación y la asocia al producto

---

### User Story 2 - Cliente califica al domiciliario (Priority: P2)

Como cliente, quiero calificar y comentar al domiciliario que realizó mi entrega, para valorar la calidad del servicio recibido.

**Why this priority**: Complementa la reputación del domiciliario, pero depende de que ya exista un domicilio entregado, por lo que es secundaria frente a la calificación del emprendimiento/producto.

**Independent Test**: Puede probarse confirmando la entrega de un domicilio y calificando al domiciliario asignado.

**Acceptance Scenarios**:

1. **Scenario**: Calificación de domiciliario tras entrega confirmada
   - **Given** un domicilio asociado a mi pedido fue confirmado como entregado
   - **When** asigno una calificación y comentario al domiciliario
   - **Then** el sistema registra la calificación asociada al domiciliario

---

### User Story 3 - Vendedor de punto fijo califica al domiciliario (Priority: P2)

Como vendedor de punto fijo, quiero calificar y comentar al domiciliario que gestionó una entrega, para valorar su servicio.

**Why this priority**: Refuerza la reputación bidireccional del ecosistema de domicilios, pero no bloquea el flujo principal de compra.

**Independent Test**: Puede probarse completando un domicilio y verificando que el vendedor de punto fijo pueda calificar al domiciliario que lo realizó.

**Acceptance Scenarios**:

1. **Scenario**: Vendedor califica al domiciliario
   - **Given** un domicilio de mi pedido fue finalizado
   - **When** asigno una calificación y comentario al domiciliario que lo realizó
   - **Then** el sistema registra la calificación asociada al domiciliario

---

### User Story 4 - Vendedor califica al cliente (Priority: P2)

Como vendedor (ambulante o de punto fijo), quiero calificar y comentar al cliente con quien concreté una venta, para valorar mi experiencia durante la venta o la entrega.

**Why this priority**: Completa la reputación de los clientes frente a los vendedores, pero no bloquea el flujo principal de compra.

**Independent Test**: Puede probarse entregando un pedido (entrega directa, retiro de reserva o domicilio) y verificando que el vendedor pueda calificar al cliente.

**Acceptance Scenarios**:

1. **Scenario**: Vendedor califica al cliente
   - **Given** un pedido mío fue entregado a un cliente (por entrega directa, retiro de reserva o domicilio)
   - **When** asigno una calificación y comentario a ese cliente
   - **Then** el sistema registra la calificación asociada al cliente

---

### User Story 5 - Domiciliario califica al emprendimiento y al cliente (Priority: P2)

Como domiciliario, quiero calificar y comentar al emprendimiento y al cliente con quienes interactué, para registrar mi experiencia del servicio.

**Why this priority**: Refuerza la reputación bidireccional del ecosistema de domicilios, pero no bloquea el flujo principal de compra.

**Independent Test**: Puede probarse completando un domicilio y verificando que el domiciliario pueda calificar al emprendimiento y al cliente.

**Acceptance Scenarios**:

1. **Scenario**: Domiciliario califica al emprendimiento y al cliente
   - **Given** entregué un domicilio recogido en un emprendimiento y destinado a un cliente
   - **When** asigno calificación y comentario al emprendimiento y/o al cliente
   - **Then** el sistema registra ambas calificaciones de forma independiente

### Edge Cases

- **¿Qué ocurre si un usuario intenta calificar un pedido o domicilio que aún no ha finalizado/entregado?** El sistema no habilita la calificación hasta que el pedido esté entregado o el domicilio finalizado; el botón para calificar solo aparece desde ese momento.
- **¿Cómo maneja el sistema un intento de calificar dos veces la misma entidad para el mismo pedido/domicilio?** Lo rechaza e informa que ya calificó esa entidad en esa interacción; el usuario puede editar su comentario (RF-04) pero no crear otro.
- **¿Qué sucede si el usuario intenta calificar una entidad con la que nunca tuvo interacción registrada (sin pedido/domicilio de por medio)?** No lo permite: toda calificación exige un pedido o domicilio real entre las partes.
- **¿Cómo se limita el rango de la calificación (por ejemplo, fuera de escala) y qué pasa si se envía un valor inválido?** La calificación es un número entero de 1 a 5 estrellas; un valor vacío o fuera de ese rango se rechaza y se pide corregirlo.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al cliente calificar y comentar un emprendimiento y los productos asociados a un pedido finalizado.
- **FR-002**: El sistema MUST permitir al cliente calificar y comentar al domiciliario que entregó su domicilio.
- **FR-003**: El sistema MUST permitir al vendedor de punto fijo calificar y comentar al domiciliario que gestionó una entrega de sus pedidos.
- **FR-004**: El sistema MUST permitir al domiciliario calificar y comentar al emprendimiento y al cliente involucrados en un domicilio que realizó.
- **FR-005**: El sistema MUST vincular cada calificación/comentario a una interacción real (pedido o domicilio) entre las partes involucradas.
- **FR-006**: El sistema MUST impedir que un mismo usuario registre más de una calificación por la misma interacción y entidad.
- **FR-007**: El sistema MUST permitir al vendedor (ambulante o de punto fijo) calificar y comentar al cliente de un pedido entregado.

### Key Entities

- **Calificación**: Valor numérico (ej. estrellas) y comentario textual, asociado a un autor, una entidad calificada (emprendimiento, producto, domiciliario o cliente) y una interacción de origen (pedido o domicilio).
- **Interacción**: Pedido o domicilio que habilita la posibilidad de calificar a las partes involucradas.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Entidad a calificar | Sí | Emprendimiento, producto, domiciliario o cliente, según el rol; debe existir una interacción finalizada (pedido entregado o domicilio finalizado) con el autor. |
| Calificación | Sí | Número entero de 1 a 5 estrellas. |
| Comentario | Sí | Texto de 1 a 500 caracteres. |

**Datos que asigna el sistema**

- Autor: el usuario en sesión.
- Interacción de origen (pedido o domicilio).
- Fecha y hora de publicación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Al menos el 60% de los pedidos y domicilios finalizados reciben una calificación dentro de las 48 horas posteriores a su cierre.
- **SC-002**: El 100% de las calificaciones registradas quedan asociadas a una interacción real verificable.
- **SC-003**: El sistema rechaza el 100% de los intentos de calificación duplicada sobre la misma interacción y entidad.

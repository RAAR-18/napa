# Feature Specification: Consulta del estado del domicilio

**Created**: 2026-09-11
**Requerimiento funcional**: RF-19
**Historias de usuario relacionadas**: HU-37, HU-38

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente hace seguimiento a su entrega (Priority: P2)

Como cliente, quiero consultar en qué estado va mi pedido o domicilio (aceptado, en camino, entregado), para saber cuándo lo voy a recibir.

**Why this priority**: Mejora la experiencia del cliente y reduce incertidumbre, pero no bloquea la ejecución del domicilio en sí, por lo que se clasifica como P2.

**Independent Test**: Puede probarse de forma independiente consultando el historial de pedidos del cliente y verificando que el estado del domicilio en curso se muestra actualizado.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de estado desde el historial del cliente
   - **Given** el cliente tiene un pedido con domicilio en curso
   - **When** consulta el estado desde su historial de pedidos
   - **Then** el sistema muestra el estado actualizado del domicilio (aceptado, en camino, entregado)

### User Story 2 - Vendedor hace seguimiento a lo despachado (Priority: P2)

Como vendedor, quiero consultar el estado de los domicilios de mis pedidos, para hacer seguimiento a lo que ya despaché.

**Why this priority**: Al igual que para el cliente, es una función de seguimiento que apoya la operación diaria sin ser bloqueante, por lo que se clasifica como P2.

**Independent Test**: Puede probarse de forma independiente consultando los pedidos despachados del vendedor y verificando que el estado de cada domicilio asociado se muestra actualizado.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de estado de domicilios despachados
   - **Given** el vendedor tiene pedidos con domicilio en curso
   - **When** consulta su estado
   - **Then** el sistema muestra el estado actualizado de cada uno

### Edge Cases

- ¿Qué sucede si el domicilio fue cancelado mientras el cliente o vendedor lo estaban consultando?
- ¿Cómo se refleja el estado cuando el domiciliario aún no ha actualizado su avance?
- ¿Qué ocurre si el pedido no tiene domicilio asociado (fue para recoger)?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar el estado actualizado del domicilio asociado a su pedido desde su historial.
- **FR-002**: El sistema DEBE permitir al vendedor consultar el estado actualizado de los domicilios asociados a sus pedidos despachados.
- **FR-003**: El sistema DEBE reflejar los cambios de estado del domicilio en tiempo oportuno para ambos roles.

### Key Entities

- **Domicilio**: Con estados como aceptado, recogido, en camino, entregado y cancelado, visible tanto para el cliente como para el vendedor asociados.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El estado del domicilio mostrado al cliente y al vendedor refleja el último cambio en menos de 30 segundos.
- **SC-002**: El 100% de los pedidos con domicilio en curso permiten consultar su estado sin errores.

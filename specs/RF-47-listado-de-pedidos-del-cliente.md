# Feature Specification: Listado de pedidos del cliente

**Created**: 2026-09-18
**Requerimiento funcional**: RF-47
**Historias de usuario relacionadas**: HU-77

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver el historial de mis compras (Priority: P2)

Como cliente, quiero listar mis pedidos, para consultar el historial de mis compras.

**Why this priority**: Es la puerta de entrada al seguimiento de cada pedido, pero no bloquea la compra, por lo que se clasifica como P2.

**Independent Test**: Puede probarse realizando varios pedidos y verificando que el cliente los ve listados con su modalidad, estado y total.

**Acceptance Scenarios**:

1. **Scenario**: Listado de pedidos
   - **Given** el cliente tiene pedidos realizados
   - **When** ingresa a sus pedidos
   - **Then** el sistema muestra el listado con emprendimiento, modalidad de entrega, fecha, estado y total, del más reciente al más antiguo

2. **Scenario**: Cliente sin pedidos
   - **Given** el cliente aún no ha realizado pedidos
   - **When** ingresa a sus pedidos
   - **Then** el sistema muestra un mensaje indicando que no tiene pedidos

### Edge Cases

- ¿Cómo se muestran las reservas programadas para una fecha futura?
- ¿Qué ocurre si el volumen de pedidos es alto? ¿Se pagina o se filtra por estado o fecha?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar el listado de sus pedidos.
- **FR-002**: El sistema DEBE mostrar por cada pedido el emprendimiento, la modalidad de entrega, la fecha, el estado y el total.
- **FR-003**: El sistema DEBE permitir filtrar el listado por estado y por fecha.
- **FR-004**: El sistema DEBE impedir que un cliente vea pedidos de otros clientes.

### Key Entities

- **Pedido**: Entidad listada, con emprendimiento, modalidad, fecha, estado y total.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente accede al listado de sus pedidos en menos de 3 segundos.
- **SC-002**: El 100% de los pedidos mostrados pertenecen al cliente autenticado.

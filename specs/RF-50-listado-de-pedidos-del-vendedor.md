# Feature Specification: Listado de pedidos del vendedor

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-50
**Historias de usuario relacionadas**: HU-80

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Vendedor consulta sus pedidos recibidos (Priority: P1)

Como vendedor, quiero listar mis pedidos, para consultar los pedidos recibidos.

**Why this priority**: Es el punto de entrada para que el vendedor gestione su operación diaria (confirmar, preparar, despachar pedidos); sin este listado no puede atender su negocio, por lo que se clasifica como P1.

**Independent Test**: Puede probarse ingresando al panel del vendedor, accediendo a la sección de pedidos y verificando que se listan únicamente los pedidos recibidos por su emprendimiento.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa del listado de pedidos
   - **Given** el vendedor tiene uno o más pedidos recibidos en distintos estados
   - **When** el vendedor accede a la sección de pedidos
   - **Then** el sistema muestra el listado de sus pedidos con datos resumidos (cliente, fecha, estado, total)

2. **Scenario**: Filtrado del listado por estado del pedido
   - **Given** el vendedor está consultando su listado de pedidos
   - **When** aplica un filtro por estado (por ejemplo, pendientes o en camino)
   - **Then** el sistema muestra únicamente los pedidos que coinciden con el estado seleccionado

### Edge Cases

- ¿Qué sucede si el vendedor no tiene ningún pedido registrado?
- ¿Cómo se ordena el listado cuando existen múltiples pedidos con el mismo estado?
- ¿Qué ocurre si un pedido cambia de estado mientras el vendedor está consultando el listado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor consultar el listado de todos los pedidos recibidos en su emprendimiento.
- **FR-002**: El sistema DEBE mostrar en el listado, al menos, el cliente, la fecha, el estado y el total de cada pedido.
- **FR-003**: El sistema DEBE permitir al vendedor filtrar el listado de pedidos por estado.
- **FR-004**: El sistema DEBE impedir que el vendedor visualice pedidos de otros emprendimientos.
- **FR-005**: El sistema DEBE mostrar la modalidad de cada pedido (entrega directa, reserva o domicilio), la fecha de entrega o retiro en el caso de las reservas, y permitir filtrar el listado por modalidad y por fecha de entrega.

### Key Entities

- **Pedido**: Entidad listada, con referencia al emprendimiento del vendedor, cliente, fecha, estado y total.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede acceder al listado de sus pedidos en menos de 3 segundos.
- **SC-002**: El 100% de los pedidos mostrados en el listado corresponden al emprendimiento del vendedor autenticado.
- **SC-003**: El vendedor puede identificar el estado de un pedido en el listado sin necesidad de abrir su detalle.

# Feature Specification: Consulta de domicilios por administrador

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-12
**Historias de usuario relacionadas**: HU-51

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Supervisar domicilios registrados (Priority: P3)

Como administrador, quiero consultar los domicilios registrados en la plataforma, para supervisar la operación general del servicio de entregas.

**Why this priority**: Es una función de supervisión administrativa, no bloquea el flujo transaccional entre cliente, vendedor y domiciliario, por lo que se prioriza después de los flujos operativos core.

**Independent Test**: Puede probarse de forma independiente ingresando como administrador a la sección de domicilios y verificando que se listan todos los domicilios existentes en el sistema, sin necesidad de que otras funciones administrativas estén implementadas.

**Acceptance Scenarios**:

1. **Scenario**: Listado general de domicilios
   - **Given** existen domicilios registrados en el sistema en distintos estados
   - **When** el administrador ingresa a la sección de consulta de domicilios
   - **Then** el sistema muestra el listado completo de domicilios con su información básica (origen, destino, estado, costo)

2. **Scenario**: Consulta de detalle de un domicilio específico
   - **Given** el administrador está viendo el listado de domicilios
   - **When** selecciona un domicilio puntual
   - **Then** el sistema muestra la información detallada de ese domicilio

### Edge Cases

- **¿Qué sucede cuando no existen domicilios registrados en el sistema?** Muestra el mensaje "No hay domicilios registrados".
- **¿Cómo se muestra un domicilio que fue cancelado, o que recibió ofertas de precio que el cliente rechazó?** Un domicilio cancelado muestra su motivo y fecha de cancelación; el detalle lista además las ofertas recibidas con su estado, incluidas las rechazadas.
- **¿Qué ocurre si el administrador consulta un domicilio que fue eliminado o archivado?** Los domicilios no se eliminan; el archivado es una acción personal de cada domiciliario y no cambia lo que ve el administrador.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador consultar el listado de todos los domicilios registrados en la plataforma, independientemente del vendedor o domiciliario asociado.
- **FR-002**: El sistema DEBE mostrar, para cada domicilio, su estado actual, origen, destino y costo.
- **FR-003**: El sistema DEBE permitir al administrador acceder al detalle completo de un domicilio seleccionado desde el listado.

### Key Entities

- **Domicilio**: Representa un servicio de entrega asociado a un pedido; incluye estado, origen, destino, costo, vendedor, cliente y domiciliario asignado.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. El administrador puede filtrar por estado y por rango de fechas, y abrir el detalle de un domicilio.

**Datos que se muestran o filtran**

- Estado, origen, destino y costo de cada domicilio.
- Detalle: vendedor, cliente, domiciliario, ofertas con su estado, trazabilidad y motivo de cancelación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador puede acceder al listado completo de domicilios en menos de 3 segundos bajo condiciones normales de operación.
- **SC-002**: El 100% de los domicilios registrados en el sistema son visibles para el administrador en la consulta general.

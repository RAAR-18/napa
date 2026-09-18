# Feature Specification: Cambio de estado del emprendimiento

**Created**: 2026-09-18
**Requerimiento funcional**: RF-30
**Historias de usuario relacionadas**: HU-55

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Indicar cuándo estoy atendiendo pedidos (Priority: P1)

Como vendedor (ambulante o de punto fijo), quiero cambiar el estado de mi emprendimiento entre abierto y cerrado, para indicar a los clientes cuándo estoy atendiendo pedidos.

**Why this priority**: Evita que el vendedor reciba pedidos inmediatos cuando no puede atenderlos (fin de jornada, descanso, sin mercancía) y evita frustraciones al cliente, por lo que se clasifica como P1.

**Independent Test**: Puede probarse cerrando el emprendimiento y verificando que el cliente lo ve como cerrado y no puede hacer pedidos de entrega directa ni de domicilio; luego reabriéndolo y verificando que vuelve a recibirlos.

**Acceptance Scenarios**:

1. **Scenario**: Apertura del emprendimiento
   - **Given** mi emprendimiento está cerrado
   - **When** lo cambio a abierto
   - **Then** el sistema lo muestra como abierto a los clientes y vuelve a permitir pedidos de entrega directa y de domicilio

2. **Scenario**: Cierre del emprendimiento
   - **Given** mi emprendimiento está abierto
   - **When** lo cambio a cerrado
   - **Then** el sistema lo muestra como cerrado a los clientes y deja de permitir nuevos pedidos de entrega directa y de domicilio

3. **Scenario**: Cierre con pedidos en curso
   - **Given** tengo pedidos pendientes o aceptados
   - **When** cierro el emprendimiento
   - **Then** el sistema me advierte de los pedidos en curso y, tras confirmar, conserva esos pedidos para que los atienda con normalidad

4. **Scenario**: Reservas con el emprendimiento cerrado
   - **Given** mi emprendimiento está cerrado
   - **When** un cliente intenta reservar para el día siguiente
   - **Then** el sistema permite la reserva, que quedará pendiente de mi respuesta

### Edge Cases

- **¿Qué ocurre con los pedidos pendientes o aceptados cuando el vendedor cierra el emprendimiento?** Se conservan: el sistema advierte al vendedor antes de cerrar y los pedidos ya creados se siguen atendiendo con normalidad.
- **¿Se pueden hacer reservas para el día siguiente mientras el emprendimiento está cerrado?** Sí: las reservas para el día siguiente se permiten y quedan pendientes de respuesta del vendedor; solo se bloquean los pedidos de entrega directa y de domicilio.
- **¿Qué pasa si el vendedor olvida cerrar el emprendimiento al terminar su jornada?** El emprendimiento se cierra automáticamente al terminar su horario de atención; si no tiene horario definido, tras 12 horas continuas abierto.
- **¿Hay un límite de veces que el vendedor puede cambiar el estado?** No hay límite; cada cambio queda registrado con fecha y hora.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor cambiar el estado de su emprendimiento entre "abierto" y "cerrado" en cualquier momento.
- **FR-002**: El sistema DEBE mostrar el estado (abierto o cerrado) del emprendimiento en el listado y en el detalle que ven los clientes.
- **FR-003**: El sistema DEBE impedir nuevos pedidos de entrega directa y de domicilio a un emprendimiento cerrado.
- **FR-004**: El sistema DEBE permitir reservas para el día siguiente a un emprendimiento cerrado, que quedan pendientes de respuesta del vendedor.
- **FR-005**: El sistema DEBE conservar y permitir atender los pedidos ya creados cuando el vendedor cierre el emprendimiento, advirtiéndole de ellos antes de cerrar.
- **FR-006**: El sistema DEBE registrar cada cambio de estado con fecha y hora (RNF16).

### Key Entities

- **Emprendimiento**: Su estado (abierto o cerrado) determina si acepta nuevos pedidos inmediatos; nace en estado "abierto".
- **Pedido**: Los pedidos creados antes del cierre no se ven afectados.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Estado | Sí | Abierto o cerrado. |

**Datos que asigna el sistema**

- Fecha y hora del cambio.
- Cierre automático al terminar el horario de atención o tras 12 horas continuas abierto sin horario.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cambio de estado se refleja para los clientes en menos de 30 segundos.
- **SC-002**: El 0% de los pedidos de entrega directa o de domicilio se crea sobre un emprendimiento cerrado.
- **SC-003**: El vendedor puede cambiar el estado de su emprendimiento en menos de 5 segundos.

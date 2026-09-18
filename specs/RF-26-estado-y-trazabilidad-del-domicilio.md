# Feature Specification: Estado y trazabilidad del domicilio

**Created**: 2026-09-18
**Requerimiento funcional**: RF-26
**Historias de usuario relacionadas**: HU-48, HU-33

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente sigue su domicilio en todo momento (Priority: P1)

Como cliente, quiero consultar la trazabilidad de mi domicilio, para saber en todo momento en qué etapa va mi pedido y dónde está.

**Why this priority**: Reduce la incertidumbre del cliente durante la espera y evita contactos innecesarios con el vendedor y el domiciliario, por lo que se clasifica como P1.

**Independent Test**: Puede probarse avanzando un domicilio por sus estados y verificando que el cliente ve la cronología completa y actualizada.

**Acceptance Scenarios**:

1. **Scenario**: Cronología del domicilio
   - **Given** mi pedido tiene un domicilio en curso
   - **When** abro la trazabilidad
   - **Then** el sistema muestra la cronología de estados (publicado, asignado, recogido, en camino, entregado, finalizado) con la fecha y hora de cada uno

2. **Scenario**: Ubicación durante el trayecto
   - **Given** mi domicilio está en camino
   - **When** consulto la trazabilidad
   - **Then** el sistema muestra en un mapa la ubicación aproximada del domiciliario

3. **Scenario**: Domicilio cancelado
   - **Given** el administrador canceló mi domicilio
   - **When** consulto la trazabilidad
   - **Then** el sistema muestra el estado cancelado con su motivo

---

### User Story 2 - Vendedor de punto fijo sigue lo que despachó (Priority: P2)

Como vendedor de punto fijo, quiero consultar el estado de un domicilio, para conocer el avance de la entrega.

**Why this priority**: Apoya la operación diaria del vendedor sin bloquear el ciclo del domicilio, por lo que se clasifica como P2.

**Independent Test**: Puede probarse consultando desde el listado de domicilios del vendedor el estado actualizado de uno en curso.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del estado de un domicilio
   - **Given** tengo domicilios en curso
   - **When** consulto el estado de uno de ellos
   - **Then** el sistema muestra su estado actualizado y el último cambio registrado

### Edge Cases

- **¿Cómo se muestra la trazabilidad cuando el domiciliario aún no ha actualizado su avance?** Muestra el último estado con su hora y el texto "sin novedades desde hace X minutos".
- **¿Qué ocurre si el domiciliario desactiva la ubicación durante el trayecto?** Se muestra la última ubicación conocida con su hora y se avisa que no está actualizada.
- **¿Hasta cuándo permanece consultable la trazabilidad de un domicilio finalizado?** Durante 12 meses después de finalizado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar en cualquier momento la trazabilidad de sus domicilios.
- **FR-002**: El sistema DEBE mostrar la cronología de estados con fecha y hora de cada evento.
- **FR-003**: El sistema DEBE mostrar la ubicación aproximada del domiciliario mientras el domicilio esté en camino.
- **FR-004**: El sistema DEBE permitir al vendedor de punto fijo consultar el estado actualizado de sus domicilios.
- **FR-005**: El sistema DEBE reflejar cada cambio de estado a las partes en un tiempo oportuno (RNF17).
- **FR-006**: El sistema DEBE restringir la trazabilidad a las partes involucradas en el domicilio.

### Key Entities

- **Trazabilidad**: Cronología de eventos de un domicilio; cada evento registra estado, fecha, hora y usuario que lo originó.
- **Domicilio**: Estados: disponible, asignado, recogido, en camino, entregado, finalizado y cancelado.

### Data Rules

**Datos que ingresa el usuario**: Ninguno.

**Datos que se muestran o filtran**

- Cronología: estado, fecha, hora y usuario que originó cada evento.
- Ubicación aproximada del domiciliario mientras el domicilio está en camino.
- Motivo, si el domicilio fue cancelado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El estado mostrado al cliente y al vendedor refleja el último cambio en menos de 30 segundos.
- **SC-002**: El 100% de los domicilios en curso permiten consultar su trazabilidad sin errores.

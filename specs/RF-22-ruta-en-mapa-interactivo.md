# Feature Specification: Ruta en mapa interactivo

**Created**: 2026-09-18
**Requerimiento funcional**: RF-22
**Historias de usuario relacionadas**: HU-45

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Llegar al punto de recogida y al punto de entrega (Priority: P1)

Como domiciliario, quiero ver un mapa interactivo con la ruta hacia el punto de recogida y el punto de entrega, para llegar al destino sin perderme.

**Why this priority**: Es la guía operativa del domiciliario durante todo el trayecto; sin ella no puede realizar el domicilio de forma eficiente, por lo que se clasifica como P1.

**Independent Test**: Puede probarse asignando un domicilio a un domiciliario y verificando que ve el mapa con su posición, el punto A, el punto B y la ruta.

**Acceptance Scenarios**:

1. **Scenario**: Mapa de un domicilio asignado
   - **Given** el domicilio fue asignado al domiciliario (por aceptar la tarifa publicada o porque el cliente aceptó su oferta)
   - **When** el domiciliario abre la ruta del domicilio
   - **Then** el sistema muestra un mapa interactivo con su ubicación actual, el punto A, el punto B y la ruta para llegar

2. **Scenario**: Acceso denegado a un domicilio ajeno
   - **Given** el domicilio está asignado a otro domiciliario
   - **When** el domiciliario intenta abrir su mapa
   - **Then** el sistema deniega el acceso

3. **Scenario**: Sin ubicación o sin conexión
   - **Given** el dispositivo no tiene permiso de ubicación o pierde conexión
   - **When** el domiciliario abre el mapa
   - **Then** el sistema informa el problema con un mensaje claro y muestra las direcciones en texto como respaldo

### Edge Cases

- **¿Qué ocurre si el punto B no puede resolverse en el mapa por una dirección ambigua?** Se muestra la dirección en texto y la coordenada registrada por el cliente, y el domiciliario puede contactarlo.
- **¿Cómo se comporta el mapa si el domiciliario pierde la señal durante el trayecto?** Conserva la última ruta cargada y retoma la posición cuando vuelve la señal (RNF28).
- **¿Se muestra la ruta completa (posición actual → A → B) o solo el tramo pendiente?** Se muestra la ruta completa (posición actual → A → B), resaltando el tramo pendiente.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE mostrar al domiciliario con un domicilio asignado un mapa interactivo con su ubicación, el punto A, el punto B y la ruta.
- **FR-002**: El sistema DEBE restringir el mapa al domiciliario asignado y solo mientras el domicilio esté activo (ni finalizado ni cancelado).
- **FR-003**: El sistema DEBE mostrar las direcciones en texto como respaldo cuando el mapa no pueda cargarse.
- **FR-004**: El sistema DEBE apoyarse en un servicio externo de mapas (RNF19) y tolerar conectividad intermitente (RNF28).

### Key Entities

- **Ruta**: Trayecto calculado desde la ubicación del domiciliario hasta el punto A y desde este hasta el punto B.
- **Domicilio**: Debe estar asignado al domiciliario que consulta el mapa.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. Requiere permiso de ubicación del dispositivo.

**Datos que se muestran o filtran**

- Posición actual del domiciliario, punto A, punto B y ruta completa (tramo pendiente resaltado).
- Direcciones en texto como respaldo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El mapa con la ruta se muestra en menos de 5 segundos tras la asignación.
- **SC-002**: El 0% de los domiciliarios sin asignación puede acceder al mapa de un domicilio.

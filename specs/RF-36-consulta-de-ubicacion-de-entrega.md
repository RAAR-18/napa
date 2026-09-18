# Feature Specification: Consulta de ubicación de entrega

**Created**: 2026-09-18
**Requerimiento funcional**: RF-36
**Historias de usuario relacionadas**: HU-61

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Llegar hasta donde está el cliente (Priority: P1)

Como vendedor ambulante, quiero ver en un mapa la ubicación del cliente, para llegar hasta donde está y entregarle su pedido.

**Why this priority**: La entrega directa y la reserva con entrega dependen de que el vendedor ambulante encuentre al cliente; sin la ubicación no puede cumplir el pedido, por lo que se clasifica como P1.

**Independent Test**: Puede probarse aceptando un pedido de entrega directa y verificando que el vendedor ambulante ve la ubicación indicada por el cliente en un mapa.

**Acceptance Scenarios**:

1. **Scenario**: Ubicación de un pedido de entrega directa
   - **Given** acepté un pedido de entrega directa
   - **When** abro la ubicación de entrega
   - **Then** el sistema muestra en un mapa interactivo la ubicación indicada por el cliente y mi posición actual

2. **Scenario**: Ubicación de una reserva el día programado
   - **Given** acepté una reserva cuya fecha es hoy
   - **When** abro la ubicación de entrega
   - **Then** el sistema muestra en un mapa la ubicación indicada por el cliente al reservar

3. **Scenario**: Pedido no aceptado o ajeno
   - **Given** el pedido está pendiente o pertenece a otro vendedor
   - **When** intento abrir la ubicación
   - **Then** el sistema deniega el acceso e informa que el pedido no está disponible

### Edge Cases

- **¿Qué ocurre si el cliente está demasiado lejos del vendedor ambulante para que la entrega sea viable?** El vendedor lo decide al aceptar o rechazar el pedido (RF-54), viendo la distancia; una vez aceptado, si no puede cumplirlo, lo reporta (RF-61).
- **¿Cómo se maneja una reserva cuya ubicación fue indicada el día anterior y el cliente ya no está allí?** La ubicación del pedido no se edita; el vendedor confirma con el cliente por el canal de contacto (RF-50) antes de iniciar la entrega.
- **¿Qué sucede si el vendedor ambulante no tiene señal o permiso de ubicación?** Muestra la última ruta cargada y la dirección en texto (RNF28).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor ambulante consultar la ubicación de entrega solo de pedidos aceptados de entrega directa o de reserva.
- **FR-002**: El sistema DEBE mostrar un mapa interactivo con la ubicación del cliente y la del vendedor, con la dirección en texto como respaldo (RNF19).
- **FR-003**: El sistema DEBE compartir la ubicación del cliente únicamente con el vendedor del pedido y mientras el pedido esté activo (RNF04).

### Key Entities

- **Ubicación de entrega**: Punto indicado por el cliente al hacer el pedido, con su dirección de referencia.
- **Pedido**: De entrega directa o reserva con vendedor ambulante, en estado aceptado.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. Requiere permiso de ubicación del dispositivo.

**Datos que se muestran o filtran**

- Ubicación del cliente indicada en el pedido, dirección de referencia y posición actual del vendedor.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La ubicación del cliente se muestra en el mapa en menos de 5 segundos.
- **SC-002**: El 0% de los vendedores ajenos al pedido puede consultar la ubicación del cliente.

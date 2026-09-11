# Feature Specification: Contacto con vendedor o domiciliario

**Created**: 2026-09-11
**Requerimiento funcional**: RF-43
**Historias de usuario relacionadas**: HU-66

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente contacta al vendedor o domiciliario de su pedido (Priority: P2)

Como cliente, quiero contactar al vendedor o domiciliario, para comunicarme con ellos cuando tenga alguna inquietud sobre mi pedido.

**Why this priority**: Mejora la experiencia del cliente y permite resolver inconvenientes durante el ciclo del pedido, pero no es indispensable para que el pedido se complete; se clasifica como P2.

**Independent Test**: Puede probarse desde el detalle de un pedido en curso, iniciando contacto con el vendedor o con el domiciliario asignado y verificando que el mensaje o llamada llega al destinatario correcto.

**Acceptance Scenarios**:

1. **Scenario**: Contacto exitoso con el vendedor
   - **Given** el cliente tiene un pedido registrado asociado a un vendedor
   - **When** el cliente selecciona la opción de contactar al vendedor desde el detalle del pedido
   - **Then** el sistema habilita el canal de comunicación (mensaje o llamada) con el vendedor correspondiente

2. **Scenario**: Contacto exitoso con el domiciliario asignado
   - **Given** el pedido del cliente tiene un domiciliario asignado para la entrega
   - **When** el cliente selecciona la opción de contactar al domiciliario
   - **Then** el sistema habilita el canal de comunicación con el domiciliario asignado

### Edge Cases

- ¿Qué sucede si el cliente intenta contactar a un domiciliario cuando el pedido aún no tiene uno asignado?
- ¿Cómo maneja el sistema la falta de respuesta del vendedor o del domiciliario tras ser contactado?
- ¿Qué ocurre si el cliente intenta contactar al vendedor o domiciliario de un pedido ya finalizado o cancelado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente iniciar contacto con el vendedor asociado a su pedido.
- **FR-002**: El sistema DEBE permitir al cliente iniciar contacto con el domiciliario asignado a su pedido, cuando exista uno.
- **FR-003**: El sistema DEBE impedir la opción de contactar a un domiciliario mientras el pedido no tenga uno asignado.
- **FR-004**: El sistema DEBE registrar el canal de contacto habilitado (mensaje o llamada) asociado al pedido para trazabilidad.

### Key Entities

- **Pedido**: Contiene la referencia al vendedor y, cuando aplica, al domiciliario asignado.
- **Canal de contacto**: Medio de comunicación habilitado entre el cliente y el vendedor o domiciliario (mensaje o llamada).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El cliente puede iniciar contacto con el vendedor o domiciliario en menos de 10 segundos desde el detalle del pedido.
- **SC-002**: El 100% de los intentos de contacto se dirigen al vendedor o domiciliario correcto asociado al pedido.
- **SC-003**: El 0% de los intentos de contacto con un domiciliario no asignado se completa exitosamente.

# Feature Specification: Confirmación de llegada por el cliente

**Created**: 2026-09-18
**Requerimiento funcional**: RF-25
**Historias de usuario relacionadas**: HU-50

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Dar por recibido mi pedido (Priority: P1)

Como cliente, quiero confirmar la llegada de mi pedido, para dar por finalizado el domicilio.

**Why this priority**: Es la conformidad final del cliente y cierra el ciclo del domicilio; habilita las calificaciones, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un domicilio entregado por el domiciliario, confirmando la llegada desde el cliente y verificando que el estado pasa a finalizado.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación de llegada
   - **Given** el domiciliario confirmó la entrega de mi pedido
   - **When** confirmo que el pedido llegó
   - **Then** el sistema cambia el estado del domicilio a finalizado, lo registra en la trazabilidad y habilita las calificaciones entre las partes

2. **Scenario**: Confirmación antes de la entrega
   - **Given** el domiciliario aún no confirmó la entrega
   - **When** intento confirmar la llegada
   - **Then** el sistema rechaza la operación e indica que el pedido todavía no figura como entregado

3. **Scenario**: Desacuerdo con la entrega
   - **Given** el domiciliario marcó el pedido como entregado pero no lo recibí
   - **When** informo el problema
   - **Then** el sistema me permite crear un reporte (RF-60) y mantiene el domicilio sin finalizar hasta su resolución

### Edge Cases

- ¿Qué sucede si el cliente no confirma la llegada en un tiempo prolongado? ¿Se finaliza automáticamente?
- ¿Qué ocurre si el cliente confirma la llegada dos veces desde dispositivos distintos?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente confirmar la llegada únicamente cuando el domicilio esté en estado "entregado".
- **FR-002**: El sistema DEBE cambiar el estado del domicilio a "finalizado" y registrar el evento en la trazabilidad.
- **FR-003**: El sistema DEBE permitir al cliente informar un desacuerdo con la entrega mediante un reporte (RF-60).
- **FR-004**: El sistema DEBE habilitar las calificaciones entre las partes una vez el domicilio esté finalizado (RF-01).

### Key Entities

- **Domicilio**: Transición de esta funcionalidad: entregado → finalizado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios finalizados cuentan con la confirmación del domiciliario y del cliente.
- **SC-002**: El 0% de las confirmaciones de llegada se acepta antes de la entrega.

# Feature Specification: Confirmación de llegada por el cliente

**Created**: 2026-09-18
**Requerimiento funcional**: RF-25
**Historias de usuario relacionadas**: HU-50

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Dar por recibido mi pedido (Priority: P1)

Como cliente, quiero ver mi código de confirmación y entregárselo al domiciliario, y luego confirmar que mi pedido llegó, para dar por finalizado el domicilio.

**Why this priority**: Es la conformidad final del cliente y cierra el ciclo del domicilio; habilita las calificaciones, por lo que se clasifica como P1.

**Independent Test**: Puede probarse con un domicilio asignado, consultando el código de confirmación, entregándolo al domiciliario al recibir el pedido, y confirmando la llegada desde el cliente una vez el domiciliario lo registra como entregado.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del código de confirmación
   - **Given** mi domicilio fue asignado a un domiciliario
   - **When** consulto el detalle de mi domicilio
   - **Then** el sistema me muestra mi código de confirmación, para entregárselo al domiciliario en el momento de la entrega

2. **Scenario**: Confirmación de llegada
   - **Given** el domiciliario confirmó la entrega de mi pedido con mi código
   - **When** confirmo que el pedido llegó
   - **Then** el sistema cambia el estado del domicilio a finalizado, lo registra en la trazabilidad y habilita las calificaciones entre las partes

3. **Scenario**: Confirmación antes de la entrega
   - **Given** el domiciliario aún no confirmó la entrega
   - **When** intento confirmar la llegada
   - **Then** el sistema rechaza la operación e indica que el pedido todavía no figura como entregado

4. **Scenario**: Desacuerdo con la entrega
   - **Given** el domiciliario marcó el pedido como entregado pero no lo recibí
   - **When** informo el problema
   - **Then** el sistema me permite crear un reporte (RF-60) y mantiene el domicilio sin finalizar hasta su resolución

### Edge Cases

- ¿Qué sucede si el cliente no confirma la llegada en un tiempo prolongado? ¿Se finaliza automáticamente tras la confirmación con código del domiciliario?
- ¿Qué ocurre si el cliente confirma la llegada dos veces desde dispositivos distintos?
- ¿Puede el cliente regenerar su código si sospecha que fue compartido por error con la persona equivocada?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE mostrar al cliente el código de confirmación de su domicilio desde que este queda asignado a un domiciliario.
- **FR-002**: El sistema DEBE permitir al cliente confirmar la llegada únicamente cuando el domicilio esté en estado "entregado" (es decir, tras la validación del código por el domiciliario, RF-24).
- **FR-003**: El sistema DEBE cambiar el estado del domicilio a "finalizado" y registrar el evento en la trazabilidad.
- **FR-004**: El sistema DEBE permitir al cliente informar un desacuerdo con la entrega mediante un reporte (RF-60).
- **FR-005**: El sistema DEBE habilitar las calificaciones entre las partes una vez el domicilio esté finalizado (RF-01).

### Key Entities

- **Domicilio**: Transición de esta funcionalidad: entregado → finalizado. Incluye el código de confirmación que el cliente entrega al domiciliario.
- **Código de confirmación**: Refuerza, no sustituye, la doble confirmación (el domiciliario lo ingresa para marcar entregado; el cliente confirma la llegada por separado).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios finalizados cuentan con la validación del código por el domiciliario y la confirmación del cliente.
- **SC-002**: El 0% de las confirmaciones de llegada se acepta antes de la entrega.
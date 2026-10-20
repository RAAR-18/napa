# Feature Specification: Cancelación de domicilio por administrador

**Created**: 2026-09-11
**Actualizado**: 2026-10-14
**Requerimiento funcional**: RF-13
**Historias de usuario relacionadas**: HU-52

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Resolver casos excepcionales cancelando un domicilio (Priority: P3)

Como administrador, quiero cancelar un domicilio indicando el motivo, para resolver casos excepcionales o reportes de mal uso de la plataforma.

**Why this priority**: Es una función administrativa de excepción que no forma parte del flujo diario de entregas; se activa solo ante incidentes, por lo que se clasifica como P3.

**Independent Test**: Puede probarse de forma independiente identificando un domicilio en curso, cancelándolo desde el panel de administrador con un motivo, y verificando que su estado cambia a cancelado y las partes son notificadas.

**Acceptance Scenarios**:

1. **Scenario**: Cancelación de un domicilio problemático
   - **Given** el administrador identifica un domicilio problemático
   - **When** lo cancela e indica el motivo de la cancelación
   - **Then** el sistema cambia el estado del domicilio y de su pedido a cancelado y notifica la cancelación a las partes involucradas (cliente, vendedor y domiciliario si ya fue asignado)

2. **Scenario**: Intento de cancelación sin motivo
   - **Given** el administrador inicia la cancelación de un domicilio
   - **When** no indica un motivo de cancelación
   - **Then** el sistema impide confirmar la cancelación hasta que se registre un motivo

### Edge Cases

- ¿Qué sucede si el domicilio ya fue entregado antes de que el administrador intente cancelarlo?
- ¿Cómo se maneja la cancelación cuando el domicilio ya tiene un pago confirmado? (el dinero se devuelve fuera de la plataforma; la plataforma no lo procesa)
- ¿Qué ocurre si el domiciliario ya está en camino al momento de la cancelación?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador cancelar cualquier domicilio registrado, independientemente de su estado, salvo aquellos ya entregados.
- **FR-002**: El sistema DEBE exigir el registro de un motivo obligatorio para completar la cancelación.
- **FR-003**: El sistema DEBE notificar la cancelación al cliente, al vendedor y al domiciliario asignado (si existiera) al momento de cancelar.
- **FR-004**: El sistema DEBE cambiar a "cancelado" el estado del pedido asociado al domicilio en la misma operación.

### Key Entities

- **Domicilio**: Entidad cancelable por el administrador; registra el motivo (la fecha queda en su trazabilidad); su pedido pasa a cancelado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las cancelaciones administrativas quedan registradas con motivo y son notificadas a las partes involucradas en menos de 1 minuto.
- **SC-002**: El sistema impide cancelar domicilios ya entregados en el 100% de los casos.

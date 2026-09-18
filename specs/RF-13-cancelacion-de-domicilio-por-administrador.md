# Feature Specification: Cancelación de domicilio por administrador

**Created**: 2026-09-11
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
   - **Then** el sistema cambia el estado del domicilio a cancelado y notifica la cancelación a las partes involucradas (cliente, vendedor y domiciliario si ya fue asignado)

2. **Scenario**: Intento de cancelación sin motivo
   - **Given** el administrador inicia la cancelación de un domicilio
   - **When** no indica un motivo de cancelación
   - **Then** el sistema impide confirmar la cancelación hasta que se registre un motivo

### Edge Cases

- ¿Qué sucede si el domicilio ya fue entregado antes de que el administrador intente cancelarlo?
- ¿Cómo se maneja la cancelación cuando el domicilio ya tiene un pago confirmado?
- ¿Qué ocurre si el domiciliario ya está en camino al momento de la cancelación?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador cancelar cualquier domicilio registrado, independientemente de su estado, salvo aquellos ya finalizados.
- **FR-002**: El sistema DEBE exigir el registro de un motivo obligatorio para completar la cancelación.
- **FR-003**: El sistema DEBE notificar la cancelación al cliente, al vendedor y al domiciliario asignado (si existiera) al momento de cancelar.

### Key Entities

- **Domicilio**: Entidad cancelable por el administrador; registra motivo y fecha de cancelación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las cancelaciones administrativas quedan registradas con motivo y son notificadas a las partes involucradas en menos de 1 minuto.
- **SC-002**: El sistema impide cancelar domicilios ya finalizados en el 100% de los casos.

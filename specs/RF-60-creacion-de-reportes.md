# Feature Specification: Creación de reportes

**Created**: 2026-09-11
**Requerimiento funcional**: RF-60
**Historias de usuario relacionadas**: HU-91, HU-92, HU-93

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente o vendedor reportan un problema de la plataforma (Priority: P1)

Como cliente o vendedor, quiero reportar un problema relacionado con la plataforma, un pedido o un domicilio, para informar situaciones que requieran atención del administrador.

**Why this priority**: Activa el mecanismo central de soporte y confianza de la plataforma; sin la posibilidad de reportar, las incidencias de pedidos, pagos o comportamiento de otros usuarios no llegan al administrador para su atención.

**Independent Test**: Puede probarse creando un reporte desde el historial de pedidos de un cliente o desde el panel del vendedor y verificando que queda registrado en estado pendiente y visible para el administrador.

**Acceptance Scenarios**:

1. **Scenario**: Reporte creado por un cliente
   - **Given** el cliente tiene un pedido o domicilio relacionado con el inconveniente
   - **When** completa el formulario de reporte indicando el motivo y la descripción
   - **Then** el sistema registra el reporte en estado pendiente y lo pone a disposición del administrador

2. **Scenario**: Reporte creado por un vendedor
   - **Given** el vendedor identifica un inconveniente relacionado con un pedido, domicilio o usuario
   - **When** completa el formulario de reporte indicando el motivo y la descripción
   - **Then** el sistema registra el reporte en estado pendiente asociado a su cuenta

---

### User Story 2 - Domiciliario reporta un problema durante la entrega (Priority: P1)

Como domiciliario, quiero reportar un problema, para informar inconvenientes ocurridos durante mi actividad de entrega.

**Why this priority**: Los incidentes durante el servicio de entrega (direcciones erróneas, comportamiento inadecuado, inseguridad) requieren atención oportuna del administrador y son críticos para la operación y seguridad del servicio de domicilios.

**Independent Test**: Puede probarse creando un reporte asociado a un domicilio en curso y verificando que queda registrado y visible para el administrador.

**Acceptance Scenarios**:

1. **Scenario**: Reporte creado por un domiciliario
   - **Given** el domiciliario tiene un domicilio asignado en el que ocurrió un inconveniente
   - **When** completa el formulario de reporte indicando el motivo y la descripción
   - **Then** el sistema registra el reporte en estado pendiente y lo asocia al domicilio correspondiente

---

### Edge Cases

- ¿Qué ocurre si un usuario intenta enviar un reporte sin descripción o motivo?
- ¿Cómo maneja el sistema reportes duplicados sobre el mismo incidente?
- ¿Puede un usuario ver el estado de los reportes que ha enviado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al cliente crear un reporte indicando motivo y descripción del problema.
- **FR-002**: El sistema MUST permitir al vendedor crear un reporte indicando motivo y descripción del problema.
- **FR-003**: El sistema MUST permitir al domiciliario crear un reporte indicando motivo y descripción del problema.
- **FR-004**: El sistema MUST registrar todo reporte creado en estado pendiente hasta su revisión.
- **FR-005**: El sistema MUST validar que el reporte incluya al menos un motivo y una descripción antes de registrarlo.

### Key Entities *(include if feature involves data)*

- **Reporte**: Representa un problema informado por un usuario; atributos clave: autor, rol del autor, motivo, descripción, entidad relacionada (pedido, domicilio, usuario), estado (pendiente, en revisión, resuelto), fecha de creación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario puede crear un reporte en menos de 1 minuto.
- **SC-002**: El 100% de los reportes creados quedan disponibles para el administrador de forma inmediata.
- **SC-003**: El sistema rechaza el 100% de los intentos de reporte sin motivo o descripción.

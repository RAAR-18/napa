# Feature Specification: Resolución de reportes

**Created**: 2026-09-11
**Requerimiento funcional**: RF-55
**Historias de usuario relacionadas**: HU-83

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cerrar un reporte con una solución (Priority: P2)

Como administrador, quiero resolver un reporte, para dar solución a los problemas informados.

**Why this priority**: Cierra el ciclo de gestión de incidencias iniciado con la creación del reporte (RF-53); es necesario para mantener la confianza de los usuarios en que sus reportes tienen seguimiento.

**Independent Test**: Puede probarse tomando un reporte pendiente, registrando la acción tomada y verificando que su estado cambia a resuelto.

**Acceptance Scenarios**:

1. **Scenario**: Resolución exitosa de un reporte
   - **Given** el administrador tiene un reporte pendiente o en revisión
   - **When** registra la resolución indicando la acción tomada
   - **Then** el sistema marca el reporte como resuelto y notifica al usuario que lo creó

2. **Scenario**: Intento de resolver un reporte ya resuelto
   - **Given** un reporte ya se encuentra en estado resuelto
   - **When** el administrador intenta resolverlo nuevamente
   - **Then** el sistema indica que el reporte ya fue resuelto y no permite duplicar la acción

---

### Edge Cases

- ¿Puede el administrador reabrir un reporte que fue cerrado incorrectamente?
- ¿Qué información mínima debe registrarse al resolver un reporte (nota de resolución, responsable)?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al administrador marcar un reporte como resuelto.
- **FR-002**: El sistema MUST requerir el registro de una nota o acción de resolución al cerrar un reporte.
- **FR-003**: El sistema MUST notificar al usuario que creó el reporte cuando este sea resuelto.
- **FR-004**: El sistema MUST impedir resolver un reporte que ya se encuentre en estado resuelto.

### Key Entities *(include if feature involves data)*

- **Reporte**: Ver definición en RF-53; en este requerimiento se actualiza su estado a resuelto y se agrega la nota de resolución.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El usuario que creó un reporte es notificado de su resolución en menos de 1 minuto tras el cierre.
- **SC-002**: El 100% de los reportes resueltos quedan con una nota de resolución registrada.

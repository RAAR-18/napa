# Feature Specification: Configuración de preferencias de notificación

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-40
**Historias de usuario relacionadas**: HU-65

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente configura sus notificaciones (Priority: P3)

Como cliente, quiero configurar mis preferencias de notificación, para decidir qué tipos de notificaciones deseo recibir sobre mis pedidos y domicilios.

**Why this priority**: Es una función de personalización que mejora la experiencia pero no es indispensable para el funcionamiento de los flujos transaccionales.

**Independent Test**: Puede probarse desactivando un tipo de notificación informativa y verificando que deja de generarse mientras los demás tipos continúan llegando.

**Acceptance Scenarios**:

1. **Scenario**: Desactivar un tipo de notificación
   - **Given** tengo notificaciones habilitadas
   - **When** desactivo un tipo de notificación informativo
   - **Then** el sistema deja de enviarme notificaciones de ese tipo, conservando las demás

2. **Scenario**: Reactivar un tipo de notificación
   - **Given** tengo un tipo de notificación desactivado
   - **When** lo reactivo desde mis preferencias
   - **Then** el sistema vuelve a generarme notificaciones de ese tipo ante nuevos eventos

---

### User Story 2 - Vendedor configura sus notificaciones (Priority: P3)

Como vendedor (ambulante o de punto fijo), quiero configurar mis preferencias de notificación, para decidir qué avisos recibo sin perder los pedidos nuevos.

**Why this priority**: Permite al vendedor evitar interrupciones mientras vende, pero no es indispensable para los flujos transaccionales.

**Independent Test**: Puede probarse desactivando un tipo informativo y verificando que los avisos de nuevos pedidos siguen llegando.

**Acceptance Scenarios**:

1. **Scenario**: Desactivar un tipo informativo
   - **Given** tengo notificaciones habilitadas
   - **When** desactivo un tipo de notificación informativo
   - **Then** el sistema deja de enviármelo y sigue enviándome los avisos de nuevos pedidos

---

### User Story 3 - Domiciliario configura sus notificaciones (Priority: P3)

Como domiciliario, quiero configurar mis preferencias de notificación, para decidir qué avisos de domicilios disponibles recibo.

**Why this priority**: Permite al domiciliario evitar avisos que no le interesan, pero no es indispensable para los flujos transaccionales.

**Independent Test**: Puede probarse desactivando el aviso de domicilios disponibles y verificando que siguen llegando los avisos de sus domicilios asignados.

**Acceptance Scenarios**:

1. **Scenario**: Desactivar los avisos de domicilios disponibles
   - **Given** tengo notificaciones habilitadas
   - **When** desactivo el aviso de nuevos domicilios disponibles
   - **Then** el sistema deja de enviármelo y sigue avisándome del avance de mis domicilios asignados

---

### User Story 4 - Administrador configura sus notificaciones (Priority: P3)

Como administrador, quiero configurar mis preferencias de notificación, para decidir qué avisos administrativos recibo.

**Why this priority**: Es una función de personalización de baja frecuencia de uso, por lo que se clasifica como P3.

**Independent Test**: Puede probarse desactivando un tipo informativo y verificando que deja de generarse.

**Acceptance Scenarios**:

1. **Scenario**: Desactivar un tipo de notificación
   - **Given** tengo notificaciones habilitadas
   - **When** desactivo un tipo de notificación informativo
   - **Then** el sistema deja de enviármelo, conservando los avisos de reportes nuevos

### Edge Cases

- **¿Qué sucede si el usuario desactiva un tipo de notificación crítico (ej. cambios de estado de un domicilio en curso)?** Las notificaciones críticas (nuevos pedidos, ofertas y cambios de estado de un domicilio en curso) no pueden desactivarse; solo las informativas.
- **¿Se conservan las preferencias configuradas si el usuario cambia de rol o dispositivo?** Sí: pertenecen a la cuenta y se conservan entre dispositivos; el rol no cambia porque se define al registrarse.
- **¿Qué ocurre con las notificaciones ya generadas antes de desactivar un tipo?** Permanecen en el historial.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a los usuarios configurar qué tipos de notificación desean recibir.
- **FR-002**: El sistema DEBE respetar las preferencias configuradas al generar nuevas notificaciones.
- **FR-003**: El sistema DEBE permitir al usuario modificar sus preferencias en cualquier momento.

### Key Entities

- **Preferencia de notificación**: Representa la configuración de un usuario sobre qué tipos de notificación desea recibir.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Tipos de notificación activados | Sí | Una opción por cada tipo informativo; las notificaciones críticas (nuevos pedidos, ofertas y cambios de un domicilio en curso) no pueden desactivarse. |

**Datos que asigna el sistema**

- Fecha de última modificación de las preferencias.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un cambio en las preferencias de notificación se aplica a los eventos generados a partir de ese momento en el 100% de los casos.
- **SC-002**: El usuario puede modificar sus preferencias de notificación en menos de 1 minuto.

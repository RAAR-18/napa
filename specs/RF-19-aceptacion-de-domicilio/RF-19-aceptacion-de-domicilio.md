# Feature Specification: Aceptación de domicilio

**Created**: 2026-09-18
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-19
**Historias de usuario relacionadas**: HU-40

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Comprometerse a realizar un domicilio con la tarifa publicada (Priority: P1)

Como domiciliario, quiero aceptar un domicilio con la tarifa publicada, para asignármelo y realizar la entrega.

**Why this priority**: Es el paso que crea el compromiso de entrega y activa el resto del flujo (ruta, recogida y entrega), por lo que se clasifica como P1.

**Independent Test**: Puede probarse aceptando un domicilio disponible con un domiciliario y verificando que queda asignado a él y deja de aparecer para los demás.

**Acceptance Scenarios**:

1. **Scenario**: Aceptación exitosa
   - **Given** el domiciliario ve la información de un domicilio disponible
   - **When** lo acepta con la tarifa publicada
   - **Then** el sistema lo asigna al domiciliario, cambia su estado a asignado, genera el código de confirmación para el cliente, notifica al cliente y al vendedor y le muestra el mapa con la ruta

2. **Scenario**: Dos domiciliarios aceptan al mismo tiempo
   - **Given** dos domiciliarios intentan aceptar el mismo domicilio casi simultáneamente
   - **When** el sistema procesa ambas solicitudes
   - **Then** el sistema asigna el domicilio únicamente a quien lo aceptó primero e informa al otro que ya no está disponible

### Edge Cases

- ¿Existe un límite de domicilios que un domiciliario puede tener asignados al mismo tiempo?
- ¿Qué ocurre si el domicilio es cancelado por el administrador justo antes de ser aceptado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario aceptar un domicilio disponible con la tarifa publicada; el domiciliario no puede modificar el valor.
- **FR-002**: El sistema DEBE asignar el domicilio de forma exclusiva a un solo domiciliario, controlando aceptaciones simultáneas (RNF08).
- **FR-003**: El sistema DEBE cambiar el estado del domicilio a "asignado" y notificar al cliente y al vendedor de punto fijo.
- **FR-004**: El sistema DEBE habilitar al domiciliario asignado la ruta en el mapa interactivo (RF-22).
- **FR-005**: El sistema DEBE generar, al asignarse el domicilio, el código de confirmación que el cliente verá (RF-25) y el domiciliario ingresará al entregar (RF-24).

### Key Entities

- **Domicilio**: Pasa de "disponible" a "asignado"; su ganancia es la tarifa publicada.
- **Domiciliario**: Usuario que se compromete a realizar la entrega.
- **Código de confirmación**: Valor único generado al asignarse el domicilio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios aceptados quedan asignados a un único domiciliario.
- **SC-002**: El cliente y el vendedor reciben la notificación de asignación en menos de 1 minuto.
- **SC-003**: El domiciliario puede aceptar un domicilio en menos de 10 segundos desde su información.
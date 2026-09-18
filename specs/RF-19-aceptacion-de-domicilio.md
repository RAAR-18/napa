# Feature Specification: Aceptación de domicilio

**Created**: 2026-09-18
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
   - **Then** el sistema lo asigna al domiciliario, cambia su estado a asignado, notifica al cliente y al vendedor y le muestra el mapa con la ruta

2. **Scenario**: Dos domiciliarios aceptan al mismo tiempo
   - **Given** dos domiciliarios intentan aceptar el mismo domicilio casi simultáneamente
   - **When** el sistema procesa ambas solicitudes
   - **Then** el sistema asigna el domicilio únicamente a quien lo aceptó primero e informa al otro que ya no está disponible

3. **Scenario**: Aceptación con ofertas pendientes
   - **Given** el domicilio tiene ofertas de precio pendientes de otros domiciliarios
   - **When** un domiciliario lo acepta con la tarifa publicada
   - **Then** el sistema asigna el domicilio y marca las ofertas pendientes como vencidas, notificando a sus autores

### Edge Cases

- ¿Existe un límite de domicilios que un domiciliario puede tener asignados al mismo tiempo?
- ¿Qué ocurre si el domicilio es cancelado por el administrador justo antes de ser aceptado?
- ¿Puede un domiciliario aceptar un domicilio que había archivado sin restaurarlo primero?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario aceptar un domicilio disponible con la tarifa publicada.
- **FR-002**: El sistema DEBE asignar el domicilio de forma exclusiva a un solo domiciliario, controlando aceptaciones simultáneas (RNF08).
- **FR-003**: El sistema DEBE cambiar el estado del domicilio a "asignado" y notificar al cliente y al vendedor de punto fijo.
- **FR-004**: El sistema DEBE marcar como vencidas las ofertas pendientes de otros domiciliarios cuando el domicilio se asigne.
- **FR-005**: El sistema DEBE habilitar al domiciliario asignado la ruta en el mapa interactivo (RF-22).

### Key Entities

- **Domicilio**: Pasa de "disponible" a "asignado"; su ganancia vigente es la tarifa publicada.
- **Domiciliario**: Usuario que se compromete a realizar la entrega.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios aceptados quedan asignados a un único domiciliario.
- **SC-002**: El cliente y el vendedor reciben la notificación de asignación en menos de 1 minuto.
- **SC-003**: El domiciliario puede aceptar un domicilio en menos de 10 segundos desde su información.

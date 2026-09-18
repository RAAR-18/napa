# Feature Specification: Configuración de tarifa mínima de domicilio

**Created**: 2026-09-18
**Requerimiento funcional**: RF-44
**Historias de usuario relacionadas**: HU-71

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrador define la tarifa mínima de domicilio (Priority: P3)

Como administrador, quiero configurar la tarifa mínima del domicilio, para establecer el valor mínimo permitido para los servicios de entrega.

**Why this priority**: Protege el ingreso mínimo de los domiciliarios y acota las ofertas, pero no es necesaria para el funcionamiento básico del flujo, por lo que se clasifica como P3.

**Independent Test**: Puede probarse definiendo una nueva tarifa mínima y verificando que las ofertas por debajo de ella son rechazadas y que los pedidos nuevos respetan ese piso.

**Acceptance Scenarios**:

1. **Scenario**: Configuración de la tarifa mínima
   - **Given** el administrador accede a la configuración de tarifas
   - **When** define un nuevo valor mínimo y lo guarda
   - **Then** el sistema actualiza la tarifa mínima vigente y la aplica a los cálculos y ofertas posteriores

2. **Scenario**: Oferta por debajo de la tarifa mínima
   - **Given** existe una tarifa mínima vigente
   - **When** un domiciliario ofrece un valor inferior
   - **Then** el sistema rechaza la oferta e indica el valor mínimo (RF-20)

3. **Scenario**: Cambio de tarifa con domicilios ya asignados
   - **Given** existen domicilios asignados con una tarifa anterior
   - **When** el administrador cambia la tarifa mínima
   - **Then** el sistema mantiene la tarifa de los domicilios ya asignados y aplica la nueva únicamente a los nuevos

### Edge Cases

- ¿Qué sucede si el administrador configura una tarifa mínima igual o menor a cero?
- ¿Cómo afecta un cambio de tarifa mínima a los domicilios publicados que aún no han sido asignados?
- ¿Qué ocurre si dos administradores modifican la tarifa mínima al mismo tiempo?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador definir y actualizar el valor mínimo permitido para un servicio de domicilio.
- **FR-002**: El sistema DEBE validar que la tarifa mínima sea un valor positivo mayor a cero.
- **FR-003**: El sistema DEBE aplicar la tarifa mínima como piso de la tarifa publicada de todo domicilio nuevo y de toda oferta de un domiciliario.
- **FR-004**: El sistema DEBE conservar la tarifa de los domicilios ya asignados cuando la tarifa mínima cambie.
- **FR-005**: El sistema DEBE mantener un registro de los cambios de la tarifa mínima, con fecha y valor anterior.

### Key Entities

- **Tarifa mínima de domicilio**: Parámetro global de la plataforma; atributos clave: valor y fecha de última actualización.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador puede actualizar la tarifa mínima en menos de 30 segundos.
- **SC-002**: El 100% de las tarifas publicadas y ofertas registradas después de un cambio cumplen con el nuevo valor.
- **SC-003**: El 0% de las ofertas por debajo de la tarifa mínima se registra.

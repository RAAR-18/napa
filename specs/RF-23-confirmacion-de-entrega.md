# Feature Specification: Confirmación de entrega

**Created**: 2026-09-11
**Requerimiento funcional**: RF-23
**Historias de usuario relacionadas**: HU-42, HU-45

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cerrar formalmente el domicilio validando el código (Priority: P1)

Como domiciliario, quiero confirmar la entrega de un domicilio ingresando el código de confirmación, para cerrar formalmente el domicilio con fecha y hora de entrega registradas.

**Why this priority**: Es el paso que cierra el ciclo completo de domicilio y habilita procesos posteriores (pago, calificación); sin esta confirmación el domicilio queda inconcluso, por lo que se clasifica como P1.

**Independent Test**: Puede probarse de forma independiente llegando al punto de entrega, ingresando el código de confirmación proporcionado por el cliente y verificando que el sistema valida el código y marca el domicilio como entregado.

**Acceptance Scenarios**:

1. **Scenario**: Confirmación exitosa con código válido
   - **Given** el domiciliario llegó a la dirección del cliente con el pedido
   - **When** ingresa el código de confirmación proporcionado por el cliente
   - **Then** el sistema valida el código y marca el domicilio como entregado, registrando fecha y hora

2. **Scenario**: Código de confirmación incorrecto
   - **Given** el domiciliario intenta confirmar la entrega
   - **When** ingresa un código de confirmación que no coincide con el registrado
   - **Then** el sistema rechaza la confirmación y solicita ingresar el código correcto

### Edge Cases

- ¿Qué sucede si el cliente no está disponible para proporcionar el código al momento de la entrega?
- ¿Cuántos intentos fallidos de código se permiten antes de bloquear la confirmación o requerir soporte?
- ¿Qué ocurre si el domiciliario intenta confirmar la entrega de un domicilio que ya fue cancelado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario ingresar el código de confirmación entregado por el cliente para validar la entrega.
- **FR-002**: El sistema DEBE validar que el código ingresado corresponda exactamente al domicilio en curso antes de marcarlo como entregado.
- **FR-003**: El sistema DEBE registrar la fecha y hora exactas en que se confirma la entrega.
- **FR-004**: El sistema DEBE rechazar la confirmación cuando el código ingresado sea incorrecto y permitir reintentarlo.

### Key Entities

- **Domicilio**: Pasa a estado "entregado" tras la validación exitosa del código, con fecha y hora de cierre.
- **Código de confirmación**: Código único asociado a un domicilio, generado al momento de su publicación o aceptación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las entregas confirmadas quedan registradas con fecha y hora exactas.
- **SC-002**: El sistema rechaza el 100% de los intentos de confirmación con un código incorrecto.

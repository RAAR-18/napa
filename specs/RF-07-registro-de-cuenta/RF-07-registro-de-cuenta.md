# Feature Specification: Registro de cuenta

**Created**: 2026-09-11
**Actualizado**: 2026-10-08
**Requerimiento funcional**: RF-07
**Historias de usuario relacionadas**: HU-24
**Relacionado con**: RF-70 (verificación de celular)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Persona interesada crea una cuenta (Priority: P1)

Como persona interesada en usar la plataforma, quiero registrarme indicando mis datos básicos y el rol que voy a desempeñar (cliente, vendedor ambulante, vendedor de punto fijo o domiciliario), para poder acceder a las funcionalidades correspondientes a cada rol.

**Why this priority**: Sin registro no existe acceso a ninguna otra funcionalidad de la plataforma; es el punto de entrada obligatorio para todos los actores.

**Independent Test**: Puede probarse completando el formulario de registro con datos válidos y un correo/teléfono no registrado, y verificando que la cuenta quede creada, que el celular se verifique con el código (RF-70) y que se pueda iniciar sesión con ella.

**Acceptance Scenarios**:

1. **Scenario**: Registro exitoso con datos válidos
   - **Given** el correo o teléfono no está registrado
   - **When** completo el formulario de registro con datos válidos, selecciono mi rol y confirmo la contraseña
   - **Then** el sistema crea mi cuenta pendiente de verificación y envía un código a mi celular (RF-70); al confirmarlo, la cuenta queda activa y se inicia mi sesión

2. **Scenario**: Intento de registro con correo/teléfono ya usado
   - **Given** el correo o teléfono ya está registrado
   - **When** intento completar el formulario de registro con ese dato
   - **Then** el sistema rechaza el registro e indica que el dato ya está en uso

3. **Scenario**: Cuenta sin verificar el celular
   - **Given** me registré pero no he confirmado el código
   - **When** intento iniciar sesión
   - **Then** el sistema no inicia mi sesión y me permite completar la verificación o pedir un código nuevo

### Edge Cases

- ¿Qué ocurre si la contraseña y su confirmación no coinciden?
- ¿Qué sucede si el usuario no selecciona ningún rol durante el registro?
- ¿Cómo maneja el sistema un registro interrumpido a medio completar (datos parciales)?
- ¿Qué ocurre con una cuenta que nunca verifica su celular?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir a una persona registrarse indicando datos básicos (nombre, celular, contraseña; el correo es opcional) y seleccionando su rol (cliente, vendedor ambulante, vendedor de punto fijo o domiciliario).
- **FR-002**: El sistema MUST validar que el celular (y el correo, si se indica) no esté previamente registrado antes de crear la cuenta.
- **FR-003**: El sistema MUST requerir la confirmación de la contraseña y validar que ambas coincidan.
- **FR-004**: El sistema MUST permitir iniciar sesión inmediatamente después de un registro exitoso; al verificarse el celular (RF-70) la sesión se inicia sin pedir de nuevo las credenciales.
- **FR-005**: El sistema MUST habilitar las funcionalidades de la cuenta según el rol elegido; en particular, el vendedor ambulante gestiona entregas directas y reservas con entrega al cliente, y el vendedor de punto fijo gestiona reservas con retiro en el punto y domicilios.
- **FR-006**: El sistema MUST exigir un celular y verificarlo con un código de un solo uso (RF-70); la cuenta queda activa solo después de verificarlo.

### Key Entities

- **Cuenta**: Representa a un usuario registrado, con datos básicos, celular verificado, credenciales y un rol asignado (cliente, vendedor ambulante, vendedor de punto fijo o domiciliario).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los usuarios pueden completar el registro de una cuenta en menos de 2 minutos.
- **SC-002**: El sistema rechaza el 100% de los intentos de registro con correo o teléfono ya utilizados.
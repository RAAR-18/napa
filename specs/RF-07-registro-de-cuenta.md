# Feature Specification: Registro de cuenta

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-07
**Historias de usuario relacionadas**: HU-24

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Persona interesada crea una cuenta (Priority: P1)

Como persona interesada en usar la plataforma, quiero registrarme indicando mis datos básicos y el rol que voy a desempeñar (cliente, vendedor ambulante, vendedor de punto fijo o domiciliario), para poder acceder a las funcionalidades correspondientes a cada rol.

**Why this priority**: Sin registro no existe acceso a ninguna otra funcionalidad de la plataforma; es el punto de entrada obligatorio para todos los actores.

**Independent Test**: Puede probarse completando el formulario de registro con datos válidos y un correo/teléfono no registrado, y verificando que la cuenta quede creada y se pueda iniciar sesión con ella.

**Acceptance Scenarios**:

1. **Scenario**: Registro exitoso con datos válidos
   - **Given** el correo o teléfono no está registrado
   - **When** completo el formulario de registro con datos válidos, selecciono mi rol y confirmo la contraseña
   - **Then** el sistema crea mi cuenta y me permite iniciar sesión

2. **Scenario**: Intento de registro con correo/teléfono ya usado
   - **Given** el correo o teléfono ya está registrado
   - **When** intento completar el formulario de registro con ese dato
   - **Then** el sistema rechaza el registro e indica que el dato ya está en uso

### Edge Cases

- **¿Qué ocurre si la contraseña y su confirmación no coinciden?** El registro se bloquea con un mensaje en el campo de confirmación y no se crea la cuenta.
- **¿Qué sucede si el usuario no selecciona ningún rol durante el registro?** El registro se bloquea: el rol es obligatorio y no hay ningún rol seleccionado por defecto.
- **¿Cómo maneja el sistema un registro interrumpido a medio completar (datos parciales)?** No se crea la cuenta ni se guardan datos parciales en el servidor; el formulario conserva lo digitado en el dispositivo mientras siga abierto.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir a una persona registrarse indicando datos básicos (nombre, correo o teléfono, contraseña) y seleccionando su rol (cliente, vendedor ambulante, vendedor de punto fijo o domiciliario).
- **FR-002**: El sistema MUST validar que el correo electrónico o número de teléfono no esté previamente registrado antes de crear la cuenta.
- **FR-003**: El sistema MUST requerir la confirmación de la contraseña y validar que ambas coincidan.
- **FR-004**: El sistema MUST permitir iniciar sesión inmediatamente después de un registro exitoso.
- **FR-005**: El sistema MUST habilitar las funcionalidades de la cuenta según el rol elegido; en particular, el vendedor ambulante gestiona entregas directas y reservas con entrega al cliente, y el vendedor de punto fijo gestiona reservas con retiro en el punto y domicilios.

### Key Entities

- **Cuenta**: Representa a un usuario registrado, con datos básicos, credenciales y un rol asignado (cliente, vendedor ambulante, vendedor de punto fijo o domiciliario).

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Nombre completo | Sí | Texto de 2 a 80 caracteres. |
| Correo electrónico | Condicional | Obligatorio si no se ingresa teléfono; formato de correo válido y no registrado. |
| Teléfono | Condicional | Obligatorio si no se ingresa correo; 10 dígitos y no registrado. |
| Contraseña | Sí | Mínimo 8 caracteres, con letras y números. |
| Confirmación de contraseña | Sí | Idéntica a la contraseña. |
| Rol | Sí | Uno de: cliente, vendedor ambulante, vendedor de punto fijo o domiciliario. |
| Autorización de tratamiento de datos | Sí | Aceptación expresa (Ley 1581 de 2012). |

**Datos que asigna el sistema**

- Identificador de la cuenta, fecha de creación y estado "activa".
- Contraseña almacenada con hash (RNF01).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los usuarios pueden completar el registro de una cuenta en menos de 2 minutos.
- **SC-002**: El sistema rechaza el 100% de los intentos de registro con correo o teléfono ya utilizados.

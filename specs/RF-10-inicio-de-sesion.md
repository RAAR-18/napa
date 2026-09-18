# Feature Specification: Inicio de sesión

**Created**: 2026-09-11
**Requerimiento funcional**: RF-10
**Historias de usuario relacionadas**: HU-27, HU-29

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Usuario inicia sesión en su cuenta (Priority: P1)

Como usuario registrado, quiero iniciar sesión con mi correo/teléfono y contraseña, para acceder de forma segura a mi cuenta.

**Why this priority**: El inicio de sesión es la puerta de entrada obligatoria a todas las funcionalidades protegidas de la plataforma.

**Independent Test**: Puede probarse ingresando credenciales válidas de una cuenta existente y verificando la redirección al panel correspondiente al rol del usuario.

**Acceptance Scenarios**:

1. **Scenario**: Inicio de sesión exitoso
   - **Given** tengo una cuenta previamente registrada
   - **When** ingreso mis credenciales correctas
   - **Then** el sistema me autentica y me redirige a mi panel según mi rol

2. **Scenario**: Intento de inicio de sesión con credenciales incorrectas
   - **Given** tengo una cuenta previamente registrada
   - **When** ingreso una contraseña incorrecta
   - **Then** el sistema rechaza el acceso y muestra un mensaje de error sin indicar cuál dato es incorrecto

---

### User Story 2 - Administrador inicia sesión en su cuenta (Priority: P1)

Como administrador, quiero iniciar sesión en mi cuenta, para acceder a las funciones administrativas.

**Why this priority**: El acceso administrativo protege operaciones sensibles (moderación, supervisión, configuración) que requieren autenticación segura.

**Independent Test**: Puede probarse ingresando credenciales administrativas válidas y verificando el acceso al panel de administración.

**Acceptance Scenarios**:

1. **Scenario**: Inicio de sesión administrativo exitoso
   - **Given** tengo una cuenta administrativa registrada
   - **When** ingreso mis credenciales correctas
   - **Then** el sistema me autentica y me redirige al panel administrativo

### Edge Cases

- **¿Qué ocurre tras varios intentos consecutivos fallidos de inicio de sesión sobre la misma cuenta?** Tras 5 intentos fallidos consecutivos la cuenta se bloquea por 15 minutos y se notifica al titular.
- **¿Cómo maneja el sistema el inicio de sesión desde un dispositivo o ubicación no habitual?** Con credenciales válidas se permite el acceso y se notifica al titular que hubo un inicio de sesión desde un dispositivo nuevo.
- **¿Qué sucede si la cuenta fue eliminada o inhabilitada y se intenta iniciar sesión con sus credenciales?** Se rechaza con el mensaje "cuenta no disponible", sin revelar más datos; si fue inhabilitada por un administrador se indica que debe contactar a soporte.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir a los usuarios y al administrador iniciar sesión mediante correo/teléfono y contraseña.
- **FR-002**: El sistema MUST validar las credenciales antes de conceder acceso a la cuenta.
- **FR-003**: El sistema MUST redirigir al usuario autenticado al panel correspondiente a su rol (cliente, vendedor, domiciliario o administrador).
- **FR-004**: El sistema MUST limitar los intentos fallidos consecutivos de inicio de sesión para mitigar ataques de fuerza bruta.

### Key Entities

- **Sesión**: Representa el acceso autenticado de un usuario o administrador, vinculada a su cuenta y su rol.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Correo o teléfono | Sí | Debe corresponder a una cuenta activa. |
| Contraseña | Sí | Debe coincidir con la almacenada; 5 fallos consecutivos bloquean la cuenta 15 minutos. |

**Datos que asigna el sistema**

- Sesión y panel según el rol de la cuenta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los usuarios pueden iniciar sesión y llegar a su panel en menos de 5 segundos bajo condiciones normales.
- **SC-002**: El sistema rechaza el 100% de los intentos de inicio de sesión con credenciales incorrectas.

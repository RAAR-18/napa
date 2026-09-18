# Feature Specification: Edición de datos de cuenta

**Created**: 2026-09-11
**Requerimiento funcional**: RF-08
**Historias de usuario relacionadas**: HU-25

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Usuario actualiza su información personal (Priority: P2)

Como usuario registrado, quiero editar mis datos personales (nombre, teléfono, foto, ubicación), para mantener mi información actualizada.

**Why this priority**: Mantiene la calidad de los datos de contacto y ubicación usados por otras funcionalidades (pedidos, domicilios), pero no bloquea el registro ni el inicio de sesión.

**Independent Test**: Puede probarse modificando uno o varios campos del perfil y verificando que los cambios se reflejen en la cuenta.

**Acceptance Scenarios**:

1. **Scenario**: Edición exitosa de datos personales
   - **Given** tengo una cuenta activa
   - **When** modifico uno o varios de mis datos personales
   - **Then** el sistema guarda los cambios y los refleja en mi perfil

2. **Scenario**: Intento de edición con datos inválidos
   - **Given** tengo una cuenta activa
   - **When** ingreso un dato con formato inválido (por ejemplo, un teléfono con caracteres no numéricos)
   - **Then** el sistema rechaza el cambio y muestra un mensaje de error

### Edge Cases

- **¿Qué ocurre si el usuario intenta editar su correo o teléfono a uno ya usado por otra cuenta?** Se rechaza e indica que ya está en uso; se conserva el valor anterior.
- **¿Cómo maneja el sistema la carga de una foto de perfil con formato o tamaño no soportado?** Solo se aceptan imágenes JPG o PNG de hasta 5 MB; cualquier otra se rechaza con un mensaje y se conserva la foto anterior.
- **¿Qué pasa si la ubicación proporcionada no puede ser validada por el proveedor de mapas?** No se guarda esa ubicación y se pide elegirla en el mapa o escribir otra dirección; los demás campos sí se pueden guardar.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir a los usuarios registrados editar su nombre, teléfono, foto de perfil y ubicación.
- **FR-002**: El sistema MUST validar el formato de los datos ingresados antes de guardarlos.
- **FR-003**: El sistema MUST reflejar los cambios en el perfil del usuario inmediatamente después de guardarlos.

### Key Entities

- **Cuenta**: Datos personales editables del usuario (nombre, teléfono, foto, ubicación).

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Nombre | No | Texto de 2 a 80 caracteres. |
| Teléfono | No | 10 dígitos y no registrado en otra cuenta. |
| Foto de perfil | No | Imagen JPG o PNG de hasta 5 MB. |
| Ubicación | No | Dirección o punto en el mapa, dentro de la cobertura de Santa Marta. |

**Datos que asigna el sistema**

- Fecha de última actualización.

**Datos que se muestran o filtran**

- Debe modificarse al menos un campo para poder guardar.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los cambios en los datos personales se reflejan en el perfil en menos de 5 segundos tras guardarse.
- **SC-002**: El sistema rechaza el 100% de los intentos de edición con datos en formato inválido.

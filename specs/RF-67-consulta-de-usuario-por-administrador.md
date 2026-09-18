# Feature Specification: Consulta de usuario por administrador

**Created**: 2026-09-11
**Requerimiento funcional**: RF-67
**Historias de usuario relacionadas**: HU-101

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Revisar la información de un usuario (Priority: P2)

Como administrador, quiero consultar un usuario, para revisar su información.

**Why this priority**: Es una funcionalidad de soporte y supervisión administrativa necesaria para atender reportes o disputas; es importante para la gestión de la plataforma aunque no forma parte del flujo transaccional principal de clientes, vendedores o domiciliarios.

**Independent Test**: Puede probarse buscando un usuario registrado desde el panel de administración y verificando que se muestra su información completa.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa de un usuario
   - **Given** existe un usuario registrado en la plataforma
   - **When** el administrador lo busca y selecciona desde el panel de usuarios
   - **Then** el sistema muestra su información personal, rol y estado de la cuenta

---

### Edge Cases

- **¿Qué ocurre si el administrador busca un usuario que fue eliminado previamente?** Se muestra en solo lectura como "eliminado", con los datos anonimizados según la política de retención.
- **¿Se muestra al administrador el historial de actividad relevante del usuario (pedidos, domicilios, reportes)?** Sí, un resumen: número de pedidos, domicilios y reportes recibidos, y calificación promedio; sin el contenido de las conversaciones.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al administrador buscar y consultar la información de un usuario registrado.
- **FR-002**: El sistema MUST mostrar el rol y el estado de la cuenta del usuario consultado.

### Key Entities *(include if feature involves data)*

- **Usuario**: Representa a cualquier persona registrada en la plataforma (cliente, vendedor, domiciliario); atributos clave: nombre, contacto, rol, estado de la cuenta, fecha de registro.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Búsqueda | Sí | Nombre, correo o teléfono del usuario. |

**Datos que se muestran o filtran**

- Datos del usuario, rol y estado de la cuenta.
- Resumen de actividad: pedidos, domicilios, reportes y calificación promedio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador puede localizar y consultar la información de un usuario en menos de 10 segundos.
- **SC-002**: El 100% de las consultas realizadas muestran el rol y el estado de cuenta actualizados del usuario.

# Feature Specification: Creación de emprendimiento

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-28
**Historias de usuario relacionadas**: HU-53

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Publicar un emprendimiento en la plataforma (Priority: P1)

Como vendedor, quiero crear mi emprendimiento indicando nombre, tipo de producto, ubicación y descripción, para que los clientes puedan encontrarme en la plataforma.

**Why this priority**: Sin un emprendimiento creado, el vendedor no puede publicar productos ni recibir pedidos; es el punto de entrada obligatorio para todo el flujo de ventas.

**Independent Test**: Puede probarse de forma independiente registrando un usuario con rol vendedor, completando el formulario de creación de emprendimiento y verificando que el emprendimiento queda visible en el listado público, sin depender de que existan productos o pedidos.

**Acceptance Scenarios**:

1. **Scenario**: Creación exitosa de emprendimiento
   - **Given** estoy registrado como vendedor y no tengo un emprendimiento activo
   - **When** completo el formulario de creación con nombre, tipo de producto, ubicación y descripción válidos
   - **Then** el sistema publica el emprendimiento y lo hace visible para los clientes

2. **Scenario**: Intento de crear un segundo emprendimiento
   - **Given** ya tengo un emprendimiento activo creado
   - **When** intento crear otro emprendimiento
   - **Then** el sistema impide la creación y me informa que ya cuento con un emprendimiento activo

### Edge Cases

- **¿Qué sucede si el vendedor deja campos obligatorios (nombre, ubicación) vacíos?** No se crea el emprendimiento y se señalan los campos obligatorios.
- **¿Cómo maneja el sistema una ubicación inválida o fuera de la cobertura del servicio?** La rechaza y pide una ubicación dentro de la cobertura de Santa Marta.
- **¿Qué ocurre si el vendedor intenta crear un emprendimiento con un nombre ya usado por otro emprendimiento en la misma zona?** Se rechaza: el nombre debe ser único dentro del mismo barrio; en barrios distintos puede repetirse.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir a un usuario con rol vendedor crear un emprendimiento indicando nombre, tipo de producto, ubicación y descripción.
- **FR-002**: El sistema DEBE validar que los campos obligatorios estén completos antes de publicar el emprendimiento.
- **FR-003**: El sistema DEBE impedir que un vendedor cree un nuevo emprendimiento mientras ya tenga uno activo.
- **FR-004**: El sistema DEBE publicar el emprendimiento como visible para los clientes inmediatamente después de su creación.
- **FR-005**: El sistema DEBE interpretar la ubicación según el rol del vendedor: la dirección del punto fijo para un vendedor de punto fijo, o la zona habitual de venta para un vendedor ambulante.
- **FR-006**: El sistema DEBE asociar al emprendimiento el tipo de vendedor (ambulante o de punto fijo) definido en su cuenta, sin solicitarlo de nuevo en el formulario.
- **FR-007**: El sistema DEBE crear el emprendimiento en estado "abierto".

### Key Entities

- **Emprendimiento**: Representa el negocio del vendedor en la plataforma; incluye nombre, tipo de producto, ubicación, descripción, tipo de vendedor (ambulante o de punto fijo) y estado (activo/inactivo).
- **Vendedor**: Usuario propietario de un emprendimiento.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Nombre del emprendimiento | Sí | Texto de 3 a 60 caracteres; único dentro del mismo barrio. |
| Tipo de producto | Sí | Una categoría de la lista: frutas y verduras, pescado y mariscos, alimentos preparados, bebidas, snacks u otros. |
| Ubicación | Sí | Punto fijo: dirección exacta y punto en el mapa. Ambulante: barrio o zona habitual de venta. Dentro de la cobertura de Santa Marta. |
| Descripción | No | Texto de hasta 300 caracteres. |
| Horario de atención | No | Hora de apertura y de cierre. |

**Datos que asigna el sistema**

- Vendedor propietario y tipo de vendedor (ambulante o de punto fijo), tomado de su cuenta.
- Estado "abierto" y fecha de creación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un vendedor puede completar la creación de su emprendimiento en menos de 3 minutos.
- **SC-002**: El 100% de los emprendimientos creados con datos válidos quedan visibles para los clientes de forma inmediata.

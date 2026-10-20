# Feature Specification: Consultar calificaciones y comentarios

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-02
**Historias de usuario relacionadas**: HU-08, HU-09, HU-10, HU-11, HU-12, HU-13, HU-14, HU-15, HU-16, HU-17, HU-18

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cliente consulta reputación antes de comprar (Priority: P1)

Como cliente, quiero consultar las calificaciones y comentarios de un emprendimiento y de sus productos, para conocer la experiencia de otros usuarios antes de decidir mi compra.

**Why this priority**: La consulta de reputación influye directamente en la decisión de compra, que es el flujo económico central de la plataforma.

**Independent Test**: Puede probarse ingresando al perfil de un emprendimiento o producto con calificaciones existentes y verificando que se listen correctamente.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de calificaciones de un emprendimiento
   - **Given** un emprendimiento tiene calificaciones registradas
   - **When** ingreso a su perfil
   - **Then** el sistema muestra el promedio de calificación y los comentarios asociados

2. **Scenario**: Consulta de calificaciones de un producto
   - **Given** un producto tiene calificaciones registradas
   - **When** consulto su ficha
   - **Then** el sistema muestra el promedio de calificación y los comentarios del producto

---

### User Story 2 - Cliente consulta reputación de domiciliarios y clientes (Priority: P2)

Como cliente, quiero consultar las calificaciones y comentarios de un domiciliario y de otro cliente, para conocer la calidad de su servicio o su reputación dentro de la plataforma.

**Why this priority**: Aporta confianza adicional en el ciclo de entrega, pero es secundaria frente a la decisión de compra inicial.

**Independent Test**: Puede probarse consultando el perfil de un domiciliario asignado a un pedido y verificando que muestre sus calificaciones históricas.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de calificaciones de un domiciliario
   - **Given** un domiciliario tiene calificaciones registradas
   - **When** consulto su perfil desde mi pedido o domicilio
   - **Then** el sistema muestra su promedio de calificación y comentarios

---

### User Story 3 - Vendedor consulta reputación de su emprendimiento y contrapartes (Priority: P2)

Como vendedor, quiero consultar las calificaciones y comentarios de mi emprendimiento, mis productos, los domiciliarios (si soy vendedor de punto fijo) y los clientes con los que interactúo, para conocer la percepción de los usuarios sobre mi negocio y sus contrapartes.

**Why this priority**: Ayuda al vendedor a mejorar su servicio, pero no es indispensable para la operación diaria de venta.

**Independent Test**: Puede probarse ingresando al panel de reputación del emprendimiento y verificando que se muestren calificaciones de emprendimiento, productos, domiciliarios y clientes.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de calificaciones del propio emprendimiento
   - **Given** mi emprendimiento tiene calificaciones registradas
   - **When** consulto la sección de reputación
   - **Then** el sistema muestra las calificaciones y comentarios recibidos

---

### User Story 4 - Domiciliario consulta reputación de emprendimientos y clientes (Priority: P2)

Como domiciliario, quiero consultar las calificaciones y comentarios de emprendimientos, otros domiciliarios y clientes, para conocer las experiencias previas antes de aceptar un domicilio.

**Why this priority**: Informa la decisión de aceptar o no un domicilio, pero es un flujo de apoyo, no bloqueante.

**Independent Test**: Puede probarse consultando el perfil de un emprendimiento o cliente asociado a un domicilio disponible.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de calificaciones de un emprendimiento antes de aceptar un domicilio
   - **Given** un emprendimiento tiene calificaciones registradas
   - **When** consulto su perfil desde el listado de domicilios disponibles
   - **Then** el sistema muestra su promedio de calificación y comentarios

### Edge Cases

- ¿Qué muestra el sistema cuando una entidad aún no tiene calificaciones registradas?
- ¿Cómo se manejan comentarios reportados o eliminados dentro del promedio mostrado?
- ¿Qué ocurre si el usuario consulta calificaciones de una entidad inactiva o eliminada?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al cliente consultar las calificaciones y comentarios de emprendimientos y productos.
- **FR-002**: El sistema MUST permitir al cliente consultar las calificaciones y comentarios de domiciliarios y de otros clientes.
- **FR-003**: El sistema MUST permitir al vendedor consultar las calificaciones y comentarios de su emprendimiento, sus productos, los domiciliarios (en el caso del vendedor de punto fijo) y los clientes involucrados en sus pedidos.
- **FR-004**: El sistema MUST permitir al domiciliario consultar las calificaciones y comentarios de emprendimientos, otros domiciliarios y clientes.
- **FR-005**: El sistema MUST calcular y mostrar un promedio de calificación por entidad junto con el listado de comentarios asociados.

### Key Entities

- **Calificación**: Registro numérico y textual asociado a una entidad calificada (emprendimiento, producto, domiciliario, cliente).
- **Perfil de reputación**: Vista agregada del promedio de calificación y los comentarios de una entidad.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El usuario puede consultar el promedio de calificación de una entidad en menos de 2 segundos.
- **SC-002**: El 100% de las entidades con calificaciones registradas muestran un promedio consistente con los valores individuales almacenados.
- **SC-003**: El 90% de los usuarios encuestados reportan que la información de reputación mostrada es clara y útil para su decisión.

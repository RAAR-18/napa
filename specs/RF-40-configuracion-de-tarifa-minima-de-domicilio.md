# Feature Specification: Configuración de tarifa mínima de domicilio

**Created**: 2026-09-11
**Requerimiento funcional**: RF-40
**Historias de usuario relacionadas**: HU-63

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrador define la tarifa mínima de entrega (Priority: P3)

Como administrador, quiero configurar la tarifa mínima del domicilio, para establecer el valor mínimo permitido para los servicios de entrega.

**Why this priority**: Es un parámetro de gobierno de la plataforma que protege el ingreso mínimo de los domiciliarios; no es requerido para el funcionamiento básico del flujo de pedidos y entregas, por lo que se clasifica como P3.

**Independent Test**: Puede probarse ingresando al panel administrativo, definiendo un nuevo valor de tarifa mínima y verificando que los domicilios publicados posteriormente respetan ese valor.

**Acceptance Scenarios**:

1. **Scenario**: Configuración exitosa de la tarifa mínima
   - **Given** el administrador accede a la configuración de tarifas de domicilio
   - **When** define un nuevo valor mínimo permitido y lo guarda
   - **Then** el sistema actualiza la tarifa mínima vigente y la aplica a los domicilios publicados a partir de ese momento

2. **Scenario**: Intento de publicar un domicilio por debajo de la tarifa mínima
   - **Given** existe una tarifa mínima de domicilio configurada
   - **When** un vendedor intenta publicar un domicilio con una tarifa inferior a la mínima
   - **Then** el sistema rechaza la publicación e indica el valor mínimo permitido

### Edge Cases

- ¿Qué sucede si el administrador configura una tarifa mínima igual o menor a cero?
- ¿Cómo afecta el cambio de la tarifa mínima a los domicilios que ya estaban publicados con una tarifa inferior a la nueva?
- ¿Qué ocurre si dos administradores intentan modificar la tarifa mínima al mismo tiempo?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador definir y actualizar el valor mínimo permitido para la tarifa de un servicio de entrega.
- **FR-002**: El sistema DEBE validar que la tarifa mínima configurada sea un valor positivo mayor a cero.
- **FR-003**: El sistema DEBE impedir la publicación de un domicilio con una tarifa inferior a la tarifa mínima vigente.
- **FR-004**: El sistema DEBE mantener un registro de los cambios realizados sobre la tarifa mínima, incluyendo fecha y valor anterior.

### Key Entities

- **Tarifa mínima de domicilio**: Parámetro global de la plataforma que define el valor mínimo permitido para un servicio de entrega; atributos clave: valor, fecha de última actualización.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador puede actualizar la tarifa mínima en menos de 30 segundos.
- **SC-002**: El 100% de los domicilios publicados después de un cambio de tarifa mínima cumplen con el nuevo valor.
- **SC-003**: El 0% de los intentos de publicar un domicilio por debajo del mínimo configurado se completa exitosamente.

# Feature Specification: Publicación de domicilio

**Created**: 2026-09-11
**Requerimiento funcional**: RF-21
**Historias de usuario relacionadas**: HU-40

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Poner a disposición un servicio de entrega (Priority: P1)

Como vendedor, quiero publicar un domicilio, para poner un servicio de entrega a disposición de los domiciliarios.

**Why this priority**: Es el punto de partida de todo el ciclo de domicilio; sin publicación no hay domicilios disponibles para aceptar, por lo que se clasifica como P1.

**Independent Test**: Puede probarse de forma independiente registrando un pedido listo para despachar, publicando un domicilio con su dirección de entrega, y verificando que aparece en la lista de disponibles para los domiciliarios.

**Acceptance Scenarios**:

1. **Scenario**: Publicación de un domicilio para un pedido listo
   - **Given** el vendedor tiene un pedido listo para despachar
   - **When** registra el domicilio con la dirección de entrega
   - **Then** el sistema lo publica como domicilio disponible para los domiciliarios

2. **Scenario**: Intento de publicar un domicilio sin pedido asociado
   - **Given** el vendedor intenta publicar un domicilio sin seleccionar un pedido listo para despachar
   - **When** confirma la publicación
   - **Then** el sistema rechaza la operación y solicita asociar un pedido válido

### Edge Cases

- ¿Qué sucede si el vendedor intenta publicar dos domicilios para el mismo pedido?
- ¿Cómo se comporta el sistema si la dirección de entrega ingresada es incompleta o inválida?
- ¿Qué pasa si el pedido asociado es cancelado antes de que el domicilio sea aceptado?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor registrar un domicilio para un pedido que se encuentre listo para despachar, indicando la dirección de entrega.
- **FR-002**: El sistema DEBE publicar automáticamente el domicilio registrado en la lista de domicilios disponibles para los domiciliarios.
- **FR-003**: El sistema DEBE impedir la publicación de un domicilio sin un pedido válido asociado.

### Key Entities

- **Domicilio**: Nace en estado "disponible" al ser publicado, vinculado a un pedido y a una dirección de entrega.
- **Pedido**: Debe estar en estado "listo para despachar" para poder generar un domicilio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios publicados aparecen en la lista de disponibles en menos de 5 segundos.
- **SC-002**: El sistema rechaza el 100% de los intentos de publicar un domicilio sin un pedido válido asociado.

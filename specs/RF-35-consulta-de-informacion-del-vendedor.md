# Feature Specification: Consulta de información del vendedor

**Created**: 2026-09-11
**Requerimiento funcional**: RF-35
**Historias de usuario relacionadas**: HU-60

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conocer quién está detrás del emprendimiento (Priority: P3)

Como cliente, quiero ver la información del vendedor, para conocer quién está detrás del emprendimiento antes de comprarle.

**Why this priority**: Aporta confianza adicional al cliente pero no es indispensable para completar una compra; se prioriza después de las funciones core de descubrimiento y compra.

**Independent Test**: Puede probarse de forma independiente consultando un emprendimiento y accediendo a la información pública de su vendedor, sin depender de que existan pedidos o calificaciones previas.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa de información del vendedor
   - **Given** estoy consultando el detalle de un emprendimiento
   - **When** accedo a la información del vendedor asociado
   - **Then** el sistema muestra sus datos públicos (nombre y datos de contacto habilitados para clientes)

2. **Scenario**: Vendedor sin datos públicos adicionales
   - **Given** el vendedor no ha completado información adicional de su perfil
   - **When** consulto su información desde el emprendimiento
   - **Then** el sistema muestra únicamente los datos mínimos disponibles sin generar error

### Edge Cases

- **¿Qué información del vendedor se considera pública y cuál se mantiene privada frente al cliente?** Nombre, foto, tipo de vendedor y calificación; el teléfono solo se comparte, por el canal de contacto, cuando hay un pedido aceptado; el correo y el documento son privados.
- **¿Qué sucede si el vendedor tiene más de un emprendimiento?** No aplica: un vendedor tiene un solo emprendimiento activo (RF-28).
- **¿Cómo se maneja la consulta si la cuenta del vendedor fue eliminada pero el emprendimiento aún es visible?** Al eliminar la cuenta, el emprendimiento se oculta, por lo que no puede consultarse.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar la información pública del vendedor asociado a un emprendimiento.
- **FR-002**: El sistema DEBE restringir la información mostrada a los datos que el vendedor haya autorizado como públicos.

### Key Entities

- **Vendedor**: Usuario propietario del emprendimiento; expone un subconjunto de su información como pública para los clientes.

### Data Rules

**Datos que ingresa el usuario**: Ninguno.

**Datos que se muestran o filtran**

- Nombre, foto, tipo de vendedor y calificación.
- Se ocultan correo, documento y teléfono.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La información pública del vendedor se muestra en menos de 3 segundos desde la solicitud.
- **SC-002**: El 100% de los datos privados del vendedor permanecen ocultos frente a la consulta del cliente.

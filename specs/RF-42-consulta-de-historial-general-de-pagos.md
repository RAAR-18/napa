# Feature Specification: Consulta de historial general de pagos

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-42
**Historias de usuario relacionadas**: HU-71

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Administrador supervisa las transacciones de la plataforma (Priority: P3)

Como administrador, quiero consultar el historial de pagos, para supervisar las transacciones de la plataforma.

**Why this priority**: Es una capacidad de supervisión y control administrativo, valiosa para la operación pero no requerida para que los flujos de compra y entrega funcionen; se clasifica como P3.

**Independent Test**: Puede probarse ingresando al panel administrativo, accediendo al historial general de pagos y verificando que se muestran las transacciones de todos los usuarios de la plataforma.

**Acceptance Scenarios**:

1. **Scenario**: Consulta exitosa del historial general de pagos
   - **Given** existen pagos registrados de distintos clientes, vendedores y domiciliarios
   - **When** el administrador accede al historial general de pagos
   - **Then** el sistema muestra el listado completo de transacciones con fecha, monto, tipo, estado y usuarios involucrados

2. **Scenario**: Filtrado del historial general de pagos
   - **Given** el administrador está consultando el historial general de pagos
   - **When** aplica un filtro por rango de fechas, estado o tipo de transacción
   - **Then** el sistema muestra únicamente los pagos que cumplen con el filtro aplicado

### Edge Cases

- **¿Qué ocurre si el volumen de transacciones es muy alto? ¿El sistema pagina o limita los resultados por defecto?** Se pagina de a 20 y se puede filtrar por fecha, estado, tipo y método.
- **¿Cómo se muestran los pagos que quedaron en estado pendiente de confirmación por un tiempo prolongado?** Se destacan como "pendiente de confirmación", con su antigüedad; los de más de 48 horas se resaltan para seguimiento.
- **¿Qué sucede si el administrador aplica un filtro que no arroja resultados?** Muestra "Sin resultados" y la opción de limpiar los filtros.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al administrador consultar el historial de todos los pagos registrados en la plataforma.
- **FR-002**: El sistema DEBE mostrar en cada registro la fecha, el monto, el tipo de transacción (pago de pedido o de domicilio), el estado y los usuarios involucrados.
- **FR-003**: El sistema DEBE permitir al administrador filtrar el historial general de pagos por fecha, estado o tipo de transacción.
- **FR-004**: El sistema DEBE restringir el acceso al historial general de pagos exclusivamente al rol administrador.

### Key Entities

- **Pago**: Representa una transacción registrada en la plataforma; atributos clave: monto, fecha, estado, tipo, usuarios involucrados.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. Filtros opcionales: fecha, estado, tipo y método de pago.

**Datos que se muestran o filtran**

- Fecha, monto, método, tipo (pedido o domicilio), estado y usuarios involucrados; paginado de a 20.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador puede acceder al historial general de pagos en menos de 3 segundos.
- **SC-002**: El 100% de las transacciones registradas en la plataforma son visibles en el historial general.
- **SC-003**: El 0% de los usuarios sin rol administrador logra acceder al historial general de pagos.

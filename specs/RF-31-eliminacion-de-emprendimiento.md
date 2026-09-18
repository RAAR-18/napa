# Feature Specification: Eliminación de emprendimiento

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-31
**Historias de usuario relacionadas**: HU-56

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Retirar un emprendimiento inactivo (Priority: P3)

Como vendedor, quiero eliminar mi emprendimiento si dejo de operar, para que ya no aparezca visible a los clientes.

**Why this priority**: Es una acción de cierre de operación, poco frecuente y no crítica para el flujo diario de ventas; se prioriza después de las funciones de creación y edición.

**Independent Test**: Puede probarse de forma independiente creando un emprendimiento sin pedidos pendientes, eliminándolo y verificando que deja de aparecer en el listado público de emprendimientos.

**Acceptance Scenarios**:

1. **Scenario**: Eliminación exitosa sin pedidos pendientes
   - **Given** tengo un emprendimiento sin pedidos pendientes
   - **When** confirmo la eliminación
   - **Then** el sistema oculta el emprendimiento y su catálogo del listado visible para los clientes

2. **Scenario**: Intento de eliminación con pedidos pendientes
   - **Given** tengo un emprendimiento con al menos un pedido pendiente
   - **When** intento eliminarlo
   - **Then** el sistema impide la eliminación y me informa que debo resolver los pedidos pendientes primero

### Edge Cases

- **¿Qué sucede si el vendedor intenta eliminar un emprendimiento con domicilios en curso pero sin pedidos pendientes?** Lo impide: los domicilios en curso deben finalizar o cancelarse antes de eliminar.
- **¿Cómo se maneja el historial de pedidos y calificaciones asociados a un emprendimiento eliminado?** Se conserva para trazabilidad; las calificaciones permanecen asociadas a un "emprendimiento eliminado".
- **¿Puede el vendedor crear un nuevo emprendimiento inmediatamente después de eliminar el anterior?** Sí, una vez confirmada la eliminación.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor eliminar su emprendimiento únicamente cuando no existan pedidos pendientes (incluidas las reservas) asociados.
- **FR-002**: El sistema DEBE solicitar confirmación explícita antes de ejecutar la eliminación.
- **FR-003**: El sistema DEBE ocultar el emprendimiento y su catálogo de productos de la vista pública inmediatamente después de la eliminación.

### Key Entities

- **Emprendimiento**: Entidad eliminada lógicamente (oculta), conservando su historial para trazabilidad.
- **Pedido**: Determina si la eliminación está permitida según su estado (pendiente o no).

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Confirmación de eliminación | Sí | Aceptación expresa; solo si no hay pedidos, reservas ni domicilios en curso. |

**Datos que asigna el sistema**

- Emprendimiento y productos ocultos; historial conservado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los intentos de eliminación con pedidos pendientes son bloqueados por el sistema.
- **SC-002**: Un emprendimiento eliminado deja de ser visible para los clientes en menos de 3 segundos tras la confirmación.

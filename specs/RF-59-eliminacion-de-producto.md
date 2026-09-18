# Feature Specification: Eliminación de producto

**Created**: 2026-09-11
**Requerimiento funcional**: RF-59
**Historias de usuario relacionadas**: HU-91

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Retirar un producto del catálogo (Priority: P2)

Como vendedor, quiero eliminar un producto que ya no voy a vender, para que no siga apareciendo en mi catálogo.

**Why this priority**: Mantiene el catálogo limpio y confiable, pero no es tan crítico como registrar o actualizar el inventario de productos activos.

**Independent Test**: Puede probarse eliminando un producto sin pedidos pendientes y verificando que desaparece del catálogo visible para los clientes.

**Acceptance Scenarios**:

1. **Scenario**: Eliminación exitosa
   - **Given** el vendedor tiene un producto publicado sin pedidos pendientes
   - **When** confirma la eliminación del producto
   - **Then** el sistema lo retira del catálogo visible para todos los clientes

2. **Scenario**: Intento de eliminar producto con pedidos pendientes
   - **Given** el vendedor tiene un producto con al menos un pedido pendiente
   - **When** intenta eliminarlo
   - **Then** el sistema rechaza la eliminación e informa que existen pedidos pendientes asociados

---

### Edge Cases

- **¿Qué ocurre con el historial de pedidos ya finalizados que incluían el producto eliminado?** Se conserva con el nombre y el precio históricos del producto.
- **¿Puede el vendedor cancelar la eliminación antes de confirmarla?** Sí: el diálogo de confirmación tiene la opción de cancelar y hasta confirmar no cambia nada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al vendedor eliminar un producto publicado que no tenga pedidos pendientes.
- **FR-002**: El sistema MUST impedir la eliminación de un producto con pedidos pendientes asociados.
- **FR-003**: El sistema MUST solicitar confirmación explícita antes de eliminar un producto.
- **FR-004**: El sistema MUST retirar el producto eliminado del catálogo visible para todos los clientes de forma inmediata.

### Key Entities *(include if feature involves data)*

- **Producto**: Ver definición en RF-55.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Confirmación de eliminación | Sí | Aceptación expresa; solo si el producto no tiene pedidos pendientes. |

**Datos que asigna el sistema**

- Retiro inmediato del catálogo; historial de pedidos conservado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un producto eliminado deja de ser visible para los clientes en menos de 5 segundos.
- **SC-002**: El sistema rechaza el 100% de los intentos de eliminación de productos con pedidos pendientes.

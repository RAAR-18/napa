# Feature Specification: Consulta de reportes por administrador

**Created**: 2026-09-11
**Requerimiento funcional**: RF-62
**Historias de usuario relacionadas**: HU-96

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Revisar reportes informados por los usuarios (Priority: P2)

Como administrador, quiero consultar los reportes, para identificar y revisar los problemas informados por los usuarios.

**Why this priority**: Depende de que existan reportes creados (RF-61) y complementa la supervisión de la plataforma; no es parte del flujo transaccional diario de clientes, vendedores o domiciliarios.

**Independent Test**: Puede probarse generando un reporte desde cualquier rol y verificando que el administrador puede consultarlo desde su panel.

**Acceptance Scenarios**:

1. **Scenario**: Consulta del listado de reportes
   - **Given** existen reportes registrados en el sistema
   - **When** el administrador ingresa a la sección de reportes
   - **Then** el sistema muestra el listado de reportes con su estado, autor y motivo

2. **Scenario**: Consulta del detalle de un reporte
   - **Given** el administrador selecciona un reporte del listado
   - **When** abre su detalle
   - **Then** el sistema muestra la descripción completa y la entidad relacionada (pedido, domicilio o usuario)

---

### Edge Cases

- **¿Cómo se priorizan visualmente los reportes más antiguos o urgentes?** Los pendientes aparecen primero, los más antiguos arriba, y se resaltan los que llevan más de 48 horas sin resolverse.
- **¿Qué ocurre si el administrador filtra por un estado que no tiene reportes asociados?** Muestra "Sin resultados" y la opción de limpiar el filtro.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al administrador consultar el listado de reportes registrados.
- **FR-002**: El sistema MUST permitir al administrador consultar el detalle completo de un reporte, incluyendo autor, motivo, descripción y entidad relacionada.
- **FR-003**: El sistema MUST permitir filtrar el listado de reportes por estado.

### Key Entities *(include if feature involves data)*

- **Reporte**: Ver definición en RF-61.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. Filtro opcional: estado (pendiente o resuelto).

**Datos que se muestran o filtran**

- Autor, motivo, descripción, entidad relacionada, fecha y estado; los pendientes primero y los de más de 48 horas resaltados.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El administrador puede acceder al listado completo de reportes en menos de 2 segundos.
- **SC-002**: El 100% de los reportes registrados son consultables desde el panel administrativo.

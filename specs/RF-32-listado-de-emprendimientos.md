# Feature Specification: Listado de emprendimientos

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-32
**Historias de usuario relacionadas**: HU-57

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Explorar emprendimientos disponibles (Priority: P1)

Como cliente, quiero ver el listado de emprendimientos disponibles en la plataforma, para explorar qué vendedores existen y cuáles están cerca de mí.

**Why this priority**: Es el punto de entrada principal del flujo de compra del cliente; sin esta función el cliente no puede descubrir a los vendedores.

**Independent Test**: Puede probarse de forma independiente registrando al menos un emprendimiento activo y verificando que aparece en el listado al ingresar como cliente a la sección de emprendimientos.

**Acceptance Scenarios**:

1. **Scenario**: Listado de emprendimientos activos
   - **Given** existen emprendimientos activos registrados
   - **When** ingreso a la sección de emprendimientos
   - **Then** el sistema muestra el listado con nombre, categoría, tipo de vendedor (ambulante o de punto fijo), estado (abierto o cerrado) y ubicación de cada uno

2. **Scenario**: Sin emprendimientos disponibles
   - **Given** no existe ningún emprendimiento activo registrado en la plataforma
   - **When** ingreso a la sección de emprendimientos
   - **Then** el sistema muestra un mensaje indicando que no hay emprendimientos disponibles

### Edge Cases

<<<<<<<< HEAD:specs/RF-32-listado-de-emprendimientos.md
- **¿Qué sucede si un emprendimiento fue eliminado mientras el cliente tenía el listado abierto?** Desaparece al actualizar la lista; si el cliente intenta abrirlo, se le informa que ya no está disponible.
- **¿Cómo se muestra un emprendimiento que temporalmente está fuera de horario de atención?** Se muestra con la etiqueta "Cerrado"; el cliente puede consultarlo, pero solo puede hacer reservas.
- **¿Qué ocurre si hay una gran cantidad de emprendimientos registrados? ¿se pagina el resultado?** El listado se pagina de a 20 y se ordena por cercanía.
========
- ¿Qué sucede si un emprendimiento fue eliminado mientras el cliente tenía el listado abierto?
- ¿Cómo se muestra un emprendimiento que temporalmente está fuera de horario de atención? (se calcula con la hora de apertura y la de cierre)
- ¿Qué ocurre si hay una gran cantidad de emprendimientos registrados? ¿se pagina el resultado?
>>>>>>>> 83c9925992e14d4ed876332b5c1239a1f83fd6ba:specs/RF-31-listado-de-emprendimientos/RF-31-listado-de-emprendimientos.md

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al cliente consultar el listado de emprendimientos activos registrados en la plataforma.
<<<<<<<< HEAD:specs/RF-32-listado-de-emprendimientos.md
- **FR-002**: El sistema DEBE mostrar, para cada emprendimiento del listado, su nombre, categoría, tipo de vendedor (ambulante o de punto fijo), estado (abierto o cerrado) y ubicación.
========
- **FR-002**: El sistema DEBE mostrar, para cada emprendimiento del listado, su nombre, categoría (de una lista fija), tipo de vendedor (ambulante o de punto fijo) y ubicación.
>>>>>>>> 83c9925992e14d4ed876332b5c1239a1f83fd6ba:specs/RF-31-listado-de-emprendimientos/RF-31-listado-de-emprendimientos.md
- **FR-003**: El sistema DEBE excluir del listado los emprendimientos inactivos o eliminados.
- **FR-004**: El sistema DEBE permitir ordenar el listado por cercanía y filtrarlo por tipo de vendedor.

### Key Entities

<<<<<<<< HEAD:specs/RF-32-listado-de-emprendimientos.md
- **Emprendimiento**: Representa el negocio del vendedor; se muestra en el listado con nombre, categoría, tipo de vendedor (ambulante o de punto fijo), estado (abierto o cerrado) y ubicación.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. Opciones: ordenar por cercanía y filtrar por tipo de vendedor.

**Datos que se muestran o filtran**

- Nombre, categoría, tipo de vendedor, estado (abierto o cerrado), ubicación y distancia.
- Paginado de a 20.
========
- **Emprendimiento**: Representa el negocio del vendedor; se muestra en el listado con nombre, categoría (de una lista fija), tipo de vendedor (ambulante o de punto fijo) y ubicación.
>>>>>>>> 83c9925992e14d4ed876332b5c1239a1f83fd6ba:specs/RF-31-listado-de-emprendimientos/RF-31-listado-de-emprendimientos.md

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El listado de emprendimientos se carga en menos de 3 segundos bajo condiciones normales de operación.
- **SC-002**: El 100% de los emprendimientos activos son visibles en el listado del cliente.

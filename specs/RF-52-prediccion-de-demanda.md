# Feature Specification: Predicción de demanda

**Created**: 2026-09-11
**Requerimiento funcional**: RF-52
**Historias de usuario relacionadas**: HU-78

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Estimar cuánto producto preparar (Priority: P3)

Como vendedor, quiero ver una estimación de cuánto producto podría vender según pedidos anticipados e histórico de ventas, para comprar o preparar solo lo necesario y reducir pérdidas por productos perecederos.

**Why this priority**: Aporta un valor diferencial importante para reducir desperdicio, pero depende de que ya exista un historial mínimo de ventas y pedidos (RF-41, RF-47, RF-50), por lo que solo tiene sentido una vez la operación básica está funcionando.

**Independent Test**: Puede probarse con una cuenta de vendedor que tenga historial de ventas registrado, consultando la sección de predicción de demanda y verificando que se muestra una estimación.

**Acceptance Scenarios**:

1. **Scenario**: Predicción disponible con historial suficiente
   - **Given** el vendedor cuenta con un historial mínimo de ventas y pedidos registrados
   - **When** consulta la sección de predicción de demanda de un producto
   - **Then** el sistema muestra una estimación basada en inteligencia artificial sobre el comportamiento esperado de ventas

2. **Scenario**: Historial insuficiente
   - **Given** el vendedor tiene un producto recién publicado sin historial de ventas
   - **When** consulta la predicción de demanda de ese producto
   - **Then** el sistema informa que no hay suficiente historial para generar una estimación confiable

---

### Edge Cases

- ¿Qué ocurre si el histórico de ventas presenta una interrupción prolongada (ej. el vendedor dejó de operar varias semanas)?
- ¿Cómo se comunica al vendedor el nivel de confianza de la estimación mostrada?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al vendedor consultar una estimación de demanda para cada uno de sus productos.
- **FR-002**: El sistema MUST basar la estimación en el histórico de ventas y los pedidos anticipados registrados del producto.
- **FR-003**: El sistema MUST indicar cuándo no existe historial suficiente para generar una predicción confiable.

### Key Entities *(include if feature involves data)*

- **Predicción de demanda**: Estimación calculada para un producto, basada en histórico de ventas y pedidos anticipados; atributos clave: producto asociado, periodo estimado, cantidad estimada, nivel de confianza.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede consultar la predicción de demanda de un producto en menos de 5 segundos.
- **SC-002**: El sistema informa claramente cuando el historial disponible es insuficiente, en el 100% de esos casos.

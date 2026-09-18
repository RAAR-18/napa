# Feature Specification: Predicción de demanda

**Created**: 2026-09-11
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-60
**Historias de usuario relacionadas**: HU-92

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Estimar cuánto producto preparar (Priority: P3)

Como vendedor, quiero ver una estimación de cuánto producto podría vender según pedidos anticipados e histórico de ventas, para comprar o preparar solo lo necesario y reducir pérdidas por productos perecederos.

**Why this priority**: Aporta un valor diferencial importante para reducir desperdicio, pero depende de que ya exista un historial mínimo de ventas y pedidos (RF-46, RF-55, RF-58), por lo que solo tiene sentido una vez la operación básica está funcionando.

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

### User Story 2 - Decidir qué reservas puedo cumplir (Priority: P2)

Como vendedor, quiero que la predicción de demanda considere las reservas recibidas para el día siguiente, para decidir si acepto o rechazo cada reserva según lo que voy a tener disponible.

**Why this priority**: Conecta la predicción con el flujo de reservas (RF-47, RF-54); depende de que la predicción base ya exista, por lo que se clasifica como P2.

**Independent Test**: Puede probarse registrando una reserva para el día siguiente y verificando que la predicción del producto muestra la demanda comprometida frente a la cantidad estimada disponible.

**Acceptance Scenarios**:

1. **Scenario**: Reservas frente a la disponibilidad prevista
   - **Given** el vendedor tiene reservas pendientes de respuesta para el día siguiente
   - **When** consulta la predicción de demanda del producto
   - **Then** el sistema muestra la cantidad estimada disponible y la cantidad ya comprometida en reservas

---

### Edge Cases

- **¿Qué ocurre si el histórico de ventas presenta una interrupción prolongada (ej. el vendedor dejó de operar varias semanas)?** Se descartan del cálculo los periodos de inactividad de más de 14 días y se muestra confianza baja hasta acumular nuevos datos.
- **¿Cómo se comunica al vendedor el nivel de confianza de la estimación mostrada?** Como una etiqueta Alta, Media o Baja junto con la cantidad de días de historial usados.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al vendedor consultar una estimación de demanda para cada uno de sus productos.
- **FR-002**: El sistema MUST basar la estimación en el histórico de ventas y los pedidos anticipados registrados del producto.
- **FR-003**: El sistema MUST indicar cuándo no existe historial suficiente para generar una predicción confiable.
- **FR-004**: El sistema MUST calcular, a partir de la predicción y del inventario actual, la disponibilidad prevista de cada producto para el día siguiente, y ponerla a disposición del cliente al reservar (RF-47).
- **FR-005**: El sistema MUST mostrar al vendedor, junto a la predicción, la cantidad ya comprometida en reservas aceptadas y pendientes.

### Key Entities *(include if feature involves data)*

- **Predicción de demanda**: Estimación calculada para un producto, basada en histórico de ventas y pedidos anticipados; atributos clave: producto asociado, periodo estimado, cantidad estimada, nivel de confianza.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. El vendedor selecciona el producto.

**Datos que se muestran o filtran**

- Cantidad estimada para el día siguiente y nivel de confianza (alta, media o baja) con los días de historial usados.
- Cantidad ya comprometida en reservas aceptadas y pendientes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede consultar la predicción de demanda de un producto en menos de 5 segundos.
- **SC-002**: El sistema informa claramente cuando el historial disponible es insuficiente, en el 100% de esos casos.

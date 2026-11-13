# Feature Specification: Predicción de demanda

**Created**: 2026-09-11
<<<<<<< HEAD:specs/RF-60-prediccion-de-demanda.md
**Actualizado**: 2026-09-18
**Requerimiento funcional**: RF-60
**Historias de usuario relacionadas**: HU-92
=======
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-59
**Historias de usuario relacionadas**: HU-90
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-59-prediccion-de-demanda.md

> ⚠️ **Fuera de alcance del MVP.** Este requerimiento se conserva documentado como mejora futura del producto, pero **no se implementa en la entrega actual**.

## User Scenarios & Testing *(mandatory, para una futura iteración post-MVP)*

### User Story 1 - Estimar cuánto producto preparar (Priority: P3, post-MVP)

Como vendedor, quiero ver una estimación de cuánto producto podría vender según pedidos anticipados e histórico de ventas, para comprar o preparar solo lo necesario y reducir pérdidas por productos perecederos.

<<<<<<< HEAD:specs/RF-60-prediccion-de-demanda.md
**Why this priority**: Aporta un valor diferencial importante para reducir desperdicio, pero depende de que ya exista un historial mínimo de ventas y pedidos (RF-46, RF-55, RF-58), por lo que solo tiene sentido una vez la operación básica está funcionando.
=======
**Why this priority**: Aporta un valor diferencial importante para reducir desperdicio, pero depende de que ya exista un historial mínimo de ventas y pedidos, y de infraestructura de IA que el MVP no incluye; queda como evolución natural una vez la operación básica (inventario manual y reservas decididas por el vendedor) esté validada con usuarios reales.
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-59-prediccion-de-demanda.md

**Independent Test (post-MVP)**: Puede probarse con una cuenta de vendedor que tenga historial de ventas registrado, consultando la sección de predicción de demanda y verificando que se muestra una estimación.

**Acceptance Scenarios**:

1. **Scenario**: Predicción disponible con historial suficiente
   - **Given** el vendedor cuenta con un historial mínimo de ventas y pedidos registrados
   - **When** consulta la sección de predicción de demanda de un producto
   - **Then** el sistema muestra una estimación basada en inteligencia artificial sobre el comportamiento esperado de ventas

2. **Scenario**: Historial insuficiente
   - **Given** el vendedor tiene un producto recién publicado sin historial de ventas
   - **When** consulta la predicción de demanda de ese producto
   - **Then** el sistema informa que no hay suficiente historial para generar una estimación confiable

### Edge Cases (post-MVP)

<<<<<<< HEAD:specs/RF-60-prediccion-de-demanda.md
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
=======
- ¿Qué ocurre si el histórico de ventas presenta una interrupción prolongada?
- ¿Cómo se comunica al vendedor el nivel de confianza de la estimación mostrada?
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-59-prediccion-de-demanda.md

## Requirements *(post-MVP, no implementar en esta entrega)*

### Functional Requirements

<<<<<<< HEAD:specs/RF-60-prediccion-de-demanda.md
- **FR-001**: El sistema MUST permitir al vendedor consultar una estimación de demanda para cada uno de sus productos.
- **FR-002**: El sistema MUST basar la estimación en el histórico de ventas y los pedidos anticipados registrados del producto.
- **FR-003**: El sistema MUST indicar cuándo no existe historial suficiente para generar una predicción confiable.
- **FR-004**: El sistema MUST calcular, a partir de la predicción y del inventario actual, la disponibilidad prevista de cada producto para el día siguiente, y ponerla a disposición del cliente al reservar (RF-47).
- **FR-005**: El sistema MUST mostrar al vendedor, junto a la predicción, la cantidad ya comprometida en reservas aceptadas y pendientes.
=======
- **FR-001**: El sistema PODRÁ permitir al vendedor consultar una estimación de demanda para cada uno de sus productos, en una iteración futura.
- **FR-002**: La estimación se basaría en el histórico de ventas y los pedidos anticipados registrados del producto.
- **FR-003**: El sistema indicaría cuándo no existe historial suficiente para generar una predicción confiable.
- **FR-004**: De implementarse, la predicción se ofrecería al vendedor como apoyo para decidir cuánto comprar o preparar y qué reservas aceptar, sin reemplazar su criterio al responder los pedidos (RF-53) ni su inventario declarado (RF-57).
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-59-prediccion-de-demanda.md

### Key Entities *(diseño de referencia, no implementado)*

- **Predicción de demanda**: Estimación calculada para un producto, basada en histórico de ventas y pedidos anticipados; atributos clave: producto asociado, periodo estimado, cantidad estimada, nivel de confianza.

<<<<<<< HEAD:specs/RF-60-prediccion-de-demanda.md
### Data Rules

**Datos que ingresa el usuario**: Ninguno. El vendedor selecciona el producto.

**Datos que se muestran o filtran**

- Cantidad estimada para el día siguiente y nivel de confianza (alta, media o baja) con los días de historial usados.
- Cantidad ya comprometida en reservas aceptadas y pendientes.

## Success Criteria *(mandatory)*
=======
## Success Criteria
>>>>>>> adf1c8ba15933f30f704783cb6f2fce2395ced1b:specs/RF-59-prediccion-de-demanda.md

No aplica en esta entrega. Al retomarse en una iteración futura, se recomienda definir de nuevo los criterios de éxito según los datos de uso reales del MVP.
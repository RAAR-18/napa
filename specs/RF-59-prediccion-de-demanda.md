# Feature Specification: Predicción de demanda

**Created**: 2026-09-11
**Actualizado**: 2026-10-01
**Requerimiento funcional**: RF-59
**Historias de usuario relacionadas**: HU-90

> ⚠️ **Fuera de alcance del MVP.** Este requerimiento se conserva documentado como mejora futura del producto, pero **no se implementa en la entrega actual**.

## User Scenarios & Testing *(mandatory, para una futura iteración post-MVP)*

### User Story 1 - Estimar cuánto producto preparar (Priority: P3, post-MVP)

Como vendedor, quiero ver una estimación de cuánto producto podría vender según pedidos anticipados e histórico de ventas, para comprar o preparar solo lo necesario y reducir pérdidas por productos perecederos.

**Why this priority**: Aporta un valor diferencial importante para reducir desperdicio, pero depende de que ya exista un historial mínimo de ventas y pedidos, y de infraestructura de IA que el MVP no incluye; queda como evolución natural una vez la operación básica (inventario manual y reservas decididas por el vendedor) esté validada con usuarios reales.

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

- ¿Qué ocurre si el histórico de ventas presenta una interrupción prolongada?
- ¿Cómo se comunica al vendedor el nivel de confianza de la estimación mostrada?

## Requirements *(post-MVP, no implementar en esta entrega)*

### Functional Requirements

- **FR-001**: El sistema PODRÁ permitir al vendedor consultar una estimación de demanda para cada uno de sus productos, en una iteración futura.
- **FR-002**: La estimación se basaría en el histórico de ventas y los pedidos anticipados registrados del producto.
- **FR-003**: El sistema indicaría cuándo no existe historial suficiente para generar una predicción confiable.
- **FR-004**: De implementarse, la predicción se ofrecería al vendedor como apoyo para decidir cuánto comprar o preparar y qué reservas aceptar, sin reemplazar su criterio al responder los pedidos (RF-53) ni su inventario declarado (RF-57).

### Key Entities *(diseño de referencia, no implementado)*

- **Predicción de demanda**: Estimación calculada para un producto, basada en histórico de ventas y pedidos anticipados; atributos clave: producto asociado, periodo estimado, cantidad estimada, nivel de confianza.

## Success Criteria

No aplica en esta entrega. Al retomarse en una iteración futura, se recomienda definir de nuevo los criterios de éxito según los datos de uso reales del MVP.
# Feature Specification: Configuración de métodos de pago aceptados por el vendedor

**Created**: 2026-09-25
**Requerimiento funcional**: RF-63
**Historias de usuario relacionadas**: HU-103

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Definir cómo me pueden pagar (Priority: P1)

Como vendedor, quiero indicar si acepto efectivo y/o registrar una cuenta bancaria, para definir los medios de pago disponibles en mis pedidos de entrega directa o recogida en punto fijo.

**Why this priority**: Sin esta configuración el sistema no sabe qué medios ofrecer al cliente al confirmar un pedido de entrega directa o recogida; es un prerrequisito del flujo de pago, por lo que se clasifica como P1.

**Independent Test**: Puede probarse activando "acepto efectivo", registrando una cuenta bancaria, y verificando que ambos medios aparecen como opción al cliente en un pedido de entrega directa o recogida de ese vendedor.

**Acceptance Scenarios**:

1. **Scenario**: Vendedor acepta efectivo y cuenta bancaria
   - **Given** soy vendedor ambulante o de punto fijo
   - **When** activo "acepto efectivo" y registro una cuenta bancaria válida
   - **Then** el sistema ofrece ambos medios a los clientes en mis pedidos de entrega directa o recogida

2. **Scenario**: Vendedor solo acepta efectivo
   - **Given** no he registrado una cuenta bancaria
   - **When** un cliente hace un pedido de entrega directa o recogida conmigo
   - **Then** el sistema solo ofrece efectivo como medio de pago

3. **Scenario**: Vendedor de punto fijo con domicilios
   - **Given** soy vendedor de punto fijo y también ofrezco domicilio
   - **When** reviso mi configuración de pagos
   - **Then** el sistema me indica que el domicilio siempre exige cuenta bancaria registrada, independientemente de si acepto efectivo en recogida

4. **Scenario**: Intento de ofrecer domicilio sin cuenta bancaria
   - **Given** soy vendedor de punto fijo sin cuenta bancaria registrada
   - **When** intento aceptar un pedido de modalidad domicilio
   - **Then** el sistema me impide aceptarlo y me indica que debo registrar una cuenta bancaria primero

### Edge Cases

- ¿Qué validación se aplica al número de cuenta bancaria (formato, banco soportado)?
- ¿Puede el vendedor desactivar el efectivo si ya tiene pedidos pendientes bajo ese medio?
- ¿Qué ocurre si el vendedor cambia de cuenta bancaria mientras tiene domicilios en curso? (se recomienda: los domicilios en curso mantienen la cuenta con la que se publicaron)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al vendedor indicar si acepta pagos en efectivo.
- **FR-002**: El sistema DEBE permitir al vendedor registrar una cuenta bancaria para recibir pagos digitales.
- **FR-003**: El sistema DEBE ofrecer al cliente, en pedidos de entrega directa o recogida, únicamente los medios de pago que el vendedor tenga habilitados.
- **FR-004**: El sistema DEBE exigir una cuenta bancaria registrada como condición para que un vendedor de punto fijo pueda aceptar pedidos con modalidad de domicilio.
- **FR-005**: El sistema NO DEBE almacenar datos de tarjetas ni billeteras electrónicas de terceros: únicamente el número de cuenta bancaria que el vendedor registre para recibir transferencias.

### Key Entities

- **Configuración de pago del vendedor**: Atributos: acepta efectivo (sí/no), cuenta bancaria registrada (opcional para entrega directa/recogida, obligatoria para domicilio).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El vendedor puede configurar sus métodos de pago aceptados en menos de 1 minuto.
- **SC-002**: El 100% de los pedidos de domicilio publicados corresponden a vendedores con cuenta bancaria registrada.
- **SC-003**: El 0% de los clientes ve un medio de pago que el vendedor no tiene habilitado.
# Feature Specification: Oferta de precio del domiciliario

**Created**: 2026-09-18
**Requerimiento funcional**: RF-20
**Historias de usuario relacionadas**: HU-41

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Proponer una ganancia acorde al esfuerzo (Priority: P2)

Como domiciliario, quiero ofertar un precio distinto por un domicilio, para proponer una ganancia acorde al esfuerzo cuando la publicada me parece baja.

**Why this priority**: Permite negociar el valor del domicilio y evita que domicilios poco atractivos queden sin tomar, pero el flujo funciona sin ella (aceptando la tarifa publicada), por lo que se clasifica como P2.

**Independent Test**: Puede probarse ofertando un valor válido sobre un domicilio disponible y verificando que el cliente recibe la oferta para decidir.

**Acceptance Scenarios**:

1. **Scenario**: Oferta de un precio válido
   - **Given** el domiciliario está viendo la información de un domicilio disponible y considera baja la ganancia
   - **When** ofrece un nuevo valor igual o superior a la tarifa mínima
   - **Then** el sistema registra la oferta como pendiente y notifica al cliente

2. **Scenario**: Oferta por debajo de la tarifa mínima
   - **Given** existe una tarifa mínima vigente
   - **When** el domiciliario ofrece un valor inferior a ella
   - **Then** el sistema rechaza la oferta e indica el valor mínimo permitido

3. **Scenario**: Segunda oferta sobre el mismo domicilio
   - **Given** el domiciliario ya tiene una oferta pendiente sobre ese domicilio
   - **When** intenta enviar otra oferta
   - **Then** el sistema informa que ya tiene una oferta pendiente y no crea una nueva

4. **Scenario**: Oferta sobre un domicilio ya asignado
   - **Given** el domicilio fue asignado o cancelado
   - **When** el domiciliario intenta ofertar
   - **Then** el sistema rechaza la oferta e informa que el domicilio ya no está disponible

### Edge Cases

- **¿Existe un valor máximo permitido para una oferta?** Sí: la oferta no puede superar 3 veces la tarifa publicada.
- **¿Qué ocurre si el cliente no responde a la oferta durante un tiempo prolongado? ¿La oferta vence?** La oferta vence a los 10 minutos; se notifica al domiciliario, que puede hacer una nueva.
- **¿Puede un domiciliario cuya oferta fue rechazada volver a ofertar sobre el mismo domicilio?** Sí, con un valor distinto al rechazado y hasta 3 ofertas por domiciliario en cada domicilio.
- **¿Cómo se comporta el sistema si el domiciliario oferta exactamente la tarifa publicada?** Rechaza la oferta e invita a aceptar el domicilio con la tarifa publicada; el valor ofertado debe ser distinto.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario ofertar un valor distinto a la tarifa publicada sobre un domicilio disponible.
- **FR-002**: El sistema DEBE validar que el valor ofertado no sea inferior a la tarifa mínima vigente (RF-45).
- **FR-003**: El sistema DEBE permitir, como máximo, una oferta pendiente por domiciliario y por domicilio.
- **FR-004**: El sistema DEBE notificar al cliente cada vez que reciba una nueva oferta.
- **FR-005**: El sistema DEBE mostrar al domiciliario el estado de su oferta (pendiente, aceptada, rechazada o vencida) y notificarle cuando cambie.

### Key Entities

- **Oferta de precio**: Propuesta de un domiciliario sobre un domicilio; incluye valor, domiciliario, fecha y estado (pendiente, aceptada, rechazada, vencida).
- **Tarifa mínima**: Valor mínimo permitido para la tarifa publicada y para las ofertas.

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Valor ofertado | Sí | Entero en pesos colombianos (COP); distinto a la tarifa publicada, no inferior a la tarifa mínima vigente y no superior a 3 veces la tarifa publicada. |

**Datos que asigna el sistema**

- Domiciliario, domicilio y fecha de la oferta.
- Estado "pendiente" y vencimiento a los 10 minutos.
- Máximo 3 ofertas por domiciliario en cada domicilio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las ofertas registradas cumplen con la tarifa mínima vigente.
- **SC-002**: El cliente recibe la notificación de una nueva oferta en menos de 1 minuto.
- **SC-003**: El 0% de los domiciliarios tiene más de una oferta pendiente sobre el mismo domicilio.

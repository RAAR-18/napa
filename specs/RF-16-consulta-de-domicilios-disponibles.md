# Feature Specification: Consulta de domicilios disponibles

**Created**: 2026-09-18
**Requerimiento funcional**: RF-16
**Historias de usuario relacionadas**: HU-36

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Elegir un domicilio para realizar (Priority: P1)

Como domiciliario, quiero ver los domicilios disponibles junto con lo que ganaría por cada uno, para elegir cuáles me convienen.

**Why this priority**: Es el punto de entrada del flujo del domiciliario; sin esta lista no puede tomar ni ofertar por ningún domicilio, por lo que se clasifica como P1.

**Independent Test**: Puede probarse publicando uno o más domicilios y verificando que un domiciliario los ve en su lista con origen, destino y ganancia, sin depender de las demás funciones del domiciliario.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de domicilios disponibles
   - **Given** existen domicilios publicados sin asignar
   - **When** el domiciliario ingresa a su lista de domicilios
   - **Then** el sistema los muestra con su origen, destino, distancia y la ganancia que recibiría por cada uno

2. **Scenario**: Lista sin domicilios disponibles
   - **Given** no existen domicilios disponibles en ese momento
   - **When** el domiciliario consulta su lista
   - **Then** el sistema muestra un mensaje indicando que no hay domicilios disponibles actualmente

3. **Scenario**: Los domicilios archivados no aparecen
   - **Given** el domiciliario archivó un domicilio que sigue disponible
   - **When** consulta su lista de disponibles
   - **Then** el sistema no muestra ese domicilio en su lista, aunque sigue visible para los demás domiciliarios

4. **Scenario**: Un domicilio tomado por otro deja de aparecer
   - **Given** otro domiciliario acepta un domicilio, o el cliente acepta la oferta de otro, mientras se consulta la lista
   - **When** la lista se actualiza
   - **Then** el sistema retira ese domicilio de los disponibles

### Edge Cases

- **¿Qué sucede si el domiciliario intenta abrir la información de un domicilio que fue tomado por otro segundos antes?** El sistema informa que ya no está disponible y lo retira de la lista.
- **¿Cómo se muestra la ganancia cuando el domicilio ya tiene ofertas de otros domiciliarios?** Se muestra siempre la tarifa publicada; las ofertas de otros domiciliarios no son visibles entre ellos.
- **¿Qué ocurre si un domicilio disponible es cancelado por el administrador mientras aparece en la lista?** Desaparece de la lista al actualizarse (máximo 30 segundos); si el domiciliario lo abre, ve que ya no está disponible.
- **¿Puede el domiciliario ver domicilios lejanos a su ubicación actual o solo los cercanos?** Ve los de toda la ciudad ordenados por cercanía y puede filtrar por distancia máxima.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario consultar los domicilios en estado "disponible".
- **FR-002**: El sistema DEBE mostrar, para cada domicilio, su origen, su destino, la distancia y la ganancia que recibiría el domiciliario.
- **FR-003**: El sistema DEBE excluir de la lista los domicilios que el propio domiciliario archivó (RF-17).
- **FR-004**: El sistema DEBE retirar de la lista los domicilios que dejen de estar disponibles (asignados o cancelados).
- **FR-005**: El sistema DEBE permitir ordenar la lista por cercanía o por ganancia.
- **FR-006**: El sistema DEBE permitir abrir desde cada domicilio su información detallada (RF-18).

### Key Entities

- **Domicilio**: Servicio de entrega en estado "disponible", visible para los domiciliarios salvo para quienes lo archivaron.
- **Ganancia**: Valor que recibiría el domiciliario por el domicilio; coincide con la tarifa vigente del domicilio.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. Opciones: ordenar por cercanía o ganancia y filtrar por distancia máxima.

**Datos que se muestran o filtran**

- Origen, destino (zona y barrio), distancia y ganancia de cada domicilio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El domiciliario visualiza la lista actualizada de domicilios disponibles en menos de 3 segundos.
- **SC-002**: El 100% de los domicilios disponibles no archivados aparecen en la lista del domiciliario.
- **SC-003**: El 0% de los domicilios asignados o cancelados permanece en la lista más de 30 segundos después del cambio.

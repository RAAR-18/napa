# Feature Specification: Archivado de domicilios

**Created**: 2026-09-18
**Requerimiento funcional**: RF-17
**Historias de usuario relacionadas**: HU-37, HU-38

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ocultar los domicilios que no me interesan (Priority: P2)

Como domiciliario, quiero archivar un domicilio, para que deje de aparecer en mi lista sin afectar a los demás domiciliarios.

**Why this priority**: Mantiene limpia la lista del domiciliario y reduce el ruido al elegir, pero no bloquea el ciclo del domicilio, por lo que se clasifica como P2.

**Independent Test**: Puede probarse archivando un domicilio disponible y verificando que desaparece de la lista de ese domiciliario pero sigue visible para otro.

**Acceptance Scenarios**:

1. **Scenario**: Archivado de un domicilio disponible
   - **Given** el domiciliario ve un domicilio disponible que no le interesa
   - **When** lo archiva
   - **Then** el sistema lo oculta de su lista de disponibles y lo mantiene disponible para los demás domiciliarios

2. **Scenario**: Archivado no notifica al vendedor ni al cliente
   - **Given** el domiciliario archiva un domicilio
   - **When** el sistema procesa la acción
   - **Then** el sistema no notifica ni penaliza al vendedor, al cliente ni al domiciliario, porque archivar es una acción personal y no un rechazo

3. **Scenario**: Archivado de un domicilio ya tomado por otro
   - **Given** el domicilio fue tomado por otro domiciliario antes de que se confirme el archivado
   - **When** el domiciliario intenta archivarlo
   - **Then** el sistema informa que el domicilio ya no está disponible y lo retira de su lista

---

### User Story 2 - Recuperar un domicilio archivado (Priority: P3)

Como domiciliario, quiero consultar mis domicilios archivados, para restaurar uno que quiera retomar.

**Why this priority**: Evita que un domicilio archivado por error o por cambio de opinión quede oculto para siempre, pero es una función de apoyo, por lo que se clasifica como P3.

**Independent Test**: Puede probarse archivando un domicilio, entrando a la lista de archivados y restaurándolo para verificar que reaparece entre los disponibles.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de domicilios archivados
   - **Given** el domiciliario tiene domicilios archivados
   - **When** ingresa a su lista de archivados
   - **Then** el sistema muestra los domicilios archivados con su información básica y su estado actual

2. **Scenario**: Restauración de un domicilio archivado
   - **Given** un domicilio archivado sigue disponible
   - **When** el domiciliario lo restaura
   - **Then** el sistema lo vuelve a mostrar en su lista de disponibles

3. **Scenario**: Restauración de un domicilio que ya no está disponible
   - **Given** un domicilio archivado fue asignado a otro domiciliario o cancelado
   - **When** el domiciliario intenta restaurarlo
   - **Then** el sistema informa que ya no está disponible y no lo agrega a la lista de disponibles

### Edge Cases

- **¿Los domicilios archivados se eliminan de la lista de archivados cuando dejan de estar disponibles?** No de inmediato: permanecen en archivados con la etiqueta "no disponible" durante 7 días y luego se eliminan de la lista.
- **¿Existe un límite de domicilios archivados por domiciliario?** No hay límite.
- **¿Qué ocurre con las ofertas de precio que el domiciliario ya había hecho sobre un domicilio que luego archiva?** La oferta pendiente sigue vigente: archivar solo oculta el domicilio de su lista.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir al domiciliario archivar un domicilio disponible, de modo que deje de aparecer en su lista de disponibles.
- **FR-002**: El sistema DEBE mantener el domicilio archivado disponible para los demás domiciliarios.
- **FR-003**: El sistema DEBE tratar el archivado como una acción personal del domiciliario, sin notificar ni penalizar a otros usuarios.
- **FR-004**: El sistema DEBE permitir al domiciliario consultar la lista de sus domicilios archivados.
- **FR-005**: El sistema DEBE permitir restaurar un domicilio archivado únicamente si sigue disponible.

### Key Entities

- **Domicilio archivado**: Relación entre un domiciliario y un domicilio que este decidió ocultar; incluye fecha de archivado.

### Data Rules

**Datos que ingresa el usuario**: Ninguno. El domiciliario selecciona el domicilio y lo archiva o lo restaura.

**Datos que asigna el sistema**

- Fecha de archivado y domiciliario que archiva (acción personal).

**Datos que se muestran o filtran**

- Lista de archivados: información básica, estado actual y etiqueta "no disponible" si ya no puede tomarse.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los domicilios archivados por un domiciliario dejan de aparecer en su lista de disponibles de inmediato.
- **SC-002**: El 0% de los archivados afecta la disponibilidad del domicilio para otros domiciliarios.
- **SC-003**: El domiciliario puede archivar o restaurar un domicilio en menos de 5 segundos.

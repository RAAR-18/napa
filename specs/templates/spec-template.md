# Feature Specification: [NOMBRE DE LA FEATURE]

**Created**: [AAAA-MM-DD]
**Actualizado**: [AAAA-MM-DD] *(omitir si no ha cambiado)*
**Requerimiento funcional**: RF-NN
**Historias de usuario relacionadas**: HU-NN, HU-NN
**Relacionado con**: [RF-NN, RF-NN o "ninguno"]

> Una spec define **QUÉ** hace el sistema y para quién. No dice cómo se implementa (eso va en `plan.md`).
> Roles, convenciones de API, modelo de datos y eventos compartidos viven en `specs/general/`; aquí solo se referencian.
> Redacción de requisitos: usar **DEBE** (no mezclar con MUST).

## User Scenarios & Testing *(mandatory)*

<!--
  Historias ordenadas por prioridad.
  P1 = núcleo del flujo (sin esto no hay producto) · P2 = importante · P3 = complementario.
  Cada historia debe poder probarse y entregarse de forma independiente.
  Actores: Comprador (Cliente), Vendedor (ambulante / punto fijo), Domiciliario, Administrador.
-->

### User Story 1 - [Título] (Priority: P1)

Como [actor], quiero [acción], para [beneficio].

**Why this priority**: [Por qué tiene esta prioridad]

**Independent Test**: [Cómo probarla por sí sola y qué valor entrega]

**Acceptance Scenarios**:

1. **Scenario**: [Nombre]
    - **Given** [estado inicial]
    - **When** [acción]
    - **Then** [resultado esperado]

2. **Scenario**: [Caso de rechazo o error]
    - **Given** [estado inicial]
    - **When** [acción]
    - **Then** [resultado esperado]

---

### User Story 2 - [Título] (Priority: P2)

[Repetir la estructura]

### Edge Cases

- ¿Qué ocurre cuando [condición límite]?
- ¿Cómo maneja el sistema [escenario de error]?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE [capacidad verificable].
- **FR-002**: El sistema DEBE [capacidad verificable].
- **FR-003**: El sistema NO DEBE [restricción].

### Key Entities *(include if feature involves data)*

- **[Entidad]**: [Qué representa y atributos clave, sin tipos de datos]. Ver definición en `specs/general/spec-general.md`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: [Métrica medible, p. ej. "El vendedor completa X en menos de N segundos"]
- **SC-002**: [El N % de los casos Y cumple Z]

<!--
  Checklist de la spec (antes de pasar al plan):
  - [ ] Cada FR se puede verificar con al menos un escenario o criterio de éxito
  - [ ] Los casos borde sin respuesta están como preguntas (se resuelven en el plan: "Open Questions")
  - [ ] No hay decisiones técnicas (frameworks, tablas, endpoints)
  - [ ] Si agrega o cambia un RF/HU: actualizado requerimientos/funcionales.md, historias_de_usuario.md y trazabilidad.md
-->
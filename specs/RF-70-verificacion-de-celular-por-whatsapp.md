# Feature Specification: Verificación de celular por WhatsApp (OTP)

**Created**: 2026-10-05
**Requerimiento funcional**: RF-70
**Historias de usuario relacionadas**: HU-105
**Relacionado con**: RF-07 (registro), RF-08 (edición de cuenta), RF-09 (cambio de contraseña), RF-10 (inicio de sesión)

> El OTP **no es un método de inicio de sesión**. Se usa para comprobar que el celular pertenece al usuario y para recuperar la contraseña. El inicio de sesión sigue siendo con contraseña (RF-10).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Verificar mi celular al registrarme (Priority: P1)

Como persona que se registra, quiero recibir un código por WhatsApp y confirmarlo, para activar mi cuenta con un celular que es realmente mío.

**Why this priority**: Sin celular verificado no hay cuenta activa, y el celular es el canal de contacto de pedidos y domicilios.

**Independent Test**: Registrar una cuenta, recibir el código, ingresarlo y verificar que la cuenta pasa a activa y se inicia sesión.

**Acceptance Scenarios**:

1. **Scenario**: Código correcto
   - **Given** me registré y mi cuenta está pendiente de verificación
   - **When** ingreso el código recibido dentro de su vigencia
   - **Then** el sistema activa mi cuenta e inicia mi sesión

2. **Scenario**: Código incorrecto
   - **Given** tengo un código vigente
   - **When** ingreso un código que no coincide
   - **Then** el sistema rechaza el intento e indica cuántos intentos me quedan

3. **Scenario**: Código vencido
   - **Given** pasó la vigencia del código desde su envío
   - **When** ingreso el código
   - **Then** el sistema indica que venció y me permite pedir uno nuevo

4. **Scenario**: Intentos agotados
   - **Given** fallé 5 veces con el mismo código
   - **When** intento otra vez
   - **Then** el sistema invalida el código y exige solicitar uno nuevo

---

### User Story 2 - Recuperar mi contraseña con un código (Priority: P1)

Como usuario que olvidó su contraseña, quiero recibir un código por WhatsApp, para poder definir una nueva contraseña (RF-09).

**Why this priority**: Es la vía de recuperación definida en RF-09; sin ella una cuenta olvidada se pierde.

**Independent Test**: Solicitar recuperación con un celular verificado, ingresar el código y definir una contraseña nueva.

**Acceptance Scenarios**:

1. **Scenario**: Recuperación exitosa
   - **Given** tengo una cuenta con celular verificado
   - **When** solicito recuperar mi contraseña e ingreso el código correcto
   - **Then** el sistema me permite definir una contraseña nueva y cierra las demás sesiones

2. **Scenario**: Celular no registrado
   - **Given** el celular no pertenece a ninguna cuenta
   - **When** solicito recuperar la contraseña
   - **Then** el sistema responde igual que si existiera, sin revelar si la cuenta existe

---

### User Story 3 - Cambiar mi celular (Priority: P2)

Como usuario, quiero verificar un celular nuevo antes de que reemplace al anterior (RF-08).

**Why this priority**: Evita perder el contacto con usuarios activos, pero es poco frecuente.

**Independent Test**: Cambiar el celular, verificar el nuevo y comprobar que el anterior sigue vigente hasta entonces.

**Acceptance Scenarios**:

1. **Scenario**: Cambio verificado
   - **Given** estoy editando mis datos
   - **When** ingreso un celular nuevo y el código que llega a ese número
   - **Then** el sistema reemplaza el celular y registra la verificación

2. **Scenario**: Cambio sin verificar
   - **Given** ingresé un celular nuevo
   - **When** no confirmo el código
   - **Then** el sistema conserva el celular anterior

---

### User Story 4 - Reenviar el código o recibirlo por SMS (Priority: P2)

Como usuario que no recibió el código, quiero pedir uno nuevo o recibirlo por SMS, para completar mi trámite.

**Why this priority**: Con conectividad intermitente, el mensaje puede no llegar; sin respaldo el usuario queda bloqueado.

**Independent Test**: Simular fallo de WhatsApp y comprobar que se ofrece SMS.

**Acceptance Scenarios**:

1. **Scenario**: Reenvío permitido
   - **Given** pasó al menos un minuto desde el último envío
   - **When** solicito reenviar
   - **Then** el sistema envía un código nuevo e invalida el anterior

2. **Scenario**: Reenvío demasiado pronto o excedido
   - **Given** solicité reenvíos por encima del límite
   - **When** vuelvo a solicitar
   - **Then** el sistema rechaza con el tiempo de espera y no envía mensaje

3. **Scenario**: Falla de WhatsApp
   - **Given** el envío por WhatsApp falló
   - **When** consulto el resultado
   - **Then** el sistema me ofrece recibir el código por SMS

### Edge Cases

- ¿Qué ocurre si el celular no tiene WhatsApp instalado?
- ¿Qué pasa si el usuario cambia de celular a uno ya registrado por otra cuenta?
- ¿Cómo se trata un celular con formato internacional distinto del colombiano?
- ¿Qué sucede si el proveedor entrega el mensaje con demora y el código ya venció?
- ¿Qué hacer si un mismo celular recibe solicitudes masivas (abuso)? (límites por celular e IP; bloqueo temporal)
- ¿Quién cubre el costo de los mensajes y qué techo mensual se fija?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE emitir un código de 6 dígitos de un solo uso, con vigencia limitada (no mayor a 10 minutos).
- **FR-002**: El sistema DEBE enviar el código por WhatsApp con una plantilla aprobada y ofrecer SMS como respaldo si el envío falla.
- **FR-003**: El sistema DEBE permitir hasta 5 intentos por código y invalidarlo al agotarlos.
- **FR-004**: El sistema DEBE limitar los envíos por celular (1 por minuto, 5 por hora) y por IP, respondiendo `429` con `Retry-After`.
- **FR-005**: El sistema DEBE invalidar el código anterior cuando se genera uno nuevo para el mismo celular y propósito.
- **FR-006**: El sistema DEBE responder igual ante celulares registrados y no registrados en la recuperación de contraseña.
- **FR-007**: El sistema DEBE activar la cuenta e iniciar sesión al verificarse el código de registro (RF-07 FR-004).
- **FR-008**: El sistema DEBE aplicar un cambio de celular solo después de verificar el número nuevo (RF-08).
- **FR-009**: El sistema DEBE registrar cada envío como `Mensaje` con canal, propósito, destino, estado e identificador del proveedor, **sin incluir el código**.
- **FR-010**: El sistema NO DEBE almacenar ni registrar el código en claro en base de datos, logs ni outbox.
- **FR-011**: El sistema DEBE actualizar el estado del `Mensaje` (enviado, entregado, fallido) cuando el proveedor ofrezca notificaciones de estado.

### Key Entities

- **Mensaje**: Envío saliente por un canal externo; ver `specs/general/spec-general.md` §3.2.
- **Código OTP**: Dato efímero en Redis (hash, intentos, vigencia); no es entidad persistente.
- **Usuario**: Su celular y la fecha de verificación (`celular_verificado_at`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La solicitud de un código responde en menos de 1,5 s (p95); el envío es asíncrono.
- **SC-002**: Al menos el 90 % de los códigos llega al usuario en menos de 30 s *(meta a validar con datos reales del proveedor)*.
- **SC-003**: El 0 % de los códigos puede usarse dos veces o después de vencer.
- **SC-004**: El 0 % de los códigos queda en claro en base de datos, logs u outbox.
- **SC-005**: El 100 % de los envíos queda registrado como `Mensaje` con su estado.

## Documentos actualizados con este RF

- `requerimientos/funcionales.md` (módulo 2), `historias_de_usuario.md` (HU-105), `trazabilidad.md`, `capstone/informe-capstone.md` y `casos de uso/gestionar_cuenta.puml` (caso de uso "Verificar celular con código").
- RF-07, RF-08, RF-09, RF-10 y RF-38 (ver `scripts/aplicar_rf70.py`).
- Se conserva la numeración: RF-20, RF-21 y RF-46 no se reutilizan, por eso el siguiente libre es RF-70.
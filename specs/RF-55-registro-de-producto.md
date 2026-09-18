# Feature Specification: Registro de producto

**Created**: 2026-09-11
**Requerimiento funcional**: RF-55
**Historias de usuario relacionadas**: HU-87

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Añadir un producto al catálogo (Priority: P1)

Como vendedor, quiero añadir un producto con nombre, precio, descripción y cantidad disponible, para ofrecerlo a los clientes cercanos.

**Why this priority**: Sin la capacidad de publicar productos no existe oferta visible en la plataforma; es la funcionalidad mínima que habilita todo el flujo de venta (descubrimiento, pedido, entrega).

**Independent Test**: Puede probarse creando un emprendimiento, registrando un producto con datos válidos y verificando que aparece de inmediato en el catálogo visible para los clientes.

**Acceptance Scenarios**:

1. **Scenario**: Registro exitoso de producto
   - **Given** el vendedor tiene un emprendimiento creado
   - **When** registra un producto con nombre, precio y cantidad válidos
   - **Then** el producto aparece de inmediato en el listado de productos de su emprendimiento

2. **Scenario**: Datos inválidos al registrar
   - **Given** el vendedor está completando el formulario de un nuevo producto
   - **When** ingresa un precio negativo o una cantidad no numérica
   - **Then** el sistema rechaza el registro y muestra un mensaje indicando el campo inválido

---

### Edge Cases

- **¿Qué ocurre si el vendedor intenta registrar un producto sin tener un emprendimiento creado?** Se bloquea y se pide crear primero el emprendimiento (RF-28).
- **¿Cómo maneja el sistema el registro de un producto con nombre duplicado dentro del mismo emprendimiento?** Se rechaza: el nombre debe ser único dentro del emprendimiento.
- **¿Qué sucede si se registra un producto con cantidad disponible igual a cero?** Está permitido; queda registrado como agotado y no aparece disponible.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al vendedor registrar un producto indicando nombre, precio, descripción y cantidad disponible.
- **FR-002**: El sistema MUST asociar el producto registrado al emprendimiento del vendedor autenticado.
- **FR-003**: El sistema MUST validar que el nombre, el precio y la cantidad sean valores válidos antes de publicar el producto.
- **FR-004**: El sistema MUST publicar el producto de forma inmediata en el catálogo visible para los clientes una vez registrado.

### Key Entities *(include if feature involves data)*

- **Producto**: Representa un artículo ofrecido por un emprendimiento; atributos clave: nombre, precio, descripción, cantidad disponible, estado (disponible/agotado).

### Data Rules

**Datos que ingresa el usuario**

| Campo | Obligatorio | Regla de validación |
|---|:---:|---|
| Nombre | Sí | Texto de 2 a 60 caracteres; único dentro del emprendimiento. |
| Precio | Sí | Entero en pesos colombianos (COP) mayor a 0, por unidad de medida. |
| Unidad de medida | Sí | Unidad, kilogramo, libra, litro o porción. |
| Descripción | No | Texto de hasta 200 caracteres. |
| Cantidad disponible | Sí | Número mayor o igual a 0; con decimales solo para kilogramo, libra y litro. |

**Datos que asigna el sistema**

- Emprendimiento del vendedor en sesión.
- Estado "disponible" (o "agotado" si la cantidad es 0) y fecha de creación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un vendedor puede registrar un producto nuevo en menos de 1 minuto.
- **SC-002**: El 100% de los productos registrados con datos válidos quedan visibles en el catálogo del emprendimiento inmediatamente después del registro.
- **SC-003**: El sistema rechaza el 100% de los intentos de registro con precio o cantidad inválidos.

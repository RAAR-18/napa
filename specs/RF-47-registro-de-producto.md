# Feature Specification: Registro de producto

**Created**: 2026-09-11
**Requerimiento funcional**: RF-47
**Historias de usuario relacionadas**: HU-73

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

- ¿Qué ocurre si el vendedor intenta registrar un producto sin tener un emprendimiento creado?
- ¿Cómo maneja el sistema el registro de un producto con nombre duplicado dentro del mismo emprendimiento?
- ¿Qué sucede si se registra un producto con cantidad disponible igual a cero?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir al vendedor registrar un producto indicando nombre, precio, descripción y cantidad disponible.
- **FR-002**: El sistema MUST asociar el producto registrado al emprendimiento del vendedor autenticado.
- **FR-003**: El sistema MUST validar que el nombre, el precio y la cantidad sean valores válidos antes de publicar el producto.
- **FR-004**: El sistema MUST publicar el producto de forma inmediata en el catálogo visible para los clientes una vez registrado.

### Key Entities *(include if feature involves data)*

- **Producto**: Representa un artículo ofrecido por un emprendimiento; atributos clave: nombre, precio, descripción, cantidad disponible, estado (disponible/agotado).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un vendedor puede registrar un producto nuevo en menos de 1 minuto.
- **SC-002**: El 100% de los productos registrados con datos válidos quedan visibles en el catálogo del emprendimiento inmediatamente después del registro.
- **SC-003**: El sistema rechaza el 100% de los intentos de registro con precio o cantidad inválidos.

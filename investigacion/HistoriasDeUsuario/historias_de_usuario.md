# Historias de Usuario

## HU01 – Registro de usuario

**Como** persona interesada en usar la plataforma, **quiero** registrarme indicando mis datos básicos y el rol que voy a desempeñar (vendedor, cliente o domiciliario), **para** poder acceder a las funcionalidades correspondientes a cada rol.

**Criterio de aceptación:** Dado que el correo o teléfono no está registrado, cuando completo el formulario de registro con datos válidos y confirmo la contraseña, entonces el sistema crea mi cuenta y me permite iniciar sesión.

---

## HU02 – Editar Datos de Usuario

**Como** usuario registrado, **quiero** editar mis datos personales (nombre, teléfono, foto, ubicación), **para** mantener mi información actualizada.

**Criterio de aceptación:** Dado que tengo una cuenta activa, cuando modifico uno o varios de mis datos personales, entonces el sistema guarda los cambios y los refleja en mi perfil.

---

## HU03 – Eliminar cuenta

### Historia sin descripción ni criterios de aceptación definidos

#### Definición pendiente

Definición pendiente en el documento original.

---

## HU04 – Iniciar sesión

**Como** usuario registrado, **quiero** iniciar sesión con mi correo/teléfono y contraseña, **para** acceder de forma segura a mi cuenta.

**Criterio de aceptación:** Dado que tengo una cuenta previamente registrada, cuando ingreso mis credenciales correctas, entonces el sistema me autentica y me redirige a mi panel según mi rol.

---

## HU05 – Cambiar contraseña

**Como** usuario registrado, **quiero** poder cambiar mi contraseña, **para** mantener la seguridad de mi cuenta.

**Criterio de aceptación:** Dado que conozco mi contraseña actual o tengo acceso a recuperación, cuando ingreso la contraseña actual y una nueva contraseña válida, entonces el sistema actualiza la contraseña y cierra las demás sesiones activas.

---

## HU06 – Crear emprendimiento

**Como** vendedor, **quiero** crear mi emprendimiento con nombre, tipo de producto, ubicación y descripción, **para** que los clientes puedan encontrarme en la plataforma.

**Criterio de aceptación:** Dado que estoy registrado como vendedor y no tengo un emprendimiento activo, cuando completo el formulario de creación con la información obligatoria, entonces el emprendimiento queda publicado y visible para los clientes.

---

## HU07 – Editar emprendimiento

**Como** vendedor, **quiero** editar la información de mi emprendimiento (ubicación, horario, descripción), **para** mantenerla actualizada según mi actividad diaria.

**Criterio de aceptación:** Dado que tengo un emprendimiento creado, cuando modifico uno o varios campos del emprendimiento, entonces el sistema guarda los cambios y los muestra a los clientes de inmediato.

---

## HU08 – Eliminar emprendimiento

**Como** vendedor, **quiero** eliminar mi emprendimiento si dejo de operar, **para** que ya no aparezca visible a los clientes.

**Criterio de aceptación:** Dado que tengo un emprendimiento sin pedidos pendientes, cuando confirmo la eliminación, entonces el sistema oculta el emprendimiento y su catálogo visible para los clientes.

---

## HU09 – Añadir producto

**Como** vendedor, **quiero** publicar un producto con nombre, precio, descripción y cantidad disponible, **para** que los clientes cercanos puedan verlo y pedirlo.

**Criterio de aceptación:** Dado que tengo un emprendimiento creado, cuando registro un producto con nombre, precio y cantidad válidos, entonces el producto aparece de inmediato en el listado de productos de mi emprendimiento.

---

## HU10 – Editar producto

**Como** vendedor, **quiero** editar los datos de un producto ya publicado (precio, nombre, descripción), **para** corregir errores o ajustar precios.

**Criterio de aceptación:** Dado que tengo un producto publicado, cuando modifico uno o varios campos del producto, entonces el sistema actualiza la información visible para los clientes.

---

## HU11 – Actualizar estado de producto

**Como** vendedor, **quiero** actualizar rápidamente la cantidad disponible de un producto o marcarlo como agotado, **para** evitar que los clientes pidan algo que ya no tengo.

**Criterio de aceptación:** Dado que tengo un producto publicado, cuando cambio la cantidad disponible o la marco en cero, entonces el sistema actualiza el estado del producto y lo marca como agotado si la cantidad llega a cero.

---

## HU12 – Consultar predicción de demanda

**Como** vendedor, **quiero** ver una estimación de cuánto producto podría vender según pedidos anticipados e histórico de ventas, **para** comprar o preparar solo lo necesario y reducir pérdidas por productos perecederos.

**Criterio de aceptación:** Dado que cuento con un historial mínimo de ventas y pedidos registrados, cuando consulto la sección de predicción de demanda, entonces el sistema muestra una estimación basada en inteligencia artificial sobre el comportamiento esperado de ventas.

---

## HU13 – Eliminar producto

**Como** vendedor, **quiero** eliminar un producto que ya no voy a vender, **para** que no siga apareciendo en mi catálogo.

**Criterio de aceptación:** Dado que tengo un producto publicado sin pedidos pendientes, cuando confirmo la eliminación del producto, entonces el sistema lo retira del catálogo visible para todos los clientes.

---

## HU14 – Listar productos

**Como** vendedor, **quiero** ver el listado completo de mis productos publicados, **para** gestionarlos fácilmente desde un solo lugar.

**Criterio de aceptación:** Dado que tengo al menos un producto publicado, cuando ingreso a la sección de mis productos, entonces el sistema muestra todos mis productos con su precio y disponibilidad actual.

---

## HU15 – Aceptar/rechazar pedido

**Como** vendedor, **quiero** aceptar o rechazar un pedido entrante, **para** confirmar solo los pedidos que puedo cumplir con el inventario que tengo.

**Criterio de aceptación:** Dado que recibí un pedido en estado pendiente, cuando reviso el pedido y decido aceptarlo o rechazarlo, entonces el sistema actualiza el estado del pedido y notifica al cliente.

---

## HU16 – Consultar pedido

**Como** vendedor, **quiero** consultar el detalle de un pedido (productos, cantidades, cliente), **para** prepararlo correctamente.

**Criterio de aceptación:** Dado que tengo un pedido pendiente o aceptado, cuando selecciono el pedido desde mi listado, entonces el sistema muestra el detalle completo del pedido.

---

## HU17 – Listar pedidos

**Como** vendedor, **quiero** ver la lista de todos mis pedidos (pendientes, aceptados, entregados), **para** organizar mi jornada de venta.

**Criterio de aceptación:** Dado que tengo pedidos registrados en la plataforma, cuando ingreso a la sección de pedidos, entonces el sistema muestra los pedidos agrupados por estado.

---

## HU18 – Entregar pedido al domiciliario

**Como** vendedor, **quiero** marcar un pedido como listo para entregar al domiciliario, **para** no tener que abandonar mi punto de venta para hacer la entrega.

**Criterio de aceptación:** Dado que un pedido fue aceptado y requiere domicilio, cuando marco el pedido como listo para recoger, entonces el sistema lo publica como domicilio disponible para los domiciliarios.

---

## HU19 – Listar emprendimientos

**Como** cliente, **quiero** ver el listado de emprendimientos disponibles en la plataforma, **para** explorar qué vendedores existen y cuáles están cerca de mí.

**Criterio de aceptación:** Dado que existen emprendimientos activos registrados, cuando ingreso a la sección de emprendimientos, entonces el sistema muestra el listado con nombre, categoría y ubicación de cada uno.

---

## HU20 – Consultar emprendimientos

**Como** cliente, **quiero** consultar emprendimientos por ubicación, ya sea cercanos a mí o en una zona distinta que yo elija, **para** descubrir vendedores tanto cerca de mí como en otros sectores de mi interés.

**Criterios de aceptación:** Dado que estoy en la sección de emprendimientos, cuando consulto sin aplicar ningún filtro de ubicación, entonces el sistema muestra por defecto los emprendimientos ordenados de más cercano a más lejano según mi ubicación actual; y dado que quiero explorar otra zona, cuando ingreso o selecciono una ubicación distinta a la mía, entonces el sistema muestra los emprendimientos cercanos a esa ubicación indicada.

---

## HU21 – Listar productos de un emprendimiento

**Como** cliente, **quiero** ver los productos de un emprendimiento específico, con precio y cantidad, **para** decidir qué comprar.

**Criterio de aceptación:** Dado que selecciono un emprendimiento del listado, cuando ingreso a su catálogo de productos, entonces el sistema muestra únicamente los productos con disponibilidad.

---

## HU22 – Realizar pedido

**Como** cliente, **quiero** armar un pedido seleccionando productos y cantidades, **para** comprarle a un vendedor sin tener que estar físicamente en su punto de venta.

**Criterio de aceptación:** Dado que seleccioné al menos un producto disponible, cuando confirmo el pedido indicando si es para recoger o a domicilio, entonces el pedido queda en estado pendiente hasta que el vendedor lo acepte.

---

## HU23 – Consultar estado del domicilio

**Como** cliente, **quiero** consultar en qué estado va mi pedido o domicilio (aceptado, en camino, entregado), **para** saber cuándo lo voy a recibir.

**Criterio de aceptación:** Dado que tengo un pedido con domicilio en curso, cuando consulto el estado desde mi historial de pedidos, entonces el sistema muestra el estado actualizado del domicilio.

---

## HU24 – Contactar vendedor o domiciliario

**Como** cliente, **quiero** poder contactar al vendedor o domiciliario asignado a mi pedido, **para** resolver dudas o coordinar entregas.

**Criterio de aceptación:** Dado que tengo un pedido activo con vendedor o domiciliario asignado, cuando toco la opción de contacto, entonces el sistema abre un canal de comunicación (chat o llamada) con la persona correspondiente.

---

## HU25 – Finalizar pedido

**Como** cliente, **quiero** confirmar que recibí mi pedido correspondiente, **para** cerrar el ciclo de compra.

**Criterio de aceptación:** Dado que mi pedido fue marcado como entregado por el domiciliario o vendedor, cuando confirmo la recepción en la aplicación, entonces el sistema marca el pedido como finalizado y archiva en mi historial.

---

## HU26 – Solicitar domicilio

**Como** cliente, **quiero** solicitar un domicilio para mi pedido seleccionando el pedido, el método de pago y viendo el costo del envío, **para** recibir mi pedido en la dirección que indique.

**Criterio de aceptación:** Dado que tengo un pedido aceptado por el vendedor, cuando selecciono el pedido, elijo un método de pago y confirmo, entonces el sistema calcula el costo del domicilio, lo muestra antes de confirmar y registra la solicitud.

---

## HU27 – Registrar domicilio

**Como** vendedor, **quiero** registrar un domicilio para un pedido que ya preparé, **para** que quede disponible para que un domiciliario lo tome.

**Criterio de aceptación:** Dado que tengo un pedido listo para despachar, cuando registro el domicilio con la dirección de entrega, entonces el sistema lo publica como domicilio disponible.

---

## HU28 – Editar domicilio

**Como** vendedor, **quiero** editar los datos de un domicilio ya registrado (dirección, notas de entrega), **para** corregir información antes de que sea aceptado por un domiciliario.

**Criterio de aceptación:** Dado que registré un domicilio que aún no ha sido aceptado, cuando modifico sus datos, entonces el sistema guarda los cambios y los refleja para los domiciliarios disponibles.

---

## HU29 – Cancelar domicilio

**Como** cliente, **quiero** cancelar un domicilio solicitado indicando el motivo, **para** informar que ya no necesito el envío.

**Criterio de aceptación:** Dado que tengo un domicilio en curso que aún no ha sido entregado, cuando lo cancelo e indico el motivo, entonces el sistema notifica la cancelación al vendedor y al domiciliario (si ya fue asignado).

---

## HU30 – Cancelar domicilio

**Como** administrador, **quiero** cancelar un domicilio indicando el motivo, **para** resolver casos excepcionales o reportes de mal uso de la plataforma.

**Criterio de aceptación:** Dado que identifico un domicilio problemático, cuando lo cancelo e indico el motivo, entonces el sistema notifica la cancelación a las partes involucradas.

---

## HU31 – Consultar domicilio

**Como** administrador, **quiero** consultar el detalle de un domicilio, incluyendo la información del pedido, del cliente y del domiciliario, **para** hacer seguimiento o resolver disputas.

**Criterio de aceptación:** Dado que existe un domicilio registrado, cuando lo consulto, entonces el sistema muestra la información completa del pedido, del cliente y del domiciliario asociados.

---

## HU32 – Listar domicilios

**Como** vendedor, **quiero** ver el listado de mis domicilios, con filtros por estado y por fecha, **para** hacer seguimiento a mis envíos.

**Criterios de aceptación:** Dado que tengo domicilios registrados, cuando consulto el listado y aplico un filtro de estado o de fecha, entonces el sistema muestra solo los domicilios que cumplen el filtro.

---

## HU33 – Listar domicilios

**Como** administrador, **quiero** ver el listado general de domicilios, con filtros por estado y por fecha, **para** supervisar la operación de la plataforma.

**Criterio de aceptación:** Dado que existen domicilios registrados en el sistema, cuando consulto el listado y aplico filtros, entonces el sistema muestra los domicilios correspondientes.

---

## HU35 – Ver domicilios disponibles

**Como** domiciliario, **quiero** ver los domicilios disponibles para tomar, **para** elegir cuál voy a realizar.

**Criterio de aceptación:** Dado que existen domicilios sin asignar, cuando consulto la lista de disponibles, entonces el sistema los muestra con su información básica (origen, destino, costo).

---

## HU36 – Aceptar domicilio

**Como** domiciliario, **quiero** aceptar un domicilio disponible viendo antes la información del pedido y la dirección de entrega, **para** comprometerme a realizarlo con la información necesaria.

**Criterio de aceptación:** Dado que hay un domicilio disponible, cuando consulto la información del pedido y la dirección de entrega y confirmo la aceptación, entonces el sistema me lo asigna y notifica la aceptación al cliente y al vendedor.

---

## HU37 – Rechazar domicilio

**Como** domiciliario, **quiero** rechazar un domicilio que se me presentó como disponible, **para** que deje de aparecer en mi lista, sin afectar su disponibilidad para los demás domiciliarios.

**Criterio de aceptación:** Dado que tengo un domicilio disponible en mi lista, cuando lo rechazo, entonces el sistema lo oculta únicamente de mi lista y este permanece visible y disponible para el resto de los domiciliarios.

---

## HU38 – Actualizar estado del domicilio

**Como** domiciliario, **quiero** actualizar el estado de un domicilio que acepté (aceptado, pedido recogido, en camino), **para** que el cliente y el vendedor sepan en qué va la entrega.

**Criterio de aceptación:** Dado que tengo un domicilio asignado, cuando marco su avance (recogido o en camino), entonces el sistema actualiza el estado visible para el cliente y el vendedor en tiempo real.

---

## HU39 – Confirmar entrega

**Como** domiciliario, **quiero** confirmar la entrega de un domicilio validando que se realizó correctamente, **para** cerrar formalmente el domicilio con fecha y hora de entrega registradas.

**Criterio de aceptación:** Dado que llegué a la dirección del cliente con el pedido, cuando confirmo la entrega, entonces el sistema valida la confirmación.

---

## HU40 – Consultar estado del domicilio

**Como** vendedor, **quiero** consultar el estado de los domicilios de mis pedidos, **para** hacer seguimiento a lo que ya despaché.

**Criterio de aceptación:** Dado que tengo pedidos con domicilio en curso, cuando consulto su estado, entonces el sistema muestra el estado actualizado de cada uno.
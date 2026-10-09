# Requerimientos Funcionales

> Derivados del backlog de historias de usuario (`investigacion/HistoriasDeUsuario/historias_de_usuario.md`), organizados por módulo. Entre paréntesis se indican las historias de usuario (HU) que sustentan cada requerimiento. Cada requerimiento tiene su especificación detallada en `specs/`.
> **Alcance del MVP:** RF-59 (Predicción de demanda) queda fuera del MVP. Para el MVP, el vendedor decide sobre las reservas con su propio criterio (RF-53), sin predicción por IA. RF-59 se conserva documentado como mejora futura.

## Reglas generales del dominio

- **Tipos de vendedor**: el vendedor es ambulante o de punto fijo; el tipo se elige al crear la cuenta y determina las modalidades de entrega que puede ofrecer.
- **Modalidades de entrega**: entrega directa (vendedor ambulante, él mismo entrega); recogida en el punto fijo o domicilio (vendedor de punto fijo, el domicilio interviene un domiciliario). Cualquier pedido, sin importar su modalidad, puede marcarse además como **reserva**, un atributo del pedido con **fecha y hora libremente acordadas con el vendedor**, no una modalidad de entrega distinta.
- **Pedido e ítems**: un pedido tiene uno o varios ítems (producto y cantidad).
- **Reservas**: el vendedor no publica disponibilidad futura ni la plataforma valida las reservas contra un inventario por fecha. Una reserva queda pendiente y el vendedor la **acepta o rechaza con su propio criterio** (RF-53), igual que cualquier otro pedido. El inventario del producto (RF-57) refleja únicamente la cantidad disponible actual. *(La predicción de demanda por IA (RF-59) queda fuera del alcance del MVP.)*
- **Domicilio**: solo lleva un pedido del punto A (punto fijo) al punto B (ubicación del cliente); **no puede editarse**. Lo publica el vendedor de punto fijo **una vez el pedido está aceptado y con el pago confirmado**, lo toman los domiciliarios aceptando la **tarifa publicada** (el domiciliario no negocia el precio) y se cierra con la confirmación del domiciliario (reforzada con un código de confirmación que el cliente le entrega) y la confirmación de llegada del cliente.
- **Rechazo de domicilio**: el domiciliario puede rechazar de forma **definitiva** un domicilio disponible que no le interese; el domicilio se retira de su lista de forma permanente (no existe restauración) y sigue disponible para los demás domiciliarios.
- **Pago**: admite dos medios: **efectivo** o **transferencia a la cuenta bancaria** que el vendedor registre. La entrega directa y la recogida en punto fijo admiten cualquiera de los dos, según lo que el vendedor haya configurado como aceptado (RF-63), y el cobro se confirma al registrar la entrega (RF-37) o el retiro (RF-65). El **domicilio admite exclusivamente pago digital** e involucra dos pagos distintos: (a) el pago del **pedido**, un solo monto que suma los productos más la tarifa del domicilio, que el cliente transfiere a la cuenta del vendedor apenas este acepta el pedido y que el vendedor confirma (RF-42) **antes de publicar** el domicilio (RF-14); y (b) el pago de la **tarifa al domiciliario** (RF-43): una vez el domicilio está «entregado», el vendedor le transfiere la tarifa y confirma el pago, y el domiciliario confirma que lo recibió, siempre por transferencia. La plataforma no procesa tarjetas ni billeteras electrónicas de terceros; solo registra la confirmación de cada cobro.
- **Autenticación**: la credencial es la contraseña. El celular se verifica con un código de un solo uso enviado por WhatsApp (SMS de respaldo) al registrarse, al cambiarlo y al recuperar la contraseña (RF-70). El administrador entra solo con contraseña.
- **Granularidad**: cada requerimiento corresponde a una pantalla o acción del usuario. Los filtros, ordenamientos y validaciones son funcionalidad del requerimiento y no requerimientos aparte.

## 1. Gestionar comentarios y calificaciones

- **RF-01 – Calificar y comentar entidades**: El sistema deberá permitir a los usuarios (cliente, vendedor, domiciliario) calificar y comentar emprendimientos, productos, domiciliarios y clientes según las interacciones permitidas para su rol. (HU-01 a HU-07)
- **RF-02 – Consultar calificaciones y comentarios**: El sistema deberá permitir a los usuarios (cliente, vendedor, domiciliario) consultar las calificaciones y comentarios asociados a emprendimientos, productos, domiciliarios y clientes. (HU-08 a HU-18)
- **RF-03 – Supervisión de calificaciones y comentarios**: El sistema deberá permitir al administrador consultar las calificaciones y comentarios publicados en la plataforma con fines de supervisión. (HU-19)
- **RF-04 – Edición de comentarios propios**: El sistema deberá permitir a los usuarios editar los comentarios que hayan publicado. (HU-21)
- **RF-05 – Eliminación de comentarios propios**: El sistema deberá permitir a los usuarios eliminar los comentarios que hayan publicado. (HU-20)
- **RF-06 – Moderación de comentarios**: El sistema deberá permitir al administrador editar o eliminar comentarios que incumplan las reglas de la plataforma. (HU-22, HU-23)

## 2. Gestionar cuenta

- **RF-07 – Registro de cuenta**: El sistema deberá permitir a una persona crear una cuenta para utilizar los servicios de la plataforma, eligiendo su rol: cliente, vendedor ambulante, vendedor de punto fijo o domiciliario; el celular se verifica con un código por WhatsApp (RF-70). (HU-24)
- **RF-08 – Edición de datos de cuenta**: El sistema deberá permitir a los usuarios editar su información personal; un celular nuevo se verifica con un código (RF-70). (HU-25)
- **RF-09 – Cambio de contraseña**: El sistema deberá permitir a los usuarios y al administrador cambiar su contraseña para mantener la seguridad de su cuenta, ingresando la actual o, en el caso de los usuarios, recuperándola con un código enviado a su celular verificado (RF-70). (HU-26, HU-30)
- **RF-10 – Inicio de sesión**: El sistema deberá permitir a los usuarios y al administrador iniciar sesión para acceder a las funcionalidades correspondientes a su perfil; las cuentas con el celular sin verificar no pueden iniciar sesión (RF-70). (HU-27, HU-29)
- **RF-11 – Eliminación de cuenta**: El sistema deberá permitir a los usuarios eliminar su cuenta. (HU-28)
- **RF-70 – Verificación de celular por WhatsApp**: El sistema deberá verificar el celular de un usuario mediante un código de un solo uso enviado por WhatsApp (con SMS de respaldo), emitido por el proveedor de verificación, al registrarse, al cambiar el celular y al recuperar la contraseña. No sustituye la contraseña como credencial de inicio de sesión. (HU-105)

## 3. Gestionar domicilios

- **RF-12 – Consulta de domicilios por administrador**: El sistema deberá permitir al administrador consultar los domicilios registrados en la plataforma. (HU-51)
- **RF-13 – Cancelación de domicilio por administrador**: El sistema deberá permitir al administrador cancelar un domicilio indicando el motivo. (HU-52)
- **RF-14 – Publicación de domicilio**: El sistema deberá permitir al vendedor de punto fijo publicar un domicilio a partir de un pedido con modalidad de domicilio que ya esté **aceptado y con el pago confirmado (RF-42)**, tomando como punto A su punto fijo y como punto B la ubicación indicada por el cliente, para que quede disponible para los domiciliarios. Un pedido aceptado sin pago confirmado no puede publicarse. Un domicilio publicado no puede editarse. (HU-31)
- **RF-15 – Listado de domicilios del vendedor**: El sistema deberá permitir al vendedor de punto fijo consultar el listado de sus domicilios. (HU-32)
- **RF-16 – Consulta de domicilios disponibles**: El sistema deberá permitir al domiciliario consultar los domicilios disponibles para ser tomados, mostrando la ganancia (la tarifa publicada) que recibiría por cada uno. (HU-36)
- **RF-17 – Rechazo de domicilios** *(antes "Archivado de domicilios")*: El sistema deberá permitir al domiciliario rechazar de forma **definitiva** un domicilio disponible que no le interese, retirándolo permanentemente de su lista de disponibles, sin afectar la disponibilidad del domicilio para los demás domiciliarios. El rechazo no es reversible: no existe una lista de domicilios rechazados ni una acción de restauración. (HU-37)
- **RF-18 – Consulta de información del domicilio por el domiciliario**: El sistema deberá permitir al domiciliario consultar la información de un domicilio (punto de origen, punto de destino, persona que lo recibe y ganancia) y, desde ella, aceptarlo o rechazarlo. (HU-39)
- **RF-19 – Aceptación de domicilio**: El sistema deberá permitir al domiciliario aceptar un domicilio disponible con la tarifa publicada, asignándoselo de forma exclusiva. (HU-40)
- **RF-22 – Ruta en mapa interactivo**: El sistema deberá mostrar al domiciliario un mapa interactivo con la ruta hacia el punto de recogida y el punto de entrega una vez tenga el domicilio asignado. (HU-45)
- **RF-23 – Recogida del pedido y salida del domiciliario**: El sistema deberá permitir al domiciliario confirmar que recogió el pedido en el punto fijo, y al vendedor de punto fijo confirmar que el domiciliario ya va en camino. (HU-35, HU-46)
- **RF-24 – Confirmación de entrega por el domiciliario**: El sistema deberá permitir al domiciliario confirmar que entregó el pedido en el punto de destino, ingresando el código de confirmación entregado por el cliente como parte de la confirmación. (HU-47)
- **RF-25 – Confirmación de llegada por el cliente**: El sistema deberá permitir al cliente ver su código de confirmación y confirmar la llegada de su pedido para dar por finalizado el domicilio. (HU-50)
- **RF-26 – Estado y trazabilidad del domicilio**: El sistema deberá permitir al cliente consultar en todo momento la trazabilidad de su domicilio, y al vendedor de punto fijo consultar su estado actualizado. (HU-33, HU-48)
- **RF-27 – Consulta de información del domiciliario**: El sistema deberá permitir al cliente y al vendedor de punto fijo consultar la información del domiciliario asignado a un domicilio. (HU-34, HU-49)

## 4. Gestionar emprendimientos

- **RF-28 – Creación de emprendimiento**: El sistema deberá permitir al vendedor (ambulante o de punto fijo) crear un emprendimiento para ofrecer sus productos en la plataforma, registrando su ubicación según el tipo de venta. (HU-53)
- **RF-29 – Edición de emprendimiento**: El sistema deberá permitir al vendedor editar la información de su emprendimiento. (HU-54)
- **RF-30 – Eliminación de emprendimiento**: El sistema deberá permitir al vendedor eliminar su emprendimiento. (HU-55)
- **RF-31 – Listado de emprendimientos**: El sistema deberá permitir al cliente consultar el listado de emprendimientos disponibles. (HU-56)
- **RF-32 – Consulta de emprendimiento**: El sistema deberá permitir al cliente consultar la información y oferta de un emprendimiento específico, incluyendo la ubicación, dirección y horario del punto fijo cuando aplique (cubre también la consulta de ubicación de recogida de un pedido). (HU-57)
- **RF-33 – Listado de productos de un emprendimiento**: El sistema deberá permitir al cliente consultar los productos ofrecidos por un emprendimiento. (HU-58)
- **RF-34 – Consulta de información del vendedor**: El sistema deberá permitir al cliente consultar la información del vendedor asociado a un emprendimiento. (HU-59)

## 5. Gestionar entregas del vendedor ambulante

- **RF-35 – Consulta de ubicación de entrega**: El sistema deberá permitir al vendedor ambulante consultar en un mapa la ubicación de entrega de un pedido aceptado de entrega directa (incluidos los marcados como reserva, en su fecha acordada). (HU-60)
- **RF-36 – Inicio de entrega directa**: El sistema deberá permitir al vendedor ambulante iniciar la entrega de un pedido aceptado, notificando al cliente que va en camino. (HU-61)
- **RF-37 – Confirmación de entrega directa**: El sistema deberá permitir al vendedor ambulante confirmar la entrega del pedido al cliente, incluyendo la confirmación del cobro (efectivo o cuenta bancaria, según lo aceptado por el vendedor). (HU-62)

## 6. Gestionar notificaciones

- **RF-38 – Consulta de notificaciones**: El sistema deberá permitir a los usuarios (cliente, vendedor, domiciliario, administrador) consultar sus notificaciones. (HU-63)
- **RF-39 – Configuración de preferencias de notificación**: El sistema deberá permitir a los usuarios configurar qué tipos de notificaciones desean recibir. (HU-64)

## 7. Gestionar pagos

- **RF-40 – Consulta de historial de pagos**: El sistema deberá permitir al cliente, al vendedor y al domiciliario consultar su historial de pagos, en efectivo o digitales. (HU-65, HU-67, HU-69)
- **RF-41 – Consulta de historial general de pagos**: El sistema deberá permitir al administrador consultar el historial de pagos de la plataforma. (HU-70)
- **RF-42 – Confirmación de pago del pedido**: El sistema deberá permitir al vendedor (ambulante o de punto fijo) confirmar que recibió el pago de un pedido, en efectivo o por transferencia a su cuenta bancaria registrada, según el medio utilizado. En entrega directa y recogida, el cobro se confirma al registrar la entrega (RF-37) o el retiro (RF-65). En domicilio, el pago es siempre por transferencia y cubre el total del pedido (productos más tarifa del domicilio): el cliente transfiere apenas el vendedor acepta el pedido y el vendedor confirma su recepción **antes de publicar el domicilio** (RF-14). (HU-66)
- **RF-43 – Confirmación del pago de la tarifa del domicilio**: El sistema deberá permitir al vendedor de punto fijo registrar y confirmar que pagó por transferencia la tarifa del domicilio al domiciliario, y al domiciliario confirmar que la recibió, una vez el domicilio esté entregado. (HU-104, HU-68)
- **RF-44 – Configuración de tarifa mínima de domicilio**: El sistema deberá permitir al administrador configurar el valor mínimo permitido para los servicios de domicilio (tarifa mínima), que aplica como piso de la tarifa publicada que calcula el sistema para cada domicilio. (HU-71)
- **RF-63 – Configuración de métodos de pago aceptados por el vendedor**: El sistema deberá permitir al vendedor indicar si acepta efectivo y/o registrar una cuenta bancaria para recibir pagos digitales, determinando así los medios de pago disponibles para sus pedidos de entrega directa o de recogida en punto fijo. El domicilio no ofrece esta configuración: siempre exige pago digital a la cuenta registrada. (HU-103)

## 8. Gestionar pedidos

- **RF-45 – Realización de pedido**: El sistema deberá permitir al cliente realizar un pedido seleccionando los productos y cantidades de un emprendimiento, la modalidad de entrega disponible según el tipo de vendedor (entrega directa para el vendedor ambulante; recogida en el punto fijo o domicilio para el vendedor de punto fijo) y la ubicación de entrega cuando aplique, pudiendo además marcar el pedido como reserva indicando la fecha y hora acordadas con el vendedor. El pedido queda en estado pendiente hasta la respuesta del vendedor, quien decide con su propio criterio si lo acepta. El pago se realiza según los medios habilitados para la modalidad elegida (efectivo o cuenta bancaria en entrega directa y recogida; solo digital en domicilio). En domicilio, el sistema muestra el total (productos más tarifa del domicilio) e informa al cliente que deberá pagarlo por transferencia apenas el vendedor acepte el pedido. (HU-72 a HU-74, HU-76)
- **RF-47 – Listado de pedidos del cliente**: El sistema deberá permitir al cliente consultar el listado de sus pedidos. (HU-77)
- **RF-48 – Consulta del estado del pedido**: El sistema deberá permitir al cliente consultar el estado actualizado de su pedido según su modalidad de entrega, indicando además si está marcado como reserva y la fecha y hora acordadas. (HU-78)
- **RF-49 – Contacto con vendedor o domiciliario**: El sistema deberá permitir al cliente contactar al vendedor o domiciliario asignado a su pedido. (HU-79)
- **RF-50 – Listado de pedidos del vendedor**: El sistema deberá permitir al vendedor consultar el listado de pedidos recibidos. (HU-80)
- **RF-51 – Consulta de detalle de pedido**: El sistema deberá permitir al vendedor consultar el detalle completo de un pedido. (HU-81)
- **RF-52 – Consulta de información del cliente**: El sistema deberá permitir al vendedor consultar la información del cliente asociado a un pedido. (HU-82)
- **RF-53 – Respuesta del vendedor a un pedido**: El sistema deberá permitir al vendedor aceptar o rechazar un pedido pendiente. Al aceptarlo, se compromete a atenderlo según su modalidad; al rechazarlo, el cliente es notificado. Cuando el pedido es una reserva, el vendedor decide con su propio criterio si puede cumplirla, sin validación automática de inventario futuro. En un pedido de domicilio, tras aceptarlo queda a la espera del pago del cliente (RF-42) antes de poder publicarlo (RF-14). (HU-83, HU-84)

## 9. Gestionar productos

- **RF-54 – Registro de producto**: El sistema deberá permitir al vendedor añadir un producto a su catálogo. (HU-85)
- **RF-55 – Listado de productos del vendedor**: El sistema deberá permitir al vendedor consultar el listado de sus productos publicados. (HU-86)
- **RF-56 – Edición de producto**: El sistema deberá permitir al vendedor editar la información de un producto publicado. (HU-87)
- **RF-57 – Actualización de inventario del producto**: El sistema deberá permitir al vendedor actualizar la cantidad disponible actual de un producto, marcándolo como agotado cuando llegue a cero. (HU-88)
- **RF-58 – Eliminación de producto**: El sistema deberá permitir al vendedor eliminar un producto de su catálogo. (HU-89)
- **RF-59 – Predicción de demanda** *(fuera de alcance del MVP)*: El sistema deberá permitir al vendedor consultar una predicción de demanda de un producto basada en inteligencia artificial, como mejora futura para apoyarlo al decidir cuánto comprar o preparar y qué reservas aceptar. **No se implementa en el MVP**: para el MVP, el vendedor decide las reservas con su propio criterio (RF-53). (HU-90)

## 10. Gestionar reportes

- **RF-60 – Creación de reportes**: El sistema deberá permitir al cliente, vendedor y domiciliario reportar problemas relacionados con la plataforma o sus servicios. (HU-91 a HU-93)
- **RF-61 – Consulta de reportes por administrador**: El sistema deberá permitir al administrador consultar los reportes registrados por los usuarios. (HU-94)
- **RF-62 – Resolución de reportes**: El sistema deberá permitir al administrador dar solución y cerrar un reporte. (HU-95)

## 11. Gestionar recogida en punto fijo

- **RF-64 – Recogida lista para retirar**: El sistema deberá permitir al vendedor de punto fijo marcar un pedido aceptado con modalidad de recogida como listo para retirar, notificando al cliente. (HU-97)
- **RF-65 – Confirmación de retiro del pedido**: El sistema deberá permitir al vendedor de punto fijo confirmar el retiro de un pedido por parte del cliente, incluyendo la confirmación del cobro (efectivo o cuenta bancaria, según lo aceptado por el vendedor). (HU-98)

## 12. Gestionar usuarios

- **RF-66 – Consulta de usuario por administrador**: El sistema deberá permitir al administrador consultar la información de un usuario. (HU-99)
- **RF-67 – Edición de usuario por administrador**: El sistema deberá permitir al administrador editar o corregir la información de un usuario. (HU-100)
- **RF-68 – Eliminación de usuario por administrador**: El sistema deberá permitir al administrador eliminar la cuenta de un usuario. (HU-101)
- **RF-69 – Listado de usuarios**: El sistema deberá permitir al administrador consultar el listado general de usuarios registrados. (HU-102)
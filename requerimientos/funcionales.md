# Requerimientos Funcionales

> Derivados del backlog de 104 historias de usuario (`investigacion/HistoriasDeUsuario/historias_de_usuario.md`), organizados por módulo. Entre paréntesis se indican las historias de usuario (HU) que sustentan cada requerimiento. Cada requerimiento tiene su especificación detallada en `specs/`.

## Reglas generales del dominio

- **Tipos de vendedor**: el vendedor es ambulante o de punto fijo; el tipo se elige al crear la cuenta y determina las modalidades de entrega que puede ofrecer.
- **Modalidades de entrega**: entrega directa y reserva con vendedor ambulante; reserva (con retiro) y domicilio con vendedor de punto fijo.
- **Pedido e ítems**: un pedido tiene uno o varios ítems (producto y cantidad).
- **Reservas**: se hacen para el día siguiente y se validan contra la disponibilidad prevista del vendedor (predicción de demanda e inventario).
- **Domicilio**: solo lleva un pedido del punto A (punto fijo) al punto B (ubicación del cliente); **no puede editarse**. Lo publica el vendedor de punto fijo, lo toman los domiciliarios y se cierra con la confirmación del domiciliario y del cliente.
- **Pago**: el cliente elige el método al hacer el pedido, según el vendedor y la modalidad: el vendedor ambulante acepta efectivo o transferencia (entrega directa y reserva); el vendedor de punto fijo acepta efectivo o transferencia en las reservas con retiro y **solo transferencia** en los domicilios. La plataforma no procesa el dinero: registra el método elegido y la confirmación de cada pago.
- **Granularidad**: cada requerimiento corresponde a una pantalla o acción del usuario. Los filtros, ordenamientos y validaciones son funcionalidad del requerimiento y no requerimientos aparte.

## 1. Gestionar comentarios y calificaciones

- **RF-01 – Calificar y comentar entidades**: El sistema deberá permitir a los usuarios (cliente, vendedor, domiciliario) calificar y comentar emprendimientos, productos, domiciliarios y clientes según las interacciones permitidas para su rol. (HU-01 a HU-07)
- **RF-02 – Consultar calificaciones y comentarios**: El sistema deberá permitir a los usuarios (cliente, vendedor, domiciliario) consultar las calificaciones y comentarios asociados a emprendimientos, productos, domiciliarios y clientes. (HU-08 a HU-18)
- **RF-03 – Supervisión de calificaciones y comentarios**: El sistema deberá permitir al administrador consultar las calificaciones y comentarios publicados en la plataforma con fines de supervisión. (HU-19)
- **RF-04 – Edición de comentarios propios**: El sistema deberá permitir a los usuarios editar los comentarios que hayan publicado. (HU-21)
- **RF-05 – Eliminación de comentarios propios**: El sistema deberá permitir a los usuarios eliminar los comentarios que hayan publicado. (HU-20)
- **RF-06 – Moderación de comentarios**: El sistema deberá permitir al administrador editar o eliminar comentarios que incumplan las reglas de la plataforma. (HU-22, HU-23)

## 2. Gestionar cuenta

- **RF-07 – Registro de cuenta**: El sistema deberá permitir a una persona crear una cuenta para utilizar los servicios de la plataforma, eligiendo su rol: cliente, vendedor ambulante, vendedor de punto fijo o domiciliario. (HU-24)
- **RF-08 – Edición de datos de cuenta**: El sistema deberá permitir a los usuarios editar su información personal. (HU-25)
- **RF-09 – Cambio de contraseña**: El sistema deberá permitir a los usuarios y al administrador cambiar su contraseña para mantener la seguridad de su cuenta. (HU-26, HU-30)
- **RF-10 – Inicio de sesión**: El sistema deberá permitir a los usuarios y al administrador iniciar sesión para acceder a las funcionalidades correspondientes a su perfil. (HU-27, HU-29)
- **RF-11 – Eliminación de cuenta**: El sistema deberá permitir a los usuarios eliminar su cuenta. (HU-28)

## 3. Gestionar domicilios

- **RF-12 – Consulta de domicilios por administrador**: El sistema deberá permitir al administrador consultar los domicilios registrados en la plataforma. (HU-51)
- **RF-13 – Cancelación de domicilio por administrador**: El sistema deberá permitir al administrador cancelar un domicilio indicando el motivo. (HU-52)
- **RF-14 – Publicación de domicilio**: El sistema deberá permitir al vendedor de punto fijo publicar un domicilio a partir de un pedido aceptado con modalidad de domicilio, tomando como punto A su punto fijo y como punto B la ubicación indicada por el cliente, para que quede disponible para los domiciliarios. Un domicilio publicado no puede editarse. (HU-31)
- **RF-15 – Listado de domicilios del vendedor**: El sistema deberá permitir al vendedor de punto fijo consultar el listado de sus domicilios. (HU-32)
- **RF-16 – Consulta de domicilios disponibles**: El sistema deberá permitir al domiciliario consultar los domicilios disponibles para ser tomados, mostrando la ganancia que recibiría por cada uno. (HU-36)
- **RF-17 – Archivado de domicilios**: El sistema deberá permitir al domiciliario archivar los domicilios que no le interesen para que dejen de aparecer en su lista de disponibles, y consultar y restaurar los domicilios archivados, sin afectar la disponibilidad del domicilio para otros domiciliarios. (HU-37, HU-38)
- **RF-18 – Consulta de información del domicilio por el domiciliario**: El sistema deberá permitir al domiciliario consultar la información de un domicilio: punto de origen, punto de destino, persona que lo recibe y ganancia. (HU-39)
- **RF-19 – Aceptación de domicilio**: El sistema deberá permitir al domiciliario aceptar un domicilio disponible con la tarifa publicada, asignándoselo de forma exclusiva. (HU-40)
- **RF-20 – Oferta de precio del domiciliario**: El sistema deberá permitir al domiciliario ofertar un precio distinto al publicado para un domicilio disponible, sin que la oferta sea inferior a la tarifa mínima vigente. (HU-41)
- **RF-21 – Gestión de ofertas de precio por el cliente**: El sistema deberá permitir al cliente consultar las ofertas de precio recibidas por su domicilio y aceptarlas o rechazarlas. Al aceptar una oferta, el domicilio queda asignado al domiciliario que la propuso. (HU-42 a HU-44)
- **RF-22 – Ruta en mapa interactivo**: El sistema deberá mostrar al domiciliario un mapa interactivo con la ruta hacia el punto de recogida y el punto de entrega una vez tenga el domicilio asignado. (HU-45)
- **RF-23 – Recogida del pedido y salida del domiciliario**: El sistema deberá permitir al domiciliario confirmar que recogió el pedido en el punto fijo, y al vendedor de punto fijo confirmar que el domiciliario ya va en camino. (HU-35, HU-46)
- **RF-24 – Confirmación de entrega por el domiciliario**: El sistema deberá permitir al domiciliario confirmar que entregó el pedido en el punto de destino. (HU-47)
- **RF-25 – Confirmación de llegada por el cliente**: El sistema deberá permitir al cliente confirmar la llegada de su pedido para dar por finalizado el domicilio. (HU-50)
- **RF-26 – Estado y trazabilidad del domicilio**: El sistema deberá permitir al cliente consultar en todo momento la trazabilidad de su domicilio, y al vendedor de punto fijo consultar su estado actualizado. (HU-33, HU-48)
- **RF-27 – Consulta de información del domiciliario**: El sistema deberá permitir al cliente y al vendedor de punto fijo consultar la información del domiciliario asignado a un domicilio. (HU-34, HU-49)

## 4. Gestionar emprendimientos

- **RF-28 – Creación de emprendimiento**: El sistema deberá permitir al vendedor (ambulante o de punto fijo) crear un emprendimiento para ofrecer sus productos en la plataforma, registrando su ubicación según el tipo de venta. (HU-53)
- **RF-29 – Edición de emprendimiento**: El sistema deberá permitir al vendedor editar la información de su emprendimiento. (HU-54)
- **RF-30 – Cambio de estado del emprendimiento**: El sistema deberá permitir al vendedor (ambulante o de punto fijo) cambiar el estado de su emprendimiento de abierto a cerrado y viceversa; un emprendimiento cerrado no recibe nuevos pedidos de entrega directa ni de domicilio. (HU-55)
- **RF-31 – Eliminación de emprendimiento**: El sistema deberá permitir al vendedor eliminar su emprendimiento. (HU-56)
- **RF-32 – Listado de emprendimientos**: El sistema deberá permitir al cliente consultar el listado de emprendimientos disponibles. (HU-57)
- **RF-33 – Consulta de emprendimiento**: El sistema deberá permitir al cliente consultar la información y oferta de un emprendimiento específico. (HU-58)
- **RF-34 – Listado de productos de un emprendimiento**: El sistema deberá permitir al cliente consultar los productos ofrecidos por un emprendimiento. (HU-59)
- **RF-35 – Consulta de información del vendedor**: El sistema deberá permitir al cliente consultar la información del vendedor asociado a un emprendimiento. (HU-60)

## 5. Gestionar entregas del vendedor ambulante

- **RF-36 – Consulta de ubicación de entrega**: El sistema deberá permitir al vendedor ambulante consultar en un mapa la ubicación de entrega de un pedido aceptado (entrega directa o reserva). (HU-61)
- **RF-37 – Inicio de entrega directa**: El sistema deberá permitir al vendedor ambulante iniciar la entrega de un pedido aceptado, notificando al cliente que va en camino. (HU-62)
- **RF-38 – Confirmación de entrega directa**: El sistema deberá permitir al vendedor ambulante confirmar la entrega del pedido al cliente, incluyendo la confirmación del pago recibido. (HU-63)

## 6. Gestionar notificaciones

- **RF-39 – Consulta de notificaciones**: El sistema deberá permitir a los usuarios (cliente, vendedor, domiciliario, administrador) consultar sus notificaciones. (HU-64)
- **RF-40 – Configuración de preferencias de notificación**: El sistema deberá permitir a los usuarios configurar qué tipos de notificaciones desean recibir. (HU-65)

## 7. Gestionar pagos

- **RF-41 – Consulta de historial de pagos**: El sistema deberá permitir al cliente, al vendedor y al domiciliario consultar su historial de pagos. (HU-66, HU-68, HU-70)
- **RF-42 – Consulta de historial general de pagos**: El sistema deberá permitir al administrador consultar el historial de pagos de la plataforma. (HU-71)
- **RF-43 – Confirmación de pago del pedido**: El sistema deberá permitir al vendedor (ambulante o de punto fijo) confirmar que recibió el pago de un pedido, ya sea en efectivo o por transferencia. (HU-67)
- **RF-44 – Confirmación de pago del domicilio**: El sistema deberá permitir al domiciliario confirmar que recibió el pago del servicio de domicilio. (HU-69)
- **RF-45 – Configuración de tarifa mínima de domicilio**: El sistema deberá permitir al administrador configurar el valor mínimo permitido para los servicios de domicilio (tarifa mínima), que aplica a la tarifa publicada y a las ofertas de los domiciliarios. (HU-72)

## 8. Gestionar pedidos

- **RF-46 – Realización de pedido**: El sistema deberá permitir al cliente realizar un pedido seleccionando los productos y cantidades de un emprendimiento, la modalidad de entrega disponible según el tipo de vendedor (entrega directa o reserva con vendedor ambulante; reserva o domicilio con vendedor de punto fijo) la ubicación de entrega cuando aplique y el método de pago permitido para el vendedor y la modalidad (ambulante: efectivo o transferencia; punto fijo: efectivo o transferencia en la reserva y solo transferencia en el domicilio), quedando el pedido en estado pendiente hasta la respuesta del vendedor. (HU-73 a HU-75, HU-77, HU-78)
- **RF-47 – Consulta de disponibilidad prevista para reservas**: El sistema deberá permitir al cliente consultar la disponibilidad prevista de los productos de un emprendimiento para el día siguiente al reservar, y limitar la cantidad reservada a esa disponibilidad. (HU-76)
- **RF-48 – Listado de pedidos del cliente**: El sistema deberá permitir al cliente consultar el listado de sus pedidos. (HU-79)
- **RF-49 – Consulta del estado del pedido**: El sistema deberá permitir al cliente consultar el estado actualizado de su pedido según su modalidad de entrega. (HU-80)
- **RF-50 – Contacto con vendedor o domiciliario**: El sistema deberá permitir al cliente contactar al vendedor o domiciliario asignado a su pedido. (HU-81)
- **RF-51 – Listado de pedidos del vendedor**: El sistema deberá permitir al vendedor consultar el listado de pedidos recibidos. (HU-82)
- **RF-52 – Consulta de detalle de pedido**: El sistema deberá permitir al vendedor consultar el detalle completo de un pedido. (HU-83)
- **RF-53 – Consulta de información del cliente**: El sistema deberá permitir al vendedor consultar la información del cliente asociado a un pedido. (HU-84)
- **RF-54 – Respuesta del vendedor a un pedido**: El sistema deberá permitir al vendedor aceptar o rechazar un pedido pendiente. Al aceptarlo, se compromete a atenderlo según su modalidad; al rechazarlo, el cliente es notificado. (HU-85, HU-86)

## 9. Gestionar productos

- **RF-55 – Registro de producto**: El sistema deberá permitir al vendedor añadir un producto a su catálogo. (HU-87)
- **RF-56 – Listado de productos del vendedor**: El sistema deberá permitir al vendedor consultar el listado de sus productos publicados. (HU-88)
- **RF-57 – Edición de producto**: El sistema deberá permitir al vendedor editar la información de un producto publicado. (HU-89)
- **RF-58 – Actualización de inventario del producto**: El sistema deberá permitir al vendedor actualizar la cantidad disponible de un producto, marcándolo como agotado cuando llegue a cero. (HU-90)
- **RF-59 – Eliminación de producto**: El sistema deberá permitir al vendedor eliminar un producto de su catálogo. (HU-91)
- **RF-60 – Predicción de demanda**: El sistema deberá permitir al vendedor consultar una predicción de demanda de un producto basada en inteligencia artificial, que además alimenta la disponibilidad prevista usada para validar las reservas. (HU-92)

## 10. Gestionar reportes

- **RF-61 – Creación de reportes**: El sistema deberá permitir al cliente, vendedor y domiciliario reportar problemas relacionados con la plataforma o sus servicios. (HU-93 a HU-95)
- **RF-62 – Consulta de reportes por administrador**: El sistema deberá permitir al administrador consultar los reportes registrados por los usuarios. (HU-96)
- **RF-63 – Resolución de reportes**: El sistema deberá permitir al administrador dar solución y cerrar un reporte. (HU-97)

## 11. Gestionar reservas

- **RF-64 – Consulta de ubicación del punto de recogida**: El sistema deberá permitir al cliente consultar la ubicación del punto fijo donde debe retirar una reserva aceptada. (HU-98)
- **RF-65 – Reserva lista para recoger**: El sistema deberá permitir al vendedor de punto fijo marcar una reserva aceptada como lista para recoger, notificando al cliente. (HU-99)
- **RF-66 – Confirmación de retiro de la reserva**: El sistema deberá permitir al vendedor de punto fijo confirmar el retiro de una reserva por parte del cliente, incluyendo la confirmación del pago recibido. (HU-100)

## 12. Gestionar usuarios

- **RF-67 – Consulta de usuario por administrador**: El sistema deberá permitir al administrador consultar la información de un usuario. (HU-101)
- **RF-68 – Edición de usuario por administrador**: El sistema deberá permitir al administrador editar o corregir la información de un usuario. (HU-102)
- **RF-69 – Eliminación de usuario por administrador**: El sistema deberá permitir al administrador eliminar la cuenta de un usuario. (HU-103)
- **RF-70 – Listado de usuarios**: El sistema deberá permitir al administrador consultar el listado general de usuarios registrados. (HU-104)

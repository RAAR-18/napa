# Requerimientos Funcionales

> Derivados del backlog de 102 historias de usuario (`investigacion/HistoriasDeUsuario/historias_de_usuario.md`), organizados por módulo. Entre paréntesis se indican las historias de usuario (HU) que sustentan cada requerimiento. Cada requerimiento tiene su especificación detallada en `specs/`.

## Reglas generales del dominio

- **Tipos de vendedor**: el vendedor es ambulante o de punto fijo; el tipo se elige al crear la cuenta y determina las modalidades de entrega que puede ofrecer.
- **Modalidades de entrega**: entrega directa y reserva con vendedor ambulante; reserva (con retiro) y domicilio con vendedor de punto fijo.
- **Pedido e ítems**: un pedido tiene uno o varios ítems (producto y cantidad).
- **Reservas**: se hacen para el día siguiente y se validan contra la disponibilidad prevista del vendedor (predicción de demanda e inventario).
- **Domicilio**: solo lleva un pedido del punto A (punto fijo) al punto B (ubicación del cliente); **no puede editarse**. Lo publica el vendedor de punto fijo, lo toman los domiciliarios y se cierra con la confirmación del domiciliario y del cliente.
- **Pago**: siempre en **efectivo**. La plataforma no procesa transferencias ni pagos electrónicos; solo registra la confirmación de cada cobro.
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
- **RF-30 – Eliminación de emprendimiento**: El sistema deberá permitir al vendedor eliminar su emprendimiento. (HU-55)
- **RF-31 – Listado de emprendimientos**: El sistema deberá permitir al cliente consultar el listado de emprendimientos disponibles. (HU-56)
- **RF-32 – Consulta de emprendimiento**: El sistema deberá permitir al cliente consultar la información y oferta de un emprendimiento específico. (HU-57)
- **RF-33 – Listado de productos de un emprendimiento**: El sistema deberá permitir al cliente consultar los productos ofrecidos por un emprendimiento. (HU-58)
- **RF-34 – Consulta de información del vendedor**: El sistema deberá permitir al cliente consultar la información del vendedor asociado a un emprendimiento. (HU-59)

## 5. Gestionar entregas del vendedor ambulante

- **RF-35 – Consulta de ubicación de entrega**: El sistema deberá permitir al vendedor ambulante consultar en un mapa la ubicación de entrega de un pedido aceptado (entrega directa o reserva). (HU-60)
- **RF-36 – Inicio de entrega directa**: El sistema deberá permitir al vendedor ambulante iniciar la entrega de un pedido aceptado, notificando al cliente que va en camino. (HU-61)
- **RF-37 – Confirmación de entrega directa**: El sistema deberá permitir al vendedor ambulante confirmar la entrega del pedido al cliente, incluyendo la confirmación del cobro en efectivo. (HU-62)

## 6. Gestionar notificaciones

- **RF-38 – Consulta de notificaciones**: El sistema deberá permitir a los usuarios (cliente, vendedor, domiciliario, administrador) consultar sus notificaciones. (HU-63)
- **RF-39 – Configuración de preferencias de notificación**: El sistema deberá permitir a los usuarios configurar qué tipos de notificaciones desean recibir. (HU-64)

## 7. Gestionar pagos

- **RF-40 – Consulta de historial de pagos**: El sistema deberá permitir al cliente, al vendedor y al domiciliario consultar su historial de pagos en efectivo. (HU-65, HU-67, HU-69)
- **RF-41 – Consulta de historial general de pagos**: El sistema deberá permitir al administrador consultar el historial de pagos de la plataforma. (HU-70)
- **RF-42 – Confirmación de pago en efectivo del pedido**: El sistema deberá permitir al vendedor (ambulante o de punto fijo) confirmar que recibió el pago en efectivo de un pedido. (HU-66)
- **RF-43 – Confirmación de pago en efectivo del domicilio**: El sistema deberá permitir al domiciliario confirmar que recibió en efectivo el pago del servicio de domicilio. (HU-68)
- **RF-44 – Configuración de tarifa mínima de domicilio**: El sistema deberá permitir al administrador configurar el valor mínimo permitido para los servicios de domicilio (tarifa mínima), que aplica a la tarifa publicada y a las ofertas de los domiciliarios. (HU-71)

## 8. Gestionar pedidos

- **RF-45 – Realización de pedido**: El sistema deberá permitir al cliente realizar un pedido seleccionando los productos y cantidades de un emprendimiento, la modalidad de entrega disponible según el tipo de vendedor (entrega directa o reserva con vendedor ambulante; reserva o domicilio con vendedor de punto fijo) y la ubicación de entrega cuando aplique, quedando el pedido en estado pendiente hasta la respuesta del vendedor. El pago es siempre en efectivo. (HU-72 a HU-74, HU-76)
- **RF-46 – Consulta de disponibilidad prevista para reservas**: El sistema deberá permitir al cliente consultar la disponibilidad prevista de los productos de un emprendimiento para el día siguiente al reservar, y limitar la cantidad reservada a esa disponibilidad. (HU-75)
- **RF-47 – Listado de pedidos del cliente**: El sistema deberá permitir al cliente consultar el listado de sus pedidos. (HU-77)
- **RF-48 – Consulta del estado del pedido**: El sistema deberá permitir al cliente consultar el estado actualizado de su pedido según su modalidad de entrega. (HU-78)
- **RF-49 – Contacto con vendedor o domiciliario**: El sistema deberá permitir al cliente contactar al vendedor o domiciliario asignado a su pedido. (HU-79)
- **RF-50 – Listado de pedidos del vendedor**: El sistema deberá permitir al vendedor consultar el listado de pedidos recibidos. (HU-80)
- **RF-51 – Consulta de detalle de pedido**: El sistema deberá permitir al vendedor consultar el detalle completo de un pedido. (HU-81)
- **RF-52 – Consulta de información del cliente**: El sistema deberá permitir al vendedor consultar la información del cliente asociado a un pedido. (HU-82)
- **RF-53 – Respuesta del vendedor a un pedido**: El sistema deberá permitir al vendedor aceptar o rechazar un pedido pendiente. Al aceptarlo, se compromete a atenderlo según su modalidad; al rechazarlo, el cliente es notificado. (HU-83, HU-84)

## 9. Gestionar productos

- **RF-54 – Registro de producto**: El sistema deberá permitir al vendedor añadir un producto a su catálogo. (HU-85)
- **RF-55 – Listado de productos del vendedor**: El sistema deberá permitir al vendedor consultar el listado de sus productos publicados. (HU-86)
- **RF-56 – Edición de producto**: El sistema deberá permitir al vendedor editar la información de un producto publicado. (HU-87)
- **RF-57 – Actualización de inventario del producto**: El sistema deberá permitir al vendedor actualizar la cantidad disponible de un producto, marcándolo como agotado cuando llegue a cero. (HU-88)
- **RF-58 – Eliminación de producto**: El sistema deberá permitir al vendedor eliminar un producto de su catálogo. (HU-89)
- **RF-59 – Predicción de demanda**: El sistema deberá permitir al vendedor consultar una predicción de demanda de un producto basada en inteligencia artificial, que además alimenta la disponibilidad prevista usada para validar las reservas. (HU-90)

## 10. Gestionar reportes

- **RF-60 – Creación de reportes**: El sistema deberá permitir al cliente, vendedor y domiciliario reportar problemas relacionados con la plataforma o sus servicios. (HU-91 a HU-93)
- **RF-61 – Consulta de reportes por administrador**: El sistema deberá permitir al administrador consultar los reportes registrados por los usuarios. (HU-94)
- **RF-62 – Resolución de reportes**: El sistema deberá permitir al administrador dar solución y cerrar un reporte. (HU-95)

## 11. Gestionar reservas en punto fijo

- **RF-63 – Consulta de ubicación del punto de recogida**: El sistema deberá permitir al cliente consultar la ubicación del punto fijo donde debe retirar una reserva aceptada. (HU-96)
- **RF-64 – Reserva lista para recoger**: El sistema deberá permitir al vendedor de punto fijo marcar una reserva aceptada como lista para recoger, notificando al cliente. (HU-97)
- **RF-65 – Confirmación de retiro de la reserva**: El sistema deberá permitir al vendedor de punto fijo confirmar el retiro de una reserva por parte del cliente, incluyendo la confirmación del cobro en efectivo. (HU-98)

## 12. Gestionar usuarios

- **RF-66 – Consulta de usuario por administrador**: El sistema deberá permitir al administrador consultar la información de un usuario. (HU-99)
- **RF-67 – Edición de usuario por administrador**: El sistema deberá permitir al administrador editar o corregir la información de un usuario. (HU-100)
- **RF-68 – Eliminación de usuario por administrador**: El sistema deberá permitir al administrador eliminar la cuenta de un usuario. (HU-101)
- **RF-69 – Listado de usuarios**: El sistema deberá permitir al administrador consultar el listado general de usuarios registrados. (HU-102)

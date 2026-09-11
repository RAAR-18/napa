# Requerimientos Funcionales

> Derivados del backlog de 87 historias de usuario (`investigacion/HistoriasDeUsuario/historias_de_usuario.md`), organizados por módulo. Entre paréntesis se indican las historias de usuario (HU) que sustentan cada requerimiento.

## 1. Gestionar comentarios y calificaciones

- **RF-01 – Calificar y comentar entidades**: El sistema deberá permitir a los usuarios (cliente, vendedor, domiciliario) calificar y comentar emprendimientos, productos, domiciliarios y clientes según las interacciones permitidas para su rol. (HU-01 a HU-06)
- **RF-02 – Consultar calificaciones y comentarios**: El sistema deberá permitir a los usuarios (cliente, vendedor, domiciliario) consultar las calificaciones y comentarios asociados a emprendimientos, productos, domiciliarios y clientes. (HU-07 a HU-17)
- **RF-03 – Supervisión de calificaciones y comentarios**: El sistema deberá permitir al administrador consultar las calificaciones y comentarios publicados en la plataforma con fines de supervisión. (HU-18)
- **RF-04 – Edición de comentarios propios**: El sistema deberá permitir a los usuarios editar los comentarios que hayan publicado. (HU-20)
- **RF-05 – Eliminación de comentarios propios**: El sistema deberá permitir a los usuarios eliminar los comentarios que hayan publicado. (HU-19)
- **RF-06 – Moderación de comentarios**: El sistema deberá permitir al administrador editar o eliminar comentarios que incumplan las reglas de la plataforma. (HU-21, HU-22)

## 2. Gestionar cuenta

- **RF-07 – Registro de cuenta**: El sistema deberá permitir a una persona crear una cuenta para utilizar los servicios de la plataforma. (HU-23)
- **RF-08 – Edición de datos de cuenta**: El sistema deberá permitir a los usuarios editar su información personal. (HU-24)
- **RF-09 – Cambio de contraseña**: El sistema deberá permitir a los usuarios y al administrador cambiar su contraseña para mantener la seguridad de su cuenta. (HU-25, HU-29)
- **RF-10 – Inicio de sesión**: El sistema deberá permitir a los usuarios y al administrador iniciar sesión para acceder a las funcionalidades correspondientes a su perfil. (HU-26, HU-28)
- **RF-11 – Eliminación de cuenta**: El sistema deberá permitir a los usuarios eliminar su cuenta. (HU-27)

## 3. Gestionar domicilios

- **RF-12 – Consulta de domicilios por administrador**: El sistema deberá permitir al administrador consultar los domicilios registrados en la plataforma. (HU-30)
- **RF-13 – Edición de domicilio**: El sistema deberá permitir al vendedor editar la información de un domicilio que aún no haya sido aceptado. (HU-31)
- **RF-14 – Cancelación de domicilio por administrador**: El sistema deberá permitir al administrador cancelar un domicilio indicando el motivo. (HU-32)
- **RF-15 – Listado de domicilios del vendedor**: El sistema deberá permitir al vendedor consultar el listado de sus domicilios. (HU-33)
- **RF-16 – Consulta de domicilios disponibles**: El sistema deberá permitir al domiciliario consultar los domicilios disponibles para ser tomados. (HU-34)
- **RF-17 – Aceptación de domicilio**: El sistema deberá permitir al domiciliario aceptar un domicilio disponible. (HU-35)
- **RF-18 – Rechazo de domicilio**: El sistema deberá permitir al domiciliario rechazar un domicilio disponible sin afectar su disponibilidad para otros domiciliarios. (HU-36)
- **RF-19 – Consulta del estado del domicilio**: El sistema deberá permitir al cliente y al vendedor consultar el estado actualizado de un domicilio en curso. (HU-37, HU-38)
- **RF-20 – Consulta de información del domicilio por el domiciliario**: El sistema deberá permitir al domiciliario consultar la información necesaria de un domicilio para realizar la entrega. (HU-39)
- **RF-21 – Publicación de domicilio**: El sistema deberá permitir al vendedor publicar un domicilio para que quede disponible para los domiciliarios. (HU-40)
- **RF-22 – Actualización de estado en tránsito**: El sistema deberá permitir al domiciliario marcar el avance del domicilio (recogido, en camino). (HU-41)
- **RF-23 – Confirmación de entrega**: El sistema deberá permitir al domiciliario confirmar la entrega de un domicilio mediante el ingreso del código de confirmación. (HU-42, HU-45)
- **RF-24 – Consulta de información del domiciliario**: El sistema deberá permitir al cliente y al vendedor consultar la información del domiciliario asignado a un domicilio. (HU-43, HU-44)
- **RF-25 – Visualización del código de confirmación**: El sistema deberá permitir al cliente visualizar el código de confirmación de entrega para proporcionarlo al domiciliario. (HU-46)

## 4. Gestionar emprendimientos

- **RF-26 – Creación de emprendimiento**: El sistema deberá permitir al vendedor crear un emprendimiento para ofrecer sus productos en la plataforma. (HU-47)
- **RF-27 – Edición de emprendimiento**: El sistema deberá permitir al vendedor editar la información de su emprendimiento. (HU-48)
- **RF-28 – Eliminación de emprendimiento**: El sistema deberá permitir al vendedor eliminar su emprendimiento. (HU-49)
- **RF-29 – Listado de emprendimientos**: El sistema deberá permitir al cliente consultar el listado de emprendimientos disponibles. (HU-50)
- **RF-30 – Consulta de emprendimiento**: El sistema deberá permitir al cliente consultar la información y oferta de un emprendimiento específico. (HU-51)
- **RF-31 – Listado de productos de un emprendimiento**: El sistema deberá permitir al cliente consultar los productos ofrecidos por un emprendimiento. (HU-52)
- **RF-32 – Consulta de información del vendedor**: El sistema deberá permitir al cliente consultar la información del vendedor asociado a un emprendimiento. (HU-53)

## 5. Gestionar notificaciones

- **RF-33 – Consulta de notificaciones**: El sistema deberá permitir a los usuarios (cliente, vendedor, domiciliario, administrador) consultar sus notificaciones. (HU-54)
- **RF-34 – Configuración de preferencias de notificación**: El sistema deberá permitir a los usuarios configurar qué tipos de notificaciones desean recibir. (HU-55)

## 6. Gestionar pagos

- **RF-35 – Selección de método de pago**: El sistema deberá permitir al cliente seleccionar un método de pago para sus compras. (HU-56)
- **RF-36 – Consulta de historial de pagos**: El sistema deberá permitir al cliente, al vendedor y al domiciliario consultar su historial de pagos. (HU-57, HU-59, HU-61)
- **RF-37 – Consulta de historial general de pagos**: El sistema deberá permitir al administrador consultar el historial de pagos de la plataforma. (HU-62)
- **RF-38 – Confirmación de pago de pedido**: El sistema deberá permitir al vendedor confirmar el pago de un pedido. (HU-58)
- **RF-39 – Confirmación de pago de domicilio**: El sistema deberá permitir al domiciliario confirmar el pago del servicio de entrega. (HU-60)
- **RF-40 – Configuración de tarifa mínima de domicilio**: El sistema deberá permitir al administrador configurar el valor mínimo permitido para los servicios de entrega. (HU-63)

## 7. Gestionar pedidos

- **RF-41 – Realización de pedido**: El sistema deberá permitir al cliente crear un pedido seleccionando productos y cantidades, la modalidad de entrega y el método de pago, quedando el pedido en estado pendiente hasta su confirmación por el vendedor. (HU-64, HU-69, HU-70, HU-71)
- **RF-42 – Consulta del estado del pedido**: El sistema deberá permitir al cliente consultar el estado actualizado de su pedido. (HU-65)
- **RF-43 – Contacto con vendedor o domiciliario**: El sistema deberá permitir al cliente contactar al vendedor o domiciliario asignado a su pedido. (HU-66)
- **RF-44 – Listado de pedidos del vendedor**: El sistema deberá permitir al vendedor consultar el listado de pedidos recibidos. (HU-67)
- **RF-45 – Consulta de detalle de pedido**: El sistema deberá permitir al vendedor consultar el detalle completo de un pedido. (HU-68)
- **RF-46 – Consulta de información del cliente**: El sistema deberá permitir al vendedor consultar la información del cliente asociado a un pedido. (HU-72)

## 8. Gestionar productos

- **RF-47 – Registro de producto**: El sistema deberá permitir al vendedor añadir un producto a su catálogo. (HU-73)
- **RF-48 – Listado de productos del vendedor**: El sistema deberá permitir al vendedor consultar el listado de sus productos publicados. (HU-74)
- **RF-49 – Edición de producto**: El sistema deberá permitir al vendedor editar la información de un producto publicado. (HU-75)
- **RF-50 – Actualización de inventario del producto**: El sistema deberá permitir al vendedor actualizar la cantidad disponible de un producto, marcándolo como agotado cuando llegue a cero. (HU-76)
- **RF-51 – Eliminación de producto**: El sistema deberá permitir al vendedor eliminar un producto de su catálogo. (HU-77)
- **RF-52 – Predicción de demanda**: El sistema deberá permitir al vendedor consultar una predicción de demanda de un producto basada en inteligencia artificial. (HU-78)

## 9. Gestionar reportes

- **RF-53 – Creación de reportes**: El sistema deberá permitir al cliente, vendedor y domiciliario reportar problemas relacionados con la plataforma o sus servicios. (HU-79, HU-80, HU-81)
- **RF-54 – Consulta de reportes por administrador**: El sistema deberá permitir al administrador consultar los reportes registrados por los usuarios. (HU-82)
- **RF-55 – Resolución de reportes**: El sistema deberá permitir al administrador dar solución y cerrar un reporte. (HU-83)

## 10. Gestionar usuarios

- **RF-56 – Consulta de usuario por administrador**: El sistema deberá permitir al administrador consultar la información de un usuario. (HU-84)
- **RF-57 – Edición de usuario por administrador**: El sistema deberá permitir al administrador editar o corregir la información de un usuario. (HU-85)
- **RF-58 – Eliminación de usuario por administrador**: El sistema deberá permitir al administrador eliminar la cuenta de un usuario. (HU-86)
- **RF-59 – Listado de usuarios**: El sistema deberá permitir al administrador consultar el listado general de usuarios registrados. (HU-87)

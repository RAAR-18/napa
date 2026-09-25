# Historias de Usuario — Ñapa

> Backlog de historias de usuario derivadas de los casos de uso del sistema **Ñapa**.
> Cada historia corresponde a una pantalla o acción que el usuario final realiza en la aplicación (un botón o una vista). Los filtros, ordenamientos y validaciones se consideran funcionalidad de la historia y se detallan en su especificación (`specs/`), no como historias aparte.


---

## Índice

- [1. Comentarios y calificaciones](#1-gestionar-comentarios-y-calificaciones)
- [2. Cuenta](#2-gestionar-cuenta)
- [3. Domicilios](#3-gestionar-domicilios)
- [4. Emprendimientos](#4-gestionar-emprendimientos)
- [5. Entregas del vendedor ambulante](#5-gestionar-entregas-del-vendedor-ambulante)
- [6. Notificaciones](#6-gestionar-notificaciones)
- [7. Pagos](#7-gestionar-pagos)
- [8. Pedidos](#8-gestionar-pedidos)
- [9. Productos](#9-gestionar-productos)
- [10. Reportes](#10-gestionar-reportes)
- [11. Recogida en punto fijo](#11-gestionar-recogida-en-punto-fijo)
- [12. Usuarios](#12-gestionar-usuarios)

---

## Resumen

| Módulo | Historias |
|---|:---:|
| Comentarios y calificaciones | 23 |
| Cuenta | 7 |
| Domicilios | 21 |
| Emprendimientos | 7 |
| Entregas del vendedor ambulante | 3 |
| Notificaciones | 2 |
| Pagos | 8 |
| Pedidos | 13 |
| Productos | 6 |
| Reportes | 5 |
| Recogida en punto fijo | 2 |
| Usuarios | 4 |
| **Total** | **101** |

> HU-90 (predicción de demanda) se conserva documentada pero está fuera de alcance del MVP; se cuenta en el total como historia diseñada, no como historia a implementar en esta entrega.

---

# 1. Gestionar comentarios y calificaciones

| ID | Historia de usuario |
|---|---|
| HU-01 | Como cliente, quiero calificar y comentar un emprendimiento, para expresar mi experiencia con él. |
| HU-02 | Como cliente, quiero calificar y comentar un producto, para expresar mi opinión sobre el producto adquirido. |
| HU-03 | Como cliente, quiero calificar y comentar un domiciliario, para valorar la calidad del servicio recibido. |
| HU-04 | Como vendedor de punto fijo, quiero calificar y comentar un domiciliario, para valorar su servicio de entrega. |
| HU-05 | Como domiciliario, quiero calificar y comentar un emprendimiento, para expresar mi experiencia con el establecimiento. |
| HU-06 | Como domiciliario, quiero calificar y comentar un cliente, para valorar mi experiencia durante el servicio. |
| HU-07 | Como vendedor, quiero calificar y comentar un cliente, para valorar mi experiencia durante la venta o la entrega. |
| HU-08 | Como cliente, quiero consultar las calificaciones y comentarios de un emprendimiento, para conocer las experiencias de otros usuarios. |
| HU-09 | Como cliente, quiero consultar las calificaciones y comentarios de un producto, para conocer la opinión de otros compradores. |
| HU-10 | Como cliente, quiero consultar las calificaciones y comentarios de un domiciliario, para conocer la calidad de su servicio. |
| HU-11 | Como cliente, quiero consultar las calificaciones y comentarios de un cliente, para conocer su reputación dentro de la plataforma. |
| HU-12 | Como vendedor, quiero consultar las calificaciones y comentarios de un emprendimiento, para conocer la percepción de los usuarios. |
| HU-13 | Como vendedor, quiero consultar las calificaciones y comentarios de un producto, para conocer la opinión de los clientes. |
| HU-14 | Como vendedor de punto fijo, quiero consultar las calificaciones y comentarios de un domiciliario, para evaluar su servicio. |
| HU-15 | Como vendedor, quiero consultar las calificaciones y comentarios de un cliente, para conocer su reputación. |
| HU-16 | Como domiciliario, quiero consultar las calificaciones y comentarios de un emprendimiento, para conocer las experiencias de otros usuarios. |
| HU-17 | Como domiciliario, quiero consultar las calificaciones y comentarios de un domiciliario, para conocer su reputación. |
| HU-18 | Como domiciliario, quiero consultar las calificaciones y comentarios de un cliente, para conocer su reputación. |
| HU-19 | Como administrador, quiero consultar las calificaciones y comentarios, para supervisar la información publicada en la plataforma. |
| HU-20 | Como usuario, quiero eliminar mis comentarios, para retirar contenido que ya no deseo mantener publicado. |
| HU-21 | Como usuario, quiero editar mis comentarios, para corregir o actualizar la información que publiqué. |
| HU-22 | Como administrador, quiero eliminar comentarios, para retirar contenido que incumpla las reglas de la plataforma. |
| HU-23 | Como administrador, quiero editar comentarios, para gestionar contenido que requiera modificaciones. |

---

# 2. Gestionar cuenta

| ID | Historia de usuario |
|---|---|
| HU-24 | Como usuario, quiero crear una cuenta indicando mi rol (cliente, vendedor ambulante, vendedor de punto fijo o domiciliario), para poder utilizar los servicios de la plataforma correspondientes a ese rol. |
| HU-25 | Como usuario, quiero editar mi cuenta, para mantener actualizada mi información personal. |
| HU-26 | Como usuario, quiero cambiar mi contraseña, para mantener segura mi cuenta. |
| HU-27 | Como usuario, quiero iniciar sesión en mi cuenta, para acceder a las funcionalidades correspondientes a mi perfil. |
| HU-28 | Como usuario, quiero eliminar mi cuenta, para dejar de utilizar la plataforma. |
| HU-29 | Como administrador, quiero iniciar sesión en mi cuenta, para acceder a las funciones administrativas. |
| HU-30 | Como administrador, quiero cambiar mi contraseña, para mantener segura mi cuenta administrativa. |

---

# 3. Gestionar domicilios

| ID | Historia de usuario |
|---|---|
| HU-31 | Como vendedor de punto fijo, quiero publicar un domicilio para un pedido aceptado, para que los domiciliarios puedan llevarlo al cliente. |
| HU-32 | Como vendedor de punto fijo, quiero listar mis domicilios, para consultar los servicios de entrega asociados a mis pedidos. |
| HU-33 | Como vendedor de punto fijo, quiero consultar el estado de un domicilio, para conocer el avance de la entrega. |
| HU-34 | Como vendedor de punto fijo, quiero consultar la información del domiciliario, para conocer quién recogerá el pedido. |
| HU-35 | Como vendedor de punto fijo, quiero confirmar que el domiciliario ya va en camino, para dejar constancia de que recibió el pedido y salió hacia el cliente. |
| HU-36 | Como domiciliario, quiero ver los domicilios disponibles junto con lo que ganaría por cada uno, para elegir cuáles me convienen. |
| HU-37 | Como domiciliario, quiero rechazar de forma definitiva un domicilio disponible que no me interesa, para que deje de aparecer en mi lista. |
| HU-39 | Como domiciliario, quiero consultar la información de un domicilio (de dónde sale, a dónde va, quién lo recibe y cuánto ganaría), para decidir si lo tomo y saber cómo realizar la entrega. |
| HU-40 | Como domiciliario, quiero aceptar un domicilio con la tarifa publicada, para asignármelo y realizar la entrega. |
| HU-41 | Como domiciliario, quiero ofertar un precio distinto por un domicilio, para proponer una ganancia acorde al esfuerzo cuando la publicada me parece baja. |
| HU-42 | Como cliente, quiero consultar las ofertas de precio de los domiciliarios sobre mi domicilio, para decidir con quién y a qué valor recibo mi pedido. |
| HU-43 | Como cliente, quiero aceptar la oferta de un domiciliario, para asignarle mi domicilio al valor propuesto. |
| HU-44 | Como cliente, quiero rechazar la oferta de un domiciliario, para mantener mi domicilio disponible para otros domiciliarios. |
| HU-45 | Como domiciliario, quiero ver un mapa interactivo con la ruta hacia el punto de recogida y el punto de entrega, para llegar al destino sin perderme. |
| HU-46 | Como domiciliario, quiero confirmar que recogí el pedido en el punto fijo, para iniciar el trayecto hacia el cliente. |
| HU-47 | Como domiciliario, quiero confirmar la entrega del pedido ingresando el código de confirmación que me da el cliente, para registrar que lo llevé al punto de destino. |
| HU-48 | Como cliente, quiero consultar la trazabilidad de mi domicilio, para saber en todo momento en qué etapa va mi pedido y dónde está. |
| HU-49 | Como cliente, quiero consultar la información del domiciliario, para conocer quién realizará mi entrega. |
| HU-50 | Como cliente, quiero confirmar la llegada de mi pedido, para dar por finalizado el domicilio. |
| HU-51 | Como administrador, quiero consultar domicilios, para supervisar los servicios de entrega registrados. |
| HU-52 | Como administrador, quiero cancelar un domicilio, para gestionar servicios que deban ser cancelados. |

> Un domicilio solo lleva un pedido del punto A (punto fijo del vendedor) al punto B (ubicación del cliente) y **no puede editarse**. Los domicilios son ofrecidos únicamente por vendedores de punto fijo. El cierre del domicilio se refuerza con un código de confirmación que el cliente entrega al domiciliario (ver RF-24, RF-25).

---

# 4. Gestionar emprendimientos

| ID | Historia de usuario |
|---|---|
| HU-53 | Como vendedor, quiero crear mi emprendimiento indicando mi ubicación de venta, para ofrecer mis productos en la plataforma. |
| HU-54 | Como vendedor, quiero editar mi emprendimiento, para mantener actualizada su información (y mi ubicación, si vendo de forma ambulante). |
| HU-55 | Como vendedor, quiero eliminar mi emprendimiento, para dejar de ofrecerlo en la plataforma. |
| HU-56 | Como cliente, quiero listar los emprendimientos, para conocer las opciones disponibles cerca de mí. |
| HU-57 | Como cliente, quiero consultar un emprendimiento, para conocer su información, su oferta, su ubicación y las modalidades de entrega que ofrece. |
| HU-58 | Como cliente, quiero listar los productos de un emprendimiento, para conocer los productos que ofrece. |
| HU-59 | Como cliente, quiero ver la información del vendedor, para conocer quién está detrás del emprendimiento. |

---

# 5. Gestionar entregas del vendedor ambulante

| ID | Historia de usuario |
|---|---|
| HU-60 | Como vendedor ambulante, quiero ver en un mapa la ubicación del cliente, para llegar hasta donde está y entregarle su pedido. |
| HU-61 | Como vendedor ambulante, quiero iniciar la entrega de un pedido, para avisar al cliente que voy en camino. |
| HU-62 | Como vendedor ambulante, quiero confirmar la entrega del pedido, para registrar que fue entregado al cliente y cobrado (en efectivo o por cuenta bancaria, según lo que acepte). |

> Estas historias aplican tanto a pedidos inmediatos como a pedidos de entrega directa marcados como reserva.

---

# 6. Gestionar notificaciones

| ID | Historia de usuario |
|---|---|
| HU-63 | Como usuario, quiero consultar mis notificaciones, para conocer las novedades y eventos relacionados con mi cuenta (nuevos pedidos, ofertas, cambios de estado, entre otros). |
| HU-64 | Como usuario, quiero configurar mis preferencias de notificación, para decidir qué tipos de notificaciones deseo recibir. |

> Estas historias aplican a los diferentes tipos de usuario definidos en el sistema: **cliente, vendedor, domiciliario y administrador**.

---

# 7. Gestionar pagos

| ID | Historia de usuario |
|---|---|
| HU-65 | Como cliente, quiero consultar mi historial de pagos, para revisar los pagos que he realizado. |
| HU-66 | Como vendedor, quiero confirmar el pago de un pedido, para registrar que el pedido fue pagado (en efectivo o por cuenta bancaria). |
| HU-67 | Como vendedor, quiero consultar el historial de pagos, para revisar los cobros relacionados con mis pedidos. |
| HU-68 | Como domiciliario, quiero confirmar el pago digital del domicilio, para registrar el cobro por el servicio de entrega. |
| HU-69 | Como domiciliario, quiero consultar el historial de pagos, para revisar lo que se ha cobrado por mis servicios. |
| HU-70 | Como administrador, quiero consultar el historial de pagos, para supervisar las transacciones de la plataforma. |
| HU-71 | Como administrador, quiero configurar la tarifa mínima del domicilio, para establecer el valor mínimo permitido para los servicios de entrega. |
| HU-103 | Como vendedor, quiero indicar si acepto efectivo y/o registrar una cuenta bancaria, para definir los medios de pago disponibles en mis pedidos de entrega directa o recogida en punto fijo. |

> El pago admite dos medios: **efectivo** o **transferencia a la cuenta bancaria del vendedor**. La entrega directa y la recogida en punto fijo admiten cualquiera de los dos, según lo configurado por el vendedor (HU-103); el domicilio admite exclusivamente pago digital.

---

# 8. Gestionar pedidos

| ID | Historia de usuario |
|---|---|
| HU-72 | Como cliente, quiero seleccionar los productos y las cantidades de un emprendimiento, para definir los artículos que deseo comprar. |
| HU-73 | Como cliente, quiero seleccionar la modalidad de entrega según el tipo de vendedor (entrega directa con un vendedor ambulante; recogida en el punto fijo o domicilio con un vendedor de punto fijo), para elegir cómo recibir mi pedido. |
| HU-74 | Como cliente, quiero indicar la ubicación donde recibiré mi pedido cuando la modalidad lo requiera (entrega directa o domicilio), para que el vendedor o el domiciliario sepan a dónde llevarlo. |
| HU-75 | Como cliente, quiero consultar la disponibilidad que el vendedor declaró para la fecha que acordemos al marcar mi pedido como reserva, para reservar solo lo que el vendedor tendrá. |
| HU-76 | Como cliente, quiero realizar un pedido con los productos seleccionados, pudiendo marcarlo como reserva con la fecha y hora que acuerde con el vendedor, para comprarlos y pagarlos con el medio disponible para la modalidad elegida (efectivo o cuenta bancaria en entrega directa y recogida; solo digital en domicilio). |
| HU-77 | Como cliente, quiero listar mis pedidos, para consultar el historial de mis compras. |
| HU-78 | Como cliente, quiero consultar el estado de mi pedido, para conocer el avance de mi compra. |
| HU-79 | Como cliente, quiero contactar al vendedor o domiciliario, para comunicarme con ellos cuando tenga alguna inquietud sobre mi pedido. |
| HU-80 | Como vendedor, quiero listar los pedidos que recibo, para consultarlos y atenderlos. |
| HU-81 | Como vendedor, quiero consultar un pedido, para conocer todos sus detalles (ítems, modalidad de entrega y cliente). |
| HU-82 | Como vendedor, quiero consultar la información del cliente, para conocer los datos necesarios para gestionar su pedido. |
| HU-83 | Como vendedor, quiero aceptar un pedido, para comprometerme a atenderlo según su modalidad de entrega. |
| HU-84 | Como vendedor, quiero rechazar un pedido, para indicar que no puedo atenderlo. |

> **Reserva** es un atributo del pedido (sí/no, con fecha y hora libremente acordadas con el vendedor), independiente de la modalidad de entrega elegida; no es una modalidad aparte. Para el MVP, la disponibilidad se valida contra el inventario que el propio vendedor declara (RF-46), sin predicción de demanda (RF-59 queda fuera de alcance del MVP).

---

# 9. Gestionar productos

| ID | Historia de usuario |
|---|---|
| HU-85 | Como vendedor, quiero añadir un producto, para ofrecerlo a los clientes. |
| HU-86 | Como vendedor, quiero listar mis productos, para consultar los productos que tengo disponibles. |
| HU-87 | Como vendedor, quiero editar un producto, para actualizar su información. |
| HU-88 | Como vendedor, quiero actualizar la cantidad de un producto, para mantener actualizado mi inventario. |
| HU-89 | Como vendedor, quiero eliminar un producto, para dejar de ofrecerlo. |
| ~~HU-90~~ | *(Fuera de alcance del MVP)* Como vendedor, quiero consultar la predicción de demanda de un producto, para tomar mejores decisiones sobre mi inventario y decidir qué reservas puedo cumplir. Se conserva documentada como mejora futura; no se cuenta en el total del MVP. |

---

# 10. Gestionar reportes

| ID | Historia de usuario |
|---|---|
| HU-91 | Como cliente, quiero reportar un problema, para informar situaciones que requieran atención. |
| HU-92 | Como vendedor, quiero reportar un problema, para informar inconvenientes relacionados con la plataforma o sus servicios. |
| HU-93 | Como domiciliario, quiero reportar un problema, para informar inconvenientes ocurridos durante mi actividad. |
| HU-94 | Como administrador, quiero consultar los reportes, para identificar y revisar los problemas informados por los usuarios. |
| HU-95 | Como administrador, quiero resolver un reporte, para dar solución a los problemas informados. |

---

# 11. Gestionar recogida en punto fijo

| ID | Historia de usuario |
|---|---|
| HU-97 | Como vendedor de punto fijo, quiero marcar un pedido como listo para recoger, para avisar al cliente que ya puede pasar por él. |
| HU-98 | Como vendedor de punto fijo, quiero confirmar el retiro del pedido, para registrar que el cliente lo recogió y lo pagó (en efectivo o por cuenta bancaria). |

> El cliente retira el pedido personalmente en el punto fijo, sea o no una reserva.

---

# 12. Gestionar usuarios

| ID | Historia de usuario |
|---|---|
| HU-99 | Como administrador, quiero consultar un usuario, para revisar su información. |
| HU-100 | Como administrador, quiero editar un usuario, para actualizar o corregir su información. |
| HU-101 | Como administrador, quiero eliminar un usuario, para gestionar las cuentas que ya no deben permanecer en la plataforma. |
| HU-102 | Como administrador, quiero listar todos los usuarios, para tener una visión general de las cuentas registradas. |

---

## Actores del sistema

| Actor | Descripción |
|---|---|
| **Usuario** | Actor base que representa las funcionalidades comunes de los usuarios de la plataforma. |
| **Cliente** | Usuario que consulta emprendimientos, realiza pedidos (entrega directa, recogida en punto fijo o domicilio, con posibilidad de marcarlos como reserva) y utiliza los servicios de la plataforma. |
| **Vendedor** | Actor abstracto: usuario que administra un emprendimiento y sus productos y atiende pedidos. Se especializa en vendedor ambulante y vendedor de punto fijo. |
| **Vendedor ambulante** | Vendedor que se desplaza (carretilla, termo, puesto móvil). Atiende pedidos de **entrega directa** (va hasta donde está el cliente), inmediatos o marcados como reserva. |
| **Vendedor de punto fijo** | Vendedor con un local o puesto fijo. Atiende pedidos de **recogida en el punto fijo** (el cliente retira) y de **domicilio** (mediante domiciliarios). |
| **Domiciliario** | Usuario encargado de llevar un pedido del punto fijo al cliente (domicilio), con la posibilidad de ofertar su precio o rechazar de forma definitiva un domicilio que no le interese. |
| **Administrador** | Usuario encargado de la administración y supervisión de la plataforma. |

---

## Modalidades de entrega por tipo de vendedor

| Modalidad | Vendedor ambulante | Vendedor de punto fijo | Quién lleva o entrega el pedido |
|---|:---:|:---:|---|
| Entrega directa | ✅ | — | El propio vendedor ambulante va hasta la ubicación del cliente. |
| Recogida en punto fijo | — | ✅ | El cliente lo retira personalmente en el punto fijo. |
| Domicilio | — | ✅ | Un domiciliario lo lleva del punto fijo (A) a la ubicación del cliente (B). |

> **Reserva** no es una cuarta modalidad: es un atributo del pedido (fecha y hora libremente acordadas con el vendedor) que puede aplicarse sobre cualquiera de las tres modalidades anteriores.

---
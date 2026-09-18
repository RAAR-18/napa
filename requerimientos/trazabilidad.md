# Matriz de trazabilidad

> Historia de usuario (HU) → caso de uso (CU) → requerimiento funcional (RF) → especificación. Los diagramas de casos de uso están en `casos de uso/`.

## 1. Gestionar comentarios y calificaciones

Diagrama: [`gestionar_comentarios_calificaciones.puml`](../casos%20de%20uso/gestionar_comentarios_calificaciones.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-01 | Calificar y comentar entidades | HU-01, HU-02, HU-03, HU-04, HU-05, HU-06, HU-07 | Cliente, Vendedor de punto fijo, Domiciliario, Vendedor | Calificar y comentar emprendimiento; Calificar y comentar producto; Calificar y comentar domiciliario; Calificar y comentar cliente | [RF-01-calificar-y-comentar-entidades.md](../specs/RF-01-calificar-y-comentar-entidades.md) |
| RF-02 | Consultar calificaciones y comentarios | HU-08, HU-09, HU-10, HU-11, HU-12, HU-13, HU-14, HU-15, HU-16, HU-17, HU-18 | Cliente, Vendedor, Vendedor de punto fijo, Domiciliario | Consultar calificaciones y comentarios de un emprendimiento; Consultar calificaciones y comentarios de un producto; Consultar calificaciones y comentarios de un domiciliario; Consultar calificaciones y comentarios de un cliente | [RF-02-consultar-calificaciones-y-comentarios.md](../specs/RF-02-consultar-calificaciones-y-comentarios.md) |
| RF-03 | Supervisión de calificaciones y comentarios | HU-19 | Administrador | Consultar calificaciones y comentarios (emprendimiento, producto, domiciliario o cliente) | [RF-03-supervision-de-calificaciones-y-comentarios.md](../specs/RF-03-supervision-de-calificaciones-y-comentarios.md) |
| RF-04 | Edición de comentarios propios | HU-21 | Usuario | Editar comentario | [RF-04-edicion-de-comentarios-propios.md](../specs/RF-04-edicion-de-comentarios-propios.md) |
| RF-05 | Eliminación de comentarios propios | HU-20 | Usuario | Eliminar comentario | [RF-05-eliminacion-de-comentarios-propios.md](../specs/RF-05-eliminacion-de-comentarios-propios.md) |
| RF-06 | Moderación de comentarios | HU-22, HU-23 | Administrador | Eliminar comentario; Editar comentario | [RF-06-moderacion-de-comentarios.md](../specs/RF-06-moderacion-de-comentarios.md) |

## 2. Gestionar cuenta

Diagrama: [`gestionar_cuenta.puml`](../casos%20de%20uso/gestionar_cuenta.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-07 | Registro de cuenta | HU-24 | Usuario | Crear una cuenta | [RF-07-registro-de-cuenta.md](../specs/RF-07-registro-de-cuenta.md) |
| RF-08 | Edición de datos de cuenta | HU-25 | Usuario | Editar mi cuenta | [RF-08-edicion-de-datos-de-cuenta.md](../specs/RF-08-edicion-de-datos-de-cuenta.md) |
| RF-09 | Cambio de contraseña | HU-26, HU-30 | Usuario, Administrador | Cambiar contraseña | [RF-09-cambio-de-contrasena.md](../specs/RF-09-cambio-de-contrasena.md) |
| RF-10 | Inicio de sesión | HU-27, HU-29 | Usuario, Administrador | Iniciar sesión en mi cuenta | [RF-10-inicio-de-sesion.md](../specs/RF-10-inicio-de-sesion.md) |
| RF-11 | Eliminación de cuenta | HU-28 | Usuario | Eliminar mi cuenta | [RF-11-eliminacion-de-cuenta.md](../specs/RF-11-eliminacion-de-cuenta.md) |

## 3. Gestionar domicilios

Diagrama: [`gestionar_domicilios.puml`](../casos%20de%20uso/gestionar_domicilios.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-12 | Consulta de domicilios por administrador | HU-51 | Administrador | Consultar domicilios | [RF-12-consulta-de-domicilios-por-administrador.md](../specs/RF-12-consulta-de-domicilios-por-administrador.md) |
| RF-13 | Cancelación de domicilio por administrador | HU-52 | Administrador | Cancelar domicilio | [RF-13-cancelacion-de-domicilio-por-administrador.md](../specs/RF-13-cancelacion-de-domicilio-por-administrador.md) |
| RF-14 | Publicación de domicilio | HU-31 | Vendedor de punto fijo | Publicar domicilio | [RF-14-publicacion-de-domicilio.md](../specs/RF-14-publicacion-de-domicilio.md) |
| RF-15 | Listado de domicilios del vendedor | HU-32 | Vendedor de punto fijo | Listar domicilios | [RF-15-listado-de-domicilios-del-vendedor.md](../specs/RF-15-listado-de-domicilios-del-vendedor.md) |
| RF-16 | Consulta de domicilios disponibles | HU-36 | Domiciliario | Listar domicilios disponibles | [RF-16-consulta-de-domicilios-disponibles.md](../specs/RF-16-consulta-de-domicilios-disponibles.md) |
| RF-17 | Archivado de domicilios | HU-37, HU-38 | Domiciliario | Archivar domicilio; Consultar domicilios archivados | [RF-17-archivado-de-domicilios.md](../specs/RF-17-archivado-de-domicilios.md) |
| RF-18 | Consulta de información del domicilio por el domiciliario | HU-39 | Domiciliario | Consultar información del domicilio | [RF-18-consulta-de-informacion-del-domicilio-por-el-domiciliario.md](../specs/RF-18-consulta-de-informacion-del-domicilio-por-el-domiciliario.md) |
| RF-19 | Aceptación de domicilio | HU-40 | Domiciliario | Aceptar domicilio | [RF-19-aceptacion-de-domicilio.md](../specs/RF-19-aceptacion-de-domicilio.md) |
| RF-20 | Oferta de precio del domiciliario | HU-41 | Domiciliario | Ofertar precio del domicilio | [RF-20-oferta-de-precio-del-domiciliario.md](../specs/RF-20-oferta-de-precio-del-domiciliario.md) |
| RF-21 | Gestión de ofertas de precio por el cliente | HU-42, HU-43, HU-44 | Cliente | Consultar ofertas de precio; Aceptar oferta de precio; Rechazar oferta de precio | [RF-21-gestion-de-ofertas-de-precio-por-el-cliente.md](../specs/RF-21-gestion-de-ofertas-de-precio-por-el-cliente.md) |
| RF-22 | Ruta en mapa interactivo | HU-45 | Domiciliario | Consultar ruta en el mapa | [RF-22-ruta-en-mapa-interactivo.md](../specs/RF-22-ruta-en-mapa-interactivo.md) |
| RF-23 | Recogida del pedido y salida del domiciliario | HU-46, HU-35 | Domiciliario, Vendedor de punto fijo | Confirmar recogida del pedido; Confirmar salida del domiciliario | [RF-23-recogida-del-pedido-y-salida-del-domiciliario.md](../specs/RF-23-recogida-del-pedido-y-salida-del-domiciliario.md) |
| RF-24 | Confirmación de entrega por el domiciliario | HU-47 | Domiciliario | Confirmar entrega del pedido | [RF-24-confirmacion-de-entrega-por-el-domiciliario.md](../specs/RF-24-confirmacion-de-entrega-por-el-domiciliario.md) |
| RF-25 | Confirmación de llegada por el cliente | HU-50 | Cliente | Confirmar llegada del pedido | [RF-25-confirmacion-de-llegada-por-el-cliente.md](../specs/RF-25-confirmacion-de-llegada-por-el-cliente.md) |
| RF-26 | Estado y trazabilidad del domicilio | HU-48, HU-33 | Cliente, Vendedor de punto fijo | Consultar trazabilidad del domicilio; Consultar estado del domicilio | [RF-26-estado-y-trazabilidad-del-domicilio.md](../specs/RF-26-estado-y-trazabilidad-del-domicilio.md) |
| RF-27 | Consulta de información del domiciliario | HU-34, HU-49 | Vendedor de punto fijo, Cliente | Consultar información del domiciliario | [RF-27-consulta-de-informacion-del-domiciliario.md](../specs/RF-27-consulta-de-informacion-del-domiciliario.md) |

## 4. Gestionar emprendimientos

Diagrama: [`gestionar_emprendimientos.puml`](../casos%20de%20uso/gestionar_emprendimientos.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-28 | Creación de emprendimiento | HU-53 | Vendedor | Crear emprendimiento | [RF-28-creacion-de-emprendimiento.md](../specs/RF-28-creacion-de-emprendimiento.md) |
| RF-29 | Edición de emprendimiento | HU-54 | Vendedor | Editar emprendimiento | [RF-29-edicion-de-emprendimiento.md](../specs/RF-29-edicion-de-emprendimiento.md) |
| RF-30 | Eliminación de emprendimiento | HU-55 | Vendedor | Eliminar emprendimiento | [RF-30-eliminacion-de-emprendimiento.md](../specs/RF-30-eliminacion-de-emprendimiento.md) |
| RF-31 | Listado de emprendimientos | HU-56 | Cliente | Listar emprendimientos | [RF-31-listado-de-emprendimientos.md](../specs/RF-31-listado-de-emprendimientos.md) |
| RF-32 | Consulta de emprendimiento | HU-57 | Cliente | Consultar emprendimiento | [RF-32-consulta-de-emprendimiento.md](../specs/RF-32-consulta-de-emprendimiento.md) |
| RF-33 | Listado de productos de un emprendimiento | HU-58 | Cliente | Listar productos de un emprendimiento | [RF-33-listado-de-productos-de-un-emprendimiento.md](../specs/RF-33-listado-de-productos-de-un-emprendimiento.md) |
| RF-34 | Consulta de información del vendedor | HU-59 | Cliente | Ver información del vendedor | [RF-34-consulta-de-informacion-del-vendedor.md](../specs/RF-34-consulta-de-informacion-del-vendedor.md) |

## 5. Gestionar entregas del vendedor ambulante

Diagrama: [`gestionar_entrega_ambulante.puml`](../casos%20de%20uso/gestionar_entrega_ambulante.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-35 | Consulta de ubicación de entrega | HU-60 | Vendedor ambulante | Consultar ubicación de entrega | [RF-35-consulta-de-ubicacion-de-entrega.md](../specs/RF-35-consulta-de-ubicacion-de-entrega.md) |
| RF-36 | Inicio de entrega directa | HU-61 | Vendedor ambulante | Iniciar entrega | [RF-36-inicio-de-entrega-directa.md](../specs/RF-36-inicio-de-entrega-directa.md) |
| RF-37 | Confirmación de entrega directa | HU-62 | Vendedor ambulante | Confirmar entrega directa | [RF-37-confirmacion-de-entrega-directa.md](../specs/RF-37-confirmacion-de-entrega-directa.md) |

## 6. Gestionar notificaciones

Diagrama: [`gestionar_notificaciones.puml`](../casos%20de%20uso/gestionar_notificaciones.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-38 | Consulta de notificaciones | HU-63 | Usuario | Consultar notificaciones | [RF-38-consulta-de-notificaciones.md](../specs/RF-38-consulta-de-notificaciones.md) |
| RF-39 | Configuración de preferencias de notificación | HU-64 | Usuario | Configurar preferencias de notificación | [RF-39-configuracion-de-preferencias-de-notificacion.md](../specs/RF-39-configuracion-de-preferencias-de-notificacion.md) |

## 7. Gestionar pagos

Diagrama: [`gestionar_pagos.puml`](../casos%20de%20uso/gestionar_pagos.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-40 | Consulta de historial de pagos | HU-65, HU-67, HU-69 | Cliente, Vendedor, Domiciliario | Consultar historial de pagos | [RF-40-consulta-de-historial-de-pagos.md](../specs/RF-40-consulta-de-historial-de-pagos.md) |
| RF-41 | Consulta de historial general de pagos | HU-70 | Administrador | Consultar historial de pagos | [RF-41-consulta-de-historial-general-de-pagos.md](../specs/RF-41-consulta-de-historial-general-de-pagos.md) |
| RF-42 | Confirmación de pago en efectivo del pedido | HU-66 | Vendedor | Confirmar pago del pedido | [RF-42-confirmacion-de-pago-en-efectivo-del-pedido.md](../specs/RF-42-confirmacion-de-pago-en-efectivo-del-pedido.md) |
| RF-43 | Confirmación de pago en efectivo del domicilio | HU-68 | Domiciliario | Confirmar pago del domicilio | [RF-43-confirmacion-de-pago-en-efectivo-del-domicilio.md](../specs/RF-43-confirmacion-de-pago-en-efectivo-del-domicilio.md) |
| RF-44 | Configuración de tarifa mínima de domicilio | HU-71 | Administrador | Configurar tarifa mínima del domicilio | [RF-44-configuracion-de-tarifa-minima-de-domicilio.md](../specs/RF-44-configuracion-de-tarifa-minima-de-domicilio.md) |

## 8. Gestionar pedidos

Diagrama: [`gestionar_pedidos.puml`](../casos%20de%20uso/gestionar_pedidos.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-45 | Realización de pedido | HU-72, HU-73, HU-74, HU-76 | Cliente | Seleccionar productos; Seleccionar modalidad de entrega; Indicar ubicación de entrega; Realizar pedido | [RF-45-realizacion-de-pedido.md](../specs/RF-45-realizacion-de-pedido.md) |
| RF-46 | Consulta de disponibilidad prevista para reservas | HU-75 | Cliente | Consultar disponibilidad prevista | [RF-46-consulta-de-disponibilidad-prevista-para-reservas.md](../specs/RF-46-consulta-de-disponibilidad-prevista-para-reservas.md) |
| RF-47 | Listado de pedidos del cliente | HU-77 | Cliente | Listar mis pedidos | [RF-47-listado-de-pedidos-del-cliente.md](../specs/RF-47-listado-de-pedidos-del-cliente.md) |
| RF-48 | Consulta del estado del pedido | HU-78 | Cliente | Consultar estado del pedido | [RF-48-consulta-del-estado-del-pedido.md](../specs/RF-48-consulta-del-estado-del-pedido.md) |
| RF-49 | Contacto con vendedor o domiciliario | HU-79 | Cliente | Contactar vendedor o domiciliario | [RF-49-contacto-con-vendedor-o-domiciliario.md](../specs/RF-49-contacto-con-vendedor-o-domiciliario.md) |
| RF-50 | Listado de pedidos del vendedor | HU-80 | Vendedor | Listar pedidos recibidos | [RF-50-listado-de-pedidos-del-vendedor.md](../specs/RF-50-listado-de-pedidos-del-vendedor.md) |
| RF-51 | Consulta de detalle de pedido | HU-81 | Vendedor | Consultar detalle del pedido | [RF-51-consulta-de-detalle-de-pedido.md](../specs/RF-51-consulta-de-detalle-de-pedido.md) |
| RF-52 | Consulta de información del cliente | HU-82 | Vendedor | Ver información del cliente | [RF-52-consulta-de-informacion-del-cliente.md](../specs/RF-52-consulta-de-informacion-del-cliente.md) |
| RF-53 | Respuesta del vendedor a un pedido | HU-83, HU-84 | Vendedor | Aceptar pedido; Rechazar pedido | [RF-53-respuesta-del-vendedor-a-un-pedido.md](../specs/RF-53-respuesta-del-vendedor-a-un-pedido.md) |

## 9. Gestionar productos

Diagrama: [`gestionar_productos.puml`](../casos%20de%20uso/gestionar_productos.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-54 | Registro de producto | HU-85 | Vendedor | Añadir producto | [RF-54-registro-de-producto.md](../specs/RF-54-registro-de-producto.md) |
| RF-55 | Listado de productos del vendedor | HU-86 | Vendedor | Listar productos | [RF-55-listado-de-productos-del-vendedor.md](../specs/RF-55-listado-de-productos-del-vendedor.md) |
| RF-56 | Edición de producto | HU-87 | Vendedor | Editar producto | [RF-56-edicion-de-producto.md](../specs/RF-56-edicion-de-producto.md) |
| RF-57 | Actualización de inventario del producto | HU-88 | Vendedor | Actualizar cantidad producto | [RF-57-actualizacion-de-inventario-del-producto.md](../specs/RF-57-actualizacion-de-inventario-del-producto.md) |
| RF-58 | Eliminación de producto | HU-89 | Vendedor | Eliminar producto | [RF-58-eliminacion-de-producto.md](../specs/RF-58-eliminacion-de-producto.md) |
| RF-59 | Predicción de demanda | HU-90 | Vendedor | Consultar predicción de demanda | [RF-59-prediccion-de-demanda.md](../specs/RF-59-prediccion-de-demanda.md) |

## 10. Gestionar reportes

Diagrama: [`gestionar_reporte.puml`](../casos%20de%20uso/gestionar_reporte.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-60 | Creación de reportes | HU-91, HU-92, HU-93 | Cliente, Vendedor, Domiciliario | Reportar problema | [RF-60-creacion-de-reportes.md](../specs/RF-60-creacion-de-reportes.md) |
| RF-61 | Consulta de reportes por administrador | HU-94 | Administrador | Consultar reportes | [RF-61-consulta-de-reportes-por-administrador.md](../specs/RF-61-consulta-de-reportes-por-administrador.md) |
| RF-62 | Resolución de reportes | HU-95 | Administrador | Resolver reporte | [RF-62-resolucion-de-reportes.md](../specs/RF-62-resolucion-de-reportes.md) |

## 11. Gestionar reservas en punto fijo

Diagrama: [`gestionar_reservas_punto_fijo.puml`](../casos%20de%20uso/gestionar_reservas_punto_fijo.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-63 | Consulta de ubicación del punto de recogida | HU-96 | Cliente | Consultar ubicación del punto de recogida | [RF-63-consulta-de-ubicacion-del-punto-de-recogida.md](../specs/RF-63-consulta-de-ubicacion-del-punto-de-recogida.md) |
| RF-64 | Reserva lista para recoger | HU-97 | Vendedor de punto fijo | Marcar reserva como lista para recoger | [RF-64-reserva-lista-para-recoger.md](../specs/RF-64-reserva-lista-para-recoger.md) |
| RF-65 | Confirmación de retiro de la reserva | HU-98 | Vendedor de punto fijo | Confirmar retiro de la reserva | [RF-65-confirmacion-de-retiro-de-la-reserva.md](../specs/RF-65-confirmacion-de-retiro-de-la-reserva.md) |

## 12. Gestionar usuarios

Diagrama: [`gestionar_usuarios.puml`](../casos%20de%20uso/gestionar_usuarios.puml)

| RF | Requerimiento | Historias de usuario | Actores | Casos de uso | Especificación |
|---|---|---|---|---|---|
| RF-66 | Consulta de usuario por administrador | HU-99 | Administrador | Consultar un usuario | [RF-66-consulta-de-usuario-por-administrador.md](../specs/RF-66-consulta-de-usuario-por-administrador.md) |
| RF-67 | Edición de usuario por administrador | HU-100 | Administrador | Editar un usuario | [RF-67-edicion-de-usuario-por-administrador.md](../specs/RF-67-edicion-de-usuario-por-administrador.md) |
| RF-68 | Eliminación de usuario por administrador | HU-101 | Administrador | Eliminar un usuario | [RF-68-eliminacion-de-usuario-por-administrador.md](../specs/RF-68-eliminacion-de-usuario-por-administrador.md) |
| RF-69 | Listado de usuarios | HU-102 | Administrador | Listar todos los usuarios | [RF-69-listado-de-usuarios.md](../specs/RF-69-listado-de-usuarios.md) |

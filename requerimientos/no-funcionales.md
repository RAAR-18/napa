# Requerimientos No Funcionales

> Aplican a todos los roles: cliente, vendedor (ambulante y de punto fijo), domiciliario y administrador. Los criterios medibles de cada funcionalidad se detallan en los `Success Criteria` de sus especificaciones en `specs/`.

- **RNF01 – Seguridad**: El sistema deberá almacenar las contraseñas de los usuarios utilizando mecanismos seguros de cifrado o hash.
- **RNF02 – Autenticación**: El sistema deberá validar las credenciales de los usuarios y del administrador antes de permitir el acceso a sus cuentas.
- **RNF03 – Autorización**: El sistema deberá restringir el acceso a las funcionalidades según el rol del usuario (cliente, vendedor ambulante, vendedor de punto fijo, domiciliario o administrador).
- **RNF04 – Privacidad**: El sistema deberá proteger la información personal de los usuarios, incluida su ubicación, conforme a la Ley 1581 de 2012 de Protección de Datos Personales, y permitir su acceso únicamente a usuarios autorizados. La ubicación del cliente solo podrá compartirse con el vendedor o el domiciliario de un pedido o domicilio activo.
- **RNF05 – Integridad de datos**: El sistema deberá garantizar la consistencia de la información de usuarios, emprendimientos, productos, pedidos, reservas, domicilios, ofertas, pagos, comentarios y reportes.
- **RNF06 – Disponibilidad**: El sistema deberá estar disponible para los usuarios durante el horario establecido de operación.
- **RNF07 – Rendimiento**: Las operaciones principales del sistema deberán responder en un tiempo máximo de 3 segundos bajo condiciones normales de funcionamiento.
- **RNF08 – Concurrencia**: El sistema deberá controlar operaciones simultáneas sobre productos, reservas, domicilios y ofertas de precio (por ejemplo, dos domiciliarios aceptando el mismo domicilio o dos clientes reservando la última cantidad prevista) para evitar inconsistencias.
- **RNF09 – Usabilidad**: La interfaz deberá ser intuitiva y permitir que los usuarios comprendan fácilmente las funcionalidades disponibles según su rol. Los flujos principales de cada rol (publicar, recibir pedido, confirmar y vender) deberán ser cortos y evitar formularios extensos.
- **RNF10 – Accesibilidad**: El sistema deberá utilizar una interfaz que facilite su uso por personas con diferentes capacidades y dispositivos.
- **RNF11 – Compatibilidad**: El sistema deberá funcionar correctamente en dispositivos móviles, tabletas y computadores.
- **RNF12 – Escalabilidad**: El sistema deberá permitir el crecimiento en número de usuarios, emprendimientos, productos, pedidos, domicilios, pagos y comentarios sin afectar significativamente su funcionamiento.
- **RNF13 – Mantenibilidad**: El sistema deberá estar desarrollado mediante una arquitectura modular que facilite la modificación, actualización y mantenimiento de sus componentes.
- **RNF14 – Extensibilidad**: El sistema deberá permitir incorporar nuevos roles, funcionalidades, modalidades de entrega y tipos de servicios sin requerir modificaciones extensas en los componentes existentes.
- **RNF15 – Fiabilidad**: El sistema deberá manejar errores y excepciones de manera controlada sin provocar pérdida o corrupción de información.
- **RNF16 – Trazabilidad**: El sistema deberá registrar las fechas de creación y última actualización de las entidades que requieran seguimiento, y la cronología de estados de cada domicilio.
- **RNF17 – Actualización de información**: Los cambios realizados sobre productos, pedidos, reservas, domicilios y pagos deberán reflejarse oportunamente para los usuarios involucrados.
- **RNF18 – Notificaciones**: El sistema deberá entregar oportunamente las notificaciones relacionadas con cambios en pedidos, reservas, domicilios (incluidas las ofertas de precio), pagos y reportes.
- **RNF19 – Interoperabilidad**: El sistema deberá permitir la integración con servicios externos necesarios para funcionalidades como mapas y rutas, comunicación o inteligencia artificial.
- **RNF20 – Recuperación**: El sistema deberá contar con mecanismos que permitan recuperar la información ante fallos del sistema o pérdida de datos.
- **RNF21 – Auditoría**: El sistema deberá permitir identificar las operaciones relevantes realizadas sobre pedidos, reservas, domicilios, pagos y reportes.
- **RNF22 – Localización**: El sistema deberá utilizar la información de ubicación de manera consistente para calcular distancias, tarifas y rutas, mostrar emprendimientos cercanos y mostrar la ubicación del cliente y del domiciliario cuando corresponda.
- **RNF23 – Consistencia de estados**: El sistema deberá garantizar que los estados de productos, pedidos, reservas, domicilios, ofertas, pagos y reportes solo puedan cambiar mediante transiciones válidas (por ejemplo, un domicilio avanza en la secuencia disponible → asignado → recogido → en camino → entregado → finalizado, o pasa a cancelado).
- **RNF24 – Protección ante errores**: El sistema deberá mostrar mensajes claros al usuario cuando una operación no pueda completarse.
- **RNF25 – Arquitectura**: El sistema deberá mantener una separación clara de responsabilidades entre sus diferentes componentes y capas para facilitar su evolución.
- **RNF26 – Moderación de contenido**: El sistema deberá proveer mecanismos que permitan controlar y moderar el contenido generado por los usuarios (comentarios y calificaciones) para prevenir contenido ofensivo, fraudulento o abusivo.
- **RNF27 – Integridad de pagos en efectivo**: El sistema deberá registrar cada pago en efectivo una sola vez, evitando confirmaciones duplicadas, incompletas o asociadas a un pedido o domicilio que no corresponde.
- **RNF28 – Tolerancia a conectividad intermitente**: Las funcionalidades críticas para vendedores y domiciliarios (publicar disponibilidad, consultar y responder pedidos, confirmar entregas) deberán tolerar conectividad intermitente y sincronizar la información cuando la señal se recupere.
- **RNF29 – Pago exclusivamente en efectivo**: El sistema no deberá procesar ni almacenar medios de pago electrónicos (tarjetas, cuentas o billeteras): el único medio de pago es el efectivo y el sistema solo registra su confirmación.
- **RNF30 – Seguimiento oportuno de domicilios**: Los cambios de estado de un domicilio y la ubicación del domiciliario deberán ser visibles para el cliente en un máximo de 30 segundos.
- **RNF31 – Eficiencia en dispositivos de gama baja**: El sistema deberá funcionar de forma fluida en dispositivos móviles de gama baja, con almacenamiento y capacidad de procesamiento limitados.

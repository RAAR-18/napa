# PLANTILLA DE PROYECTO: EXPERIENCIA FINAL DE DISEÑO

Ñapa - Plataforma para vendedores informales y pequeños comerciantes.

## INFORMACIÓN GENERAL

- **Programa:** Ingeniería de Sistemas (Universidad del Magdalena)
- **Curso:** Proyecto Culminante de Diseño
- **Código del curso:** 9011655
- **Créditos:** 4
- **Periodo académico:** 2026-2
- **Semestre:** 9

## 1. IDENTIFICACIÓN DEL PROYECTO

- **Título del proyecto:** Ñapa - Plataforma de comercio de cercanía y domicilios para vendedores informales

**Tipo de proyecto:**
- ✅ Productivo - impacta directamente los ingresos diarios de vendedores informales
- ✅ Innovación / transferencia tecnológica - introduce publicación digital, pedidos anticipados y logística de domicilios a un sector que hoy opera de forma verbal/WhatsApp
- ☐ Investigación aplicada
- ☐ Intervención agroambiental

- **Contexto real del problema:** El problema se presenta en el comercio informal de Santa Marta: vendedores ambulantes, de carretilla, de alimentos preparados y pequeños negocios
familiares que operan en calles, barrios, parques y zonas turísticas, sin punto fijo ni canal digital de venta. Su alcance depende exclusivamente del tránsito peatonal, no pueden
anunciar con anticipación qué tienen disponible ni recibir pedidos antes de salir a vender, y una parte de su mercancía especialmente productos perecederos como frutas, pescado
y alimentos preparados se pierde al final del día por falta de un mecanismo para conectar oferta y demanda antes de que el producto se dañe. El detalle completo del problema,
su formulación técnica y la evidencia recogida en campo está documentado en [`docs/investigacion/00-problema.md`](../investigacion/00-problema.md).

- **Duración del proyecto:** 4 meses

**Trabajo:**
- ☐ Individual
- ✅ En equipo
- **Integrantes (5):** Iván Marchena, Eduardo Vergara, Stiven Navarro, Camilo Jiménez, Rafael Acuña - 2022214053. 

## 2. DESCRIPCIÓN DEL PROBLEMA DE INGENIERÍA

**Situación problemática identificada:** En Santa Marta, una parte importante de la actividad comercial la constituyen vendedores informales y pequeños comerciantes (carretilleros, vendedores ambulantes,
de alimentos preparados, negocios familiares) que dependen de la venta diaria de sus productos para sostener a sus familias. Estos vendedores no pueden dar visibilidad a su oferta más allá del punto físico
donde se ubican, no tienen un canal estructurado para recibir pedidos anticipados, y dependen de mecanismos informales (voz a voz, WhatsApp, llamadas, carteles) que no permiten gestionar de forma ordenada la oferta,
los pedidos ni las entregas. Esta situación es crítica para quienes venden productos perecederos (frutas, pescado, alimentos preparados), donde lo no vendido en el día se convierte en pérdida económica directa.

Por su parte, los consumidores cercanos no tienen forma de saber qué vendedores están cerca, qué productos ofrecen, a qué precio y en qué cantidad, lo que limita sus posibilidades de compra y mantiene al vendedor
dependiente exclusivamente del tránsito peatonal.

**Necesidad o demanda del entorno:** Las 5 entrevistas semiestructuradas realizadas ([`docs/investigacion/02-entrevistas.md`](../investigacion/02-entrevistas.md)) confirman de forma consistente: 
(1) incertidumbre de demanda al comprar/preparar mercancía, (2) alto riesgo económico en productos perecederos, (3) existencia real de pedidos anticipados manejados informalmente por WhatsApp/llamadas,
y (4) el domicilio como oportunidad no resuelta — los vendedores no pueden abandonar su punto de venta para entregar personalmente.

**Usuarios o beneficiarios:**
- **Vendedor:** vendedor de carretilla, vendedor ambulante, vendedor de alimentos preparados, pequeño negocio familiar, comerciante con establecimiento.
- **Cliente:** persona cercana que quiere descubrir y comprar productos sin depender de coincidir físicamente con el vendedor.
- **Domiciliario:** persona que genera ingresos adicionales realizando entregas para vendedores que no pueden abandonar su punto de venta.

**Justificación técnica y social del proyecto:** Técnicamente, el problema es resoluble con un modelo de publicación de disponibilidad en tiempo real, gestión de pedidos e inventario, y un mercado de domicilios
bajo demanda — sin requerir que el vendedor adopte herramientas complejas (mapa de empatía, Dolor 5: "Publicar → recibir pedido → confirmar → vender"). Socialmente, la solución impacta directamente los ingresos
de un sector vulnerable de la economía informal santamartense, reduciendo el desperdicio de productos perecederos y ampliando el mercado del vendedor más allá de su ubicación física.

## 3. RESTRICCIONES Y CONDICIONANTES DEL DISEÑO

- ✅ Técnicas
- ✅ Económicas
- ☐ Ambientales
- ✅ Sociales y culturales
- ✅ Normativas y legales
- ☐ Salud y seguridad
- ✅ Éticas

**Descripción detallada de la incorporación de restricciones:**

- **Técnicas:** los vendedores operan en la calle durante toda su jornada, sin garantía de conexión estable a internet ni de datos móviles constantes, y muchos usan equipos de gama baja con almacenamiento
y capacidad de procesamiento limitados. Esto condiciona el diseño para que funcionalidades críticas como publicar disponibilidad o consultar pedidos toleren conectividad intermitente y sincronicen cuando la 
señal se recupere, en vez de requerir una conexión permanente.
- **Económicas:** los vendedores informales no tienen presupuesto para tecnología costosa ni comisiones altas por transacción. Esto llevó a decidir que el sistema no procese pagos mediante una pasarela 
solo registra el método elegido (Efectivo, Nequi, Daviplata) y deja que el dinero se mueva fuera de la aplicación.
- **Sociales y culturales:** las entrevistas mostraron vendedores mayores con temor a herramientas complicadas ("Yo no tengo tiempo para estar aprendiendo cosas difíciles" - Sergio, entrevista #3). 
El diseño de casos de uso privilegia flujos cortos (publicar → recibir pedido → confirmar → vender) y evita catálogos o formularios extensos.
- **Normativas y legales:** el manejo de ubicación (para calcular cercanía) y de datos personales (nombre, teléfono, foto) debe cumplir la Ley 1581 de Protección de Datos Personales. 
Esto se refleja en RNF04 (Privacidad) y en que el sistema restringe el acceso a datos personales únicamente a usuarios autorizados según su rol (RNF03).
- **Éticas:** el sistema de calificación bidireccional (Cliente ↔ Vendedor ↔ Domiciliario) introduce un riesgo real de sesgo  por ejemplo, calificaciones usadas como represalia,
o domiciliarios calificados injustamente por demoras fuera de su control (tráfico, clima). Por eso se incluyeron casos de uso de moderación (Eliminar comentario, Editar comentario) con intervención del Administrador.





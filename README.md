# Ñapa

Plataforma para conectar a vendedores informales y pequeños comerciantes de las grandes ciudades con clientes cercanos,
permitiendo publicar productos, gestionar pedidos anticipados y coordinar domicilios sin abandonar el punto de venta.

## Problema

Vendedores informales (carretillas, puestos ambulantes, pequeños negocios familiares)
dependen del tránsito peatonal para vender y no tienen forma de dar visibilidad a su oferta,
recibir pedidos anticipados ni coordinar entregas sin dejar su punto de venta.
Esto es especialmente crítico para productos perecederos (frutas, pescado, alimentos preparados),
donde lo no vendido en el día se pierde o pierde valor.

## Estructura del repositorio

```
├── capstone/
│   └── informe-capstone.md      # Entregable oficial (plantilla del curso, link al documento word)
├── investigacion/               # Entrevistas, mapa de empatía, hallazgos
│   └── HistoriasDeUsuario/      # Historias de usuario con su actor y caso de uso
├── requerimientos/              # Requerimientos funcionales y no funcionales, matriz de trazabilidad
├── casos de uso/                # Diagramas de casos de uso (PlantUML), uno por módulo
├── specs/                       # Specs y planes (ver specs/README.md)
│   ├── templates/               # spec-template.md y plan-template.md
│   ├── general/                 # Roles, convenciones de API, modelo de datos (ER) y catálogo de eventos
│   ├── foundations/plan.md      # Plan de la infraestructura transversal
│   └── RF-NN-nombre/              # RF-NN-nombre.md (QUÉ) y plan.md (CÓMO) por requerimiento
├── backend/                     # Spring Boot, arquitectura hexagonal por módulo
└── frontend/                    # React Native + Expo
```

## Actores principales

- **Cliente**: descubre emprendimientos cercanos y hace pedidos en tres modalidades: entrega directa, recogida en punto fijo o domicilio. Cualquier pedido puede marcarse como reserva.
- **Vendedor**: administra su emprendimiento y sus productos y atiende pedidos. Tiene dos perfiles con flujos distintos:
  - **Vendedor ambulante**: atiende *entregas directas* (va hasta donde está el cliente), inmediatas o marcadas como reserva.
  - **Vendedor de punto fijo**: atiende *recogidas* en su punto (inmediatas o reservas) y ofrece *domicilios* mediante domiciliarios.
- **Domiciliario**: toma domicilios disponibles (con la ganancia visible), los acepta con la tarifa publicada o los rechaza de forma definitiva; no negocia el precio. Recoge el pedido en el punto fijo y lo lleva al cliente.
- **Administrador**: supervisa usuarios, domicilios, pagos, comentarios y reportes, y define la tarifa mínima de domicilio.

## Modalidades de entrega

| Modalidad | Vendedor ambulante | Vendedor de punto fijo |
|---|:---:|:---:|
| Entrega directa | ✅ | — |
| Recogida en punto fijo | — | ✅ |
| Domicilio (punto A → punto B, no editable) | — | ✅ |

**Reserva** no es una modalidad: es un atributo de cualquier pedido, con fecha y hora acordadas con el vendedor, que este acepta o rechaza con su propio criterio.

## Pagos

La plataforma **no procesa dinero**: solo registra la confirmación de cada cobro. Medios admitidos:

- **Efectivo** o **transferencia a la cuenta bancaria del vendedor** en entrega directa y recogida, según lo que el vendedor acepte.
- **Domicilio: solo transferencia digital.** El cliente paga un total (productos + tarifa) apenas el vendedor acepta el pedido; el vendedor confirma el pago antes de publicar el domicilio. Tras la entrega, el vendedor transfiere la tarifa al domiciliario y ambos lo confirman.

No se guardan datos de tarjetas ni billeteras electrónicas de terceros.

## Stack previsto

Backend: Java 21, Spring Boot 4.1.x, Spring Security, JPA + Hibernate Spatial, PostgreSQL 16 + PostGIS, Redis 7, Firebase Admin (FCM), Twilio Verify (verificación del celular por WhatsApp o SMS). Frontend: React Native + Expo (development build), expo-sqlite. Detalle y decisiones en [`specs/foundations/plan.md`](specs/foundations/plan.md).

## Desarrollo local

Requisitos: JDK 21, Node.js 24 o compatible con Expo SDK 58 y Docker Compose.

```powershell
docker compose up -d
cd backend
.\gradlew.bat test
```

Para iniciar la API localmente con el adaptador OTP de desarrollo, conserva la base de datos de Compose y ejecuta:

```powershell
$env:DATABASE_PASSWORD = "napa_local_dev_only"
$env:NAPA_JWT_SECRET = "replace-with-at-least-32-random-local-bytes"
$env:NAPA_PAYMENT_ACCOUNT_ENCRYPTION_KEY = "base64-encoded-32-random-bytes-from-your-secret-manager"
$env:NAPA_OTP_CONSOLE_CODE = "123456"
.\gradlew.bat bootRun --args="--spring.profiles.active=otp-console"
```

El secreto JWT es obligatorio y debe tener al menos 32 bytes. Para crear el administrador inicial, configura una sola vez `NAPA_ADMIN_BOOTSTRAP_ENABLED=true`, `NAPA_ADMIN_NAME`, `NAPA_ADMIN_PHONE` (formato E.164) y `NAPA_ADMIN_PASSWORD` (8–128 caracteres). El bootstrap se marca como completado en la base de datos; no vuelvas a habilitarlo para reemplazar la cuenta.

La tarifa mínima de domicilio inicia en **3050 COP**. El valor se define con `NAPA_TARIFA_MINIMA_DEFECTO` (por defecto `3050`) y debe ser un entero positivo. Un administrador puede consultarla y actualizarla mediante `GET` y `POST /api/v1/tarifa-minima`; cada cambio efectivo se conserva en el historial y aplica solo a pedidos nuevos. RF-45 usa base `3050 COP`, `500 COP/km`, `200 COP/min` y factor de respaldo por línea recta `1.3`; se pueden ajustar con `NAPA_PEDIDOS_TARIFA_BASE`, `NAPA_PEDIDOS_TARIFA_VALOR_POR_KM`, `NAPA_PEDIDOS_TARIFA_VALOR_POR_MIN` y `NAPA_PEDIDOS_TARIFA_FACTOR_LINEA_RECTA`.

El backend expone `POST /api/v1/pedidos/cotizacion` para clientes. Calcula precios actuales, valida inventario y modalidades, y devuelve el costo del domicilio cuando aplica; es de solo lectura y no requiere `Idempotency-Key`. `POST /api/v1/pedidos` revalida la solicitud, congela precios y ubicación, persiste el pedido como `PENDIENTE` y registra el evento E01 en el outbox dentro de la misma transacción; requiere `Idempotency-Key`. El backend todavía no ofrece pantallas para crear pedidos ni consultas o transiciones de estado.

El backend también requiere `NAPA_PAYMENT_ACCOUNT_ENCRYPTION_KEY`: una clave aleatoria de 32 bytes codificada en Base64 para cifrar cuentas bancarias con AES-GCM. Guárdala en el gestor de secretos del entorno y nunca en Git. La clave cifra los datos en `configuracion_pago`; antes de rotarla habrá que descifrar y volver a cifrar todas las configuraciones y copias vigentes de domicilios con la clave nueva.

El perfil `otp-console` selecciona el único adaptador de consola, `ConsolaVerificacionAdapter`. El código se configura con `NAPA_OTP_CONSOLE_CODE` y debe tener exactamente seis dígitos; no hay un código predeterminado y la API rechaza el arranque de ese perfil si falta o es inválido. El adaptador no envía mensajes ni guarda o imprime el código: mantiene en Redis el estado de verificación durante 10 minutos, con un máximo de 5 intentos y consumo único al acertar. Los límites de envío son 1 por minuto y 5 por hora por celular, y 20 por hora por IP.

Este perfil es exclusivo para desarrollo/pruebas y no puede combinarse con `prod`. Por defecto se selecciona el proveedor `unavailable`: solicitar o confirmar un código responde `503` y no se contacta a Twilio. El registro conserva la cuenta pendiente y devuelve `verificacion.estado = FALLIDO` si el envío no está disponible, para permitir reintentarlo después.

Si el arranque falla con `Migration checksum mismatch`, compara primero `flyway_schema_history` con los archivos de `backend/src/main/resources/db/migration`. Una base creada desde otra rama puede tener migraciones distintas con los mismos números. En el entorno local revisado, la base `napa` contenía V4 `emprendimiento`, V5 `producto` y V6 `imagen_emprendimiento`, mientras que esta versión contiene V4 `notifications`, V5 `mensaje` y V6 `emprendimiento`. Modificar solo el historial con `repair` no adapta esas tablas.

Para probar esta versión conservando la base anterior, crea una base separada una sola vez. Desde `backend/`, con Docker activo:

```powershell
docker compose -f ..\docker-compose.yml exec -T postgres createdb -U napa -O napa napa_dev
```

Antes de ejecutar `bootRun`, configura en esa misma terminal:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/napa_dev"
```

Mantén también las variables de contraseña, secreto JWT y OTP del ejemplo anterior. Flyway creará el esquema de esta versión en `napa_dev`. Las variables de PowerShell se aplican a esa terminal; configura `DATABASE_URL` nuevamente al abrir otra. La base `napa` y sus datos se conservan; las cuentas de esa base no aparecerán en `napa_dev`.

Para habilitar el cálculo de rutas, configura una clave de OpenRouteService con Routes API habilitada:

```powershell
$env:ORS_API_KEY = "tu-clave-local"
```

El envío push con Firebase Cloud Messaging está desactivado por defecto. Para habilitarlo en un entorno local, configura credenciales de Google Application Default Credentials fuera del repositorio y activa el adapter:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\ruta\fuera-del-repo\firebase-service-account.json"
$env:NAPA_NOTIFICATIONS_FCM_ENABLED = "true"
```

En otra terminal, prepara e inicia la app móvil:

```powershell
cd frontend
npm ci
npm run lint
npm run typecheck
npm start
```

Escanea el QR con Expo Go. El frontend utiliza la dirección de red del servidor Expo como dirección del backend y asume que ambos dispositivos están en la misma red Wi-Fi. Si usas un túnel de Expo, un emulador o la detección automática no funciona, configura la IP local del computador antes de arrancar Expo:

```powershell
$env:EXPO_PUBLIC_API_URL = "http://192.168.1.25:8080"
npm start
```

Reemplaza `192.168.1.25` por la IPv4 del computador en tu red local. No uses `localhost` en un teléfono físico: apuntaría al propio teléfono. El backend debe estar ejecutándose en el puerto `8080`.

`EXPO_PUBLIC_API_URL` debe contener solo el origen del backend, por ejemplo `http://192.168.1.25:8080`, sin `/api/v1`: el cliente añade las rutas de la API. Reinicia Expo si cambias esta variable. Para probar el iPhone, abre el proyecto con Expo Go escaneando el QR; la vista del navegador es la versión web. La sesión y el identificador del dispositivo usan SecureStore en iOS/Android y `localStorage` en web. Si el navegador bloquea ese almacenamiento, la app muestra un mensaje para habilitarlo.

`metro.config.js` incluye soporte para los archivos `.wasm` de SQLite y añade `Cross-Origin-Embedder-Policy: credentialless` y `Cross-Origin-Opener-Policy: same-origin` al servidor de desarrollo. Si se despliega la versión web, el hosting debe servir esas cabeceras y un contexto seguro para usar SQLite en navegador. Reinicia Expo cuando cambies su configuración de Metro.

Para acceder a la API desde el navegador, el perfil `otp-console` permite CORS desde `http://localhost:8081` y `http://127.0.0.1:8081`. Para otro origen (otra IP o puerto), configura `NAPA_WEB_ALLOWED_ORIGINS` en la terminal del backend con una lista separada por comas y reinícialo, por ejemplo:

```powershell
$env:NAPA_WEB_ALLOWED_ORIGINS = "http://localhost:8081,http://192.168.1.25:8081"
```

Cada origen incluye esquema, host y puerto, sin ruta ni barra final. Esta variable reemplaza la lista predeterminada. Fuera de `otp-console` no se permiten orígenes web adicionales salvo configuración explícita. Las solicitudes nativas de Expo Go no necesitan CORS; las rutas protegidas siguen exigiendo un token válido.

La app incluye registro (cliente, vendedor ambulante, vendedor de punto fijo o domiciliario), verificación del celular, inicio/cierre de sesión, recuperación y cambio de contraseña. En el perfil `otp-console`, ingresa en la pantalla de verificación el código configurado en `NAPA_OTP_CONSOLE_CODE`. Después de iniciar sesión, el cliente puede explorar emprendimientos y sus productos; el vendedor puede crear su emprendimiento, publicar productos, actualizar sus cantidades y configurar los medios de pago aceptados desde **Configurar métodos de pago**. Allí puede habilitar efectivo y registrar o quitar una cuenta bancaria; una cuenta es necesaria para ofrecer domicilios desde un punto fijo.

### Productos e inventario: gestión en línea

La migración `V7__productos.sql` añade el catálogo y `V8__producto_version.sql` incorpora la versión para evitar sobrescrituras simultáneas, sin cambiar las migraciones anteriores. Usa `napa_dev` si conservas la base local incompatible descrita arriba. Cada producto pertenece al emprendimiento activo del vendedor autenticado; no se acepta elegir otro propietario en el formulario.

| Operación | Endpoint | Perfil |
|---|---|---|
| Publicar producto | `POST /api/v1/productos` | Vendedor |
| Mis productos | `GET /api/v1/productos` | Vendedor |
| Consultar para editar | `GET /api/v1/productos/{id}` | Vendedor propietario |
| Editar nombre, precio o descripción | `PATCH /api/v1/productos/{id}` | Vendedor propietario |
| Retirar del catálogo | `POST /api/v1/productos/{id}/eliminar` | Vendedor propietario |
| Fijar cantidad actual | `POST /api/v1/productos/{id}/inventario` | Vendedor propietario |
| Catálogo del emprendimiento | `GET /api/v1/emprendimientos/{id}/productos` | Cliente |

El precio es un entero positivo en pesos colombianos por unidad de venta. Las unidades son `UNIDAD`, `KG`, `LIBRA`, `LITRO` y `PORCION`. La cantidad admite hasta dos decimales; al crear debe ser mayor que cero. En inventario se permite cero: el producto queda `AGOTADO` y sigue visible con la etiqueta **Solo reserva**. Reponer una cantidad positiva lo devuelve a `DISPONIBLE`. Se rechazan nombres duplicados dentro del mismo emprendimiento, sin distinguir mayúsculas. Las escrituras `POST` requieren `Idempotency-Key`; las actualizaciones manuales bloquean la fila durante la transacción.

La edición acepta uno o varios de los campos `nombre`, `precio` y `descripcion`; `descripcion: null` la borra. Rechaza campos de inventario, unidad y estado. No exige `Idempotency-Key`. La consulta devuelve `version` y `ETag`, y la app envía `If-Match` (por ejemplo, `"0"`) para detectar cambios desde otra pantalla. Un conflicto devuelve `409`, conserva lo escrito en el formulario y ofrece recargar los datos. Inventario y eliminación también incrementan la versión.

Eliminar exige exactamente `{ "confirmar": true }` y responde `204`. Es un borrado lógico: oculta el producto de ambos listados, conserva su registro y libera su nombre. Repetir la misma clave devuelve el mismo `204`; una clave nueva sobre un producto ya eliminado devuelve `409 INVALID_STATE`.

El módulo de pedidos calcula tarifas y cotizaciones, persiste los pedidos creados, sus ítems y el evento de outbox en una sola transacción, y permite al vendedor listar y consultar sus pedidos. `NAPA_PEDIDOS_ENABLED=true` es el valor predeterminado: RF-58 consulta los pedidos en estados abiertos mediante un adaptador JDBC real, y la creación bloquea los productos en orden de ID para coordinarse con su eliminación. Sin pedidos abiertos, la eliminación conserva su comportamiento normal; si hay pedidos pendientes, responde `HAS_PENDING_ORDERS`. Las respuestas de aceptación y rechazo también se procesan transaccionalmente; una aceptación inmediata descuenta inventario y crea el pago pendiente, mientras que una reserva no descuenta stock.

Para probar desde la app:

1. Como vendedor con emprendimiento creado, abre **Mis productos → Agregar producto**. Por ejemplo: Mango, 3500 COP por kilogramo y 12,5 de cantidad.
2. Como cliente, abre **Explorar emprendimientos → Ver productos de…** y comprueba el precio, la unidad y la cantidad.
3. Como vendedor, marca el producto como agotado. Actualiza el catálogo del cliente para ver **Solo reserva**; también se refresca al volver a la pantalla o al primer plano de la app.
4. Repón la cantidad desde **Mis productos** y comprueba que vuelve a estar disponible.
5. Abre **Editar**, cambia el precio o la descripción y guarda. Vuelve a consultar el catálogo como cliente.
6. Usa un producto de prueba para **Eliminar**: primero cancela y comprueba que sigue visible; después confirma y comprueba que desaparece de ambos listados.
7. Como cliente, desde un catálogo agrega uno o más productos al pedido, elige entrega directa, recogida o domicilio según el vendedor, indica la ubicación si aplica, revisa la cotización, elige un medio habilitado y confirma. La pantalla muestra el pedido en estado `PENDIENTE`.
8. Como vendedor del emprendimiento receptor, abre **Pedidos recibidos**, prueba los filtros y la paginación, consulta el detalle y acepta o rechaza un pedido pendiente. Para domicilios, configura antes la cuenta bancaria requerida.

La compra, consulta y respuesta de pedidos requieren conexión. La app ya ofrece listado paginado y filtrable, detalle y acciones de aceptación/rechazo con confirmación y clave de idempotencia; tras responder, vuelve a consultar el estado. No hay todavía caché SQLite ni cola offline para estos recorridos, actualización automática por notificaciones, transiciones posteriores del pedido, confirmación completa del pago ni flujo de entregas. La etiqueta **Solo reserva** permite incluir productos agotados únicamente en una reserva futura.

El entorno de Compose usa credenciales exclusivamente locales. No las reutilices en producción.

## Estado del proyecto

La implementación cubrió las fundaciones F0–F1 y avanza en F2 (identidad). El estado de cada fase, las decisiones aprobadas y los bloqueos de proveedor están en [el plan de fundaciones](specs/foundations/plan.md).

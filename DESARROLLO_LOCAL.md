# Desarrollo y pruebas locales

Este entorno usa la base **napa_preview**. Conserva las bases `napa` y
`napa_dev` y sus cuentas. Los datos de esta demostracion son ficticios.

## Iniciar

Con Docker Desktop abierto, ejecuta desde la raiz del repositorio:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/iniciar-local.ps1
```

El comando inicia PostgreSQL, Redis, la API y Expo en segundo plano. La primera
compilacion puede tardar un minuto. Abre la app en <http://localhost:8081>.
La salud del backend se consulta en <http://localhost:8080/actuator/health>.
Expo mantiene activa la recarga automatica para ver los cambios de interfaz.

Para detener los procesos de API y Expo creados por el script:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/detener-local.ps1
```

El comando comprueba que los PID pertenecen a este entorno antes de detenerlos.
Conserva PostgreSQL, Redis y todos los datos.

Si quieres volver a preparar los datos de ejemplo, con la API ya activa:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/cargar-demo.ps1
```

El cargador conserva su estado en `.local/demo-state.clixml` y no repite las
operaciones ya completadas. No borres ese archivo mientras conserves la base
de demostracion. Si cambias productos o pedidos desde la app, tus cambios
permanecen; el cargador no los restablece.

## Probar desde un iPhone

Para revisar la version web en un iPhone, conecta el telefono al mismo Wi-Fi
del PC. En PowerShell, desde la raiz del repositorio, reinicia los servicios
con la IPv4 del adaptador Wi-Fi que muestra `ipconfig`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/detener-local.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/iniciar-local.ps1 -IpLocal 192.168.1.11
```

Esa era la IP del PC al preparar esta guia; puede cambiar al reconectarte o
cambiar de red. Usa la direccion de Wi-Fi, no la de WSL ni Docker.

Cuando terminen de iniciar, abre **Safari** en el iPhone y entra a
**http://192.168.1.11:8081** (sustituye la IP si cambio). Usa las cuentas de
demostracion de abajo. No uses `localhost` desde el telefono: apuntaria al
propio iPhone.

`-IpLocal` configura la direccion de la API para el telefono y agrega ese
origen concreto a CORS. Conserva los datos y las claves locales. Si cambias
de IP o vuelves al modo habitual, detén e inicia otra vez los servicios.

Si Safari no conecta, prueba **http://192.168.1.11:8080/actuator/health**:
debe mostrar `UP`. Comprueba que ambos equipos esten en la misma red, que
el Wi-Fi no aisle a sus clientes y que el firewall permita los puertos
8080 y 8081 en esa red privada. No es necesario abrir puertos en el router.

Esta prueba comprueba la interfaz y los flujos web en Safari. Los mapas y
las notificaciones nativas requieren probar la aplicacion instalada.
La ubicacion en un navegador puede requerir HTTPS; una pagina HTTP por IP
local no permite validar ese permiso completamente. SQLite web y las
funciones sin conexion que dependen de el tambien requieren un contexto
seguro y las cabeceras de aislamiento. Para comprobar esas funciones en
iPhone, usa un build nativo o un entorno web con HTTPS.

Para instalar la app nativa desde Windows, el proyecto ya incluye el perfil
`development` en `frontend/eas.json`. Necesitas acceso al proyecto Expo de
`raar-18` y al equipo Apple Developer que firmara el build. Este metodo con
EAS para iPhone requiere una suscripcion activa a Apple Developer.

```powershell
cd frontend
npx.cmd eas-cli@latest login
npx.cmd eas-cli@latest device:create
npx.cmd eas-cli@latest build --platform ios --profile development
```

Registra el iPhone desde el enlace que muestra `device:create`, sigue los
pasos de firma de Apple y define el identificador iOS del proyecto cuando
EAS lo solicite (por ejemplo, `co.edu.napa.app`, si pertenece a tu equipo).
Instala el build desde el enlace de Expo. Habilita el modo desarrollador
en el iPhone si iOS lo solicita.

Con el build instalado y el backend activo, detén solo el frontend del
script y, desde `frontend`, inicia Metro para el cliente de desarrollo:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ../scripts/detener-local.ps1 -Servicio Frontend
$env:EXPO_PUBLIC_API_URL = 'http://192.168.1.11:8080'
npx.cmd expo start --dev-client --lan --port 8081
```

Escanea el QR con la camara del iPhone para abrir tu build de Ñapa y permite
el acceso a la red local cuando iOS lo solicite. En esta modalidad la API
tambien debe ser accesible desde el telefono. Las claves y permisos del
push nativo se configuran aparte; compilar e instalar no los habilita por si solo.

Referencias: [builds de desarrollo de Expo](https://docs.expo.dev/develop/development-builds/introduction/)
y [distribucion interna para iOS](https://docs.expo.dev/build/internal-distribution/).

## Cuentas de demostracion

Todas usan la contrasena **NapaDemo2026!**.

| Perfil | Identificador para iniciar sesion |
|---|---|
| Cliente | `3000000001` |
| Vendedor de punto fijo | `3000000002` |
| Vendedor ambulante | `3000000003` |
| Domiciliario | `3000000004` |
| Administrador | `admin@napa.local` |

El codigo de verificacion para registros y recuperacion en este entorno es
**123456**. El adaptador local no envia WhatsApp ni SMS.

## Que recorrer

- **Cliente:** Explorar emprendimientos, catalogo, compra, Mis pedidos,
  detalle, calificaciones, perfil y notificaciones.
- **Punto fijo:** Mis productos, inventario, pedidos pendientes/listos/entregados,
  configuracion de pagos y domicilios.
- **Ambulante:** productos y un pedido en camino para probar la entrega directa.
- **Domiciliario:** un domicilio disponible y otro asignado, ruta y entrega.
- **Administrador:** usuarios, domicilios, pagos, calificaciones y reportes.

Hay un producto agotado para ver la presentacion de "Solo reserva" y un pedido
entregado con pago confirmado para revisar historiales. Los domicilios
disponibles estan sujetos a las reglas normales de expiracion de la app.

## Archivos y logs

- `.env.local` en la raiz guarda los secretos generados para **napa_preview**.
  Se reutilizan al reiniciar. No lo compartas ni lo borres: cambiar la clave
  de cifrado impediria leer las cuentas bancarias ya guardadas en esta base.
- `frontend/.env.local` apunta a `http://localhost:8080`, sin `/api/v1`.
- `.local/backend.log`, `.local/backend.error.log`, `.local/frontend.log` y
  `.local/frontend.error.log` permiten revisar los procesos.
- Los secretos, los logs y el estado de demostracion estan ignorados por Git.

Los scripts usan `ExecutionPolicy Bypass` solo en sus procesos de PowerShell;
no cambian la politica permanente del equipo.

## Interfaz visual

Cada rol entra directamente a su pantalla principal: el cliente a Explorar,
los vendedores a Pedidos recibidos, el domiciliario a Domicilios disponibles
y el administrador a Supervisar domicilios. Mi cuenta sigue disponible en el
menú e incluye los accesos de cada perfil y el cierre de sesión.

En escritorio se muestra un menú lateral; en pantallas pequeñas se usan una
cabecera, un menú desplegable y navegación inferior. Los colores, tipografías
y recursos compartidos están en `frontend/src/components/theme.ts`; la
navegación está en `AppShell.tsx` y los componentes visuales en `NapaUI.tsx`.
En móvil, las pantallas internas y los formularios de acceso incluyen una
flecha **Volver** fija arriba. Regresa por el historial de navegación o, si
se abrió un enlace directo, al inicio del rol (a la bienvenida en las
pantallas públicas). En el inicio de cada rol no se muestra esa flecha.
Las ilustraciones son decorativas; los nombres, estados, valores y productos
siguen llegando del backend.

Las fuentes y sus licencias están en `frontend/assets/fonts`. Las ilustraciones
están en `frontend/assets/illustrations` y no requieren servicios externos.

## Validar cambios

```powershell
cd frontend
npm.cmd run lint -- --no-cache
npm.cmd run typecheck
npm.cmd test
```

La version web permite recorrer la interfaz. Los mapas nativos y el push real
necesitan la configuracion y el development build movil descritos en el README.
La prueba web por si sola no comprueba el comportamiento en Android o iOS.

$ErrorActionPreference = 'Stop'
$raizProyecto = Split-Path -Parent $PSScriptRoot
$archivoEntorno = Join-Path $raizProyecto '.env.local'
$archivoEstado = Join-Path $raizProyecto '.local/demo-state.clixml'
if (-not (Test-Path -LiteralPath $archivoEntorno) -or
    -not ((Get-Content -LiteralPath $archivoEntorno) -contains 'DATABASE_URL=jdbc:postgresql://localhost:5432/napa_preview')) {
    throw 'Inicia primero el entorno napa_preview con iniciar-local.ps1.'
}
$estado = if (Test-Path -LiteralPath $archivoEstado) { Import-Clixml -LiteralPath $archivoEstado } else { @{} }
$api = 'http://localhost:8080/api/v1'
$passwordDemo = 'NapaDemo2026!'

function Clave([string]$operacion) {
    $hash = [Security.Cryptography.SHA256]::Create()
    try { $bytes = $hash.ComputeHash([Text.Encoding]::UTF8.GetBytes('napa-preview:' + $operacion)) }
    finally { $hash.Dispose() }
    return (New-Object Guid (,[byte[]]$bytes[0..15])).ToString()
}

function Peticion([string]$metodo, [string]$ruta, $sesion, $datos = $null, [string]$operacion = '') {
    $cabeceras = @{ 'X-Device-Id' = 'napa-preview-seed' }
    if ($sesion) { $cabeceras.Authorization = 'Bearer ' + $sesion.accessToken }
    if ($metodo -eq 'POST') { $cabeceras['Idempotency-Key'] = Clave ($operacion + ':' + $ruta) }
    $argumentos = @{ Uri = $api + $ruta; Method = $metodo; Headers = $cabeceras; TimeoutSec = 30 }
    if ($null -ne $datos) {
        $argumentos.ContentType = 'application/json; charset=utf-8'
        $argumentos.Body = [Text.Encoding]::UTF8.GetBytes(($datos | ConvertTo-Json -Depth 12 -Compress))
    }
    return Invoke-RestMethod @argumentos
}

function Recordar([string]$nombre, [scriptblock]$crear) {
    if (-not $estado.ContainsKey($nombre)) {
        $estado[$nombre] = & $crear
        $estado | Export-Clixml -LiteralPath $archivoEstado
    }
    return $estado[$nombre]
}

function Cuenta([string]$nombre, [string]$celular, [string]$rol) {
    $claveCuenta = 'cuenta-' + $celular
    if ($estado.ContainsKey($claveCuenta)) {
        return Peticion 'POST' '/auth/login' $null @{ identificador = $celular; password = $passwordDemo }
    }
    # No probar login de cuentas inexistentes: esos fallos se acumulan por IP.
    # Una clave nueva permite consultar la unicidad sin reproducir un registro
    # anterior desde la cache de idempotencia.
    $creada = $true
    try {
        $null = Peticion 'POST' '/auth/register' $null @{
            nombre = $nombre; celular = $celular; correo = $null; rol = $rol
            password = $passwordDemo; confirmPassword = $passwordDemo
        } ($celular + '-' + [guid]::NewGuid().ToString())
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -ne 409) { throw }
        $creada = $false
    }
    if ($creada) {
        $sesion = Peticion 'POST' '/auth/verify-phone/confirm' $null @{ celular = $celular; codigo = '123456' } $celular
    } else {
        try {
            $sesion = Peticion 'POST' '/auth/login' $null @{ identificador = $celular; password = $passwordDemo }
        } catch {
            if ($_.Exception.Response.StatusCode.value__ -ne 403) { throw }
            $null = Peticion 'POST' '/auth/verify-phone/request' $null @{ celular = $celular } $celular
            $sesion = Peticion 'POST' '/auth/verify-phone/confirm' $null @{ celular = $celular; codigo = '123456' } $celular
        }
    }
    $estado[$claveCuenta] = $sesion.usuario.id
    $estado | Export-Clixml -LiteralPath $archivoEstado
    return $sesion
}

$cliente = Cuenta 'Cliente Demo' '+573000000001' 'CLIENTE'
$fijo = Cuenta 'Vendedora Punto Fijo Demo' '+573000000002' 'VENDEDOR_PUNTO_FIJO'
$ambulante = Cuenta 'Vendedor Ambulante Demo' '+573000000003' 'VENDEDOR_AMBULANTE'
$domiciliario = Cuenta 'Domiciliario Demo' '+573000000004' 'DOMICILIARIO'
$admin = Peticion 'POST' '/auth/login' $null @{ identificador = 'admin@napa.local'; password = $passwordDemo }

$negocioFijo = Recordar 'negocio-fijo' {
    Peticion 'POST' '/emprendimientos' $fijo @{
        nombre = 'Frutas de la Plaza - Demo'; categoria = 'FRUTAS_VERDURAS'
        descripcion = 'Negocio ficticio para recorrer el catalogo, pedidos y domicilios.'
        horaApertura = '06:00'; horaCierre = '23:00'
        ubicacion = @{ latitud = 4.6097; longitud = -74.0817; direccion = 'Punto fijo de demostracion, Bogota'; referencia = 'Datos ficticios' }
    } 'negocio-fijo'
}
$negocioAmbulante = Recordar 'negocio-ambulante' {
    Peticion 'POST' '/emprendimientos' $ambulante @{
        nombre = 'Sazon al Paso - Demo'; categoria = 'ALIMENTOS_PREPARADOS'
        descripcion = 'Venta ambulante ficticia con entregas directas y reservas.'
        horaApertura = '06:00'; horaCierre = '23:00'
        ubicacion = @{ latitud = 4.611; longitud = -74.083; direccion = 'Ubicacion de demostracion, Bogota'; referencia = 'Datos ficticios' }
    } 'negocio-ambulante'
}
foreach ($vendedor in @($fijo, $ambulante)) {
    $null = Peticion 'PUT' '/vendedores/me/configuracion-pago' $vendedor @{
        aceptaEfectivo = $true
        cuentaBancaria = @{ banco = 'Banco Demo'; tipoCuenta = 'AHORROS'; numeroCuenta = '00000000001' }
    }
}

$productos = @{}
foreach ($producto in @(
    @{ clave = 'mango'; sesion = $fijo; nombre = 'Mango fresco'; precio = 4500; unidad = 'LIBRA'; cantidad = 30 },
    @{ clave = 'banano'; sesion = $fijo; nombre = 'Banano'; precio = 2500; unidad = 'LIBRA'; cantidad = 25 },
    @{ clave = 'aguacate'; sesion = $fijo; nombre = 'Aguacate'; precio = 3500; unidad = 'UNIDAD'; cantidad = 18 },
    @{ clave = 'fresa'; sesion = $fijo; nombre = 'Fresa - Solo reserva'; precio = 6000; unidad = 'LIBRA'; cantidad = 1 },
    @{ clave = 'arepa'; sesion = $ambulante; nombre = 'Arepa con queso'; precio = 5000; unidad = 'UNIDAD'; cantidad = 40 },
    @{ clave = 'jugo'; sesion = $ambulante; nombre = 'Jugo natural'; precio = 4000; unidad = 'PORCION'; cantidad = 20 }
)) {
    $p = $producto
    $productos[$p.clave] = Recordar ('producto-' + $p.clave) {
        Peticion 'POST' '/productos' $p.sesion @{
            nombre = $p.nombre; precio = $p.precio; unidad = $p.unidad
            descripcion = 'Producto de demostracion'; cantidadDisponible = $p.cantidad
        } $p.clave
    }
}
$null = Recordar 'fresa-agotada' {
    Peticion 'POST' ('/productos/' + $productos.fresa.id + '/inventario') $fijo @{ cantidadDisponible = 0 } 'fresa-agotada'
}

function PedidoDemo([string]$nombre, $negocio, $producto, [string]$modalidad) {
    return Recordar $nombre {
        $datos = @{ emprendimientoId = $negocio.id; modalidad = $modalidad; items = @(@{ productoId = $producto.id; cantidad = 2 }) }
        if ($modalidad -ne 'RECOGIDA') {
            $datos.ubicacionEntrega = @{ latitud = 4.615; longitud = -74.078; direccion = 'Destino ficticio de demostracion, Bogota'; referencia = 'Datos ficticios'; barrio = 'Centro - Demo' }
        }
        $cotizacion = Peticion 'POST' '/pedidos/cotizacion' $cliente $datos $nombre
        $datos.medioPago = if ($modalidad -eq 'DOMICILIO') { 'CUENTA_BANCARIA' } else { 'EFECTIVO' }
        $datos.totalEsperado = $cotizacion.total
        Peticion 'POST' '/pedidos' $cliente $datos $nombre
    }
}
function Accion($pedido, $sesion, [string]$accion, $datos = $null) {
    return Recordar ($pedido.id + '-' + $accion) {
        Peticion 'POST' ('/pedidos/' + $pedido.id + '/' + $accion) $sesion $datos ($pedido.id + '-' + $accion)
    }
}

$pendiente = PedidoDemo 'pedido-pendiente' $negocioFijo $productos.banano 'RECOGIDA'
$listo = PedidoDemo 'pedido-listo' $negocioFijo $productos.mango 'RECOGIDA'
$null = Accion $listo $fijo 'aceptar'
$null = Accion $listo $fijo 'marcar-listo'
$entregado = PedidoDemo 'pedido-entregado' $negocioFijo $productos.aguacate 'RECOGIDA'
$null = Accion $entregado $fijo 'aceptar'
$null = Accion $entregado $fijo 'marcar-listo'
$null = Accion $entregado $fijo 'confirmar-retiro' @{ cobroConfirmado = $true }
$directo = PedidoDemo 'pedido-directo' $negocioAmbulante $productos.arepa 'ENTREGA_DIRECTA'
$null = Accion $directo $ambulante 'aceptar'
$null = Accion $directo $ambulante 'iniciar-entrega'

foreach ($nombre in @('pedido-domicilio-disponible', 'pedido-domicilio-asignado')) {
    $pedido = PedidoDemo $nombre $negocioFijo $productos.mango 'DOMICILIO'
    $null = Accion $pedido $fijo 'aceptar'
    $null = Accion $pedido $fijo 'pago/confirmar'
    $domicilio = Accion $pedido $fijo 'domicilio'
    if ($nombre -eq 'pedido-domicilio-asignado') {
        $null = Recordar 'asignacion-demo' {
            Peticion 'POST' ('/domicilios/' + $domicilio.id + '/aceptar') $domiciliario $null 'asignacion-demo'
        }
    }
}
$null = Recordar 'calificacion-demo' {
    Peticion 'POST' '/calificaciones' $cliente @{
        pedidoId = $entregado.id; tipoEntidad = 'EMPRENDIMIENTO'; entidadId = $negocioFijo.id
        puntaje = 5; comentario = 'Comentario ficticio para revisar las pantallas de calificaciones.'
    } 'calificacion-demo'
}
$null = Recordar 'reporte-demo' {
    Peticion 'POST' '/reportes' $cliente @{ motivo = 'PLATAFORMA'; descripcion = 'Reporte de demostracion para revisar la consulta y resolucion desde administracion.' } 'reporte-demo'
}

Write-Host 'Datos de demostracion preparados: cinco perfiles, dos negocios, seis productos y seis pedidos.'
Write-Host 'Tambien hay pagos, domicilios, calificacion, reporte y notificaciones generados por la API.'
Write-Host 'Todas las cuentas usan NapaDemo2026!; codigo de verificacion local: 123456.'

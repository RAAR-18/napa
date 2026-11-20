param(
    [ValidateSet('Todo', 'Backend', 'Frontend')][string]$Servicio = 'Todo',
    [string]$IpLocal
)

$ErrorActionPreference = 'Stop'
$raizProyecto = Split-Path -Parent $PSScriptRoot
$directorioLocal = Join-Path $raizProyecto '.local'
$archivoEntorno = Join-Path $raizProyecto '.env.local'
Set-Location -LiteralPath $raizProyecto
New-Item -ItemType Directory -Path $directorioLocal -Force | Out-Null

if (-not (Test-Path -LiteralPath $archivoEntorno)) {
    $aleatorio = [Security.Cryptography.RandomNumberGenerator]::Create()
    $claveJwt = New-Object byte[] 48
    $claveCifrado = New-Object byte[] 32
    $aleatorio.GetBytes($claveJwt)
    $aleatorio.GetBytes($claveCifrado)
    $aleatorio.Dispose()
    @(
        '# Exclusivo para la base napa_preview. No compartir ni versionar.'
        'DATABASE_URL=jdbc:postgresql://localhost:5432/napa_preview'
        'DATABASE_PASSWORD=napa_local_dev_only'
        ('NAPA_JWT_SECRET=' + [Convert]::ToBase64String($claveJwt))
        ('NAPA_PAYMENT_ACCOUNT_ENCRYPTION_KEY=' + [Convert]::ToBase64String($claveCifrado))
        'NAPA_OTP_CONSOLE_CODE=123456'
        'NAPA_ADMIN_BOOTSTRAP_ENABLED=true'
        'NAPA_ADMIN_NAME=Administrador Demo'
        'NAPA_ADMIN_PHONE=+573000000005'
        'NAPA_ADMIN_EMAIL=admin@napa.local'
        'NAPA_ADMIN_PASSWORD=NapaDemo2026!'
    ) | Set-Content -LiteralPath $archivoEntorno -Encoding UTF8
}

foreach ($linea in Get-Content -LiteralPath $archivoEntorno) {
    if ($linea -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
if ($env:DATABASE_URL -ne 'jdbc:postgresql://localhost:5432/napa_preview') {
    throw 'Este script solo inicia napa_preview. Revisa DATABASE_URL en .env.local.'
}

# Solo se habilita el origen indicado; la autenticacion y los demas origenes se conservan.
if ($IpLocal) {
    $direccionTelefono = $null
    if (-not [Net.IPAddress]::TryParse($IpLocal, [ref]$direccionTelefono) -or
        $direccionTelefono.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
        throw 'IpLocal debe ser la direccion IPv4 del PC en tu Wi-Fi, por ejemplo 192.168.1.11.'
    }
    $IpLocal = $direccionTelefono.ToString()
    $env:EXPO_PUBLIC_API_URL = "http://${IpLocal}:8080"
    $origenesWeb = if ($env:NAPA_WEB_ALLOWED_ORIGINS) {
        @($env:NAPA_WEB_ALLOWED_ORIGINS -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    } else { @('http://localhost:8081', 'http://127.0.0.1:8081') }
    $env:NAPA_WEB_ALLOWED_ORIGINS = ((@($origenesWeb) + "http://${IpLocal}:8081") | Select-Object -Unique) -join ','
}

if ($Servicio -eq 'Backend') {
    $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
    Set-Location -LiteralPath (Join-Path $raizProyecto 'backend')
    & .\gradlew.bat --offline --no-daemon bootRun '--args=--spring.profiles.active=otp-console'
    exit $LASTEXITCODE
}

if ($Servicio -eq 'Frontend') {
    if (-not $env:EXPO_PUBLIC_API_URL) { $env:EXPO_PUBLIC_API_URL = 'http://localhost:8080' }
    $env:CI = $null
    Set-Location -LiteralPath (Join-Path $raizProyecto 'frontend')
    & node.exe node_modules/expo/bin/cli start --web --lan --port 8081
    exit $LASTEXITCODE
}

$docker = Get-Command docker.exe -ErrorAction SilentlyContinue
$rutaDocker = if ($docker) { $docker.Source } else {
    Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'
}
if (-not (Test-Path -LiteralPath $rutaDocker)) {
    throw 'No se encontro Docker. Instala o inicia Docker Desktop y vuelve a ejecutar.'
}
& $rutaDocker compose up -d --wait
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron iniciar PostgreSQL y Redis.' }
$baseExiste = & $rutaDocker compose exec -T postgres psql -U napa -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='napa_preview'"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar PostgreSQL.' }
if ($baseExiste -notcontains '1') {
    & $rutaDocker compose exec -T postgres createdb -U napa -O napa napa_preview
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear napa_preview.' }
}

if (-not (Test-Path -LiteralPath 'frontend/node_modules/expo-notifications/package.json')) {
    Push-Location -LiteralPath 'frontend'
    try {
        & npm.cmd ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw 'Fallo npm ci.' }
    } finally { Pop-Location }
}

function Test-Servicio([string]$url) {
    try { return (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 }
    catch { return $false }
}

foreach ($componente in @(
    @{ Nombre = 'Backend'; Url = 'http://localhost:8080/actuator/health' },
    @{ Nombre = 'Frontend'; Url = 'http://localhost:8081' }
)) {
    if (Test-Servicio $componente.Url) {
        Write-Host ($componente.Nombre + ' ya esta activo.')
        continue
    }
    $nombreLog = $componente.Nombre.ToLowerInvariant()
    $argumentos = '-NoProfile -ExecutionPolicy Bypass -File "' + $PSCommandPath + '" -Servicio ' + $componente.Nombre
    if ($IpLocal) { $argumentos += ' -IpLocal ' + $IpLocal }
    $proceso = Start-Process powershell.exe -ArgumentList $argumentos -WorkingDirectory $raizProyecto -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $directorioLocal "$nombreLog.log") `
        -RedirectStandardError (Join-Path $directorioLocal "$nombreLog.error.log")
    $proceso.Id | Set-Content -LiteralPath (Join-Path $directorioLocal "$nombreLog.pid")
    Write-Host ($componente.Nombre + ' iniciando. Log: .local/' + $nombreLog + '.log')
}
Write-Host 'App: http://localhost:8081'
if ($IpLocal) {
    Write-Host "iPhone (misma red Wi-Fi): http://${IpLocal}:8081"
    Write-Host 'Si ya estaban activos los servicios, detenlos y reinicia con -IpLocal para aplicar el cambio.'
}
Write-Host 'API: http://localhost:8080/actuator/health'
Write-Host 'Espera a que terminen de iniciar antes de abrir la app.'

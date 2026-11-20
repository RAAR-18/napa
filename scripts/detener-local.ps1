param([ValidateSet('Todo', 'Backend', 'Frontend')][string]$Servicio = 'Todo')

$ErrorActionPreference = 'Stop'
$raizProyecto = Split-Path -Parent $PSScriptRoot
$scriptInicio = Join-Path $PSScriptRoot 'iniciar-local.ps1'
$componentes = if ($Servicio -eq 'Todo') { @('Backend', 'Frontend') } else { @($Servicio) }
foreach ($componente in $componentes) {
    $archivoPid = Join-Path $raizProyecto ('.local/' + $componente.ToLowerInvariant() + '.pid')
    if (-not (Test-Path -LiteralPath $archivoPid)) { continue }
    $idProcesoLocal = [int](Get-Content -LiteralPath $archivoPid -Raw)
    $procesoLocal = Get-CimInstance Win32_Process -Filter "ProcessId=$idProcesoLocal"
    if ($procesoLocal) {
        if ($procesoLocal.Name -ne 'powershell.exe' -or
            -not $procesoLocal.CommandLine.Contains($scriptInicio) -or
            -not $procesoLocal.CommandLine.Contains('-Servicio ' + $componente)) {
            throw "El PID de $componente no corresponde al proceso de este entorno. No se detuvo."
        }
        & taskkill.exe /PID $idProcesoLocal /T /F | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "No se pudo detener $componente." }
        Write-Host "$componente detenido."
    }
    Remove-Item -LiteralPath $archivoPid
}
Write-Host 'PostgreSQL, Redis y los datos se conservan.'

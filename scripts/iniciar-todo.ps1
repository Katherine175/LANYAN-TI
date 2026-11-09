# Enciende el backend y la pantalla web, cada uno en su ventana, y abre el navegador.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\iniciar-todo.ps1
# Para apagar: cierra las dos ventanas que se abren.

function Test-Puerto($puerto) {
    return [bool](Get-NetTCPConnection -State Listen -LocalPort $puerto -ErrorAction SilentlyContinue)
}

function Start-Ventana($script) {
    Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot $script)
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Output 'Docker Desktop no esta listo. Abrelo, espera a que diga Engine running y vuelve a ejecutar este comando.'
    exit 1
}

if (-not (Test-Puerto 8080)) { Start-Ventana 'iniciar-backend.ps1' }
if (-not (Test-Puerto 8443)) { Start-Ventana 'iniciar-frontend.ps1' }

Write-Output 'Encendiendo LANYAN TI, espera un momento...'
$listo = $false
foreach ($intento in 1..90) {
    try {
        $respuesta = Invoke-WebRequest 'http://127.0.0.1:8080/actuator/health' -UseBasicParsing -TimeoutSec 2
        if ($respuesta.StatusCode -eq 200 -and (Test-Puerto 8443)) { $listo = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
}

if (-not $listo) {
    Write-Output 'El sistema no termino de encender. Revisa los mensajes de las ventanas que se abrieron.'
    exit 1
}

Start-Process 'http://127.0.0.1:8443'
Write-Output 'Listo: http://127.0.0.1:8443'

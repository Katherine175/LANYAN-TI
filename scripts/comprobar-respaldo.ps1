# Restaura un respaldo en un contenedor desechable para confirmar que sirve.
# No toca la base de desarrollo ni publica puertos.
# Uso: powershell -File scripts\comprobar-respaldo.ps1 respaldos\archivo.dump

param([Parameter(Mandatory = $true)][string]$Respaldo)

if (-not (Test-Path $Respaldo)) { Write-Error "No existe el archivo $Respaldo"; exit 1 }

$contenedor = 'lanyan-comprobar-respaldo'
docker run -d --rm --name $contenedor -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_USER=lanyan_app -e POSTGRES_DB=lanyan_ti postgres:17-alpine | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Error 'No se pudo crear el contenedor temporal.'; exit 1 }

try {
    $listo = $false
    foreach ($intento in 1..30) {
        docker exec $contenedor pg_isready -q -h 127.0.0.1 -U lanyan_app -d lanyan_ti
        if ($LASTEXITCODE -eq 0) { $listo = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $listo) { Write-Error 'PostgreSQL temporal no respondio.'; exit 1 }

    docker cp $Respaldo "${contenedor}:/tmp/respaldo.dump" | Out-Null
    docker exec $contenedor pg_restore -h 127.0.0.1 -U lanyan_app -d lanyan_ti --no-owner --exit-on-error /tmp/respaldo.dump
    if ($LASTEXITCODE -ne 0) { Write-Error 'El respaldo no se pudo restaurar.'; exit 1 }

    docker exec $contenedor psql -h 127.0.0.1 -U lanyan_app -d lanyan_ti -At -c "select 'tablas: ' || count(*) from pg_tables where schemaname='public'" -c "select 'migraciones: ' || count(*) from flyway_schema_history where success" -c "select 'usuarios: ' || count(*) from identity_users"
    Write-Output 'Respaldo valido.'
} finally {
    docker stop $contenedor | Out-Null
}

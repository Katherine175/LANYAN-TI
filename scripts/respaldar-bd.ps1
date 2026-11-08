# Guarda una copia de la base local en respaldos\. Solo lee la base; no la modifica.
# Uso, desde cualquier carpeta: powershell -File scripts\respaldar-bd.ps1

$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

$carpeta = Join-Path $raiz 'respaldos'
New-Item -ItemType Directory -Force $carpeta | Out-Null
$nombre = 'lanyan_ti_{0}.dump' -f (Get-Date -Format 'yyyyMMdd_HHmmss')

docker compose exec -T postgres sh -c 'pg_dump -U $POSTGRES_USER -d $POSTGRES_DB -Fc -f /tmp/respaldo.dump'
if ($LASTEXITCODE -ne 0) { Write-Error 'No se pudo generar el respaldo. Comprueba que Docker y PostgreSQL esten activos.'; exit 1 }

docker compose cp postgres:/tmp/respaldo.dump (Join-Path $carpeta $nombre)
$copiado = $LASTEXITCODE
docker compose exec -T postgres rm -f /tmp/respaldo.dump
if ($copiado -ne 0) { Write-Error 'No se pudo copiar el respaldo a respaldos\.'; exit 1 }

Write-Output "Respaldo creado: respaldos\$nombre"

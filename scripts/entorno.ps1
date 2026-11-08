# Prepara el entorno comun: PostgreSQL activo y variables DB_* tomadas de .env.
# Lo usan los demas scripts; no hace falta ejecutarlo directamente.

$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

if (-not (Test-Path .env)) { Write-Error 'Falta el archivo .env en la raiz del proyecto.'; exit 1 }

docker compose up -d --wait --wait-timeout 90
if ($LASTEXITCODE -ne 0) { Write-Error 'PostgreSQL no inicio. Comprueba que Docker Desktop este abierto.'; exit 1 }

$valores = @{}
foreach ($linea in Get-Content .env) {
    if ($linea -match '^\s*([A-Z_]+)=(.*)$') { $valores[$Matches[1]] = $Matches[2].Trim() }
}
$env:DB_URL = 'jdbc:postgresql://localhost:{0}/{1}' -f $valores['POSTGRES_PORT'], $valores['POSTGRES_DB']
$env:DB_USERNAME = $valores['DB_USERNAME']
$env:DB_PASSWORD = $valores['DB_PASSWORD']

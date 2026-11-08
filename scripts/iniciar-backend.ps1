# Inicia PostgreSQL y el backend en http://127.0.0.1:8080. Ctrl+C lo detiene.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\iniciar-backend.ps1

. (Join-Path $PSScriptRoot 'entorno.ps1')
Set-Location (Join-Path $raiz 'backend')
.\mvnw.cmd --offline spring-boot:run

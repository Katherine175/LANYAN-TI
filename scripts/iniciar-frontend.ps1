# Inicia la pantalla web en http://127.0.0.1:8443. Ctrl+C la detiene.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\iniciar-frontend.ps1

Set-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend')
npx vite

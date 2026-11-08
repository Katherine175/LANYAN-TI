# Crea la primera cuenta de administrador. Solo funciona si la base no tiene usuarios.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\crear-admin.ps1

. (Join-Path $PSScriptRoot 'entorno.ps1')

$env:LANYAN_ADMIN_NAME = Read-Host 'Nombre completo'
$env:LANYAN_ADMIN_EMAIL = Read-Host 'Correo'
$secreto = Read-Host 'Contrasena nueva (minimo 12 caracteres)' -AsSecureString
$env:LANYAN_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $secreto).Password

try {
    Set-Location (Join-Path $raiz 'backend')
    .\mvnw.cmd --offline spring-boot:run '-Dspring-boot.run.profiles=bootstrap-admin'
    if ($LASTEXITCODE -eq 0) { Write-Output 'Administrador creado. Ya puedes iniciar el backend.' }
    else { Write-Error 'No se creo la cuenta. Revisa el mensaje anterior.' }
} finally {
    Remove-Item Env:LANYAN_ADMIN_PASSWORD, Env:LANYAN_ADMIN_NAME, Env:LANYAN_ADMIN_EMAIL -ErrorAction SilentlyContinue
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}

# =====================================================================
#  VideoEcraft — Generador de keystore y valores para GitHub Secrets
# =====================================================================
#
#   Uso:
#     .\scripts\setup-keystore.ps1
#
#   Crea un keystore de release y muestra los 4 valores que debes pegar
#   en GitHub → Settings → Secrets and variables → Actions.
#
#   El keystore es el "DNI" de tu app: sirve para firmar las actualizaciones
#   de forma que Android las acepte como la misma aplicación. Si lo pierdes,
#   NO podrás publicar actualizaciones sobre las instalaciones existentes.
#   Guarda una copia en un lugar seguro (gestor de contraseñas, backup
#   cifrado). NUNCA lo subas al repositorio.
# =====================================================================

param(
    [string]$Alias = "videoecraft",
    [string]$OutputDir = "$PSScriptRoot\..\keys",
    [string]$DistinguishedName = "CN=Otaplus, OU=VideoEcraft, O=Otaplus, L=Caracas, C=VE"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  VideoEcraft — Configuración de firma"       -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# --- Localizar keytool: viene con el JDK, no con el JRE ---
$keytool = $null

# 1) JAVA_HOME
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (Test-Path $candidate) { $keytool = $candidate }
}

# 2) Android Studio trae su propio JBR (que sí incluye keytool)
if (-not $keytool) {
    $jbr = Get-ChildItem "C:\Program Files\Android\Android Studio*\jbr\bin\keytool.exe" `
        -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jbr) { $keytool = $jbr.FullName }
}

# 3) PATH
if (-not $keytool) {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { $keytool = $cmd.Source }
}

if (-not $keytool) {
    Write-Host "ERROR: no se encontró 'keytool'." -ForegroundColor Red
    Write-Host "Instala un JDK 17 o ejecuta desde la terminal de Android Studio." -ForegroundColor Yellow
    exit 1
}

Write-Host "keytool: $keytool" -ForegroundColor DarkGray
Write-Host ""

# --- Crear carpeta de salida ---
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$keystorePath = Join-Path $OutputDir "videoecraft-release.jks"

if (Test-Path $keystorePath) {
    Write-Host "AVISO: ya existe $keystorePath" -ForegroundColor Yellow
    $overwrite = Read-Host "¿Sobrescribirlo? (s/N)"
    if ($overwrite -notmatch '^[sSyY]$') {
        Write-Host "Cancelado. Se usa el keystore existente." -ForegroundColor Yellow
    }
    else {
        Remove-Item $keystorePath -Force
    }
}

if (-not (Test-Path $keystorePath)) {
    Write-Host "Vas a crear el keystore de release." -ForegroundColor Green
    Write-Host "Se te pedirá una contraseña (mínimo 6 caracteres)." -ForegroundColor Green
    Write-Host "Apúntala: la necesitarás para los secrets." -ForegroundColor Green
    Write-Host ""

    & $keytool -genkeypair `
        -v `
        -keystore $keystorePath `
        -alias $Alias `
        -keyalg RSA `
        -keysize 4096 `
        -validity 10000 `
        -dname $DistinguishedName

    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: keytool falló." -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  Valores para GitHub Secrets"                -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# --- Base64 del keystore ---
$bytes = [System.IO.File]::ReadAllBytes($keystorePath)
$base64 = [System.Convert]::ToBase64String($bytes)

$base64File = Join-Path $OutputDir "keystore-base64.txt"
$base64 | Set-Content -Path $base64File -NoNewline -Encoding ascii

Write-Host "Secret 1 — VIDEOECRAFT_KEYSTORE_BASE64" -ForegroundColor Yellow
Write-Host "  (contenido guardado en: $base64File)" -ForegroundColor DarkGray
Write-Host "  Cópialo completo al secret. Empieza así:" -ForegroundColor DarkGray
Write-Host "  $($base64.Substring(0, [Math]::Min(60, $base64.Length)))..." -ForegroundColor DarkGray
Write-Host ""

Write-Host "Secret 2 — VIDEOECRAFT_KEYSTORE_PASSWORD" -ForegroundColor Yellow
Write-Host "  La contraseña que acabas de escribir." -ForegroundColor DarkGray
Write-Host ""

Write-Host "Secret 3 — VIDEOECRAFT_KEY_ALIAS" -ForegroundColor Yellow
Write-Host "  $Alias" -ForegroundColor Green
Write-Host ""

Write-Host "Secret 4 — VIDEOECRAFT_KEY_PASSWORD" -ForegroundColor Yellow
Write-Host "  Normalmente la misma que el Secret 2." -ForegroundColor DarkGray
Write-Host ""

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  Dónde pegarlos"                              -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  1. Abre tu repo en GitHub" -ForegroundColor White
Write-Host "  2. Settings → Secrets and variables → Actions" -ForegroundColor White
Write-Host "  3. New repository secret (4 veces, con los nombres de arriba)" -ForegroundColor White
Write-Host ""

Write-Host "AVISO IMPORTANTE" -ForegroundColor Red
Write-Host "  Guarda $keystorePath en un lugar SEGURO y fuera del repo." -ForegroundColor Red
Write-Host "  Si lo pierdes no podrás publicar actualizaciones." -ForegroundColor Red
Write-Host "  La carpeta keys/ ya está en .gitignore." -ForegroundColor Red
Write-Host ""

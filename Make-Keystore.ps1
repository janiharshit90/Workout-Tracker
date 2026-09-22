# Make-Keystore.ps1 - run ONCE, from your project root, on Windows.
# Creates the permanent signing key for your releases and prints the values
# to paste into GitHub -> Settings -> Secrets and variables -> Actions.
#
# KEEP release.keystore AND THE PASSWORD SAFE (e.g. password manager + cloud backup).
# If you lose it, you can never ship an update over the installed app again.

$ErrorActionPreference = "Stop"
$alias = "mentzer"
$file  = "release.keystore"

if (Test-Path $file) { Write-Host "$file already exists - not overwriting." -ForegroundColor Yellow; exit 1 }
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    Write-Host "keytool not found. It ships with the JDK (you installed Temurin 17). Make sure its bin folder is on PATH." -ForegroundColor Red
    exit 1
}

$pw = Read-Host "Choose a keystore password (min 6 chars)"
keytool -genkeypair -v -keystore $file -alias $alias -keyalg RSA -keysize 2048 -validity 10000 `
    -storepass $pw -keypass $pw -dname "CN=Mentzer Tracker"

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $file)))
Set-Content -Path "keystore.properties" -Value @"
storeFile=release.keystore
storePassword=$pw
keyAlias=$alias
keyPassword=$pw
"@

Write-Host ""
Write-Host "Add these 4 repository secrets on GitHub:" -ForegroundColor Green
Write-Host "  KEYSTORE_BASE64 = (copied to your clipboard now)"
Write-Host "  STORE_PASSWORD  = $pw"
Write-Host "  KEY_ALIAS       = $alias"
Write-Host "  KEY_PASSWORD    = $pw"
Set-Clipboard -Value $b64
Write-Host ""
Write-Host "keystore.properties was also written so LOCAL release builds use the same key." -ForegroundColor Green
Write-Host "Both files are git-ignored. Back them up somewhere safe." -ForegroundColor Yellow

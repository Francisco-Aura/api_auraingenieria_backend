<#
.SYNOPSIS
  Compila backend y frontend EN ESTE PC y deja un paquete listo para llevar al servidor
  (no hace falta instalar nada en el servidor ni tener red hacia el).

  Genera, fuera de los repositorios:  C:\Programas\paquete_despliegue\
     aura_DES.jar  aura_PRE.jar   (cada uno con la cookie de sesion de su entorno)
     frontend\                     (dist; vale para todos los entornos)
     sql\                          (scripts SQL manuales pendientes)
     instalar.sh                   (se ejecuta EN EL SERVIDOR: bash instalar.sh DES)
  y los comprime en  C:\Programas\paquete_despliegue.tar.gz

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\empaquetar.ps1
#>
[CmdletBinding()]
param(
    [ValidateSet('DES', 'PRE', 'PRO')]
    [string[]]$Entorno = @('DES', 'PRE'),
    [string]$RutaFront,
    [string]$Salida = 'C:\Programas\paquete_despliegue',
    # SQL manuales que viajan en el paquete (los *_Planificador_* se aplican solos al arrancar).
    [string[]]$Sql = @('V20261120_1_Create_Tareas_tramos_tiempo.sql')
)

$ErrorActionPreference = 'Stop'
$cookies = @{ DES = 'AURA_SESSION_DES'; PRE = 'AURA_SESSION_PRE'; PRO = '' }

$raiz = Split-Path -Parent $PSScriptRoot
if (-not $RutaFront) { $RutaFront = Join-Path (Split-Path -Parent $raiz) 'api_auraingenieria_front_vue' }
if (-not (Test-Path (Join-Path $RutaFront 'package.json'))) { throw "No encuentro el frontend en $RutaFront (usa -RutaFront)." }

function Escribir-Paso([string]$t) { Write-Host ''; Write-Host "=== $t ===" -ForegroundColor Cyan }

# Carpeta de salida limpia
if (Test-Path $Salida) { Remove-Item $Salida -Recurse -Force }
New-Item -ItemType Directory -Path $Salida, (Join-Path $Salida 'sql') | Out-Null

# ---------------- Frontend ----------------
Escribir-Paso 'Compilando frontend'
Push-Location $RutaFront
try {
    Remove-Item dist -Recurse -Force -ErrorAction SilentlyContinue
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'npm run build ha fallado.' }
    if (-not (Test-Path 'dist\index.html')) { throw 'No existe dist\index.html' }
} finally { Pop-Location }
Copy-Item (Join-Path $RutaFront 'dist') (Join-Path $Salida 'frontend') -Recurse

# ---------------- Backend (un JAR por entorno) ----------------
$local = Join-Path $raiz 'src\main\resources\application-local.properties'
$copia = "$local.deploy-bak"
if ((Test-Path $copia) -and -not (Test-Path $local)) { Move-Item $copia $local -Force }
$mvn = if (Test-Path (Join-Path $raiz '.mvn\wrapper\maven-wrapper.properties')) { Join-Path $raiz 'mvnw.cmd' } else { 'mvn' }

foreach ($n in $Entorno) {
    Escribir-Paso "Compilando backend para $n"
    $habiaLocal = Test-Path $local
    if ($habiaLocal) { Move-Item $local $copia -Force }
    Push-Location $raiz
    try {
        if ($cookies[$n]) {
            Set-Content -Path $local -Value "server.servlet.session.cookie.name=$($cookies[$n])" -Encoding ASCII
        }
        & $mvn -B clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "La compilacion del backend ha fallado (codigo $LASTEXITCODE)." }
    } finally {
        Pop-Location
        Remove-Item $local -Force -ErrorAction SilentlyContinue
        if ($habiaLocal) { Move-Item $copia $local -Force }
    }
    $jar = Get-ChildItem (Join-Path $raiz 'target') -Filter '*.jar' -File |
        Where-Object { $_.Name -notmatch '\.original$' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar -or $jar.Length -eq 0) { throw 'No hay JAR valido en target\.' }
    Copy-Item $jar.FullName (Join-Path $Salida "aura_$n.jar")
    Write-Host ("aura_{0}.jar  {1:N1} MB" -f $n, ($jar.Length / 1MB))
}

# ---------------- SQL manual ----------------
foreach ($f in $Sql) {
    $ruta = Join-Path $raiz "src\main\resources\BBDD\$f"
    if (-not (Test-Path $ruta)) { throw "No existe el SQL $f" }
    Copy-Item $ruta (Join-Path $Salida 'sql')
}

# ---------------- instalar.sh (se ejecuta en el servidor) ----------------
$sh = @'
#!/bin/bash
# Uso (EN EL SERVIDOR):  bash instalar.sh DES      o      bash instalar.sh PRE
set -Eeuo pipefail

ENT="${1:-}"
case "$ENT" in DES|PRE|PRO) ;; *) echo "Uso: bash instalar.sh DES|PRE|PRO"; exit 1;; esac

AQUI="$(cd "$(dirname "$0")" && pwd)"
TS=$(date +%Y%m%d_%H%M%S)
B="$HOME/backups"; D="$HOME/deploy"
mkdir -p "$B"

if [ "$ENT" = "PRO" ]; then
  read -r -p "Vas a desplegar en PRODUCCION. Escribe PRO para continuar: " r
  [ "$r" = "PRO" ] || { echo Cancelado; exit 1; }
fi

echo; echo "=== 1/5 Copia de seguridad en $B ==="
if [ -f "$D/backend/aura.jar" ]; then cp "$D/backend/aura.jar" "$B/aura_$TS.jar"; echo "JAR guardado"; fi
if [ -d "$D/frontend" ]; then tar -czf "$B/frontend_$TS.tar.gz" -C "$D" frontend; echo "Frontend guardado"; fi
if sudo -n mariadb-dump --single-transaction --routines --triggers aura 2>/dev/null | gzip > "$B/aura_$TS.sql.gz"; then
  echo "BBDD guardada: aura_$TS.sql.gz"
else
  rm -f "$B/aura_$TS.sql.gz"
  read -r -p "AVISO: no se ha podido volcar la BBDD. Continuar sin copia de la BBDD? (s/n): " r
  [ "$r" = "s" ] || exit 1
fi

echo; echo "=== 2/5 Backend ==="
cp "$AQUI/aura_$ENT.jar" /tmp/aura.jar
bash "$HOME/deploy_backend.sh"

echo; echo "=== 3/5 Esperando backend ==="
OK=0
for i in $(seq 1 40); do
  if curl -fsS http://127.0.0.1:8986/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then OK=1; echo "Backend disponible"; break; fi
  echo "Esperando backend... $i/40"; sleep 5
done
if [ "$OK" -ne 1 ]; then echo "ERROR: el backend no responde"; systemctl status backend.service --no-pager -l || true; exit 1; fi

echo; echo "=== 4/5 SQL manual ==="
for f in "$AQUI"/sql/*.sql; do
  [ -e "$f" ] || continue
  read -r -p "Aplicar $(basename "$f") a la BBDD aura? (s/n): " r
  if [ "$r" = "s" ]; then sudo mariadb aura < "$f" && echo "Aplicado"; fi
done

echo; echo "=== 5/5 Frontend ==="
rm -rf /tmp/aura-frontend
cp -r "$AQUI/frontend" /tmp/aura-frontend
bash "$HOME/deploy_frontend.sh"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/)
echo "HTTP frontend -> $HTTP"
[ "$HTTP" = "200" ] || { echo "ERROR: el frontend devuelve $HTTP"; exit 1; }

echo; echo "==== DESPLIEGUE $ENT COMPLETADO ===="
'@
[System.IO.File]::WriteAllText((Join-Path $Salida 'instalar.sh'), ($sh -replace "`r", ''), (New-Object System.Text.UTF8Encoding($false)))

# ---------------- Comprimir ----------------
Escribir-Paso 'Comprimiendo'
$tgz = "$Salida.tar.gz"
Remove-Item $tgz -Force -ErrorAction SilentlyContinue
tar -czf $tgz -C $Salida .
if ($LASTEXITCODE -ne 0) { throw 'tar ha fallado.' }
Write-Host ("Paquete listo: {0}  ({1:N1} MB)" -f $tgz, ((Get-Item $tgz).Length / 1MB)) -ForegroundColor Green

<#
.SYNOPSIS
  Despliega AURA (backend y/o frontend) a mano en uno o varios entornos.
  Hace lo mismo que los .gitlab-ci.yml de los dos repositorios.

  Backend : compila, copia el JAR a /tmp/aura.jar y ejecuta /home/babenza/deploy_backend.sh
  Frontend: npm run build, copia dist\ a /tmp/aura-frontend y ejecuta /home/babenza/deploy_frontend.sh

.EXAMPLE
  # Todo (backend + frontend) en DES y PRE
  powershell -ExecutionPolicy Bypass -File scripts\desplegar.ps1 DES,PRE

  # Solo el backend en DES
  powershell -ExecutionPolicy Bypass -File scripts\desplegar.ps1 DES -Parte Backend

  # Solo el frontend en PRE
  powershell -ExecutionPolicy Bypass -File scripts\desplegar.ps1 PRE -Parte Frontend
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('DES', 'PRE', 'PRO')]
    [string[]]$Entorno,

    [ValidateSet('Todo', 'Backend', 'Frontend')]
    [string]$Parte = 'Todo',

    # Por defecto el backend compila con -DskipTests, igual que el pipeline.
    [switch]$ConTests,

    # Frontend: borra node_modules y hace npm ci (lo que hace el pipeline). Lento; solo si cambian las dependencias.
    [switch]$InstalarDependencias,

    # Por defecto, antes de publicar se guarda en ~/backups del servidor: el JAR actual, el frontend actual
    # y un volcado de la BBDD 'aura' (solo si se despliega backend). Conserva las 10 ultimas de cada tipo.
    [switch]$SinBackup,
    [switch]$SinBackupBBDD,

    # Carpeta del repositorio del frontend.
    [string]$RutaFront,

    # Clave privada SSH. Por defecto ~\.ssh\aura_des | aura_pre | aura_pro si existe.
    [string]$Clave
)

$ErrorActionPreference = 'Stop'

$entornos = @{
    DES = @{ Host = '192.168.100.4'; Rama = 'develop'; Cookie = 'AURA_SESSION_DES' }
    PRE = @{ Host = '192.168.100.3'; Rama = 'release'; Cookie = 'AURA_SESSION_PRE' }
    PRO = @{ Host = '192.168.100.2'; Rama = 'main';    Cookie = '' }
}
$Entorno = @($Entorno | Select-Object -Unique)

$raiz = Split-Path -Parent $PSScriptRoot
if (-not $RutaFront) { $RutaFront = Join-Path (Split-Path -Parent $raiz) 'api_auraingenieria_front_vue' }
$hacerBack = $Parte -in 'Todo', 'Backend'
$hacerFront = $Parte -in 'Todo', 'Frontend'

function Escribir-Paso([string]$texto) {
    Write-Host ''
    Write-Host "=== $texto ===" -ForegroundColor Cyan
}

function Confirmar([string]$pregunta, [string]$palabra) {
    $r = Read-Host "$pregunta (escribe $palabra para continuar)"
    if ($r -cne $palabra) { throw 'Cancelado.' }
}

function Obtener-Git([string]$carpeta) {
    Push-Location $carpeta
    try {
        [pscustomobject]@{
            Rama   = (git rev-parse --abbrev-ref HEAD).Trim()
            Commit = (git rev-parse --short HEAD).Trim()
            Sucios = @(git status --porcelain).Count
        }
    } finally { Pop-Location }
}

# ------------------------------------------------------------------
# Resumen y confirmaciones
# ------------------------------------------------------------------
Escribir-Paso "AURA - DESPLIEGUE $Parte en $($Entorno -join ', ')"

$repos = @()
if ($hacerBack)  { $repos += @{ Nombre = 'backend';  Carpeta = $raiz } }
if ($hacerFront) {
    if (-not (Test-Path (Join-Path $RutaFront 'package.json'))) { throw "No encuentro el frontend en $RutaFront (usa -RutaFront)." }
    $repos += @{ Nombre = 'frontend'; Carpeta = $RutaFront }
}

$desajuste = $false
foreach ($r in $repos) {
    $g = Obtener-Git $r.Carpeta
    Write-Host ("{0,-9} rama {1}  commit {2}  cambios sin commitear: {3}" -f $r.Nombre, $g.Rama, $g.Commit, $g.Sucios)
    foreach ($n in $Entorno) {
        if ($g.Rama -ne $entornos[$n].Rama) {
            Write-Host "  AVISO: el pipeline despliega $n desde '$($entornos[$n].Rama)' y $($r.Nombre) esta en '$($g.Rama)'." -ForegroundColor Yellow
            $desajuste = $true
        }
    }
}
if ($desajuste) { Confirmar 'Desplegar de todas formas' 'si' }
if ($Entorno -contains 'PRO') { Confirmar 'Vas a desplegar en PRODUCCION' 'PRO' }

# ------------------------------------------------------------------
# SSH
# ------------------------------------------------------------------
$opcionesBase = @('-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', '-o', 'ServerAliveInterval=10', '-o', 'ServerAliveCountMax=3')

function Preparar-Ssh([string]$nombre) {
    $script:opciones = $opcionesBase
    $k = if ($Clave) { $Clave } else { Join-Path $env:USERPROFILE ".ssh\aura_$($nombre.ToLower())" }
    if (Test-Path $k) {
        $script:opciones += @('-i', $k, '-o', 'IdentitiesOnly=yes')
        Write-Host "Clave SSH: $k"
    } else {
        Write-Host 'Clave SSH: la de por defecto (agente o id_*)'
    }
    $script:destino = "babenza@$($entornos[$nombre].Host)"
}

function Invoke-Remoto([string]$comando) {
    & ssh @script:opciones $script:destino $comando
    if ($LASTEXITCODE -ne 0) { throw "Fallo el comando remoto (codigo $LASTEXITCODE): $comando" }
}

# ------------------------------------------------------------------
# Compilaciones
# ------------------------------------------------------------------
function Compilar-Frontend {
    Escribir-Paso 'Compilando frontend'
    Push-Location $RutaFront
    try {
        if ($InstalarDependencias) {
            Remove-Item node_modules -Recurse -Force -ErrorAction SilentlyContinue
            npm ci --legacy-peer-deps
            if ($LASTEXITCODE -ne 0) { throw 'npm ci ha fallado.' }
        }
        Remove-Item dist -Recurse -Force -ErrorAction SilentlyContinue
        npm run build
        if ($LASTEXITCODE -ne 0) { throw 'npm run build ha fallado.' }
        if (-not (Test-Path 'dist\index.html')) { throw 'No existe dist\index.html' }
    } finally { Pop-Location }
}

function Compilar-Backend([string]$nombre) {
    Escribir-Paso "Compilando backend para $nombre"
    $cookie = $entornos[$nombre].Cookie
    $local = Join-Path $raiz 'src\main\resources\application-local.properties'
    $copia = "$local.deploy-bak"

    # Si una ejecucion anterior se corto a medias, recupera la configuracion local del puesto.
    if ((Test-Path $copia) -and -not (Test-Path $local)) { Move-Item $copia $local -Force }

    $mvn = if (Test-Path (Join-Path $raiz '.mvn\wrapper\maven-wrapper.properties')) { Join-Path $raiz 'mvnw.cmd' } else { 'mvn' }
    $habiaLocal = Test-Path $local
    if ($habiaLocal) { Move-Item $local $copia -Force }
    Push-Location $raiz
    try {
        # El pipeline borra application-local.properties y escribe solo la cookie del entorno.
        if ($cookie) {
            Write-Host "Cookie de sesion: $cookie"
            Set-Content -Path $local -Value "server.servlet.session.cookie.name=$cookie" -Encoding ASCII
        } else {
            Write-Host 'Sin cookie de sesion especifica para este entorno'
        }
        $mvnArgs = @('-B', 'clean', 'package')
        if (-not $ConTests) { $mvnArgs += '-DskipTests' }
        & $mvn @mvnArgs
        if ($LASTEXITCODE -ne 0) { throw "La compilacion del backend ha fallado (codigo $LASTEXITCODE)." }
    }
    finally {
        Pop-Location
        Remove-Item $local -Force -ErrorAction SilentlyContinue
        if ($habiaLocal) { Move-Item $copia $local -Force }
    }

    $jar = Get-ChildItem (Join-Path $raiz 'target') -Filter '*.jar' -File |
        Where-Object { $_.Name -notmatch '\.original$' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar -or $jar.Length -eq 0) { throw 'No hay JAR valido en target\.' }
    Write-Host ("JAR: {0}  {1:N1} MB" -f $jar.Name, ($jar.Length / 1MB))
    return $jar.FullName
}

# ------------------------------------------------------------------
# Copia de seguridad en el servidor (antes de sustituir nada)
# ------------------------------------------------------------------
function Hacer-Backup([string]$nombre) {
    if ($SinBackup) { Write-Host 'Backup omitido (-SinBackup).' -ForegroundColor Yellow; return }
    Escribir-Paso "Copia de seguridad en $nombre (~/backups)"

    $remoto = @'
set -Eeuo pipefail
TS=$(date +%Y%m%d_%H%M%S)
B="$HOME/backups"; D="$HOME/deploy"
mkdir -p "$B"
'@
    if ($hacerBack) {
        $remoto += @'

if [ -f "$D/backend/aura.jar" ]; then cp "$D/backend/aura.jar" "$B/aura_$TS.jar"; echo "JAR guardado: aura_$TS.jar"; else echo "No hay JAR previo"; fi
ls -t "$B"/aura_*.jar 2>/dev/null | tail -n +11 | xargs -r rm -f
'@
        if (-not $SinBackupBBDD) {
            $remoto += @'

sudo -n mariadb-dump --single-transaction --routines --triggers aura | gzip > "$B/aura_$TS.sql.gz"
echo "BBDD guardada: aura_$TS.sql.gz"
ls -t "$B"/aura_*.sql.gz 2>/dev/null | tail -n +11 | xargs -r rm -f
'@
        }
    }
    if ($hacerFront) {
        $remoto += @'

if [ -d "$D/frontend" ]; then tar -czf "$B/frontend_$TS.tar.gz" -C "$D" frontend; echo "Frontend guardado: frontend_$TS.tar.gz"; else echo "No hay frontend previo"; fi
ls -t "$B"/frontend_*.tar.gz 2>/dev/null | tail -n +11 | xargs -r rm -f
'@
    }

    # El script viaja por stdin; se quitan los CR de Windows para que bash lo entienda.
    ($remoto -replace "`r", '') | & ssh @script:opciones $script:destino 'bash -s'
    if ($LASTEXITCODE -ne 0) {
        throw 'La copia de seguridad ha fallado: no se despliega nada. (Con -SinBackupBBDD se omite solo el volcado de la BBDD; ese paso necesita sudo sin contrasena.)'
    }
}

# ------------------------------------------------------------------
# Publicacion
# ------------------------------------------------------------------
function Publicar-Backend([string]$nombre, [string]$jar) {
    Escribir-Paso "Copiando JAR a $nombre"
    & scp @script:opciones $jar "$($script:destino):/tmp/aura.jar"
    if ($LASTEXITCODE -ne 0) { throw "Fallo scp (codigo $LASTEXITCODE)." }

    Escribir-Paso "Ejecutando deploy backend $nombre"
    Invoke-Remoto 'bash /home/babenza/deploy_backend.sh'

    Escribir-Paso "Esperando backend $nombre"
    for ($i = 1; $i -le 40; $i++) {
        $salida = & ssh @script:opciones $script:destino 'curl -fsS http://127.0.0.1:8986/actuator/health 2>/dev/null'
        if (($salida -join '') -match '"status":"UP"') {
            Write-Host "Backend $nombre disponible" -ForegroundColor Green
            return
        }
        Write-Host "Esperando backend $nombre... $i/40"
        Start-Sleep -Seconds 5
    }
    Write-Host "ERROR: Backend $nombre no responde correctamente" -ForegroundColor Red
    & ssh @script:opciones $script:destino 'systemctl status backend.service --no-pager -l || true'
    throw "Backend $nombre no arranca."
}

function Publicar-Frontend([string]$nombre) {
    Escribir-Paso "Copiando frontend a $nombre"
    # Sin rsync en Windows: se borra el destino temporal y scp -r crea /tmp/aura-frontend como copia de dist\
    Invoke-Remoto 'rm -rf /tmp/aura-frontend'
    & scp -r @script:opciones (Join-Path $RutaFront 'dist') "$($script:destino):/tmp/aura-frontend"
    if ($LASTEXITCODE -ne 0) { throw "Fallo scp (codigo $LASTEXITCODE)." }

    Escribir-Paso "Ejecutando deploy frontend $nombre"
    Invoke-Remoto 'bash /home/babenza/deploy_frontend.sh'

    $http = & ssh @script:opciones $script:destino "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/"
    Write-Host "HTTP $nombre -> $http"
    if (($http -join '').Trim() -ne '200') { throw "Frontend $nombre devuelve HTTP $http" }
    Write-Host "Frontend $nombre disponible" -ForegroundColor Green
}

# ------------------------------------------------------------------
# Ejecucion
# ------------------------------------------------------------------
if ($hacerFront) { Compilar-Frontend }   # el dist vale para todos los entornos (VUE_APP_API_URL vacia)

$resultados = @()
foreach ($n in $Entorno) {
    Escribir-Paso "ENTORNO $n ($($entornos[$n].Host))"
    Preparar-Ssh $n
    Escribir-Paso "Comprobando SSH con $n"
    Invoke-Remoto 'echo SSH_OK && hostname && whoami'

    if ($hacerBack) {
        $jar = Compilar-Backend $n          # el JAR cambia por entorno (cookie de sesion)
    }
    Hacer-Backup $n                         # tras compilar: si la compilacion falla no se toca el servidor
    if ($hacerBack) {
        Publicar-Backend $n $jar
    }
    if ($hacerFront) { Publicar-Frontend $n }

    $resultados += $n
    Write-Host ''
    Write-Host "$Parte DESPLEGADO OK en $n" -ForegroundColor Green
}

# ------------------------------------------------------------------
# Recordatorio de SQL manual
# ------------------------------------------------------------------
# Solo los *_Planificador_*.sql viajan en el JAR y se aplican solos al arrancar (ScriptsBBDD).
# El resto de scripts de BBDD\ se lanzan a mano contra la base de datos de cada entorno.
if ($hacerBack) {
    Push-Location $raiz
    try {
        $manuales = @(git status --porcelain -- src/main/resources/BBDD | Where-Object { $_ -notmatch '_Planificador_' })
    } finally { Pop-Location }
    if ($manuales.Count -gt 0) {
        Write-Host ''
        Write-Host 'AVISO: scripts SQL nuevos o modificados que NO se aplican solos (ejecutalos a mano en cada BBDD):' -ForegroundColor Yellow
        $manuales | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
    }
}

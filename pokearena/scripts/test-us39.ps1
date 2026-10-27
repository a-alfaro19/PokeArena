param(
    [string]$PostgresBin = 'C:/Program Files/PostgreSQL/18/bin',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$MavenCommand = 'mvn'
)
$ErrorActionPreference = 'Stop'

# Usa un clúster desechable y un puerto libre, sin tocar la instalación ni la base del proyecto.
$projectRoot = Split-Path $PSScriptRoot -Parent
$clusterPath = Join-Path ([IO.Path]::GetTempPath()) ('pokearena-us39-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $clusterPath | Out-Null
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
$port = $listener.LocalEndpoint.Port
$listener.Stop()
$environmentNames = @('JAVA_HOME', 'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD', 'POKEARENA_TEST_DATABASE_URL', 'DB_PASSWORD')
$savedEnvironment = @{}
foreach ($name in $environmentNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$started = $false

try {
    # La confianza sin contraseña se restringe al loopback del clúster temporal de pruebas.
    & (Join-Path $PostgresBin 'initdb.exe') -D "$clusterPath/data" -U postgres --auth=trust --encoding=UTF8 --no-locale | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo inicializar PostgreSQL temporal' }
    $startArguments = '-D "' + "$clusterPath/data" + '" -l "' + "$clusterPath/server.log" + '" -o "-h 127.0.0.1 -p ' + $port + '" -w start'
    $process = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList $startArguments -WindowStyle Hidden -PassThru
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'No se pudo iniciar PostgreSQL temporal' }
    $started = $true
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $port -U postgres pokearena_us39_test
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base de pruebas' }

    # Prepara un esquema previo para comprobar la actualización compatible con datos existentes.
    & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $port -U postgres -d pokearena_us39_test -v ON_ERROR_STOP=1 -f "$projectRoot/src/main/resources/schema.sql" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar el esquema previo' }
    & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $port -U postgres -d pokearena_us39_test -v ON_ERROR_STOP=1 -c "INSERT INTO abilities(code,name) VALUES ('TEST-LEGACY','Legacy ability')" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron preparar datos previos' }

    # Mantiene Java 25 en el pom; la verificación local usa el estándar Java 21 explícitamente.
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    $env:POKEARENA_TEST_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$port/pokearena_us39_test"
    $env:SPRING_DATASOURCE_URL = $env:POKEARENA_TEST_DATABASE_URL
    $env:SPRING_DATASOURCE_USERNAME = 'postgres'
    $env:SPRING_DATASOURCE_PASSWORD = ''
    $env:DB_PASSWORD = ''
    Push-Location $projectRoot
    try {
        & $MavenCommand '-Djava.version=21' clean verify
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación o alguna prueba; revise target/surefire-reports' }
    } finally { Pop-Location }
    $legacy = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $port -U postgres -d pokearena_us39_test -t -A -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM abilities WHERE code='TEST-LEGACY' AND name='Legacy ability' AND trigger_code IS NULL AND effect_code IS NULL AND parameters='{}'::jsonb"
    if ($LASTEXITCODE -ne 0 -or $legacy.Trim() -ne '1') { throw 'La migración no preservó la habilidad previa' }
    Write-Host 'Migración sobre esquema previo: habilidad y metadatos preservados.'
} finally {
    # Detiene siempre el servidor temporal y restaura las variables del proceso llamador.
    if ($started) {
        $stopArguments = '-D "' + "$clusterPath/data" + '" -m fast -w stop'
        Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList $stopArguments -WindowStyle Hidden -Wait
    }
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    Write-Host "Archivos temporales de diagnóstico (servidor detenido): $clusterPath"
}

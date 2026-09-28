$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & ./mvnw.bat '-DincludeScope=test' '-Dmdep.outputFile=target/coordinator-demo-classpath.txt' dependency:build-classpath
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o classpath.' }
    $dependencies = (Get-Content target/coordinator-demo-classpath.txt -Raw).Trim()
    & java "-Dlogback.configurationFile=scripts/coordinator-demo-logback.xml" --class-path $dependencies scripts/CoordinatorDemo.java
    if ($LASTEXITCODE -ne 0) { throw 'A demonstracao do Coordinator falhou.' }
} finally {
    Pop-Location
}


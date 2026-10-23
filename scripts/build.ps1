param(
    [string]$JavaHome = '',
    [string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousJavaHome = $env:JAVA_HOME
$previousDebug = $env:DEBUG

try {
    if (-not $JavaHome) {
        $candidates = @(
            $env:JAVA_HOME,
            'C:\Program Files\Android\Android Studio\jbr',
            'C:\Program Files\Android\openjdk\jdk-21.0.8'
        )
        $JavaHome = $candidates | Where-Object {
            $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe'))
        } | Select-Object -First 1
    }
    if ($JavaHome) {
        if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
            throw 'The selected JavaHome does not contain bin\java.exe. Use JDK 17 or 21.'
        }
        $env:JAVA_HOME = $JavaHome
    }
    # DEBUG is used by the standard Windows wrapper to echo every command.
    $env:DEBUG = ''
    Push-Location $projectRoot
    try {
        & .\gradlew.bat @Tasks --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE. See output above."
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:DEBUG = $previousDebug
}

[CmdletBinding()]
param(
    [string] $ProjectPath = (Get-Location).Path,
    [string[]] $Tasks = @('codeQualityCheck', 'lint', 'testDebugUnitTest'),
    [string] $JavaHome
)

$ErrorActionPreference = 'Stop'
$ProjectPath = (Resolve-Path -LiteralPath $ProjectPath).Path
$Wrapper = Join-Path $ProjectPath 'gradlew.bat'

if (-not (Test-Path -LiteralPath $Wrapper)) {
    throw "No Gradle wrapper was found at '$Wrapper'. Run this script from an Android Gradle project or pass -ProjectPath."
}

# Prefer an explicit override, then the persistent Windows user setting. This
# avoids accidentally selecting an IDE-bundled Java version from a stale shell.
$ConfiguredUserJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
} elseif ($ConfiguredUserJavaHome -and (Test-Path -LiteralPath (Join-Path $ConfiguredUserJavaHome 'bin\java.exe'))) {
    $env:JAVA_HOME = $ConfiguredUserJavaHome
}

# Fall back to the current process setting or a known local installation.
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $candidateJdks = @(
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
        'C:\Android Projects\Eclipse Adoptium\jdk-21.0.12.8-hotspot'
    ) | Where-Object { $_ }

    foreach ($candidate in $candidateJdks) {
        if (Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe')) {
            $env:JAVA_HOME = $candidate
            break
        }
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'Java 21 was not found. Set JAVA_HOME to a JDK 21 folder, then rerun this script.'
}

$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"
if (-not $env:JAVA_TOOL_OPTIONS) {
    $env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp'
}
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
}

Write-Host "Project: $ProjectPath"
Write-Host "Java:    $env:JAVA_HOME"
Write-Host "Gradle:  $env:GRADLE_USER_HOME"
Write-Host "Checks:  $($Tasks -join ', ')"

Push-Location $ProjectPath
try {
    & $Wrapper --continue --stacktrace @Tasks
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle checks failed with exit code $LASTEXITCODE. Review the messages above and rerun after fixing the reported issues."
    }
}
finally {
    Pop-Location
}

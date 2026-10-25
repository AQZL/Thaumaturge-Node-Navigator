param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArguments = @('build')
)

$ErrorActionPreference = 'Stop'
$previousJavaHome = $env:JAVA_HOME
$previousJavaToolOptions = $env:JAVA_TOOL_OPTIONS
Push-Location $PSScriptRoot
try {
    $localJdk = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '.local/java25') -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe') } |
        Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
    $probe = [uri]'https://maven.neoforged.net/releases/'
    $systemProxy = [System.Net.WebRequest]::GetSystemWebProxy().GetProxy($probe)
    if ($systemProxy.Host -ne $probe.Host -and $systemProxy.Scheme -eq 'http' -and !$systemProxy.UserInfo) {
        $proxyOptions = "-Dhttp.proxyHost=$($systemProxy.Host) -Dhttp.proxyPort=$($systemProxy.Port) -Dhttps.proxyHost=$($systemProxy.Host) -Dhttps.proxyPort=$($systemProxy.Port)"
        $env:JAVA_TOOL_OPTIONS = ("$previousJavaToolOptions $proxyOptions").Trim()
    }
    & (Join-Path $PSScriptRoot 'gradlew.bat') @GradleArguments
    $buildExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:JAVA_TOOL_OPTIONS = $previousJavaToolOptions
    Pop-Location
}
exit $buildExitCode

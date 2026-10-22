param([switch]$Pairing)
$ErrorActionPreference = "Stop"
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOriginalInterop = $env:SOFAPAD_TEST_INTEROP
$taskProcess = $null
Push-Location $taskRoot
try {
    cargo build --locked --jobs 2 --manifest-path windows-agent/Cargo.toml --example wss_fixture
    if ($LASTEXITCODE -ne 0) { throw "Build da fixture Rust falhou." }
    $taskStart = [Diagnostics.ProcessStartInfo]::new()
    $taskStart.FileName = Join-Path $taskRoot "windows-agent\target\debug\examples\wss_fixture.exe"
    $taskStart.WorkingDirectory = $taskRoot
    if ($Pairing) { $taskStart.Arguments = '--pairing' }
    $taskStart.UseShellExecute = $false
    $taskStart.CreateNoWindow = $true
    $taskStart.RedirectStandardOutput = $true
    $taskStart.RedirectStandardError = $true
    $taskProcess = [Diagnostics.Process]::Start($taskStart)
    # Credenciais de teste efêmeras: IPC em memória, sem imprimir nem persistir.
    $taskLine = $taskProcess.StandardOutput.ReadLine()
    if (-not $taskLine) { throw "Fixture não iniciou." }
    $taskSettings = $taskLine | ConvertFrom-Json
    if ($taskSettings.host -ne "127.0.0.1" -or $taskSettings.token.Length -ne 64) { throw "Fixture inválida." }
    $env:SOFAPAD_TEST_INTEROP = $taskLine
    & "$taskRoot\android-app\gradlew.bat" -p "$taskRoot\android-app" :app:testDebugUnitTest --rerun-tasks --offline --no-daemon --max-workers=1 --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Interoperabilidade Kotlin/Rust falhou." }
    if (-not $taskProcess.WaitForExit(10000)) { throw "Fixture não encerrou após o cliente." }
    if ($taskProcess.ExitCode -ne 0) { throw "Fixture Rust retornou erro." }
    Write-Host "Interoperabilidade Kotlin/Rust WSS: aprovada; credenciais fictícias somente em memória. Pareamento persistente: $Pairing"
} finally {
    $env:SOFAPAD_TEST_INTEROP = $taskOriginalInterop
    if ($taskProcess -and -not $taskProcess.HasExited) { $taskProcess.Kill(); $taskProcess.WaitForExit() }
    if ($taskProcess) { $taskProcess.Dispose() }
    Pop-Location
}

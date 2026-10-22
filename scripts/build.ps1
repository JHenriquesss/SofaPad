param(
    [switch]$SkipAndroid,
    [switch]$SkipRust
)
$ErrorActionPreference = "Stop"
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location $taskRoot
try {
    if (-not $SkipRust) {
        cargo fmt --manifest-path windows-agent/Cargo.toml -- --check
        if ($LASTEXITCODE -ne 0) { throw "Formatação Rust falhou." }
        cargo test --locked --manifest-path windows-agent/Cargo.toml
        if ($LASTEXITCODE -ne 0) { throw "Testes Rust falharam." }
        cargo clippy --locked --manifest-path windows-agent/Cargo.toml --all-targets -- -D warnings
        if ($LASTEXITCODE -ne 0) { throw "Clippy falhou." }
        cargo build --locked --release --manifest-path windows-agent/Cargo.toml
        if ($LASTEXITCODE -ne 0) { throw "Build Rust falhou." }
    }
    if (-not $SkipAndroid) {
        & "$taskRoot\android-app\gradlew.bat" -p "$taskRoot\android-app" :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Build/testes Android falharam." }
    }
} finally { Pop-Location }

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOutput = Join-Path $taskRoot 'artifacts'
$taskApk = Join-Path $taskRoot 'android-app\app\build\outputs\apk\debug\app-debug.apk'
$taskAgent = Join-Path $taskRoot 'windows-agent\target\release\sofapad-agent.exe'
if (-not (Test-Path -LiteralPath $taskApk) -or -not (Test-Path -LiteralPath $taskAgent)) { throw 'Execute scripts/build.ps1 antes de empacotar.' }
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
$taskApkOutput = Join-Path $taskOutput 'SofaPad-0.3.0-desenvolvimento.apk'
$taskAgentOutput = Join-Path $taskOutput 'SofaPad-agent-0.3.0.exe'
Copy-Item -LiteralPath $taskApk -Destination $taskApkOutput
Copy-Item -LiteralPath $taskAgent -Destination $taskAgentOutput
Copy-Item -LiteralPath (Join-Path $taskRoot 'docs\uso-domestico.md') -Destination (Join-Path $taskOutput 'Como-usar-SofaPad-0.3.0.md')
Get-FileHash -Algorithm SHA256 -LiteralPath $taskApkOutput, $taskAgentOutput | Format-Table -AutoSize
Write-Host 'Artefatos de desenvolvimento preparados localmente. Validação em celular físico e assinatura de release pendentes.'

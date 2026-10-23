$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskOutput = Join-Path $taskRoot 'artifacts'
$taskApk = Join-Path $taskRoot 'android-app\app\build\outputs\apk\debug\app-debug.apk'
$taskAgent = Join-Path $taskRoot 'windows-agent\target\release\sofapad-agent.exe'
$taskMetadataPath = Join-Path $taskRoot 'android-app\app\build\outputs\apk\debug\output-metadata.json'
if (-not (Test-Path -LiteralPath $taskApk) -or -not (Test-Path -LiteralPath $taskAgent) -or -not (Test-Path -LiteralPath $taskMetadataPath)) { throw 'Execute scripts/build.ps1 antes de empacotar.' }
$taskMetadata = Get-Content -LiteralPath $taskMetadataPath -Raw | ConvertFrom-Json
$taskAppMatch = [regex]::Match($taskMetadata.elements[0].versionName, '^(\d+\.\d+\.\d+)-desenvolvimento$')
$taskAgentMatch = [regex]::Match((Get-Content -LiteralPath (Join-Path $taskRoot 'windows-agent\Cargo.toml') -Raw), '(?m)^version = "(\d+\.\d+\.\d+)"')
if ($taskMetadata.variantName -ne 'debug' -or $taskMetadata.elements.Count -ne 1 -or -not $taskAppMatch.Success -or -not $taskAgentMatch.Success) { throw 'Metadados de versão incompatíveis com o pacote de desenvolvimento.' }
$taskAppVersion = $taskAppMatch.Groups[1].Value
$taskAgentVersion = $taskAgentMatch.Groups[1].Value
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
$taskApkOutput = Join-Path $taskOutput "SofaPad-$taskAppVersion-desenvolvimento.apk"
$taskAgentOutput = Join-Path $taskOutput "SofaPad-agent-$taskAgentVersion.exe"
Copy-Item -LiteralPath $taskApk -Destination $taskApkOutput
Copy-Item -LiteralPath $taskAgent -Destination $taskAgentOutput
Copy-Item -LiteralPath (Join-Path $taskRoot 'docs\uso-domestico.md') -Destination (Join-Path $taskOutput "Como-usar-SofaPad-$taskAgentVersion.md")
Get-FileHash -Algorithm SHA256 -LiteralPath $taskApkOutput, $taskAgentOutput | Format-Table -AutoSize
Write-Host 'Artefatos de desenvolvimento preparados localmente. Validação em celular físico e assinatura de release pendentes.'

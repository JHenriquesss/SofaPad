[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$AgentPath,
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if (-not $AgentPath) {
    $taskCargo = Get-Content -LiteralPath (Join-Path $taskRoot 'windows-agent\Cargo.toml') -Raw
    $taskVersion = [regex]::Match($taskCargo, '(?m)^version = "(\d+\.\d+\.\d+)"').Groups[1].Value
    if (-not $taskVersion) { throw 'Versao do agente nao encontrada; informe -AgentPath.' }
    $AgentPath = Join-Path $taskRoot "artifacts\SofaPad-agent-$taskVersion.exe"
}
$taskAgent = Get-Item -LiteralPath $AgentPath
if ($taskAgent.PSIsContainer -or $taskAgent.Name -notmatch '^sofapad-agent(?:-\d+\.\d+\.\d+)?\.exe$') {
    throw 'Informe o executavel sofapad-agent.exe ou SofaPad-agent-VERSAO.exe.'
}
$taskAgentPath = $taskAgent.FullName
$taskSha = [Security.Cryptography.SHA256]::Create()
try {
    $taskPathHash = ([BitConverter]::ToString($taskSha.ComputeHash([Text.Encoding]::UTF8.GetBytes($taskAgentPath.ToLowerInvariant())))).Replace('-', '').Substring(0, 16)
} finally { $taskSha.Dispose() }

$taskRules = @(
    @{ Name = "SofaPad-LAN-v1-$taskPathHash-TCP"; Protocol = 'TCP'; LocalPort = 49375; DisplayName = 'SofaPad - controle na rede privada' },
    @{ Name = "SofaPad-LAN-v1-$taskPathHash-mDNS"; Protocol = 'UDP'; LocalPort = 5353; DisplayName = 'SofaPad - descoberta na rede privada' }
)
Write-Host "Executavel: $taskAgentPath"
Write-Host 'Permissao: entrada TCP 49375 e UDP 5353, somente neste executavel, perfil Privado e sub-rede IPv4 local.'
Write-Host 'O script nao altera perfil de rede, estado do firewall, roteador ou regras de outros programas.'
if (-not $Apply) {
    Get-NetConnectionProfile | Select-Object InterfaceAlias, NetworkCategory, IPv4Connectivity | Format-Table
    Get-NetFirewallProfile | Select-Object Name, Enabled | Format-Table
    foreach ($taskRule in $taskRules) {
        Get-NetFirewallRule -Name $taskRule.Name -PolicyStore PersistentStore -ErrorAction SilentlyContinue |
            Select-Object Name, DisplayName, Enabled, Profile, Action | Format-Table
    }
    Write-Host 'Somente consulta. Para configurar, execute novamente com -Apply. O Windows pode pedir autorizacao de administrador uma vez.'
    return
}

$taskPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $WhatIfPreference -and -not $taskPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'Solicitando ao Windows a autorizacao para criar somente as regras acima...'
    $taskArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"{0}"' -f $PSCommandPath), '-AgentPath', ('"{0}"' -f $taskAgentPath), '-Apply')
    $taskElevated = Start-Process -FilePath (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') -Verb RunAs -WindowStyle Hidden -ArgumentList $taskArguments -Wait -PassThru
    if ($taskElevated.ExitCode -ne 0) { throw 'O Windows nao concluiu a configuracao. Execute o script em PowerShell como administrador para ver o motivo.' }
    Write-Host 'Regras do SofaPad configuradas. Elas so permitem acesso em uma rede com perfil Privado e nao ligam o firewall.'
    return
}

foreach ($taskRule in $taskRules) {
    if ($PSCmdlet.ShouldProcess("$taskAgentPath / $($taskRule.Protocol) $($taskRule.LocalPort)", 'Permitir entrada somente na rede privada e sub-rede IPv4 local')) {
        $taskParameters = @{
            Name = $taskRule.Name
            PolicyStore = 'PersistentStore'
            Description = 'SofaPad: acesso local autenticado. Limitado ao executavel, perfil Privado e sub-rede IPv4 local.'
            Enabled = 'True'
            Direction = 'Inbound'
            Action = 'Allow'
            Profile = 'Private'
            Program = $taskAgentPath
            Protocol = $taskRule.Protocol
            LocalPort = $taskRule.LocalPort
            RemoteAddress = 'LocalSubnet4'
            EdgeTraversalPolicy = 'Block'
        }
        $taskExisting = Get-NetFirewallRule -Name $taskRule.Name -PolicyStore PersistentStore -ErrorAction SilentlyContinue
        if ($taskExisting) {
            Set-NetFirewallRule @taskParameters -NewDisplayName $taskRule.DisplayName | Out-Null
        } else {
            New-NetFirewallRule @taskParameters -DisplayName $taskRule.DisplayName | Out-Null
        }
        Write-Host "Regra configurada: $($taskRule.Name)"
    }
}
if (-not $WhatIfPreference) {
    if (-not @(Get-NetConnectionProfile | Where-Object { $_.NetworkCategory -eq 'Private' }).Count) {
        Write-Warning 'A conexao atual nao esta marcada como Privada. As regras nao se aplicam ao perfil Publico; selecione Privada nas configuracoes somente se esta for sua rede domestica confiavel.'
    }
    if (-not (Get-NetFirewallProfile -Name Private).Enabled) {
        Write-Warning 'O Firewall do Windows esta desligado no perfil Privado. A regra fica preparada, mas sua ausencia nao explica um bloqueio atual desse firewall.'
    }
}

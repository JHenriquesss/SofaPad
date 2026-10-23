# Diagnosticar a conexão e preparar o firewall

O agente precisa estar aberto e escutando no IP exibido em `Status`, na porta TCP 49375. O computador pode usar cabo Ethernet e o celular Wi-Fi, desde que os dois alcancem a mesma rede local. Descobrir o anúncio mDNS não garante que a conexão TCP esteja liberada.

## Permissão específica do SofaPad

Na pasta `scripts`, um duplo clique em `liberar-sofapad.cmd` prepara duas regras. O Windows solicita autorização de administrador uma vez; o agente continua funcionando como usuário comum. Isso não altera o perfil da rede nem liga/desliga o firewall.

As regras permitem somente:

- O executável SofaPad da versão empacotada em `artifacts`.
- Entrada TCP 49375 para pareamento/controle e UDP 5353 para descoberta.
- Perfil **Privado**, com origem na sub-rede IPv4 local e travessia de borda bloqueada.

Se a rede estiver marcada como Pública, essas regras não se aplicam. Escolha o perfil Privado nas configurações de rede do Windows somente na sua rede doméstica confiável. Se o firewall já estiver desligado, adicionar uma regra não resolve o bloqueio atual: investigue o endereço, o celular e o caminho até o PC.

Consulta e simulação, sem criar regras nem abrir UAC:

```powershell
.\scripts\configurar-firewall.ps1
.\scripts\configurar-firewall.ps1 -Apply -WhatIf
```

Para outro caminho de instalação:

```powershell
.\scripts\configurar-firewall.ps1 -AgentPath 'C:\CAMINHO\SofaPad-agent-0.3.1.exe' -Apply
```

As regras têm nomes `SofaPad-LAN-v1-HASH-TCP` e `SofaPad-LAN-v1-HASH-mDNS`, com hash do caminho do executável. Repetir a configuração do mesmo caminho atualiza essas regras sem duplicar. Uma nova versão com outro nome exige configurar seu próprio executável. As regras podem ser removidas pelo Firewall do Windows com Segurança Avançada, selecionando as entradas do SofaPad e conferindo o caminho em Programas e Serviços.

Implementação baseada no [New-NetFirewallRule](https://learn.microsoft.com/en-us/powershell/module/netsecurity/new-netfirewallrule) e [Set-NetFirewallRule](https://learn.microsoft.com/en-us/powershell/module/netsecurity/set-netfirewallrule) da Microsoft.

## Separar rede de aplicativo

Com o celular no Wi-Fi, abrir `https://IP_DO_PC:49375` no navegador pode ajudar: um aviso de certificado já confirma que o TCP/TLS chegou ao agente. Pare nesse aviso, sem prosseguir nem informar código. A resposta HTTP não é uma página do SofaPad; o protocolo usado pelo app é WebSocket autenticado.

Se não houver resposta, confira se o IP e a porta são os atuais, se o celular está em Wi-Fi comum (não de convidados), se há VPN e se o roteador isola Wi-Fi e Ethernet. Se o navegador alcançar o agente e o SofaPad não, o próximo diagnóstico deve examinar o erro de socket/permissões do Android.

Validação desta entrega: parser PowerShell, consulta dos perfis e simulação `-Apply -WhatIf`, sem alterações de firewall. A configuração administrativa real ainda precisa de execução e conferência após o usuário aceitar o UAC.

## Diagnóstico no Android físico por USB

`NetworkProbeTest` usa o processo do próprio aplicativo para testar TCP e, depois, TLS/WebSocket com o mesmo pin SHA-256 e hostname usados no pareamento. Ele não abre uma tela, envia comandos de entrada ou usa códigos/tokens reais. A resposta esperada é HTTP 403, pois a requisição de pareamento não tem autorização. Uma falha mostra a exceção específica do socket/TLS nesse teste isolado, sem acessar as credenciais salvas do aplicativo.

O teste é opt-in: sem os argumentos públicos de IP e identidade, ele é ignorado. Obtenha a identidade no agente local e substitua os dois valores ilustrativos antes de executar:

```powershell
.\android-app\gradlew.bat -p android-app :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain
adb devices -l
adb install -r android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class br.com.sofapad.NetworkProbeTest -e sofapadHost IP_DO_PC -e sofapadFingerprint IDENTIDADE_SHA256_DE_64_CARACTERES br.com.sofapad.test/androidx.test.runner.AndroidJUnitRunner
```

O APK de desenvolvimento do SofaPad precisa estar instalado no aparelho, com a mesma assinatura do APK de testes. Se houver mais de um aparelho, selecione o serial explicitamente com `adb -s SERIAL`. O telefone deve autorizar a depuração USB neste computador. A recusa HTTP conta como uma tentativa inválida de pareamento; não repita o teste em loop.

O argumento opcional `-e sofapadPort PORTA` seleciona uma porta de diagnóstico diferente de 49375. O exemplo Rust `diagnose_tls` usa entrada simulada e apresenta uma identidade efêmera pública; não substitui o agente doméstico nem autoriza controle.

Compilação de `assembleDebugAndroidTest` e execução pelo Wi-Fi em aparelho U2/Android 10 concluídas: o teste falhou no agente 0.3.1 e passou no 0.3.2, com a mesma identidade persistente do PC. Veja a [correção TLS](correcao-tls-0.3.2.md). Nenhum emulador foi iniciado.

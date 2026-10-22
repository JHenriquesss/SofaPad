# Conexão de desenvolvimento — Etapa B

Documento histórico da versão 0.2.0. Para o app atual, use [pareamento persistente da Etapa C](uso-domestico.md). A interface de autorização temporária descrita abaixo foi substituída no Android.

A implementação já contém entrada real `SendInput`, cliente Android WSS, reconexão e descoberta. O teste local confirmou Unicode e liberação de arrasto em uma janela Win32 própria. A validação completa em celular físico e Wi-Fi doméstico ainda está pendente. Esta versão não substitui a instalação doméstica da Etapa C.

## Autorizar uma sessão no PC

Compile conforme [instalação](instalacao.md). No PC do desenvolvedor, descubra o IPv4 da interface Wi-Fi e execute, substituindo o endereço de exemplo pelo endereço real desse PC:

```powershell
.\windows-agent\target\release\sofapad-agent.exe --control 192.168.1.10:49375
```

1. Confirme a autorização na janela do próprio Windows. O padrão é Não.
2. Uma segunda janela mostra endereço, porta, identidade SHA-256 do certificado e código privado da sessão. Cada um dos dois códigos tem 64 caracteres hexadecimais.
3. Mantenha o agente aberto. O diálogo pode ser fechado depois de copiar os dados; o console permite encerrar com Ctrl+C.

A identidade TLS e o código são gerados novamente a cada execução. Ficam somente em memória no agente e no cliente; não há arquivo de credenciais, parâmetro de token na linha de comando, token em URL nem log de texto digitado. A autorização expira após oito horas ou ao encerrar o processo. Não compartilhe nem cole o código em issues, mensagens ou logs.

Sem argumentos, o executável continua sendo apenas o simulador loopback da Etapa A. O modo real exige `--control` e confirmação local. Não aceita bind em `0.0.0.0`, endereço público ou todas as interfaces. Para este cliente Android, use IPv4 privado da rede doméstica. O servidor aceita um controlador por vez.

## Conectar pelo Android

1. Instale o APK debug no celular do desenvolvedor.
2. Abra a engrenagem e vá até Conectar ao PC.
3. Use Buscar PCs na rede para preencher IP/porta, ou digite esses dois campos. O agente anuncia `_couchremote._tcp.local.`.
4. Copie integralmente a identidade e o código exibidos na janela do PC. A descoberta não fornece credenciais nem autoriza o dispositivo.
5. Toque Conectar com autorização do PC. A tela principal só indica PC conectado depois do handshake TLS, autorização e mensagem `hello` válida.

O app usa `wss://IP:PORTA/v1/control`. Ele verifica o SHA-256 exato do certificado confirmado no PC, sua validade e o hostname/IP pelo verificador padrão do OkHttp. Não há opção para ignorar certificados. `Authorization: Bearer ...` é enviado dentro do TLS antes da abertura do WebSocket.

As credenciais desta etapa não sobrevivem ao encerramento do app. Sair temporariamente e retornar permite reconectar usando os dados ainda em memória. Ao reiniciar o agente, é necessário repetir a autorização. O fluxo definitivo com QR/código simples, dispositivo persistente, Keystore/DPAPI e revogação individual continua pertencendo à Etapa C.

## Firewall e rede

Não foram alteradas regras de firewall neste desenvolvimento. Se o Windows solicitar acesso ao executar o agente, autorize somente na rede privada escolhida para teste. Não desative o firewall, não abra portas no roteador e não use UPnP. Redes de convidados ou isolamento entre clientes podem impedir comunicação mesmo quando ambos mostram o mesmo Wi-Fi.

Após perda de rede, o Android tenta novamente com espera crescente de até 15 segundos. Isso não repete texto, cliques ou atalhos antigos. Se o IP mudar, encontre o PC novamente e confirme os dados válidos da sessão. Mudança de IP com descoberta/reconexão completamente automática e identidade persistente exige o trabalho restante da Etapa C.

## Entrega de texto e encerramento

- Enviar mantém a composição visível e bloqueada enquanto aguarda `ack` do agente. Ao confirmar, fecha o teclado e volta ao controle.
- Se a conexão cair antes da confirmação, o texto permanece no campo. O aviso pede conferir o PC antes de reenviar: o agente pode já ter inserido o texto e apenas a resposta ter sido perdida.
- O app não enfileira comandos estando desconectado. A fila tem limite de 64 comandos e combina somente movimentos/rolagens consecutivos, sem atravessar press/release.
- Ao sair do app, suspende a conexão e tenta enviar `release_all`. O servidor também libera estado quando detecta desconexão, falha ou três segundos sem dados.
- Use Ctrl+C no console para encerrar normalmente. Finalização forçada do processo ou desligamento abrupto não equivale ao encerramento testado.
- O agente não altera foco ao receber comandos. A entrada vai ao aplicativo focado; não há suporte a UAC/secure desktop ou janelas de integridade superior. Modificadores físicos pressionados levam à recusa de teclado, preservando o estado físico do usuário.

## Verificações reproduzíveis

Os testes de WSS usam certificados e autorização efêmeros, em loopback, sem abrir a LAN. Os testes JVM usam MockWebServer com TLS e não precisam de emulador. O teste Win32 é opt-in porque cria e foca brevemente uma janela própria, injeta texto nela e restaura foco/cursor originais:

```powershell
cargo test --locked --jobs 2 --manifest-path windows-agent/Cargo.toml -- --test-threads=1
cargo test --locked --jobs 2 --manifest-path windows-agent/Cargo.toml --test windows_native -- --ignored --test-threads=1
.\android-app\gradlew.bat -p android-app :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1
.\scripts\test-interop.ps1
```

O script de build e o Gradle limitam concorrência: Rust com dois jobs/testes sequenciais, Java com heap de 1 GiB e compilação Kotlin no mesmo processo, Gradle com um worker. Não é necessário iniciar emulador para esses comandos.

Referências: [Microsoft SendInput](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendinput), [Android NSD](https://developer.android.com/develop/connectivity/wifi/use-nsd), [permissões LAN](https://developer.android.com/privacy-and-security/local-network-permission), [rcgen](https://docs.rs/rcgen/latest/rcgen/fn.generate_simple_self_signed.html), [rustls](https://github.com/rustls/rustls), [OkHttp](https://github.com/square/okhttp) e [mdns-sd](https://docs.rs/mdns-sd/latest/mdns_sd/).

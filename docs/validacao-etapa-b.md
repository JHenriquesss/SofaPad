# Validação da implementação da Etapa B

22/10/2026, Windows x64 de desenvolvimento. **Implementação entregue; aceite completo em celular físico/Wi-Fi ainda pendente.** Não considerar a versão doméstica pronta.

## Resultado

| Verificação | Resultado |
| --- | --- |
| Testes Rust de protocolo, entrada planejada, sessão, TLS e WebSocket | 25 aprovados |
| Teste Win32 opt-in com `SendInput` real | 1 aprovado |
| Testes Kotlin/JVM de protocolo, fila, TLS e reconexão | 10 aprovados |
| Cliente Kotlin real → servidor Rust real, WSS em loopback, entrada simulada | 1 aprovado |
| Clippy com `-D warnings` e formatação Rust | aprovados |
| Build Windows release | executado com sucesso |
| APK debug, APK de testes e lint Android | executados com sucesso; lint sem erros, 12 avisos não bloqueantes |

São **37 testes executados com sucesso**. O teste Win32 fica ignorado no comando Rust comum e foi executado separadamente com `--ignored`. O teste de interoperabilidade fica condicionado à fixture e foi executado pelo script próprio, sem ignorados nessa execução JVM. Os oito testes de interface da Etapa A continuam no projeto e foram compilados, mas não foram executados novamente nesta etapa.

O lint registrou avisos sobre versões mais recentes das dependências e sobre o `X509TrustManager` dedicado ao certificado confirmado pelo usuário. Esse verificador exige o SHA-256 exato do certificado e validade temporal; o hostname/IP permanece validado pelo OkHttp. Os testes recusaram certificado incorreto e autorização recusada. Não foi desativada validação TLS global nem usado `trust all`.

## O que foi exercitado de verdade

- `SendInput` inseriu `Olá, ação! 😀` em um controle EDIT Win32 próprio. O teste conferiu texto UTF-16, ausência de Enter adicional, liberação de botão após arrasto e restauração do foco e cursor originais.
- O backend não chama APIs para ativar sua janela ao receber entrada. A janela de teste ganhou foco por ação explícita da fixture, e foi destruída ao terminar.
- Não foram acionados volume/reprodução nem clipboard real, para preservar o trabalho paralelo. As sequências de atalhos/mídia são implementadas; testes de planejamento verificam a ordem de teclas/modificadores.
- Um cliente Kotlin/OkHttp conectou ao Rust/rustls, verificou a identidade, enviou autorização e Unicode e recebeu confirmação. A fixture usa entrada simulada; esse teste não afirma controle físico do Windows pela rede.
- Os testes TLS Rust recusaram cliente sem autorização e certificado desconhecido; a conexão válida liberou botão ao desconectar.
- Os testes Kotlin verificaram certificado incorreto, recusa de autorização, confirmação de texto, fila limitada, combinação de movimentos sem atravessar botões e reconexão sem repetir texto sem confirmação.

## Recursos da máquina

Nenhum emulador foi iniciado e nenhum processo do projeto de transcrição foi interrompido, reconfigurado ou finalizado. Foram usados builds sequenciais: Rust com dois jobs, testes sequenciais, Gradle com um worker e heap Java limitado a 1 GiB, com Kotlin no mesmo processo. As leituras de memória livre foram aproximadamente 5,1 GiB no início e 6,2 GiB após o primeiro build Android; não são uma medição contínua de pico.

O teste nativo usou somente uma janela descartável e restaurou foco/cursor, com assertivas. Nenhum agente de controle ficou executando em background após a validação; a fixture WSS de loopback é encerrada pelo script.

## Comandos

```powershell
cargo test --locked --jobs 2 --manifest-path windows-agent/Cargo.toml -- --test-threads=1
cargo test --locked --jobs 2 --manifest-path windows-agent/Cargo.toml --test windows_native -- --ignored --test-threads=1
cargo clippy --locked --jobs 2 --manifest-path windows-agent/Cargo.toml --all-targets -- -D warnings
cargo build --locked --jobs 2 --release --manifest-path windows-agent/Cargo.toml
.\android-app\gradlew.bat -p android-app :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1
.\scripts\test-interop.ps1
```

Foi necessário manter a propriedade de diretório temporário Java documentada em [instalação](instalacao.md). Dependências novas foram baixadas antes da execução offline. O script de interoperabilidade transfere a configuração efêmera da fixture por pipe e variável de ambiente de processo; não imprime nem grava o código de autorização.

## Arquivos principais

- `windows-agent/src/windows_input.rs` e `input_plan.rs`: backend Win32 e sequências testáveis.
- `windows-agent/src/secure.rs`, `server.rs` e `main.rs`: WSS, autorização, confirmação local, anúncio mDNS e encerramento.
- `android-app/.../RemoteConnection.kt`: fila, cliente TLS, heartbeat, confirmação e reconexão.
- `ConnectionPanel.kt`, `PcDiscovery.kt`, `Simulator.kt`, `MainActivity.kt` e `SofaPadApp.kt`: configuração, descoberta, estado, ciclo de vida e envio confirmado.
- Testes Rust/JVM, fixture de interoperabilidade, manifestos, dependências, scripts e documentação.

## O que falta para aceitar a etapa e avançar à instalação

1. Celular físico: instalar APK, confirmar sessão no PC e validar descoberta/fallback na rede real. Nenhum celular estava disponível via ADB durante esta execução.
2. Conferir os gestos simultâneos, IME e rotação com a conexão real; testar Notepad/navegador, atalhos, mídia e perda de Wi-Fi/suspensão.
3. Medir consumo e resposta do agente real enquanto o celular está conectado.
4. Etapa C: substituir a autorização de desenvolvimento por pareamento persistente e simples, Keystore/DPAPI, revogação individual, bandeja e inicialização após login.
5. Etapa D: matriz completa de reinícios, falhas e instalação em outro PC, sem reaproveitar credenciais.

O cliente atual usa IPv4 privado, guarda autorização apenas em memória e exige nova confirmação após reinício do agente/app. Descoberta mDNS está implementada, mas seu funcionamento na rede doméstica e a política de firewall ainda exigem validação presencial. Não foram alterados firewall, roteador, privilégios ou segurança do Windows.

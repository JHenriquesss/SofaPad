# Fluidez do cursor e reconexão — 0.3.4

## O que mudou

**Android**

- **Envio imediato.** Antes, os comandos saíam a cada 16 ms, num relógio fixo fora de sincronia com a tela, e o cursor andava aos trancos. Agora cada movimento é enviado assim que acontece, com teto de cerca de 180 comandos por segundo, abaixo do limite de 240 do agente. Se muitos comandos aguardam confirmação (Wi-Fi lento), os movimentos se somam na fila em vez de derrubar a conexão.
- **Wi-Fi de baixa latência.** Enquanto o app está aberto na tela, ele pede ao Android o modo `WIFI_MODE_FULL_LOW_LATENCY`, que evita a economia de energia do Wi-Fi entre toques (fonte de atrasos de 100–200 ms). É liberado ao sair do app. Exige as permissões normais `WAKE_LOCK` e `ACCESS_NETWORK_STATE`, sem pedido ao usuário.
- **Reconexão mais rápida.** Primeira nova tentativa em 0,25 s, depois 1, 2, 4 e no máximo 8 s (antes: 1 s até 15 s). Quando o Wi-Fi volta, o app tenta na hora.
- **Rotação sem reconectar.** Girar o celular recriava a tela e derrubava a conexão; agora só solta os botões.
- **Ajustes finos no touchpad.** Movimentos menores que a zona de toque eram descartados. Agora, se o dedo fica pousado além do tempo de um clique (300 ms), todo movimento é enviado. O toque-clique continua igual.

**Windows**

- **Reconexão instantânea do mesmo celular.** O agente aceita conexões em paralelo (até oito abertas). Se o mesmo aparelho reconectar enquanto a conexão antiga ainda está pendurada (queda de Wi-Fi sem aviso), a nova sessão assume na hora: a antiga perde a permissão de injetar entrada e termina na próxima leitura. O estado de mouse e teclado é compartilhado entre conexões, então um arrasto deixado pela sessão antiga é solto.
- **Outro aparelho recebe "ocupado" na hora** (HTTP 409), em vez de ficar travado até o prazo de conexão. Ele não derruba quem está controlando.
- Espera entre verificações de novas conexões caiu de 100 ms para 20 ms.

O protocolo não mudou: APK 0.3.3 e agente 0.3.4 (ou o contrário) continuam compatíveis, mas as melhorias completas exigem os dois atualizados.

## Arquivos

- Android: `RemoteConnection.kt`, `MainActivity.kt`, `SofaPadApp.kt`, `Simulator.kt`, `AndroidManifest.xml`, `RemoteConnectionTest.kt`, versão em `app/build.gradle.kts`.
- Windows: `secure.rs`, `server.rs`, `household.rs`, `input.rs`, `tray.rs`, `tests/tls.rs`, versão em `Cargo.toml`/`Cargo.lock`.
- Documentação: `protocol/README.md`, `docs/uso-domestico.md`.

## Verificações

- Rust: todos os testes aprovados, incluindo o novo teste em que o mesmo aparelho substitui uma sessão pendurada com arrasto ativo e outro aparelho recebe 409; formatação e Clippy sem avisos.
- Android: APK debug, testes JVM (incluindo o novo teste de envio imediato sem passar de 240 comandos/s) e lint aprovados.
- Interoperabilidade Kotlin/Rust aprovada com e sem pareamento persistente.

Testes instrumentados e uso real no celular não foram executados nesta validação local.

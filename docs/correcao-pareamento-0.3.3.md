# Pareamento com outro celular conectado — 0.3.3

## Causa e correção

O servidor Windows atende uma conexão de controle por vez. Ao escolher `Parear celular`, a versão anterior encerrava a conexão ativa antes de abrir a janela temporária de pareamento. O celular já autorizado podia reconectar automaticamente e ocupar novamente o servidor. A tentativa do segundo aparelho então aguardava TLS sem chegar à confirmação no Windows.

Agora o agente abre a janela de pareamento antes de desconectar o controlador atual. Enquanto o código está disponível e válido, conexões de controle recebem HTTP 409 e o servidor fica disponível para o pareamento. Essas recusas não consomem o limite de tentativas de autorização. A reserva termina ao usar, cancelar ou expirar o código. A confirmação presencial e o código de uso único continuam obrigatórios.

O APK mostra os oito caracteres do código durante a digitação e limpa o campo depois do sucesso. O estabelecimento de TLS/WebSocket tem limite de oito segundos, separado do minuto para confirmar no Windows. Ao cancelar ou esgotar o prazo, o cliente fecha o socket.

O controle continua sendo de um celular por vez. Depois de parear outro aparelho, feche o SofaPad no anterior antes de usar o novo. Esta correção permite concluir o pareamento sem adicionar controle simultâneo.

## Arquivos e verificações

- Android: `ConnectionPanel.kt`, `PairingClient.kt`, `PairingTest.kt` e versão em `app/build.gradle.kts`.
- Windows: `pairing.rs`, `server.rs`, `tray.rs`, `tests/tls.rs` e versão em `Cargo.toml`/`Cargo.lock`.
- Guia: `docs/uso-domestico.md`.

Comandos de validação (Gradle com um worker e heap de 1 GiB):

```powershell
.\scripts\build.ps1 -SkipAndroid
.\android-app\gradlew.bat -p android-app :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 --console=plain
.\scripts\test-interop.ps1 -Pairing
.\scripts\package-dev.ps1
```

Os testes Rust incluem liberação de um arraste ao abrir o pareamento, seis reconexões recusadas do controlador anterior, pareamento do novo aparelho e retomada da autorização anterior. Os testes JVM incluem TLS sem resposta, cancelamento do socket no prazo e tempo de confirmação independente do estabelecimento da conexão.

Não foram iniciados emuladores. O aceite completo de gestos, IME, reconexão e reinício continua pendente.

Resultado local: 34 testes Rust aprovados, dois testes nativos opt-in não executados; formatação, Clippy e build release aprovados. APK debug, APK de testes e lint concluídos (zero erros, 12 avisos preexistentes). Os 19 testes JVM passaram, incluindo interoperabilidade Kotlin/Rust com pareamento e recarga da autorização. O certificado de desenvolvimento do APK foi preservado para permitir atualizar sem desinstalar.

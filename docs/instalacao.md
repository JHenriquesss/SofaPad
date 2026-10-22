# Build e instalação de desenvolvimento

Esta versão abre em simulação e também permite uma sessão de controle real autorizada no Windows. Consulte [conexão da Etapa B](conexao-etapa-b.md). A validação em celular físico e o fluxo doméstico persistente ainda estão pendentes; não instalar como produto pronto no PC da usuária.

## Ferramentas

- Windows 10/11 x64 para gerar e executar o agente Windows.
- Rust estável, Cargo e ferramentas C++ do Visual Studio Build Tools com Windows SDK. O build local foi preparado com Rust 1.96.0 e target `x86_64-pc-windows-msvc`.
- JDK 17, Android SDK Platform 36, Build Tools e Platform Tools. Android Studio é uma opção para instalar essas ferramentas.
- Para executar o APK: Android 10/API 29 ou superior, físico ou emulador.

Configure `JAVA_HOME` para o JDK e `ANDROID_HOME` para o SDK, ou informe `sdk.dir` em `android-app/local.properties` (arquivo local ignorado pelo Git). As dependências são baixadas no primeiro build. Não é preciso instalar Gradle globalmente: o wrapper está no repositório e verifica o checksum da distribuição.

Versões fixadas: AGP 8.13.2, Gradle 8.14.3, Kotlin/plugin Compose 2.2.10, Compose BOM 2025.08.00 e OkHttp 4.12.0; compile/target SDK 36. As versões de Rust resolvidas estão em `windows-agent/Cargo.lock`. A escolha de SDK 36 permite testar com a plataforma instalada; a migração para target 37 exigirá a revisão de permissões LAN para esse novo target.

Referências usadas: [compatibilidade AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes), [BOM Compose](https://android-developers.googleblog.com/2025/08/whats-new-in-jetpack-compose-august-25-release.html), [gestos Compose](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) e [tungstenite](https://docs.rs/tungstenite/0.30.0/tungstenite/).

## Compilar e testar

No PowerShell, a partir da raiz do checkout:

```powershell
.\scripts\build.ps1
```

O script executa formatação, testes, Clippy e build release Rust; depois APK debug, testes JVM e lint Android. Usa dois jobs Rust, testes sequenciais, um worker Gradle e heap Java de 1 GiB, sem emulador. Para executar apenas uma plataforma, use `-SkipAndroid` ou `-SkipRust`. Qualquer falha interrompe o script.

Comandos individuais:

```powershell
cargo test --locked --manifest-path windows-agent/Cargo.toml
cargo clippy --locked --manifest-path windows-agent/Cargo.toml --all-targets -- -D warnings
cargo build --locked --release --manifest-path windows-agent/Cargo.toml
.\android-app\gradlew.bat -p android-app :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Em Linux/macOS, os testes do backend simulado Rust também podem ser executados. Para o Android, use `sh android-app/gradlew -p android-app ...` com os mesmos nomes de tarefas. Um executável Windows exige toolchain e linker compatíveis com Windows.

Saídas locais, ignoradas pelo Git:

- APK: `android-app/app/build/outputs/apk/debug/app-debug.apk`.
- Agente Windows: `windows-agent/target/release/sofapad-agent.exe` (simulado por padrão; controle real exige autorização).
- Testes JVM: `android-app/app/build/reports/tests/testDebugUnitTest/index.html`.
- Lint: `android-app/app/build/reports/lint-results-debug.html`.

O APK debug usa a assinatura de desenvolvimento gerada pelo SDK. Não há chave de release no projeto e nenhum APK é versionado.

## Executar o Android

Conecte o seu celular de desenvolvimento com depuração USB habilitada, autorize esse computador e confira:

```powershell
adb devices
adb install -r android-app/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n br.com.sofapad/.MainActivity
```

Se houver mais de um dispositivo, use `adb -s SERIAL ...`. O app abre diretamente no controle em simulação; as configurações oferecem a conexão real. O manifesto declara `INTERNET`, sem prompt de runtime no target SDK 36. Não pede acessibilidade, captura, overlay ou localização.

Para testes instrumentados com um dispositivo/emulador disponível:

```powershell
.\android-app\gradlew.bat -p android-app :app:connectedDebugAndroidTest
```

Os testes verificam posições dos controles, cancelamento de botão, texto com acentos, envio sem Enter e acesso aos atalhos na digitação compacta. Há testes adicionais dependentes de aparelhos reais em [testes manuais](testes-manuais.md).

## Executar o agente local

```powershell
cargo run --locked --manifest-path windows-agent/Cargo.toml
```

Sem argumentos, o console identifica a simulação, escutando em `ws://127.0.0.1:49375/v1/simulate`. Encerre com Ctrl+C. Esse endpoint nunca controla o Windows e não é o endpoint usado pelo Android. Para controle real WSS, autorização local, descoberta e conexão do celular, siga [conexão da Etapa B](conexao-etapa-b.md).

## Falha de socket Java no ambiente Codex/Windows

Foi observada a falha `Unable to establish loopback connection`, com `Invalid argument: connect` em `UnixDomainSockets`, causada pelo uso de diretório temporário virtualizado neste ambiente. A execução local foi desbloqueada com uma pasta no checkout e a propriedade oficial `jdk.net.unixdomain.tmpdir`:

```powershell
New-Item -ItemType Directory -Force artifacts/java-tmp | Out-Null
$taskSocketDirectory = (Resolve-Path artifacts/java-tmp).Path
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$taskSocketDirectory"
```

Use um caminho curto sem espaços nesse caso; a configuração vale apenas para essa sessão do terminal. Não é necessário alterar firewall ou privilégios do aplicativo. Referência: [OpenJDK — diretórios de sockets no Windows](https://mail.openjdk.org/pipermail/nio-dev/2023-March/013297.html).

## Ainda não implementado

Pareamento persistente para uso diário, QR/código simplificado, credenciais em Keystore/DPAPI, revogação por dispositivo, bandeja e inicialização após login. A Etapa B usa identidade/código efêmeros em memória, não credenciais persistidas em texto puro. A matriz de testes em celular físico continua necessária antes da instalação doméstica.

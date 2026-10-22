# Validação da implementação da Etapa C

Executada em 22/10/2026 no PC de desenvolvimento. A entrega implementa o fluxo seguro persistente; **o aceite doméstico em celular físico ainda está pendente**.

## Alterações

- Rust: `pairing.rs`, `secure.rs` e `server.rs` implementam janela de uso único, confirmação local, hashes de tokens, persistência, limites e revogação. `platform.rs` usa DPAPI do usuário, gravação atômica e registro HKCU para início opcional após login.
- Windows: `household.rs`, `tray.rs` e `main.rs` executam em bandeja sem console no release, sem elevação ou serviço Session 0. Menu de status, pareamento, cancelamento, revogação, início/desativação e saída; recuperação do ícone após reiniciar Explorer. Apenas o clique explícito na bandeja/pareamento abre diálogos; receber comandos não ativa a janela.
- Android: `PairingClient.kt`, `CredentialStore.kt`, conexão, descoberta, atividade e configurações usam pin presencial, Keystore/AES-GCM e recuperação automática do PC salvo. A descoberta atualiza IP/porta somente para a identidade já salva. Sem replay de comandos após reconexão.
- Protocolo, guia de uso, roteiro de validação e empacotamento local atualizados. APK debug e agente recebem versão 0.3.0 de desenvolvimento.

## Verificações executadas

Com Rust/Cargo 1.96.0, MSVC x64, JDK 17 e Android SDK 36:

```powershell
cargo fmt --manifest-path windows-agent/Cargo.toml -- --check
cargo clippy --locked --jobs 2 --manifest-path windows-agent/Cargo.toml --all-targets -- -D warnings
cargo test --locked --jobs 2 --manifest-path windows-agent/Cargo.toml -- --test-threads=1
cargo test --locked --jobs 2 --manifest-path windows-agent/Cargo.toml -- --ignored --test-threads=1
cargo build --locked --jobs 2 --release --manifest-path windows-agent/Cargo.toml
.\scripts\test-interop.ps1 -Pairing
.\android-app\gradlew.bat -p android-app :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain
.\scripts\package-dev.ps1
```

Resultados registrados após executar os comandos:

- Rust: 31 testes automáticos e dois opt-in nativos aprovados. Incluem DPAPI real com rejeição de adulteração; código expirado/cancelado/reutilizado e limite de tentativas; falha ao salvar sem autorizar; identidade recuperada; recusa presencial; código curto impedido de controlar; revogação durante arrasto libera o botão.
- Teste nativo da bandeja cria/remove somente seu próprio ícone e janela oculta, sem servidor de entrada. Teste Win32 de Unicode/arrasto usa janela de teste própria e restaura foco/cursor.
- JVM: 14 testes aprovados no teste de interoperabilidade, incluindo pin/hostname, respostas inválidas e cliente Kotlin pareando com servidor Rust WSS, recarga do registro persistido e envio de `Olá, ação! 😀` confirmado. **A entrada desse teste de rede é simulada**, separada do teste real Win32.
- Build release Rust, APK debug, APK de testes e lint executados. Os nove testes Android instrumentados foram compilados, mas não executados nesta etapa.
- Lint sem erros; avisos de versões mais novas e de `X509TrustManager` personalizado. O gerenciador implementa pin do certificado confirmado presencialmente, verifica validade e mantém o verificador de hostname; os testes cobrem rejeição de pin/hostname incorretos.
- Os testes de socket exigiram execução fora do sandbox do Codex: o sandbox recusava bind loopback com erro Windows 10013. Não foi alterado firewall para os testes.

Sem emulador. Builds sequenciais, Rust com dois jobs e testes sequenciais; Gradle com um worker, heap de 1 GiB e compilador Kotlin no mesmo processo. O outro projeto de transcrição não foi modificado.

Artefatos locais em `artifacts/`, excluídos do Git:

| Arquivo | SHA-256 |
| --- | --- |
| `SofaPad-0.3.0-desenvolvimento.apk` | `B336D70585BB2B3A47DDCB6671E001ECBF5E4971A611B772C8AA6BB8DEC8CF10` |
| `SofaPad-agent-0.3.0.exe` | `0F22635DBF9B27839F6D9EF8ECD54F00FA2044E0540B0480CD1E144C44EA4F62` |

O cabeçalho PE do agente release foi conferido: subsistema Windows GUI (2), sem console. O guia foi copiado para `Como-usar-SofaPad-0.3.0.md` junto dos artefatos. Nenhum agente, fixture ou emulador de teste permaneceu em execução.

## Segurança e referências

- [Microsoft DPAPI](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata): proteção por usuário, `CRYPTPROTECT_UI_FORBIDDEN`, sem `CRYPTPROTECT_LOCAL_MACHINE`; liberação do buffer com `LocalFree`.
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore) e [KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec): AES-GCM, IV gerado pelo provedor, autenticação do arquivo e chave fora do backup. Segurança em hardware depende do aparelho.
- [Shell_NotifyIcon](https://learn.microsoft.com/en-us/windows/win32/shell/notification-area) e [Run do usuário](https://learn.microsoft.com/en-us/windows/win32/setupapi/run-and-runonce-registry-keys): Win32 nativo, início normal após login, opção reversível. Nenhuma entrada de inicialização deste PC foi criada durante os testes.
- [Permissões LAN Android](https://developer.android.com/privacy-and-security/local-network-permission): target 36 mantido; manifesto somente `INTERNET`. A migração para target 37 exige `ACCESS_LOCAL_NETWORK` e teste do prompt/recusa.

## Pendências reais

1. Celular físico: executar teste instrumentado do Keystore, parear/conferir diálogo do PC e validar gestos/IME nos dois layouts com rede Wi-Fi real.
2. Habilitar/desabilitar início automático e reiniciar o Windows; reiniciar o celular; trocar IP, desligar Wi-Fi e suspender/retomar o PC. A recarga do registro foi testada em loopback, mas isso não substitui esses cenários.
3. Validar a seleção de rede em PCs com VPN/múltiplos adaptadores e o firewall privado. Atualmente é usado um endereço IPv4 privado da rota selecionada; o status mostra qual.
4. Medir CPU/memória e latência em uso real. Preparar assinatura de release fora do Git e distribuição depois do aceite.

Não foi instalado agente no PC da usuária nem iniciado emulador. Nenhum segredo, certificado privado, APK ou executável deve ser commitado. O fluxo usa código e conferência de identidade; QR não é obrigatório e ficou de fora desta versão.

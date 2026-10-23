# Correção da conexão Windows/Android — agente 0.3.2

O teste no aparelho físico U2 com Android 10 reproduziu a conexão TCP bem-sucedida seguida de encerramento prematuro do TLS. A sonda registrou `WouldBlock` no servidor; o problema também ocorreu por encaminhamento USB, sem depender do trajeto Wi-Fi. Aumentar o timeout da sonda não resolveu o problema.

## Causa e correção

O listener do agente opera em modo não bloqueante para verificar encerramento e mudanças de rede. No Windows, o socket aceito herda propriedades do listener ([Microsoft `accept`](https://learn.microsoft.com/en-us/windows/win32/api/winsock2/nf-winsock2-accept)). Rustls e Tungstenite são usados aqui em modo síncrono: o código encerrava a conexão ao receber `WouldBlock` durante a negociação ou entre pacotes, em vez de aguardar dados.

`secure::serve_tls` agora chama `set_nonblocking(false)` no socket aceito, mantendo os timeouts de três segundos. O listener continua não bloqueante. Não há alteração em pin SHA-256, hostname, autenticação, confirmação presencial ou firewall. O APK 0.3.1 continua compatível e não precisa ser reinstalado para essa correção do agente.

O APK 0.3.2 melhora a informação de estado: a seção passou a se chamar `Conexão com o PC` e informa explicitamente quando nenhum PC foi autorizado. Antes, o título fixo `PC pareado` podia ser interpretado como uma confirmação, mesmo com a tela principal em simulação. A autorização salva e a sessão `PC conectado` continuam estados distintos.

## Evidências e arquivos

- `windows-agent/src/secure.rs`: correção e teste `tls_waits_for_delayed_client_on_accepted_nonblocking_stream`. O teste força um socket aceito não bloqueante, atrasa o cliente TLS e exige HTTP 403 sem autorização. Falhou no código anterior e passou após a correção.
- `android-app/app/src/androidTest/java/br/com/sofapad/NetworkProbeTest.kt`: sonda opt-in no processo do aplicativo, com IP, porta e identidade pública como argumentos. Confere TCP e TLS/WSS; envia uma requisição sem código/token real e exige recusa HTTP 403.
- `windows-agent/examples/diagnose_tls.rs`: sonda de desenvolvimento, com entrada simulada e autorização indisponível. Registra apenas contagens de bytes e categorias de erro, sem buffers, texto, cabeçalhos ou segredos; encerra após duas conexões ou um minuto de espera.
- `scripts/configurar-firewall.ps1` e `scripts/liberar-sofapad.cmd`: preparação opcional de regras por executável/perfil privado/sub-rede local, validada em consulta e `-WhatIf`. Não foi necessária para reproduzir nem corrigir essa falha.
- `scripts/package-dev.ps1`: guia nomeado pela versão do agente, para permitir hotfix Windows sem sobrescrever o guia da versão Android anterior.
- `docs/uso-domestico.md` e `docs/firewall-e-rede.md`: uso das versões e diagnóstico reproduzível.

Comandos de verificação:

```powershell
cargo test --locked --jobs 2 --manifest-path windows-agent/Cargo.toml --lib tls_waits_for_delayed_client_on_accepted_nonblocking_stream -- --test-threads=1
.\scripts\build.ps1 -SkipAndroid
.\android-app\gradlew.bat -p android-app :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain
.\scripts\test-interop.ps1 -Pairing
.\scripts\package-dev.ps1
```

O teste físico via Wi-Fi passou na sonda corrigida e no agente doméstico 0.3.2 final: `OK (1 test)` em ambos, incluindo a recusa de acesso sem autorização e a mesma identidade persistente do agente anterior. Os 32 testes automáticos Rust, formatação, Clippy e build release passaram; os dois testes nativos opt-in não foram repetidos nesta correção de transporte.

A interoperabilidade Kotlin/Rust também passou, com pareamento, recarga da autorização e envio Unicode em entrada simulada. Os 16 testes JVM executaram sem falhas nem skips.

O launcher já instalado no aparelho de testes foi selecionado como padrão e os APKs de desenvolvimento/testes foram instalados sem apagar dados. Em 23/10/2026, o usuário concluiu o pareamento nesse aparelho U2/Android 10 e confirmou `PC conectado` e controle funcionando. A autorização foi feita pelo fluxo normal, com código e confirmação no Windows.

O POCO X6 Pro/Android 16 ainda precisa de nova validação. A matriz completa de gestos, digitação, reconexão e reinício continua pendente; essa confirmação não representa aceite doméstico de todos os cenários.

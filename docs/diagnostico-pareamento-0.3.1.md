# Diagnóstico de pareamento — 0.3.1

O primeiro teste em celular físico, um POCO X6 Pro com Android 16 e HyperOS 3.0.10.0, encontrou o PC na rede, mas o pareamento falhou imediatamente em duas tentativas, sem uma janela de confirmação percebida no Windows. A versão 0.3.0 mostrava uma única mensagem para falhas de conexão, autorização e gravação. Esse relato não permite atribuir a causa ao firewall, ao certificado ou ao código.

## Mudanças

- `PairingClient.kt`: erros distintos para preparação local do TLS, rede, identidade, código recusado, encerramento pelo PC, tempo de confirmação e resposta incompatível. Sem mensagens brutas de exceção, tokens ou códigos de pareamento. Na preparação local, o diagnóstico pode incluir somente o nome da classe de exceção, para investigar diferenças entre Android e JVM.
- `ConnectionPanel.kt`: mostra `Conectando` antes da conexão, `Código aceito` após o upgrade WSS autorizado e `Salvando` depois de receber a autorização. A mensagem de falha informa a etapa correta.
- `Simulator.kt`: erro ao persistir a autorização é identificado separadamente e deixa disponível a opção de esquecer o PC para recuperar o armazenamento protegido.
- `household.rs`: a confirmação do primeiro pareamento usa `MB_SETFOREGROUND`/`MB_TOPMOST` para reduzir o risco de ficar atrás da janela do código. Esse diálogo só é aberto no pareamento com código válido; comandos normais de controle continuam preservando o foco. [Documentação Microsoft](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-messageboxw).
- APK e agente 0.3.1, protocolo de rede compatível com 0.3.0. O empacotamento passa a obter as versões dos metadados de build e do manifesto Cargo, preservando os artefatos antigos.
- Anexos locais da conversa são ignorados pelo Git; a foto enviada não é publicada.

## Verificação

```powershell
.\scripts\build.ps1 -SkipAndroid
.\android-app\gradlew.bat -p android-app :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=1 --console=plain
.\scripts\test-interop.ps1 -Pairing
.\scripts\package-dev.ps1
```

Rust: 31 testes automáticos, formatação, Clippy e build release. Android: APK debug, lint e testes instrumentados compilados; 16 testes JVM no teste integrado com o servidor Rust, incluindo os dois novos casos de erro de rede/encerramento sem token e as verificações de erro TLS/código/resposta inválida. Os testes instrumentados no celular continuam pendentes. Sem emulador; builds com os limites de recursos já definidos.

## Repetir no aparelho

1. Encerre o agente anterior pela bandeja e execute o 0.3.1. A identidade e os aparelhos já salvos permanecem no registro do mesmo usuário do Windows.
2. Instale o APK 0.3.1 por cima do anterior e gere um código novo no PC. Confira o IP em `Status`.
3. Se aparecer `Código aceito`, a conexão segura e o código passaram; procure e confirme a janela no Windows. Pode fechar a janela que mostra o código com OK.
4. Se falhar antes disso, registre o novo texto do erro. Rede, verificação TLS e código recusado exigem ações diferentes. Duas tentativas, isoladamente, não atingem o limite de cinco falhas por minuto.
5. Se o PC autorizar mas o Android não conseguir gravar a autorização, a mensagem será específica dessa gravação.

Esta versão corrige o diagnóstico e melhora a visibilidade da confirmação. **A causa da falha observada e a solução no aparelho ainda precisam ser confirmadas pelo novo teste.** Não há alteração de firewall, confiança global em certificados ou abertura de controle sem autenticação.

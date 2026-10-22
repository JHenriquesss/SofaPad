# Orientações permanentes ao Codex — Couch Remote

Leia `ESPECIFICACAO.md` integralmente antes de implementar. O protótipo `prototipo_layout.html` é referência visual; o aplicativo final deve ser nativo Android Kotlin/Jetpack Compose.

## Prioridades

1. Funcionamento real, simples e testável para usuária não técnica.
2. Respeitar rigorosamente layouts: horizontal botões esquerdo/direito **lado a lado à esquerda**, touchpad **à direita**; vertical touchpad **acima**, botões **lado a lado abaixo**. Engrenagem no canto superior direito.
3. Aba `Digitar` abre teclado nativo, compõe texto localmente, envia no botão `Enviar`, não pressiona Enter automaticamente; inclui atalhos do PC `Alt+Tab`, `Ctrl+C`, `Ctrl+V`, `Ctrl+A`, `Enter`, `Esc` na v1.
4. Rust Windows leve rodando na sessão interativa do usuário após login; Kotlin/Compose Android APK nativo; Wi-Fi LAN, WebSocket, descoberta e reconexão.
5. Pareamento seguro e controle autorizado; nunca abrir comando remoto shell/genérico; nunca fingir que um mock é implementação.

## Como trabalhar

- Implemente em etapas pequenas, com código e testes executáveis. No fim de cada etapa informe arquivos modificados, comandos executados, resultados e bloqueios reais.
- Antes de declarar build OK, execute o build e registre o resultado. Se Android SDK, Windows target ou dispositivos estiverem ausentes, declare explicitamente que o teste local não foi possível.
- Não invente APIs/bibliotecas/versões. Use dependências suportadas e documentadas.
- Não adicionar backend web, cloud, contas, WebView, React ou Electron.
- Não elevar privilégios por padrão, não tentar controlar telas protegidas e não desabilitar segurança Windows.
- Nunca incluir chaves privadas, segredos, tokens reais ou APK assinado em commits públicos.
- Preferir backend de entrada do Windows isolado com testes e validação de mensagens; liberar teclas/botões pressionados em qualquer erro.
- Sempre preservar foco original do Windows ao enviar texto ou atalhos; não fazer janela do agente ganhar foco ao receber comandos.
- Documentação em português brasileiro, comentários só quando úteis.

## Começo recomendado

Leia também `PROMPT_INICIAL_CODEX.md` e faça a Etapa A da especificação. Não comece por instalar o aplicativo no PC da usuária; desenvolvimento e testes primeiro no PC do desenvolvedor.

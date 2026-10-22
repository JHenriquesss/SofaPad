# Couch Remote — Especificação funcional e técnica (v1.0)

**Status:** especificação para implementação. **Plataformas:** Android nativo e Windows 10/11. **Idioma da interface:** português brasileiro.

## 1. Objetivo

Construir um controle remoto para PC por Wi-Fi local, para uma usuária não técnica que deseja controlar o computador deitada, sem abrir navegador nem fazer configurações cotidianas. O Android instala um **APK**; no Windows existe um agente **Rust** leve que inicia **automaticamente após o login**. Depois do pareamento inicial, abrir o app é suficiente para conectar e usar. Não usar nuvem, contas, internet, Bluetooth ou streaming de tela.

A referência visual navegável está em `prototipo_layout.html`. Não copiar seu código HTML para dentro do app; recriar com **Kotlin + Jetpack Compose**. A referência HTML simula, mas **não** controla o PC.

## 2. Decisões fechadas de produto

1. Android **nativo** (Kotlin/Compose), não WebView/PWA.
2. Agente Windows **Rust**. Implementar envio real de entrada via API Windows `SendInput` (a biblioteca `windows` ou `windows-sys` é adequada); Enigo é opcional se comprovadamente atender a todos os cenários. Não depender de Python/Node/servidor externo em produção.
3. Wi-Fi na mesma rede, com conexão persistente WebSocket; reconexão automática. Descoberta mDNS, e fallback por endereço manual ou QR.
4. Primeira autorização via PC (janela de pareamento/QR ou código), depois armazenamento seguro de credenciais; demais acessos automáticos.
5. **Orientação automática** seguindo a rotação física do aparelho; configuração para travar vertical ou horizontal. Respeitar preferências do sistema e permitir troca manual caso Android esteja com rotação bloqueada. Persistir escolha.
6. **Horizontal, destra:** à esquerda dois botões **lado a lado** (esquerdo e direito); **à direita touchpad grande**. **Vertical:** touchpad acima, ocupando a maior área disponível; botões esquerdo/direito **lado a lado abaixo**. Nunca usar o layout lado a lado touchpad/botões no modo vertical. Para canhotos, inverter horizontal (touchpad esquerdo, botões direitos); no vertical, manter touchpad acima e botões abaixo, podendo inverter ordem dos botões se desejado.
7. Ícone pequeno de **engrenagem no canto superior direito** abre configurações; não colocar muitas opções na tela principal.
8. Botão **Digitar** abre o teclado **nativo do Android** num campo de edição com corretor e composição (acentos, emojis compatíveis). **Enviar** transmite texto ao campo focado no Windows e fecha teclado, retornando ao touchpad. **Cancelar** não envia. `Enviar` não deve pressionar Enter automaticamente.
9. Na tela **Digitar**, incluir uma **faixa de atalhos do PC** visível/rolável mesmo com teclado virtual: **Alt+Tab, Ctrl+C, Ctrl+V, Ctrl+A, Enter, Esc**. Os atalhos operam **no PC**, não no campo local Android. Ajustar ao espaço real quando o teclado IME estiver aberto.
10. Tela principal oferece Enter, Esc, reprodução/pausa e volume, com prioridade ao touchpad e aos cliques. Os atalhos secundários podem ficar na tela de digitação.

## 3. Layout e ergonomia

### Horizontal — padrão do protótipo (mão direita)

```text
┌─────────────────────────────────────────────────────┐
│ ● PC conectado   [⌨ Digitar] [↵] [▶/❚❚] [⚙]        │
├────────────────────┬────────────────────────────────┤
│ [ Clique ] [Clique]│                                │
│ [ esquerdo][direito]   TOUCHPAD (área ampla)        │
│                    │                                │
└────────────────────┴────────────────────────────────┘
```

* A área de botões é à **esquerda**, os **dois botões lado a lado**; o touchpad ocupa o restante da largura à direita.
* O design deve permitir acionar cliques com polegar esquerdo e navegar com polegar direito, mesmo em aparelhos de telas compactas.
* Alvos táteis com pelo menos **48 dp** sempre que razoavelmente possível. Não fazer teclas muito estreitas: reorganizar a barra superior em caso de espaço insuficiente.

### Vertical

```text
┌──────────────────────┐
│ ● PC conectado    ⚙  │
│ [⌨ Digitar] [↵]     │
├──────────────────────┤
│                      │
│       TOUCHPAD       │
│                      │
│                      │
├──────────┬───────────┤
│ Clique   │ Clique    │
│ esquerdo │ direito   │
└──────────┴───────────┘
```

* Touchpad grande **acima**; cliques **embaixo** e lado a lado.
* Área de touchpad dimensionada dinamicamente com o espaço; respeitar barras do sistema, recortes e teclado virtual.

### Modos de tela

- **Controle**: layout conforme orientação; controle por toque/gestos.
- **Digitação**: teclado Android nativo + campo multiline + botão Enviar/Cancelar + faixa rolável com atalhos. Caso a altura disponível na horizontal seja muito pequena, comprimir cabeçalho e reposicionar botões; não ocultar Enviar ou impedir acesso aos atalhos.
- **Configurações**: orientação `Automático / Horizontal / Vertical`; destro/canhoto; sensibilidade do cursor; dispositivos pareados; reconectar; informações; opção de segurança de revogação.
- **Não conectado**: instruções simples, estado claro e opção de tentar novamente; sem detalhes técnicos para a usuária.

## 4. Gestos do touchpad — v1

- Um dedo deslizando: movimento **relativo** do cursor do Windows (delta x/y); ajustar sensibilidade; coalescer eventos e limitar taxa de envio se necessário.
- Um toque breve: clique esquerdo. Dois toques rápidos: duplo clique.
- Dois dedos deslizando verticalmente: rolagem, com orientação semelhante ao touchpad usual; evitar confundir com movimento de cursor de um dedo.
- Botões esquerdo/direito: clique com press/release; o botão esquerdo pode permanecer pressionado para arrastar com outro dedo sobre o touchpad.
- Na desconexão, suspensão, troca de tela ou cancelamento do gesto, garantir liberação de botões/teclas pressionados; nunca deixar um botão virtual travado.
- Feedback sutil de botão pressionado, sem animações pesadas. Vibração opcional e desativável (se incluída).

## 5. Teclado e atalhos

O texto é **composto no Android** via IME e enviado somente ao tocar `Enviar`. Não enviar cada letra enquanto digita no aparelho. Isso permite autocorreção, texto com acentuação e revisão.

No Windows, **não** usar Ctrl+V como implementação padrão do envio de texto, pois isso alteraria a área de transferência do usuário. Preferir injeção Unicode com `SendInput` + `KEYEVENTF_UNICODE`, lidando com UTF-16 e pares substitutos quando cabível. Testar Notepad, navegador e apps comuns. Alguns alvos podem ignorar eventos Unicode ou ter entrada protegida — tratar como limite, sem prometer funcionamento universal.

Os botões de atalho enviam eventos de pressionamento e liberação de teclas via Windows `SendInput`, respeitando a ordem correta e com proteção para soltar modificadores caso ocorra erro. Comandos iniciais: `Alt+Tab`, `Ctrl+C`, `Ctrl+V`, `Ctrl+A`, `Enter`, `Esc`, `Backspace` (incluí-lo se houver espaço no painel). `Ctrl+C` e `Ctrl+V` referem-se à seleção/clipboard **do PC**, não ao Android. Ação `Ctrl+V` usa o clipboard já existente no PC; não transporta automaticamente o clipboard do Android.

**Limite do Windows:** a injeção via `SendInput` está sujeita ao UIPI; um agente comum não controla janelas de integridade maior e não deve alegar capacidade de operar UAC/secure desktop. Elevar todo o agente por padrão é desaconselhado. Não burlar telas de segurança.

## 6. Aplicativo Android

- Kotlin, Jetpack Compose, coroutines; HTTP/WebSocket via OkHttp (ou equivalente estável). Sem permissões de Acessibilidade, captura de tela, VPN, overlay ou serviço permanente desnecessário.
- App foreground enquanto usado. Desconectar/pausar com segurança quando sai da tela; religar automaticamente quando o usuário retornar.
- Descoberta por Android `NsdManager` (mDNS/DNS-SD) quando disponível. Fallback `Inserir endereço/IP` e/ou QR do agente Windows. Redes com AP isolation podem impedir conexão apesar de mesmo Wi-Fi: exibir mensagem útil.
- Configurar permissões em função do target SDK real. Para **Android 17 / target SDK 37+**, adotar `ACCESS_LOCAL_NETWORK` com solicitação apropriada; verificar requisitos atuais, evitar solicitar permissões de localização se não forem realmente necessárias. Permissão `INTERNET` para sockets quando pertinente.
- Preferências locais em DataStore; credenciais/segredos protegidos pelo Android Keystore ou mecanismo moderno equivalente.
- Suporte mínimo sugerido: **Android 10 (API 29)**, a confirmar conforme aparelhos de teste. Não assumir dimensões fixas; trabalhar em dp e layout responsivo.
- Gerar APK debug instalável desde o início, e depois APK release assinado (chave **não** deve ir ao Git).

## 7. Agente Windows em Rust

- Suportar Windows 10/11 x64. Crate/workspace separado em `windows-agent/`.
- Agente rodando **na sessão interativa do usuário**, invisível ou com ícone de bandeja (tray) e menu `Status`, `Parear celular`, `Dispositivos`, `Sair`.
- Iniciar automaticamente **após login** (pasta Startup ou tarefa no Agendador com privilégios normais), habilitar/desabilitar opção. Uma instalação pode pedir autorização uma vez; **não** disparar UAC a cada inicialização.
- **Não** usar um Windows Service da Session 0 para gerar comandos de mouse no desktop. Não exigir administrador por padrão.
- Receber comandos autenticados, validar tipo/tamanho/intervalo; limitar frequência; bloquear uso não pareado. Em desconexão liberar botões pressionados.
- Preferir abstração `InputController` com backend Windows, para testar sem fazer injeção real; permitir testes de protocolo/estado em outras plataformas, mas limitar compilação do backend Win32.
- Tratamento de firewall na instalação inicial de forma clara. Não abrir roteador para acesso externo; não configurar UPnP ou porta pública.
- Medir tempo de resposta e memória/CPU na máquina real; **meta**, não promessa: ocioso com CPU próximo de zero e memória abaixo de ~50 MiB quando possível.

## 8. Comunicação, pareamento e segurança

- PC anuncia serviço `_couchremote._tcp.local` (nome a confirmar) na LAN; Android descobre e usa IP/porta atuais. Reconectar após mudança de IP/Wi-Fi, após desbloquear app, e após suspensão do PC.
- WebSocket persistente em TCP. Mensagens JSON pequenas são suficientes para v1 (sem criar protocolo binário prematuramente). Incluir campo `v` de versão, `type`, `seq` onde necessário.
- **Não expor WebSocket aberto sem autenticação**. Parear presencialmente pelo PC: QR/código temporário apresentado em janela Windows. Após pareamento, persistir dispositivo autorizado e possibilidade de revogar.
- Usar comunicação **criptografada e autenticada** dentro da LAN: por exemplo WSS com certificado/chave do agente e chave pública/fingerprint confirmado pelo QR, devidamente verificados pelo cliente Android. Não desativar validação TLS globalmente nem usar `trust all`. Pedir ao Codex solução apoiada em bibliotecas maduras, sem implementar criptografia caseira. Primeiro pareamento requer confirmação no PC.
- Credenciais guardadas com Android Keystore e Windows DPAPI ou armazenamento com proteção equivalente. Rotacionar/revogar token em pareamento novo ou ação do usuário. Não registrar texto digitado, tokens ou conteúdo da área de transferência em logs.
- Dispositivos não pareados podem descobrir o anúncio, mas não enviar comandos. O agente deve recusar conexões não autorizadas e limitar tentativas de pareamento.
- Se criptografia for um bloqueio para o primeiro protótipo de desenvolvimento, implementar inicialmente em `localhost` ou numa LAN de teste explicitamente controlada, **mas não considerar a versão pronta para distribuição até implementar autenticação e transporte seguro**.

## 9. Protocolo proposto (JSON ilustrativo)

Mensagens cliente -> agente, depois de conexão autenticada:

```json
{"v":1,"type":"mouse_move","seq":101,"dx":8.2,"dy":-3.7}
{"v":1,"type":"mouse_button","seq":102,"button":"left","state":"down"}
{"v":1,"type":"mouse_button","seq":103,"button":"left","state":"up"}
{"v":1,"type":"mouse_scroll","seq":104,"vertical":-2.0}
{"v":1,"type":"text_insert","seq":105,"text":"Olá, tudo bem?"}
{"v":1,"type":"shortcut","seq":106,"name":"ALT_TAB"}
{"v":1,"type":"media","seq":107,"name":"PLAY_PAUSE"}
```

Definir `ping/pong`, resposta de `hello`/estado, limites de payload (por exemplo texto até 4 KiB por comando em v1, configurável) e limites de taxa. **Não** aceitar nomes arbitrários de executáveis, comandos shell, paths locais ou pedidos genéricos de execução. Mapear atalhos por allowlist. Rejeitar versões não suportadas e comandos desconhecidos.

## 10. Fluxo de instalação e operação

1. No **seu PC de desenvolvimento**: compilar agente Rust e APK Android; validar protocolo em loopback e testes unitários.
2. Instalar agente e habilitar `Iniciar com Windows`. Na primeira instalação, exibir pareamento e, se preciso, instruir autorização de firewall privado.
3. Instalar APK no aparelho Android. Descobrir PC, ler QR/código de pareamento, confirmar no Windows.
4. Em usos posteriores: abrir app -> descoberta/reconexão -> touchpad pronto. A indicação de conexão deve ser visível mas discreta.
5. No **PC dela**: instalar agente compilado, iniciar novo pareamento para **aquele computador**. Não copiar credenciais/chaves privadas do seu PC.
6. Validar comportamento após reiniciar Windows, mudar IP DHCP, desligar e ligar Wi-Fi, bloquear a tela, suspender/resumir. Funcionar após login; **não** prometer controlar a tela de login antes da sessão do Windows existir.

## 11. Estrutura sugerida do repositório

```text
couch-remote/
├── AGENTS.md
├── ESPECIFICACAO.md
├── prototipo_layout.html
├── android-app/               # Android Studio + Gradle + Compose
├── windows-agent/             # cargo + crates Rust
├── protocol/                  # especificação e fixtures JSON
├── docs/
│   ├── instalacao.md
│   └── testes-manuais.md
└── scripts/                   # build/teste/empacotamento
```

O `prototipo_layout.html` é **documento de referência**, não componente do APK final.

## 12. Etapas para o Codex

### Etapa A — Base compilável
- Criar estrutura do repositório, manifestos e dependências estáveis.
- Crie um agente Rust mínimo com abstração `InputController`, estrutura de servidor local e testes de protocolo.
- Criar APK Compose funcional com **tela de controle**, **digitar**, **configurações**, orientações, destro/canhoto e **modo simulação** sem conexão.
- Garantir build Android e Rust, com documentação de comandos e testes.

### Etapa B — Controle real LAN
- Implementar eventos reais no Windows (movimento, cliques, arrastar, scroll, atalho, mídia, Unicode).
- Conectar Android ao agente por WebSocket, handle de estado, reconexão e indicador.
- Implementar descoberta e fallback manual de IP; testar Android físico e Windows real.

### Etapa C — Uso doméstico seguro
- Pareamento, transporte seguro, credenciais persistentes, revogação e tratamento de permissões Android.
- Configuração de inicialização no Windows e ícone de bandeja.
- APK de instalação, documentação simples para uma usuária comum.

### Etapa D — Robustez
- Testes end-to-end, validação de gestures e IME em retrato/paisagem, reconexão, travas de botão, desempenho, erros.
- Ajustes de UX sem adicionar funcionalidades fora do escopo.

**Não** rotular versão pronta se não existir pareamento seguro ou se o agente não controlar o Windows real. Evitar código placeholder que aparenta conclusão.

## 13. Critérios de aceite — o que significa 'pronto'

- [ ] Android app nativo instalável, abre diretamente no controle em português.
- [ ] Windows agent inicia sozinho após login, em background, sem confirmação diária de administrador.
- [ ] Após 1º pareamento, app conecta sem redigitar IP ou credenciais na rede doméstica suportada.
- [ ] Horizontal: botões E/D lado a lado à esquerda e touchpad à direita; canhoto inverte.
- [ ] Vertical: touchpad acima e botões E/D lado a lado abaixo.
- [ ] Configurações acessíveis pelo ícone pequeno no canto superior direito.
- [ ] Android IME permite autocorreção e acentos; tocar Enviar insere texto no foco do PC e retorna ao mouse, sem Enter automático.
- [ ] Tela Digitar tem Alt+Tab, Ctrl+C, Ctrl+V, Ctrl+A, Enter, Esc, com rolagem se faltar largura.
- [ ] Movimento relativo, clique, duplo clique, rolagem e arrastar funcionam em apps convencionais Windows.
- [ ] Ao desconectar no meio de um drag, o botão é liberado.
- [ ] Rede externa não recebe acesso; dispositivo não autorizado não controla o PC; pareamento revogável.
- [ ] Usuária consegue repetir uso após reiniciar os dois dispositivos sem ajuda técnica.
- [ ] Tests automatizados de protocolo e comportamento crítico; instruções de build e instalação reproduzíveis.

## 14. Fora do escopo v1

Transmissão de imagem/tela, controle pela internet, login remoto do Windows antes da sessão, transferência de arquivos, clipboard sincronizado Android-PC, macros programáveis arbitrárias, automação de jogos, assistente de voz. Atalhos fixos básicos **já estão dentro** do escopo v1.

## 15. Referências oficiais

- Microsoft `SendInput`: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendinput
- Microsoft `KEYBDINPUT`: https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-keybdinput
- Android local network permissions: https://developer.android.com/privacy-and-security/local-network-permission
- Android Jetpack Compose: https://developer.android.com/compose

## 16. Observações técnicas a validar pelo Codex

As implementações exatas de TLS/pinning, de armazenamento de credenciais e de descoberta mDNS podem variar com versões de bibliotecas. O Codex deve consultar APIs oficiais disponíveis, justificar decisões e **testar**, em vez de inventar versões ou assumir recursos que não compilam. Mantenha arquitetura simples, mínima e auditável.

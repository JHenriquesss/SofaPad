# SofaPad — Couch Remote

Controle remoto de um PC Windows pelo celular Android, na mesma rede Wi-Fi, com touchpad, botões de mouse, digitação e atalhos.

**Estado atual: Etapa A — base nativa em simulação.** Há app Android Kotlin/Compose, agente Rust local, protocolo e testes. Esta versão ainda não controla o Windows nem conecta o celular ao PC. O HTML continua sendo apenas referência visual.

O produto final terá um agente para Windows 10/11 x64 na sessão do usuário após login e conexão Wi-Fi local, sem contas, nuvem ou transmissão de tela. Entrada real, pareamento seguro, descoberta e inicialização automática pertencem às próximas etapas.

## Conteúdo

- [Especificação](ESPECIFICACAO.md): requisitos e critérios de aceite da v1.
- [Protótipo visual](prototipo_layout.html): abra o arquivo no navegador para experimentar os layouts e ações simuladas.
- [Plano de conclusão](docs/plano-de-conclusao.md): avaliação inicial, prioridades e verificações por etapa.
- [Build e instalação de desenvolvimento](docs/instalacao.md).
- [Testes manuais](docs/testes-manuais.md).
- [Resultados da Etapa A](docs/validacao-etapa-a.md): 29 testes aprovados e limites da entrega.
- [Contrato de protocolo](protocol/README.md) e fixtures compartilhadas Kotlin/Rust.
- [Orientações de desenvolvimento](AGENTS.md).
- [Prompt da Etapa A](PROMPT_INICIAL_CODEX.md).
- [Pacote original](LEIA_PRIMEIRO.md).

## Compilar e experimentar

O app abre em Controle, inclui Digitar com IME nativo e Configurações persistidas em DataStore. O cursor e os eventos são simulados no próprio celular. O agente simula entrada em um servidor WebSocket restrito a loopback, com validação de mensagens e liberação de estado em erros/desconexão.

No Windows, com Rust/Build Tools, JDK 17 e Android SDK 36 configurados:

```powershell
.\scripts\build.ps1
```

Os comandos individuais, saídas e instruções para instalar o APK debug estão em [instalação](docs/instalacao.md). O protótipo HTML não faz parte do APK.

## Interface nativa em simulação

Controle horizontal e digitação com teclado Android aberto, capturados no emulador:

![Controle horizontal em simulação](docs/imagens/controle-horizontal.png)

![Digitação horizontal com Gboard aberto](docs/imagens/digitar-horizontal.png)

## Regras essenciais

- Horizontal: dois botões lado a lado à esquerda e touchpad à direita; inverter os grupos para canhotos.
- Vertical: touchpad acima e dois botões lado a lado abaixo.
- Digitação pelo teclado Android, envio explícito e sem Enter automático.
- Controle autorizado, transporte criptografado e liberação de teclas e botões em falhas.
- Sem execução remota de shell, elevação de privilégios por padrão ou controle de telas protegidas.

Chaves de assinatura, credenciais de pareamento e artefatos locais não devem ser publicados neste repositório.

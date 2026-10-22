# SofaPad — Couch Remote

Controle remoto de um PC Windows pelo celular Android, na mesma rede Wi-Fi, com touchpad, botões de mouse, digitação e atalhos.

**Estado atual: especificação e protótipo visual.** Ainda não existem app Android, agente Windows, APK ou executável. O HTML simula a interface e não controla o computador.

O aplicativo será nativo em Kotlin/Jetpack Compose. O agente será escrito em Rust para Windows 10/11 x64, executando na sessão do usuário após login. O produto funciona na rede local, sem contas, nuvem ou transmissão de tela.

## Conteúdo

- [Especificação](ESPECIFICACAO.md): requisitos e critérios de aceite da v1.
- [Protótipo visual](prototipo_layout.html): abra o arquivo no navegador para experimentar os layouts e ações simuladas.
- [Plano de conclusão](docs/plano-de-conclusao.md): avaliação inicial, prioridades e verificações por etapa.
- [Orientações de desenvolvimento](AGENTS.md).
- [Prompt da Etapa A](PROMPT_INICIAL_CODEX.md).
- [Pacote original](LEIA_PRIMEIRO.md).

## Próxima entrega

A Etapa A produzirá uma base compilável: app Compose com Controle, Digitar e Configurações em modo simulação; agente Rust local com abstração de entrada; protocolo validado e testes. As etapas seguintes acrescentam controle real, comunicação segura, pareamento, descoberta e robustez.

Não há comandos de build ou instalação do aplicativo nesta revisão, pois os projetos ainda não foram implementados. O protótipo HTML não faz parte do APK final.

## Regras essenciais

- Horizontal: dois botões lado a lado à esquerda e touchpad à direita; inverter os grupos para canhotos.
- Vertical: touchpad acima e dois botões lado a lado abaixo.
- Digitação pelo teclado Android, envio explícito e sem Enter automático.
- Controle autorizado, transporte criptografado e liberação de teclas e botões em falhas.
- Sem execução remota de shell, elevação de privilégios por padrão ou controle de telas protegidas.

Chaves de assinatura, credenciais de pareamento e artefatos locais não devem ser publicados neste repositório.

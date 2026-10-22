# Prompt inicial para enviar ao Codex

Implemente o projeto **Couch Remote** neste repositório. Leia primeiro `AGENTS.md`, depois `ESPECIFICACAO.md`, e utilize `prototipo_layout.html` como referência visual para criar o app **Android nativo com Kotlin + Jetpack Compose**, acompanhado de agente **Rust para Windows**.

Comece **apenas pela Etapa A** da especificação: inicialize os projetos, estrutura compartilhada de protocolo, app Android com modos Controle/Digitar/Configurações e orientação horizontal/vertical, simulação local para visualizar cliques e eventos, e agente Rust compilável com abstração testável de entrada e um primeiro servidor local estruturado. Garanta que o app Android tenha: horizontal com os dois botões E/D lado a lado na esquerda e touchpad à direita; vertical com touchpad acima e dois botões lado a lado embaixo; engrenagem superior; aba Digitar com teclado nativo e atalhos Alt+Tab, Ctrl+C, Ctrl+V, Ctrl+A, Enter e Esc **já na v1**.

**Não declare controle real do Windows se ainda for simulado.** Rode todos os testes/builds possíveis no ambiente e relate o que depende de Windows/Android físico. Não invente bibliotecas ou APIs; consulte documentação quando houver dúvidas. Crie `docs/instalacao.md` e `docs/testes-manuais.md`, e apresente um plano curto para Etapas B, C e D após a Etapa A. Preserve escopo e prioridades da especificação.

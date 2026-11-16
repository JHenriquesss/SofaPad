# Clique do meio e digitação em tempo real — 0.3.7

## Clique do meio na rodinha

**O que mudou.** Dois toques rápidos na rodinha (entre `Clique esquerdo` e `Clique direito`) enviam um clique do meio ao PC, como apertar a roda de um mouse. Deslizar continua rolando, e o layout não mudou.

**Como o toque é reconhecido.**

- Cada toque precisa ser curto (menos de 300 ms) e sem deslizar além da zona de toque do Android.
- O segundo toque precisa começar dentro do tempo de toque duplo do sistema (normalmente 300 ms).
- Um toque só não faz nada, então não há atraso para rolar.
- O toque que segura a roda girando por inércia não conta como primeiro toque.
- O clique é completo (pressiona e solta). Não há como segurar o botão do meio.

**Protocolo.** `mouse_button` aceita `button: "middle"`. Agentes até a 0.3.6 recusam esse valor como mensagem inválida e encerram a sessão; o app então mostra a conexão como recusada até a pessoa reconectar. Por isso, **atualize o agente do PC e o APK juntos**.

## Digitação em tempo real

**O que mudou.** A aba `Digitar` ganhou o botão `Tempo real`, no começo da fileira de atalhos. Desligado, nada muda: compõe no celular e envia com `Enviar`, sem Enter. Ligado, cada edição do campo vai na hora para a janela ativa do PC. Não houve mudança no agente nem no protocolo: o app usa `text_insert` e o atalho `BACKSPACE`, que já existiam.

**Como funciona.**

- O app guarda exatamente o que o PC recebeu nesta sessão, e o campo mostra só isso.
- A cada mudança, compara com o novo conteúdo e manda os Backspace necessários e o texto novo. Por exemplo, a correção automática de "ola" para "olá " vira um Backspace e "á ".
- O Enter do teclado do celular pressiona Enter no PC e o campo recomeça vazio, como num chat.
- Liga apenas com o campo vazio: um rascunho que nunca foi ao PC não pode servir de base para Backspace.

**Edições recusadas.** O campo volta ao que o PC tem e aparece um aviso:

- apagar mais de 32 caracteres de uma vez;
- apagar emoji composto (tom de pele, bandeiras, sequências com ZWJ, variações) ou acento separado, em que o número de Backspace no Windows depende do programa;
- quebra de linha fora do fim do texto, ou texto colado com várias linhas;
- sem conexão com o PC.

**Recomeço com o campo vazio.** Acontece ao sair da aba, ao ir para segundo plano (o foco do PC pode ter mudado) e quando a conexão falha com texto em trânsito. Neste último caso aparece um aviso para conferir no PC, e nada é repetido automaticamente.

**Riscos que continuam.**

- As letras vão para a janela que tiver o foco no PC, inclusive se ela mudar durante a digitação.
- Se o texto no PC mudar por fora (outro clique, autocompletar do site), os Backspace seguintes podem apagar algo diferente do esperado.

## Verificado

- Agente: testes Rust, incluindo as fixtures compartilhadas com `middle`, além de clippy e fmt.
- Android: testes JVM do plano de edição em `LiveTypingTest` e do Simulator, lint, e os 26 testes instrumentados no emulador Pixel_6 (Android 13), com 1 opcional pulado:
  - duplo toque na rodinha clica;
  - toques espaçados, dedo descansando e toque que para a inércia não clicam;
  - tempo real espelha cada edição;
  - tempo real recusa apagar emoji composto e quebrar linha no meio;
  - Enter no fim pressiona Enter;
  - tempo real não liga sobre um rascunho.
- Durante a bateria, o emulador travou ("System has crashed") e foi reiniciado a frio. O `AppFlowTest`, que espera o teclado real, falhou antes disso também no código do `main` e passou depois do reinício.

## Não verificado

- Clique do meio e digitação em tempo real num PC Windows real a partir do celular físico.
- Comportamento do Gboard real com correção automática e digitação por deslize. Os testes inserem texto direto no campo, sem um teclado de verdade.

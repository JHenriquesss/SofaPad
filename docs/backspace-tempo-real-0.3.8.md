# Backspace no tempo real — APK 0.3.8

Só o app mudou. O agente do PC continua na 0.3.7.

**Problema relatado.** No navegador, a pessoa digitou `y` com o tempo real ligado e tocou na sugestão "YouTube". O PC ficou com `yYouTube`, e o `y` não podia ser apagado pelo celular.

**Causa.** O navegador completou o `y` sozinho e deixou selecionado o trecho completado (`y[outube.com]`). Para trocar o `y` minúsculo pela sugestão, o app mandou um Backspace e depois "YouTube". Esse Backspace apagou só o trecho selecionado, e o `y` ficou. O celular não vê o que o PC completa sozinho. Por segurança, o campo só deixava apagar o que o próprio celular tinha enviado.

**O que mudou.**

- Com o campo vazio, cada Backspace do teclado do celular vai direto ao PC. Serve para apagar o que já estava escrito lá ou o que o navegador completou.
  - O Gboard manda esse Backspace como tecla quando não há texto antes do cursor; o app o repassa ao PC.
  - Às vezes o primeiro toque logo depois de esvaziar o campo se perde (o Gboard ainda acha que há texto). Basta tocar de novo.
- O botão `⌫` dos atalhos:
  - com texto no campo, apaga a última letra no campo e no PC juntos (antes deixava os dois diferentes);
  - com o campo vazio, apaga direto no PC.
- `Ctrl+A`, `Ctrl+V`, `Esc`, `Alt+Tab` e `Enter` fazem o campo recomeçar vazio, porque o texto do PC mudou. `Ctrl+C` mantém o campo.

**Limite que continua.** Quando o PC completa ou muda o texto sozinho, uma correção feita pelo teclado do celular (sugestão ou correção automática que troca letras já enviadas) pode apagar a coisa errada. A saída é apagar o resto com o Backspace no campo vazio.

**Verificado.**

- Testes JVM do Simulator: Backspace com o campo vazio e atalhos mantendo campo e PC iguais.
- 27 testes instrumentados no emulador Pixel_6 (Android 13), com 1 opcional pulado, incluindo a tecla Backspace com o campo vazio.
- `scripts/build.ps1` e lint aprovados.
- No emulador, com o Gboard real: cada toque no Backspace do teclado com o campo vazio chegou ao app como tecla e virou Backspace no PC simulado.

**Não verificado.** O toque em sugestão do Gboard e o completar do navegador num PC real. O Gboard do emulador não mostra sugestões.

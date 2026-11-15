# Início automático com o Windows e rodinha de rolagem — 0.3.6

Agente Windows e APK mudam. O protocolo não mudou: versões anteriores continuam compatíveis entre si.

## Agente: inicia sozinho depois do login

**Problema relatado.** O agente não iniciava com o Windows; era preciso abri-lo antes de cada uso, e esquecer significava voltar ao PC. A opção existia no menu da bandeja (`Iniciar com Windows`), mas vinha desligada e nunca tinha sido escolhida neste PC: não havia entrada do SofaPad em `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`.

**O que mudou.**

- Iniciar após o login passa a ser o padrão. Ao abrir o agente (build release), ele grava a própria entrada de inicialização do usuário atual, sem administrador e sem UAC.
- A entrada acompanha o executável em uso. Como o nome do arquivo traz a versão (`SofaPad-agent-0.3.6.exe`), a entrada antiga apontaria para um arquivo que deixou de existir depois de uma atualização; agora, abrir a versão nova uma vez corrige o caminho.
- O menu da bandeja tem um único item `Iniciar com o Windows`, com marca de seleção. Desmarcar grava a escolha em `%LOCALAPPDATA%\SofaPad\inicio-automatico-desativado`, e o agente não volta a se registrar sozinho.
- Logo após o login a barra de tarefas pode ainda não aceitar ícones. Antes, essa falha encerrava o agente com uma mensagem de erro; agora ele insiste por até 30 segundos.
- Builds de desenvolvimento (`cargo run`) nunca se registram, para não apontar a inicialização para `target/debug`.

Quem tinha escolhido `Desativar início com Windows` numa versão anterior precisa desmarcar a opção de novo uma vez: aquela versão não guardava a escolha.

O gerenciador de inicialização do próprio Windows (`Configurações > Aplicativos > Inicialização`) continua valendo: se o SofaPad for desligado ali, o agente não altera essa decisão.

## App: rodinha entre os botões

**Problema relatado.** Rolar com dois dedos no touchpad é pouco ergonômico para uso prolongado.

**O que mudou.** Entre `Clique esquerdo` e `Clique direito` há uma rodinha, na ordem de um mouse: esquerdo, roda, direito. A disposição dos grupos não mudou (horizontal: botões à esquerda e touchpad à direita; vertical: touchpad acima e botões abaixo).

- Deslizar o dedo sobre a roda rola a página no sentido de um mouse: dedo para baixo, página desce. É o mesmo sentido da rolagem com dois dedos, que continua funcionando.
- Um degrau da roda do mouse a cada 18 dp de dedo, com envio fracionado: a rolagem acompanha o dedo em vez de pular de três em três linhas nos programas que aceitam rolagem suave.
- Soltar o dedo em movimento deixa a roda girando por inércia, desacelerando sozinha. Tocar na roda a segura. Parar o dedo antes de soltar não gera inércia.
- Pousar o dedo sem deslizar não rola.
- As ranhuras desenhadas acompanham o giro e o celular dá um toque leve de vibração a cada degrau (respeita a configuração de vibração do Android).
- Na vertical a faixa dos botões passou de 80 para 96 dp de altura, para a roda ter curso.
- Se a conexão cair, a inércia para; nenhum comando é guardado para depois.

A rodinha usa o comando `mouse_scroll` que já existia; o agente 0.3.4 também a entende.

## Verificações

- Rust: 38 testes aprovados, formatação, Clippy e build release. Novos: a entrada de inicialização acompanha o executável em uso, o início é o padrão até ser desmarcado, e a escolha de desmarcar sobrevive às aberturas seguintes (os testes usam valores próprios no registro, não a entrada real).
- Android: APK debug, testes JVM e lint aprovados.
- Instrumentados no emulador Pixel 6 (Android 13): 20 aprovados e 1 opcional ignorado. Novo `ScrollWheelTest` (dedo para baixo rola para baixo, dedo para cima rola para cima, dedo pousado ou parado não rola nem deixa inércia, a inércia continua até a roda ser tocada e termina sozinha) e `LayoutTest` conferindo a rodinha entre os botões nas duas orientações. `landscapeActionsStayAboveTheRealKeyboard` falhou na primeira execução com o emulador recém-ligado (o teclado não abriu em 10 s) e passou ao ser repetido; a tela Digitar não foi alterada.
- No PC de desenvolvimento: abrir `SofaPad-agent-0.3.6.exe` gravou `"…\SofaPad-agent-0.3.6.exe" --household` em `HKCU\…\Run` e o agente ficou escutando na porta 49375.

**Não verificado:** reinício real do Windows (login → agente na bandeja), a rodinha num celular físico (nenhum estava conectado por adb) e a sensação da vibração por degrau, que o emulador não reproduz.

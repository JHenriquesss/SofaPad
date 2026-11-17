# Avisos de conexão, versão do agente e ícone — 0.3.9

Agente do PC e app mudaram. Atualize os dois.

## Problema relatado

Um testador da 0.3.7 passou a ter o cursor aos saltos e o teclado sem funcionar; só a rolagem funcionava. O app não mostrava erro nenhum, e não havia como saber qual versão do agente estava aberta no PC.

## O que foi encontrado

- **Abrir o agente novo com outro já aberto não fazia nada.** O arquivo novo fechava em silêncio, e a versão antiga continuava rodando. A pessoa achava que tinha atualizado. No PC de desenvolvimento, o agente 0.3.7 estava aberto e o início automático ainda apontava para o arquivo da 0.3.6.
- **O app reconectava em silêncio.** Do app 0.3.6 em diante, quando o PC recusa um comando, o app reconecta sozinho. Se o PC recusa sempre (agente antigo, antivírus, tecla presa), o app reconecta sem parar, os movimentos em trânsito se perdem e o cursor anda aos saltos.
- **Causa no PC do testador, relatada depois:** ele abriu o executável novo sem escolher `Sair` no antigo. O agente 0.3.4 continuou rodando, e o app novo quebrava ao usar a rodinha, sem reconectar.

## Agente do PC

- **Ícone:** um sofá azul no arquivo, na bandeja e nas janelas do SofaPad, e também como ícone do app Android e no topo do app. A fonte está em `assets/sofapad-icon.svg`; `scripts/make-icon.py` gera o `.ico`.
- **Versão visível:**
  - ao passar o mouse sobre o ícone da bandeja (`SofaPad 0.3.9 — controle pelo celular`);
  - no título de todas as janelas;
  - em `Status`, junto com o arquivo em uso e se o início automático aponta para ele;
  - nas propriedades do arquivo no Windows.
- **Abrir o arquivo novo troca a versão.** Ele pede para o SofaPad aberto encerrar (como `Sair`), assume o lugar e regrava o início automático. Se o antigo não fechar em 5 segundos, aparece um aviso pedindo `Sair` manual.
- **`hello` informa a versão** no campo `agent`.

## App

- **Versão do agente no topo:** `PC conectado · agente 0.3.9`. Com agente sem versão (anterior à 0.3.9) ou mais antigo que o necessário, aparece um aviso em vermelho pedindo para abrir no PC o arquivo novo. `Configurações` também mostra a versão.
- **Motivo da falha em vermelho** abaixo do estado da conexão (e na linha de estado do modo compacto de digitação). Some depois de 10 segundos de conexão estável. Motivos:
  - o PC recusou um comando (antivírus, janela de administrador, tecla modificadora presa);
  - comandos chegaram em rajada (limite de taxa do agente);
  - o agente não entendeu um comando (agente mais antigo que o app);
  - o PC demorou mais de 2,5 s para confirmar;
  - PC em uso por outro celular ou em pareamento;
  - celular não reconhecido;
  - PC inalcançável após três tentativas.
- **Fim do loop de reconexão:** três recusas do PC em 20 segundos param a reconexão automática. O estado passa a `Conexão interrompida`, com o botão `Tentar novamente`.
- Comando que o agente não entende também leva a `Conexão interrompida`, em vez de `Confira identidade e código no PC`, que apontava a causa errada.
- `Configurações` lista os últimos 20 avisos com hora, em `Problemas recentes da conexão`.

## Limite

O agente responde só `INPUT_ERROR`, sem dizer se foi tecla presa ou bloqueio do Windows. Por isso o aviso lista as três causas.

## Verificado

- Testes Rust, clippy e fmt; o executável release traz o ícone e a versão 0.3.9 nas propriedades.
- Testes JVM com servidor WSS local:
  - o motivo da recusa chega à interface;
  - a terceira recusa para a reconexão e `Tentar novamente` recomeça;
  - `hello` sem versão é tratado como agente antigo.
- `scripts/build.ps1`, lint e testes instrumentados no emulador Pixel_6.
- No PC de desenvolvimento: ao abrir o `SofaPad-agent-0.3.9.exe` com o 0.3.7 aberto, em 1 segundo o 0.3.7 fechou, o 0.3.9 ficou escutando na porta 49375 e o início automático passou do arquivo da 0.3.6 para o da 0.3.9.
- Ícone conferido no arquivo `.exe`, no ícone do app Android (tela de informações do app) e no topo do app, no emulador.

## Não verificado

- A correção no PC do testador (ele ainda precisa abrir o agente 0.3.9).
- Os avisos numa recusa real do Windows e o aviso de agente antigo num celular físico.
- A aparência do ícone na bandeja, o texto ao passar o mouse e a janela `Status` (não foram abertos na tela durante a verificação).

# Como usar o SofaPad

Agente Windows 0.3.10 e APK Android 0.3.10 de desenvolvimento: o fluxo está implementado e o teste em celular físico está em andamento; o aceite doméstico ainda está pendente.

## Preparar o PC

1. Coloque `SofaPad-agent-0.3.10.exe` numa pasta permanente do seu usuário, por exemplo `Documentos\SofaPad`. Não precisa executar como administrador.
2. Abra o programa. O ícone do SofaPad (um sofá azul) aparece perto do relógio; pode estar dentro da seta de ícones ocultos. Passe o mouse sobre ele para ver a versão. O build release não abre console.
   - **Para atualizar, basta abrir o arquivo novo:** ele fecha o SofaPad que estava aberto, assume o lugar e passa a iniciar com o Windows. Não é preciso escolher `Sair` antes.
   - `Status` mostra a versão, o arquivo em uso e se o início automático aponta para ele.
3. Se o Windows perguntar sobre firewall, permita somente na sua rede **privada/doméstica**. Não abra portas no roteador. PC e celular precisam estar na mesma rede; Wi-Fi de convidados pode impedir a conexão.
4. Pelo ícone, abra `Status` para ver o endereço do PC. Depois escolha `Parear celular`.

## Autorizar o celular uma vez

1. Instale o APK de desenvolvimento no Android 10 ou superior. Se necessário, permita a instalação apenas pelo aplicativo usado para abrir esse APK. Não desative o Play Protect.
2. No PC, clique no sofá do SofaPad perto do relógio e escolha `Parear celular`. Deixe a janela aberta.
3. No celular, toque na engrenagem e em `Parear PC`. O pareamento tem três passos, um por tela:
   1. **Achar o PC.** Toque no seu PC na lista. Se ele não aparecer, toque em `Não encontrou? Digitar o endereço` e digite o IP mostrado em `Status` no PC.
   2. **Conferir.** O celular mostra um código de conferência de 16 caracteres em quatro grupos, como `AB12 CD34 EF56 7890`. Compare com o da janela do PC. Se for igual, toque em `É igual`. Se for diferente, toque em `É diferente` e não continue: o celular pode estar falando com outro aparelho.
   3. **Código.** Digite o código de uso único de 8 caracteres da janela do PC, dê um nome ao celular e toque em `Parear`.
4. No Windows, confirme o nome desse celular em até um minuto. Só confirme se você iniciou esse pedido. O código vale cinco minutos e pode ser usado uma única vez; se falhar, escolha `Parear celular` novamente.

O código de conferência são os 16 primeiros caracteres da identidade do PC. A identidade completa de 64 caracteres continua disponível em `Ver identidade completa` no celular e no fim da janela do PC. Ela não é segredo; o segredo é o código de uso único.

Fechar a janela que mostra o código mantém o pareamento disponível até a expiração. `Cancelar pareamento` no menu da bandeja anula o código e pedidos ainda não confirmados.

Ao abrir `Parear celular`, o agente reserva a conexão para o novo pareamento até o uso, cancelamento ou expiração do código. Isso impede que outro celular já autorizado bloqueie o pedido ao reconectar automaticamente. Depois, mantenha o SofaPad aberto em apenas um celular por vez para controlar o PC; feche o app no aparelho anterior ao trocar de controlador. Desde a versão 0.3.4, um segundo celular recebe a mensagem de PC em uso na hora, e o mesmo celular volta a controlar imediatamente depois de uma queda de Wi-Fi.

## Usar depois

Abra o SofaPad no celular. A autorização é recuperada e o app tenta reconectar ao PC. A descoberta pode atualizar o endereço se o roteador mudar o IP. Se o app foi colocado em simulação nesta sessão, use `Conectar ao PC` nas configurações.

O PC precisa estar ligado e com o usuário já conectado ao Windows. Desde a versão 0.3.6, o agente passa a iniciar sozinho depois do login assim que é aberto uma vez: não é preciso abri-lo todo dia. A opção `Iniciar com o Windows` da bandeja aparece marcada; clique nela para desmarcar se preferir abrir manualmente (a escolha é lembrada). Vale só para esse usuário e não pede administrador. Ao trocar de versão ou mover o executável, abra o novo uma vez: o início automático passa a apontar para ele. Se o agente não aparecer depois de reiniciar, confira em `Configurações do Windows > Aplicativos > Inicialização` se o SofaPad não foi desligado ali.

Deslize no touchpad para mover o cursor. Gestos do touchpad, como no notebook:

- Toque com um dedo: clique esquerdo. O clique sai um instante depois (um quarto de segundo), o tempo de saber se vem um segundo toque.
- Toque, e logo em seguida toque de novo e segure: o botão esquerdo fica pressionado enquanto o dedo desliza, para arrastar janelas, arquivos ou selecionar texto. Solte o dedo para soltar.
- Dois toques rápidos: clique duplo.
- Toque com dois dedos: clique direito.
- Deslize com dois dedos: rolar.

Entre os botões `Clique esquerdo` e `Clique direito` há uma **rodinha**, como a de um mouse: deslize o dedo sobre ela para cima ou para baixo para rolar a página. Um deslize rápido deixa a roda girando por inércia; toque nela para parar. **Dois toques rápidos** na rodinha fazem o **clique do meio**, como apertar a roda de um mouse: no navegador, abre um link em nova aba ou fecha a aba sob o cursor. O celular dá um leve toque de vibração a cada degrau, se a vibração de toque estiver ligada no Android.

Também dá para segurar o botão esquerdo da tela e mover outro dedo no touchpad para arrastar. `Digitar` abre o teclado do celular: revise o texto e toque em `Enviar`. Não é enviado Enter automaticamente. Os atalhos agem no PC.

Para ver o texto aparecer no PC enquanto digita, toque em **`Tempo real`** (no começo da fileira de atalhos, com o campo vazio). Cada letra vai direto para a janela ativa do PC, e o Enter do teclado do celular pressiona Enter. Antes de digitar, confira se o cursor do PC está no campo certo: se outra janela pegar o foco, as letras vão para ela. Para proteger o texto do PC, algumas edições são recusadas (o campo volta ao que o PC tem e aparece um aviso): apagar mais de 32 letras de uma vez, apagar emoji composto (como 👍🏽 ou bandeiras) e quebrar linha no meio do texto. Nesses casos, apague no próprio PC. **Com o campo vazio, o Backspace do teclado (ou o `⌫` dos atalhos) apaga direto no PC**: use-o para apagar o que já estava escrito lá ou o que o navegador completou sozinho. Às vezes o primeiro toque logo depois de esvaziar o campo não chega; toque de novo. Depois de `Ctrl+A`, `Ctrl+V`, `Esc`, `Alt+Tab` ou `Enter`, o campo recomeça vazio, porque o texto do PC mudou. Se a conexão cair ou o app for para segundo plano, o campo recomeça vazio; confira no PC o que chegou. `Tempo real` vem desligado e fica como você deixou enquanto o app estiver aberto.

## Bloquear um celular ou trocar de PC

No PC, abra a bandeja e escolha `Dispositivos > Revogar: nome do celular`. Confirme. A conexão atual é encerrada e esse celular precisa de outro pareamento. O agente admite até dez autorizações; novos pareamentos emitem tokens novos, e os anteriores continuam na lista até serem revogados.

No celular, `Esquecer este PC` apaga a autorização local. Para um aparelho perdido, a revogação deve ser feita no PC. O app guarda um PC por vez; o Windows pode autorizar vários celulares, com um controlador conectado por vez.

Em outro computador, faça um novo pareamento. Não copie a pasta privada de credenciais nem a chave de assinatura. Para remover o agente, desmarque `Iniciar com o Windows`, escolha `Sair` e apague o executável. O registro privado fica em `%LOCALAPPDATA%\SofaPad\credentials.dpapi`; apagá-lo elimina todas as autorizações e a identidade, exigindo parear novamente.

## Se não conectar

Confira se o agente está aberto, os dois aparelhos estão na mesma rede e o firewall permite o programa na rede privada. Confira `Status` no PC e `Tentar novamente` no celular. Se a identidade mudou por reinstalação/perda do registro privado, esqueça o PC no celular e repita o pareamento presencial.

Com o PC conectado, o topo do app mostra a versão do agente (`PC conectado · agente 0.3.9`). Se o agente do PC for mais antigo do que o app precisa, aparece um aviso em vermelho pedindo para abrir no PC o arquivo novo.

Quando algo falha, o app mostra o motivo em vermelho logo abaixo do estado da conexão, no topo da tela. Os últimos avisos, com hora, ficam em `Configurações`, em `Problemas recentes da conexão`; uma captura dessa lista ajuda a relatar um defeito. Os avisos mais comuns:

- **O PC recusou um comando:** o Windows não aceitou a tecla ou o clique. Confira se o antivírus bloqueou o SofaPad, se há uma janela de administrador em primeiro plano e se alguma tecla Ctrl, Shift, Alt ou Windows está presa no PC. Depois de três recusas em 20 segundos o app para de reconectar e mostra `Tentar novamente`.
- **O SofaPad do PC não entendeu um comando:** o agente do PC é mais antigo que o app. Atualize o agente.
- **O PC demorou a responder** ou **os comandos chegaram em rajada:** Wi-Fi fraco ou PC ocupado. Aproxime-se do roteador.
- **O PC está em uso por outro celular:** só um celular controla por vez.

O script opcional `scripts/liberar-sofapad.cmd` solicita uma autorização do Windows e prepara regras apenas para o SofaPad na rede privada local. Consulte [firewall e diagnóstico de rede](firewall-e-rede.md). Se o Firewall do Windows já estiver desligado, adicionar a regra não explica nem resolve por si só uma falha de acesso.

Na versão 0.3.1, `Conectando ao PC com segurança` aparece antes da autorização; `Código aceito` só aparece depois que o PC aceitou o código no canal seguro. Nesse ponto, procure a janela de confirmação no Windows. Você pode fechar a janela que mostra o código com OK para deixar a confirmação visível. O agente solicita que a confirmação apareça na frente das outras janelas.

Os erros agora distinguem rede, verificação de identidade, código recusado, encerramento pelo PC e gravação no Android. Se aparecer erro de rede, confira o IP em `Status` e a permissão do executável no firewall da rede privada; isso não exige desligar o firewall. Se a mensagem for de código recusado, gere outro código; após várias falhas, aguarde um minuto. Se o PC autorizar e o Android não conseguir salvar, a mensagem indicará essa etapa e oferecerá `Esquecer este PC` para tentar novamente.

Desde a versão 0.3.3, a conexão segura tem limite de oito segundos; depois de o código ser aceito, há um prazo separado de um minuto para confirmar no Windows.

VPNs e computadores com várias interfaces podem selecionar outro endereço; desligue a VPN para o teste e confira o IP em `Status`. Não há controle de UAC, tela de login anterior à sessão nem aplicativos elevados. O agente funciona com os privilégios normais do usuário.

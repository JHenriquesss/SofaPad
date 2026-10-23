# Como usar o SofaPad

Agente Windows e APK Android 0.3.3 de desenvolvimento: o fluxo está implementado e o teste em celular físico está em andamento; o aceite doméstico ainda está pendente.

## Preparar o PC

1. Coloque `SofaPad-agent-0.3.3.exe` numa pasta permanente do seu usuário, por exemplo `Documentos\SofaPad`. Não precisa executar como administrador.
2. Abra o programa. O ícone SofaPad aparece perto do relógio; pode estar dentro da seta de ícones ocultos. O build release não abre console.
3. Se o Windows perguntar sobre firewall, permita somente na sua rede **privada/doméstica**. Não abra portas no roteador. PC e celular precisam estar na mesma rede; Wi-Fi de convidados pode impedir a conexão.
4. Pelo ícone, abra `Status` para ver o endereço do PC. Depois escolha `Parear celular`.

## Autorizar o celular uma vez

1. Instale o APK de desenvolvimento no Android 10 ou superior. Se necessário, permita a instalação apenas pelo aplicativo usado para abrir esse APK. Não desative o Play Protect.
2. No SofaPad, toque na engrenagem e vá a `Parear PC`.
3. Toque em `Buscar PCs na rede` e escolha o seu PC. Se não aparecer, digite o IP e a porta mostrados na janela do PC.
4. Confira **todos os 64 caracteres** da identidade com a janela no próprio PC e marque a confirmação. A identidade sugerida pela descoberta precisa ser conferida. Se não coincidir, não continue. Também pode digitá-la manualmente.
5. Digite o código de 8 caracteres da janela do PC, dê um nome ao celular e toque em `Parear e salvar autorização`. O código fica visível para revisão e é apagado do campo após o sucesso.
6. No Windows, confirme o nome desse celular em até um minuto. Só confirme se você iniciou esse pedido. O código vale cinco minutos e pode ser usado uma única vez; se falhar, escolha `Parear celular` novamente.

Fechar a janela que mostra o código mantém o pareamento disponível até a expiração. `Cancelar pareamento` no menu da bandeja anula o código e pedidos ainda não confirmados.

Ao abrir `Parear celular`, o agente reserva a conexão para o novo pareamento até o uso, cancelamento ou expiração do código. Isso impede que outro celular já autorizado bloqueie o pedido ao reconectar automaticamente. Depois, mantenha o SofaPad aberto em apenas um celular por vez para controlar o PC; feche o app no aparelho anterior ao trocar de controlador.

## Usar depois

Abra o SofaPad no celular. A autorização é recuperada e o app tenta reconectar ao PC. A descoberta pode atualizar o endereço se o roteador mudar o IP. Se o app foi colocado em simulação nesta sessão, use `Conectar ao PC salvo` nas configurações.

O PC precisa estar ligado e com o usuário já conectado ao Windows. Para não abrir o programa manualmente todo dia, escolha `Iniciar com Windows` na bandeja. A opção vale só para esse usuário e não pede administrador. `Desativar início com Windows` desfaz essa escolha; mantenha o executável na mesma pasta.

Deslize no touchpad para mover o cursor. Segure o botão esquerdo e mova outro dedo para arrastar. `Digitar` abre o teclado do celular: revise o texto e toque em `Enviar`. Não é enviado Enter automaticamente. Os atalhos agem no PC.

## Bloquear um celular ou trocar de PC

No PC, abra a bandeja e escolha `Dispositivos > Revogar: nome do celular`. Confirme. A conexão atual é encerrada e esse celular precisa de outro pareamento. O agente admite até dez autorizações; novos pareamentos emitem tokens novos, e os anteriores continuam na lista até serem revogados.

No celular, `Esquecer este PC` apaga a autorização local. Para um aparelho perdido, a revogação deve ser feita no PC. O app guarda um PC por vez; o Windows pode autorizar vários celulares, com um controlador conectado por vez.

Em outro computador, faça um novo pareamento. Não copie a pasta privada de credenciais nem a chave de assinatura. Para remover o agente, desative o início com Windows, escolha `Sair` e apague o executável. O registro privado fica em `%LOCALAPPDATA%\SofaPad\credentials.dpapi`; apagá-lo elimina todas as autorizações e a identidade, exigindo parear novamente.

## Se não conectar

Confira se o agente está aberto, os dois aparelhos estão na mesma rede e o firewall permite o programa na rede privada. Confira `Status` no PC e `Tentar novamente` no celular. Se a identidade mudou por reinstalação/perda do registro privado, esqueça o PC no celular e repita o pareamento presencial.

O script opcional `scripts/liberar-sofapad.cmd` solicita uma autorização do Windows e prepara regras apenas para o SofaPad na rede privada local. Consulte [firewall e diagnóstico de rede](firewall-e-rede.md). Se o Firewall do Windows já estiver desligado, adicionar a regra não explica nem resolve por si só uma falha de acesso.

Na versão 0.3.1, `Conectando ao PC com segurança` aparece antes da autorização; `Código aceito` só aparece depois que o PC aceitou o código no canal seguro. Nesse ponto, procure a janela de confirmação no Windows. Você pode fechar a janela que mostra o código com OK para deixar a confirmação visível. O agente solicita que a confirmação apareça na frente das outras janelas.

Os erros agora distinguem rede, verificação de identidade, código recusado, encerramento pelo PC e gravação no Android. Se aparecer erro de rede, confira o IP em `Status` e a permissão do executável no firewall da rede privada; isso não exige desligar o firewall. Se a mensagem for de código recusado, gere outro código; após várias falhas, aguarde um minuto. Se o PC autorizar e o Android não conseguir salvar, a mensagem indicará essa etapa e oferecerá `Esquecer este PC` para tentar novamente.

Desde a versão 0.3.3, a conexão segura tem limite de oito segundos; depois de o código ser aceito, há um prazo separado de um minuto para confirmar no Windows.

VPNs e computadores com várias interfaces podem selecionar outro endereço; desligue a VPN para o teste e confira o IP em `Status`. Não há controle de UAC, tela de login anterior à sessão nem aplicativos elevados. O agente funciona com os privilégios normais do usuário.

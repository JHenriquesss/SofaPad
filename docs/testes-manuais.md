# Testes manuais

Registrar aparelho, versão Android, dimensões/orientação, teclado utilizado e resultado. Simulação não valida entrada real no Windows. Os testes abaixo são uma lista de execução, não uma declaração de aprovação.

## Etapa A — APK em simulação

- [ ] Abrir o app: indicação de simulação visível; não mostrar PC conectado de verdade.
- [ ] Vertical: touchpad acima, dois botões lado a lado abaixo; engrenagem superior direita.
- [ ] Horizontal destra: botões lado a lado à esquerda e touchpad à direita.
- [ ] Horizontal canhota: grupos invertidos; botões continuam lado a lado.
- [ ] Automática segue o sistema; testar também com rotação do sistema bloqueada e escolha manual de orientação.
- [ ] Preferências de orientação, mão e sensibilidade sobrevivem ao fechar e reabrir.
- [ ] Deslizar um dedo move o cursor da prévia; dois dedos rolam sem mover o cursor.
- [ ] Toque produz clique; dois toques produzem dois cliques; não manter botão pressionado.
- [ ] Segurar o botão esquerdo e mover outro dedo no touchpad: arrastar na simulação; soltar o dedo do botão encerra o arrasto mesmo se outro dedo continuar no touchpad.
- [ ] Cancelar toque, abrir configurações, abrir Digitar, sair do app ou girar o dispositivo libera os botões.
- [ ] Digitar abre IME nativo; compor `Olá, ação! 😀` usando autocorreção/acentos reais.
- [ ] Enviar mostra exatamente esse texto na prévia, sem nova linha adicional; fecha teclado e volta ao controle.
- [ ] Cancelar não envia texto. Atalhos Ctrl+A/C/V operam no simulador e preservam o campo Android.
- [ ] Alt+Tab, Ctrl+C, Ctrl+V, Ctrl+A, Enter e Esc acessíveis por rolagem, com o teclado aberto, em ambas as orientações.
- [ ] Texto com mais de 4096 bytes UTF-8 desabilita Enviar e mostra erro; reduzir texto permite enviar.
- [ ] Na horizontal compacta, teclado aberto não encobre Enviar/Cancelar nem impede acessar atalhos.
- [ ] Enter, Esc, volume e reprodução/pausa atualizam feedback da simulação.
- [ ] Sem pedido de permissão de acessibilidade, rede, localização, captura ou overlay.

## Etapas B/C — integração real, pendente

Executar no PC e celular do desenvolvedor, após as implementações correspondentes:

- [ ] Mover, clicar, rolar, dar duplo clique e arrastar em Bloco de Notas e navegador reais.
- [ ] Inserir acentos e emojis compatíveis sem alterar clipboard nem enviar Enter.
- [ ] Atalhos fixos pressionam/soltam modificadores na ordem correta e preservam foco original.
- [ ] Janela do agente não ganha foco ao receber entrada.
- [ ] Desconectar durante arrasto, sair do app e suspender PC liberam todos os botões/teclas.
- [ ] Dispositivo sem pareamento não envia entrada; confirmação presencial no PC é necessária.
- [ ] Certificado incorreto, token inválido e dispositivo revogado são recusados.
- [ ] Credenciais não aparecem em arquivos públicos, URLs ou logs; texto enviado não é registrado em logs.
- [ ] Primeiro pareamento pode exigir passos; usos posteriores reconectam sem credenciais/IP manual.
- [ ] Descoberta/fallback funcionam após mudança de IP; AP isolation gera orientação útil.
- [ ] Início após login com privilégios normais, sem UAC diário; desligar essa preferência funciona.
- [ ] Conferir identidade completa do PC antes do primeiro pareamento; código errado, reutilizado, expirado ou cancelado não autoriza.
- [ ] Recusar a confirmação no PC; o celular não salva autorização e não controla.
- [ ] Revogar um celular durante arrasto; botão liberado, reconexão recusada, outro aparelho continua autorizado.
- [ ] Fechar/reabrir e reiniciar o Android preserva a autorização protegida; executar teste instrumentado do Keystore.
- [ ] Esquecer o PC apaga a autorização local e permite um novo pareamento; não substitui revogar um celular perdido.
- [ ] Reiniciar o agente conserva identidade e aparelhos; reinstalar/perder o registro exige nova conferência presencial.
- [ ] Sem tentativa de controlar tela protegida, tela de login anterior à sessão ou aplicativo elevado.

## Etapa D — robustez, pendente

- [ ] Reiniciar ambos os dispositivos e repetir uso sem ajuda técnica.
- [ ] Testar desligar/ligar Wi-Fi, perder sinal e suspender/resumir PC sem repetir texto/cliques antigos.
- [ ] Testar teclados Android distintos, tela pequena, fonte ampliada e rotação durante composição.
- [ ] Medir CPU/memória do agente ocioso e latência percebida, registrando máquina e condições.
- [ ] Instalar em outro PC com pareamento novo; não copiar credenciais do desenvolvedor.

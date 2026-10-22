# Plano de conclusão

Avaliação inicial em 22/10/2026, atualizada após a implementação da Etapa C. A fonte dos requisitos permanece sendo `ESPECIFICACAO.md`.

## Diagnóstico

O conteúdo inicial continha cinco arquivos de especificação/protótipo. A base nativa e o controle real foram implementados nas Etapas A/B. A Etapa C acrescentou pareamento persistente, revogação, Keystore/DPAPI, bandeja e opção de início após login. Os testes nativos Win32 e a interoperabilidade Kotlin/Rust passaram. O aceite em celular físico e rede real, a inicialização após login e os testes de suspensão/reinício continuam pendentes.

A recomendação é manter as tecnologias e o escopo definidos e entregar incrementos pequenos, cada um com um resultado executável. Não há evidência para estimar uma data de conclusão antes do primeiro build e do primeiro teste entre celular e PC.

## Ordem de execução

| Etapa | Entrega | Evidência para encerrar |
| --- | --- | --- |
| A — Base compilável | `android-app/`, `windows-agent/`, `protocol/`, instruções de build e testes manuais; Compose em simulação explícita; agente local com entrada abstraída | APK debug e agente compilados; testes do protocolo e de liberação de estado passando; revisão dos dois layouts |
| B — Controle real | Backend `SendInput`, Unicode, atalhos fixos, mídia e integração WebSocket; descoberta e fallback manual | Android e Windows reais: mover, clicar, rolar, arrastar e digitar no foco original; liberar botões na desconexão |
| C — Uso doméstico seguro | Pareamento confirmado no PC, WSS com identidade verificada, armazenamento protegido, revogação, bandeja e inicialização após login | Rejeitar dispositivo não autorizado, certificado incorreto e credencial revogada; reconectar após reiniciar sem pedir credenciais novamente |
| D — Robustez | Ajustes de gestos, teclado, orientação, reconexão e empacotamento | Critérios de aceite da especificação verificados em dispositivos reais; instalação reproduzível e instruções simples |

A ordem segue a especificação. A Etapa B antecipou TLS e autorização efêmera; a C mantém uma identidade de PC protegida e emite tokens individuais após confirmação local. O modo simulado permanece em loopback. A distribuição final depende da matriz de validação em dispositivos reais e da assinatura de release, sem publicar a chave privada.

## Primeiro teste integrado

Assim que a base compilar, priorizar um fluxo completo: abrir o app no celular de desenvolvimento, conectar ao agente, mover o cursor e inserir `Olá, ação!` no Bloco de Notas sem alterar a área de transferência nem enviar Enter. Em seguida, iniciar um arrasto e derrubar a conexão para verificar que o botão é liberado. Esse teste reduz a incerteza da integração antes de investir em acabamento.

## Pontos que merecem teste cedo

- **Entrada e foco:** não ativar a janela do agente ao receber comandos; verificar resultados parciais de `SendInput`, Unicode/UTF-16 e modificadores já pressionados. A API respeita limites de integridade do Windows; não prometer controle de UAC ou de aplicativos elevados. Referência: [Microsoft — SendInput](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendinput).
- **IME e orientação:** testar composição com acentos, rotação durante a digitação e acesso a Enviar/Cancelar e aos seis atalhos com teclado aberto em tela horizontal compacta.
- **Gestos simultâneos:** botão esquerdo mantido com um dedo enquanto outro move o touchpad; cancelamento, saída do app, suspensão e perda de rede devem liberar estado no cliente e no agente.
- **Identidade do PC:** descoberta encontra um endereço, mas não autoriza o controle. Nunca substituir a verificação de identidade TLS por confiança global em qualquer certificado.
- **Permissões LAN:** decidir conforme o target SDK efetivamente escolhido. A documentação atual exige tratamento de `ACCESS_LOCAL_NETWORK` para acesso direto na LAN ao usar target 37+, e orienta não solicitá-la para targets inferiores. Referência: [Android — Local network permission](https://developer.android.com/privacy-and-security/local-network-permission).
- **Reconexão:** testar IP alterado, Wi-Fi interrompido e PC suspenso; evitar repetir comandos antigos, especialmente texto e cliques, ao reconectar.

## Ambiente verificado

- Windows com Rust/Cargo 1.96.0, linker MSVC e build release do agente executado; entrada Unicode e liberação de arrasto verificadas em janela Win32 própria.
- Microsoft OpenJDK 17.0.16 instalado.
- Android SDK com plataformas API 33, 34, 35 e 36 e ferramentas ADB instaladas; SDK localizado fora do PATH padrão desta sessão.
- Nenhum celular físico disponível via ADB; a Etapa A foi validada em emulador Android 16/API 36.
- ai-memory consultado: nenhuma página consolidada relevante encontrada para SofaPad/Couch Remote.
- A implementação da Etapa A já gera APK debug e executável Rust release. Resultados de validação ficam em `docs/validacao-etapa-a.md`.
- A implementação da Etapa B gera APK debug e agente 0.2.0; resultados e limites ficam em `docs/validacao-etapa-b.md`. Não foi iniciado emulador nesta etapa para preservar recursos do outro projeto em execução.
- A implementação da Etapa C gera APK debug e agente 0.3.0. Resultados, decisões de segurança e limites ficam em `docs/validacao-etapa-c.md`. A conexão Kotlin/Rust foi testada com pareamento e recarga do registro, sem emulador.

## Como reduzir o prazo

1. Implementar a Etapa A com versões documentadas e fixadas, Gradle Wrapper e comandos reproduzíveis.
2. Executar os testes de entrada no PC de desenvolvimento e conectar um celular físico cedo.
3. Automatizar build e testes quando existirem projetos executáveis.
4. Manter o visual de referência e os atalhos obrigatórios; adiar apenas o que a especificação já declara fora da v1.
5. Publicar binários somente após validar pareamento, transporte seguro e instalação; manter segredos e assinaturas fora do Git.

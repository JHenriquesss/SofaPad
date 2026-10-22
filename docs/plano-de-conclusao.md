# Plano de conclusão

Avaliação inicial em 22/10/2026. A fonte dos requisitos permanece sendo `ESPECIFICACAO.md`.

## Diagnóstico

O produto tem objetivo, layout e escopo v1 bem definidos. O conteúdo inicial contém cinco arquivos: instruções, especificação, apresentação, prompt e protótipo HTML. Não há implementação Kotlin/Rust, manifestos, testes ou builds.

A recomendação é manter as tecnologias e o escopo definidos e entregar incrementos pequenos, cada um com um resultado executável. Não há evidência para estimar uma data de conclusão antes do primeiro build e do primeiro teste entre celular e PC.

## Ordem de execução

| Etapa | Entrega | Evidência para encerrar |
| --- | --- | --- |
| A — Base compilável | `android-app/`, `windows-agent/`, `protocol/`, instruções de build e testes manuais; Compose em simulação explícita; agente local com entrada abstraída | APK debug e agente compilados; testes do protocolo e de liberação de estado passando; revisão dos dois layouts |
| B — Controle real | Backend `SendInput`, Unicode, atalhos fixos, mídia e integração WebSocket; descoberta e fallback manual | Android e Windows reais: mover, clicar, rolar, arrastar e digitar no foco original; liberar botões na desconexão |
| C — Uso doméstico seguro | Pareamento confirmado no PC, WSS com identidade verificada, armazenamento protegido, revogação, bandeja e inicialização após login | Rejeitar dispositivo não autorizado, certificado incorreto e credencial revogada; reconectar após reiniciar sem pedir credenciais novamente |
| D — Robustez | Ajustes de gestos, teclado, orientação, reconexão e empacotamento | Critérios de aceite da especificação verificados em dispositivos reais; instalação reproduzível e instruções simples |

A ordem segue a especificação. Na Etapa A, definir também o contrato de autenticação e os limites do protocolo para evitar retrabalho. Enquanto o transporte seguro da Etapa C não existir, manter o servidor em loopback; qualquer teste LAN intermediário exige rede de teste explicitamente controlada. A versão doméstica só pode ser distribuída após segurança e controle real estarem validados.

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

- Windows com Rust/Cargo 1.96.0 e target `x86_64-pc-windows-msvc` instalado. Isso ainda não valida o linker nem um build do agente.
- Microsoft OpenJDK 17.0.16 instalado.
- Android SDK com plataformas API 33, 34, 35 e 36 e ferramentas ADB instaladas; SDK localizado fora do PATH padrão desta sessão.
- `adb devices` executado: nenhum dispositivo listado.
- ai-memory consultado: nenhuma página consolidada relevante encontrada para SofaPad/Couch Remote.
- Nenhum build ou teste do aplicativo executado: os projetos ainda não existem.

## Como reduzir o prazo

1. Implementar a Etapa A com versões documentadas e fixadas, Gradle Wrapper e comandos reproduzíveis.
2. Executar os testes de entrada no PC de desenvolvimento e conectar um celular físico cedo.
3. Automatizar build e testes quando existirem projetos executáveis.
4. Manter o visual de referência e os atalhos obrigatórios; adiar apenas o que a especificação já declara fora da v1.
5. Publicar binários somente após validar pareamento, transporte seguro e instalação; manter segredos e assinaturas fora do Git.

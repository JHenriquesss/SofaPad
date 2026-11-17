# Pareamento em três passos — 0.3.10

Agente do PC e app mudaram. O app 0.3.10 também pareia com agentes anteriores.

## Problema

A tela de conexão em `Configurações` era um formulário longo, com rolagem: busca de PCs, IP, porta, um campo para a identidade de 64 caracteres, uma caixa de confirmação, código e nome, tudo junto. Pedia para conferir 64 caracteres à mão, e na prática ninguém conseguia.

## O que mudou no app

`Configurações` agora mostra só um resumo da conexão e o botão `Parear PC`. O pareamento virou um assistente de três telas curtas:

1. **Achar o PC.** Lista os PCs anunciados na rede; um toque escolhe. `Não encontrou? Digitar o endereço` abre o campo de IP.
2. **Conferir.** O celular mostra um código de conferência de 16 caracteres em quatro grupos (`AB12 CD34 EF56 7890`) e pergunta se é igual ao da janela do PC. `É diferente` volta ao passo 1 com um alerta e não envia nada.
3. **Código.** Código de uso único de 8 caracteres, nome do celular e `Parear`.

A identidade completa fica em `Ver identidade completa`.

## Como a conferência funciona

- Ao escolher o PC, o app faz só o aperto de mão TLS com aquele endereço e lê a identidade (SHA-256 do certificado) que ele apresenta. Nenhum pedido e nenhum segredo são enviados nesse momento.
- O código de conferência são os 16 primeiros caracteres dessa identidade (64 bits).
- Se a rede anunciou uma identidade para aquele PC e o endereço apresentou outra, o app recusa antes de mostrar qualquer código.
- Depois da confirmação, o app fixa a identidade completa. O código de uso único só é enviado a um servidor com exatamente aquele certificado.
- Digitar a identidade à mão deixou de ser necessário.

## Segurança

- A identidade não é segredo: serve para a pessoa saber que o celular está falando com o PC certo. O segredo é o código de uso único.
- A conferência passou de 64 para 16 caracteres. Para enganar a pessoa, outro aparelho da rede precisaria de um certificado cuja identidade comece com os mesmos 16 caracteres do PC dela, o que exige da ordem de 2^64 tentativas. Uma versão mais curta (6 caracteres, cogitada antes) seria quebrável em minutos e foi descartada.
- A conferência continua obrigatória: sem tocar em `É igual`, o campo do código não aparece.

## O que mudou no agente

A janela de `Parear celular` mostra, nesta ordem: o código de conferência, o código de uso único e o lembrete de confirmar o nome do celular. A identidade completa fica no fim. Em agentes anteriores, o código de conferência são os 16 primeiros caracteres da identidade que a janela já mostrava.

## Verificado

- Testes JVM com servidor TLS local: a leitura da identidade devolve o SHA-256 do certificado sem que nenhum pedido chegue ao servidor; endereço público é recusado; porta fechada falha.
- Testes instrumentados no emulador:
  - o campo do código só aparece depois de `É igual`;
  - `É diferente` volta ao passo 1 com alerta;
  - endereço fora da rede local é recusado sem tentar conexão;
  - identidade anunciada diferente da apresentada é recusada;
  - o IP digitado sobrevive à rotação.
- No emulador, contra o agente real (0.3.7) que roda neste PC: digitando o IP, o passo 2 mostrou `4DAF 8D9B D94E 17E5`, igual aos 16 primeiros caracteres do SHA-256 do certificado lido à parte com `openssl s_client`. A identidade completa também conferiu, e `É igual` levou ao passo 3. Isso também mostra que o app novo funciona com agente antigo.

## Não verificado

- O assistente num celular físico, com a lista de PCs achados pela rede (o emulador não recebe os anúncios mDNS).
- A nova janela de pareamento na tela do PC.
- O envio do código de uso único e a confirmação do nome no PC com o assistente novo (o envio usa o mesmo cliente de pareamento da 0.3.9, coberto pelos testes JVM).

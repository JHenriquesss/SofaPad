# Protocolo v1 — base da Etapa A

JSON UTF-8 em WebSocket. As [fixtures válidas](fixtures/valid.json) são verificadas pelo codificador Kotlin e pelo decodificador Rust. As [inválidas](fixtures/invalid.json) devem ser rejeitadas pelo agente.

## Estado desta entrega

O agente atende **somente** `ws://127.0.0.1:49375/v1/simulate`, com backend simulado. O app Android também simula localmente, sem conexão de rede nem permissão `INTERNET`. Não há controle de Windows, descoberta, pareamento ou WSS nesta etapa. A simulação local não equivale a uma autorização.

O servidor suporta uma sessão por vez, recusa `Origin` de navegadores e endpoints diferentes, limita frames e mensagens e encerra sessões ociosas após três segundos. Os testes usam portas efêmeras em loopback, sem abrir firewall.

## Mensagens e limites

Todas as mensagens cliente → agente incluem `v: 1`, `type` e `seq` inteiro de 1 até 9007199254740991. A sequência deve aumentar estritamente na mesma conexão. Reconexões começam uma nova sequência; não reenviar comandos pendentes da conexão anterior.

| Tipo | Campos adicionais | Limites |
| --- | --- | --- |
| `mouse_move` | `dx`, `dy` numéricos finitos | cada delta entre −2048 e 2048 |
| `mouse_button` | `button`, `state` | `left`/`right`; `down`/`up` |
| `mouse_scroll` | `vertical` numérico finito | −120 até 120; positivo para cima |
| `text_insert` | `text` string | até 4096 bytes UTF-8; sem Enter adicional |
| `shortcut` | `name` | `ALT_TAB`, `CTRL_C`, `CTRL_V`, `CTRL_A`, `ENTER`, `ESC`, `BACKSPACE` |
| `media` | `name` | `PLAY_PAUSE`, `VOLUME_UP`, `VOLUME_DOWN` |
| `release_all` | nenhum | liberar estado de entrada |
| `ping` | nenhum | heartbeat de aplicação |

Payload total máximo: 8192 bytes; limite de 240 comandos por janela de um segundo, incluindo heartbeat. Tipos e campos desconhecidos, versões incompatíveis, números inválidos, sequência repetida e texto excessivo encerram a sessão e provocam liberação de entrada. Falha parcial do backend, fechamento, timeout ou erro de transporte também tentam liberar todo o estado. O backend real deverá implementar soltura de teclas e modificadores além dos botões.

Na abertura, o agente envia:

```json
{"v":1,"type":"hello","mode":"simulation","authorized":false,"max_text_bytes":4096,"idle_timeout_ms":3000}
```

Comando aceito recebe `{"v":1,"type":"ack","seq":1,"mode":"simulation"}`; `ping` recebe `pong` no lugar de `ack`. `ack` confirma que a simulação tratou a mensagem, sem afirmar entrada real no PC. Erros retornam somente código fixo, sem ecoar texto ou dados privados. O cliente de desenvolvimento deve enviar heartbeat ao menos uma vez por segundo quando não houver comandos.

## Contrato para as próximas etapas

O endpoint de produção só deverá habilitar entrada após transporte WSS com identidade do PC verificada e autenticação de dispositivo previamente autorizado. Descoberta nunca concede autorização. O primeiro pareamento requer confirmação presencial no PC; expiração, limitação de tentativas e revogação precisam ser testadas. Credenciais ficarão em Keystore/DPAPI; nada de tokens em URL, fixtures ou logs. Formato de pareamento e autenticação será fechado com as bibliotecas da Etapa C, sem criptografia própria.

Em uma sessão autenticada futura, manter o mesmo formato de comandos e seus limites. Texto, atalhos e cliques não devem ser repetidos automaticamente depois de falha de conexão, pois o cliente pode não saber se o PC já os aplicou.

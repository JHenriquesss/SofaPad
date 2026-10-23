# Protocolo v1 — Etapas A/B/C

JSON UTF-8 em WebSocket. As [fixtures válidas](fixtures/valid.json) são verificadas pelo codificador Kotlin e pelo decodificador Rust. As [inválidas](fixtures/invalid.json) devem ser rejeitadas pelo agente.

## Estado desta entrega

No Windows, o padrão é a bandeja com registro persistente. Controle usa WSS `/v1/control`, Bearer individual no upgrade e `SendInput`. O certificado é validado por pin SHA-256, validade e hostname padrão do OkHttp. O nome TLS `sofapad.local` é estável: um resolvedor restrito liga esse nome ao IP privado descoberto, preservando a verificação de hostname e a identidade mesmo após DHCP. Descoberta não pode trocar o pin salvo. `--simulate` atende somente `ws://127.0.0.1:49375/v1/simulate`. A opção legada `--control` mantém autorização efêmera de diagnóstico.

O servidor controla o PC por uma sessão por vez, recusa `Origin` de navegadores e endpoints diferentes, limita frames e mensagens e encerra sessões ociosas após três segundos. Os testes usam portas efêmeras em loopback, sem abrir firewall.

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

Comando aceito recebe `{"v":1,"type":"ack","seq":1,"mode":"simulation"}`; `ping` recebe `pong` no lugar de `ack`. `ack` confirma que a simulação tratou a mensagem, sem afirmar entrada real no PC. Erros retornam somente código fixo, sem ecoar texto ou dados privados. O cliente de desenvolvimento deve enviar heartbeat ao menos uma vez por segundo quando não houver comandos. O app Android envia cada comando assim que ele acontece, soma movimentos enquanto houver muitos comandos sem confirmação e mantém no máximo cerca de 180 comandos por segundo, abaixo do limite do agente.

## Pareamento persistente da Etapa C

O PC abre uma janela de cinco minutos pelo menu da bandeja e mostra um código aleatório de oito caracteres hexadecimais. O Android confere presencialmente o fingerprint completo antes de enviar esse código em `Authorization: Bearer …` no upgrade WSS de `/v1/pair`. Há limite de cinco falhas por IP em um minuto, tabela limitada a 64 IPs e código consumido por uma única conexão. Não há entrada remota nessa rota.

O cliente envia apenas `{"v":1,"type":"pair","name":"Meu celular"}` (nome até 48 caracteres e 128 bytes, sem controles). O Windows pede confirmação local. Campos desconhecidos e mensagens de controle são rejeitados. Recusa, cancelamento, expiração ou confirmação após 60 segundos não geram autorização. Após salvar com sucesso, responde `{"v":1,"type":"paired","token":"<token privado>"}` e fecha. O token tem 32 bytes aleatórios, codificados em hexadecimal; não é o código curto de pareamento.

Cada pareamento emite outro token, sem invalidar outros aparelhos automaticamente. A lista na bandeja permite revogar cada autorização, inclusive nomes repetidos. Há até dez autorizações e um controlador conectado por vez. Se outro aparelho tentar controlar enquanto há uma sessão ativa, recebe `409` na hora. Se o mesmo aparelho reconectar (por exemplo, após uma queda de Wi-Fi que deixou a conexão antiga pendurada), a nova sessão assume imediatamente: a antiga perde a permissão de injetar entrada, botões e teclas que ela deixou pressionados são soltos, e ela termina na próxima leitura. A revogação é persistida, fecha o socket atual e impede novos comandos; a sessão também revalida o token antes de cada mensagem.

Identidade/chave TLS e hashes SHA-256 dos tokens ficam em documento protegido pelo DPAPI do usuário, com substituição atômica. O Android salva um PC em arquivo privado fora do backup, cifrado com AES-256-GCM e chave não exportável do Android Keystore. Falha de abertura do registro do PC não gera uma identidade nova silenciosamente. Tokens nunca vão em URLs, mDNS ou logs; mDNS publica apenas a identidade pública em TXT `id`.

Na sessão autenticada, `hello` informa `mode: "control"` e `authorized: true`; `ack`/`pong` mantêm esse modo. O cliente descarta a fila ao perder a conexão; texto, atalhos e cliques não são repetidos automaticamente. A autorização persistente dura até revogação ou remoção do registro; somente o modo legado temporário expira em oito horas e troca identidade ao reiniciar.

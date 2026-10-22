# Validação da Etapa A

Executada em 22/10/2026 no PC de desenvolvimento Windows x64. A entrega é uma base compilável em **simulação**, sem entrada real no Windows e sem integração de rede Android/PC.

## Resultado automatizado

| Verificação | Comando/tarefa | Resultado |
| --- | --- | --- |
| Protocolo, estado e WebSocket Rust | `cargo test --locked --manifest-path windows-agent/Cargo.toml` | 16 testes aprovados |
| Formatação Rust | `cargo fmt --manifest-path windows-agent/Cargo.toml -- --check` | aprovado |
| Análise estática Rust | `cargo clippy --locked --manifest-path windows-agent/Cargo.toml --all-targets -- -D warnings` | aprovado |
| Executável Windows | `cargo build --locked --release --manifest-path windows-agent/Cargo.toml` | aprovado, `sofapad-agent.exe` gerado |
| APK Android | `:app:assembleDebug` | aprovado, APK debug gerado |
| Testes Kotlin/JVM | `:app:testDebugUnitTest` | 5 testes aprovados |
| Interface Android | `:app:connectedDebugAndroidTest` | 8 testes aprovados em emulador Android 16/API 36 |
| Lint Android | `:app:lintDebug` | aprovado, zero erros; 9 avisos não bloqueantes |

Total: **29 testes aprovados**, sem testes ignorados. Os avisos de lint são oito sugestões de atualização de dependências/ferramentas e uma recomendação de configuração de backup para Android anterior à API 31. O manifesto desabilita backup e contém regras explícitas de exclusão para Android 12+. As versões foram fixadas para este build; nenhuma supressão global de lint foi usada.

O script `scripts/build.ps1` foi exercitado em suas duas opções de plataforma. O comando Android final também incluiu testes instrumentados. Os relatórios detalhados permanecem nos diretórios locais `build/`, ignorados pelo Git. Houve um aviso da ferramenta sobre versões de XML do SDK, sem impedir compilação ou execução.

## Comportamentos cobertos

- Fixtures JSON compartilhadas entre codificador Kotlin e decodificador Rust.
- Rejeição de versões, comandos/campos desconhecidos, deltas fora de limites, sequência repetida, texto excessivo e payload excessivo.
- Limite de texto medido em bytes UTF-8 e envio sem Enter adicional.
- Liberação de estado em erro parcial do backend, mensagem inválida, excesso de taxa, desconexão e timeout.
- Conexão WebSocket real em loopback; proibição de bind externo e de Origin de navegador.
- Posições dos botões/touchpad em vertical e horizontal, com inversão para canhotos.
- Cancelamento de botão pressionado, cancelamento de texto e atalhos sem alterar a composição Android.
- Digitação compacta com 64 dp disponíveis; teste do app completo verificando Enviar, Cancelar e atalho acima do teclado real.

## Conferência no emulador

O app foi instalado e aberto no emulador Pixel 5/API 36, temporariamente configurado em 720 × 1600 e densidade 320. Foram conferidos visualmente o controle vertical, horizontal e a digitação horizontal com Gboard aberto. O layout horizontal agrupa mídia na barra superior para preservar altura do touchpad; o teclado aberto usa cabeçalho reduzido e ações fixas ao lado do campo.

Orientação horizontal e modo canhoto foram alterados nas configurações e permaneceram ativos após encerrar o processo e reabrir o app. A posição dos grupos foi conferida na hierarquia da interface após esse reinício. Capturas nativas estão no README.

O emulador foi iniciado em modo somente leitura, sem snapshots salvos. O primeiro boot apresentou um diálogo de System UI sem resposta; a interface foi recuperada e a resolução reduzida antes da suíte final. Isso não foi tratado como teste em aparelho físico.

## Arquivos entregues

- `android-app/`: projeto Gradle/Compose, wrapper oficial com checksum, telas nativas, simulador, preferências DataStore e testes.
- `windows-agent/`: crate Rust, `Cargo.lock`, abstração `InputController`, simulador, validação de protocolo e servidor loopback.
- `protocol/`: contrato e fixtures válidas/inválidas.
- `scripts/build.ps1`: build e verificações reproduzíveis.
- `docs/`: instalação, testes manuais, plano e este registro; README atualizado.
- `.gitignore` e `.gitattributes`: exclusão de temporários e normalização de fontes.

APK, executável, relatórios e credenciais não são versionados.

## Limites e próximos passos

Não foi possível testar em celular físico: nenhum estava disponível via ADB. Gestos com dois dedos e arrasto simultâneo ainda exigem conferência tátil em aparelho real. O suporte mínimo declarado é API 29, mas a execução desta etapa ocorreu na API 36.

**Etapa B:** backend `SendInput`, Unicode/atalhos/mídia, integração Android/Windows, estado e reconexão, descoberta e fallback. Manter qualquer endpoint intermediário inseguro restrito a loopback ou rede de teste explicitamente controlada.

**Etapa C:** pareamento presencial, WSS com identidade verificada, Keystore/DPAPI, revogação, bandeja e inicialização após login. Essas funções ainda não existem nesta entrega.

**Etapa D:** validar em Windows e Android reais os critérios de aceite, reinício, suspensão, perda de Wi-Fi, teclas/botões presos e consumo de recursos. Só então preparar instalação doméstica.

# Couch Remote — pacote para começar com Codex

## Arquivos

- `ESPECIFICACAO.md`: requisitos, interface, arquitetura, segurança, critérios de aceite e fases do trabalho (fonte principal da verdade).
- `AGENTS.md`: diretrizes permanentes para o Codex neste repositório.
- `PROMPT_INICIAL_CODEX.md`: texto para colar na primeira tarefa do Codex.
- `prototipo_layout.html`: demonstração visual interativa offline, **não** é o app Android final nem se conecta ao Windows.

## Uso recomendado

1. Crie uma pasta/repositório `couch-remote` no seu PC.
2. Copie os arquivos do pacote para a raiz do repositório.
3. Abra a pasta no Codex local, e envie o conteúdo de `PROMPT_INICIAL_CODEX.md`.
4. O Codex deve construir os projetos Android/Rust. Teste no **seu PC Windows** com seu celular primeiro.
5. Só depois compile a versão para instalar no Windows dela e pareie o dispositivo na rede local dela.

O Codex cloud também pode criar código em um ambiente ligado a repositório; porém não substitui testes em Windows e em Android físico. Não copie credenciais de pareamento de um PC para o outro.

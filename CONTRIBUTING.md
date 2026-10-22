# Contribuir pelo OpenCode ou outro editor

Leia `AGENTS.md` e `ESPECIFICACAO.md` antes de alterar o projeto. O fluxo é trabalhar numa branch, testar e abrir um pull request para `main`.

## Preparar a outra máquina

```powershell
git clone https://github.com/JHenriquesss/SofaPad.git
cd SofaPad
git switch -c fix/ajustes-no-teste
```

Se já tiver um checkout limpo, atualize `main` com `git pull --ff-only` antes de criar a branch. Para compilar tudo, prepare Rust/MSVC, JDK 17 e Android SDK 36 conforme [instalação](docs/instalacao.md). O pacote binário da prévia não contém o código-fonte para edição; use o clone do repositório.

Um pedido inicial possível para o OpenCode:

> Leia AGENTS.md e ESPECIFICACAO.md. Estou testando SofaPad em Windows e Android reais. Corrija o problema que vou descrever, mantendo Kotlin/Compose, Rust, os layouts e o pareamento seguro. Faça mudanças pequenas nesta branch, execute os testes possíveis e registre o resultado. Não adicione credenciais, chaves ou binários ao Git. Ao terminar, prepare um PR para main descrevendo problema, correção e validação.

Descreva o erro com o modelo/Android do celular, orientação, ação realizada e resultado esperado. Não envie código de pareamento, token ou dados privados. Se a ferramenta não tiver autenticação no GitHub, os commits podem ser enviados por você e o PR pode ser aberto pelo site.

## Testar e abrir PR

```powershell
.\scripts\build.ps1
.\scripts\test-interop.ps1 -Pairing
git push -u origin fix/ajustes-no-teste
```

Abra o PR no GitHub com destino `main`, usando o modelo do repositório. Se for colaborador sem acesso de escrita, envie por um fork. Commits adicionais na mesma branch atualizam o PR e disparam novamente o CI.

O [CI](https://github.com/JHenriquesss/SofaPad/actions/workflows/ci.yml) executa em runner Windows do GitHub:

- Rust 1.96.0: formatação, testes automáticos, Clippy e build release.
- JDK 17/Android SDK 36: APK debug, testes JVM, lint e compilação dos testes instrumentados.
- Cliente Kotlin e servidor Rust: pareamento WSS, recuperação do registro persistido e envio Unicode confirmado. A entrada do teste de rede é simulada. O job verifica que esse teste passou sem ser ignorado.

O fluxo roda em PRs para `main`, pushes em `main` e acionamento manual. Usa permissão de leitura, ações fixadas por commit e não publica releases nem realiza merge. PRs apenas leem o cache Gradle. Não há emulador ou execução dos testes interativos de bandeja/SendInput no runner; esses testes e a matriz do Android físico continuam conforme [validação](docs/testes-manuais.md).

Depois de abrir o PR, envie o link neste chat para revisão do código e dos resultados do CI. Um CI verde não substitui o teste físico nem a revisão antes de integrar.

Referências da configuração: [checkout](https://github.com/actions/checkout/tree/v6), [setup-java](https://github.com/actions/setup-java/tree/v5), [setup-gradle](https://github.com/gradle/actions/tree/v5) e [imagem Windows 2025](https://github.com/actions/runner-images/blob/main/images/windows/Windows2025-Readme.md).

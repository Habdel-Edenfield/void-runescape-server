# Regras de trabalho com o Habdel (fork Habdel-Edenfield/void-runescape-server)

As instruções do projeto estão em `.claude/CLAUDE.md` (spotless, testes, estilo). Este arquivo só acrescenta o que vale para este fork.

## Conversa

- **Falar sempre em português**, simples: o Habdel não é programador. Explicar decisões, sem jargão.
- **Não pedir para repetir partes do jogo nem criar conta nova.** Para cobrir algo que ele já passou, use testes que rodam o jogo.
- **Só reiniciar o servidor quando ele disser "pode reiniciar"** (ele sai do jogo antes).
- Commit só depois de ele aprovar no jogo, e mostrando antes a lista de arquivos quando ele pedir.
- **Outra sessão pode estar mexendo nesta pasta** (ex.: `tools/montador/`, `settings.gradle.kts`, `ZzChickenRepro.kt` não são da tradução). Nunca `git add -A`/`git add .`: adicione arquivo por arquivo e confira `git status` antes do commit. Não troque de branch nem faça stash/reset sem combinar.

## Rodar

```bash
# compilar o servidor
./gradlew :game:shadowJar
# copiar para a pasta de execução (nunca rode o jar de game/build/libs: recompilar com ele no ar quebra o desligamento)
cp game/build/libs/void-server-dev.jar ~/Documents/void-downloads/run/void-server.jar
# ligar, com o diretório atual na raiz do repositório
java -jar ~/Documents/void-downloads/run/void-server.jar > ~/Documents/void-downloads/server.log 2>&1
# desligar salvando as contas: SIGTERM no processo da porta 43594
kill -TERM "$(ss -ltnp | grep ':43594 ' | grep -oP 'pid=\K[0-9]+')"
# cliente desktop com Ctrl+WASD
java -jar ~/Documents/void-downloads/void-client-ctrl-wasd.jar
```

- Pronto quando o log mostra `[Main] Void loaded in`.
- **Testes**: `LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ./gradlew --no-daemon ...` (o sistema está em pt_BR e o formato de número quebra testes). O teste do módulo `database` falha nesta máquina (PostgreSQL embutido); ignore.
- **Cliente web**: porta **8085** (a 8080 é do Apache do Hardgaard; não mexer). Configurações com ponto não passam pelo `./gradlew` no Ubuntu (o `sh` descarta): use `env web.server.enabled=true web.server.port=8085 java -jar ...`. O jar só serve o web com a correção da branch `fix/shadow-jar-ktor-json`. O mundo "Local" do site aponta para 8080; para testar, carregue o cliente apontando para 8085.
- **Português no jogo**: comando `portugues` no console (tecla `). No teclado do Habdel o console pode não abrir: com ele deslogado, acrescente `portuguese_dialogue = true` em `[variables]` de `data/saves/<conta>.toml` (faça cópia antes). Conta dele: `9475518`. Conta de teste do web: `testeweb` (senha em `~/Documents/void-downloads/conta-teste-web.txt`, não repetir no chat).
- Cache, cliente e logs ficam em `~/Documents/void-downloads/` (fora do Git). Cache extraído em `data/cache/`.

## Branches

| Repositório | Branch | Conteúdo |
|---|---|---|
| servidor | `fix/shadow-jar-ktor-json` | O `void-server.jar` empacotado volta a servir o cliente web (classe de JSON do Ktor removida pelo `minimize`). |
| servidor | `fix/network-text-latin1` | Texto com acento não derruba mais a conexão (rede enviava UTF-8). Teste em `JagExtensionsTest`. |
| servidor | `feat/pt-br-dialogue-prototype` | Tradução pt-BR (sobre `fix/network-text-latin1`): Ilha Tutorial, Lumbridge, chat, testes. Ver `.claude/translation.md`. |
| cliente | `feat/ctrl-wasd-camera` | Ctrl + W/A/S/D move a câmera como as setas. |

- **Cliente**: `~/Documents/void-client`, `origin` = fork `Habdel-Edenfield/void-client`, `upstream` = `GregHib/void-client`. Compilar com `bash ./gradlew shadowJar` (o `gradlew` vem sem permissão de execução). Jar em `client/build/libs/void-client-1.2.0.jar`; a cópia em uso é `~/Documents/void-downloads/void-client-ctrl-wasd.jar`.
- **Servidor**: `origin` = fork, `upstream` = `GregHib/void`. Nada de pull request no projeto do GregHib sem o Habdel pedir.

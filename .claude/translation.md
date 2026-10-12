# Tradução pt-BR (só neste fork)

Tradução do texto que o servidor envia ao jogador. Branch `feat/pt-br-dialogue-prototype` (sobre `fix/network-text-latin1`). Feitos: Ilha Tutorial e Lumbridge. Mapa do que falta: `tools/translation/map_text.py` + `build_report.py`.

## Como funciona

- **`Translation.kt`** (`game/.../content/entity/player/dialogue/`): `Player.translate(text)` devolve a tradução quando o jogador tem `portuguese_dialogue` ligado; sem tradução, devolve o inglês e chama `missingTranslation(text)` + loga `Missing pt_br translation: <texto>` (uma vez por texto). Índice montado uma vez por carga da tabela. `Player.translate(row)` troca uma linha de tabela por `<tabela>_pt_br.<linha>` (caixa de instruções da Ilha Tutorial).
- **Tabela** `data/entity/player/dialogue/translation/dialogue_pt_br.tables.toml`: linhas `en`/`pt`. Frase exata vence; depois linhas com marcadores `%1%`, `%2%` (casam qualquer texto) e `%server%` (`server.name`). **Gerada** por `tools/translation/gen_dialogue.py` a partir de `tools/translation/sources/`: não edite a tabela à mão.
- **Variável** `portuguese_dialogue` em `translation.vars.toml` (persistente). **Comando** `portugues` liga/desliga. Se o console (tecla `) não abrir no teclado ABNT2, ligue no save: `portuguese_dialogue = true` em `[variables]` de `data/saves/<conta>.toml`, com o jogador deslogado.
- **Ganchos**: `npc`, `player`, `statement`, `choice` (título e opções), `item`/`items`, `makeAmount`, `levelUp`, `% Done` da ilha, e `sendContinue` ("Click here to continue", texto do cache sobrescrito só quando traduzido).
- **Chat**: `messageTranslator` em `engine/.../client/EncodeExtensions.kt` (não faz nada por padrão; `Translation` preenche). Console nunca é traduzido.
- **Rede**: depende de `fix/network-text-latin1` (texto enviado 1 byte por caractere). Sem isso, acento derruba a conexão.

## Método por região

1. Mapear NPCs da região **e todos os handlers que os atendem, inclusive curingas** (`banker*`) e scripts fora da pasta da região. O que o jogador vê, não a pasta.
2. `gen_dialogue.py --dump` → `split_groups.py` → um assistente por grupo, em paralelo. Prompt: ler os scripts para saber quem fala, seguir `GUIA_DE_ESTILO.md`, escrever `en → pt` com `json.dump(ensure_ascii=False)`, rodar `validate.py` até OK, e **responder com as 5 traduções de que menos tem certeza** + escolhas feitas.
3. Revisar as dúvidas e uma amostra aleatória; validar todos os grupos; gerar a tabela.
4. **Teste de passeio** da região (`LumbridgeTranslationTest`: conversa com cada NPC da área com `Talk-to`, uma vez por posição de opção 1..5) e, para atividades, um teste como `TutorialIslandTranslationTest`. **Prova obrigatória**: apagar uma tradução de propósito e ver o teste falhar apontando a frase.
5. Suíte completa sem fail-fast: `LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ./gradlew --no-daemon --no-configuration-cache --continue -I <init sem failFast> test --rerun`. O init script: `gradle.projectsEvaluated { rootProject.allprojects { tasks.withType(Test).configureEach { failFast = false } } }`.
6. `spotlessApply`, depois **commit só quando o Habdel aprovar no jogo**, adicionando os arquivos um por um.

## Armadilhas

- A chave `en` tem que ser **idêntica** ao texto do jogo: erro de digitação, espaço no início, ponto final. O extrator copia do código; nunca redigite.
- Variável Kotlin simples `$x` não inclui ponto: `"keeps $cost."` termina com ".". `${...}` vira `%n%`; `${Settings["server.name"]}` e `${World.name}` viram `%server%`.
- Todo `%n%`, `%server%` e tag (`<col=...>`, `<br>`, `<red>`, `</col>`) do inglês tem que estar no português (`validate.py` confere).
- **Limite de 512 bytes por texto** na tabela (acento = 2 bytes). Acima disso o servidor **não sobe**. O gerador pula textos > 500 bytes.
- Só Latin-1 (U+0020–U+00FF). Aspas curvas, travessão, reticências de um caractere e emoji quebram o texto no cliente.
- Texto com `\n` (só em código comentado hoje) é pulado: a tabela não aceita quebra de linha.
- **O extrator não enxerga**: falas passadas por funções auxiliares (`menu(followUp)` — já tratado), npc indicado por variável (`npc<X>(target.id, "...")`, `npcId = ...` — já tratado), sorteios `when { 0 -> "..." }` (conselhos do Farmer: traduzidos à mão em `GENERAL`), mensagens de dados (`*.tables.toml`, definições de item). **Só o teste de passeio pega tudo**, porque roda o jogo.
- Palavras inseridas pelo jogo no meio da frase (nome de item, "sir/madam", "inventory/bank") ficam em inglês dentro do português.
- Fora do alcance: menus do botão direito, nomes ao passar o mouse, rótulos fixos de interface, tela de login (cache/cliente).
- Rodar testes com o sistema em pt_BR: números saem "15.000" e testes que conferem "15,000" falham. Gradle repassa o idioma do daemon: use `--no-daemon` + `LANG`/`LC_ALL`.
- `WorldTest` com `loadNpcs = true`: os spawns só entram no mundo depois de um `tick()`.
- `TutorialIslandTranslationTest`/`LumbridgeTranslationTest` trocam `messageTranslator`/`missingTranslation`: restaure sempre no fim.

## Próximo trabalho: mensagens das habilidades

741 mensagens de chat (167 com variável no meio) e 1.776 falas (45 com variável) em `content/skill/**`, mais mensagens em `data/skill/**/*.tables.toml` e `*.recipes.toml`. Antes de traduzir, o Habdel quer: (1) quantas têm palavra inserida em inglês (item, peixe, minério) separadas das que só têm número, e as opções para isso; (2) o teste pronto antes da tradução (não há "passeio por região": um teste por habilidade fazendo as ações, como `TutorialIslandTranslationTest`, ou percorrendo as linhas das tabelas de dados).

## Pendências

- Fala da Xenia (história do Dragith Nurn) > 512 bytes: continua em inglês.
- Uma segunda fala de Lumbridge falta no mapa (1.123/1.125): localizar com `map_text.py`.
- Guia de Linux em `docs/linux-setup` (ainda não escrito).
- `run-server.sh` do pacote sem permissão de execução (`game/build.gradle.kts`, task `bundle`).
- README manda `./gradlew run`: abre também o cache viewer; o certo é `./gradlew :game:run`.
- Formato de número segue o idioma do sistema (`Text.kt`, `DecimalFormat("#,###")`).
- `AncientEffigiesTest` instável (falha sozinho, passa na suíte).
- Avisos "Player doesn't have interface open" (`equipment_bonuses`, `audio_options`) durante o tutorial.
- Comando `level_dialogues` com mensagem invertida (`SkillCommands.kt`).
- Teste do módulo `database` falha aqui (PostgreSQL embutido não roda `initdb` em `/tmp`).
- `readString` da rede lê acentos com sinal (cliente → servidor).
- `worlds.json`: mundo "Local" fixo em `localhost:8080`.
- Cliente (`~/Documents/void-client`): `gradlew` sem permissão de execução; `.gradle/` e `build/` fora do `.gitignore`.

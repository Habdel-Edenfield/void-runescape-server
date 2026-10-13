# Tradução pt-BR (só neste fork)

Tradução do texto que o servidor envia ao jogador e, para a janela de tarefas, do texto que o cliente desenha a partir do cache. Branch `feat/pt-br-dialogue-prototype` (sobre `fix/network-text-latin1`). Feitos: Ilha Tutorial, Lumbridge, a janela de tarefas (conjuntos Lumbridge/Draynor e Unstable Foundations), a janela de "subiu de nível" (sem a lista de desbloqueios), os rótulos fixos do item 1 do mapa e as janelas do item 2 (abas Logout, Opções, Habilidades, Quest Journal, Estilos de combate e Orações; amigos, ignorados, clã e chat; banco e loja). Mapa do que falta: `tools/translation/map_text.py` + `build_report.py`.

## Como funciona

- **`Translation.kt`** (`game/.../content/entity/player/dialogue/`): `Player.translate(text)` devolve a tradução quando o jogador tem `portuguese_dialogue` ligado; sem tradução, devolve o inglês e chama `missingTranslation(text)` + loga `Missing pt_br translation: <texto>` (uma vez por texto). Índice montado uma vez por carga da tabela. `Player.translate(row)` troca uma linha de tabela por `<tabela>_pt_br.<linha>` (caixa de instruções da Ilha Tutorial).
- **Tabela** `data/entity/player/dialogue/translation/dialogue_pt_br.tables.toml`: linhas `en`/`pt`. Frase exata vence; depois linhas com marcadores `%1%`, `%2%` (casam qualquer texto) e `%server%` (`server.name`). **Gerada** por `tools/translation/gen_dialogue.py` a partir de `tools/translation/sources/`: não edite a tabela à mão.
- **Variável** `portuguese_dialogue` em `translation.varcs.toml` (persistente): é o **varc 2000**, enviado ao cliente porque os scripts das janelas do cache leem esse valor. `playerSpawn` sempre reenvia (o cliente guarda o valor da conta anterior). **Comando** `portugues` liga/desliga. Se o console (tecla `) não abrir no teclado ABNT2, ligue no save: `portuguese_dialogue = true` em `[variables]` de `data/saves/<conta>.toml`, com o jogador deslogado.
- **Ganchos**: `npc`, `player`, `statement`, `choice` (título e opções), `item`/`items`, `makeAmount`, `levelUp`, `% Done` da ilha, e `sendContinue` ("Click here to continue", texto do cache sobrescrito só quando traduzido).
- **Chat**: `messageTranslator` em `engine/.../client/EncodeExtensions.kt` (não faz nada por padrão; `Translation` preenche). Console nunca é traduzido.
- **Rede**: depende de `fix/network-text-latin1` (texto enviado 1 byte por caractere). Sem isso, acento derruba a conexão.

## Janelas que o cliente desenha do cache: tradução dentro do cache

A janela de tarefas (interface 917), a aba lateral (1056), o aviso "Task Complete!" (1055) e a janela de "subiu de nível" com pergaminho (741, `skill_level_details`) são desenhados **pelo cliente**, com scripts e dados do cache: o servidor só manda números e qual janela abrir. Por isso o português vai **para dentro do cache, ao lado do inglês**, e os scripts escolhem o idioma pelo varc 2000. Inglês intacto, escolha por jogador. Para achar outras janelas assim, há um mapa feito pela sessão "Mapear janelas do cliente ainda em inglês".

- **Tabela** `interface_pt_br.tables.toml` (na mesma pasta): nomes de tarefa (`name_*`), descrições e dicas (`desc_*`, `hintN_*`, `set_*`), descrições de oração (`prayer_*`), dificuldades e conjuntos (`difficulty_*`, `area_*`), frases de nível (`level_up_*`), meses (`month_*`), frases fixas dos scripts (`script_*`), textos passados por hooks como dicas de botão e o link do guia (`tooltip_*`), rótulos fixos (`label_*`, com `interface`/`component`) e trechos do chat (`chat_*`). **Gerada** de `tools/translation/sources/pt_tasks*.json`, `pt_level_up*.json`, `pt_labels*.json` e `pt_ui_*.json` (abas, social/chat, banco/loja): não edite à mão.
- **Lógica** em `cache/.../cache/translation/CacheTranslationPatch.kt` (calcula o patch, não grava; para outra janela, acrescente ali as listas dela); **ferramenta** `./gradlew :tools:translateInterfaces --args=dump|table|patch` (`tools/.../cache/InterfaceTranslation.kt`): `dump` lista o que falta em `sources/missing_interfaces.json`, `table` gera a tabela, `patch` grava no `data/cache` (recusa com o servidor ligado; `--cache`/`--table` para testar numa cópia). Rodar `patch` de novo desfaz e refaz: sem mudança, diz "Cache already up to date".
- **O que o patch faz** (troca 1 instrução por 1, para nenhum salto dos scripts originais mudar de lugar): `struct_param` de um campo de texto (`STRUCT_TEXTS`: tarefas e descrição de oração) vira chamada a `pt_br_param_text`, que acha num enum o enum em português daquele campo e nele o texto da ficha; `enum` de conjunto/dificuldade vira `pt_br_task_enum` (cópias em português dos enums 3487/3488); `enum_string` (3400) das frases de nível vira `pt_br_enum_string` (cópia do enum 1477); cada frase fixa vira um script `pt_br_string` (inglês ou português); os scripts genéricos que recebem texto de hooks (38, dica de botão; 2381, link do guia; 1160, dicas do banco) ganham uma chamada a `pt_br_tooltip`; os meses (enum 950) vão pela mesma troca de `enum` dos conjuntos. Scripts novos 4106+, enums novos 3524-3527.
- **Rótulos fixos de qualquer janela** (texto do desenho que nenhum script reescreve): listados em `LABELS` de `CacheTranslationPatch` (o resto do texto fixo da janela vai em `LABELS_KEPT`, com o motivo), traduzidos em `sources/pt_labels.json`. Já entram: barra da Ilha (371), missão concluída (277), apresentação da missão (178), troca (335, 334), bônus de equipamento (667), itens na morte (102), PIN do banco (13, 14), loja (620). Não precisam do cache: só o servidor manda.
- **Servidor**: `CacheInterfaceTranslation.kt` manda os rótulos ao abrir qualquer janela que tenha linhas `label_` ("Descrição", "Parabéns!", "Aceitar"...) e redesenha a aba de tarefas quando o idioma muda; `Translation.kt` traduz nome da tarefa, dificuldade e conjunto quando entram no chat como `%n%` (só linhas `name_`, `difficulty_`, `area_`, `chat_` de `interface_pt_br`).
- **Cliente**: baixa sozinho os arquivos que mudaram ao abrir (compara com o servidor). Depois de gravar o cache: reiniciar o servidor **e** fechar/abrir o cliente. Cópia do cache original em `~/Documents/void-downloads/cache-antes-tarefas-pt/`.
- **Scripts conferidos**: `CHECKED_SCRIPTS` lista, para cada script das abas e do chat, o que fica em inglês de propósito (opções do botão direito, nomes de golpes e estilos, nomes de janela). O teste falha em qualquer outra frase com letras; assinaturas de hook e frases só de tags/números/pontuação passam sozinhas.
- **Testes**: `CacheTranslationPatchTest` (módulo `cache`: todo texto das janelas tem português, nenhum script lê o inglês direto depois do patch, o `data/cache` está com a versão atual) e `TaskTranslationTest` (módulo `game`: chat, rótulos das janelas, varc). Os testes do módulo `tools` **não rodam** na suíte (`buildSrc/shared.gradle.kts`), por isso a lógica fica no `cache`.

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
- Fora do alcance pelo servidor: menus do botão direito, nomes ao passar o mouse, rótulos fixos de interface, tela de login (cache/cliente). Dá para alcançar pelo cache, como na janela de tarefas; opções do botão direito continuam em inglês pelo guia de estilo.
- **Cache**: trocar o `data/cache` (cache novo do upstream) apaga a tradução das janelas: rode `translateInterfaces --args=patch` de novo (o teste `The cache has the current translation` avisa). Nunca grave com o servidor ligado (ele entrega o cache ao cliente).
- O decodificador de scripts do projeto lê texto como UTF-8 e o cache guarda Latin-1: script com acento não pode ser lido e regravado. O patch só reescreve scripts com texto ASCII e reconhece os seus pelo nome `pt_br_*`.
- `WorldTest.createClient` liga o cliente falso só às janelas; para testar variável enviada ao cliente, faça `(player.variables as PlayerVariables).client = client`.
- Rodar testes com o sistema em pt_BR: números saem "15.000" e testes que conferem "15,000" falham. Gradle repassa o idioma do daemon: use `--no-daemon` + `LANG`/`LC_ALL`.
- `WorldTest` com `loadNpcs = true`: os spawns só entram no mundo depois de um `tick()`.
- `TutorialIslandTranslationTest`/`LumbridgeTranslationTest` trocam `messageTranslator`/`missingTranslation`: restaure sempre no fim.

## Próximo trabalho: mensagens das habilidades

741 mensagens de chat (167 com variável no meio) e 1.776 falas (45 com variável) em `content/skill/**`, mais mensagens em `data/skill/**/*.tables.toml` e `*.recipes.toml`. Antes de traduzir, o Habdel quer: (1) quantas têm palavra inserida em inglês (item, peixe, minério) separadas das que só têm número, e as opções para isso; (2) o teste pronto antes da tradução (não há "passeio por região": um teste por habilidade fazendo as ações, como `TutorialIslandTranslationTest`, ou percorrendo as linhas das tabelas de dados).

## Pendências

- Rótulos ainda em inglês nas janelas do item 1 do mapa (`tools/translation/MAPA_JANELAS_CLIENTE.md`): mensagens do PIN do banco (o servidor manda em linhas cortadas pela largura, como a caixa da Ilha), ajuda do banco 767 (parágrafos que podem passar de 500 bytes), itens na morte (script 118), limite de riqueza da troca (script 694), linhas de bônus do equipamento (nomes de atributo). Recompensas de missão: só as de Lumbridge estão na tabela (entram juntas, separadas por `<br>`).

- Janela de tarefas: os outros conjuntos (Varrock, Falador, Seers' Village, Ardougne, Karamja, Fremennik, D&Ds) continuam em inglês: traduzir `missing_interfaces.json` depois de ampliar `AREAS` em `CacheTranslationPatch`.
- Janela de "subiu de nível": a lista do que se desbloqueia vem do **guia de habilidades** (scripts 977-1022, 1566, 1567, 2787, 2788, 3354, 3355; ~8.200 frases, muitas montadas em pedaços) e continua em inglês. O guia é um projeto à parte. O título "Task List" e as opções do botão direito ficam em inglês pelo guia.

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

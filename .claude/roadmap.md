# Roadmap do fork (Habdel-Edenfield/void-runescape-server)

Ideias e pendências deste fork. O roadmap do projeto original fica em `docs/Roadmap.md` (do GregHib, não editar para coisas do fork).

## Próximos passos

### Jogador escolher a distância de visão do mapa

**Situação hoje (2026-10-12):** o servidor manda a todos o mapa de 168 × 168 quadrados, o maior que o cliente aceita. A borda bege do cenário fica bem mais longe, mas a área carregada é ~2,6× maior que a original (104 × 104). Num PC mais fraco isso pesa: no PC do Habdel deu uma leve travada na primeira vez, até o fundo extra carregar. Só existe a configuração geral do servidor, `world.players.mapSize` em `game/src/main/resources/game.properties` (104, 120, 136 ou 168). Ela vale para todo mundo, inclusive o cliente web.

**Objetivo:** cada jogador escolhe "perto / médio / longe / muito longe" (104 / 120 / 136 / 168). Quem tem computador fraco fica no leve; quem quer profundidade escolhe longe.

**Feito:**
- A escolha fica salva na conta (variável `view_distance`, em tiles). Sem escolha, vale `world.players.mapSize`.
- `AccountManager.setup` usa a escolha ao criar o `Viewport`.
- O comando `view_distance` (alias `distancia`) abre um menu Perto / Médio / Longe / Muito longe. A troca manda o mapa de novo na hora, sem relogar (`RegionLoading.setViewDistance`).
- Comandos também podem ser digitados no chat comum com `::` (ex.: `::distancia`), porque o console (tecla `) não abre em teclado ABNT2 (`Chat.kt`).
- Testes em `RegionLoadingTest`.

**Falta:**
- **Botões na tela Graphics Options (742), para depois.** Jogador comum não usa comandos, e o lugar onde o Habdel procurou a opção é essa tela (ícone do monitor na aba Opções). Plano: seção "View Distance" abaixo de "Screen Sizing", com 4 botões no estilo MIN/LOW/MID/HIGH. O servidor trata os cliques chamando `setViewDistance` e realça o botão escolhido.
  - Por que ainda não dá (levantado pela sessão do Montador em 2026-10-12, lendo o cache): a 742 no cache é só a moldura (fundo, título, fechar). Todo o conteúdo é criado na hora de abrir pelos scripts de cliente 2594 → 2595 → 2596 (`cc_create`, sub-rotinas 1216-1220, 2918, 3451, 4041). A marcação do botão escolhido vem das preferências do cliente (instruções 3111, 6131), não de varp/varbit, e os cliques mudam a configuração no próprio cliente, sem avisar o servidor. Pôr uma seção ali exige escrever ou alterar CS2. O Void tem `ClientScriptEncoder`, mas não tem montador de texto para CS2, e o Montador só grava interfaces novas.
  - Também não há botão livre na aba Opções (261), e acrescentar um esbarra no mesmo limite de editar uma interface existente.
  - Alternativa já disponível: uma interface nova "view_distance" montada no Montador, com 4 botões e realce controlado pelo servidor (visibilidade de componente). Ficou de lado porque só abriria pelo `::distancia`, sem ganho real sobre o menu atual.
  - Retomar quando o Montador ou o Void conseguirem editar interfaces existentes ou CS2.

**Pontos a decidir:**
- Padrão para conta nova: mais leve (104) e o jogador aumenta, ou longe (168) e quem precisar diminui.
- Cliente web: no navegador costuma pesar mais. Talvez um padrão menor para quem entra pelo web.
- Lado do cliente do PC: a distância que ele desenha (`RENDER_DISTANCE_MULTIPLIER`) e a névoa (`CULLING_DISTANCE_MULTIPLIER`) em `~/Documents/void-client/client/src/Loader.java` são fixas. Num segundo passo podem acompanhar a escolha, para a névoa esconder a borda aos poucos em vez de cortar seco.
- A travada da primeira carga: medir se vem do carregamento do mapa (arquivos) ou do desenho, antes de tentar suavizar.

**Limites que não mudam:** 168 é o máximo que o cliente desta versão aceita. Pessoas e NPCs continuam aparecendo só até ~15 quadrados (limite do jeito que o jogo envia personagens), seja qual for a distância do cenário.

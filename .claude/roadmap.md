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
- Botões numa tela do jogo, porque jogador comum não usa comandos. A aba Opções não tem botão livre. Plano combinado com o Habdel: seção "View Distance" na tela Graphics Options (742), abaixo de "Screen Sizing", com 4 botões no estilo MIN/LOW/MID/HIGH. A tela é editada e gravada no cache pela sessão do Montador de Interfaces; o servidor trata os cliques chamando `setViewDistance` e realça o botão escolhido.

**Pontos a decidir:**
- Padrão para conta nova: mais leve (104) e o jogador aumenta, ou longe (168) e quem precisar diminui.
- Cliente web: no navegador costuma pesar mais. Talvez um padrão menor para quem entra pelo web.
- Lado do cliente do PC: a distância que ele desenha (`RENDER_DISTANCE_MULTIPLIER`) e a névoa (`CULLING_DISTANCE_MULTIPLIER`) em `~/Documents/void-client/client/src/Loader.java` são fixas. Num segundo passo podem acompanhar a escolha, para a névoa esconder a borda aos poucos em vez de cortar seco.
- A travada da primeira carga: medir se vem do carregamento do mapa (arquivos) ou do desenho, antes de tentar suavizar.

**Limites que não mudam:** 168 é o máximo que o cliente desta versão aceita. Pessoas e NPCs continuam aparecendo só até ~15 quadrados (limite do jeito que o jogo envia personagens), seja qual for a distância do cenário.

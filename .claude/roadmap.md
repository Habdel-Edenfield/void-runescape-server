# Roadmap do fork (Habdel-Edenfield/void-runescape-server)

Ideias e pendências deste fork. O roadmap do projeto original fica em `docs/Roadmap.md` (do GregHib, não editar para coisas do fork).

## Próximos passos

### Jogador escolher a distância de visão do mapa

**Situação hoje (2026-10-12):** o servidor manda a todos o mapa de 168 × 168 quadrados, o maior que o cliente aceita. A borda bege do cenário fica bem mais longe, mas a área carregada é ~2,6× maior que a original (104 × 104). Num PC mais fraco isso pesa: no PC do Habdel deu uma leve travada na primeira vez, até o fundo extra carregar. Só existe a configuração geral do servidor, `world.players.mapSize` em `game/src/main/resources/game.properties` (104, 120, 136 ou 168). Ela vale para todo mundo, inclusive o cliente web.

**Objetivo:** cada jogador escolhe "perto / médio / longe / muito longe" (104 / 120 / 136 / 168). Quem tem computador fraco fica no leve; quem quer profundidade escolhe longe.

**Caminho sugerido (só servidor, vale para o cliente do PC e o web):**
1. Guardar a escolha no save do jogador (uma variável, ex. `map_size`), com o padrão vindo de `world.players.mapSize`.
2. Em `AccountManager.setup`, usar a escolha do jogador em vez do padrão do servidor ao criar o `Viewport`.
3. Ao trocar, mudar `viewport.size` e mandar o mapa de novo na hora (`RegionLoading.updateRegion(player, initial = false, force = true)`), sem precisar relogar.
4. Primeiro um comando simples para testar (ex. `distancia 136`); depois uma opção numa tela de configurações do jogo (Opções gráficas ou uma tela nova feita no Montador de Interfaces).
5. Testes: trocar a opção manda o mapa com o tamanho novo; o padrão vale para conta nova.

**Pontos a decidir:**
- Padrão para conta nova: mais leve (104) e o jogador aumenta, ou longe (168) e quem precisar diminui.
- Cliente web: no navegador costuma pesar mais. Talvez um padrão menor para quem entra pelo web.
- Lado do cliente do PC: a distância que ele desenha (`RENDER_DISTANCE_MULTIPLIER`) e a névoa (`CULLING_DISTANCE_MULTIPLIER`) em `~/Documents/void-client/client/src/Loader.java` são fixas. Num segundo passo podem acompanhar a escolha, para a névoa esconder a borda aos poucos em vez de cortar seco.
- A travada da primeira carga: medir se vem do carregamento do mapa (arquivos) ou do desenho, antes de tentar suavizar.

**Limites que não mudam:** 168 é o máximo que o cliente desta versão aceita. Pessoas e NPCs continuam aparecendo só até ~15 quadrados (limite do jeito que o jogo envia personagens), seja qual for a distância do cenário.

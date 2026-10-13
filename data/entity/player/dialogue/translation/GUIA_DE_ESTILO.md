# Guia de estilo e glossário da tradução pt-BR

Vale para todo texto em `dialogue_pt_br.tables.toml` e `tutorial_island_pt_br.tables.toml`. As traduções-fonte ficam em `tools/translation/sources/`; a tabela é gerada a partir delas.

## Regras

- Português do Brasil natural, "você" informal, tom de fantasia medieval. Mantenha a personalidade de cada personagem (grosso, engraçado, antiquado) e as piadas quando der.
- Formas no masculino genérico ("bem-vindo", "pronto", "recém-chegado"), como o RuneScape brasileiro oficial de 2011.
- **Nomes como o jogo mostra, em inglês**: NPCs (Duke Horacio, Survival Expert), lugares (Lumbridge, Wizards' Tower, Lumbridge Castle), missões (Cook's Assistant, The Blood Pact), itens, habilidades e abas/interfaces (Quest Journal, Magic Spellbook). Motivo: menus e inventário continuam em inglês e o jogador precisa achar a coisa.
- **Item e habilidade**: na primeira menção em cada script, português com o inglês entre parênteses — "machado (hatchet)", "Cooking (culinária)". Depois, português ou inglês, o que soar natural.
- **Opções de clique, estilos e tipos de ataque** ficam em inglês entre aspas simples: 'Use', 'Talk-to', 'Attack', 'Wield', 'Prospect', 'Mine', 'Store'; Accurate (preciso), Aggressive (agressivo), Defensive (defensivo), Controlled (controlado), Rapid, Longrange, Stab, Lunge, Slash, Block.
- **Fala errada de propósito continua errada**: goblins ("Mim matar você, humano!", "General Bentnoze manda nós usar vermelha!"), sotaque caipira do Seth Groats e do Tool Leprechaun ("Dia, sô!", "cê", "num").
- **Erro de digitação do original**: a chave `en` mantém o erro (tem que casar com o jogo); o português sai correto ("Faether Aereck" → "Father Aereck").
- **Números no formato brasileiro** quando estão no texto: "1.940 moedas (coins)".
- **Marcadores** `%1%`, `%2%`, `%server%` e **tags** `<col=...>`, `<br>`, `<red>`, `<blue>`, `</col>` sempre presentes; pode mudar de lugar conforme a gramática.
- Só caracteres Latin-1: nada de aspas curvas, travessão (—), reticências de um caractere (…) ou emoji. Use ' " - e três pontos.
- Comprimento próximo do original (até ~1,5×). Caixas de NPC quebram linha sozinhas; a caixa de instruções da ilha tem linhas fixas e um teste mede a largura em pixels.
- **Palavras inseridas pelo jogo no meio da frase** (decisão do Habdel, "opção D", 2026-10-12):
  - Nome de item, criatura, NPC ou jogador fica em inglês, com construção neutra que não concorda gênero com a palavra: "Você come: %1%.", "Você capturou: %1%!", "Você conseguiu: %1%.".
  - Palavras de um grupo pequeno (tipo de runa, metal, canteiro de agricultura, direção do talismã) vão para o português com o inglês entre parênteses pelo **glossário de palavras inseridas**: no `pt`, escreva `%1g%` no lugar de `%1%` e o jogo troca "air" por "ar (air)", "iron" por "ferro (iron)", "herb patch" por "canteiro de ervas (herb patch)", "north-east" por "nordeste". O glossário fica nas linhas `word_` (seção "Glossary" de `gen_dialogue.py`); só as frases com `g` usam, porque a mesma palavra pode ser outra coisa ("rune" é metal em "bar of rune", mas é item em "rune essence").
  - Quando o próprio texto escreve o item (sem `%1%`), vale a regra de cima: português com o inglês entre parênteses ("Você lapida a safira (sapphire).").

## Glossário fixado

| Inglês | Português |
|---|---|
| hatchet | machado |
| pickaxe | picareta |
| tinderbox | pederneira |
| logs | toras |
| small fishing net | rede de pesca pequena |
| shrimp(s) | camarão / camarões |
| pot of flour / bucket of water | pote de farinha / balde de água |
| dough / bread dough | massa / massa de pão |
| range (cozinha) | fogão |
| furnace / anvil / hammer | fornalha / bigorna / martelo |
| ore / bar | minério / barra |
| tin / copper | estanho / cobre |
| dagger / sword / wooden shield | adaga / espada / escudo de madeira |
| shortbow / arrows | arco curto / flechas |
| sling / staff | funda / cajado |
| air rune / mind rune | runa de ar / runa da mente |
| shears / wool / ball of wool | tesoura de tosquia / lã / novelo |
| rake / seed dibber / spade / secateurs / watering can / trowel | ancinho / plantador / pá / tesoura de poda / regador / pazinha |
| hops | lúpulo |
| statuettes | estatuetas |
| inventory | inventário |
| bank / bank box / booth | banco / cofre / guichê |
| coins | moedas |
| quest / quest points | missão / pontos de missão |
| task(s) | tarefa(s) (Taskmaster, Task List, Achievement Diary ficam em inglês) |
| Task names | traduzidos, com o trocadilho quando der ("On the Run" = "Na correria"); nome de missão, NPC ou lugar fica em inglês. O chat usa o mesmo nome da janela |
| Beginner / Easy / Medium / Hard / Elite | Iniciante / Fácil / Média / Difícil / Elite (concordam com "tarefa"; "de dificuldade Média") |
| set (de tarefas) | conjunto ("do conjunto de Lumbridge/Draynor (Iniciante)") |
| Description / Hints / Rewards / Requirements (abas) | Descrição / Dicas / Recompensas / Requisitos |
| Pin / Back (botões) | Fixar / Voltar (a opção do botão direito continua 'Pin') |
| Skill Advance Guide | fica em inglês (nome de janela): "Abrir o Skill Advance Guide" |
| Congratulations! (subiu de nível) | Parabéns! — "Você subiu um nível em Cooking!", "Agora você está no nível 2." |
| Nomes de janela | ficam em inglês: Task List, Friends List, Ignore List, Clan Chat, Equipment Bonuses, Items Kept on Death, Bank PIN, Bank of RuneScape |
| Golpes e estilos de combate (Punch, Chop, Accurate...), nomes de oração e de feitiço | em inglês; o resto da frase em português ("XP de Attack", "Nível 01<br>Thick Skin<br>Aumenta Defence em 5%") |
| Tab 1 (banco) | Aba 1 |
| On / Off / Friends / Hide / All / Filter (filtros do chat) | Ligado / Desligado / Amigos / Esconder / Todos / Filtro |
| From / To (mensagem privada) | De / Para |
| melee / ranged / magic | corpo a corpo / à distância (longo alcance) / magia |
| rangers | arqueiros |
| the Duke | o Duke |
| the Thing (Fred the Farmer) | a Coisa |
| Danger Tutor (Doomsayer) | Tutor do Perigo |
| Click here to continue | Clique aqui para continuar |
| teleport (verbo e nome) | teletransportar / teletransporte (o nome do feitiço ou item fica em inglês: Varrock Teleport) |
| fairy ring | fica em inglês (o fairy ring, os fairy rings) |
| grapple | arpéu (grapple) |
| crossbow / bolts / limbs | besta / virotes / braços de besta |
| pouch (Runecrafting, Summoning) | bolsa (pouch) |
| pet | mascote |
| collection box | caixa de coleta |
| dungeon | masmorra (nome próprio em inglês: Brimhaven Dungeon); "calabouço" ficou nas recompensas de Lumbridge |
| Task names (conjuntos) | traduzidos com o trocadilho refeito em português ("Doing the Ironing" = "Passando a ferro"); nome de minijogo, missão, NPC e lugar fica em inglês |

Habilidades com glosa usada: Woodcutting, Firemaking, Fishing, Cooking (culinária), Mining e Smithing (mineração e forja), Prayer (oração), Magic (magia), Runecrafting (criação de runas), Fletching (confecção de arcos e flechas), Farming (agricultura), Crafting (artesanato).

**Inconsistência conhecida**: "leprechaun" virou "duende" nas falas da Lost City e ficou "leprechaun" nas do Tool Leprechaun e do Vinesweeper. Decidir um só.

## Guia de habilidades (Skill Advance Guide)

Vale para `tools/translation/sources/pt_guide_*.json` (janela do guia, 499, e lista do que se desbloqueia na janela de "subiu de nível", 741). Cada linha é um texto inteiro como o jogador vê: um nome da lista da esquerda, um título de menu, um parágrafo de introdução ou uma frase "You can now...".

- **Nome da lista da esquerda** (ao lado do ícone): nome de item, criatura, objeto, feitiço, oração, lugar ou missão **fica em inglês, exatamente como está** (é o nome do inventário e do botão direito). Traduza só o que está em volta: "Members: Bronze boots" = "Membros: Bronze boots"; "Hard leather body<br> (with 10 Defence)" = "Hard leather body<br> (com 10 em Defence)". Nome que é descrição, e não nome de coisa do jogo ("Possibility of stealing quadruple loot from a man", "Nurture basic evil tree saplings"), é traduzido.
- **Frases** ("You can now wield ..."): tudo em português. A coisa destacada em azul (`<col=000080>...</col>`), quando é item, criatura ou objeto comum, vai em português com o inglês do original entre parênteses, dentro do azul: "Agora você pode empunhar <col=000080>escudos quadrados de bronze (bronze square shields)</col>." (decisão do Habdel, 2026-10-13). Nome próprio (NPC, lugar, missão, minijogo, feitiço, oração, curse, familiar de Summoning, andar de Daemonheim) fica só em inglês.
- **Habilidades** em inglês, sem glosa (o guia repete muito): "(com 48 em Fishing e 15 em Strength)".
- **Padrões fixos** (iguais em todo o guia):

| Inglês | Português |
|---|---|
| Members: X | Membros: X |
| You can now ... / Members can now ... | Agora você pode ... / Membros agora podem ... |
| Members now have the Defence level required to wear X. (They also need level 40 Magic.) | Membros agora têm o nível de Defence para vestir X. (Também precisam de nível 40 em Magic.) |
| (with 10 Defence) / (with level 10 Defence) | (com 10 em Defence) / (com nível 10 em Defence) |
| (with 80 Defence and Constitution) | (com 80 em Defence e Constitution) |
| (after Tourist Trap) / (after starting Zogre Flesh Eaters) | (depois de Tourist Trap) / (depois de começar Zogre Flesh Eaters) |
| (Tier 3) | (categoria 3) |
| 2 bars / 1 bar | 2 barras / 1 barra |
| within Daemonheim / in Daemonheim | em Daemonheim |
| in Stealing Creation | no Stealing Creation |
| for your house / in your house | para a sua casa / na sua casa |
| floor 12 of Daemonheim (once the previous floors are complete) | o andar 12 de Daemonheim (depois de completar os andares anteriores) |
| Ancient Magicks / Lunar spellbook | grimório Ancient Magicks / grimório Lunar |

- **Verbos**: wear = vestir; wield = empunhar; use = usar; smith = forjar; construct/build = construir; make = fazer; craft = fabricar; fletch = confeccionar; infuse (bolsas de Summoning) = infundir; plant = plantar; cook = cozinhar; slay = matar; throw = arremessar; cast = lançar; light = acender; clean (ervas) = limpar; pickpocket = furtar; steal from = roubar de; mine = minerar; chop down = cortar; net = pegar com rede; catch ... barehanded = pegar ... com as mãos; fight = enfrentar; access = acessar; display = expor.
- **Itens frequentes**: platebody = peitoral de placas; chainbody = cota de malha; platelegs = calças de placas; plateskirt = saia de placas; full helm = elmo completo; medium helm = elmo médio; kiteshield = escudo pipa; square shield = escudo quadrado; defender = defensor; boots = botas; gauntlets = manoplas; gloves = luvas; vambraces = braçadeiras; coif = coifa; robe top/bottom = parte de cima/de baixo da túnica; hat = chapéu; sword = espada; longsword = espada longa; 2h sword = espada de duas mãos; scimitar = cimitarra; dagger = adaga; mace = maça; warhammer = martelo de guerra; battleaxe = machado de batalha; claws = garras; spear = lança; hasta = hasta; maul = marreta; rapier = florete; hatchet = machado; pickaxe = picareta; arrows = flechas; arrowtips = pontas de flecha; bolts = virotes; darts = dardos; javelins = azagaias; throwing axes = machados de arremesso; knives = facas; crossbow = besta; shortbow / longbow = arco curto / arco longo; staff/staves = cajado/cajados; logs = toras; ore = minério; bar = barra; tree = árvore; seed = semente; sapling = muda; pouch = bolsa; scroll = pergaminho; charm = amuleto (gold charm = amuleto dourado); shards = fragmentos; trap = armadilha; hide = couro; leather = couro curtido; essence = essência; implings = implings.
- **Metais e materiais**: bronze; iron = ferro; steel = aço; black = preto; white = branco; mithril; adamant = adamante; rune = runa (o metal); dragon = dragão; oak = carvalho; willow = salgueiro; teak = teca; maple = bordo; mahogany = mogno; yew = teixo; magic (árvore) = mágica. Metais e madeiras de Daemonheim (novite, bathus, marmaros, kratonite, fractite, zephyrium, argonite, katagon, gorgonite, promethium, primal; tangle gum, seeping elm, blood spindle, utuku, spinebeam, bovistrangler, thigat, corpsethorn, entgallow, grave creeper) ficam em inglês.

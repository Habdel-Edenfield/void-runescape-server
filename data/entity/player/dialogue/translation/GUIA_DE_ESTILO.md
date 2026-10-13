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
- Itens inseridos pelo jogo no meio da frase ficam em inglês; prefira construções neutras: "Você conseguiu: %1%." em vez de concordar gênero com uma palavra inglesa.

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

Habilidades com glosa usada: Woodcutting, Firemaking, Fishing, Cooking (culinária), Mining e Smithing (mineração e forja), Prayer (oração), Magic (magia), Runecrafting (criação de runas), Fletching (confecção de arcos e flechas), Farming (agricultura), Crafting (artesanato).

**Inconsistência conhecida**: "leprechaun" virou "duende" nas falas da Lost City e ficou "leprechaun" nas do Tool Leprechaun e do Vinesweeper. Decidir um só.

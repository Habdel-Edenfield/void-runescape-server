# Ferramentas de tradução (pt-BR)

Scripts Python (só biblioteca padrão) que mantêm a tradução do fork. Rode sempre a partir da raiz do repositório e com `python3 -I`.
O sistema todo está explicado em [`.claude/translation.md`](../../.claude/translation.md); o guia de estilo e o glossário ficam em
[`data/entity/player/dialogue/translation/GUIA_DE_ESTILO.md`](../../data/entity/player/dialogue/translation/GUIA_DE_ESTILO.md).

| Script | O que faz |
|---|---|
| `map_text.py` | Mapa de todo o texto do jogo e quanto está traduzido (JSON na saída padrão). |
| `build_report.py` | Gera a página HTML do mapa a partir do JSON. |
| `gen_dialogue.py` | Extrai as falas dos scripts Kotlin, junta com as traduções e **escreve a tabela** `dialogue_pt_br.tables.toml`. Com `--dump`, lista o que falta em `sources/missing.json`. |
| `skill_text.py` | Lê as frases das habilidades (código de `content/skill`, colunas de mensagem de `data/skill` e `eat_message` dos itens) do mesmo jeito que `SkillTranslationTest`; `gen_dialogue.py` usa para as seções `skill_*`/`data_*`. |
| `split_groups.py` | Divide `sources/missing.json` em grupos equilibrados para tradução em paralelo. |
| `validate.py` | Confere um arquivo de tradução contra o seu grupo (falta, caractere proibido, marcador ou tag perdidos, tamanho). |
| `gen_stages.py` | Escreve `tutorial_island_pt_br.tables.toml` (caixa de instruções da Ilha Tutorial). |
| `./gradlew :tools:translateInterfaces --args=dump\|table\|patch` | Janelas que o cliente desenha do cache (tarefas, subiu de nível, abas, chat, banco, loja): lista o que falta (`sources/missing_interfaces.json`), gera `interface_pt_br.tables.toml` a partir de `sources/pt_tasks*.json`, `pt_level_up*.json`, `pt_labels*.json` (rótulos fixos que o servidor manda) e `pt_ui_*.json` (abas, social/chat, banco/loja), e grava no cache (servidor desligado). Ver `.claude/translation.md`. |
| `sources/` | Fontes da tradução: `<regiao>_scripts.json` (lista de scripts da região) e `pt_*.json` (en → pt). A tabela é gerada a partir daqui; edite aqui, não na tabela. |

## Traduzir uma região, na ordem

```bash
# 1. Mapear: ver NPCs e scripts da região
python3 -I tools/translation/map_text.py "$PWD" > /tmp/map.json

# 2. Listar os scripts da região em sources/<regiao>_scripts.json
#    (hoje gen_dialogue.py lê sources/lumbridge_scripts.json; para outra região, acrescente a leitura dela em NPCS)

# 3. Extrair o que falta e dividir em grupos
python3 -I tools/translation/gen_dialogue.py "$PWD" tools/translation/sources --dump
python3 -I tools/translation/split_groups.py tools/translation/sources 6

# 4. Traduzir: um assistente por group_N.json, escrevendo sources/pt_<regiao>_N.json (prompt em .claude/translation.md)

# 5. Validar cada grupo
python3 -I tools/translation/validate.py tools/translation/sources/group_1.json tools/translation/sources/pt_<regiao>_1.json

# 6. Gerar a tabela (falha se faltar tradução ou sobrar tradução sem fala)
python3 -I tools/translation/gen_dialogue.py "$PWD" tools/translation/sources

# 7. Teste de passeio da região (tem que falhar se uma tradução for apagada de propósito)
LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 ./gradlew :game:test --tests "content.entity.player.dialogue.LumbridgeTranslationTest"
```

`missing.json`, `missing_interfaces.json` e `group_*.json` são intermediários e ficam fora do Git. `gen_dialogue.py` ignora os `pt_tasks*`/`pt_level_up*`/`pt_labels*`/`pt_ui*`, que são das janelas do cache.

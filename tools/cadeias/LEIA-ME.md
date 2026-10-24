# Cadeias de produção

Duas páginas, geradas dos dados do Void:

- **O guia** (`cadeias-guia.html`, feito por `guia.py`): uma trilha de cartões por capítulo, na casca do Organizador do Hardgaard (o "trello"; `trilha.css` copia as regras de lá). Cada cartão é um passo: o que entra, a janela do jogo que aparece (desenhada dos sprites e fontes do cache, com os itens reais dentro) e o que sai. Por enquanto só o capítulo do bronze: fornalha, bigorna com as 26 peças, o conjunto vestido na janela de equipamento, hastes e flechas, virotes, dardos e besta.
- **O catálogo** (`cadeias-de-producao.html`, feito por `gerar.py`): a página antiga, no tema grafite, com todos os itens que alguma habilidade transforma em outro item: o que sai do mundo, o que é feito disso e o produto final, em níveis de cima para baixo. Em cada item, o ícone do jogo, o nome, o que precisa para fazer, o que ele vira e, nos equipáveis, os bônus da janela de equipamento. No fim, a explicação de como este servidor calcula uma luta, com um exemplo.

As duas ficam em `~/Documents/void-downloads/`: um arquivo só cada, com as imagens dentro, que abre direto no Chrome. Só a letra vem da internet. O dono ainda vai decidir se o catálogo fica no fim do guia ou sai.

## Regerar

Na raiz do Void:

```bash
bash tools/cadeias/gerar.sh
```

O script faz três coisas:

1. `./gradlew :tools:cadeias:cadeiasExport`: tira do cache o nome de cada item, os bônus de equipamento (os mesmos parâmetros que a janela de equipamento lê) e os ingredientes das bolsas de Summoning. Sai em `~/Documents/void-downloads/cadeias/itens.json`.
2. `./gradlew :tools:icon:dumpItemSprites` (módulo do GregHib, o mesmo do Grand Exchange do site): desenha o ícone de cada item do modelo 3D, do jeito que o cliente desenha, em 36×32 e em 72×64. Sai em `~/Documents/void-downloads/cadeias/icones/`. Só roda quando a pasta não existe; apague-a quando o cache mudar.
3. `gerar.py` e `guia.py`: leem `data/` (receitas, tabelas, itens), o `itens.json`, os ícones e a exportação do Montador de Interfaces (sprites, fontes e interfaces do cache, para desenhar a aba de estilos e a janela de equipamento) e escreve o HTML.

Precisa de Python 3 com Pillow e da exportação do Montador em `~/Documents/void-montador-de-interfaces/cache/jogo/exportado` (o Montador a faz ao abrir; à mão: `./gradlew :tools:montador:montadorExport -Pargs="data/cache/ data/ <pasta>"`).

## De onde vêm as regras

- Receitas: todos os `*.recipes.toml` de `data/` que têm habilidade (ou estão numa pasta de habilidade). Ficam de fora os arquivos de despejar poção, encher saco e tingir capa, e as receitas de missão sem habilidade.
- Tabelas: `bars`, `smithing` (com os botões da bigorna em `smithing.ifaces.toml`), `fletchables`, `fletching_unf`, `arrow_shafts`, `fletching_bolts`, `fletching_darts`, `cooking`, `dairy`, `runes`, `combination_runes`, `tiaras`, `jewellery`, `silver_casting`, `spinning`, `weaving`, `firing`, `pottery`, `tanning`, `herbs`, `jewellery_enchant`, `enchant_bolts`, `plank_make`.
- Regras que estão só no código, repetidas em `dados.py` com o nome do arquivo: balas de canhão (`Cannonballs.kt`), vidro derretido (`MoltenGlass.kt`), flechas sem ponta (`HeadlessArrows.kt`), colheita das sementes (`FarmingPatchPick.kt` e os `harvest` dos canteiros), muda que vira árvore jovem (`Farming.kt`), bolsas e pergaminhos de Summoning (`SummoningCrafting.kt`, parâmetros do cache).
- Combate: `Hit.kt`, `Damage.kt`, `Bonus.kt`, `Equipment.kt`, `EquipmentBonuses.kt`, `Weapon.kt`, `Prayer.kt` em `game/src/main/kotlin/content/`.

O produto de uma receita é o primeiro item adicionado que não é vasilhame devolvido (balde, tigela, frasco: os itens que aparecem como `empty` de outro) nem regador. Ferramentas (`requires`) não entram na cadeia.

## Arquivos

| Arquivo | O que é |
|---|---|
| `gerar.sh` | o comando único |
| `guia.py` | monta o guia em trilha (o formato aprovado em 13/10) |
| `trilha.css` | a casca do Organizador do Hardgaard, copiada em 13/10 |
| `gerar.py` | monta o catálogo |
| `dados.py` | lê `data/` e monta os passos e os níveis |
| `groml.py` | lê o formato de configuração do Void |
| `imagens.py` | desenha janelas do jogo a partir do cache (as contas do cliente 634, como no Montador) |
| `build.gradle.kts`, `src/` | o exportador do cache (`cadeiasExport`) |

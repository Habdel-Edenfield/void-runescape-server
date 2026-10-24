# Mapa das janelas que o cliente desenha em inglês

Relatório completo (página): https://claude.ai/artifact/A6NgmQ8YiF543ttWpMhuyq
Feito em 2026-10-12, só leitura, a partir do cache 634 original (antes do patch das tarefas).
Programas para refazer o mapa: [`mapa_cliente/`](mapa_cliente/README.md).

## Números

- 1.065 interfaces no cache; 413 têm nome no servidor (`*.ifaces.toml`); 333 delas mostram texto em inglês vindo do cache. As 652 sem nome o Void não abre, fora a tela de login (596) e o lobby (906), que o cliente desenha sozinho.
- Caminho do jogador novo (≈ 70 janelas): cerca de 900 frases, fora os projetos grandes; umas 450 aparecem nas primeiras horas (abas, chat, banco, loja, Ilha).

## Já resolvido

- Tarefas 917, 1056, 1055 (só Lumbridge/Draynor e Unstable Foundations: 152 de 509 tarefas). Faltam 357 tarefas, ≈ 1.000 textos.
- Caixa de instruções da Ilha (372) e caixa "subiu de nível" (740): servidor.
- Pergaminho de nível (741): pronto na sessão "Tradução da janela de tarefas", menos a lista de desbloqueios (vem do guia de habilidades). Commit 791638a.

## Sugestão de ordem

1. Rótulos fixos que o servidor pode mandar ao abrir (modelo `CacheInterfaceTranslation.kt`), ≈ 90 frases: barra da Ilha 371 ("Tutorial Island Progress", "Please follow the instructions below..."), missão concluída 277, apresentação da missão 178, troca 335/334, equipamento 667, itens na morte 102, PIN do banco 13/14/767, loja 620.
2. Ampliar o patch do cache (`CacheTranslationPatch.kt`), ≈ 250 a 300 frases: Logout 182 (script 1104), Options 261, Stats 320, Quest Journal 190, Friends/Ignore/Clan 550/551/589, Combat Styles 884 (só ≈ 13 frases; estilos e golpes ficam em inglês), Prayer 271 (50 descrições em structs, param 734), banco 762, loja 620, chat 137/751 (scripts divididos 84, 89, 178).
3. Decidir no guia: nomes de emotes, de feitiços, de penteados/roupas da criação de personagem, verbos do escolher quantidade ("Cook All"), "Tab 1" do banco.
4. Tela de login e frases fixas do cliente (ver achado abaixo).
5. Projetos grandes: outros conjuntos de tarefas; guia de habilidades 499 (≈ 9.150 frases, scripts 977–1022, 1566, 1567, 2787, 2788, 3354, 3355); músicas (≈ 970 frases "onde destrava", enums 952 e 1349); chat rápido (1.072 frases + 195 menus, índice 24, lido pelo Java do cliente); dicas de feitiços (≈ 355, script 6) e de familiares (≈ 150, script 2472).

## Achados

- **O cliente já tem português**: `Class274` do `void-client` guarda 162 frases fixas em inglês, alemão, francês e português (RuneScape oficial de 2011), escolhidas pelo parâmetro `lang` (3 = português; o `Loader` fixa 0). Cuidado: também traduz opções que o guia deixa em inglês ("Walk here", "Use", "Examine", "Cancel"), vale para o cliente inteiro e muda 36 trechos de script do cache que leem o idioma (instrução CS2 3335; ex.: o script 3977 só escreve "Pin"/"Back" quando o idioma é inglês).
- **Tela de login**: aparece antes de o servidor mandar o varc 2000, então não dá para escolher o idioma por jogador ali; só pelo idioma do próprio cliente.
- **Outros scripts de dica** além do 38 e do 2381: 6 (feitiços), 724 (ícone de área), 1160 (banco, apresentação da missão, equipamento), 2472 (invocação), 647 e 649 (Grand Exchange).
- Examinar itens/NPCs/objetos (≈ 22.700 linhas em `data/**/*.items.toml`, `*.npcs.toml`, `*.objs.toml`) é texto do servidor, não do cliente.

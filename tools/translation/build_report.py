"""Builds the translation map report (HTML) from map.json. Usage: python3 -I build_report.py map.json out.html [lumbridge_status]"""
import html
import json
import sys
from collections import defaultdict

data = json.load(open(sys.argv[1]))
out = sys.argv[2]
lumbridge_note = sys.argv[3] if len(sys.argv) > 3 else "Tradução em andamento"

KINGDOMS = {
    "misthalin": "Misthalin",
    "asgarnia": "Asgarnia",
    "kharidian_desert": "Kharidian Desert",
    "morytania": "Morytania",
    "kandarin": "Kandarin",
    "karamja": "Karamja",
    "wilderness": "Wilderness",
    "fremennik_province": "Fremennik Province",
    "tirannwn": "Tirannwn",
    "troll_country": "Troll Country",
    "realm": "Outros reinos",
}
OUTSIDE = {
    "quest": "Missões",
    "minigame": "Minijogos",
    "activity": "Atividades",
    "social": "Social e comércio",
    "skill": "Habilidades",
    "entity": "Geral",
}
PACKAGES = {
    "area": ("NPCs e lugares", "Falas dos NPCs de cada cidade e região."),
    "skill": ("Habilidades", "Mensagens de cortar árvore, pescar, minerar, lutar, magia..."),
    "quest": ("Missões", "Diálogos e mensagens das missões."),
    "minigame": ("Minijogos", "Castle Wars, Pest Control, Fist of Guthix..."),
    "activity": ("Atividades e eventos", "Eventos aleatórios, Penguin Hide and Seek, estrelas cadentes..."),
    "entity": ("Sistemas gerais", "Banco, lojas, morte, equipamento, comandos."),
    "social": ("Social", "Amigos, clã, troca, Grand Exchange."),
}
DATA = {
    "items.toml": "Itens",
    "npcs.toml": "NPCs",
    "objs.toml": "Objetos do cenário",
    "tables.toml": "Tabelas de habilidades",
    "recipes.toml": "Receitas (combinar itens)",
    "combat.toml": "Combate",
}
DONE = {"misthalin/tutorial_island": "Concluída", "misthalin/lumbridge": "Concluída"}
WIP = {}


def fmt(n):
    return f"{n:,}".replace(",", ".")


def pretty(slug):
    return " ".join(part.capitalize() if part not in ("of", "the") else part for part in slug.replace("_", " ").split())


def bar(done, total):
    pct = 0 if total == 0 else round(100 * done / total)
    full = " full" if total and done >= total else ""
    return f'<span class="bar{full}" role="img" aria-label="{pct}% traduzido"><span style="width:{pct}%"></span></span>'


totals = data["totals"]
examine_total = sum(v["examine"]["total"] for v in data["data"].values())
data_messages = sum(v["messages"]["total"] for v in data["data"].values())
grand = totals["dialogue"]["total"] + totals["messages"]["total"] + examine_total + data_messages
grand_done = totals["dialogue"]["translated"] + totals["messages"]["translated"]

groups = defaultdict(list)
for region in data["regions"]:
    top = region["region"].split("/")[0]
    groups[top].append(region)


def region_rows(regions):
    rows = []
    for r in sorted(regions, key=lambda r: -r["dialogue"]["total"]):
        if r["dialogue"]["total"] == 0 and r["messages"]["total"] == 0:
            continue
        name = r["region"].split("/", 1)[1] if "/" in r["region"] else r["region"]
        status = ""
        if r["region"] in DONE:
            status = f'<span class="chip done">{DONE[r["region"]]}</span>'
        elif r["region"] in WIP:
            status = f'<span class="chip wip">{WIP[r["region"]]}</span>'
        d = r["dialogue"]
        rows.append(
            f'<tr><th scope="row">{html.escape(pretty(name))} {status}</th>'
            f'<td class="num">{fmt(r["talking_npcs"])}</td>'
            f'<td class="num">{fmt(d["total"])}</td>'
            f'<td class="num">{fmt(d["translated"])}</td>'
            f'<td class="prog">{bar(d["translated"], d["total"])}</td></tr>'
        )
    return "\n".join(rows)


def section(keys, names):
    blocks = []
    ordered = sorted(keys, key=lambda k: -sum(r["dialogue"]["total"] for r in groups[k]))
    for key in ordered:
        regions = groups[key]
        total = sum(r["dialogue"]["total"] for r in regions)
        done = sum(r["dialogue"]["translated"] for r in regions)
        if total == 0:
            continue
        opened = " open" if key == "misthalin" else ""
        blocks.append(
            f'<details class="kingdom"{opened}><summary><span class="kname">{names[key]}</span>'
            f'<span class="kmeta">{len(regions)} {"região" if len(regions) == 1 else "regiões"} · {fmt(total)} falas</span>{bar(done, total)}</summary>'
            f'<div class="table-wrap"><table><thead><tr><th scope="col">Região</th><th scope="col" class="num">NPCs que falam</th>'
            f'<th scope="col" class="num">Falas</th><th scope="col" class="num">Traduzidas</th><th scope="col">Progresso</th></tr></thead>'
            f'<tbody>{region_rows(regions)}</tbody></table></div></details>'
        )
    return "\n".join(blocks)


package_totals = defaultdict(lambda: [0, 0, 0, 0])
for p in data["packages"]:
    top = p["package"].split("/")[0]
    if top not in PACKAGES:
        continue
    package_totals[top][0] += p["dialogue"]["total"]
    package_totals[top][1] += p["dialogue"]["translated"]
    package_totals[top][2] += p["messages"]["total"]
    package_totals[top][3] += p["messages"]["translated"]

package_rows = []
for key, (d, dt, m, mt) in sorted(package_totals.items(), key=lambda kv: -(kv[1][0] + kv[1][2])):
    label, hint = PACKAGES[key]
    package_rows.append(
        f'<tr><th scope="row">{label}<small>{hint}</small></th><td class="num">{fmt(d)}</td><td class="num">{fmt(m)}</td>'
        f'<td class="num">{fmt(dt + mt)}</td><td class="prog">{bar(dt + mt, d + m)}</td></tr>'
    )

data_rows = []
for kind, label in DATA.items():
    v = data["data"].get(kind)
    if not v:
        continue
    data_rows.append(
        f'<tr><th scope="row">{label}</th><td class="num">{fmt(v["examine"]["total"])}</td><td class="num">{fmt(v["messages"]["total"])}</td></tr>'
    )

page = f"""<title>Mapa da tradução Void</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Alegreya+SC:wght@500;700&family=Alegreya+Sans:wght@400;500;700&display=swap">
<style>
/* Layout: one reading column; geography (kingdom > region) is the structure, each row carries its own progress bar */
:root {{
  --bg: #eceeea;
  --surface: #fbfbf8;
  --fg: #1e2523;
  --muted: #5b6662;
  --line: #d3d8d3;
  --accent: #8c6a12;
  --accent-soft: #e9dfc3;
  --done: #2e7a4e;
  --done-soft: #d6eadc;
  --track: #dde1dc;
  --display: "Alegreya SC", "Palatino Linotype", Georgia, serif;
  --body: "Alegreya Sans", "Segoe UI", system-ui, sans-serif;
}}
@media (prefers-color-scheme: dark) {{
  :root:not([data-theme="light"]) {{
    --bg: #151918; --surface: #1d2321; --fg: #e5e8e3; --muted: #9ba7a2; --line: #2d3532;
    --accent: #d9b356; --accent-soft: #3a3220; --done: #63c08b; --done-soft: #1f3729; --track: #2b3330;
    color-scheme: dark;
  }}
}}
:root[data-theme="dark"] {{
  --bg: #151918; --surface: #1d2321; --fg: #e5e8e3; --muted: #9ba7a2; --line: #2d3532;
  --accent: #d9b356; --accent-soft: #3a3220; --done: #63c08b; --done-soft: #1f3729; --track: #2b3330;
  color-scheme: dark;
}}
body {{ background: var(--bg); color: var(--fg); font: 400 17px/1.55 var(--body); }}
.page {{ max-width: 920px; margin: 0 auto; padding-inline: 20px; padding-block: 40px 64px; display: grid; gap: 40px; }}
h1, h2 {{ font-family: var(--display); font-weight: 700; text-wrap: balance; margin: 0; letter-spacing: 0.01em; }}
h1 {{ font-size: clamp(2rem, 5vw, 2.8rem); line-height: 1.1; }}
h2 {{ font-size: 1.55rem; }}
p {{ margin: 0; max-width: 65ch; }}
.lede {{ color: var(--muted); font-size: 1.1rem; }}
.eyebrow {{ font: 700 0.78rem/1 var(--body); text-transform: uppercase; letter-spacing: 0.12em; color: var(--accent); }}
header {{ display: grid; gap: 12px; }}
section {{ display: grid; gap: 16px; }}
.summary {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; }}
.stat {{ background: var(--surface); border: 1px solid var(--line); border-radius: 8px; padding: 16px; display: grid; gap: 4px; min-width: 0; }}
.stat b {{ font: 700 1.9rem/1.1 var(--display); font-variant-numeric: tabular-nums; }}
.stat span {{ color: var(--muted); font-size: 0.95rem; }}
.overall {{ display: grid; gap: 8px; }}
.overall .bar {{ width: 100%; height: 12px; }}
.bar {{ display: inline-block; width: 120px; max-width: 100%; height: 8px; border-radius: 4px; background: var(--track); overflow: hidden; vertical-align: middle; }}
.bar > span {{ display: block; height: 100%; min-width: 2px; background: var(--accent); }}
.bar.full > span {{ background: var(--done); }}
.chip {{ display: inline-block; font: 700 0.72rem/1 var(--body); text-transform: uppercase; letter-spacing: 0.08em; padding: 4px 7px; border-radius: 4px; margin-left: 6px; vertical-align: 2px; }}
.chip.done {{ background: var(--done-soft); color: var(--done); }}
.chip.wip {{ background: var(--accent-soft); color: var(--accent); }}
.kingdom {{ background: var(--surface); border: 1px solid var(--line); border-radius: 8px; }}
.kingdom + .kingdom {{ margin-top: -4px; }}
.kingdom summary {{ cursor: pointer; display: flex; flex-wrap: wrap; align-items: center; gap: 8px 16px; padding: 14px 16px; list-style: none; }}
.kingdom summary::-webkit-details-marker {{ display: none; }}
.kingdom summary::before {{ content: "▸"; color: var(--accent); transition: transform 0.15s; }}
.kingdom[open] summary::before {{ transform: rotate(90deg); }}
.kingdom summary:focus-visible {{ outline: 2px solid var(--accent); outline-offset: 2px; border-radius: 8px; }}
.kname {{ font: 700 1.2rem/1.2 var(--display); flex: 1 1 auto; }}
.kmeta {{ color: var(--muted); font-variant-numeric: tabular-nums; }}
.table-wrap {{ overflow-x: auto; border-top: 1px solid var(--line); }}
table {{ width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; }}
th, td {{ text-align: left; padding: 9px 16px; border-bottom: 1px solid var(--line); vertical-align: middle; }}
tbody tr:last-child th, tbody tr:last-child td {{ border-bottom: 0; }}
thead th {{ font: 700 0.75rem/1.2 var(--body); text-transform: uppercase; letter-spacing: 0.08em; color: var(--muted); }}
tbody th {{ font-weight: 500; }}
th small {{ display: block; color: var(--muted); font-weight: 400; font-size: 0.88rem; }}
.num {{ text-align: right; white-space: nowrap; }}
.prog {{ width: 140px; }}
.panel {{ background: var(--surface); border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }}
.notes {{ display: grid; gap: 10px; padding-left: 1.2em; margin: 0; max-width: 70ch; }}
.order {{ display: grid; gap: 10px; padding-left: 1.4em; margin: 0; max-width: 70ch; }}
.order li::marker {{ color: var(--accent); font-weight: 700; }}
footer {{ color: var(--muted); font-size: 0.92rem; }}
@media (max-width: 560px) {{
  th, td {{ padding: 8px 10px; }}
  .prog {{ width: 80px; }}
  .bar {{ width: 70px; }}
}}
@media (prefers-reduced-motion: reduce) {{ .kingdom summary::before {{ transition: none; }} }}
</style>
<div class="page">
  <header>
    <span class="eyebrow">Void · RuneScape 2011 em português</span>
    <h1>Mapa da tradução</h1>
    <p class="lede">Todo o texto que o servidor envia ao jogador, região por região, e quanto já está em português. A Ilha Tutorial e Lumbridge estão prontas.</p>
  </header>

  <section aria-labelledby="total">
    <h2 id="total">O tamanho do trabalho</h2>
    <div class="summary">
      <div class="stat"><b>{fmt(totals["dialogue"]["total"])}</b><span>falas de diálogo nos scripts</span></div>
      <div class="stat"><b>{fmt(totals["messages"]["total"])}</b><span>mensagens do chat no código</span></div>
      <div class="stat"><b>{fmt(examine_total)}</b><span>descrições de "Examine" (itens, NPCs, objetos)</span></div>
      <div class="stat"><b>{fmt(data_messages)}</b><span>mensagens nos arquivos de dados</span></div>
    </div>
    <div class="overall">
      <p><b>{fmt(grand_done)}</b> de cerca de <b>{fmt(grand)}</b> textos traduzidos: a Ilha Tutorial, Lumbridge e as mensagens gerais que aparecem nelas.</p>
      {bar(grand_done, grand)}
    </div>
  </section>

  <section aria-labelledby="regions">
    <h2 id="regions">Por região do mapa</h2>
    <p>Falas dos NPCs que aparecem em cada região, contando os scripts que respondem a eles, inclusive os gerais (banqueiros, lojas). Por isso um mesmo script compartilhado conta em mais de uma região.</p>
    {section([k for k in KINGDOMS if k in groups], KINGDOMS)}
  </section>

  <section aria-labelledby="outside">
    <h2 id="outside">Fora do mapa</h2>
    <p>NPCs que só aparecem em missões, minijogos e atividades, em áreas próprias.</p>
    {section([k for k in OUTSIDE if k in groups], OUTSIDE)}
  </section>

  <section aria-labelledby="types">
    <h2 id="types">Por tipo de conteúdo</h2>
    <p>O código inteiro, sem repetir nada. Aqui aparecem as mensagens do chat das habilidades, que o jogador vê o tempo todo em qualquer lugar.</p>
    <div class="panel table-wrap"><table>
      <thead><tr><th scope="col">Conteúdo</th><th scope="col" class="num">Falas</th><th scope="col" class="num">Chat</th><th scope="col" class="num">Traduzidos</th><th scope="col">Progresso</th></tr></thead>
      <tbody>{"".join(package_rows)}</tbody>
    </table></div>
  </section>

  <section aria-labelledby="data">
    <h2 id="data">Textos nos arquivos de dados</h2>
    <p>Descrições que aparecem ao clicar em "Examine" e mensagens guardadas nas tabelas. Elas já passam pelo gancho do chat; falta só traduzir.</p>
    <div class="panel table-wrap"><table>
      <thead><tr><th scope="col">Arquivo</th><th scope="col" class="num">Examine</th><th scope="col" class="num">Mensagens</th></tr></thead>
      <tbody>{"".join(data_rows)}</tbody>
    </table></div>
  </section>

  <section aria-labelledby="known">
    <h2 id="known">Pendências conhecidas</h2>
    <ul class="notes">
      <li>Uma fala da Xenia (a história do Dragith Nurn) tem mais de 500 bytes, acima do limite das tabelas do servidor. Continua em inglês até o limite ser revisto.</li>
      <li>Algumas falas chegam ao jogador por caminhos que a leitura do código não enxerga (listas de conselhos, funções auxiliares). Os testes de passeio de cada região pegam esses casos rodando o jogo.</li>
      <li>Palavras que o próprio jogo insere no meio da frase (como "sir", "inventory", nomes de itens) continuam em inglês dentro da frase em português.</li>
    </ul>
  </section>

  <section aria-labelledby="reach">
    <h2 id="reach">O que este método não alcança</h2>
    <ul class="notes">
      <li>Menus do botão direito e o texto ao passar o mouse ("Walk here", "Talk-to", nomes de NPCs, itens e objetos).</li>
      <li>Rótulos fixos das telas: nomes das abas, painel de opções, nomes das habilidades, botões do chat, tela de login.</li>
      <li>Mensagens do próprio cliente, como "Loading - please wait".</li>
    </ul>
    <p>Essas partes vêm do cache da Jagex ou do código do cliente. Traduzi-las exigiria editar o cache ou o cliente, que são outros projetos.</p>
  </section>

  <section aria-labelledby="next">
    <h2 id="next">Ordem sugerida</h2>
    <ol class="order">
      <li><b>Mensagens das habilidades</b>: aparecem em qualquer lugar, para todo jogador ({fmt(package_totals["skill"][2])} mensagens e {fmt(package_totals["skill"][0])} falas).</li>
      <li><b>Draynor e Varrock</b>: vizinhas de Lumbridge e as primeiras cidades grandes.</li>
      <li><b>Falador, Al Kharid e Port Sarim</b>: completam a área inicial.</li>
      <li><b>Descrições de "Examine"</b> dos itens mais comuns, por último, porque o volume é grande e são opcionais para jogar.</li>
    </ol>
  </section>

  <footer>
    <p>Contagens de textos únicos tiradas do código e dos dados do fork em 12/10/2026. Falas com variáveis (nomes, números) contam uma vez. A conta de "traduzidas" usa a tabela de tradução atual.</p>
  </footer>
</div>
"""
open(out, "w").write(page)
print("written", out)

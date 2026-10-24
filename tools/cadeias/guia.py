#!/usr/bin/env python3
"""O guia das cadeias de produção: uma trilha de cartões por capítulo, na casca do Organizador do Hardgaard.

Cada cartão é um passo: o que entra, a janela do jogo que aparece (desenhada dos sprites do cache, com os itens reais
dentro), o que sai. Usa os mesmos dados, ícones e desenhos de janela de gerar.py; só a página é outra.
Por enquanto só o capítulo do bronze.
"""
import argparse
import base64
import datetime
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

import imagens  # noqa: E402
from dados import TIPOS_BIGORNA, Dados  # noqa: E402
from gerar import AQUI, CASA, BONUS, Pagina, e, formata_bonus, valor_bonus  # noqa: E402
from groml import ler  # noqa: E402

TEXTO_FAZER = "Choose how many you wish to make, then<br>click on the chosen item to begin."
LARANJA = 0xFF981F   # Colours.ORANGE, a cor dos textos das janelas
VERDE = 0x00FF00     # Colours.GREEN: tem barras suficientes
BRANCO = 0xFFFFFF    # Colours.WHITE: tem o nível

# os quatro caminhos pontilhados dos cartões do Organizador / Livro, um por cartão
CAMINHOS = [
    '<path d="M-128 -80 V-16 Q-128 0 -112 0 H-28"/><path d="M28 0 H80 Q96 0 96 16 V80"/>',
    '<path d="M0 -80 V-28"/><path d="M-28 0 H-112 Q-128 0 -128 16 V80"/>',
    '<path d="M-130 80 V16 Q-130 0 -114 0 H-28"/><path d="M28 0 H200"/>',
    '<path d="M0 80 V28"/><path d="M28 0 H112 Q128 0 128 -16 V-80"/>',
]
LUPA = ('<svg width="14" height="14" viewBox="0 0 16 16" aria-hidden="true"><circle cx="7" cy="7" r="5" fill="none" stroke="currentColor" stroke-width="1.6"/>'
        '<path d="M11 11l3.5 3.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>')
LIGA = '<div class="liga" aria-hidden="true"><svg><path d="M12 0 V56"/></svg></div>'
# a bandeira da aba, em pixel (o Organizador usa as bandeiras do automapa; esta é um quadrado de 11 px na cor do acento)
BANDEIRA = ("data:image/svg+xml;utf8," + "<svg xmlns='http://www.w3.org/2000/svg' width='11' height='11' viewBox='0 0 11 11'>"
            "<path d='M2 1h7v6H4v3H2z' fill='%23f4693a'/></svg>")


def caminho(chave, k=1.0):
    h = 0
    for ch in chave:
        h = (h * 31 + ord(ch)) & 0xFFFFFFFF
    d = CAMINHOS[h % len(CAMINHOS)]
    if k != 1.0:
        import re
        d = re.sub(r"-?\d+(?:\.\d+)?", lambda m: str(round(float(m.group(0)) * k)), d)
    return d


def xp(v):
    return f"{v:g}".replace(".", ",") + " xp"


class Guia(Pagina):
    def __init__(self, d, icones, grafite, mont):
        super().__init__(d, icones, grafite, mont)
        self.smithing_ids = {}
        for nome, campos in ler(os.path.join(d.data, "skill", "smithing", "smithing.ifaces.toml")).items():
            if nome.startswith("smithing.") and "id" in campos:
                self.smithing_ids[nome[len("smithing."):]] = campos["id"]

    # ------------------------------------------------------------ imagens
    def icone_jogo(self, sid):
        """O ícone de 36x32, como aparece dentro das janelas."""
        return Image.open(os.path.join(self.icones, f"{self.d.itens[sid]['id']}.png")).convert("RGBA")

    def icone1x(self, sid):
        return self.icone(sid, hd=False)

    def janela_fazer(self, itens, texto=TEXTO_FAZER, amount="1"):
        """A caixa de diálogo "quantos fazer" (interfaces 905 e 916) no lugar da conversa, 504x124, com os itens."""
        W, H = 504, 124
        caixa = imagens.compor(self.mont, 905, W, H)
        topo = imagens.compor(self.mont, 916, W - 2, 34, textos={1: texto, 17: amount}, tamanhos={1: (300, 34)})
        caixa.alpha_composite(topo, (1, 0))
        # Os itens são postos pelo script do cliente na faixa de baixo (componente 13 de 905): um ícone por escolha,
        # espalhados por igual, com o nome embaixo.
        faixa_y, faixa_h = 33, 91
        n = len(itens)
        fonte = self.mont.fonte(494)
        for i, sid in enumerate(itens):
            cx = 1 + (W - 2) * (i + 1) // (n + 1)
            y = faixa_y + (faixa_h - 32) // 2 - 8
            caixa.alpha_composite(self.icone_jogo(sid), (cx - 18, y))
            imagens.desenhar_texto(caixa, fonte, self.d.nome(sid), cx - 70, y + 34, 140, 14, LARANJA, halign=1, valign=0, sombra=True)
        return caixa

    def janela_forjar(self, metal, nivel_jogador=99, barras=99):
        """A janela da bigorna (interface 300) como o servidor a abre para um metal: título, ícones, nomes e barras."""
        d = self.d
        ids = self.smithing_ids
        textos = {ids["title"]: f"{metal.capitalize()} Smithing"}
        extras = []
        cores = {}
        smithing = d.linhas("smithing")
        for tipo in TIPOS_BIGORNA:
            item = f"{metal}_{tipo}"
            if not d.existe(item) or tipo not in ids:
                continue
            extras.append((ids[tipo], self.icone_jogo(item)))
            nivel = smithing.get(item, {}).get("level", 1)
            cores[ids[f"{tipo}_name"]] = BRANCO if nivel_jogador >= nivel else 0
            cores[ids[f"{tipo}_bar"]] = VERDE if barras >= 1 else LARANJA
        visiveis = {ids["wire_bronze"]: metal == "bronze", ids["spit_iron"]: metal == "iron", ids["studs_steel"]: metal == "steel",
                    ids["bullseye_lantern"]: False, ids["grapple"]: False, ids["darts"]: False}
        for nome in ("claw", "pickaxes"):
            if nome in ids:
                visiveis[ids[nome]] = False
        return imagens.compor(self.mont, 300, 512, 334, textos=textos, extras=extras, cores=cores, visiveis=visiveis)

    # ------------------------------------------------------------ peças da trilha
    def arte(self, sid, chave, k=1.6):
        svg = f'<svg aria-hidden="true">{caminho(chave, k)}<rect x="-28" y="-28" width="56" height="56" rx="12"/></svg>'
        return f'<div class="arte capa">{svg}<img class="px slot" src="{self.icone1x(sid)}" width="36" height="32" alt=""></div>'

    def linha(self, sid, pequeno, tags=()):
        direita = "".join(f'<span class="tag {c}">{e(t)}</span>' for t, c in tags)
        return (f'<div class="linha"><img class="px" src="{self.icone1x(sid)}" width="36" height="32" alt="">'
                f'<span class="txt"><b>{e(self.d.nome(sid))}</b><small>{pequeno}</small></span><span class="lado-direito">{direita}</span></div>')

    def figura(self, im, titulo, legenda):
        return (f'<figure><div class="rolar"><img class="px" src="{imagens.png_base64(im)}" width="{im.width}" height="{im.height}" alt=""></div>'
                f'<figcaption><b>{e(titulo)}</b>{e(legenda)}</figcaption></figure>')

    def cartao(self, ident, capa_sid, tags, titulo, frase, entram, meio, saem, grupos_saida=None):
        """Um passo: capa com o item que sai, etiquetas, título, uma frase, o que entra, a janela (ou o aviso de que não
        há janela), o que sai. `saem` é uma lista de linhas prontas; `grupos_saida`, uma lista de (nome, linhas)."""
        meta = "".join(f'<span class="tag {c}">{e(t)}</span>' for t, c in tags)
        partes = [f'<div class="cartao" id="{ident}" data-busca="{e(titulo.lower())}">', self.arte(capa_sid, titulo),
                  f'<div class="corpo"><div class="meta">{meta}</div><h3>{e(titulo)}</h3><p>{frase}</p>',
                  f'<span class="eyebrow">Entra</span><div class="linhas">{"".join(entram)}</div>', meio]
        if grupos_saida:
            partes.append('<span class="eyebrow">Sai</span>')
            for nome, linhas in grupos_saida:
                partes.append(f'<p class="grupo">{e(nome)}</p><div class="linhas">{"".join(linhas)}</div>')
        elif saem:
            partes.append(f'<span class="eyebrow">Sai</span><div class="linhas">{"".join(saem)}</div>')
        partes.append("</div></div>")
        return "\n".join(partes)

    # ------------------------------------------------------------ o capítulo do bronze
    def bronze(self):
        d = self.d
        bars = d.linhas("bars")["bronze_bar"]
        smith = d.linhas("smithing")
        ores = d.linhas("ores")

        def passo(sid):
            return next(d.passos[n] for n in d.feito_por[sid] if d.passos[n]["fonte"].startswith(("smithing.", "bars.")))

        def passo_de(sid, prefixo):
            return next(d.passos[n] for n in d.feito_por[sid] if d.passos[n]["fonte"].startswith(prefixo))

        # as peças da bigorna, por nível
        pecas = []
        for tipo in TIPOS_BIGORNA:
            item = f"bronze_{tipo}"
            if d.existe(item) and item in smith:
                p = passo(item)
                pecas.append((item, p["level"], p["xp"], p["ins"][0][1], p["outs"][0][1]))
        pecas.sort(key=lambda x: (x[1], x[0]))
        QUEST = {"bronze_claws": "missão Death Plateau", "bronze_pickaxe": "missão Perils of Ice Mountain", "bronze_dart_tip": "missão The Tourist Trap"}
        VIRA = {"bronze_arrowtips": "vira flecha", "bronze_bolts_unf": "vira virote", "bronze_dart_tip": "vira dardo", "bronze_limbs": "vira besta",
                "bronze_nails": "prega escudo tribal e casa", "bronze_wire": "vira rede de algas"}

        def linha_peca(item, lvl, xpv, barras, faz):
            peq = f"{barras} barra{'s' if barras > 1 else ''} · {xp(xpv)}" + (f" · faz {faz}" if faz > 1 else "")
            tags = [(f"Smithing {lvl}", "nivel")]
            if item in VIRA:
                tags.append((VIRA[item], ""))
            if item in QUEST:
                tags.append((QUEST[item], "quest"))
            return self.linha(item, peq, tags)

        armas = [linha_peca(*p) for p in pecas if d.itens[p[0]]["campos"].get("slot") == "Weapon"]
        armaduras = [linha_peca(*p) for p in pecas if d.itens[p[0]]["campos"].get("slot") in ("Hat", "Chest", "Legs", "Shield") and p[0] != "bronze_limbs"]
        outras = [linha_peca(*p) for p in pecas if p[0] in VIRA]
        niveis = [p[1] for p in pecas]

        # o conjunto vestido
        kit = ["bronze_full_helm", "bronze_platebody", "bronze_platelegs", "bronze_kiteshield", "bronze_sword"]
        ONDE = {"Hat": "na cabeça", "Chest": "no corpo", "Legs": "nas pernas", "Shield": "no escudo", "Weapon": "na mão"}
        totais = {chave: sum(valor_bonus(d.cache[s], chave) for s in kit) for _, chave in BONUS}

        def resumo_bonus(sid):
            reg = d.cache[sid]
            partes = []
            for rotulo, chave in BONUS:
                v = valor_bonus(reg, chave)
                nomes = {"stab_attack": "Stab", "slash_attack": "Slash", "crush_attack": "Crush", "range_attack": "Range", "stab_defence": "def Stab",
                         "slash_defence": "def Slash", "crush_defence": "def Crush", "range_defence": "def Ranged", "magic_defence": "def Magic",
                         "strength": "Str", "ranged_strength": "Ranged Str"}
                if v and chave in nomes:
                    nome = nomes[chave]
                    partes.append(f"{nome} {formata_bonus(chave, v)}")
            return " · ".join(partes) or "sem bônus"

        vestiveis = [p[0] for p in pecas if d.itens[p[0]]["campos"].get("slot") and p[0] != "bronze_limbs"]
        img_kit, _ = self.janela_equipamento("", kit)

        # o que continua do bronze
        p_flecha = passo_de("bronze_arrow", "skill/fletching/arrowtip")
        p_haste = passo_de("arrow_shaft", "fletchables.logs")
        p_sem_ponta = passo_de("headless_arrow", "HeadlessArrows")
        p_virote = passo_de("bronze_bolts", "fletching_bolts")
        p_dardo = passo_de("bronze_dart", "fletching_darts")
        p_besta_u = passo_de("bronze_crossbow_u", "skill/fletching/crossbow")
        p_besta = passo_de("bronze_crossbow", "skill/fletching/crossbow")
        p_cabo = passo_de("wooden_stock", "fletchables.logs")
        p_corda = passo_de("crossbow_string", "spinning.sinew")

        cartoes = [
            self.cartao("fornalha", "bronze_bar", [(f"Smithing {bars['level']}", "nivel"), (xp(bars["xp"] / 10), "")], "Fornalha",
                        "Use um dos minérios na fornalha; o jogo pergunta quantas barras fazer.",
                        [self.linha("copper_ore", f"1 por barra · da mina, Mining {ores['copper_ore']['level']}"),
                         self.linha("tin_ore", f"1 por barra · da mina, Mining {ores['tin_ore']['level']}")],
                        self.figura(self.janela_fazer(["bronze_bar"]), "A pergunta da fornalha", "Na caixa de conversa. Uma escolha só: a barra de bronze."),
                        [self.linha("bronze_bar", "1 por vez")]),
            self.cartao("bigorna", "bronze_sword", [(f"Smithing {min(niveis)} a {max(niveis)}", "nivel"), ("martelo", ""), (f"{len(pecas)} peças", "")], "Bigorna",
                        "Use a barra na bigorna, com o martelo na mochila: a janela lista tudo que o bronze faz.",
                        [self.linha("bronze_bar", "1 a 5 por peça"), self.linha("hammer", "na mochila, não gasta")],
                        self.figura(self.janela_forjar("bronze"), "Bronze Smithing", "Nome em branco: tem o nível. Barras em verde: tem barra. Dardos, garras e picareta só aparecem depois da missão de cada um."),
                        None, [("Armas", armas), ("Armaduras", armaduras), ("Peças para outras cadeias", outras)]),
            self.cartao("equipamento", "bronze_platebody", [(f"def Slash +{totais['slash_defence']}", "bonus"), (f"def Stab +{totais['stab_defence']}", "bonus"),
                                                            (f"def Crush +{totais['crush_defence']}", "bonus"), (f"Str +{totais['strength']}.0", "bonus")], "Equipamento",
                        "Vista as peças. A janela de equipamento soma o que cada uma dá; abaixo, o que cada peça vale sozinha.",
                        [self.linha(s, ONDE[d.itens[s]["campos"]["slot"]]) for s in kit],
                        self.figura(Image.open(__import__("io").BytesIO(base64.b64decode(img_kit.split(",", 1)[1]))), "Equipment Bonuses",
                                    "Elmo, peitoral, perneiras, escudo e espada de bronze vestidos. Os números são a soma das cinco peças."),
                        None, [("Cada peça que se veste", [self.linha(s, resumo_bonus(s), [(ONDE[d.itens[s]["campos"]["slot"]], "")]) for s in vestiveis])]),
            self.cartao("hastes", "headless_arrow", [(f"Fletching {p_haste['level']}", "nivel"), ("faca", ""), ("penas", "")], "Hastes e penas",
                        "Entalhe um tronco com a faca: 15 hastes. Depois junte as penas, 15 por vez.",
                        [self.linha("logs", f"1 tronco = {p_haste['outs'][0][1]} hastes · {xp(p_haste['xp'])}"), self.linha("knife", "na mochila, não gasta"),
                         self.linha("feather", "1 por haste")],
                        self.figura(self.janela_fazer(["headless_arrow"], "How many sets of 15 do you wish to feather?"), "A pergunta das penas",
                                    "Ao usar a pena na haste. As hastes vêm do tronco sem janela: a faca pergunta o que entalhar."),
                        [self.linha("headless_arrow", f"15 por vez · {xp(p_sem_ponta['xp'] * 15)} por 15")]),
            self.cartao("flechas", "bronze_arrow", [(f"Fletching {p_flecha['level']}", "nivel"), (xp(p_flecha["xp"]) + " por 15", "")], "Flechas",
                        "Junte as pontas de bronze nas hastes com pena: 15 flechas por vez.",
                        [self.linha("headless_arrow", "15 por vez"), self.linha("bronze_arrowtips", "15 por vez · da bigorna")],
                        self.figura(self.janela_fazer(["bronze_arrow"], "How many set of 15 would you like to make?"), "A pergunta das flechas", "Ao usar as pontas nas hastes."),
                        [self.linha("bronze_arrow", "15 por vez")]),
            self.cartao("virotes", "bronze_bolts", [(f"Fletching {p_virote['level']}", "nivel"), (xp(p_virote["xp"]) + " cada", "")], "Virotes",
                        "Use a pena no virote sem pena: o jogo junta até 10 de uma vez, sem perguntar.",
                        [self.linha("bronze_bolts_unf", "10 por vez · da bigorna"), self.linha("feather", "1 por virote")],
                        '<p class="sem-janela">Este passo não abre janela: a pena na peça já faz.</p>',
                        [self.linha("bronze_bolts", "até 10 por vez")]),
            self.cartao("dardos", "bronze_dart", [(f"Fletching {p_dardo['level']}", "nivel"), (xp(p_dardo["xp"]) + " cada", ""), ("missão The Tourist Trap", "quest")], "Dardos",
                        "Use a pena na ponta de dardo: até 10 de uma vez, sem perguntar. As pontas só saem da bigorna depois da missão.",
                        [self.linha("bronze_dart_tip", "10 por vez · da bigorna"), self.linha("feather", "1 por dardo")],
                        '<p class="sem-janela">Este passo não abre janela: a pena na peça já faz.</p>',
                        [self.linha("bronze_dart", "até 10 por vez")]),
            self.cartao("besta-partes", "bronze_crossbow_u", [(f"Fletching {p_besta_u['level']}", "nivel"), (xp(p_besta_u["xp"]), "")], "Besta: as partes",
                        "Junte os braços de bronze ao cabo de madeira.",
                        [self.linha("bronze_limbs", "1 · da bigorna"), self.linha("wooden_stock", f"1 · tronco + faca, Fletching {p_cabo['level']}, {xp(p_cabo['xp'])}")],
                        self.figura(self.janela_fazer(["bronze_crossbow_u"], "How many would you like to make?"), "A pergunta da besta", "Ao usar os braços no cabo."),
                        [self.linha("bronze_crossbow_u", "1 por vez")]),
            self.cartao("besta-corda", "bronze_crossbow", [(f"Fletching {p_besta['level']}", "nivel"), (xp(p_besta["xp"]), "")], "Besta: a corda",
                        "Junte a corda à besta sem corda.",
                        [self.linha("bronze_crossbow_u", "1"), self.linha("crossbow_string", f"1 · tendão ou raiz na roda de fiar, Crafting {p_corda['level']}, {xp(p_corda['xp'])}")],
                        self.figura(self.janela_fazer(["bronze_crossbow"], "How many would you like to make?"), "A pergunta da corda", "Ao usar a corda na besta."),
                        [self.linha("bronze_crossbow", "1 por vez", [("Ranged", "bonus")])]),
        ]
        return LIGA.join(cartoes)

    # ------------------------------------------------------------ página
    def html(self):
        with open(os.path.join(AQUI, "trilha.css"), encoding="utf-8") as f:
            css = f.read()
        hoje = datetime.date.today().strftime("%d/%m/%Y")
        return f"""<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Cadeias de produção</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600;700&display=swap">
<style>
{css}
</style>
</head>
<body>
<header class="topo"><div class="largura"><div class="topo-linha">
  <nav class="abas" aria-label="Capítulos"><a href="#fornalha" aria-current="page"><img class="bandeira" src="{BANDEIRA}" alt="">Bronze</a></nav>
  <div class="lado"><div class="busca"><label>{LUPA}<input id="busca" type="search" placeholder="Buscar nos cartões..." autocomplete="off"><kbd>Ctrl</kbd><kbd>K</kbd></label></div></div>
</div></div></header>
<main class="largura">
  <header class="capitulo"><p class="eyebrow">Capítulo 1 · Smithing e Fletching</p><h1>Bronze</h1>
    <p class="lede">Do minério à espada e ao que o bronze ainda vira: flechas, virotes, dardos e besta. Cada cartão é um passo: o que entra, a janela do jogo, o que sai.</p></header>
  <div class="trilha" id="trilha">
{self.bronze()}
  </div>
  <p class="rodape-pagina">Gerado em {hoje} a partir de <code>data/</code> e do cache do Void. Janelas desenhadas dos sprites e fontes do cache; ícones desenhados do modelo 3D de cada item. Para refazer: <code>bash tools/cadeias/gerar.sh</code>.</p>
</main>
<script>
(function () {{
  var campo = document.getElementById('busca');
  var cartoes = Array.prototype.slice.call(document.querySelectorAll('.trilha .cartao'));
  var ligas = Array.prototype.slice.call(document.querySelectorAll('.trilha .liga'));
  function filtrar() {{
    var q = campo.value.trim().toLowerCase();
    cartoes.forEach(function (c) {{ c.classList.toggle('escondido', !!q && c.textContent.toLowerCase().indexOf(q) < 0); }});
    ligas.forEach(function (l) {{ l.classList.toggle('escondido', !!q); }});
  }}
  campo.addEventListener('input', filtrar);
  document.addEventListener('keydown', function (ev) {{
    if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === 'k') {{ ev.preventDefault(); campo.focus(); campo.select(); }}
  }});
}})();
</script>
</body>
</html>
"""


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--void", default=os.path.abspath(os.path.join(AQUI, "..", "..")))
    ap.add_argument("--cadeias", default=os.path.join(CASA, "Documents", "void-downloads", "cadeias"))
    ap.add_argument("--montador", default=os.path.join(CASA, "Documents", "void-montador-de-interfaces", "cache", "jogo", "exportado"))
    ap.add_argument("--grafite", default=os.path.join(CASA, "Documents", "design-grafite"))
    ap.add_argument("--saida", default=os.path.join(CASA, "Documents", "void-downloads", "cadeias-guia.html"))
    args = ap.parse_args()
    d = Dados(args.void, os.path.join(args.cadeias, "itens.json"))
    mont = imagens.Montador(args.montador)
    guia = Guia(d, os.path.join(args.cadeias, "icones"), args.grafite, mont)
    with open(args.saida, "w", encoding="utf-8") as f:
        f.write(guia.html())
    print(f"-> {args.saida} ({os.path.getsize(args.saida) // 1024} KB)")


if __name__ == "__main__":
    main()

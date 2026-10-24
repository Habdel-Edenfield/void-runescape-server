#!/usr/bin/env python3
"""Gera a página "Cadeias de produção" (um HTML só, no tema grafite) a partir dos dados do Void.

Entradas:
  --void      raiz do repositório do Void (data/ e o código)
  --cadeias   pasta com itens.json (saída de `./gradlew :tools:cadeias:cadeiasExport`) e icones/ (saída de
              `./gradlew :tools:icon:dumpItemSprites`)
  --montador  a exportação do cache feita pelo Montador de Interfaces (sprites, fontes e interfaces)
  --grafite   a pasta do tema grafite (tokens.css e components/bundle.css)
  --saida     o HTML a escrever
"""
import argparse
import datetime
import html
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402

import imagens  # noqa: E402
from dados import HABILIDADES, ORIGENS, Dados  # noqa: E402

AQUI = os.path.dirname(os.path.abspath(__file__))
CASA = os.path.expanduser("~")

# EquipBonuses.names, na ordem e com os rótulos da janela de equipamento
BONUS = [
    ("Stab", "stab_attack"), ("Slash", "slash_attack"), ("Crush", "crush_attack"), ("Magic", "magic_attack"), ("Range", "range_attack"),
    ("Stab", "stab_defence"), ("Slash", "slash_defence"), ("Crush", "crush_defence"), ("Magic", "magic_defence"), ("Ranged", "range_defence"),
    ("Summoning", "summoning_defence"), ("Absorb Melee", "absorb_melee"), ("Absorb Magic", "absorb_magic"), ("Absorb Ranged", "absorb_range"),
    ("Strength", "strength"), ("Ranged Strength", "ranged_strength"), ("Prayer", "prayer_bonus"), ("Magic Damage", "magic_damage"),
]
VELOCIDADE = {2: "Very fast", 3: "Fast", 4: "Standard", 5: "Slow", 6: "Very slow"}


def e(s):
    return html.escape(str(s), quote=True)


def num(v, casas=1):
    if v is None:
        return ""
    s = f"{v:.{casas}f}".replace(".", ",")
    if "," in s:
        s = s.rstrip("0").rstrip(",")
    return s


def milhar(n):
    return f"{n:,}".replace(",", ".")


def valor_bonus(reg, chave):
    """O que o servidor lê: o parâmetro como inteiro (força é guardada como 5.0 e lida como 5)."""
    return int(reg.get("bonus", {}).get(chave, 0))


def formata_bonus(chave, valor, totais=False):
    """EquipBonuses.format: na lista de um item (totais=False) o sinal só aparece em força; nos totais, em tudo."""
    if chave in ("magic_damage", "absorb_melee", "absorb_magic", "absorb_range"):
        return f"{'+' if valor >= 0 else ''}{valor}%"
    if chave in ("strength", "ranged_strength"):
        return f"{'+' if valor > 0 else ''}{valor:.1f}"
    if totais:
        return f"{'+' if valor >= 0 else ''}{valor}"
    return str(valor)


class Pagina:
    def __init__(self, d, icones, grafite, mont):
        self.d = d
        self.icones = icones
        self.grafite = grafite
        self.mont = mont
        self._icone_cache = {}

    # ------------------------------------------------------------ pedaços
    def icone(self, sid, hd=True):
        chave = (sid, hd)
        if chave not in self._icone_cache:
            iid = self.d.itens[sid]["id"]
            p = os.path.join(self.icones, f"{iid}_hd.png" if hd else f"{iid}.png")
            self._icone_cache[chave] = imagens.png_base64(Image.open(p)) if os.path.exists(p) else None
        return self._icone_cache[chave]

    def link(self, sid, qtd=None):
        nome = e(self.d.nome(sid))
        pre = f"{qtd}× " if qtd and qtd != 1 else ""
        if sid in self.d.no_grafo:
            return f'{pre}<a href="#i-{e(sid)}">{nome}</a>'
        return f"{pre}{nome}"

    def passo_html(self, p):
        partes = [self.link(i, q) for i, q in p["ins"]]
        for grupo in p["alt"]:
            partes.append("um de: " + ", ".join(self.link(i) for i in grupo))
        ingredientes = " + ".join(partes) if partes else "nada"
        outs = p["outs"][0]
        faz = f" (faz {outs[1]})" if outs[1] != 1 else ""
        skill = HABILIDADES.get(p["skill"], p["skill"])
        nivel = f"{skill}, nível {p['level']}"
        xp = f", {num(p['xp'])} xp" if p.get("xp") else ""
        tools = ""
        if p["tools"]:
            tools = ", com " + " e ".join(e(self.d.nome(t)) for t in p["tools"])
        nota = f" <span class=\"nota\">({e(p['nota'])})</span>" if p.get("nota") else ""
        return f"<li>{ingredientes}{faz}: {nivel}{xp}, {e(p['onde'])}{tools}.{nota}</li>"

    def bonus_html(self, sid):
        reg = self.d.cache.get(sid, {})
        campos = self.d.itens[sid]["campos"]
        if not campos.get("slot") and not reg.get("bonus"):
            return None
        secoes = []
        for titulo, faixa in (("Attack bonus", BONUS[0:5]), ("Defence bonus", BONUS[5:14]), ("Other", BONUS[14:18])):
            linhas = []
            for rotulo, chave in faixa:
                v = valor_bonus(reg, chave)
                if v != 0:
                    linhas.append(f"{rotulo.replace('Ranged Strength', 'Ranged Str.')} {formata_bonus(chave, v)}")
            if linhas:
                secoes.append(f"<b>{titulo}</b> {' · '.join(linhas)}")
        outras = []
        if "attack_speed" in reg and reg["attack_speed"] != 0:
            outras.append(f"<b>Attack Rate</b> {VELOCIDADE.get(reg['attack_speed'], reg['attack_speed'])}")
        peso = campos.get("weight", 0.0)
        outras.append(f"<b>Weight</b> {int(peso * 10) / 10:.1f} kg")
        secoes.append(" · ".join(outras))
        if len(secoes) == 1:
            secoes.insert(0, "<b>Sem bônus</b> veste, mas não muda ataque nem defesa")
        return "<br>".join(secoes)

    def item_html(self, sid):
        d = self.d
        nome = d.nome(sid)
        img = self.icone(sid)
        img_html = f'<img class="pixel" src="{img}" width="72" height="64" alt="">' if img else '<span class="sem-icone"></span>'
        feito = [d.passos[n] for n in d.feito_por.get(sid, [])]
        feito.sort(key=lambda p: (p["level"], p["skill"], p["fonte"]))
        produtos = d.produtos(sid)
        busca = f"{nome} {sid}".lower()
        partes = [f'<section class="item-cadeia" id="i-{e(sid)}" data-busca="{e(busca)}">',
                  f'<h3>{img_html}<span class="nome">{e(nome)}</span><code class="sid">{e(sid)}</code></h3>',
                  '<dl class="ficha">']
        if feito:
            partes.append(f"<dt>Feito com</dt><dd><ul class=\"passos\">{''.join(self.passo_html(p) for p in feito)}</ul></dd>")
        else:
            origem = ORIGENS.get(d.origem(sid), "loja, gota de monstro, missão ou coleta")
            partes.append(f"<dt>De onde vem</dt><dd>Não é feito por habilidade: {e(origem)}.</dd>")
        if produtos:
            partes.append(f"<dt>Vira</dt><dd>{', '.join(self.link(p) for p in produtos)}</dd>")
        else:
            partes.append("<dt>Vira</dt><dd>Nada: é produto final.</dd>")
        bonus = self.bonus_html(sid)
        if bonus:
            partes.append(f"<dt>Bônus</dt><dd class=\"bonus\">{bonus}</dd>")
        partes.append("</dl></section>")
        return "\n".join(partes)

    # ------------------------------------------------------------ níveis
    def niveis_html(self):
        d = self.d
        por_nivel = {}
        for sid, lv in d.nivel.items():
            por_nivel.setdefault(lv, []).append(sid)
        saida = []
        for lv in sorted(por_nivel):
            itens = por_nivel[lv]
            if lv == 0:
                titulo = "Nível 0: o que sai do mundo"
                legenda = "Minério, tronco, peixe cru, couro, erva, semente, essência. Nenhuma habilidade faz estes itens; eles entram no começo de alguma cadeia."
                grupos = {}
                for sid in itens:
                    grupos.setdefault(d.origem(sid), []).append(sid)
                ordem = list(ORIGENS) + ["outros"]
                rotulo = lambda g: ORIGENS.get(g, "Outras origens: loja, gota de monstro, missão ou coleta")  # noqa: E731
                chave = lambda sid: d.nome(sid).lower()  # noqa: E731
            else:
                titulo = f"Nível {lv}" + {1: ": feito direto do que sai do mundo", 2: ": o segundo passo"}.get(lv, "")
                legenda = f"Cada item aqui precisa de pelo menos um ingrediente do nível {lv - 1}; todos os ingredientes estão acima."
                grupos = {}
                for sid in itens:
                    grupos.setdefault(d.skill_principal(sid) or "outros", []).append(sid)
                ordem = list(HABILIDADES) + ["outros"]
                rotulo = lambda g: HABILIDADES.get(g, g)  # noqa: E731

                def chave(sid):
                    ps = [d.passos[n] for n in d.feito_por.get(sid, [])]
                    return (min(p["level"] for p in ps) if ps else 0, d.nome(sid).lower())
            saida.append(f'<section class="nivel" id="nivel-{lv}">')
            saida.append(f"<h2>{e(titulo)}</h2>")
            saida.append(f"<p class=\"legenda\">{e(legenda)} <span class=\"conta\">{len(itens)} itens.</span></p>")
            for g in ordem:
                if g not in grupos:
                    continue
                lista = sorted(grupos[g], key=chave)
                saida.append(f'<div class="grupo"><h4 class="sobretitulo">{e(rotulo(g))} · {len(lista)}</h4>')
                saida.extend(self.item_html(sid) for sid in lista)
                saida.append("</div>")
            saida.append("</section>")
        return "\n".join(saida), sorted(por_nivel)

    # ------------------------------------------------------------ combate
    def combate(self):
        d = self.d
        arma = "bronze_sword"
        armadura = ["iron_full_helm", "iron_platebody", "iron_platelegs", "iron_kiteshield"]
        b = lambda sid, k: valor_bonus(d.cache[sid], k)  # noqa: E731
        att_lv, str_lv, def_lv = 10, 10, 10
        slash_att = b(arma, "slash_attack")
        forca = b(arma, "strength")
        def_slash = sum(b(a, "slash_defence") for a in armadura)
        # estilos da espada (weapon_styles.toml, tipo "sword"): chop = accurate/slash, slash = aggressive/slash
        ex = {}
        for estilo, att_st, str_st in (("chop", 3, 0), ("slash", 0, 3)):
            eff_att = att_lv + 8 + att_st
            rat_att = eff_att * (slash_att + 64)
            eff_def = def_lv + 8
            rat_def = eff_def * (def_slash + 64)
            chance = 1 - (rat_def + 2) / (2 * (rat_att + 1)) if rat_att > rat_def else rat_att / (2 * (rat_def + 1))
            eff_str = str_lv + 8 + str_st
            maximo = 5 + (eff_str * (forca + 64)) // 64
            ex[estilo] = dict(eff_att=eff_att, rat_att=rat_att, eff_def=eff_def, rat_def=rat_def, chance=chance, eff_str=eff_str, maximo=maximo)
        return dict(arma=arma, armadura=armadura, att_lv=att_lv, str_lv=str_lv, def_lv=def_lv, slash_att=slash_att, forca=forca,
                    def_slash=def_slash, ex=ex, def_por_peca={a: b(a, "slash_defence") for a in armadura})

    def janela_equipamento(self, titulo, vestidos):
        """A janela Equipment Bonuses (interface 667) como o servidor a preenche para quem veste `vestidos`."""
        d = self.d
        totais = {chave: sum(valor_bonus(d.cache[s], chave) for s in vestidos) for _, chave in BONUS}
        textos = {}
        # worn_equipment.ifaces.toml: equipment_bonuses.<chave> = componente 30..47, na ordem de EquipBonuses.names
        for n, (rotulo, chave) in enumerate(BONUS):
            textos[30 + n] = f"{rotulo}: {formata_bonus(chave, totais[chave], True)}"
        # os encaixes de equipamento no componente 7: capacete 13, capa 14, amuleto 15, munição 16, arma 17, corpo 18,
        # escudo 19, pernas 20, botas 21, luvas 22, anel 23 (sprite 170 é a caixa de 36x36)
        encaixe = {"Hat": 13, "Cape": 14, "Amulet": 15, "Ammo": 16, "Weapon": 17, "Chest": 18, "Shield": 19, "Legs": 20, "Feet": 21, "Hands": 22, "Ring": 23}
        extras = []
        for s in vestidos:
            slot = d.itens[s]["campos"].get("slot")
            p = os.path.join(self.icones, f"{d.itens[s]['id']}.png")
            if slot in encaixe and os.path.exists(p):
                extras.append((encaixe[slot], Image.open(p).convert("RGBA")))
        im = imagens.compor(self.mont, 667, 512, 334, textos=textos, extras=extras)
        return imagens.png_base64(im), titulo

    def aba_estilos(self, arma, nivel_combate, estilos, escolhido):
        # combat_style.ifaces.toml: style1..4 = componentes 11..14; o texto de cada botão é o filho de tipo 4
        texto_de = {11: 31, 12: 27, 13: 25, 14: 22}
        sprite_de = {11: 29, 12: 26, 13: 23, 14: 20}
        textos = {0: self.d.nome(arma), 1: f"Combat Lvl: {nivel_combate}"}
        sprites = {}
        for n, nome in enumerate(estilos):
            textos[texto_de[11 + n]] = nome
            if n == escolhido:
                sprites[sprite_de[11 + n]] = 654
        im = imagens.compor(self.mont, 884, 190, 261, textos=textos, sprites=sprites, escondidos=(3,))
        return imagens.png_base64(im)

    def combate_html(self):
        c = self.combate()
        d = self.d
        ic = imagens.icones_das_habilidades(self.mont)

        def icone_skill(nome, rotulo):
            im = ic.get(nome)
            if im is None:
                return e(rotulo)
            return f'<img class="pixel skill" src="{imagens.png_base64(imagens.escala(im, 2))}" width="50" height="50" alt=""> {e(rotulo)}'

        chop, slash = c["ex"]["chop"], c["ex"]["slash"]
        pecas = " + ".join(f"{e(d.nome(a))} {c['def_por_peca'][a]}" for a in c["armadura"])
        nivel_combate = int(0.25 * (c["def_lv"] + 10) + 0.325 * (c["att_lv"] + c["str_lv"]))
        img_def, _ = self.janela_equipamento("defensor", c["armadura"])
        img_att, _ = self.janela_equipamento("atacante", [c["arma"]])
        img_aba = self.aba_estilos(c["arma"], nivel_combate, ["Chop", "Slash", "Lunge", "Block"], 0)
        pct = lambda x: f"{x * 100:.0f}%"  # noqa: E731
        conta_chance = (
            f"nível de Attack {c['att_lv']} + 8 + 3 (estilo Accurate)        = {chop['eff_att']}\n"
            f"bônus de Slash da espada {c['slash_att']} + 64                   = {c['slash_att'] + 64}\n"
            f"ataque: {chop['eff_att']} × {c['slash_att'] + 64}                               = {chop['rat_att']}\n"
            f"\n"
            f"nível de Defence {c['def_lv']} + 8 (estilo do defensor: sem bônus) = {chop['eff_def']}\n"
            f"defesa de Slash da armadura {c['def_slash']} + 64                = {c['def_slash'] + 64}\n"
            f"defesa: {chop['eff_def']} × {c['def_slash'] + 64}                               = {chop['rat_def']}\n"
            f"\n"
            + (f"ataque é maior: 1 − ({chop['rat_def']} + 2) ÷ (2 × ({chop['rat_att']} + 1)) = {chop['chance']:.3f} → acerta {pct(chop['chance'])} das vezes"
               if chop["rat_att"] > chop["rat_def"] else
               f"defesa é maior: {chop['rat_att']} ÷ (2 × ({chop['rat_def']} + 1))           = {chop['chance']:.3f} → acerta {pct(chop['chance'])} das vezes")
        )
        conta_dano = (
            f"nível de Strength {c['str_lv']} + 8 + 0 (Accurate não dá força)   = {chop['eff_str']}\n"
            f"bônus de Strength da espada {c['forca']} + 64                    = {c['forca'] + 64}\n"
            f"5 + {chop['eff_str']} × {c['forca'] + 64} ÷ 64 (sem a parte decimal)          = {chop['maximo']}\n"
            f"\n"
            f"quando acerta, o dano é sorteado de 0 a {chop['maximo']} (um jogador nível 10 de Constitution tem 100 pontos de vida)"
        )
        conta_slash = (
            f"ataque: ({c['att_lv']} + 8 + 0) × {c['slash_att'] + 64} = {slash['rat_att']} → acerta {pct(slash['chance'])} das vezes\n"
            f"dano:   5 + ({c['str_lv']} + 8 + 3) × {c['forca'] + 64} ÷ 64 = {slash['maximo']}"
        )
        return f"""
<section class="nivel" id="combate">
<h2>Como este servidor calcula uma luta</h2>
<p>Lido no código do servidor, não na wiki: a chance de acertar está em <code>Hit.kt</code>, o dano em <code>Damage.kt</code>, o bônus de estilo em <code>Bonus.kt</code>, os bônus de equipamento em <code>Equipment.kt</code> e <code>EquipmentBonuses.kt</code>, a força da arma em <code>Weapon.kt</code> e as preces em <code>Prayer.kt</code>. Tudo em <code>game/src/main/kotlin/content/</code>.</p>

<h3>1. Acertar ou errar</h3>
<p>Cada golpe é um sorteio. O servidor monta um número para quem ataca e outro para quem defende, e compara os dois.</p>
<ul>
<li><strong>Nível efetivo de ataque:</strong> o nível de {icone_skill('attack', 'Attack')} (multiplicado pela prece ativa, se houver), mais 8, mais o bônus do estilo de combate: +3 em <em>Accurate</em>, +1 em <em>Controlled</em>, nada em <em>Aggressive</em> e <em>Defensive</em>. Para arco e besta vale o nível de Ranged; para feitiço, o de Magic.</li>
<li><strong>Bônus do equipamento:</strong> a soma, em tudo que o jogador veste, do bônus de ataque do tipo que o estilo usa (Stab, Slash ou Crush; Range; Magic). É o número da janela de equipamento, na coluna <em>Attack bonus</em>.</li>
<li><strong>Número de ataque</strong> = nível efetivo × (bônus do equipamento + 64).</li>
<li><strong>Número de defesa</strong> = a mesma conta do lado de quem apanha: nível de {icone_skill('defence', 'Defence')} com prece, mais 8, mais +3 se ele está em <em>Defensive</em> (ou +1 em <em>Controlled</em>), vezes (defesa do equipamento daquele tipo + 64). Contra feitiço, o nível usado é 30% de Defence mais 70% de Magic.</li>
<li><strong>Chance:</strong> se o ataque é maior que a defesa, chance = 1 − (defesa + 2) ÷ (2 × (ataque + 1)). Se não, chance = ataque ÷ (2 × (defesa + 1)). O servidor sorteia um número entre 0 e 1; se ele fica abaixo da chance, o golpe acerta.</li>
</ul>

<h3>2. O dano máximo</h3>
<ul>
<li><strong>Nível efetivo de força:</strong> o nível de {icone_skill('strength', 'Strength')} com prece, mais 8, mais +3 em <em>Aggressive</em> ou +1 em <em>Controlled</em>. Para arco e besta vale Ranged com o bônus <em>Ranged Strength</em>; feitiço tem dano fixo por feitiço.</li>
<li><strong>Bônus de força:</strong> a soma do <em>Strength</em> do equipamento (coluna <em>Other</em> da janela). O servidor guarda esse número com uma casa decimal, mostra como +5.0 e usa só a parte inteira, 5.</li>
<li><strong>Dano máximo</strong> = 5 + nível efetivo × (bônus de força + 64) ÷ 64, jogando fora a parte decimal. Os pontos são os de 2011: um jogador tem 10 pontos de vida por nível de Constitution.</li>
<li>Quando o golpe acerta, o dano é <strong>sorteado entre 0 e o máximo</strong>, todos com a mesma chance. Depois entram os modificadores: prece de proteção (tira 60% contra jogador, 100% contra monstro), ataque especial, elmo de Slayer e amuleto Salve contra o alvo certo, armadura Void, e a absorção da armadura (<em>Absorb</em>, só na parte do golpe que passa de 200).</li>
</ul>

<h3 id="exemplo">Exemplo: espada de bronze contra armadura de ferro</h3>
<p>Dois jogadores sem prece. Quem ataca tem Attack {c['att_lv']} e Strength {c['str_lv']} e segura uma <a href="#i-bronze_sword">Bronze sword</a> no estilo <em>Chop</em> (o primeiro botão: Accurate, tipo Slash). Quem defende tem Defence {c['def_lv']} e veste <a href="#i-iron_full_helm">Iron full helm</a>, <a href="#i-iron_platebody">Iron platebody</a>, <a href="#i-iron_platelegs">Iron platelegs</a> e <a href="#i-iron_kiteshield">Iron kiteshield</a>.</p>
<p>A aba de estilos de quem ataca, como o jogo desenha:</p>
<p><img class="pixel janela" src="{img_aba}" width="380" height="522" alt="Aba de estilos de combate com a espada de bronze"></p>
<p>A janela de equipamento de quem ataca: a espada dá Slash {c['slash_att']} e Strength +{c['forca']}.0.</p>
<p><img class="pixel janela" src="{img_att}" width="512" height="334" alt="Janela de equipamento com a espada de bronze"></p>
<p>A janela de quem defende: a defesa de Slash soma {c['def_slash']} ({pecas}).</p>
<p><img class="pixel janela" src="{img_def}" width="512" height="334" alt="Janela de equipamento com a armadura de ferro"></p>
<h4>A chance de acertar</h4>
<div class="codigo"><pre>{e(conta_chance)}</pre></div>
<h4>O dano</h4>
<div class="codigo"><pre>{e(conta_dano)}</pre></div>
<h4>Se trocar para o segundo botão, <em>Slash</em> (Aggressive)</h4>
<p>Perde os +3 de ataque e ganha +3 de força:</p>
<div class="codigo"><pre>{e(conta_slash)}</pre></div>

<h3>Onde é igual à wiki e onde difere</h3>
<ul>
<li>As contas de nível efetivo (+8 e o bônus do estilo), de ataque × (bônus + 64) e da chance são as da wiki de 2011. Monstro recebe +9 fixo em vez de +8 mais estilo.</li>
<li>O dano máximo é a versão em pontos de vida de 2011: 5 + nível × (bônus + 64) ÷ 64. A conta antiga da wiki, com arredondamento e resultado em múltiplos de 10, dá números um pouco diferentes; o servidor não arredonda, só corta a parte decimal.</li>
<li>Na wiki, um golpe que acerta mas sorteia 0 aparece como erro (número azul). Aqui o 0 sorteado aparece como golpe de 0, não como erro; só o sorteio de acerto que falha mostra o erro.</li>
<li>O bônus de força da arma é usado sem a parte decimal: uma arma com Strength +7.5 conta como 7.</li>
<li>Prece: o multiplicador vem das preces ativas (por exemplo, Clarity of Thought dá 5% em Attack). Com Amulet of zealots o acréscimo dobra; Turmoil soma uma parte do nível do alvo em vez de multiplicar.</li>
</ul>
</section>
"""

    # ------------------------------------------------------------ página
    def html(self):
        d = self.d
        corpo, niveis = self.niveis_html()
        total = len(d.no_grafo)
        with open(os.path.join(self.grafite, "tokens.css"), encoding="utf-8") as f:
            tokens = f.read()
        with open(os.path.join(self.grafite, "components", "bundle.css"), encoding="utf-8") as f:
            bundle = f.read()
        hoje = datetime.date.today().strftime("%d/%m/%Y")
        nav = ['<a class="item" href="#inicio"><span class="n">·</span><span class="t">Como ler</span></a>']
        for lv in niveis:
            t = "Nível 0 · do mundo" if lv == 0 else f"Nível {lv}"
            nav.append(f'<a class="item" href="#nivel-{lv}"><span class="n">{lv}</span><span class="t">{t}</span></a>')
        nav.append('<a class="item" href="#combate"><span class="n">·</span><span class="t">Como se calcula uma luta</span></a>')
        nav.append('<a class="item" href="#fora"><span class="n">·</span><span class="t">O que ficou de fora</span></a>')
        nav.append('<a class="item" href="#regerar"><span class="n">·</span><span class="t">Como regerar</span></a>')
        por_skill = {}
        for p in d.passos:
            por_skill[p["skill"]] = por_skill.get(p["skill"], 0) + 1
        fora = "".join(f"<li><code>{e(o)}</code>: {e(pq)}</li>" for o, pq in d.fora)
        skills = ", ".join(f"{HABILIDADES.get(s, s)} ({n})" for s, n in sorted(por_skill.items(), key=lambda x: -x[1]))
        return f"""<!doctype html>
<html lang="pt-BR" data-theme="grafite">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Cadeias de produção</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600;700&display=swap">
<style>
{tokens}
{bundle}
/* Só o que é desta página. Toda cor vem de var(--nome) de tokens.css. */
html, body {{ height: 100%; }}
.pagina > .navegacao {{ position: sticky; top: 0; max-height: 100vh; overflow-y: auto; scrollbar-width: thin; scrollbar-color: var(--rolagem-2) transparent; }}
.nivel {{ margin-top: var(--espaco-48); padding-top: var(--espaco-24); border-top: var(--traco-1) solid var(--linha); }}
.nivel > h2 {{ margin-top: 0; }}
.grupo > .sobretitulo {{ margin-top: var(--espaco-32); }}
.item-cadeia {{ margin-top: var(--espaco-24); scroll-margin-top: var(--espaco-16); }}
.item-cadeia h3 {{ display: flex; align-items: center; gap: var(--espaco-12); margin: 0 0 var(--espaco-8); }}
.item-cadeia h3 img, .item-cadeia h3 .sem-icone {{ flex: none; width: 72px; height: 64px; border-radius: var(--canto-3); background: var(--poco); }}
.item-cadeia h3 .nome {{ flex: 1 1 auto; min-width: 0; }}
.item-cadeia h3 .sid {{ flex: none; font: var(--letra-miuda) / var(--entrelinha-ferramenta) var(--font-mono); color: var(--texto-2); background: none; border: 0; padding: 0; }}
.item-cadeia .ficha {{ margin: 0; }}
.ficha ul.passos {{ margin: 0; padding-left: var(--espaco-18); }}
.ficha ul.passos li {{ margin: 0 0 var(--espaco-4); }}
.ficha .seta {{ color: var(--texto-2); }}
.ficha .nota {{ color: var(--texto-2); }}
.ficha dd.bonus b {{ color: var(--texto-forte); font-weight: var(--peso-semi); }}
.legenda .conta {{ color: var(--texto); }}
.prosa img.janela {{ max-width: 100%; height: auto; display: block; }}
.prosa img.skill {{ vertical-align: middle; width: 25px; height: 25px; }}
.prosa .busca-itens {{ margin: var(--espaco-24) 0; }}
.prosa .codigo pre {{ white-space: pre; }}
.item-cadeia.escondido, .grupo.escondido, .nivel.escondido {{ display: none; }}
</style>
</head>
<body class="leitura">
<div class="pagina">
  <nav class="navegacao" aria-label="Seções">
    <div class="conta"><span><b>{milhar(total)}</b> itens em {len(niveis)} níveis</span></div>
    {''.join(nav)}
  </nav>
  <main>
    <article class="prosa">
      <p class="sobretitulo">Void · gerado em {hoje} a partir de data/ e do cache</p>
      <h1 id="inicio">Cadeias de produção</h1>
      <p>Todos os itens que uma habilidade transforma em outro item, e só eles: matéria-prima, item do meio e produto final. São <strong>{milhar(total)} itens</strong> de {milhar(len(d.itens))} que o jogo define; os outros não são feitos de nada nem viram nada.</p>
      <p>A ordem é a hierarquia de produção, de cima para baixo. <strong>Nível 0</strong> é o que sai do mundo. Um item fica no nível seguinte ao do ingrediente mais fundo que ele usa, então tudo que entra num item aparece acima dele. Item que entra em várias cadeias aparece uma vez, com a lista do que ele vira.</p>
      <p>Em cada item: a imagem do jogo (o ícone desenhado do modelo 3D do cache, em dobro), o nome como o jogo mostra, o que precisa para fazer (ingredientes, quantidade, habilidade e nível) e o que ele vira. Nos itens que se equipam, os bônus do jeito que a janela de equipamento mostra: só os que não são zero.</p>
      <p>Passos de produção lidos: {milhar(len(d.passos))}, por habilidade: {e(skills)}.</p>
      <label class="campo busca-itens"><span class="rotulo">Procurar item</span><input class="entrada" type="search" id="busca" placeholder="por exemplo, bronze" autocomplete="off"><span class="ajuda" id="busca-ajuda">Escreva parte do nome; a página esconde o resto.</span></label>
{corpo}
{self.combate_html()}
<section class="nivel" id="fora">
<h2>O que ficou de fora, e por quê</h2>
<p>Fora da página: {milhar(len(d.itens) - total)} itens que nenhuma habilidade faz nem usa. Receitas que não produzem item novo também ficam fora: despejar poção de um frasco no outro, encher saco de legumes, tingir capa. Dungeoneering tem itens próprios e ficou fora inteiro. A lista abaixo é o que o gerador encontrou e deixou de lado, um por um.</p>
<details class="dobravel"><summary>{len(d.fora)} coisas deixadas de lado</summary><div class="corpo"><ul>{fora}</ul></div></details>
</section>
<section class="nivel" id="regerar">
<h2>Como regerar</h2>
<p>Quando as receitas ou o cache mudarem, rode na raiz do Void:</p>
<div class="codigo quebra"><pre>bash tools/cadeias/gerar.sh</pre></div>
<p>O comando exporta nomes e bônus do cache, desenha os ícones que faltam e reescreve este arquivo. Veja <code>tools/cadeias/LEIA-ME.md</code>.</p>
</section>
    </article>
  </main>
</div>
<script>
(function () {{
  var campo = document.getElementById('busca');
  var ajuda = document.getElementById('busca-ajuda');
  var itens = Array.prototype.slice.call(document.querySelectorAll('.item-cadeia'));
  var total = itens.length;
  function filtrar() {{
    var q = campo.value.trim().toLowerCase();
    var vistos = 0;
    itens.forEach(function (it) {{
      var mostra = !q || it.getAttribute('data-busca').indexOf(q) >= 0;
      it.classList.toggle('escondido', !mostra);
      if (mostra) vistos++;
    }});
    document.querySelectorAll('.grupo').forEach(function (g) {{
      g.classList.toggle('escondido', !g.querySelector('.item-cadeia:not(.escondido)'));
    }});
    document.querySelectorAll('.nivel[id^="nivel-"]').forEach(function (n) {{
      n.classList.toggle('escondido', !n.querySelector('.item-cadeia:not(.escondido)'));
    }});
    ajuda.textContent = q ? vistos + ' de ' + total + ' itens com "' + q + '"' : 'Escreva parte do nome; a página esconde o resto.';
  }}
  campo.addEventListener('input', filtrar);
  var links = Array.prototype.slice.call(document.querySelectorAll('.navegacao .item'));
  var alvos = links.map(function (a) {{ return document.querySelector(a.getAttribute('href')); }});
  function marcar() {{
    var y = window.scrollY + 120;
    var atual = 0;
    alvos.forEach(function (el, i) {{ if (el && el.offsetParent !== null && el.offsetTop <= y) atual = i; }});
    links.forEach(function (a, i) {{ a.classList.toggle('atual', i === atual); }});
  }}
  window.addEventListener('scroll', marcar, {{ passive: true }});
  marcar();
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
    ap.add_argument("--saida", default=os.path.join(CASA, "Documents", "void-downloads", "cadeias-de-producao.html"))
    args = ap.parse_args()

    d = Dados(args.void, os.path.join(args.cadeias, "itens.json"))
    for a in d.avisos:
        print("aviso:", a, file=sys.stderr)
    mont = imagens.Montador(args.montador)
    pagina = Pagina(d, os.path.join(args.cadeias, "icones"), args.grafite, mont)
    saida = pagina.html()
    os.makedirs(os.path.dirname(args.saida), exist_ok=True)
    with open(args.saida, "w", encoding="utf-8") as f:
        f.write(saida)
    print(f"{len(d.no_grafo)} itens, {len(d.passos)} passos -> {args.saida} ({os.path.getsize(args.saida) // 1024} KB)")


if __name__ == "__main__":
    main()

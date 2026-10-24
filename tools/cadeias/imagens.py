"""Desenha janelas do jogo a partir do cache, com as mesmas contas do cliente 634.

Usa o que o Montador de Interfaces exporta do cache (sprites em PNG, fontes como grade de letras e
as interfaces em JSON) e repete as regras de posição e de texto conferidas no void-client
(web/jogos/runescape/interfaces.js e fonte.js do Montador).
"""
import base64
import io
import json
import os
import re
from collections import defaultdict

from PIL import Image, ImageDraw


class Fonte:
    def __init__(self, desc, atlas):
        self.id = desc["id"]
        self.glifos = desc["glyphs"]            # [x, y, w, h, desloc. x, desloc. y] por código cp1252
        self.avancos = desc["advances"]
        self.linha = desc["baseline"]           # altura de linha; o topo da letra fica em (linha de base - linha)
        self.ascent = desc["ascent"]
        self.descent = desc["descent"]
        k = desc.get("kerning")
        self.kerning = base64.b64decode(k) if isinstance(k, str) and k else None
        self.atlas = atlas.convert("RGBA")

    def kern(self, anterior, c):
        if self.kerning is None or anterior < 0:
            return 0
        b = self.kerning[anterior * 256 + c]
        return b - 256 if b > 127 else b

    def avanco(self, c):
        return int(self.avancos[c]) if c < len(self.avancos) else 0

    def largura(self, codigos):
        x = 0
        anterior = -1
        for c in codigos:
            x += self.kern(anterior, c) + self.avanco(c)
            anterior = c
        return x


def codigo(ch):
    c = ord(ch)
    if c < 0x80 or 0xa0 <= c < 0x100:
        return c
    return 63


def _pedacos(texto):
    """Divide em letras e etiquetas <br>, <col=...>, </col>."""
    out = []
    s = str(texto or "")
    i = 0
    while i < len(s):
        if s[i] == "<":
            fim = s.find(">", i + 1)
            if fim > i:
                dentro = s[i + 1:fim]
                i = fim + 1
                if dentro in ("gt", "lt", "nbsp"):
                    out.append(("letra", codigo({"gt": ">", "lt": "<", "nbsp": " "}[dentro])))
                    continue
                eq = dentro.find("=")
                out.append(("tag", dentro[:eq], dentro[eq + 1:]) if eq >= 0 else ("tag", dentro, None))
                continue
        if s[i] == "\n":
            out.append(("tag", "br", None))
            i += 1
            continue
        out.append(("letra", codigo(s[i])))
        i += 1
    return out


def _linhas(fonte, texto, limite):
    """<br> sempre quebra; com limite, quebra também nos espaços para caber na largura."""
    saida = []
    atual = []
    lista = _pedacos(texto)
    if limite is None:
        for p in lista:
            if p[0] == "tag" and p[1] == "br":
                saida.append(atual)
                atual = []
            else:
                atual.append(p)
        saida.append(atual)
        return saida
    palavra = []

    def letras(l):
        return [p[1] for p in l if p[0] == "letra"]

    def por_palavra():
        nonlocal atual, palavra
        if not palavra:
            return
        tentativa = atual + palavra
        sem_espaco_final = tentativa[:-1] if tentativa and tentativa[-1] == ("letra", 32) else tentativa
        if any(p[0] == "letra" for p in atual) and fonte.largura(letras(sem_espaco_final)) > limite:
            while atual and atual[-1] == ("letra", 32):
                atual.pop()
            saida.append(atual)
            atual = list(palavra)
        else:
            atual = tentativa
        palavra = []

    for p in lista:
        if p[0] == "tag" and p[1] == "br":
            por_palavra()
            saida.append(atual)
            atual = []
            continue
        palavra.append(p)
        if p == ("letra", 32):
            por_palavra()
    por_palavra()
    saida.append(atual)
    return saida[:100]


def desenhar_texto(canvas, fonte, texto, x, y, w, h, cor, halign=0, valign=0, sombra=True, altura_linha=0):
    """Desenha como o cliente (Class324): linha de base por alinhamento vertical, sombra preta em +1,+1."""
    altura = altura_linha or fonte.linha
    cabe = fonte.ascent + fonte.descent + altura <= h or 2 * altura <= h
    lista = _linhas(fonte, texto, max(1, w) if cabe else None)
    n = len(lista)
    if valign == 3 and n == 1:
        valign = 1
    if valign == 0:
        base = y + fonte.ascent
    elif valign == 1:
        base = y + fonte.ascent + (h - fonte.ascent - fonte.descent - altura * (n - 1)) // 2
    elif valign == 2:
        base = y + h - fonte.descent - altura * (n - 1)
    else:
        folga = max(0, (h - fonte.ascent - fonte.descent - altura * (n - 1)) // (n + 1))
        altura += folga
        base = y + fonte.ascent + folga
    rgb = ((cor >> 16) & 255, (cor >> 8) & 255, cor & 255)
    for l, linha in enumerate(lista):
        codigos = [p[1] for p in linha if p[0] == "letra"]
        lw = fonte.largura(codigos)
        lx = x
        if halign == 1:
            lx = x + (w - lw) // 2
        elif halign == 2:
            lx = x + w - lw
        topo = base - fonte.linha
        px = lx
        anterior = -1
        cor_atual = rgb
        for p in linha:
            if p[0] == "tag":
                if p[1] == "col" and p[2]:
                    v = int(p[2][-6:], 16)
                    cor_atual = ((v >> 16) & 255, (v >> 8) & 255, v & 255)
                elif p[1] == "/col":
                    cor_atual = rgb
                continue
            c = p[1]
            px += fonte.kern(anterior, c)
            if c != 32:
                g = fonte.glifos[c]
                if g and g[2] > 0 and g[3] > 0:
                    glifo = fonte.atlas.crop((g[0], g[1], g[0] + g[2], g[1] + g[3]))
                    mascara = glifo.getchannel("A")
                    if sombra:
                        canvas.paste((0, 0, 0, 255), (px + g[4] + 1, topo + g[5] + 1), mascara)
                    canvas.paste(cor_atual + (255,), (px + g[4], topo + g[5]), mascara)
            px += fonte.avanco(c)
            anterior = c
        base += altura


class Montador:
    """Lê a exportação do Montador de Interfaces (cache/jogo/exportado)."""

    def __init__(self, pasta):
        self.pasta = pasta
        with open(os.path.join(pasta, "fonts.json"), encoding="utf-8") as f:
            self.fontes_desc = {d["id"]: d for d in json.load(f)}
        self.fontes = {}
        self._sprites = {}

    def fonte(self, fid):
        if fid not in self.fontes:
            desc = self.fontes_desc[fid]
            self.fontes[fid] = Fonte(desc, Image.open(os.path.join(self.pasta, "fonts", f"{fid}.png")))
        return self.fontes[fid]

    def sprite(self, sid):
        if sid not in self._sprites:
            p = os.path.join(self.pasta, "sprites", f"{sid}.png")
            self._sprites[sid] = Image.open(p).convert("RGBA") if os.path.exists(p) else None
        return self._sprites[sid]

    def interface(self, iid):
        with open(os.path.join(self.pasta, "interfaces", f"{iid}.json"), encoding="utf-8") as f:
            return json.load(f)


def _tamanho(c, pw, ph):
    wm, hm = c.get("widthMode", 0), c.get("heightMode", 0)
    w = c["width"] if wm == 0 else pw - c["width"] if wm == 1 else (c["width"] * pw) >> 14 if wm == 2 else 0
    h = c["height"] if hm == 0 else ph - c["height"] if hm == 1 else (c["height"] * ph) >> 14 if hm == 2 else 0
    if wm == 4:
        w = h
    if hm == 4:
        h = w
    return w, h


def _posicao(modo, base, pai, lado):
    if modo == 0:
        return base
    if modo == 1:
        return int((pai - lado) / 2) + base
    if modo == 2:
        return pai - lado - base
    if modo == 3:
        return (pai * base) >> 14
    if modo == 4:
        return int((pai - lado) / 2) + ((pai * base) >> 14)
    return pai - lado - ((pai * base) >> 14)


def layout(tree, W, H, escondidos=(), visiveis=None, tamanhos=None):
    """`visiveis` força um componente visível/escondido pelo índice; `tamanhos` troca a largura e altura de um
    componente (o que um script do jogo faz ao abrir a janela)."""
    visiveis = visiveis or {}
    tamanhos = tamanhos or {}
    filhos = defaultdict(list)
    for c in tree:
        p = c.get("parent")
        filhos[-1 if p is None else p].append(c)
    for lst in filhos.values():
        lst.sort(key=lambda c: c["index"])
    saida = []

    def andar(pai, ox, oy, pw, ph, corte, esc, prof):
        if prof > 64:
            return
        for c in filhos.get(pai, []):
            w, h = _tamanho(c, pw, ph)
            if c["index"] in tamanhos:
                w, h = tamanhos[c["index"]]
            x = ox + _posicao(c.get("xMode", 0), c["x"], pw, w)
            y = oy + _posicao(c.get("yMode", 0), c["y"], ph, h)
            oculto = visiveis[c["index"]] is False if c["index"] in visiveis else bool(c.get("hidden"))
            e = esc or oculto or c["index"] in escondidos
            saida.append((c, x, y, w, h, corte, e))
            if c["type"] == 0:
                l = max(corte[0] if corte else -10**9, x)
                t = max(corte[1] if corte else -10**9, y)
                r = min(corte[2] if corte else 10**9, x + w)
                b = min(corte[3] if corte else 10**9, y + h)
                andar(c["index"], x, y, c.get("scrollWidth") or w, c.get("scrollHeight") or h, (l, t, max(l, r), max(t, b)), e, prof + 1)

    andar(-1, 0, 0, W, H, None, False, 0)
    return saida


def compor(mont, iid, W, H, textos=None, sprites=None, escondidos=(), extras=None, cores=None, visiveis=None, tamanhos=None, fundo=None):
    """Desenha a interface `iid` numa caixa W x H. `textos` e `sprites` trocam o texto/sprite de um componente
    pelo índice (o que o servidor faz por script quando a janela abre); `extras` é uma lista de
    (índice do componente, imagem) para desenhar por cima de um componente (um item num encaixe)."""
    doc = mont.interface(iid)
    canvas = Image.new("RGBA", (W, H), fundo or (0, 0, 0, 0))
    textos = textos or {}
    sprites = sprites or {}
    cores = cores or {}
    extras = dict(extras or [])
    for c, x, y, w, h, corte, esc in layout(doc["tree"], W, H, escondidos, visiveis, tamanhos):
        if esc or w < 1 or h < 1:
            continue
        if corte and (corte[2] - corte[0] < 1 or corte[3] - corte[1] < 1):
            continue  # a caixa de corte do pai não tem área: nada aparece
        camada = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        t = c["type"]
        if t == 3:
            cor = c.get("colour", 0)
            rgba = ((cor >> 16) & 255, (cor >> 8) & 255, cor & 255, 255 - (c.get("alpha", 0) & 255))
            d = ImageDraw.Draw(camada)
            if c.get("filled"):
                d.rectangle((x, y, x + w - 1, y + h - 1), fill=rgba)
            else:
                d.rectangle((x, y, x + w - 1, y + h - 1), outline=rgba)
        elif t == 5:
            sid = sprites.get(c["index"], c.get("sprite", -1))
            sp = mont.sprite(sid) if sid is not None and sid >= 0 else None
            if sp is None:
                continue
            if c.get("tiling"):
                for ty in range(y, y + h, sp.height):
                    for tx in range(x, x + w, sp.width):
                        camada.paste(sp, (tx, ty), sp)
                mascara = Image.new("L", (W, H), 0)
                ImageDraw.Draw(mascara).rectangle((x, y, x + w - 1, y + h - 1), fill=255)
                camada.putalpha(Image.composite(camada.getchannel("A"), Image.new("L", (W, H), 0), mascara))
            else:
                if (sp.width, sp.height) != (w, h):
                    sp = sp.resize((w, h), Image.NEAREST)
                camada.paste(sp, (x, y), sp)
        elif t == 4:
            texto = textos.get(c["index"], c.get("text", ""))
            if texto:
                desenhar_texto(camada, mont.fonte(c["font"]), texto, x, y, w, h, cores.get(c["index"], c.get("colour", 0)),
                               c.get("horizontalAlign", 0), c.get("verticalAlign", 0), bool(c.get("shaded")), c.get("lineHeight", 0))
        else:
            if c["index"] not in extras:
                continue
        if c["index"] in extras:
            im = extras[c["index"]]
            camada.paste(im, (x + (w - im.width) // 2, y + (h - im.height) // 2), im)
        if corte:
            l, tt, r, b = corte
            mascara = Image.new("L", (W, H), 0)
            ImageDraw.Draw(mascara).rectangle((l, tt, r - 1, b - 1), fill=255)
            camada.putalpha(Image.composite(camada.getchannel("A"), Image.new("L", (W, H), 0), mascara))
        canvas.alpha_composite(camada)
    return canvas


def icones_das_habilidades(mont):
    """Os ícones da aba de habilidades (interface 320), na ordem em que o jogo os mostra."""
    ordem = ["attack", "constitution", "mining", "strength", "agility", "smithing", "defence", "herblore", "fishing",
             "ranged", "thieving", "cooking", "prayer", "crafting", "firemaking", "magic", "fletching", "woodcutting",
             "runecrafting", "slayer", "farming", "construction", "hunter", "summoning", "dungeoneering"]
    doc = mont.interface(320)
    icones = []
    for c, x, y, w, h, corte, esc in layout(doc["tree"], 190, 261):
        if c["type"] == 5 and w == 25 and h == 25 and c.get("sprite", -1) >= 0:
            icones.append((y, x, c["sprite"]))
    icones.sort()
    return {nome: mont.sprite(sid) for nome, (_, _, sid) in zip(ordem, icones)}


def png_base64(im):
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=True)
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")


def escala(im, vezes):
    return im.resize((im.width * vezes, im.height * vezes), Image.NEAREST)

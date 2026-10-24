"""Monta as cadeias de produção a partir dos dados do Void (data/) e do que o cache exporta (itens.json).

Cada "passo" é uma transformação feita por uma habilidade: ingredientes -> produto, com nível, xp e onde se faz.
As regras que o servidor guarda em código (não em tabela) estão repetidas aqui, uma por uma, com o arquivo de origem.
"""
import glob
import json
import os
from collections import OrderedDict, defaultdict

from groml import ler

# Receitas que não produzem nada novo: despejar poção, encher saco, tingir capa.
ARQUIVOS_FORA = ("decant", "vegetable_sack", "colouring")

# Anvil.kt: os tipos de peça da bigorna e os metais da fornalha (Furnace.kt)
TIPOS_BIGORNA = ["dagger", "hatchet", "mace", "med_helm", "bolts_unf", "sword", "dart_tip", "nails", "wire", "spit", "studs",
                 "arrowtips", "scimitar", "limbs", "longsword", "knife", "full_helm", "sq_shield", "grapple_tip", "warhammer",
                 "battleaxe", "chainbody", "kiteshield", "claws", "2h_sword", "plateskirt", "platelegs", "platebody", "pickaxe",
                 "crossbow_bolt", "crossbow_limbs"]
METAIS = ["bronze", "blurite", "iron", "silver", "steel", "gold", "mithril", "adamant", "rune"]
# Jewellery.kt
GEMAS = ["gold", "sapphire", "emerald", "ruby", "diamond", "dragonstone", "onyx", "enchanted_gem"]

HABILIDADES = OrderedDict([
    ("smithing", "Smithing (forja)"),
    ("crafting", "Crafting (artesanato)"),
    ("fletching", "Fletching (arcos e flechas)"),
    ("herblore", "Herblore (herbologia)"),
    ("cooking", "Cooking (cozinha)"),
    ("runecrafting", "Runecrafting (runas)"),
    ("farming", "Farming (agricultura)"),
    ("magic", "Magic (magia)"),
    ("firemaking", "Firemaking (fogo)"),
    ("summoning", "Summoning (invocação)"),
])

ORIGENS = OrderedDict([
    ("mining", "Mining (mineração)"),
    ("woodcutting", "Woodcutting (lenha)"),
    ("fishing", "Fishing (pesca)"),
    ("farming", "Farming (agricultura): semente ou colheita"),
    ("hunter", "Hunter (caça)"),
    ("runecrafting", "Runecrafting (runas): essência e talismã"),
    ("summoning", "Summoning (invocação): bolsa, fragmento, amuleto"),
    ("magic", "Magic (magia): runas"),
])


class Dados:
    def __init__(self, raiz, itens_json):
        self.raiz = raiz
        self.data = os.path.join(raiz, "data")
        self.itens = OrderedDict()      # sid -> dict(id, campos, arquivo)
        self.cache = {}                 # sid -> registro do itens.json
        self.tabelas = {}
        self.passos = []
        self.avisos = []
        self.fora = []                  # (o que, por quê)
        self.multi_add = []
        self._carregar_itens(itens_json)
        self._carregar_tabelas()
        self._receitas()
        self._smithing()
        self._fletching()
        self._cooking()
        self._runecrafting()
        self._crafting()
        self._herblore()
        self._farming()
        self._magic()
        self._summoning()
        self._grafo()

    # ------------------------------------------------------------ leitura
    def arquivos(self, sufixo, raiz=None):
        return sorted(glob.glob(os.path.join(raiz or self.data, "**", "*." + sufixo), recursive=True))

    def _carregar_itens(self, itens_json):
        for path in self.arquivos("items.toml"):
            for nome, campos in ler(path).items():
                if nome.endswith("_noted") or nome.endswith("_lent"):
                    continue
                self.itens[nome] = {"id": campos.get("id", -1), "campos": campos, "arquivo": os.path.relpath(path, self.data)}
        with open(itens_json, encoding="utf-8") as f:
            for reg in json.load(f):
                self.cache[reg["sid"]] = reg
        # vasilhames que voltam vazios (balde, tigela, frasco): nunca são o produto de uma receita
        self.subprodutos = {c["campos"].get("empty") for c in self.itens.values() if c["campos"].get("empty")}

    def existe(self, sid):
        return sid in self.itens

    def nome(self, sid):
        reg = self.cache.get(sid)
        if reg and reg.get("name") and reg["name"] != "null":
            return reg["name"]
        return sid.replace("_", " ").capitalize()

    def _carregar_tabelas(self):
        for path in self.arquivos("tables.toml"):
            cab = None
            cabnome = None
            for nome, campos in ler(path).items():
                if "." not in nome:
                    cab, cabnome = campos, nome
                    self.tabelas.setdefault(cabnome, (cab, OrderedDict()))
                    continue
                tab, row = nome.split(".", 1)
                cabs = self.tabelas[tab][0]
                linha = OrderedDict()
                for col, tipo in cabs.items():
                    if col.endswith("_default"):
                        linha[col[:-8]] = tipo
                linha.update(campos)
                self.tabelas[tab][1][row] = linha

    def linhas(self, tab):
        return self.tabelas[tab][1]

    # ------------------------------------------------------------ passos
    @staticmethod
    def ferramenta(sid):
        return sid.startswith("watering_can")

    def produto(self, adds):
        for a, q in adds:
            if a not in self.subprodutos and not self.ferramenta(a):
                return [(a, q)]
        return adds[:1]

    @staticmethod
    def it(v):
        if isinstance(v, dict):
            return (v["id"], int(v.get("amount", v.get("charges", 1))))
        return (v, 1)

    def passo(self, skill, level, ins, outs, fonte, onde, xp=None, alt=None, tools=None, nota=None):
        for i, _ in ins:
            if not self.existe(i):
                self.avisos.append(f"{fonte}: ingrediente desconhecido {i}")
        for o, _ in outs:
            if not self.existe(o):
                self.avisos.append(f"{fonte}: produto desconhecido {o}")
        self.passos.append(dict(skill=skill, level=int(level or 1), xp=xp, ins=ins, alt=alt or [], outs=outs,
                                tools=tools or [], fonte=fonte, onde=onde, nota=nota))

    def _receitas(self):
        for path in self.arquivos("recipes.toml"):
            rel = os.path.relpath(path, self.data)
            if any(x in os.path.basename(path) for x in ARQUIVOS_FORA):
                self.fora.append((rel, "não produz item novo (despejar poção, encher saco ou tingir)"))
                continue
            pasta_skill = rel.split("/")[1] if rel.startswith("skill/") else None
            for nome, c in ler(path).items():
                skill = c.get("skill") or pasta_skill
                if c.get("message", "").startswith("You pour from one container"):
                    continue
                if not skill:
                    self.fora.append((f"{rel}: {nome}", "receita de missão ou de área, sem habilidade"))
                    continue
                add = [self.it(v) for v in c.get("add", [])]
                if not add:
                    continue
                if len(add) > 1:
                    self.multi_add.append((rel, nome, [a for a, _ in add]))
                ins = [self.it(v) for v in c.get("remove", []) if not self.ferramenta(self.it(v)[0])]
                alt = [[self.it(v)[0] for v in c.get("one", [])]] if c.get("one") else []
                tools = [self.it(v)[0] for v in c.get("requires", [])]
                onde = "juntando um item no outro"
                if skill == "cooking" and "range" in c.get("animation", ""):
                    onde = "no fogão"
                self.passo(skill, c.get("level", 1), ins, self.produto(add), f"{rel}:{nome}", onde, c.get("xp"), alt, tools,
                           "só em mundo de membros" if c.get("members") else None)

    def _smithing(self):
        for bar, r in self.linhas("bars").items():
            ores = r["ore"]
            am = list(r.get("amount") or [1])
            am += [1] * (len(ores) - len(am))
            nota = None
            if r.get("chance", 255) != 255:
                nota = f"chance de {round(r['chance'] / 255 * 100)}% por minério"
            self.passo("smithing", r.get("level", 1), list(zip(ores, am)), [(bar, 1)], f"bars.{bar}", "na fornalha",
                       (r.get("xp") or 0) / 10, nota=nota)
        self.passo("smithing", 35, [("steel_bar", 1)], [("cannonball", 4)], "Cannonballs.kt", "na fornalha, com o molde de munição", 25.6, tools=["ammo_mould"])
        ifaces = {}
        for path in self.arquivos("ifaces.toml"):
            if path.endswith("smithing.ifaces.toml"):
                ifaces = ler(path)
        for item, r in self.linhas("smithing").items():
            metal = tipo = None
            if item == "bullseye_lantern_frame":
                metal, tipo = "steel", "lantern"
            elif item == "mithril_grapple_tip":
                metal, tipo = "mithril", "grapple"
            else:
                for m in METAIS:
                    if item.startswith(m + "_") and item[len(m) + 1:] in TIPOS_BIGORNA:
                        metal, tipo = m, item[len(m) + 1:]
            if item == "cannonball":
                continue
            if not metal:
                self.fora.append((item, "está na tabela de Smithing, mas a bigorna do servidor não tem botão para fazer"))
                continue
            comp = ifaces.get("smithing." + tipo, {})
            self.passo("smithing", r.get("level", 1), [(metal + "_bar", comp.get("bars", 1))], [(item, comp.get("amount", 1))],
                       f"smithing.{item}", "na bigorna", (r.get("xp") or 0) / 10, tools=["hammer"])

    def _fletching(self):
        for log, r in self.linhas("fletchables").items():
            for prod in r["products"]:
                u = self.linhas("arrow_shafts" if prod == "arrow_shaft" else "fletching_unf").get(prod if prod != "arrow_shaft" else log)
                if not u:
                    self.avisos.append(f"fletchables.{log}: sem linha para {prod}")
                    continue
                self.passo("fletching", u.get("level", 1), [(log, 1)], [(prod, u.get("amount", 1))], f"fletchables.{log}->{prod}",
                           "entalhando o tronco com a faca", (u.get("xp") or 0) / 10, tools=["knife"])
        self.passo("fletching", 1, [("arrow_shaft", 1), ("feather", 1)], [("headless_arrow", 1)], "HeadlessArrows.kt", "juntando um item no outro", 1.0)
        for unf, r in self.linhas("fletching_bolts").items():
            prod = unf.replace("_unf", "")
            if not self.existe(prod):
                self.fora.append((unf, f"emplumar: o servidor tenta criar '{prod}', item que não existe (defeito no servidor; o certo seria runite_bolts)"))
                continue
            self.passo("fletching", r.get("level", 1), [(unf, 1), ("feather", 1)], [(prod, 1)], f"fletching_bolts.{unf}", "juntando um item no outro", (r.get("xp") or 0) / 10)
        for tip, r in self.linhas("fletching_darts").items():
            self.passo("fletching", r.get("level", 1), [(tip, 1), ("feather", 1)], [(tip.replace("_tip", ""), 1)], f"fletching_darts.{tip}", "juntando um item no outro", (r.get("xp") or 0) / 10)

    def _cooking(self):
        for raw, r in self.linhas("cooking").items():
            cooked = r.get("cooked") or raw.replace("raw", "cooked")
            onde = "no fogão" if r.get("range_only") else "na fogueira ou no fogão"
            self.passo("cooking", r.get("level", 1), [(raw, 1)], [(cooked, 1)], f"cooking.{raw}", onde, (r.get("xp") or 0) / 10,
                       nota="pode queimar" if r.get("burnt") else None)
        for prod, r in self.linhas("dairy").items():
            self.passo("cooking", r.get("level", 1), [], [(prod, 1)], f"dairy.{prod}", "na batedeira de leite", (r.get("xp") or 0) / 10, alt=[list(r["inputs"])])

    def _runecrafting(self):
        for rune, r in self.linhas("runes").items():
            ess = "pure_essence" if r.get("pure_essence") else "rune_essence"
            self.passo("runecrafting", r.get("level", 1), [(ess, 1)], [(rune, 1)], f"runes.{rune}", "no altar da runa", (r.get("xp") or 0) / 10,
                       nota=None if ess == "pure_essence" else "serve essência pura também")
        for altar, r in self.linhas("combination_runes").items():
            for k, v in r.items():
                if k.endswith("_rune") and isinstance(v, str):
                    elem = k[:-5]
                    lvl = self.linhas("runes").get(k, {}).get("level", 1)
                    self.passo("runecrafting", lvl, [("pure_essence", 1), (k, 1), (elem + "_talisman", 1)], [(v, 1)],
                               f"combination_runes.{altar}.{k}", f"no {altar.replace('_', ' ')}", r.get(k + "_xp", 0) / 10,
                               nota="metade das runas falha sem o Binding necklace")
        for tiara, r in self.linhas("tiaras").items():
            self.passo("runecrafting", 1, [("tiara", 1), (tiara.replace("_tiara", "_talisman"), 1)], [(tiara, 1)], f"tiaras.{tiara}", "no altar da runa", (r.get("xp") or 0) / 10)

    def _crafting(self):
        for item, r in self.linhas("jewellery").items():
            gem = next((g for g in GEMAS if item.startswith(g + "_")), None)
            if item == "ring_of_slaying_8":
                gem, tipo = "enchanted_gem", "ring"
            elif gem:
                tipo = item[len(gem) + 1:]
            else:
                self.avisos.append(f"jewellery.{item}: gema não reconhecida")
                continue
            ins = [("gold_bar", 1)] + ([(gem, 1)] if gem != "gold" else [])
            self.passo("crafting", r.get("level", 1), ins, [(item, 1)], f"jewellery.{item}", "na fornalha, com o molde", r.get("xp"),
                       tools=[tipo.replace("amulet_unstrung", "amulet") + "_mould"])
        for mould, r in self.linhas("silver_casting").items():
            self.passo("crafting", r.get("level", 1), [("silver_bar", 1)], [(r["product"], max(1, r.get("amount", 1)))], f"silver_casting.{mould}",
                       "na fornalha, com o molde", (r.get("xp") or 0) / 10, tools=[mould], nota=f"missão {r['quest']}" if r.get("quest") else None)
        for inp, r in self.linhas("spinning").items():
            self.passo("crafting", r.get("level", 1), [(inp, 1)], [(r["product"], 1)], f"spinning.{inp}", "na roda de fiar", (r.get("xp") or 0) / 10,
                       nota=f"missão {r['quest']}" if r.get("quest") else None)
        for inp, r in self.linhas("weaving").items():
            self.passo("crafting", r.get("level", 1), [(inp, r.get("amount", 1))], [(r["product"], 1)], f"weaving.{inp}", "no tear", (r.get("xp") or 0) / 10)
        pot = self.linhas("pottery")
        for inp, r in self.linhas("firing").items():
            for prod in r["products"]:
                p = pot.get(prod, {})
                onde = "na roda de oleiro" if inp == "soft_clay" else "no forno de cerâmica"
                self.passo("crafting", p.get("level", 1), [(inp, 1)], [(prod, 1)], f"firing.{inp}->{prod}", onde, (p.get("xp") or 0) / 10)
        self.passo("crafting", 1, [("bucket_of_sand", 1), ("soda_ash", 1)], [("molten_glass", 1)], "MoltenGlass.kt", "na fornalha", 20.0)
        for hide, r in self.linhas("tanning").items():
            self.passo("crafting", 1, [(hide, 1)], [(r["leather"], 1)], f"tanning.{hide}", "no curtidor (Ellis, em Al Kharid)", nota="paga em moedas; não dá xp")
            if r.get("hard_leather"):
                self.passo("crafting", 1, [(hide, 1)], [(r["hard_leather"], 1)], f"tanning.{hide}.hard", "no curtidor (Ellis, em Al Kharid)", nota="paga em moedas; não dá xp")

    def _herblore(self):
        for grimy, r in self.linhas("herbs").items():
            self.passo("herblore", r.get("level", 1), [(grimy, 1)], [(grimy.replace("grimy", "clean"), 1)], f"herbs.{grimy}", "limpando a erva", (r.get("xp") or 0) / 10)

    def _farming(self):
        objs = {}
        for path in self.arquivos("objs.toml", os.path.join(self.data, "skill", "farming")):
            objs.update(ler(path))
        com_colheita = [(oname, oc["harvest"]) for oname, oc in objs.items() if oc.get("harvest") and oc["harvest"] != "weeds"]

        def colheita_de(crop):
            if self.existe("grimy_" + crop):
                return "grimy_" + crop
            for oname, h in com_colheita:
                if oname.startswith(crop + "_") and "fullygrown" in oname:
                    return h
            for oname, h in com_colheita:
                if oname.startswith(crop + "_") or oname == crop:
                    return h
            return None

        for seed, reg in self.itens.items():
            c = reg["campos"]
            crop = c.get("farming_crop")
            if not crop:
                continue
            prod = colheita_de(crop)
            if not prod:
                if seed.endswith("_sapling"):
                    self.fora.append((seed, "vira uma árvore no canteiro, não um item (os troncos vêm de Woodcutting)"))
                else:
                    self.fora.append((seed, "o canteiro não devolve item ao colher"))
                continue
            self.passo("farming", c.get("farming_level", 1), [(seed, 1)], [(prod, 1)], f"seed.{seed}", f"no canteiro de {c.get('farming_patch', '?')}",
                       c.get("farming_xp"), nota="uma colheita rende várias unidades")
        for item in list(self.itens):
            if item.endswith("_seedling_w") and self.existe(item.replace("_seedling_w", "_sapling")):
                self.passo("farming", 1, [(item, 1)], [(item.replace("_seedling_w", "_sapling"), 1)], f"Farming.kt:{item}", "no inventário, com o tempo", nota="cresce sozinha depois de regada")

    def _magic(self):
        for lvl, r in self.linhas("jewellery_enchant").items():
            for tipo in ("ring", "necklace", "bracelet", "amulet"):
                par = r.get(tipo)
                if par:
                    self.passo("magic", r.get("level", 1), [(par[0], 1)], [(par[1], 1)], f"jewellery_enchant.{lvl}.{tipo}", "com o feitiço de encantar joia",
                               (r.get("xp") or 0) / 10, nota="gasta runas do feitiço")
        for bolt, r in self.linhas("enchant_bolts").items():
            self.passo("magic", r.get("level", 1), [(bolt, 10)], [(bolt + "_e", 10)], f"enchant_bolts.{bolt}", "com o feitiço Enchant Crossbow Bolt", nota="gasta runas do feitiço")
        for k, r in self.linhas("plank_make").items():
            self.passo("magic", 86, [(r["log"], 1)], [(r["plank"], 1)], f"plank_make.{k}", "com o feitiço Plank Make (livro lunar)", 90.0, nota=f"gasta runas e {r['cost']} moedas")

    def _summoning(self):
        for sid, reg in self.cache.items():
            p = reg.get("pouch")
            if not p or not self.existe(sid):
                continue
            campos = self.itens[sid]["campos"]
            ins = [(i, q) for i, q in p["ingredients"] if self.existe(i)]
            self.passo("summoning", p.get("level", 1), ins, [(sid, 1)], f"cache:{sid}", "no obelisco de Summoning", campos.get("infuse_experience"))
            scroll = p.get("scroll")
            if scroll and self.existe(scroll):
                sc = self.itens[scroll]["campos"]
                self.passo("summoning", p.get("level", 1), [(sid, 1)], [(scroll, 10)], f"cache:{sid}->scroll", "no obelisco de Summoning", sc.get("transform_experience"))

    # ------------------------------------------------------------ grafo
    def _grafo(self):
        self.feito_por = defaultdict(list)
        self.usado_em = defaultdict(list)
        for n, p in enumerate(self.passos):
            for o, _ in p["outs"]:
                self.feito_por[o].append(n)
            for i, _ in p["ins"]:
                self.usado_em[i].append(n)
            for grupo in p["alt"]:
                for i in grupo:
                    self.usado_em[i].append(n)
        self.no_grafo = {i for i in (set(self.feito_por) | set(self.usado_em)) if self.existe(i)}
        self.nivel = {}
        pilha = set()

        def calc(item):
            if item in self.nivel:
                return self.nivel[item]
            if item in pilha:
                return 0
            if item not in self.feito_por:
                self.nivel[item] = 0
                return 0
            pilha.add(item)
            best = 0
            for n in self.feito_por[item]:
                p = self.passos[n]
                for i in [i for i, _ in p["ins"]] + [i for g in p["alt"] for i in g]:
                    best = max(best, calc(i) + 1)
            pilha.discard(item)
            self.nivel[item] = best
            return best

        for item in self.no_grafo:
            calc(item)
        self.ferramentas = {t for p in self.passos for t in p["tools"]} - self.no_grafo

    # ------------------------------------------------------------ consultas
    def origem(self, sid):
        """Habilidade que define o item (pela pasta do .items.toml), para os itens que saem do mundo."""
        arq = self.itens[sid]["arquivo"]
        partes = arq.split("/")
        if partes[0] == "skill" and partes[1] in ORIGENS:
            return partes[1]
        return "outros"

    def skill_principal(self, sid):
        """A habilidade do passo mais simples que faz o item."""
        ns = self.feito_por.get(sid)
        if not ns:
            return None
        return min((self.passos[n] for n in ns), key=lambda p: (p["level"], p["skill"]))["skill"]

    def produtos(self, sid):
        outs = OrderedDict()
        for n in self.usado_em.get(sid, []):
            for o, _ in self.passos[n]["outs"]:
                if o in self.no_grafo:
                    outs[o] = True
        return sorted(outs, key=lambda o: (self.nivel.get(o, 0), self.nome(o)))

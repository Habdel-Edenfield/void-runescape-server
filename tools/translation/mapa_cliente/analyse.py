"""Maps every interface to the English text the client draws by itself from the cache (read-only).

Reads the dump written by Dump.java (interfaces, client scripts, enums, structs) and the server's
*.ifaces.toml and Kotlin sources, then writes per_interface.json, shared_scripts.json and appendix.json
next to the dump. Scripts named pt_br_* (the translation patch) are skipped, so on a patched cache the
counts only show what is still in English.
Usage:
  python3 -I tools/translation/mapa_cliente/analyse.py <repo root> <dump dir>
"""
import collections
import glob
import json
import os
import re
import sys

REPO = sys.argv[1]
OUT = sys.argv[2]

PUSH_INT, PUSH_STR, GOSUB, ENUM, ENUM_STRING, STRUCT_PARAM = 0, 3, 40, 3408, 3400, 4500
# cc_/if_setop, setopbase, settargetverb: right-click options, which stay in English
SETOP = {1300, 2300, 1305, 2305, 1306, 2306, 1310, 2310}
# A script reached from this many interfaces counts as shared and isn't added to each one
SHARED = 6
# Interfaces the gameframe opens by name (GameFrame.kt)
GAMEFRAME = set("chat_box chat_background filter_buttons private_chat health_orb prayer_orb energy_orb summoning_orb combat_styles task_system task_popup stats quest_journals inventory worn_equipment prayer_list modern_spellbook friends_list ignore_list clan_chat options emotes music_player notes area_status_icon toplevel toplevel_full".split())


def jsonl(name):
    with open(os.path.join(OUT, name), encoding="utf-8") as f:
        return [json.loads(line) for line in f]


comps = jsonl("interfaces.jsonl")
scripts = {s["id"]: s for s in jsonl("scripts.jsonl") if not (s["name"] or "").startswith("pt_br_")}
enums = {e["id"]: e for e in jsonl("enums.jsonl") if e["map"].get(str(-2**31)) != "pt_br"}
structs = {s["id"]: s for s in jsonl("structs.jsonl")}

by_iface = collections.defaultdict(list)
for c in comps:
    by_iface[c["i"]].append(c)

"""
    Which strings are shown text
"""

TAG = re.compile(r"<[^>]*>")
# Hook argument types, e.g. "IIsii", "IIvvY"
SIGNATURE = re.compile(r"^[iIsSlYzZo1gOJvdkef]{1,24}$")
# Text the server always replaces: "Line1", "qj3", "Title"...
PLACEHOLDER = re.compile(r"^(line|Line|qj|Title|Body Text|ifquestname|Line ?\d|title|text|Text|Name|name|Unknown|X days|Sample text|Item)\s?\d*\s*$")


def is_signature(s):
    return bool(SIGNATURE.match(s)) and s not in ("Is", "is")


def is_text(s):
    if s is None:
        return False
    t = TAG.sub("", s).strip()
    if len(re.findall(r"[A-Za-z]", t)) < 2 or is_signature(s) or t.lower() in ("null", "id"):
        return False
    # urls, event names, file names
    return not (" " not in t and ("/" in t or "_" in t or "." in t[1:-1]))


"""
    Scripts
"""

# Int stack effect of simple instructions, to walk back over a hook's arguments
INT_EFFECT = {0: 1, 33: 1, 1: 1, 25: 1, 42: 1, 3300: 1, 4000: -1, 4001: -1, 4002: -1, 4003: -1, 4004: 0, 4005: 0, 4011: -1, 4012: -1, 3: 0, 35: 0}


def hook_target(ins, j, sig):
    """The script id pushed before a hook's arguments, walking back from its signature at [j]"""
    need = sum(1 for ch in sig if ch not in "sY") + 1
    if "Y" in sig:
        # A trigger list: its values, then their count right before the signature
        if j < 1 or ins[j - 1][0] != PUSH_INT:
            return None
        need += ins[j - 1][1] + 1
    have = 0
    for m in range(j - 1, max(j - need - 40, -1), -1):
        op = ins[m][0]
        if op not in INT_EFFECT:
            return None
        have += INT_EFFECT[op]
        if have == need:
            return ins[m][1] if op == PUSH_INT and ins[m][1] in scripts else None
    return None


def script_info(sid):
    ins = scripts[sid]["ins"]
    strings, options, calls, hooks, enum_reads, params = [], [], set(), set(), set(), set()
    for k, x in enumerate(ins):
        op, value = x[0], x[1]
        text = x[2] if len(x) > 2 else None
        if op == PUSH_STR and is_text(text):
            if any(ins[j][0] in SETOP for j in range(k + 1, min(k + 3, len(ins)))):
                options.append(text)
            else:
                strings.append(text)
        if op == GOSUB:
            calls.add(value)
        # enum: push_int <key type>, push_int 's', push_int <enum>, <key>, enum
        if op == PUSH_INT and k >= 2 and ins[k - 1][0] == PUSH_INT and ins[k - 1][1] == 115 and ins[k - 2][0] == PUSH_INT:
            if any(ins[j][0] == ENUM for j in range(k + 1, min(k + 6, len(ins)))):
                enum_reads.add(value)
        # enum_string: push_int <enum>, <key>, enum_string
        if op == PUSH_INT and value in enums:
            if any(ins[j][0] == ENUM_STRING for j in range(k + 1, min(k + 4, len(ins)))):
                enum_reads.add(value)
        if op == STRUCT_PARAM and k >= 1 and ins[k - 1][0] == PUSH_INT:
            params.add(ins[k - 1][1])
        # Hooks set at run time (cc_/if_seton*): script id, arguments, signature
        if (1400 <= op < 1500 or 2400 <= op < 2500) and k >= 1:
            for j in range(k - 1, max(k - 4, -1), -1):
                if ins[j][0] == PUSH_STR and ins[j][2:] and is_signature(ins[j][2]):
                    target = hook_target(ins, j, ins[j][2])
                    if target is not None:
                        hooks.add(target)
                    break
    return dict(options=options, strings=strings, calls=calls, hooks=hooks, enums=enum_reads, params=params)


INFO = {sid: script_info(sid) for sid in scripts}

# Hook scripts many interfaces use (tooltips, scrollbars): counted, but not followed
GENERIC = set()


def closure(roots):
    seen, stack = set(), list(roots)
    while stack:
        sid = stack.pop()
        if sid in seen or sid not in INFO:
            continue
        seen.add(sid)
        if sid not in GENERIC:
            stack.extend(INFO[sid]["calls"] | INFO[sid]["hooks"])
    return seen


param_values = collections.defaultdict(set)
for struct in structs.values():
    for p, v in struct["p"].items():
        if isinstance(v, str) and is_text(v):
            param_values[int(p)].add(v)


def enum_texts(eid):
    e = enums.get(eid)
    if not e or e["v"] != "s":
        return []
    return [v for v in e["map"].values() if isinstance(v, str) and is_text(v)]


"""
    Server side: interface names, who opens them, which components it writes
"""


def parse_ifaces():
    names, comp_names, types = {}, {}, {}
    for path in glob.glob(REPO + "/data/**/*.ifaces.toml", recursive=True):
        current, sub = None, None
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if m := re.match(r"^\[([a-z0-9_]+)\]$", line):
                current, sub = m.group(1), None
            elif m := re.match(r"^\[\.([a-z0-9_]+)\]$", line):
                sub = m.group(1)
            elif (m := re.match(r"^id = (\d+)$", line)) and current:
                if sub is None:
                    names.setdefault(int(m.group(1)), []).append(current)
                else:
                    comp_names[(current, sub)] = int(m.group(1))
            elif (m := re.match(r'^type = "([a-z_]+)"$', line)) and current and sub is None:
                types[current] = m.group(1)
    return names, comp_names, types


iface_names, comp_names, iface_types = parse_ifaces()

opens = collections.defaultdict(set)
sendtexts = collections.defaultdict(set)
OPEN = re.compile(r'\bopen\("([a-z0-9_]+)(\$?)')
SEND_TEXT = re.compile(r'(?:sendText|sendTranslated)\(\s*"([a-z0-9_]+)"\s*,\s*"([a-z0-9_]+)')
for path in glob.glob(REPO + "/game/src/main/kotlin/**/*.kt", recursive=True) + glob.glob(REPO + "/engine/src/main/kotlin/**/*.kt", recursive=True):
    source = open(path, encoding="utf-8").read()
    relative = os.path.relpath(path, REPO).replace("game/src/main/kotlin/", "")
    for m in OPEN.finditer(source):
        # open("dialogue_npc_chat$lines") opens every name starting with "dialogue_npc_chat"
        opens[m.group(1) + ("*" if m.group(2) else "")].add(relative)
    for m in SEND_TEXT.finditer(source):
        sendtexts[m.group(1)].add(m.group(2))


def who_opens(name):
    files = set(opens.get(name, ()))
    for key, value in opens.items():
        if key.endswith("*") and name.startswith(key[:-1]):
            files |= value
    return files


"""
    Per interface
"""

iface_roots = {}
for iid, cs in by_iface.items():
    iface_roots[iid] = {h[0] for c in cs for h in c["hooks"].values() if h and isinstance(h[0], int)}
root_users = collections.Counter(s for roots in iface_roots.values() for s in roots)
GENERIC.update(s for s, n in root_users.items() if n >= SHARED)
script_users = collections.defaultdict(set)
for iid, roots in iface_roots.items():
    for sid in closure(roots):
        script_users[sid].add(iid)

results = []
for iid, cs in sorted(by_iface.items()):
    names = iface_names.get(iid, [])
    server_comps = {comp_names[(n, comp)] for n in names for comp in sendtexts.get(n, ()) if (n, comp) in comp_names}
    static, options, hook_strings = [], [], []
    for c in cs:
        if is_text(c["text"]) and c["c"] not in server_comps and not PLACEHOLDER.match(TAG.sub("", c["text"]).strip()):
            static.append((c["c"], c["text"]))
        options += [o for o in (c["options"] or []) + [c["useOption"], c["optionOverride"]] if o and is_text(o)]
        for h in c["hooks"].values():
            if h:
                hook_strings += [(h[0], a) for a in h[1:] if isinstance(a, str) and is_text(a)]
    reach = closure(iface_roots[iid])
    own = sorted(s for s in reach if len(script_users[s]) < SHARED)
    shared = sorted(s for s in reach if len(script_users[s]) >= SHARED)
    own_strings, shared_strings, enum_ids, param_ids = set(), set(), set(), set()
    for s in own:
        own_strings.update(INFO[s]["strings"])
        options += INFO[s]["options"]
        enum_ids |= INFO[s]["enums"]
        param_ids |= INFO[s]["params"]
    for s in shared:
        shared_strings.update(INFO[s]["strings"])
    enum_detail = {e: len(enum_texts(e)) for e in sorted(enum_ids) if enum_texts(e)}
    param_detail = {p: len(param_values[p]) for p in sorted(param_ids) if param_values.get(p)}
    results.append(dict(
        id=iid,
        names=names,
        types=[iface_types.get(n) for n in names],
        opened_by=sorted(set().union(*[who_opens(n) for n in names])) if names else [],
        gameframe=any(n in GAMEFRAME for n in names),
        server_text=sorted(set().union(*[sendtexts.get(n, set()) for n in names])) if names else [],
        static=len({t for _, t in static}),
        static_comp=static,
        hook=len({t for _, t in hook_strings}),
        hook_ex=sorted({t for _, t in hook_strings}),
        hook_scripts=sorted({s for s, _ in hook_strings}),
        script_strings=len(own_strings),
        script_ex=sorted(own_strings),
        own_scripts=own,
        shared_strings=len(shared_strings),
        shared_scripts=shared,
        enums=enum_detail,
        enum_total=sum(enum_detail.values()),
        params=param_detail,
        param_total=sum(param_detail.values()),
        options=len(set(options)),
        options_ex=sorted(set(options)),
    ))

with open(os.path.join(OUT, "per_interface.json"), "w", encoding="utf-8") as f:
    json.dump(results, f, ensure_ascii=False, indent=1)

shared_report = sorted(((len(v), k) for k, v in script_users.items() if len(v) >= SHARED), reverse=True)
with open(os.path.join(OUT, "shared_scripts.json"), "w", encoding="utf-8") as f:
    json.dump([{"script": k, "interfaces": n, "texts": sorted(set(INFO[k]["strings"]))} for n, k in shared_report], f, ensure_ascii=False, indent=1)


def total(r):
    return r["static"] + r["hook"] + r["script_strings"] + r["enum_total"] + r["param_total"]


# One row per interface the server knows by name, for the report's appendix table
appendix = []
for r in sorted((r for r in results if r["names"]), key=lambda r: -total(r)):
    opened = "moldura/aba" if r["gameframe"] else (f"{len(r['opened_by'])} arq." if r["opened_by"] else "?")
    examples = [e for e in r["script_ex"][:3] + r["hook_ex"][:2] + [t for _, t in r["static_comp"]][:3] if len(e) < 90][:4]
    appendix.append([r["id"], " / ".join(r["names"]), r["types"][0] or "", opened, r["static"], r["hook"], r["script_strings"], r["enum_total"] + r["param_total"], r["options"], len(r["server_text"]), examples])
with open(os.path.join(OUT, "appendix.json"), "w", encoding="utf-8") as f:
    json.dump(appendix, f, ensure_ascii=False, separators=(",", ":"))

named = [r for r in results if r["names"]]
print(f"{len(results)} interfaces, {len(named)} named, {sum(1 for r in named if total(r) > 0)} named with English text from the cache")
print(f"{len(shared_report)} shared scripts")

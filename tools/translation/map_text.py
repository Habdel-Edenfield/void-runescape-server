"""Maps every piece of player-facing text in Void and how much is translated.

Writes JSON to stdout: per region (NPCs spawned there and the scripts handling them) and per
content package (all dialogue and chat messages in the code), plus text kept in data files.
Usage: python3 -I map_text.py <repo root>
"""
import glob
import json
import os
import re
import sys

ROOT = sys.argv[1]
KOTLIN = ROOT + "/game/src/main/kotlin/"

LIT = r'"((?:[^"\\$]|\\.|\$\{[^}]*\}|\$[a-zA-Z_][a-zA-Z0-9_]*|\$)*)"'
DIALOGUE = [
    re.compile(r'\b(?:npc|player)(?:<\w+>)?\(\s*(?:(?:npcId\s*=\s*)?(?:"[a-z0-9_]+"|[A-Za-z_][\w.]*)\s*,\s*)?' + LIT),
    re.compile(r'\bmenu\(\s*' + LIT),
    re.compile(r'\bstatement\(\s*' + LIT),
    re.compile(r'\bchoice\(\s*' + LIT),
    re.compile(r'\boption(?:<\w+>)?\(\s*' + LIT),
    re.compile(r'\bitems?\(\s*"[a-z0-9_]+"\s*,\s*(?:"[a-z0-9_]+"\s*,\s*)?' + LIT),
]
MESSAGE = re.compile(r'\bmessage\(\s*' + LIT)
HANDLER = re.compile(r'\b(npcOperate|npcApproach|itemOnNPCOperate|itemOnNPCApproach)\(\s*(?:"[^"]*"\s*,\s*)?"([^"]+)"')


def load_wildcards():
    wild = {}
    path = ROOT + "/data/.temp/wildcards.txt"
    for line in open(path):
        if "|" in line:
            key, value = line.rstrip("\n").split("|", 1)
            wild[key] = set(value.split(","))
    return wild


WILD = load_wildcards()


def expand(pattern):
    out = set()
    for part in pattern.split(","):
        part = part.strip()
        out |= WILD.get(part, {part})
    return out


def translated_rows():
    text = open(ROOT + "/data/entity/player/dialogue/translation/dialogue_pt_br.tables.toml").read()
    rows = re.findall(r'^en = "((?:[^"\\]|\\.)*)"', text, re.M)
    exact = set(rows)
    patterns = []
    for row in rows:
        if re.search(r"%\d+%|%server%", row):
            regex = re.escape(row)
            regex = re.sub(r"%\\d\\+%|%[0-9]+%", ".*?", regex)
            regex = regex.replace("%server%", ".*?")
            patterns.append(re.compile("^" + re.sub(r"%\d+%", ".*?", re.escape(row)).replace("%server%", ".*?") + "$"))
    return exact, patterns


EXACT, PATTERNS = translated_rows()


def normalise(text):
    """Source literal as it reaches the player, with ${...} as a wildcard marker"""
    return re.sub(r"\$\{[^}]*\}|\$[a-zA-Z_][a-zA-Z0-9_]*", "\x00", text)


def is_translated(text):
    if "\x00" not in text:
        if text in EXACT:
            return True
        return any(p.match(text) for p in PATTERNS)
    sample = text.replace("\x00", "X")
    return any(p.match(sample) for p in PATTERNS)


FILES = {}
for path in glob.glob(KOTLIN + "**/*.kt", recursive=True):
    src = open(path).read()
    dialogue = set()
    for pattern in DIALOGUE:
        dialogue |= {normalise(m) for m in pattern.findall(src)}
    messages = {normalise(m) for m in MESSAGE.findall(src)}
    handled = set()
    for match in HANDLER.finditer(src):
        handled |= expand(match.group(2))
    FILES[path[len(KOTLIN):]] = {"dialogue": dialogue, "messages": messages, "npcs": handled}

NPC_FILES = {}
for path, info in FILES.items():
    for npc in info["npcs"]:
        NPC_FILES.setdefault(npc, set()).add(path)


def summarise(texts):
    texts = {t for t in texts if t.strip()}
    done = sum(1 for t in texts if is_translated(t))
    variable = sum(1 for t in texts if "\x00" in t)
    return {"total": len(texts), "translated": done, "variable": variable}


def region_of(path):
    rel = os.path.relpath(path, ROOT + "/data")
    parts = rel.split(os.sep)
    if parts[0] == "area" and len(parts) >= 4:
        return "/".join(parts[1:3])
    if parts[0] == "area":
        return parts[1]
    return "/".join(parts[:2]) if len(parts) > 2 else parts[0]


regions = {}
for spawn in glob.glob(ROOT + "/data/**/*.npc-spawns.toml", recursive=True):
    region = region_of(spawn)
    ids = set(re.findall(r'id = "([^"]+)"', open(spawn).read()))
    entry = regions.setdefault(region, {"npcs": set(), "files": set()})
    entry["npcs"] |= ids

result_regions = []
for region, entry in sorted(regions.items()):
    talking = {npc for npc in entry["npcs"] if npc in NPC_FILES}
    files = set()
    for npc in talking:
        files |= NPC_FILES[npc]
    dialogue = set()
    messages = set()
    for path in files:
        dialogue |= FILES[path]["dialogue"]
        messages |= FILES[path]["messages"]
    shared = sorted(p for p in files if sum(1 for n in FILES[p]["npcs"]) > 0 and not any(seg in p for seg in region.split("/")[-1:]))
    result_regions.append({
        "region": region,
        "npcs": len(entry["npcs"]),
        "talking_npcs": len(talking),
        "scripts": sorted(files),
        "dialogue": summarise(dialogue),
        "messages": summarise(messages),
    })

packages = {}
for path, info in FILES.items():
    parts = path.split("/")
    key = "/".join(parts[1:3]) if parts[0] == "content" and len(parts) > 3 else "/".join(parts[:-1]) or path
    entry = packages.setdefault(key, {"dialogue": set(), "messages": set(), "files": 0})
    entry["dialogue"] |= info["dialogue"]
    entry["messages"] |= info["messages"]
    entry["files"] += 1

result_packages = []
for key, entry in sorted(packages.items()):
    result_packages.append({
        "package": key,
        "files": entry["files"],
        "dialogue": summarise(entry["dialogue"]),
        "messages": summarise(entry["messages"]),
    })

data_text = {}
for path in glob.glob(ROOT + "/data/**/*.toml", recursive=True):
    if "/.temp/" in path or "/translation/" in path:
        continue
    text = open(path).read()
    kind = os.path.basename(path).split(".", 1)[1] if "." in os.path.basename(path) else path
    examines = re.findall(r'^examine = "((?:[^"\\]|\\.)*)"', text, re.M)
    messages = re.findall(r'^[a-z_]*message[a-z_]* = "((?:[^"\\]|\\.)*)"', text, re.M)
    entry = data_text.setdefault(kind, {"examine": set(), "messages": set()})
    entry["examine"] |= set(examines)
    entry["messages"] |= set(messages)

result_data = {}
for kind, entry in data_text.items():
    if entry["examine"] or entry["messages"]:
        result_data[kind] = {"examine": summarise(entry["examine"]), "messages": summarise(entry["messages"])}

all_dialogue = set()
all_messages = set()
for info in FILES.values():
    all_dialogue |= info["dialogue"]
    all_messages |= info["messages"]

json.dump({
    "totals": {"dialogue": summarise(all_dialogue), "messages": summarise(all_messages)},
    "regions": result_regions,
    "packages": result_packages,
    "data": result_data,
}, sys.stdout, indent=1, default=list)

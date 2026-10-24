"""Prints what analyse.py found for some interfaces: who opens them and their English text by source.
Usage:
  python3 -I tools/translation/mapa_cliente/show.py <dump dir> <interface id>... [--all]
Without --all only the first 12 texts of each source are shown.
"""
import json
import os
import sys

OUT = sys.argv[1]
ids = [int(a) for a in sys.argv[2:] if a != "--all"]
limit = None if "--all" in sys.argv else 12

results = {r["id"]: r for r in json.load(open(os.path.join(OUT, "per_interface.json"), encoding="utf-8"))}
enums = {}
for line in open(os.path.join(OUT, "enums.jsonl"), encoding="utf-8"):
    e = json.loads(line)
    enums[e["id"]] = e
params = {}
for line in open(os.path.join(OUT, "structs.jsonl"), encoding="utf-8"):
    for key, value in json.loads(line)["p"].items():
        if isinstance(value, str):
            params.setdefault(int(key), set()).add(value)

for iid in ids:
    r = results.get(iid)
    if r is None:
        print(f"===== {iid}: not in the cache")
        continue
    print(f"===== {iid} {' / '.join(r['names']) or '(no server name)'} {r['types']}")
    opened = (["gameframe"] if r["gameframe"] else []) + r["opened_by"]
    print(f"  opened by: {', '.join(opened) or '?'}")
    print(f"  server writes: {', '.join(r['server_text']) or '-'}")
    print(f"  1 fixed ({r['static']}): {[f'{c}:{t}' for c, t in r['static_comp']][:limit]}")
    print(f"  2 scripts ({r['script_strings']}, scripts {r['own_scripts'][:30]}): {r['script_ex'][:limit]}")
    print(f"    + {r['shared_strings']} in scripts shared with other interfaces {r['shared_scripts'][:20]}")
    for enum, count in r["enums"].items():
        values = [v for v in enums[int(enum)]["map"].values() if isinstance(v, str)]
        print(f"  3 enum {enum} ({count}): {values[:5]}")
    for param, count in r["params"].items():
        print(f"  3 struct param {param} ({count}): {sorted(params.get(int(param), []))[:4]}")
    print(f"  4 tooltips ({r['hook']}, scripts {r['hook_scripts']}): {r['hook_ex'][:limit]}")
    print(f"  right-click options ({r['options']}): {r['options_ex'][:limit]}")

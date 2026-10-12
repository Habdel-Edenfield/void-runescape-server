"""Splits sources/missing.json (from gen_dialogue.py --dump) into N balanced groups for parallel translation.

Usage: python3 -I tools/translation/split_groups.py tools/translation/sources 6
Writes group_1.json ... group_N.json next to missing.json; whole scripts stay in one group.
"""
import json
import sys

folder, count = sys.argv[1], int(sys.argv[2])
missing = json.load(open(folder + "/missing.json"))
groups = [[] for _ in range(count)]
sizes = [0] * count
for script, lines in sorted(missing.items(), key=lambda kv: -len(kv[1])):
    index = sizes.index(min(sizes))
    groups[index].append((script, lines))
    sizes[index] += len(lines)
for index, group in enumerate(groups, 1):
    json.dump({script: lines for script, lines in group}, open(f"{folder}/group_{index}.json", "w"), indent=1, ensure_ascii=False)
    print(index, sizes[index - 1], [script.split("/")[-1] for script, _ in group])

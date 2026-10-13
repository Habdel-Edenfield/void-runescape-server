"""Turns the QuestJournalTranslationTest dump (TRANSLATION_DUMP) into group_journal_N.json files, one quest per section.

Usage: python3 -I tools/translation/journal_groups.py <dump> tools/translation/sources <groups>"""
import json, re, sys, collections, glob
dump, out = sys.argv[1], sys.argv[2]
rules = [
    (r"<maroon>\d+<navy> rune essences", "<maroon>%1%<navy> rune essences"),
    (r"<maroon>\d+<navy> quantities", "<maroon>%1%<navy> quantities"),
    (r"<navy> - I need \d+ more\.", "<navy> - I need %1% more."),
]
# Lines the test doesn't reach: they need items in the inventory, a timer or a counter
extra = {
    "tears_of_guthix": ["<navy>I will be able to collect the Tears of Guthix <maroon>in %1% day<navy>.",
                        "<navy>I will be able to collect the Tears of Guthix <maroon>in %1% days<navy>."],
    "demon_slayer": ["<maroon>Traiborn<navy> needs <maroon>%1%<navy> more <maroon>bones<navy>."],
    "in_search_of_the_myreque": ["<navy>I need to collect <maroon>%1% <navy>more <maroon>wooden planks"],
    "imp_catcher": [f"<str>1 {c} Bead." for c in ["Black", "Red", "White", "Yellow"]],
    "dorics_quest": ["<str>6 Clay.", "<str>4 Copper Ore.", "<str>2 Iron Ore."],
}
quests = collections.OrderedDict()
for line in open(dump, encoding="utf-8"):
    quest, text = line.rstrip("\n").split("\t", 1)
    for pattern, repl in rules:
        text = re.sub(pattern, repl, text)
    quests.setdefault(quest, [])
    if text not in quests[quest]:
        quests[quest].append(text)
for quest, texts in extra.items():
    for t in texts:
        if t not in quests.setdefault(quest, []):
            quests[quest].append(t)
files = {}
for f in glob.glob("game/src/main/kotlin/content/quest/**/*.kt", recursive=True):
    m = re.search(r'questJournalOpen\("([^"]+)"', open(f).read())
    if m:
        files[m.group(1)] = f
# Balance into groups by total characters
n = int(sys.argv[3])
groups = [dict() for _ in range(n)]
sizes = [0] * n
for quest, texts in sorted(quests.items(), key=lambda q: -sum(map(len, q[1]))):
    i = sizes.index(min(sizes))
    groups[i][f"Quest journal '{quest}' - {files.get(quest, '?')}"] = [{"kind": "journal", "en": t} for t in texts]
    sizes[i] += sum(map(len, texts))
for i, g in enumerate(groups, 1):
    json.dump(g, open(f"{out}/group_journal_{i}.json", "w"), ensure_ascii=False, indent=1)
    print(i, sum(len(v) for v in g.values()), sizes[i - 1], list(k.split("'")[1] for k in g))

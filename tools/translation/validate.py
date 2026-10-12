"""Validates a translation file against its group. Usage: python3 -I validate.py group_N.json pt_groupN.json"""
import json, re, sys
group = json.load(open(sys.argv[1]))
pt = json.load(open(sys.argv[2]))
expected = [line["en"] for lines in group.values() for line in lines]
problems = []
for en in expected:
    if en not in pt:
        problems.append(f"MISSING: {en}")
        continue
    text = pt[en]
    if not text.strip():
        problems.append(f"EMPTY: {en}")
    bad = [c for c in text if ord(c) > 0xFF or ord(c) < 0x20]
    if bad:
        problems.append(f"BAD CHARACTERS {bad!r}: {text}")
    if "\\" in text:
        problems.append(f"BACKSLASH: {text}")
    for token in set(re.findall(r"%server%|%\d+%", en)):
        if token not in text:
            problems.append(f"LOST {token}: {text}")
    for tag in re.findall(r"<[^>]+>", en):
        if tag not in text:
            problems.append(f"LOST TAG {tag}: {text}")
    if len(text) > max(60, len(en) * 1.6):
        problems.append(f"TOO LONG ({len(text)} vs {len(en)}): {text}")
extra = [k for k in pt if k not in expected]
for k in extra:
    problems.append(f"UNKNOWN KEY (en changed?): {k}")
print("\n".join(problems) if problems else f"OK: {len(expected)} lines")
sys.exit(1 if problems else 0)

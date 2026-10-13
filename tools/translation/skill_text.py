"""Reads the text the skills show the player, the same way SkillTranslationTest does.

Code: chat messages and dialogue lines in game/src/main/kotlin/content/skill/**. Words the code chooses
between (if/else, when, "slot".plural(n), .an()) become one line per word, the rest `%1%`, `%2%`...
Data: message columns of data/skill/**/*.tables.toml and *.recipes.toml, and items' eat_message.

  python3 -I tools/translation/skill_text.py <repo root>    # prints counts
"""
import glob
import itertools
import os
import re
import sys

CALLS = [
    ("chat", re.compile(r'\bmessage\(\s*(?:text\s*=\s*)?(?=")')),
    ("dialogue", re.compile(r'\b(?:npc|player)(?:<\w+>)?\(\s*(?:(?:npcId\s*=\s*)?(?:"[a-z0-9_]+"|[A-Za-z_][\w.]*)\s*,\s*)?(?=")')),
    ("dialogue", re.compile(r'\bstatement\(\s*(?=")')),
    ("dialogue", re.compile(r'\bchoice\(\s*(?=")')),
    ("dialogue", re.compile(r'\boption(?:<\w+>)?\(\s*(?=")')),
    ("dialogue", re.compile(r'\bitem\(\s*"[a-z0-9_]+"\s*,\s*(?=")')),
    ("dialogue", re.compile(r'\bitems\(\s*"[a-z0-9_]+"\s*,\s*"[a-z0-9_]+"\s*,\s*(?=")')),
    # The title of the how-many box: makeAmount(..., text = "How many would you like to make?")
    ("dialogue", re.compile(r'\btext\s*=\s*(?=")')),
]
# message(when (x) { 1 -> "..." else -> "..." }): each branch is a chat message
MESSAGE_WHEN = re.compile(r"\bmessage\(\s*when\b[^{]*\{")
BRANCH = re.compile(r'->\s*(?=")')
# Ids passed to calls with the same names: Tables.npc("traps.$trap.npc"), row.npc("caught")
ID = re.compile(r"^[a-z0-9_.%]*[._][a-z0-9_.%]*$|^[a-z]+$")
NOT_TEXT = {"  Room at (%1%, %2%) [%3%] contains key(s): [ %4% ]"}

TABLE_COLUMNS = {"message", "cooked_message", "burnt_message", "tip", "question", "failure", "requires_message"}
ITEM_COLUMNS = {"eat_message", "cast"}
TOML_TEXT = re.compile(r'^([a-z_]+) = "((?:[^"\\]|\\.)*)"\s*(?:#.*)?$')


def char_end(code, start):
    end = code.find("'", start + (3 if code[start + 1:start + 2] == "\\" else 2))
    return len(code) if end == -1 else end + 1


def without_comments(source):
    out = []
    i = 0
    quoted = False
    while i < len(source):
        c = source[i]
        if quoted and c == "\\":
            out.append(source[i:i + 2])
            i += 2
            continue
        if not quoted and c == "'":
            end = char_end(source, i)
            out.append(source[i:end])
            i = end
            continue
        if c == '"':
            quoted = not quoted
        elif not quoted and source.startswith("//", i):
            end = source.find("\n", i)
            i = len(source) if end == -1 else end
            continue
        elif not quoted and source.startswith("/*", i):
            end = source.find("*/", i + 2)
            end = len(source) if end == -1 else end + 2
            out.append(re.sub(r"[^\n]", " ", source[i:end]))
            i = end
            continue
        out.append(c)
        i += 1
    return "".join(out)


def block_end(code, start):
    """The index of the brace closing the block opened just before start"""
    depth = 1
    i = start
    while depth > 0:
        c = code[i]
        if c == '"':
            i = read(code, i)[1]
            continue
        if c == "'":
            i = char_end(code, i)
            continue
        depth += {"{": 1, "}": -1}.get(c, 0)
        i += 1
    return i - 1


def read(code, start):
    """The parts of the string literal opening at start: ("text", str) or ("expr", source), and the index after it"""
    parts = []
    text = []

    def flush():
        if text:
            parts.append(("text", "".join(text)))
            text.clear()

    i = start + 1
    while i < len(code):
        c = code[i]
        if c == '"':
            flush()
            return parts, i + 1
        if c == "\\":
            nxt = code[i + 1]
            if nxt == "u":
                text.append(chr(int(code[i + 2:i + 6], 16)))
                i += 6
                continue
            text.append({"n": "\n", "t": "\t"}.get(nxt, nxt))
            i += 2
        elif c == "$" and code[i + 1:i + 2] == "{":
            flush()
            depth = 1
            j = i + 2
            while depth > 0:
                ch = code[j]
                if ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
                elif ch == '"':
                    j = read(code, j)[1]
                    continue
                elif ch == "'":
                    j = char_end(code, j)
                    continue
                j += 1
            parts.append(("expr", code[i + 2:j - 1].strip()))
            i = j
        elif c == "$" and (code[i + 1:i + 2].isalpha() or code[i + 1:i + 2] == "_"):
            flush()
            j = i + 1
            while j < len(code) and (code[j].isalnum() or code[j] == "_"):
                j += 1
            parts.append(("expr", code[i + 1:j]))
            i = j
        else:
            text.append(c)
            i += 1
    raise ValueError(f"Unterminated string at {start}")


IF_ELSE = re.compile(r"^if\s*\(.*?\)\s*(.+?)\s+else\s+(.+)$", re.S)
WHEN_BRANCH = re.compile(r'->\s*"((?:[^"\\$]|\\.)*)"')
PLURAL = re.compile(r'^"([^"$]*)"\.plural\(')
ELVIS = re.compile(r'\?:\s*"([^"$]*)"$')
STRING = re.compile(r'^"([^"$]*)"$')
VALUE = None  # a placeholder


def plural(word, count):
    return word if count == 1 else (word if word.endswith("s") else word + "s")


def alternatives(expr):
    """The words an expression chooses between, None standing for a value the code computes"""
    match = IF_ELSE.match(expr)
    if match:
        return [(STRING.match(b.strip()).group(1) if STRING.match(b.strip()) else VALUE) for b in match.groups()]
    if expr.startswith("when"):
        return [m.group(1) for m in WHEN_BRANCH.finditer(expr)] or [VALUE]
    match = PLURAL.match(expr)
    if match:
        return [plural(match.group(1), 1), plural(match.group(1), 2)]
    if expr.endswith(".an()"):
        return [" a", " an", ""]
    match = ELVIS.search(expr)
    if match:
        return [match.group(1), VALUE]
    return [VALUE]


def keys(parts):
    """Table keys for a string: one per combination of chosen words, computed values as %1%, %2%..."""
    options = [[p[1]] if p[0] == "text" else alternatives(p[1]) for p in parts]
    combinations = 1
    for option in options:
        combinations *= len(option)
    if combinations > 8:
        # e.g. the gem bag's four plurals: one line with placeholders reads better than 16
        options = [[p[1]] if p[0] == "text" else [VALUE] for p in parts]
    out = []
    for combination in itertools.islice(itertools.product(*options), 64):
        count = 0
        text = []
        for value in combination:
            if value is VALUE:
                count += 1
                text.append(f"%{count}%")
            else:
                text.append(value)
        key = "".join(text)
        if key not in out:
            out.append(key)
    return out


def pattern(parts):
    count = 0
    text = []
    for kind, value in parts:
        if kind == "text":
            text.append(value)
        else:
            count += 1
            text.append(f"%{count}%")
    return "".join(text)


def code_texts(root):
    """[(skill file relative to content/skill, kind, pattern, [keys])] in file order"""
    skills = os.path.join(root, "game/src/main/kotlin/content/skill")
    found = []
    for path in sorted(glob.glob(skills + "/**/*.kt", recursive=True)):
        rel = os.path.relpath(path, skills)
        if rel.endswith("Commands.kt"):
            continue
        code = without_comments(open(path).read())
        starts = {}
        for kind, regex in CALLS:
            for m in regex.finditer(code):
                starts.setdefault(m.end(), kind)
        for m in MESSAGE_WHEN.finditer(code):
            for branch in BRANCH.finditer(code, m.end(), block_end(code, m.end())):
                starts.setdefault(branch.end(), "chat")
        seen = set()
        for start in sorted(starts):
            if code.startswith('"""', start):
                continue
            kind = starts[start]
            parts = read(code, start)[0]
            if not any(k == "text" and re.search("[A-Za-z]", v) for k, v in parts) or any(k == "text" and "\n" in v for k, v in parts):
                continue
            text = pattern(parts)
            if text in seen or text in NOT_TEXT or ID.match(text):
                continue
            seen.add(text)
            found.append((rel, kind, text, keys(parts)))
    return found


def unescape(text):
    return re.sub(r"\\(.)", r"\1", text)


def data_texts(root):
    """[(file relative to data, [texts])]"""
    data = os.path.join(root, "data")
    files = sorted(glob.glob(data + "/skill/**/*.tables.toml", recursive=True) + glob.glob(data + "/skill/**/*.recipes.toml", recursive=True) + glob.glob(data + "/**/*.items.toml", recursive=True))
    out = []
    for path in sorted(set(files)):
        columns = ITEM_COLUMNS if path.endswith(".items.toml") else TABLE_COLUMNS
        texts = []
        for line in open(path):
            m = TOML_TEXT.match(line.rstrip("\n"))
            # A table's header declares each column's type: cooked_message = "string"
            if m and m.group(1) in columns and m.group(2) != "string":
                text = unescape(m.group(2))
                if text not in texts:
                    texts.append(text)
        if texts:
            out.append((os.path.relpath(path, data), texts))
    return out


if __name__ == "__main__":
    code = code_texts(sys.argv[1])
    data = data_texts(sys.argv[1])
    print(len(code), "code strings,", sum(len(k) for *_, k in code), "keys;", sum(len(t) for _, t in data), "data texts in", len(data), "files")

"""Leitor do formato de configuração do Void (Groml: um TOML simplificado com seções `[.filha]`).

Só lê; segue config/README.md do Void: strings com aspas duplas (com escape) ou simples, números,
booleanos, listas (podem ocupar várias linhas), tabelas em linha `{ id = "x", amount = 2 }`,
comentários com `#`, seção `[.nome]` que herda o nome da última seção sem ponto, e `clone = "outra"`.
"""
import re
from collections import OrderedDict


class Groml:
    def __init__(self, text):
        self.t = text
        self.i = 0

    def ws(self, nl=True):
        t = self.t
        while self.i < len(t):
            c = t[self.i]
            if c == "#":
                while self.i < len(t) and t[self.i] != "\n":
                    self.i += 1
            elif c in " \t\r" or (nl and c == "\n"):
                self.i += 1
            else:
                break

    def value(self):
        self.ws()
        c = self.t[self.i]
        if c == '"':
            return self.basic()
        if c == "'":
            j = self.t.index("'", self.i + 1)
            s = self.t[self.i + 1:j]
            self.i = j + 1
            return s
        if c == "[":
            self.i += 1
            out = []
            while True:
                self.ws()
                if self.t[self.i] == "]":
                    self.i += 1
                    return out
                out.append(self.value())
                self.ws()
                if self.t[self.i] == ",":
                    self.i += 1
        if c == "{":
            self.i += 1
            out = OrderedDict()
            while True:
                self.ws()
                if self.t[self.i] == "}":
                    self.i += 1
                    return out
                k = self.key()
                self.ws()
                if self.t[self.i] != "=":
                    raise ValueError("esperava '=' em " + self.t[self.i:self.i + 40])
                self.i += 1
                out[k] = self.value()
                self.ws()
                if self.t[self.i] == ",":
                    self.i += 1
        m = re.match(r"[+-]?[0-9_]+(\.[0-9_]+)?([eE][+-]?[0-9]+)?", self.t[self.i:])
        if m and m.group(0):
            s = m.group(0)
            self.i += len(s)
            s = s.replace("_", "")
            return float(s) if ("." in s or "e" in s or "E" in s) else int(s)
        if self.t.startswith("true", self.i):
            self.i += 4
            return True
        if self.t.startswith("false", self.i):
            self.i += 5
            return False
        raise ValueError("valor inválido: " + self.t[self.i:self.i + 40])

    def basic(self):
        self.i += 1
        out = []
        esc = {"n": "\n", "t": "\t", "r": "\r", '"': '"', "\\": "\\"}
        while True:
            c = self.t[self.i]
            if c == "\\":
                n = self.t[self.i + 1]
                out.append(esc.get(n, n))
                self.i += 2
            elif c == '"':
                self.i += 1
                return "".join(out)
            else:
                out.append(c)
                self.i += 1

    def key(self):
        self.ws()
        m = re.match(r"[A-Za-z0-9_\-.+']+", self.t[self.i:])
        if not m:
            raise ValueError("chave inválida: " + self.t[self.i:self.i + 40])
        self.i += len(m.group(0))
        return m.group(0)

    def parse(self):
        """-> OrderedDict nome_da_secao -> OrderedDict chave -> valor"""
        sections = OrderedDict()
        cur = None
        base = ""
        while True:
            self.ws()
            if self.i >= len(self.t):
                break
            if self.t[self.i] == "[":
                j = self.t.index("]", self.i)
                name = self.t[self.i + 1:j].strip()
                self.i = j + 1
                if name.startswith("."):
                    name = base + name
                else:
                    base = name
                cur = OrderedDict()
                sections[name] = cur
                continue
            k = self.key()
            self.ws(nl=False)
            if self.t[self.i] != "=":
                raise ValueError(f"esperava '=' depois de {k}: " + self.t[self.i:self.i + 40])
            self.i += 1
            v = self.value()
            if k == "clone" and isinstance(v, str) and v in sections:
                for ck, cv in sections[v].items():
                    cur.setdefault(ck, cv)
            else:
                cur[k] = v
        return sections


def ler(path):
    with open(path, encoding="utf-8") as f:
        try:
            return Groml(f.read()).parse()
        except Exception as e:
            raise RuntimeError(f"{path}: {e}") from e

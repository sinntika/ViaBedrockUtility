#!/usr/bin/env python3
# Applies the yarn -> mojang mapping table in mappings.tsv to all mod sources,
# then repairs any file whose name no longer matches its public type.
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "src", "main", "java")
MAP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mappings.tsv")

imports = []
names = []
literals = []

with open(MAP, "r", encoding="utf-8") as fh:
    for raw in fh:
        line = raw.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        parts = [p for p in line.split("\t") if p != ""]
        if len(parts) != 3:
            print("bad line: %r" % line)
            continue
        kind, src, dst = parts[0].strip(), parts[1].strip(), parts[2].strip()
        if kind == "I":
            imports.append((src, dst))
        elif kind == "N":
            names.append((src, dst))
        elif kind == "L":
            literals.append((src, dst))
        else:
            print("bad kind: %r" % line)

# longest source first so EntityRenderer never clobbers EntityRendererFactory
imports.sort(key=lambda p: len(p[0]), reverse=True)
names.sort(key=lambda p: len(p[0]), reverse=True)

files = []
for dirpath, dirnames, filenames in os.walk(SRC):
    for fn in filenames:
        if fn.endswith(".java"):
            files.append(os.path.join(dirpath, fn))
files.sort()

changed = 0
for path in files:
    with open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    orig = text
    for s, d in imports:
        text = text.replace(s, d)
        text = text.replace(s.replace(".", "/"), d.replace(".", "/"))
    for s, d in names:
        text = re.sub(r"\b%s\b" % re.escape(s), d, text)
    for s, d in literals:
        text = text.replace(s, d)
    if text != orig:
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)
        changed += 1
print("rewrote %d files" % changed)

type_re = re.compile(
    r"^public\s+(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+|static\s+)*"
    r"(?:@interface|class|interface|enum|record)\s+(\w+)",
    re.M,
)
renamed = 0
for path in files:
    if not os.path.exists(path):
        continue
    base = os.path.basename(path)[:-5]
    with open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    m = type_re.search(text)
    if m and m.group(1) != base:
        new_path = os.path.join(os.path.dirname(path), m.group(1) + ".java")
        if not os.path.exists(new_path):
            os.rename(path, new_path)
            print("renamed file %s.java -> %s.java" % (base, m.group(1)))
            renamed += 1
print("renamed %d files" % renamed)

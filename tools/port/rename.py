#!/usr/bin/env python3
"""Rewrite yarn (1.21.5) names to official Mojang names.

The mapping table lives next to this script in mappings.tsv. Each line is
"<kind>\t<from>\t<to>" where kind is:

  I  fully qualified name. Both the dotted form (imports, javadoc) and the
     slash form (mixin descriptors such as Lnet/minecraft/util/Identifier;)
     are rewritten.
  N  simple name, rewritten on word boundaries only.
  L  plain literal substring, applied last (method renames, descriptors).

Longer keys are applied first so that e.g. EntityRendererFactory is handled
before EntityRenderer. The script is idempotent: once a name has been
rewritten it no longer matches any key, so it can run on every build.
"""

import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
SOURCE_DIR = os.path.join(REPO_ROOT, "src", "main", "java")
MAPPING_FILE = os.path.join(SCRIPT_DIR, "mappings.tsv")


def load_mappings():
    qualified = []
    simple = []
    literal = []

    with open(MAPPING_FILE, encoding="utf-8") as handle:
        for number, raw in enumerate(handle, start=1):
            line = raw.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue

            parts = [part for part in line.split("\t") if part != ""]
            if len(parts) != 3:
                print("mappings.tsv:%d: ignoring malformed line" % number)
                continue

            kind, source, target = parts[0].strip(), parts[1].strip(), parts[2].strip()
            if kind == "I":
                qualified.append((source, target))
            elif kind == "N":
                simple.append((source, target))
            elif kind == "L":
                literal.append((source, target))
            else:
                print("mappings.tsv:%d: unknown kind %r" % (number, kind))

    qualified.sort(key=lambda pair: len(pair[0]), reverse=True)
    simple.sort(key=lambda pair: len(pair[0]), reverse=True)
    return qualified, simple, literal


def convert(text, qualified, simple, literal):
    for source, target in qualified:
        text = text.replace(source, target)
        text = text.replace(source.replace(".", "/"), target.replace(".", "/"))

    for source, target in simple:
        text = re.sub(r"\b%s\b" % re.escape(source), target, text)

    for source, target in literal:
        text = text.replace(source, target)

    return text


def main():
    if not os.path.isdir(SOURCE_DIR):
        print("no source directory at %s" % SOURCE_DIR)
        return 1

    qualified, simple, literal = load_mappings()
    print(
        "loaded %d qualified, %d simple and %d literal rules"
        % (len(qualified), len(simple), len(literal))
    )

    changed = []
    for directory, _subdirectories, filenames in os.walk(SOURCE_DIR):
        for filename in sorted(filenames):
            if not filename.endswith(".java"):
                continue

            path = os.path.join(directory, filename)
            with open(path, encoding="utf-8") as handle:
                original = handle.read()

            updated = convert(original, qualified, simple, literal)
            if updated != original:
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write(updated)
                changed.append(os.path.relpath(path, REPO_ROOT))

    print("rewrote %d files" % len(changed))
    for path in changed:
        print("  %s" % path)
    return 0


if __name__ == "__main__":
    sys.exit(main())

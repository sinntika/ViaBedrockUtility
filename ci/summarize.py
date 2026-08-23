#!/usr/bin/env python3
"""Condense a Gradle log into a unique list of javac errors.

Usage: python3 ci/summarize.py <log file> [workspace prefix to strip]

Gradle prints the compiler output twice (once while compiling and once in the
failure report), so plain grep produces a report full of duplicates. This keeps
each distinct file/line/message together with its symbol and location details.
"""

import re
import sys

ERROR = re.compile(r"^\s*(\S+\.java):(\d+): error: (.*)$")
DETAIL = re.compile(r"^\s+(symbol|location|required|found|reason):")
LIMIT = 400


def main():
    if len(sys.argv) < 2:
        print("usage: summarize.py <log file> [prefix]")
        return 1

    prefix = sys.argv[2] + "/" if len(sys.argv) > 2 and sys.argv[2] else ""

    with open(sys.argv[1], encoding="utf-8", errors="replace") as handle:
        lines = handle.read().splitlines()

    seen = set()
    errors = []

    for index, raw in enumerate(lines):
        line = raw.replace(prefix, "") if prefix else raw
        match = ERROR.match(line)
        if not match:
            continue

        details = []
        cursor = index + 1
        while cursor < len(lines) and DETAIL.match(lines[cursor]):
            details.append(" ".join(lines[cursor].split()))
            cursor += 1

        key = (match.group(1), match.group(2), match.group(3), tuple(details))
        if key in seen:
            continue

        seen.add(key)
        text = "%s:%s: %s" % (match.group(1), match.group(2), match.group(3))
        if details:
            text += "  [" + " | ".join(details) + "]"
        errors.append(text)

    print("unique errors: %d" % len(errors))
    for text in errors[:LIMIT]:
        print(text)
    if len(errors) > LIMIT:
        print("... %d more" % (len(errors) - LIMIT))
    return 0


if __name__ == "__main__":
    sys.exit(main())

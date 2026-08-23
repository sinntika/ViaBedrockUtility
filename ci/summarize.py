#!/usr/bin/env python3
# Collapses a gradle log into a compact list of unique javac errors with details.
import re
import sys

log = sys.argv[1] if len(sys.argv) > 1 else "ci/gradle.log"
prefix = sys.argv[2] if len(sys.argv) > 2 else ""

err_re = re.compile(r"^\s*(\S+\.java):(\d+): error: (.*)$")
det_re = re.compile(r"^\s+(symbol|location|required|found|reason):\s*(.*)$")

with open(log, "r", errors="replace") as fh:
    lines = fh.read().splitlines()

seen = set()
out = []
i = 0
while i < len(lines):
    m = err_re.match(lines[i])
    if not m:
        i += 1
        continue
    f, ln, msg = m.group(1), m.group(2), m.group(3).strip()
    if prefix and f.startswith(prefix):
        f = f[len(prefix):].lstrip("/")
    if f.startswith("src/main/java/org/oryxel/viabedrockutility/"):
        f = f[len("src/main/java/org/oryxel/viabedrockutility/"):]
    details = []
    j = i + 1
    while j < len(lines):
        d = det_re.match(lines[j])
        if not d:
            break
        details.append("%s: %s" % (d.group(1), d.group(2).strip()))
        j += 1
    key = (f, ln, msg, tuple(details))
    if key not in seen:
        seen.add(key)
        s = "%s:%s: %s" % (f, ln, msg)
        if details:
            s += "   [" + " | ".join(details) + "]"
        out.append(s)
    i = max(j, i + 1)

print("unique errors: %d" % len(out))
for s in out[:250]:
    print(s)

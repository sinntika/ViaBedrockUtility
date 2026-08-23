#!/usr/bin/env python3
# Collapses a gradle log into unique javac errors, each with the offending
# source line and javac's symbol/location detail.
import re
import sys

log = sys.argv[1] if len(sys.argv) > 1 else "ci/gradle.log"
prefix = sys.argv[2] if len(sys.argv) > 2 else ""

err_re = re.compile(r"^\s*(\S+\.java):(\d+): error: (.*)$")
det_re = re.compile(r"^\s+(symbol|location|required|found|reason):\s*(.*)$")
MOD = "src/main/java/org/oryxel/viabedrockutility/"

with open(log, "r", errors="replace") as fh:
    lines = fh.read().splitlines()

seen = set()
out = []
for i, line in enumerate(lines):
    m = err_re.match(line)
    if not m:
        continue
    f, ln, msg = m.group(1), m.group(2), m.group(3).strip()
    if prefix and f.startswith(prefix):
        f = f[len(prefix):].lstrip("/")
    if f.startswith(MOD):
        f = f[len(MOD):]
    src = ""
    details = []
    for j in range(i + 1, min(i + 7, len(lines))):
        nxt = lines[j]
        if err_re.match(nxt):
            break
        d = det_re.match(nxt)
        if d:
            details.append("%s: %s" % (d.group(1), d.group(2).strip()))
            continue
        if details:
            break
        t = nxt.strip()
        if t and not t.startswith("^") and not src:
            src = t
    key = (f, ln, msg, tuple(details))
    if key in seen:
        continue
    seen.add(key)
    head = "%s:%s: %s" % (f, ln, msg)
    if details:
        head += "   [" + " | ".join(details) + "]"
    out.append(head)
    if src:
        out.append("      src: " + src[:170])

print("unique errors: %d" % (len([o for o in out if not o.startswith("      src: ")])))
for s in out[:300]:
    print(s)

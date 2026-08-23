#!/usr/bin/env python3
"""Ground truth probe.

Reads the actual Minecraft jar on the compile classpath and reports which
imports resolve, where a simple class name actually lives now, what nested
types a class owns, and the real member signatures of everything this port
touches. Also replays every "Cannot remap" warning Mixin produced, because
those are stale injection targets that compile fine but break at runtime, and
reports which Lombok release the build resolved, since Lombok has to keep up
with the JDK.
"""
import os
import re
import subprocess
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "java")
SEARCH_ROOTS = [
    os.path.join(ROOT, ".gradle", "loom-cache"),
    os.path.expanduser("~/.gradle/caches/fabric-loom"),
]
LOMBOK_CACHE = os.path.expanduser(
    "~/.gradle/caches/modules-2/files-2.1/org.projectlombok/lombok"
)
LOMBOK_METADATA = "https://repo1.maven.org/maven2/org/projectlombok/lombok/maven-metadata.xml"
VALIDATED_PREFIXES = (
    "net.minecraft.",
    "com.mojang.blaze3d.",
    "com.mojang.math.",
)
WIDTH = 300
GRADLE_LOG = os.path.join(ROOT, "ci", "gradle.log")

GREP_TOKENS = []

# Simple names the port lost track of: report every place they live now.
SIMPLE_NAMES = [
    "DepthTestFunction",
    "DestFactor",
    "SourceFactor",
    "MultiBufferSource",
    "LightTexture",
    "CameraRenderState",
    "SubmitNodeCollector",
    "RenderSetup",
    "RenderPipeline",
    "OverlayTexture",
]

FQN_CHECKS = []

NESTED_DUMPS = []

JAVAP = [
    ("net.minecraft.client.renderer.entity.EntityRenderer", ["submit", "render"], 12),
]


def find_jar():
    best, best_count = None, -1
    for base in SEARCH_ROOTS:
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            for fn in filenames:
                if not fn.endswith(".jar"):
                    continue
                if "sources" in fn or "javadoc" in fn:
                    continue
                if not fn.startswith("minecraft"):
                    continue
                path = os.path.join(dirpath, fn)
                try:
                    with zipfile.ZipFile(path) as zf:
                        count = sum(1 for nm in zf.namelist() if nm.endswith(".class"))
                except Exception:
                    continue
                if count > best_count:
                    best, best_count = path, count
    return best, best_count


def lombok_report():
    print("== LOMBOK ==")
    cached = []
    if os.path.isdir(LOMBOK_CACHE):
        cached = sorted(os.listdir(LOMBOK_CACHE))
    print("resolved in cache: %s" % (", ".join(cached) if cached else "(none)"))
    try:
        import urllib.request

        with urllib.request.urlopen(LOMBOK_METADATA, timeout=20) as resp:
            body = resp.read().decode("utf-8", "replace")
        latest = re.search(r"<latest>([^<]+)</latest>", body)
        versions = re.findall(r"<version>([^<]+)</version>", body)
        print("maven latest:      %s" % (latest.group(1) if latest else "unknown"))
        print("maven recent:      %s" % ", ".join(versions[-6:]))
    except Exception as exc:
        print("maven metadata unavailable: %s" % exc)


def javap(cls, keywords, limit):
    print("-- %s --" % cls)
    try:
        proc = subprocess.run(
            ["javap", "-p", "-cp", jarpath, cls], capture_output=True, text=True, timeout=90
        )
    except Exception as exc:
        print("   javap crashed: %s" % exc)
        return
    if proc.returncode != 0:
        err = (proc.stderr or "").strip().split("\n")
        print("   javap error: %s" % (err[0][:140] if err else "unknown"))
        return
    shown = 0
    for line in proc.stdout.split("\n"):
        text = line.strip()
        if not text:
            continue
        if keywords and not any(k in text for k in keywords):
            continue
        print("   %s" % text[:WIDTH])
        shown += 1
        if shown >= limit:
            print("   ... (truncated)")
            break
    if shown == 0:
        print("   (no member matched: %s)" % ", ".join(keywords))


lombok_report()
print("")

jarpath, jarcount = find_jar()
print("== JAR ==")
if not jarpath:
    print("no minecraft jar found, nothing to probe")
    raise SystemExit(0)
print("%d classes  %s" % (jarcount, os.path.basename(jarpath)))

entries = []
with zipfile.ZipFile(jarpath) as zf:
    for nm in zf.namelist():
        if nm.endswith(".class"):
            entries.append(nm[:-6])

classes = set()
for entry in entries:
    dotted = entry.replace("/", ".")
    classes.add(dotted)
    if "$" in dotted:
        classes.add(dotted.replace("$", "."))
packages = set(e.rsplit("/", 1)[0] for e in entries if "/" in e)

java_files = []
for dirpath, dirnames, filenames in os.walk(SRC):
    for fn in filenames:
        if fn.endswith(".java"):
            java_files.append(os.path.join(dirpath, fn))
java_files.sort()

repo_files = []
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if not d.startswith(".") and d != "run"]
    if os.sep + "build" + os.sep in dirpath and "generated" not in dirpath:
        continue
    for fn in filenames:
        if fn.endswith(".java") or fn.endswith(".json"):
            repo_files.append(os.path.join(dirpath, fn))
repo_files.sort()

import_re = re.compile(r"^\s*import\s+(static\s+)?([^;]+);")
bad = []
for path in java_files:
    rel = os.path.relpath(path, SRC)
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            m = import_re.match(line)
            if not m:
                continue
            spec = m.group(2).strip()
            wildcard = spec.endswith(".*")
            if wildcard:
                spec = spec[:-2]
            if not spec.startswith(VALIDATED_PREFIXES):
                continue
            if wildcard and not m.group(1):
                if spec.replace(".", "/") not in packages:
                    bad.append((spec + ".*", "NO PACKAGE", rel))
                continue
            if spec in classes:
                continue
            parent = spec.rsplit(".", 1)[0]
            if not wildcard and parent in classes:
                continue
            simple = spec.rsplit(".", 1)[1]
            hits = sorted(c for c in classes if c.rsplit(".", 1)[-1] == simple)
            hint = hits[0] if hits else "NO CLASS NAMED " + simple
            if len(hits) > 1:
                hint = " | ".join(hits[:3])
            bad.append((spec, hint, rel))

print("")
print("== BAD IMPORTS (%d) ==" % len(bad))
for spec, hint, rel in bad:
    print(spec)
    print("    -> %s" % hint)
    print("    in %s" % rel)

print("")
print("== WHERE DID IT GO ==")
for simple in SIMPLE_NAMES:
    hits = sorted(c for c in classes if c.rsplit(".", 1)[-1] == simple and "$" not in c)
    nested = sorted(c for c in classes if c.endswith("." + simple) and "$" in c)
    found = hits + [n for n in nested if n not in hits]
    print("%s -> %s" % (simple.ljust(22), ", ".join(found[:6]) if found else "GONE"))

print("")
print("== STALE MIXIN TARGETS ==")
if os.path.isfile(GRADLE_LOG):
    seen = []
    with open(GRADLE_LOG, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            text = line.strip()
            if "Cannot remap" in text or "Cannot find" in text:
                if text not in seen:
                    seen.append(text)
    if seen:
        for text in seen:
            print("  " + text[:WIDTH])
    else:
        print("  none")
else:
    print("  (gradle log not written yet)")

if GREP_TOKENS:
    print("")
    print("== SOURCE GREP ==")
    for token in GREP_TOKENS:
        hits = []
        for path in repo_files:
            rel = os.path.relpath(path, ROOT)
            try:
                with open(path, "r", encoding="utf-8", errors="replace") as fh:
                    for no, line in enumerate(fh, 1):
                        if token in line:
                            hits.append("%s:%d  %s" % (rel, no, line.strip()[:200]))
            except Exception:
                continue
        print("-- %s (%d) --" % (token, len(hits)))
        for hit in hits[:10]:
            print("   " + hit)

if FQN_CHECKS:
    print("")
    print("== FQN CHECK ==")
    width = max(len(f) for f in FQN_CHECKS) + 4
    for fqn in FQN_CHECKS:
        print("%s%s" % (fqn.ljust(width), "OK" if fqn in classes else "MISSING"))

if NESTED_DUMPS:
    print("")
    print("== NESTED TYPES ==")
    for outer in NESTED_DUMPS:
        prefix = outer.replace(".", "/") + "$"
        nested = sorted(
            e[len(prefix):] for e in entries if e.startswith(prefix) and "$" not in e[len(prefix):]
        )
        print("-- %s (%d) --" % (outer, len(nested)))
        print("  " + (" ".join(nested[:40]) if nested else "(none)"))

print("")
print("== REAL SIGNATURES ==")
for cls, keywords, limit in JAVAP:
    javap(cls, keywords, limit)

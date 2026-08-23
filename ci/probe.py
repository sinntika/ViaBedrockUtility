#!/usr/bin/env python3
"""Ground truth probe.

Reads the actual Minecraft jar on the compile classpath and reports which
imports resolve, which fully qualified names exist, what nested types a class
owns, whether an interface is sealed, and the real member signatures of
everything this port touches. 1.21.11 uses deobfuscated official names that do
not match the old Mojang mapping names, so nothing here is allowed to be a
guess.
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
VALIDATED_PREFIXES = (
    "net.minecraft.",
    "com.mojang.blaze3d.",
    "com.mojang.math.",
)

FQN_CHECKS = [
    "net.minecraft.client.renderer.OrderedSubmitNodeCollector",
    "net.minecraft.client.renderer.rendertype.RenderSetup",
    "net.minecraft.client.resources.model.AtlasManager",
]

NESTED_DUMPS = [
    "net.minecraft.client.renderer.rendertype.RenderSetup",
    "net.minecraft.client.renderer.OrderedSubmitNodeCollector",
]

SEALED_CHECKS = [
    "net.minecraft.core.ClientAsset",
]

JAVAP = [
    ("net.minecraft.client.renderer.OrderedSubmitNodeCollector", ["submit"], 30),
    ("com.mojang.blaze3d.vertex.VertexFormat$Mode", ["public static final"], 16),
    ("net.minecraft.client.renderer.rendertype.RenderSetup", ["public"], 26),
    ("net.minecraft.core.ClientAsset$Texture", ["public"], 10),
    ("net.minecraft.core.ClientAsset$ResourceTexture", ["public"], 10),
    ("net.minecraft.core.ClientAsset$DownloadedTexture", ["public"], 10),
    ("net.minecraft.client.renderer.rendertype.RenderTypes", ["create", "builder"], 12),
]


def find_jar():
    best, best_count = None, -1
    for base in SEARCH_ROOTS:
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            for fn in filenames:
                if not fn.endswith(".jar") or "sources" in fn:
                    continue
                if not fn.startswith("minecraft-merged"):
                    continue
                path = os.path.join(dirpath, fn)
                if "loom.mappings" not in path:
                    continue
                try:
                    with zipfile.ZipFile(path) as zf:
                        count = sum(1 for nm in zf.namelist() if nm.endswith(".class"))
                except Exception:
                    continue
                if count > best_count:
                    best, best_count = path, count
    return best, best_count


def run_javap(args, timeout=90):
    try:
        return subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    except Exception as exc:
        print("   javap crashed: %s" % exc)
        return None


def javap(cls, keywords, limit):
    print("-- %s --" % cls)
    proc = run_javap(["javap", "-cp", jarpath, cls])
    if proc is None:
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
        print("   %s" % text[:170])
        shown += 1
        if shown >= limit:
            print("   ... (truncated)")
            break
    if shown == 0:
        print("   (no member matched: %s)" % ", ".join(keywords))


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
print("== FQN CHECK ==")
width = max(len(f) for f in FQN_CHECKS) + 4
for fqn in FQN_CHECKS:
    print("%s%s" % (fqn.ljust(width), "OK" if fqn in classes else "MISSING"))

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
print("== SEALED CHECK ==")
for cls in SEALED_CHECKS:
    proc = run_javap(["javap", "-v", "-cp", jarpath, cls])
    print("-- %s --" % cls)
    if proc is None or proc.returncode != 0:
        print("   javap -v failed")
        continue
    hit = False
    for line in proc.stdout.split("\n"):
        text = line.strip()
        if "PermittedSubclasses" in text or text.startswith("flags:"):
            print("   %s" % text[:170])
            hit = True
    if not hit:
        print("   not sealed (no PermittedSubclasses attribute)")

print("")
print("== REAL SIGNATURES ==")
for cls, keywords, limit in JAVAP:
    javap(cls, keywords, limit)

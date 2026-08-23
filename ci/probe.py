#!/usr/bin/env python3
"""Ground truth probe.

Reads the actual Minecraft jar on the compile classpath and reports which
imports resolve, what a package contains, and the real member signatures of
everything this port touches. It can also reach outside the Minecraft jar and
probe any other dependency on the classpath, which is how Fabric API changes
get caught. Finally it replays every "Cannot remap" warning Mixin produced,
because those are stale injection targets that compile fine but break at
runtime, and reports which Lombok release the build resolved.
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
DEPENDENCY_ROOTS = SEARCH_ROOTS + [
    os.path.expanduser("~/.gradle/caches/modules-2/files-2.1"),
]
LOMBOK_CACHE = os.path.expanduser(
    "~/.gradle/caches/modules-2/files-2.1/org.projectlombok/lombok"
)
VALIDATED_PREFIXES = (
    "net.minecraft.",
    "com.mojang.blaze3d.",
    "com.mojang.math.",
)
WIDTH = 600
GRADLE_LOG = os.path.join(ROOT, "ci", "gradle.log")

SIMPLE_NAMES = [
    "BlockRenderDispatcher",
    "BlockModelResolver",
]

PACKAGE_LISTINGS = [
    "net.minecraft.client.renderer.block",
]

JAVAP = [
    ("net.minecraft.client.renderer.block.BlockModelResolver", [], 25),
    ("net.minecraft.client.Minecraft", ["Resolver", "Model"], 40),
    ("net.minecraft.client.renderer.entity.EntityRendererProvider$Context", ["Context("], 6),
    ("com.mojang.blaze3d.GpuFormat", [], 45),
]

# Classes outside the Minecraft jar, looked up across every cached dependency.
EXTRA_JAVAP = []
EXTRA_PACKAGES = []


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


def dependency_jars():
    out = []
    for base in DEPENDENCY_ROOTS:
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            for fn in filenames:
                if not fn.endswith(".jar"):
                    continue
                if "sources" in fn or "javadoc" in fn:
                    continue
                out.append(os.path.join(dirpath, fn))
    return out


def javap(cls, keywords, limit, classpath=None):
    print("-- %s --" % cls)
    cp = classpath or jarpath
    if not cp:
        print("   (not found on the classpath)")
        return
    try:
        proc = subprocess.run(
            ["javap", "-p", "-cp", cp, cls], capture_output=True, text=True, timeout=90
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


print("== LOMBOK ==")
print(
    "resolved in cache: %s"
    % (", ".join(sorted(os.listdir(LOMBOK_CACHE))) if os.path.isdir(LOMBOK_CACHE) else "(none)")
)
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

if SIMPLE_NAMES:
    print("")
    print("== WHERE DID IT GO ==")
    for simple in SIMPLE_NAMES:
        hits = sorted(c for c in classes if c.rsplit(".", 1)[-1] == simple and "$" not in c)
        nested = sorted(c for c in classes if c.endswith("." + simple) and "$" in c)
        found = hits + [n for n in nested if n not in hits]
        print("%s -> %s" % (simple.ljust(22), ", ".join(found[:6]) if found else "GONE"))

print("")
print("== PACKAGE LISTING ==")
for pkg in PACKAGE_LISTINGS:
    prefix = pkg.replace(".", "/") + "/"
    names = sorted(
        set(
            e[len(prefix):]
            for e in entries
            if e.startswith(prefix)
            and "/" not in e[len(prefix):]
            and "$" not in e[len(prefix):]
        )
    )
    print("-- %s (%d) --" % (pkg, len(names)))
    line = "  "
    for name in names:
        if len(line) + len(name) > WIDTH:
            print(line)
            line = "  "
        line += name + " "
    if line.strip():
        print(line)

# Look up classes and packages that live in other dependencies, e.g. Fabric API.
located = {}
extra_pkg_hits = dict((pkg, set()) for pkg in EXTRA_PACKAGES)
extra_pkg_jars = dict((pkg, None) for pkg in EXTRA_PACKAGES)
if EXTRA_JAVAP or EXTRA_PACKAGES:
    wanted = dict(
        (cls.replace(".", "/") + ".class", cls) for cls, _, _ in EXTRA_JAVAP
    )
    for jar in dependency_jars():
        try:
            with zipfile.ZipFile(jar) as zf:
                names = zf.namelist()
        except Exception:
            continue
        for entry, cls in wanted.items():
            if cls not in located and entry in names:
                located[cls] = jar
        for pkg in EXTRA_PACKAGES:
            prefix = pkg.replace(".", "/") + "/"
            for nm in names:
                if not nm.startswith(prefix) or not nm.endswith(".class"):
                    continue
                tail = nm[len(prefix):-6]
                if "/" in tail:
                    continue
                extra_pkg_hits[pkg].add(tail)
                extra_pkg_jars[pkg] = jar

    print("")
    print("== EXTRA PACKAGES ==")
    for pkg in EXTRA_PACKAGES:
        names = sorted(extra_pkg_hits[pkg])
        jar = extra_pkg_jars[pkg]
        print(
            "-- %s (%d) in %s --"
            % (pkg, len(names), os.path.basename(jar) if jar else "nothing")
        )
        line = "  "
        for name in names:
            if len(line) + len(name) > WIDTH:
                print(line)
                line = "  "
            line += name + " "
        if line.strip():
            print(line)

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

print("")
print("== REAL SIGNATURES ==")
for cls, keywords, limit in JAVAP:
    javap(cls, keywords, limit)
for cls, keywords, limit in EXTRA_JAVAP:
    jar = located.get(cls)
    javap(cls, keywords, limit, classpath=jar)

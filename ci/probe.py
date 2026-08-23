#!/usr/bin/env python3
"""Ground truth probe.

Reads the actual Minecraft jar on the compile classpath and reports which
imports resolve, what a package contains, and the real member signatures of
everything this port touches. It can also reach outside the Minecraft jar and
probe any other dependency on the classpath, which is how Fabric API and
CubeConverter changes get caught. It replays every "Cannot remap" warning Mixin
produced, because those are stale injection targets that compile fine but break
at runtime, reports which Lombok release the build resolved, and diffs the
branch against master so nothing that got dropped during the port stays
unnoticed.
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
# Third party libraries that are also provided by other mods at runtime, so the
# build must compile against exactly the same classes.
LIB_PREFIXES = (
    "org.cube.converter.",
    "team.unnamed.mocha.",
)
WIDTH = 600
GRADLE_LOG = os.path.join(ROOT, "ci", "gradle.log")

SIMPLE_NAMES = []

PACKAGE_LISTINGS = []

JAVAP = []

# Classes from the libraries above, probed out of the resolved dependency jars.
LIB_JAVAP = [
    ("org.cube.converter.util.GsonUtil", [], 30),
]

# Classes outside the Minecraft jar, looked up across every cached dependency.
EXTRA_JAVAP = []
EXTRA_PACKAGES = []

jarpath = None


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


def git(*args):
    try:
        proc = subprocess.run(
            ["git"] + list(args), cwd=ROOT, capture_output=True, text=True, timeout=90
        )
    except Exception as exc:
        return None, "git crashed: %s" % exc
    if proc.returncode != 0:
        err = (proc.stderr or "").strip().split("\n")
        return None, "git error: %s" % (err[0][:200] if err else "unknown")
    return proc.stdout.strip(), None


import_re = re.compile(r"^\s*import\s+(static\s+)?([^;]+);")

java_files = []
for dirpath, dirnames, filenames in os.walk(SRC):
    for fn in filenames:
        if fn.endswith(".java"):
            java_files.append(os.path.join(dirpath, fn))
java_files.sort()

lib_imports = {}
for path in java_files:
    rel = os.path.relpath(path, SRC)
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            m = import_re.match(line)
            if not m:
                continue
            spec = m.group(2).strip()
            if spec.startswith(LIB_PREFIXES):
                lib_imports.setdefault(spec, rel)

print("== LOMBOK ==")
print(
    "resolved in cache: %s"
    % (", ".join(sorted(os.listdir(LOMBOK_CACHE))) if os.path.isdir(LOMBOK_CACHE) else "(none)")
)

print("")
print("== DIFF VS MASTER ==")
base = None
for ref in ("origin/master", "master"):
    out, err = git("rev-parse", "--verify", ref)
    if out:
        base = ref
        break
if not base:
    print("  (master is not available in this checkout)")
else:
    head, _ = git("rev-parse", "--short", "HEAD")
    tip, _ = git("rev-parse", "--short", base)
    print("  %s (%s) ... HEAD (%s)" % (base, tip or "?", head or "?"))
    shortstat, err = git("diff", "--shortstat", "%s...HEAD" % base)
    print("  %s" % (shortstat or err or "(no changes)"))
    names, err = git("diff", "--name-status", "%s...HEAD" % base)
    rows = [r for r in (names or "").split("\n") if r.strip()]
    gone = [r for r in rows if r[:1] in ("D", "R")]
    print("  -- deleted or renamed (%d) --" % len(gone))
    for row in gone[:80]:
        print("     %s" % row[:WIDTH])
    shipped = [
        r
        for r in rows
        if "src/main/java" in r or "src/main/resources" in r or "buildSrc" in r
    ]
    print("  -- shipped sources touched (%d) --" % len(shipped))
    for row in shipped[:140]:
        print("     %s" % row[:WIDTH])

# Index the shared libraries so drift shows up before the game launches.
lib_index = {}
lib_jar_counts = {}
if lib_imports or LIB_JAVAP:
    for jar in dependency_jars():
        try:
            with zipfile.ZipFile(jar) as zf:
                names = zf.namelist()
        except Exception:
            continue
        for nm in names:
            if not nm.endswith(".class"):
                continue
            dotted = nm[:-6].replace("/", ".")
            if not dotted.startswith(LIB_PREFIXES):
                continue
            lib_index.setdefault(dotted, jar)
            key = os.path.basename(jar)
            lib_jar_counts[key] = lib_jar_counts.get(key, 0) + 1

print("")
print("== LIB JARS ==")
if lib_jar_counts:
    for name in sorted(lib_jar_counts):
        print("  %-56s %d classes" % (name[:56], lib_jar_counts[name]))
else:
    print("  (none resolved)")

print("")
print("== LIB IMPORTS (%d) ==" % len(lib_imports))
for spec in sorted(lib_imports):
    rel = lib_imports[spec]
    if spec.endswith(".*"):
        pkg = spec[:-2] + "."
        if any(c.startswith(pkg) for c in lib_index):
            continue
        print("  MISSING PACKAGE %s" % spec)
        print("      in %s" % rel)
        continue
    if spec in lib_index:
        continue
    parent = spec.rsplit(".", 1)[0]
    if parent in lib_index:
        continue
    simple = spec.rsplit(".", 1)[-1]
    hits = sorted(c for c in lib_index if c.rsplit(".", 1)[-1] == simple)
    print("  MISSING %s" % spec)
    print("      -> %s" % (" | ".join(hits[:3]) if hits else "no class named " + simple))
    print("      in %s" % rel)

if LIB_JAVAP:
    print("")
    print("== LIB SIGNATURES ==")
    for cls, keywords, limit in LIB_JAVAP:
        javap(cls, keywords, limit, classpath=lib_index.get(cls))

jarpath, jarcount = find_jar()
print("")
print("== JAR ==")
if not jarpath:
    print("no minecraft jar found, nothing left to probe")
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

if PACKAGE_LISTINGS:
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

if JAVAP or EXTRA_JAVAP:
    print("")
    print("== REAL SIGNATURES ==")
    for cls, keywords, limit in JAVAP:
        javap(cls, keywords, limit)
    for cls, keywords, limit in EXTRA_JAVAP:
        javap(cls, keywords, limit, classpath=located.get(cls))

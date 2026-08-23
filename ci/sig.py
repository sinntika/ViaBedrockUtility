#!/usr/bin/env python3
"""Dump the real member signatures and bytecode of the classes this port injects
into, straight out of the Minecraft jar that Loom put on the compile classpath.

The mixin failures that survive a green build are always descriptor drift, so the
only useful evidence is what the jar actually contains.
"""
import os
import subprocess
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOTS = [
    os.path.join(ROOT, ".gradle", "loom-cache"),
    os.path.expanduser("~/.gradle/caches/fabric-loom"),
]

TARGETS = [
    ("net.minecraft.client.renderer.entity.EntityRenderDispatcher", ["getRenderer"], 40),
    ("net.minecraft.client.renderer.entity.state.EntityRenderState", [], 80),
    ("net.minecraft.client.renderer.entity.state.PlayerRenderState", [], 80),
    ("net.minecraft.client.renderer.entity.state.LivingEntityRenderState", [], 80),
    ("net.minecraft.world.entity.player.PlayerSkin", [], 40),
    ("net.minecraft.client.renderer.entity.EntityRenderer", ["RenderState"], 40),
]

BYTECODE = ["net.minecraft.client.renderer.entity.EntityRenderDispatcher"]
BYTECODE_LIMIT = 900


def find_jar():
    best, count = None, -1
    for base in ROOTS:
        if not os.path.isdir(base):
            continue
        for dirpath, _dirnames, filenames in os.walk(base):
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
                        found = sum(1 for nm in zf.namelist() if nm.endswith(".class"))
                except Exception:
                    continue
                if found > count:
                    best, count = path, found
    return best, count


jar, jarcount = find_jar()
print("jar: %s (%d classes)" % (os.path.basename(jar) if jar else "NOT FOUND", jarcount))
if not jar:
    raise SystemExit(0)

print("")
print("== SIGNATURES ==")
for cls, keywords, limit in TARGETS:
    print("-- %s --" % cls)
    try:
        proc = subprocess.run(
            ["javap", "-p", "-cp", jar, cls], capture_output=True, text=True, timeout=120
        )
    except Exception as exc:
        print("   javap crashed: %s" % exc)
        continue
    if proc.returncode != 0:
        err = (proc.stderr or "").strip().split("\n")
        print("   javap error: %s" % (err[0][:200] if err else "unknown"))
        continue
    shown = 0
    for line in proc.stdout.split("\n"):
        text = line.strip()
        if not text:
            continue
        if keywords and not any(k in text for k in keywords):
            continue
        print("   %s" % text[:400])
        shown += 1
        if shown >= limit:
            print("   ... truncated")
            break
    if shown == 0:
        print("   (no member matched: %s)" % ", ".join(keywords))

for cls in BYTECODE:
    print("")
    print("== BYTECODE %s ==" % cls)
    try:
        proc = subprocess.run(
            ["javap", "-p", "-c", "-cp", jar, cls],
            capture_output=True,
            text=True,
            timeout=180,
        )
    except Exception as exc:
        print("   javap crashed: %s" % exc)
        continue
    if proc.returncode != 0:
        err = (proc.stderr or "").strip().split("\n")
        print("   javap error: %s" % (err[0][:200] if err else "unknown"))
        continue
    keep = False
    shown = 0
    for line in proc.stdout.split("\n"):
        text = line.rstrip()
        stripped = text.strip()
        if not stripped:
            continue
        # Method headers end with ); and are not bytecode instructions.
        is_header = stripped.endswith(");") and not stripped[0].isdigit()
        if is_header:
            keep = "getRenderer" in stripped
            if keep:
                print("   %s" % stripped[:400])
                shown += 1
            continue
        if keep:
            print("   %s" % stripped[:400])
            shown += 1
        if shown >= BYTECODE_LIMIT:
            print("   ... truncated")
            break
    if shown == 0:
        print("   (no getRenderer bytecode found)")

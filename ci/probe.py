#!/usr/bin/env python3
"""Ground truth probe.

Reads the actual Minecraft jar that sits on the compile classpath and reports
which imports resolve, which fully qualified names exist, and the real member
signatures of the classes this port depends on. Guessing mapping names from
memory does not work for 1.21.11, so every rename gets checked against the jar.
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
    "net.minecraft.client.model.Model",
    "net.minecraft.client.model.geom.ModelPart.Cube",
    "net.minecraft.client.model.geom.ModelPart.Polygon",
    "net.minecraft.world.entity.player.PlayerModelType",
    "net.minecraft.core.ClientAsset.Texture",
    "net.minecraft.client.renderer.rendertype.RenderTypes",
    "net.minecraft.client.renderer.culling.Frustum",
    "com.mojang.blaze3d.vertex.DefaultVertexFormat",
    "com.mojang.blaze3d.vertex.VertexFormat.Mode",
    "net.minecraft.client.renderer.entity.EntityRendererProvider.Context",
    "net.minecraft.client.renderer.entity.state.AvatarRenderState",
    "net.minecraft.client.renderer.SubmitNodeCollector",
]

PACKAGE_DUMPS = [
    "net/minecraft/client/renderer/rendertype",
    "net/minecraft/core",
]

JAVAP = [
    ("net.minecraft.client.multiplayer.PlayerInfo", ["Skin", "skin", "Profile"]),
    ("net.minecraft.client.renderer.texture.TextureManager", ["register", "release"]),
    (
        "net.minecraft.client.Minecraft",
        [
            "EntityModels",
            "ItemModel",
            "MapRenderer",
            "BlockRenderer",
            "font",
            "getConnection",
            "hitResult",
            "EquipmentAsset",
        ],
    ),
    ("net.minecraft.client.model.Model", ["public", "root", "allParts"]),
    (
        "net.minecraft.client.model.geom.ModelPart",
        ["setPos", "Pose", "public net", "public void", "public final"],
    ),
    ("net.minecraft.client.model.geom.ModelPart$Cube", ["public", "polygons", "Polygon"]),
    ("net.minecraft.client.model.player.PlayerModel", ["public", "static"]),
    ("net.minecraft.client.renderer.rendertype.RenderTypes", ["entity"]),
    ("net.minecraft.core.ClientAsset", ["public", "Texture"]),
    ("net.minecraft.core.ClientAsset$Texture", ["public"]),
    (
        "net.minecraft.client.renderer.entity.EntityRenderer",
        ["extractRenderState", "shouldRender", "submit", "getTextureLocation", "public"],
    ),
    ("net.minecraft.client.renderer.entity.EntityRendererProvider$Context", ["public"]),
    ("net.minecraft.client.resources.DefaultPlayerSkin", ["public", "static"]),
    (
        "net.minecraft.resources.Identifier",
        ["parse", "withDefaultNamespace", "fromNamespaceAndPath"],
    ),
    ("com.mojang.blaze3d.vertex.VertexFormat", ["Mode", "public"]),
    ("net.minecraft.client.renderer.entity.state.AvatarRenderState", ["public"]),
    (
        "net.minecraft.world.entity.Entity",
        [
            "getViewYRot",
            "getVisualRotationYInDegrees",
            "getXRot",
            "getYRot",
            "getViewScale",
            "distanceToSqr",
        ],
    ),
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
print("== PACKAGE DUMPS ==")
for pkg in PACKAGE_DUMPS:
    members = sorted(
        e.rsplit("/", 1)[1]
        for e in entries
        if e.rsplit("/", 1)[0] == pkg and "$" not in e
    )
    print("-- %s (%d) --" % (pkg, len(members)))
    print("  " + " ".join(members[:60]))

print("")
print("== REAL SIGNATURES ==")
for cls, keywords in JAVAP:
    print("-- %s --" % cls)
    try:
        proc = subprocess.run(
            ["javap", "-cp", jarpath, cls],
            capture_output=True,
            text=True,
            timeout=90,
        )
    except Exception as exc:
        print("   javap crashed: %s" % exc)
        continue
    if proc.returncode != 0:
        err = (proc.stderr or "").strip().split("\n")
        print("   javap error: %s" % (err[0][:140] if err else "unknown"))
        continue
    shown = 0
    for line in proc.stdout.split("\n"):
        text = line.strip()
        if not text:
            continue
        if keywords and not any(k in text for k in keywords):
            continue
        print("   %s" % text[:150])
        shown += 1
        if shown >= 16:
            print("   ... (truncated)")
            break
    if shown == 0:
        print("   (no member matched: %s)" % ", ".join(keywords))

#!/usr/bin/env python3
"""Dump the real member signatures of every class the custom-block port touches,
straight out of the jars Loom put on the compile classpath.

The sandbox this is driven from has no Minecraft jar and no network, so any
signature written from memory is a guess. This turns the CI runner into the
source of truth instead: it prints what the jars actually contain, and the next
commit is written against that output.
"""
import os
import subprocess
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR_ROOTS = [
    os.path.join(ROOT, ".gradle", "loom-cache"),
    os.path.expanduser("~/.gradle/caches/fabric-loom"),
    os.path.expanduser("~/.gradle/caches/modules-2"),
]

# Libraries that have to sit on javap's classpath for the targets below to
# resolve. Matched against the lowercased jar file name.
LIB_KEYWORDS = (
    "viabedrock",
    "viaversion",
    "cubeconverter",
    "mocha",
    "fastutil",
)

# (class, keywords, max lines). An empty keyword list prints every member.
TARGETS = [
    # -- registry mutation, phase 1 --
    ("net.minecraft.core.MappedRegistry", [], 90),
    ("net.minecraft.core.IdMapper", [], 40),
    ("net.minecraft.core.Holder$Reference", [], 40),
    ("net.minecraft.resources.ResourceKey", [], 30),
    # -- block construction, phase 2 --
    ("net.minecraft.world.level.block.state.BlockBehaviour$Properties", [], 90),
    (
        "net.minecraft.world.level.block.Block",
        ["BLOCK_STATE_REGISTRY", "getId", "defaultBlockState", "initCache", "Block("],
        30,
    ),
    ("net.minecraft.world.level.block.state.BlockState", ["initCache"], 10),
    ("net.minecraft.world.phys.shapes.Shapes", [], 60),
    ("com.mojang.math.OctahedralGroup", ["ROT_90", "BLOCK_ROT"], 30),
    # -- model baking, phase 3 --
    ("net.minecraft.client.resources.model.ModelBakery", [], 40),
    ("net.minecraft.client.resources.model.ModelManager", ["Dispatch", "bake"], 30),
    ("net.minecraft.client.resources.model.ModelBaker", [], 30),
    ("net.minecraft.client.resources.model.sprite.MaterialBaker", [], 30),
    ("net.minecraft.client.resources.model.sprite.Material", [], 40),
    ("net.minecraft.client.resources.model.sprite.TextureSlots", [], 40),
    ("net.minecraft.client.resources.model.cuboid.FaceBakery", [], 40),
    ("net.minecraft.client.resources.model.cuboid.CuboidFace", [], 40),
    ("net.minecraft.client.resources.model.geometry.QuadCollection", [], 40),
    ("net.minecraft.client.resources.model.geometry.BakedQuad", [], 40),
    ("net.minecraft.client.resources.model.SimpleModelWrapper", [], 30),
    ("net.minecraft.client.renderer.block.dispatch.BlockStateModel", [], 30),
    ("net.minecraft.client.renderer.block.dispatch.SingleVariant", [], 30),
    # -- ViaBedrock side, phase 4 --
    ("net.raphimc.viabedrock.protocol.storage.ResourcePackStorage", [], 60),
    ("net.raphimc.viabedrock.api.resourcepack.definition.ModelDefinitions", [], 40),
    ("net.raphimc.viabedrock.protocol.rewriter.BlockStateRewriter", [], 60),
    ("net.raphimc.viabedrock.protocol.rewriter.ResourcePackRewriter", [], 30),
    ("net.raphimc.viabedrock.api.model.BedrockBlockState", [], 40),
    ("net.raphimc.viabedrock.api.util.MoLangEngine", [], 30),
    # -- regression guard for the crash that started all this --
    (
        "net.minecraft.client.renderer.entity.EntityRenderDispatcher",
        ["getRenderer"],
        20,
    ),
]


def _walk_jars():
    for base in JAR_ROOTS:
        if not os.path.isdir(base):
            continue
        for dirpath, _dirnames, filenames in os.walk(base):
            for fn in filenames:
                if not fn.endswith(".jar"):
                    continue
                if "sources" in fn or "javadoc" in fn:
                    continue
                yield os.path.join(dirpath, fn)


def _class_count(path):
    try:
        with zipfile.ZipFile(path) as zf:
            return sum(1 for nm in zf.namelist() if nm.endswith(".class"))
    except Exception:
        return -1


def find_minecraft_jar():
    best, count = None, -1
    for path in _walk_jars():
        if not os.path.basename(path).startswith("minecraft"):
            continue
        found = _class_count(path)
        if found > count:
            best, count = path, found
    return best, count


def find_libs():
    # Keep the biggest jar per file name, so a stale partial download loses.
    picked = {}
    for path in _walk_jars():
        name = os.path.basename(path)
        lowered = name.lower()
        if not any(kw in lowered for kw in LIB_KEYWORDS):
            continue
        found = _class_count(path)
        if found <= 0:
            continue
        if name not in picked or found > picked[name][1]:
            picked[name] = (path, found)
    return sorted(picked.items())


jar, jarcount = find_minecraft_jar()
print("jar: %s (%d classes)" % (os.path.basename(jar) if jar else "NOT FOUND", jarcount))
if not jar:
    raise SystemExit(0)

libs = find_libs()
print("")
print("== CLASSPATH LIBS (%d) ==" % len(libs))
for name, (_path, found) in libs:
    print("   %-60s %d classes" % (name, found))

classpath = os.pathsep.join([jar] + [path for _name, (path, _c) in libs])

print("")
print("== SIGNATURES ==")
for cls, keywords, limit in TARGETS:
    print("-- %s --" % cls)
    try:
        proc = subprocess.run(
            ["javap", "-p", "-cp", classpath, cls],
            capture_output=True,
            text=True,
            timeout=120,
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

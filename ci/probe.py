#!/usr/bin/env python3
# Ground truth for porting: indexes the real Minecraft compile jar, then checks
# every net.minecraft / com.mojang import in the mod against it and suggests the
# correct fully qualified name for each import that does not exist.
import os
import re
import zipfile

PREFIXES = ("net/minecraft/", "com/mojang/")

EXTRA = """PlayerRenderer PlayerRenderState AvatarRenderer AvatarRenderState PlayerModel PlayerSkin
PoseStack VertexConsumer NativeImage Axis RenderStateShard RenderPipeline RenderPipelines RenderType
Identifier Connection ServerboundCustomPayloadPacket ClientboundGameProfilePacket PackReloadConfig
Display ItemDisplay SkinManager PlayerSkinRenderCache EquipmentAssetManager DynamicTexture
CustomPacketPayload StreamCodec EntityRenderDispatcher CapeLayer LevelEntityGetter""".split()

PKGS = [
    "net/minecraft/client/renderer/entity",
    "net/minecraft/client/renderer/entity/state",
    "net/minecraft/client/renderer/entity/player",
    "net/minecraft/client/renderer/rendertype",
    "net/minecraft/client/resources/server",
    "com/mojang/blaze3d/vertex",
]

roots = [os.path.join(os.path.expanduser("~"), ".gradle", "caches")]
ws = os.environ.get("GITHUB_WORKSPACE", "")
if ws:
    roots.append(os.path.join(ws, ".gradle"))

jars = []
for root in roots:
    if not os.path.isdir(root):
        continue
    for dirpath, dirnames, filenames in os.walk(root):
        for fn in filenames:
            if not fn.endswith(".jar") or fn.endswith("-sources.jar"):
                continue
            if "intermediary" in fn:
                continue
            p = os.path.join(dirpath, fn)
            try:
                sz = os.path.getsize(p)
            except OSError:
                continue
            if sz > 3 * 1024 * 1024:
                jars.append((sz, p))
jars.sort(reverse=True)

index = {}
fqns = set()
entries = set()
mc_jars = []
for sz, p in jars[:30]:
    try:
        zf = zipfile.ZipFile(p)
        names = zf.namelist()
        zf.close()
    except Exception:
        continue
    ents = [n for n in names if n.startswith(PREFIXES) and n.endswith(".class")]
    if len(ents) < 300:
        continue
    mc_jars.append((p, len(ents)))
    for n in ents:
        entries.add(n)
        dotted = n[:-6].replace("/", ".").replace("$", ".")
        fqns.add(dotted)
        if "$" not in n:
            index.setdefault(n[n.rfind("/") + 1:-6], []).append(n[:-6].replace("/", "."))
    if len(mc_jars) >= 1:
        break

print("== JAR ==")
for p, c in mc_jars:
    print("%d classes  %s" % (c, os.path.basename(p)))
if not mc_jars:
    print("NO MINECRAFT JAR FOUND")

src = os.path.join(ws or ".", "src", "main", "java")
bad = {}
for dirpath, dirnames, filenames in os.walk(src):
    for fn in filenames:
        if not fn.endswith(".java"):
            continue
        path = os.path.join(dirpath, fn)
        rel = path[len(src) + 1:]
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if not line.startswith("import "):
                    continue
                s = line[7:].strip()
                if s.startswith("static "):
                    s = s[7:].strip()
                s = s.rstrip(";").strip()
                if s.endswith(".*"):
                    s = s[:-2]
                if not s.startswith(("net.minecraft.", "com.mojang.")):
                    continue
                if s in fqns:
                    continue
                if "." in s and s.rsplit(".", 1)[0] in fqns:
                    continue
                bad.setdefault(s, set()).add(rel)

print("")
print("== BAD IMPORTS (%d) ==" % len(bad))
for s in sorted(bad):
    simple = s.rsplit(".", 1)[1]
    cands = sorted(set(index.get(simple, [])))
    hint = "  ".join(cands[:3]) if cands else "NO CLASS NAMED %s" % simple
    print("%s" % s)
    print("    -> %s" % hint)
    print("    in %s" % ", ".join(sorted(bad[s])[:3]))

print("")
print("== NAME LOOKUP ==")
for nm in EXTRA:
    hits = sorted(set(index.get(nm, [])))
    print("%-32s %s" % (nm, "  ".join(hits[:3]) if hits else "MISSING"))

print("")
print("== PACKAGE DUMPS ==")
uniq = sorted(entries)
for pkg in PKGS:
    pre = pkg + "/"
    kids = sorted(set(n[len(pre):-6] for n in uniq
                      if n.startswith(pre) and "/" not in n[len(pre):-6] and "$" not in n[len(pre):-6]))
    print("-- %s (%d) --" % (pkg, len(kids)))
    print("  " + " ".join(kids[:90]))
    print("")

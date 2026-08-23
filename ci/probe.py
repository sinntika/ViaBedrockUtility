#!/usr/bin/env python3
# Ground truth for porting. Indexes the real Minecraft compile jar, validates
# every mod import against it, suggests replacements, and dumps the real method
# signatures of the classes this mod hooks into.
import os
import subprocess
import zipfile

PREFIXES = ("net/minecraft/", "com/mojang/")
SKIP_IMPORT = ("com.mojang.brigadier.", "com.mojang.authlib.", "com.mojang.serialization.",
               "com.mojang.datafixers.", "com.mojang.logging.")

FQN_CHECK = [
    "net.minecraft.world.level.Level",
    "net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket",
    "net.minecraft.client.model.geom.ModelPart.Cube",
    "net.minecraft.client.model.geom.ModelPart.Polygon",
    "net.minecraft.client.model.geom.ModelPart.Vertex",
    "net.minecraft.world.entity.Display.ItemDisplay",
    "net.minecraft.client.resources.server.PackReloadConfig.IdAndPath",
    "net.minecraft.client.renderer.rendertype.RenderType.CompositeState",
    "com.mojang.blaze3d.vertex.PoseStack.Pose",
    "net.minecraft.world.entity.player.PlayerSkin.Model",
]

PKGS = ["net/minecraft/network/protocol/login"]

JAVAP = [
    ("net.minecraft.client.renderer.entity.EntityRenderer", ["exture"], 8),
    ("net.minecraft.client.renderer.entity.player.AvatarRenderer", ["AvatarRenderer(", "exture", "odel"], 10),
    ("net.minecraft.client.renderer.entity.EntityRenderDispatcher", ["enderer"], 8),
    ("net.minecraft.client.renderer.rendertype.RenderType", ["entity"], 12),
    ("net.minecraft.world.entity.player.PlayerSkin", [""], 14),
    ("net.minecraft.client.multiplayer.ClientLevel", ["ntit"], 8),
    ("net.minecraft.world.entity.Entity", ["moveDist", "walkDist", "setPos", "position("], 8),
    ("net.minecraft.client.resources.server.DownloadedPackSource", ["List", "ack"], 10),
    ("net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl", ["ogin", "andle"], 8),
    ("net.minecraft.client.renderer.entity.layers.CapeLayer", [""], 8),
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
            if not fn.endswith(".jar") or fn.endswith("-sources.jar") or "intermediary" in fn:
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
jarpath = None
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
    jarpath = p
    for n in ents:
        entries.add(n)
        fqns.add(n[:-6].replace("/", ".").replace("$", "."))
        if "$" not in n:
            index.setdefault(n[n.rfind("/") + 1:-6], []).append(n[:-6].replace("/", "."))
    break

print("== JAR ==")
print("%d classes  %s" % (len(entries), os.path.basename(jarpath) if jarpath else "NONE FOUND"))

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
                if s.startswith(SKIP_IMPORT):
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
    print("%s" % s)
    print("    -> %s" % ("  ".join(cands[:3]) if cands else "NO CLASS NAMED %s" % simple))
    print("    in %s" % ", ".join(sorted(bad[s])[:2]))

print("")
print("== FQN CHECK ==")
for f in FQN_CHECK:
    print("%-70s %s" % (f, "OK" if f in fqns else "MISSING"))

print("")
print("== PACKAGE DUMPS ==")
for pkg in PKGS:
    pre = pkg + "/"
    kids = sorted(set(n[len(pre):-6] for n in sorted(entries)
                      if n.startswith(pre) and "/" not in n[len(pre):-6] and "$" not in n[len(pre):-6]))
    print("-- %s (%d) --" % (pkg, len(kids)))
    print("  " + " ".join(kids[:60]))

print("")
print("== REAL SIGNATURES ==")
for cls, keys, cap in JAVAP:
    print("-- %s --" % cls)
    if not jarpath:
        continue
    try:
        res = subprocess.run(["javap", "-cp", jarpath, cls],
                             capture_output=True, text=True, timeout=90)
        text = res.stdout or res.stderr
    except Exception as exc:
        text = "javap failed: %s" % exc
    picked = [l.strip() for l in text.splitlines() if any(k in l for k in keys)]
    for l in picked[:cap]:
        print("   " + l[:150])

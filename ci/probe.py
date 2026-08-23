#!/usr/bin/env python3
# Dumps the real class names available on the Minecraft compile classpath.
# This is ground truth for building the yarn -> mojang mapping table.
import os
import zipfile

NAMES = """ResourceLocation Minecraft PlayerModel PlayerSkin RenderStateShard RenderType RenderPipelines
PoseStack MultiBufferSource VertexConsumer ModelPart CubeDeformation MeshDefinition CubeListBuilder
PartDefinition PartPose LayerDefinition EntityModel EntityRenderer EntityRendererProvider
EntityRenderDispatcher LivingEntityRenderer PlayerRenderer CapeLayer EntityRenderState
LivingEntityRenderState PlayerRenderState ClientLevel LocalPlayer AbstractClientPlayer
ClientPacketListener ClientHandshakePacketListenerImpl PlayerInfo FriendlyByteBuf StreamCodec
StreamDecoder StreamEncoder CustomPacketPayload Commands Vec3 Mth BlockPos Direction Vec3i
Axis LightTexture DeltaTracker OverlayTexture Frustum NativeImage DynamicTexture TextureManager
AbstractTexture DefaultPlayerSkin LevelEntityGetter Entity EntityType LivingEntity Player Screen
EquipmentAssetManager EquipmentClientInfo EquipmentLayerRenderer KeyframeAnimations AnimationDefinition
Keyframe AnimationChannel DownloadedPackSource ResourceManager PackResources PackType Pack Util
BlockHitResult EntityHitResult Display Identifier MinecraftClient""".split()

PKGS = [
    "net/minecraft/resources",
    "net/minecraft/client",
    "net/minecraft/client/model",
    "net/minecraft/client/renderer",
    "net/minecraft/client/resources",
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
            p = os.path.join(dirpath, fn)
            try:
                sz = os.path.getsize(p)
            except OSError:
                continue
            if sz > 3 * 1024 * 1024:
                jars.append((sz, p))
jars.sort(reverse=True)

index = {}
mc_jars = []
all_entries = []
for sz, p in jars[:30]:
    try:
        zf = zipfile.ZipFile(p)
        names = zf.namelist()
        zf.close()
    except Exception:
        continue
    ents = [n for n in names
            if n.startswith("net/minecraft/") and n.endswith(".class") and "$" not in n]
    if len(ents) < 300:
        continue
    mc_jars.append((p, len(ents), sz))
    for n in ents:
        simple = n[n.rfind("/") + 1:-6]
        index.setdefault(simple, []).append(n[:-6])
    all_entries.extend(ents)

print("== JARS ==")
for p, c, sz in mc_jars:
    print("%7d classes %5dMB %s" % (c, sz // (1024 * 1024), p))
if not mc_jars:
    print("NO MINECRAFT JAR FOUND. largest jars:")
    for sz, p in jars[:20]:
        print("  %5dMB %s" % (sz // (1024 * 1024), p))

print("")
print("== NAME LOOKUP ==")
for nm in NAMES:
    hits = sorted(set(index.get(nm, [])))
    if not hits:
        print("%-34s MISSING" % nm)
    else:
        print("%-34s %s" % (nm, "  ".join(hits[:3])))

print("")
print("== PACKAGE DUMPS ==")
uniq = sorted(set(all_entries))
for pkg in PKGS:
    pre = pkg + "/"
    kids = sorted(set(n[len(pre):-6] for n in uniq
                      if n.startswith(pre) and "/" not in n[len(pre):-6]))
    print("-- %s (%d) --" % (pkg, len(kids)))
    print("  " + " ".join(kids[:80]))
    print("")

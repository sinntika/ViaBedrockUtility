# ViaBedrockUtility 1.0.0

One source tree, built for two Minecraft versions. Pick the jar that matches the
instance you play on and never install both at once.

| jar | Minecraft | built from |
| --- | --- | --- |
| `viabedrockutility-1.0.0+1.21.11.jar` | 1.21.11 | `port/1.21.11` |
| `viabedrockutility-1.0.0+26.2.jar` | 26.2 | `port/26.2` |

Both jars need Fabric API and ViaFabricPlus.

The 26.2 jar compiles against the exact CubeConverter commit that ViaFabricPlus
4.6.2 jar-in-jars, because that copy wins over the shaded one at runtime. When
ViaFabricPlus bumps CubeConverter, this jar has to be rebuilt against the new
commit.

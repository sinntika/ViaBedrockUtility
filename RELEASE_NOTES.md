# ViaBedrockUtility 1.0.0

Builds for Minecraft 26.2 (`port/26.2`) and 1.21.11 (`port/1.21.11`).

## Fixed in the 26.2 build

26.2 split `EntityRenderDispatcher#getRenderer` into an `Entity` overload and an
`EntityRenderState` overload, and moved the player skin model lookup into the
render state overload. The mod's two unqualified `@Inject` targets therefore both
resolved against the render state overload, and Mixin rejected the entity typed
handlers with:

```
Invalid descriptor on ...EntityRenderDispatcherMixin->@Inject::getPlayerRenderer!
Expected (Lnet/minecraft/client/renderer/entity/state/EntityRenderState;...)V
but found (Lnet/minecraft/world/entity/Entity;...)V
```

which crashed the client during `Initializing game`.

The render state carries no entity identity, while every cache in this mod is
keyed by UUID, so:

- the entity UUID is now stamped onto the render state while the renderer
  extracts/creates it,
- both dispatcher injections are pinned to explicit method descriptors, one per
  overload, so an overload change can no longer silently retarget them,
- the injections moved to `HEAD`, so they no longer depend on the internal call
  order (`PlayerSkin.model()`, `Map.get` ordinals) inside the vanilla method.

# ViaBedrockUtility 1.0.0

## 26.2 build

### Fixes

1. **Crash on startup (`Initializing game`)**
   `render.dispatcher.EntityRenderDispatcherMixin` was still injecting into the
   pre-render-state `getRenderer(Entity)` signature, which made Mixin fail with
   `InvalidInjectionException: Invalid descriptor`. The mixin now targets both
   26.2 overloads (`getRenderer(Entity)` and `getRenderer(EntityRenderState)`)
   and the entity UUID is carried over to the render state through
   `EntityRenderStateMixin` / `EntityRendererMixin`.

2. **`Unknown custom packet payload: viabedrockutility:data`**
   The payload type was registered for both the configuration and the play
   phase, but a receiver only existed for the play phase. ViaBedrock already
   sends `CONFIRM` / skin / cape payloads while the client is still in the
   configuration phase, so those packets were dropped. A
   `ClientConfigurationNetworking` receiver was added; both phases share the
   same `PayloadHandler`.

### Notes

- Remove the old `viabedrockutility-1.0.0+26.2.jar` from the mods folder before
  installing this one.
- `indium-1.0.35+mc1.21` is built for 1.21 and should be removed on 26.2.
- `faster-random` does nothing on 26.2 (its mixins target `class_2919` /
  `class_5819`, which no longer exist); it is safe to remove.

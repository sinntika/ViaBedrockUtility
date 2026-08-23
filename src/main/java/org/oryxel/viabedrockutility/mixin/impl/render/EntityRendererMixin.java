package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.oryxel.viabedrockutility.mixin.interfaces.IEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Carries the entity UUID over into the render state so the dispatcher can still
 * find this mod's per entity renderers once vanilla stops handing it an entity.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
    private void viaBedrockUtility$stampUuidOnNewState(final Entity entity, final float partialTick, final CallbackInfoReturnable<EntityRenderState> cir) {
        final EntityRenderState state = cir.getReturnValue();
        if (state != null) {
            ((IEntityRenderState) state).viaBedrockUtility$setEntityUuid(entity.getUUID());
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void viaBedrockUtility$stampUuidOnReusedState(final Entity entity, final EntityRenderState state, final float partialTick, final CallbackInfo ci) {
        ((IEntityRenderState) state).viaBedrockUtility$setEntityUuid(entity.getUUID());
    }
}

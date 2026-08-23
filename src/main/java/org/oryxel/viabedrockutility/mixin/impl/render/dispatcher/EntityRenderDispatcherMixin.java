package org.oryxel.viabedrockutility.mixin.impl.render.dispatcher;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("unchecked")
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "getRenderer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/PlayerSkin;model()Lnet/minecraft/world/entity/player/PlayerSkin$Model;"), cancellable = true)
    public <T extends Entity> void getPlayerRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        final EntityRenderer<?, ?> renderer = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedPlayerRenderers().get(entity.getUUID());
        if (renderer != null) {
            cir.setReturnValue((EntityRenderer<? super T, ?>) renderer);
        }
    }

    @Inject(method = "getRenderer", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 2), cancellable = true)
    public <T extends Entity> void getRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        final CustomEntityTicker data = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedCustomEntities().get(entity.getUUID());
        if (data != null && data.getRenderer() != null) {
            cir.setReturnValue((EntityRenderer<? super T, ?>) data.getRenderer());
        }
    }
}
package org.oryxel.viabedrockutility.mixin.impl.render.dispatcher;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.mixin.interfaces.IEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 26.2 has two getRenderer overloads: the legacy entity one and a render state
 * one that holds the player skin lookup. Both injections are pinned to an
 * explicit descriptor, because an unqualified target matches every overload and
 * mixin then validates the handler against whichever overload happens to contain
 * the injection point.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void viaBedrockUtility$getRendererForEntity(final T entity, final CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        final EntityRenderer<?, ?> renderer = viaBedrockUtility$findRenderer(entity.getUUID());
        if (renderer != null) {
            cir.setReturnValue((EntityRenderer<? super T, ?>) renderer);
        }
    }

    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    private void viaBedrockUtility$getRendererForState(final EntityRenderState state, final CallbackInfoReturnable<EntityRenderer> cir) {
        final EntityRenderer<?, ?> renderer = viaBedrockUtility$findRenderer(((IEntityRenderState) state).viaBedrockUtility$getEntityUuid());
        if (renderer != null) {
            cir.setReturnValue(renderer);
        }
    }

    @Unique
    private static EntityRenderer<?, ?> viaBedrockUtility$findRenderer(final UUID uuid) {
        if (uuid == null || !ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return null;
        }

        final EntityRenderer<?, ?> playerRenderer = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedPlayerRenderers().get(uuid);
        if (playerRenderer != null) {
            return playerRenderer;
        }

        final CustomEntityTicker data = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedCustomEntities().get(uuid);
        if (data != null && data.getRenderer() != null) {
            return data.getRenderer();
        }

        return null;
    }
}

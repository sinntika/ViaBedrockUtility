package org.oryxel.viabedrockutility.mixin.impl.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientWorldMixin {
    @Shadow protected abstract LevelEntityGetter<Entity> getEntities();

    @Inject(method = "removeEntity", at = @At(value = "HEAD"))
    private void injectRemoveEntity(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        final PayloadHandler handler = ViaBedrockUtility.getInstance().getPayloadHandler();

        Entity entity = this.getEntities().get(entityId);
        if (entity != null) {
            handler.getCachedPlayerRenderers().remove(entity.getUUID());
            handler.getCachedCustomEntities().remove(entity.getUUID());
            handler.getCachedPlayerCapes().remove(entity.getUUID());
            handler.getCachedSkinInfo().remove(entity.getUUID());
        }
    }
}

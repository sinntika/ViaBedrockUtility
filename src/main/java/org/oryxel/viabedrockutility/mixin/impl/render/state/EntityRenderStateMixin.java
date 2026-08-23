package org.oryxel.viabedrockutility.mixin.impl.render.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.oryxel.viabedrockutility.mixin.interfaces.IEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements IEntityRenderState {
    @Unique
    private UUID viaBedrockUtility$entityUuid;

    @Override
    public UUID viaBedrockUtility$getEntityUuid() {
        return this.viaBedrockUtility$entityUuid;
    }

    @Override
    public void viaBedrockUtility$setEntityUuid(final UUID uuid) {
        this.viaBedrockUtility$entityUuid = uuid;
    }
}

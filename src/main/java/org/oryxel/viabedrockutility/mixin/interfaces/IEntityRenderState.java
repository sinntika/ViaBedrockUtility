package org.oryxel.viabedrockutility.mixin.interfaces;

import java.util.UUID;

/**
 * 26.2 renders entities from a detached render state, and that state carries no
 * entity identity. Every cache in this mod is keyed by UUID, so the UUID is
 * stapled onto the render state while it is being extracted and read back when
 * the dispatcher picks a renderer.
 */
public interface IEntityRenderState {
    UUID viaBedrockUtility$getEntityUuid();

    void viaBedrockUtility$setEntityUuid(final UUID uuid);
}

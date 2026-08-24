package org.oryxel.viabedrockutility.mixin.interfaces;

import net.minecraft.resources.ResourceKey;

/**
 * Vanilla freezes {@code BuiltInRegistries.BLOCK} during bootstrap, but Bedrock
 * servers only tell us about their custom blocks once we are already in game, so
 * the registry has to be reopened, written to, and closed again.
 *
 * <p>Every unfreeze must be paired with a refreeze, and every block registered
 * while unfrozen must be unregistered again on disconnect, otherwise the next
 * server inherits state that does not belong to it.
 */
public interface IMappedRegistry {

	void viaBedrockUtility$unfreeze();

	void viaBedrockUtility$refreeze();

	void viaBedrockUtility$unregister(final ResourceKey<?> key);
}

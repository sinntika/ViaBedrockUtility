package org.oryxel.viabedrockutility.mixin.blocks;

import net.minecraft.client.Minecraft;
import org.oryxel.viabedrockutility.block.DynamicBlockCache;
import org.oryxel.viabedrockutility.block.model.GeometryRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Releases the custom blocks when the connection ends.
 *
 * <p>The blocks and their geometries belong to one server. Keeping them would
 * leave the next server's ids pointing at the previous server's blocks, and the
 * block registry would grow for as long as the game runs.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftDisconnectMixin {

	@Inject(
			method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
			at = @At("HEAD"),
			require = 0,
			expect = 0)
	private void viaBedrockUtility$releaseCustomBlocks(final CallbackInfo callback) {
		try {
			DynamicBlockCache.clear();
			GeometryRegistry.clear();
		} catch (final Throwable throwable) {
			org.slf4j.LoggerFactory.getLogger("ViaBedrockUtility|Blocks")
					.error("Could not release the custom blocks", throwable);
		}
	}
}

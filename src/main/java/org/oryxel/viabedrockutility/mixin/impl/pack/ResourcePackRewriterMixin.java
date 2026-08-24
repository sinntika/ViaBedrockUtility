package org.oryxel.viabedrockutility.mixin.impl.pack;

import net.raphimc.viabedrock.api.resourcepack.content.Content;
import net.raphimc.viabedrock.protocol.rewriter.ResourcePackRewriter;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.oryxel.viabedrockutility.block.model.PackBlockResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the custom block sprites to the pack ViaBedrock converts.
 *
 * <p>This is the last moment the converted pack can still be written to: after
 * this it is zipped and handed to the client as a server resource pack, so
 * anything added later would never reach the texture atlas.
 */
@Mixin(value = ResourcePackRewriter.class, remap = false)
public abstract class ResourcePackRewriterMixin {

	@Inject(method = "bedrockToJava", at = @At("TAIL"))
	private static void viaBedrockUtility$collectBlockResources(
			final ResourcePackStorage storage, final CallbackInfoReturnable<Content> callback) {
		PackBlockResources.collect(storage, callback.getReturnValue());
	}
}

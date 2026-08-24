package org.oryxel.viabedrockutility.mixin.blocks;

import com.viaversion.viaversion.libs.fastutil.ints.Int2IntMap;
import net.raphimc.viabedrock.protocol.model.BlockProperties;
import net.raphimc.viabedrock.protocol.rewriter.BlockStateRewriter;
import org.oryxel.viabedrockutility.block.CustomBlockRegistrar;
import org.oryxel.viabedrockutility.mixin.interfaces.IBlockStateRewriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads the custom block palette out of ViaBedrock's block state rewriter.
 *
 * <p>The palette is the constructor argument, so the whole feature needs one
 * injection at the end of the constructor and one field accessor. This is the
 * riskiest injection in the mod, which is why it lives in a config that is not
 * required: if it ever stops matching, the blocks turn themselves off and the
 * rest of the mod is unaffected.
 */
@Mixin(value = BlockStateRewriter.class, remap = false)
public abstract class BlockStateRewriterMixin implements IBlockStateRewriter {

	@Override
	@Accessor("blockStateIdMappings")
	public abstract Int2IntMap viaBedrockUtility$blockStateIdMappings();

	@Inject(method = "<init>", at = @At("RETURN"), require = 0, expect = 0)
	private void viaBedrockUtility$registerCustomBlocks(
			final BlockProperties[] palette,
			final boolean unused,
			final CallbackInfo callback) {
		try {
			CustomBlockRegistrar.register((BlockStateRewriter) (Object) this, palette);
		} catch (final Throwable throwable) {
			// Never let this break the login sequence.
			throw0(throwable);
		}
	}

	private static void throw0(final Throwable throwable) {
		org.slf4j.LoggerFactory.getLogger("ViaBedrockUtility|Blocks")
				.error("Could not read the custom block palette", throwable);
	}
}

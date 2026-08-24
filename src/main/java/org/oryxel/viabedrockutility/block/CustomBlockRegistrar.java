package org.oryxel.viabedrockutility.block;

import com.viaversion.viaversion.libs.fastutil.ints.Int2IntMap;
import com.viaversion.viaversion.libs.fastutil.ints.IntSortedSet;
import net.minecraft.world.level.block.state.BlockState;
import net.raphimc.viabedrock.protocol.model.BlockProperties;
import net.raphimc.viabedrock.protocol.rewriter.BlockStateRewriter;
import org.oryxel.viabedrockutility.mixin.interfaces.IBlockStateRewriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the custom block palette a Bedrock server sends into real client blocks.
 *
 * <p>The palette arrives as the argument of ViaBedrock's block state rewriter
 * constructor, so it can be read without touching how that constructor works.
 * For each entry the components are translated, a block is registered, and the
 * bedrock ids of that block are pointed at the new Java block state id so chunks
 * decode to it.
 *
 * <p>Failures are contained per block. A definition this cannot handle leaves
 * that one block mapped the way ViaBedrock mapped it, which is what happens
 * today anyway, rather than breaking the connection.
 */
public final class CustomBlockRegistrar {

	private static final Logger LOGGER = LoggerFactory.getLogger("ViaBedrockUtility|Blocks");

	private CustomBlockRegistrar() {
	}

	/**
	 * Registers every entry of the palette.
	 *
	 * @param rewriter the rewriter being constructed, already holding its mappings
	 * @param palette the custom blocks the server declared, possibly empty
	 */
	public static void register(
			final BlockStateRewriter rewriter,
			final BlockProperties[] palette) {
		if (rewriter == null || palette == null || palette.length == 0) {
			return;
		}

		final Int2IntMap mappings;
		try {
			mappings = ((IBlockStateRewriter) rewriter).viaBedrockUtility$blockStateIdMappings();
		} catch (final Throwable throwable) {
			// The accessor is optional, so this is reachable when it did not apply.
			LOGGER.error("Could not reach the block id mappings; custom blocks stay off", throwable);
			return;
		}
		if (mappings == null) {
			return;
		}

		int registered = 0;
		for (final BlockProperties properties : palette) {
			try {
				if (register(rewriter, mappings, properties)) {
					registered++;
				}
			} catch (final Throwable throwable) {
				LOGGER.error(
						"Could not register the custom block {}",
						properties == null ? "?" : properties.name(),
						throwable);
			}
		}

		LOGGER.info("Registered {} of {} custom block(s)", registered, palette.length);
	}

	private static boolean register(
			final BlockStateRewriter rewriter,
			final Int2IntMap mappings,
			final BlockProperties properties) {
		if (properties == null) {
			return false;
		}
		final String name = properties.name();
		if (name == null || name.isBlank()) {
			return false;
		}

		// Permutations are not split into vanilla block states yet, so the
		// identifier is the identity of the block and every bedrock state of it
		// renders the same way.
		final BlockComponentsTranslator.Result result =
				BlockComponentsTranslator.translate(properties.properties());
		final BlockState state = DynamicBlockCache.register(name, result);
		if (state == null) {
			return false;
		}

		final int javaId = DynamicBlockCache.javaId(name);
		if (javaId < 0) {
			return false;
		}

		// validBlockStates is public and already holds every bedrock runtime id of
		// this identifier, so the ids do not have to be recomputed from the palette.
		final IntSortedSet bedrockIds = rewriter.validBlockStates(name);
		if (bedrockIds == null || bedrockIds.isEmpty()) {
			// Nothing points at the block, so it would never appear in a chunk.
			return false;
		}

		for (final int bedrockId : bedrockIds) {
			mappings.put(bedrockId, javaId);
		}
		return true;
	}
}

package org.oryxel.viabedrockutility.block.model;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.model.loading.v1.BlockStateResolver;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.oryxel.viabedrockutility.block.BlockComponentsTranslator;
import org.oryxel.viabedrockutility.block.DynamicBlockCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supplies the models for the blocks {@link DynamicBlockCache} registered.
 *
 * <p>This runs on Fabric's block state resolver, which exists for exactly this
 * case: blocks that are only known once a server has described them. Vanilla
 * asks for the model and hands over the real {@link ModelBaker}, so the mod
 * never touches the baking pipeline itself. The alternative is mixing into
 * {@code ModelBakery} to capture its private baker and into
 * {@code ModelManager} to merge the results back in, and an injection that
 * stops matching in either place leaves the game unable to start.
 *
 * <p>A resolver can only be registered while the plugin is initialising, which
 * happens once per resource reload. Joining a Bedrock server applies the pack
 * ViaBedrock converted, and that reload is where these models appear. Blocks
 * that turn up after it simply have no model until the next reload, which is a
 * missing texture rather than a crash.
 */
public final class CustomBlockModelPlugin implements ModelLoadingPlugin {

	private static final Logger LOGGER =
			LoggerFactory.getLogger("ViaBedrockUtility|Models");

	private CustomBlockModelPlugin() {
	}

	/** Registers the plugin. Safe to call once, from client init. */
	public static void register() {
		ModelLoadingPlugin.register(new CustomBlockModelPlugin());
	}

	@Override
	public void initialize(final ModelLoadingPlugin.Context context) {
		try {
			resolve(context);
		} catch (final Throwable throwable) {
			// The reload belongs to the game, not to this feature.
			LOGGER.error("Could not register the custom block models", throwable);
		}
	}

	private static void resolve(final ModelLoadingPlugin.Context context) {
		final Map<String, BlockComponentsTranslator.Result> results = DynamicBlockCache.results();
		if (results.isEmpty()) {
			return;
		}

		// Fabric rejects a second resolver for the same block, and a block only
		// carries one state here, so the blocks already seen are tracked.
		final Set<Block> seen = new HashSet<>();
		int registered = 0;

		for (final Map.Entry<String, BlockComponentsTranslator.Result> entry
				: results.entrySet()) {
			try {
				final BlockState state = DynamicBlockCache.state(entry.getKey());
				if (state == null || !seen.add(state.getBlock())) {
					continue;
				}

				context.registerBlockStateResolver(
						state.getBlock(), resolver(entry.getKey(), entry.getValue()));
				registered++;
			} catch (final Throwable throwable) {
				LOGGER.error("Could not register a model for {}", entry.getKey(), throwable);
			}
		}

		LOGGER.info("Registered {} custom block model(s) for this reload", registered);
	}

	private static BlockStateResolver resolver(
			final String key,
			final BlockComponentsTranslator.Result result) {
		return context -> {
			try {
				final BlockState state = DynamicBlockCache.state(key);
				if (state == null) {
					return;
				}
				context.setModel(state, new Unbaked(key, result).asRoot());
			} catch (final Throwable throwable) {
				LOGGER.error("Could not resolve the model of {}", key, throwable);
			}
		};
	}

	/**
	 * The description of one custom block model. Vanilla bakes it, so the
	 * geometry only has to be turned into cuboid elements once it asks.
	 */
	private record Unbaked(String key, BlockComponentsTranslator.Result result)
			implements BlockStateModel.Unbaked {

		@Override
		public BlockStateModel bake(final ModelBaker baker) {
			try {
				return BlockModelBaker.bake(baker, this.key, this.result);
			} catch (final Throwable throwable) {
				LOGGER.error("Could not bake the model of {}", this.key, throwable);
				// A block drawn as the missing model is still better than a client
				// that cannot finish loading its resources.
				return new SingleVariant(baker.missingBlockModelPart());
			}
		}

		@Override
		public void resolveDependencies(final ResolvableModel.Resolver resolver) {
			// The geometry comes from the pack, not from a parent model.
		}
	}
}

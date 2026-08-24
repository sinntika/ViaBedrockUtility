package org.oryxel.viabedrockutility.block;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.oryxel.viabedrockutility.mixin.interfaces.IHolderReference;
import org.oryxel.viabedrockutility.mixin.interfaces.IIdMapper;
import org.oryxel.viabedrockutility.mixin.interfaces.IMappedRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns every block this mod creates while connected to a Bedrock server.
 *
 * <p>Registration happens mid-game, long after vanilla froze its block registry,
 * so each write reopens the registry through the duck interfaces and closes it
 * again immediately. Everything registered this way is released in
 * {@link #clear()} on disconnect, otherwise the next server would inherit blocks
 * from the previous one and the ids would no longer line up.
 */
public final class DynamicBlockCache {

	public static final String NAMESPACE = "viabedrock";

	private static final Logger LOGGER = LoggerFactory.getLogger("ViaBedrockUtility|Blocks");

	private static final Map<String, BlockState> KEY_TO_STATE = new ConcurrentHashMap<>();
	private static final Map<String, ResourceKey<Block>> KEY_TO_REGISTRY_KEY = new ConcurrentHashMap<>();
	private static final Map<String, BlockComponentsTranslator.Result> KEY_TO_RESULT =
			new ConcurrentHashMap<>();

	private DynamicBlockCache() {
	}

	/**
	 * The cache key for a Bedrock block state. Permutations are not split into
	 * vanilla block states yet, so the full state string is the identity.
	 */
	public static String key(final net.raphimc.viabedrock.api.model.BlockState bedrockState) {
		return bedrockState.toBlockStateString();
	}

	/**
	 * Registers a block for the given key, or returns the already registered one.
	 */
	public static synchronized BlockState register(
			final String key,
			final BlockComponentsTranslator.Result result) {
		final BlockState existing = KEY_TO_STATE.get(key);
		if (existing != null) {
			return existing;
		}

		final ResourceKey<Block> registryKey = ResourceKey.create(
				Registries.BLOCK,
				Identifier.fromNamespaceAndPath(NAMESPACE, sanitize(key)));

		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
				.setId(registryKey)
				// The shape comes from the pack, so it must never be cached or used
				// for face culling.
				.noOcclusion()
				.dynamicShape()
				.friction(result.friction())
				.destroyTime(result.destroyTime());
		final int lightEmission = result.lightEmission();
		if (lightEmission > 0) {
			properties = properties.lightLevel(state -> lightEmission);
		}

		final CustomBlock block =
				new CustomBlock(properties, result.shape(), result.collisionShape());

		final IMappedRegistry blocks = (IMappedRegistry) BuiltInRegistries.BLOCK;
		final Holder.Reference<Block> holder;
		blocks.viaBedrockUtility$unfreeze();
		try {
			holder = ((WritableRegistry<Block>) BuiltInRegistries.BLOCK)
					.register(registryKey, block, RegistrationInfo.BUILT_IN);
		} finally {
			// Refreeze even on failure; a permanently open registry corrupts every
			// later lookup.
			blocks.viaBedrockUtility$refreeze();
		}

		// The holder was created after freezing, so its tag set was never bound.
		((IHolderReference) holder).viaBedrockUtility$resolveTags();

		final BlockState state = block.defaultBlockState();
		// Both of these normally happen in the vanilla bootstrap, in this order.
		// Without initCache the state keeps a null cache and the first lighting or
		// occlusion lookup on it throws, and without the mapper entry the state has
		// no network id at all.
		state.initCache();
		Block.BLOCK_STATE_REGISTRY.add(state);

		KEY_TO_STATE.put(key, state);
		KEY_TO_REGISTRY_KEY.put(key, registryKey);
		KEY_TO_RESULT.put(key, result);

		return state;
	}

	public static BlockState state(final String key) {
		return KEY_TO_STATE.get(key);
	}

	/** The Java block state id, or -1 when the key was never registered. */
	public static int javaId(final String key) {
		final BlockState state = KEY_TO_STATE.get(key);
		return state == null ? -1 : Block.getId(state);
	}

	/** Registered blocks and the pack data phase 3 bakes models from. */
	public static Map<String, BlockComponentsTranslator.Result> results() {
		return Collections.unmodifiableMap(KEY_TO_RESULT);
	}

	public static Map<String, BlockState> states() {
		return Collections.unmodifiableMap(KEY_TO_STATE);
	}

	public static boolean isEmpty() {
		return KEY_TO_STATE.isEmpty();
	}

	/** Releases every block registered for the connection that just ended. */
	public static synchronized void clear() {
		if (KEY_TO_STATE.isEmpty()) {
			KEY_TO_RESULT.clear();
			KEY_TO_REGISTRY_KEY.clear();
			return;
		}

		final int count = KEY_TO_STATE.size();
		final IMappedRegistry blocks = (IMappedRegistry) BuiltInRegistries.BLOCK;
		final IIdMapper states = (IIdMapper) Block.BLOCK_STATE_REGISTRY;

		blocks.viaBedrockUtility$unfreeze();
		try {
			for (final Map.Entry<String, BlockState> entry : KEY_TO_STATE.entrySet()) {
				for (final BlockState possible
						: entry.getValue().getBlock().getStateDefinition().getPossibleStates()) {
					states.viaBedrockUtility$unregisterState(possible);
				}

				final ResourceKey<Block> registryKey = KEY_TO_REGISTRY_KEY.get(entry.getKey());
				if (registryKey != null) {
					blocks.viaBedrockUtility$unregister(registryKey);
				}
			}
		} finally {
			blocks.viaBedrockUtility$refreeze();
		}

		KEY_TO_STATE.clear();
		KEY_TO_REGISTRY_KEY.clear();
		KEY_TO_RESULT.clear();

		LOGGER.info("Released {} custom block(s)", count);
	}

	/**
	 * Bedrock block identifiers allow characters a Java {@code Identifier} path
	 * rejects, and an invalid path throws instead of degrading.
	 */
	private static String sanitize(final String key) {
		final String lowered = key.toLowerCase(Locale.ROOT);
		final StringBuilder builder = new StringBuilder(lowered.length());
		for (int index = 0; index < lowered.length(); index++) {
			final char character = lowered.charAt(index);
			builder.append(Identifier.validPathChar(character) ? character : '_');
		}
		return builder.isEmpty() ? "unknown" : builder.toString();
	}
}

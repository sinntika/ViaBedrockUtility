package org.oryxel.viabedrockutility.block.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;

/**
 * The Bedrock geometries of the server that is currently connected.
 *
 * <p>Phase 4 fills this while the server resource pack is read; the baker only
 * reads from it. A block whose geometry never arrived falls back to a full cube,
 * because an unknown geometry should look wrong rather than be invisible and
 * impossible to find.
 */
public final class GeometryRegistry {

	private static final String VANILLA_PREFIX = "minecraft:";

	private static final Map<String, BedrockGeometryModel> MODELS = new ConcurrentHashMap<>();

	private GeometryRegistry() {
	}

	public static void register(final String identifier, final BedrockGeometryModel model) {
		if (identifier == null || identifier.isEmpty() || model == null) {
			return;
		}
		MODELS.put(strip(identifier), model);
	}

	/** The geometry for the identifier, or the built-in full block as a fallback. */
	public static BedrockGeometryModel get(final String identifier) {
		if (identifier != null) {
			final BedrockGeometryModel model = MODELS.get(strip(identifier));
			if (model != null) {
				return model;
			}
		}
		return FullBlockGeometry.get();
	}

	public static boolean has(final String identifier) {
		return identifier != null && MODELS.containsKey(strip(identifier));
	}

	public static int size() {
		return MODELS.size();
	}

	public static void clear() {
		MODELS.clear();
	}

	/**
	 * Block definitions write {@code minecraft:geometry.full_block} while the
	 * geometry files themselves declare {@code geometry.full_block}, so both
	 * spellings have to resolve to the same entry.
	 */
	private static String strip(final String identifier) {
		return identifier.startsWith(VANILLA_PREFIX)
				? identifier.substring(VANILLA_PREFIX.length())
				: identifier;
	}
}

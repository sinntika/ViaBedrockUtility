package org.oryxel.viabedrockutility.block.model;

import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.api.resourcepack.content.Content;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Harvests the two things custom blocks need out of the Bedrock packs.
 *
 * <p>ViaBedrock builds definitions for entity geometries and item textures, but
 * nothing reads block geometries or {@code terrain_texture.json}, so both are
 * read here. The pack files are all reachable through public API, which is why
 * this is a plain reader rather than a mixin into the definition classes.
 *
 * <p>Everything is best-effort per file: a server can send any pack it likes,
 * including malformed or partial ones, and a single unreadable model must not
 * cost the player every other block.
 */
public final class PackBlockResources {

	private static final Logger LOGGER = LoggerFactory.getLogger("ViaBedrockUtility|Blocks");

	/** The namespace ViaBedrock's converted pack uses. */
	private static final String NAMESPACE = "viabedrock";
	private static final String TEXTURE_PREFIX = "block/";
	private static final String TEXTURE_TARGET = "assets/" + NAMESPACE + "/textures/" + TEXTURE_PREFIX;

	private static final String MODEL_DIRECTORY = "models/";
	private static final String JSON_EXTENSION = ".json";
	private static final String TERRAIN_TEXTURE = "textures/terrain_texture.json";

	/** The 1.12+ geometry wrapper, and the prefix the pre-1.12 format used instead. */
	private static final String GEOMETRY_LIST = "minecraft:geometry";
	private static final String GEOMETRY_PREFIX = "geometry.";

	private static final String FALLBACK_TEXTURE_NAME = "missing";

	private PackBlockResources() {
	}

	/**
	 * Reads every pack in the stack and writes the block sprites into the
	 * converted pack.
	 *
	 * <p>The stack is walked bottom to top because that is the order Bedrock
	 * resolves overrides in, so a pack higher in the stack overwrites what a
	 * lower one registered under the same name.
	 *
	 * @param storage the pack stack the server sent
	 * @param output the converted java pack, still open for writing
	 */
	public static void collect(final ResourcePackStorage storage, final Content output) {
		if (storage == null || output == null) {
			return;
		}

		final Map<String, String> textures = new HashMap<>();
		int geometries = 0;

		for (final ResourcePack pack : storage.getPackStackBottomToTop()) {
			final Content content = pack.content();
			if (content == null) {
				continue;
			}

			geometries += collectGeometries(content);
			collectTextures(content, output, textures);
		}

		// Captured by value: the baker runs later, on the render thread, and must
		// not see a map that a following pack download is still mutating.
		final Map<String, String> resolved = Map.copyOf(textures);
		BlockModelBaker.setTextureResolver(texture -> identifier(resolved, texture));

		LOGGER.info(
				"Read {} block geometr{} and {} block texture(s) from the server packs",
				geometries,
				geometries == 1 ? "y" : "ies",
				resolved.size());
	}

	/**
	 * Turns a Bedrock texture name into the sprite the converted pack holds.
	 *
	 * <p>Unknown names still get an identifier rather than nothing: a block
	 * whose texture the pack never defined should show up as a missing texture,
	 * which is diagnosable, instead of as an invisible block, which is not.
	 */
	private static Identifier identifier(final Map<String, String> textures, final String texture) {
		final String name = textures.getOrDefault(texture, sanitize(texture));
		return Identifier.fromNamespaceAndPath(NAMESPACE, TEXTURE_PREFIX + name);
	}

	private static int collectGeometries(final Content content) {
		int found = 0;
		for (final String path : modelFiles(content)) {
			final JsonObject json = readJson(content, path);
			if (json == null || !isGeometry(json)) {
				continue;
			}

			try {
				final List<BedrockGeometryModel> models = BedrockGeometryModel.fromJson(json);
				if (models == null) {
					continue;
				}
				for (final BedrockGeometryModel model : models) {
					if (model == null || model.getIdentifier() == null) {
						continue;
					}
					GeometryRegistry.register(model.getIdentifier(), model);
					found++;
				}
			} catch (final Throwable throwable) {
				LOGGER.debug("Could not read the geometry in {}", path, throwable);
			}
		}
		return found;
	}

	/**
	 * Bedrock only loads geometry from {@code models/}, so the whole pack is
	 * never walked. The fallback exists because the listing is relative to the
	 * requested directory in some pack layouts and absolute in others.
	 */
	private static List<String> modelFiles(final Content content) {
		try {
			final List<String> files = content.getFilesDeep(MODEL_DIRECTORY, JSON_EXTENSION);
			if (files != null && !files.isEmpty()) {
				return files;
			}
		} catch (final Throwable throwable) {
			LOGGER.debug("Could not list the models directory", throwable);
		}

		try {
			final List<String> files = content.getFilesDeep("", JSON_EXTENSION);
			if (files != null) {
				return files.stream().filter(path -> path.startsWith(MODEL_DIRECTORY)).toList();
			}
		} catch (final Throwable throwable) {
			LOGGER.debug("Could not list the pack contents", throwable);
		}

		return List.of();
	}

	private static boolean isGeometry(final JsonObject json) {
		if (json.has(GEOMETRY_LIST)) {
			return true;
		}
		// The pre-1.12 format had no wrapper; each geometry was a top level key.
		for (final Map.Entry<String, JsonElement> entry : json.entrySet()) {
			if (entry.getKey().startsWith(GEOMETRY_PREFIX)) {
				return true;
			}
		}
		return false;
	}

	private static void collectTextures(
			final Content content, final Content output, final Map<String, String> textures) {
		final JsonObject json = readJson(content, TERRAIN_TEXTURE);
		if (json == null) {
			return;
		}

		final JsonElement data = json.get("texture_data");
		if (data == null || !data.isJsonObject()) {
			return;
		}

		for (final Map.Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
			final String source = texturePath(entry.getValue());
			if (source == null || source.isEmpty()) {
				continue;
			}

			final String name = sanitize(entry.getKey());
			if (copyImage(content, output, source, name)) {
				textures.put(entry.getKey(), name);
			}
		}
	}

	/**
	 * A texture entry is a string, an object with a {@code path}, or a list of
	 * either when the block has variations. Only the first variation is taken:
	 * picking between them needs the block's position, which no model has.
	 */
	private static String texturePath(final JsonElement value) {
		if (value == null || !value.isJsonObject()) {
			return null;
		}
		return path(value.getAsJsonObject().get("textures"));
	}

	private static String path(final JsonElement element) {
		if (element == null) {
			return null;
		}
		if (element.isJsonPrimitive()) {
			return element.getAsString();
		}
		if (element.isJsonObject()) {
			final JsonElement path = element.getAsJsonObject().get("path");
			return path != null && path.isJsonPrimitive() ? path.getAsString() : null;
		}
		if (element.isJsonArray()) {
			final JsonArray array = element.getAsJsonArray();
			return array.isEmpty() ? null : path(array.get(0));
		}
		return null;
	}

	/**
	 * Bedrock references textures without a file extension, so the shortname
	 * lookup is tried first and the literal path only as a fallback.
	 */
	private static boolean copyImage(
			final Content content, final Content output, final String source, final String name) {
		Content.LazyImage image = null;
		try {
			image = content.getShortnameImage(source);
		} catch (final Throwable throwable) {
			LOGGER.debug("Could not read the texture {}", source, throwable);
		}

		if (image == null) {
			try {
				image = content.getImage(source);
			} catch (final Throwable throwable) {
				LOGGER.debug("Could not read the texture {}", source, throwable);
			}
		}

		if (image == null) {
			return false;
		}

		try {
			return output.putPngImage(TEXTURE_TARGET + name + ".png", image);
		} catch (final Throwable throwable) {
			LOGGER.debug("Could not write the texture {}", name, throwable);
			return false;
		}
	}

	private static JsonObject readJson(final Content content, final String path) {
		try {
			if (content.contains(path)) {
				return content.getJson(path);
			}
		} catch (final Throwable throwable) {
			LOGGER.debug("Could not parse {}", path, throwable);
			return null;
		}

		// The directory listing can be relative to the directory it was asked for.
		if (path.startsWith(MODEL_DIRECTORY)) {
			return null;
		}

		try {
			final String nested = MODEL_DIRECTORY + path;
			return content.contains(nested) ? content.getJson(nested) : null;
		} catch (final Throwable throwable) {
			LOGGER.debug("Could not parse {}", path, throwable);
			return null;
		}
	}

	/**
	 * Reduces a Bedrock name to something a resource location accepts.
	 *
	 * <p>Bedrock names allow characters and directory prefixes that Java
	 * identifiers reject, and an invalid identifier throws rather than degrading,
	 * so anything unusable becomes an underscore.
	 */
	public static String sanitize(final String texture) {
		if (texture == null || texture.isEmpty()) {
			return FALLBACK_TEXTURE_NAME;
		}

		String name = texture;
		final int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}

		final int dot = name.lastIndexOf('.');
		if (dot > 0) {
			name = name.substring(0, dot);
		}

		name = name.toLowerCase(Locale.ROOT);

		final StringBuilder builder = new StringBuilder(name.length());
		for (int index = 0; index < name.length(); index++) {
			final char character = name.charAt(index);
			builder.append(Identifier.validPathChar(character) ? character : '_');
		}

		final String sanitized = builder.toString();
		return sanitized.isEmpty() ? FALLBACK_TEXTURE_NAME : sanitized;
	}
}

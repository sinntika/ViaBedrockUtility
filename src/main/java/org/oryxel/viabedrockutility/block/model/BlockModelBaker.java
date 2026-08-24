package org.oryxel.viabedrockutility.block.model;

import com.mojang.math.Quadrant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.cuboid.CuboidRotation;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.cube.converter.converter.enums.RotationType;
import org.cube.converter.model.element.Cube;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.model.impl.java.JavaItemModel;
import org.cube.converter.util.element.Position2V;
import org.cube.converter.util.element.Position3V;
import org.cube.converter.util.element.UVMap;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.block.BlockComponentsTranslator;
import org.oryxel.viabedrockutility.block.DynamicBlockCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bakes the block models for everything {@link DynamicBlockCache} registered.
 *
 * <p>The model is described as cuboid elements and handed to vanilla's
 * {@link UnbakedCuboidGeometry#bake}, which is the same code path a json block
 * model takes. Building the quads by hand instead would mean owning UV mapping,
 * vertex winding, element rotation and material flags, all of which move between
 * versions; this way only the description has to be right.
 *
 * <p>CubeConverter already converts the Bedrock cubes into Java model space and
 * limits the rotation angles to what Java allows, so the converted cubes are
 * used as they come. Only the UV maps still need scaling, because Bedrock states
 * them in texture pixels while Java wants the fixed 16x16 space.
 */
public final class BlockModelBaker {

	/** Where the pack ViaBedrock converts puts its block sprites. */
	private static final String TEXTURE_NAMESPACE = "viabedrock";
	private static final String TEXTURE_PREFIX = "block/";

	/** Bedrock's own default, used when a geometry declares no texture size. */
	private static final float DEFAULT_TEXTURE_SIZE = 16.0F;

	/** The Bedrock alias that covers every side face at once. */
	private static final String SIDE_FACE = "side";

	private static final Logger LOGGER = LoggerFactory.getLogger("ViaBedrockUtility|Models");

	/**
	 * The per-face texture names handed to CubeConverter. The values only have to
	 * be stable, since the sprites themselves are resolved through
	 * {@link TextureSlots} rather than through the converted model.
	 */
	private static final Map<org.cube.converter.util.element.Direction, String> CONVERTER_TEXTURES =
			Map.of(
					org.cube.converter.util.element.Direction.DOWN, "down",
					org.cube.converter.util.element.Direction.UP, "up",
					org.cube.converter.util.element.Direction.NORTH, "north",
					org.cube.converter.util.element.Direction.SOUTH, "south",
					org.cube.converter.util.element.Direction.WEST, "west",
					org.cube.converter.util.element.Direction.EAST, "east");

	/** Turns a Bedrock texture name into the sprite of the converted Java pack. */
	public interface TextureResolver {

		Identifier resolve(String bedrockTexture);
	}

	private static volatile TextureResolver textureResolver = BlockModelBaker::defaultTexture;

	private BlockModelBaker() {
	}

	/**
	 * Replaces the texture lookup. Phase 4 sets this once the pack's own texture
	 * definitions have been read; until then the name is mapped directly.
	 */
	public static void setTextureResolver(final TextureResolver resolver) {
		textureResolver = resolver == null ? BlockModelBaker::defaultTexture : resolver;
	}

	/**
	 * Bakes a model for every registered custom block.
	 *
	 * <p>A single broken model must not take the whole resource reload down with
	 * it, so failures are logged per block and the rest still bake.
	 */
	public static Map<BlockState, BlockStateModel> bakeAll(final ModelBaker baker) {
		final Map<String, BlockComponentsTranslator.Result> results = DynamicBlockCache.results();
		if (results.isEmpty()) {
			return Map.of();
		}

		final Map<BlockState, BlockStateModel> models = new HashMap<>();
		for (final Map.Entry<String, BlockComponentsTranslator.Result> entry : results.entrySet()) {
			final BlockState state = DynamicBlockCache.state(entry.getKey());
			if (state == null) {
				continue;
			}

			try {
				models.put(state, bake(baker, entry.getKey(), entry.getValue()));
			} catch (final Throwable throwable) {
				LOGGER.error("Could not bake a model for {}", entry.getKey(), throwable);
			}
		}

		LOGGER.info("Baked {} custom block model(s)", models.size());
		return models;
	}

	/** Bakes the model of one custom block. */
	public static BlockStateModel bake(
			final ModelBaker baker,
			final String key,
			final BlockComponentsTranslator.Result result) {
		final ModelDebugName debugName = () -> DynamicBlockCache.NAMESPACE + ":" + key;
		final BedrockGeometryModel geometry = GeometryRegistry.get(result.geometry());
		final JavaItemModel converted =
				geometry.toJavaItemModel(CONVERTER_TEXTURES, RotationType.POST_1_21_11);

		float textureWidth = DEFAULT_TEXTURE_SIZE;
		float textureHeight = DEFAULT_TEXTURE_SIZE;
		final Position2V textureSize = converted.getScale();
		if (textureSize != null) {
			if (textureSize.getX() > 0.0F) {
				textureWidth = textureSize.getX();
			}
			if (textureSize.getY() > 0.0F) {
				textureHeight = textureSize.getY();
			}
		}

		final List<CuboidModelElement> elements = new ArrayList<>();
		for (final Parent parent : converted.getParents()) {
			for (final Cube cube : parent.getCubes().values()) {
				final CuboidModelElement element =
						element(cube, parent, result, textureWidth, textureHeight);
				if (element != null) {
					elements.add(element);
				}
			}
		}

		final TextureSlots slots = slots(result, debugName);
		final QuadCollection quads = elements.isEmpty()
				? QuadCollection.EMPTY
				: UnbakedCuboidGeometry.bake(
						elements, slots, baker, BlockModelRotation.IDENTITY, debugName);

		if (elements.isEmpty()) {
			LOGGER.warn("{} has no textured geometry and will not be visible", key);
		}

		// Ambient occlusion is on, like a vanilla block model; the shapes come from
		// the pack and are not necessarily full cubes, so nothing else can be
		// assumed here.
		return new SingleVariant(
				new SimpleModelWrapper(quads, true, particle(baker, result, slots, debugName)));
	}

	/**
	 * One Bedrock cube as a vanilla cuboid element, or null when it has nothing
	 * left to draw.
	 */
	private static CuboidModelElement element(
			final Cube cube,
			final Parent parent,
			final BlockComponentsTranslator.Result result,
			final float textureWidth,
			final float textureHeight) {
		final Position3V position = cube.getPosition();
		final Position3V size = cube.getSize();
		if (position == null || size == null) {
			return null;
		}

		final Map<Direction, CuboidFace> faces =
				faces(cube, result, textureWidth, textureHeight);
		if (faces.isEmpty()) {
			// Every face was untextured, so the cube would only add invisible quads.
			return null;
		}

		final Vector3f from = new Vector3f(position.getX(), position.getY(), position.getZ());
		final Vector3f to = new Vector3f(from).add(size.getX(), size.getY(), size.getZ());

		// A cuboid element carries a single rotation, so a rotated bone can only be
		// honoured when the cube inside it is not rotated itself.
		CuboidRotation rotation = rotation(cube.getRotation(), cube.getPivot());
		if (rotation == null) {
			rotation = rotation(parent.getRotation(), parent.getPivot());
		}

		return new CuboidModelElement(from, to, faces, rotation, true, result.lightEmission());
	}

	/** The textured faces of one cube, keyed by the direction they face. */
	private static Map<Direction, CuboidFace> faces(
			final Cube cube,
			final BlockComponentsTranslator.Result result,
			final float textureWidth,
			final float textureHeight) {
		final Map<Direction, CuboidFace> faces = new EnumMap<>(Direction.class);

		final UVMap uvMap = cube.getUvMap();
		if (uvMap == null) {
			return faces;
		}

		// Box UVs have to be unfolded into per-face UVs, and per-face UVs are stated
		// in the geometry's own texture size; this does both.
		final UVMap perFace = uvMap.toJavaPerfaceUV(textureWidth, textureHeight);
		if (perFace == null) {
			return faces;
		}

		final Map<org.cube.converter.util.element.Direction, Float[]> uvs = perFace.getUvMap();
		if (uvs == null) {
			return faces;
		}
		final Map<org.cube.converter.util.element.Direction, Float> rotations =
				perFace.getUvRotation();

		for (final Map.Entry<org.cube.converter.util.element.Direction, Float[]> entry
				: uvs.entrySet()) {
			final Float[] uv = entry.getValue();
			if (uv == null || uv.length < 4
					|| uv[0] == null || uv[1] == null || uv[2] == null || uv[3] == null) {
				continue;
			}

			final Direction direction = vanilla(entry.getKey());
			if (texture(result, direction) == null) {
				// Leaving the face out is better than baking it against the missing
				// texture, which would draw a solid magenta side.
				continue;
			}

			final CuboidFace.UVs box = new CuboidFace.UVs(uv[0], uv[1], uv[2], uv[3]);
			final Quadrant rotation =
					quadrant(rotations == null ? null : rotations.get(entry.getKey()));

			// No cull direction: the shapes come from the pack, so a face cannot be
			// assumed to sit flush against the neighbouring block.
			faces.put(
					direction,
					new CuboidFace(
							null,
							CuboidFace.NO_TINT,
							"#" + direction.getName(),
							box,
							rotation));
		}

		return faces;
	}

	/** The texture slots the faces above refer to. */
	private static TextureSlots slots(
			final BlockComponentsTranslator.Result result,
			final ModelDebugName debugName) {
		final TextureSlots.Data.Builder builder = new TextureSlots.Data.Builder();
		boolean any = false;

		for (final Direction direction : Direction.values()) {
			final String texture = texture(result, direction);
			if (texture == null) {
				continue;
			}
			builder.addTexture(direction.getName(), new Material(textureResolver.resolve(texture)));
			any = true;
		}

		if (!any) {
			return TextureSlots.EMPTY;
		}
		return new TextureSlots.Resolver().addLast(builder.build()).resolve(debugName);
	}

	/**
	 * The particle sprite, taken from the first textured face. Breaking a block
	 * without one would otherwise throw while spawning particles.
	 */
	private static Material.Baked particle(
			final ModelBaker baker,
			final BlockComponentsTranslator.Result result,
			final TextureSlots slots,
			final ModelDebugName debugName) {
		for (final Direction direction : Direction.values()) {
			if (texture(result, direction) != null) {
				return baker.materials().resolveSlot(slots, direction.getName(), debugName);
			}
		}
		return baker.missingBlockModelPart().particleMaterial();
	}

	/**
	 * The Bedrock texture for a face. Material instances may name the face
	 * directly, use {@code side} for all four sides, or use the catch-all the
	 * translator already resolves.
	 */
	private static String texture(
			final BlockComponentsTranslator.Result result,
			final Direction direction) {
		final String direct = result.texture(direction.getName());
		if (direct != null) {
			return direct;
		}
		if (direction != Direction.UP && direction != Direction.DOWN) {
			return result.texture(SIDE_FACE);
		}
		return null;
	}

	/** Java only supports UV rotations in quarter turns. */
	private static Quadrant quadrant(final Float rotation) {
		if (rotation == null) {
			return Quadrant.R0;
		}
		final int steps = Math.round(rotation / 90.0F) & 3;
		return switch (steps) {
			case 1 -> Quadrant.R90;
			case 2 -> Quadrant.R180;
			case 3 -> Quadrant.R270;
			default -> Quadrant.R0;
		};
	}

	/**
	 * The element rotation, or null when there is none. A rotation around one
	 * axis is stated as such because that is the case Java renders exactly; the
	 * general form is only used when more than one axis is involved.
	 */
	private static CuboidRotation rotation(final Position3V rotation, final Position3V pivot) {
		if (rotation == null || rotation.isZero()) {
			return null;
		}

		final Vector3f origin = pivot == null
				? new Vector3f()
				: new Vector3f(pivot.getX(), pivot.getY(), pivot.getZ());

		final boolean x = rotation.getX() != 0.0F;
		final boolean y = rotation.getY() != 0.0F;
		final boolean z = rotation.getZ() != 0.0F;

		final CuboidRotation.RotationValue value;
		if (x && !y && !z) {
			value = new CuboidRotation.SingleAxisRotation(Direction.Axis.X, rotation.getX());
		} else if (!x && y && !z) {
			value = new CuboidRotation.SingleAxisRotation(Direction.Axis.Y, rotation.getY());
		} else if (!x && !y && z) {
			value = new CuboidRotation.SingleAxisRotation(Direction.Axis.Z, rotation.getZ());
		} else {
			value = new CuboidRotation.EulerXYZRotation(
					rotation.getX(), rotation.getY(), rotation.getZ());
		}

		// No rescale: Bedrock does not grow the cube to fit its rotation.
		return new CuboidRotation(origin, value, false);
	}

	private static Direction vanilla(final org.cube.converter.util.element.Direction direction) {
		return switch (direction) {
			case DOWN -> Direction.DOWN;
			case UP -> Direction.UP;
			case NORTH -> Direction.NORTH;
			case SOUTH -> Direction.SOUTH;
			case WEST -> Direction.WEST;
			case EAST -> Direction.EAST;
		};
	}

	/**
	 * The fallback texture lookup: Bedrock names a texture like
	 * {@code textures/blocks/stone}, and the pack ViaBedrock converts holds the
	 * sprites under one flat folder.
	 */
	private static Identifier defaultTexture(final String bedrockTexture) {
		String path = bedrockTexture;

		final int slash = path.lastIndexOf('/');
		if (slash >= 0) {
			path = path.substring(slash + 1);
		}
		final int dot = path.lastIndexOf('.');
		if (dot > 0) {
			path = path.substring(0, dot);
		}
		path = path.toLowerCase(Locale.ROOT);

		final StringBuilder builder = new StringBuilder(path.length());
		for (int index = 0; index < path.length(); index++) {
			final char character = path.charAt(index);
			builder.append(Identifier.validPathChar(character) ? character : '_');
		}

		return Identifier.fromNamespaceAndPath(
				TEXTURE_NAMESPACE,
				TEXTURE_PREFIX + (builder.isEmpty() ? "missing" : builder.toString()));
	}
}

package org.oryxel.viabedrockutility.block;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.NumberTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.nbt.tag.Tag;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Turns the {@code components} NBT of a Bedrock block definition into the pieces
 * a vanilla block needs.
 *
 * <p>Only data extraction happens here. Nothing in this class touches the block
 * registry or the model system, which keeps it testable and keeps the ordering
 * problems in {@link DynamicBlockCache}.
 */
public final class BlockComponentsTranslator {

	/** Vanilla's default, used when the block ships no friction component. */
	public static final float DEFAULT_FRICTION = 0.6F;

	/**
	 * Bedrock block breaking is server authoritative, so this value only decides
	 * how the client animates mining before the server answers.
	 */
	public static final float DEFAULT_DESTROY_TIME = 1.0F;

	public static final String FULL_BLOCK_GEOMETRY = "minecraft:geometry.full_block";

	/** Bedrock's wildcard face name in {@code material_instances}. */
	public static final String ALL_FACES = "*";

	private BlockComponentsTranslator() {
	}

	public static Result translate(final CompoundTag blockTag) {
		final Result.Builder builder = Result.builder();
		if (blockTag == null) {
			return builder.build();
		}

		// Some payloads nest the components, others already are the components.
		CompoundTag components = compound(blockTag, "components");
		if (components == null) {
			components = blockTag;
		}

		final VoxelShape selection = readBox(components.get("minecraft:selection_box"));
		final VoxelShape collision = readBox(components.get("minecraft:collision_box"));

		final Tag geometry = components.get("minecraft:geometry");
		if (geometry instanceof StringTag identifier) {
			builder.geometry(identifier.getValue());
		} else if (geometry instanceof CompoundTag geometryTag
				&& geometryTag.get("identifier") instanceof StringTag identifier) {
			builder.geometry(identifier.getValue());
		}

		final Tag friction = components.get("minecraft:friction");
		if (friction instanceof NumberTag number) {
			builder.friction(number.asFloat());
		}

		builder.destroyTime(readDestroyTime(components.get("minecraft:destructible_by_mining")));
		builder.lightEmission(readLightEmission(components.get("minecraft:light_emission")));
		readMaterialInstances(components.get("minecraft:material_instances"), builder);

		final Result.Transform transform = readTransform(components.get("minecraft:transformation"));
		builder.transform(transform);

		// The transformation component rotates the collision box too, not just the
		// model, so it has to be applied here rather than only at bake time.
		builder.shape(rotate(selection, transform));
		builder.collisionShape(rotate(collision, transform));

		return builder.build();
	}

	private static CompoundTag compound(final CompoundTag tag, final String name) {
		final Tag value = tag.get(name);
		return value instanceof CompoundTag child ? child : null;
	}

	/**
	 * Reads a Bedrock box component. Returns {@code null} when the component is
	 * absent, which means "full block", and an empty shape when it is explicitly
	 * disabled.
	 */
	private static VoxelShape readBox(final Tag tag) {
		if (tag instanceof NumberTag number) {
			// Shorthand form: 0 disables the box, anything else is a full block.
			return number.asBoolean() ? Shapes.block() : Shapes.empty();
		}
		if (!(tag instanceof CompoundTag box)) {
			return null;
		}
		if (box.get("enabled") instanceof NumberTag enabled && !enabled.asBoolean()) {
			return Shapes.empty();
		}

		// Newer packs list several boxes, already in Java-style min/max pixels.
		if (box.get("boxes") instanceof ListTag<?> boxes) {
			VoxelShape shape = Shapes.empty();
			for (final Tag entry : boxes.getValue()) {
				if (!(entry instanceof CompoundTag part)) {
					continue;
				}
				shape = Shapes.or(shape, Shapes.create(new AABB(
						number(part, "minX", 0.0F) / 16.0F,
						number(part, "minY", 0.0F) / 16.0F,
						number(part, "minZ", 0.0F) / 16.0F,
						number(part, "maxX", 16.0F) / 16.0F,
						number(part, "maxY", 16.0F) / 16.0F,
						number(part, "maxZ", 16.0F) / 16.0F)));
			}
			return shape;
		}

		// Classic form: an origin measured from the block centre on X and Z, plus a
		// size. Y is already measured from the bottom of the block.
		final float[] origin = readVector(box.get("origin"), 0.0F);
		final float[] size = readVector(box.get("size"), 16.0F);
		if (origin == null && size == null) {
			return null;
		}

		final float[] safeOrigin = origin == null ? new float[] {-8.0F, 0.0F, -8.0F} : origin;
		final float[] safeSize = size == null ? new float[] {16.0F, 16.0F, 16.0F} : size;
		final float minX = (safeOrigin[0] + 8.0F) / 16.0F;
		final float minY = safeOrigin[1] / 16.0F;
		final float minZ = (safeOrigin[2] + 8.0F) / 16.0F;
		return Shapes.create(new AABB(
				minX,
				minY,
				minZ,
				minX + safeSize[0] / 16.0F,
				minY + safeSize[1] / 16.0F,
				minZ + safeSize[2] / 16.0F));
	}

	private static float[] readVector(final Tag tag, final float fallback) {
		if (!(tag instanceof ListTag<?> list)) {
			return null;
		}
		final float[] values = {fallback, fallback, fallback};
		int index = 0;
		for (final Tag entry : list.getValue()) {
			if (index >= values.length) {
				break;
			}
			if (entry instanceof NumberTag number) {
				values[index] = number.asFloat();
			}
			index++;
		}
		return values;
	}

	private static float number(final CompoundTag tag, final String name, final float fallback) {
		return tag.get(name) instanceof NumberTag number ? number.asFloat() : fallback;
	}

	private static float readDestroyTime(final Tag tag) {
		if (tag instanceof NumberTag number) {
			return number.asFloat();
		}
		if (tag instanceof CompoundTag component) {
			return number(component, "value", DEFAULT_DESTROY_TIME);
		}
		return DEFAULT_DESTROY_TIME;
	}

	private static int readLightEmission(final Tag tag) {
		final float value;
		if (tag instanceof NumberTag number) {
			value = number.asFloat();
		} else if (tag instanceof CompoundTag component) {
			value = number(component, "emission", 0.0F);
		} else {
			return 0;
		}
		if (value <= 0.0F) {
			return 0;
		}
		// Older definitions store a 0..1 fraction, newer ones a 0..15 level.
		final int level = value <= 1.0F ? Math.round(value * 15.0F) : Math.round(value);
		return Math.min(15, level);
	}

	private static void readMaterialInstances(final Tag tag, final Result.Builder builder) {
		if (!(tag instanceof CompoundTag component)) {
			return;
		}
		CompoundTag materials = compound(component, "materials");
		if (materials == null) {
			materials = component;
		}
		for (final Map.Entry<String, Tag> entry : materials.entrySet()) {
			final Tag value = entry.getValue();
			if (value instanceof StringTag alias) {
				// A face can point at another face's material instead of its own.
				builder.faceAlias(entry.getKey(), alias.getValue());
			} else if (value instanceof CompoundTag material
					&& material.get("texture") instanceof StringTag texture) {
				builder.texture(entry.getKey(), texture.getValue());
			}
		}
	}

	private static Result.Transform readTransform(final Tag tag) {
		if (!(tag instanceof CompoundTag component)) {
			return Result.Transform.IDENTITY;
		}
		return new Result.Transform(
				number(component, "TX", 0.0F),
				number(component, "TY", 0.0F),
				number(component, "TZ", 0.0F),
				number(component, "SX", 1.0F),
				number(component, "SY", 1.0F),
				number(component, "SZ", 1.0F),
				number(component, "RX", 0.0F),
				number(component, "RY", 0.0F),
				number(component, "RZ", 0.0F));
	}

	private static VoxelShape rotate(final VoxelShape shape, final Result.Transform transform) {
		if (shape == null || transform.isIdentityRotation()) {
			return shape;
		}
		VoxelShape rotated = shape;
		rotated = rotateAroundAxis(rotated, 0, transform.rx());
		rotated = rotateAroundAxis(rotated, 1, transform.ry());
		rotated = rotateAroundAxis(rotated, 2, transform.rz());
		return rotated;
	}

	/**
	 * Rotates the boxes of a shape in 90 degree steps. Done by hand on the boxes
	 * instead of through {@code Shapes.rotate}, whose {@code OctahedralGroup}
	 * constants get renamed between versions.
	 *
	 * @param axis 0 for X, 1 for Y, 2 for Z
	 */
	private static VoxelShape rotateAroundAxis(final VoxelShape shape, final int axis, final float degrees) {
		final int steps = Math.round(degrees / 90.0F) & 3;
		if (steps == 0) {
			return shape;
		}
		VoxelShape rotated = Shapes.empty();
		for (final AABB box : shape.toAabbs()) {
			AABB current = box;
			for (int step = 0; step < steps; step++) {
				current = rotateOnce(current, axis);
			}
			rotated = Shapes.or(rotated, Shapes.create(current));
		}
		return rotated;
	}

	private static AABB rotateOnce(final AABB box, final int axis) {
		return switch (axis) {
			// Around X: y becomes 1 - z, z becomes y.
			case 0 -> new AABB(box.minX, 1.0D - box.maxZ, box.minY, box.maxX, 1.0D - box.minZ, box.maxY);
			// Around Y: x becomes 1 - z, z becomes x.
			case 1 -> new AABB(1.0D - box.maxZ, box.minY, box.minX, 1.0D - box.minZ, box.maxY, box.maxX);
			// Around Z: x becomes 1 - y, y becomes x.
			default -> new AABB(1.0D - box.maxY, box.minX, box.minZ, 1.0D - box.minY, box.maxX, box.maxZ);
		};
	}

	/**
	 * Everything phase 2 needs to register a block, plus the model data phase 3
	 * will bake.
	 */
	public record Result(
			VoxelShape shape,
			VoxelShape collisionShape,
			String geometry,
			Map<String, String> textures,
			Map<String, String> faceAliases,
			float friction,
			float destroyTime,
			int lightEmission,
			Transform transform) {

		public static Builder builder() {
			return new Builder();
		}

		/**
		 * Resolves the texture of a face, following one level of face aliasing and
		 * falling back to the wildcard entry.
		 */
		public String texture(final String face) {
			final String direct = this.textures.get(face);
			if (direct != null) {
				return direct;
			}
			final String alias = this.faceAliases.get(face);
			if (alias != null) {
				final String aliased = this.textures.get(alias);
				if (aliased != null) {
					return aliased;
				}
			}
			return this.textures.get(ALL_FACES);
		}

		public record Transform(
				float tx,
				float ty,
				float tz,
				float sx,
				float sy,
				float sz,
				float rx,
				float ry,
				float rz) {

			public static final Transform IDENTITY =
					new Transform(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F);

			public boolean isIdentityRotation() {
				return this.rx == 0.0F && this.ry == 0.0F && this.rz == 0.0F;
			}
		}

		public static final class Builder {

			private VoxelShape shape;
			private VoxelShape collisionShape;
			private String geometry = FULL_BLOCK_GEOMETRY;
			private final Map<String, String> textures = new LinkedHashMap<>();
			private final Map<String, String> faceAliases = new LinkedHashMap<>();
			private float friction = DEFAULT_FRICTION;
			private float destroyTime = DEFAULT_DESTROY_TIME;
			private int lightEmission;
			private Transform transform = Transform.IDENTITY;

			private Builder() {
			}

			public Builder shape(final VoxelShape shape) {
				this.shape = shape;
				return this;
			}

			public Builder collisionShape(final VoxelShape collisionShape) {
				this.collisionShape = collisionShape;
				return this;
			}

			public Builder geometry(final String geometry) {
				if (geometry != null && !geometry.isBlank()) {
					this.geometry = geometry;
				}
				return this;
			}

			public Builder texture(final String face, final String texture) {
				if (face != null && texture != null) {
					this.textures.put(face, texture);
				}
				return this;
			}

			public Builder faceAlias(final String face, final String target) {
				if (face != null && target != null) {
					this.faceAliases.put(face, target);
				}
				return this;
			}

			public Builder friction(final float friction) {
				// Vanilla clamps friction into this range and asserts otherwise.
				this.friction = Math.max(0.0F, Math.min(1.0F, friction));
				return this;
			}

			public Builder destroyTime(final float destroyTime) {
				this.destroyTime = Math.max(0.0F, destroyTime);
				return this;
			}

			public Builder lightEmission(final int lightEmission) {
				this.lightEmission = Math.max(0, Math.min(15, lightEmission));
				return this;
			}

			public Builder transform(final Transform transform) {
				if (transform != null) {
					this.transform = transform;
				}
				return this;
			}

			public Result build() {
				final VoxelShape resolvedShape = this.shape == null ? Shapes.block() : this.shape;
				final VoxelShape resolvedCollision =
						this.collisionShape == null ? resolvedShape : this.collisionShape;
				return new Result(
						resolvedShape,
						resolvedCollision,
						this.geometry,
						Collections.unmodifiableMap(new LinkedHashMap<>(this.textures)),
						Collections.unmodifiableMap(new LinkedHashMap<>(this.faceAliases)),
						this.friction,
						this.destroyTime,
						this.lightEmission,
						this.transform);
			}
		}
	}
}

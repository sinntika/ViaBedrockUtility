package org.oryxel.viabedrockutility.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block whose shape comes from a Bedrock block definition instead of from
 * code. One instance is created per Bedrock block key while connected, and it is
 * unregistered again on disconnect.
 *
 * <p>The shapes are stored per block rather than per block state because Bedrock
 * permutations are not modelled as vanilla block states yet, so every state of
 * this block is the same shape.
 */
public class CustomBlock extends Block {

	private final VoxelShape shape;
	private final VoxelShape collisionShape;

	public CustomBlock(
			final BlockBehaviour.Properties properties,
			final VoxelShape shape,
			final VoxelShape collisionShape) {
		super(properties);

		this.shape = shape;
		this.collisionShape = collisionShape;
	}

	@Override
	protected VoxelShape getShape(
			final BlockState state,
			final BlockGetter level,
			final BlockPos pos,
			final CollisionContext context) {
		return this.shape;
	}

	@Override
	protected VoxelShape getCollisionShape(
			final BlockState state,
			final BlockGetter level,
			final BlockPos pos,
			final CollisionContext context) {
		return this.collisionShape;
	}

	@Override
	protected VoxelShape getInteractionShape(
			final BlockState state,
			final BlockGetter level,
			final BlockPos pos) {
		return this.shape;
	}

	@Override
	protected VoxelShape getBlockSupportShape(
			final BlockState state,
			final BlockGetter level,
			final BlockPos pos) {
		return this.collisionShape;
	}

	/**
	 * Custom blocks are rendered from a converted Bedrock model, so vanilla must
	 * never treat them as full cubes for face culling or lighting.
	 */
	@Override
	protected VoxelShape getOcclusionShape(final BlockState state) {
		return Shapes.empty();
	}

	@Override
	protected boolean propagatesSkylightDown(final BlockState state) {
		return true;
	}

	@Override
	protected boolean isPathfindable(final BlockState state, final PathComputationType type) {
		return false;
	}
}

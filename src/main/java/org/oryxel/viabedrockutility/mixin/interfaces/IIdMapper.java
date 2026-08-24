package org.oryxel.viabedrockutility.mixin.interfaces;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Block states get their network ids from {@code Block.BLOCK_STATE_REGISTRY},
 * which is an {@code IdMapper} and has no removal path of its own.
 */
public interface IIdMapper {

	void viaBedrockUtility$unregisterState(final BlockState state);
}

package org.oryxel.viabedrockutility.mixin.impl.registry;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.List;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.oryxel.viabedrockutility.mixin.interfaces.IIdMapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(IdMapper.class)
public class IdMapperMixin implements IIdMapper {

	@Shadow
	private int nextId;

	@Shadow
	@Final
	private Reference2IntMap tToId;

	@Shadow
	@Final
	private List idToT;

	@Override
	public void viaBedrockUtility$unregisterState(final BlockState state) {
		final int id = Block.getId(state);
		if (id < 0 || id >= this.idToT.size()) {
			return;
		}

		this.idToT.remove(id);
		this.tToId.remove(state);
		this.nextId = this.idToT.size();
	}
}

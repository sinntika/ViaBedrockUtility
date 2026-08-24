package org.oryxel.viabedrockutility.mixin.impl.registry;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import org.oryxel.viabedrockutility.mixin.interfaces.IHolderReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Holder.Reference.class)
public class HolderReferenceMixin implements IHolderReference {

	@Shadow
	private Set tags;

	@Override
	public void viaBedrockUtility$resolveTags() {
		this.tags = new HashSet<>();
	}
}

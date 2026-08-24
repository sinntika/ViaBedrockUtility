package org.oryxel.viabedrockutility.mixin.impl.registry;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceKey;
import org.oryxel.viabedrockutility.mixin.interfaces.IMappedRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Raw types on purpose: mixin matches shadow fields by descriptor, and the
 * descriptors here are all erased to Object anyway. Adding the generics back
 * would only force unchecked casts at every call site.
 */
@Mixin(MappedRegistry.class)
public class MappedRegistryMixin implements IMappedRegistry {

	@Shadow
	@Final
	private ObjectList byId;

	@Shadow
	@Final
	private Reference2IntMap toId;

	@Shadow
	@Final
	private Map byLocation;

	@Shadow
	@Final
	private Map byKey;

	@Shadow
	@Final
	private Map byValue;

	@Shadow
	@Final
	private Map registrationInfos;

	@Shadow
	private Map unregisteredIntrusiveHolders;

	@Shadow
	private boolean frozen;

	@Override
	public void viaBedrockUtility$unfreeze() {
		this.unregisteredIntrusiveHolders = new IdentityHashMap<>();
		this.frozen = false;
	}

	@Override
	public void viaBedrockUtility$refreeze() {
		this.unregisteredIntrusiveHolders = null;
		this.frozen = true;
	}

	@Override
	public void viaBedrockUtility$unregister(final ResourceKey<?> key) {
		final Object removed = this.byKey.remove(key);
		if (!(removed instanceof Holder.Reference<?> reference)) {
			// Already gone. Unregistering twice has to stay harmless, because the
			// disconnect path can run after a failed registration.
			return;
		}

		this.byLocation.remove(key.identifier());
		this.byValue.remove(reference.value());
		this.byId.remove(reference);
		this.toId.remove(reference.value());
		this.registrationInfos.remove(key);
	}
}

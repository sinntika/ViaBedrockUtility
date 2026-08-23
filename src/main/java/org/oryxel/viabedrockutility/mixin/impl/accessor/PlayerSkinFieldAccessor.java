package org.oryxel.viabedrockutility.mixin.impl.accessor;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(PlayerInfo.class)
public interface PlayerSkinFieldAccessor {
    @Accessor("texturesSupplier")
    @Mutable
    void setPlayerSkin(Supplier<PlayerSkin> playerSkin);
}

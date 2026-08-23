package org.oryxel.viabedrockutility.mixin.impl.accessor;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(PlayerInfo.class)
public interface PlayerSkinFieldAccessor {
    // 1.21.11: PlayerInfo.texturesSupplier is now PlayerInfo.skinLookup.
    @Accessor("skinLookup")
    @Mutable
    void setPlayerSkin(Supplier<PlayerSkin> playerSkin);
}

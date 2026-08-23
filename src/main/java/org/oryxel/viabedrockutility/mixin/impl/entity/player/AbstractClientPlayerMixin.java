package org.oryxel.viabedrockutility.mixin.impl.entity.player;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.World;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin extends Entity {
    @Shadow private PlayerInfo playerListEntry;

    public AbstractClientPlayerMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "getSkinTextures", at = @At(value = "TAIL"), cancellable = true)
    public void injectGetSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        Identifier cape = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedPlayerCapes().get(getUuid());
        if (cape != null) {
            PlayerSkin skin = playerListEntry == null ? DefaultPlayerSkin.getSkinTextures(this.getUUID()) : playerListEntry.getSkinTextures();
            if (!cape.equals(skin.capeTexture())) {
                cir.setReturnValue(new PlayerSkin(
                        skin.texture(),
                        skin.textureUrl(),
                        cape,
                        skin.elytraTexture(),
                        skin.model(),
                        skin.secure()
                ));
            }
        }
    }

}
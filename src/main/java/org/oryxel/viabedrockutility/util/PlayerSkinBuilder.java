package org.oryxel.viabedrockutility.util;

import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;

public final class PlayerSkinBuilder {
    public Identifier texture;
    public String textureUrl;
    public Identifier capeTexture;
    public Identifier elytraTexture;
    public PlayerSkin.Model model;
    public boolean secure;

    public PlayerSkinBuilder(final PlayerSkin base) {
        this.texture = base.texture();
        this.textureUrl = base.textureUrl();
        this.capeTexture = base.capeTexture();
        this.elytraTexture = base.elytraTexture();
        this.model = base.model();
        this.secure = base.secure();
    }

    public PlayerSkin build() {
        return new PlayerSkin(texture, textureUrl, capeTexture, elytraTexture, model, secure);
    }
}
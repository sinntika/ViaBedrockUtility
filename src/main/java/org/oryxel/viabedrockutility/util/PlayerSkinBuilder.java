package org.oryxel.viabedrockutility.util;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

// 1.21.11 replaced the plain identifier/url fields of PlayerSkin with
// ClientAsset.Texture assets. This builder keeps the old field shape so every
// caller stays untouched, and converts back to assets on build().
public final class PlayerSkinBuilder {
    public Identifier texture;
    public String textureUrl;
    public Identifier capeTexture;
    public Identifier elytraTexture;
    public PlayerModelType model;
    public boolean secure;

    public PlayerSkinBuilder(final PlayerSkin base) {
        this.texture = path(base.body());
        this.textureUrl = base.body() instanceof ClientAsset.DownloadedTexture downloaded ? downloaded.url() : null;
        this.capeTexture = path(base.cape());
        this.elytraTexture = path(base.elytra());
        this.model = base.model();
        this.secure = base.secure();
    }

    public PlayerSkin build() {
        return new PlayerSkin(asset(this.texture, this.textureUrl), asset(this.capeTexture, null), asset(this.elytraTexture, null), this.model, this.secure);
    }

    // The texture path is what the game samples, so a dynamically registered
    // texture has to keep the exact identifier it was registered under.
    public static ClientAsset.Texture asset(final Identifier identifier, final String url) {
        if (identifier == null) {
            return null;
        }

        return url == null ? new ClientAsset.ResourceTexture(identifier, identifier) : new ClientAsset.DownloadedTexture(identifier, url);
    }

    public static Identifier path(final ClientAsset.Texture asset) {
        return asset == null ? null : asset.texturePath();
    }
}

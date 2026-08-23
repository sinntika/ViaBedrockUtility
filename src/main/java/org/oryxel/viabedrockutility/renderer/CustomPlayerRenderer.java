package org.oryxel.viabedrockutility.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.Identifier;

public class CustomPlayerRenderer extends PlayerRenderer {
    private final Identifier texture;

    public CustomPlayerRenderer(final EntityRendererProvider.Context ctx, final PlayerModel model, final boolean slim, Identifier texture) {
        super(ctx, slim);

        if (model != null) {
            this.model = model;
        }

        this.texture = texture;
    }

    @Override
    public Identifier getTexture(PlayerRenderState playerEntityRenderState) {
        return this.texture;
    }
}

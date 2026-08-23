package org.oryxel.viabedrockutility.renderer.model;

import lombok.Getter;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.EntityModel;
import org.oryxel.viabedrockutility.renderer.CustomEntityRenderer;

@Getter
public class CustomEntityModel<T extends CustomEntityRenderer.CustomEntityRenderState> extends EntityModel<T> {
    public CustomEntityModel(ModelPart root) {
        super(root);
    }
}

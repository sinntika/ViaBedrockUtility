package org.oryxel.viabedrockutility.mixin.impl.render.feature;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.resources.Identifier;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CapeLayer.class)
public class CapeFeatureRendererMixin {
    // 1.21.11: layers submit geometry instead of rendering it directly.
    @Redirect(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entitySolid(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"),
            require = 0 // Fail safely if other mods overwrite this
    )
    public RenderType solidToTranslucent(final Identifier texture) {
        if (texture.getNamespace().equals(ViaBedrockUtilityFabric.MOD_ID)) {
            // Capes can be translucent in Bedrock
            return RenderTypes.entityTranslucent(texture, true);
        }
        return RenderTypes.entitySolid(texture);
    }
}

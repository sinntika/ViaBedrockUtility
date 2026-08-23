package org.oryxel.viabedrockutility.renderer;

import lombok.Getter;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;
import com.mojang.math.Axis;
import org.oryxel.viabedrockutility.animation.animator.Animator;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.material.data.Material;
import org.oryxel.viabedrockutility.pack.definitions.AnimationDefinitions;
import org.oryxel.viabedrockutility.renderer.model.CustomEntityModel;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class CustomEntityRenderer<T extends Entity> extends EntityRenderer<T, CustomEntityRenderer.CustomEntityRenderState> {
    private final Map<String, Animator> animators = new ConcurrentHashMap<>();

    private final CustomEntityTicker ticker;
    private final List<Model> models;

    public CustomEntityRenderer(final CustomEntityTicker ticker, final List<Model> models, EntityRendererProvider.Context context) {
        super(context);
        this.models = models;
        this.ticker = ticker;
    }

    @Override
    public void render(CustomEntityRenderState state, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        for (Model model : this.models) {
            matrices.push();

            this.setupTransforms(state, matrices);
            matrices.scale(-1.0F, -1.0F, 1.0F);
            matrices.translate(0.0F, -1.501F, 0.0F);
            this.animators.values().forEach(animator -> {
                try {
                    animator.animate(model.model(), state);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            RenderType renderLayer = model.material.info().getVariants().get("skinning_color").build().apply(model.texture);
            if (renderLayer != null) {
                VertexConsumer vertexConsumer = vertexConsumers.getBuffer(renderLayer);
                model.model.render(matrices, vertexConsumer, light, OverlayTexture.packUv(0, 10));
            }

            matrices.pop();
        }
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
        double d = 64.0F * Entity.getRenderDistanceMultiplier();
        return entity.squaredDistanceTo(x, y, z) <= d * d;
    }

    @Override
    public void updateRenderState(T entity, CustomEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.yaw = entity.getYaw(tickDelta);
        state.bodyYaw = entity.getBodyYaw();
        state.bodyPitch = entity.getPitch();
        state.distanceTraveled = entity.distanceTraveled;
    }

    private void setupTransforms(CustomEntityRenderState state, PoseStack matrices) {
        matrices.multiply(Axis.POSITIVE_Y.rotationDegrees(180 - state.yaw));
    }

    @Override
    public CustomEntityRenderState createRenderState() {
        return new CustomEntityRenderState();
    }

    public record Model(String key, String geometry, CustomEntityModel<CustomEntityRenderState> model, Identifier texture, Material material) {
    }

    @Getter
    public static class CustomEntityRenderState extends EntityRenderState {
        private float yaw, bodyYaw, bodyPitch;
        private float distanceTraveled;
    }

    public void reset() {
        this.animators.values().forEach(animator -> this.models.forEach(m -> animator.stop(m.model(), true)));
        this.animators.clear();
    }

    public void play(final AnimationDefinitions.AnimationData data) {
        if (data == null) {
            return;
        }

        this.animators.put(data.animation().getIdentifier(), new Animator(this.ticker, data));
    }
}

package org.oryxel.viabedrockutility.mixin.impl.render;

import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.util.MathUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements IModelPart {
    @Shadow public float originX;
    @Shadow public float originZ;

    @Shadow @Final private Map<String, ModelPart> children;

    @Shadow public abstract Stream<ModelPart> traverse();

    @Shadow public float xScale;
    @Shadow public float yScale;
    @Shadow public float zScale;
    @Unique
    private String name = "";

    @Unique private boolean isVBUModel;
    @Unique private boolean neededOffset;

    @Unique
    private Vector3f pivot = new Vector3f();

    @Unique
    private Vector3f offset = new Vector3f();

    @Unique
    private Vector3f rotation = new Vector3f();
    
    @Unique
    private Vector3f defaultRotation = new Vector3f();

    @Unique
    private boolean alreadySetRotation = false;

    @Inject(method = "applyTransform", at = @At("HEAD"))
    public void render(PoseStack matrices, CallbackInfo ci) {
        // Offset is needed for rotating too!
        matrices.translate(this.offset.x / 16.0F, this.offset.y / 16.0F, this.offset.z / 16.0F);

        matrices.translate(this.pivot.x / 16.0F, this.pivot.y / 16.0F, this.pivot.z / 16.0F);
        matrices.multiply((new Quaternionf()).rotationXYZ(this.rotation.x * MathUtil.DEGREES_TO_RADIANS, this.rotation.y * MathUtil.DEGREES_TO_RADIANS, this.rotation.z * MathUtil.DEGREES_TO_RADIANS));
        matrices.translate(-this.pivot.x / 16.0F, -this.pivot.y / 16.0F, -this.pivot.z / 16.0F);

        matrices.translate(-this.offset.x / 16.0F, -this.offset.y / 16.0F, -this.offset.z / 16.0F);
    }

    @Inject(method = "applyTransform", at = @At("TAIL"))
    public void renderTail(PoseStack matrices, CallbackInfo ci) {
        // Do this after scale since well, this shouldn't be affected by scaling.
        matrices.translate(this.offset.x / 16.0F, this.offset.y / 16.0F, this.offset.z / 16.0F);

        if (!this.isVBUModel || !this.neededOffset) {
            return;
        }

        // Have to do this because of how java pivot point and bedrock pivot point system works, I think? ehhh whatever it works, just don't touch it.
        matrices.translate(-this.originX / 16.0F, 0, -this.originZ / 16.0F);
    }

    @Inject(method = "getChild", at = @At("HEAD"), cancellable = true)
    private void getChild(String name, CallbackInfoReturnable<ModelPart> cir) {
        if (this.isVBUModel) {
            cir.setReturnValue(this.children.getOrDefault(name, new ModelPart(List.of(), Map.of())));
        }
    }

    @Override
    public String viaBedrockUtility$getName() {
        return this.name;
    }

    @Override
    public void viaBedrockUtility$setName(String name) {
        this.name = name;
    }

    @Override
    public void viaBedrockUtility$setNeededOffset(boolean needed) {
        this.neededOffset = needed;
    }

    @Override
    public boolean viaBedrockUtility$isVBUModel() {
        return this.isVBUModel;
    }

    @Override
    public void viaBedrockUtility$setVBUModel() {
        this.isVBUModel = true;
    }

    @Override
    public void viaBedrockUtility$resetEverything() {
        this.traverse().toList().forEach(part -> {
            viaBedrockUtility$setOffset(this.offset);
            viaBedrockUtility$setAngles(this.defaultRotation);
            this.xScale = this.yScale = this.zScale = 1.0F;
        });
    }

    @Override
    public void viaBedrockUtility$setPivot(Vector3f vec3) {
        this.pivot = vec3;
    }

    @Override
    public void viaBedrockUtility$setOffset(Vector3f vec3) {
        this.offset = new Vector3f(vec3.x, -vec3.y, vec3.z);
    }

    @Override
    public void viaBedrockUtility$setAngles(Vector3f vec3) {
        if (!this.alreadySetRotation) {
            this.defaultRotation = new Vector3f(vec3.x, vec3.y, vec3.z);
            this.alreadySetRotation = true;
        }

        this.rotation = new Vector3f(vec3.x, vec3.y, vec3.z);
    }
}

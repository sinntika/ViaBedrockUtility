package org.oryxel.viabedrockutility.animation.vanilla;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.mocha.MoLangEngine;
import team.unnamed.mocha.runtime.Scope;

import java.io.IOException;

// Taken from vanilla Transformation.
public record AnimateTransformation(Target target, VBUKeyFrame[] keyframes) {
    public static class Interpolations {
        public static final Interpolation LINEAR = (scope,dest, delta, keyframes, start, end, scale) -> {
            Vector3f vector3f2 = eval(scope, keyframes[start].target());
            Vector3f vector3f3 = eval(scope, keyframes[end].target());
            return vector3f2.lerp(vector3f3, delta, dest).mul(scale);
        };
        public static final Interpolation CUBIC = (scope, dest, delta, keyframes, start, end, scale) -> {
            Vector3f vector3f2 = eval(scope, keyframes[Math.max(0, start - 1)].target());
            Vector3f vector3f3 = eval(scope, keyframes[start].target());
            Vector3f vector3f4 = eval(scope, keyframes[end].target());
            Vector3f vector3f5 = eval(scope, keyframes[Math.min(keyframes.length - 1, end + 1)].target());
            dest.set(Mth.catmullrom(delta, vector3f2.x(), vector3f3.x(), vector3f4.x(), vector3f5.x()) * scale, Mth.catmullrom(delta, vector3f2.y(), vector3f3.y(), vector3f4.y(), vector3f5.y()) * scale, Mth.catmullrom(delta, vector3f2.z(), vector3f3.z(), vector3f4.z(), vector3f5.z()) * scale);
            return dest;
        };
    }

    private static Vector3f eval(Scope scope, String[] molang3) {
        try {
            return new Vector3f((float) MoLangEngine.eval(scope, molang3[0]).getAsNumber(),
                    (float) MoLangEngine.eval(scope, molang3[1]).getAsNumber(),
                    (float) MoLangEngine.eval(scope, molang3[2]).getAsNumber());
        } catch (IOException e) {
            e.printStackTrace();
            return new Vector3f();
        }
    }

    public static class Targets {
        public static final Target OFFSET = (part, vec3) -> ((IModelPart)((Object)part)).viaBedrockUtility$setOffset(vec3);
        public static final Target ROTATE = (part, vec3) -> ((IModelPart)((Object)part)).viaBedrockUtility$setAngles(vec3);
        public static final Target SCALE = (part, vec3) -> {
            part.xScale = -vec3.x;
            part.yScale = vec3.y;
            part.zScale = vec3.z;
        };
    }

    public interface Target {
        void apply(ModelPart var1, Vector3f var2);
    }

    public interface Interpolation {
        Vector3f apply(Scope scope, Vector3f var1, float var2, VBUKeyFrame[] var3, int var4, int var5, float var6);
    }
}



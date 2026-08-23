package org.oryxel.viabedrockutility.animation.vanilla;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.animation.Animation;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import team.unnamed.mocha.runtime.Scope;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class KeyframeAnimations {
    public static void animate(Scope scope, Model model, VBUAnimation animation, long runningTime, float scale, Vector3f tempVec) {
        float g = KeyframeAnimations.getRunningSeconds(animation, runningTime);
        for (Map.Entry<String, List<AnimateTransformation>> entry : animation.boneAnimations().entrySet()) {
            Optional<ModelPart> optional = getPartByName(model.getParts(), entry.getKey());
            if (optional.isEmpty()) {
                continue;
            }

            final ModelPart part = optional.get();
            List<AnimateTransformation> list = entry.getValue();
            for (AnimateTransformation transformation : list) {
                VBUKeyFrame[] lvs = transformation.keyframes();
                int i = Math.max(0, Mth.binarySearch(0, lvs.length, index -> {
                    if (lvs[index] == null) {
                        return false;
                    }

                    return g <= lvs[index].timestamp();
                }) - 1);
                int j = Math.min(lvs.length - 1, i + 1);
                if (lvs[i] == null || lvs[j] == null) {
                    continue;
                }

                VBUKeyFrame lv = lvs[i];
                VBUKeyFrame lv2 = lvs[j];
                float h = g - lv.timestamp();
                float k = j != i ? Mth.clamp(h / (lv2.timestamp() - lv.timestamp()), 0.0f, 1.0f) : 1F;

                lv2.interpolation().apply(scope, tempVec, k, lvs, i, j, scale);
                transformation.target().apply(part, tempVec);
            }
        }
    }

    private static float getRunningSeconds(VBUAnimation animation, long runningTime) {
        float f = (float)runningTime / 1000.0f;
        return animation.looping() ? f % animation.lengthInSeconds() : f;
    }

    public static float getRunningSeconds(Animation animation, VBUAnimation vbu, long runningTime) {
        float f = (float)runningTime / 1000.0f;
        return animation.getLoop().getValue().equals(true) ? f % vbu.lengthInSeconds() : f;
    }

    private static Optional<ModelPart> getPartByName(List<ModelPart> parts, String name) {
        for (ModelPart part : parts) {
            if (((IModelPart)((Object)part)).viaBedrockUtility$getName().equals(name) && part.isEmpty()) {
                return Optional.of(part);
            }
        }

        return Optional.empty();
    }
}
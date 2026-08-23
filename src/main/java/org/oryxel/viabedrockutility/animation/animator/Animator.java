package org.oryxel.viabedrockutility.animation.animator;

import lombok.Setter;
import net.minecraft.client.model.Model;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.animation.vanilla.KeyframeAnimations;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.mocha.MoLangEngine;
import org.oryxel.viabedrockutility.pack.definitions.AnimationDefinitions;
import org.oryxel.viabedrockutility.renderer.CustomEntityRenderer;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Animator {
    private final CustomEntityTicker ticker;
    private final AnimationDefinitions.AnimationData data;

    private long animationStartMS;

    private boolean donePlaying, started, firstPlay;

    private final Vector3f TEMP_VEC = new Vector3f();

    @Setter
    private Scope baseScope;

    public Animator(CustomEntityTicker ticker, AnimationDefinitions.AnimationData data) {
        this.ticker = ticker;
        this.data = data;

        this.animationStartMS = System.currentTimeMillis();
        this.firstPlay = true;
    }

    public void animate(Model model, CustomEntityRenderer.CustomEntityRenderState state) throws IOException {
        if (this.donePlaying) {
            if (this.data.animation().getLoop().getValue().equals(true)) {
                this.donePlaying = false;
            } else {
                return;
            }
        }

        final Scope scope = this.baseScope.copy();

        final MutableObjectBinding queryBinding = new MutableObjectBinding();
        queryBinding.setAllFrom((MutableObjectBinding) this.baseScope.get("query"));

        queryBinding.set("modified_distance_moved", Value.of(state.getDistanceTraveled()));
        queryBinding.set("modified_move_speed", Value.of(0.7F)); // We don't know this value I think? not yet.

        queryBinding.set("body_y_rotation", Value.of(state.getBodyYaw()));
        queryBinding.set("body_x_rotation", Value.of(state.getBodyPitch()));

        scope.set("q", queryBinding);
        scope.set("query", queryBinding);

        if (!this.started) {
            boolean skipThisTick = true;

            float seconds = (System.currentTimeMillis() - this.animationStartMS) / 1000F;
            double requiredLaunchTime = MoLangEngine.eval(scope, this.firstPlay ? this.data.animation().getStartDelay() : this.data.animation().getLoopDelay()).getAsNumber();
            if (seconds >= requiredLaunchTime) {
                skipThisTick = false;
                this.started = true;
                this.firstPlay = false;

                this.animationStartMS = System.currentTimeMillis();
                this.ticker.runPreAnimationTask();
            }

            if (this.started && this.data.animation().isResetBeforePlay()) {
                ((IModelPart)((Object)model.getRootPart())).viaBedrockUtility$resetEverything();
                this.TEMP_VEC.set(0);
            }

            if (skipThisTick) {
                return;
            }
        }

        float runningTime = KeyframeAnimations.getRunningSeconds(data.animation(), data.compiled(), System.currentTimeMillis() - this.animationStartMS);

        queryBinding.set("anim_time", Value.of(runningTime));
        queryBinding.set("life_time", Value.of(runningTime));

        KeyframeAnimations.animate(scope, model, data.compiled(), System.currentTimeMillis() - this.animationStartMS, 1, TEMP_VEC);

        float runningTimeWithoutLoop = (System.currentTimeMillis() - this.animationStartMS) / 1000F;
        this.tickTimeline(runningTimeWithoutLoop);

        if (runningTimeWithoutLoop >= data.compiled().lengthInSeconds()) {
            System.out.println("Reset since animation length: " + data.compiled().lengthInSeconds());
            this.stop(model);
        }
    }

    private void tickTimeline(float runningTime) {
        final Queue<Map.Entry<Float, List<String>>> entries = new ConcurrentLinkedQueue<>();
        this.data.animation().getTimeline().entrySet().forEach(entries::add);

        Map.Entry<Float, List<String>> entry;
        while ((entry = entries.peek()) != null) {
            entries.poll();

            float timestamp = entry.getKey();
            if (runningTime < timestamp) {
                continue;
            }

            if (entry.getValue().isEmpty()) {
                continue;
            }

            // Equal to this timestamp and smaller than the next timestamp.
            if ((entries.peek() == null || entries.peek().getKey() > runningTime) && Math.abs(timestamp - runningTime) < 0.005F) {
                this.ticker.handleAnimationTimeline(entry.getValue());
                System.out.println(runningTime + "," + timestamp);
            }
        }
    }

    public void stop(Model model) {
        this.stop(model, false);
    }

    public void stop(Model model, boolean forcefully) {
        if (this.data.animation().getLoop().getValue().equals(false) || forcefully) {
            ((IModelPart)((Object)model.getRootPart())).viaBedrockUtility$resetEverything();
        }

        this.animationStartMS = System.currentTimeMillis();

        this.donePlaying = true;
        this.started = false;
    }
}

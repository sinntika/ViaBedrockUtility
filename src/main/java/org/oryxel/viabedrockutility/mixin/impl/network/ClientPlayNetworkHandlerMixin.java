package org.oryxel.viabedrockutility.mixin.impl.network;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    // Have to do this since you can't run custom command when playing in a server.
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void injectSendMessage(String content, CallbackInfo ci) {
        if (!ViaBedrockUtility.DEBUGGING || !content.startsWith("$animate")) {
            return;
        }

        ci.cancel();

        if (content.length() < "$animate ".length()) {
            return;
        }

        String[] split = content.substring("$animate ".length()).split(" ");
        if (split.length < 1) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            System.out.println("No target!");
            return;
        }

        if (ViaBedrockUtility.getInstance().getPackManager() == null) {
            return;
        }

        final UUID uuid = ((EntityHitResult)client.crosshairTarget).getEntity().getUUID();
        if (!ViaBedrockUtility.getInstance().getPayloadHandler().getCachedCustomEntities().containsKey(uuid)) {
            System.out.println("couldn't find");
            return;
        }

        CustomEntityTicker cache = ViaBedrockUtility.getInstance().getPayloadHandler().getCachedCustomEntities().get(uuid);
        if (split[0].equals("reset")) {
            cache.getRenderer().reset();
        } else if (split[0].equals("test") && split.length == 3) {
            cache.getRenderer().play(ViaBedrockUtility.getInstance().getPackManager().getAnimationDefinitions().getAnimations().get(split[2]));
        }
    }
}

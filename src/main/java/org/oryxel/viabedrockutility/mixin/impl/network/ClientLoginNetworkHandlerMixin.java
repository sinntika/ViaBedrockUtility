package org.oryxel.viabedrockutility.mixin.impl.network;

import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.resources.Identifier;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
@Mixin(ClientHandshakePacketListenerImpl.class)
public class ClientLoginNetworkHandlerMixin {
    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "onSuccess", at = @At("RETURN"))
    public void onSuccess(ClientboundGameProfilePacket packet, CallbackInfo ci) {
        // Let ViaBedrock know that we want to receive full bedrock pack, also we have to do this to send it early.
        // Also use a different identifier to avoiding sending the same one twice.
        ViaBedrockUtility.getInstance().setViaBedrockPresent(false);
        this.connection.send(new ServerboundCustomPayloadPacket(new RegistrationPayload(RegistrationPayload.REGISTER, List.of(Identifier.fromNamespaceAndPath(ViaBedrockUtilityFabric.MOD_ID, "confirm")))));
    }
}

package org.oryxel.viabedrockutility;

import com.mojang.brigadier.Command;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.commands.Commands;
import org.oryxel.viabedrockutility.mappings.BedrockMappings;
import org.oryxel.viabedrockutility.material.VanillaMaterials;
import org.oryxel.viabedrockutility.pack.PackManager;
import org.oryxel.viabedrockutility.payload.BasePayload;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;

@Getter
@Setter
public class ViaBedrockUtility {
    public static boolean DEBUGGING = true;

    @Getter
    private static final ViaBedrockUtility instance = new ViaBedrockUtility();

    private ViaBedrockUtility() {}

    private CustomEntityPayloadHandler payloadHandler;
    private PackManager packManager;
    private boolean viaBedrockPresent;

    public void init() {
        VanillaMaterials.init();
        BedrockMappings.load();

        // Register custom payload.
        this.payloadHandler = new CustomEntityPayloadHandler();
        // Fabric API renamed these to say which direction they travel in.
        PayloadTypeRegistry.clientboundConfiguration().register(BasePayload.ID, BasePayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BasePayload.ID, BasePayload.STREAM_CODEC);

        // ViaBedrock already talks to us while the client is still in the configuration phase
        // (CONFIRM, skin information, capes), so a play-only receiver silently drops those packets
        // and the game logs "Unknown custom packet payload: viabedrockutility:data".
        // Both phases share the same handler; PayloadHandler is phase agnostic.
        ClientConfigurationNetworking.registerGlobalReceiver(BasePayload.ID, (payload, context) -> payload.handle(this.payloadHandler));
        ClientPlayNetworking.registerGlobalReceiver(BasePayload.ID, (payload, context) -> payload.handle(this.payloadHandler));

        // To enable debugging in order to use animate test thingy (look at ClientPacketListener)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("vbudebug").executes(context -> {
                            DEBUGGING = !DEBUGGING;
                            System.out.println("Debugging status: " + DEBUGGING);
                            return Command.SINGLE_SUCCESS;
                        }
                )));
    }
}

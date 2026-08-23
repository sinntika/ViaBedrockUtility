package org.oryxel.viabedrockutility.mixin.impl.pack;

import net.minecraft.client.resource.server.ReloadScheduler;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.pack.PackManager;
import org.oryxel.viabedrockutility.pack.content.Content;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Mixin(DownloadedPackSource.class)
public class ServerResourcePackLoaderMixin {
    @Inject(method = "toProfiles", at = @At("HEAD"))
    private void toProfiles(List<ReloadScheduler.PackInfo> packs, CallbackInfoReturnable<List<Pack>> cir) {
        if (!ViaBedrockUtility.getInstance().isViaBedrockPresent()) {
            return;
        }

        final List<Content> contents = new ArrayList<>();
        packs.stream().map(ReloadScheduler.PackInfo::path).forEach(pack -> {
            try {
                final Content content = new Content(Files.readAllBytes(pack));
                for (final String path : content.getFilesDeep("bedrock/", ".mcpack")) {
                    contents.add(new Content(content.get(path)));
                }
            } catch (IOException e) {
                ViaBedrockUtilityFabric.LOGGER.warn("Failed to read pack {}", pack);
            }
        });

        ViaBedrockUtility.getInstance().setPackManager(new PackManager(contents));
    }
}

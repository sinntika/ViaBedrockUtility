package org.oryxel.viabedrockutility.payload;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.model.player.PlayerModel;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.resources.Identifier;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.mixin.impl.accessor.PlayerSkinFieldAccessor;
import org.oryxel.viabedrockutility.pack.PackManager;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.oryxel.viabedrockutility.payload.impl.entity.ModelRequestPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.BaseSkinPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.CapeDataPayload;
import org.oryxel.viabedrockutility.payload.impl.skin.SkinDataPayload;
import org.oryxel.viabedrockutility.renderer.CustomPlayerRenderer;
import org.oryxel.viabedrockutility.util.GeometryUtil;

import org.oryxel.viabedrockutility.util.ImageUtil;
import org.oryxel.viabedrockutility.util.PlayerSkinBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class PayloadHandler {
    protected final Map<UUID, CustomEntityTicker> cachedCustomEntities = new ConcurrentHashMap<>();
    protected final Map<UUID, EntityRenderer<?, ?>> cachedPlayerRenderers = new ConcurrentHashMap<>();
    protected final Map<UUID, Identifier> cachedPlayerCapes = new ConcurrentHashMap<>();
    protected final Map<UUID, SkinInfo> cachedSkinInfo = new ConcurrentHashMap<>();
    protected PackManager packManager;

    public void handle(final BasePayload payload) {
        if (this.packManager != ViaBedrockUtility.getInstance().getPackManager()) {
            this.packManager = ViaBedrockUtility.getInstance().getPackManager();
        }

        if (this.packManager == null) {
            return;
        }

        if (payload instanceof ModelRequestPayload modelRequest) {
            this.handle(modelRequest);
        } else if (payload instanceof BaseSkinPayload baseSkin) {
            this.cachedSkinInfo.put(baseSkin.getPlayerUuid(), new SkinInfo(baseSkin.getGeometry(), baseSkin.getResourcePatch(), baseSkin.getSkinWidth(), baseSkin.getSkinHeight(), baseSkin.getChunkCount()));
        } else if (payload instanceof SkinDataPayload skinData) {
            this.handle(skinData);
        } else if (payload instanceof CapeDataPayload capePayload) {
            this.handle(capePayload);
        }
    }

    public void handle(final ModelRequestPayload payload) {}

    public void handle(final CapeDataPayload payload) {
        final NativeImage capeImage = ImageUtil.toNativeImage(payload.getCapeData(), payload.getWidth(), payload.getHeight());
        if (capeImage == null) {
            return;
        }

        final Minecraft client = Minecraft.getInstance();
        client.getTextureManager().register(payload.getIdentifier(), new DynamicTexture(() -> payload.getIdentifier().toString() + capeImage.hashCode() , capeImage));

        if (client.getConnection() == null) {
            return;
        }

        this.cachedPlayerCapes.put(payload.getPlayerUuid(), payload.getIdentifier());

        // It's ok to use this here, the reason we don't use this for player geometry because there can be fake entity.
        // But most fake entity don't have cape so we should be fine!
        final PlayerInfo entry = client.getConnection().getPlayerInfo(payload.getPlayerUuid());
        if (entry == null) {
            return;
        }

        final PlayerSkinBuilder builder = new PlayerSkinBuilder(entry.getSkin());
        builder.capeTexture = payload.getIdentifier();

        ((PlayerSkinFieldAccessor)entry).setPlayerSkin(builder::build);
    }

    private static final List<String> HARDCODED_GEOMETRY_IDENTIFIERS = List.of(
            "geometry.humanoid.custom", "geometry.humanoid.customSlim");

    public void handle(final SkinDataPayload payload) {
        final SkinInfo info = this.cachedSkinInfo.get(payload.getPlayerUuid());
        if (info == null) {
            ViaBedrockUtilityFabric.LOGGER.error("Skin info was null!");
            return;
        }

        info.setData(payload.getSkinData(), payload.getChunkPosition());
        ViaBedrockUtilityFabric.LOGGER.info("Skin chunk {} received for {}", payload.getChunkPosition(), payload.getPlayerUuid());

        if (info.isComplete()) {
            // All skin data has been received
            this.cachedSkinInfo.remove(payload.getPlayerUuid());
        } else {
            return;
        }

        final NativeImage skinImage = ImageUtil.toNativeImage(info.getData(), info.getWidth(), info.getHeight());
        if (skinImage == null) {
            return;
        }

        final Minecraft client = Minecraft.getInstance();

        final Identifier identifier = Identifier.fromNamespaceAndPath(ViaBedrockUtilityFabric.MOD_ID, payload.getPlayerUuid().toString());
        client.getTextureManager().register(identifier, new DynamicTexture(() -> identifier.toString() + skinImage.hashCode(), skinImage));

        if (client.getConnection() != null) {
            final PlayerInfo entry = client.getConnection().getPlayerInfo(payload.getPlayerUuid());

            // If we can still get player list entry then use this to set skin still a good idea!
            if (entry != null) {
                final PlayerSkinBuilder builder = new PlayerSkinBuilder(entry.getSkin());
                builder.texture = identifier;

                ((PlayerSkinFieldAccessor)entry).setPlayerSkin(builder::build);
            }
        }

        // Ex: skinResourcePatch={"geometry":{"default":"geometry.humanoid.custom.1742391406.1704"}}
        String requiredGeometry = null;
        try {
            requiredGeometry = JsonParser.parseString(info.getResourcePatch()).getAsJsonObject()
                    .getAsJsonObject("geometry").get("default").getAsString();
        } catch (Exception ignored) {}

        // Hardcoded I know...
        boolean slim = requiredGeometry != null && "geometry.humanoid.customSlim".contains(requiredGeometry);

        PlayerModel model = null;
        if (!info.getGeometryRaw().isEmpty()) {
            final List<BedrockGeometryModel> geometries;
            try {
                final JsonObject object = JsonParser.parseString(info.getGeometryRaw()).getAsJsonObject();
                geometries = BedrockGeometryModel.fromJson(object);

                if (!geometries.isEmpty()) {
                    BedrockGeometryModel geometry = geometries.getFirst();
                    if (requiredGeometry != null) {
                        for (final BedrockGeometryModel geometryModel : geometries) {
                            if (geometryModel.getIdentifier().equals(requiredGeometry)) {
                                geometry = geometryModel;
                                break;
                            }
                        }
                    }

                    model = (PlayerModel) GeometryUtil.buildModel(geometry, true, slim);
                }
            } catch (final Exception ignored) {
            }
        }

        if (model == null) {
            if (requiredGeometry == null) {
                return;
            }

            boolean found = false;

            // HARDCODED_GEOMETRY_IDENTIFIERS.contains(requiredGeometry) won't work which is weird.
            // By the way for the same reason requiredGeometry.equals(identifier) won't work either but identifier.equals(requiredGeometry) works!
            for (final String i : HARDCODED_GEOMETRY_IDENTIFIERS) {
                if (i.equals(requiredGeometry)) {
                    requiredGeometry = i;
                    found = true;
                    break;
                }
            }

            if (!found) {
                return;
            }
        }

        if (model == null) {
            // This is likely a classic skin with hardcoded identifier! TODO: 128x128
            model = new PlayerModel(PlayerModel.createMesh(CubeDeformation.NONE, slim).getRoot().bake(64, 64), slim);
        }

        final EntityRendererProvider.Context entityContext = new EntityRendererProvider.Context(client.getEntityRenderDispatcher(),
                client.getItemModelResolver(), client.getMapRenderer(), client.getBlockRenderer(),
                client.getResourceManager(), client.getEntityModels(), new EquipmentAssetManager(), client.getAtlasManager(), client.font, client.playerSkinRenderCache());
        this.cachedPlayerRenderers.put(payload.getPlayerUuid(), new CustomPlayerRenderer(entityContext, model, slim, identifier));

        if (client.getConnection() == null) {
            return;
        }

        final PlayerInfo entry = client.getConnection().getPlayerInfo(payload.getPlayerUuid());

        // Do this once again for emmmm the slim or wide model.
        if (entry != null) {
            final PlayerSkinBuilder builder = new PlayerSkinBuilder(entry.getSkin());
            builder.texture = identifier;
            builder.model = slim ? PlayerModelType.SLIM : PlayerModelType.WIDE;

            ((PlayerSkinFieldAccessor)entry).setPlayerSkin(builder::build);
        }
    }

    @Getter
    public static class SkinInfo {
        private final String geometryRaw, resourcePatch;
        private final int width, height;
        private final byte[][] skinData;

        public SkinInfo(String geometryRaw, String resourcePatch, int width, int height, int chunkCount) {
            this.geometryRaw = geometryRaw;
            this.resourcePatch = resourcePatch;
            this.skinData = new byte[chunkCount][];
            this.width = width;
            this.height = height;
        }
        /**
         * Should the skin data be sent to us through multiple plugin messages, assemble it.
         */
        public byte[] getData() {
            if (skinData.length == 1) {
                // No concatenation needed
                return skinData[0];
            }

            int totalLength = 0;
            for (byte[] data : skinData) {
                totalLength += data.length;
            }
            byte[] totalData = new byte[totalLength];
            int currentIndex = 0;
            for (byte[] currentData : skinData) {
                // Copy all arrays to one array
                System.arraycopy(currentData, 0, totalData, currentIndex, currentData.length);
                currentIndex += currentData.length;
            }
            return totalData;
        }

        public void setData(byte[] data, int chunk) {
            this.skinData[chunk] = data;
        }

        public boolean isComplete() {
            for (byte[] data : skinData) {
                if (data == null) {
                    return false;
                }
            }
            return true;
        }
    }
}

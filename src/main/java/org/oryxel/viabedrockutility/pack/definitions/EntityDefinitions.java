// This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock - Copyright 2025 - 2026
package org.oryxel.viabedrockutility.pack.definitions;

import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import org.cube.converter.data.bedrock.BedrockEntityData;
import org.cube.converter.parser.bedrock.data.impl.BedrockEntityParser;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.pack.PackManager;
import org.oryxel.viabedrockutility.pack.content.Content;

import java.util.HashMap;
import java.util.Map;

// https://wiki.bedrock.dev/entities/entity-intro-rp.html
@Getter
public class EntityDefinitions {
    private final Map<String, EntityDefinition> entities = new HashMap<>();

    public EntityDefinitions(final PackManager packManager) {
        for (final Content content : packManager.getPacks()) {
            for (final String entityPath : content.getFilesDeep("entity/", ".json")) {
                try {
                    final BedrockEntityData entityData = BedrockEntityParser.parse(content.getString(entityPath));
                    final ResourceLocation identifier = ResourceLocation.fromNamespaceAndPath(entityData.getIdentifier());
                    this.entities.put(identifier.toString(), new EntityDefinition(identifier, entityData));
                } catch (Throwable e) {
                    ViaBedrockUtilityFabric.LOGGER.warn("Failed to parse entity definition {}", entityPath);
                }
            }
        }
    }

    public record EntityDefinition(ResourceLocation identifier, BedrockEntityData entityData) {
    }
}

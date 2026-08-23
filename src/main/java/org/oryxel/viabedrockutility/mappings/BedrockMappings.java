package org.oryxel.viabedrockutility.mappings;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.oryxel.viabedrockutility.enums.bedrock.ActorFlags;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class BedrockMappings {
    // These files are plain JSON trees, so this no longer borrows CubeConverter's
    // GsonUtil: whichever copy of that library wins at runtime decides what its
    // helpers look like, and the mod should not care.
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = Logger.getLogger("ViaBedrockUtility");

    @Getter
    private static Map<ActorFlags, String> bedrockEntityFlagMoLangQueries;

    public static void load() {
        final JsonObject entityFlagMoLangQueryMappingsJson = readJson("bedrock/entity_flag_molang_query_mappings.json");
        bedrockEntityFlagMoLangQueries = new EnumMap<>(ActorFlags.class);
        final Set<ActorFlags> unmappedEntityFlags = EnumSet.noneOf(ActorFlags.class);
        final List<String> unknownEntityFlags = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : entityFlagMoLangQueryMappingsJson.entrySet()) {
            final ActorFlags entityFlag;
            try {
                entityFlag = ActorFlags.valueOf(entry.getKey());
            } catch (IllegalArgumentException e) {
                // The mapping file names a flag this build's generated enum does not have.
                unknownEntityFlags.add(entry.getKey());
                continue;
            }
            if (entry.getValue().isJsonNull()) {
                unmappedEntityFlags.add(entityFlag);
                continue;
            }
            bedrockEntityFlagMoLangQueries.put(entityFlag, entry.getValue().getAsString());
        }

        // ActorFlags is generated from Mojang's protocol docs, so a docs bump can introduce
        // a flag the mapping file has never heard of. Treat that as "this flag has no MoLang
        // query" and say so once, rather than refusing to let the game start.
        final List<String> missingEntityFlags = new ArrayList<>();
        for (ActorFlags entityFlag : ActorFlags.values()) {
            if (!bedrockEntityFlagMoLangQueries.containsKey(entityFlag) && !unmappedEntityFlags.contains(entityFlag)) {
                missingEntityFlags.add(entityFlag.name());
            }
        }
        if (!missingEntityFlags.isEmpty()) {
            LOGGER.warning("No bedrock MoLang query mapping for " + String.join(", ", missingEntityFlags) + ", treating them as unmapped");
        }
        if (!unknownEntityFlags.isEmpty()) {
            LOGGER.warning("Unknown bedrock actor flags in the MoLang query mapping file: " + String.join(", ", unknownEntityFlags));
        }
    }

    private static JsonObject readJson(String file) {
        return readJson(file, JsonObject.class);
    }

    private static  <T> T readJson(String file, final Class<T> classOfT) {
        file = "assets/viabedrockutility/" + file;
        try (final InputStream inputStream = BedrockMappings.class.getClassLoader().getResourceAsStream(file)) {
            if (inputStream == null) {
                return null;
            }

            return GSON.fromJson(new InputStreamReader(inputStream), classOfT);
        } catch (IOException e) {
            return null;
        }
    }
}

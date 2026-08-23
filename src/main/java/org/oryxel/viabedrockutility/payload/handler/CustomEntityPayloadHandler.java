package org.oryxel.viabedrockutility.payload.handler;

import lombok.Getter;
import org.oryxel.viabedrockutility.entity.CustomEntityTicker;
import org.oryxel.viabedrockutility.material.VanillaMaterials;
import org.oryxel.viabedrockutility.material.data.Material;
import org.oryxel.viabedrockutility.pack.definitions.EntityDefinitions;
import org.oryxel.viabedrockutility.payload.PayloadHandler;
import org.oryxel.viabedrockutility.payload.impl.entity.ModelRequestPayload;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.standard.MochaMath;

@Getter
public class CustomEntityPayloadHandler extends PayloadHandler {
    public static final Scope BASE_SCOPE = Scope.create();
    public static final Material DEFAULT_MATERIAL;

    static {
        //noinspection UnstableApiUsage
        BASE_SCOPE.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
        BASE_SCOPE.readOnly(true);

        DEFAULT_MATERIAL = VanillaMaterials.getMaterial("entity");
    }

    @Override
    public void handle(ModelRequestPayload payload) {
        if (!this.packManager.getEntityDefinitions().getEntities().containsKey(payload.getIdentifier())) {
            return;
        }

        final EntityDefinitions.EntityDefinition definition = this.packManager.getEntityDefinitions().getEntities().get(payload.getIdentifier());

        CustomEntityTicker ticker = this.cachedCustomEntities.get(payload.getUuid());
        if (ticker == null) {
            ticker = new CustomEntityTicker(definition);
            this.cachedCustomEntities.put(payload.getUuid(), ticker);
        }

        ticker.setEntityFlags(payload.getEntityData().flags());
        ticker.setVariant(payload.getEntityData().variant());
        ticker.setMarkVariant(payload.getEntityData().mark_variant());

        ticker.update();
    }
}

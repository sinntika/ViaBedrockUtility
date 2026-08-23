package org.oryxel.viabedrockutility.entity;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.resources.Identifier;
import org.cube.converter.data.bedrock.BedrockEntityData;
import org.cube.converter.data.bedrock.controller.BedrockRenderController;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.enums.bedrock.ActorFlags;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.mappings.BedrockMappings;
import org.oryxel.viabedrockutility.material.data.Material;
import org.oryxel.viabedrockutility.mocha.MoLangEngine;
import org.oryxel.viabedrockutility.pack.PackManager;
import org.oryxel.viabedrockutility.pack.definitions.EntityDefinitions;
import org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler;
import org.oryxel.viabedrockutility.renderer.CustomEntityRenderer;
import org.oryxel.viabedrockutility.renderer.model.CustomEntityModel;
import org.oryxel.viabedrockutility.util.GeometryUtil;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.oryxel.viabedrockutility.payload.handler.CustomEntityPayloadHandler.*;

public class CustomEntityTicker {
    @Getter
    private final Scope entityScope = BASE_SCOPE.copy();

    @Setter
    private Set<ActorFlags> entityFlags = EnumSet.noneOf(ActorFlags.class);

    private final CustomEntityPayloadHandler payloadHandler = ViaBedrockUtility.getInstance().getPayloadHandler();
    private final PackManager packManager = ViaBedrockUtility.getInstance().getPackManager();

    private final EntityDefinitions.EntityDefinition entityDefinition;
    private final Map<String, String> inverseGeometryMap = new HashMap<>();
    private final Map<String, String> inverseTextureMap = new HashMap<>();
    private final Map<String, String> inverseMaterialMap = new HashMap<>();

    private final Set<String> availableModels = new HashSet<>();
    private final List<EvaluatedModel> models = new ArrayList<>();

    @Getter
    private final CustomEntityRenderer<?> renderer;

    @Setter
    private Integer variant, markVariant;

    private boolean hasPlayInitAnimation;

    public CustomEntityTicker(final EntityDefinitions.EntityDefinition entityDefinition) {
        final Minecraft client = Minecraft.getInstance();
        final EntityRendererProvider.Context context = new EntityRendererProvider.Context(client.getEntityRenderDispatcher(),
                client.getItemModelManager(), client.getMapRenderer(), client.getBlockRenderManager(),
                client.getResourceManager(), client.getLoadedEntityModels(), new EquipmentAssetManager(), client.textRenderer);
        this.renderer = new CustomEntityRenderer<>(this, new CopyOnWriteArrayList<>(), context);

        this.entityDefinition = entityDefinition;

        final MutableObjectBinding variableBinding = new MutableObjectBinding();
        this.entityScope.set("variable", variableBinding);
        this.entityScope.set("v", variableBinding);
        try {
            for (String initExpression : this.entityDefinition.entityData().getScripts().initialize()) {
                MoLangEngine.eval(this.entityScope, initExpression);
            }
        } catch (Throwable e) {
            ViaBedrockUtilityFabric.LOGGER.warn("Failed to initialize custom entity variables", e);
        }

        final MutableObjectBinding geometryBinding = new MutableObjectBinding();
        for (Map.Entry<String, String> entry : entityDefinition.entityData().getGeometries().entrySet()) {
            geometryBinding.set(entry.getKey(), Value.of(entry.getValue()));
            this.inverseGeometryMap.putIfAbsent(entry.getValue(), entry.getKey());
        }
        geometryBinding.block();
        this.entityScope.set("geometry", geometryBinding);

        final MutableObjectBinding textureBinding = new MutableObjectBinding();
        for (Map.Entry<String, String> entry : entityDefinition.entityData().getTextures().entrySet()) {
            textureBinding.set(entry.getKey(), Value.of(entry.getValue()));
            this.inverseTextureMap.putIfAbsent(entry.getValue(), entry.getKey());
        }
        textureBinding.block();
        this.entityScope.set("texture", textureBinding);

        final MutableObjectBinding materialBinding = new MutableObjectBinding();
        for (Map.Entry<String, String> entry : entityDefinition.entityData().getMaterials().entrySet()) {
            materialBinding.set(entry.getKey(), Value.of(entry.getValue()));
            this.inverseMaterialMap.putIfAbsent(entry.getValue(), entry.getKey());
        }
        materialBinding.block();
        this.entityScope.set("material", materialBinding);

        this.entityScope.readOnly(true);
    }

    public void handleAnimationTimeline(List<String> expressions) {
        try {
            for (String expression : expressions) {
                MoLangEngine.eval(this.entityScope, expression);
            }
        } catch (Throwable e) {
            ViaBedrockUtilityFabric.LOGGER.warn("Failed to initialize custom entity pre-animation variables", e);
        }

        this.update();
    }

    public void runPreAnimationTask() {
        try {
            for (String initExpression : this.entityDefinition.entityData().getScripts().pre_animation()) {
                MoLangEngine.eval(this.entityScope, initExpression);
            }
        } catch (Throwable e) {
            ViaBedrockUtilityFabric.LOGGER.warn("Failed to initialize custom entity pre-animation variables", e);
        }
    }

    public void update() {
        final Scope executionScope = this.entityScope.copy();
        final MutableObjectBinding queryBinding = new MutableObjectBinding();
        if (this.variant != null) {
            queryBinding.set("variant", Value.of(this.variant));
        }
        if (this.markVariant != null) {
            queryBinding.set("mark_variant", Value.of(this.markVariant));
        }

        final Set<ActorFlags> entityFlags = this.entityFlags();
        for (Map.Entry<ActorFlags, String> entry : BedrockMappings.getBedrockEntityFlagMoLangQueries().entrySet()) {
            if (entityFlags.contains(entry.getKey())) {
                queryBinding.set(entry.getValue(), Value.of(true));
            }
        }
        if (entityFlags.contains(ActorFlags.ONFIRE)) { // "on fire" flag has two names in MoLang
            queryBinding.set("is_onfire", Value.of(true));
        }

        queryBinding.block();
        executionScope.set("query", queryBinding);
        executionScope.set("q", queryBinding);

        if (!evaluateRenderControllerChange(executionScope)) {
            this.renderer.getAnimators().values().forEach(animator -> animator.setBaseScope(executionScope.copy()));
            return;
        }

        final Set<String> old = new HashSet<>(this.availableModels);
        this.availableModels.clear();
        for (EvaluatedModel model : this.models) {
            final Identifier texture = Identifier.fromNamespaceAndPath(model.textureValue().toLowerCase(Locale.ROOT));

            BedrockGeometryModel geometry = this.packManager.getModelDefinitions().getEntityModels().get(model.geometryValue());
            if (geometry == null) {
                continue;
            }

            if (old.contains(model.key())) {
                this.availableModels.add(model.key());
                continue;
            }

            final CustomEntityModel<CustomEntityRenderer.CustomEntityRenderState> cModel = (CustomEntityModel<CustomEntityRenderer.CustomEntityRenderState>) GeometryUtil.buildModel(geometry, false, false);

            this.renderer.getModels().add(new CustomEntityRenderer.Model(model.key(), model.geometryValue(), cModel, texture, evalMaterial(executionScope, model.controller())));
            this.availableModels.add(model.key());
        }
        this.renderer.getModels().removeIf(model -> !this.availableModels.contains(model.key()));

        if (!this.hasPlayInitAnimation) {
            this.entityDefinition.entityData().getScripts().animates().forEach(animate -> {
                if (!animate.expression().isBlank()) {
                    try {
                        final Value conditionResult = MoLangEngine.eval(executionScope, animate.expression());
                        if (!conditionResult.getAsBoolean()) {
                            return;
                        }
                    } catch (Throwable e) {
                        ViaBedrockUtilityFabric.LOGGER.warn("Failed to evaluate start animation request condition", e);
                        return;
                    }
                }

                try {
                    this.renderer.play(this.packManager.getAnimationDefinitions().getAnimations().get(this.entityDefinition.entityData().getAnimations().get(animate.name())));
                } catch (Throwable e) {
                    ViaBedrockUtilityFabric.LOGGER.warn("Failed to play default start animation.", e);
                }
            });
            this.hasPlayInitAnimation = true;
        }

        this.renderer.getAnimators().values().forEach(animator -> animator.setBaseScope(executionScope.copy()));
    }

    private boolean evaluateRenderControllerChange(final Scope executionScope) {
        final List<EvaluatedModel> newModels = new ArrayList<>();
        for (final BedrockEntityData.RenderController entityRenderController : this.entityDefinition.entityData().getControllers()) {
            final BedrockRenderController renderController = this.packManager.getRenderControllerDefinitions().getRenderControllers().get(entityRenderController.identifier());
            if (renderController == null) {
                continue;
            }
            if (!entityRenderController.condition().isBlank()) {
                try {
                    final Value conditionResult = MoLangEngine.eval(executionScope, entityRenderController.condition());
                    if (!conditionResult.getAsBoolean()) {
                        continue;
                    }
                } catch (Throwable e) {
                    ViaBedrockUtilityFabric.LOGGER.warn("Failed to evaluate render controller condition", e);
                    continue;
                }
            }

            try {
                final Scope renderControllerGeometryScope = executionScope.copy();
                renderControllerGeometryScope.set("array", this.getArrayBinding(executionScope, renderController.geometries()));
                final Scope renderControllerTextureScope = executionScope.copy();
                renderControllerTextureScope.set("array", this.getArrayBinding(executionScope, renderController.textures()));

                final String geometryValue = MoLangEngine.eval(renderControllerGeometryScope, renderController.geometryExpression()).getAsString();
                final String geometryName = this.inverseGeometryMap.get(geometryValue);
                for (String textureExpression : renderController.textureExpressions()) {
                    final String textureValue = MoLangEngine.eval(renderControllerTextureScope, textureExpression).getAsString();
                    final String textureName = this.inverseTextureMap.get(textureValue);
                    if (geometryName != null && textureName != null) {
                        newModels.add(new EvaluatedModel(geometryName + "_" + textureName, renderController, geometryValue, textureValue));
                    }
                }
            } catch (Throwable e) {
                this.models.clear();
                return true;
            }
        }

        if (!newModels.isEmpty() && !this.models.equals(newModels)) {
            this.availableModels.clear();
            this.models.clear();
            this.models.addAll(newModels);
            return true;
        } else {
            return false;
        }
    }

    private Material evalMaterial(final Scope executionScope, BedrockRenderController controller) {
        if (controller == null || controller.materialsMap().isEmpty()) {
            return DEFAULT_MATERIAL;
        }

        // I know it is more complex than just this but this is the case most of the time, and also I don't have the energy.
        if (!controller.materialsMap().containsKey("*")) {
            return DEFAULT_MATERIAL;
        }

        final Scope renderControllerMaterialScope = executionScope.copy();
        try {
            renderControllerMaterialScope.set("array", this.getArrayBinding(executionScope, controller.materials()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            final String materialMapName = MoLangEngine.eval(renderControllerMaterialScope, controller.materialsMap().get("*")).getAsString();
            final String materialName = this.entityDefinition.entityData().getMaterials().get(this.inverseMaterialMap.get(materialMapName));

            if (materialName == null) {
                return DEFAULT_MATERIAL;
            }

            return this.packManager.getMaterialDefinitions().getMaterial(materialName);
        } catch (IOException ignored) {}

        return DEFAULT_MATERIAL;
    }

    private MutableObjectBinding getArrayBinding(final Scope executionScope, final List<BedrockRenderController.Array> arrays) throws IOException {
        final MutableObjectBinding arrayBinding = new MutableObjectBinding();
        for (BedrockRenderController.Array array : arrays) {
            if (array.name().toLowerCase(Locale.ROOT).startsWith("array.")) {
                final String[] resolvedExpressions = new String[array.values().size()];
                for (int i = 0; i < array.values().size(); i++) {
                    resolvedExpressions[i] = MoLangEngine.eval(executionScope, array.values().get(i)).getAsString();
                }
                arrayBinding.set(array.name().substring(6), Value.of(resolvedExpressions));
            }
        }
        arrayBinding.block();
        return arrayBinding;
    }

    public record EvaluatedModel(String key, BedrockRenderController controller, String geometryValue, String textureValue) {
    }

    public Set<ActorFlags> entityFlags() {
        return this.entityFlags;
    }
}

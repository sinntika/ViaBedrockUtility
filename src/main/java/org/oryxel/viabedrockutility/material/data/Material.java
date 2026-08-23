package org.oryxel.viabedrockutility.material.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.*;
import java.util.function.Function;

import static net.minecraft.client.renderer.RenderPipelines.ENTITY_SNIPPET;
// removed: RenderStateShard no longer exists in 1.21.11
import static org.oryxel.viabedrockutility.util.JsonUtil.*;

// https://wiki.bedrock.dev/visuals/materials
public record Material(String identifier, String baseIdentifier, MaterialInfo info) {
    public static Map<String, Material> parse(final Map<String, Material> existing, final JsonObject base) {
        final Map<String, Material> map = new HashMap<>();

        if (!base.has("materials")) {
            return map;
        }

        final JsonObject materials = base.getAsJsonObject("materials");
        for (final String elementName : materials.keySet()) {
            final JsonElement element = materials.get(elementName);
            if (elementName.equals("version") || !element.isJsonObject()) {
                continue;
            }

            final JsonObject object = element.getAsJsonObject();
            final String[] split = elementName.split(":");
            if (split.length > 2) {
                continue;
            }

            final String identifier = split[0], baseIdentifier = split.length == 1 ? "" : split[1];

            final MaterialInfo material;
            if (!baseIdentifier.isBlank()) {
                final Material parent = map.getOrDefault(baseIdentifier, existing.get(baseIdentifier));
                if (parent == null) {
                    material = MaterialInfo.emptyMaterial();
                } else {
                    material = parent.info.clone();
                }
            } else {
                material = MaterialInfo.emptyMaterial();
            }

            material.parse(object, false);
            map.put(identifier, new Material(identifier, baseIdentifier, material));
        }

        return map;
    }

    @RequiredArgsConstructor
    @ToString
    @Getter
    @Setter
    public static class MaterialInfo {
        protected final Set<String> states, defines;
        protected final Set<String> vertexFields;
        protected String vertexShader = "", fragmentShader = "";
        protected String blendSrc = "", blendDst = "", depthFunc = "";

        protected final Map<String, Variant> variants = new HashMap<>();

        private Function<Identifier, RenderType> function;

        public void parse(final JsonObject object, boolean ignoreVariants) {
            final Set<String> extraStates = arrayToStringSet(object.getAsJsonArray("+states"));
            final Set<String> extraDefines = arrayToStringSet(object.getAsJsonArray("+defines"));
            final Set<String> removeStates = arrayToStringSet(object.getAsJsonArray("-states"));
            final Set<String> removeDefines = arrayToStringSet(object.getAsJsonArray("-defines"));

            this.states.addAll(extraStates);
            this.defines.addAll(extraDefines);
            this.states.removeAll(removeStates);
            this.defines.removeAll(removeDefines);

            if (object.has("vertexShader")) {
                this.vertexShader = object.get("vertexShader").getAsString();
            }

            if (object.has("fragmentShader")) {
                this.fragmentShader = object.get("fragmentShader").getAsString();
            }

            if (object.has("vertexFields")) {
                this.vertexFields.clear();
                this.vertexFields.addAll(vertexFields(object.getAsJsonArray("vertexFields")));
            }

            if (object.has("blendSrc")) {
                this.blendSrc = object.get("blendSrc").getAsString();
            }

            if (object.has("blendDst")) {
                this.blendSrc = object.get("blendDst").getAsString();
            }

            if (object.has("depthFunc")) {
                this.blendSrc = object.get("depthFunc").getAsString();
            }

            if (ignoreVariants) {
                return;
            }

            if (object.has("variants") && object.get("variants").isJsonArray()) {
                final JsonArray jsonVariants = object.getAsJsonArray("variants");
                for (JsonElement variantElement : jsonVariants) {
                    if (!variantElement.isJsonObject()) {
                        continue;
                    }

                    final JsonObject variantObject = variantElement.getAsJsonObject();
                    for (final String variantName : variantObject.keySet()) {
                        if (!variantObject.get(variantName).isJsonObject()) {
                            continue;
                        }

                        final Variant baseVariant = this.variants.getOrDefault(variantName, MaterialInfo.emptyVariant()).clone();
                        this.variants.put(variantName, baseVariant);

                        baseVariant.parse(variantObject.getAsJsonObject(variantName), true);
                    }
                }
            }

            this.variants.forEach((k, v) -> {
                v.states.addAll(extraStates);
                v.defines.addAll(extraDefines);
                v.states.removeAll(removeStates);
                v.defines.removeAll(removeDefines);

                v.vertexFields.addAll(vertexFields);

                v.fragmentShader = fragmentShader;
                v.vertexShader = vertexShader;

                v.blendSrc = blendSrc;
                v.blendDst = blendDst;

                v.depthFunc = depthFunc;
            });
        }

        public Function<Identifier, RenderType> build() {
            return Objects.requireNonNullElseGet(this.function, () -> this.function = Util.memoize(texture -> {
                final VertexFormat vertexFormat = this.vertexFields.isEmpty()
                        ? DefaultVertexFormat.ENTITY
                        : buildVertexFormat(this.vertexFields);

                // 26.2 merged SourceFactor and DestFactor into a single BlendFactor enum.
                final BlendFunction blend;
                if (!this.blendSrc.isBlank() && !this.blendDst.isBlank()) {
                    final BlendFactor srcFactor = switch (this.blendSrc) {
                        case "SourceAlpha" -> BlendFactor.SRC_ALPHA;
                        case "SourceColor" -> BlendFactor.SRC_COLOR;
                        case "ConstantAlpha" -> BlendFactor.CONSTANT_ALPHA;
                        case "ConstantColor" -> BlendFactor.CONSTANT_COLOR;
                        case "DstAlpha" -> BlendFactor.DST_ALPHA;
                        case "DstColor" -> BlendFactor.DST_COLOR;
                        case "OneMinusConstantAlpha" -> BlendFactor.ONE_MINUS_CONSTANT_ALPHA;
                        case "OneMinusConstantColor" -> BlendFactor.ONE_MINUS_CONSTANT_COLOR;
                        case "OneMinusDstAlpha" -> BlendFactor.ONE_MINUS_DST_ALPHA;
                        case "OneMinusDstColor" -> BlendFactor.ONE_MINUS_DST_COLOR;
                        case "OneMinusSrcAlpha" -> BlendFactor.ONE_MINUS_SRC_ALPHA;
                        case "OneMinusSrcColor" -> BlendFactor.ONE_MINUS_SRC_COLOR;
                        case "SourceAlphaSaturate" -> BlendFactor.SRC_ALPHA_SATURATE;
                        case "Zero" -> BlendFactor.ZERO;
                        default -> BlendFactor.ONE;
                    };

                    final BlendFactor dstFactor = switch (this.blendDst) {
                        case "SourceAlpha" -> BlendFactor.SRC_ALPHA;
                        case "SourceColor" -> BlendFactor.SRC_COLOR;
                        case "ConstantAlpha" -> BlendFactor.CONSTANT_ALPHA;
                        case "ConstantColor" -> BlendFactor.CONSTANT_COLOR;
                        case "DstAlpha" -> BlendFactor.DST_ALPHA;
                        case "DstColor" -> BlendFactor.DST_COLOR;
                        case "OneMinusConstantAlpha" -> BlendFactor.ONE_MINUS_CONSTANT_ALPHA;
                        case "OneMinusConstantColor" -> BlendFactor.ONE_MINUS_CONSTANT_COLOR;
                        case "OneMinusDstAlpha" -> BlendFactor.ONE_MINUS_DST_ALPHA;
                        case "OneMinusDstColor" -> BlendFactor.ONE_MINUS_DST_COLOR;
                        case "OneMinusSrcAlpha" -> BlendFactor.ONE_MINUS_SRC_ALPHA;
                        case "OneMinusSrcColor" -> BlendFactor.ONE_MINUS_SRC_COLOR;
                        case "Zero" -> BlendFactor.ZERO;
                        default -> BlendFactor.ONE;
                    };

                    blend = new BlendFunction(srcFactor, dstFactor);
                } else {
                    blend = BlendFunction.TRANSLUCENT;
                }

                RenderPipeline.Builder builder = RenderPipeline.builder(ENTITY_SNIPPET);

                builder.withLocation(Identifier.fromNamespaceAndPath("viabedrockutility", "pipeline/" + UUID.randomUUID() + this.hashCode()));

                // 26.2: blending lives in the color target state and the depth test in the
                // depth stencil state, and the vertex format is a binding plus a topology.
                builder.withColorTargetState(new ColorTargetState(blend));

                builder.withVertexBinding(0, vertexFormat);
                builder.withPrimitiveTopology(this.defines.contains("LINE_STRIP") ? PrimitiveTopology.DEBUG_LINE_STRIP : PrimitiveTopology.QUADS);

                // Totally possible, but not now.
//                if (!this.fragmentShader.isBlank()) {
//                    builder.withFragmentShader(this.fragmentShader.replace("shaders/", "").split("\\.")[0]);
//                }
//                if (!this.vertexShader.isBlank()) {
//                    builder.withVertexShader(this.vertexShader.replace("shaders/", "").split("\\.")[0]);
//                }

                builder.withCull(!this.states.contains("DisableCulling"));

                final CompareOp depthOp = switch (this.depthFunc) {
                    case "Equal" -> CompareOp.EQUAL;
                    case "Bigger" -> CompareOp.GREATER_THAN;
                    default -> CompareOp.LESS_THAN_OR_EQUAL;
                };
                builder.withDepthStencilState(new DepthStencilState(depthOp, !this.states.contains("DisableDepthWrite")));

                if (this.defines.contains("ALPHA_TEST")) {
                    builder.withShaderDefine("ALPHA_CUTOUT", 0.1F);
                }

                if (this.defines.contains("USE_EMISSIVE")) {
                    builder.withShaderDefine("EMISSIVE");
                }

                // 1.21.11: RenderType.MultiPhaseParameters is gone, a render type is
                // now a pipeline plus a RenderSetup that binds the samplers.
                final RenderSetup.RenderSetupBuilder renderSetupBuilder = RenderSetup.builder(builder.build());
                if (!this.defines.contains("NO_TEXTURE")) {
                    renderSetupBuilder.withTexture("Sampler0", texture);
                }

                renderSetupBuilder.useLightmap();
                renderSetupBuilder.useOverlay();
                return RenderType.create("custom", renderSetupBuilder.createRenderSetup());
            }));

        }

        // 26.2 deleted the VertexFormatElement constants: an attribute is now a name
        // plus a GpuFormat. The formats are copied from vanilla's entity format so
        // this keeps working the next time Mojang reshuffles them.
        private static VertexFormat buildVertexFormat(final Set<String> fields) {
            final VertexFormat.Builder builder = VertexFormat.builder(0);

            int added = 0;
            added += addAttribute(builder, fields, "Position", "Position");
            added += addAttribute(builder, fields, "Color", "Color");
            added += addAttribute(builder, fields, "UV", "UV0");
            added += addAttribute(builder, fields, "UV0", "UV0");

            if (fields.contains("BoneId0")) {
                // Not entirely sure, educated guess.
                added += copyAttribute(builder, "UV1", "UV1");
                added += copyAttribute(builder, "UV2", "UV2");
            } else {
                added += addAttribute(builder, fields, "UV1", "UV1");
                added += addAttribute(builder, fields, "UV2", "UV2");
            }

            added += addAttribute(builder, fields, "Normal", "Normal");

            return added == 0 ? DefaultVertexFormat.ENTITY : builder.build();
        }

        private static int addAttribute(final VertexFormat.Builder builder, final Set<String> fields, final String name, final String vanillaName) {
            return fields.contains(name) ? copyAttribute(builder, name, vanillaName) : 0;
        }

        private static int copyAttribute(final VertexFormat.Builder builder, final String name, final String vanillaName) {
            if (!DefaultVertexFormat.ENTITY.contains(vanillaName)) {
                return 0;
            }

            builder.addAttribute(name, DefaultVertexFormat.ENTITY.getElement(vanillaName).format());
            return 1;
        }

        public static MaterialInfo emptyMaterial() {
            return new MaterialInfo(new HashSet<>(), new HashSet<>(), new HashSet<>());
        }

        public static Variant emptyVariant() {
            return new Variant(new HashSet<>(), new HashSet<>(), new HashSet<>());
        }

        public static class Variant extends MaterialInfo {
            public Variant(Set<String> states, Set<String> defines, Set<String> vertexFields) {
                super(states, defines, vertexFields);
            }

            @Override
            public Variant clone() {
                final Variant info = new Variant(new HashSet<>(states), new HashSet<>(defines), new HashSet<>(vertexFields));
                info.setBlendDst(blendDst);
                info.setBlendSrc(blendSrc);
                info.setDepthFunc(depthFunc);
                info.setVertexShader(vertexShader);
                info.setFragmentShader(fragmentShader);
                return info;
            }
        }

        @Override
        public MaterialInfo clone() {
            final MaterialInfo info = new MaterialInfo(new HashSet<>(states), new HashSet<>(defines), new HashSet<>(vertexFields));
            info.setBlendDst(blendDst);
            info.setBlendSrc(blendSrc);
            info.setDepthFunc(depthFunc);
            info.setVertexShader(vertexShader);
            info.setFragmentShader(fragmentShader);
            variants.forEach((k, v) -> {
                info.variants.put(k, v.clone());
            });

            return info;
        }
    }
}

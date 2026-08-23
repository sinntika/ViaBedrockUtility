package org.oryxel.viabedrockutility.material.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.util.Util;

import java.util.*;
import java.util.function.Function;

import static net.minecraft.client.renderer.RenderPipelines.ENTITY_SNIPPET;
import static net.minecraft.client.renderer.RenderStateShard.*;
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
                final VertexFormat vertexFormat;
                if (!this.vertexFields.isEmpty()) {
                    VertexFormat.Builder vertexBuilder = VertexFormat.builder();
                    //         POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL = VertexFormat.
                    //         builder().add("Position", VertexFormatElement.POSITION).add("Color", VertexFormatElement.COLOR)
                    //         .add("UV0", VertexFormatElement.UV_0).add("UV1", VertexFormatElement.UV_1)
                    //         .add("UV2", VertexFormatElement.UV_2).add("Normal", VertexFormatElement.NORMAL).skip(1).build();

                    if (this.vertexFields.contains("Position")) {
                        vertexBuilder.add("Position", VertexFormatElement.POSITION);
                    }
                    if (this.vertexFields.contains("Color")) {
                        vertexBuilder.add("Color", VertexFormatElement.COLOR);
                    }
                    if (this.vertexFields.contains("UV")) {
                        vertexBuilder.add("UV", VertexFormatElement.UV);
                    }
                    if (this.vertexFields.contains("UV0")) {
                        vertexBuilder.add("UV0", VertexFormatElement.UV0);
                    }
                    if (this.vertexFields.contains("BoneId0")) {
                        // Not entirely sure, educated guess.
                        vertexBuilder.add("UV1", VertexFormatElement.UV1);
                        vertexBuilder.add("UV2", VertexFormatElement.UV2);
                    } else {
                        if (this.vertexFields.contains("UV1")) {
                            vertexBuilder.add("UV1", VertexFormatElement.UV1);
                        }
                        if (this.vertexFields.contains("UV2")) {
                            vertexBuilder.add("UV2", VertexFormatElement.UV2);
                        }
                    }
                    if (this.vertexFields.contains("Normal")) {
                        vertexBuilder.add("Normal", VertexFormatElement.NORMAL).padding(1);
                    }
                    vertexFormat = vertexBuilder.build();
                } else {
                    vertexFormat = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;
                }

                final BlendFunction blend;
                if (!this.blendSrc.isBlank() && !this.blendDst.isBlank()) {
                    final SourceFactor srcFactor = switch (this.blendSrc) {
                        case "SourceAlpha" -> SourceFactor.SRC_ALPHA;
                        case "SourceColor" -> SourceFactor.SRC_COLOR;
                        case "ConstantAlpha" -> SourceFactor.CONSTANT_ALPHA;
                        case "ConstantColor" -> SourceFactor.CONSTANT_COLOR;
                        case "DstAlpha" -> SourceFactor.DST_ALPHA;
                        case "DstColor" -> SourceFactor.DST_COLOR;
                        case "OneMinusConstantAlpha" -> SourceFactor.ONE_MINUS_CONSTANT_ALPHA;
                        case "OneMinusConstantColor" -> SourceFactor.ONE_MINUS_CONSTANT_COLOR;
                        case "OneMinusDstAlpha" -> SourceFactor.ONE_MINUS_DST_ALPHA;
                        case "OneMinusDstColor" -> SourceFactor.ONE_MINUS_DST_COLOR;
                        case "OneMinusSrcAlpha" -> SourceFactor.ONE_MINUS_SRC_ALPHA;
                        case "OneMinusSrcColor" -> SourceFactor.ONE_MINUS_SRC_COLOR;
                        case "SourceAlphaSaturate" -> SourceFactor.SRC_ALPHA_SATURATE;
                        case "Zero" -> SourceFactor.ZERO;
                        default -> SourceFactor.ONE;
                    };

                    final DestFactor dstFactor = switch (this.blendDst) {
                        case "SourceAlpha" -> DestFactor.SRC_ALPHA;
                        case "SourceColor" -> DestFactor.SRC_COLOR;
                        case "ConstantAlpha" -> DestFactor.CONSTANT_ALPHA;
                        case "ConstantColor" -> DestFactor.CONSTANT_COLOR;
                        case "DstAlpha" -> DestFactor.DST_ALPHA;
                        case "DstColor" -> DestFactor.DST_COLOR;
                        case "OneMinusConstantAlpha" -> DestFactor.ONE_MINUS_CONSTANT_ALPHA;
                        case "OneMinusConstantColor" -> DestFactor.ONE_MINUS_CONSTANT_COLOR;
                        case "OneMinusDstAlpha" -> DestFactor.ONE_MINUS_DST_ALPHA;
                        case "OneMinusDstColor" -> DestFactor.ONE_MINUS_DST_COLOR;
                        case "OneMinusSrcAlpha" -> DestFactor.ONE_MINUS_SRC_ALPHA;
                        case "OneMinusSrcColor" -> DestFactor.ONE_MINUS_SRC_COLOR;
                        case "Zero" -> DestFactor.ZERO;
                        default -> DestFactor.ONE;
                    };

                    blend = new BlendFunction(srcFactor, dstFactor);
                } else {
                    blend = BlendFunction.TRANSLUCENT;
                }

                RenderPipeline.Builder builder = RenderPipeline.builder(ENTITY_SNIPPET).withSampler("Sampler1");

                builder.withLocation(Identifier.fromNamespaceAndPath("viabedrockutility", "pipeline/" + UUID.randomUUID() + this.hashCode()));
                builder.withBlend(blend);

                builder.withVertexFormat(vertexFormat, this.defines.contains("LINE_STRIP") ? VertexFormat.DrawMode.LINE_STRIP : VertexFormat.DrawMode.QUADS);

                // Totally possible, but not now.
//                if (!this.fragmentShader.isBlank()) {
//                    builder.withFragmentShader(this.fragmentShader.replace("shaders/", "").split("\\.")[0]);
//                }
//                if (!this.vertexShader.isBlank()) {
//                    builder.withVertexShader(this.vertexShader.replace("shaders/", "").split("\\.")[0]);
//                }

                builder.withCull(!this.states.contains("DisableCulling"));

                builder.withDepthTestFunction(switch (this.depthFunc) {
                    case "Equal" -> DepthTestFunction.EQUAL_DEPTH_TEST;
                    case "Bigger" -> DepthTestFunction.GREATER_DEPTH_TEST;
                    default -> DepthTestFunction.LEQUAL_DEPTH_TEST;
                });

                builder.withDepthWrite(!this.states.contains("DisableDepthWrite"));

                if (this.defines.contains("ALPHA_TEST")) {
                    builder.withShaderDefine("ALPHA_CUTOUT", 0.1F);
                }

                if (this.defines.contains("USE_EMISSIVE")) {
                    builder.withShaderDefine("EMISSIVE");
                }

                final RenderType.MultiPhaseParameters.Builder renderLayerBuilder = RenderType.MultiPhaseParameters.builder();
                if (!this.defines.contains("NO_TEXTURE")) {
                    renderLayerBuilder.texture(new Texture(texture, TriState.FALSE, false));
                }

                renderLayerBuilder.lightmap(ENABLE_LIGHTMAP);
                renderLayerBuilder.overlay(ENABLE_OVERLAY_COLOR);
                return RenderType.of("custom", 1536, true, true, builder.build(), renderLayerBuilder.build(false));
            }));

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

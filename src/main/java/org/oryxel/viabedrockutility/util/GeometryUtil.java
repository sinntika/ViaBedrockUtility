package org.oryxel.viabedrockutility.util;

import com.google.common.collect.Maps;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.Direction;
import org.cube.converter.model.element.Cube;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.util.element.Position3V;
import org.cube.converter.util.element.UVMap;
import org.joml.Vector3f;
import org.oryxel.viabedrockutility.mixin.interfaces.IModelPart;
import org.oryxel.viabedrockutility.renderer.model.CustomEntityModel;

import java.util.*;

public final class GeometryUtil {
    private static final List<String> LEG_RELATED = List.of("leftleg", "rightleg", "rightpants", "leftpants");

    public static Model buildModel(final BedrockGeometryModel geometry, final boolean player, boolean slim) {
        // There are some times when the skin image file is larger than the geometry UV points.
        // In this case, we need to scale UV calls
        // https://github.com/Camotoy/BedrockSkinUtility/issues/9
        final float uvWidth = geometry.getTextureSize().getX();
        final float uvHeight = geometry.getTextureSize().getY();

        final Map<String, PartInfo> stringToPart = new HashMap<>();
        for (final Parent bone : geometry.getParents()) {
            final Map<String, ModelPart> children = Maps.newHashMap();
            final ModelPart part = new ModelPart(List.of(), children);

            boolean neededOffset = switch (bone.getName().toLowerCase(Locale.ROOT)) {
                case "rightarm", "leftarm" -> player;
                default -> false;
            };

            ((IModelPart)((Object)part)).viaBedrockUtility$setVBUModel();
            ((IModelPart)((Object)part)).viaBedrockUtility$setName(bone.getName());
            ((IModelPart)((Object)part)).viaBedrockUtility$setNeededOffset(neededOffset);
            ((IModelPart)((Object)part)).viaBedrockUtility$setAngles(new Vector3f(bone.getRotation().getX() , bone.getRotation().getY(), bone.getRotation().getZ()));

            boolean isLeg = player && LEG_RELATED.contains(bone.getName().toLowerCase(Locale.ROOT));
            if (isLeg) {
                part.setPos(0, bone.getPivot().getY(), 0);
                part.setInitialPose(part.storePose());
            } else {
                ((IModelPart)((Object)part)).viaBedrockUtility$setPivot(new Vector3f(bone.getPivot().getX(), -bone.getPivot().getY() + 24.016F, bone.getPivot().getZ()));
            }

            // Java don't allow individual cubes to have their own rotation therefore, we have to separate each cube into ModelPart to be able to rotate.
            for (final Cube cube : bone.getCubes().values()) {
                final Position3V pos = cube.getPosition();

                final float sizeX = cube.getSize().getX(), sizeY = cube.getSize().getY(), sizeZ = cube.getSize().getZ();
                final float inflate = cube.getInflate();

                final UVMap uvMap = cube.getUvMap().clone();

                final Set<Direction> set = new HashSet<>();
                for (final Direction direction : Direction.values()) {
                    if (uvMap.getUvMap().containsKey(org.cube.converter.util.element.Direction.values()[direction.ordinal()])) {
                        set.add(direction);
                    }
                }

                final ModelPart.Cube cuboid = new ModelPart.Cube(0, 0, pos.getX(), isLeg ? pos.getY() : -(pos.getY() - 24.016F + sizeY), pos.getZ(), sizeX, sizeY, sizeZ, inflate, inflate, inflate, cube.isMirror(), uvWidth, uvHeight, set);
                correctUv(cuboid, set, uvMap, uvWidth, uvHeight, cube.getInflate(), cube.isMirror());

                final ModelPart cubePart = new ModelPart(List.of(cuboid), Map.of());
                ((IModelPart)((Object)cubePart)).viaBedrockUtility$setPivot(new Vector3f(cube.getPivot().getX(), -cube.getPivot().getY() + 24.016F, cube.getPivot().getZ()));
                ((IModelPart)((Object)cubePart)).viaBedrockUtility$setAngles(new Vector3f(cube.getRotation().getX(), cube.getRotation().getY(), cube.getRotation().getZ()));
                ((IModelPart)((Object)cubePart)).viaBedrockUtility$setVBUModel();
                ((IModelPart)((Object)cubePart)).viaBedrockUtility$setNeededOffset(neededOffset);
                ((IModelPart)((Object)cubePart)).viaBedrockUtility$setName(bone.getName());
                children.put(cube.getParent() + cube.hashCode(), cubePart);
            }

            String parent = bone.getParent();
            String name = bone.getName();
            if (player) {
                switch (name.toLowerCase(Locale.ROOT)) { // Also do this with the overlays? Those are final, though.
                    case "head", "rightarm", "body", "leftarm", "leftleg", "rightleg" -> parent = "root";
                }
            }

            stringToPart.put(adjustFormatting(player, name), new PartInfo(adjustFormatting(player, parent), part, children));
        }

        PartInfo root = stringToPart.get("root");
        if (root == null) {
            final Map<String, ModelPart> rootParts = Maps.newHashMap();
            stringToPart.put("root", root = new PartInfo("", new ModelPart(List.of(), rootParts), rootParts));
        } else if (!player) {
            final Map<String, ModelPart> rootParts = Maps.newHashMap();
            root = new PartInfo("", new ModelPart(List.of(), rootParts), rootParts);
        }

        for (Map.Entry<String, PartInfo> entry : stringToPart.entrySet()) {
            if (entry.getValue().parent.isBlank() && entry.getValue().part() != root.part) {
                root.children.put(entry.getKey(), entry.getValue().part());
                continue;
            }

            PartInfo parentPart = stringToPart.get(entry.getValue().parent);
            if (parentPart != null) {
                parentPart.children.put(entry.getKey(), entry.getValue().part);
            }
        }

        return player ? new PlayerModel(root.part(), slim) : new CustomEntityModel<>(root.part());
    }

    private static String adjustFormatting(boolean player, String name) {
        if (!player) {
            return name;
        }

        if (name == null) {
            return null;
        }

        return switch (name.toLowerCase(Locale.ROOT)) {
            case "leftarm" -> "left_arm";
            case "rightarm" -> "right_arm";
            case "leftleg" -> "left_leg";
            case "rightleg" -> "right_leg";
            default -> name.toLowerCase(Locale.ROOT);
        };
    }

    private record PartInfo(String parent, ModelPart part, Map<String, ModelPart> children) {
    }

    private static void correctUv(final ModelPart.Cube cuboid, final Set<Direction> set, final UVMap map, final float uvWidth, final float uvHeight, final float inflate, final boolean mirror) {
        float x = cuboid.minX, y = cuboid.minY, z = cuboid.minZ;
        float f = cuboid.maxX, g = cuboid.maxY, h = cuboid.maxZ;

        x -= inflate;
        y -= inflate;
        z -= inflate;
        f += inflate;
        g += inflate;
        h += inflate;

        if (mirror) {
            float i = f;
            f = x;
            x = i;
        }

        ModelPart.Vertex vertex = new ModelPart.Vertex(x, y, z, 0.0F, 0.0F);
        ModelPart.Vertex vertex2 = new ModelPart.Vertex(f, y, z, 0.0F, 8.0F);
        ModelPart.Vertex vertex3 = new ModelPart.Vertex(f, g, z, 8.0F, 8.0F);
        ModelPart.Vertex vertex4 = new ModelPart.Vertex(x, g, z, 8.0F, 0.0F);
        ModelPart.Vertex vertex5 = new ModelPart.Vertex(x, y, h, 0.0F, 0.0F);
        ModelPart.Vertex vertex6 = new ModelPart.Vertex(f, y, h, 0.0F, 8.0F);
        ModelPart.Vertex vertex7 = new ModelPart.Vertex(f, g, h, 8.0F, 8.0F);
        ModelPart.Vertex vertex8 = new ModelPart.Vertex(x, g, h, 8.0F, 0.0F);

        final ModelPart.Polygon[] sides = cuboid.polygons;
        int s = 0;

        if (set.contains(Direction.DOWN)) {
            final Float[] uv = map.getUvMap().get(org.cube.converter.util.element.Direction.DOWN);
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex6, vertex5, vertex, vertex2}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.DOWN);
        }

        if (set.contains(Direction.UP)) {
            final Float[] uv = map.getUvMap().get(org.cube.converter.util.element.Direction.UP);
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex3, vertex4, vertex8, vertex7}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.UP);
        }

        if (set.contains(Direction.WEST)) {
            final Float[] uv = map.getUvMap().get(org.cube.converter.util.element.Direction.WEST);
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex, vertex5, vertex8, vertex4}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.WEST);
        }

        if (set.contains(Direction.NORTH)) {
            final Float[] uv = map.getUvMap().get(org.cube.converter.util.element.Direction.NORTH);
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex2, vertex, vertex4, vertex3}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.NORTH);
        }

        if (set.contains(Direction.EAST)) {
            final Float[] uv = map.getUvMap().get(org.cube.converter.util.element.Direction.EAST);
            sides[s++] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex6, vertex2, vertex3, vertex7}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.EAST);
        }

        if (set.contains(Direction.SOUTH)) {
            final Float[] uv = map.getUvMap().get(org.cube.converter.util.element.Direction.SOUTH);
            sides[s] = new ModelPart.Polygon(new ModelPart.Vertex[]{vertex5, vertex6, vertex7, vertex8}, uv[0], uv[1], uv[2], uv[3], uvWidth, uvHeight, mirror, Direction.SOUTH);
        }
    }
}
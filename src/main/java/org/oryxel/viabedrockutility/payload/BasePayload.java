package org.oryxel.viabedrockutility.payload;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.oryxel.viabedrockutility.ViaBedrockUtility;
import org.oryxel.viabedrockutility.enums.bedrock.ActorFlags;
import org.oryxel.viabedrockutility.fabric.ViaBedrockUtilityFabric;
import org.oryxel.viabedrockutility.payload.enums.PayloadType;
import org.oryxel.viabedrockutility.payload.impl.entity.*;
import org.oryxel.viabedrockutility.payload.impl.skin.*;
import org.oryxel.viabedrockutility.util.EnumUtil;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@Getter
public class BasePayload implements CustomPacketPayload {
    public static CustomPacketPayload.Type<BasePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ViaBedrockUtilityFabric.MOD_ID, "data"));

    public static final StreamCodec<FriendlyByteBuf, BasePayload> STREAM_CODEC = StreamCodec.of(null, buf -> {
        final int type = buf.readInt();
        if (type > PayloadType.values().length - 1) {
            throw new RuntimeException("Invalid type: " + type);
        }

        switch (PayloadType.values()[type]) {
            case CONFIRM -> {
                // Confirm that ViaBedrock is present, this should be sent back right after we send confirm register channel.
                ViaBedrockUtility.getInstance().setViaBedrockPresent(true);
                return new BasePayload();
            }
            case MODEL_REQUEST -> {
                final String identifier = readString(buf);

                BigInteger combinedFlags = BigInteger.ZERO;
                if (buf.readBoolean()) {
                    combinedFlags = combinedFlags.add(BigInteger.valueOf(buf.readLong()));
                }
                if (buf.readBoolean()) {
                    combinedFlags = combinedFlags.add(BigInteger.valueOf(buf.readLong()).shiftLeft(64));
                }

                Integer variant = null, mark_variant = null;
                if (buf.readBoolean()) {
                    variant = buf.readInt();
                }
                if (buf.readBoolean()) {
                    mark_variant = buf.readInt();
                }

                return new ModelRequestPayload(identifier, EnumUtil.getEnumSetFromBitmask(ActorFlags.class, combinedFlags, ActorFlags::getValue), variant, mark_variant, buf.readUUID());
            }

            case ANIMATE -> {
                // TODO: Implement this.
                return new BasePayload();
            }

            case CAPE -> {
                return CapeDataPayload.STREAM_DECODER.decode(buf);
            }
            case SKIN_INFORMATION -> {
                return BaseSkinPayload.STREAM_DECODER.decode(buf);
            }
            case SKIN_DATA -> {
                return SkinDataPayload.STREAM_DECODER.decode(buf);
            }

            default -> throw new IllegalStateException("Unexpected value: " + PayloadType.values()[type]);
        }
    });

    public void handle(final PayloadHandler handler) {
        try {
            handler.handle(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static String readString(FriendlyByteBuf buf) {
        int length = buf.readInt();
        String result = buf.toString(buf.readerIndex(), length, StandardCharsets.UTF_8);
        buf.readerIndex(buf.readerIndex() + length);
        return result;
    }
}

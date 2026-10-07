package com.example.myfirstmod.network;

import com.example.myfirstmod.BtsdhzOriginal;
import com.example.myfirstmod.util.PlacementMode;
import com.example.myfirstmod.util.PlacementModeState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 服务端 -> 客户端: 下发该玩家在存档里记住的放置逻辑（客户端预测与提示线用）。 */
public record PlacementModeSyncPayload(int slabMode, int stairMode) implements CustomPacketPayload {
    public static final Type<PlacementModeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "placement_mode_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementModeSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.slabMode());
                        buffer.writeVarInt(payload.stairMode());
                    },
                    buffer -> new PlacementModeSyncPayload(buffer.readVarInt(), buffer.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlacementModeSyncPayload payload, IPayloadContext context) {
        PlacementModeState.setClient(
                context.player(),
                PlacementMode.byOrdinal(payload.slabMode()),
                PlacementMode.byOrdinal(payload.stairMode())
        );
    }
}

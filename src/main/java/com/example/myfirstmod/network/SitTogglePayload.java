package com.example.myfirstmod.network;

import com.example.myfirstmod.BtsdhzOriginal;
import com.example.myfirstmod.util.SitLogic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端 -> 服务端:切换是否坐下。 */
public record SitTogglePayload() implements CustomPacketPayload {
    public static final Type<SitTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "sit_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SitTogglePayload> STREAM_CODEC =
            StreamCodec.unit(new SitTogglePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SitTogglePayload payload, IPayloadContext context) {
        SitLogic.toggleSit(context.player());
    }
}

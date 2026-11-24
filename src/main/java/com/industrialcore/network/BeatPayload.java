package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 77 Part 4 - lightweight Server -> Client factory beat summary packet.
 * Carries up to 8 active machine coordinates within player range so closed-GUI
 * world rhythms and micro-particles match real running factory load cleanly.
 */
public record BeatPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 8;
    public static final byte CAT_PROCESSING = 0;
    public static final byte CAT_GENERATION = 1;
    public static final byte CAT_TRANSPORTER = 2;

    public static final Type<BeatPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "factory_beat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BeatPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                List<Entry> list = payload.entries();
                int count = Math.min(MAX_ENTRIES, list.size());
                buf.writeByte(count);
                for (int i = 0; i < count; i++) {
                    Entry e = list.get(i);
                    buf.writeBlockPos(e.pos());
                    buf.writeByte(e.category());
                    buf.writeByte(e.intensity());
                }
            },
            buf -> {
                int count = Math.max(0, Math.min(MAX_ENTRIES, buf.readByte() & 0xFF));
                List<Entry> out = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    out.add(new Entry(buf.readBlockPos(), buf.readByte(), buf.readByte()));
                }
                return new BeatPayload(List.copyOf(out));
            }
    );

    public record Entry(BlockPos pos, byte category, byte intensity) {}

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static volatile Consumer<BeatPayload> clientHandler;

    public static void handle(BeatPayload payload, IPayloadContext context) {
        Consumer<BeatPayload> handler = clientHandler;
        if (handler != null) {
            context.enqueueWork(() -> handler.accept(payload));
        }
    }
}

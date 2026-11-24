package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import com.industrialcore.machine.MachineActions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server request to change a machine setting (Phase 11).
 *
 * <p>The payload only carries the block position, the face and an action id. It never carries item
 * stacks or results: the server picks the filter item from the player's carried stack and resolves
 * recipes itself, so a modified client cannot name an arbitrary output or inject items.</p>
 *
 * @param side   face index 0..5, or -1 for a machine wide action
 * @param action action id; the meaning depends on the machine (see {@code CrafterBlockEntity})
 */
public record MachineConfigPayload(BlockPos pos, int side, int action) implements CustomPacketPayload {
    /** Machine wide: register the recipe arranged in the crafter's grid. */
    public static final int ACTION_REGISTER_GRID = MachineActions.REGISTER_GRID;

    /** Face actions, mirrored here so both screens can import one class. */
    public static final int ACTION_FACE_MODE = MachineActions.FACE_MODE;
    public static final int ACTION_FACE_SLOT = MachineActions.FACE_SLOT;
    public static final int ACTION_FACE_FILTER = MachineActions.FACE_FILTER;
    public static final int ACTION_FACE_ADD_ITEM = MachineActions.FACE_ADD_ITEM;
    public static final int ACTION_FACE_CLEAR = MachineActions.FACE_CLEAR;

    public static final Type<MachineConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "machine_config"));

    public static final StreamCodec<FriendlyByteBuf, MachineConfigPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos());
                buffer.writeVarInt(payload.side());
                buffer.writeVarInt(payload.action());
            },
            buffer -> new MachineConfigPayload(buffer.readBlockPos(),
                    buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

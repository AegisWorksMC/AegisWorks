package com.industrialcore.blockentity;

import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 104: BlockEntity for {@code machine_bounding}. Persists {@code masterPos} in NBT and
 * transparently delegates Energy, Item, and Fluid capabilities to the owning master machine.
 */
public class MachineBoundingBlockEntity extends BlockEntity {
    private static final String TAG_HAS_MASTER = "HasMaster";
    private static final String TAG_MX = "MasterX";
    private static final String TAG_MY = "MasterY";
    private static final String TAG_MZ = "MasterZ";

    @Nullable
    private BlockPos masterPos;

    public MachineBoundingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_BOUNDING.get(), pos, state);
    }

    @Nullable
    public BlockPos getMasterPos() {
        return masterPos;
    }

    public void setMasterPos(@Nullable BlockPos pos) {
        this.masterPos = pos != null ? pos.immutable() : null;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private boolean isMasterReady() {
        return masterPos != null && !masterPos.equals(worldPosition)
                && level != null && level.isLoaded(masterPos);
    }

    @Nullable
    public IEnergyStorage getProxiedEnergy(@Nullable Direction side) {
        if (!isMasterReady()) {
            return null;
        }
        return level.getCapability(Capabilities.EnergyStorage.BLOCK, masterPos, side);
    }

    @Nullable
    public IItemHandler getProxiedItems(@Nullable Direction side) {
        if (!isMasterReady()) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, masterPos, side);
    }

    @Nullable
    public IFluidHandler getProxiedFluids(@Nullable Direction side) {
        if (!isMasterReady()) {
            return null;
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, masterPos, side);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (masterPos != null) {
            tag.putBoolean(TAG_HAS_MASTER, true);
            tag.putInt(TAG_MX, masterPos.getX());
            tag.putInt(TAG_MY, masterPos.getY());
            tag.putInt(TAG_MZ, masterPos.getZ());
        } else {
            tag.putBoolean(TAG_HAS_MASTER, false);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.getBoolean(TAG_HAS_MASTER)) {
            this.masterPos = new BlockPos(tag.getInt(TAG_MX), tag.getInt(TAG_MY), tag.getInt(TAG_MZ));
        } else {
            this.masterPos = null;
        }
    }
}

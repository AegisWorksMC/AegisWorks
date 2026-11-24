package com.industrialcore.blockentity;

import com.industrialcore.block.AbstractLifeBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.life.ChunkLoaderTickets;
import com.industrialcore.life.LifeWave2Logic;
import com.industrialcore.life.LifeWave2Logic.ChunkCoord;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.ChunkLoaderMenu;
import com.industrialcore.network.ChunkLoaderActionPayload;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Phase 95: BlockEntity for the Chunk Loader ({@code chunk_loader}).
 *
 * <p>Uses NeoForge's {@link com.industrialcore.life.ChunkLoaderTickets} to force 1x1, 3x3 (4+
 * motors), or 5x5 (16+ motors) chunks while powered and enabled, and immediately releases 100% of
 * tickets on disable, power loss, radius shrink, motor removal, or block removal.</p>
 */
public class ChunkLoaderBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final String MACHINE_ID = "chunk_loader";

    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_ENABLED = 3;
    public static final int DATA_RADIUS = 4;
    public static final int DATA_MAX_RADIUS = 5;
    public static final int DATA_LOADED_CHUNKS = 6;
    public static final int DATA_FE_PER_TICK = 7;
    public static final int DATA_COUNT = 8;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_ENABLED = "Enabled";
    private static final String TAG_RADIUS = "Radius";
    private static final String TAG_INVENTORY = "Inventory";

    private final MachineWorkTracker workTracker = new MachineWorkTracker();
    private final LoaderInventory inventory = new LoaderInventory(this);
    private final ModEnergyStorage energy = new ModEnergyStorage(
            LifeWave2Logic.CHUNK_BASE_CAPACITY, LifeWave2Logic.CHUNK_MAX_RECEIVE);

    private boolean enabled = true;
    private int requestedRadius;
    private int loadedChunks;
    private int currentFePerTick;
    private int chunkState = LifeWave2Logic.CHUNK_STATE_IDLE;
    private boolean ticketsSynced = false;
    private int lastSyncedRadius = -1;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            int motors = getMotorCount();
            int maxR = LifeWave2Logic.maxUnlockedChunkRadius(motors);
            int effR = LifeWave2Logic.effectiveChunkRadius(requestedRadius, motors);
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATE -> chunkState;
                case DATA_ENABLED -> enabled ? 1 : 0;
                case DATA_RADIUS -> effR;
                case DATA_MAX_RADIUS -> maxR;
                case DATA_LOADED_CHUNKS -> loadedChunks;
                case DATA_FE_PER_TICK -> currentFePerTick;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHUNK_LOADER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChunkLoaderBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        int motors = getMotorCount();
        int batteries = getBatteryCount();
        int ownerLvl = workTracker.ownerLevel(level, MACHINE_ID);

        int effRadius = LifeWave2Logic.effectiveChunkRadius(requestedRadius, motors);
        int desiredCount = LifeWave2Logic.chunkCountForRadius(effRadius);
        int cost = LifeWave2Logic.chunkEnergyPerTick(desiredCount, batteries, ownerLvl);
        currentFePerTick = enabled ? cost : 0;

        if (!enabled) {
            if (ticketsSynced || ChunkLoaderTickets.getActiveChunkCount(level, worldPosition) > 0) {
                ChunkLoaderTickets.releaseAll(level, worldPosition);
                ticketsSynced = false;
                lastSyncedRadius = -1;
            }
            loadedChunks = 0;
            chunkState = LifeWave2Logic.CHUNK_STATE_IDLE;
            updateLit(level, state, false);
            return;
        }

        if (energy.getEnergyStored() < cost) {
            if (ticketsSynced || ChunkLoaderTickets.getActiveChunkCount(level, worldPosition) > 0) {
                ChunkLoaderTickets.releaseAll(level, worldPosition);
                ticketsSynced = false;
                lastSyncedRadius = -1;
            }
            loadedChunks = 0;
            chunkState = LifeWave2Logic.CHUNK_STATE_NO_ENERGY;
            updateLit(level, state, false);
            return;
        }

        if (!ticketsSynced || lastSyncedRadius != effRadius) {
            int centerCx = worldPosition.getX() >> 4;
            int centerCz = worldPosition.getZ() >> 4;
            List<ChunkCoord> desired = LifeWave2Logic.chunkCoords(centerCx, centerCz, effRadius);
            ChunkLoaderTickets.syncForcedChunks(level, worldPosition, desired);
            loadedChunks = ChunkLoaderTickets.getActiveChunkCount(level, worldPosition);
            ticketsSynced = true;
            lastSyncedRadius = effRadius;
        }

        energy.consume(cost);
        chunkState = LifeWave2Logic.CHUNK_STATE_ACTIVE;
        workTracker.recordActiveTick(level, MACHINE_ID);
        updateLit(level, state, true);
        level.blockEntityChanged(worldPosition);
    }

    public boolean shouldKeepChunksLoaded() {
        return enabled && energy.getEnergyStored() > 0;
    }

    public void handleAction(int action) {
        int maxR = LifeWave2Logic.maxUnlockedChunkRadius(getMotorCount());
        switch (action) {
            case ChunkLoaderActionPayload.ACTION_TOGGLE -> {
                enabled = !enabled;
                if (!enabled && level instanceof ServerLevel sl) {
                    ChunkLoaderTickets.releaseAll(sl, worldPosition);
                    ticketsSynced = false;
                    lastSyncedRadius = -1;
                    loadedChunks = 0;
                    chunkState = LifeWave2Logic.CHUNK_STATE_IDLE;
                }
            }
            case ChunkLoaderActionPayload.ACTION_RADIUS_DEC ->
                    requestedRadius = Math.max(0, Math.min(maxR, requestedRadius) - 1);
            case ChunkLoaderActionPayload.ACTION_RADIUS_INC ->
                    requestedRadius = Math.min(maxR, Math.min(maxR, requestedRadius) + 1);
            default -> {
                return;
            }
        }
        setChanged();
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
        if (!value && level instanceof ServerLevel sl) {
            ChunkLoaderTickets.releaseAll(sl, worldPosition);
            ticketsSynced = false;
            lastSyncedRadius = -1;
            loadedChunks = 0;
            chunkState = LifeWave2Logic.CHUNK_STATE_IDLE;
        }
        setChanged();
    }

    public void setRequestedRadius(int r) {
        this.requestedRadius = Math.max(0, Math.min(LifeWave2Logic.CHUNK_MAX_RADIUS, r));
        setChanged();
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(AbstractLifeBlock.LIT) && state.getValue(AbstractLifeBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(AbstractLifeBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(LifeWave2Logic.CHUNK_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    public int getMotorCount() {
        return inventory.getStackInSlot(MOTOR_SLOT).getCount();
    }

    public int getBatteryCount() {
        return inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, MACHINE_ID);
        }
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(MACHINE_ID);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getLoadedChunks() {
        return loadedChunks;
    }

    public int getChunkState() {
        return chunkState;
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    @Override
    @Nullable
    public UUID getOwnerUuid() {
        return workTracker.ownerUuid();
    }

    @Override
    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        workTracker.setOwnerUuid(ownerUuid);
        setChanged();
    }

    @Override
    public String getMachineProgressId() {
        return MACHINE_ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.chunk_loader");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new ChunkLoaderMenu(containerId, playerInventory, this, dataAccess);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putBoolean(TAG_ENABLED, enabled);
        tag.putInt(TAG_RADIUS, requestedRadius);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        enabled = !tag.contains(TAG_ENABLED) || tag.getBoolean(TAG_ENABLED);
        requestedRadius = Math.max(0, Math.min(LifeWave2Logic.CHUNK_MAX_RADIUS, tag.getInt(TAG_RADIUS)));
        workTracker.load(tag);
    }

    private static final class LoaderInventory extends ItemStackHandler {
        private final ChunkLoaderBlockEntity owner;

        LoaderInventory(ChunkLoaderBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == MOTOR_SLOT) {
                return stack.is(ModItems.MOTOR.get());
            }
            if (slot == BATTERY_SLOT) {
                return stack.is(ModItems.BATTERY.get());
            }
            return false;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == MOTOR_SLOT || slot == BATTERY_SLOT) {
                return owner.getUpgradeLimit();
            }
            return super.getSlotLimit(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot == BATTERY_SLOT) {
                owner.syncBatteryCapacity();
            }
            owner.setChanged();
        }
    }
}

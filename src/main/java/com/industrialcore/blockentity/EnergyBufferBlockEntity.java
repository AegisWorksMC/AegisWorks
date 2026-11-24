package com.industrialcore.blockentity;

import com.industrialcore.block.EnergyBufferBlock;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.machine.BufferTier;
import com.industrialcore.menu.EnergyBufferMenu;
import com.industrialcore.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity of the Phase 17 large energy buffer.
 *
 * <p>The buffer stores surplus FE of a grid and feeds it back to its neighbours while the generator
 * is idle or the cable network is rebuilding. It is deliberately tiny in code and expensive in the
 * crafting recipe: a big FE tank that every factory wants, without new rules.</p>
 *
 * <p>Cost model: the six neighbours are cached exactly like the generator does. A capability lookup
 * is only repeated when the neighbour's block state changes or after its chunk was unloaded, so a
 * buffer next to static machines costs no lookups per tick.</p>
 */
public class EnergyBufferBlockEntity extends BlockEntity implements MenuProvider {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_PUSH = 2;
    public static final int DATA_COUNT = 3;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CAPACITY = "Capacity";

    private static final Direction[] DIRECTIONS = Direction.values();

    /** Phase 18: capacity and throughput come from the tier of the block. */
    private final BufferTier tier;
    private final ModEnergyStorage energy;
    private final BlockPos[] neighbourPositions = new BlockPos[DIRECTIONS.length];
    private final BlockState[] neighbourStates = new BlockState[DIRECTIONS.length];
    private final IEnergyStorage[] neighbourStorages = new IEnergyStorage[DIRECTIONS.length];

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_PUSH -> tier.pushPerTick();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server authoritative.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public EnergyBufferBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_BUFFER.get(), pos, state);
        this.tier = state.getBlock() instanceof EnergyBufferBlock block
                ? block.tier()
                : BufferTier.BASIC;
        this.energy = new ModEnergyStorage(tier.capacity(), tier.maxReceive());
        for (int index = 0; index < DIRECTIONS.length; index++) {
            neighbourPositions[index] = worldPosition.relative(DIRECTIONS[index]);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  EnergyBufferBlockEntity buffer) {
        if (level instanceof ServerLevel serverLevel) {
            buffer.tick(serverLevel);
        }
    }

    private void tick(ServerLevel level) {
        int capacity = Math.max(1, tier.capacity());
        if (energy.getMaxEnergyStored() != capacity) {
            energy.setCapacity(capacity);
            setChanged();
        }
        int maxReceive = Math.max(1, tier.maxReceive());
        if (energy.getMaxReceive() != maxReceive) {
            energy.setMaxReceive(maxReceive);
        }
        if (energy.getEnergyStored() > 0) {
            pushEnergy(level);
        }
    }

    /** Pushes FE into all neighbours that accept it. Returns true when any FE moved. */
    private boolean pushEnergy(ServerLevel level) {
        int budget = Math.max(0, tier.pushPerTick());
        if (budget <= 0) {
            return false;
        }
        boolean moved = false;
        for (int index = 0; index < DIRECTIONS.length; index++) {
            int stored = energy.getEnergyStored();
            if (stored <= 0) {
                break;
            }
            Direction direction = DIRECTIONS[index];
            BlockPos neighbourPos = neighbourPositions[index];
            if (!level.isLoaded(neighbourPos)) {
                // Never force-load chunks and never keep a reference into an unloaded chunk.
                neighbourStates[index] = null;
                neighbourStorages[index] = null;
                continue;
            }
            BlockState state = level.getBlockState(neighbourPos);
            if (state != neighbourStates[index]) {
                neighbourStates[index] = state;
                neighbourStorages[index] = level.getCapability(
                        Capabilities.EnergyStorage.BLOCK, neighbourPos, direction.getOpposite());
            }
            IEnergyStorage target = neighbourStorages[index];
            if (target == null || !target.canReceive()) {
                continue;
            }
            int accepted = target.receiveEnergy(Math.min(budget, stored), false);
            if (accepted > 0) {
                energy.consume(accepted);
                moved = true;
            }
        }
        if (moved) {
            level.blockEntityChanged(worldPosition);
        }
        return moved;
    }

    // ------------------------------------------------------------------
    // Menu / capabilities
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable(tier.translationKey());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new EnergyBufferMenu(containerId, playerInventory, this, dataAccess);
    }

    public ModEnergyStorage getEnergyStorage() {
        return energy;
    }

    /** Tier of this block (Phase 18 adds the reinforced tier). */
    public BufferTier getTier() {
        return tier;
    }

    public ContainerData getContainerData() {
        return dataAccess;
    }

    public int getEnergyStoredForTesting() {
        return energy.getEnergyStored();
    }

    public void setEnergyStored(int amount) {
        energy.setEnergy(amount);
        setChanged();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.putInt(TAG_CAPACITY, energy.getMaxEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_CAPACITY)) {
            energy.setCapacity(Math.max(1, tag.getInt(TAG_CAPACITY)));
        }
        if (tag.contains(TAG_ENERGY)) {
            energy.setEnergy(tag.getInt(TAG_ENERGY));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // The GUI reads the FE through the container data.
        return new CompoundTag();
    }
}

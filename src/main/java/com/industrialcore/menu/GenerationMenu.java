package com.industrialcore.menu;

import com.industrialcore.block.GenerationBlock;
import com.industrialcore.block.RecoveryBlock;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.RecoveryKind;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared menu of the ten power devices (Phase 33). Fuel generators expose one fuel slot,
 * the six block-entity types without fuel exposure expose none - the layout decides from the
 * block instance, which is identical on client and server. GUI values arrive through the
 * shared {@link ContainerData} (energy, capacity, burn, burn total, rate, state), exactly
 * like the original generator menu.
 */
public class GenerationMenu extends AbstractContainerMenu {
    /** True when the device behind {@code pos} has a fuel slot (fuel generators only). */
    private final boolean hasFuelSlot;
    /** Phase 41: the geothermal generator draws a heat-source indicator instead of a slot. */
    private final boolean geothermal;
    /** Phase 42: which condition device this is, for the GUI's source indicator. */
    private final boolean hydro;
    private final boolean solar;
    private final boolean wind;
    @Nullable
    private final GenerationKind generationKind;
    @Nullable
    private final RecoveryKind recoveryKind;
    private final ContainerData data;
    private final BlockPos blockPos;
    @Nullable
    private final BlockEntity blockEntity;

    public GenerationMenu(int containerId, Inventory playerInventory, net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos(), null, null,
                buffer.isReadable(2) ? decodeGenKind(buffer.readByte()) : null,
                buffer.isReadable(1) ? decodeRecKind(buffer.readByte()) : null);
    }

    public GenerationMenu(int containerId, Inventory playerInventory, GenerationBlockEntity device, ContainerData data) {
        this(containerId, playerInventory, device.getBlockPos(), device, data, null, null);
    }

    public GenerationMenu(int containerId, Inventory playerInventory, RecoveryBlockEntity device, ContainerData data) {
        this(containerId, playerInventory, device.getBlockPos(), device, data, null, null);
    }

    @Nullable
    private static GenerationKind decodeGenKind(int ord) {
        GenerationKind[] vals = GenerationKind.values();
        return ord >= 0 && ord < vals.length ? vals[ord] : null;
    }

    @Nullable
    private static RecoveryKind decodeRecKind(int ord) {
        RecoveryKind[] vals = RecoveryKind.values();
        return ord >= 0 && ord < vals.length ? vals[ord] : null;
    }

    private GenerationMenu(int containerId, Inventory playerInventory, BlockPos pos,
                           @Nullable BlockEntity device, @Nullable ContainerData providedData,
                           @Nullable GenerationKind explicitGenKind, @Nullable RecoveryKind explicitRecKind) {
        super(ModMenus.GENERATION.get(), containerId);
        Level level = playerInventory.player.level();
        Block block = level.getBlockState(pos).getBlock();
        this.generationKind = explicitGenKind != null ? explicitGenKind
                : (block instanceof GenerationBlock gb ? gb.kind() : null);
        this.recoveryKind = explicitRecKind != null ? explicitRecKind
                : (block instanceof RecoveryBlock rb ? rb.kind() : null);
        this.hasFuelSlot = this.generationKind != null && this.generationKind.hasFuel();
        this.geothermal = this.generationKind != null && this.generationKind.needsLava();
        this.hydro = this.generationKind == com.industrialcore.machine.GenerationKind.HYDRO;
        this.solar = this.generationKind == com.industrialcore.machine.GenerationKind.SOLAR;
        this.wind = this.generationKind == com.industrialcore.machine.GenerationKind.WIND;
        this.blockPos = pos.immutable();
        this.blockEntity = device != null ? device
                : level.getBlockEntity(pos);
        // On the client the values arrive as data slots; a bridge over the client block entity
        // would drop them, because only the server device knows the live values.
        this.data = providedData != null ? providedData
                : level.isClientSide ? new SimpleContainerData(GenerationBlockEntity.DATA_COUNT)
                : resolveData(this.blockEntity);

        // One fuel slot for fuel generators; Nuclear Multiblock also adds a dedicated water-bucket slot.
        if (this.hasFuelSlot) {
            int fallbackSlots = isNuclearMultiblock() ? 2 : 1;
            net.neoforged.neoforge.items.IItemHandler fuelHandler =
                    this.blockEntity instanceof GenerationBlockEntity generation
                            ? generation.getInventory()
                            : new net.neoforged.neoforge.items.ItemStackHandler(fallbackSlots);
            Rect fuel = GenerationLayout.FUEL_SLOT;
            addSlot(new net.neoforged.neoforge.items.SlotItemHandler(
                    fuelHandler, GenerationBlockEntity.FUEL_SLOT, fuel.x(), fuel.y()));
            if (isNuclearMultiblock()) {
                Rect bucket = GenerationLayout.NUCLEAR_BUCKET_SLOT;
                addSlot(new net.neoforged.neoforge.items.SlotItemHandler(
                        fuelHandler, GenerationBlockEntity.BUCKET_SLOT, bucket.x(), bucket.y()));
            }
        }

        // Player inventory, from the shared grid: same position on every machine panel.
        for (int index = 0; index < 36; ++index) {
            Rect rect = GenerationLayout.playerSlot(index);
            int inventoryIndex = index < 27 ? index + 9 : index - 27;
            addSlot(new Slot(playerInventory, inventoryIndex, rect.x(), rect.y()));
        }

        addDataSlots(this.data);
    }

    private static ContainerData resolveData(@Nullable BlockEntity device) {
        if (device instanceof GenerationBlockEntity generation) {
            return asData(generation);
        }
        if (device instanceof RecoveryBlockEntity recovery) {
            return asData(recovery);
        }
        return new SimpleContainerData(GenerationBlockEntity.DATA_COUNT);
    }

    // Feeds either device type through the shared index layout of GenerationBlockEntity.
    private static ContainerData asData(GenerationBlockEntity generation) {
        return new DataBridge(generation, null);
    }

    private static ContainerData asData(RecoveryBlockEntity recovery) {
        return new DataBridge(null, recovery);
    }

    /** Bridges both device types onto the shared data indices. */
    private static final class DataBridge implements ContainerData {
        @Nullable
        private final GenerationBlockEntity generation;
        @Nullable
        private final RecoveryBlockEntity recovery;

        private DataBridge(@Nullable GenerationBlockEntity generation, @Nullable RecoveryBlockEntity recovery) {
            this.generation = generation;
            this.recovery = recovery;
        }

        @Override
        public int get(int index) {
            if (generation != null) {
                return switch (index) {
                    case GenerationBlockEntity.DATA_ENERGY -> generation.getEnergyStorage().getEnergyStored();
                    case GenerationBlockEntity.DATA_CAPACITY -> generation.getEnergyStorage().getMaxEnergyStored();
                    case GenerationBlockEntity.DATA_BURN -> generation.getBurnTime();
                    case GenerationBlockEntity.DATA_BURN_TOTAL -> generation.getBurnTimeTotal();
                    case GenerationBlockEntity.DATA_RATE -> generation.getRate();
                    case GenerationBlockEntity.DATA_STATE -> generation.getStatus().ordinal();
                    case GenerationBlockEntity.DATA_MB_FORMED -> generation.isMultiblockFormed() ? 1 : 0;
                    case GenerationBlockEntity.DATA_MB_REASON -> generation.getMultiblockFailReason().ordinal();
                    case GenerationBlockEntity.DATA_MB_DIMS -> generation.getMultiblockDimsPacked();
                    case GenerationBlockEntity.DATA_MB_CORES -> generation.getMultiblockCoreCells();
                    case GenerationBlockEntity.DATA_MB_COOLANT -> generation.getMultiblockCoolantBlocks();
                    case GenerationBlockEntity.DATA_MB_MULT_X100 -> generation.getMultiblockMultX100();
                    case GenerationBlockEntity.DATA_COOLANT_MB -> generation.getCoolantMb();
                    case GenerationBlockEntity.DATA_OVERDRIVE -> generation.isOverdriveMode() ? 1 : 0;
                    case GenerationBlockEntity.DATA_COOLDOWN -> generation.getOverdriveCooldownTicks();
                    default -> 0;
                };
            }
            if (recovery != null) {
                return switch (index) {
                    case GenerationBlockEntity.DATA_ENERGY -> recovery.getEnergyStorage().getEnergyStored();
                    case GenerationBlockEntity.DATA_CAPACITY -> recovery.getEnergyStorage().getMaxEnergyStored();
                    case GenerationBlockEntity.DATA_RATE -> recovery.getRate();
                    case GenerationBlockEntity.DATA_STATE -> recovery.getStatus().ordinal();
                    default -> 0;
                };
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
            // Server-driven values only; the client copies them from sync packets.
        }

        @Override
        public int getCount() {
            return GenerationBlockEntity.DATA_COUNT;
        }
    }

    public boolean hasFuelSlot() {
        return this.hasFuelSlot;
    }

    /** Phase 41: true for the geothermal generator (heat-source indicator in the GUI). */
    public boolean isGeothermal() {
        return this.geothermal;
    }

    /** Phase 42: true for the hydro generator (water-source indicator in the GUI). */
    public boolean isHydro() {
        return this.hydro;
    }

    /** Phase 42: true for the solar generator (daylight indicator in the GUI). */
    public boolean isSolar() {
        return this.solar;
    }

    /** Phase 42: true for the wind turbine (open-air indicator in the GUI). */
    public boolean isWind() {
        return this.wind;
    }

    @Nullable
    public GenerationKind getGenerationKind() {
        return this.generationKind;
    }

    @Nullable
    public RecoveryKind getRecoveryKind() {
        return this.recoveryKind;
    }

    @Nullable
    public BlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    // GUI accessors used by GenerationScreen; values arrive through the shared data slots.
    public int getEnergyStored() {
        return this.data.get(GenerationBlockEntity.DATA_ENERGY);
    }

    public int getMaxEnergy() {
        return this.data.get(GenerationBlockEntity.DATA_CAPACITY);
    }

    public int getBurnProgress() {
        return this.data.get(GenerationBlockEntity.DATA_BURN);
    }

    public int getBurnTotal() {
        return this.data.get(GenerationBlockEntity.DATA_BURN_TOTAL);
    }

    public int getRate() {
        return this.data.get(GenerationBlockEntity.DATA_RATE);
    }

    public int getDeviceState() {
        return this.data.get(GenerationBlockEntity.DATA_STATE);
    }

    public boolean isNuclearMultiblock() {
        return this.generationKind == GenerationKind.NUCLEAR;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public boolean isMultiblockFormed() {
        return this.data.get(GenerationBlockEntity.DATA_MB_FORMED) != 0;
    }

    public int getMultiblockFailReason() {
        return this.data.get(GenerationBlockEntity.DATA_MB_REASON);
    }

    public int getMultiblockDimsPacked() {
        return this.data.get(GenerationBlockEntity.DATA_MB_DIMS);
    }

    public int getMultiblockCoreCells() {
        return this.data.get(GenerationBlockEntity.DATA_MB_CORES);
    }

    public int getMultiblockCoolantBlocks() {
        return this.data.get(GenerationBlockEntity.DATA_MB_COOLANT);
    }

    public int getMultiblockMultX100() {
        return this.data.get(GenerationBlockEntity.DATA_MB_MULT_X100);
    }

    /** Phase 105.6: Stored cooling water volume (mB, 0..16,000) in the nuclear coolant tank. */
    public int getCoolantMb() {
        return this.data.get(GenerationBlockEntity.DATA_COOLANT_MB);
    }

    /** Phase 114: True when Nuclear Overdrive Mode is enabled. */
    public boolean isOverdriveMode() {
        return this.data.get(GenerationBlockEntity.DATA_OVERDRIVE) != 0;
    }

    /** Phase 114: Remaining emergency cooldown ticks (0 when clear). */
    public int getOverdriveCooldownTicks() {
        return Math.max(0, this.data.get(GenerationBlockEntity.DATA_COOLDOWN));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack current = slot.getItem();
            result = current.copy();
            int machineSlots = isNuclearMultiblock() ? 2 : (hasFuelSlot ? 1 : 0);
            if (index < machineSlots) {
                if (!moveItemStackTo(current, machineSlots, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (isNuclearMultiblock() && current.is(net.minecraft.world.item.Items.WATER_BUCKET)) {
                if (!moveItemStackTo(current, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(current, 0, Math.min(1, machineSlots), false)) {
                return ItemStack.EMPTY;
            }
            if (current.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (current.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, current);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity != null) {
            double maxDistSq = isNuclearMultiblock() ? 196.0D : 64.0D;
            return !blockEntity.isRemoved()
                    && Vec3.atCenterOf(blockEntity.getBlockPos())
                            .distanceToSqr(player.position()) <= maxDistSq;
        }
        return true;
    }
}

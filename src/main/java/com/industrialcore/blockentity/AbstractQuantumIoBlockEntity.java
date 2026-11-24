package com.industrialcore.blockentity;

import com.industrialcore.blockentity.QuantumStorageBlockEntity.FluidKey;
import com.industrialcore.blockentity.QuantumStorageBlockEntity.ItemKey;
import com.industrialcore.energy.ModEnergyStorage;
import com.industrialcore.level.IOwnedMachine;
import com.industrialcore.level.MachineWorkTracker;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.level.UpgradeInventoryUtil;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.QuantumIoMenu;
import com.industrialcore.network.QuantumFreqActionPayload;
import com.industrialcore.network.QuantumIoActionPayload;
import com.industrialcore.network.QuantumIoSyncPayload;
import com.industrialcore.network.QuantumStorageSyncPayload.ChannelSummary;
import com.industrialcore.registry.ModItems;
import com.industrialcore.storage.QuantumNetLogic;
import com.industrialcore.storage.QuantumNetLogic.QuantumChannel;
import com.industrialcore.storage.QuantumNetworkData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 96: Shared base BlockEntity for {@code quantum_importer} and {@code quantum_exporter}.
 */
public abstract class AbstractQuantumIoBlockEntity extends BlockEntity implements MenuProvider, IOwnedMachine {
    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_FACING = 3;
    public static final int DATA_INTERVAL = 4;
    public static final int DATA_MAX_ITEMS = 5;
    public static final int DATA_MAX_FLUID = 6;
    public static final int DATA_BLACKLIST = 7;
    public static final int DATA_COUNT = 8;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_INVENTORY = "Inventory";
    private static final String TAG_CHANNEL = "ChannelId";
    private static final String TAG_BLACKLIST = "Blacklist";
    private static final String TAG_FILTERS = "Filters";

    protected final MachineWorkTracker workTracker = new MachineWorkTracker();
    protected final UpgradeInventory inventory = new UpgradeInventory(this);
    protected final ModEnergyStorage energy = new ModEnergyStorage(
            QuantumNetLogic.IO_BASE_CAPACITY, QuantumNetLogic.IO_MAX_RECEIVE);

    protected final ItemStack[] filterSlots = new ItemStack[QuantumNetLogic.FILTER_SLOTS];
    protected String channelId = "";
    protected boolean blacklist;
    protected int ioState = QuantumNetLogic.IO_STATE_IDLE;
    protected int cooldown;
    protected long syncRevision = 1L;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            int motors = getMotorCount();
            int ownerLvl = (level instanceof ServerLevel sl)
                    ? workTracker.ownerLevel(sl, getMachineProgressId()) : 1;
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_STATE -> ioState;
                case DATA_FACING -> getFacing().get3DDataValue();
                case DATA_INTERVAL -> QuantumNetLogic.ioIntervalTicks(motors, ownerLvl);
                case DATA_MAX_ITEMS -> QuantumNetLogic.ioMaxItemsPerCycle(motors);
                case DATA_MAX_FLUID -> QuantumNetLogic.ioMaxFluidMbPerCycle(motors);
                case DATA_BLACKLIST -> blacklist ? 1 : 0;
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

    protected AbstractQuantumIoBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                           boolean defaultBlacklist) {
        super(type, pos, state);
        this.blacklist = defaultBlacklist;
        for (int i = 0; i < filterSlots.length; i++) {
            filterSlots[i] = ItemStack.EMPTY;
        }
    }

    public abstract boolean isExporter();

    protected abstract boolean performCycle(ServerLevel level, BlockPos targetPos, Direction targetSide,
                                            QuantumChannel<ItemKey, FluidKey> channel,
                                            int maxItems, int maxFluidMb);

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractQuantumIoBlockEntity be) {
        if (level instanceof ServerLevel sl) {
            be.tickServer(sl, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        syncBatteryCapacity();
        if (channelId.isEmpty()) {
            ioState = QuantumNetLogic.IO_STATE_NO_CHANNEL;
            updateLit(level, state, false);
            return;
        }
        QuantumNetworkData net = QuantumNetworkData.get(level);
        QuantumChannel<ItemKey, FluidKey> channel = net.getChannel(channelId);
        String ownerStr = workTracker.ownerUuid() != null ? workTracker.ownerUuid().toString() : "";
        if (channel == null || !channel.canAccess(ownerStr)) {
            ioState = QuantumNetLogic.IO_STATE_NO_CHANNEL;
            updateLit(level, state, false);
            return;
        }

        int batteries = getBatteryCount();
        int motors = getMotorCount();
        int ownerLvl = workTracker.ownerLevel(level, getMachineProgressId());
        int opCost = QuantumNetLogic.ioEnergyPerOp(batteries, ownerLvl);
        if (energy.getEnergyStored() < opCost) {
            ioState = QuantumNetLogic.IO_STATE_NO_ENERGY;
            updateLit(level, state, false);
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        Direction facing = getFacing();
        BlockPos targetPos = worldPosition.relative(facing);
        if (!level.isLoaded(targetPos)) {
            ioState = QuantumNetLogic.IO_STATE_WAITING;
            updateLit(level, state, false);
            return;
        }

        int maxItems = QuantumNetLogic.ioMaxItemsPerCycle(motors);
        int maxFluid = QuantumNetLogic.ioMaxFluidMbPerCycle(motors);
        boolean moved = performCycle(level, targetPos, facing.getOpposite(), channel, maxItems, maxFluid);
        cooldown = QuantumNetLogic.ioIntervalTicks(motors, ownerLvl);

        if (moved) {
            energy.consume(opCost);
            channel.bumpRevision();
            net.markMutated();
            workTracker.recordActiveTick(level, getMachineProgressId());
            ioState = QuantumNetLogic.IO_STATE_ACTIVE;
            updateLit(level, state, true);
            setChanged();
        } else {
            ioState = QuantumNetLogic.IO_STATE_WAITING;
            updateLit(level, state, false);
        }
    }

    public Direction getFacing() {
        BlockState st = getBlockState();
        if (st.hasProperty(BlockStateProperties.FACING)) {
            return st.getValue(BlockStateProperties.FACING);
        }
        return Direction.NORTH;
    }

    private void updateLit(ServerLevel level, BlockState state, boolean lit) {
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private void syncBatteryCapacity() {
        int desired = UpgradeMath.scaledCapacity(QuantumNetLogic.IO_BASE_CAPACITY, getBatteryCount());
        if (energy.getMaxEnergyStored() != desired) {
            energy.setCapacity(desired);
        }
    }

    // ------------------------------------------------------------------
    // Filter & Frequency actions
    // ------------------------------------------------------------------

    public void setChannelId(@Nullable String nextId) {
        this.channelId = nextId == null ? "" : nextId.trim();
        this.syncRevision++;
        setChanged();
    }

    public String getChannelId() {
        return channelId;
    }

    public boolean isBlacklist() {
        return blacklist;
    }

    public void setBlacklist(boolean value) {
        this.blacklist = value;
        this.syncRevision++;
        setChanged();
    }

    public ItemStack getFilterSlot(int index) {
        if (index < 0 || index >= filterSlots.length) {
            return ItemStack.EMPTY;
        }
        return filterSlots[index];
    }

    public void setFilterSlot(int index, ItemStack stack) {
        if (index < 0 || index >= filterSlots.length) {
            return;
        }
        filterSlots[index] = (stack == null || stack.isEmpty()) ? ItemStack.EMPTY : stack.copyWithCount(1);
        this.syncRevision++;
        setChanged();
    }

    public void clearAllFilters() {
        for (int i = 0; i < filterSlots.length; i++) {
            filterSlots[i] = ItemStack.EMPTY;
        }
        this.syncRevision++;
        setChanged();
    }

    public void handleIoAction(ServerPlayer player, QuantumIoActionPayload payload) {
        switch (payload.action()) {
            case QuantumIoActionPayload.ACTION_SET_FILTER -> setFilterSlot(payload.slotIndex(), payload.filterStack());
            case QuantumIoActionPayload.ACTION_CLEAR_FILTER -> setFilterSlot(payload.slotIndex(), ItemStack.EMPTY);
            case QuantumIoActionPayload.ACTION_CLEAR_ALL -> clearAllFilters();
            case QuantumIoActionPayload.ACTION_TOGGLE_BLACKLIST -> setBlacklist(!blacklist);
            default -> {
                return;
            }
        }
        syncToPlayer(player);
    }

    public void handleFreqAction(ServerPlayer player, QuantumFreqActionPayload payload) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        QuantumNetworkData net = QuantumNetworkData.get(sl);
        String viewerUuid = player.getUUID().toString();
        switch (payload.action()) {
            case QuantumFreqActionPayload.ACTION_CONNECT -> {
                QuantumChannel<ItemKey, FluidKey> ch = net.getChannel(payload.value());
                if (ch != null && ch.canAccess(viewerUuid)) {
                    setChannelId(ch.channelId());
                }
            }
            case QuantumFreqActionPayload.ACTION_DISCONNECT -> setChannelId("");
            default -> {}
        }
        syncToPlayer(player);
    }

    public void syncToPlayer(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        QuantumNetworkData net = QuantumNetworkData.get(sl);
        String viewerUuid = player.getUUID().toString();
        QuantumChannel<ItemKey, FluidKey> active = channelId.isEmpty() ? null : net.getChannel(channelId);
        String activeName = active != null ? active.name() : "";

        List<ItemStack> filters = new ArrayList<>(filterSlots.length);
        for (ItemStack s : filterSlots) {
            filters.add(s.copy());
        }

        List<QuantumChannel<ItemKey, FluidKey>> visible = net.visibleChannelsFor(viewerUuid);
        List<ChannelSummary> summaries = new ArrayList<>(visible.size());
        for (QuantumChannel<ItemKey, FluidKey> ch : visible) {
            summaries.add(new ChannelSummary(
                    ch.channelId(), ch.name(), ch.isPublic(), ch.isOwner(viewerUuid),
                    ch.storage().usedTypes(), ch.storage().maxTypes()));
        }

        PacketDistributor.sendToPlayer(player, new QuantumIoSyncPayload(
                worldPosition, active != null ? active.channelId() : "", activeName,
                blacklist, filters, summaries));
    }

    public long getSyncRevision() {
        if (level instanceof ServerLevel sl) {
            return syncRevision * 31L + QuantumNetworkData.get(sl).globalRevision();
        }
        return syncRevision;
    }

    // ------------------------------------------------------------------
    // Upgrades, energy, ownership & MenuProvider
    // ------------------------------------------------------------------

    public int getMotorCount() {
        return inventory.getStackInSlot(MOTOR_SLOT).getCount();
    }

    public int getBatteryCount() {
        return inventory.getStackInSlot(BATTERY_SLOT).getCount();
    }

    public int getUpgradeLimit() {
        if (level instanceof ServerLevel sl) {
            return workTracker.upgradeLimit(sl, getMachineProgressId());
        }
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(getMachineProgressId());
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    @Nullable
    @Override
    public UUID getOwnerUuid() {
        return workTracker.ownerUuid();
    }

    @Override
    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        workTracker.setOwnerUuid(ownerUuid);
        setChanged();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        workTracker.setCreativeOperator(player.isCreative());
        return new QuantumIoMenu(containerId, playerInventory, this, dataAccess);
    }

    // ------------------------------------------------------------------
    // NBT Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_ENERGY, energy.getEnergyStored());
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        if (!channelId.isEmpty()) {
            tag.putString(TAG_CHANNEL, channelId);
        }
        tag.putBoolean(TAG_BLACKLIST, blacklist);
        ListTag fList = new ListTag();
        for (int i = 0; i < filterSlots.length; i++) {
            CompoundTag ft = new CompoundTag();
            ft.putInt("Slot", i);
            if (!filterSlots[i].isEmpty()) {
                ft.put("Stack", filterSlots[i].saveOptional(registries));
            }
            fList.add(ft);
        }
        tag.put(TAG_FILTERS, fList);
        workTracker.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UpgradeInventoryUtil.loadWithMinSize(inventory, registries, tag.getCompound(TAG_INVENTORY), SLOT_COUNT);
        syncBatteryCapacity();
        energy.setEnergy(tag.getInt(TAG_ENERGY));
        channelId = tag.getString(TAG_CHANNEL);
        if (tag.contains(TAG_BLACKLIST)) {
            blacklist = tag.getBoolean(TAG_BLACKLIST);
        }
        for (int i = 0; i < filterSlots.length; i++) {
            filterSlots[i] = ItemStack.EMPTY;
        }
        ListTag fList = tag.getList(TAG_FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < fList.size(); i++) {
            CompoundTag ft = fList.getCompound(i);
            int slot = ft.getInt("Slot");
            if (slot >= 0 && slot < filterSlots.length && ft.contains("Stack")) {
                filterSlots[slot] = ItemStack.parseOptional(registries, ft.getCompound("Stack"));
            }
        }
        workTracker.load(tag);
    }

    protected static final class UpgradeInventory extends ItemStackHandler {
        private final AbstractQuantumIoBlockEntity owner;

        UpgradeInventory(AbstractQuantumIoBlockEntity owner) {
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

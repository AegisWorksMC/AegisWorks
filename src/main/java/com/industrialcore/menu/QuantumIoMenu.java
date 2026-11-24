package com.industrialcore.menu;

import com.industrialcore.blockentity.AbstractQuantumIoBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.QuantumIoSyncPayload;
import com.industrialcore.network.QuantumStorageSyncPayload.ChannelSummary;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import com.industrialcore.storage.QuantumIoLayout;
import com.industrialcore.storage.QuantumNetLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 96: Shared Menu for {@code quantum_importer} and {@code quantum_exporter}.
 */
public class QuantumIoMenu extends AbstractContainerMenu {
    public static final int MOTOR_SLOT = 0;
    public static final int BATTERY_SLOT = 1;
    public static final int MACHINE_SLOTS = 2;

    private final BlockPos pos;
    private final boolean exporter;
    private final Player player;
    @Nullable
    private final AbstractQuantumIoBlockEntity blockEntity;
    private final ContainerData data;
    private long lastSyncedRev = -1L;

    // Client-synced snapshot
    private String clientChannelId = "";
    private String clientChannelName = "";
    private boolean clientBlacklist;
    private List<ItemStack> clientFilters = emptyFilters();
    private List<ChannelSummary> clientChannels = List.of();

    private static List<ItemStack> emptyFilters() {
        List<ItemStack> list = new ArrayList<>(QuantumNetLogic.FILTER_SLOTS);
        for (int i = 0; i < QuantumNetLogic.FILTER_SLOTS; i++) {
            list.add(ItemStack.EMPTY);
        }
        return list;
    }

    public QuantumIoMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos(), buf.readBoolean());
    }

    private QuantumIoMenu(int containerId, Inventory playerInventory, BlockPos pos, boolean exporter) {
        this(containerId, playerInventory, pos, exporter, resolve(playerInventory, pos),
                new SimpleContainerData(AbstractQuantumIoBlockEntity.DATA_COUNT));
    }

    public QuantumIoMenu(int containerId, Inventory playerInventory,
                         AbstractQuantumIoBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be.isExporter(), be, data);
    }

    private QuantumIoMenu(int containerId, Inventory playerInventory, BlockPos pos, boolean exporter,
                          @Nullable AbstractQuantumIoBlockEntity be, ContainerData data) {
        super(ModMenus.QUANTUM_IO.get(), containerId);
        this.pos = pos;
        this.exporter = exporter;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;
        this.clientBlacklist = !exporter;

        IItemHandler inv = be != null ? be.getInventory() : new ItemStackHandler(MACHINE_SLOTS);

        Rect mr = QuantumIoLayout.MOTOR_SLOT;
        this.addSlot(new SlotItemHandler(inv, MOTOR_SLOT, mr.x(), mr.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.MOTOR.get());
            }

            @Override
            public int getMaxStackSize() {
                return upgradeLimit();
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return upgradeLimit();
            }
        });

        Rect br = QuantumIoLayout.BATTERY_SLOT;
        this.addSlot(new SlotItemHandler(inv, BATTERY_SLOT, br.x(), br.y()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BATTERY.get());
            }

            @Override
            public int getMaxStackSize() {
                return upgradeLimit();
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return upgradeLimit();
            }
        });

        int idx = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Rect r = QuantumIoLayout.playerSlot(idx++);
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, r.x(), r.y()));
            }
        }
        for (int col = 0; col < 9; col++) {
            Rect r = QuantumIoLayout.playerSlot(idx++);
            this.addSlot(new Slot(playerInventory, col, r.x(), r.y()));
        }

        this.addDataSlots(data);
    }

    @Nullable
    private static AbstractQuantumIoBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof AbstractQuantumIoBlockEntity be ? be : null;
    }

    public int upgradeLimit() {
        if (blockEntity != null && !player.level().isClientSide) {
            return blockEntity.getUpgradeLimit();
        }
        return UpgradeCapBridge.clientUpgradeLimit.applyAsInt(machineId());
    }

    public String machineId() {
        return exporter ? "quantum_exporter" : "quantum_importer";
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (blockEntity != null && player instanceof ServerPlayer sp) {
            long rev = blockEntity.getSyncRevision();
            if (rev != lastSyncedRev) {
                lastSyncedRev = rev;
                blockEntity.syncToPlayer(sp);
            }
        }
    }

    public void applyClientSync(QuantumIoSyncPayload payload) {
        if (!payload.pos().equals(this.pos)) {
            return;
        }
        this.clientChannelId = payload.activeChannelId();
        this.clientChannelName = payload.activeChannelName();
        this.clientBlacklist = payload.blacklist();
        List<ItemStack> next = emptyFilters();
        for (int i = 0; i < Math.min(next.size(), payload.filters().size()); i++) {
            next.set(i, payload.filters().get(i));
        }
        this.clientFilters = next;
        this.clientChannels = List.copyOf(payload.channels());
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!this.moveItemStackTo(stack, MACHINE_SLOTS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.is(ModItems.MOTOR.get())) {
                if (!this.moveItemStackTo(stack, MOTOR_SLOT, MOTOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(ModItems.BATTERY.get())) {
                if (!this.moveItemStackTo(stack, BATTERY_SLOT, BATTERY_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        if (blockEntity != null) {
            return !blockEntity.isRemoved()
                    && Vec3.atCenterOf(this.pos).distanceToSqr(p.position()) <= 64.0D;
        }
        return p.level().isClientSide
                && Vec3.atCenterOf(this.pos).distanceToSqr(p.position()) <= 64.0D;
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public boolean isExporter() {
        return exporter;
    }

    public int getEnergyStored() {
        return data.get(AbstractQuantumIoBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(AbstractQuantumIoBlockEntity.DATA_CAPACITY));
    }

    public int getIoState() {
        return data.get(AbstractQuantumIoBlockEntity.DATA_STATE);
    }

    public int getFacingIndex() {
        return data.get(AbstractQuantumIoBlockEntity.DATA_FACING);
    }

    public int getIntervalTicks() {
        return data.get(AbstractQuantumIoBlockEntity.DATA_INTERVAL);
    }

    public int getMaxItemsPerCycle() {
        return data.get(AbstractQuantumIoBlockEntity.DATA_MAX_ITEMS);
    }

    public int getMaxFluidPerCycle() {
        return data.get(AbstractQuantumIoBlockEntity.DATA_MAX_FLUID);
    }

    public boolean isBlacklist() {
        return clientBlacklist;
    }

    public String getClientChannelId() {
        return clientChannelId;
    }

    public String getClientChannelName() {
        return clientChannelName;
    }

    public List<ItemStack> getClientFilters() {
        return clientFilters;
    }

    public List<ChannelSummary> getClientChannels() {
        return clientChannels;
    }

    public int getMotorCount() {
        return this.slots.get(MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(BATTERY_SLOT).getItem().getCount();
    }
}

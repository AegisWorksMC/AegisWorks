package com.industrialcore.menu;

import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.machine.QuarryStatus;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

/**
 * GUI menu for the quarry. Values are read through ContainerData so the client always sees
 * the server's numbers; all actions are sent as explicit network payloads.
 */
public class QuarryMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return QuarryBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    /** GUI page whose slots are active; purely a client-side display concern. */
    private int visibleTab = QuarryLayout.TAB_SETTINGS;
    @Nullable
    private final QuarryBlockEntity blockEntity;

    public QuarryMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public QuarryMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public QuarryMenu(int containerId, Inventory playerInventory, QuarryBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private QuarryMenu(int containerId, Inventory playerInventory, BlockPos pos,
                       @Nullable QuarryBlockEntity blockEntity, @Nullable ContainerData data) {
        super(ModMenus.QUARRY.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(QuarryBlockEntity.TOTAL_SLOTS);

        // All positions come from QuarryLayout, so the menu and the screen cannot disagree.
        // Slots of the inactive tab stay in place but report isActive() == false, so neither
        // the screen nor the click handling can use them (see TabSlot below).

        // Upgrade slots (settings tab).
        QuarryLayout.Rect motor = QuarryLayout.MOTOR_SLOT;
        QuarryLayout.Rect battery = QuarryLayout.BATTERY_SLOT;
        this.addSlot(new TabSlot(machineInventory, QuarryBlockEntity.MOTOR_SLOT,
                motor.x(), motor.y(), QuarryLayout.TAB_SETTINGS));
        this.addSlot(new TabSlot(machineInventory, QuarryBlockEntity.BATTERY_SLOT,
                battery.x(), battery.y(), QuarryLayout.TAB_SETTINGS));

        // Collected loot (2 rows of 9, status tab).
        for (int index = 0; index < QuarryBlockEntity.OUTPUT_SLOTS; index++) {
            QuarryLayout.Rect rect = QuarryLayout.outputSlot(index, QuarryLayout.TAB_STATUS);
            this.addSlot(new TabSlot(machineInventory, QuarryBlockEntity.OUTPUT_START + index,
                    rect.x(), rect.y(), QuarryLayout.TAB_STATUS));
        }

        // Player inventory + hotbar (visible on both tabs).
        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                QuarryLayout.Rect rect = QuarryLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            QuarryLayout.Rect rect = QuarryLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    /**
     * Vanilla applies synced menu data through {@link ContainerData#set(int, int)}. The block
     * entity's own data is read-only on the client (it is server authoritative and its set() is a
     * no-op on purpose), so the client menu needs its own writable cache. Without this the GUI
     * only refreshed when a full block entity update happened to arrive (e.g. after changing a
     * setting), which is exactly the "energy does not update" bug.
     */
    private static ContainerData resolveData(Inventory playerInventory, @Nullable QuarryBlockEntity blockEntity,
                                            @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(QuarryBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static QuarryBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof QuarryBlockEntity quarry ? quarry : null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity != null) {
            return !this.blockEntity.isRemoved()
                    && Vec3.atCenterOf(this.pos).distanceToSqr(player.position()) <= 64.0D;
        }
        return player.level().isClientSide
                && Vec3.atCenterOf(this.pos).distanceToSqr(player.position()) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = QuarryBlockEntity.TOTAL_SLOTS;
        boolean moved;

        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else if (stack.is(ModItems.MOTOR.get())) {
            moved = this.moveItemStackTo(stack, QuarryBlockEntity.MOTOR_SLOT, QuarryBlockEntity.MOTOR_SLOT + 1, false);
        } else if (stack.is(ModItems.BATTERY.get())) {
            moved = this.moveItemStackTo(stack, QuarryBlockEntity.BATTERY_SLOT, QuarryBlockEntity.BATTERY_SLOT + 1, false);
        } else {
            moved = this.moveItemStackTo(stack, QuarryBlockEntity.OUTPUT_START, machineSlots, false);
        }

        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * Selects the page whose slots are interactive. Only the client screen calls this; the server
     * menu keeps the default page, and item handling never depends on it (slot indices and shift
     * clicking are unchanged).
     */
    public void setVisibleTab(int tab) {
        this.visibleTab = tab;
    }

    public int getVisibleTab() {
        return this.visibleTab;
    }

    /**
     * Slot wrapper whose {@link Slot#isActive()} follows the visible tab. Vanilla already skips
     * inactive slots when drawing, highlighting and hit-testing, so the hidden page cannot steal a
     * click or draw an item under the other tab. The slot keeps its real position and index.
     */
    private final class TabSlot extends SlotItemHandler {
        private final int page;

        private TabSlot(IItemHandler handler, int index, int x, int y, int page) {
            super(handler, index, x, y);
            this.page = page;
        }

        @Override
        public boolean isActive() {
            return super.isActive() && QuarryMenu.this.visibleTab == this.page;
        }
    }

    public int getEnergyStored() {
        return this.data.get(QuarryBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return this.data.get(QuarryBlockEntity.DATA_CAPACITY);
    }

    public QuarryStatus getStatus() {
        QuarryStatus[] values = QuarryStatus.values();
        int ordinal = this.data.get(QuarryBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getCursor() {
        return this.data.get(QuarryBlockEntity.DATA_CURSOR);
    }

    public int getVolume() {
        return this.data.get(QuarryBlockEntity.DATA_VOLUME);
    }

    public int getMotorCount() {
        return this.data.get(QuarryBlockEntity.DATA_MOTORS);
    }

    public int getBatteryCount() {
        return this.data.get(QuarryBlockEntity.DATA_BATTERIES);
    }

    public boolean isRunning() {
        return this.data.get(QuarryBlockEntity.DATA_RUNNING) != 0;
    }

    public boolean isPreviewEnabled() {
        return this.data.get(QuarryBlockEntity.DATA_PREVIEW) != 0;
    }

    public int getRangeWidth() {
        return this.data.get(QuarryBlockEntity.DATA_WIDTH);
    }

    public int getRangeDepth() {
        return this.data.get(QuarryBlockEntity.DATA_DEPTH);
    }

    public int getRangeHeight() {
        return this.data.get(QuarryBlockEntity.DATA_HEIGHT);
    }

    public int getBlocksMined() {
        return this.data.get(QuarryBlockEntity.DATA_MINED);
    }

    public int getBlocksSkipped() {
        return this.data.get(QuarryBlockEntity.DATA_SKIPPED);
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public QuarryBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}

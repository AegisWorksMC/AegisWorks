package com.industrialcore.menu;

import com.industrialcore.blockentity.LargeQuarryBlockEntity;
import com.industrialcore.machine.QuarryStatus;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.ItemTags;
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

import java.util.List;

/**
 * Menu of the large quarry: collected ores, pickaxe arms, the motor slot, the status numbers and
 * the player inventory.
 *
 * <p>The pickaxe slot count is sent with the open packet, so the server and the client menu build
 * exactly the same slot list. Installing a motor unlocks a slot for the <b>next</b> time the GUI is
 * opened; the machine keeps its pickaxes in any case, because arms are never removed again.</p>
 */
public class LargeQuarryMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return LargeQuarryBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    private final int toolSlots;
    /**
     * Side this menu was built for. The block entity's own level can be null for a moment while
     * it is being created; the menu's side never changes, so the selection is always read from
     * the right copy: the display ids on the client, the authoritative set on the server.
     */
    private final boolean clientSide;
    @Nullable
    private final LargeQuarryBlockEntity blockEntity;

    public LargeQuarryMenu(int containerId, Inventory playerInventory,
                           RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos(), buffer.readVarInt());
    }

    public LargeQuarryMenu(int containerId, Inventory playerInventory, BlockPos pos,
                           int toolSlots) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null, toolSlots);
    }

    public LargeQuarryMenu(int containerId, Inventory playerInventory,
                           LargeQuarryBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data,
                blockEntity.getToolSlotCount());
    }

    private LargeQuarryMenu(int containerId, Inventory playerInventory, BlockPos pos,
                            @Nullable LargeQuarryBlockEntity blockEntity,
                            @Nullable ContainerData data, int toolSlots) {
        super(ModMenus.LARGE_QUARRY.get(), containerId);
        this.clientSide = playerInventory.player.level().isClientSide;
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);
        this.toolSlots = Math.max(1, Math.min(
                com.industrialcore.machine.LargeQuarryBalance.MAX_TOOL_SLOTS, toolSlots));

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(LargeQuarryBlockEntity.TOTAL_SLOTS);
        for (int index = 0; index < LargeQuarryBlockEntity.OUTPUT_SLOTS; index++) {
            Rect rect = LargeQuarryLayout.outputSlot(index);
            this.addSlot(new OutputSlot(machineInventory,
                    LargeQuarryBlockEntity.OUTPUT_START + index, rect.x(), rect.y()));
        }
        for (int index = 0; index < this.toolSlots; index++) {
            Rect rect = LargeQuarryLayout.toolSlot(index);
            this.addSlot(new ToolSlot(machineInventory,
                    LargeQuarryBlockEntity.TOOL_START + index, rect.x(), rect.y()));
        }
        Rect upgrade = LargeQuarryLayout.UPGRADE_SLOT;
        this.addSlot(new UpgradeSlot(machineInventory,
                LargeQuarryBlockEntity.UPGRADE_SLOT, upgrade.x(), upgrade.y()));
        Rect battery = LargeQuarryLayout.BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(machineInventory,
                LargeQuarryBlockEntity.BATTERY_SLOT, battery.x(), battery.y(), ModItems.BATTERY,
                () -> com.industrialcore.level.UpgradeCapBridge.effectiveLimit(
                        playerInventory.player, blockEntity, "large_quarry")));

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = LargeQuarryLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = LargeQuarryLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    /**
     * The block entity's container data is read-only on the client (server authoritative), so the
     * client menu needs its own writable cache that vanilla fills from the data slot packets.
     */
    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable LargeQuarryBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(LargeQuarryBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static LargeQuarryBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof LargeQuarryBlockEntity quarry
                ? quarry : null;
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
        int machineSlots = this.slots.size() - 36;
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else if (stack.is(ItemTags.PICKAXES)) {
            // Shift-clicked pickaxes go to the first free tool slot, then to the player inventory.
            moved = this.moveItemStackTo(stack, LargeQuarryBlockEntity.TOOL_START,
                    LargeQuarryBlockEntity.TOOL_START + this.toolSlots, false);
            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.MOTOR.get())) {
            // Menu slot indices, not block entity slot indices: the upgrade slot directly follows
            // the (variable number of) unlocked pickaxe slots.
            int upgradeIndex = LargeQuarryBlockEntity.TOOL_START + this.toolSlots;
            moved = this.moveItemStackTo(stack, upgradeIndex, upgradeIndex + 1, false);
            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BATTERY.get())) {
            int batteryIndex = LargeQuarryBlockEntity.TOOL_START + this.toolSlots + 1;
            moved = this.moveItemStackTo(stack, batteryIndex, batteryIndex + 1, false);
            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else {
            moved = false;
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

    public int getEnergyStored() {
        return this.data.get(LargeQuarryBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return this.data.get(LargeQuarryBlockEntity.DATA_CAPACITY);
    }

    public QuarryStatus getStatus() {
        QuarryStatus[] values = QuarryStatus.values();
        int ordinal = this.data.get(LargeQuarryBlockEntity.DATA_STATUS);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public int getCursor() {
        return this.data.get(LargeQuarryBlockEntity.DATA_CURSOR);
    }

    public int getVolume() {
        return this.data.get(LargeQuarryBlockEntity.DATA_VOLUME);
    }

    public boolean isRunning() {
        return this.data.get(LargeQuarryBlockEntity.DATA_RUNNING) != 0;
    }

    public int getAreaWidth() {
        return this.data.get(LargeQuarryBlockEntity.DATA_WIDTH);
    }

    public int getAreaDepth() {
        return this.data.get(LargeQuarryBlockEntity.DATA_DEPTH);
    }

    public int getAreaHeight() {
        return this.data.get(LargeQuarryBlockEntity.DATA_HEIGHT);
    }

    public int getBlocksMined() {
        return this.data.get(LargeQuarryBlockEntity.DATA_MINED);
    }

    public int getBlocksSkipped() {
        return this.data.get(LargeQuarryBlockEntity.DATA_SKIPPED);
    }

    public int getItemsCollected() {
        return this.data.get(LargeQuarryBlockEntity.DATA_ITEMS);
    }

    public int getToolSlots() {
        return this.data.get(LargeQuarryBlockEntity.DATA_TOOL_SLOTS);
    }

    /** Phase 82: installed motors, shown in the upgrade slot tooltip. */
    public int getMotorCount() {
        return this.data.get(LargeQuarryBlockEntity.DATA_MOTORS);
    }

    public int getBatteryCount() {
        int idx = LargeQuarryBlockEntity.TOOL_START + this.toolSlots + 1;
        return this.slots.size() > idx ? this.slots.get(idx).getItem().getCount() : 0;
    }

    public int getPickaxeCount() {
        return this.data.get(LargeQuarryBlockEntity.DATA_PICKAXES);
    }

    public int getOreTotal() {
        return this.data.get(LargeQuarryBlockEntity.DATA_ORE_TOTAL);
    }

    public int getSelectedOreCount() {
        return this.data.get(LargeQuarryBlockEntity.DATA_ORE_SELECTED);
    }

    public int getMiningCost() {
        return this.data.get(LargeQuarryBlockEntity.DATA_COST);
    }

    public int getEfficiency() {
        return this.data.get(LargeQuarryBlockEntity.DATA_EFFICIENCY);
    }

    public int getFortune() {
        return this.data.get(LargeQuarryBlockEntity.DATA_FORTUNE);
    }

    public int getSilkTouch() {
        return this.data.get(LargeQuarryBlockEntity.DATA_SILK);
    }

    /** Pickaxe slots this menu was opened with; installing a motor takes effect on reopen. */
    public int getOpenToolSlots() {
        return this.toolSlots;
    }

    /**
     * Selected ore ids. On the client this is the display copy that arrived with the block update,
     * so the picker can draw the current state without asking the server every frame; on the
     * server (GameTests, integrations) it is the authoritative selection.
     */
    public List<String> getSelectedOreIds() {
        if (this.blockEntity == null) {
            return List.of();
        }
        return this.clientSide
                ? this.blockEntity.getClientOreIds()
                : this.blockEntity.getSelectedOreIds();
    }

    /**
     * Output slots of the machine: items can be taken out and shift-clicked away, but the player
     * can never put something into a slot the machine fills itself.
     */
    private static final class OutputSlot extends SlotItemHandler {
        private OutputSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** Pickaxe arms: only pickaxes, so a random item cannot fill an arm. */
    private static final class ToolSlot extends SlotItemHandler {
        private ToolSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ItemTags.PICKAXES);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Motor slot: inserting a motor permanently unlocks one more pickaxe arm. */
    private static final class UpgradeSlot extends SlotItemHandler {
        private UpgradeSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.MOTOR.get());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public LargeQuarryBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}

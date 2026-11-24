package com.industrialcore.menu;

import com.industrialcore.blockentity.TransporterBlockEntity;
import com.industrialcore.machine.TransportChannel;
import com.industrialcore.machine.TransportRole;
import com.industrialcore.machine.TransporterKind;
import com.industrialcore.machine.TransporterStatus;
import com.industrialcore.menu.QuarryLayout.Rect;
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
 * Menu of the Phase 26-29 transporters, shared by all four tiers.
 *
 * <p>The tier decides the buffer size, so the menu builds its slot grid from
 * {@link TransporterLayout} for the resolved kind; the screen does the same, and GuiLayoutTest
 * checks both against each other. Shift-click moves machine contents into the player inventory
 * and player items into the buffer - every slot is a plain buffer slot, so there is no protected
 * slot to guard here (item safety lives in the transfer path, not in the GUI).</p>
 */
public class TransporterMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return TransporterBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    private final TransporterKind kind;
    @Nullable
    private final TransporterBlockEntity blockEntity;

    public TransporterMenu(int containerId, Inventory playerInventory,
                           RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos(),
                buffer.isReadable(1) ? decodeKind(buffer.readByte()) : null);
    }

    public TransporterMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, null);
    }

    private TransporterMenu(int containerId, Inventory playerInventory, BlockPos pos,
                            @Nullable TransporterKind explicitKind) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null, explicitKind);
    }

    public TransporterMenu(int containerId, Inventory playerInventory,
                           TransporterBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data, blockEntity.getKind());
    }

    @Nullable
    private static TransporterKind decodeKind(int ord) {
        TransporterKind[] vals = TransporterKind.values();
        return ord >= 0 && ord < vals.length ? vals[ord] : null;
    }

    private TransporterMenu(int containerId, Inventory playerInventory, BlockPos pos,
                            @Nullable TransporterBlockEntity blockEntity,
                            @Nullable ContainerData data,
                            @Nullable TransporterKind explicitKind) {
        super(ModMenus.TRANSPORTER.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.kind = explicitKind != null ? explicitKind
                : (blockEntity != null ? blockEntity.getKind() : TransporterKind.BASIC);
        this.data = resolveData(playerInventory, blockEntity, data);

        IItemHandler machineInventory = blockEntity != null
                ? blockEntity.getInventory()
                : new ItemStackHandler(this.kind.slotCount());

        for (int index = 0; index < this.kind.slotCount(); index++) {
            Rect rect = TransporterLayout.slot(this.kind, index);
            this.addSlot(new SlotItemHandler(machineInventory, index, rect.x(), rect.y()));
        }

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = TransporterLayout.playerSlot(this.kind, playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = TransporterLayout.playerSlot(this.kind, playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable TransporterBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(TransporterBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static TransporterBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof TransporterBlockEntity transporter
                ? transporter : null;
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
        int machineSlots = this.kind.slotCount();
        boolean moved;
        if (index < machineSlots) {
            moved = this.moveItemStackTo(stack, machineSlots, this.slots.size(), true);
        } else {
            moved = this.moveItemStackTo(stack, 0, machineSlots, false);
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

    public TransporterKind getKind() {
        return this.kind;
    }

    /** Display channel name; the string lives in the block entity's update tag. */
    public String getChannel() {
        return this.blockEntity != null ? this.blockEntity.getChannel() : TransportChannel.NONE;
    }

    public TransportRole getRole() {
        TransportRole[] roles = TransportRole.values();
        int ordinal = this.data.get(TransporterBlockEntity.DATA_ROLE);
        return roles[Math.max(0, Math.min(roles.length - 1, ordinal))];
    }

    public boolean isStructureValid() {
        return this.data.get(TransporterBlockEntity.DATA_STRUCTURE) != 0;
    }

    /** Registered peers of the other role on this channel (0 until the server computed it). */
    public int getPeerCount() {
        return Math.max(0, this.data.get(TransporterBlockEntity.DATA_RECEIVERS));
    }

    public TransporterStatus getStatus() {
        return TransporterStatus.byOrdinal(this.data.get(TransporterBlockEntity.DATA_STATUS));
    }

    public int getLastItemsMoved() {
        return Math.max(0, this.data.get(TransporterBlockEntity.DATA_LAST_ITEMS));
    }

    public int getLastPowerMoved() {
        return Math.max(0, this.data.get(TransporterBlockEntity.DATA_LAST_POWER));
    }

    public boolean isItemEnabled() {
        return this.data.get(TransporterBlockEntity.DATA_ITEM_ENABLED) != 0;
    }

    public boolean isPowerEnabled() {
        return this.data.get(TransporterBlockEntity.DATA_POWER_ENABLED) != 0;
    }

    public int getEnergyStored() {
        return this.data.get(TransporterBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(0, this.data.get(TransporterBlockEntity.DATA_CAPACITY));
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public TransporterBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}

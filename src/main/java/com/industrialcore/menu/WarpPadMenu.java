package com.industrialcore.menu;

import com.industrialcore.blockentity.WarpPadBlockEntity;
import com.industrialcore.level.UpgradeCapBridge;
import com.industrialcore.life.LifeWave2Layout;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.WarpListSyncPayload;
import com.industrialcore.network.WarpListSyncPayload.TargetRow;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Phase 95: Menu for the Teleport Warp Pad ({@code warp_pad}) with S2C target-list sync. */
public class WarpPadMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_START = WarpPadBlockEntity.SLOT_COUNT;

    private final BlockPos pos;
    private final Player player;
    @Nullable
    private final WarpPadBlockEntity blockEntity;
    private final ContainerData data;

    private String clientSelfName = "";
    private boolean clientSelfPublic = true;
    private final List<TargetRow> clientTargets = new ArrayList<>();
    @Nullable
    private Runnable clientSyncListener;

    public WarpPadMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readBlockPos());
    }

    public WarpPadMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos),
                new SimpleContainerData(WarpPadBlockEntity.DATA_COUNT));
    }

    public WarpPadMenu(int containerId, Inventory playerInventory,
                       WarpPadBlockEntity be, ContainerData data) {
        this(containerId, playerInventory, be.getBlockPos(), be, data);
    }

    private WarpPadMenu(int containerId, Inventory playerInventory, BlockPos pos,
                        @Nullable WarpPadBlockEntity be, ContainerData data) {
        super(ModMenus.WARP_PAD.get(), containerId);
        this.pos = pos;
        this.player = playerInventory.player;
        this.blockEntity = be;
        this.data = data;

        IItemHandler inv = be != null
                ? be.getInventory()
                : new ItemStackHandler(WarpPadBlockEntity.SLOT_COUNT);

        Rect mr = LifeWave2Layout.WARP_MOTOR_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, WarpPadBlockEntity.MOTOR_SLOT,
                mr.x(), mr.y(), ModItems.MOTOR, this::upgradeLimit));

        Rect br = LifeWave2Layout.WARP_BATTERY_SLOT;
        this.addSlot(new MachineUpgradeSlot(inv, WarpPadBlockEntity.BATTERY_SLOT,
                br.x(), br.y(), ModItems.BATTERY, this::upgradeLimit));

        XpCollectorMenu.addPlayerSlots(this::addSlot, playerInventory, LifeWave2Layout::warpPlayerSlot);
        this.addDataSlots(data);
    }

    @Nullable
    private static WarpPadBlockEntity resolve(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof WarpPadBlockEntity be ? be : null;
    }

    private int upgradeLimit() {
        return UpgradeCapBridge.effectiveLimit(player, blockEntity, WarpPadBlockEntity.MACHINE_ID);
    }

    public void applyClientSync(WarpListSyncPayload payload) {
        if (!pos.equals(payload.pos())) {
            return;
        }
        clientSelfName = payload.selfName();
        clientSelfPublic = payload.selfPublic();
        clientTargets.clear();
        clientTargets.addAll(payload.targets());
        if (clientSyncListener != null) {
            clientSyncListener.run();
        }
    }

    public void setClientSyncListener(@Nullable Runnable listener) {
        this.clientSyncListener = listener;
    }

    public String getClientSelfName() {
        return clientSelfName;
    }

    public boolean isClientSelfPublic() {
        return clientSelfPublic;
    }

    public List<TargetRow> getClientTargets() {
        return Collections.unmodifiableList(clientTargets);
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public int getEnergyStored() {
        return data.get(WarpPadBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, data.get(WarpPadBlockEntity.DATA_CAPACITY));
    }

    public int getWarpState() {
        return data.get(WarpPadBlockEntity.DATA_STATE);
    }

    public int getCooldownTicks() {
        return Math.max(0, data.get(WarpPadBlockEntity.DATA_COOLDOWN));
    }

    public boolean isPublicFlag() {
        return data.get(WarpPadBlockEntity.DATA_PUBLIC) != 0;
    }

    public int getMotorCount() {
        return this.slots.get(WarpPadBlockEntity.MOTOR_SLOT).getItem().getCount();
    }

    public int getBatteryCount() {
        return this.slots.get(WarpPadBlockEntity.BATTERY_SLOT).getItem().getCount();
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PLAYER_INV_START) {
            if (!this.moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.MOTOR.get())) {
            if (!this.moveItemStackTo(stack, WarpPadBlockEntity.MOTOR_SLOT,
                    WarpPadBlockEntity.MOTOR_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BATTERY.get())) {
            if (!this.moveItemStackTo(stack, WarpPadBlockEntity.BATTERY_SLOT,
                    WarpPadBlockEntity.BATTERY_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(p, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player p) {
        return XpCollectorMenu.isWithinReach(blockEntity, pos, p);
    }
}

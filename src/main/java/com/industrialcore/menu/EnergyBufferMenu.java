package com.industrialcore.menu;

import com.industrialcore.blockentity.EnergyBufferBlockEntity;
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
import org.jetbrains.annotations.Nullable;

/**
 * Menu of the Phase 17 large energy buffer.
 *
 * <p>The block has no machine slots; the menu only exposes the FE data and the player inventory, so
 * the buffer behaves like a tool block rather than a container.</p>
 */
public class EnergyBufferMenu extends AbstractContainerMenu {
    private static final ContainerData EMPTY_DATA = new ContainerData() {
        @Override
        public int get(int index) {
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return EnergyBufferBlockEntity.DATA_COUNT;
        }
    };

    private final ContainerData data;
    private final BlockPos pos;
    @Nullable
    private final EnergyBufferBlockEntity blockEntity;

    public EnergyBufferMenu(int containerId, Inventory playerInventory,
                            RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, buffer.readBlockPos());
    }

    public EnergyBufferMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, resolve(playerInventory, pos), null);
    }

    public EnergyBufferMenu(int containerId, Inventory playerInventory,
                            EnergyBufferBlockEntity blockEntity, ContainerData data) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity, data);
    }

    private EnergyBufferMenu(int containerId, Inventory playerInventory, BlockPos pos,
                             @Nullable EnergyBufferBlockEntity blockEntity,
                             @Nullable ContainerData data) {
        super(ModMenus.ENERGY_BUFFER.get(), containerId);
        this.pos = pos;
        this.blockEntity = blockEntity;
        this.data = resolveData(playerInventory, blockEntity, data);

        int playerSlots = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                Rect rect = EnergyBufferLayout.playerSlot(playerSlots++);
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, rect.x(), rect.y()));
            }
        }
        for (int column = 0; column < 9; column++) {
            Rect rect = EnergyBufferLayout.playerSlot(playerSlots++);
            this.addSlot(new Slot(playerInventory, column, rect.x(), rect.y()));
        }

        this.addDataSlots(this.data);
    }

    private static ContainerData resolveData(Inventory playerInventory,
                                             @Nullable EnergyBufferBlockEntity blockEntity,
                                             @Nullable ContainerData provided) {
        if (playerInventory.player.level().isClientSide) {
            return new SimpleContainerData(EnergyBufferBlockEntity.DATA_COUNT);
        }
        if (provided != null) {
            return provided;
        }
        return blockEntity != null ? blockEntity.getContainerData() : EMPTY_DATA;
    }

    @Nullable
    private static EnergyBufferBlockEntity resolve(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof EnergyBufferBlockEntity buffer
                ? buffer : null;
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
        // The buffer has no machine slots, so a shift-click only moves inside the player inventory.
        return ItemStack.EMPTY;
    }

    public int getEnergyStored() {
        return this.data.get(EnergyBufferBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return Math.max(1, this.data.get(EnergyBufferBlockEntity.DATA_CAPACITY));
    }

    public int getPushPerTick() {
        return this.data.get(EnergyBufferBlockEntity.DATA_PUSH);
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Nullable
    public EnergyBufferBlockEntity getBlockEntity() {
        return this.blockEntity;
    }
}

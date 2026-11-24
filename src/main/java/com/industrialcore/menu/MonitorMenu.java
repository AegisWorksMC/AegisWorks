package com.industrialcore.menu;

import com.industrialcore.blockentity.MonitorBlockEntity;
import com.industrialcore.machine.FactoryStats;
import com.industrialcore.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Menu of the factory monitor (Phase 82).
 *
 * <p>The terminal is read-only and has no item slots: the menu exists so the screen can be opened
 * and so the panel has a position, not to move items. The statistics themselves are synced by the
 * block entity's update tag (they are wider than the 16-bit container data channel), and the client
 * menu reads the synced copy.</p>
 */
public class MonitorMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    @Nullable
    private final MonitorBlockEntity blockEntity;

    public MonitorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory,
                (buffer != null && buffer.readableBytes() >= Long.BYTES)
                        ? buffer.readBlockPos() : BlockPos.ZERO,
                null);
    }

    public MonitorMenu(int containerId, Inventory playerInventory, MonitorBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity.getBlockPos(), blockEntity);
    }

    private MonitorMenu(int containerId, Inventory playerInventory, BlockPos pos,
                        @Nullable MonitorBlockEntity blockEntity) {
        super(ModMenus.FACTORY_MONITOR.get(), containerId);
        this.pos = pos != null ? pos : BlockPos.ZERO;
        this.blockEntity = blockEntity != null
                ? blockEntity
                : resolve(playerInventory, this.pos);
    }

    @Nullable
    private static MonitorBlockEntity resolve(@Nullable Inventory inventory, BlockPos pos) {
        if (inventory == null || inventory.player == null || inventory.player.level() == null) {
            return null;
        }
        return inventory.player.level().getBlockEntity(pos) instanceof MonitorBlockEntity monitor
                ? monitor : null;
    }

    /** Latest synced snapshot, or empty when the block entity is not loaded. */
    public FactoryStats getStats() {
        return this.blockEntity == null ? FactoryStats.EMPTY : this.blockEntity.getStats();
    }

    public BlockPos getBlockPos() {
        return this.pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return Vec3.atCenterOf(this.pos).distanceToSqr(player.position()) <= 64.0D
                && (this.blockEntity == null || !this.blockEntity.isRemoved());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}

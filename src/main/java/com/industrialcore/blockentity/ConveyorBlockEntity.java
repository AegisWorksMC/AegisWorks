package com.industrialcore.blockentity;

import com.industrialcore.block.ItemPipeBlock;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy compatibility for worlds created before item pipes replaced conveyor belts.
 *
 * <p>The conveyor is no longer craftable, obtainable or in the creative tab. Its block id and
 * block-entity id stay registered only so an existing world does not lose the block (or the items
 * on it) when the chunk loads. On the first server tick the legacy belt drops every stack it was
 * carrying at its position and replaces itself with a Basic Item Pipe. Nothing is converted
 * automatically in a way that could void an item: the drop happens before the block replacement,
 * and breaking an unconverted belt still drops its contents through {@link #dropContents()}.</p>
 */
public class ConveyorBlockEntity extends BlockEntity {
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_STACK = "Stack";
    private static final int MAX_LEGACY_STACKS = 64;

    private final List<ItemStack> legacyItems = new ArrayList<>();
    private boolean converted;

    public ConveyorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONVEYOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConveyorBlockEntity conveyor) {
        if (level instanceof ServerLevel serverLevel) {
            conveyor.convert(serverLevel);
        }
    }

    private void convert(ServerLevel level) {
        if (converted) {
            return;
        }
        converted = true;
        dropContents();
        BlockState pipe = ItemPipeBlock.recalculate(
                ModBlocks.ITEM_PIPE.get().defaultBlockState(), level, worldPosition);
        // UPDATE_ALL also removes this block entity and notifies the new pipe's neighbours.
        level.setBlock(worldPosition, pipe, Block.UPDATE_ALL);
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (ItemStack stack : legacyItems) {
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
            }
        }
        legacyItems.clear();
        setChanged();
    }

    public int getLegacyItemCount() {
        return legacyItems.size();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag items = new ListTag();
        for (ItemStack stack : legacyItems) {
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_STACK, stack.save(registries));
            items.add(entry);
        }
        tag.put(TAG_ITEMS, items);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        legacyItems.clear();
        ListTag items = tag.getList(TAG_ITEMS, CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < items.size() && legacyItems.size() < MAX_LEGACY_STACKS; index++) {
            ItemStack stack = ItemStack.parseOptional(registries, items.getCompound(index).getCompound(TAG_STACK));
            if (!stack.isEmpty()) {
                legacyItems.add(stack);
            }
        }
    }
}

package com.industrialcore.blockentity;

import com.industrialcore.blockentity.QuantumStorageBlockEntity.FluidKey;
import com.industrialcore.blockentity.QuantumStorageBlockEntity.ItemKey;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.storage.QuantumNetLogic.QuantumChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Phase 96: Quantum Exporter ({@code quantum_exporter}) — wirelessly supplies filtered items and
 * fluids from the linked Quantum Storage frequency channel into the adjacent inventory or tank
 * with strict transaction conservation (extracted == actually inserted).
 */
public class QuantumExporterBlockEntity extends AbstractQuantumIoBlockEntity {
    public static final String MACHINE_ID = "quantum_exporter";
    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

    public QuantumExporterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_EXPORTER.get(), pos, state, false);
    }

    @Override
    public boolean isExporter() {
        return true;
    }

    @Override
    public String getMachineProgressId() {
        return MACHINE_ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.quantum_exporter");
    }

    @Override
    protected boolean performCycle(ServerLevel level, BlockPos targetPos, Direction targetSide,
                                   QuantumChannel<ItemKey, FluidKey> channel,
                                   int maxItems, int maxFluidMb) {
        boolean moved = false;

        IItemHandler itemTarget = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetSide);
        IFluidHandler fluidTarget = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, targetSide);

        int itemBudget = maxItems;
        int fluidBudget = maxFluidMb;

        // Export items & fluids matching non-empty ghost filter slots (or any non-blacklisted item if blacklist mode)
        boolean anyFilter = false;
        for (ItemStack proto : filterSlots) {
            if (!proto.isEmpty()) {
                anyFilter = true;
                break;
            }
        }

        if (!blacklist && anyFilter) {
            for (ItemStack proto : filterSlots) {
                if (proto.isEmpty()) {
                    continue;
                }
                // 1. If adjacent is a fluid handler and filter is a filled fluid container (e.g. water bucket)
                if (fluidTarget != null && fluidBudget > 0) {
                    Optional<FluidStack> contained = FluidUtil.getFluidContained(proto);
                    if (contained.isPresent() && !contained.get().isEmpty()) {
                        int movedMb = exportFluidKey(channel, fluidTarget, FluidKey.of(contained.get()), fluidBudget);
                        if (movedMb > 0) {
                            fluidBudget -= movedMb;
                            moved = true;
                        }
                    }
                }
                // 2. Export matching item to adjacent IItemHandler
                if (itemTarget != null && itemBudget > 0) {
                    int movedCount = exportItemKey(channel, itemTarget, ItemKey.of(proto), itemBudget);
                    if (movedCount > 0) {
                        itemBudget -= movedCount;
                        moved = true;
                    }
                }
            }
        } else if (blacklist) {
            // Blacklist mode: export any stored item not in the filter list
            if (itemTarget != null && itemBudget > 0) {
                for (ItemKey key : new java.util.ArrayList<>(channel.storage().itemsView().keySet())) {
                    if (itemBudget <= 0) {
                        break;
                    }
                    ItemStack sample = key.toStack(1);
                    if (isInFilter(sample)) {
                        continue;
                    }
                    int movedCount = exportItemKey(channel, itemTarget, key, itemBudget);
                    if (movedCount > 0) {
                        itemBudget -= movedCount;
                        moved = true;
                    }
                }
            }
        }

        return moved;
    }

    private boolean isInFilter(ItemStack candidate) {
        for (ItemStack f : filterSlots) {
            if (!f.isEmpty() && ItemStack.isSameItem(f, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static int exportItemKey(QuantumChannel<ItemKey, FluidKey> channel, IItemHandler target,
                                     ItemKey key, int budget) {
        BigInteger avail = channel.storage().getItemCount(key);
        if (avail.signum() <= 0 || budget <= 0) {
            return 0;
        }
        int maxStack = Math.max(1, key.toStack(1).getMaxStackSize());
        int totalMoved = 0;
        int remaining = Math.min(budget, avail.min(MAX_INT).intValue());
        while (remaining > 0) {
            int batch = Math.min(remaining, maxStack);
            ItemStack probe = key.toStack(batch);
            ItemStack simRem = ItemHandlerHelper.insertItemStacked(target, probe, true);
            int canFit = batch - simRem.getCount();
            if (canFit <= 0) {
                break;
            }
            // Extract exact canFit, insert, and immediately return any unexpected remainder (strict conservation)
            BigInteger pulled = channel.storage().extractItem(key, BigInteger.valueOf(canFit), false);
            int pulledInt = pulled.min(MAX_INT).intValue();
            if (pulledInt <= 0) {
                break;
            }
            ItemStack realRem = ItemHandlerHelper.insertItemStacked(target, key.toStack(pulledInt), false);
            int actuallyInserted = pulledInt - realRem.getCount();
            if (!realRem.isEmpty()) {
                channel.storage().insertItem(key, BigInteger.valueOf(realRem.getCount()), false);
            }
            if (actuallyInserted <= 0) {
                break;
            }
            totalMoved += actuallyInserted;
            remaining -= actuallyInserted;
        }
        return totalMoved;
    }

    private static int exportFluidKey(QuantumChannel<ItemKey, FluidKey> channel, IFluidHandler target,
                                      FluidKey key, int budgetMb) {
        BigInteger avail = channel.storage().getFluidAmount(key);
        if (avail.signum() <= 0 || budgetMb <= 0) {
            return 0;
        }
        int want = Math.min(budgetMb, avail.min(MAX_INT).intValue());
        int canFit = target.fill(key.toStack(want), IFluidHandler.FluidAction.SIMULATE);
        if (canFit <= 0) {
            return 0;
        }
        BigInteger pulled = channel.storage().extractFluid(key, BigInteger.valueOf(canFit), false);
        int pulledInt = pulled.min(MAX_INT).intValue();
        if (pulledInt <= 0) {
            return 0;
        }
        int filled = target.fill(key.toStack(pulledInt), IFluidHandler.FluidAction.EXECUTE);
        int leftover = pulledInt - filled;
        if (leftover > 0) {
            channel.storage().insertFluid(key, BigInteger.valueOf(leftover), false);
        }
        return filled;
    }
}

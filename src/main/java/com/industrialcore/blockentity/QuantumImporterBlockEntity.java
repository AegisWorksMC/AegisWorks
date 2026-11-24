package com.industrialcore.blockentity;

import com.industrialcore.blockentity.QuantumStorageBlockEntity.FluidKey;
import com.industrialcore.blockentity.QuantumStorageBlockEntity.ItemKey;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.storage.QuantumNetLogic;
import com.industrialcore.storage.QuantumNetLogic.QuantumChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase 96: Quantum Importer ({@code quantum_importer}) — wirelessly pulls filtered items and
 * fluids from the adjacent block into the linked Quantum Storage frequency channel with zero loss.
 */
public class QuantumImporterBlockEntity extends AbstractQuantumIoBlockEntity {
    public static final String MACHINE_ID = "quantum_importer";
    private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

    public QuantumImporterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_IMPORTER.get(), pos, state, true);
    }

    @Override
    public boolean isExporter() {
        return false;
    }

    @Override
    public String getMachineProgressId() {
        return MACHINE_ID;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.industrialcore.quantum_importer");
    }

    @Override
    protected boolean performCycle(ServerLevel level, BlockPos targetPos, Direction targetSide,
                                   QuantumChannel<ItemKey, FluidKey> channel,
                                   int maxItems, int maxFluidMb) {
        boolean moved = false;

        // 1. Pull items from adjacent IItemHandler with strict simulate-first conservation
        IItemHandler itemTarget = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, targetSide);
        if (itemTarget != null && maxItems > 0) {
            List<String> itemFilterIds = buildItemFilterIds();
            int remaining = maxItems;
            int slots = itemTarget.getSlots();
            for (int s = 0; s < slots && remaining > 0; s++) {
                ItemStack peek = itemTarget.getStackInSlot(s);
                if (peek.isEmpty()) {
                    continue;
                }
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(peek.getItem());
                if (!QuantumNetLogic.matchesFilter(itemId != null ? itemId.toString() : "",
                        itemFilterIds, blacklist)) {
                    continue;
                }
                int want = Math.min(remaining, peek.getCount());
                ItemStack simExt = itemTarget.extractItem(s, want, true);
                if (simExt.isEmpty()) {
                    continue;
                }
                ItemKey key = ItemKey.of(simExt);
                BigInteger canStore = channel.storage().insertItem(
                        key, BigInteger.valueOf(simExt.getCount()), true);
                int toMove = canStore.min(BigInteger.valueOf(simExt.getCount())).intValue();
                if (toMove <= 0) {
                    continue;
                }
                ItemStack taken = itemTarget.extractItem(s, toMove, false);
                if (!taken.isEmpty()) {
                    BigInteger stored = channel.storage().insertItem(
                            ItemKey.of(taken), BigInteger.valueOf(taken.getCount()), false);
                    int leftover = taken.getCount() - stored.min(MAX_INT).intValue();
                    if (leftover > 0) {
                        // Defensive fallback (should never happen after simulate): return remainder
                        itemTarget.insertItem(s, taken.copyWithCount(leftover), false);
                    }
                    if (stored.signum() > 0) {
                        remaining -= stored.min(MAX_INT).intValue();
                        moved = true;
                    }
                }
            }
        }

        // 2. Pull fluids from adjacent IFluidHandler with strict simulate-first conservation
        IFluidHandler fluidTarget = level.getCapability(Capabilities.FluidHandler.BLOCK, targetPos, targetSide);
        if (fluidTarget != null && maxFluidMb > 0) {
            List<String> fluidFilterIds = buildFluidFilterIds();
            FluidStack simDrain = fluidTarget.drain(maxFluidMb, IFluidHandler.FluidAction.SIMULATE);
            if (!simDrain.isEmpty()) {
                ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(simDrain.getFluid());
                if (QuantumNetLogic.matchesFilter(fluidId != null ? fluidId.toString() : "",
                        fluidFilterIds, blacklist)) {
                    FluidKey fKey = FluidKey.of(simDrain);
                    BigInteger canStore = channel.storage().insertFluid(
                            fKey, BigInteger.valueOf(simDrain.getAmount()), true);
                    int toMove = canStore.min(BigInteger.valueOf(simDrain.getAmount())).intValue();
                    if (toMove > 0) {
                        FluidStack drained = fluidTarget.drain(
                                simDrain.copyWithAmount(toMove), IFluidHandler.FluidAction.EXECUTE);
                        if (!drained.isEmpty()) {
                            BigInteger stored = channel.storage().insertFluid(
                                    FluidKey.of(drained), BigInteger.valueOf(drained.getAmount()), false);
                            int leftover = drained.getAmount() - stored.min(MAX_INT).intValue();
                            if (leftover > 0) {
                                fluidTarget.fill(drained.copyWithAmount(leftover),
                                        IFluidHandler.FluidAction.EXECUTE);
                            }
                            if (stored.signum() > 0) {
                                moved = true;
                            }
                        }
                    }
                }
            }
        }
        return moved;
    }

    private List<String> buildItemFilterIds() {
        List<String> out = new ArrayList<>();
        for (ItemStack s : filterSlots) {
            if (!s.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
                if (id != null) {
                    out.add(id.toString());
                }
            }
        }
        return out;
    }

    private List<String> buildFluidFilterIds() {
        List<String> out = new ArrayList<>();
        for (ItemStack s : filterSlots) {
            if (!s.isEmpty()) {
                Optional<FluidStack> contained = FluidUtil.getFluidContained(s);
                if (contained.isPresent() && !contained.get().isEmpty()) {
                    ResourceLocation id = BuiltInRegistries.FLUID.getKey(contained.get().getFluid());
                    if (id != null) {
                        out.add(id.toString());
                    }
                }
            }
        }
        return out;
    }
}

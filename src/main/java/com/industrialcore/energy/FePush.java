package com.industrialcore.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Pushes FE from a device into its neighbours, with the same caching rule as the original
 * generator: the six neighbour positions are built once, a capability lookup only repeats when
 * the neighbour's block state differs (the neighbour was placed, changed or broken) or after
 * its chunk was unloaded. A static neighbourhood therefore costs no lookups per tick.
 */
public final class FePush {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final BlockPos[] positions = new BlockPos[DIRECTIONS.length];
    private final BlockState[] states = new BlockState[DIRECTIONS.length];
    private final IEnergyStorage[] storages = new IEnergyStorage[DIRECTIONS.length];

    /** Builds the cached neighbour positions for one device position. */
    public void init(BlockPos self) {
        for (int index = 0; index < DIRECTIONS.length; index++) {
            positions[index] = self.relative(DIRECTIONS[index]);
        }
    }

    /**
     * Pushes up to {@code budgetPerFace} FE into every neighbour that accepts it.
     * Returns true when any FE moved.
     */
    public boolean pushEach(ServerLevel level, ModEnergyStorage source, int budgetPerFace) {
        if (budgetPerFace <= 0) {
            return false;
        }
        boolean moved = false;
        for (int index = 0; index < DIRECTIONS.length; index++) {
            if (source.getEnergyStored() <= 0) {
                break;
            }
            moved |= pushInto(level, source, index, budgetPerFace);
        }
        return moved;
    }

    /**
     * Pushes at most {@code totalBudget} FE in this tick, shared across all accepting
     * neighbours. Used by devices whose design output cap is per device, not per face.
     */
    public boolean pushShared(ServerLevel level, ModEnergyStorage source, int totalBudget) {
        if (totalBudget <= 0) {
            return false;
        }
        boolean moved = false;
        int remaining = totalBudget;
        for (int index = 0; index < DIRECTIONS.length; index++) {
            if (remaining <= 0 || source.getEnergyStored() <= 0) {
                break;
            }
            int share = Math.max(1, remaining / (DIRECTIONS.length - index));
            if (pushInto(level, source, index, share)) {
                moved = true;
                remaining -= share;
            }
        }
        return moved;
    }

    private boolean pushInto(ServerLevel level, ModEnergyStorage source, int index, int budget) {
        Direction direction = DIRECTIONS[index];
        BlockPos neighbourPos = positions[index];
        if (!level.isLoaded(neighbourPos)) {
            // Never force-load chunks and never keep a reference into an unloaded chunk.
            states[index] = null;
            storages[index] = null;
            return false;
        }
        BlockState state = level.getBlockState(neighbourPos);
        if (state != states[index]) {
            states[index] = state;
            storages[index] = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, neighbourPos, direction.getOpposite());
        }
        IEnergyStorage target = storages[index];
        if (target == null || !target.canReceive()) {
            return false;
        }
        int accepted = target.receiveEnergy(Math.min(budget, source.getEnergyStored()), false);
        if (accepted <= 0) {
            return false;
        }
        source.consume(accepted);
        return true;
    }
}
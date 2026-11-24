package com.industrialcore.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * FE storage used by every machine in the mod.
 *
 * <p>Behavior: one-way flow - energy enters via {@link #receiveEnergy} (grid/machines
 * charging) and leaves via {@link #consume} (the owning machine's own tick, as in the
 * original generator design). External extraction is always disabled ({@link #canExtract()}
 * returns false), so machines cannot be drained by cables/pipes and no FE is duplicated
 * through push/pull loops.</p>
 *
 * <p>Phase 33: additionally keeps a cumulative consumption counter ({@link #getConsumedTotal}).
 * The energy recovery devices credit heat and regeneration strictly as a percentage of FE
 * the machine already spent, measured by this counter - never energy from nothing. The
 * counter only ever grows; {@link #setEnergy} (used to refill or reset buffers) also resets
 * it to zero.</p>
 */
public class ModEnergyStorage implements IEnergyStorage {
    protected int capacity;
    protected int energy;
    protected int maxReceive;
    protected int maxExtract;
    /** Cumulative FE this storage has consumed (Phase 33 recovery device measurement). */
    protected long consumedTotal;

    public ModEnergyStorage(int capacity, int maxReceive) {
        this(capacity, maxReceive, 0);
    }

    public ModEnergyStorage(int capacity, int maxReceive, int maxExtract) {
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
        this.consumedTotal = 0L;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!canReceive()) {
            return 0;
        }
        int energyReceived = Math.min(capacity - energy, Math.min(this.maxReceive, maxReceive));
        if (!simulate) {
            energy += energyReceived;
        }
        return energyReceived;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!canExtract()) {
            return 0;
        }
        int energyExtracted = Math.min(energy, Math.min(this.maxExtract, maxExtract));
        if (!simulate) {
            energy -= energyExtracted;
        }
        return energyExtracted;
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return maxReceive > 0;
    }

    /** Generators add FE (never reversible). */
    public int generate(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int space = capacity - energy;
        int added = Math.min(space, amount);
        if (added > 0) {
            energy += added;
        }
        return added;
    }

    /**
     * The owning machine spends FE on work; the cumulative counter feeds the Phase 33
     * recovery devices. Returns the amount actually spent (partial spends allowed, as
     * in the original storage so every existing caller keeps its behaviour).
     */
    public int consume(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int taken = Math.min(energy, amount);
        if (taken > 0) {
            energy -= taken;
            consumedTotal += taken;
        }
        return taken;
    }

    /**
     * Reverses FE flow for tests (simulated charge), does not fire listeners.
     * Resets the cumulative consumption counter together with the buffer.
     */
    public void setEnergy(int energy) {
        this.energy = Math.max(0, Math.min(capacity, energy));
        this.consumedTotal = 0L;
    }

    /** Total FE this storage ever consumed; never decreases except via setEnergy. */
    public long getConsumedTotal() {
        return this.consumedTotal;
    }

    /**
     * Narrows a capability lookup to this mod's own storage. Foreign FE blocks (other mods)
     * and empty capabilities simply yield {@code null}, so observing a neighbour can neither
     * throw a {@link ClassCastException} nor credit heat/regeneration from an unknown
     * counter (Phase 34.1 audit fix). Own storage is returned unchanged.
     */
    public static ModEnergyStorage ownStorage(IEnergyStorage storage) {
        return storage instanceof ModEnergyStorage machine ? machine : null;
    }

    /** Capacity can be restored from saved data (see GenerationBlockEntity). */
    public void setCapacity(int capacity) {
        this.capacity = Math.max(0, capacity);
        if (this.energy > this.capacity) {
            this.energy = this.capacity;
        }
    }

    public void setMaxReceive(int maxReceive) {
        this.maxReceive = Math.max(0, maxReceive);
    }

    /** Current receive limit; lets a machine skip a per-tick write when config is unchanged. */
    public int getMaxReceive() {
        return this.maxReceive;
    }
}

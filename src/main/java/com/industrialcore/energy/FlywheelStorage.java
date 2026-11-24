package com.industrialcore.energy;

/**
 * Energy storage of the flywheel (Phase 33): a deliberately loss-typed buffer.
 *
 * <p>It accepts at most its receive limit per tick (200 FE/t - slow charging) and keeps only
 * {@link GenerationBalance#FLYWHEEL_STORE_PERCENT}% of what it accepts; the missing 5% is
 * friction. It cannot extract FE externally: like every device in this mod it discharges
 * only through the block entity's own push, so the flywheel is a peak shaver, never a
 * second battery.</p>
 */
public class FlywheelStorage extends ModEnergyStorage {
    private final Runnable onChange;

    public FlywheelStorage(int capacity, int maxReceive, Runnable onChange) {
        super(capacity, maxReceive);
        this.onChange = onChange;
    }

    @Override
    public int receiveEnergy(int offered, boolean simulate) {
        int accepted = Math.min(offered, this.maxReceive);
        if (accepted <= 0) {
            return 0;
        }
        int room = this.capacity - this.energy;
        if (room <= 0) {
            return 0;
        }
        // Only accept FE we can actually store after friction, so nothing vanishes silently.
        long affordable = (long) room * 100 / GenerationBalance.FLYWHEEL_STORE_PERCENT;
        accepted = (int) Math.min(accepted, affordable);
        if (accepted <= 0) {
            return 0;
        }
        int stored = accepted * GenerationBalance.FLYWHEEL_STORE_PERCENT / 100;
        if (!simulate && stored > 0) {
            this.energy += stored;
            this.onChange.run();
        }
        return accepted;
    }
}
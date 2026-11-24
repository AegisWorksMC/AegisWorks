package com.industrialcore.preview;

import com.industrialcore.machine.QuarryStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Immutable snapshot sent from the (server authoritative) block entity to the client-side
 * renderer through the block update tag. Only rendering data is included.
 *
 * @param previousCursor    cursor reported by the previous update, used for client interpolation
 * @param previousWork      within-block work fraction reported by the previous update (0..1)
 * @param work              within-block work fraction of this update (0..1)
 * @param syncIntervalTicks interval the server currently uses between visual updates
 */
public record PreviewData(
        BlockPos machinePos,
        Direction facing,
        int width,
        int depth,
        int height,
        int previousCursor,
        float previousWork,
        int cursor,
        float work,
        int volume,
        int syncIntervalTicks,
        QuarryStatus status,
        boolean running,
        boolean preview) {

    /**
     * Fractional drill position of the previous update. The drill and the frame are drawn from
     * this single value, so they can never advance at different rates.
     */
    public double previousDrillIndex() {
        return com.industrialcore.machine.MiningSync.drillIndex(previousCursor, previousWork);
    }

    /** Fractional drill position of this update (server confirmed). */
    public double drillIndex() {
        return com.industrialcore.machine.MiningSync.drillIndex(cursor, work);
    }
}

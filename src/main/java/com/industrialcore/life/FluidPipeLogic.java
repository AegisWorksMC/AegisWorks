package com.industrialcore.life;

/**
 * Phase 105.6: Pure-Java fluid pipe transfer math for {@code fluid_pipe}.
 *
 * <p>Zero Minecraft imports so unit tests verify transfer caps and fair multi-sink distribution
 * directly on a plain JVM.</p>
 */
public final class FluidPipeLogic {
    /** Maximum fluid volume (mB) routed per pipe network per server tick. */
    public static final int MAX_TRANSFER_MB_PER_TICK = 500;
    /** Safety cap on BFS pipe network traversal nodes. */
    public static final int MAX_NETWORK_NODES = 256;

    private FluidPipeLogic() {
    }

    /** Clamps a single tick fluid offer to the pipe's rated transfer limit. */
    public static int clampTransfer(int offeredMb) {
        if (offeredMb <= 0) {
            return 0;
        }
        return Math.min(MAX_TRANSFER_MB_PER_TICK, offeredMb);
    }

    /**
     * Fairly distributes up to {@code availableMb} mB across {@code sinkSpaces.length} sinks
     * without exceeding any sink's capacity or losing a single mB to rounding.
     */
    public static int[] distributeFluid(int availableMb, int[] sinkSpaces) {
        if (sinkSpaces == null || sinkSpaces.length == 0) {
            return new int[0];
        }
        int[] alloc = new int[sinkSpaces.length];
        int remaining = Math.max(0, availableMb);
        if (remaining == 0) {
            return alloc;
        }

        int active = 0;
        for (int space : sinkSpaces) {
            if (space > 0) {
                active++;
            }
        }
        while (remaining > 0 && active > 0) {
            int share = Math.max(1, remaining / active);
            int nextActive = 0;
            boolean progressed = false;
            for (int i = 0; i < sinkSpaces.length && remaining > 0; i++) {
                int left = Math.max(0, sinkSpaces[i]) - alloc[i];
                if (left <= 0) {
                    continue;
                }
                int give = Math.min(left, Math.min(share, remaining));
                if (give > 0) {
                    alloc[i] += give;
                    remaining -= give;
                    progressed = true;
                }
                if (Math.max(0, sinkSpaces[i]) - alloc[i] > 0) {
                    nextActive++;
                }
            }
            if (!progressed) {
                break;
            }
            active = nextActive;
        }
        return alloc;
    }
}

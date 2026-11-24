package com.industrialcore.endgame;

/**
 * Phase 99: Pure-Java conservation and mode logic for Unlimited Energy Conduit
 * ({@code hyper_energy_conduit}) and Unlimited Item Conduit ({@code hyper_item_conduit}).
 */
public final class HyperConduitLogic {
    /** Maximum FE per transfer operation at the NeoForge {@code IEnergyStorage} boundary. */
    public static final int UNLIMITED_FE_PER_TICK = Integer.MAX_VALUE;
    /** Maximum full stacks transferred per server tick across a Hyper Item Conduit network. */
    public static final int MAX_STACKS_PER_TICK = 64;
    /** Safety traversal limit for conduit BFS networks. */
    public static final int MAX_NETWORK_NODES = 1_024;

    /** Per-face connection mode for {@code hyper_item_conduit}. */
    public enum FaceMode {
        BOTH(0, "gui.industrialcore.conduit.mode.both"),
        EXTRACT(1, "gui.industrialcore.conduit.mode.extract"),
        INSERT(2, "gui.industrialcore.conduit.mode.insert"),
        DISABLED(3, "gui.industrialcore.conduit.mode.disabled");

        private final int id;
        private final String translationKey;

        FaceMode(int id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        public int id() {
            return id;
        }

        public String translationKey() {
            return translationKey;
        }

        public boolean allowsExtract() {
            return this == BOTH || this == EXTRACT;
        }

        public boolean allowsInsert() {
            return this == BOTH || this == INSERT;
        }

        public boolean isConnected() {
            return this != DISABLED;
        }

        public FaceMode next() {
            FaceMode[] all = values();
            return all[(this.ordinal() + 1) % all.length];
        }

        public static FaceMode fromId(int id) {
            for (FaceMode m : values()) {
                if (m.id == id) {
                    return m;
                }
            }
            return BOTH;
        }
    }

    private HyperConduitLogic() {}

    /**
     * Overflow-safe FE transfer amount between a source and sink clamped to
     * {@code [0, Integer.MAX_VALUE]}.
     */
    public static int computeEnergyTransfer(long sourceAvailable, long sinkRoom) {
        if (sourceAvailable <= 0L || sinkRoom <= 0L) {
            return 0;
        }
        long move = Math.min(sourceAvailable, Math.min(sinkRoom, (long) UNLIMITED_FE_PER_TICK));
        return (int) Math.max(0L, move);
    }

    /**
     * Lossless {@code long}-precision energy distribution from a pool of {@code totalAvailable} FE
     * across multiple sinks, guaranteeing {@code sum(alloc) <= totalAvailable},
     * {@code alloc[i] <= sinkRooms[i]}, and zero overflow.
     */
    public static long[] distributeEnergy(long totalAvailable, long[] sinkRooms) {
        int n = sinkRooms == null ? 0 : sinkRooms.length;
        long[] alloc = new long[n];
        if (totalAvailable <= 0L || n == 0) {
            return alloc;
        }
        long remaining = totalAvailable;
        long[] room = new long[n];
        for (int i = 0; i < n; i++) {
            room[i] = Math.max(0L, sinkRooms[i]);
        }
        while (remaining > 0L) {
            int needy = 0;
            for (int i = 0; i < n; i++) {
                if (room[i] > 0L) {
                    needy++;
                }
            }
            if (needy == 0) {
                break;
            }
            long share = Math.max(1L, remaining / needy);
            boolean progressed = false;
            for (int i = 0; i < n && remaining > 0L; i++) {
                if (room[i] > 0L) {
                    long take = Math.min(room[i], Math.min(share, remaining));
                    if (take > 0L) {
                        alloc[i] += take;
                        room[i] -= take;
                        remaining -= take;
                        progressed = true;
                    }
                }
            }
            if (!progressed) {
                break;
            }
        }
        return alloc;
    }

    /**
     * Pure-Java simulate-first item count moved between a source slot and a destination slot,
     * guaranteeing strict conservation (0 moved when destination is full).
     */
    public static int computeItemMove(int sourceCount, int destCount, int maxStack) {
        int safeMax = Math.max(1, maxStack);
        int safeSrc = Math.max(0, sourceCount);
        int safeDst = Math.max(0, Math.min(safeMax, destCount));
        int room = safeMax - safeDst;
        return Math.min(safeSrc, room);
    }
}

package com.industrialcore.storage;

import com.industrialcore.machine.UpgradeMath;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 96: Pure-Java Quantum Storage Network logic, frequency channel registry,
 * node capacity aggregation, importer/exporter filter &amp; throughput math, and portable terminal
 * energy constants (zero Minecraft imports for deterministic unit testing).
 */
public final class QuantumNetLogic {
    public static final int CHANNEL_NAME_MAX_LEN = 20;
    public static final int FILTER_SLOTS = 9;

    // Importer / Exporter power & throughput constants
    public static final int IO_BASE_CAPACITY = 60_000;
    public static final int IO_MAX_RECEIVE = 8_000;
    public static final int IO_BASE_INTERVAL_TICKS = 20;
    public static final int IO_MIN_INTERVAL_TICKS = 2;
    public static final int IO_BASE_MAX_ITEMS = 16;
    public static final int IO_MAX_ITEMS = 256;
    public static final int IO_BASE_MAX_FLUID_MB = 1_000;
    public static final int IO_MAX_FLUID_MB = 16_000;
    public static final int IO_BASE_FE_PER_OP = 120;

    public static final int IO_STATE_IDLE = 0;
    public static final int IO_STATE_ACTIVE = 1;
    public static final int IO_STATE_NO_ENERGY = 2;
    public static final int IO_STATE_NO_CHANNEL = 3;
    public static final int IO_STATE_WAITING = 4;

    // Portable Quantum Terminal energy constants
    public static final int PORTABLE_ENERGY_CAPACITY = 200_000;
    public static final int PORTABLE_MAX_RECEIVE = 10_000;
    public static final int PORTABLE_OPEN_COST = 400;
    public static final int PORTABLE_ACTION_COST = 20;

    /** 6-direction offset matching Minecraft Direction.values() order (D, U, N, S, W, E). */
    public record Offset3(int dx, int dy, int dz) {}

    private static final Offset3[] DIR_OFFSETS = {
            new Offset3(0, -1, 0), // DOWN
            new Offset3(0, 1, 0),  // UP
            new Offset3(0, 0, -1), // NORTH
            new Offset3(0, 0, 1),  // SOUTH
            new Offset3(-1, 0, 0), // WEST
            new Offset3(1, 0, 0)   // EAST
    };

    private QuantumNetLogic() {}

    public static String makeNodeId(String dimensionId, int x, int y, int z) {
        return dimensionId + "@" + x + "," + y + "," + z;
    }

    public static String sanitizeChannelName(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return "CH-1";
        }
        if (trimmed.length() > CHANNEL_NAME_MAX_LEN) {
            return trimmed.substring(0, CHANNEL_NAME_MAX_LEN);
        }
        return trimmed;
    }

    // ------------------------------------------------------------------
    // Importer / Exporter math & 6-direction targeting
    // ------------------------------------------------------------------

    private static int clampMotors(int motors) {
        return Math.max(0, Math.min(UpgradeMath.MAX_UPGRADE_STACK, motors));
    }

    /** Operating interval in ticks: 20t at 0 motors down to 2t at 64 motors (with mastery time). */
    public static int ioIntervalTicks(int motors, int ownerLevel) {
        int m = clampMotors(motors);
        int base = IO_BASE_INTERVAL_TICKS
                - ((IO_BASE_INTERVAL_TICKS - IO_MIN_INTERVAL_TICKS) * m) / UpgradeMath.MAX_UPGRADE_STACK;
        return Math.max(IO_MIN_INTERVAL_TICKS, UpgradeMath.applyMasteryTime(base, ownerLevel));
    }

    /** Max items moved per cycle: 16 at 0 motors up to 256 at 64 motors. */
    public static int ioMaxItemsPerCycle(int motors) {
        int m = clampMotors(motors);
        return IO_BASE_MAX_ITEMS
                + ((IO_MAX_ITEMS - IO_BASE_MAX_ITEMS) * m) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** Max fluid mB moved per cycle: 1,000 mB at 0 motors up to 16,000 mB at 64 motors. */
    public static int ioMaxFluidMbPerCycle(int motors) {
        int m = clampMotors(motors);
        return IO_BASE_MAX_FLUID_MB
                + ((IO_MAX_FLUID_MB - IO_BASE_MAX_FLUID_MB) * m) / UpgradeMath.MAX_UPGRADE_STACK;
    }

    /** FE cost per successful transfer cycle (up to 35% battery reduction + mastery discount). */
    public static int ioEnergyPerOp(int batteries, int ownerLevel) {
        return Math.max(1, UpgradeMath.applyMasteryEnergy(
                UpgradeMath.discountedEnergy(IO_BASE_FE_PER_OP, batteries), ownerLevel));
    }

    /** 6-direction target offset for direction index 0..5 (DOWN, UP, NORTH, SOUTH, WEST, EAST). */
    public static Offset3 facingTargetOffset(int dirIndex) {
        if (dirIndex < 0 || dirIndex >= DIR_OFFSETS.length) {
            return DIR_OFFSETS[2];
        }
        return DIR_OFFSETS[dirIndex];
    }

    /**
     * Evaluates a 9-slot filter list against {@code candidateId}.
     * <ul>
     *   <li>When {@code blacklist == true}: passes unless {@code candidateId} appears in {@code filterIds}
     *       (empty list + blacklist passes all items).</li>
     *   <li>When {@code blacklist == false} (whitelist): passes only if {@code candidateId} appears in
     *       {@code filterIds}.</li>
     * </ul>
     */
    public static boolean matchesFilter(String candidateId, List<String> filterIds, boolean blacklist) {
        if (candidateId == null || candidateId.isEmpty()) {
            return false;
        }
        boolean found = false;
        if (filterIds != null) {
            for (String f : filterIds) {
                if (f != null && !f.isEmpty() && f.equals(candidateId)) {
                    found = true;
                    break;
                }
            }
        }
        return blacklist ? !found : found;
    }

    /**
     * Strict transfer conservation helper: never transfers more than available source, per-cycle
     * budget, or simulated target room (zero duplication, zero loss).
     */
    public static int computeSafeTransfer(int available, int cycleBudget, int targetRoom) {
        if (available <= 0 || cycleBudget <= 0 || targetRoom <= 0) {
            return 0;
        }
        return Math.min(available, Math.min(cycleBudget, targetRoom));
    }

    // ------------------------------------------------------------------
    // Frequency Channel & Network Registry (generic over K, F)
    // ------------------------------------------------------------------

    public static final class QuantumChannel<K, F> {
        private final String channelId;
        private String name;
        private final String ownerUuid;
        private boolean isPublic;
        private final QuantumStorageData<K, F> storage = new QuantumStorageData<>();
        private final Map<String, StorageCoreTier[]> nodes = new LinkedHashMap<>();
        private long revision = 1L;

        public QuantumChannel(String channelId, String name, String ownerUuid, boolean isPublic) {
            this.channelId = channelId;
            this.name = sanitizeChannelName(name);
            this.ownerUuid = ownerUuid == null ? "" : ownerUuid;
            this.isPublic = isPublic;
            recomputeCapacity();
        }

        public String channelId() {
            return channelId;
        }

        public String name() {
            return name;
        }

        public void setName(String nextName) {
            this.name = sanitizeChannelName(nextName);
            bumpRevision();
        }

        public String ownerUuid() {
            return ownerUuid;
        }

        public boolean isPublic() {
            return isPublic;
        }

        public void setPublic(boolean pub) {
            this.isPublic = pub;
            bumpRevision();
        }

        public QuantumStorageData<K, F> storage() {
            return storage;
        }

        public Map<String, StorageCoreTier[]> nodesView() {
            return nodes;
        }

        public int nodeCount() {
            return nodes.size();
        }

        public long revision() {
            return revision;
        }

        public void bumpRevision() {
            revision++;
        }

        public boolean canAccess(String requesterUuid) {
            if (isPublic) {
                return true;
            }
            return ownerUuid.isEmpty()
                    || (requesterUuid != null && !requesterUuid.isEmpty() && ownerUuid.equals(requesterUuid));
        }

        public boolean isOwner(String requesterUuid) {
            return ownerUuid.isEmpty()
                    || (requesterUuid != null && !requesterUuid.isEmpty() && ownerUuid.equals(requesterUuid));
        }

        /** Registers or updates a connected Quantum Storage Core node's 8 capacity core slots. */
        public void updateNode(String nodeId, StorageCoreTier[] coreSlots) {
            if (nodeId == null || nodeId.isEmpty()) {
                return;
            }
            StorageCoreTier[] copy = new StorageCoreTier[StorageCoreTier.CORE_SLOT_COUNT];
            if (coreSlots != null) {
                int len = Math.min(copy.length, coreSlots.length);
                System.arraycopy(coreSlots, 0, copy, 0, len);
            }
            nodes.put(nodeId, copy);
            recomputeCapacity();
            bumpRevision();
        }

        /** Unregisters a core node on disconnect or block break. Existing stored items are NEVER lost. */
        public boolean removeNode(String nodeId) {
            if (nodeId == null || nodes.remove(nodeId) == null) {
                return false;
            }
            recomputeCapacity();
            bumpRevision();
            return true;
        }

        /**
         * Aggregates maxTypes and maxPerType across all registered core nodes. If no nodes are
         * currently attached, falls back to base capacity without ever deleting stored items.
         */
        public void recomputeCapacity() {
            if (nodes.isEmpty()) {
                storage.setExternalCapacity(StorageCoreTier.BASE_MAX_TYPES, StorageCoreTier.BASE_MAX_PER_TYPE);
                return;
            }
            long totalTypes = 0L;
            BigInteger totalPerType = BigInteger.ZERO;
            for (StorageCoreTier[] slots : nodes.values()) {
                totalTypes += StorageCoreTier.totalMaxTypes(slots);
                totalPerType = totalPerType.add(StorageCoreTier.totalMaxPerType(slots));
            }
            int clampedTypes = (int) Math.min(Integer.MAX_VALUE, Math.max(StorageCoreTier.BASE_MAX_TYPES, totalTypes));
            if (totalPerType.compareTo(StorageCoreTier.BASE_MAX_PER_TYPE) < 0) {
                totalPerType = StorageCoreTier.BASE_MAX_PER_TYPE;
            }
            storage.setExternalCapacity(clampedTypes, totalPerType);
        }
    }

    public static final class QuantumNetworkRegistry<K, F> {
        private final Map<String, QuantumChannel<K, F>> channels = new LinkedHashMap<>();
        private int nextSeq = 1;
        private long globalRevision = 1L;

        public long globalRevision() {
            return globalRevision;
        }

        public void bumpGlobalRevision() {
            globalRevision++;
        }

        public int nextSeq() {
            return nextSeq;
        }

        public void setNextSeq(int seq) {
            this.nextSeq = Math.max(1, seq);
        }

        public QuantumChannel<K, F> createChannel(String rawName, String ownerUuid, boolean isPublic) {
            String id = "ch_" + (nextSeq++);
            while (channels.containsKey(id)) {
                id = "ch_" + (nextSeq++);
            }
            QuantumChannel<K, F> ch = new QuantumChannel<>(id, rawName, ownerUuid, isPublic);
            channels.put(id, ch);
            globalRevision++;
            return ch;
        }

        public void putChannelUnchecked(QuantumChannel<K, F> ch) {
            if (ch != null && ch.channelId() != null && !ch.channelId().isEmpty()) {
                channels.put(ch.channelId(), ch);
            }
        }

        public QuantumChannel<K, F> get(String channelId) {
            return (channelId == null || channelId.isEmpty()) ? null : channels.get(channelId);
        }

        public boolean deleteChannel(String channelId, String requesterUuid) {
            QuantumChannel<K, F> ch = get(channelId);
            if (ch == null || !ch.isOwner(requesterUuid)) {
                return false;
            }
            channels.remove(channelId);
            globalRevision++;
            return true;
        }

        public boolean togglePublic(String channelId, String requesterUuid) {
            QuantumChannel<K, F> ch = get(channelId);
            if (ch == null || !ch.isOwner(requesterUuid)) {
                return false;
            }
            ch.setPublic(!ch.isPublic());
            globalRevision++;
            return true;
        }

        public void updateNode(String channelId, String nodeId, StorageCoreTier[] cores) {
            QuantumChannel<K, F> ch = get(channelId);
            if (ch != null) {
                ch.updateNode(nodeId, cores);
                globalRevision++;
            }
        }

        public void removeNodeFromAll(String nodeId) {
            if (nodeId == null || nodeId.isEmpty()) {
                return;
            }
            boolean changed = false;
            for (QuantumChannel<K, F> ch : channels.values()) {
                if (ch.removeNode(nodeId)) {
                    changed = true;
                }
            }
            if (changed) {
                globalRevision++;
            }
        }

        public List<QuantumChannel<K, F>> allChannels() {
            return new ArrayList<>(channels.values());
        }

        public List<QuantumChannel<K, F>> visibleChannelsFor(String requesterUuid) {
            List<QuantumChannel<K, F>> out = new ArrayList<>();
            for (QuantumChannel<K, F> ch : channels.values()) {
                if (ch.canAccess(requesterUuid)) {
                    out.add(ch);
                }
            }
            out.sort(Comparator.comparing((QuantumChannel<K, F> c) -> c.name().toLowerCase(Locale.ROOT))
                    .thenComparing(QuantumChannel::channelId));
            return out;
        }

        public int size() {
            return channels.size();
        }
    }
}

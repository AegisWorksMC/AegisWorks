package com.industrialcore.machine;

/**
 * The four transporter tiers of Phases 26-29: the single source of truth for their performance
 * differences.
 *
 * <p>Everything that distinguishes one tier from another lives here - transfer interval, item cap
 * per transfer, buffer slots, FE capability and multiblock size - so a unit test can pin the whole
 * progression table and the four block entities stay free of magic numbers.</p>
 *
 * <p>Two spec rules are encoded as constants because they apply to <em>every</em> tier: a transfer
 * never costs FE ({@link #FE_COST_PER_TRANSFER}) and never loses a percentage of the moved FE
 * ({@link #POWER_LOSS_PERCENT}). Speed and capacity, never price, are what separate the tiers.</p>
 *
 * <p>{@code structureSize == 0} marks the Phase 28 single block: it never validates a shell.</p>
 */
public enum TransporterKind {
    // id, interval, itemCap, slots, powerPerTick, capacity, maxReceive, pushPerTick, structureSize, supportsPower
    /** Phase 26: one item every 20 ticks inside a 3x3x3 shell (25 casings). */
    BASIC("basic_item_transporter", 20, 1, 2, 0, 0, 0, 0, 3, false),
    /** Phase 27: 64 items every 5 ticks inside a 4x4x4 shell (55 casings). */
    ADVANCED("advanced_item_transporter", 5, 64, 8, 0, 0, 0, 0, 4, false),
    /** Phase 28: one stack per tick plus FE transfer, single block, no shell. */
    QUANTUM("quantum_transporter", 1, 64, 16, 50_000, 100_000, 50_000, 50_000, 0, true),
    /** Phase 29: 4096 items and 1,000,000 FE per tick inside a 5x5x5 shell (97 casings). */
    ARRAY("quantum_transporter_array", 1, 4096, 64, 1_000_000, 2_000_000, 1_000_000, 1_000_000, 5, true);

    /** The item transfer itself is free at every tier. */
    public static final int FE_COST_PER_TRANSFER = 0;
    /** FE transfer loses nothing at every tier (0 percent). */
    public static final int POWER_LOSS_PERCENT = 0;
    /** Slots per row of the buffer grid in the GUI. */
    public static final int GRID_COLUMNS = 8;

    private final String machineId;
    private final int transferIntervalTicks;
    private final int maxItemsPerTransfer;
    private final int slotCount;
    private final int powerTransferPerTick;
    private final int energyCapacity;
    private final int energyMaxReceive;
    private final int powerPushPerTick;
    private final int structureSize;
    private final boolean supportsPower;

    TransporterKind(String machineId, int transferIntervalTicks, int maxItemsPerTransfer,
                    int slotCount, int powerTransferPerTick, int energyCapacity,
                    int energyMaxReceive, int powerPushPerTick, int structureSize,
                    boolean supportsPower) {
        this.machineId = machineId;
        this.transferIntervalTicks = transferIntervalTicks;
        this.maxItemsPerTransfer = maxItemsPerTransfer;
        this.slotCount = slotCount;
        this.powerTransferPerTick = powerTransferPerTick;
        this.energyCapacity = energyCapacity;
        this.energyMaxReceive = energyMaxReceive;
        this.powerPushPerTick = powerPushPerTick;
        this.structureSize = structureSize;
        this.supportsPower = supportsPower;
    }

    /** Registry id of the controller block ({@code industrialcore:<id>}). */
    public String machineId() {
        return this.machineId;
    }

    /** Ticks between two transfer attempts. */
    public int transferIntervalTicks() {
        return this.transferIntervalTicks;
    }

    /** Maximum number of <em>items</em> (not stacks) one transfer may move. */
    public int maxItemsPerTransfer() {
        return this.maxItemsPerTransfer;
    }

    /** Buffer slots of this tier; every slot is a plain item buffer for pipes and players. */
    public int slotCount() {
        return this.slotCount;
    }

    /** Maximum FE one channel transfer may move (0 for the item-only tiers). */
    public int powerTransferPerTick() {
        return this.powerTransferPerTick;
    }

    /** Internal FE buffer capacity (0 for the item-only tiers). */
    public int energyCapacity() {
        return this.energyCapacity;
    }

    /** Rate at which cables may fill the internal buffer. */
    public int energyMaxReceive() {
        return this.energyMaxReceive;
    }

    /** Rate at which a receiver pushes its buffer into the neighbouring grid. */
    public int powerPushPerTick() {
        return this.powerPushPerTick;
    }

    /** Edge length of the multiblock shell, or 0 for the single-block Phase 28 device. */
    public int structureSize() {
        return this.structureSize;
    }

    /** True for Phase 28/29: the device has an FE buffer and can move FE. */
    public boolean supportsPower() {
        return this.supportsPower;
    }

    /** FE charged for performing a transfer - always 0, by spec. */
    public int feCostPerTransfer() {
        return FE_COST_PER_TRANSFER;
    }

    /** Percentage of FE lost while transferring - always 0, by spec. */
    public int powerLossPercent() {
        return POWER_LOSS_PERCENT;
    }

    /** Buffer rows in the GUI (8 columns). */
    public int gridRows() {
        return (this.slotCount + GRID_COLUMNS - 1) / GRID_COLUMNS;
    }

    /** Lookup by registry id; {@code null} when the id is not a transporter. */
    public static TransporterKind byMachineId(String id) {
        for (TransporterKind kind : values()) {
            if (kind.machineId.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}

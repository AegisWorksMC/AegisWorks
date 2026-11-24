package com.industrialcore.machine;

import com.industrialcore.Config;

/**
 * Tiers of the large energy buffer (Phase 17 basic, Phase 18 reinforced).
 *
 * <p>Both blocks share one block entity type, one menu and one screen, exactly like the processing
 * machines; the block instance decides the capacity and the throughput. The basic tier keeps the
 * Phase 17 values unchanged (1x), so an existing world behaves exactly as before.</p>
 *
 * <p>The reinforced buffer is the first consumer of the Phase 18 assembler parts
 * ({@code reinforced_machine_frame} and {@code electronic_component}).</p>
 *
 * <p><b>Where the throughput limits apply (Phase 36 audit, clarified in Phase 36.1):</b>
 * neither limit is a whole-equipment per-tick total. The push limit is applied per
 * neighbour face inside one push tick, and the receive limit is applied per receive call -
 * every connected pusher calls receiveEnergy once per tick. With all six faces connected
 * and each counterpart able to supply or accept enough FE, the theoretical per-tick totals
 * are up to six times the configured values (BASIC: 6 x 512 = 3,072 FE received and
 * 6 x 256 = 1,536 FE sent per tick; REINFORCED: 6 x 1,024 = 6,144 FE received and
 * 6 x 512 = 3,072 FE sent per tick). The real total always also depends on what the
 * connected devices can push out or take in.</p>
 */
public enum BufferTier {
    /** Phase 17: 250,000 FE, 512 FE per receive call, 256 FE/t push per neighbour. */
    BASIC("energy_buffer", "block.industrialcore.energy_buffer", 1, 1, 1),
    /** Phase 18: 4x storage, 1,024 FE per receive call, 512 FE/t push per neighbour. */
    REINFORCED("energy_buffer_mk2", "block.industrialcore.energy_buffer_mk2", 4, 2, 2),
    /**
     * Phase 30: the bank of the high amplifier tiers. Sixteen times the storage of the basic tier
     * (4,000,000 FE), eight times the per-call receive limit and sixteen times the push per
     * neighbour (4,096 FE/t). Two of them can feed one 8,192 FE/t amplifier, five hold one
     * x32,768 cycle.
     */
    HIGH_DENSITY("energy_buffer_mk3", "block.industrialcore.energy_buffer_mk3", 16, 8, 16);

    private final String id;
    private final String translationKey;
    private final int capacityMultiplier;
    private final int receiveMultiplier;
    private final int pushMultiplier;

    BufferTier(String id, String translationKey, int capacityMultiplier, int receiveMultiplier,
               int pushMultiplier) {
        this.id = id;
        this.translationKey = translationKey;
        this.capacityMultiplier = capacityMultiplier;
        this.receiveMultiplier = receiveMultiplier;
        this.pushMultiplier = pushMultiplier;
    }

    public String id() {
        return this.id;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public int capacityMultiplier() {
        return this.capacityMultiplier;
    }

    public int receiveMultiplier() {
        return this.receiveMultiplier;
    }

    public int pushMultiplier() {
        return this.pushMultiplier;
    }

    /** Stored FE of one block of this tier. */
    public int capacity() {
        return scaled(Config.energyBufferCapacity(), this.capacityMultiplier);
    }

    /**
     * FE accepted per receive call: each connected pusher calls receiveEnergy once per
     * tick, so up to six faces can deliver six times this value per tick in total.
     */
    public int maxReceive() {
        return scaled(Config.energyBufferMaxReceive(), this.receiveMultiplier);
    }

    /** FE pushed into one neighbour per tick - a per-face limit, not a per-tick total. */
    public int pushPerTick() {
        return scaled(Config.energyBufferPushPerTick(), this.pushMultiplier);
    }

    /** Multiplies a config value without overflowing an int. */
    public static int scaled(int base, int multiplier) {
        long value = (long) Math.max(0, base) * Math.max(0, multiplier);
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    public static BufferTier byIndex(int index) {
        BufferTier[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}

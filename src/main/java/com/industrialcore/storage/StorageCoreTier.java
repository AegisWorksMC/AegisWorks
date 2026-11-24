package com.industrialcore.storage;

import java.math.BigInteger;

/**
 * Phase 89: Five tiers of capacity expansion modules for the Quantum Storage Core.
 *
 * <p>Pure Java (no Minecraft imports) so storage math is directly unit tested.</p>
 */
public enum StorageCoreTier {
    MK1("storage_core_mk1", 1, 64, BigInteger.valueOf(65_536L), 0xFF55FFFF),
    MK2("storage_core_mk2", 2, 512, BigInteger.valueOf(10_000_000L), 0xFF55FF88),
    MK3("storage_core_mk3", 3, 2_048, BigInteger.valueOf(100_000_000_000L), 0xFFFFD700),
    MK4("storage_core_mk4", 4, 8_192, BigInteger.TEN.pow(21), 0xFFFF7755),
    MK5("storage_core_mk5", 5, 65_536, BigInteger.TEN.pow(68), 0xFFFFE57F);

    /** Initial capacity of the Quantum Storage Core with zero cores installed. */
    public static final int BASE_MAX_TYPES = 32;
    public static final BigInteger BASE_MAX_PER_TYPE = BigInteger.valueOf(16_384L);
    /** Number of dedicated storage-core upgrade slots in the block entity & GUI. */
    public static final int CORE_SLOT_COUNT = 8;

    private final String id;
    private final int tier;
    private final int bonusTypes;
    private final BigInteger bonusPerType;
    private final int accentColor;

    StorageCoreTier(String id, int tier, int bonusTypes, BigInteger bonusPerType, int accentColor) {
        this.id = id;
        this.tier = tier;
        this.bonusTypes = bonusTypes;
        this.bonusPerType = bonusPerType;
        this.accentColor = accentColor;
    }

    public String id() {
        return id;
    }

    public int tier() {
        return tier;
    }

    public int bonusTypes() {
        return bonusTypes;
    }

    public BigInteger bonusPerType() {
        return bonusPerType;
    }

    public int accentColor() {
        return accentColor;
    }

    public static int totalMaxTypes(StorageCoreTier[] slots) {
        long total = BASE_MAX_TYPES;
        if (slots != null) {
            for (StorageCoreTier core : slots) {
                if (core != null) {
                    total += core.bonusTypes;
                }
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static BigInteger totalMaxPerType(StorageCoreTier[] slots) {
        BigInteger total = BASE_MAX_PER_TYPE;
        if (slots != null) {
            for (StorageCoreTier core : slots) {
                if (core != null) {
                    total = total.add(core.bonusPerType);
                }
            }
        }
        return total;
    }

    public static StorageCoreTier byId(String id) {
        if (id == null) {
            return null;
        }
        for (StorageCoreTier t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}

package com.industrialcore.machine;

/**
 * The conventional power devices (Phase 33, geothermal added in Phase 41). The block instance
 * carries the kind, so one
 * block entity type, one menu and one screen serve all six - exactly like the processing
 * machines share their type and the block selects the recipe source.
 *
 * <p>Values are the rated output (FE/t while active), the internal FE buffer and the FE pushed
 * into one neighbour per tick. Fuel kinds burn any vanilla-burnable item plus the fuel can and
 * the fuel rod; their fuel content and burn length are derived from the fuel, not from the kind.
 * The nuclear generator halves its output without adjacent water and the wind turbine derives
 * its output from the air above it (see {@code GenerationBalance.windOutput}).</p>
 */
public enum GenerationKind {
    /** 火力: solid fuel boiler. 90 FE/t, coal lasts 640 ticks (57,600 FE direct). */
    THERMAL("thermal_generator", 90, 100_000, 256, true, Byproduct.EXHAUST),
    /** 水力: needs an adjacent water block; no fuel. */
    HYDRO("hydro_generator", 50, 50_000, 200, false, Byproduct.NONE),
    /** 風力: output scales with the open air above it (18..70 FE/t). */
    WIND("wind_generator", 0, 50_000, 200, false, Byproduct.NONE),
    /** 太陽光: 60 FE/t in daylight with sky access, 0 otherwise. */
    SOLAR("solar_generator", 60, 50_000, 200, false, Byproduct.NONE),
    /** 内燃機関: fuel can / lava bucket, 160 FE/t, pools waste heat. */
    COMBUSTION("combustion_generator", 160, 150_000, 512, true, Byproduct.HEAT),
    /** 原子力: 400 FE/t with water cooling, 200 without; pools exhaust steam. */
    NUCLEAR("nuclear_generator", 400, 200_000, 900, true, Byproduct.EXHAUST),
    /**
     * 地熱 (Phase 41): 90 FE/t while any of the six neighbours is a vanilla lava block.
     * No fuel, no consumption - the lava is only a placement condition, like water for the
     * hydro generator, so the check is cached and refreshed on the same timer.
     */
    GEOTHERMAL("geothermal_generator", 90, 100_000, 256, false, Byproduct.NONE);

    /** What a fuel generator pools for the recovery devices. */
    public enum Byproduct {
        NONE,
        /** Compressed exhaust, drained by the pressure recovery device. */
        EXHAUST,
        /** Hot surfaces/exhaust, drained by the heat recovery device. */
        HEAT
    }

    private final String id;
    private final int output;
    private final int capacity;
    private final int pushPerTick;
    private final boolean hasFuel;
    private final Byproduct byproduct;

    GenerationKind(String id, int output, int capacity, int pushPerTick, boolean hasFuel,
                   Byproduct byproduct) {
        this.id = id;
        this.output = output;
        this.capacity = capacity;
        this.pushPerTick = pushPerTick;
        this.hasFuel = hasFuel;
        this.byproduct = byproduct;
    }

    public String id() {
        return this.id;
    }

    public String translationKey() {
        return "block.industrialcore." + this.id;
    }

    /** Rated output in FE/t while the device is active (0 for the wind turbine's formula). */
    public int output() {
        return this.output;
    }

    public int capacity() {
        return this.capacity;
    }

    /** FE pushed into one neighbour per tick. Always at least the rated output. */
    public int pushPerTick() {
        return this.pushPerTick;
    }

    /** True when the device has a fuel slot in its GUI. */
    public boolean hasFuel() {
        return this.hasFuel;
    }

    public Byproduct byproduct() {
        return this.byproduct;
    }

    /** True when the device only runs with an adjacent water block (hydro, nuclear cooling). */
    public boolean needsWater() {
        return this == HYDRO || this == NUCLEAR;
    }

    /** True when the device only runs with an adjacent vanilla lava block (geothermal). */
    public boolean needsLava() {
        return this == GEOTHERMAL;
    }
}
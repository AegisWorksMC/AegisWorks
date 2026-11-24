package com.industrialcore.machine;

import com.industrialcore.Config;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The processing machines of the ore line.
 *
 * <p>One block entity type serves every machine, exactly like the four pipe and cable tiers share
 * a type; the block instance decides which recipe source is used.</p>
 *
 * <ul>
 *   <li>{@link #CRUSHER}: 2x line (raw ore -> 2 dust) and the pure-ore step (pure ore -> 3 dust),</li>
 *   <li>{@link #ELECTRIC_FURNACE}: vanilla smelting at twice the vanilla speed,</li>
 *   <li>{@link #ELECTROLYZER}: 3x and 6x line (raw ore -> pure ore, exposed ore -> 2 pure ore),</li>
 *   <li>{@link #PRECISION_CRUSHER}: 6x entry (silk-touch ore block -> exposed ore),</li>
 *   <li>{@link #FAST_CRUSHER}: the shared crushing recipes, but one cycle crushes a whole batch,
 *   <li>{@link #METAL_PRESS}: machine parts (ingot -> plate, Phase 17).</li>
 * </ul>
 */
public enum ProcessingKind {
    CRUSHER("crusher", "block.industrialcore.crusher"),
    ELECTRIC_FURNACE("electric_furnace", "block.industrialcore.electric_furnace"),
    ELECTROLYZER("electrolyzer", "block.industrialcore.electrolyzer"),
    PRECISION_CRUSHER("precision_crusher", "block.industrialcore.precision_crusher"),
    /**
     * Phase 17: metal press. Appended at the end on purpose; the ordinal of the existing kinds is
     * never allowed to change.
     */
    METAL_PRESS("metal_press", "block.industrialcore.metal_press"),
    /**
     * Phase 23: fast crusher. Uses the same {@code industrialcore:crushing} recipes as
     * {@link #CRUSHER}, but one cycle processes a whole batch
     * ({@link ProcessingBalance#FAST_CRUSH_BATCH}) at once: the recipe's time per cycle and the
     * recipe's FE per tick multiplied by the batch, so the FE per item never changes. Appended
     * at the end, like every new kind, so the existing ordinals never change.
     */
    FAST_CRUSHER("fast_crusher", "block.industrialcore.fast_crusher");

    private final String id;
    private final String translationKey;

    ProcessingKind(String id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public String id() {
        return this.id;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public ResourceLocation registryId() {
        return ResourceLocation.fromNamespaceAndPath(com.industrialcore.IndustrialCore.MOD_ID, this.id);
    }

    /** Internal FE buffer size. */
    public int capacity() {
        return switch (this) {
            case CRUSHER -> Config.crusherCapacity();
            case ELECTRIC_FURNACE -> Config.furnaceCapacity();
            case ELECTROLYZER, PRECISION_CRUSHER, FAST_CRUSHER -> ProcessingBalance.MACHINE_CAPACITY;
            case METAL_PRESS -> ProcessingBalance.MACHINE_CAPACITY;
        };
    }

    /** Maximum FE accepted per tick from cables and generators. */
    public int maxReceive() {
        return switch (this) {
            case CRUSHER -> Config.crusherMaxReceive();
            case ELECTRIC_FURNACE -> Config.furnaceMaxReceive();
            case ELECTROLYZER, PRECISION_CRUSHER, FAST_CRUSHER -> ProcessingBalance.MACHINE_MAX_RECEIVE;
            case METAL_PRESS -> ProcessingBalance.MACHINE_MAX_RECEIVE;
        };
    }

    /** Default duration shown by the GUI while idle. A matching recipe may override it. */
    public int defaultTimeTicks() {
        return switch (this) {
            case CRUSHER, FAST_CRUSHER -> Config.crusherTimeTicks();
            case ELECTRIC_FURNACE -> ProcessingBalance.furnaceTime(
                    ProcessingBalance.VANILLA_FURNACE_TIME_TICKS, furnaceSpeedPercent());
            case ELECTROLYZER -> ProcessingBalance.ELECTROLYZER_TIME_TICKS;
            case PRECISION_CRUSHER -> ProcessingBalance.PRECISION_TIME_TICKS;
            case METAL_PRESS -> ProcessingBalance.METAL_PRESS_TIME_TICKS;
        };
    }

    /** Default FE per tick while idle. A matching recipe may override it. */
    public int defaultEnergyPerTick() {
        return switch (this) {
            case CRUSHER -> Config.crusherEnergyPerTick();
            case FAST_CRUSHER -> (int) Math.min(Integer.MAX_VALUE,
                    (long) Config.crusherEnergyPerTick() * ProcessingBalance.FAST_CRUSH_BATCH);
            case ELECTRIC_FURNACE -> Config.furnaceEnergyPerTick();
            case ELECTROLYZER -> ProcessingBalance.ELECTROLYZER_ENERGY_PER_TICK;
            case PRECISION_CRUSHER -> ProcessingBalance.PRECISION_ENERGY_PER_TICK;
            case METAL_PRESS -> ProcessingBalance.METAL_PRESS_ENERGY_PER_TICK;
        };
    }

    /** Speed of the electric furnace as a percentage of the vanilla cooking time. */
    public int furnaceSpeedPercent() {
        return Config.furnaceSpeedPercent();
    }

    /** True for the machines that read the mod's own single-input recipe types. */
    public boolean usesIndustrialRecipe() {
        return this == CRUSHER || this == FAST_CRUSHER || processorFamily() != null;
    }

    /**
     * The {@link ProcessorRecipe} family of this machine, or {@code null} when it uses the crushing
     * type or the vanilla smelting type.
     */
    @Nullable
    public ProcessorRecipe.Family processorFamily() {
        return switch (this) {
            case ELECTROLYZER -> ProcessorRecipe.Family.ELECTROLYSIS;
            case PRECISION_CRUSHER -> ProcessorRecipe.Family.PRECISION_CRUSHING;
            case METAL_PRESS -> ProcessorRecipe.Family.PRESSING;
            case CRUSHER, FAST_CRUSHER, ELECTRIC_FURNACE -> null;
        };
    }

    public static ProcessingKind byIndex(int index) {
        ProcessingKind[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}

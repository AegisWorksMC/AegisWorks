package com.industrialcore.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase 109–110: Dedicated-server-safe hook so common {@code Block#animateTick} implementations
 * can delegate Tier 3 distance- and particle-setting-throttled client particles without
 * importing any {@code net.minecraft.client.*} classes.
 */
public final class LivingFactoryParticleHook {
    public enum EffectKind {
        // Phase 109
        NUCLEAR_STEAM,
        SINGULARITY_SPARK,
        PROCESSING_METAL_SPARK,
        PROCESSING_HEAT_EMBER,
        PROCESSING_ARCANE_SPARK,
        // Phase 110: Generators & Quarries
        GENERATOR_SMOKE,
        GENERATOR_HEAT_SPARK,
        GENERATOR_WATER_MIST,
        QUARRY_LASER_SPARK,
        // Phase 110: Migrated from server sendParticles to throttled client animateTick
        GROWTH_PULSE,
        BIO_FEED,
        MOB_FAN_WIND,
        ROTARY_DRILL_SPARK,
        ITEM_VACUUM_SWIRL,
        XP_COLLECTOR_ORB,
        MOB_DUPLICATOR_BIO,
        AUTO_BUILDER_SPARK,
        FLUID_PUMP_SPLASH,
        WIRELESS_CHARGER_ARC,
        CLIMATE_CONTROLLER_BEACON,
        WARP_PAD_PORTAL,
        SUIT_MODIFIER_HOLO,
        // Phase 111: Motion-synced ambient audio pulses for non-BER active machines
        AUDIO_CRAFTER_WELD,
        AUDIO_HARVESTER_SHEAR,
        AUDIO_QUANTUM_CHIME,
        AUDIO_REGEN_HUM
    }

    @FunctionalInterface
    public interface Handler {
        void onAnimateTick(EffectKind kind, BlockState state, Level level, BlockPos pos, RandomSource random);
    }

    public static volatile Handler handler = (kind, state, level, pos, random) -> {};

    private LivingFactoryParticleHook() {}
}

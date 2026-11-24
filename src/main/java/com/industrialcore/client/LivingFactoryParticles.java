package com.industrialcore.client;

import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.blockentity.SingularityReactorBlockEntity;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import com.industrialcore.machine.LivingFactoryParticleHook;
import com.industrialcore.reactor.ReactorMultiblockLogic.IntPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Phase 109–110 Tier 3: Unified client-only factory atmosphere particles invoked via
 * {@link LivingFactoryParticleHook} from {@code Block#animateTick}.
 *
 * <p>Strictly skips all particle spawns when the local player is farther than
 * {@link LivingFactoryAnimLogic#PARTICLE_MAX_DISTANCE} (24 blocks) OR when the client's
 * Particles setting is {@link ParticleStatus#MINIMAL}.</p>
 */
public final class LivingFactoryParticles {
    private LivingFactoryParticles() {}

    public static void onAnimateTick(LivingFactoryParticleHook.EffectKind kind,
                                     BlockState state, Level level, BlockPos pos, RandomSource random) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // Phase 112: Trigger distance-gated (12m) & budget-throttled per-machine SFX for non-BER machines
        dispatchAmbientFactorySfx(kind, state, level, pos);

        boolean isMinimal = mc.options.particles().get() == ParticleStatus.MINIMAL;
        double distSq = mc.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        if (!LivingFactoryAnimLogic.shouldSpawnParticle(distSq, isMinimal)) {
            return;
        }

        switch (kind) {
            case NUCLEAR_STEAM -> spawnNuclearCoolingSteam(level, pos, random);
            case SINGULARITY_SPARK -> spawnSingularityQuantumSpark(level, pos, random);
            case PROCESSING_METAL_SPARK -> spawnProcessingMicroSpark(state, level, pos, random, 0);
            case PROCESSING_HEAT_EMBER -> spawnProcessingMicroSpark(state, level, pos, random, 1);
            case PROCESSING_ARCANE_SPARK -> spawnProcessingMicroSpark(state, level, pos, random, 2);
            case GENERATOR_SMOKE -> spawnGeneratorSmoke(level, pos, random);
            case GENERATOR_HEAT_SPARK -> spawnGeneratorHeat(level, pos, random);
            case GENERATOR_WATER_MIST -> spawnGeneratorMist(level, pos, random);
            case QUARRY_LASER_SPARK -> spawnQuarryLaserSpark(level, pos, random);
            case GROWTH_PULSE -> spawnSimpleAbove(level, pos, random, ParticleTypes.HAPPY_VILLAGER, 0.35F, 0.95D, 0.45D, 0.02D);
            case BIO_FEED -> spawnSimpleAbove(level, pos, random, ParticleTypes.HAPPY_VILLAGER, 0.25F, 1.05D, 0.32D, 0.015D);
            case MOB_FAN_WIND -> spawnMobFanWind(state, level, pos, random);
            case ROTARY_DRILL_SPARK -> spawnRotaryDrillSpark(state, level, pos, random);
            case ITEM_VACUUM_SWIRL -> spawnSimpleAbove(level, pos, random, ParticleTypes.PORTAL, 0.40F, 1.02D, 0.38D, -0.04D);
            case XP_COLLECTOR_ORB -> spawnSimpleAbove(level, pos, random, ParticleTypes.HAPPY_VILLAGER, 0.32F, 0.95D, 0.32D, 0.02D);
            case MOB_DUPLICATOR_BIO -> spawnSimpleAbove(level, pos, random, ParticleTypes.WITCH, 0.38F, 1.50D, 0.55D, 0.02D);
            case AUTO_BUILDER_SPARK -> spawnSimpleAbove(level, pos, random, ParticleTypes.ELECTRIC_SPARK, 0.30F, 1.05D, 0.30D, 0.025D);
            case FLUID_PUMP_SPLASH -> spawnSimpleAbove(level, pos, random, ParticleTypes.SPLASH, 0.42F, 1.04D, 0.22D, 0.04D);
            case WIRELESS_CHARGER_ARC -> spawnSimpleAbove(level, pos, random, ParticleTypes.ELECTRIC_SPARK, 0.45F, 1.08D, 0.32D, 0.025D);
            case CLIMATE_CONTROLLER_BEACON -> spawnSimpleAbove(level, pos, random, ParticleTypes.END_ROD, 0.42F, 2.75D, 0.25D, 0.03D);
            case WARP_PAD_PORTAL -> spawnSimpleAbove(level, pos, random, ParticleTypes.PORTAL, 0.50F, 0.55D, 0.65D, 0.05D);
            case SUIT_MODIFIER_HOLO -> spawnSimpleAbove(level, pos, random, ParticleTypes.ELECTRIC_SPARK, 0.32F, 1.45D, 0.26D, 0.015D);
            case AUDIO_CRAFTER_WELD, AUDIO_HARVESTER_SHEAR, AUDIO_QUANTUM_CHIME, AUDIO_REGEN_HUM -> {}
        }
    }

    private static void dispatchAmbientFactorySfx(LivingFactoryParticleHook.EffectKind kind,
                                                  BlockState state, Level level, BlockPos pos) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        switch (kind) {
            case PROCESSING_HEAT_EMBER, PROCESSING_ARCANE_SPARK,
                 BIO_FEED, AUDIO_HARVESTER_SHEAR, AUDIO_CRAFTER_WELD, AUDIO_QUANTUM_CHIME ->
                    FactoryAudioController.tryPlayAmbientPulse(pos, blockId);
            case GENERATOR_SMOKE, GENERATOR_HEAT_SPARK, AUDIO_REGEN_HUM -> {
                if (isGeneratorOrRecoveryRunning(level, pos)) {
                    FactoryAudioController.tryPlayAmbientPulse(pos, blockId);
                }
            }
            default -> {}
        }
    }

    private static boolean isGeneratorOrRecoveryRunning(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GenerationBlockEntity gen) {
            return gen.isClientRunning();
        }
        if (be instanceof RecoveryBlockEntity rec) {
            return rec.isClientRunning();
        }
        return false;
    }

    private static void spawnGeneratorSmoke(Level level, BlockPos pos, RandomSource random) {
        if (!isGeneratorOrRecoveryRunning(level, pos) || random.nextFloat() >= 0.42F) {
            return;
        }
        double cx = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.28D;
        double cy = pos.getY() + 1.25D;
        double cz = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.28D;
        level.addParticle(ParticleTypes.SMOKE, cx, cy, cz, 0.0D, 0.032D + random.nextDouble() * 0.015D, 0.0D);
    }

    private static void spawnGeneratorHeat(Level level, BlockPos pos, RandomSource random) {
        if (!isGeneratorOrRecoveryRunning(level, pos) || random.nextFloat() >= 0.34F) {
            return;
        }
        double cx = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.38D;
        double cy = pos.getY() + 1.12D;
        double cz = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.38D;
        level.addParticle(ParticleTypes.FLAME, cx, cy, cz, 0.0D, 0.022D, 0.0D);
        if (random.nextFloat() < 0.45F) {
            level.addParticle(ParticleTypes.SMOKE, cx, cy + 0.08D, cz, 0.0D, 0.028D, 0.0D);
        }
    }

    private static void spawnGeneratorMist(Level level, BlockPos pos, RandomSource random) {
        if (!isGeneratorOrRecoveryRunning(level, pos) || random.nextFloat() >= 0.38F) {
            return;
        }
        double cx = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.35D;
        double cy = pos.getY() + 1.10D;
        double cz = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.35D;
        level.addParticle(ParticleTypes.CLOUD, cx, cy, cz, 0.0D, 0.026D, 0.0D);
    }

    private static void spawnQuarryLaserSpark(Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() >= 0.40F) {
            return;
        }
        double cx = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.30D;
        double cy = pos.getY() + 1.06D;
        double cz = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.30D;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 0.0D, 0.032D, 0.0D);
    }

    private static void spawnMobFanWind(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() >= 0.72F) {
            return;
        }
        Direction dir = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.NORTH;
        double ox = pos.getX() + 0.5D + dir.getStepX() * 0.62D;
        double oy = pos.getY() + 0.5D + dir.getStepY() * 0.62D;
        double oz = pos.getZ() + 0.5D + dir.getStepZ() * 0.62D;
        double jx = dir.getStepX() == 0 ? (random.nextDouble() - 0.5D) * 0.60D : 0.0D;
        double jy = dir.getStepY() == 0 ? (random.nextDouble() - 0.5D) * 0.60D : 0.0D;
        double jz = dir.getStepZ() == 0 ? (random.nextDouble() - 0.5D) * 0.60D : 0.0D;
        level.addParticle(ParticleTypes.CLOUD, ox + jx, oy + jy, oz + jz,
                dir.getStepX() * 0.22D, dir.getStepY() * 0.22D, dir.getStepZ() * 0.22D);
    }

    private static void spawnRotaryDrillSpark(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() >= 0.45F) {
            return;
        }
        Direction dir = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.NORTH;
        double px = pos.getX() + 0.5D + dir.getStepX() * 0.68D + (random.nextDouble() - 0.5D) * 0.25D;
        double py = pos.getY() + 0.5D + dir.getStepY() * 0.68D + (random.nextDouble() - 0.5D) * 0.25D;
        double pz = pos.getZ() + 0.5D + dir.getStepZ() * 0.68D + (random.nextDouble() - 0.5D) * 0.25D;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz,
                dir.getStepX() * 0.04D, 0.025D, dir.getStepZ() * 0.04D);
    }

    private static void spawnSimpleAbove(Level level, BlockPos pos, RandomSource random,
                                         net.minecraft.core.particles.SimpleParticleType type,
                                         float chance, double yOff, double spread, double vy) {
        if (random.nextFloat() >= chance) {
            return;
        }
        double cx = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D * spread;
        double cy = pos.getY() + yOff;
        double cz = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D * spread;
        level.addParticle(type, cx, cy, cz, 0.0D, vy, 0.0D);
    }

    private static void spawnNuclearCoolingSteam(Level level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof GenerationBlockEntity gen)
                || gen.kind() != GenerationKind.NUCLEAR
                || !gen.isMultiblockFormed()
                || !gen.isClientWaterCooled()) {
            return;
        }
        if (random.nextFloat() >= 0.65F) {
            return;
        }
        IntPos min = gen.getClientMinPos();
        IntPos max = gen.getClientMaxPos();
        double cx = (min != null && max != null) ? (min.x() + max.x() + 1.0D) * 0.5D : pos.getX() + 0.5D;
        double topY = (max != null) ? max.y() + 1.08D : pos.getY() + 1.08D;
        double cz = (min != null && max != null) ? (min.z() + max.z() + 1.0D) * 0.5D : pos.getZ() + 0.5D;
        double spread = (min != null && max != null) ? Math.max(0.35D, (max.x() - min.x()) * 0.28D) : 0.35D;

        double ox = (random.nextDouble() - 0.5D) * 2.0D * spread;
        double oz = (random.nextDouble() - 0.5D) * 2.0D * spread;
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                cx + ox, topY, cz + oz,
                (random.nextDouble() - 0.5D) * 0.008D,
                0.045D + random.nextDouble() * 0.025D,
                (random.nextDouble() - 0.5D) * 0.008D);
        if (random.nextFloat() < 0.45F) {
            level.addParticle(ParticleTypes.CLOUD,
                    cx + ox * 0.6D, topY + 0.05D, cz + oz * 0.6D,
                    0.0D, 0.032D + random.nextDouble() * 0.018D, 0.0D);
        }
    }

    private static void spawnSingularityQuantumSpark(Level level, BlockPos pos, RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SingularityReactorBlockEntity sr) || !sr.isMultiblockFormed()) {
            return;
        }
        if (random.nextFloat() >= 0.70F) {
            return;
        }
        IntPos min = sr.getClientMinPos();
        IntPos max = sr.getClientMaxPos();
        double cx = (min != null && max != null) ? (min.x() + max.x() + 1.0D) * 0.5D : pos.getX() + 0.5D;
        double cy = (min != null && max != null) ? (min.y() + max.y() + 1.0D) * 0.5D : pos.getY() + 0.8D;
        double cz = (min != null && max != null) ? (min.z() + max.z() + 1.0D) * 0.5D : pos.getZ() + 0.5D;
        double r = (min != null && max != null) ? Math.max(0.45D, ((max.x() - min.x()) - 1) * 0.32D) : 0.45D;

        double ox = (random.nextDouble() - 0.5D) * 2.0D * r;
        double oy = (random.nextDouble() - 0.5D) * 1.4D * r;
        double oz = (random.nextDouble() - 0.5D) * 2.0D * r;
        level.addParticle(ParticleTypes.END_ROD,
                cx + ox, cy + oy, cz + oz,
                -ox * 0.015D, 0.012D, -oz * 0.015D);
        if (random.nextFloat() < 0.55F) {
            level.addParticle(ParticleTypes.PORTAL,
                    cx + ox * 1.15D, cy + oy, cz + oz * 1.15D,
                    -ox * 0.18D, -oy * 0.12D, -oz * 0.18D);
        }
    }

    private static void spawnProcessingMicroSpark(BlockState state, Level level, BlockPos pos,
                                                  RandomSource random, int variant) {
        if (random.nextFloat() >= 0.24F) {
            return;
        }
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        double fx = pos.getX() + 0.5D + facing.getStepX() * 0.52D + (random.nextDouble() - 0.5D) * 0.34D;
        double fy = pos.getY() + 0.46D + (random.nextDouble() - 0.5D) * 0.24D;
        double fz = pos.getZ() + 0.5D + facing.getStepZ() * 0.52D + (random.nextDouble() - 0.5D) * 0.34D;

        if (variant == 1) {
            level.addParticle(ParticleTypes.FLAME, fx, fy, fz,
                    facing.getStepX() * 0.012D, 0.014D, facing.getStepZ() * 0.012D);
            if (random.nextFloat() < 0.45F) {
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 1.02D, pos.getZ() + 0.5D,
                        0.0D, 0.020D, 0.0D);
            }
        } else if (variant == 2) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, fx, fy, fz,
                    facing.getStepX() * 0.02D, 0.018D, facing.getStepZ() * 0.02D);
        } else {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, fx, fy, fz,
                    facing.getStepX() * 0.025D + (random.nextDouble() - 0.5D) * 0.02D,
                    0.025D,
                    facing.getStepZ() * 0.025D + (random.nextDouble() - 0.5D) * 0.02D);
        }
    }
}

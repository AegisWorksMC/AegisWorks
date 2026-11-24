package com.industrialcore.raid.entity;

import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Phase 116: Original Rift Enemy #1 — {@code rift_stalker} (次元裂傷体 / Rift Stalker).
 *
 * <p>High-speed floating crystal-core skirmisher with geometric blade-wings that periodically
 * executes short-range spatial blinks (5..8m) to flank turret firing arcs and rush defenders.</p>
 */
public class RiftStalkerEntity extends AbstractRiftInvaderEntity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_JAMMED_TICKS =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    RiftStalkerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    private int blinkCooldown = 50;
    private int recentHitCount = 0;
    private long lastHitWindowTick = -1000L;

    public RiftStalkerEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 18;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_JAMMED_TICKS, 0);
    }

    public boolean isPhaseJammed() {
        return this.entityData.get(DATA_JAMMED_TICKS) > 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected double baseHealth() {
        return 36.0D;
    }

    @Override
    protected double baseAttack() {
        return 7.0D;
    }

    @Override
    protected double baseArmor() {
        return 4.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.34D;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && level() instanceof ServerLevel sl) {
            long now = sl.getGameTime();
            if (now - lastHitWindowTick > com.industrialcore.raid.RiftArenaLogic.STALKER_JAM_WINDOW_TICKS) {
                recentHitCount = 1;
            } else {
                recentHitCount++;
            }
            lastHitWindowTick = now;
            // Phase 120 Weakness: Rapid consecutive hits (Pulse Laser / Blade sweep) trigger Phase Jamming!
            if (recentHitCount >= com.industrialcore.raid.RiftArenaLogic.STALKER_JAM_HIT_THRESHOLD && !isPhaseJammed()) {
                recentHitCount = 0;
                this.entityData.set(DATA_JAMMED_TICKS, com.industrialcore.raid.RiftArenaLogic.STALKER_JAM_DURATION_TICKS);
                this.blinkCooldown = Math.max(this.blinkCooldown, com.industrialcore.raid.RiftArenaLogic.STALKER_JAM_DURATION_TICKS + 15);
                this.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                        com.industrialcore.raid.RiftArenaLogic.STALKER_JAM_DURATION_TICKS, 3, false, true));
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0D, getZ(), 14, 0.35D, 0.55D, 0.35D, 0.08D);
                if (source.getEntity() instanceof Player p) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.industrialcore.rift_weakness_jammed").withStyle(net.minecraft.ChatFormatting.AQUA), true);
                }
            }
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            int jam = this.entityData.get(DATA_JAMMED_TICKS);
            if (jam > 0) {
                this.entityData.set(DATA_JAMMED_TICKS, jam - 1);
                if (jam % 6 == 0) {
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0D, getZ(), 3, 0.25D, 0.4D, 0.25D, 0.03D);
                }
                return; // Blink disabled while phase-jammed!
            }
            if (blinkCooldown > 0) {
                blinkCooldown--;
            } else {
                trySpatialBlink(sl);
            }
        }
    }

    private void trySpatialBlink(ServerLevel sl) {
        LivingEntity target = getTarget();
        Vec3 aimPoint = target != null
                ? target.position()
                : (reactorAnchor != null ? Vec3.atBottomCenterOf(reactorAnchor) : null);
        if (aimPoint == null) {
            blinkCooldown = 40;
            return;
        }
        double distSq = distanceToSqr(aimPoint);
        if (distSq < 9.0D || distSq > 1024.0D) {
            blinkCooldown = 35;
            return;
        }
        // Compute a flanking blink 5..8 blocks closer with lateral juke to evade turret line of fire
        Vec3 toTarget = aimPoint.subtract(position());
        double len = Math.max(0.1D, toTarget.horizontalDistance());
        double nx = toTarget.x / len;
        double nz = toTarget.z / len;
        double side = random.nextBoolean() ? 1.0D : -1.0D;
        double step = 5.0D + random.nextDouble() * 3.0D;
        double tx = getX() + nx * step + (-nz * side) * 2.6D;
        double tz = getZ() + nz * step + (nx * side) * 2.6D;
        double ty = getY();

        BlockPos dest = BlockPos.containing(tx, ty, tz);
        if (!sl.getBlockState(dest).isAir() || !sl.getBlockState(dest.above()).isAir()
                || sl.getBlockState(dest.below()).isAir()) {
            blinkCooldown = 25;
            return;
        }

        // Departure & arrival purple rift blink bursts + 3D spatial afterimage ribbon
        double fromX = getX(), fromY = getY() + 0.95D, fromZ = getZ();
        triggerAction(ACTION_BLINK_SLASH, 16);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, fromX, fromY, fromZ,
                14, 0.28D, 0.55D, 0.28D, 0.08D);
        teleportTo(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D);
        double toX = getX(), toY = getY() + 0.95D, toZ = getZ();
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, toX, toY, toZ,
                16, 0.28D, 0.55D, 0.28D, 0.10D);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, toX, toY + 0.1D, toZ,
                6, 0.25D, 0.35D, 0.25D, 0.05D);
        PacketDistributor.sendToPlayersNear(sl, null, toX, toY, toZ, 56.0D,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_BLINK_TRAIL,
                        fromX, fromY, fromZ, toX, toY, toZ,
                        0xFFD84DFF, 12, 0.55F));
        sl.playSound(null, blockPosition(), ModSounds.RIFT_STALKER_BLINK.get(), SoundSource.HOSTILE, 0.80F, 0.96F + random.nextFloat() * 0.10F);

        int tier = getRiftTier();
        blinkCooldown = Math.max(36, 82 - tier * 4 + random.nextInt(16));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide && level() instanceof ServerLevel sl) {
            triggerAction(ACTION_BLINK_SLASH, 12);
            sl.playSound(null, blockPosition(), ModSounds.RIFT_STALKER_BLINK.get(),
                    SoundSource.HOSTILE, 0.72F, 1.12F);
        }
        return hit;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.RIFT_STALKER_BLINK.get();
    }
}

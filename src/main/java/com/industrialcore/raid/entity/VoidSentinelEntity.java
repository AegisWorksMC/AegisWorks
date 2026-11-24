package com.industrialcore.raid.entity;

import com.industrialcore.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 116: Original Rift Enemy #2 — {@code void_sentinel} (虚空砲撃機 / Void Sentinel).
 *
 * <p>Levitating polyhedral artillery unit encircled by dual counter-rotating energy gyro-rings.
 * Hovers 2 blocks above terrain at medium range (10..16m), charges up visibly, and fires rapid
 * purple plasma bolt volleys with zero block destruction.</p>
 */
public class VoidSentinelEntity extends AbstractRiftInvaderEntity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_PETAL_OPEN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    VoidSentinelEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_CRASH_STUN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    VoidSentinelEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    private int fireCooldown = 36;
    private int burstRemaining = 0;
    private int burstInterval = 0;

    public VoidSentinelEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.xpReward = 22;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PETAL_OPEN, false);
        builder.define(DATA_CRASH_STUN, 0);
    }

    public boolean isPetalOpen() {
        return this.entityData.get(DATA_PETAL_OPEN);
    }

    public void setPetalOpen(boolean open) {
        this.entityData.set(DATA_PETAL_OPEN, open);
    }

    public boolean isCrashStunned() {
        return this.entityData.get(DATA_CRASH_STUN) > 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 46.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.5D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 56.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 32.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected double baseHealth() {
        return 32.0D;
    }

    @Override
    protected double baseAttack() {
        return 6.0D;
    }

    @Override
    protected double baseArmor() {
        return 6.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.25D;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && amount > 0.0F && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            boolean isRanged = source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                    || source.getDirectEntity() != source.getEntity()
                    || (source.getEntity() instanceof LivingEntity le
                        && le.getMainHandItem().getItem() instanceof com.industrialcore.item.ElectricWeaponItem
                        && !(le.getMainHandItem().getItem() instanceof com.industrialcore.item.QuantumBladeItem));
            boolean openCore = isPetalOpen();
            boolean stunned = isCrashStunned();
            float mult = com.industrialcore.raid.RiftArenaLogic.evaluateSentinelDamageMultiplier(openCore, isRanged, stunned);
            amount *= mult;
            if (com.industrialcore.raid.RiftArenaLogic.shouldSentinelCrashStun(openCore, isRanged, stunned)
                    && level() instanceof ServerLevel sl) {
                this.entityData.set(DATA_CRASH_STUN, com.industrialcore.raid.RiftArenaLogic.SENTINEL_CRASH_STUN_TICKS);
                this.entityData.set(DATA_PETAL_OPEN, true);
                this.burstRemaining = 0;
                this.fireCooldown = 60;
                sl.playSound(null, blockPosition(), ModSounds.RIFT_STALKER_BLINK.get(), SoundSource.HOSTILE, 0.90F, 0.72F);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(
                        sl, null, getX(), getY() + 1.0D, getZ(), 64.0D,
                        new com.industrialcore.network.WeaponBeamFxPayload(
                                com.industrialcore.network.WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT,
                                getX(), getY() + 1.0D, getZ(), getX(), getY() + 1.1D, getZ(),
                                0xFFFFD866, 14, 1.85F, 1.0F, true));
                if (source.getEntity() instanceof Player p) {
                    p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.industrialcore.rift_weakness_sentinel_crit").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), true);
                }
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }
        int stun = this.entityData.get(DATA_CRASH_STUN);
        if (stun > 0) {
            this.entityData.set(DATA_CRASH_STUN, stun - 1);
            // Drop toward ground while crash-stunned!
            setDeltaMovement(0.0D, -0.22D, 0.0D);
            if (stun % 6 == 0) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.9D, getZ(), 4, 0.3D, 0.3D, 0.3D, 0.05D);
            }
            return;
        }
        // Open diamond petal armor during charge (fireCooldown <= 16) or active burst!
        boolean shouldOpen = burstRemaining > 0 || (getTarget() != null && fireCooldown <= 16);
        if (isPetalOpen() != shouldOpen) {
            this.entityData.set(DATA_PETAL_OPEN, shouldOpen);
        }
        // Smooth levitation height control (~2.0 blocks above floor)
        double targetY = findHoverTargetY(sl);
        double vy = Math.max(-0.14D, Math.min(0.16D, (targetY - getY()) * 0.14D));

        LivingEntity target = getTarget();
        Vec3 anchor = target != null
                ? target.position()
                : (reactorAnchor != null ? Vec3.atBottomCenterOf(reactorAnchor) : null);

        double vx = getDeltaMovement().x * 0.85D;
        double vz = getDeltaMovement().z * 0.85D;
        if (anchor != null) {
            Vec3 diff = anchor.subtract(position());
            double hDist = Math.max(0.01D, diff.horizontalDistance());
            double nx = diff.x / hDist;
            double nz = diff.z / hDist;
            // Maintain 10..15m standoff artillery orbit
            if (hDist > 15.0D) {
                vx = nx * 0.16D;
                vz = nz * 0.16D;
            } else if (hDist < 9.0D) {
                vx = -nx * 0.11D;
                vz = -nz * 0.11D;
            } else {
                // Slow lateral strafe orbit
                vx = -nz * 0.08D;
                vz = nx * 0.08D;
            }
            getLookControl().setLookAt(anchor.x, anchor.y + 1.0D, anchor.z);
        }
        setDeltaMovement(vx, vy, vz);

        // Plasma volley AI
        if (target != null && target.isAlive() && distanceToSqr(target) <= 32.0D * 32.0D) {
            if (burstRemaining > 0) {
                if (burstInterval > 0) {
                    burstInterval--;
                } else {
                    firePlasmaBolt(sl, target);
                    burstRemaining--;
                    burstInterval = 5;
                }
            } else if (fireCooldown > 0) {
                fireCooldown--;
                if (fireCooldown == 12) {
                    // Phase 119: Visible ring convergence & main lens charge animation
                    triggerAction(ACTION_CHARGE, 12);
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            getX(), getY() + 0.95D, getZ(), 8, 0.35D, 0.35D, 0.35D, 0.06D);
                }
            } else {
                int tier = getRiftTier();
                burstRemaining = tier >= 6 ? 4 : (tier >= 3 ? 3 : 2);
                burstInterval = 0;
                fireCooldown = Math.max(28, 56 - tier * 3);
            }
        }
    }

    private double findHoverTargetY(ServerLevel sl) {
        BlockPos base = blockPosition();
        for (int dy = 0; dy <= 8; dy++) {
            BlockPos check = base.below(dy);
            if (!sl.getBlockState(check).isAir()) {
                return check.getY() + 2.4D + Math.sin(tickCount * 0.09D) * 0.30D;
            }
        }
        return (reactorAnchor != null ? reactorAnchor.getY() + 2.5D : getY());
    }

    private void firePlasmaBolt(ServerLevel sl, LivingEntity target) {
        Vec3 origin = new Vec3(getX(), getY() + 0.95D, getZ());
        Vec3 aim = target.getBoundingBox().getCenter().subtract(origin).normalize();
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        double speed = 0.85D + getRiftTier() * 0.04D;
        RiftPlasmaBoltEntity bolt = RiftPlasmaBoltEntity.create(
                sl, this, origin.add(aim.scale(0.65D)), aim.scale(speed), dmg, getRiftTier() >= 7);
        sl.addFreshEntity(bolt);
        triggerAction(ACTION_FIRE_RECOIL, 8);
        sl.playSound(null, blockPosition(), ModSounds.VOID_SENTINEL_CANNON.get(),
                SoundSource.HOSTILE, 0.85F, 0.94F + random.nextFloat() * 0.10F);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }
}

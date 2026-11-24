package com.industrialcore.raid.entity;

import com.industrialcore.item.ElectricWeaponItem;
import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Phase 116: Original Rift Enemy #3 — {@code phase_juggernaut} (異相重装兵 / Phase Juggernaut).
 *
 * <p>Heavy obsidian-and-violet armored vanguard deploying a directional frontal phase shield
 * (reduces frontal normal damage by 60%; flanked attacks deal full damage and high-output
 * electric weapons partially pierce it) plus non-destructive kinetic shockwave slams.</p>
 */
public class PhaseJuggernautEntity extends AbstractRiftInvaderEntity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_SHIELD_BROKEN_TICKS =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    PhaseJuggernautEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    private int slamCooldown = 65;

    public PhaseJuggernautEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 35;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHIELD_BROKEN_TICKS, 0);
    }

    public boolean isShieldBroken() {
        return this.entityData.get(DATA_SHIELD_BROKEN_TICKS) > 0;
    }

    public void setShieldBrokenTicks(int ticks) {
        this.entityData.set(DATA_SHIELD_BROKEN_TICKS, Math.max(0, ticks));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 128.0D)
                .add(Attributes.ATTACK_DAMAGE, 13.0D)
                .add(Attributes.ARMOR, 16.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.92D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.05D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected double baseHealth() {
        return 84.0D;
    }

    @Override
    protected double baseAttack() {
        return 10.0D;
    }

    @Override
    protected double baseArmor() {
        return 12.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.22D;
    }

    /** Pure-Java frontal phase shield arc classifier (front ±72 deg = dot &gt; 0.30). */
    public static com.industrialcore.raid.DimensionalRaidLogic.ShieldHitOutcome evaluateShieldHit(double facingDot, boolean piercing) {
        return com.industrialcore.raid.DimensionalRaidLogic.evaluateShieldHit(facingDot, piercing);
    }

    /**
     * Phase 120 Tactical Weakness: Twin Forearm Tower Shields cut ALL frontal ranged damage by 95% (0.05x).
     * Striking with {@code quantum_blade} triggers SHIELD BREAK (3.5s stun, shields blown open, 2.2x weakness damage!).
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && amount > 0.0F && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Vec3 srcPos = source.getSourcePosition();
            Entity attacker = source.getEntity();
            if (srcPos == null && attacker != null) {
                srcPos = attacker.position();
            }
            boolean isQuantumBlade = attacker instanceof LivingEntity le
                    && le.getMainHandItem().getItem() instanceof com.industrialcore.item.QuantumBladeItem;
            boolean isRanged = source.is(DamageTypeTags.IS_PROJECTILE)
                    || source.getDirectEntity() != attacker
                    || (attacker instanceof LivingEntity le2
                        && le2.getMainHandItem().getItem() instanceof ElectricWeaponItem
                        && !isQuantumBlade);
            double dot = 1.0D;
            Vec3 facing = Vec3.directionFromRotation(0.0F, this.getYRot()).normalize();
            Vec3 toSource = facing;
            if (srcPos != null) {
                toSource = srcPos.subtract(position());
                double hLen = toSource.horizontalDistance();
                if (hLen > 0.1D) {
                    dot = (toSource.x / hLen) * facing.x + (toSource.z / hLen) * facing.z;
                }
            }

            var res = com.industrialcore.raid.RiftArenaLogic.evaluateJuggernautHit(
                    isShieldBroken(), isQuantumBlade, isRanged, dot);
            amount *= res.damageMultiplier();

            if (level() instanceof ServerLevel sl) {
                Vec3 shieldPos = position().add(facing.scale(0.92D)).add(0.0D, 1.25D, 0.0D);
                if (res.triggersBreak()) {
                    setShieldBrokenTicks(com.industrialcore.raid.RiftArenaLogic.JUGGERNAUT_BREAK_STUN_TICKS);
                    sl.playSound(null, blockPosition(), ModSounds.SOVEREIGN_DEFEAT_COLLAPSE.get(),
                            SoundSource.HOSTILE, 0.90F, 1.50F);
                    sl.playSound(null, blockPosition(), ModSounds.RIFT_SHIELD_DEFLECT.get(),
                            SoundSource.HOSTILE, 0.95F, 0.72F);
                    PacketDistributor.sendToPlayersNear(sl, null, shieldPos.x, shieldPos.y, shieldPos.z, 64.0D,
                            new WeaponBeamFxPayload(
                                    WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT,
                                    shieldPos.x, shieldPos.y, shieldPos.z,
                                    shieldPos.x + facing.x, shieldPos.y, shieldPos.z + facing.z,
                                    0xFFFFD866, 16, 2.20F, 1.0F, true));
                    if (attacker instanceof Player p) {
                        p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "message.industrialcore.rift_weakness_shield_break")
                                .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), true);
                    }
                } else if (res == com.industrialcore.raid.RiftArenaLogic.JuggernautHitResult.RANGED_BLOCKED_95
                        || res == com.industrialcore.raid.RiftArenaLogic.JuggernautHitResult.FRONT_MELEE_GUARDED) {
                    triggerAction(ACTION_SHIELD_FLASH, 12);
                    sl.playSound(null, blockPosition(), ModSounds.RIFT_SHIELD_DEFLECT.get(),
                            SoundSource.HOSTILE, 0.85F, 0.96F + random.nextFloat() * 0.10F);
                    PacketDistributor.sendToPlayersNear(sl, null, shieldPos.x, shieldPos.y, shieldPos.z, 56.0D,
                            new WeaponBeamFxPayload(
                                    WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT,
                                    shieldPos.x, shieldPos.y, shieldPos.z,
                                    shieldPos.x + facing.x, shieldPos.y, shieldPos.z + facing.z,
                                    0xFFC852FF, 11, 1.45F, 0.0F, true));
                } else {
                    Vec3 hitPos = position().add(0.0D, 1.25D, 0.0D);
                    Vec3 normal = toSource.lengthSqr() > 1e-4D ? toSource.normalize() : facing;
                    PacketDistributor.sendToPlayersNear(sl, null, hitPos.x, hitPos.y, hitPos.z, 56.0D,
                            new WeaponBeamFxPayload(
                                    WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT,
                                    hitPos.x, hitPos.y, hitPos.z,
                                    hitPos.x + normal.x, hitPos.y, hitPos.z + normal.z,
                                    0xFFFFD866, 10, 1.30F, 1.0F, true));
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
        int broken = this.entityData.get(DATA_SHIELD_BROKEN_TICKS);
        if (broken > 0) {
            this.entityData.set(DATA_SHIELD_BROKEN_TICKS, broken - 1);
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            if (broken % 6 == 0) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.4D, getZ(), 5, 0.4D, 0.4D, 0.4D, 0.05D);
            }
            return; // Stunned while shield-broken!
        }
        if (slamCooldown > 0) {
            slamCooldown--;
        } else {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive() && distanceToSqr(target) <= 30.25D) {
                performShockwaveSlam(sl);
                slamCooldown = Math.max(48, 86 - getRiftTier() * 3);
            }
        }
    }

    private void performShockwaveSlam(ServerLevel sl) {
        double cx = getX();
        double cy = getY() + 0.3D;
        double cz = getZ();
        for (int i = 0; i < 18; i++) {
            double ang = i * (Math.PI * 2.0D / 18.0D);
            sl.sendParticles(ParticleTypes.WITCH,
                    cx + Math.cos(ang) * 2.2D, cy + 0.2D, cz + Math.sin(ang) * 2.2D,
                    2, 0.1D, 0.1D, 0.1D, 0.02D);
        }
        triggerAction(ACTION_SLAM, 14);
        sl.playSound(null, blockPosition(), ModSounds.PHASE_JUGGERNAUT_SLAM.get(),
                SoundSource.HOSTILE, 0.95F, 0.94F + random.nextFloat() * 0.08F);
        PacketDistributor.sendToPlayersNear(sl, null, cx, cy, cz, 56.0D,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_RIFT_PORTAL_VORTEX,
                        cx, cy, cz, cx, cy + 1.2D, cz,
                        0xFFB84DFF, 16, 4.2F, 0.35F, false));

        float slamDmg = (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.85D);
        for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(blockPosition()).inflate(5.5D))) {
            if (!p.isSpectator() && !p.isCreative()) {
                p.hurt(sl.damageSources().mobAttack(this), slamDmg);
                Vec3 push = p.position().subtract(position()).normalize().scale(1.15D);
                p.push(push.x, 0.45D, push.z);
                p.hurtMarked = true;
            }
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.NETHERITE_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }
}

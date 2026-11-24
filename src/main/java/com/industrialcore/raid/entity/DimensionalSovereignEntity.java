package com.industrialcore.raid.entity;

import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.raid.DimensionalRaidLogic;
import com.industrialcore.registry.ModEntities;
import com.industrialcore.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Phase 116: Original Rift Boss #4 — {@code dimensional_sovereign} (次元支配者 / Dimensional Sovereign).
 *
 * <p>Towering multi-ring singularity entity descending in Wave 3 with 4 signature boss mechanics:
 * omnidirectional plasma ring barrage, gravity-well player pull pulse, {@code rift_stalker} escort
 * summoning, and Overdrive Phase 2 (crimson-magenta rage at HP &le; 50% with +35% speed and +40%
 * attack cadence).</p>
 */
public class DimensionalSovereignEntity extends AbstractRiftInvaderEntity {
    private static final EntityDataAccessor<Boolean> DATA_OVERDRIVE =
            SynchedEntityData.defineId(DimensionalSovereignEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BARRIER_ACTIVE =
            SynchedEntityData.defineId(DimensionalSovereignEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_STUN_TICKS =
            SynchedEntityData.defineId(DimensionalSovereignEntity.class, EntityDataSerializers.INT);

    private int barrageCooldown = 45;
    private int gravityCooldown = 95;
    private int summonCooldown = 140;
    private int completedBarrierStages = 0;
    private final java.util.Set<java.util.UUID> activeAnchorIds = new java.util.HashSet<>();

    public DimensionalSovereignEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.addTag(DimensionalRaidLogic.BOSS_TAG);
        this.xpReward = 150;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 360.0D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
                .add(Attributes.ARMOR, 18.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 72.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OVERDRIVE, false);
        builder.define(DATA_BARRIER_ACTIVE, false);
        builder.define(DATA_STUN_TICKS, 0);
    }

    public boolean isOverdrivePhase() {
        return this.entityData.get(DATA_OVERDRIVE);
    }

    public boolean isAbsoluteBarrierActive() {
        return this.entityData.get(DATA_BARRIER_ACTIVE);
    }

    public boolean isCoreExposedStunned() {
        return this.entityData.get(DATA_STUN_TICKS) > 0;
    }

    /** Pure-Java Overdrive Phase 2 threshold predicate (triggers at HP &lt;= 50%). */
    public static boolean shouldEnterOverdrive(float currentHp, float maxHp, boolean alreadyOverdrive) {
        return DimensionalRaidLogic.shouldEnterOverdrive(currentHp, maxHp, alreadyOverdrive);
    }

    /** Allows controlled overdrive state transitions (used by combat AI &amp; tests). */
    public void setOverdrivePhase(boolean overdrive) {
        this.entityData.set(DATA_OVERDRIVE, overdrive);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 48.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected double baseHealth() {
        return 220.0D;
    }

    @Override
    protected double baseAttack() {
        return 12.0D;
    }

    @Override
    protected double baseArmor() {
        return 14.0D;
    }

    @Override
    protected double baseSpeed() {
        return 0.24D;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && amount > 0.0F && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float mult = com.industrialcore.raid.RiftArenaLogic.evaluateBossDamageMultiplier(
                    isAbsoluteBarrierActive(), isCoreExposedStunned());
            amount *= mult;
            if (isAbsoluteBarrierActive() && level() instanceof ServerLevel sl) {
                sl.playSound(null, blockPosition(), ModSounds.RIFT_SHIELD_DEFLECT.get(), SoundSource.HOSTILE, 0.85F, 0.85F);
                PacketDistributor.sendToPlayersNear(sl, null, getX(), getY() + 1.8D, getZ(), 72.0D,
                        new WeaponBeamFxPayload(
                                WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT,
                                getX(), getY() + 1.8D, getZ(), getX(), getY() + 2.0D, getZ(),
                                0xFFC852FF, 12, 2.4F, 0.0F, true));
            }
        }
        return super.hurt(source, amount);
    }

    /** Called by {@link RiftAnchorCrystalEntity} when destroyed by ranged defenders. */
    public void onAnchorCrystalDestroyed(ServerLevel sl, java.util.UUID crystalId) {
        activeAnchorIds.remove(crystalId);
        activeAnchorIds.removeIf(id -> {
            var e = sl.getEntity(id);
            return e == null || !e.isAlive();
        });
        if (isAbsoluteBarrierActive() && activeAnchorIds.isEmpty()) {
            this.entityData.set(DATA_BARRIER_ACTIVE, false);
            this.entityData.set(DATA_STUN_TICKS, com.industrialcore.raid.RiftArenaLogic.BOSS_EXPOSED_STUN_TICKS);
            triggerAction(ACTION_SKILL_FLARE, 28);
            sl.playSound(null, blockPosition(), ModSounds.SOVEREIGN_ROAR_OVERDRIVE.get(), SoundSource.HOSTILE, 1.0F, 1.25F);
            PacketDistributor.sendToPlayersNear(sl, null, getX(), getY() + 1.8D, getZ(), 96.0D,
                    new WeaponBeamFxPayload(
                            WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT,
                            getX(), getY() + 1.8D, getZ(), getX(), getY() + 2.0D, getZ(),
                            0xFFFFD866, 22, 4.5F, 1.0F, true));
            for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(blockPosition()).inflate(64.0D))) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.industrialcore.rift_boss_barrier_broken")
                        .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD), true);
            }
        }
    }

    private void activateAbsoluteBarrier(ServerLevel sl, int stage) {
        completedBarrierStages = stage;
        this.entityData.set(DATA_BARRIER_ACTIVE, true);
        triggerAction(ACTION_SKILL_FLARE, 30);
        sl.playSound(null, blockPosition(), ModSounds.RIFT_PORTAL_HORN.get(), SoundSource.HOSTILE, 1.0F, 0.85F);
        activeAnchorIds.clear();
        BlockPos center = reactorAnchor != null ? reactorAnchor : blockPosition();
        var pillars = com.industrialcore.raid.RiftArenaLogic.SNIPER_PILLARS;
        for (int i = 0; i < com.industrialcore.raid.RiftArenaLogic.BOSS_ANCHOR_CRYSTAL_COUNT; i++) {
            var pSpec = pillars.get((i + stage) % pillars.size());
            RiftAnchorCrystalEntity crystal = ModEntities.RIFT_ANCHOR_CRYSTAL.get().create(sl);
            if (crystal == null) continue;
            double cx = center.getX() + pSpec.dx() + 0.5D;
            double cy = center.getY() + pSpec.height() + 2.0D;
            double cz = center.getZ() + pSpec.dz() + 0.5D;
            crystal.moveTo(cx, cy, cz, 0.0F, 0.0F);
            crystal.bindToBoss(this, getRiftTier());
            sl.addFreshEntity(crystal);
            activeAnchorIds.add(crystal.getUUID());
            PacketDistributor.sendToPlayersNear(sl, null, cx, cy, cz, 96.0D,
                    new WeaponBeamFxPayload(
                            WeaponBeamFxPayload.KIND_RIFT_PORTAL_VORTEX,
                            cx, cy - 1.5D, cz, cx, cy + 5.5D, cz,
                            0xFFD84DFF, 30, 2.5F, 1.0F, true));
        }
        for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(blockPosition()).inflate(64.0D))) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.industrialcore.rift_boss_barrier_up", activeAnchorIds.size())
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD), true);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !(level() instanceof ServerLevel sl)) {
            return;
        }

        // Phase 120: Drop & expose core while stunned after Anchor Crystals shatter!
        int stun = this.entityData.get(DATA_STUN_TICKS);
        if (stun > 0) {
            this.entityData.set(DATA_STUN_TICKS, stun - 1);
            double floorY = (reactorAnchor != null ? reactorAnchor.getY() + 1.0D : getY() - 0.15D);
            double vy = Math.max(-0.22D, Math.min(0.10D, (floorY - getY()) * 0.18D));
            setDeltaMovement(0.0D, vy, 0.0D);
            if (stun % 5 == 0) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.6D, getZ(), 6, 0.8D, 0.8D, 0.8D, 0.06D);
            }
            return;
        }

        // Check Absolute Phase Barrier stages (HP <= 60% & HP <= 25%) -> spawn 3 Anchor Crystals!
        int nextBarrier = com.industrialcore.raid.RiftArenaLogic.nextBossBarrierStage(
                getHealth(), getMaxHealth(), completedBarrierStages, isAbsoluteBarrierActive());
        if (nextBarrier > 0) {
            activateAbsoluteBarrier(sl, nextBarrier);
        }
        // Self-heal check in case all anchors were discarded externally
        if (isAbsoluteBarrierActive() && tickCount % 20 == 0) {
            activeAnchorIds.removeIf(id -> {
                var e = sl.getEntity(id);
                return e == null || !e.isAlive();
            });
            if (activeAnchorIds.isEmpty()) {
                onAnchorCrystalDestroyed(sl, java.util.UUID.randomUUID());
            }
        }

        // Check Overdrive Phase 2 trigger (HP <= 50%)
        if (shouldEnterOverdrive(getHealth(), getMaxHealth(), isOverdrivePhase())) {
            enterOverdrivePhase(sl);
        }

        boolean od = isOverdrivePhase();
        int tier = getRiftTier();

        // Hover smoothly ~2.6 blocks above ground, stalking toward reactor/player
        double targetY = findBossHoverY(sl);
        double vy = Math.max(-0.14D, Math.min(0.16D, (targetY - getY()) * 0.12D));

        LivingEntity target = getTarget();
        Vec3 anchor = target != null
                ? target.position()
                : (reactorAnchor != null ? Vec3.atBottomCenterOf(reactorAnchor) : null);

        double vx = getDeltaMovement().x * 0.86D;
        double vz = getDeltaMovement().z * 0.86D;
        if (anchor != null) {
            Vec3 diff = anchor.subtract(position());
            double hDist = Math.max(0.01D, diff.horizontalDistance());
            double nx = diff.x / hDist;
            double nz = diff.z / hDist;
            double moveSpd = (od ? 0.19D : 0.13D) * DimensionalRaidLogic.tierSpeedMultiplier(tier);
            if (hDist > 12.0D) {
                vx = nx * moveSpd;
                vz = nz * moveSpd;
            } else {
                vx = -nz * (moveSpd * 0.72D);
                vz = nx * (moveSpd * 0.72D);
            }
            getLookControl().setLookAt(anchor.x, anchor.y + 1.2D, anchor.z);
        }
        setDeltaMovement(vx, vy, vz);

        // Ambient boss aura particles
        if (tickCount % 6 == 0) {
            sl.sendParticles(od ? ParticleTypes.DRAGON_BREATH : ParticleTypes.REVERSE_PORTAL,
                    getX(), getY() + 1.6D, getZ(), od ? 10 : 6, 0.9D, 0.9D, 0.9D, 0.04D);
        }

        // 1. Omnidirectional Plasma Ring Barrage
        if (barrageCooldown > 0) {
            barrageCooldown--;
        } else {
            fireOmnidirectionalBarrage(sl, tier, od);
            int baseCd = Math.max(36, 72 - tier * 3);
            barrageCooldown = od ? Math.max(24, (int) (baseCd * 0.62F)) : baseCd;
        }

        // 2. Gravity Well Pull Pulse
        if (gravityCooldown > 0) {
            gravityCooldown--;
        } else {
            triggerGravityWell(sl, od);
            gravityCooldown = od ? 75 : 115;
        }

        // 3. Summon Rift Stalker Escorts (capped to max 4 active nearby)
        if (summonCooldown > 0) {
            summonCooldown--;
        } else {
            summonStalkerEscorts(sl, tier, od);
            summonCooldown = od ? 140 : 200;
        }
    }

    private void enterOverdrivePhase(ServerLevel sl) {
        this.entityData.set(DATA_OVERDRIVE, true);
        triggerAction(ACTION_SKILL_FLARE, 30);
        sl.playSound(null, blockPosition(), ModSounds.SOVEREIGN_ROAR_OVERDRIVE.get(), SoundSource.HOSTILE, 1.0F, 0.96F);
        PacketDistributor.sendToPlayersNear(sl, null, getX(), getY() + 1.6D, getZ(), 80.0D,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_RIFT_PORTAL_VORTEX,
                        getX(), getY() + 0.4D, getZ(), getX(), getY() + 7.5D, getZ(),
                        0xFFFF3898, 28, 4.8F, 1.0F, true));
        for (int i = 0; i < 28; i++) {
            double ang = i * (Math.PI * 2.0D / 28.0D);
            sl.sendParticles(ParticleTypes.DRAGON_BREATH,
                    getX(), getY() + 1.6D, getZ(), 0, Math.cos(ang) * 0.35D, 0.08D, Math.sin(ang) * 0.35D, 1.0D);
        }
    }

    private void fireOmnidirectionalBarrage(ServerLevel sl, int tier, boolean od) {
        triggerAction(ACTION_SKILL_FLARE, 22);
        int count = Math.min(16, 8 + (tier >= 4 ? 2 : 0) + (tier >= 7 ? 2 : 0) + (od ? 4 : 0));
        float dmg = (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * (od ? 1.15D : 0.90D));
        double speed = 0.72D + tier * 0.035D + (od ? 0.14D : 0.0D);
        Vec3 center = new Vec3(getX(), getY() + 1.65D, getZ());
        double phaseOffset = (tickCount * 0.13D) % (Math.PI * 2.0D);
        for (int i = 0; i < count; i++) {
            double ang = phaseOffset + i * (Math.PI * 2.0D / count);
            Vec3 dir = new Vec3(Math.cos(ang), -0.08D, Math.sin(ang)).normalize();
            RiftPlasmaBoltEntity bolt = RiftPlasmaBoltEntity.create(
                    sl, this, center.add(dir.scale(1.1D)), dir.scale(speed), dmg, od);
            sl.addFreshEntity(bolt);
        }
        sl.playSound(null, blockPosition(), ModSounds.VOID_SENTINEL_CANNON.get(),
                SoundSource.HOSTILE, 0.95F, od ? 0.68F : 0.78F);
    }

    private void triggerGravityWell(ServerLevel sl, boolean od) {
        triggerAction(ACTION_SKILL_FLARE, 22);
        Vec3 center = new Vec3(getX(), getY() + 1.5D, getZ());
        sl.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 36, 2.5D, 1.2D, 2.5D, 0.25D);
        sl.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 10, 1.6D, 0.9D, 1.6D, 0.08D);
        sl.playSound(null, blockPosition(), ModSounds.RIFT_PORTAL_HORN.get(), SoundSource.HOSTILE, 0.80F, 1.15F);

        double pullStrength = od ? 0.62D : 0.44D;
        for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(blockPosition()).inflate(18.0D))) {
            if (!p.isSpectator() && !p.isCreative()) {
                Vec3 pull = center.subtract(p.position());
                double d = Math.max(1.5D, pull.length());
                Vec3 v = pull.scale(pullStrength / d);
                p.push(v.x, Math.min(0.28D, v.y + 0.12D), v.z);
                p.hurtMarked = true;
            }
        }
    }

    private void summonStalkerEscorts(ServerLevel sl, int tier, boolean od) {
        int existing = sl.getEntitiesOfClass(RiftStalkerEntity.class,
                new AABB(blockPosition()).inflate(28.0D)).size();
        if (existing >= 4) {
            return;
        }
        triggerAction(ACTION_SKILL_FLARE, 22);
        int spawnCount = Math.min(od || tier >= 6 ? 2 : 1, 4 - existing);
        for (int i = 0; i < spawnCount; i++) {
            RiftStalkerEntity escort = ModEntities.RIFT_STALKER.get().create(sl);
            if (escort == null) {
                continue;
            }
            double ang = random.nextDouble() * Math.PI * 2.0D;
            double sx = getX() + Math.cos(ang) * 3.5D;
            double sz = getZ() + Math.sin(ang) * 3.5D;
            double sy = findEscortFloorY(sl, (int) Math.floor(sx), (int) Math.floor(sz));
            escort.moveTo(sx, sy, sz, random.nextFloat() * 360.0F, 0.0F);
            escort.configureForTier(tier, 0.85D, reactorAnchor);
            sl.addFreshEntity(escort);
            PacketDistributor.sendToPlayersNear(sl, null, sx, sy, sz, 64.0D,
                    new WeaponBeamFxPayload(
                            WeaponBeamFxPayload.KIND_RIFT_PORTAL_VORTEX,
                            sx, sy + 0.1D, sz, sx, sy + 5.0D, sz,
                            0xFFB84DFF, 22, 2.0F, 0.5F, false));
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, sx, sy + 1.0D, sz, 14, 0.3D, 0.6D, 0.3D, 0.08D);
        }
    }

    /** Phase 119: Colossal dual purple/cyan 3D shockwave rings &amp; collapsing energy pillar on Boss defeat! */
    @Override
    protected void spawnDeathCatharsisFx(ServerLevel sl) {
        double cx = getX();
        double cy = getY() + 1.65D;
        double cz = getZ();
        sl.playSound(null, blockPosition(), ModSounds.SOVEREIGN_DEFEAT_COLLAPSE.get(),
                SoundSource.HOSTILE, 1.0F, 0.96F);
        PacketDistributor.sendToPlayersNear(sl, null, cx, cy, cz, 96.0D,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_BOSS_COLLAPSE_SHOCKWAVE,
                        cx, cy, cz, cx, cy + 12.0D, cz,
                        0xFFD84DFF, 38, 13.5F, 1.0F, true));
        for (int i = 0; i < 36; i++) {
            double ang = i * (Math.PI * 2.0D / 36.0D);
            sl.sendParticles(i % 2 == 0 ? ParticleTypes.DRAGON_BREATH : ParticleTypes.END_ROD,
                    cx, cy, cz, 0, Math.cos(ang) * 0.45D, 0.12D + (i % 3) * 0.08D, Math.sin(ang) * 0.45D, 1.0D);
        }
    }

    private double findBossHoverY(ServerLevel sl) {
        BlockPos base = blockPosition();
        for (int dy = 0; dy <= 10; dy++) {
            BlockPos check = base.below(dy);
            if (!sl.getBlockState(check).isAir()) {
                return check.getY() + 2.6D + Math.sin(tickCount * 0.07D) * 0.35D;
            }
        }
        return (reactorAnchor != null ? reactorAnchor.getY() + 2.6D : getY());
    }

    private double findEscortFloorY(ServerLevel sl, int x, int z) {
        int baseY = reactorAnchor != null ? reactorAnchor.getY() : blockPosition().getY();
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos f = new BlockPos(x, baseY + dy - 1, z);
            if (!sl.getBlockState(f).isAir() && sl.getBlockState(f.above()).isAir()) {
                return f.getY() + 1.0D;
            }
        }
        return baseY;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SOVEREIGN_DEFEAT_COLLAPSE.get();
    }
}

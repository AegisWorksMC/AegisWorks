package com.industrialcore.raid.entity;

import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.raid.DimensionalRaidLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Phase 116 / 119: Shared base class for all 4 original AegisWorks Dimensional Rift Raid enemies.
 *
 * <p>Guarantees zero daylight burning, zero block griefing, clean Tier 1..10 attribute scaling,
 * synced action animation state for procedural 3D renderers, crystal-shatter death catharsis FX,
 * and automatic tagging with {@link DimensionalRaidLogic#INVADER_TAG}.</p>
 */
public abstract class AbstractRiftInvaderEntity extends Monster {
    public static final int ACTION_NONE = 0;
    public static final int ACTION_BLINK_SLASH = 1;
    public static final int ACTION_CHARGE = 2;
    public static final int ACTION_FIRE_RECOIL = 3;
    public static final int ACTION_SHIELD_FLASH = 4;
    public static final int ACTION_SLAM = 5;
    public static final int ACTION_SKILL_FLARE = 6;

    private static final EntityDataAccessor<Integer> DATA_RIFT_TIER =
            SynchedEntityData.defineId(AbstractRiftInvaderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_KIND =
            SynchedEntityData.defineId(AbstractRiftInvaderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_TICKS =
            SynchedEntityData.defineId(AbstractRiftInvaderEntity.class, EntityDataSerializers.INT);

    @Nullable
    protected BlockPos reactorAnchor;

    protected AbstractRiftInvaderEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 15;
        this.addTag(DimensionalRaidLogic.INVADER_TAG);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RIFT_TIER, 1);
        builder.define(DATA_ACTION_KIND, ACTION_NONE);
        builder.define(DATA_ACTION_TICKS, 0);
    }

    public void triggerAction(int actionKind, int durationTicks) {
        this.entityData.set(DATA_ACTION_KIND, actionKind);
        this.entityData.set(DATA_ACTION_TICKS, Math.max(0, durationTicks));
    }

    public int getActionKind() {
        return this.entityData.get(DATA_ACTION_KIND);
    }

    public int getActionTicks() {
        return this.entityData.get(DATA_ACTION_TICKS);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            int ticks = this.entityData.get(DATA_ACTION_TICKS);
            if (ticks > 0) {
                int next = ticks - 1;
                this.entityData.set(DATA_ACTION_TICKS, next);
                if (next == 0) {
                    this.entityData.set(DATA_ACTION_KIND, ACTION_NONE);
                }
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            spawnDeathCatharsisFx(sl);
        }
    }

    /** Phase 119: Standard Rift enemy death plasma pop &amp; crystal shard scatter (overridden by Boss). */
    protected void spawnDeathCatharsisFx(ServerLevel sl) {
        double cx = getX();
        double cy = getY() + getBbHeight() * 0.55D;
        double cz = getZ();
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, cy, cz, 14, 0.35D, 0.45D, 0.35D, 0.14D);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 10, 0.30D, 0.35D, 0.30D, 0.10D);
        PacketDistributor.sendToPlayersNear(sl, null, cx, cy, cz, 56.0D,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT,
                        cx, cy, cz, cx, cy + 0.1D, cz,
                        0xFFD45CFF, 12, 1.35F, 0.85F, true));
    }

    public int getRiftTier() {
        return Math.max(1, Math.min(DimensionalRaidLogic.MAX_TIER, this.entityData.get(DATA_RIFT_TIER)));
    }

    @Nullable
    public BlockPos getReactorAnchor() {
        return reactorAnchor;
    }

    /** Configures health, armor, attack damage, and movement speed for the active Rift Tier. */
    public void configureForTier(int tier, double configDiffMult, @Nullable BlockPos reactorPos) {
        int safeTier = DimensionalRaidLogic.clampTier(tier);
        this.entityData.set(DATA_RIFT_TIER, safeTier);
        this.reactorAnchor = reactorPos != null ? reactorPos.immutable() : null;

        double hpMult = DimensionalRaidLogic.tierHealthMultiplier(safeTier) * configDiffMult;
        double atkMult = DimensionalRaidLogic.tierAttackMultiplier(safeTier) * configDiffMult;
        double spdMult = DimensionalRaidLogic.tierSpeedMultiplier(safeTier);

        var hpAttr = getAttribute(Attributes.MAX_HEALTH);
        if (hpAttr != null) {
            double scaledHp = DimensionalRaidLogic.scaledHealth(baseHealth(), hpMult);
            hpAttr.setBaseValue(scaledHp);
            setHealth((float) scaledHp);
        }
        var atkAttr = getAttribute(Attributes.ATTACK_DAMAGE);
        if (atkAttr != null) {
            atkAttr.setBaseValue(DimensionalRaidLogic.scaledAttack(baseAttack(), atkMult));
        }
        var armorAttr = getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(Math.min(24.0D, baseArmor() + (safeTier - 1) * 1.1D));
        }
        var spdAttr = getAttribute(Attributes.MOVEMENT_SPEED);
        if (spdAttr != null) {
            spdAttr.setBaseValue(baseSpeed() * spdMult);
        }
    }

    protected abstract double baseHealth();

    protected abstract double baseAttack();

    protected abstract double baseArmor();

    protected abstract double baseSpeed();

    @Override
    public boolean isSensitiveToWater() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RiftTier", getRiftTier());
        if (reactorAnchor != null) {
            tag.putLong("ReactorAnchor", reactorAnchor.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_RIFT_TIER, DimensionalRaidLogic.clampTier(tag.getInt("RiftTier")));
        if (tag.contains("ReactorAnchor")) {
            reactorAnchor = BlockPos.of(tag.getLong("ReactorAnchor"));
        }
    }
}

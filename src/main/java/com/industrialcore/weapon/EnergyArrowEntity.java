package com.industrialcore.weapon;

import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.registry.ModEntities;
import com.industrialcore.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Phase 101: High-velocity glowing light arrow fired by {@code energy_bow}.
 *
 * <p>Features low gravity drop, zero pickup clutter, optional homing toward a locked target
 * ({@code att_multi_lock}), and optional 2.5-second gravity singularity deployment on impact
 * ({@code att_gravity_arrow}).</p>
 */
public class EnergyArrowEntity extends AbstractArrow {
    private static final EntityDataAccessor<Boolean> DATA_GRAVITY =
            SynchedEntityData.defineId(EnergyArrowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOMING =
            SynchedEntityData.defineId(EnergyArrowEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String TAG_TARGET_UUID = "TargetUuid";
    private static final String TAG_GRAVITY = "GravityField";
    private static final String TAG_HOMING = "Homing";
    private static final String TAG_LIFE = "LifeTicks";
    private static final String TAG_ORIGIN_X = "OriginX";
    private static final String TAG_ORIGIN_Y = "OriginY";
    private static final String TAG_ORIGIN_Z = "OriginZ";
    private static final String TAG_HAS_ORIGIN = "HasOrigin";
    private static final String TAG_CONFIG_DMG = "ConfigDmg";

    @Nullable
    private UUID targetUuid;
    private boolean gravityField;
    private boolean homing;
    private boolean fieldSpawned;
    private int lifeTicks;
    private double originX;
    private double originY;
    private double originZ;
    private boolean hasOrigin;
    private double configuredBaseDamage = VirtualWeaponLogic.BOW_ARROW_BASE_DAMAGE;

    public EnergyArrowEntity(EntityType<? extends EnergyArrowEntity> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
    }

    public EnergyArrowEntity(Level level, LivingEntity shooter, ItemStack weaponStack) {
        super(ModEntities.ENERGY_ARROW.get(), shooter, level, new ItemStack(Items.ARROW), weaponStack);
        this.pickup = Pickup.DISALLOWED;
        this.setNoGravity(true);
        Vec3 eye = shooter.getEyePosition();
        this.originX = eye.x;
        this.originY = eye.y;
        this.originZ = eye.z;
        this.hasOrigin = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GRAVITY, false);
        builder.define(DATA_HOMING, false);
    }

    public void configure(@Nullable UUID homingTarget, boolean deployGravityField, double baseDamage) {
        this.targetUuid = homingTarget;
        this.homing = homingTarget != null;
        this.gravityField = deployGravityField;
        this.configuredBaseDamage = Math.max(1.0D, baseDamage);
        this.setBaseDamage(this.configuredBaseDamage);
        this.entityData.set(DATA_HOMING, this.homing);
        this.entityData.set(DATA_GRAVITY, this.gravityField);
        if (!this.hasOrigin) {
            this.originX = this.getX();
            this.originY = this.getY();
            this.originZ = this.getZ();
            this.hasOrigin = true;
        }
    }

    public boolean isGravityArrow() {
        return this.entityData.get(DATA_GRAVITY);
    }

    public boolean isHomingArrow() {
        return this.entityData.get(DATA_HOMING);
    }

    @Override
    public void tick() {
        super.tick();
        lifeTicks++;

        // Despawn quickly after hitting ground or flying too long
        if (!this.level().isClientSide) {
            if (this.inGround && this.inGroundTime >= 8) {
                this.discard();
                return;
            }
            if (lifeTicks > 120) {
                this.discard();
                return;
            }
        }

        if (!this.inGround) {
            // Very gentle drop (20% of normal arrow gravity) for laser-flat feel
            Vec3 vel = this.getDeltaMovement();
            if (!this.homing) {
                this.setDeltaMovement(vel.x, vel.y - 0.008D, vel.z);
            } else if (this.level() instanceof ServerLevel sl && targetUuid != null) {
                Entity target = sl.getEntity(targetUuid);
                if (target instanceof LivingEntity le && le.isAlive()) {
                    Vec3 targetCenter = le.position().add(0.0D, le.getBbHeight() * 0.55D, 0.0D);
                    Vec3 desired = targetCenter.subtract(this.position()).normalize();
                    double speed = Math.max(2.6D, vel.length());
                    Vec3 steered = vel.normalize().scale(0.55D).add(desired.scale(0.45D)).normalize().scale(speed);
                    this.setDeltaMovement(steered);
                    this.hasImpulse = true;
                }
            }

            if (this.level().isClientSide && (lifeTicks & 1) == 0) {
                this.level().addParticle(
                        isGravityArrow() ? ParticleTypes.DRAGON_BREATH : ParticleTypes.END_ROD,
                        this.getX(), this.getY(), this.getZ(),
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel sl) {
            Vec3 impact = result.getLocation();
            double dist = this.hasOrigin
                    ? impact.distanceTo(new Vec3(this.originX, this.originY, this.originZ))
                    : 0.0D;
            float distMult = VirtualWeaponLogic.bowDistanceDamageMultiplier(dist);
            double curSpeed = Math.max(0.5D, this.getDeltaMovement().length());
            // Normalize out speed drag so sniper damage scales purely & reliably by distance (1.0x -> 2.8x)
            this.setBaseDamage((this.configuredBaseDamage * distMult) / curSpeed);

            Entity hit = result.getEntity();
            if (hit instanceof LivingEntity le) {
                le.invulnerableTime = 0;
            }
            triggerImpactEffects(sl, impact, dist, distMult, true);
        }
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel sl) {
            Vec3 impact = result.getLocation();
            double dist = this.hasOrigin
                    ? impact.distanceTo(new Vec3(this.originX, this.originY, this.originZ))
                    : 0.0D;
            float distMult = VirtualWeaponLogic.bowDistanceDamageMultiplier(dist);
            triggerImpactEffects(sl, impact, dist, distMult, false);
        }
        super.onHitBlock(result);
    }

    private void triggerImpactEffects(ServerLevel sl, Vec3 impact, double distance,
                                      float distMult, boolean entityHit) {
        boolean longShot = entityHit && distance >= VirtualWeaponLogic.BOW_LONG_SHOT_MIN_DIST;
        double pillarHeight = 14.0D * (0.75D + 0.65D * distMult);
        float pillarRadius = (gravityField ? 0.55F : 0.36F) * (0.70F + 0.55F * distMult);
        int pillarColor = gravityField
                ? 0xFFC56BFF
                : (longShot ? 0xFFFFE066 : 0xFF5CFFA8);

        // Skyward radiant pillar of light flash scaled by sniper distance multiplier!
        PacketDistributor.sendToPlayersNear(sl, null, impact.x, impact.y, impact.z, 96.0D,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_BOW_IMPACT_PILLAR,
                        impact.x, impact.y, impact.z,
                        impact.x, impact.y + pillarHeight, impact.z,
                        pillarColor, longShot ? 16 : 12, pillarRadius,
                        Math.min(1.0F, (distMult - 1.0F) / (VirtualWeaponLogic.BOW_DISTANCE_MAX_MULT - 1.0F)),
                        entityHit));

        if (longShot && this.getOwner() instanceof ServerPlayer shooter) {
            int distMeters = (int) Math.round(distance);
            String multStr = String.format(Locale.ROOT, "%.1f", distMult);
            shooter.displayClientMessage(
                    Component.translatable("message.industrialcore.weapon.bow_long_shot", distMeters, multStr)
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    true);
            sl.playSound(null, shooter.blockPosition(), ModSounds.WEAPON_LOCKON.get(),
                    SoundSource.PLAYERS, 0.85F, 1.48F);
            sl.playSound(null, shooter.blockPosition(), SoundEvents.ARROW_HIT_PLAYER,
                    SoundSource.PLAYERS, 0.80F, 1.35F);
            sl.sendParticles(ParticleTypes.END_ROD, impact.x, impact.y + 0.5D, impact.z,
                    14, 0.45D, 0.8D, 0.45D, 0.12D);
        }

        if (gravityField && !fieldSpawned) {
            fieldSpawned = true;
            WeaponRuntimeEvents.spawnGravityField(sl, impact, this.getOwner());
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetUuid != null) {
            tag.putUUID(TAG_TARGET_UUID, targetUuid);
        }
        tag.putBoolean(TAG_GRAVITY, gravityField);
        tag.putBoolean(TAG_HOMING, homing);
        tag.putInt(TAG_LIFE, lifeTicks);
        tag.putBoolean(TAG_HAS_ORIGIN, hasOrigin);
        tag.putDouble(TAG_ORIGIN_X, originX);
        tag.putDouble(TAG_ORIGIN_Y, originY);
        tag.putDouble(TAG_ORIGIN_Z, originZ);
        tag.putDouble(TAG_CONFIG_DMG, configuredBaseDamage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(TAG_TARGET_UUID)) {
            targetUuid = tag.getUUID(TAG_TARGET_UUID);
        }
        gravityField = tag.getBoolean(TAG_GRAVITY);
        homing = tag.getBoolean(TAG_HOMING);
        lifeTicks = tag.getInt(TAG_LIFE);
        hasOrigin = tag.getBoolean(TAG_HAS_ORIGIN);
        originX = tag.getDouble(TAG_ORIGIN_X);
        originY = tag.getDouble(TAG_ORIGIN_Y);
        originZ = tag.getDouble(TAG_ORIGIN_Z);
        if (tag.contains(TAG_CONFIG_DMG)) {
            configuredBaseDamage = Math.max(1.0D, tag.getDouble(TAG_CONFIG_DMG));
        }
        this.entityData.set(DATA_GRAVITY, gravityField);
        this.entityData.set(DATA_HOMING, homing);
    }
}

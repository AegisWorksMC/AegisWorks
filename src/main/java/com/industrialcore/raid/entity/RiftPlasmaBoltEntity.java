package com.industrialcore.raid.entity;

import com.industrialcore.raid.DimensionalRaidLogic;
import com.industrialcore.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 116: High-visibility purple/crimson plasma bolt fired by {@link VoidSentinelEntity} and
 * {@link DimensionalSovereignEntity}. Strictly zero block destruction.
 */
public class RiftPlasmaBoltEntity extends Projectile {
    private static final EntityDataAccessor<Boolean> DATA_OVERDRIVE =
            SynchedEntityData.defineId(RiftPlasmaBoltEntity.class, EntityDataSerializers.BOOLEAN);

    private float damage = 6.0F;
    private int lifeTicks = 0;

    public RiftPlasmaBoltEntity(EntityType<? extends RiftPlasmaBoltEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public static RiftPlasmaBoltEntity create(Level level, LivingEntity shooter, Vec3 pos, Vec3 velocity,
                                              float damage, boolean overdrive) {
        RiftPlasmaBoltEntity bolt = new RiftPlasmaBoltEntity(ModEntities.RIFT_PLASMA_BOLT.get(), level);
        bolt.setOwner(shooter);
        bolt.setPos(pos.x, pos.y, pos.z);
        bolt.setDeltaMovement(velocity);
        bolt.damage = Math.max(1.0F, damage);
        bolt.entityData.set(DATA_OVERDRIVE, overdrive);
        bolt.addTag(DimensionalRaidLogic.INVADER_TAG);
        return bolt;
    }

    public boolean isOverdrive() {
        return this.entityData.get(DATA_OVERDRIVE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OVERDRIVE, false);
    }

    @Override
    public void tick() {
        super.tick();
        lifeTicks++;
        if (lifeTicks > 90) {
            discard();
            return;
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            onHit(hit);
        }
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);

        if (level().isClientSide && lifeTicks % 2 == 0) {
            level().addParticle(isOverdrive() ? ParticleTypes.DRAGON_BREATH : ParticleTypes.WITCH,
                    getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) {
            return false;
        }
        if (target == getOwner()) {
            return false;
        }
        // Invaders never friendly-fire each other
        return !(target instanceof AbstractRiftInvaderEntity)
                && !target.getTags().contains(DimensionalRaidLogic.INVADER_TAG);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            Entity owner = getOwner();
            target.hurt(sl.damageSources().indirectMagic(this, owner != null ? owner : this), damage);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    getX(), getY(), getZ(), 6, 0.15D, 0.15D, 0.15D, 0.06D);
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        // Strictly zero block destruction — dissipate into sparks cleanly
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    getX(), getY(), getZ(), 6, 0.15D, 0.15D, 0.15D, 0.05D);
            discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putInt("Life", lifeTicks);
        tag.putBoolean("Overdrive", isOverdrive());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = Math.max(1.0F, tag.getFloat("Damage"));
        lifeTicks = tag.getInt("Life");
        entityData.set(DATA_OVERDRIVE, tag.getBoolean("Overdrive"));
    }
}

package com.industrialcore.raid.entity;

import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.raid.DimensionalRaidLogic;
import com.industrialcore.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Phase 120: Floating Dimensional Anchor Crystal ({@code rift_anchor_crystal}) spawned in sets of 3
 * above the Void Arena pillars whenever {@link DimensionalSovereignEntity} raises its Absolute Phase Barrier.
 * Destroying all 3 shatters the boss's 90%-reduction barrier and drops it into an exposed-core stun!
 */
public class RiftAnchorCrystalEntity extends Mob {
    @Nullable
    private UUID bossUuid;

    public RiftAnchorCrystalEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 36.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    public void bindToBoss(DimensionalSovereignEntity boss, int tier) {
        this.bossUuid = boss.getUUID();
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            double scaled = 30.0D + DimensionalRaidLogic.clampTier(tier) * 14.0D;
            hp.setBaseValue(scaled);
            setHealth((float) scaled);
        }
    }

    @Nullable
    public UUID getBossUuid() {
        return bossUuid;
    }

    @Override
    public boolean isNoAi() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSq) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0.0D, 0.0D, 0.0D);
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            if (tickCount % 12 == 0 && bossUuid != null) {
                Entity boss = sl.getEntity(bossUuid);
                if (boss instanceof DimensionalSovereignEntity sov && sov.isAlive()) {
                    // Visible glowing tether beam connecting this anchor crystal to the Sovereign's barrier
                    PacketDistributor.sendToPlayersNear(sl, null, getX(), getY() + 1.0D, getZ(), 96.0D,
                            new WeaponBeamFxPayload(
                                    WeaponBeamFxPayload.KIND_PRISM_CHAIN,
                                    getX(), getY() + 1.1D, getZ(),
                                    sov.getX(), sov.getY() + 1.9D, sov.getZ(),
                                    0xFFD04CFF, 13, 0.22F, 0.75F, false));
                } else if (tickCount > 40) {
                    discard();
                }
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            double cx = getX(), cy = getY() + 1.0D, cz = getZ();
            sl.playSound(null, blockPosition(), ModSounds.RIFT_SHIELD_DEFLECT.get(),
                    SoundSource.HOSTILE, 1.0F, 1.35F);
            sl.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 18, 0.45D, 0.55D, 0.45D, 0.14D);
            PacketDistributor.sendToPlayersNear(sl, null, cx, cy, cz, 96.0D,
                    new WeaponBeamFxPayload(
                            WeaponBeamFxPayload.KIND_RIFT_SHIELD_HIT,
                            cx, cy, cz, cx, cy + 0.1D, cz,
                            0xFFFFD866, 16, 2.2F, 1.0F, true));
            if (bossUuid != null && sl.getEntity(bossUuid) instanceof DimensionalSovereignEntity sov && sov.isAlive()) {
                sov.onAnchorCrystalDestroyed(sl, this.getUUID());
            }
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_CLUSTER_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (bossUuid != null) {
            tag.putUUID("BossUuid", bossUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("BossUuid")) {
            bossUuid = tag.getUUID("BossUuid");
        }
    }
}

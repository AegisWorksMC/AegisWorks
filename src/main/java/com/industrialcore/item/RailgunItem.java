package com.industrialcore.item;

import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.registry.ModSounds;
import com.industrialcore.weapon.VirtualWeaponLogic;
import com.industrialcore.weapon.VirtualWeaponLogic.TargetCandidate;
import com.industrialcore.weapon.WeaponAttachment;
import com.industrialcore.weapon.WeaponKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Electromagnetic Railgun — Plasma Wave Cannon ({@code railgun}).
 *
 * <ul>
 *   <li>Passively auto-charges ({@code 0.0f -> 1.0f}) every tick while stored anywhere in a
 *       player's inventory (60s / 1,200t base; 20s / 400t with {@code att_fast_capacitor}).</li>
 *   <li>Right-click at 100% unleashes a colossal 72m long, 3.5m wide block- &amp; entity-piercing
 *       Plasma Wave Cannon (ZERO block destruction!) dealing 72 damage + EMP shockwave, then resets
 *       charge to 0% and begins recharging automatically.</li>
 *   <li>{@code att_wall_scanner}: Highlights hostile mobs within 48m through walls.</li>
 *   <li>{@code att_recoil_gyro}: Eliminates recoil knockback + triggers EMP shockwave AoE.</li>
 * </ul>
 */
public class RailgunItem extends ElectricWeaponItem {
    public static final String TAG_AUTO_CHARGE = "AutoCharge";

    public RailgunItem(Properties properties) {
        super(WeaponKind.RAILGUN, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        float cur = getAutoCharge(stack);
        if (VirtualWeaponLogic.canFireRailgunWave(cur)) {
            return;
        }
        boolean fast = isAttachmentEnabled(stack, WeaponAttachment.FAST_CAPACITOR);
        int cost = VirtualWeaponLogic.railgunShotEnergyCost(fast);
        boolean hasPower = player.isCreative() || getEnergy(stack) >= cost;
        float next = VirtualWeaponLogic.stepRailgunAutoCharge(cur, fast, hasPower);
        if (next != cur) {
            setAutoCharge(stack, next);
            if (VirtualWeaponLogic.canFireRailgunWave(next) && !VirtualWeaponLogic.canFireRailgunWave(cur)) {
                level.playSound(null, player.blockPosition(), ModSounds.WEAPON_LOCKON.get(),
                        SoundSource.PLAYERS, 0.58F, 1.32F);
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean fast = isAttachmentEnabled(stack, WeaponAttachment.FAST_CAPACITOR);
        int cost = VirtualWeaponLogic.railgunShotEnergyCost(fast);
        if (!player.isCreative() && getEnergy(stack) < cost) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("gui.industrialcore.machine.status.no_energy")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        float charge = getAutoCharge(stack);
        if (!VirtualWeaponLogic.canFireRailgunWave(charge)) {
            if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
                int pct = Math.round(charge * 100.0F);
                int remSec = VirtualWeaponLogic.railgunRemainingSeconds(charge, fast);
                sp.displayClientMessage(
                        Component.translatable("message.industrialcore.weapon.railgun_charging", pct, remSec)
                                .withStyle(ChatFormatting.GOLD), true);
                sl.playSound(null, sp.blockPosition(), ModSounds.UI_LOCKED.get(),
                        SoundSource.PLAYERS, 0.55F, 1.15F);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            fireWaveCannon(sl, sp, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Unleashes the colossal 72m × 3.5m Plasma Wave Cannon (complete block &amp; entity penetration,
     * ZERO terrain destruction) and resets passive auto-charge to 0%.
     * Returns number of direct entities pierced.
     */
    public static int fireWaveCannon(ServerLevel sl, ServerPlayer sp, ItemStack stack) {
        boolean fast = isAttachmentEnabled(stack, WeaponAttachment.FAST_CAPACITOR);
        boolean gyro = isAttachmentEnabled(stack, WeaponAttachment.RECOIL_GYRO);
        int cost = VirtualWeaponLogic.railgunShotEnergyCost(fast);
        if (!consumeEnergy(stack, cost, sp.isCreative())) {
            sp.displayClientMessage(
                    Component.translatable("gui.industrialcore.machine.status.no_energy")
                            .withStyle(ChatFormatting.RED), true);
            return 0;
        }

        // Reset passive auto-charge immediately on fire
        setAutoCharge(stack, 0.0F);
        sp.containerMenu.broadcastChanges();

        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0F).normalize();
        Vec3 end = eye.add(look.scale(VirtualWeaponLogic.RAILGUN_RANGE));

        // 3.5m-wide (1.75m half-width) corridor piercing ALL blocks & entities with ZERO block damage!
        double halfW = VirtualWeaponLogic.RAILGUN_WAVE_HALF_WIDTH;
        AABB beamBounds = new AABB(eye, end).inflate(halfW + 1.0D);
        List<LivingEntity> candidates = sl.getEntitiesOfClass(LivingEntity.class, beamBounds,
                e -> e != sp && e.isAlive() && !e.isSpectator());

        Map<Integer, LivingEntity> byId = new HashMap<>();
        List<TargetCandidate> tCands = new ArrayList<>(candidates.size());
        for (LivingEntity le : candidates) {
            byId.put(le.getId(), le);
            tCands.add(new TargetCandidate(le.getId(), le.getX(), le.getY() + le.getBbHeight() * 0.5D, le.getZ()));
        }
        List<TargetCandidate> hits = VirtualWeaponLogic.findCorridorTargets(
                eye.x, eye.y, eye.z, end.x, end.y, end.z, halfW, tCands);

        Set<Integer> empDamagedIds = new HashSet<>();
        int hitCount = 0;
        for (TargetCandidate tc : hits) {
            LivingEntity target = byId.get(tc.id());
            if (target == null || !target.isAlive()) {
                continue;
            }
            target.invulnerableTime = 0;
            target.hurt(sl.damageSources().playerAttack(sp), VirtualWeaponLogic.RAILGUN_WAVE_DAMAGE);
            Vec3 center = new Vec3(tc.x(), tc.y(), tc.z());
            sl.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 2, 0.25D, 0.25D, 0.25D, 0.0D);
            hitCount++;
            if (gyro) {
                triggerEmpShockwave(sl, sp, center, target.getId(), empDamagedIds);
            }
        }

        // Always pulse EMP shockwave along the cannon axis when Recoil Gyro is installed
        if (gyro && hitCount == 0) {
            Vec3 empCenter = eye.add(look.scale(16.0D));
            triggerEmpShockwave(sl, sp, empCenter, -1, empDamagedIds);
        }

        // Recoil knockback + pitch kick unless Recoil Gyro is installed
        double recoil = VirtualWeaponLogic.railgunRecoilSpeed(gyro);
        if (recoil > 0.0D) {
            Vec3 kick = look.scale(-recoil);
            sp.setDeltaMovement(sp.getDeltaMovement().add(kick.x, Math.max(0.10D, kick.y * 0.5D), kick.z));
            sp.hurtMarked = true;
            sp.setXRot(Math.max(-90.0F, sp.getXRot() - VirtualWeaponLogic.railgunPitchKick(false)));
        }

        // Colossal 1.35-block-radius Plasma Wave Cannon with 1.2s (24-tick) lingering afterglow (bottom-center barrel)!
        Vec3 muzzle = eye.add(look.scale(1.05D)).add(0.0D, -0.22D, 0.0D);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_RAILGUN_BEAM,
                        muzzle.x, muzzle.y, muzzle.z,
                        end.x, end.y, end.z,
                        gyro ? 0xFF42F5FF : 0xFF6BB2FF,
                        24, 1.35F, 1.0F, hitCount > 0));

        for (int step = 3; step <= 60; step += 4) {
            Vec3 p = eye.add(look.scale(step));
            sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 3, 0.6D, 0.6D, 0.6D, 0.03D);
        }

        sl.playSound(null, sp.blockPosition(), ModSounds.WEAPON_RAILGUN_FIRE.get(),
                SoundSource.PLAYERS, 1.00F, 1.00F);
        sp.getCooldowns().addCooldown(stack.getItem(), 12);
        return hitCount;
    }

    /** Legacy helper kept for compatibility; delegates to {@link #fireWaveCannon} at full charge. */
    public static int fireRailgunShot(ServerLevel sl, ServerPlayer sp, ItemStack stack, float chargeRatio) {
        if (!VirtualWeaponLogic.canFireRailgunWave(chargeRatio)) {
            return 0;
        }
        return fireWaveCannon(sl, sp, stack);
    }

    public static float getAutoCharge(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0.0F : Math.max(0.0F, Math.min(1.0F, cd.copyTag().getFloat(TAG_AUTO_CHARGE)));
    }

    public static void setAutoCharge(ItemStack stack, float charge) {
        float clamped = Math.max(0.0F, Math.min(1.0F, charge));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putFloat(TAG_AUTO_CHARGE, clamped));
    }

    private static void triggerEmpShockwave(ServerLevel sl, ServerPlayer sp, Vec3 center,
                                            int primaryId, Set<Integer> alreadyHit) {
        AABB empBox = new AABB(center, center).inflate(VirtualWeaponLogic.RAILGUN_EMP_RADIUS);
        List<LivingEntity> nearby = sl.getEntitiesOfClass(LivingEntity.class, empBox,
                e -> e != sp && e.getId() != primaryId && e.isAlive() && !e.isSpectator()
                        && e.position().distanceToSqr(center)
                        <= VirtualWeaponLogic.RAILGUN_EMP_RADIUS * VirtualWeaponLogic.RAILGUN_EMP_RADIUS);
        for (LivingEntity mob : nearby) {
            if (alreadyHit.add(mob.getId())) {
                mob.invulnerableTime = 0;
                mob.hurt(sl.damageSources().playerAttack(sp), VirtualWeaponLogic.RAILGUN_EMP_DAMAGE);
                Vec3 push = mob.position().subtract(center);
                if (push.lengthSqr() > 1.0E-4D) {
                    push = push.normalize().scale(0.65D);
                    mob.setDeltaMovement(mob.getDeltaMovement().add(push.x, 0.25D, push.z));
                }
            }
        }
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 20, 1.4D, 1.0D, 1.4D, 0.14D);
    }
}

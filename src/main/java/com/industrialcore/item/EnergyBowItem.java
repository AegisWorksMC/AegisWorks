package com.industrialcore.item;

import com.industrialcore.registry.ModSounds;
import com.industrialcore.weapon.EnergyArrowEntity;
import com.industrialcore.weapon.VirtualWeaponLogic;
import com.industrialcore.weapon.VirtualWeaponLogic.TargetCandidate;
import com.industrialcore.weapon.WeaponAttachment;
import com.industrialcore.weapon.WeaponKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 101: Energy Bow ({@code energy_bow}).
 *
 * <ul>
 *   <li>Consumes ZERO vanilla arrows — fires glowing high-velocity light arrows powered by FE.</li>
 *   <li>{@code att_multi_lock}: Locks onto up to 4 hostile mobs in sight and fires homing light
 *       arrows simultaneously at all locked targets!</li>
 *   <li>{@code att_gravity_arrow}: Deploys a 2.5s gravity singularity field on impact pulling mobs
 *       within 7m smoothly inward.</li>
 *   <li>{@code att_auto_draw}: Greatly shortens draw time (8 ticks vs 20) for rapid fire.</li>
 * </ul>
 */
public class EnergyBowItem extends ElectricWeaponItem {
    public EnergyBowItem(Properties properties) {
        super(WeaponKind.ENERGY_BOW, properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isCreative() && getEnergy(stack) < VirtualWeaponLogic.BOW_FE_PER_ARROW) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("gui.industrialcore.machine.status.no_energy")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            int held = getUseDuration(stack, entity) - remainingUseDuration;
            if (held == 4 && isAttachmentEnabled(stack, WeaponAttachment.MULTI_LOCK)) {
                int locks = findLockedHostiles(sl, sp).size();
                if (locks > 0) {
                    sl.playSound(null, sp.blockPosition(), ModSounds.WEAPON_LOCKON.get(),
                            SoundSource.PLAYERS, 0.42F, 0.98F + 0.06F * locks);
                }
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (level.isClientSide || !(entity instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
            return;
        }
        int held = getUseDuration(stack, entity) - timeCharged;
        boolean autoDraw = isAttachmentEnabled(stack, WeaponAttachment.AUTO_DRAW);
        float drawRatio = VirtualWeaponLogic.bowDrawRatio(held, autoDraw);
        fireEnergyBow(sl, sp, stack, drawRatio);
    }

    /**
     * Fires 1 straight arrow (or up to 4 homing arrows with {@code att_multi_lock}).
     * Returns the number of arrows spawned.
     */
    public static int fireEnergyBow(ServerLevel sl, ServerPlayer sp, ItemStack stack, float drawRatio) {
        if (drawRatio < 0.2F) {
            return 0;
        }
        boolean multiLock = isAttachmentEnabled(stack, WeaponAttachment.MULTI_LOCK);
        boolean gravity = isAttachmentEnabled(stack, WeaponAttachment.GRAVITY_ARROW);

        List<LivingEntity> locked = multiLock ? findLockedHostiles(sl, sp) : List.of();
        int shotCount = locked.isEmpty() ? 1 : locked.size();
        int totalCost = shotCount * VirtualWeaponLogic.BOW_FE_PER_ARROW
                + (gravity ? VirtualWeaponLogic.BOW_FE_GRAVITY_BONUS : 0);

        if (!consumeEnergy(stack, totalCost, sp.isCreative())) {
            // Fallback to single shot if enough FE for 1 arrow
            if (shotCount > 1 && consumeEnergy(stack, VirtualWeaponLogic.BOW_FE_PER_ARROW, sp.isCreative())) {
                locked = List.of(locked.get(0));
            } else {
                sp.displayClientMessage(
                        Component.translatable("gui.industrialcore.machine.status.no_energy")
                                .withStyle(ChatFormatting.RED), true);
                return 0;
            }
        }

        double dmg = VirtualWeaponLogic.BOW_ARROW_BASE_DAMAGE * (0.45D + 0.55D * drawRatio);
        float speed = VirtualWeaponLogic.BOW_ARROW_SPEED * (0.65F + 0.35F * drawRatio);

        if (locked.isEmpty()) {
            EnergyArrowEntity arrow = new EnergyArrowEntity(sl, sp, stack);
            arrow.configure(null, gravity, dmg);
            arrow.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), 0.0F, speed, 0.3F);
            sl.addFreshEntity(arrow);
            sl.playSound(null, sp.blockPosition(), ModSounds.WEAPON_BOW_FIRE.get(),
                    SoundSource.PLAYERS, 0.80F, 1.00F);
            return 1;
        }

        Vec3 eye = sp.getEyePosition();
        int fired = 0;
        for (int i = 0; i < locked.size(); i++) {
            LivingEntity target = locked.get(i);
            EnergyArrowEntity arrow = new EnergyArrowEntity(sl, sp, stack);
            // Deploy gravity field on the first arrow (avoid stacking 4 fields on same tick)
            arrow.configure(target.getUUID(), gravity && i == 0, dmg);
            Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D).subtract(eye).normalize();
            // Slight fan offset for sleek multi-lock missile feel
            double spreadAngle = (i - (locked.size() - 1) * 0.5D) * 0.06D;
            Vec3 dir = aim.yRot((float) spreadAngle).normalize();
            arrow.setPos(eye.x + dir.x * 0.6D, eye.y - 0.1D + dir.y * 0.6D, eye.z + dir.z * 0.6D);
            arrow.shoot(dir.x, dir.y, dir.z, speed, 0.15F);
            sl.addFreshEntity(arrow);
            fired++;
        }
        sl.playSound(null, sp.blockPosition(), ModSounds.WEAPON_BOW_FIRE.get(),
                SoundSource.PLAYERS, 0.86F, 1.08F);
        return fired;
    }

    /** Finds up to 4 hostile mobs inside the player's sight cone (usable on both client HUD & server). */
    public static List<LivingEntity> findLockedHostiles(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        AABB box = player.getBoundingBox().inflate(VirtualWeaponLogic.BOW_MULTI_LOCK_RANGE);
        List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !e.isSpectator() && e instanceof Enemy);
        if (mobs.isEmpty()) {
            return List.of();
        }
        Map<Integer, LivingEntity> byId = new HashMap<>();
        List<TargetCandidate> cands = new ArrayList<>(mobs.size());
        for (LivingEntity mob : mobs) {
            byId.put(mob.getId(), mob);
            cands.add(new TargetCandidate(mob.getId(), mob.getX(), mob.getY() + mob.getBbHeight() * 0.55D, mob.getZ()));
        }
        List<TargetCandidate> selected = VirtualWeaponLogic.selectMultiLockTargets(
                eye.x, eye.y, eye.z, look.x, look.y, look.z,
                VirtualWeaponLogic.BOW_MULTI_LOCK_RANGE,
                VirtualWeaponLogic.BOW_MULTI_LOCK_COS,
                VirtualWeaponLogic.BOW_MULTI_LOCK_MAX,
                cands);
        List<LivingEntity> result = new ArrayList<>(selected.size());
        for (TargetCandidate tc : selected) {
            LivingEntity mob = byId.get(tc.id());
            if (mob != null) {
                result.add(mob);
            }
        }
        return result;
    }
}

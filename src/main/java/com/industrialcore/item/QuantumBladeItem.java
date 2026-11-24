package com.industrialcore.item;

import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.registry.ModSounds;
import com.industrialcore.weapon.VirtualWeaponLogic;
import com.industrialcore.weapon.WeaponAttachment;
import com.industrialcore.weapon.WeaponKind;
import com.industrialcore.weapon.WeaponRuntimeEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Phase 101: Quantum Blade ({@code quantum_blade}).
 *
 * <ul>
 *   <li>High-speed, high-damage FE melee blade (18 damage while powered, drops to 4 at 0 FE,
 *       never breaks).</li>
 *   <li>{@code att_plasma_wave}: Left-click swing launches a piercing 14m plasma slash wave.</li>
 *   <li>{@code att_blink_drive}: Right-click performs a strict safe-coord blink teleport (up to 14m,
 *       never suffocates in walls) + AoE arrival slash.</li>
 *   <li>{@code att_quantum_loot}: Bonus fortune-like drops + auto-collect to inventory or linked Quantum Storage!</li>
 * </ul>
 */
public class QuantumBladeItem extends ElectricWeaponItem {
    public static final String TAG_WAVE_READY_TICK = "WaveReadyTick";
    public static final String TAG_BLINK_READY_TICK = "BlinkReadyTick";
    public static final String TAG_COMBO_STAGE = "ComboStage";
    public static final String TAG_LAST_SWING_TICK = "LastSwingTick";

    public QuantumBladeItem(Properties properties) {
        super(WeaponKind.QUANTUM_BLADE, properties.attributes(createAttributes()));
    }

    private static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                                VirtualWeaponLogic.BLADE_DAMAGE_UNPOWERED - 1.0D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -1.8D,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide && attacker instanceof ServerPlayer sp
                && attacker.level() instanceof ServerLevel sl) {
            boolean powered = consumeEnergy(stack, VirtualWeaponLogic.BLADE_FE_MELEE, sp.isCreative());
            if (powered) {
                float bonus = VirtualWeaponLogic.BLADE_DAMAGE_POWERED - VirtualWeaponLogic.BLADE_DAMAGE_UNPOWERED;
                target.invulnerableTime = 0;
                target.hurt(sl.damageSources().playerAttack(sp), bonus);
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
                sl.playSound(null, target.blockPosition(), ModSounds.WEAPON_BLADE_SLASH.get(),
                        SoundSource.PLAYERS, 0.64F, 1.15F);
            }
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isAttachmentEnabled(stack, WeaponAttachment.BLINK_DRIVE)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            boolean blinked = tryBlinkDrive(sl, sp, stack);
            return blinked ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Executes a 3-hit Japanese katana combo slash (0: Kesagiri, 1: Gyaku-Kesa, 2: Horizontal Sweep),
     * broadcasting the world-space additive crescent slash arc, playing the high-energy plasma blade
     * Doppler SFX, and also launching {@code att_plasma_wave} if enabled &amp; ready.
     */
    public int performKatanaSwing(ServerPlayer sp, ItemStack stack) {
        if (!(sp.level() instanceof ServerLevel sl)) {
            return -1;
        }
        long now = sl.getGameTime();
        long lastTick = getLastSwingTick(stack);
        if (now - lastTick < 3L) {
            return 0; // deduplicate same-tick AttackEntity + LeftClick
        }
        int nextStage = VirtualWeaponLogic.nextBladeComboStage(getComboStage(stack), now - lastTick);
        setComboState(stack, nextStage, now);

        Vec3 eye = sp.getEyePosition().add(0.0D, -0.18D, 0.0D);
        Vec3 look = sp.getViewVector(1.0F).normalize();
        Vec3 arcOrigin = eye.add(look.scale(0.55D));
        Vec3 arcTarget = eye.add(look.scale(2.55D));

        int arcColor = switch (nextStage) {
            case 0 -> 0xFF3EF7FF; // Cyan Kesagiri
            case 1 -> 0xFFE26BFF; // Magenta Gyaku-Kesa
            default -> 0xFF5CFFA8; // Emerald Horizontal Sweep
        };
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_BLADE_SLASH_ARC,
                        arcOrigin.x, arcOrigin.y, arcOrigin.z,
                        arcTarget.x, arcTarget.y, arcTarget.z,
                        arcColor, 7, 1.35F, nextStage / 2.0F, false));
        float pitch = 0.94F + nextStage * 0.11F;
        sl.playSound(null, sp.blockPosition(), ModSounds.WEAPON_BLADE_SLASH.get(),
                SoundSource.PLAYERS, 0.76F, pitch);

        return tryFirePlasmaWave(sp, stack);
    }

    /**
     * Launches a piercing crescent plasma slash wave forward ({@code att_plasma_wave}).
     * Returns number of entities hit (or -1 if on cooldown / disabled / no FE).
     */
    public int tryFirePlasmaWave(ServerPlayer sp, ItemStack stack) {
        if (!(sp.level() instanceof ServerLevel sl) || !isAttachmentEnabled(stack, WeaponAttachment.PLASMA_WAVE)) {
            return -1;
        }
        long now = sl.getGameTime();
        if (now < getWaveReadyTick(stack)) {
            return -1;
        }
        if (!consumeEnergy(stack, VirtualWeaponLogic.BLADE_FE_WAVE, sp.isCreative())) {
            return -1;
        }
        setWaveReadyTick(stack, now + VirtualWeaponLogic.BLADE_WAVE_COOLDOWN_TICKS);

        Vec3 start = sp.getEyePosition().add(0.0D, -0.22D, 0.0D);
        Vec3 look = sp.getViewVector(1.0F).normalize();
        Vec3 end = start.add(look.scale(VirtualWeaponLogic.BLADE_WAVE_RANGE));

        AABB sweepBox = new AABB(start, end).inflate(1.8D);
        List<LivingEntity> mobs = sl.getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> e != sp && e.isAlive() && !e.isSpectator());

        int hits = 0;
        for (LivingEntity mob : mobs) {
            Vec3 mobCenter = mob.position().add(0.0D, mob.getBbHeight() * 0.5D, 0.0D);
            Vec3 toMob = mobCenter.subtract(start);
            double proj = toMob.dot(look);
            if (proj < 0.4D || proj > VirtualWeaponLogic.BLADE_WAVE_RANGE) {
                continue;
            }
            Vec3 closestOnRay = start.add(look.scale(proj));
            if (closestOnRay.distanceToSqr(mobCenter) <= 2.45D * 2.45D) {
                mob.invulnerableTime = 0;
                mob.hurt(sl.damageSources().playerAttack(sp), VirtualWeaponLogic.BLADE_WAVE_DAMAGE);
                sl.sendParticles(ParticleTypes.CRIT, mobCenter.x, mobCenter.y, mobCenter.z, 6, 0.3D, 0.3D, 0.3D, 0.1D);
                hits++;
            }
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_PLASMA_WAVE,
                        start.x, start.y, start.z,
                        end.x, end.y, end.z,
                        0xFFE26BFF, 11, 1.85F, getComboStage(stack) / 2.0F, hits > 0));
        return hits;
    }

    /**
     * Phase Dash: high-speed 6-tick forward piercing dash (up to 13.5m), stopping
     * safely before solid walls, negating fall damage, slicing EVERY enemy along the corridor, and
     * leaving a blazing cyan-emerald afterimage streak + slash flash.
     */
    public static boolean tryBlinkDrive(ServerLevel sl, ServerPlayer sp, ItemStack stack) {
        if (!isAttachmentEnabled(stack, WeaponAttachment.BLINK_DRIVE)) {
            return false;
        }
        long now = sl.getGameTime();
        if (now < getBlinkReadyTick(stack)) {
            return false;
        }
        if (!consumeEnergy(stack, VirtualWeaponLogic.BLADE_FE_BLINK, sp.isCreative())) {
            sp.displayClientMessage(
                    Component.translatable("gui.industrialcore.machine.status.no_energy")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }

        Vec3 origin = sp.position();
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0F).normalize();

        // Sample 0.5m steps from 1.0m up to 13.5m (26 samples), stopping strictly before solid walls
        int steps = 26;
        Vec3[] positions = new Vec3[steps];
        boolean[] safeFlags = new boolean[steps];
        boolean hitSolidWall = false;
        for (int i = 0; i < steps; i++) {
            double dist = 1.0D + i * 0.5D;
            Vec3 sampleEye = eye.add(look.scale(dist));
            Vec3 feetPos = new Vec3(sampleEye.x, sampleEye.y - sp.getEyeHeight() + 0.15D, sampleEye.z);
            positions[i] = feetPos;
            if (hitSolidWall) {
                safeFlags[i] = false;
                continue;
            }
            BlockPos eyeBp = BlockPos.containing(sampleEye);
            if (!sl.getBlockState(eyeBp).getCollisionShape(sl, eyeBp).isEmpty()) {
                hitSolidWall = true;
                safeFlags[i] = false;
                continue;
            }
            safeFlags[i] = isSafeStandingSpot(sl, sp, feetPos);
        }

        int safeIdx = VirtualWeaponLogic.findSafeBlinkIndex(safeFlags);
        if (safeIdx < 0) {
            return false;
        }

        Vec3 dest = positions[safeIdx];
        setBlinkReadyTick(stack, now + VirtualWeaponLogic.BLADE_BLINK_COOLDOWN_TICKS);

        // Start 6-tick smooth high-speed Phase Dash corridor slice!
        WeaponRuntimeEvents.startSwiftDash(sl, sp, origin, dest);

        // Blazing cyan/emerald afterimage ribbon & slash flash along full dash path
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_BLINK_TRAIL,
                        origin.x, origin.y + 1.0D, origin.z,
                        dest.x, dest.y + 1.0D, dest.z,
                        0xFF36FFCC, 14, 0.42F, 1.0F, true));
        sl.playSound(null, sp.blockPosition(), ModSounds.WEAPON_BLADE_DASH.get(),
                SoundSource.PLAYERS, 0.88F, 1.04F);
        return true;
    }

    /** Verifies player AABB has zero block collisions and non-suffocating head/feet blocks. */
    public static boolean isSafeStandingSpot(ServerLevel sl, ServerPlayer sp, Vec3 feetPos) {
        if (!sl.getWorldBorder().isWithinBounds(feetPos.x, feetPos.z)) {
            return false;
        }
        if (feetPos.y < sl.getMinBuildHeight() + 1 || feetPos.y > sl.getMaxBuildHeight() - 2) {
            return false;
        }
        AABB candidateBox = sp.getDimensions(sp.getPose()).makeBoundingBox(feetPos);
        if (!sl.noCollision(sp, candidateBox)) {
            return false;
        }
        BlockPos feetBp = BlockPos.containing(feetPos.x, feetPos.y + 0.1D, feetPos.z);
        BlockPos headBp = BlockPos.containing(feetPos.x, feetPos.y + sp.getEyeHeight(), feetPos.z);
        return !sl.getBlockState(feetBp).isSuffocating(sl, feetBp)
                && !sl.getBlockState(headBp).isSuffocating(sl, headBp);
    }

    public static long getWaveReadyTick(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0L : cd.copyTag().getLong(TAG_WAVE_READY_TICK);
    }

    public static void setWaveReadyTick(ItemStack stack, long tick) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(TAG_WAVE_READY_TICK, tick));
    }

    public static long getBlinkReadyTick(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0L : cd.copyTag().getLong(TAG_BLINK_READY_TICK);
    }

    public static void setBlinkReadyTick(ItemStack stack, long tick) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(TAG_BLINK_READY_TICK, tick));
    }

    public static int getComboStage(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0 : Math.max(0, Math.min(2, cd.copyTag().getInt(TAG_COMBO_STAGE)));
    }

    public static long getLastSwingTick(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? -100L : cd.copyTag().getLong(TAG_LAST_SWING_TICK);
    }

    public static void setComboState(ItemStack stack, int stage, long gameTime) {
        int s = Math.max(0, Math.min(2, stage));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(TAG_COMBO_STAGE, s);
            tag.putLong(TAG_LAST_SWING_TICK, gameTime);
        });
    }
}

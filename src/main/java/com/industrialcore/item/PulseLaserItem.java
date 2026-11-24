package com.industrialcore.item;

import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.registry.ModSounds;
import com.industrialcore.weapon.LaserAimRange;
import com.industrialcore.weapon.LaserTargetMode;
import com.industrialcore.weapon.VirtualWeaponLogic;
import com.industrialcore.weapon.VirtualWeaponLogic.HeatState;
import com.industrialcore.weapon.VirtualWeaponLogic.TargetCandidate;
import com.industrialcore.weapon.WeaponAttachment;
import com.industrialcore.weapon.WeaponKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 101 / 102.5: Continuous-beam Pulse Laser ({@code pulse_laser}).
 *
 * <ul>
 *   <li>Always fires a <b>straight beam</b> along the player's current look ray (28m).</li>
 *   <li>{@code att_auto_aim}: Smoothly snaps the player's camera view toward the locked target
 *       center while firing (respecting {@link LaserTargetMode}: {@code HOSTILE_ONLY} vs
 *       {@code ALL_MOBS}, toggled via Sneak + Left-Click).</li>
 *   <li>{@code att_cryo_core}: Cuts heat buildup and ramps sustained hit damage up to 2.2x.</li>
 *   <li>{@code att_prism_lens}: Refracts beam from primary target to up to 2 nearby targets (6m).</li>
 * </ul>
 */
public class PulseLaserItem extends ElectricWeaponItem {
    public static final String TAG_HEAT = "Heat";
    public static final String TAG_OVERHEATED = "Overheated";
    public static final String TAG_RAMP_TICKS = "RampTicks";
    public static final String TAG_LAST_TARGET = "LastTarget";
    public static final String TAG_LAST_FIRE_TICK = "LastFireTick";
    public static final String TAG_TARGET_MODE = "TargetMode";
    public static final String TAG_AIM_RANGE = "AimRange";
    public static final String TAG_CHARGE = "LaserCharge";
    public static final String TAG_TICKS_SINCE_HIT = "TicksSinceHit";
    public static final String TAG_LAST_CHARGE_TICK = "LastChargeTick";

    public PulseLaserItem(Properties properties) {
        super(WeaponKind.PULSE_LASER, properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // Phase 105.8: Custom FPS front-aim stance is rendered in WeaponFirstPersonRenderer (no sideways bow twist).
        return UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isOverheated(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.industrialcore.weapon.overheated")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!player.isCreative() && getEnergy(stack) < VirtualWeaponLogic.LASER_FE_PER_TICK) {
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
            boolean fired = fireLaserTick(sl, sp, stack);
            if (!fired) {
                sp.stopUsingItem();
            }
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        long now = level.getGameTime();
        boolean currentlyFiring = (player.isUsingItem() && player.getUseItem() == stack)
                || (now - getLastFireTick(stack) <= 2L);
        if (!currentlyFiring) {
            float heat = getHeat(stack);
            boolean ov = isOverheated(stack);
            if (heat > 0.0F || ov || getRampTicks(stack) > 0) {
                boolean cryo = isAttachmentEnabled(stack, WeaponAttachment.CRYO_CORE);
                HeatState next = VirtualWeaponLogic.stepLaserHeat(heat, ov, false, cryo);
                setHeatState(stack, next.heat(), next.overheated(), 0, -1, getLastFireTick(stack));
            }
            // Advance hit-accumulation charge hold/decay cleanly when not actively firing
            tickChargeDecay(stack, now);
        }
    }

    /**
     * Advances {@code LaserCharge} hold (5.0s / 100 ticks) and gentle decay (-0.005/tick after 100 ticks)
     * cleanly across elapsed game ticks, even while unequipped or stored in inventory.
     */
    public static void tickChargeDecay(ItemStack stack, long nowTick) {
        float charge = getCharge(stack);
        if (charge <= 0.0F) {
            if (getTicksSinceHit(stack) != 0) {
                setChargeState(stack, 0.0F, 0, nowTick);
            }
            return;
        }
        long lastSync = getLastChargeTick(stack);
        if (lastSync <= 0L) {
            setChargeState(stack, charge, getTicksSinceHit(stack), nowTick);
            return;
        }
        long elapsed = nowTick - lastSync;
        if (elapsed <= 0L) {
            return;
        }
        int steps = (int) Math.min(2_000L, elapsed);
        int ticksSinceHit = getTicksSinceHit(stack);
        float c = charge;
        for (int i = 0; i < steps; i++) {
            VirtualWeaponLogic.LaserChargeState st =
                    VirtualWeaponLogic.stepLaserCharge(c, ticksSinceHit, false, false);
            c = st.charge();
            ticksSinceHit = st.ticksSinceLastHit();
            if (c <= 0.0F) {
                ticksSinceHit = 0;
                break;
            }
        }
        setChargeState(stack, c, ticksSinceHit, nowTick);
    }

    /**
     * Server-authoritative single tick of continuous Pulse Laser firing.
     * Returns {@code true} if the beam fired cleanly, or {@code false} if overheated/out of FE.
     */
    public static boolean fireLaserTick(ServerLevel sl, ServerPlayer sp, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof PulseLaserItem)) {
            return false;
        }
        boolean cryo = isAttachmentEnabled(stack, WeaponAttachment.CRYO_CORE);
        boolean autoAim = isAttachmentEnabled(stack, WeaponAttachment.AUTO_AIM);
        boolean prism = isAttachmentEnabled(stack, WeaponAttachment.PRISM_LENS);

        float curHeat = getHeat(stack);
        boolean curOv = isOverheated(stack);
        if (curOv) {
            HeatState cooled = VirtualWeaponLogic.stepLaserHeat(curHeat, true, false, cryo);
            setHeatState(stack, cooled.heat(), cooled.overheated(), 0, -1, sl.getGameTime());
            return false;
        }
        if (!consumeEnergy(stack, VirtualWeaponLogic.LASER_FE_PER_TICK, sp.isCreative())) {
            sp.displayClientMessage(
                    Component.translatable("gui.industrialcore.machine.status.no_energy")
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }

        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0F).normalize();
        // Slightly offset muzzle origin toward right-lower-front of eye for crisp 3D look
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-4D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 muzzle = eye.add(look.scale(0.72D)).add(right.scale(0.26D)).add(0.0D, -0.16D, 0.0D);

        // Phase 102.5: ALWAYS straight-line raycast along player's current look vector!
        // Auto-aim snaps the player's camera toward the target rather than bending the beam.
        Vec3 maxEnd = eye.add(look.scale(VirtualWeaponLogic.LASER_RANGE));
        BlockHitResult blockHit = sl.clip(new ClipContext(
                eye, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        Vec3 endPos = blockHit.getType() == HitResult.Type.MISS ? maxEnd : blockHit.getLocation();

        LivingEntity primaryTarget = findDirectRayTarget(sl, sp, eye, endPos, autoAim);
        Vec3 hitPos = primaryTarget != null
                ? primaryTarget.getBoundingBox().getCenter()
                : endPos;
        LaserTargetMode mode = getTargetMode(stack);

        // Update heat & cryo sustained ramp
        int lastId = getLastTargetId(stack);
        int ramp = getRampTicks(stack);
        if (primaryTarget != null && primaryTarget.getId() == lastId) {
            ramp = Math.min(VirtualWeaponLogic.LASER_CRYO_RAMP_TICKS, ramp + 1);
        } else if (primaryTarget != null) {
            ramp = 1;
            if (autoAim) {
                sl.playSound(null, sp.blockPosition(), ModSounds.WEAPON_LOCKON.get(),
                        SoundSource.PLAYERS, 0.36F, 1.08F);
            }
        } else {
            ramp = 0;
        }

        HeatState nextHeat = VirtualWeaponLogic.stepLaserHeat(curHeat, false, true, cryo);
        setHeatState(stack, nextHeat.heat(), nextHeat.overheated(),
                ramp, primaryTarget != null ? primaryTarget.getId() : -1, sl.getGameTime());

        if (nextHeat.overheated()) {
            sl.playSound(null, sp.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.65F, 1.4F);
            sp.displayClientMessage(
                    Component.translatable("message.industrialcore.weapon.overheated")
                            .withStyle(ChatFormatting.RED), true);
        }

        // Catch up any idle gap ticks strictly before this tick
        long now = sl.getGameTime();
        if (now > 1L) {
            tickChargeDecay(stack, now - 1L);
        }
        float curCharge = getCharge(stack);
        int curTicksSinceHit = getTicksSinceHit(stack);

        // Primary beam damage scaled by hit-accumulation charge (0.40 -> 2.00/t, or 2.20/t with Cryo)
        final LivingEntity hitTarget = primaryTarget;
        boolean dealtDamage = false;
        float dmg = VirtualWeaponLogic.laserChargedDamage(curCharge, cryo);
        if (hitTarget != null && hitTarget.isAlive()) {
            hitTarget.invulnerableTime = 0;
            dealtDamage = hitTarget.hurt(sl.damageSources().playerAttack(sp), dmg);
            int sparkCount = 1 + Math.round(curCharge * 4.0F);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    hitPos.x, hitPos.y, hitPos.z, sparkCount, 0.14D, 0.14D, 0.14D, 0.04D);
            if (curCharge >= 0.70F) {
                sl.sendParticles(ParticleTypes.END_ROD,
                        hitPos.x, hitPos.y, hitPos.z, 2, 0.18D, 0.18D, 0.18D, 0.05D);
            }

            // Prism Lens refraction chains to up to 2 nearby matching targets (6m)
            if (prism) {
                List<LivingEntity> nearby = sl.getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(hitPos, hitPos).inflate(VirtualWeaponLogic.LASER_PRISM_RANGE),
                        e -> e != sp && e != hitTarget && e.isAlive()
                                && matchesFilter(e, mode));
                if (!nearby.isEmpty()) {
                    Map<Integer, LivingEntity> byId = new HashMap<>();
                    List<TargetCandidate> cands = new ArrayList<>(nearby.size());
                    for (LivingEntity le : nearby) {
                        byId.put(le.getId(), le);
                        cands.add(new TargetCandidate(le.getId(), le.getX(), le.getY() + le.getBbHeight() * 0.55D, le.getZ()));
                    }
                    List<TargetCandidate> chains = VirtualWeaponLogic.selectPrismChains(
                            hitTarget.getId(), hitPos.x, hitPos.y, hitPos.z,
                            VirtualWeaponLogic.LASER_PRISM_RANGE,
                            VirtualWeaponLogic.LASER_PRISM_MAX_TARGETS, cands);
                    float chainDmg = dmg * VirtualWeaponLogic.LASER_PRISM_DAMAGE_FACTOR;
                    for (TargetCandidate tc : chains) {
                        LivingEntity secondary = byId.get(tc.id());
                        if (secondary != null && secondary.isAlive()) {
                            secondary.invulnerableTime = 0;
                            if (secondary.hurt(sl.damageSources().playerAttack(sp), chainDmg)) {
                                dealtDamage = true;
                            }
                            PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                                    new WeaponBeamFxPayload(
                                            WeaponBeamFxPayload.KIND_PRISM_CHAIN,
                                            hitPos.x, hitPos.y, hitPos.z,
                                            tc.x(), tc.y(), tc.z(),
                                            0xFFD95BFF, 3, 0.055F * VirtualWeaponLogic.laserBeamWidthFactor(curCharge),
                                            curCharge));
                        }
                    }
                }
            }
        }

        // Step hit-accumulation charge: grows on hit (resets 5s timer), holds for 100t without hit
        VirtualWeaponLogic.LaserChargeState nextCharge =
                VirtualWeaponLogic.stepLaserCharge(curCharge, curTicksSinceHit, dealtDamage, cryo);
        setChargeState(stack, nextCharge.charge(), nextCharge.ticksSinceLastHit(), now);

        // Broadcast main 3D beam with dynamic width scaled by charge (0.6x thin -> 2.4x overdrive!)
        // Continuous beam audio is driven smoothly on the client by LaserAudioController (2-layer + sizzle).
        float widthFactor = VirtualWeaponLogic.laserBeamWidthFactor(nextCharge.charge());
        int beamColor = nextCharge.charge() >= 0.85F
                ? 0xFFFF6CF5
                : (cryo ? 0xFF7BE8FF : (nextHeat.heat() > 70.0F ? 0xFFFF5E7E : 0xFF3EF7FF));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new WeaponBeamFxPayload(
                        WeaponBeamFxPayload.KIND_LASER_BEAM,
                        muzzle.x, muzzle.y, muzzle.z,
                        hitPos.x, hitPos.y, hitPos.z,
                        beamColor, 3, 0.072F * widthFactor, nextCharge.charge(), dealtDamage));
        return !nextHeat.overheated();
    }

    /**
     * Finds the optimal target within 28m respecting the weapon's {@link LaserTargetMode},
     * {@link LaserAimRange}, lock hysteresis, and unobstructed line of sight.
     */
    @Nullable
    public static LivingEntity findAutoAimTarget(Level level, Player player, ItemStack stack) {
        return findAutoAimTarget(level, player, stack, -1, 1.0F);
    }

    @Nullable
    public static LivingEntity findAutoAimTarget(Level level, Player player, ItemStack stack,
                                                 int lockedTargetId, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick).normalize();
        return findAutoAimTarget(level, player, eye, look,
                getTargetMode(stack), getAimRange(stack), lockedTargetId, partialTick);
    }

    @Nullable
    public static LivingEntity findAutoAimTarget(Level level, Player player, Vec3 eye, Vec3 look,
                                                 LaserTargetMode mode) {
        return findAutoAimTarget(level, player, eye, look, mode, LaserAimRange.WIDE, -1, 1.0F);
    }

    @Nullable
    public static LivingEntity findAutoAimTarget(Level level, Player player, Vec3 eye, Vec3 look,
                                                 LaserTargetMode mode, LaserAimRange range,
                                                 int lockedTargetId, float partialTick) {
        AABB search = player.getBoundingBox().inflate(VirtualWeaponLogic.LASER_RANGE);
        List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, search,
                e -> e != player && e.isAlive() && matchesFilter(e, mode));
        if (mobs.isEmpty()) {
            return null;
        }
        Map<Integer, LivingEntity> byId = new HashMap<>();
        List<TargetCandidate> candidates = new ArrayList<>(mobs.size());
        for (LivingEntity mob : mobs) {
            Vec3 interpPos = mob.getPosition(partialTick);
            Vec3 center = mob.getBoundingBox().getCenter().add(interpPos.subtract(mob.position()));
            // Must have unobstructed line of sight
            BlockHitResult clip = level.clip(new ClipContext(
                    eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (clip.getType() != HitResult.Type.MISS
                    && clip.getLocation().distanceToSqr(eye) < center.distanceToSqr(eye) - 0.5D) {
                continue;
            }
            byId.put(mob.getId(), mob);
            candidates.add(new TargetCandidate(mob.getId(), center.x, center.y, center.z));
        }
        TargetCandidate best = VirtualWeaponLogic.selectAutoAimTarget(
                eye.x, eye.y, eye.z, look.x, look.y, look.z,
                VirtualWeaponLogic.LASER_RANGE, range, lockedTargetId, candidates);
        return best != null ? byId.get(best.id()) : null;
    }

    public static boolean matchesFilter(LivingEntity entity, LaserTargetMode mode) {
        boolean isEnemy = entity instanceof Enemy;
        boolean isAngryNeutral = entity instanceof NeutralMob nm && nm.isAngry();
        boolean isMob = entity instanceof Mob;
        boolean isPlayer = entity instanceof Player;
        return VirtualWeaponLogic.matchesTargetFilter(
                mode, isEnemy, isAngryNeutral, isMob, isPlayer,
                entity.isSpectator(), entity.isInvulnerable());
    }

    @Nullable
    private static LivingEntity findDirectRayTarget(ServerLevel sl, ServerPlayer sp, Vec3 start, Vec3 end,
                                                    boolean autoAim) {
        double inflate = autoAim
                ? VirtualWeaponLogic.LASER_RAY_INFLATE_AUTO_AIM
                : VirtualWeaponLogic.LASER_RAY_INFLATE_BASE;
        AABB rayBox = new AABB(start, end).inflate(0.85D);
        List<LivingEntity> entities = sl.getEntitiesOfClass(LivingEntity.class, rayBox,
                e -> e != sp && e.isAlive() && !e.isSpectator() && e.isPickable());
        LivingEntity closest = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity le : entities) {
            var hit = le.getBoundingBox().inflate(inflate).clip(start, end);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(start);
                if (d < bestDist) {
                    bestDist = d;
                    closest = le;
                }
            }
        }
        return closest;
    }

    public static LaserAimRange getAimRange(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? LaserAimRange.WIDE : LaserAimRange.byId(cd.copyTag().getString(TAG_AIM_RANGE));
    }

    public static void setAimRange(ItemStack stack, LaserAimRange range) {
        LaserAimRange safe = range == null ? LaserAimRange.WIDE : range;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_AIM_RANGE, safe.id()));
    }

    /** Server-authoritative cycle via R key or Suit Modifier ({@code NARROW -> WIDE -> FULL_360}). */
    public static LaserAimRange toggleAimRange(ServerPlayer sp, ItemStack stack) {
        LaserAimRange next = getAimRange(stack).next();
        setAimRange(stack, next);
        sp.containerMenu.broadcastChanges();
        ChatFormatting color = switch (next) {
            case NARROW -> ChatFormatting.AQUA;
            case WIDE -> ChatFormatting.GREEN;
            case FULL_360 -> ChatFormatting.LIGHT_PURPLE;
        };
        sp.displayClientMessage(
                Component.translatable("message.industrialcore.weapon.laser_range_changed",
                                Component.translatable(next.translationKey()))
                        .withStyle(color),
                true);
        if (sp.level() instanceof ServerLevel sl) {
            float pitch = switch (next) {
                case NARROW -> 1.32F;
                case WIDE -> 1.08F;
                case FULL_360 -> 0.86F;
            };
            sl.playSound(null, sp.blockPosition(), ModSounds.WEAPON_LOCKON.get(),
                    SoundSource.PLAYERS, 0.45F, pitch);
        }
        return next;
    }

    public static LaserTargetMode getTargetMode(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? LaserTargetMode.HOSTILE_ONLY : LaserTargetMode.byId(cd.copyTag().getString(TAG_TARGET_MODE));
    }

    public static void setTargetMode(ItemStack stack, LaserTargetMode mode) {
        LaserTargetMode safe = mode == null ? LaserTargetMode.HOSTILE_ONLY : mode;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TAG_TARGET_MODE, safe.id()));
    }

    /** Server-authoritative toggle via Sneak + Left-Click with action bar &amp; crisp electronic chime. */
    public static LaserTargetMode toggleTargetMode(ServerPlayer sp, ItemStack stack) {
        LaserTargetMode next = getTargetMode(stack).next();
        setTargetMode(stack, next);
        sp.containerMenu.broadcastChanges();
        sp.displayClientMessage(
                Component.translatable("message.industrialcore.weapon.laser_mode_changed",
                                Component.translatable(next.translationKey()))
                        .withStyle(next == LaserTargetMode.HOSTILE_ONLY ? ChatFormatting.AQUA : ChatFormatting.GOLD),
                true);
        if (sp.level() instanceof ServerLevel sl) {
            sl.playSound(null, sp.blockPosition(), ModSounds.WEAPON_LOCKON.get(),
                    SoundSource.PLAYERS, 0.45F, next == LaserTargetMode.HOSTILE_ONLY ? 1.18F : 0.96F);
        }
        return next;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        LaserTargetMode mode = getTargetMode(stack);
        tooltip.add(Component.translatable("tooltip.industrialcore.weapon.laser_mode",
                        Component.translatable(mode.translationKey()))
                .withStyle(mode == LaserTargetMode.HOSTILE_ONLY ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        LaserAimRange range = getAimRange(stack);
        ChatFormatting rangeCol = switch (range) {
            case NARROW -> ChatFormatting.AQUA;
            case WIDE -> ChatFormatting.GREEN;
            case FULL_360 -> ChatFormatting.LIGHT_PURPLE;
        };
        tooltip.add(Component.translatable("tooltip.industrialcore.weapon.laser_range",
                        Component.translatable(range.translationKey()))
                .withStyle(rangeCol));
        tooltip.add(Component.translatable("tooltip.industrialcore.weapon.laser_mode_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.industrialcore.weapon.laser_range_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // -------------------------------------------------------------------------
    // Heat & Ramp persistence helpers (read by HUD as well!)
    // -------------------------------------------------------------------------

    public static float getHeat(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0.0F : Math.max(0.0F, Math.min(100.0F, cd.copyTag().getFloat(TAG_HEAT)));
    }

    public static boolean isOverheated(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().getBoolean(TAG_OVERHEATED);
    }

    public static int getRampTicks(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0 : Math.max(0, cd.copyTag().getInt(TAG_RAMP_TICKS));
    }

    public static int getLastTargetId(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? -1 : cd.copyTag().getInt(TAG_LAST_TARGET);
    }

    public static long getLastFireTick(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? -100L : cd.copyTag().getLong(TAG_LAST_FIRE_TICK);
    }

    public static float getCharge(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0.0F : Math.max(0.0F, Math.min(1.0F, cd.copyTag().getFloat(TAG_CHARGE)));
    }

    public static int getTicksSinceHit(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0 : Math.max(0, cd.copyTag().getInt(TAG_TICKS_SINCE_HIT));
    }

    public static long getLastChargeTick(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? 0L : cd.copyTag().getLong(TAG_LAST_CHARGE_TICK);
    }

    public static void setChargeState(ItemStack stack, float charge, int ticksSinceHit, long gameTime) {
        float clamped = Math.max(0.0F, Math.min(1.0F, charge));
        int safeTicks = Math.max(0, ticksSinceHit);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putFloat(TAG_CHARGE, clamped);
            tag.putInt(TAG_TICKS_SINCE_HIT, safeTicks);
            tag.putLong(TAG_LAST_CHARGE_TICK, gameTime);
        });
    }

    public static void setHeatState(ItemStack stack, float heat, boolean overheated,
                                    int rampTicks, int lastTargetId, long gameTime) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putFloat(TAG_HEAT, Math.max(0.0F, Math.min(100.0F, heat)));
            tag.putBoolean(TAG_OVERHEATED, overheated);
            tag.putInt(TAG_RAMP_TICKS, Math.max(0, rampTicks));
            tag.putInt(TAG_LAST_TARGET, lastTargetId);
            tag.putLong(TAG_LAST_FIRE_TICK, gameTime);
        });
    }
}

package com.industrialcore.weapon;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Phase 101: Pure-Java combat, heat, charge, targeting, blink safety, gravity vortex, and
 * attachment management math for the 4 endgame electric weapons.
 *
 * <p>Zero Minecraft runtime dependency so every balance invariant and edge case is covered by
 * fast JUnit 5 tests.</p>
 */
public final class VirtualWeaponLogic {
    // Shared FE Battery Specs
    public static final int WEAPON_CAPACITY = 5_000_000;
    public static final int WEAPON_MAX_RECEIVE = 150_000;

    // Pulse Laser Constants
    public static final double LASER_RANGE = 28.0D;
    public static final double LASER_PRISM_RANGE = 6.0D;
    public static final int LASER_PRISM_MAX_TARGETS = 2;
    public static final int LASER_FE_PER_TICK = 450;
    public static final float LASER_BASE_DAMAGE_PER_TICK = 1.4F;
    /** Phase 102.5+: Hit-accumulation charge curve (0.40/t at 0% -> 2.00/t at 100%, 5s hold, gentle decay). */
    public static final float LASER_CHARGE_BASE_DAMAGE = 0.40F;
    public static final float LASER_CHARGE_MAX_MULT = 5.0F;
    public static final float LASER_CHARGE_CRYO_MAX_MULT = 5.5F;
    public static final float LASER_CHARGE_GAIN_PER_HIT = 0.015F;
    public static final float LASER_CHARGE_CRYO_GAIN_MULT = 1.35F;
    public static final int LASER_CHARGE_HOLD_TICKS = 100; // 5.0 seconds
    public static final float LASER_CHARGE_DECAY_PER_TICK = 0.005F; // -0.10/sec
    public static final float LASER_BEAM_WIDTH_MIN = 0.60F;
    public static final float LASER_BEAM_WIDTH_MAX = 2.40F;
    public static final float LASER_PRISM_DAMAGE_FACTOR = 0.65F;
    public static final float LASER_HEAT_GAIN_BASE = 2.6F;
    public static final float LASER_HEAT_GAIN_CRYO = 0.75F;
    public static final float LASER_HEAT_COOL_BASE = 2.2F;
    public static final float LASER_HEAT_COOL_CRYO = 3.8F;
    public static final float LASER_OVERHEAT_THRESHOLD = 100.0F;
    public static final float LASER_COOL_UNLOCK_THRESHOLD = 25.0F;
    public static final float LASER_CRYO_MAX_MULT = 2.2F;
    public static final int LASER_CRYO_RAMP_TICKS = 50;
    public static final double LASER_AUTO_AIM_COS = Math.cos(Math.toRadians(28.0D));
    /** High-precision auto-aim snap threshold (errors <= 6 deg snap directly to 0 deg). */
    public static final float LASER_AIM_SNAP_THRESHOLD_DEG = 6.0F;
    /** Target velocity lead prediction horizon in ticks (0.5..1.0t). */
    public static final double LASER_LEAD_TICKS = 0.75D;
    /** Server-side straight-ray AABB inflate margins (+0.25m sync-jitter forgiveness when auto-aim is active). */
    public static final double LASER_RAY_INFLATE_BASE = 0.30D;
    public static final double LASER_RAY_INFLATE_AUTO_AIM = 0.55D;

    // Railgun Plasma Wave Cannon Constants
    public static final double RAILGUN_RANGE = 72.0D;
    public static final int RAILGUN_CHARGE_BASE_TICKS = 36; // legacy compat
    public static final int RAILGUN_CHARGE_FAST_TICKS = 14; // legacy compat
    /** Passive inventory auto-charge: 60s (1,200t) standard, 20s (400t) with att_fast_capacitor. */
    public static final int RAILGUN_AUTO_CHARGE_BASE_TICKS = 1_200;
    public static final int RAILGUN_AUTO_CHARGE_FAST_TICKS = 400;
    public static final int RAILGUN_FE_BASE = 25_000;
    public static final int RAILGUN_FE_FAST = 15_000;
    public static final float RAILGUN_BASE_DAMAGE = 38.0F;
    public static final float RAILGUN_WAVE_DAMAGE = 72.0F;
    public static final double RAILGUN_WAVE_HALF_WIDTH = 1.75D; // 3.5m wide wave cannon beam
    public static final boolean RAILGUN_WAVE_DESTROYS_TERRAIN = false;
    public static final float RAILGUN_EMP_DAMAGE = 18.0F;
    public static final double RAILGUN_EMP_RADIUS = 5.5D;
    public static final double RAILGUN_WALL_SCAN_RANGE = 48.0D;
    public static final double RAILGUN_RECOIL_BASE = 0.65D;
    public static final float RAILGUN_PITCH_KICK_BASE = 6.5F;

    // Quantum Blade & Phase Dash Constants
    public static final float BLADE_DAMAGE_POWERED = 18.0F;
    public static final float BLADE_DAMAGE_UNPOWERED = 4.0F;
    public static final int BLADE_FE_MELEE = 800;
    public static final int BLADE_FE_WAVE = 3_500;
    public static final float BLADE_WAVE_DAMAGE = 16.0F;
    public static final double BLADE_WAVE_RANGE = 15.0D;
    public static final int BLADE_WAVE_COOLDOWN_TICKS = 14;
    public static final int BLADE_FE_BLINK = 4_500;
    public static final double BLADE_BLINK_MAX_RANGE = 13.5D;
    public static final int BLADE_SWIFT_DASH_TICKS = 6;
    public static final double BLADE_SWIFT_CORRIDOR_HALF_WIDTH = 1.50D; // >= 2.5m corridor width
    public static final float BLADE_BLINK_SLASH_DAMAGE = 26.0F;
    public static final double BLADE_BLINK_SLASH_RADIUS = 3.5D;
    public static final int BLADE_BLINK_COOLDOWN_TICKS = 30;
    public static final int BLADE_COMBO_RESET_TICKS = 32;
    public static final int BLADE_FE_LOOT_BONUS = 500;

    // Energy Bow Constants
    public static final int BOW_DRAW_BASE_TICKS = 20;
    public static final int BOW_DRAW_AUTO_TICKS = 8;
    public static final int BOW_FE_PER_ARROW = 1_800;
    public static final int BOW_FE_GRAVITY_BONUS = 2_200;
    public static final float BOW_ARROW_BASE_DAMAGE = 16.0F;
    public static final float BOW_ARROW_SPEED = 3.8F;
    public static final int BOW_MULTI_LOCK_MAX = 4;
    public static final double BOW_MULTI_LOCK_RANGE = 44.0D;
    public static final double BOW_MULTI_LOCK_COS = Math.cos(Math.toRadians(24.0D));
    /** Optical sniper scope zoom at full draw (~2.78x magnification) &amp; 96m rangefinder max. */
    public static final float BOW_SCOPE_MAX_ZOOM_FOV_MULT = 0.36F;
    public static final double BOW_RANGEFINDER_MAX_DIST = 96.0D;
    /** Distance damage scaling: 1.0x up to 15m -> linear ramp -> 2.8x at >= 60m. */
    public static final double BOW_DISTANCE_BASE_THRESHOLD = 15.0D;
    public static final double BOW_DISTANCE_MAX_THRESHOLD = 60.0D;
    public static final float BOW_DISTANCE_MAX_MULT = 2.8F;
    public static final double BOW_LONG_SHOT_MIN_DIST = 30.0D;
    public static final int GRAVITY_DURATION_TICKS = 50; // 2.5 seconds
    public static final double GRAVITY_RADIUS = 7.0D;
    public static final double GRAVITY_MAX_PULL = 0.34D;

    private VirtualWeaponLogic() {}

    // -------------------------------------------------------------------------
    // Attachment Management (shared with suit_modifier)
    // -------------------------------------------------------------------------

    public static boolean canInstall(@Nullable WeaponKind kind, @Nullable WeaponAttachment att,
                                     Map<String, Boolean> installed) {
        if (kind == null || att == null || installed == null) {
            return false;
        }
        return att.matches(kind) && !installed.containsKey(att.id());
    }

    public static boolean install(@Nullable WeaponKind kind, @Nullable WeaponAttachment att,
                                  Map<String, Boolean> installed) {
        if (!canInstall(kind, att, installed)) {
            return false;
        }
        installed.put(att.id(), Boolean.TRUE);
        return true;
    }

    public static boolean toggle(@Nullable WeaponAttachment att, Map<String, Boolean> installed) {
        if (att == null || installed == null || !installed.containsKey(att.id())) {
            return false;
        }
        boolean cur = Boolean.TRUE.equals(installed.get(att.id()));
        installed.put(att.id(), !cur);
        return true;
    }

    public static boolean uninstall(@Nullable WeaponAttachment att, Map<String, Boolean> installed) {
        if (att == null || installed == null || !installed.containsKey(att.id())) {
            return false;
        }
        installed.remove(att.id());
        return true;
    }

    public static boolean isAttachmentEnabled(@Nullable WeaponAttachment att, Map<String, Boolean> installed) {
        return att != null && installed != null && Boolean.TRUE.equals(installed.get(att.id()));
    }

    // -------------------------------------------------------------------------
    // Pulse Laser: Heat, Overheat Latch, Cryo Ramp, Auto-Aim & Prism Chaining
    // -------------------------------------------------------------------------

    public record HeatState(float heat, boolean overheated) {}

    /**
     * Advances Pulse Laser heat by 1 tick.
     * Reaching 100% latches {@code overheated = true} until cooled back to {@code <= 25%}.
     */
    public static HeatState stepLaserHeat(float currentHeat, boolean overheated,
                                          boolean firing, boolean cryoCore) {
        float h = Math.max(0.0F, Math.min(LASER_OVERHEAT_THRESHOLD, currentHeat));
        boolean ov = overheated;
        if (ov) {
            // Cannot fire while latched overheated; cool down until <= 25%
            float cool = cryoCore ? LASER_HEAT_COOL_CRYO : LASER_HEAT_COOL_BASE;
            h = Math.max(0.0F, h - cool);
            if (h <= LASER_COOL_UNLOCK_THRESHOLD) {
                ov = false;
            }
            return new HeatState(h, ov);
        }
        if (firing) {
            float gain = cryoCore ? LASER_HEAT_GAIN_CRYO : LASER_HEAT_GAIN_BASE;
            h = Math.min(LASER_OVERHEAT_THRESHOLD, h + gain);
            if (h >= LASER_OVERHEAT_THRESHOLD) {
                ov = true;
            }
        } else {
            float cool = cryoCore ? LASER_HEAT_COOL_CRYO : LASER_HEAT_COOL_BASE;
            h = Math.max(0.0F, h - cool);
        }
        return new HeatState(h, ov);
    }

    /**
     * Computes Pulse Laser damage per tick; with {@code att_cryo_core}, sustained contact ramps
     * smoothly from 1.0x up to 2.2x over {@link #LASER_CRYO_RAMP_TICKS}.
     */
    public static float laserTickDamage(int consecutiveHitTicks, boolean cryoCore) {
        if (!cryoCore || consecutiveHitTicks <= 0) {
            return LASER_BASE_DAMAGE_PER_TICK;
        }
        float t = Math.min(1.0F, (float) consecutiveHitTicks / (float) LASER_CRYO_RAMP_TICKS);
        float mult = 1.0F + t * (LASER_CRYO_MAX_MULT - 1.0F);
        return LASER_BASE_DAMAGE_PER_TICK * mult;
    }

    /** Hit-accumulation laser charge state (charge in [0, 1] and ticks elapsed since last damaging hit). */
    public record LaserChargeState(float charge, int ticksSinceLastHit) {}

    /**
     * Advances Pulse Laser charge by 1 tick:
     * <ul>
     *   <li>On a damaging hit: adds {@value #LASER_CHARGE_GAIN_PER_HIT} (+35% with Cryo Core),
     *       clamped to {@code 1.0f}, and resets {@code ticksSinceLastHit = 0}.</li>
     *   <li>Without a hit: holds charge completely steady for {@value #LASER_CHARGE_HOLD_TICKS} ticks
     *       (5.0 seconds), then decays gently by {@value #LASER_CHARGE_DECAY_PER_TICK} per tick
     *       down to {@code 0.0f}.</li>
     * </ul>
     */
    public static LaserChargeState stepLaserCharge(float currentCharge, int ticksSinceLastHit,
                                                   boolean dealtDamage, boolean cryoCore) {
        float c = Math.max(0.0F, Math.min(1.0F, currentCharge));
        if (dealtDamage) {
            float gain = cryoCore
                    ? LASER_CHARGE_GAIN_PER_HIT * LASER_CHARGE_CRYO_GAIN_MULT
                    : LASER_CHARGE_GAIN_PER_HIT;
            return new LaserChargeState(Math.min(1.0F, c + gain), 0);
        }
        int nextTicks = Math.max(0, ticksSinceLastHit) + 1;
        if (nextTicks <= LASER_CHARGE_HOLD_TICKS) {
            return new LaserChargeState(c, nextTicks);
        }
        return new LaserChargeState(Math.max(0.0F, c - LASER_CHARGE_DECAY_PER_TICK), nextTicks);
    }

    /**
     * Computes Pulse Laser per-tick damage from current charge {@code [0..1]}:
     * {@code 0.40} at 0% -> {@code 2.00} at 100% (or {@code 2.20} at 100% with {@code att_cryo_core}).
     */
    public static float laserChargedDamage(float charge, boolean cryoCore) {
        float c = Math.max(0.0F, Math.min(1.0F, charge));
        float maxMult = cryoCore ? LASER_CHARGE_CRYO_MAX_MULT : LASER_CHARGE_MAX_MULT;
        float mult = 1.0F + c * (maxMult - 1.0F);
        return LASER_CHARGE_BASE_DAMAGE * mult;
    }

    /**
     * Computes the 3D beam width multiplier from current charge {@code [0..1]}:
     * {@code 0.60x} thin beam at 0% -> {@code 1.35x} at 50% -> {@code 2.40x} massive beam at 100%.
     */
    public static float laserBeamWidthFactor(float charge) {
        float c = Math.max(0.0F, Math.min(1.0F, charge));
        return LASER_BEAM_WIDTH_MIN + 1.20F * c + 0.60F * c * c;
    }

    /** Pure 3D target descriptor for auto-aim, prism chaining, and multi-lock selection. */
    public record TargetCandidate(int id, double x, double y, double z) {}

    /** Phase 102.5: Camera yaw/pitch pair in Minecraft degrees. */
    public record LookAngles(float yawDeg, float pitchDeg) {}

    /** Wraps any angle in degrees into {@code (-180, 180]}. */
    public static float wrapDegrees(float deg) {
        float w = deg % 360.0F;
        if (w > 180.0F) {
            w -= 360.0F;
        }
        if (w <= -180.0F) {
            w += 360.0F;
        }
        return w;
    }

    /**
     * Computes the shortest signed angular delta in degrees from {@code currentDeg} to
     * {@code targetDeg} in {@code (-180, 180]}. Crossing the ±180° seam (e.g. {@code 179 -> -179})
     * yields {@code +2°} rather than spinning backward by {@code -358°}.
     */
    public static float shortestAngleDelta(float currentDeg, float targetDeg) {
        return wrapDegrees(targetDeg - currentDeg);
    }

    /** Computes Minecraft yaw and pitch (clamped to [-89.5, 89.5]) from eye toward target point. */
    public static LookAngles computeLookAngles(double eyeX, double eyeY, double eyeZ,
                                               double targetX, double targetY, double targetZ) {
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(1.0E-6D, horiz))));
        pitch = Math.max(-89.5F, Math.min(89.5F, pitch));
        return new LookAngles(wrapDegrees(yaw), pitch);
    }

    /**
     * Predicts a moving target's center position {@code leadTicks} into the future using its
     * current velocity vector {@code (velX, velY, velZ)} so high-speed or slim mobs remain centered.
     */
    public static Vec3d predictTargetPosition(double posX, double posY, double posZ,
                                              double velX, double velY, double velZ,
                                              double leadTicks) {
        double t = Math.max(0.0D, Math.min(2.0D, leadTicks));
        return new Vec3d(posX + velX * t, posY + velY * t, posZ + velZ * t);
    }

    /**
     * Smoothly steps camera yaw &amp; pitch toward target angles along the shortest angular arc,
     * snapping directly to {@code 0°} error once combined angular difference enters
     * {@link #LASER_AIM_SNAP_THRESHOLD_DEG} ({@code 6°}), with pitch clamped to {@code [-89.5, 89.5]}.
     */
    public static LookAngles stepAimRotation(float currentYaw, float currentPitch,
                                             float targetYaw, float targetPitch,
                                             float smoothing, float maxStepDeg) {
        return stepAimRotation(currentYaw, currentPitch, targetYaw, targetPitch,
                smoothing, maxStepDeg, LASER_AIM_SNAP_THRESHOLD_DEG);
    }

    /**
     * Full precision rotation step with explicit snap-adsorption threshold in degrees.
     * When {@code hypot(dYaw, dPitch) <= snapThresholdDeg}, adsorbs cleanly to exact target angles
     * across the {@code ±180°} seam so moving targets incur zero trailing lag.
     */
    public static LookAngles stepAimRotation(float currentYaw, float currentPitch,
                                             float targetYaw, float targetPitch,
                                             float smoothing, float maxStepDeg,
                                             float snapThresholdDeg) {
        float clampedTargetPitch = Math.max(-89.5F, Math.min(89.5F, targetPitch));
        float rawDYaw = shortestAngleDelta(currentYaw, targetYaw);
        float rawDPitch = clampedTargetPitch - currentPitch;
        float snap = Math.max(0.0F, snapThresholdDeg);
        if (Math.hypot(rawDYaw, rawDPitch) <= snap) {
            return new LookAngles(currentYaw + rawDYaw, clampedTargetPitch);
        }

        float k = Math.max(0.0F, Math.min(1.0F, smoothing));
        float cap = Math.max(0.5F, maxStepDeg);
        float dYaw = Math.max(-cap, Math.min(cap, rawDYaw * k));
        float dPitch = Math.max(-cap, Math.min(cap, rawDPitch * k));
        float nextYaw = currentYaw + dYaw;
        float nextPitch = Math.max(-89.5F, Math.min(89.5F, currentPitch + dPitch));
        return new LookAngles(nextYaw, nextPitch);
    }

    /**
     * Phase 102.5: Pure target filter predicate for {@link LaserTargetMode}.
     * Always excludes players, spectators, and invulnerable entities.
     */
    public static boolean matchesTargetFilter(LaserTargetMode mode,
                                              boolean isEnemy,
                                              boolean isAngryNeutral,
                                              boolean isMob,
                                              boolean isPlayer,
                                              boolean isSpectator,
                                              boolean isInvulnerable) {
        if (isPlayer || isSpectator || isInvulnerable) {
            return false;
        }
        LaserTargetMode m = mode == null ? LaserTargetMode.HOSTILE_ONLY : mode;
        return switch (m) {
            case HOSTILE_ONLY -> isEnemy || isAngryNeutral;
            case ALL_MOBS -> isMob || isEnemy;
        };
    }

    /**
     * Selects the best hostile candidate inside the player's forward cone for {@code att_auto_aim}.
     * Balances angular alignment with proximity so targets right near the reticle win cleanly.
     */
    @Nullable
    public static TargetCandidate selectAutoAimTarget(double eyeX, double eyeY, double eyeZ,
                                                      double dirX, double dirY, double dirZ,
                                                      double maxRange, double minDot,
                                                      List<TargetCandidate> candidates) {
        double dirLen = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dirLen < 1.0E-6D || candidates == null || candidates.isEmpty()) {
            return null;
        }
        double nx = dirX / dirLen;
        double ny = dirY / dirLen;
        double nz = dirZ / dirLen;
        double maxDistSqr = maxRange * maxRange;

        TargetCandidate best = null;
        double bestScore = Double.MAX_VALUE;
        for (TargetCandidate c : candidates) {
            double dx = c.x() - eyeX;
            double dy = c.y() - eyeY;
            double dz = c.z() - eyeZ;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr < 1.0E-4D || distSqr > maxDistSqr) {
                continue;
            }
            double dist = Math.sqrt(distSqr);
            double dot = (dx * nx + dy * ny + dz * nz) / dist;
            if (dot < minDot) {
                continue;
            }
            // Lower score = closer to center of crosshair & nearer distance
            double anglePenalty = (1.0D - dot) * 30.0D;
            double score = dist * 0.4D + anglePenalty;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /**
     * Selects the optimal auto-aim target respecting {@link LaserAimRange} FOV preset and lock
     * hysteresis ({@code lockedTargetId}):
     * <ul>
     *   <li>If {@code lockedTargetId >= 0} is still present in {@code candidates}, within
     *       {@code maxRange}, and inside {@code aimRange}, retains that exact lock without jitter.</li>
     *   <li>{@link LaserAimRange#NARROW} (±25°) &amp; {@link LaserAimRange#WIDE} (±60°):
     *       prioritizes the smallest angular offset from the reticle (distance as micro tie-breaker).</li>
     *   <li>{@link LaserAimRange#FULL_360} (±180°): prioritizes the nearest target in any direction.</li>
     * </ul>
     */
    @Nullable
    public static TargetCandidate selectAutoAimTarget(double eyeX, double eyeY, double eyeZ,
                                                      double dirX, double dirY, double dirZ,
                                                      double maxRange,
                                                      @Nullable LaserAimRange aimRange,
                                                      int lockedTargetId,
                                                      List<TargetCandidate> candidates) {
        double dirLen = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dirLen < 1.0E-6D || candidates == null || candidates.isEmpty()) {
            return null;
        }
        LaserAimRange range = aimRange == null ? LaserAimRange.WIDE : aimRange;
        double nx = dirX / dirLen;
        double ny = dirY / dirLen;
        double nz = dirZ / dirLen;
        double maxDistSqr = maxRange * maxRange;
        double minDot = range.minDot();
        boolean full360 = range.isFull360();

        // 1. Lock hysteresis: keep existing locked target while still valid in range & FOV
        if (lockedTargetId >= 0) {
            for (TargetCandidate c : candidates) {
                if (c.id() != lockedTargetId) {
                    continue;
                }
                double dx = c.x() - eyeX;
                double dy = c.y() - eyeY;
                double dz = c.z() - eyeZ;
                double distSqr = dx * dx + dy * dy + dz * dz;
                if (distSqr >= 1.0E-4D && distSqr <= maxDistSqr) {
                    if (full360) {
                        return c;
                    }
                    double dot = (dx * nx + dy * ny + dz * nz) / Math.sqrt(distSqr);
                    if (dot >= minDot) {
                        return c;
                    }
                }
                break;
            }
        }

        // 2. Select best new candidate based on LaserAimRange priority rule
        TargetCandidate best = null;
        double bestScore = Double.MAX_VALUE;
        for (TargetCandidate c : candidates) {
            double dx = c.x() - eyeX;
            double dy = c.y() - eyeY;
            double dz = c.z() - eyeZ;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr < 1.0E-4D || distSqr > maxDistSqr) {
                continue;
            }
            double dist = Math.sqrt(distSqr);
            double dot = (dx * nx + dy * ny + dz * nz) / dist;
            if (!full360 && dot < minDot) {
                continue;
            }
            double score = full360
                    ? distSqr + (1.0D - dot) * 1.0E-4D
                    : (1.0D - dot) + dist * 1.0E-5D;
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /**
     * Selects up to {@code maxChains} secondary targets within {@code chainRadius} of the primary
     * hit for {@code att_prism_lens}, ordered nearest-first.
     */
    public static List<TargetCandidate> selectPrismChains(int primaryId,
                                                          double hitX, double hitY, double hitZ,
                                                          double chainRadius, int maxChains,
                                                          List<TargetCandidate> candidates) {
        if (candidates == null || candidates.isEmpty() || maxChains <= 0) {
            return List.of();
        }
        double rSqr = chainRadius * chainRadius;
        List<TargetCandidate> valid = new ArrayList<>();
        for (TargetCandidate c : candidates) {
            if (c.id() == primaryId) {
                continue;
            }
            double dx = c.x() - hitX;
            double dy = c.y() - hitY;
            double dz = c.z() - hitZ;
            if (dx * dx + dy * dy + dz * dz <= rSqr) {
                valid.add(c);
            }
        }
        valid.sort(Comparator.comparingDouble(c -> {
            double dx = c.x() - hitX;
            double dy = c.y() - hitY;
            double dz = c.z() - hitZ;
            return dx * dx + dy * dy + dz * dz;
        }));
        return valid.size() <= maxChains ? List.copyOf(valid) : List.copyOf(valid.subList(0, maxChains));
    }

    // -------------------------------------------------------------------------
    // Railgun: Charge, Energy Cost, Piercing Damage & Recoil
    // -------------------------------------------------------------------------

    public static int railgunChargeTicks(boolean fastCapacitor) {
        return fastCapacitor ? RAILGUN_CHARGE_FAST_TICKS : RAILGUN_CHARGE_BASE_TICKS;
    }

    /** Passive inventory auto-charge duration in ticks: 1,200t (60s) base, 400t (20s) with Fast Capacitor. */
    public static int railgunAutoChargeTicks(boolean fastCapacitor) {
        return fastCapacitor ? RAILGUN_AUTO_CHARGE_FAST_TICKS : RAILGUN_AUTO_CHARGE_BASE_TICKS;
    }

    /** Advances passive Railgun Wave Cannon auto-charge by 1 tick (pauses only when unpowered). */
    public static float stepRailgunAutoCharge(float currentCharge, boolean fastCapacitor, boolean hasPower) {
        float c = Math.max(0.0F, Math.min(1.0F, currentCharge));
        if (!hasPower || c >= 1.0F) {
            return c;
        }
        float step = 1.0F / (float) railgunAutoChargeTicks(fastCapacitor);
        float next = c + step;
        return next >= 0.9999F ? 1.0F : next;
    }

    /** Wave Cannon may fire ONLY once passive charge reaches 100%. */
    public static boolean canFireRailgunWave(float currentCharge) {
        return currentCharge >= 1.0F - 1.0E-4F;
    }

    /** Remaining seconds (rounded up) until passive Wave Cannon charge reaches 100%. */
    public static int railgunRemainingSeconds(float currentCharge, boolean fastCapacitor) {
        float c = Math.max(0.0F, Math.min(1.0F, currentCharge));
        if (canFireRailgunWave(c)) {
            return 0;
        }
        double remTicks = (1.0D - c) * railgunAutoChargeTicks(fastCapacitor);
        return Math.max(1, (int) Math.ceil(remTicks / 20.0D));
    }

    public static float railgunChargeRatio(int heldTicks, boolean fastCapacitor) {
        int needed = railgunChargeTicks(fastCapacitor);
        if (heldTicks <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) heldTicks / (float) needed);
    }

    public static int railgunShotEnergyCost(boolean fastCapacitor) {
        return fastCapacitor ? RAILGUN_FE_FAST : RAILGUN_FE_BASE;
    }

    /** Scales railgun damage from 35% at min release (>=0.25 charge) up to 100% (38 dmg) at full charge. */
    public static float railgunDamage(float chargeRatio) {
        float r = Math.max(0.0F, Math.min(1.0F, chargeRatio));
        if (r < 0.25F) {
            return 0.0F;
        }
        float norm = (r - 0.25F) / 0.75F;
        return RAILGUN_BASE_DAMAGE * (0.35F + 0.65F * norm);
    }

    public static double railgunRecoilSpeed(boolean recoilGyro) {
        return recoilGyro ? 0.0D : RAILGUN_RECOIL_BASE;
    }

    public static float railgunPitchKick(boolean recoilGyro) {
        return recoilGyro ? 0.0F : RAILGUN_PITCH_KICK_BASE;
    }

    // -------------------------------------------------------------------------
    // Quantum Blade: Melee Damage & Strict Safe-Blink Search
    // -------------------------------------------------------------------------

    public static float bladeMeleeDamage(int energyStored, boolean creative) {
        return (creative || energyStored >= BLADE_FE_MELEE) ? BLADE_DAMAGE_POWERED : BLADE_DAMAGE_UNPOWERED;
    }

    /**
     * Given an array of passability flags along a blink ray from index {@code 0} (near player)
     * to {@code length - 1} (farthest desired point), returns the farthest index {@code i}
     * where standing at {@code i} is safe ({@code passable[i] == true}), or {@code -1} if none.
     *
     * <p>If a solid wall blocks the ray at some point, walking backwards from the impact point
     * guarantees the player NEVER teleports inside a solid block.</p>
     */
    public static int findSafeBlinkIndex(boolean[] sampleSafe) {
        if (sampleSafe == null || sampleSafe.length == 0) {
            return -1;
        }
        for (int i = sampleSafe.length - 1; i >= 0; i--) {
            if (sampleSafe[i]) {
                return i;
            }
        }
        return -1;
    }

    /** Advances Quantum Blade katana 3-hit combo: 0 (Kesagiri) -> 1 (Gyaku-Kesa) -> 2 (Sweep) -> 0. */
    public static int nextBladeComboStage(int currentStage, long elapsedTicks) {
        if (elapsedTicks < 0L || elapsedTicks > BLADE_COMBO_RESET_TICKS) {
            return 0;
        }
        return (Math.max(0, currentStage) + 1) % 3;
    }

    /**
     * Finds every candidate along the 3D segment {@code start -> end} whose perpendicular distance
     * to the corridor axis is {@code <= halfWidth}, sorted from {@code start} toward {@code end}.
     * Used by both Quantum Blade Phase Dash and Railgun Plasma Wave.
     */
    public static List<TargetCandidate> findCorridorTargets(double sx, double sy, double sz,
                                                            double ex, double ey, double ez,
                                                            double halfWidth,
                                                            List<TargetCandidate> candidates) {
        if (candidates == null || candidates.isEmpty() || halfWidth <= 0.0D) {
            return List.of();
        }
        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;
        double lenSqr = dx * dx + dy * dy + dz * dz;
        if (lenSqr < 1.0E-6D) {
            return List.of();
        }
        double rSqr = halfWidth * halfWidth;

        record Hit(TargetCandidate c, double t) {}
        List<Hit> hits = new ArrayList<>();
        for (TargetCandidate c : candidates) {
            double px = c.x() - sx;
            double py = c.y() - sy;
            double pz = c.z() - sz;
            double t = (px * dx + py * dy + pz * dz) / lenSqr;
            if (t < -0.05D || t > 1.08D) {
                continue;
            }
            double clampedT = Math.max(0.0D, Math.min(1.0D, t));
            double cx = sx + dx * clampedT;
            double cy = sy + dy * clampedT;
            double cz = sz + dz * clampedT;
            double ox = c.x() - cx;
            double oy = c.y() - cy;
            double oz = c.z() - cz;
            if (ox * ox + oy * oy + oz * oz <= rSqr) {
                hits.add(new Hit(c, clampedT));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::t));
        List<TargetCandidate> out = new ArrayList<>(hits.size());
        for (Hit h : hits) {
            out.add(h.c());
        }
        return List.copyOf(out);
    }

    // -------------------------------------------------------------------------
    // Energy Bow: Draw Speed, Multi-Lock Selection & Gravity Singularity Pull
    // -------------------------------------------------------------------------

    public static int bowFullDrawTicks(boolean autoDraw) {
        return autoDraw ? BOW_DRAW_AUTO_TICKS : BOW_DRAW_BASE_TICKS;
    }

    public static float bowDrawRatio(int heldTicks, boolean autoDraw) {
        if (heldTicks <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) heldTicks / (float) bowFullDrawTicks(autoDraw));
    }

    /**
     * Sniper distance damage scaling for {@code energy_bow}:
     * {@code 0..15m -> 1.0x}, linear ramp from {@code 15m..60m} ({@code 30m -> 1.6x}, {@code 45m -> 2.2x}),
     * clamped to {@code 2.8x} at {@code >= 60m}.
     */
    public static float bowDistanceDamageMultiplier(double distanceMeters) {
        if (Double.isNaN(distanceMeters) || distanceMeters <= BOW_DISTANCE_BASE_THRESHOLD) {
            return 1.0F;
        }
        if (distanceMeters >= BOW_DISTANCE_MAX_THRESHOLD) {
            return BOW_DISTANCE_MAX_MULT;
        }
        float t = (float) ((distanceMeters - BOW_DISTANCE_BASE_THRESHOLD)
                / (BOW_DISTANCE_MAX_THRESHOLD - BOW_DISTANCE_BASE_THRESHOLD));
        return 1.0F + t * (BOW_DISTANCE_MAX_MULT - 1.0F);
    }

    /**
     * Smooth optical scope FOV multiplier as {@code energy_bow} is drawn ({@code 0.0 -> 1.0}):
     * smoothly zooms from {@code 1.0f} (1.0x) down to {@link #BOW_SCOPE_MAX_ZOOM_FOV_MULT}
     * ({@code 0.36f} ≈ 2.78x optical magnification).
     */
    public static float bowScopeFovMultiplier(float drawRatio) {
        float r = Math.max(0.0F, Math.min(1.0F, drawRatio));
        float eased = r * r * (3.0F - 2.0F * r);
        return 1.0F - eased * (1.0F - BOW_SCOPE_MAX_ZOOM_FOV_MULT);
    }

    /**
     * Selects up to {@code maxLocks} (4) hostile targets inside the player's sight cone,
     * sorted by crosshair alignment + distance for {@code att_multi_lock}.
     */
    public static List<TargetCandidate> selectMultiLockTargets(double eyeX, double eyeY, double eyeZ,
                                                               double dirX, double dirY, double dirZ,
                                                               double maxRange, double minDot,
                                                               int maxLocks,
                                                               List<TargetCandidate> candidates) {
        double dirLen = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (dirLen < 1.0E-6D || candidates == null || candidates.isEmpty() || maxLocks <= 0) {
            return List.of();
        }
        double nx = dirX / dirLen;
        double ny = dirY / dirLen;
        double nz = dirZ / dirLen;
        double maxDistSqr = maxRange * maxRange;

        record Scored(TargetCandidate c, double score) {}
        List<Scored> matches = new ArrayList<>();
        for (TargetCandidate c : candidates) {
            double dx = c.x() - eyeX;
            double dy = c.y() - eyeY;
            double dz = c.z() - eyeZ;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr < 1.0E-4D || distSqr > maxDistSqr) {
                continue;
            }
            double dist = Math.sqrt(distSqr);
            double dot = (dx * nx + dy * ny + dz * nz) / dist;
            if (dot < minDot) {
                continue;
            }
            double score = (1.0D - dot) * 40.0D + dist * 0.25D;
            matches.add(new Scored(c, score));
        }
        matches.sort(Comparator.comparingDouble(Scored::score));
        List<TargetCandidate> out = new ArrayList<>(Math.min(maxLocks, matches.size()));
        for (int i = 0; i < Math.min(maxLocks, matches.size()); i++) {
            out.add(matches.get(i).c());
        }
        return List.copyOf(out);
    }

    public record Vec3d(double x, double y, double z) {}

    /**
     * Computes the inward pull delta-velocity vector for a mob inside a {@code att_gravity_arrow}
     * singularity field. Strictly clamped so mobs pull smoothly toward the vortex center without
     * flinging out or producing NaN at zero distance.
     */
    public static Vec3d gravityPullVector(double centerX, double centerY, double centerZ,
                                          double mobX, double mobY, double mobZ,
                                          double radius) {
        double dx = centerX - mobX;
        double dy = (centerY + 0.35D) - mobY;
        double dz = centerZ - mobZ;
        double distSqr = dx * dx + dy * dy + dz * dz;
        if (distSqr < 0.04D || distSqr > radius * radius) {
            return new Vec3d(0.0D, 0.0D, 0.0D);
        }
        double dist = Math.sqrt(distSqr);
        // Stronger pull slightly outside center, tapering smoothly near edge
        double falloff = 1.0D - (dist / radius) * 0.45D;
        double strength = Math.min(GRAVITY_MAX_PULL, 0.22D * falloff + 0.06D);
        return new Vec3d(
                (dx / dist) * strength,
                Math.max(-0.12D, Math.min(0.18D, (dy / dist) * strength)),
                (dz / dist) * strength
        );
    }
}

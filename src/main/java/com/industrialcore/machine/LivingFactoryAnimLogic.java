package com.industrialcore.machine;

/**
 * Phase 109–111: Pure-Java animation curves and lightweight culling/throttling guards for the
 * "Living Sci-Fi Industrial Plant" hybrid visual &amp; motion-synced audio system.
 *
 * <p>Kept strictly free of Minecraft client/server classes so unit tests can verify every curve,
 * stroke profile, distance bound, and particle-throttle rule directly on a plain JVM.</p>
 */
public final class LivingFactoryAnimLogic {
    /** Distance-culling radius (blocks) for Tier 2 hero BlockEntityRenderers (target: 32..40). */
    public static final int BER_VIEW_DISTANCE = 36;

    /** Maximum distance (blocks) from the player at which Tier 3 factory particles spawn. */
    public static final double PARTICLE_MAX_DISTANCE = 24.0D;
    public static final double PARTICLE_MAX_DIST_SQ = PARTICLE_MAX_DISTANCE * PARTICLE_MAX_DISTANCE;

    private LivingFactoryAnimLogic() {}

    public static boolean shouldRenderBer(double distSq, int viewDistance) {
        if (viewDistance <= 0 || distSq < 0.0D || Double.isNaN(distSq)) {
            return false;
        }
        double maxSq = (double) viewDistance * (double) viewDistance;
        return distSq <= maxSq;
    }

    public static boolean shouldSpawnParticle(double distSq, boolean isMinimalParticles) {
        if (isMinimalParticles || distSq < 0.0D || Double.isNaN(distSq)) {
            return false;
        }
        return distSq <= PARTICLE_MAX_DIST_SQ;
    }

    public static float wrapDegrees360(float deg) {
        float mod = deg % 360.0F;
        return mod < 0.0F ? mod + 360.0F : mod;
    }

    public static float cyclePhase(float timeSec, float periodSec) {
        if (periodSec <= 0.0F) {
            return 0.0F;
        }
        return FactoryAudioLogic.wrap01(timeSec / periodSec);
    }

    // -------------------------------------------------------------------------
    // Phase 109 Hero BERs (1..4)
    // -------------------------------------------------------------------------

    public static float singularityOuterRingDeg(float timeSec) {
        return wrapDegrees360(timeSec * 54.0F);
    }

    public static float singularityInnerRingDeg(float timeSec) {
        return wrapDegrees360(-timeSec * 88.0F);
    }

    public static float singularityPrecessionDeg(float timeSec) {
        return wrapDegrees360(timeSec * 34.0F);
    }

    public static float singularityCorePulse(float timeSec) {
        return 0.98F + 0.14F * (float) Math.sin(timeSec * 4.2D);
    }

    public static float nuclearCoreBobY(float timeSec) {
        return 0.11F * (float) Math.sin(timeSec * 2.2D);
    }

    public static float nuclearGlowPulse(float timeSec) {
        return 0.84F + 0.16F * (float) Math.sin(timeSec * 3.6D);
    }

    public static float mobFanBladeDeg(float timeSec) {
        return wrapDegrees360(timeSec * 720.0F);
    }

    public static float crusherRollerPrimaryDeg(float timeSec) {
        return wrapDegrees360(timeSec * 260.0F);
    }

    public static float crusherRollerCounterDeg(float timeSec) {
        return wrapDegrees360(-timeSec * 260.0F);
    }

    // -------------------------------------------------------------------------
    // Phase 110 Expanded Hero BERs (5..10)
    // -------------------------------------------------------------------------

    public static float windTurbineBladeDeg(float timeSec, int windOutput) {
        if (windOutput <= 0) {
            return 0.0F;
        }
        float speedDegPerSec = Math.min(360.0F, 75.0F + windOutput * 1.4F);
        return wrapDegrees360(timeSec * speedDegPerSec);
    }

    public static float flywheelRotorDeg(float timeSec, float chargeRatio) {
        if (chargeRatio <= 0.0F || Float.isNaN(chargeRatio)) {
            return 0.0F;
        }
        float clamped = Math.min(1.0F, chargeRatio);
        float speedDegPerSec = 90.0F + clamped * 450.0F;
        return wrapDegrees360(timeSec * speedDegPerSec);
    }

    public static float rotaryDrillHeadDeg(float timeSec) {
        return wrapDegrees360(timeSec * 640.0F);
    }

    public static float autoFisherReelDeg(float timeSec) {
        return wrapDegrees360(timeSec * 190.0F);
    }

    public static float beaconRingDeg(float timeSec) {
        return wrapDegrees360(timeSec * 42.0F);
    }

    public static float beaconRingBobY(float timeSec) {
        return 0.065F * (float) Math.sin(timeSec * 2.4D);
    }

    public static float chunkLoaderCoreDeg(float timeSec) {
        return wrapDegrees360(timeSec * 36.0F);
    }

    public static float chunkLoaderBobY(float timeSec) {
        return 0.055F * (float) Math.sin(timeSec * 2.0D);
    }

    // -------------------------------------------------------------------------
    // Phase 111 Complete Physical Motion Curves (19 groups)
    // -------------------------------------------------------------------------

    /**
     * Hydraulic Metal Press stroke progress in {@code [0, 1]} (0 = top retracted, 1 = bottom stamp).
     * Period = 1.25s: rapid powered descent (0..0.38), brief dwell stamp (0.38..0.48), smooth retract (0.48..1.0).
     */
    public static final float METAL_PRESS_PERIOD_SEC = 1.25F;
    public static final float METAL_PRESS_IMPACT_PHASE = 0.38F;

    public static float metalPressStroke01(float timeSec) {
        float p = cyclePhase(timeSec, METAL_PRESS_PERIOD_SEC);
        if (p < 0.38F) {
            float u = p / 0.38F;
            return u * u; // accelerating hydraulic descent
        }
        if (p < 0.48F) {
            return 1.0F; // crisp dwell at bottom dead center
        }
        float u = (p - 0.48F) / 0.52F;
        return 1.0F - (0.5F - 0.5F * (float) Math.cos(Math.PI * u)); // smooth cosine retract
    }

    /** True during the brief bottom-dead-center stamp flash window. */
    public static boolean metalPressIsStamping(float timeSec) {
        float p = cyclePhase(timeSec, METAL_PRESS_PERIOD_SEC);
        return p >= 0.36F && p <= 0.50F;
    }

    /** Precision Assembler weld-head horizontal scan offset in {@code [-0.26, +0.26]} blocks. */
    public static final float ASSEMBLER_PERIOD_SEC = 1.60F;

    public static float assemblerHeadOffsetX(float timeSec) {
        return 0.26F * (float) Math.sin(timeSec * (Math.PI * 2.0D / ASSEMBLER_PERIOD_SEC));
    }

    /**
     * Quantum Compressor heavy overhead ram stroke in {@code [0, 1]} (period 1.50s, pulse peak at 0.42).
     */
    public static final float COMPRESSOR_PERIOD_SEC = 1.50F;
    public static final float COMPRESSOR_PEAK_PHASE = 0.42F;

    public static float quantumCompressorStroke01(float timeSec) {
        float p = cyclePhase(timeSec, COMPRESSOR_PERIOD_SEC);
        if (p < 0.42F) {
            float u = p / 0.42F;
            return u * u * u; // heavy punch down
        }
        if (p < 0.54F) {
            return 1.0F;
        }
        float u = (p - 0.54F) / 0.46F;
        return 1.0F - u;
    }

    /** Reconstructor holo-ring angle & convergence radius pulse in {@code [0.16, 0.30]}. */
    public static float reconstructorRingDeg(float timeSec) {
        return wrapDegrees360(timeSec * 82.0F);
    }

    public static float reconstructorRadius(float timeSec) {
        return 0.23F + 0.07F * (float) Math.sin(timeSec * 3.8D);
    }

    /** Quarry / Large Quarry emitter ring rotation & downward laser pulse intensity {@code [0.55, 1.0]}. */
    public static float quarryRingDeg(float timeSec) {
        return wrapDegrees360(timeSec * 140.0F);
    }

    public static float quarryLaserPulse(float timeSec) {
        return 0.78F + 0.22F * (float) Math.sin(timeSec * 9.0D);
    }

    /** Ore Synthesizer top orbital energy ring rotation (96 deg/sec). */
    public static float oreSynthesizerRingDeg(float timeSec) {
        return wrapDegrees360(timeSec * 96.0F);
    }

    /** Phase Reactor tilted purple phase ring rotation (72 deg/sec). */
    public static float phaseReactorRingDeg(float timeSec) {
        return wrapDegrees360(timeSec * 72.0F);
    }

    /** Matter Amplifier oscillating pulse offset between left/right side capacitors in {@code [-0.46, +0.46]}. */
    public static float matterAmplifierPulseX(float timeSec) {
        return 0.46F * (float) Math.sin(timeSec * 5.2D);
    }

    /** Crop Harvester fast reciprocating mower blade offset in {@code [-0.14, +0.14]}. */
    public static float cropHarvesterBladeX(float timeSec) {
        return 0.14F * (float) Math.sin(timeSec * 24.0D);
    }

    /** Tree Harvester rapid chainsaw bar reciprocation + micro-vibration. */
    public static float treeHarvesterSawX(float timeSec) {
        return 0.11F * (float) Math.sin(timeSec * 30.0D);
    }

    /** Growth Accelerator open-cage core spin & vertical pulse in {@code [-0.07, +0.07]}. */
    public static float growthCoreDeg(float timeSec) {
        return wrapDegrees360(timeSec * 68.0F);
    }

    public static float growthCoreBobY(float timeSec) {
        return 0.07F * (float) Math.sin(timeSec * 3.2D);
    }

    /** Item Vacuum swirling hopper vortex angle & converging radius {@code [0.14, 0.34]}. */
    public static float vacuumVortexDeg(float timeSec) {
        return wrapDegrees360(-timeSec * 220.0F);
    }

    public static float vacuumVortexRadius(float timeSec) {
        float p = cyclePhase(timeSec, 0.90F);
        return 0.34F - p * 0.20F; // converges inward toward throat
    }

    /** XP Collector floating emerald orb spin & gentle bob. */
    public static float xpOrbDeg(float timeSec) {
        return wrapDegrees360(timeSec * 58.0F);
    }

    public static float xpOrbBobY(float timeSec) {
        return 0.06F * (float) Math.sin(timeSec * 2.8D);
    }

    /** Laser Turret calm radar-sweep yaw offset in {@code [-42, +42]} degrees. */
    public static float laserTurretSweepYawDeg(float timeSec) {
        return 42.0F * (float) Math.sin(timeSec * 1.15D);
    }

    /**
     * Phase 113: Exact turret dome horizontal yaw (degrees) so local {@code -Z} (barrel forward)
     * points toward {@code (dx, dz)} in world space.
     * Matches OpenGL {@code Axis.YP.rotationDegrees(yaw)} where {@code -Z} rotates to
     * {@code (-sin(yaw), -cos(yaw))}.
     */
    public static float turretAimYawDeg(double dx, double dz) {
        if (Math.abs(dx) < 1.0E-6D && Math.abs(dz) < 1.0E-6D) {
            return 0.0F;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, -dz));
    }

    /**
     * Phase 113: Exact turret barrel pitch (degrees, clamped {@code [-55, +65]}) so local {@code -Z}
     * tilts toward {@code (dx, dy, dz)}. Positive pitch tilts barrels UP ({@code +Y}).
     */
    public static float turretAimPitchDeg(double dx, double dy, double dz) {
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0E-6D && Math.abs(dy) < 1.0E-6D) {
            return 0.0F;
        }
        float raw = (float) Math.toDegrees(Math.atan2(dy, Math.max(1.0E-6D, horiz)));
        return Math.max(-55.0F, Math.min(65.0F, raw));
    }

    /** Shortest-arc angular interpolation (degrees) for smooth turret tracking. */
    public static float lerpAngleDeg(float currentDeg, float targetDeg, float alpha) {
        float diff = ((targetDeg - currentDeg + 540.0F) % 360.0F) - 180.0F;
        float t = Math.max(0.0F, Math.min(1.0F, alpha));
        return currentDeg + diff * t;
    }

    /**
     * Phase 116: Stateful turret aim tracker that retains the last engaged target orientation
     * ({@code lastAimYaw}, {@code lastAimPitch}) instead of resetting to the block's default
     * facing after eliminating a target.
     */
    public static final class TurretAimState {
        public float yaw;
        public float pitch;
        public float lastAimYaw;
        public float lastAimPitch;
        public boolean initialized;
        public boolean hasEverLocked;
    }

    /** Subtle idle micro-sweep amplitude (degrees) around {@code lastAimYaw} after losing target. */
    public static final float TURRET_HOLD_SWEEP_DEG = 5.5F;

    /**
     * Advances turret yaw/pitch smoothly while strictly retaining {@code lastAimYaw}/{@code lastAimPitch}
     * once any target has been engaged (never snapping back to {@code defaultFacingYaw}).
     */
    public static void stepTurretAim(TurretAimState state, float defaultFacingYaw,
                                     boolean locked, float targetYaw, float targetPitch, float timeSec) {
        if (!state.initialized) {
            state.yaw = defaultFacingYaw;
            state.pitch = 0.0F;
            state.lastAimYaw = defaultFacingYaw;
            state.lastAimPitch = 0.0F;
            state.initialized = true;
        }
        if (locked) {
            state.hasEverLocked = true;
            state.lastAimYaw = targetYaw;
            state.lastAimPitch = targetPitch;
            state.yaw = lerpAngleDeg(state.yaw, targetYaw, 0.38F);
            state.pitch = state.pitch + (targetPitch - state.pitch) * 0.38F;
            return;
        }
        float desiredYaw = state.hasEverLocked
                ? state.lastAimYaw + TURRET_HOLD_SWEEP_DEG * (float) Math.sin(timeSec * 0.90D)
                : defaultFacingYaw + 14.0F * (float) Math.sin(timeSec * 0.90D);
        float desiredPitch = state.hasEverLocked ? state.lastAimPitch : 0.0F;
        state.yaw = lerpAngleDeg(state.yaw, desiredYaw, 0.12F);
        state.pitch = state.pitch + (desiredPitch - state.pitch) * 0.12F;
    }

    /** Fluid Pump dual-cylinder rhythmic piston offset in {@code [0, 0.14]} blocks (period 0.85s). */
    public static final float FLUID_PUMP_PERIOD_SEC = 0.85F;

    public static float fluidPumpPistonOffsetY(float timeSec, boolean phaseShiftHalf) {
        double angle = timeSec * (Math.PI * 2.0D / FLUID_PUMP_PERIOD_SEC) + (phaseShiftHalf ? Math.PI : 0.0D);
        return 0.07F + 0.07F * (float) Math.sin(angle);
    }

    /** Auto Builder twin projector scan sweep angle in {@code [-28, +28]} degrees. */
    public static float autoBuilderScanDeg(float timeSec) {
        return 28.0F * (float) Math.sin(timeSec * 2.6D);
    }

    /** Hydro Generator water turbine impeller rotation (210 deg/sec when running). */
    public static float hydroTurbineDeg(float timeSec, boolean running) {
        return running ? wrapDegrees360(timeSec * 210.0F) : 0.0F;
    }

    /** Pressure Recovery exhaust turbine blade rotation (340 deg/sec when running). */
    public static float pressureTurbineDeg(float timeSec, boolean running) {
        return running ? wrapDegrees360(timeSec * 340.0F) : 0.0F;
    }

    /** Quantum Transporters (4 tiers) floating chamber core & ring rotation. */
    public static float transporterRingDeg(float timeSec) {
        return wrapDegrees360(timeSec * 78.0F);
    }
}

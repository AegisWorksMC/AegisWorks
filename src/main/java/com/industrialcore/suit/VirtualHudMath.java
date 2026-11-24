package com.industrialcore.suit;

/**
 * Phase 98: Pure-Java math for the 3D Tactical AR Hologram HUD.
 *
 * <p>Covers full-set detection, smooth clamped camera-parallax inertia, dynamic HP/energy colors,
 * boot-sequence easing, and compact FE formatting.</p>
 */
public final class VirtualHudMath {
    /** Full-set hologram boot-up duration in milliseconds (1.2s). */
    public static final long BOOT_DURATION_MS = 1_200L;

    /** Comfort-clamped horizontal and vertical parallax amplitudes in GUI pixels. */
    public static final float MAX_PARALLAX_X = 12.0F;
    public static final float MAX_PARALLAX_Y = 7.0F;

    /** Phase 100: 3-layer depth parallax multipliers (back shadow/glass, mid frame, front data). */
    public static final float DEPTH_BACK = 0.60F;
    public static final float DEPTH_MID = 1.00F;
    public static final float DEPTH_FRONT = 1.35F;
    public static final float MAX_LAYER_OFFSET_X = 15.0F;
    public static final float MAX_LAYER_OFFSET_Y = 9.0F;

    /** Phase 100 / 102.5: Virtual Hotbar, Slim EXP Bar, &amp; Selected Item Name non-overlapping layout. */
    public static final int HOTBAR_WIDTH = 186;
    public static final int HOTBAR_HEIGHT = 24;
    public static final int HOTBAR_BOTTOM_MARGIN = 1;
    public static final int HOTBAR_SLOT_STEP = 20;
    public static final int OFFHAND_WIDTH = 24;
    public static final int OFFHAND_GAP = 4;
    public static final int EXP_BAR_HEIGHT = 14;
    public static final int EXP_HOTBAR_GAP = 3;
    public static final int ITEM_NAME_EXP_GAP = 10;
    public static final int ITEM_NAME_HEIGHT = 9;
    public static final long ITEM_NAME_HOLD_MS = 2_200L;
    public static final long ITEM_NAME_FADE_MS = 500L;

    /** Phase 102.5: Tactical Left Self Card visual hierarchy (Primary Shield+HP vs Slim Food/O2). */
    public static final int SELF_CARD_WIDTH = 160;
    public static final int SELF_CARD_HEIGHT = 56;
    public static final int SELF_CARD_HEIGHT_O2 = 65;
    public static final int SELF_FACE_SIZE = 24;
    public static final int SHIELD_BAR_Y = 24;
    public static final int SHIELD_BAR_HEIGHT = 7;
    public static final int HP_BAR_Y = 33;
    public static final int HP_BAR_HEIGHT = 7;
    public static final int FOOD_BAR_Y = 48;
    public static final int FOOD_BAR_HEIGHT = 3;
    public static final int O2_BAR_Y = 57;
    public static final int O2_BAR_HEIGHT = 3;
    public static final int SQUAD_CARD_WIDTH = 152;
    public static final int SQUAD_CARD_HEIGHT = 32;
    public static final int SQUAD_CARD_GAP = 4;

    /** Right-column Weapon Tactical Card + stacked Modules & Target Scan geometry. */
    public static final int RIGHT_MARGIN = 12;
    public static final int BOTTOM_MARGIN = 12;
    public static final int RIGHT_PANEL_GAP = 6;
    public static final int WEAPON_CARD_WIDTH = 152;
    public static final int WEAPON_CARD_HEIGHT_LASER = 44;
    public static final int WEAPON_CARD_HEIGHT_COMPACT = 34;
    public static final int RIGHT_MODULES_WIDTH = 136;
    public static final int RIGHT_MODULES_HEIGHT = 50;
    public static final int RIGHT_SCAN_WIDTH = 136;
    public static final int RIGHT_SCAN_HEIGHT = 40;
    public static final int CROSSHAIR_SAFE_HALF_SIZE = 32;

    /** Pulse Laser right-side crosshair semicircle heat arc geometry (r=15px, -65°..+65°). */
    public static final float LASER_ARC_RADIUS = 15.0F;
    public static final float LASER_ARC_START_DEG = -65.0F;
    public static final float LASER_ARC_END_DEG = 65.0F;
    public static final float LASER_ARC_THICKNESS = 2.5F;

    public record ArcPoint(float x, float y, float angleDeg) {}

    public record HudRect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean overlaps(HudRect other) {
            return this.x < other.right() && this.right() > other.x
                    && this.y < other.bottom() && this.bottom() > other.y;
        }
    }

    /** Phase 100: Virtual Inventory hologram unfold animation & background parallax limits. */
    public static final long INV_OPEN_DURATION_MS = 150L;
    public static final float INV_MAX_PARALLAX_X = 5.0F;
    public static final float INV_MAX_PARALLAX_Y = 3.5F;

    /** Dynamic HP color stops (Emerald -> Amber -> Crimson). */
    public static final int HP_EMERALD = 0xFF38F2A6;
    public static final int HP_AMBER = 0xFFFFC94D;
    public static final int HP_CRIMSON = 0xFFFF4D6A;

    /** Suit energy color stops (Cyan -> Gold -> Crimson). */
    public static final int ENERGY_CYAN = 0xFF42F5FF;
    public static final int ENERGY_GOLD = 0xFFFFD166;
    public static final int ENERGY_LOW = 0xFFFF4D6A;

    private VirtualHudMath() {}

    /** Returns true iff all 4 armor slots hold Virtual Aegis pieces. */
    public static boolean isFullSet(boolean helmet, boolean chest, boolean legs, boolean boots) {
        return helmet && chest && legs && boots;
    }

    /**
     * Low-pass damped parallax step: smoothly pulls {@code current} toward {@code targetOffset}
     * (derived from angular camera velocity + lateral movement) and clamps strictly to
     * {@code [-maxAbs, +maxAbs]} to avoid motion sickness.
     */
    public static float stepParallax(float current, float targetOffset, float smoothing, float maxAbs) {
        float limit = Math.max(0.0F, maxAbs);
        float clampedTarget = Math.max(-limit, Math.min(limit, targetOffset));
        float alpha = Math.max(0.0F, Math.min(1.0F, smoothing));
        float next = current + (clampedTarget - current) * alpha;
        return Math.max(-limit, Math.min(limit, next));
    }

    /** Computes shortest signed angle delta in degrees in {@code [-180, 180]}. */
    public static float wrapAngleDelta(float currentDeg, float previousDeg) {
        float delta = (currentDeg - previousDeg) % 360.0F;
        if (delta > 180.0F) {
            delta -= 360.0F;
        } else if (delta < -180.0F) {
            delta += 360.0F;
        }
        return delta;
    }

    /** Smoothly eases the 1.2s AR visor boot-up progress into {@code [0.0, 1.0]}. */
    public static double bootProgress(long elapsedMs) {
        if (elapsedMs <= 0L) {
            return 0.0D;
        }
        if (elapsedMs >= BOOT_DURATION_MS) {
            return 1.0D;
        }
        double t = (double) elapsedMs / (double) BOOT_DURATION_MS;
        double inv = 1.0D - t;
        return 1.0D - inv * inv * inv;
    }

    /** Returns dynamic HP color for ratio in {@code [0.0, 1.0]}. */
    public static int hpColor(float ratio) {
        float r = Math.max(0.0F, Math.min(1.0F, ratio));
        if (r >= 0.60F) {
            return HP_EMERALD;
        }
        if (r >= 0.30F) {
            float t = (r - 0.30F) / 0.30F;
            return blend(HP_AMBER, HP_EMERALD, t);
        }
        float t = r / 0.30F;
        return blend(HP_CRIMSON, HP_AMBER, t);
    }

    /** Returns AR HUD suit energy bar color for ratio in {@code [0.0, 1.0]}. */
    public static int energyColor(double ratio) {
        double r = Math.max(0.0D, Math.min(1.0D, ratio));
        if (r >= 0.50D) {
            return ENERGY_CYAN;
        }
        if (r >= 0.15D) {
            double t = (r - 0.15D) / 0.35D;
            return blend(ENERGY_GOLD, ENERGY_CYAN, (float) t);
        }
        double t = r / 0.15D;
        return blend(ENERGY_LOW, ENERGY_GOLD, (float) t);
    }

    /** Compact FE readout for AR HUD (e.g. {@code 40.0M}, {@code 850.5K}, {@code 920}). */
    public static String formatCompactFe(long fe) {
        long v = Math.max(0L, fe);
        if (v >= 1_000_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.2fG", v / 1_000_000_000.0D);
        }
        if (v >= 1_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fM", v / 1_000_000.0D);
        }
        if (v >= 1_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fK", v / 1_000.0D);
        }
        return Long.toString(v);
    }

    /** Linear ARGB blend helper (pure Java). */
    public static int blend(int c1, int c2, float t) {
        float k = Math.max(0.0F, Math.min(1.0F, t));
        int a = Math.round(((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * k);
        int r = Math.round(((c1 >>> 16) & 0xFF) + (((c2 >>> 16) & 0xFF) - ((c1 >>> 16) & 0xFF)) * k);
        int g = Math.round(((c1 >>> 8) & 0xFF) + (((c2 >>> 8) & 0xFF) - ((c1 >>> 8) & 0xFF)) * k);
        int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // -------------------------------------------------------------------------
    // Phase 100: 3-Layer Depth Parallax, Virtual Hotbar, & Virtual Inventory
    // -------------------------------------------------------------------------

    /** Computes a single depth layer's parallax offset, strictly clamped to {@code [-hardCap, +hardCap]}. */
    public static float layerOffset(float baseParallax, float depthMultiplier, float hardCap) {
        float cap = Math.max(0.0F, hardCap);
        float raw = baseParallax * depthMultiplier;
        return Math.max(-cap, Math.min(cap, raw));
    }

    /** Smoothly glides the Virtual Hotbar selection frame toward {@code targetSlot} (0..8). */
    public static float stepHotbarSelector(float currentPos, int targetSlot, float alpha) {
        int clampedTarget = Math.max(0, Math.min(8, targetSlot));
        float safeCur = Math.max(0.0F, Math.min(8.0F, currentPos));
        float k = Math.max(0.0F, Math.min(1.0F, alpha));
        float next = safeCur + (clampedTarget - safeCur) * k;
        if (Math.abs(next - clampedTarget) < 0.005F) {
            return (float) clampedTarget;
        }
        return Math.max(0.0F, Math.min(8.0F, next));
    }

    /** Top Y coordinate of the Virtual Hotbar dock. */
    public static int hotbarTop(int screenH) {
        return screenH - HOTBAR_BOTTOM_MARGIN - HOTBAR_HEIGHT;
    }

    /** Bottom Y coordinate of the Slim EXP & Coordinate bar (always strictly above Virtual Hotbar). */
    public static int expBarBottom(int screenH) {
        return hotbarTop(screenH) - EXP_HOTBAR_GAP;
    }

    /** Top Y coordinate of the Slim EXP & Coordinate bar. */
    public static int expBarTop(int screenH) {
        return expBarBottom(screenH) - EXP_BAR_HEIGHT;
    }

    /** Bottom Y coordinate of the selected hotbar item name (strictly >= 10px above EXP bar). */
    public static int selectedItemNameBottom(int screenH) {
        return expBarTop(screenH) - ITEM_NAME_EXP_GAP;
    }

    /** Top Y coordinate of the selected hotbar item name. */
    public static int selectedItemNameTop(int screenH) {
        return selectedItemNameBottom(screenH) - ITEM_NAME_HEIGHT;
    }

    /** Tight combat-vitals gap between Shield bar bottom and HP bar top (2px). */
    public static int shieldHpGap() {
        return HP_BAR_Y - (SHIELD_BAR_Y + SHIELD_BAR_HEIGHT);
    }

    /** Visual separation gap between HP bar bottom and auxiliary Food bar top (8px > shieldHpGap). */
    public static int hpFoodGap() {
        return FOOD_BAR_Y - (HP_BAR_Y + HP_BAR_HEIGHT);
    }

    /** Top Y coordinate of the {@code mateIndex}-th (0..1) nearby squad teammate card below Self Card. */
    public static int squadCardTop(int selfTop, int selfHeight, int mateIndex) {
        int idx = Math.max(0, mateIndex);
        return selfTop + selfHeight + SQUAD_CARD_GAP + idx * (SQUAD_CARD_HEIGHT + SQUAD_CARD_GAP);
    }

    /** Bounding rectangle of the bottom Virtual Hotbar dock. */
    public static HudRect hotbarRect(int screenW, int screenH) {
        return new HudRect((screenW - HOTBAR_WIDTH) / 2, hotbarTop(screenH), HOTBAR_WIDTH, HOTBAR_HEIGHT);
    }

    /** Bounding rectangle of the Slim EXP &amp; Coordinate bar above the Virtual Hotbar. */
    public static HudRect expBarRect(int screenW, int screenH) {
        return new HudRect((screenW - HOTBAR_WIDTH) / 2, expBarTop(screenH), HOTBAR_WIDTH, EXP_BAR_HEIGHT);
    }

    /** Protected center crosshair rectangle that weapon tactical panels must never overlap. */
    public static HudRect crosshairSafeZone(int screenW, int screenH) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        return new HudRect(cx - CROSSHAIR_SAFE_HALF_SIZE, cy - CROSSHAIR_SAFE_HALF_SIZE,
                CROSSHAIR_SAFE_HALF_SIZE * 2, CROSSHAIR_SAFE_HALF_SIZE * 2);
    }

    /**
     * Bottom-Right bounding rectangle for the active Electric Weapon tactical card.
     * Automatically clears the Virtual Hotbar &amp; EXP bar even on narrow 854x480 screens while
     * staying completely outside the center crosshair safe zone.
     */
    public static HudRect weaponCardRect(int screenW, int screenH, boolean isPulseLaser) {
        int w = WEAPON_CARD_WIDTH;
        int h = isPulseLaser ? WEAPON_CARD_HEIGHT_LASER : WEAPON_CARD_HEIGHT_COMPACT;
        int x = screenW - RIGHT_MARGIN - w;
        int hotbarRight = (screenW + HOTBAR_WIDTH) / 2;
        int bottomY = (x >= hotbarRight + 6)
                ? (screenH - BOTTOM_MARGIN)
                : Math.min(screenH - BOTTOM_MARGIN, expBarTop(screenH) - 6);
        int y = Math.max(4, bottomY - h);
        return new HudRect(x, y, w, h);
    }

    /** Bounding rectangle for the Active Modules panel stacked cleanly above the Weapon Card. */
    public static HudRect rightModulesRect(int screenW, int screenH,
                                           boolean holdingWeapon, boolean isPulseLaser) {
        int w = RIGHT_MODULES_WIDTH;
        int h = RIGHT_MODULES_HEIGHT;
        int x = screenW - RIGHT_MARGIN - w;
        int bottomY;
        if (holdingWeapon) {
            bottomY = weaponCardRect(screenW, screenH, isPulseLaser).y() - RIGHT_PANEL_GAP;
        } else {
            int hotbarRight = (screenW + HOTBAR_WIDTH) / 2;
            bottomY = (x >= hotbarRight + 6)
                    ? (screenH - BOTTOM_MARGIN)
                    : Math.min(screenH - BOTTOM_MARGIN, expBarTop(screenH) - 6);
        }
        int y = Math.max(4, bottomY - h);
        return new HudRect(x, y, w, h);
    }

    /** Bounding rectangle for the Target Scan card stacked cleanly above the Active Modules panel. */
    public static HudRect rightScanRect(int screenW, int screenH,
                                        boolean holdingWeapon, boolean isPulseLaser) {
        int w = RIGHT_SCAN_WIDTH;
        int h = RIGHT_SCAN_HEIGHT;
        int x = screenW - RIGHT_MARGIN - w;
        HudRect mods = rightModulesRect(screenW, screenH, holdingWeapon, isPulseLaser);
        int y = Math.max(4, mods.y() - RIGHT_PANEL_GAP - h);
        return new HudRect(x, y, w, h);
    }

    /**
     * Computes a point along the Pulse Laser's right-side crosshair semicircle heat arc.
     * {@code fillFraction = 0.0} is the bottom ({@code +65°}); {@code 1.0} is the top ({@code -65°}).
     */
    public static ArcPoint laserHeatArcPoint(int cx, int cy, float radius, float fillFraction) {
        float t = Math.max(0.0F, Math.min(1.0F, fillFraction));
        float angleDeg = LASER_ARC_END_DEG + (LASER_ARC_START_DEG - LASER_ARC_END_DEG) * t;
        double rad = Math.toRadians(angleDeg);
        float px = cx + (float) (Math.cos(rad) * radius);
        float py = cy + (float) (Math.sin(rad) * radius);
        return new ArcPoint(px, py, angleDeg);
    }

    /** Dynamic heat color for the crosshair semicircle arc: Cyan -> Amber -> Danger Red (flashing if overheated). */
    public static int laserHeatArcColor(float heatRatio, boolean overheated, long nowMs) {
        if (overheated) {
            boolean bright = ((nowMs / 110L) & 1L) == 0L;
            return bright ? 0xFFFF2848 : 0xAA771022;
        }
        float r = Math.max(0.0F, Math.min(1.0F, heatRatio));
        if (r < 0.55F) {
            return blend(0xFF3CF2FF, 0xFF7BE8FF, r / 0.55F);
        }
        if (r < 0.85F) {
            return blend(0xFF3CF2FF, 0xFFFFB83D, (r - 0.55F) / 0.30F);
        }
        return blend(0xFFFFB83D, 0xFFFF3855, (r - 0.85F) / 0.15F);
    }

    /** Alpha [0..1] for selected hotbar item name fade-out (full for 2.2s, fades over 0.5s). */
    public static float selectedItemNameAlpha(long elapsedMs) {
        if (elapsedMs < 0L) {
            return 0.0F;
        }
        if (elapsedMs <= ITEM_NAME_HOLD_MS) {
            return 1.0F;
        }
        long fade = elapsedMs - ITEM_NAME_HOLD_MS;
        if (fade >= ITEM_NAME_FADE_MS) {
            return 0.0F;
        }
        return 1.0F - (float) fade / (float) ITEM_NAME_FADE_MS;
    }

    /** Ease-out cubic unfold progress ({@code 0.0..1.0}) for the 150ms Virtual Inventory open animation. */
    public static double inventoryOpenProgress(long elapsedMs) {
        if (elapsedMs <= 0L) {
            return 0.0D;
        }
        if (elapsedMs >= INV_OPEN_DURATION_MS) {
            return 1.0D;
        }
        double t = (double) elapsedMs / (double) INV_OPEN_DURATION_MS;
        double inv = 1.0D - t;
        return 1.0D - inv * inv * inv;
    }

    /**
     * Mouse-driven subtle 3D parallax for Virtual Inventory background layers only.
     * Strictly clamped to {@code [-maxOffset, +maxOffset]}.
     */
    public static float inventoryBackParallax(double mouseCoord, double centerCoord,
                                              double halfSpan, float maxOffset) {
        float limit = Math.max(0.0F, maxOffset);
        if (halfSpan <= 1.0D || limit <= 0.0F) {
            return 0.0F;
        }
        double norm = Math.max(-1.0D, Math.min(1.0D, (mouseCoord - centerCoord) / halfSpan));
        return (float) (-norm * limit);
    }
}

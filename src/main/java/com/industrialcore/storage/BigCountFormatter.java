package com.industrialcore.storage;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Phase 89: Mushroom-Legend style inflation count formatter & tier-scaled luxury color palette.
 *
 * <p>Supports arbitrary {@link BigInteger} counts well past 10^68 (無量大数) in both English
 * ({@code K/M/B/T/Qa/.../Uvg/Muryo}) and Japanese ({@code 万/億/兆/京/.../無量大数}) modes, with
 * animated prism and golden-rainbow shimmer colors for high-magnitude counts.</p>
 */
public final class BigCountFormatter {
    public static final BigInteger THOUSAND = BigInteger.valueOf(1_000L);
    public static final BigInteger TEN_THOUSAND = BigInteger.valueOf(10_000L);
    public static final BigInteger MURYO_THRESHOLD = BigInteger.TEN.pow(68);

    public enum UnitMode {
        ENGLISH,
        JAPANESE;

        public UnitMode toggle() {
            return this == ENGLISH ? JAPANESE : ENGLISH;
        }
    }

    public enum ColorTier {
        PLAIN(0xFFFFFFFF, false, false),
        CYAN(0xFF55FFFF, false, false),
        EMERALD(0xFF55FF88, false, false),
        GOLD(0xFFFFD700, false, false),
        MAGENTA(0xFFD966FF, false, false),
        SUNSET(0xFFFF8844, false, false),
        PRISM(0xFF88EEFF, true, false),
        MURYO(0xFFFFE566, true, true);

        private final int baseArgb;
        private final boolean prism;
        private final boolean muryo;

        ColorTier(int baseArgb, boolean prism, boolean muryo) {
            this.baseArgb = baseArgb;
            this.prism = prism;
            this.muryo = muryo;
        }

        public int baseArgb() {
            return baseArgb;
        }

        public boolean isPrism() {
            return prism;
        }

        public boolean isMuryo() {
            return muryo;
        }
    }

    // 10^(3*i) for i = 1..22 (K .. Uvg)
    private static final String[] EN_UNITS = {
            "", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No",
            "Dc", "Ud", "Dd", "Td", "Qad", "Qid", "Sxd", "Spd", "Ocd", "Nod",
            "Vg", "Uvg"
    };

    // 10^(4*i) for i = 1..17 (万 .. 無量大数)
    private static final String[] JP_UNITS = {
            "", "万", "億", "兆", "京", "垓", "𥝱", "穣", "溝", "澗", "正",
            "載", "極", "恒河沙", "阿僧祇", "那由他", "不可思議", "無量大数"
    };

    // Short slot-overlay labels for long Japanese names so tiny 16x16 slot badges never overflow
    private static final String[] JP_COMPACT_UNITS = {
            "", "万", "億", "兆", "京", "垓", "𥝱", "穣", "溝", "澗", "正",
            "載", "極", "恒", "阿", "那", "不", "無量"
    };

    private static final BigInteger[] POW10_3 = new BigInteger[EN_UNITS.length];
    private static final BigInteger[] POW10_4 = new BigInteger[JP_UNITS.length];

    static {
        for (int i = 0; i < POW10_3.length; i++) {
            POW10_3[i] = BigInteger.TEN.pow(i * 3);
        }
        for (int i = 0; i < POW10_4.length; i++) {
            POW10_4[i] = BigInteger.TEN.pow(i * 4);
        }
    }

    private BigCountFormatter() {}

    /** Full human-readable formatted count for tooltips, headers, and panels. */
    public static String format(BigInteger value, UnitMode mode) {
        return formatInternal(value, mode, false);
    }

    /** Ultra-compact formatted count for 16x16 slot overlays. */
    public static String formatCompact(BigInteger value, UnitMode mode) {
        return formatInternal(value, mode, true);
    }

    /** Exact base-10 integer with 3-digit comma separators for hover tooltips. */
    public static String formatExact(BigInteger value) {
        if (value == null) {
            return "0";
        }
        boolean neg = value.signum() < 0;
        String raw = value.abs().toString();
        int len = raw.length();
        if (len <= 3) {
            return neg ? "-" + raw : raw;
        }
        StringBuilder sb = new StringBuilder(len + len / 3 + 1);
        if (neg) {
            sb.append('-');
        }
        int firstGroup = len % 3;
        if (firstGroup == 0) {
            firstGroup = 3;
        }
        sb.append(raw, 0, firstGroup);
        for (int i = firstGroup; i < len; i += 3) {
            sb.append(',').append(raw, i, i + 3);
        }
        return sb.toString();
    }

    /** Returns the color tier for the given count and unit mode. */
    public static ColorTier colorTier(BigInteger value, UnitMode mode) {
        if (value == null) {
            return ColorTier.PLAIN;
        }
        BigInteger abs = value.abs();
        if (abs.compareTo(MURYO_THRESHOLD) >= 0) {
            return ColorTier.MURYO;
        }
        int digits = abs.equals(BigInteger.ZERO) ? 1 : abs.toString().length();
        // exponent = digits - 1
        int exp = digits - 1;
        if (mode == UnitMode.JAPANESE) {
            if (exp < 4) {
                return ColorTier.PLAIN;
            }
            if (exp < 8) {
                return ColorTier.CYAN;
            }
            if (exp < 12) {
                return ColorTier.EMERALD;
            }
            if (exp < 16) {
                return ColorTier.GOLD;
            }
            if (exp < 20) {
                return ColorTier.MAGENTA;
            }
            if (exp < 36) {
                return ColorTier.SUNSET;
            }
            return ColorTier.PRISM;
        } else {
            if (exp < 3) {
                return ColorTier.PLAIN;
            }
            if (exp < 6) {
                return ColorTier.CYAN;
            }
            if (exp < 9) {
                return ColorTier.EMERALD;
            }
            if (exp < 12) {
                return ColorTier.GOLD;
            }
            if (exp < 15) {
                return ColorTier.MAGENTA;
            }
            if (exp < 33) {
                return ColorTier.SUNSET;
            }
            return ColorTier.PRISM;
        }
    }

    /**
     * Resolves an ARGB color for rendering the count at wall-clock {@code timeMs}.
     * Static for lower tiers, sunset warmth for Qa..No / 垓..溝, flowing neon prism for Dc+ / 澗+,
     * and golden-rainbow shimmer for Muryo (10^68+).
     */
    public static int argbColor(BigInteger value, UnitMode mode, long timeMs) {
        ColorTier tier = colorTier(value, mode);
        if (tier == ColorTier.SUNSET) {
            // Subtle warm coral-to-amber glow
            double wave = 0.5D + 0.5D * Math.sin(timeMs * 0.003D);
            return lerpRgb(0xFFFF6644, 0xFFFFAA00, wave);
        }
        if (tier == ColorTier.PRISM) {
            float hue = (float) (((timeMs % 3200L) / 3200.0D));
            return hsvToArgb(hue, 0.68F, 1.0F);
        }
        if (tier == ColorTier.MURYO) {
            // Golden-rainbow shimmer cycling between radiant gold and high-luminance prism
            double shimmer = 0.5D + 0.5D * Math.sin(timeMs * 0.0065D);
            float hue = (float) (((timeMs % 2000L) / 2000.0D));
            int rainbow = hsvToArgb(hue, 0.58F, 1.0F);
            return lerpRgb(0xFFFFE55C, rainbow, 0.48D * shimmer);
        }
        return tier.baseArgb();
    }

    /** True when the value has entered the 10^68+ 無量大数 tier and should display the ✦ star accent. */
    public static boolean isMuryo(BigInteger value) {
        return value != null && value.abs().compareTo(MURYO_THRESHOLD) >= 0;
    }

    private static String formatInternal(BigInteger value, UnitMode mode, boolean compact) {
        if (value == null || value.signum() == 0) {
            return "0";
        }
        boolean neg = value.signum() < 0;
        BigInteger abs = value.abs();
        String prefix = neg ? "-" : "";

        // 10^68+ 無量大数 special prestige tier
        if (abs.compareTo(MURYO_THRESHOLD) >= 0) {
            String num = scaledDecimal(abs, MURYO_THRESHOLD, compact);
            if (mode == UnitMode.JAPANESE) {
                return prefix + num + (compact ? "無量" : "無量大数");
            }
            return prefix + num + (compact ? "Mr" : "Muryo");
        }

        if (mode == UnitMode.JAPANESE) {
            if (abs.compareTo(TEN_THOUSAND) < 0) {
                return prefix + abs;
            }
            int digits = abs.toString().length();
            int group = Math.min(JP_UNITS.length - 1, (digits - 1) / 4);
            String num = scaledDecimal(abs, POW10_4[group], compact);
            String unit = compact ? JP_COMPACT_UNITS[group] : JP_UNITS[group];
            return prefix + num + unit;
        } else {
            if (abs.compareTo(THOUSAND) < 0) {
                return prefix + abs;
            }
            int digits = abs.toString().length();
            int group = Math.min(EN_UNITS.length - 1, (digits - 1) / 3);
            String num = scaledDecimal(abs, POW10_3[group], compact);
            return prefix + num + EN_UNITS[group];
        }
    }

    private static String scaledDecimal(BigInteger abs, BigInteger divisor, boolean compact) {
        BigDecimal bd = new BigDecimal(abs).divide(new BigDecimal(divisor), 2, RoundingMode.DOWN);
        BigDecimal hundred = BigDecimal.valueOf(100);
        if (bd.compareTo(hundred) >= 0) {
            return bd.setScale(0, RoundingMode.DOWN).toPlainString();
        }
        if (compact && bd.compareTo(BigDecimal.TEN) >= 0) {
            return bd.setScale(0, RoundingMode.DOWN).toPlainString();
        }
        BigDecimal oneDec = bd.setScale(1, RoundingMode.DOWN);
        String s = oneDec.toPlainString();
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private static int lerpRgb(int a, int b, double t) {
        double c = Math.max(0.0D, Math.min(1.0D, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * c);
        int g = (int) Math.round(ag + (bg - ag) * c);
        int bl = (int) Math.round(ab + (bb - ab) * c);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int hsvToArgb(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6.0F;
        int sector = (int) Math.floor(hh);
        float f = hh - sector;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r, g, b;
        switch (sector % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000
                | (Math.round(r * 255.0F) << 16)
                | (Math.round(g * 255.0F) << 8)
                | Math.round(b * 255.0F);
    }

    public static String formatFluidTooltip(BigInteger mb, UnitMode mode) {
        return String.format(Locale.ROOT, "%s mB", format(mb, mode));
    }
}

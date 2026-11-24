package com.industrialcore.client;

import com.industrialcore.machine.FaceMode;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.menu.GuiMetrics;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

/**
 * Shared visual language of every Industrial Core machine panel (Phase 42).
 *
 * <p>The benchmark is the ore synthesizer (Phase 39): a dark instrument face inside a gunmetal
 * housing, one recessed bay, a header band with the machine glyph, a status strip and a plain player
 * inventory. Every other screen used to repeat its own palette constants and its own border code,
 * which is why panels of the same mod looked like they came from different mods. This class is now
 * the single source of those colours and primitives; a screen keeps only its own bay layout.</p>
 *
 * <p>Rules that must not drift (see {@code docs/MACHINE_GUI_TEXTURE_GUIDE.md}):</p>
 * <ul>
 *   <li>Cyan means "what the machine produces" (energy, progress, output).</li>
 *   <li>Purple means "what is put into the machine" (ingredients, input groups).</li>
 *   <li>Orange is reserved for heat and burning, and only in the bay that shows it.</li>
 *   <li>Slot pitch stays 18 px, the player inventory keeps the vanilla 3x9 + hotbar arrangement.</li>
 *   <li>Text uses {@link #TEXT} for values and {@link #MUTED} for labels; one line never mixes
 *       more than three colours.</li>
 * </ul>
 *
 * <p>Nothing here reads game state: the caller passes the server-synced numbers in. The drawing is
 * {@code fill}-only, so no GUI texture has to be shipped.</p>
 */
public final class MachineTheme {
    // ---- housing ---------------------------------------------------------------
    /** Panel background, top of the gradient. */
    public static final int BG_TOP = 0xFF0C131B;
    /** Panel background, bottom of the gradient. */
    public static final int BG_BOTTOM = 0xFF06090E;
    public static final int FRAME_LIGHT = 0xFF3B4B59;
    public static final int FRAME_DARK = 0xFF020406;
    public static final int FRAME_MID = 0xFF17222C;
    public static final int PANEL_TOP = 0xFF121C25;
    public static final int PANEL_BOTTOM = 0xFF0A1118;
    public static final int BAY_TOP = 0xFF0A1017;
    public static final int BAY_BOTTOM = 0xFF05080C;
    public static final int LINE_SOFT = 0xFF1E2A35;
    public static final int HAIRLINE = 0xFF0A0F15;

    // ---- metal -----------------------------------------------------------------
    public static final int METAL = 0xFF2B3742;
    public static final int METAL_LIGHT = 0xFF4A5C6B;
    public static final int METAL_DARK = 0xFF0D141A;
    public static final int RIVET = 0xFF7F909E;
    public static final int RIVET_HI = 0xFFC6D4DE;

    // ---- slots -----------------------------------------------------------------
    public static final int SLOT_FRAME = 0xFF1B2530;
    public static final int SLOT_SHADOW = 0xFF04070A;
    public static final int SLOT_EDGE = 0xFF2B3843;
    public static final int SLOT_CELL = 0xFF080D12;

    // ---- accents ---------------------------------------------------------------
    public static final int PURPLE = 0xFF966ECC;
    public static final int PURPLE_DIM = 0xFF57397F;
    public static final int PURPLE_BRIGHT = 0xFFB98DEE;
    public static final int CYAN = 0xFF3FC7F5;
    public static final int CYAN_DIM = 0xFF1B6F8C;
    public static final int CYAN_DEEP = 0xFF10404F;
    public static final int CYAN_BRIGHT = 0xFF9BE6FF;
    /** Heat / burning accent, only for combustion gauges. */
    public static final int HEAT = 0xFFFF7A1A;
    public static final int HEAT_BRIGHT = 0xFFFFD166;

    // ---- text ------------------------------------------------------------------
    public static final int TEXT = 0xFFEAF2F8;
    public static final int MUTED = 0xFF8FA3B4;
    public static final int STATUS_OK = 0xFF54C97A;
    public static final int STATUS_WARN = 0xFFE0B34A;
    public static final int STATUS_BAD = 0xFFEE7D5A;

    /** Header band height of a full-height panel (the benchmark's 26 px). */
    public static final int HEADER_HEIGHT = 26;
    /** Slot pitch: 16 px cell plus a 2 px gutter. */
    public static final int SLOT_PITCH = 18;

    private MachineTheme() {
    }

    // ------------------------------------------------------------------ shell

    /**
     * Draws the whole panel shell: gradient face, two-layer bevel and four corner rivets. Call it
     * first in {@code renderBg}, then draw the machine's own bay on top.
     */
    public static void frame(GuiGraphics graphics, int left, int top, int width, int height) {
        gradient(graphics, left, top, width, height, BG_TOP, BG_BOTTOM);

        graphics.fill(left, top, left + width, top + 1, FRAME_LIGHT);
        graphics.fill(left, top, left + 1, top + height, FRAME_LIGHT);
        graphics.fill(left, top + height - 1, left + width, top + height, FRAME_DARK);
        graphics.fill(left + width - 1, top, left + width, top + height, FRAME_DARK);

        graphics.fill(left + 1, top + 1, left + width - 1, top + 2, FRAME_MID);
        graphics.fill(left + 1, top + 1, left + 2, top + height - 1, FRAME_MID);
        graphics.fill(left + 1, top + height - 2, left + width - 1, top + height - 1, 0xFF111A23);
        graphics.fill(left + width - 2, top + 1, left + width - 1, top + height - 1, 0xFF111A23);

        rivet(graphics, left + 3, top + 3);
        rivet(graphics, left + width - 4, top + 3);
        rivet(graphics, left + 3, top + height - 4);
        rivet(graphics, left + width - 4, top + height - 4);
    }

    // ------------------------------------------------------------ shared chrome

    /**
     * Draws the chrome every panel shares in one call: housing, 30 px header band, machine glyph,
     * status strip housing and the inventory block. A screen calls this first and then draws only
     * its own body between {@code GuiMetrics.BODY_TOP} and {@code bodyBottom}.
     *
     * <p>Text is not drawn here: titles, the state line and the inventory label go through the
     * screen's label pass so they stay above item stacks. See {@link #title}, {@link #statusText}
     * and {@link #inventoryLabel}.</p>
     */
    public static void chrome(GuiGraphics graphics, int left, int top, int height, int bodyBottom,
                              int statusColor) {
        GuiDopamine.observeChrome(left, top, GuiMetrics.WIDTH, height, statusColor);
        frame(graphics, left, top, GuiMetrics.WIDTH, height);
        band(graphics, left, top);
        if (statusColor == STATUS_OK) {
            double phase = GuiDopamine.beatPhase(GuiDopamine.BPM_GUI);
            int span = GuiMetrics.WIDTH - GuiMetrics.TITLE_X - 24;
            int sweepX = left + GuiMetrics.TITLE_X + (int) Math.round(phase * span);
            graphics.fill(sweepX, top + GuiMetrics.HEADER_HEIGHT - 1,
                    Math.min(left + GuiMetrics.WIDTH - 4, sweepX + 10),
                    top + GuiMetrics.HEADER_HEIGHT, CYAN_BRIGHT);
        }
        statusStrip(graphics, left + GuiMetrics.STATUS_INSET, top + GuiMetrics.STATUS_TOP,
                GuiMetrics.WIDTH - 2 * GuiMetrics.STATUS_INSET, GuiMetrics.STATUS_HEIGHT,
                statusColor);
        inventoryBlock(graphics, left, top, bodyBottom);
    }

    /** Header band plus the machine glyph, without the text. */
    public static void band(GuiGraphics graphics, int left, int top) {
        band(graphics, left, top, GuiMetrics.WIDTH);
    }

    /**
     * Header band of a panel that is not a machine panel (the recipe picker dialog is wider than
     * 196 px). Same colours and same accent rule, scaled to {@code width}.
     */
    public static void band(GuiGraphics graphics, int left, int top, int width) {
        int band = GuiMetrics.HEADER_HEIGHT;
        gradient(graphics, left + 2, top + 2, width - 4, band - 3, PANEL_TOP, PANEL_BOTTOM);
        graphics.fill(left + 2, top + band - 1, left + width - 2, top + band, LINE_SOFT);
        // Accent under the title block only: 1/phi of the band, so the eye starts at the name.
        int accent = Math.round((width - GuiMetrics.TITLE_X - 8) / 1.618f);
        graphics.fill(left + GuiMetrics.TITLE_X, top + band - 1,
                left + GuiMetrics.TITLE_X + accent, top + band, CYAN_DEEP);
        machineIcon(graphics, left + GuiMetrics.ICON_X, top + GuiMetrics.ICON_Y,
                GuiMetrics.ICON_SIZE);
    }

    /**
     * Title text in the header band, truncated before the header Skill Tree button / status lamp
     * so a long Japanese machine name never runs under it.
     */
    public static void title(Font font, GuiGraphics graphics, int left, int top,
                             Component title) {
        int room = com.industrialcore.level.SkillTreeLayout.MACHINE_HEADER_BUTTON.x()
                - GuiMetrics.TITLE_X - 5;
        graphics.drawString(font, fit(font, title, room), left + GuiMetrics.TITLE_X,
                top + GuiMetrics.TITLE_Y, TEXT, false);
    }

    /** State line inside the status strip, truncated to leave room for the right-hand value. */
    public static void statusText(Font font, GuiGraphics graphics, int left, int top,
                                  Component text, int color) {
        drawStatusText(font, graphics, left, top, text, color,
                GuiMetrics.WIDTH - 2 * GuiMetrics.STATUS_INSET - 30 - 12);
    }

    /**
     * Status text that keeps clear of a read-out wider than the default allowance:
     * {@code reservedRight} is the width of the component {@link #statusValue} draws on the same
     * row, so a long English state label is shortened instead of running into the number.
     */
    public static void statusText(Font font, GuiGraphics graphics, int left, int top,
                                  Component text, int color, int reservedRight) {
        drawStatusText(font, graphics, left, top, text, color,
                GuiMetrics.WIDTH - GuiMetrics.STATUS_INSET - 8 - reservedRight - 6
                        - GuiMetrics.STATUS_TEXT_X);
    }

    private static void drawStatusText(Font font, GuiGraphics graphics, int left, int top,
                                       Component text, int color, int room) {
        graphics.drawString(font, fit(font, text, room), left + GuiMetrics.STATUS_TEXT_X,
                top + GuiMetrics.STATUS_TEXT_Y, color, false);
    }

    /**
     * Right-hand read-out of the status strip (energy, per cent, tier). It is shortened from the
     * left when a long translation plus a large number would otherwise walk into the state text;
     * the right edge never moves, so the strip keeps its rhythm at any language.
     */
    public static void statusValue(Font font, GuiGraphics graphics, int left, int top,
                                   Component text, int color) {
        int room = 78;
        Component fitted = fit(font, text, room);
        int x = left + GuiMetrics.WIDTH - GuiMetrics.STATUS_INSET - 8 - font.width(fitted);
        graphics.drawString(font, fitted, x, top + GuiMetrics.STATUS_TEXT_Y, color, false);
    }

    /** Separator and the inventory label above the player rows. */
    public static void inventoryBlock(GuiGraphics graphics, int left, int top, int bodyBottom) {
        int separator = top + GuiMetrics.separatorY(bodyBottom);
        graphics.fill(left + GuiMetrics.SIDE, separator, left + GuiMetrics.WIDTH - GuiMetrics.SIDE,
                separator + 1, 0xFF16202A);
        graphics.fill(left + GuiMetrics.SIDE, separator + 1,
                left + GuiMetrics.WIDTH - GuiMetrics.SIDE, separator + 2, 0xFF05080C);
    }

    /** Baseline of the inventory label; {@code bodyBottom} is the same value as for the block. */
    public static void inventoryLabel(Font font, GuiGraphics graphics, int left, int top,
                                      int bodyBottom, Component label) {
        graphics.drawString(font, fit(font, label, GuiMetrics.CONTENT), left + GuiMetrics.SIDE,
                top + GuiMetrics.inventoryLabelY(bodyBottom), MUTED, false);
    }

    /**
     * Header band with the accent underline under the title. {@code height} is clipped by the
     * caller so the band never runs into the first slot row of a compact panel.
     *
     * @deprecated Phase 43 panels use {@link #band} and {@link GuiMetrics#HEADER_HEIGHT}.
     */
    @Deprecated
    public static void header(GuiGraphics graphics, int left, int top, int width, int height) {
        int band = Math.max(4, Math.min(HEADER_HEIGHT, height));
        gradient(graphics, left + 2, top + 2, width - 4, band - 3, PANEL_TOP, PANEL_BOTTOM);
        graphics.fill(left + 2, top + band - 1, left + width - 2, top + band, LINE_SOFT);
        graphics.fill(left + 2, top + band - 1, left + 8, top + band, CYAN_DEEP);
    }

    /**
     * Shortens {@code text} with an ellipsis so it never grows past {@code maxWidth} pixels. Used by
     * the status lines, which share their row with a right-aligned value: a long status must be
     * trimmed instead of running into that value.
     */
    public static Component fit(Font font, Component text, int maxWidth) {
        if (maxWidth <= 0) {
            return Component.empty();
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int room = Math.max(0, maxWidth - font.width(ellipsis));
        return Component.literal(font.plainSubstrByWidth(text.getString(), room) + ellipsis);
    }

    /**
     * Height a header band may use on a panel whose first machine slot starts at {@code slotTop}:
     * the band has to end at least 2 px above that slot.
     */
    public static int safeHeaderHeight(int slotTop) {
        return Math.max(4, Math.min(HEADER_HEIGHT, slotTop - 2));
    }

    /** Top y of the first non-player slot, or a small default for slot-less panels. */
    public static int firstSlotTop(List<Slot> slots) {
        int best = Integer.MAX_VALUE;
        for (Slot slot : slots) {
            // Player inventory sits in the bottom third; machine slots are everything above it.
            if (slot.y < 82) {
                best = Math.min(best, slot.y);
            }
        }
        return best == Integer.MAX_VALUE ? 8 : best;
    }

    /** Hairline divider above the player inventory label. */
    public static void inventorySeparator(GuiGraphics graphics, int left, int top, int width, int y) {
        int separator = top + y - 4;
        graphics.fill(left + 8, separator, left + width - 8, separator + 1, 0xFF16202A);
        graphics.fill(left + 8, separator + 1, left + width - 8, separator + 2, 0xFF05080C);
    }

    /** Recessed bay with cyan corner brackets, drawn behind a machine's own sockets. */
    public static void bay(GuiGraphics graphics, int left, int top, int x, int y, int width,
                           int height) {
        int x0 = left + x;
        int y0 = top + y;
        int x1 = x0 + width;
        int y1 = y0 + height;

        graphics.fill(x0, y0, x1, y1, FRAME_DARK);
        gradient(graphics, x0 + 1, y0 + 1, width - 2, height - 2, BAY_TOP, BAY_BOTTOM);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0xFF02050A);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, 0xFF02050A);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, 0xFF16222C);
        graphics.fill(x0 + 1, y1 - 1, x1 - 1, y1, 0xFF16222C);

        bracket(graphics, x0 + 1, y0 + 1, 1, 1, 6, CYAN_DEEP);
        bracket(graphics, x1 - 2, y0 + 1, -1, 1, 6, CYAN_DEEP);
        bracket(graphics, x0 + 1, y1 - 2, 1, -1, 6, CYAN_DEEP);
        bracket(graphics, x1 - 2, y1 - 2, -1, -1, 6, CYAN_DEEP);
    }

    /**
     * One side-configuration cell of a machine: the fill and the edge together tell the mode, so
     * auto / input / output / none reads without a tooltip. Used by every machine that exposes
     * per-face routing.
     */
    public static void faceCell(GuiGraphics graphics, int x, int y, int width, int height,
                                FaceMode mode) {
        int fill = switch (mode) {
            case AUTO -> PANEL_TOP;
            case INPUT -> PURPLE_DIM;
            case OUTPUT -> CYAN_DEEP;
            case NONE -> 0xFF3A2222;
        };
        int edge = switch (mode) {
            case AUTO -> LINE_SOFT;
            case INPUT -> PURPLE;
            case OUTPUT -> CYAN;
            case NONE -> STATUS_BAD;
        };
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, edge);
        graphics.fill(x, y + height - 1, x + width, y + height, edge);
    }

    /** Inset slot well: 1 px metal rim, shadowed top-left, lit bottom-right, black cell. */
    public static void slotWell(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 2, y - 2, x + 18, y + 18, SLOT_FRAME);
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_CELL);
        graphics.fill(x - 1, y - 1, x + 17, y, SLOT_SHADOW);
        graphics.fill(x - 1, y - 1, x, y + 17, SLOT_SHADOW);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, SLOT_EDGE);
        graphics.fill(x + 16, y - 1, x + 17, y + 17, SLOT_EDGE);
        graphics.fill(x, y, x + 16, y + 16, SLOT_CELL);
    }

    /** Slot wells for every slot the menu exposes. */
    public static void slots(GuiGraphics graphics, int left, int top, List<Slot> slots) {
        for (Slot slot : slots) {
            slotWell(graphics, left + slot.x, top + slot.y);
        }
    }

    /** Multi-layer pseudo-additive neon bloom around a rectangle (+3/+2/+1 aura + white-hot filament). */
    public static void glowRect(GuiGraphics graphics, int x, int y, int width, int height,
                                int rgb, double intensity) {
        if (intensity <= 0.01 || width <= 0 || height <= 0) {
            return;
        }
        int baseRgb = rgb & 0x00FFFFFF;
        double clamped = Math.max(0.0, Math.min(1.5, intensity));
        int[] pads = {3, 2, 1};
        int[] baseAlphas = {0x16, 0x2C, 0x52};
        for (int i = 0; i < pads.length; i++) {
            int p = pads[i];
            int a = Math.max(2, Math.min(220, (int) Math.round(baseAlphas[i] * clamped)));
            int col = (a << 24) | baseRgb;
            graphics.fill(x - p, y - p, x + width + p, y - p + 1, col);
            graphics.fill(x - p, y + height + p - 1, x + width + p, y + height + p, col);
            graphics.fill(x - p, y - p + 1, x - p + 1, y + height + p - 1, col);
            graphics.fill(x + width + p - 1, y - p + 1, x + width + p, y + height + p - 1, col);
        }
    }

    /** Purple frame that groups the ingredient slots of a machine, with active neon pulse. */
    public static void ingredientGroup(GuiGraphics graphics, int left, int top, int x, int y,
                                       int width, int height) {
        int x0 = left + x;
        int y0 = top + y;
        int x1 = x0 + width;
        int y1 = y0 + height;

        boolean active = GuiDopamine.isTrackedActive();
        if (active) {
            double p = 0.35 + 0.35 * GuiDopamine.beatPulse(GuiDopamine.BPM_GUI);
            glowRect(graphics, x0, y0, width, height, PURPLE, p);
        }
        graphics.fill(x0, y0, x1, y0 + 1, active ? PURPLE : PURPLE_DIM);
        graphics.fill(x0, y1 - 1, x1, y1, 0xFF2A1C3F);
        graphics.fill(x0, y0, x0 + 1, y1, active ? PURPLE : PURPLE_DIM);
        graphics.fill(x1 - 1, y0, x1, y1, 0xFF2A1C3F);
        int cornerCol = active ? CYAN_BRIGHT : PURPLE;
        bracket(graphics, x0 - 1, y0 - 1, 1, 1, 5, cornerCol);
        bracket(graphics, x1, y0 - 1, -1, 1, 5, cornerCol);
        bracket(graphics, x0 - 1, y1, 1, -1, 5, cornerCol);
        bracket(graphics, x1, y1, -1, -1, 5, cornerCol);
    }

    /**
     * Cyan ring around a result slot without the exit chevrons: for a result well that sits on the
     * right edge of a 176 px panel, where the chevrons would be drawn outside the housing.
     */
    public static void outputWell(GuiGraphics graphics, int x, int y) {
        GuiDopamine.observeOutput(x, y);
        double pulse = GuiDopamine.beatPulse(GuiDopamine.BPM_GUI);
        glowRect(graphics, x - 4, y - 4, 24, 24, CYAN, 0.30 + 0.45 * pulse);
        graphics.fill(x - 4, y - 4, x + 20, y + 20, CYAN_DEEP);
        slotWell(graphics, x, y);
        graphics.fill(x - 4, y - 4, x + 20, y - 3, CYAN_DIM);
        graphics.fill(x - 4, y + 19, x + 20, y + 20, CYAN_DIM);
        graphics.fill(x - 4, y - 3, x - 3, y + 19, CYAN_DIM);
        graphics.fill(x + 19, y - 3, x + 20, y + 19, CYAN_DIM);
        int bCol = pulse > 0.35 ? 0xFFFFFFFF : CYAN;
        bracket(graphics, x - 3, y - 3, 1, 1, 4, bCol);
        bracket(graphics, x + 18, y - 3, -1, 1, 4, bCol);
        bracket(graphics, x - 3, y + 18, 1, -1, 4, bCol);
        bracket(graphics, x + 18, y + 18, -1, -1, 4, bCol);
        drawPerimeterTrace(graphics, x - 4, y - 4, 24);
    }

    /** Cyan ring around a result slot, with two chevrons showing where the item leaves. */
    public static void outputSlot(GuiGraphics graphics, int x, int y) {
        GuiDopamine.observeOutput(x, y);
        double pulse = GuiDopamine.beatPulse(GuiDopamine.BPM_GUI);
        glowRect(graphics, x - 4, y - 4, 24, 24, CYAN, 0.35 + 0.50 * pulse);
        graphics.fill(x - 4, y - 4, x + 20, y + 20, CYAN_DEEP);
        slotWell(graphics, x, y);
        graphics.fill(x - 4, y - 4, x + 20, y - 3, CYAN_DIM);
        graphics.fill(x - 4, y + 19, x + 20, y + 20, CYAN_DIM);
        graphics.fill(x - 4, y - 3, x - 3, y + 19, CYAN_DIM);
        graphics.fill(x + 19, y - 3, x + 20, y + 19, CYAN_DIM);
        int bCol = pulse > 0.35 ? 0xFFFFFFFF : CYAN;
        bracket(graphics, x - 3, y - 3, 1, 1, 4, bCol);
        bracket(graphics, x + 18, y - 3, -1, 1, 4, bCol);
        bracket(graphics, x - 3, y + 18, 1, -1, 4, bCol);
        bracket(graphics, x + 18, y + 18, -1, -1, 4, bCol);
        drawPerimeterTrace(graphics, x - 4, y - 4, 24);

        long beatStep = (Util.getMillis() / 180L) % 3L;
        for (int i = 0; i < 2; i++) {
            int cx = x + 23 + i * 3;
            int chevronColor = (beatStep == i) ? CYAN_BRIGHT : (i == 0 ? CYAN : CYAN_DEEP);
            for (int k = 0; k <= 3; k++) {
                graphics.fill(cx + k, y + 4 + k, cx + k + 1, y + 5 + k, chevronColor);
                graphics.fill(cx + k, y + 12 - k, cx + k + 1, y + 13 - k, chevronColor);
            }
        }
    }

    private static void drawPerimeterTrace(GuiGraphics graphics, int rx, int ry, int size) {
        int max = size - 1;
        int perimeter = max * 4;
        int step = (int) ((Util.getMillis() / 36L) % perimeter);
        for (int trail = 0; trail < 3; trail++) {
            int p = Math.floorMod(step - trail, perimeter);
            int px;
            int py;
            if (p < max) {
                px = rx + p;
                py = ry;
            } else if (p < max * 2) {
                px = rx + max;
                py = ry + (p - max);
            } else if (p < max * 3) {
                px = rx + max - (p - max * 2);
                py = ry + max;
            } else {
                px = rx;
                py = ry + max - (p - max * 3);
            }
            int col = trail == 0 ? 0xFFE6FBFF : (trail == 1 ? CYAN_BRIGHT : CYAN);
            graphics.fill(px, py, px + 1, py + 1, col);
        }
    }

    // ------------------------------------------------------------------ gauges

    /** Vertical FE column with segmented cells, quarter ticks and a state lamp at its foot. */
    public static void energyColumn(GuiGraphics graphics, int x0, int y0, int width, int height,
                                    int stored, int capacity) {
        GuiDopamine.observeEnergy(stored, capacity);
        int x1 = x0 + width;
        int y1 = y0 + height;

        int cap = Math.max(1, capacity);
        int value = Math.max(0, Math.min(stored, cap));
        double ratio = (double) value / cap;
        if (ratio > 0.01) {
            glowRect(graphics, x0, y0, width, height, CYAN, 0.25 + 0.45 * ratio);
        }

        graphics.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, METAL);
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, FRAME_DARK);
        graphics.fill(x0, y0, x1, y1, 0xFF060A0F);
        graphics.fill(x0, y0, x0 + 1, y1, 0xFF0A1016);
        graphics.fill(x1 - 1, y0, x1, y1, 0xFF0A1016);

        int trackTop = y0 + 1;
        int trackBottom = y1 - 1;
        int innerHeight = trackBottom - trackTop;
        int filled = (int) Math.round(innerHeight * ratio);
        if (filled > 0) {
            int fillTop = trackBottom - filled;
            int base = ratio < 0.25 ? CYAN_DEEP : (ratio < 0.75 ? CYAN_DIM : CYAN);
            gradient(graphics, x0 + 1, fillTop, width - 2, filled, blend(base, CYAN_DEEP, 0.45),
                    base);
            long now = Util.getMillis();
            double pulse = GuiDopamine.beatPulseAt(now, GuiDopamine.BPM_GUI);
            int topCap = ratio >= 0.99 ? 0xFFFFFFFF : blend(CYAN_BRIGHT, 0xFFFFFFFF, 0.4 + pulse * 0.45);
            graphics.fill(x0 + 1, fillTop, x1 - 1, fillTop + 1, topCap);
            graphics.fill(x1 - 2, fillTop + 1, x1 - 1, trackBottom, blend(base, 0xFFFFFFFF, 0.25));
            // Rising plasma bubbles with 3x3 halo + #FFFFFF pure white core
            int innerW = Math.max(1, width - 3);
            for (int b = 0; b < 3 && filled > 4; b++) {
                int dy = (int) (((now / 65L) + b * (filled / 3 + 5)) % Math.max(1, filled - 1));
                int by = trackBottom - 1 - dy;
                if (by > fillTop + 1 && by < trackBottom - 1) {
                    int bx = x0 + 1 + ((b * 3 + dy / 5) % innerW);
                    graphics.fill(bx - 1, by - 1, bx + 2, by + 2, 0x4466F2FF);
                    graphics.fill(bx, by, bx + 1, by + 1, 0xFFFFFFFF);
                }
            }
        } else {
            for (int i = 0; i < 4; i++) {
                int y = trackTop + 6 + i * 12;
                graphics.fill(x0 + 3, y, x0 + 5, y + 1, 0xFF15202A);
            }
        }
        for (int y = trackTop + 6; y < trackBottom; y += 6) {
            graphics.fill(x0 + 1, y, x1 - 1, y + 1, HAIRLINE);
        }
        for (int q = 1; q <= 3; q++) {
            int y = trackBottom - innerHeight * q / 4;
            graphics.fill(x0 - 1, y, x0, y + 1, METAL_LIGHT);
            graphics.fill(x1, y, x1 + 1, y + 1, METAL_DARK);
        }
        int lampColor = ratio <= 0.0 ? 0xFF33414D
                : (ratio < 0.25 ? STATUS_BAD : (ratio >= 1.0 ? CYAN_BRIGHT : CYAN));
        graphics.fill(x0 + 2, y1 + 2, x1 - 2, y1 + 3, lampColor);
    }

    /** Horizontal FE bar with the same housing as the column. */
    public static void energyBar(GuiGraphics graphics, int x0, int y0, int width, int height,
                                 int stored, int capacity) {
        GuiDopamine.observeEnergy(stored, capacity);
        int x1 = x0 + width;
        int y1 = y0 + height;
        int cap = Math.max(1, capacity);
        int value = Math.max(0, Math.min(stored, cap));
        double ratio = (double) value / cap;
        if (ratio > 0.01) {
            glowRect(graphics, x0, y0, width, height, CYAN, 0.25 + 0.40 * ratio);
        }
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, FRAME_DARK);
        graphics.fill(x0, y0, x1, y1, 0xFF060A0F);

        int filled = (int) Math.round((double) (width - 2) * value / cap);
        if (filled > 0) {
            long now = Util.getMillis();
            double pulse = GuiDopamine.beatPulseAt(now, GuiDopamine.BPM_GUI);
            gradient(graphics, x0 + 1, y0 + 1, filled, height - 2, CYAN_DEEP, CYAN);
            graphics.fill(x0 + 1, y0 + 1, x0 + 1 + filled, y0 + 2,
                    blend(CYAN_BRIGHT, 0xFFFFFFFF, 0.35 + pulse * 0.45));
            graphics.fill(Math.max(x0 + 1, x0 + filled), y0 + 1, x0 + 1 + filled, y1 - 1, 0xFFFFFFFF);
            if (filled > 4 && height >= 6) {
                int innerH = Math.max(1, height - 3);
                for (int b = 0; b < 3; b++) {
                    int dx = (int) (((now / 55L) + b * (filled / 3 + 7)) % filled);
                    int by = y0 + 2 + ((b * 2 + dx / 6) % innerH);
                    int px = x0 + 1 + dx;
                    graphics.fill(Math.max(x0 + 1, px - 1), by - 1, Math.min(x0 + 1 + filled, px + 2), by + 2, 0x4466F2FF);
                    graphics.fill(px, by, Math.min(x0 + 1 + filled, px + 1), by + 1, 0xFFFFFFFF);
                }
            }
        }
        for (int q = 1; q <= 3; q++) {
            int x = x0 + 1 + (width - 2) * q / 4;
            graphics.fill(x, y0, x + 1, y1, 0xFF16202A);
        }
    }

    /** Horizontal progress conduit: multilayer bloom, neon chevron wave & pure white lens flare edge. */
    public static void progressBar(GuiGraphics graphics, int x0, int y0, int width, int height,
                                   int progress, int total) {
        GuiDopamine.observeProgress(x0, y0, width, height, progress, total);
        int x1 = x0 + width;
        int y1 = y0 + height;
        int time = Math.max(1, total);
        int done = Math.max(0, Math.min(progress, time));
        int innerWidth = Math.max(0, width - 2);
        int filled = (int) Math.round((double) innerWidth * done / time);
        long now = Util.getMillis();
        double pulse = GuiDopamine.beatPulseAt(now, GuiDopamine.BPM_GUI);

        if (filled > 0) {
            glowRect(graphics, x0, y0, width, height, CYAN, 0.35 + 0.45 * pulse);
        }
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, FRAME_DARK);
        graphics.fill(x0, y0, x1, y1, 0xFF070B10);

        if (filled > 0) {
            gradient(graphics, x0 + 1, y0 + 1, filled, height - 2, PURPLE_DIM, PURPLE);
            // Animated diagonal neon chevron wave with incandescent filament center
            int waveOffset = (int) (now / 38L);
            for (int dx = 0; dx < filled; dx++) {
                int band = (dx - waveOffset) & 7;
                if (band == 0) {
                    graphics.fill(x0 + 1 + dx, y0 + 1, x0 + 2 + dx, y1 - 1, 0x88E8FFFF);
                } else if (band == 1) {
                    graphics.fill(x0 + 1 + dx, y0 + 1, x0 + 2 + dx, y1 - 1, 0x5556E4FF);
                }
            }
            graphics.fill(x0 + 1, y0 + 1, x0 + 1 + filled, y0 + 2, PURPLE_BRIGHT);
            int edge = x0 + 1 + filled;
            int flareMidY = (y0 + y1) / 2;
            graphics.fill(Math.max(x0 + 1, edge - 4), y0 + 1, edge, y1 - 1, CYAN);
            graphics.fill(Math.max(x0 + 1, edge - 1), y0, Math.min(x1, edge + 1), y1, 0x805CE6FF);
            graphics.fill(Math.max(x0 + 1, edge - 1), y0 + 1, edge, y1 - 1, 0xFFFFFFFF);
            graphics.fill(Math.max(x0 + 1, edge - 3), flareMidY, Math.min(x1, edge + 2), flareMidY + 1, 0xFFFFFFFF);
        }
        for (int q = 1; q <= 3; q++) {
            int nx = x0 + 1 + innerWidth * q / 4;
            boolean passed = done * 4 >= time * q;
            int nodeCol = passed ? blend(CYAN_BRIGHT, 0xFFFFFFFF, 0.3 + pulse * 0.6) : 0xFF16202A;
            graphics.fill(nx, y0 + 1, nx + 1, y1 - 1, nodeCol);
        }
    }

    /** Burning gauge: warm heat bloom with haloed animated embers. */
    public static void burnBar(GuiGraphics graphics, int x0, int y0, int width, int height,
                               int burn, int total) {
        int x1 = x0 + width;
        int y1 = y0 + height;
        int time = Math.max(1, total);
        int lit = Math.max(0, Math.min(burn, time));
        int filled = (int) Math.round((double) (width - 2) * lit / time);
        if (filled > 0) {
            glowRect(graphics, x0, y0, width, height, HEAT, 0.45);
        }
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, FRAME_DARK);
        graphics.fill(x0, y0, x1, y1, 0xFF1A1512);
        if (filled > 0) {
            long now = Util.getMillis();
            gradient(graphics, x0 + 1, y0 + 1, filled, height - 2, 0xFF8C3D0C, HEAT);
            graphics.fill(x0 + 1, y0 + 1, x0 + 1 + filled, y0 + 2,
                    ((now / 110L) % 2L == 0L) ? 0xFFFFFFFF : 0xFFFFE082);
            int innerH = Math.max(1, height - 3);
            for (int e = 0; e < 3 && filled > 3; e++) {
                int ex = (int) (((now / 60L) + e * (filled / 3 + 4)) % filled);
                int ey = y0 + 1 + ((e * 2 + (int) (now / 130L)) % innerH);
                int px = x0 + 1 + ex;
                graphics.fill(Math.max(x0 + 1, px - 1), ey - 1, Math.min(x0 + 1 + filled, px + 2), ey + 2, 0x44FFAD33);
                graphics.fill(px, ey, px + 1, ey + 1, 0xFFFFFFFF);
            }
        }
    }

    /**
     * Status strip of the shared chrome: {@code height} high, state lamp on the left, a pulsing
     * light bar on the right. Text is drawn by the screen's label pass so the font stays in one
     * place.
     */
    public static void statusStrip(GuiGraphics graphics, int x0, int y0, int width, int height,
                                   int statusColor) {
        int x1 = x0 + width;
        int y1 = y0 + height;
        gradient(graphics, x0, y0, width, height, 0xFF0E1720, 0xFF080D13);
        graphics.fill(x0, y0, x1, y0 + 1, LINE_SOFT);
        graphics.fill(x0, y1 - 1, x1, y1, 0xFF05080C);

        double pulse = (statusColor == STATUS_OK || statusColor == CYAN)
                ? GuiDopamine.beatPulse(GuiDopamine.BPM_GUI) : 0.0;
        if (pulse > 0.05) {
            int sweepX = x0 + 16 + (int) Math.round(GuiDopamine.beatPhase(GuiDopamine.BPM_GUI)
                    * Math.max(1, width - 26));
            int glowAlpha = (int) Math.round(pulse * 55.0);
            graphics.fill(sweepX, y0 + 1, Math.min(x1 - 4, sweepX + 12), y1 - 1,
                    (glowAlpha << 24) | (statusColor & 0x00FFFFFF));
        }

        int ix = x0 + 5;
        int iy = y0 + (height - 12) / 2 + 1;
        graphics.fill(ix - 2, iy - 2, ix + 10, iy + 10, 0xFF05080C);
        graphics.fill(ix - 2, iy - 2, ix + 10, iy - 1, 0xFF16202A);
        graphics.fill(ix - 1, iy - 1, ix + 9, iy + 9, 0xFF070C11);
        statusIcon(graphics, ix, iy, statusColor);

        int barCol = pulse > 0.05 ? blend(statusColor, 0xFFFFFFFF, pulse * 0.5) : dim(statusColor);
        graphics.fill(x1 - 3, y0 + 2, x1 - 2, y1 - 2, barCol);
        graphics.fill(x1 - 5, y0 + 2, x1 - 4, y1 - 2, 0xFF16202A);
    }

    /**
     * 14 px status strip, kept for the screens that have not moved to the shared chrome yet.
     *
     * @deprecated use {@link #statusStrip(GuiGraphics, int, int, int, int, int)} with
     *         {@link GuiMetrics#STATUS_HEIGHT}.
     */
    @Deprecated
    public static void statusStrip(GuiGraphics graphics, int x0, int y0, int width, int statusColor) {
        statusStrip(graphics, x0, y0, width, 14, statusColor);
    }

    /** State pictogram with animated equalizer dance while running. */
    public static void statusIcon(GuiGraphics graphics, int x, int y, int colour) {
        if (colour == STATUS_OK || colour == CYAN) {
            long step = (Util.getMillis() / 120L) % 4L;
            int h1 = step == 0 ? 3 : (step == 1 ? 6 : 4);
            int h2 = step == 1 ? 2 : (step == 2 ? 6 : 5);
            int h3 = step == 2 ? 1 : (step == 3 ? 5 : 2);
            graphics.fill(x + 1, y + h1, x + 3, y + 8, colour);
            graphics.fill(x + 4, y + h2, x + 6, y + 8, blend(colour, 0xFFFFFFFF, 0.20));
            graphics.fill(x + 7, y + h3, x + 8, y + 8, blend(colour, 0xFFFFFFFF, 0.35));
            return;
        }
        graphics.fill(x + 1, y + 5, x + 3, y + 8, colour);
        graphics.fill(x + 4, y + 3, x + 6, y + 8, blend(colour, 0xFFFFFFFF, 0.15));
        graphics.fill(x + 7, y + 1, x + 8, y + 8, blend(colour, 0xFFFFFFFF, 0.3));
    }

    /** Machine glyph: purple containment ring around a cyan core, 12x12 like the benchmark. */
    public static void machineIcon(GuiGraphics graphics, int x, int y, int size) {
        int r = size - 1;
        graphics.fill(x + 2, y, x + r - 1, y + 1, PURPLE_DIM);
        graphics.fill(x + 2, y + r, x + r - 1, y + r + 1, PURPLE_DIM);
        graphics.fill(x, y + 2, x + 1, y + r - 1, PURPLE_DIM);
        graphics.fill(x + r, y + 2, x + r + 1, y + r - 1, PURPLE_DIM);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, PURPLE_DIM);
        graphics.fill(x + r - 1, y + 1, x + r, y + 2, PURPLE_DIM);
        graphics.fill(x + 1, y + r - 1, x + 2, y + r, PURPLE_DIM);
        graphics.fill(x + r - 1, y + r - 1, x + r, y + r, PURPLE_DIM);
        graphics.fill(x + 4, y + 4, x + r - 3, y + r - 3, PURPLE);
        graphics.fill(x + 5, y + 5, x + r - 4, y + r - 4, CYAN_DIM);
        graphics.fill(x + 5, y + 5, x + 6, y + 6, CYAN_BRIGHT);
        graphics.fill(x + 6, y + 6, x + 7, y + 7, CYAN);
    }

    /** Status lamp for the header band (small diamonds, dimmed ring + beat-pulsing lit core). */
    public static void headerLamp(GuiGraphics graphics, int x, int y, int statusColor) {
        double pulse = (statusColor == STATUS_OK || statusColor == CYAN)
                ? GuiDopamine.beatPulse(GuiDopamine.BPM_GUI) : 0.0;
        int ring = pulse > 0.1 ? blend(dim(statusColor), statusColor, pulse * 0.6) : dim(statusColor);
        int core = pulse > 0.1 ? blend(statusColor, 0xFFFFFFFF, pulse * 0.45) : statusColor;
        diamond(graphics, x, y, ring);
        diamond(graphics, x + 1, y + 1, core);
    }

    // ------------------------------------------------------------------ helper colours

    /** Running = green, no power = red orange, other problems = amber, idle = muted. */
    public static int statusColor(MachineStatus status) {
        if (status == MachineStatus.WORKING) {
            return STATUS_OK;
        }
        if (status.isProblem()) {
            return status == MachineStatus.NO_ENERGY ? STATUS_BAD : STATUS_WARN;
        }
        return MUTED;
    }

    /** Colour for a boolean-style state: active, warning, or missing requirement. */
    public static int stateColor(boolean active, boolean warning) {
        if (active) {
            return CYAN;
        }
        return warning ? STATUS_WARN : MUTED;
    }

    public static int dim(int color) {
        return blend(color, 0xFF000000, 0.55);
    }

    public static int blend(int from, int to, double t) {
        double clamped = Math.max(0d, Math.min(1d, t));
        int a = (int) Math.round(((from >>> 24) & 0xFF) * (1 - clamped)
                + ((to >>> 24) & 0xFF) * clamped);
        int r = (int) Math.round(((from >>> 16) & 0xFF) * (1 - clamped)
                + ((to >>> 16) & 0xFF) * clamped);
        int g = (int) Math.round(((from >>> 8) & 0xFF) * (1 - clamped)
                + ((to >>> 8) & 0xFF) * clamped);
        int b = (int) Math.round((from & 0xFF) * (1 - clamped) + (to & 0xFF) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void gradient(GuiGraphics graphics, int x, int y, int width, int height,
                               int topColor, int bottomColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int row = 0; row < height; row++) {
            float t = height == 1 ? 0f : (float) row / (height - 1);
            graphics.fill(x, y + row, x + width, y + row + 1, blend(topColor, bottomColor, t));
        }
    }

    /** L-shaped corner accent growing in (dx, dy) direction. */
    public static void bracket(GuiGraphics graphics, int x, int y, int dx, int dy, int length,
                               int color) {
        for (int i = 0; i < length; i++) {
            graphics.fill(x + dx * i, y, x + dx * i + 1, y + 1, color);
            graphics.fill(x, y + dy * i, x + 1, y + dy * i + 1, color);
        }
    }

    public static void diamond(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 2, y, x + 3, y + 1, color);
        graphics.fill(x + 1, y + 1, x + 4, y + 2, color);
        graphics.fill(x, y + 2, x + 5, y + 3, color);
        graphics.fill(x + 1, y + 3, x + 4, y + 4, color);
        graphics.fill(x + 2, y + 4, x + 3, y + 5, color);
    }

    public static void rivet(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 2, y + 2, FRAME_DARK);
        graphics.fill(x, y, x + 1, y + 2, RIVET);
        graphics.fill(x, y, x + 2, y + 1, RIVET);
        graphics.fill(x, y, x + 1, y + 1, RIVET_HI);
    }
}

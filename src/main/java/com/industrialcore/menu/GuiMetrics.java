package com.industrialcore.menu;

/**
 * Shared geometry of every Industrial Core machine panel (Phase 43 redesign).
 *
 * <p>Before this class each panel picked its own width (176 or 196), its own header height
 * (15 / 26) and its own distance between a label and the widget under it. The result was a family
 * of panels that all "worked" but none of them shared a rhythm: the title sat 6 px from the top in
 * one screen and 12 px in another, the status line was a floating text in some panels and a strip
 * in others, and the player inventory started at a different height in every machine.</p>
 *
 * <p>This class fixes one grid for all of them. Everything is measured from the panel edges so a
 * screen only decides <em>what</em> is in its body, never where the chrome sits.</p>
 *
 * <h2>The grid</h2>
 * <pre>
 *   y = 0    ┌──────────────────────────────────────────┐
 *            │ header band 30 px: icon, title, lamp      │
 *   y = 30   ├──────────────────────────────────────────┤
 *   y = 34   │ status strip 16 px: state | key read-out  │
 *   y = 56   ├──────────────────────────────────────────┤
 *   y = 58   │ body: gauges, bays, slots (machine own)   │
 *            │                                           │
 *  bodyBottom├──────────────────────────────────────────┤
 *            │ separator                                 │
 *            │ "Inventory" label                         │
 *            │ 3 player rows                             │
 *            │ hotbar                                    │
 *            └──────────────────────────────────────────┘  height = bodyBottom + 106
 * </pre>
 *
 * <h2>Golden ratio</h2>
 * <p>The panel is not a golden rectangle (a machine panel is dominated by the fixed 3x9 + hotbar
 * block, so a 1:1.618 panel would be absurdly wide). The ratio is used where it actually shows:</p>
 * <ul>
 *   <li><b>header : status strip = 30 : 18.5</b> — the header band and the strip below it are the
 *       same "chrome block"; the band plus the 4 px gap (34) against the 16 px strip keeps the
 *       1:1.618 step (34 / 21 ≈ 1.618) so the title never competes with the state line.</li>
 *   <li><b>body : chrome = 1 : 1.618</b> — a standard body of 74 px sits under 120 px of
 *       header + status + inventory chrome; the working area is the smaller, denser half and the
 *       framing is the calmer half.</li>
 *   <li><b>title block : empty header space ≈ 1 : 1.618</b> — the title occupies the left 38 % of
 *       the band, the rest is breathing room for long Japanese names.</li>
 *   <li><b>inventory block : body = 1 : 1.618 at the default body height</b> — the fixed 82 px
 *       inventory block anchors the bottom; when a machine needs more body it grows upward, so the
 *       inventory never moves between machines.</li>
 * </ul>
 */
public final class GuiMetrics {
    /** Width of every machine panel. */
    public static final int WIDTH = 196;
    /** Horizontal margin between the housing and the content. */
    public static final int SIDE = 12;
    /** Content width: {@code WIDTH - 2 * SIDE}. */
    public static final int CONTENT = WIDTH - 2 * SIDE;

    // ---- header ---------------------------------------------------------------
    public static final int HEADER_HEIGHT = 30;
    public static final int ICON_X = 12;
    public static final int ICON_Y = 7;
    public static final int ICON_SIZE = 16;
    /** Title text origin: right of the 16 px glyph plus a 6 px gap. */
    public static final int TITLE_X = ICON_X + ICON_SIZE + 6;
    /** Title baseline row; the 8 px text sits 11 px from the top edge. */
    public static final int TITLE_Y = 11;
    /** Status lamp in the header, right aligned. */
    public static final int LAMP_X = WIDTH - 20;
    public static final int LAMP_Y = 12;

    // ---- status strip ---------------------------------------------------------
    public static final int STATUS_TOP = 34;
    public static final int STATUS_HEIGHT = 16;
    /** Inset of the strip from the housing sides. */
    public static final int STATUS_INSET = 10;
    /** Baseline of the state text inside the strip. */
    public static final int STATUS_TEXT_Y = STATUS_TOP + 4;
    /** X of the state text, right of the 12 px state pictogram (which ends at SIDE + 15). */
    public static final int STATUS_TEXT_X = SIDE + 22;

    // ---- body -----------------------------------------------------------------
    /** First y a machine may use for its own content. */
    public static final int BODY_TOP = 58;

    // ---- inventory ------------------------------------------------------------
    /** First row x: the 3x9 block is centred (196 - 162) / 2 = 17. */
    public static final int PLAYER_X = (WIDTH - 9 * 18) / 2;
    public static final int PLAYER_ROW_STRIDE = 18;
    /** Distance from the inventory top to the hotbar. */
    public static final int HOTBAR_OFFSET = 58;
    /** Space under the hotbar. */
    public static final int BOTTOM_MARGIN = 10;
    /** Separator line under the body. */
    public static final int SEPARATOR_OFFSET = 8;
    /** Baseline of the "Inventory" label. */
    public static final int INVENTORY_LABEL_OFFSET = 12;
    /** Top of the first player inventory row. */
    public static final int PLAYER_INV_OFFSET = 24;
    /** Total height below {@code bodyBottom}: separator + label + rows + hotbar + margin. */
    public static final int FOOTER = PLAYER_INV_OFFSET + HOTBAR_OFFSET + 16 + BOTTOM_MARGIN;

    private GuiMetrics() {
    }

    /** Separator y for a panel whose body ends at {@code bodyBottom}. */
    public static int separatorY(int bodyBottom) {
        return bodyBottom + SEPARATOR_OFFSET;
    }

    /** Baseline y of the inventory label for a panel whose body ends at {@code bodyBottom}. */
    public static int inventoryLabelY(int bodyBottom) {
        return bodyBottom + INVENTORY_LABEL_OFFSET;
    }

    /** Top y of the first player inventory row. */
    public static int playerInvY(int bodyBottom) {
        return bodyBottom + PLAYER_INV_OFFSET;
    }

    /** Top y of the hotbar. */
    public static int hotbarY(int bodyBottom) {
        return playerInvY(bodyBottom) + HOTBAR_OFFSET;
    }

    /** Total panel height for a given body bottom. */
    public static int height(int bodyBottom) {
        return bodyBottom + FOOTER;
    }

    /** Right edge of the content area. */
    public static int contentRight() {
        return WIDTH - SIDE;
    }

    /** X of a player inventory column (0..8). */
    public static int playerColumnX(int column) {
        return PLAYER_X + column * 18;
    }

    /**
     * Y of a player slot row: 0..2 are the inventory rows, 3 is the hotbar.
     *
     * @param bodyBottom bottom of the machine's own body area
     */
    public static int playerRowY(int bodyBottom, int row) {
        return row < 3 ? playerInvY(bodyBottom) + row * PLAYER_ROW_STRIDE : hotbarY(bodyBottom);
    }

    /** Slot rectangle of a player inventory index (0..35). */
    public static QuarryLayout.Rect playerSlot(int bodyBottom, int index) {
        if (index < 27) {
            return new QuarryLayout.Rect(playerColumnX(index % 9),
                    playerRowY(bodyBottom, index / 9), 16, 16);
        }
        return new QuarryLayout.Rect(playerColumnX(index - 27), hotbarY(bodyBottom), 16, 16);
    }
}

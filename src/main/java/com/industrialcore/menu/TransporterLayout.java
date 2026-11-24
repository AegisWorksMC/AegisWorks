package com.industrialcore.menu;

import com.industrialcore.machine.TransporterKind;
import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Geometry of the Phase 26-29 transporter GUIs, shared by the menu (slot positions) and the
 * screen (drawing and widgets). Same contract as the other layouts: the menu builds its slots
 * from these numbers, the screen draws from the same ones, and {@code GuiLayoutTest} proves for
 * every tier that no two rects overlap and nothing leaves the panel.
 *
 * <p>Phase 43 moves the panel onto {@link GuiMetrics}: 196 px wide, a 30 px header band, the
 * structure/transfer state in the shared status strip, then the channel controls. The two lower
 * tiers have no FE and no transfer toggles, so their body simply starts higher — the panel keeps
 * the same header, the same status strip and the same inventory block on every tier.</p>
 *
 * <pre>
 *   y  34  status strip: structure + transfer state
 *   y  58  channel field | Set | Send/Receive
 *   y  78  item toggle | FE toggle              (Phase 28/29 only)
 *   y 100  FE bar, y 110 stored / capacity      (Phase 28/29 only)
 *   y 122  peer count + last transfer           (y 84 on the lower tiers)
 *   y 134  buffer grid, 8 columns x (1 / 1 / 2 / 8) rows   (y 96 on the lower tiers)
 *   bodyBottom = grid bottom + 6, then the shared 108 px inventory block
 * </pre>
 */
public final class TransporterLayout {
    public static final int WIDTH = GuiMetrics.WIDTH;

    /** Channel name field. */
    public static final Rect CHANNEL_FIELD = new Rect(12, 58, 84, 16);
    /** "Set" button that sends the typed channel name to the server. */
    public static final Rect SET_BUTTON = new Rect(100, 58, 30, 16);
    /** Send / receive role toggle. */
    public static final Rect ROLE_BUTTON = new Rect(134, 58, 50, 16);
    /** Item transfer on/off (Phase 28/29); 80 px so "アイテム: ON" is not clipped. */
    public static final Rect ITEM_BUTTON = new Rect(12, 78, 80, 16);
    /** FE transfer on/off (Phase 28/29). */
    public static final Rect POWER_BUTTON = new Rect(96, 78, 80, 16);

    /** FE bar (Phase 28/29). */
    public static final Rect ENERGY_BAR = new Rect(12, 100, 168, 7);
    /** Baseline top of the energy text (Phase 28/29). */
    public static final int ENERGY_TEXT_Y = 110;

    /** Baseline top of the state line: the shared status strip, on every tier. */
    public static final int STATUS_TEXT_Y = GuiMetrics.STATUS_TEXT_Y;

    /** Slot pitch of the buffer grid. */
    public static final int GRID_STRIDE = 18;
    /** Top-left of the buffer grid, horizontally centred over the content area. */
    public static final int GRID_LEFT = 12 + (GuiMetrics.CONTENT - 8 * GRID_STRIDE) / 2;

    private TransporterLayout() {
    }

    /** Baseline top of the peer / last-transfer line. */
    public static int destTextY(TransporterKind kind) {
        return kind.supportsPower() ? 122 : 84;
    }

    /** Top edge of the buffer grid. */
    public static int gridTop(TransporterKind kind) {
        return kind.supportsPower() ? 134 : 96;
    }

    /** Number of buffer slots per grid row (8; the first row may be shorter). */
    public static int slotsInRow(TransporterKind kind, int row) {
        int firstRows = kind.slotCount() / TransporterKind.GRID_COLUMNS;
        if (row < firstRows) {
            return TransporterKind.GRID_COLUMNS;
        }
        if (row == firstRows && kind.slotCount() % TransporterKind.GRID_COLUMNS != 0) {
            return kind.slotCount() % TransporterKind.GRID_COLUMNS;
        }
        return 0;
    }

    /** Bottom edge of the buffer grid (exclusive of the 1 px gap below it). */
    public static int gridBottom(TransporterKind kind) {
        int rows = kind.gridRows();
        return gridTop(kind) + (rows - 1) * GRID_STRIDE + 16;
    }

    /** Rectangle of buffer slot {@code index} of the given tier. */
    public static Rect slot(TransporterKind kind, int index) {
        if (index < 0 || index >= kind.slotCount()) {
            return new Rect(0, 0, 0, 0);
        }
        int column = index % TransporterKind.GRID_COLUMNS;
        int row = index / TransporterKind.GRID_COLUMNS;
        return new Rect(GRID_LEFT + column * GRID_STRIDE, gridTop(kind) + row * GRID_STRIDE,
                16, 16);
    }

    /** Bottom of the machine body: just under the buffer grid. */
    public static int bodyBottom(TransporterKind kind) {
        return gridBottom(kind) + 6;
    }

    /** Top of the player inventory row for this tier. */
    public static int playerInvY(TransporterKind kind) {
        return GuiMetrics.playerInvY(bodyBottom(kind));
    }

    /** Top of the hotbar row for this tier. */
    public static int hotbarY(TransporterKind kind) {
        return GuiMetrics.hotbarY(bodyBottom(kind));
    }

    /** Panel height for this tier. */
    public static int height(TransporterKind kind) {
        return GuiMetrics.height(bodyBottom(kind));
    }

    /** Rectangle of a player inventory slot (0..26 rows, 27..35 hotbar). */
    public static Rect playerSlot(TransporterKind kind, int index) {
        return GuiMetrics.playerSlot(bodyBottom(kind), index);
    }
}

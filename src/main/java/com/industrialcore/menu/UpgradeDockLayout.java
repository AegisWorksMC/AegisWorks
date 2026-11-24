package com.industrialcore.menu;

import com.industrialcore.menu.QuarryLayout.Rect;

/**
 * Phase 113: Pure-Java zero-overlap geometry for the attached right-side 3-slot
 * Virtual Upgrade &amp; Machine Level Mastery Dock rendered by {@code AdaptiveContainerScreen}.
 */
public final class UpgradeDockLayout {
    public static final int DOCK_GAP = 4;
    public static final int DOCK_WIDTH = 88;
    public static final int DOCK_TOP_Y = 8;
    public static final int DOCK_HEIGHT = 176;

    public static final int PAD_X = 6;
    public static final int TITLE_Y = 6;
    public static final int LEVEL_TEXT_Y = 18;
    public static final Rect XP_BAR = new Rect(PAD_X, 29, DOCK_WIDTH - PAD_X * 2, 6);
    public static final int CAP_TEXT_Y = 39;
    public static final int DIVIDER_Y = 50;

    public static final int ROW_START_Y = 55;
    public static final int ROW_STRIDE = 39;
    public static final int ROW_HEIGHT = 35;

    public static final int SLOT_REL_X = 6;
    public static final int SLOT_SIZE = 18;
    public static final int INFO_REL_X = 28;
    public static final int INFO_WIDTH = DOCK_WIDTH - INFO_REL_X - 5;

    private UpgradeDockLayout() {}

    public static Rect rowRect(int index) {
        return new Rect(4, ROW_START_Y + index * ROW_STRIDE, DOCK_WIDTH - 8, ROW_HEIGHT);
    }

    public static Rect slotRect(int index) {
        int ry = ROW_START_Y + index * ROW_STRIDE;
        return new Rect(SLOT_REL_X, ry + 8, SLOT_SIZE, SLOT_SIZE);
    }

    public static int rowLabelY(int index) {
        return ROW_START_Y + index * ROW_STRIDE + 6;
    }

    public static int rowBonusY(int index) {
        return ROW_START_Y + index * ROW_STRIDE + 19;
    }
}

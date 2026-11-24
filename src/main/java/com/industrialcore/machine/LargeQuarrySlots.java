package com.industrialcore.machine;

/**
 * The one and only description of the large quarry inventory layout.
 *
 * <p>The layout used to be written down as six separate constants in the block entity, and the
 * slot count was additionally restored from the saved NBT by {@code ItemStackHandler}. Those two
 * sources disagreed as soon as the layout changed: a world saved with the Phase 20 machine stores
 * an 18 slot inventory, while the current machine addresses pickaxes at slots 18..25. Loading such
 * a world shrank the array back to 18 and the next tick threw
 * {@code RuntimeException: Slot 18 not in valid range - [0,18)} in {@code refreshToolStats}.</p>
 *
 * <p>Now the layout lives here, the block entity derives its public constants from it, the item
 * handler refuses to change size while loading, and a saved inventory is migrated positionally
 * (the Phase 20 inventory was outputs only, so slot {@code i} keeps its meaning). This class is
 * Minecraft-free so the invariants are unit tested.</p>
 */
public final class LargeQuarrySlots {
    /** Collected ore / dust, two rows of nine like the normal quarry. */
    public static final int OUTPUT_START = 0;
    public static final int OUTPUT_SLOTS = LargeQuarryBalance.OUTPUT_SLOTS;

    /** Pickaxe arms. Slot {@code TOOL_START + i} exists for {@code 0 <= i < TOOL_CAPACITY}. */
    public static final int TOOL_START = OUTPUT_START + OUTPUT_SLOTS;
    public static final int TOOL_CAPACITY = LargeQuarryBalance.MAX_TOOL_SLOTS;

    /** The single motor input that permanently unlocks one more arm. */
    public static final int UPGRADE_SLOT = TOOL_START + TOOL_CAPACITY;
    /** Phase 87: dedicated battery cell slot (+10,000 FE/cell and up to 35% FE discount). */
    public static final int BATTERY_SLOT = UPGRADE_SLOT + 1;
    public static final int TOTAL_SLOTS = BATTERY_SLOT + 1;

    /**
     * Inventory size written by the Phase 20 machine (18 outputs, no pickaxes and no motor slot).
     * Kept only for migration and tests; loading maps its slots one to one.
     */
    public static final int LEGACY_TOTAL_SLOTS = OUTPUT_SLOTS;

    private LargeQuarrySlots() {}

    public static boolean isOutputSlot(int slot) {
        return slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_SLOTS;
    }

    public static boolean isToolSlot(int slot) {
        return slot >= TOOL_START && slot < UPGRADE_SLOT;
    }

    public static boolean isUpgradeSlot(int slot) {
        return slot == UPGRADE_SLOT;
    }

    public static boolean isBatterySlot(int slot) {
        return slot == BATTERY_SLOT;
    }

    /** Absolute slot of a pickaxe arm; the index must be inside {@link #TOOL_CAPACITY}. */
    public static int toolSlot(int index) {
        return TOOL_START + index;
    }

    /** Index of a pickaxe arm inside the tool range, or -1 when the slot is not a tool slot. */
    public static int toolIndex(int slot) {
        return isToolSlot(slot) ? slot - TOOL_START : -1;
    }

    /**
     * Number of arms that can actually be read right now: the unlocked count clamped to the
     * physical range. Motor upgrades only ever raise the stored value through
     * {@link LargeQuarryBalance#clampToolSlots}, this is the second line of defence for old or
     * edited saves.
     */
    public static int activeToolSlots(int configured) {
        return LargeQuarryBalance.clampToolSlots(configured);
    }

    /** True when the slot is inside the current inventory. */
    public static boolean isInRange(int slot) {
        return slot >= 0 && slot < TOTAL_SLOTS;
    }

    /**
     * Maps a slot of a saved inventory into the current layout, or -1 when the item cannot be
     * placed. The Phase 20 layout was outputs only, so all of its slots are still outputs.
     */
    public static int migrateSlot(int slot, int savedSize) {
        if (savedSize == LEGACY_TOTAL_SLOTS) {
            return isOutputSlot(slot) ? slot : -1;
        }
        return isInRange(slot) ? slot : -1;
    }
}

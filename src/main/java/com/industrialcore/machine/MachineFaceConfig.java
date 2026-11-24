package com.industrialcore.machine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-face item automation settings of a machine (Phase 11.3-11.5).
 *
 * <p>Every face has three independent settings:</p>
 * <ul>
 *   <li>a {@link FaceMode}: {@code AUTO} (the machine's previous behaviour), {@code INPUT} (only
 *       insert), {@code OUTPUT} (only extract) or {@code NONE} (no automation at all),</li>
 *   <li>a target slot: {@link #ANY_SLOT} or one specific slot, so "top = material A, side =
 *       material B" is expressible,</li>
 *   <li>a {@link ItemFilterMode} plus a set of item ids. An {@code ALLOW} filter only accepts the
 *       listed items, a {@code DENY} filter blocks them.</li>
 * </ul>
 *
 * <p>The class is free of Minecraft classes (item ids are stored as registry id strings), so the
 * decision table and the cycling behaviour are unit tested. The block entity owns persistence and
 * converts stacks to ids.</p>
 *
 * <p>The default is {@code AUTO} / any slot / filter off on every face, which is exactly the
 * pre-Phase-11 behaviour. A machine loaded from an older world therefore keeps working and never
 * silently stops accepting items.</p>
 */
public final class MachineFaceConfig {
    public static final int SIDES = 6;
    /** Configured slot value meaning "every slot this machine accepts on that face". */
    public static final int ANY_SLOT = -1;

    private final FaceMode[] modes = new FaceMode[SIDES];
    private final int[] slots = new int[SIDES];
    private final ItemFilterMode[] filters = new ItemFilterMode[SIDES];
    private final List<Set<String>> allowed = new ArrayList<>(SIDES);

    public MachineFaceConfig() {
        for (int side = 0; side < SIDES; side++) {
            modes[side] = FaceMode.AUTO;
            slots[side] = ANY_SLOT;
            filters[side] = ItemFilterMode.OFF;
            allowed.add(new LinkedHashSet<>());
        }
    }

    private MachineFaceConfig(MachineFaceConfig other) {
        for (int side = 0; side < SIDES; side++) {
            modes[side] = other.modes[side];
            slots[side] = other.slots[side];
            filters[side] = other.filters[side];
            allowed.add(new LinkedHashSet<>(other.allowed.get(side)));
        }
    }

    public MachineFaceConfig copy() {
        return new MachineFaceConfig(this);
    }

    private static int checkSide(int side) {
        if (side < 0 || side >= SIDES) {
            throw new IllegalArgumentException("side " + side);
        }
        return side;
    }

    public FaceMode mode(int side) {
        return modes[checkSide(side)];
    }

    public int slot(int side) {
        return slots[checkSide(side)];
    }

    public ItemFilterMode filterMode(int side) {
        return filters[checkSide(side)];
    }

    /** Immutable view of the filter ids of one face. */
    public Set<String> allowedIds(int side) {
        return Set.copyOf(allowed.get(checkSide(side)));
    }

    public void setMode(int side, FaceMode mode) {
        modes[checkSide(side)] = mode == null ? FaceMode.AUTO : mode;
    }

    public void setSlot(int side, int slot) {
        slots[checkSide(side)] = slot < 0 ? ANY_SLOT : slot;
    }

    public void setFilterMode(int side, ItemFilterMode mode) {
        filters[checkSide(side)] = mode == null ? ItemFilterMode.OFF : mode;
    }

    public void setAllowed(int side, Collection<String> ids) {
        Set<String> target = allowed.get(checkSide(side));
        target.clear();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isBlank()) {
                    target.add(id);
                }
            }
        }
    }

    /** Adds or removes one id. Returns true when the set changed. */
    public boolean toggleAllowed(int side, String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        Set<String> target = allowed.get(checkSide(side));
        if (!target.remove(id)) {
            target.add(id);
            return true;
        }
        return true;
    }

    /** Cycles the face mode: AUTO -> INPUT -> OUTPUT -> NONE -> AUTO. */
    public FaceMode cycleMode(int side) {
        int index = checkSide(side);
        modes[index] = modes[index].next();
        return modes[index];
    }

    /** Cycles the filter mode: OFF -> ALLOW -> DENY -> OFF. */
    public ItemFilterMode cycleFilter(int side) {
        int index = checkSide(side);
        filters[index] = filters[index].next();
        return filters[index];
    }

    /**
     * Cycles the target slot through {@link #ANY_SLOT} and the supplied candidate slots.
     *
     * @param candidates slot indices this machine exposes to automation
     * @return the new slot value
     */
    public int cycleSlot(int side, int[] candidates) {
        int index = checkSide(side);
        int current = slots[index];
        if (candidates == null || candidates.length == 0) {
            slots[index] = ANY_SLOT;
            return ANY_SLOT;
        }
        int position = -1;
        for (int candidate = 0; candidate < candidates.length; candidate++) {
            if (candidates[candidate] == current) {
                position = candidate;
                break;
            }
        }
        int next = position + 1;
        slots[index] = next >= candidates.length ? ANY_SLOT : candidates[next];
        return slots[index];
    }

    /** True when the face filter accepts this item id. */
    public boolean itemAllowed(int side, String itemId) {
        int index = checkSide(side);
        ItemFilterMode mode = filters[index];
        if (mode == ItemFilterMode.OFF || itemId == null) {
            return true;
        }
        boolean listed = allowed.get(index).contains(itemId);
        return mode == ItemFilterMode.ALLOW ? listed : !listed;
    }

    /**
     * True when automation may insert {@code itemId} into {@code slot} through this face.
     *
     * @param slot the slot the caller wants to insert into
     */
    public boolean canInsert(int side, int slot, String itemId) {
        int index = checkSide(side);
        if (!modes[index].allowsInsert()) {
            return false;
        }
        if (slots[index] != ANY_SLOT && slots[index] != slot) {
            return false;
        }
        return itemAllowed(index, itemId);
    }

    /** True when automation may extract from {@code slot} through this face. */
    public boolean canExtract(int side, int slot, String itemId) {
        int index = checkSide(side);
        if (!modes[index].allowsExtract()) {
            return false;
        }
        if (slots[index] != ANY_SLOT && slots[index] != slot) {
            return false;
        }
        return itemAllowed(index, itemId);
    }

    /** True when nothing was configured, i.e. the machine behaves exactly as before Phase 11. */
    public boolean isDefault() {
        for (int side = 0; side < SIDES; side++) {
            if (modes[side] != FaceMode.AUTO || slots[side] != ANY_SLOT
                    || filters[side] != ItemFilterMode.OFF || !allowed.get(side).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Short human readable summary for the GUI and the chat feedback of the wrench. */
    public String describe(int side) {
        int index = checkSide(side);
        StringBuilder builder = new StringBuilder(modes[index].id());
        if (slots[index] != ANY_SLOT) {
            builder.append(" slot=").append(slots[index]);
        }
        if (filters[index] != ItemFilterMode.OFF) {
            builder.append(' ').append(filters[index].id()).append('=').append(allowed.get(index).size());
        }
        return builder.toString();
    }
}

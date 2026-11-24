package com.industrialcore.machine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Unlocked minerals of one reconstructor (Phase 15).
 *
 * <p>Analysing a sample is irreversible, so the set of unlocked minerals and the current selection
 * are the authoritative state of the machine. They are stored in the block entity NBT, which means
 * two machines never share their progress: each furnace has to be fed with its own samples.</p>
 *
 * <p>The class is free of Minecraft classes (the minerals are plain string keys), so the whole
 * decision table can be unit tested.</p>
 */
public final class ReconstructorMinerals {
    /** Insertion ordered set of unlocked mineral keys. */
    private final LinkedHashSet<String> unlocked = new LinkedHashSet<>();
    /** Currently selected mineral key, empty when nothing is selected. */
    private String selected = "";

    /**
     * Unlocks a mineral.
     *
     * @return false for an empty key or for a mineral that is already unlocked
     */
    public boolean unlock(String mineral) {
        if (mineral == null || mineral.isBlank()) {
            return false;
        }
        return unlocked.add(mineral);
    }

    public boolean isUnlocked(String mineral) {
        return mineral != null && unlocked.contains(mineral);
    }

    /** Unlocked minerals in the order they were unlocked. */
    public List<String> unlocked() {
        return List.copyOf(unlocked);
    }

    public boolean isEmpty() {
        return unlocked.isEmpty();
    }

    public int size() {
        return unlocked.size();
    }

    /** The selected mineral key, empty when nothing is selected. */
    public String selected() {
        return selected;
    }

    /**
     * Selects an unlocked mineral.
     *
     * @return false when the mineral is not unlocked; the selection then stays unchanged
     */
    public boolean select(String mineral) {
        if (!isUnlocked(mineral)) {
            return false;
        }
        selected = mineral;
        return true;
    }

    /**
     * Switches to the next unlocked mineral. The first unlocked mineral is chosen when nothing is
     * selected yet.
     *
     * @return the new selection, empty when no mineral is unlocked
     */
    public String cycle() {
        if (unlocked.isEmpty()) {
            selected = "";
            return selected;
        }
        List<String> minerals = new ArrayList<>(unlocked);
        int index = minerals.indexOf(selected);
        selected = minerals.get((index + 1) % minerals.size());
        return selected;
    }

    /**
     * Restores a saved state. The selection is dropped when it is not part of the restored set,
     * so a hand edited NBT cannot select a mineral that was never unlocked.
     */
    public void restore(List<String> minerals, String selection) {
        unlocked.clear();
        if (minerals != null) {
            for (String mineral : minerals) {
                if (mineral != null && !mineral.isBlank()) {
                    unlocked.add(mineral);
                }
            }
        }
        selected = isUnlocked(selection) ? selection : "";
    }

    /** Forgets every unlocked mineral. */
    public void clear() {
        unlocked.clear();
        selected = "";
    }
}

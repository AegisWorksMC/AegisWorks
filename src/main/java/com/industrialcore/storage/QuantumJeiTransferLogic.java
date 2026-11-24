package com.industrialcore.storage;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Pure-Java planner & executor for JEI one-tap recipe transfer into the Quantum Storage 3x3
 * crafting grid.
 *
 * <p>Sources ingredients from <b>Quantum Storage first</b> and falls back to the player's
 * inventory, guaranteeing strict item conservation (zero duplication and zero item loss) even
 * across multi-slot shared ingredients and {@code maxTransfer} bulk transfers.</p>
 */
public final class QuantumJeiTransferLogic {

    private QuantumJeiTransferLogic() {}

    /**
     * Outcome of a transfer execution.
     *
     * @param transferredSets number of complete craft sets moved into the 3x3 grid (0 if aborted)
     * @param missingSlots    grid slot indices (0..8) that could not be satisfied for at least 1 set
     */
    public record TransferResult(int transferredSets, List<Integer> missingSlots) {
        public boolean success() {
            return transferredSets > 0 && missingSlots.isEmpty();
        }
    }

    /** Mutable stack entry representing one player inventory slot (or 3x3 grid slot). */
    public static final class MutableSlot<K> {
        public K key;
        public int count;

        public MutableSlot(K key, int count) {
            this.key = count > 0 ? key : null;
            this.count = Math.max(0, count);
        }

        public boolean isEmpty() {
            return key == null || count <= 0;
        }

        public void clear() {
            this.key = null;
            this.count = 0;
        }
    }

    /**
     * Checks which 3x3 grid slots (0..8) are missing from the combined pool of
     * {@code storage + craftGrid + playerInventory} without mutating any state.
     *
     * <p>Used both for JEI's client-side missing-slot preview and server-side pre-validation.</p>
     */
    public static <K> List<Integer> findMissingSlots(
            List<Predicate<K>> slotMatchers,
            Map<K, BigInteger> storageCounts,
            List<MutableSlot<K>> craftGrid,
            List<MutableSlot<K>> playerInventory) {

        Map<K, BigInteger> pool = buildCombinedPool(storageCounts, craftGrid, playerInventory);
        List<Integer> missing = new ArrayList<>();
        int len = Math.min(9, slotMatchers.size());
        for (int i = 0; i < len; i++) {
            Predicate<K> matcher = slotMatchers.get(i);
            if (matcher == null) {
                continue;
            }
            K match = pickBestAvailable(pool, matcher);
            if (match == null) {
                missing.add(i);
            } else {
                decrement(pool, match, BigInteger.ONE);
            }
        }
        return missing;
    }

    /**
     * Performs a complete, item-conserving recipe transfer:
     * <ol>
     *   <li>Verifies at least 1 complete craft set exists across {@code storage + craftGrid + playerInv}.</li>
     *   <li>Safely returns current {@code craftGrid} items to {@code storage} (overflow to {@code playerInv}).
     *       If any slot cannot be cleared, aborts cleanly without mutating anything further.</li>
     *   <li>Assigns a concrete matching item key per required slot (preferring Quantum Storage stock).</li>
     *   <li>Computes {@code sets = maxTransfer ? maxAvailableSets : 1}, clamped by each item's
     *       max stack size and shared-key budgets.</li>
     *   <li>Extracts exact counts from <b>storage first</b>, then {@code playerInv}, into {@code craftGrid}.</li>
     * </ol>
     */
    public static <K, F> TransferResult executeTransfer(
            List<Predicate<K>> slotMatchers,
            QuantumStorageData<K, F> storage,
            List<MutableSlot<K>> craftGrid,
            List<MutableSlot<K>> playerInventory,
            ToIntFunction<K> maxStackSizeFn,
            boolean maxTransfer) {

        // 1. Pre-check against combined pool BEFORE moving existing grid contents
        List<Integer> missing = findMissingSlots(slotMatchers, storage.itemsView(), craftGrid, playerInventory);
        if (!missing.isEmpty()) {
            return new TransferResult(0, missing);
        }

        // 2. Return existing 3x3 craft grid contents safely to storage (overflow -> player inventory)
        if (!clearGridSafely(storage, craftGrid, playerInventory, maxStackSizeFn)) {
            return new TransferResult(0, List.of());
        }

        // 3. Build post-clear combined availability map & choose concrete keys for each required slot
        Map<K, BigInteger> storageMap = new LinkedHashMap<>(storage.itemsView());
        Map<K, BigInteger> invMap = countSlots(playerInventory);
        Map<K, BigInteger> totalPool = new LinkedHashMap<>(storageMap);
        for (Map.Entry<K, BigInteger> e : invMap.entrySet()) {
            totalPool.merge(e.getKey(), e.getValue(), BigInteger::add);
        }

        int len = Math.min(9, slotMatchers.size());
        @SuppressWarnings("unchecked")
        K[] chosenKeys = (K[]) new Object[9];
        Map<K, Integer> slotsPerKey = new LinkedHashMap<>();

        // Reserve 1 item per slot while choosing concrete keys (storage priority!)
        Map<K, BigInteger> workStorage = new LinkedHashMap<>(storageMap);
        Map<K, BigInteger> workTotal = new LinkedHashMap<>(totalPool);
        for (int i = 0; i < len; i++) {
            Predicate<K> matcher = slotMatchers.get(i);
            if (matcher == null) {
                continue;
            }
            K picked = pickBestAvailable(workStorage, matcher);
            if (picked == null) {
                picked = pickBestAvailable(workTotal, matcher);
            }
            if (picked == null) {
                return new TransferResult(0, List.of(i));
            }
            chosenKeys[i] = picked;
            slotsPerKey.merge(picked, 1, Integer::sum);
            decrement(workStorage, picked, BigInteger.ONE);
            decrement(workTotal, picked, BigInteger.ONE);
        }

        if (slotsPerKey.isEmpty()) {
            return new TransferResult(0, List.of());
        }

        // 4. Determine how many complete sets to place into the 3x3 grid
        int sets = 1;
        if (maxTransfer) {
            int maxSets = 64;
            for (Map.Entry<K, Integer> e : slotsPerKey.entrySet()) {
                K key = e.getKey();
                int neededPerSet = e.getValue();
                int stackCap = Math.max(1, maxStackSizeFn.applyAsInt(key));
                BigInteger avail = totalPool.getOrDefault(key, BigInteger.ZERO);
                BigInteger setsByAvail = avail.divide(BigInteger.valueOf(neededPerSet));
                int byAvailInt = setsByAvail.min(BigInteger.valueOf(64L)).intValue();
                maxSets = Math.min(maxSets, Math.min(stackCap, byAvailInt));
            }
            sets = Math.max(1, maxSets);
        }

        // 5. Extract exact counts (storage priority -> player inventory fallback) into craftGrid
        for (int i = 0; i < 9; i++) {
            K key = chosenKeys[i];
            if (key == null) {
                continue;
            }
            int remaining = sets;
            BigInteger fromStorage = storage.extractItem(key, BigInteger.valueOf(remaining), false);
            int got = fromStorage.intValue();
            remaining -= got;
            if (remaining > 0) {
                int fromInv = takeFromSlots(playerInventory, key, remaining);
                got += fromInv;
            }
            if (got > 0) {
                craftGrid.get(i).key = key;
                craftGrid.get(i).count = got;
            }
        }

        return new TransferResult(sets, List.of());
    }

    private static <K, F> boolean clearGridSafely(
            QuantumStorageData<K, F> storage,
            List<MutableSlot<K>> craftGrid,
            List<MutableSlot<K>> playerInventory,
            ToIntFunction<K> maxStackSizeFn) {
        for (MutableSlot<K> slot : craftGrid) {
            if (slot.isEmpty()) {
                continue;
            }
            BigInteger toStore = BigInteger.valueOf(slot.count);
            BigInteger accepted = storage.insertItem(slot.key, toStore, false);
            int rem = slot.count - accepted.intValue();
            if (rem > 0) {
                rem = insertIntoSlots(playerInventory, slot.key, rem,
                        Math.max(1, maxStackSizeFn.applyAsInt(slot.key)));
            }
            if (rem <= 0) {
                slot.clear();
            } else {
                slot.count = rem;
                return false;
            }
        }
        return true;
    }

    private static <K> Map<K, BigInteger> buildCombinedPool(
            Map<K, BigInteger> storageCounts,
            List<MutableSlot<K>> craftGrid,
            List<MutableSlot<K>> playerInventory) {
        Map<K, BigInteger> pool = new LinkedHashMap<>();
        for (Map.Entry<K, BigInteger> e : storageCounts.entrySet()) {
            if (e.getValue().signum() > 0) {
                pool.put(e.getKey(), e.getValue());
            }
        }
        addSlotsToMap(pool, craftGrid);
        addSlotsToMap(pool, playerInventory);
        return pool;
    }

    private static <K> Map<K, BigInteger> countSlots(List<MutableSlot<K>> slots) {
        Map<K, BigInteger> map = new LinkedHashMap<>();
        addSlotsToMap(map, slots);
        return map;
    }

    private static <K> void addSlotsToMap(Map<K, BigInteger> map, List<MutableSlot<K>> slots) {
        for (MutableSlot<K> s : slots) {
            if (!s.isEmpty()) {
                map.merge(s.key, BigInteger.valueOf(s.count), BigInteger::add);
            }
        }
    }

    /** Picks the highest-stock matching key available in {@code pool}, or {@code null}. */
    private static <K> K pickBestAvailable(Map<K, BigInteger> pool, Predicate<K> matcher) {
        K best = null;
        BigInteger bestAmt = BigInteger.ZERO;
        for (Map.Entry<K, BigInteger> e : pool.entrySet()) {
            if (e.getValue().signum() > 0 && matcher.test(e.getKey())) {
                if (best == null || e.getValue().compareTo(bestAmt) > 0) {
                    best = e.getKey();
                    bestAmt = e.getValue();
                }
            }
        }
        return best;
    }

    private static <K> void decrement(Map<K, BigInteger> pool, K key, BigInteger amount) {
        BigInteger cur = pool.getOrDefault(key, BigInteger.ZERO);
        BigInteger next = cur.subtract(amount);
        if (next.signum() <= 0) {
            pool.remove(key);
        } else {
            pool.put(key, next);
        }
    }

    private static <K> int takeFromSlots(List<MutableSlot<K>> slots, K key, int wanted) {
        int taken = 0;
        for (MutableSlot<K> s : slots) {
            if (taken >= wanted) {
                break;
            }
            if (!s.isEmpty() && s.key.equals(key)) {
                int take = Math.min(wanted - taken, s.count);
                s.count -= take;
                if (s.count <= 0) {
                    s.clear();
                }
                taken += take;
            }
        }
        return taken;
    }

    private static <K> int insertIntoSlots(List<MutableSlot<K>> slots, K key, int amount, int maxStack) {
        int rem = amount;
        for (MutableSlot<K> s : slots) {
            if (rem <= 0) {
                return 0;
            }
            if (!s.isEmpty() && s.key.equals(key) && s.count < maxStack) {
                int add = Math.min(rem, maxStack - s.count);
                s.count += add;
                rem -= add;
            }
        }
        for (MutableSlot<K> s : slots) {
            if (rem <= 0) {
                return 0;
            }
            if (s.isEmpty()) {
                int add = Math.min(rem, maxStack);
                s.key = key;
                s.count = add;
                rem -= add;
            }
        }
        return rem;
    }
}

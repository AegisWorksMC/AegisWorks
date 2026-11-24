package com.industrialcore.storage;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 89: Pure-Java {@link BigInteger} storage model for items, fluids, and 8 storage-core slots.
 *
 * <p>Generic over item key {@code K} and fluid key {@code F} so all capacity math, 10^68
 * arithmetic, safe core-removal guards, and 3x3 auto-refill logic are unit tested without booting
 * Minecraft.</p>
 */
public final class QuantumStorageData<K, F> {
    private final LinkedHashMap<K, BigInteger> items = new LinkedHashMap<>();
    private final LinkedHashMap<F, BigInteger> fluids = new LinkedHashMap<>();
    private final StorageCoreTier[] cores = new StorageCoreTier[StorageCoreTier.CORE_SLOT_COUNT];
    private boolean useExternalCapacity;
    private int externalMaxTypes = StorageCoreTier.BASE_MAX_TYPES;
    private BigInteger externalMaxPerType = StorageCoreTier.BASE_MAX_PER_TYPE;

    public int maxTypes() {
        return useExternalCapacity ? externalMaxTypes : StorageCoreTier.totalMaxTypes(cores);
    }

    public BigInteger maxPerType() {
        return useExternalCapacity ? externalMaxPerType : StorageCoreTier.totalMaxPerType(cores);
    }

    /**
     * Phase 96: Overrides capacity limits for shared network channels whose capacity is aggregated
     * across all connected {@code quantum_storage_core} nodes. Never deletes existing items/fluids
     * even when the new limit is smaller than currently stored counts (only future inserts are capped).
     */
    public void setExternalCapacity(int maxTypes, BigInteger maxPerType) {
        this.useExternalCapacity = true;
        this.externalMaxTypes = Math.max(StorageCoreTier.BASE_MAX_TYPES, maxTypes);
        this.externalMaxPerType = (maxPerType == null || maxPerType.compareTo(StorageCoreTier.BASE_MAX_PER_TYPE) < 0)
                ? StorageCoreTier.BASE_MAX_PER_TYPE
                : maxPerType;
    }

    public void clearExternalCapacity() {
        this.useExternalCapacity = false;
    }

    public boolean hasExternalCapacity() {
        return useExternalCapacity;
    }

    public int usedTypes() {
        return items.size() + fluids.size();
    }

    public int itemTypeCount() {
        return items.size();
    }

    public int fluidTypeCount() {
        return fluids.size();
    }

    public StorageCoreTier getCore(int slot) {
        if (slot < 0 || slot >= cores.length) {
            return null;
        }
        return cores[slot];
    }

    public StorageCoreTier[] snapshotCores() {
        return cores.clone();
    }

    /**
     * Checks whether replacing core slot {@code slot} with {@code replacement} (or {@code null} to
     * remove it) would keep every currently stored type and stack count within the resulting limits.
     */
    public boolean canChangeCore(int slot, StorageCoreTier replacement) {
        if (slot < 0 || slot >= cores.length) {
            return false;
        }
        StorageCoreTier[] preview = cores.clone();
        preview[slot] = replacement;
        int nextMaxTypes = StorageCoreTier.totalMaxTypes(preview);
        if (usedTypes() > nextMaxTypes) {
            return false;
        }
        BigInteger nextMaxPerType = StorageCoreTier.totalMaxPerType(preview);
        return largestStoredAmount().compareTo(nextMaxPerType) <= 0;
    }

    public boolean canRemoveCore(int slot) {
        return canChangeCore(slot, null);
    }

    /** Sets a core slot if safe, returning {@code false} when blocked by the removal guard. */
    public boolean trySetCore(int slot, StorageCoreTier replacement) {
        if (!canChangeCore(slot, replacement)) {
            return false;
        }
        cores[slot] = replacement;
        return true;
    }

    /** Direct setter for NBT / save loading (does not reject already-persisted state). */
    public void setCoreUnchecked(int slot, StorageCoreTier core) {
        if (slot >= 0 && slot < cores.length) {
            cores[slot] = core;
        }
    }

    public BigInteger largestStoredAmount() {
        BigInteger max = BigInteger.ZERO;
        for (BigInteger v : items.values()) {
            if (v.compareTo(max) > 0) {
                max = v;
            }
        }
        for (BigInteger v : fluids.values()) {
            if (v.compareTo(max) > 0) {
                max = v;
            }
        }
        return max;
    }

    public BigInteger getItemCount(K key) {
        if (key == null) {
            return BigInteger.ZERO;
        }
        return items.getOrDefault(key, BigInteger.ZERO);
    }

    public BigInteger getFluidAmount(F key) {
        if (key == null) {
            return BigInteger.ZERO;
        }
        return fluids.getOrDefault(key, BigInteger.ZERO);
    }

    public Map<K, BigInteger> itemsView() {
        return Collections.unmodifiableMap(items);
    }

    public Map<F, BigInteger> fluidsView() {
        return Collections.unmodifiableMap(fluids);
    }

    /**
     * Inserts up to {@code amount} of {@code key} respecting both {@link #maxTypes()} and
     * {@link #maxPerType()}. Returns the amount actually accepted.
     */
    public BigInteger insertItem(K key, BigInteger amount, boolean simulate) {
        if (key == null || amount == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger current = items.get(key);
        if (current == null && usedTypes() >= maxTypes()) {
            return BigInteger.ZERO;
        }
        BigInteger base = current == null ? BigInteger.ZERO : current;
        BigInteger room = maxPerType().subtract(base);
        if (room.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger accepted = amount.min(room);
        if (!simulate && accepted.signum() > 0) {
            items.put(key, base.add(accepted));
        }
        return accepted;
    }

    /** Extracts up to {@code amount} of {@code key}. Entries hitting 0 are automatically pruned. */
    public BigInteger extractItem(K key, BigInteger amount, boolean simulate) {
        if (key == null || amount == null || amount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger current = items.get(key);
        if (current == null || current.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger taken = amount.min(current);
        if (!simulate && taken.signum() > 0) {
            BigInteger rem = current.subtract(taken);
            if (rem.signum() <= 0) {
                items.remove(key);
            } else {
                items.put(key, rem);
            }
        }
        return taken;
    }

    /** Inserts up to {@code amountMb} of fluid {@code key}. Returns amount actually accepted. */
    public BigInteger insertFluid(F key, BigInteger amountMb, boolean simulate) {
        if (key == null || amountMb == null || amountMb.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger current = fluids.get(key);
        if (current == null && usedTypes() >= maxTypes()) {
            return BigInteger.ZERO;
        }
        BigInteger base = current == null ? BigInteger.ZERO : current;
        BigInteger room = maxPerType().subtract(base);
        if (room.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger accepted = amountMb.min(room);
        if (!simulate && accepted.signum() > 0) {
            fluids.put(key, base.add(accepted));
        }
        return accepted;
    }

    /** Extracts up to {@code amountMb} of fluid {@code key}. Entries hitting 0 are automatically pruned. */
    public BigInteger extractFluid(F key, BigInteger amountMb, boolean simulate) {
        if (key == null || amountMb == null || amountMb.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger current = fluids.get(key);
        if (current == null || current.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger taken = amountMb.min(current);
        if (!simulate && taken.signum() > 0) {
            BigInteger rem = current.subtract(taken);
            if (rem.signum() <= 0) {
                fluids.remove(key);
            } else {
                fluids.put(key, rem);
            }
        }
        return taken;
    }

    /** Direct loader helper for NBT restoration (ignores <= 0 entries). */
    public void putItemUnchecked(K key, BigInteger amount) {
        if (key != null && amount != null && amount.signum() > 0) {
            items.put(key, amount);
        }
    }

    /** Direct loader helper for NBT restoration (ignores <= 0 entries). */
    public void putFluidUnchecked(F key, BigInteger amountMb) {
        if (key != null && amountMb != null && amountMb.signum() > 0) {
            fluids.put(key, amountMb);
        }
    }

    /**
     * Auto-refill helper for the built-in 3x3 crafting grid: for each non-null consumed template key
     * in {@code consumedTemplates}, attempts to pull 1 unit from storage and returns a parallel list
     * of how many units were pulled (0 or 1) for each grid slot.
     */
    public List<Integer> pullCraftRefills(List<K> consumedTemplates) {
        List<Integer> refilled = new ArrayList<>(consumedTemplates.size());
        for (K template : consumedTemplates) {
            if (template == null) {
                refilled.add(0);
                continue;
            }
            BigInteger pulled = extractItem(template, BigInteger.ONE, false);
            refilled.add(pulled.intValue());
        }
        return refilled;
    }

    public void clearAll() {
        items.clear();
        fluids.clear();
        for (int i = 0; i < cores.length; i++) {
            cores[i] = null;
        }
    }
}

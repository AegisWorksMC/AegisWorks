package com.industrialcore.storage;

import com.industrialcore.storage.QuantumJeiTransferLogic.MutableSlot;
import com.industrialcore.storage.QuantumJeiTransferLogic.TransferResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 89 pure-Java unit tests for {@link QuantumStorageData} and {@link StorageCoreTier}:
 * base & expanded capacities, 10^68 arithmetic & lossless string serialization, core removal guard,
 * fluid storage, 3x3 auto-refill crafting pulls, and shipped asset/recipe existence.
 */
class QuantumStorageTest {

    @Test
    void baseCapacityAndFiveCoreTiersExpandAdditively() {
        QuantumStorageData<String, String> vault = new QuantumStorageData<>();
        assertEquals(32, vault.maxTypes());
        assertEquals(BigInteger.valueOf(16_384L), vault.maxPerType());

        assertTrue(vault.trySetCore(0, StorageCoreTier.MK1));
        assertEquals(32 + 64, vault.maxTypes());
        assertEquals(BigInteger.valueOf(16_384L + 65_536L), vault.maxPerType());

        assertTrue(vault.trySetCore(1, StorageCoreTier.MK2));
        assertTrue(vault.trySetCore(2, StorageCoreTier.MK3));
        assertTrue(vault.trySetCore(3, StorageCoreTier.MK4));
        assertTrue(vault.trySetCore(4, StorageCoreTier.MK5));

        BigInteger expectedCap = StorageCoreTier.BASE_MAX_PER_TYPE
                .add(StorageCoreTier.MK1.bonusPerType())
                .add(StorageCoreTier.MK2.bonusPerType())
                .add(StorageCoreTier.MK3.bonusPerType())
                .add(StorageCoreTier.MK4.bonusPerType())
                .add(StorageCoreTier.MK5.bonusPerType());
        assertEquals(expectedCap, vault.maxPerType());
        assertTrue(vault.maxPerType().compareTo(BigInteger.TEN.pow(68)) > 0);
    }

    @Test
    void muryoScaleArithmeticZeroPruningAndStringRoundTripWorkLosslessly() {
        QuantumStorageData<String, String> vault = new QuantumStorageData<>();
        vault.trySetCore(0, StorageCoreTier.MK5);

        BigInteger muryo = BigInteger.TEN.pow(68);
        BigInteger accepted = vault.insertItem("minecraft:iron_ingot", muryo, false);
        assertEquals(muryo, accepted);
        assertEquals(muryo, vault.getItemCount("minecraft:iron_ingot"));

        // Lossless NBT string round-trip (exact 69 digits preserved!)
        String serialized = vault.getItemCount("minecraft:iron_ingot").toString();
        QuantumStorageData<String, String> restored = new QuantumStorageData<>();
        restored.setCoreUnchecked(0, StorageCoreTier.MK5);
        restored.putItemUnchecked("minecraft:iron_ingot", new BigInteger(serialized));
        assertEquals(muryo, restored.getItemCount("minecraft:iron_ingot"));

        // Subtracting down to 0 prunes the entry automatically
        BigInteger half = muryo.divide(BigInteger.TWO);
        assertEquals(half, restored.extractItem("minecraft:iron_ingot", half, false));
        assertEquals(muryo.subtract(half), restored.getItemCount("minecraft:iron_ingot"));
        assertEquals(muryo.subtract(half),
                restored.extractItem("minecraft:iron_ingot", muryo, false));
        assertEquals(BigInteger.ZERO, restored.getItemCount("minecraft:iron_ingot"));
        assertEquals(0, restored.itemTypeCount());
    }

    @Test
    void coreRemovalGuardBlocksWhenStoredTypesOrStackAmountExceedsReducedLimit() {
        QuantumStorageData<String, String> vault = new QuantumStorageData<>();
        vault.trySetCore(0, StorageCoreTier.MK1);

        // 1. Amount exceeds base 16,384 -> removing MK1 must be blocked!
        vault.insertItem("minecraft:diamond", BigInteger.valueOf(50_000L), false);
        assertFalse(vault.canRemoveCore(0), "Removing core must fail while count > base cap");
        assertFalse(vault.trySetCore(0, null));
        assertEquals(StorageCoreTier.MK1, vault.getCore(0));

        // Drain below 16,384 -> removal now succeeds!
        vault.extractItem("minecraft:diamond", BigInteger.valueOf(40_000L), false);
        assertTrue(vault.canRemoveCore(0));
        assertTrue(vault.trySetCore(0, null));

        // 2. Stored type count > 32 -> removing MK1 must be blocked!
        vault.trySetCore(0, StorageCoreTier.MK1);
        for (int i = 0; i < 40; i++) {
            vault.insertItem("item_" + i, BigInteger.ONE, false);
        }
        assertTrue(vault.usedTypes() > StorageCoreTier.BASE_MAX_TYPES);
        assertFalse(vault.canRemoveCore(0), "Removing core must fail while usedTypes > base types");
        // Upgrading MK1 -> MK2 is allowed because limits increase!
        assertTrue(vault.canChangeCore(0, StorageCoreTier.MK2));
        assertTrue(vault.trySetCore(0, StorageCoreTier.MK2));
    }

    @Test
    void fluidsAndItemsCoexistAndAutoRefillPullsCraftTemplatesCleanly() {
        QuantumStorageData<String, String> vault = new QuantumStorageData<>();
        vault.trySetCore(0, StorageCoreTier.MK2);

        // Fluid insert & extract
        BigInteger water = BigInteger.valueOf(500_000L);
        assertEquals(water, vault.insertFluid("minecraft:water", water, false));
        assertEquals(BigInteger.valueOf(1000L),
                vault.extractFluid("minecraft:water", BigInteger.valueOf(1000L), false));
        assertEquals(BigInteger.valueOf(499_000L), vault.getFluidAmount("minecraft:water"));

        // 3x3 auto-refill test (e.g. chest recipe: 8 planks + 1 empty center)
        vault.insertItem("minecraft:oak_planks", BigInteger.valueOf(10), false);
        List<String> pattern = Arrays.asList(
                "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_planks",
                "minecraft:oak_planks", null, "minecraft:oak_planks",
                "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_planks"
        );
        List<Integer> firstRefill = vault.pullCraftRefills(pattern);
        assertEquals(List.of(1, 1, 1, 1, 0, 1, 1, 1, 1), firstRefill);
        assertEquals(BigInteger.valueOf(2), vault.getItemCount("minecraft:oak_planks"));

        // Second craft only 2 planks remain in storage -> refills first 2 slots, leaves rest 0
        List<Integer> secondRefill = vault.pullCraftRefills(pattern);
        assertEquals(List.of(1, 1, 0, 0, 0, 0, 0, 0, 0), secondRefill);
        assertEquals(BigInteger.ZERO, vault.getItemCount("minecraft:oak_planks"));
    }

    @Test
    void quantumStorageBlockItemRecipesAndAssetsExist() {
        Path res = Path.of("src/main/resources");
        assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/blockstates/quantum_storage_core.json")));
        assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/models/block/quantum_storage_core.json")));
        assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/models/item/quantum_storage_core.json")));
        assertTrue(Files.isRegularFile(res.resolve("data/industrialcore/loot_table/blocks/quantum_storage_core.json")));
        assertTrue(Files.isRegularFile(res.resolve("data/industrialcore/recipe/quantum_storage_core.json")));
        for (StorageCoreTier tier : StorageCoreTier.values()) {
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/models/item/" + tier.id() + ".json")));
            assertTrue(Files.isRegularFile(res.resolve("assets/industrialcore/textures/item/" + tier.id() + ".png")));
            assertTrue(Files.isRegularFile(res.resolve("data/industrialcore/recipe/" + tier.id() + ".json")));
        }
    }

    // ------------------------------------------------------------------
    // JEI One-Tap Recipe Transfer Logic & Zero-Duplication Tests
    // ------------------------------------------------------------------

    @Test
    void jeiTransferSingleAndMaxFromStorageOnlyConservesEveryItem() {
        QuantumStorageData<String, String> vault = new QuantumStorageData<>();
        vault.trySetCore(0, StorageCoreTier.MK2);
        vault.insertItem("minecraft:cobblestone", BigInteger.valueOf(100), false);

        // Furnace recipe: 8 cobblestone around empty center
        List<Predicate<String>> furnace = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            furnace.add(i == 4 ? null : "minecraft:cobblestone"::equals);
        }

        List<MutableSlot<String>> grid = emptySlots(9);
        List<MutableSlot<String>> playerInv = emptySlots(36);

        // 1. Single-set transfer (maxTransfer = false) from storage only
        TransferResult single = QuantumJeiTransferLogic.executeTransfer(
                furnace, vault, grid, playerInv, k -> 64, false);
        assertTrue(single.success());
        assertEquals(1, single.transferredSets());
        assertEquals(BigInteger.valueOf(92), vault.getItemCount("minecraft:cobblestone"));
        for (int i = 0; i < 9; i++) {
            assertEquals(i == 4 ? 0 : 1, grid.get(i).count);
        }
        assertEquals(BigInteger.valueOf(100), totalCount("minecraft:cobblestone", vault, grid, playerInv));

        // 2. Bulk transfer (maxTransfer = true): returns 8 to storage (back to 100), then 100 / 8 = 12 sets!
        TransferResult bulk = QuantumJeiTransferLogic.executeTransfer(
                furnace, vault, grid, playerInv, k -> 64, true);
        assertTrue(bulk.success());
        assertEquals(12, bulk.transferredSets());
        assertEquals(BigInteger.valueOf(4), vault.getItemCount("minecraft:cobblestone"));
        for (int i = 0; i < 9; i++) {
            assertEquals(i == 4 ? 0 : 12, grid.get(i).count);
        }
        assertEquals(BigInteger.valueOf(100), totalCount("minecraft:cobblestone", vault, grid, playerInv));
    }

    @Test
    void jeiTransferCombinesStoragePriorityAndPlayerInvAndReturnsOldGridSafely() {
        QuantumStorageData<String, String> vault = new QuantumStorageData<>();
        vault.insertItem("minecraft:iron_ingot", BigInteger.valueOf(2), false);

        List<MutableSlot<String>> grid = emptySlots(9);
        // Existing unrelated item in crafting grid must be returned to storage!
        grid.get(0).key = "minecraft:diamond";
        grid.get(0).count = 5;

        List<MutableSlot<String>> playerInv = emptySlots(36);
        playerInv.get(0).key = "minecraft:iron_ingot";
        playerInv.get(0).count = 3;
        playerInv.get(1).key = "minecraft:stick";
        playerInv.get(1).count = 4;

        // Iron pickaxe: 3 iron ingots (slots 0,1,2) + 2 sticks (slots 4,7)
        List<Predicate<String>> pickaxe = Arrays.asList(
                "minecraft:iron_ingot"::equals, "minecraft:iron_ingot"::equals, "minecraft:iron_ingot"::equals,
                null, "minecraft:stick"::equals, null,
                null, "minecraft:stick"::equals, null
        );

        TransferResult res = QuantumJeiTransferLogic.executeTransfer(
                pickaxe, vault, grid, playerInv, k -> 64, false);
        assertTrue(res.success());
        assertEquals(1, res.transferredSets());
        // Old diamonds safely returned into Quantum Storage
        assertEquals(BigInteger.valueOf(5), vault.getItemCount("minecraft:diamond"));
        // Storage iron drained first (2 -> 0), then 1 iron taken from player inv (3 -> 2)
        assertEquals(BigInteger.ZERO, vault.getItemCount("minecraft:iron_ingot"));
        assertEquals(2, playerInv.get(0).count);
        assertEquals(2, playerInv.get(1).count);
        // Exact conservation across every item type (zero duplication / zero loss)
        assertEquals(BigInteger.valueOf(5), totalCount("minecraft:iron_ingot", vault, grid, playerInv));
        assertEquals(BigInteger.valueOf(4), totalCount("minecraft:stick", vault, grid, playerInv));
        assertEquals(BigInteger.valueOf(5), totalCount("minecraft:diamond", vault, grid, playerInv));
    }

    @Test
    void jeiTransferAbortsSafelyWhenIngredientsMissingWithoutDuplication() {
        QuantumStorageData<String, String> vault = new QuantumStorageData<>();
        vault.insertItem("minecraft:cobblestone", BigInteger.valueOf(5), false); // need 8 for furnace

        List<Predicate<String>> furnace = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            furnace.add(i == 4 ? null : "minecraft:cobblestone"::equals);
        }
        List<MutableSlot<String>> grid = emptySlots(9);
        grid.get(0).key = "minecraft:gold_ingot";
        grid.get(0).count = 3;
        List<MutableSlot<String>> playerInv = emptySlots(36);

        TransferResult res = QuantumJeiTransferLogic.executeTransfer(
                furnace, vault, grid, playerInv, k -> 64, false);
        assertFalse(res.success());
        assertEquals(3, res.missingSlots().size(), "Exactly 3 slots are missing (5 available, 8 required)");
        // Nothing mutated when missing!
        assertEquals(BigInteger.valueOf(5), vault.getItemCount("minecraft:cobblestone"));
        assertEquals(3, grid.get(0).count);
        assertEquals("minecraft:gold_ingot", grid.get(0).key);
    }

    @Test
    void coreQuantumStorageClassesHaveZeroJeiImportsForSoftDependencySafety() throws IOException {
        List<Path> nonJeiSources = List.of(
                Path.of("src/main/java/com/industrialcore/menu/QuantumStorageMenu.java"),
                Path.of("src/main/java/com/industrialcore/client/QuantumStorageScreen.java"),
                Path.of("src/main/java/com/industrialcore/client/JeiSearchBridge.java"),
                Path.of("src/main/java/com/industrialcore/network/QuantumStorageJeiTransferPayload.java"),
                Path.of("src/main/java/com/industrialcore/storage/QuantumJeiTransferLogic.java")
        );
        for (Path src : nonJeiSources) {
            String code = Files.readString(src);
            assertFalse(code.contains("mezz.jei"),
                    src + " must never import mezz.jei so the mod runs cleanly without JEI");
        }
    }

    private static List<MutableSlot<String>> emptySlots(int n) {
        List<MutableSlot<String>> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new MutableSlot<>(null, 0));
        }
        return list;
    }

    private static BigInteger totalCount(String key, QuantumStorageData<String, String> vault,
                                         List<MutableSlot<String>> grid,
                                         List<MutableSlot<String>> playerInv) {
        BigInteger total = vault.getItemCount(key);
        for (MutableSlot<String> s : grid) {
            if (!s.isEmpty() && key.equals(s.key)) {
                total = total.add(BigInteger.valueOf(s.count));
            }
        }
        for (MutableSlot<String> s : playerInv) {
            if (!s.isEmpty() && key.equals(s.key)) {
                total = total.add(BigInteger.valueOf(s.count));
            }
        }
        return total;
    }
}

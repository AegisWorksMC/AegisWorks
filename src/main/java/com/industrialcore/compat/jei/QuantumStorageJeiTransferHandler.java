package com.industrialcore.compat.jei;

import com.industrialcore.blockentity.QuantumStorageBlockEntity.ItemKey;
import com.industrialcore.client.SoundDirector;
import com.industrialcore.menu.QuantumStorageMenu;
import com.industrialcore.network.QuantumStorageJeiTransferPayload;
import com.industrialcore.network.QuantumStorageSyncPayload;
import com.industrialcore.registry.ModMenus;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * High-capacity Quantum Storage JEI one-tap crafting recipe transfer handler for {@link QuantumStorageMenu}.
 *
 * <p>Evaluates missing slots against the combined total of Quantum Storage stock + current 3x3
 * crafting grid + player inventory, highlighting only genuinely missing ingredients in red.
 * When executed ({@code doTransfer = true}), sends a server-authoritative
 * {@link QuantumStorageJeiTransferPayload} carrying only the recipe id and {@code maxTransfer}.</p>
 */
public final class QuantumStorageJeiTransferHandler
        implements IRecipeTransferHandler<QuantumStorageMenu, RecipeHolder<CraftingRecipe>> {

    private final IRecipeTransferHandlerHelper transferHelper;

    public QuantumStorageJeiTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
        this.transferHelper = transferHelper;
    }

    @Override
    public Class<? extends QuantumStorageMenu> getContainerClass() {
        return QuantumStorageMenu.class;
    }

    @Override
    public Optional<MenuType<QuantumStorageMenu>> getMenuType() {
        return Optional.of(ModMenus.QUANTUM_STORAGE_CORE.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(QuantumStorageMenu container,
                                               RecipeHolder<CraftingRecipe> recipe,
                                               IRecipeSlotsView recipeSlots,
                                               Player player,
                                               boolean maxTransfer,
                                               boolean doTransfer) {
        if (recipe.value().isSpecial()) {
            return transferHelper.createInternalError();
        }

        // 1. Build combined virtual stock pool: Quantum Storage + current 3x3 grid + player inventory
        Map<ItemKey, BigInteger> pool = buildClientCombinedPool(container);

        // 2. Match every required JEI input slot against the combined pool
        List<IRecipeSlotView> inputViews = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        List<IRecipeSlotView> missingSlots = new ArrayList<>();

        for (IRecipeSlotView slotView : inputViews) {
            if (slotView.isEmpty()) {
                continue;
            }
            List<ItemStack> candidates = slotView.getItemStacks().toList();
            if (candidates.isEmpty()) {
                continue;
            }
            ItemKey matched = consumeMatchingCandidate(pool, candidates);
            if (matched == null) {
                missingSlots.add(slotView);
            }
        }

        if (!missingSlots.isEmpty()) {
            return transferHelper.createUserErrorForMissingSlots(
                    Component.translatable("jei.tooltip.error.recipe.transfer.missing"),
                    missingSlots);
        }

        // 3. Execute server-authoritative transfer on click
        if (doTransfer) {
            SoundDirector.playClick();
            PacketDistributor.sendToServer(new QuantumStorageJeiTransferPayload(
                    container.getBlockPos(), recipe.id(), maxTransfer));
        }
        return null;
    }

    private static Map<ItemKey, BigInteger> buildClientCombinedPool(QuantumStorageMenu container) {
        Map<ItemKey, BigInteger> pool = new LinkedHashMap<>();
        // A. Quantum Storage synced contents (BigInteger precision)
        for (QuantumStorageSyncPayload.ItemEntry entry : container.getClientItems()) {
            if (!entry.stack().isEmpty() && entry.count().signum() > 0) {
                pool.merge(ItemKey.of(entry.stack()), entry.count(), BigInteger::add);
            }
        }
        // B. Current 3x3 crafting grid (will be returned before filling)
        for (int i = 0; i < QuantumStorageMenu.CRAFT_GRID_COUNT; i++) {
            ItemStack s = container.slots.get(QuantumStorageMenu.CRAFT_GRID_START + i).getItem();
            if (!s.isEmpty()) {
                pool.merge(ItemKey.of(s), BigInteger.valueOf(s.getCount()), BigInteger::add);
            }
        }
        // C. Player inventory + hotbar
        for (int i = QuantumStorageMenu.PLAYER_INV_START; i < container.slots.size(); i++) {
            ItemStack s = container.slots.get(i).getItem();
            if (!s.isEmpty()) {
                pool.merge(ItemKey.of(s), BigInteger.valueOf(s.getCount()), BigInteger::add);
            }
        }
        return pool;
    }

    @Nullable
    private static ItemKey consumeMatchingCandidate(Map<ItemKey, BigInteger> pool,
                                                    List<ItemStack> candidates) {
        ItemKey best = null;
        BigInteger bestAmt = BigInteger.ZERO;
        // Pass 1: exact item + components match (prefer highest stock)
        for (ItemStack cand : candidates) {
            if (cand.isEmpty()) {
                continue;
            }
            ItemKey exact = ItemKey.of(cand);
            BigInteger amt = pool.getOrDefault(exact, BigInteger.ZERO);
            if (amt.signum() > 0 && (best == null || amt.compareTo(bestAmt) > 0)) {
                best = exact;
                bestAmt = amt;
            }
        }
        // Pass 2: same item fallback for vanilla ingredients without strict component requirements
        if (best == null) {
            for (Map.Entry<ItemKey, BigInteger> e : pool.entrySet()) {
                if (e.getValue().signum() <= 0) {
                    continue;
                }
                ItemStack probe = e.getKey().toStack(1);
                for (ItemStack cand : candidates) {
                    if (!cand.isEmpty() && ItemStack.isSameItem(cand, probe)) {
                        if (best == null || e.getValue().compareTo(bestAmt) > 0) {
                            best = e.getKey();
                            bestAmt = e.getValue();
                        }
                        break;
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }
        BigInteger next = bestAmt.subtract(BigInteger.ONE);
        if (next.signum() <= 0) {
            pool.remove(best);
        } else {
            pool.put(best, next);
        }
        return best;
    }
}

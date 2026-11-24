package com.industrialcore.level;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.level.CraftUnlockLogic.CraftCheckResult;
import com.industrialcore.level.CraftUnlockLogic.MissingPrereq;
import com.industrialcore.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

/**
 * Phase 97: Server &amp; client-safe craft-unlock gate wrapping vanilla Crafting Table / 2x2
 * inventory {@link ResultSlot}s and shared with {@code QuantumStorageMenu}.
 *
 * <p>Blocking happens inside {@link Slot#mayPickup(Player)} BEFORE any recipe ingredients are
 * removed, guaranteeing 0% material loss across all click types (left, right, Shift-click,
 * number-key swap, and Q-drop).</p>
 */
public final class CraftUnlockGate {
    private static final long NOTIFY_COOLDOWN_MS = 250L;
    private static final Map<UUID, Long> LAST_NOTIFY_MS = new ConcurrentHashMap<>();

    /** Wired in ClientSetup so this common class never imports client-only classes. */
    public static volatile ToIntFunction<String> clientLevelLookup = id -> MachineLevelMath.MIN_LEVEL;
    public static volatile Runnable clientLockedSound = () -> {};

    private CraftUnlockGate() {}

    public static CraftCheckResult checkCraft(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return CraftCheckResult.ALLOWED;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null || !IndustrialCore.MOD_ID.equals(key.getNamespace())) {
            return CraftCheckResult.ALLOWED;
        }
        ToIntFunction<String> levelOf;
        if (player instanceof ServerPlayer sp) {
            PlayerMachineProgress prog = MachineProgressSavedData.get(sp.serverLevel())
                    .getOrCreate(sp.getUUID());
            levelOf = prog::getLevel;
        } else {
            levelOf = clientLevelLookup;
        }
        return CraftUnlockLogic.evaluate(
                key.getPath(), levelOf, player.isCreative(), Config.enableCraftUnlockGate());
    }

    public static void notifyBlocked(Player player, CraftCheckResult result) {
        if (player == null || result == null || result.allowed()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = LAST_NOTIFY_MS.put(player.getUUID(), now);
        if (prev != null && (now - prev) < NOTIFY_COOLDOWN_MS) {
            return;
        }
        MissingPrereq first = result.firstMissing();
        if (player instanceof ServerPlayer sp) {
            sp.playNotifySound(ModSounds.UI_LOCKED.get(), SoundSource.PLAYERS, 0.75F, 1.0F);
            if (first != null && result.machineId() != null) {
                Component targetName = Component.translatable("block.industrialcore." + result.machineId());
                Component parentName = Component.translatable("block.industrialcore." + first.parentId());
                sp.displayClientMessage(
                        Component.translatable("message.industrialcore.craft_locked",
                                        targetName, parentName, first.requiredLevel())
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        } else {
            Runnable snd = clientLockedSound;
            if (snd != null) {
                snd.run();
            }
        }
    }

    /** Wraps slot 0 of a vanilla {@link CraftingMenu} or {@link InventoryMenu} in {@link GatedResultSlot}. */
    public static void wrapResultSlot(AbstractContainerMenu menu, Player player) {
        if (!(menu instanceof CraftingMenu || menu instanceof InventoryMenu)) {
            return;
        }
        if (menu.slots.size() < 2) {
            return;
        }
        Slot slot0 = menu.slots.get(0);
        if (!(slot0 instanceof ResultSlot) || slot0 instanceof GatedResultSlot) {
            return;
        }
        Slot slot1 = menu.slots.get(1);
        if (!(slot1.container instanceof CraftingContainer craftMatrix)) {
            return;
        }
        GatedResultSlot gated = new GatedResultSlot(
                player, craftMatrix, slot0.container, slot0.getSlotIndex(), slot0.x, slot0.y);
        gated.index = slot0.index;
        menu.slots.set(0, gated);
    }

    public static final class GatedResultSlot extends ResultSlot {
        public GatedResultSlot(Player player, CraftingContainer craftSlots, Container container,
                               int slot, int x, int y) {
            super(player, craftSlots, container, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            CraftCheckResult res = checkCraft(player, this.getItem());
            if (!res.allowed()) {
                notifyBlocked(player, res);
                return false;
            }
            return super.mayPickup(player);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            CraftCheckResult res = checkCraft(player, stack);
            if (!res.allowed()) {
                notifyBlocked(player, res);
                return;
            }
            super.onTake(player, stack);
        }
    }
}

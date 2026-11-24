package com.industrialcore.machine;

import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Phase 113: Unbounded virtual upgrade storage ({@code motor}, {@code battery},
 * {@code specialization_module}) governed by the owner's Machine Level total upgrade cap
 * ({@code 4} at Lv.1 -&gt; {@code 96} at Lv.99 -&gt; {@code 1000} at Lv.1000).
 *
 * <p>Eliminates the vanilla 64-item slot limit while preserving full item-drop safety when the
 * machine is broken and clean client-server click interactions (deposit 1 / bulk / withdraw 1 / 64).</p>
 */
public final class VirtualUpgradeBank {
    public enum UpgradeKind {
        MOTOR(0),
        BATTERY(1),
        SPECIAL(2);

        private final int id;

        UpgradeKind(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static UpgradeKind byId(int id) {
            return switch (id) {
                case 1 -> BATTERY;
                case 2 -> SPECIAL;
                default -> MOTOR;
            };
        }

        public Item item() {
            return switch (this) {
                case MOTOR -> ModItems.MOTOR.get();
                case BATTERY -> ModItems.BATTERY.get();
                case SPECIAL -> ModItems.SPECIALIZATION_MODULE.get();
            };
        }
    }

    /** Category controlling how {@code specialization_module} enhances a specific machine. */
    public enum SpecialCategory {
        TURRET,
        PROCESSING,
        GENERATOR,
        HARVEST_MINE,
        FIELD,
        THROUGHPUT
    }

    private static final String TAG_BANK = "UpgradeBank";
    private static final String TAG_MOTORS = "Motors";
    private static final String TAG_BATTERIES = "Batteries";
    private static final String TAG_SPECIALS = "Specials";

    private int motors;
    private int batteries;
    private int specials;

    public int motors() {
        return motors;
    }

    public int batteries() {
        return batteries;
    }

    public int specials() {
        return specials;
    }

    public int count(UpgradeKind kind) {
        return switch (kind) {
            case MOTOR -> motors;
            case BATTERY -> batteries;
            case SPECIAL -> specials;
        };
    }

    public int total() {
        return motors + batteries + specials;
    }

    public int remaining(int totalLimit, int externalInstalled) {
        return Math.max(0, totalLimit - (total() + Math.max(0, externalInstalled)));
    }

    public int deposit(UpgradeKind kind, int requested, int totalLimit, int externalInstalled) {
        if (requested <= 0) {
            return 0;
        }
        int room = remaining(totalLimit, externalInstalled);
        int added = Math.min(requested, room);
        if (added <= 0) {
            return 0;
        }
        switch (kind) {
            case MOTOR -> motors += added;
            case BATTERY -> batteries += added;
            case SPECIAL -> specials += added;
        }
        return added;
    }

    public int withdraw(UpgradeKind kind, int requested) {
        if (requested <= 0) {
            return 0;
        }
        int have = count(kind);
        int taken = Math.min(requested, have);
        if (taken <= 0) {
            return 0;
        }
        switch (kind) {
            case MOTOR -> motors -= taken;
            case BATTERY -> batteries -= taken;
            case SPECIAL -> specials -= taken;
        }
        return taken;
    }

    public void setCounts(int motors, int batteries, int specials) {
        this.motors = Math.max(0, motors);
        this.batteries = Math.max(0, batteries);
        this.specials = Math.max(0, specials);
    }

    public void save(CompoundTag tag) {
        if (motors == 0 && batteries == 0 && specials == 0) {
            return;
        }
        CompoundTag sub = new CompoundTag();
        sub.putInt(TAG_MOTORS, motors);
        sub.putInt(TAG_BATTERIES, batteries);
        sub.putInt(TAG_SPECIALS, specials);
        tag.put(TAG_BANK, sub);
    }

    public void load(CompoundTag tag) {
        if (tag.contains(TAG_BANK)) {
            CompoundTag sub = tag.getCompound(TAG_BANK);
            this.motors = Math.max(0, sub.getInt(TAG_MOTORS));
            this.batteries = Math.max(0, sub.getInt(TAG_BATTERIES));
            this.specials = Math.max(0, sub.getInt(TAG_SPECIALS));
        } else {
            this.motors = 0;
            this.batteries = 0;
            this.specials = 0;
        }
    }

    /** Drops every stored upgrade cleanly in max-64 stacks when the machine block is destroyed. */
    public void dropAll(Level level, BlockPos pos) {
        dropKind(level, pos, UpgradeKind.MOTOR, motors);
        dropKind(level, pos, UpgradeKind.BATTERY, batteries);
        dropKind(level, pos, UpgradeKind.SPECIAL, specials);
        motors = 0;
        batteries = 0;
        specials = 0;
    }

    private static void dropKind(Level level, BlockPos pos, UpgradeKind kind, int count) {
        if (count <= 0) {
            return;
        }
        Item item = kind.item();
        int rem = count;
        while (rem > 0) {
            int batch = Math.min(64, rem);
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    new ItemStack(item, batch));
            rem -= batch;
        }
    }

    /**
     * Server-authoritative player interaction for the 3-slot Upgrade Dock:
     * <ul>
     *   <li>{@code depositOne}: inserts 1 matching item from carried cursor or player inventory</li>
     *   <li>{@code depositBulk}: inserts all matching items from cursor &amp; inventory up to cap</li>
     *   <li>{@code withdrawOne}: extracts 1 item to player inventory</li>
     *   <li>{@code withdrawStack}: extracts up to 64 items to player inventory</li>
     * </ul>
     */
    public boolean handlePlayerInteraction(ServerPlayer player, UpgradeKind kind, int action,
                                           int totalLimit, int externalInstalled) {
        Item item = kind.item();
        if (action == 0 || action == 1) {
            int maxAdd = action == 0 ? 1 : remaining(totalLimit, externalInstalled);
            if (maxAdd <= 0) {
                return false;
            }
            int added = 0;
            // 1. Pull from cursor carried stack first
            if (player.containerMenu != null) {
                ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty() && carried.is(item)) {
                    int take = Math.min(carried.getCount(), maxAdd - added);
                    int actual = deposit(kind, take, totalLimit, externalInstalled);
                    if (actual > 0) {
                        if (!player.isCreative()) {
                            carried.shrink(actual);
                            if (carried.isEmpty()) {
                                player.containerMenu.setCarried(ItemStack.EMPTY);
                            }
                        }
                        added += actual;
                    }
                }
            }
            // 2. Pull remaining from player inventory
            if (added < maxAdd) {
                var inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize() && added < maxAdd; i++) {
                    ItemStack st = inv.getItem(i);
                    if (!st.isEmpty() && st.is(item)) {
                        int take = Math.min(st.getCount(), maxAdd - added);
                        int actual = deposit(kind, take, totalLimit, externalInstalled);
                        if (actual > 0) {
                            if (!player.isCreative()) {
                                st.shrink(actual);
                                if (st.isEmpty()) {
                                    inv.setItem(i, ItemStack.EMPTY);
                                }
                            }
                            added += actual;
                        }
                    }
                }
            }
            if (added > 0 && player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
            return added > 0;
        }
        if (action == 2 || action == 3) {
            int req = action == 2 ? 1 : 64;
            int taken = withdraw(kind, req);
            if (taken <= 0) {
                return false;
            }
            ItemStack out = new ItemStack(item, taken);
            if (!player.getInventory().add(out) && !out.isEmpty()) {
                player.drop(out, false);
            }
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
            return true;
        }
        return false;
    }
}

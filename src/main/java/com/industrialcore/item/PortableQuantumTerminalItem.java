package com.industrialcore.item;

import com.industrialcore.menu.QuantumStorageMenu;
import com.industrialcore.storage.QuantumNetLogic;
import com.industrialcore.storage.QuantumNetLogic.QuantumChannel;
import com.industrialcore.storage.QuantumNetworkData;
import com.industrialcore.blockentity.QuantumStorageBlockEntity.FluidKey;
import com.industrialcore.blockentity.QuantumStorageBlockEntity.ItemKey;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Phase 96: Portable Quantum Terminal ({@code portable_quantum_terminal}).
 *
 * <p>Electric handheld Quantum Storage dashboard allowing remote access (across any distance or dimension) to
 * linked Quantum Storage frequency channels, including full 13x6 grid operations, 3x3 auto-refill
 * crafting, and JEI one-tap recipe transfer. Exposes {@link IEnergyStorage} item capability so the
 * {@code wireless_charger} charges it automatically.</p>
 */
public class PortableQuantumTerminalItem extends Item {
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_CHANNEL_ID = "ChannelId";
    private static final String TAG_CHANNEL_NAME = "ChannelName";

    public PortableQuantumTerminalItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int getEnergy(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) {
            return 0;
        }
        return Math.max(0, Math.min(QuantumNetLogic.PORTABLE_ENERGY_CAPACITY, cd.copyTag().getInt(TAG_ENERGY)));
    }

    public static void setEnergy(ItemStack stack, int energy) {
        int clamped = Math.max(0, Math.min(QuantumNetLogic.PORTABLE_ENERGY_CAPACITY, energy));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TAG_ENERGY, clamped));
    }

    public static boolean consumeEnergy(ItemStack stack, int cost, boolean creative) {
        if (creative || cost <= 0) {
            return true;
        }
        int cur = getEnergy(stack);
        if (cur < cost) {
            return false;
        }
        setEnergy(stack, cur - cost);
        return true;
    }

    public static String getChannelId(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) {
            return "";
        }
        return cd.copyTag().getString(TAG_CHANNEL_ID);
    }

    public static String getChannelName(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) {
            return "";
        }
        return cd.copyTag().getString(TAG_CHANNEL_NAME);
    }

    public static void setChannel(ItemStack stack, String channelId, String channelName) {
        String id = channelId == null ? "" : channelId;
        String name = channelName == null ? "" : channelName;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(TAG_CHANNEL_ID, id);
            tag.putString(TAG_CHANNEL_NAME, name);
        });
    }

    public static IEnergyStorage createEnergyCapability(ItemStack stack) {
        return new TerminalEnergyStorage(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            boolean creative = sp.isCreative();
            if (!consumeEnergy(stack, QuantumNetLogic.PORTABLE_OPEN_COST, creative)) {
                sp.displayClientMessage(
                        Component.translatable("gui.industrialcore.machine.status.no_energy")
                                .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            String chId = getChannelId(stack);
            // Validate existing channel still exists and is accessible
            if (!chId.isEmpty()) {
                QuantumChannel<ItemKey, FluidKey> ch = QuantumNetworkData.get(sl).getChannel(chId);
                if (ch == null || !ch.canAccess(sp.getUUID().toString())) {
                    chId = "";
                    setChannel(stack, "", "");
                } else {
                    setChannel(stack, chId, ch.name());
                }
            }
            int slotIndex = hand == InteractionHand.MAIN_HAND ? sp.getInventory().selected : -1;
            String finalChId = chId;
            sp.openMenu(new SimpleMenuProvider(
                    (cid, inv, p) -> new QuantumStorageMenu(cid, inv, slotIndex, finalChId),
                    Component.translatable("item.industrialcore.portable_quantum_terminal")),
                    buf -> {
                        buf.writeBoolean(true);
                        buf.writeVarInt(slotIndex);
                        buf.writeUtf(finalChId, 64);
                    });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int e = getEnergy(stack);
        return Math.round(13.0F * e / (float) QuantumNetLogic.PORTABLE_ENERGY_CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFF55E6FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        int energy = getEnergy(stack);
        tooltip.add(Component.translatable("gui.industrialcore.energy_value",
                        energy, QuantumNetLogic.PORTABLE_ENERGY_CAPACITY)
                .withStyle(ChatFormatting.AQUA));
        String chName = getChannelName(stack);
        if (!chName.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.industrialcore.portable_terminal.channel", chName)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.translatable("tooltip.industrialcore.portable_terminal.unlinked")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.industrialcore.portable_terminal.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static final class TerminalEnergyStorage implements IEnergyStorage {
        private final ItemStack stack;

        TerminalEnergyStorage(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) {
                return 0;
            }
            int cur = getEnergy(stack);
            int room = QuantumNetLogic.PORTABLE_ENERGY_CAPACITY - cur;
            int accepted = Math.min(room, Math.min(maxReceive, QuantumNetLogic.PORTABLE_MAX_RECEIVE));
            if (!simulate && accepted > 0) {
                setEnergy(stack, cur + accepted);
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return getEnergy(stack);
        }

        @Override
        public int getMaxEnergyStored() {
            return QuantumNetLogic.PORTABLE_ENERGY_CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}

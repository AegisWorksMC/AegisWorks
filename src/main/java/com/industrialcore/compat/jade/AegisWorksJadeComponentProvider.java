package com.industrialcore.compat.jade;

import com.industrialcore.machine.MachineStopReason;
import com.industrialcore.storage.BigCountFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.math.BigInteger;

/**
 * Phase 114: Client-side Jade tooltip renderer for AegisWorks machines, generators,
 * reactors, storage buffers, and conduits.
 */
public enum AegisWorksJadeComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return AegisWorksJadePlugin.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.getBoolean(AegisWorksJadeDataProvider.KEY_PRESENT)) {
            return;
        }

        // 1. Energy stored / max + net FE/t rate
        if (data.getBoolean(AegisWorksJadeDataProvider.KEY_HAS_ENERGY)) {
            long energy = data.getLong(AegisWorksJadeDataProvider.KEY_ENERGY);
            long cap = Math.max(1L, data.getLong(AegisWorksJadeDataProvider.KEY_CAPACITY));
            long rate = data.getLong(AegisWorksJadeDataProvider.KEY_RATE);
            String curFmt = formatFe(energy);
            String capFmt = formatFe(cap);
            if (rate > 0L) {
                tooltip.add(Component.translatable("jade.industrialcore.energy_gen",
                                curFmt, capFmt, formatFe(rate))
                        .withStyle(ChatFormatting.AQUA));
            } else if (rate < 0L) {
                tooltip.add(Component.translatable("jade.industrialcore.energy_use",
                                curFmt, capFmt, formatFe(-rate))
                        .withStyle(ChatFormatting.AQUA));
            } else {
                tooltip.add(Component.translatable("jade.industrialcore.energy_stored",
                                curFmt, capFmt)
                        .withStyle(ChatFormatting.AQUA));
            }
        }

        // 2. Machine Level + Installed Upgrades (M / B / S)
        int lvl = data.getInt(AegisWorksJadeDataProvider.KEY_LEVEL);
        boolean lb = data.getBoolean(AegisWorksJadeDataProvider.KEY_LIMIT_BROKEN);
        boolean hasUpg = data.getBoolean(AegisWorksJadeDataProvider.KEY_HAS_UPGRADES);
        int m = data.getInt(AegisWorksJadeDataProvider.KEY_MOTORS);
        int b = data.getInt(AegisWorksJadeDataProvider.KEY_BATTERIES);
        int s = data.getInt(AegisWorksJadeDataProvider.KEY_SPECIALS);

        if (lvl > 0 && hasUpg) {
            tooltip.add(Component.translatable("jade.industrialcore.level_and_upgrades", lvl, m, b, s)
                    .withStyle(lb ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        } else if (lvl > 0) {
            tooltip.add(Component.translatable("jade.industrialcore.level_only", lvl)
                    .withStyle(lb ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        } else if (hasUpg && (m > 0 || b > 0 || s > 0)) {
            tooltip.add(Component.translatable("jade.industrialcore.upgrades_only", m, b, s)
                    .withStyle(ChatFormatting.GRAY));
        }

        // 3. Nuclear coolant readout when applicable
        if (data.contains(AegisWorksJadeDataProvider.KEY_COOLANT_MAX)) {
            int mb = data.getInt(AegisWorksJadeDataProvider.KEY_COOLANT_MB);
            int maxMb = data.getInt(AegisWorksJadeDataProvider.KEY_COOLANT_MAX);
            tooltip.add(Component.translatable("jade.industrialcore.coolant", mb, maxMb)
                    .withStyle(mb > 0 ? ChatFormatting.BLUE : ChatFormatting.RED));
        }

        // 4. Clear Running Status / Progress / Overdrive OR Explicit 1-word Stop Reason
        boolean working = data.getBoolean(AegisWorksJadeDataProvider.KEY_WORKING);
        boolean overdrive = data.getBoolean(AegisWorksJadeDataProvider.KEY_OVERDRIVE);
        int cdSec = data.getInt(AegisWorksJadeDataProvider.KEY_COOLDOWN_SEC);
        MachineStopReason stop = MachineStopReason.byNameOrNone(
                data.getString(AegisWorksJadeDataProvider.KEY_STOP_REASON));

        if (cdSec > 0) {
            tooltip.add(Component.translatable("jade.industrialcore.status_cooldown", cdSec)
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else if (working) {
            if (overdrive) {
                tooltip.add(Component.translatable("jade.industrialcore.status_overdrive")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            } else if (data.contains(AegisWorksJadeDataProvider.KEY_PROGRESS_PCT)) {
                int pct = data.getInt(AegisWorksJadeDataProvider.KEY_PROGRESS_PCT);
                tooltip.add(Component.translatable("jade.industrialcore.status_working_pct", pct)
                        .withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(Component.translatable("jade.industrialcore.status_working")
                        .withStyle(ChatFormatting.GREEN));
            }
        } else if (stop.isHalted()) {
            tooltip.add(Component.translatable("jade.industrialcore.status_halted",
                            Component.translatable(stop.translationKey()))
                    .withStyle(stop.isCritical() ? ChatFormatting.RED : ChatFormatting.YELLOW));
        }
    }

    private static String formatFe(long value) {
        if (value >= 1_000_000L) {
            return BigCountFormatter.formatCompact(BigInteger.valueOf(value), BigCountFormatter.UnitMode.ENGLISH);
        }
        return String.format("%,d", value);
    }
}

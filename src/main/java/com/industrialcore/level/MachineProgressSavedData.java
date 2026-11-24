package com.industrialcore.level;

import com.industrialcore.level.MachineSkillTree.Branch;
import com.industrialcore.network.MachineProgressSyncPayload;
import com.industrialcore.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-level persistence for per-player machine mastery progress (Phase 84–97).
 */
public final class MachineProgressSavedData extends SavedData {
    private static final String DATA_NAME = "industrialcore_machine_progress";
    private static final String TAG_PLAYERS = "Players";
    private static final String TAG_LIMIT_BROKEN = "LimitBroken";
    private static final String TAG_LIMIT_BROKEN_BRANCHES = "LimitBrokenBranches";
    private static final String TAG_MACHINES = "Machines";
    private static final String TAG_REVEALED = "RevealedNodes";
    private static final String TAG_UNLOCKED_RIFT_TIER = "UnlockedRiftTier";

    private static final SavedData.Factory<MachineProgressSavedData> FACTORY =
            new SavedData.Factory<>(MachineProgressSavedData::new, MachineProgressSavedData::load);

    private final Map<UUID, PlayerMachineProgress> players = new HashMap<>();

    public MachineProgressSavedData() {}

    public static MachineProgressSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static MachineProgressSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    public PlayerMachineProgress getOrCreate(UUID playerUuid) {
        return players.computeIfAbsent(playerUuid, ignored -> new PlayerMachineProgress());
    }

    @Nullable
    public PlayerMachineProgress getIfPresent(UUID playerUuid) {
        return players.get(playerUuid);
    }

    public int getLevel(UUID playerUuid, String machineId) {
        PlayerMachineProgress progress = players.get(playerUuid);
        return progress == null ? MachineLevelMath.MIN_LEVEL : progress.getLevel(machineId);
    }

    public static int ownerLevel(ServerLevel level, @Nullable UUID ownerUuid, String machineId) {
        if (ownerUuid == null) {
            return 0;
        }
        return get(level).getLevel(ownerUuid, machineId);
    }

    public static int effectiveUpgradeLimit(ServerLevel level, @Nullable UUID ownerUuid,
                                            String machineId, boolean creative) {
        if (ownerUuid == null || creative) {
            return MachineLevelMath.MAX_UPGRADE_CAP;
        }
        int lvl = get(level).getLevel(ownerUuid, machineId);
        return MachineLevelMath.effectiveUpgradeLimit(true, lvl, false);
    }

    /** Phase 113: XP-linked total virtual upgrade limit (4 at Lv.1 -> 96 at Lv.99 -> 1000 at Lv.1000). */
    public static int effectiveTotalUpgradeLimit(ServerLevel level, @Nullable UUID ownerUuid,
                                                 String machineId, boolean creative) {
        if (ownerUuid == null || creative) {
            return MachineLevelMath.LIMIT_BROKEN_MAX_TOTAL_UPGRADE_CAP;
        }
        int lvl = get(level).getLevel(ownerUuid, machineId);
        return com.industrialcore.ConfigBalanceHelper.configuredUpgradeLimit(
                lvl,
                com.industrialcore.Config.baseUpgradeLimit(),
                com.industrialcore.Config.maxStandardUpgradeLimit());
    }

    public static double effectiveMasterySpeed(ServerLevel level, @Nullable UUID ownerUuid, String machineId) {
        if (ownerUuid == null) {
            return 1.0D;
        }
        return MachineLevelMath.masterySpeedMultiplier(get(level).getLevel(ownerUuid, machineId));
    }

    public static double effectiveMasteryEnergy(ServerLevel level, @Nullable UUID ownerUuid, String machineId) {
        if (ownerUuid == null) {
            return 1.0D;
        }
        return MachineLevelMath.masteryEnergyMultiplier(get(level).getLevel(ownerUuid, machineId));
    }

    public static void recordWorkBatch(ServerLevel level, @Nullable UUID ownerUuid,
                                       String machineId, long xpDelta) {
        if (ownerUuid == null || xpDelta <= 0L || !MachineSkillTree.isTracked(machineId)) {
            return;
        }
        double xpMult = com.industrialcore.Config.machineXpMultiplier();
        if (xpMult <= 0.0D) {
            return;
        }
        long scaledXp = Math.max(1L, Math.round(xpDelta * xpMult));
        MachineProgressSavedData data = get(level);
        PlayerMachineProgress progress = data.getOrCreate(ownerUuid);
        long beforeXp = progress.getXp(machineId);
        PlayerMachineProgress.ChangeResult res = progress.addXp(machineId, scaledXp);
        if (res.newXp() != beforeXp || res.justLimitBroke()) {
            data.setDirty();
        }
        if (res.leveledUp() || res.justLimitBroke()) {
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (online != null) {
                syncToPlayer(online, progress);
                notifyProgress(online, machineId, progress, res);
            }
        }
    }

    /** Phase 97: server-authoritative activation of per-branch Lv.1000 Limit Break. */
    public static boolean tryLimitBreakBranch(ServerPlayer player, @Nullable Branch branch) {
        if (branch == null) {
            return false;
        }
        MachineProgressSavedData data = get(player.serverLevel());
        PlayerMachineProgress progress = data.getOrCreate(player.getUUID());
        if (!progress.tryLimitBreakBranch(branch)) {
            player.playNotifySound(ModSounds.UI_LOCKED.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
            return false;
        }
        data.setDirty();
        syncToPlayer(player, progress);
        player.playNotifySound(ModSounds.LIMIT_BREAK.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        player.sendSystemMessage(
                Component.translatable("message.industrialcore.branch_limit_break_unlocked",
                                Component.translatable(branch.translationKey()),
                                MachineLevelMath.LIMIT_BROKEN_MAX_LEVEL)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        return true;
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerMachineProgress progress = get(player.serverLevel()).getOrCreate(player.getUUID());
        syncToPlayer(player, progress);
    }

    public static void syncToPlayer(ServerPlayer player, PlayerMachineProgress progress) {
        PacketDistributor.sendToPlayer(player,
                new MachineProgressSyncPayload(
                        progress.isLimitBroken(),
                        progress.snapshotXp(),
                        progress.snapshotRevealedNodes(),
                        progress.snapshotLimitBrokenBranchIds(),
                        progress.getUnlockedRiftTier()));
    }

    /** Phase 116: Returns the player's highest unlocked Dimensional Rift Raid Tier (1..10). */
    public static int getUnlockedRiftTier(ServerLevel level, @Nullable UUID playerUuid) {
        if (playerUuid == null) {
            return com.industrialcore.raid.DimensionalRaidLogic.MIN_TIER;
        }
        PlayerMachineProgress progress = get(level).getIfPresent(playerUuid);
        return progress == null
                ? com.industrialcore.raid.DimensionalRaidLogic.MIN_TIER
                : progress.getUnlockedRiftTier();
    }

    /**
     * Phase 116: Records a Dimensional Rift Raid clear at {@code clearedTier}.
     * Unlocks {@code Tier + 1} (up to Tier 10) if clearing the player's current max unlocked tier,
     * persists to SavedData, and syncs to the online client.
     *
     * @return the newly unlocked tier if an unlock occurred, or {@code -1} if unchanged
     */
    public static int recordRiftTierClear(ServerLevel level, @Nullable UUID playerUuid, int clearedTier) {
        if (playerUuid == null) {
            return -1;
        }
        MachineProgressSavedData data = get(level);
        PlayerMachineProgress progress = data.getOrCreate(playerUuid);
        boolean unlocked = progress.recordRiftTierCleared(clearedTier);
        if (unlocked) {
            data.setDirty();
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(playerUuid);
            if (online != null) {
                syncToPlayer(online, progress);
            }
            return progress.getUnlockedRiftTier();
        }
        return -1;
    }

    public static void markRevealed(ServerPlayer player, Collection<String> machineIds) {
        if (machineIds == null || machineIds.isEmpty()) {
            return;
        }
        MachineProgressSavedData data = get(player.serverLevel());
        PlayerMachineProgress progress = data.getOrCreate(player.getUUID());
        if (progress.markRevealed(machineIds)) {
            data.setDirty();
            syncToPlayer(player, progress);
        }
    }

    /** True when the owner's branch for {@code machineId} has activated Lv.1000 Limit Break. */
    public static boolean isOwnerBranchLimitBroken(ServerLevel level, @Nullable UUID ownerUuid, String machineId) {
        if (ownerUuid == null) {
            return false;
        }
        MachineProgressSavedData data = get(level);
        PlayerMachineProgress progress = data.players.get(ownerUuid);
        if (progress == null) {
            return false;
        }
        MachineSkillTree.Branch b = MachineSkillTree.branchOf(machineId);
        return b != null ? progress.isBranchLimitBroken(b) : progress.isLimitBroken();
    }

    public static void notifyProgress(ServerPlayer player, String machineId,
                                      PlayerMachineProgress progress,
                                      PlayerMachineProgress.ChangeResult res) {
        if (res.newLevel() >= 50) {
            AegisAdvancements.grant(player, AegisAdvancements.MACHINE_LV50);
        }
        if (res.oldLevel() < 100 && res.newLevel() >= 100) {
            celebrateLimitBreakLv100(player, machineId, res.newLevel());
        }
        if (res.justLimitBroke()) {
            player.sendSystemMessage(
                    Component.translatable("message.industrialcore.limit_break_unlocked",
                            MachineLevelMath.LIMIT_BROKEN_MAX_LEVEL)
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    /** Phase 114: Celebratory Lv.100 Limit-Break sound, golden+cyan firework fountain &amp; action-bar alert. */
    public static void celebrateLimitBreakLv100(ServerPlayer player, String machineId, int level) {
        AegisAdvancements.grant(player, AegisAdvancements.LIMIT_BREAK_LV100);
        player.playNotifySound(ModSounds.LIMIT_BREAK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.playNotifySound(ModSounds.LEVEL_UP.get(), SoundSource.PLAYERS, 0.9F, 1.15F);
        ServerLevel sl = player.serverLevel();
        double px = player.getX();
        double py = player.getY() + 1.1D;
        double pz = player.getZ();
        for (int i = 0; i < 24; i++) {
            double ang = i * (Math.PI * 2.0D / 24.0D);
            double vx = Math.cos(ang) * 0.28D;
            double vz = Math.sin(ang) * 0.28D;
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    px, py, pz, 0, vx, 0.32D, vz, 1.0D);
            if (i % 2 == 0) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                        px, py + 0.2D, pz, 0, vx * 0.9D, 0.24D, vz * 0.9D, 1.0D);
            }
        }
        Component machineName = Component.translatable("block.industrialcore." + machineId);
        player.displayClientMessage(
                Component.translatable("message.industrialcore.limit_break_lv100_reached", machineName, level)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                true);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<UUID, PlayerMachineProgress> entry : players.entrySet()) {
            PlayerMachineProgress progress = entry.getValue();
            Map<String, Long> snapshot = progress.snapshotXp();
            Set<String> revealed = progress.snapshotRevealedNodes();
            Set<String> lbBranches = progress.snapshotLimitBrokenBranchIds();
            if (!progress.isLimitBroken() && snapshot.isEmpty() && revealed.size() <= 6
                    && progress.getUnlockedRiftTier() <= 1) {
                continue;
            }
            CompoundTag pTag = new CompoundTag();
            pTag.putBoolean(TAG_LIMIT_BROKEN, progress.isLimitBroken());
            pTag.putInt(TAG_UNLOCKED_RIFT_TIER, progress.getUnlockedRiftTier());
            ListTag brList = new ListTag();
            for (String b : lbBranches) {
                brList.add(StringTag.valueOf(b));
            }
            pTag.put(TAG_LIMIT_BROKEN_BRANCHES, brList);

            CompoundTag machinesTag = new CompoundTag();
            for (Map.Entry<String, Long> xpEntry : snapshot.entrySet()) {
                machinesTag.putLong(xpEntry.getKey(), xpEntry.getValue());
            }
            pTag.put(TAG_MACHINES, machinesTag);
            ListTag revList = new ListTag();
            for (String id : revealed) {
                revList.add(StringTag.valueOf(id));
            }
            pTag.put(TAG_REVEALED, revList);
            playersTag.put(entry.getKey().toString(), pTag);
        }
        tag.put(TAG_PLAYERS, playersTag);
        return tag;
    }

    public static MachineProgressSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MachineProgressSavedData data = new MachineProgressSavedData();
        CompoundTag playersTag = tag.getCompound(TAG_PLAYERS);
        for (String uuidKey : playersTag.getAllKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            CompoundTag pTag = playersTag.getCompound(uuidKey);
            boolean limitBroken = pTag.getBoolean(TAG_LIMIT_BROKEN);
            List<String> lbBranches = null;
            if (pTag.contains(TAG_LIMIT_BROKEN_BRANCHES, Tag.TAG_LIST)) {
                ListTag brList = pTag.getList(TAG_LIMIT_BROKEN_BRANCHES, Tag.TAG_STRING);
                lbBranches = new ArrayList<>(brList.size());
                for (int i = 0; i < brList.size(); i++) {
                    String b = brList.getString(i);
                    if (!b.isEmpty()) {
                        lbBranches.add(b);
                    }
                }
            }
            CompoundTag machinesTag = pTag.getCompound(TAG_MACHINES);
            Map<String, Long> xpMap = new LinkedHashMap<>();
            for (String machineId : machinesTag.getAllKeys()) {
                long xp = machinesTag.getLong(machineId);
                if (xp > 0L) {
                    xpMap.put(machineId, xp);
                }
            }
            List<String> revealed = null;
            if (pTag.contains(TAG_REVEALED, Tag.TAG_LIST)) {
                ListTag revList = pTag.getList(TAG_REVEALED, Tag.TAG_STRING);
                revealed = new ArrayList<>(revList.size());
                for (int i = 0; i < revList.size(); i++) {
                    String id = revList.getString(i);
                    if (!id.isEmpty()) {
                        revealed.add(id);
                    }
                }
            }
            PlayerMachineProgress restored = PlayerMachineProgress.fromSnapshot(limitBroken, xpMap, revealed, lbBranches);
            if (pTag.contains(TAG_UNLOCKED_RIFT_TIER)) {
                restored.setUnlockedRiftTier(pTag.getInt(TAG_UNLOCKED_RIFT_TIER));
            }
            data.players.put(uuid, restored);
        }
        return data;
    }
}

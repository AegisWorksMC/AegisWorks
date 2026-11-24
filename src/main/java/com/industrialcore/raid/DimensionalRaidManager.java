package com.industrialcore.raid;

import com.industrialcore.Config;
import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.SingularityReactorBlockEntity;
import com.industrialcore.level.AegisAdvancements;
import com.industrialcore.level.MachineProgressSavedData;
import com.industrialcore.network.WeaponBeamFxPayload;
import com.industrialcore.raid.DimensionalRaidLogic.RewardBundle;
import com.industrialcore.raid.DimensionalRaidLogic.SpawnOffset;
import com.industrialcore.raid.DimensionalRaidLogic.StartDenial;
import com.industrialcore.raid.DimensionalRaidLogic.WaveSpec;
import com.industrialcore.raid.RiftArenaLogic.PillarSpec;
import com.industrialcore.raid.RiftArenaLogic.ReturnPoint;
import com.industrialcore.raid.entity.AbstractRiftInvaderEntity;
import com.industrialcore.raid.entity.RiftAnchorCrystalEntity;
import com.industrialcore.registry.ModEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 115 / 116 / 119 / 120: Server-authoritative runtime manager for voluntary Tier 1..10
 * "Dimensional Rift Raids" hosted inside the dedicated Void Arena dimension
 * ({@code industrialcore:rift_arena}).
 *
 * <p>Handles persistent return-coordinate saving, 1024-block-strided arena generation,
 * enemy-favored terrain spawns, live {@code MM:SS} countdown timer with 30s warning flash,
 * void-fall 15%-HP rescue, 10s victory return grace, and zero-residue despawn on timeout/wipe/relog.</p>
 */
public final class DimensionalRaidManager {
    public static final ResourceKey<Level> RIFT_ARENA_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, "rift_arena"));

    private static final double PARTICIPANT_RADIUS = 24.0D;
    private static final double ARENA_ENTITY_RADIUS = 64.0D;

    private static final Map<ResourceKey<Level>, Map<BlockPos, ActiveRaid>> ACTIVE = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> COOLDOWN_UNTIL = new HashMap<>();

    private DimensionalRaidManager() {}

    public static boolean isRaidActiveAt(ServerLevel level, BlockPos reactorPos) {
        for (Map<BlockPos, ActiveRaid> map : ACTIVE.values()) {
            for (ActiveRaid r : map.values()) {
                if (r.sourceLevelKey.equals(level.dimension()) && r.sourceReactorPos.equals(reactorPos)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int remainingCooldownTicks(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Long> map = COOLDOWN_UNTIL.get(level.dimension());
        if (map == null) {
            return 0;
        }
        Long until = map.get(pos);
        if (until == null) {
            return 0;
        }
        long rem = until - level.getGameTime();
        return rem > 0L ? (int) Math.min(Integer.MAX_VALUE, rem) : 0;
    }

    /** Convenience Tier 1 overload. */
    public static StartDenial tryStartRaid(ServerLevel level, BlockPos reactorPos, @Nullable ServerPlayer initiator) {
        return tryStartRaid(level, reactorPos, initiator, DimensionalRaidLogic.MIN_TIER);
    }

    /** Attempts to launch a voluntary Dimensional Rift Raid into {@code industrialcore:rift_arena}. */
    public static StartDenial tryStartRaid(ServerLevel sourceLevel, BlockPos reactorPos,
                                           @Nullable ServerPlayer initiator, int selectedTier) {
        if (!(sourceLevel.getBlockEntity(reactorPos) instanceof SingularityReactorBlockEntity reactor)) {
            return StartDenial.NOT_FORMED;
        }
        int safeTier = DimensionalRaidLogic.clampTier(selectedTier);
        UUID ownerId = initiator != null ? initiator.getUUID() : reactor.getOwnerUuid();
        int unlockedMax = (initiator != null && initiator.isCreative())
                ? DimensionalRaidLogic.MAX_TIER
                : MachineProgressSavedData.getUnlockedRiftTier(sourceLevel, ownerId);

        int cd = remainingCooldownTicks(sourceLevel, reactorPos);
        long cost = DimensionalRaidLogic.activationCostForTier(Config.raidActivationCostFe(), safeTier);
        StartDenial check = DimensionalRaidLogic.checkStart(
                Config.enableDimensionalRaid(),
                reactor.isMultiblockFormed(),
                isRaidActiveAt(sourceLevel, reactorPos),
                cd,
                reactor.getStoredEnergyLong(),
                cost,
                safeTier,
                unlockedMax);
        if (check != StartDenial.ALLOWED) {
            if (initiator != null) {
                initiator.playNotifySound(ModSounds.UI_LOCKED.get(), SoundSource.PLAYERS, 0.75F, 0.85F);
                initiator.displayClientMessage(
                        denialMessage(check, cd, cost, safeTier, unlockedMax).withStyle(ChatFormatting.RED), true);
            }
            return check;
        }

        // Consume Tier-scaled FE activation cost
        reactor.setStoredEnergyLong(reactor.getStoredEnergyLong() - cost);
        BlockPos srcPos = reactorPos.immutable();

        MinecraftServer server = sourceLevel.getServer();
        ServerLevel arenaLevel = server.getLevel(RIFT_ARENA_KEY);
        boolean inDedicatedArena = (arenaLevel != null);
        ServerLevel battleLevel = inDedicatedArena ? arenaLevel : sourceLevel;

        RiftArenaSavedData saved = RiftArenaSavedData.get(server);
        BlockPos arenaCenter;
        if (inDedicatedArena) {
            int instanceIdx = saved.allocateInstanceIndex();
            int cx = RiftArenaLogic.arenaCenterX(instanceIdx);
            arenaCenter = new BlockPos(cx, RiftArenaLogic.ARENA_Y, 0);
            RiftArenaGenerator.generateArena(battleLevel, arenaCenter.getX(), arenaCenter.getY(), arenaCenter.getZ());
        } else {
            arenaCenter = srcPos;
        }

        // Gather participants around the Singularity Reactor & persistently store return coordinates
        List<ServerPlayer> participants = new ArrayList<>(nearbyPlayers(sourceLevel, srcPos, PARTICIPANT_RADIUS));
        if (initiator != null && !participants.contains(initiator)) {
            participants.add(initiator);
        }
        Set<UUID> participantIds = new HashSet<>();
        for (ServerPlayer sp : participants) {
            participantIds.add(sp.getUUID());
            if (inDedicatedArena) {
                saved.saveReturnPoint(sp.getUUID(), new ReturnPoint(
                        sp.serverLevel().dimension().location().toString(),
                        sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), sp.getXRot()));
                sp.teleportTo(battleLevel,
                        arenaCenter.getX() + 0.5D, arenaCenter.getY() + 1.15D, arenaCenter.getZ() + 0.5D,
                        sp.getYRot(), sp.getXRot());
                sp.setDeltaMovement(0.0D, 0.0D, 0.0D);
                sp.fallDistance = 0.0F;
            }
        }

        ActiveRaid raid = new ActiveRaid(
                sourceLevel.dimension(), srcPos, arenaCenter, ownerId, participantIds,
                safeTier, Config.raidDifficultyMultiplier(), inDedicatedArena);
        ACTIVE.computeIfAbsent(battleLevel.dimension(), ignored -> new HashMap<>()).put(arenaCenter, raid);

        battleLevel.playSound(null, arenaCenter, ModSounds.RIFT_PORTAL_HORN.get(), SoundSource.HOSTILE, 1.0F, 0.94F);
        Component startMsg = Component.translatable("message.industrialcore.rift_raid_started_tier", safeTier)
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
        for (ServerPlayer sp : participants) {
            sp.displayClientMessage(startMsg, true);
        }

        raid.spawnWave(battleLevel, 1);
        return StartDenial.ALLOWED;
    }

    /** Safely returns a player to their persistently saved Overworld/original coordinates. */
    public static void returnPlayerSafely(MinecraftServer server, ServerPlayer sp) {
        RiftArenaSavedData saved = RiftArenaSavedData.get(server);
        ReturnPoint pt = saved.consumeReturnPoint(sp.getUUID());
        ServerLevel dest = server.overworld();
        double x = dest.getSharedSpawnPos().getX() + 0.5D;
        double y = dest.getSharedSpawnPos().getY() + 1.0D;
        double z = dest.getSharedSpawnPos().getZ() + 0.5D;
        float yRot = sp.getYRot();
        float xRot = sp.getXRot();

        if (pt != null) {
            ResourceLocation dimLoc = ResourceLocation.tryParse(pt.dimensionId());
            if (dimLoc != null) {
                ServerLevel targetLvl = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
                if (targetLvl != null && !targetLvl.dimension().equals(RIFT_ARENA_KEY)) {
                    dest = targetLvl;
                }
            }
            x = pt.x();
            y = pt.y();
            z = pt.z();
            yRot = pt.yRot();
            xRot = pt.xRot();
        }
        sp.teleportTo(dest, x, y, z, yRot, xRot);
        sp.setDeltaMovement(0.0D, 0.0D, 0.0D);
        sp.fallDistance = 0.0F;
    }

    /** Relog safety rescue: if a player rejoins inside {@code rift_arena} without an active raid, send them home! */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (!sp.serverLevel().dimension().equals(RIFT_ARENA_KEY)) {
            return;
        }
        boolean inActive = false;
        Map<BlockPos, ActiveRaid> arenaRaids = ACTIVE.get(RIFT_ARENA_KEY);
        if (arenaRaids != null) {
            for (ActiveRaid r : arenaRaids.values()) {
                if (r.participantIds.contains(sp.getUUID())) {
                    inActive = true;
                    r.bossEvent.removePlayer(sp);
                    r.bossEvent.addPlayer(sp);
                    break;
                }
            }
        }
        if (!inActive) {
            returnPlayerSafely(sp.getServer(), sp);
        }
    }

    /** Re-attaches active Void Arena countdown BossBars cleanly after a client finishes dimension respawn. */
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (!event.getTo().equals(RIFT_ARENA_KEY)) {
            return;
        }
        Map<BlockPos, ActiveRaid> arenaRaids = ACTIVE.get(RIFT_ARENA_KEY);
        if (arenaRaids == null) {
            return;
        }
        for (ActiveRaid r : arenaRaids.values()) {
            if (r.participantIds.contains(sp.getUUID())
                    || sp.distanceToSqr(r.arenaCenter.getX() + 0.5D, r.arenaCenter.getY(), r.arenaCenter.getZ() + 0.5D)
                    <= ARENA_ENTITY_RADIUS * ARENA_ENTITY_RADIUS) {
                r.bossEvent.removePlayer(sp);
                r.bossEvent.addPlayer(sp);
            }
        }
    }

    private static MutableComponent denialMessage(StartDenial denial, int cdTicks, long cost,
                                                  int selectedTier, int unlockedMax) {
        return switch (denial) {
            case DISABLED_BY_CONFIG -> Component.translatable("message.industrialcore.rift_raid_disabled");
            case NOT_FORMED -> Component.translatable("message.industrialcore.rift_raid_not_formed");
            case ALREADY_ACTIVE -> Component.translatable("message.industrialcore.rift_raid_already_active");
            case ON_COOLDOWN -> Component.translatable("message.industrialcore.rift_raid_cooldown",
                    Math.max(1, (cdTicks + 19) / 20));
            case TIER_LOCKED -> Component.translatable("message.industrialcore.rift_raid_tier_locked",
                    selectedTier, unlockedMax);
            case INSUFFICIENT_ENERGY -> Component.translatable("message.industrialcore.rift_raid_no_energy",
                    String.format("%,d", cost));
            case ALLOWED -> Component.empty();
        };
    }

    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, ActiveRaid> map = ACTIVE.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, ActiveRaid>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ActiveRaid> e = it.next();
            ActiveRaid raid = e.getValue();
            if (!raid.tick(level)) {
                raid.cleanupAndReturn(level);
                COOLDOWN_UNTIL.computeIfAbsent(raid.sourceLevelKey, ignored -> new HashMap<>())
                        .put(raid.sourceReactorPos, level.getServer().overworld().getGameTime() + DimensionalRaidLogic.COOLDOWN_TICKS);
                it.remove();
            }
        }
    }

    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Entity src = event.getExplosion().getDirectSourceEntity();
        Entity indirect = event.getExplosion().getIndirectSourceEntity();
        if (isInvader(src) || isInvader(indirect)) {
            event.getAffectedBlocks().clear();
        }
    }

    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (isInvader(event.getEntity())) {
            event.setCanGrief(false);
        }
    }

    public static boolean isInvader(@Nullable Entity entity) {
        return entity != null
                && (entity instanceof AbstractRiftInvaderEntity
                    || entity instanceof RiftAnchorCrystalEntity
                    || entity.getTags().contains(DimensionalRaidLogic.INVADER_TAG));
    }

    public static List<Mob> getActiveInvaders(ServerLevel level, BlockPos pos) {
        Map<BlockPos, ActiveRaid> map = ACTIVE.get(level.dimension());
        if (map == null) {
            return List.of();
        }
        ActiveRaid raid = map.get(pos);
        if (raid == null) {
            return List.of();
        }
        List<Mob> list = new ArrayList<>();
        for (UUID id : raid.invaderIds) {
            if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
                list.add(m);
            }
        }
        return list;
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            Map<BlockPos, ActiveRaid> map = ACTIVE.remove(sl.dimension());
            if (map != null) {
                for (ActiveRaid r : map.values()) {
                    r.bossEvent.removeAllPlayers();
                }
            }
            COOLDOWN_UNTIL.remove(sl.dimension());
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        for (Map<BlockPos, ActiveRaid> map : ACTIVE.values()) {
            for (ActiveRaid r : map.values()) {
                r.bossEvent.removeAllPlayers();
            }
        }
        ACTIVE.clear();
        COOLDOWN_UNTIL.clear();
    }

    private static List<ServerPlayer> nearbyPlayers(ServerLevel level, BlockPos pos, double radius) {
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(radius));
    }

    /** Encapsulates one active 3-wave Tiered Rift Raid inside {@code industrialcore:rift_arena}. */
    private static final class ActiveRaid {
        final ResourceKey<Level> sourceLevelKey;
        final BlockPos sourceReactorPos;
        final BlockPos arenaCenter;
        @Nullable
        final UUID initiatorUuid;
        final Set<UUID> participantIds;
        final int tier;
        final double diffMult;
        final boolean inDedicatedArena;
        final ServerBossEvent bossEvent;
        final BossEvent.BossBarColor baseBarColor;
        final Set<UUID> invaderIds = new HashSet<>();

        int currentWave = 0;
        int waveTotal = 1;
        int nextWaveDelay = 0;
        int ageTicks = 0;
        int remainingTicks;
        int victoryGraceTicks = -1;
        int emptyArenaTicks = 0;

        ActiveRaid(ResourceKey<Level> sourceLevelKey, BlockPos sourceReactorPos, BlockPos arenaCenter,
                   @Nullable UUID initiatorUuid, Set<UUID> participantIds,
                   int tier, double diffMult, boolean inDedicatedArena) {
            this.sourceLevelKey = sourceLevelKey;
            this.sourceReactorPos = sourceReactorPos;
            this.arenaCenter = arenaCenter;
            this.initiatorUuid = initiatorUuid;
            this.participantIds = participantIds;
            this.tier = tier;
            this.diffMult = diffMult;
            this.inDedicatedArena = inDedicatedArena;
            this.remainingTicks = RiftArenaLogic.timeLimitTicks(tier);
            this.baseBarColor = tier >= 6
                    ? BossEvent.BossBarColor.RED
                    : (tier >= 3 ? BossEvent.BossBarColor.PINK : BossEvent.BossBarColor.PURPLE);
            this.bossEvent = new ServerBossEvent(
                    Component.translatable("bossbar.industrialcore.rift_raid_arena",
                            tier, 1, DimensionalRaidLogic.TOTAL_WAVES,
                            RiftArenaLogic.formatCountdown(remainingTicks), 0),
                    baseBarColor,
                    BossEvent.BossBarOverlay.NOTCHED_10);
            this.bossEvent.setDarkenScreen(false);
        }

        boolean tick(ServerLevel level) {
            // 1. Void-fall 15%-HP non-lethal safety rescue for all participants in arena
            List<ServerPlayer> liveArenaPlayers = new ArrayList<>();
            for (ServerPlayer sp : level.players()) {
                if (sp.distanceToSqr(arenaCenter.getX() + 0.5D, arenaCenter.getY(), arenaCenter.getZ() + 0.5D)
                        <= ARENA_ENTITY_RADIUS * ARENA_ENTITY_RADIUS) {
                    if (sp.isAlive()) {
                        liveArenaPlayers.add(sp);
                        if (inDedicatedArena && sp.getY() < RiftArenaLogic.VOID_RESCUE_Y) {
                            float pen = RiftArenaLogic.voidFallPenaltyDamage(sp.getHealth(), sp.getMaxHealth());
                            if (pen > 0.0F) {
                                sp.hurt(level.damageSources().outOfBorder(), pen);
                            }
                            sp.teleportTo(level,
                                    arenaCenter.getX() + 0.5D, arenaCenter.getY() + 1.5D, arenaCenter.getZ() + 0.5D,
                                    sp.getYRot(), sp.getXRot());
                            sp.setDeltaMovement(0.0D, 0.0D, 0.0D);
                            sp.fallDistance = 0.0F;
                            sp.displayClientMessage(Component.translatable("message.industrialcore.rift_arena_void_rescue")
                                    .withStyle(ChatFormatting.YELLOW), true);
                        }
                    }
                }
            }

            // 2. Post-victory 10s loot collection countdown before safe return
            if (victoryGraceTicks >= 0) {
                syncBossBarPlayers(level);
                bossEvent.setColor(BossEvent.BossBarColor.GREEN);
                bossEvent.setProgress(victoryGraceTicks / (float) RiftArenaLogic.VICTORY_RETURN_GRACE_TICKS);
                bossEvent.setName(Component.translatable("bossbar.industrialcore.rift_raid_victory_return",
                        Math.max(1, (victoryGraceTicks + 19) / 20)));
                victoryGraceTicks--;
                return victoryGraceTicks >= 0;
            }

            // 3. Wipe / empty arena guard (after 40 ticks grace for teleport arrival)
            if (inDedicatedArena && !participantIds.isEmpty()) {
                if (liveArenaPlayers.isEmpty()) {
                    emptyArenaTicks++;
                    if (emptyArenaTicks >= 60) {
                        return false;
                    }
                } else {
                    emptyArenaTicks = 0;
                }
            }

            // 4. Countdown Timer & 30-second Red Flash Warning!
            ageTicks++;
            remainingTicks--;
            if (RiftArenaLogic.isTimeExpired(remainingTicks)) {
                Component msg = Component.translatable("message.industrialcore.rift_raid_timeout")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                for (ServerPlayer sp : liveArenaPlayers) {
                    sp.displayClientMessage(msg, true);
                    sp.sendSystemMessage(msg);
                    sp.playNotifySound(ModSounds.UI_LOCKED.get(), SoundSource.MASTER, 1.0F, 0.65F);
                }
                return false;
            }
            if (RiftArenaLogic.isWarningPeriod(remainingTicks)) {
                bossEvent.setColor(((remainingTicks / 10) % 2 == 0)
                        ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
                if (remainingTicks % 20 == 0) {
                    for (ServerPlayer sp : liveArenaPlayers) {
                        sp.playNotifySound(ModSounds.WEAPON_LOCKON.get(), SoundSource.MASTER, 0.65F, 1.35F);
                    }
                }
            } else {
                bossEvent.setColor(baseBarColor);
            }

            if (inDedicatedArena && (ageTicks == 12 || ageTicks == 25)) {
                bossEvent.removeAllPlayers();
                syncBossBarPlayers(level);
            } else if (remainingTicks % 10 == 0 && (!inDedicatedArena || ageTicks >= 12)) {
                syncBossBarPlayers(level);
            }

            if (nextWaveDelay > 0) {
                nextWaveDelay--;
                bossEvent.setName(Component.translatable("bossbar.industrialcore.rift_raid_arena",
                        tier, currentWave, DimensionalRaidLogic.TOTAL_WAVES,
                        RiftArenaLogic.formatCountdown(remainingTicks), 0));
                if (nextWaveDelay == 0) {
                    spawnWave(level, currentWave + 1);
                }
                return true;
            }

            // 5. Track live invaders
            int alive = 0;
            Iterator<UUID> it = invaderIds.iterator();
            while (it.hasNext()) {
                Entity ent = level.getEntity(it.next());
                if (!(ent instanceof Mob mob) || !mob.isAlive()) {
                    it.remove();
                    continue;
                }
                alive++;
                if (remainingTicks % 40 == 0 && mob.getTarget() == null && !liveArenaPlayers.isEmpty()) {
                    mob.setTarget(liveArenaPlayers.get(0));
                }
            }

            bossEvent.setName(Component.translatable("bossbar.industrialcore.rift_raid_arena",
                    tier, currentWave, DimensionalRaidLogic.TOTAL_WAVES,
                    RiftArenaLogic.formatCountdown(remainingTicks), alive));
            bossEvent.setProgress(DimensionalRaidLogic.bossBarProgress(alive, waveTotal));

            if (alive == 0) {
                if (currentWave < DimensionalRaidLogic.TOTAL_WAVES) {
                    nextWaveDelay = DimensionalRaidLogic.WAVE_DELAY_TICKS;
                    level.playSound(null, arenaCenter, ModSounds.MACHINE_QUANTUM_CHIME.get(),
                            SoundSource.HOSTILE, 0.9F, 1.15F);
                    Component nextMsg = Component.translatable("message.industrialcore.rift_raid_next_wave",
                                    currentWave + 1, DimensionalRaidLogic.TOTAL_WAVES)
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
                    for (ServerPlayer sp : liveArenaPlayers) {
                        sp.displayClientMessage(nextMsg, true);
                    }
                    return true;
                } else {
                    grantVictory(level, liveArenaPlayers);
                    victoryGraceTicks = inDedicatedArena ? RiftArenaLogic.VICTORY_RETURN_GRACE_TICKS : 0;
                    return inDedicatedArena;
                }
            }
            return true;
        }

        void spawnWave(ServerLevel level, int waveNum) {
            currentWave = waveNum;
            WaveSpec spec = DimensionalRaidLogic.waveSpec(waveNum, tier);
            waveTotal = spec.totalCount();
            invaderIds.clear();

            level.playSound(null, arenaCenter, ModSounds.RIFT_PORTAL_HORN.get(),
                    SoundSource.HOSTILE, 1.0F, 0.94F + (waveNum - 1) * 0.04F);

            List<SpawnOffset> offsets = DimensionalRaidLogic.computeSpawnRing(spec.totalCount(), waveNum * 7 + tier);
            int idx = 0;

            for (int i = 0; i < spec.stalkerCount(); i++) {
                spawnRoleInArena(level, InvaderRole.RIFT_STALKER, i, offsets.get(idx++));
            }
            for (int i = 0; i < spec.sentinelCount(); i++) {
                spawnRoleInArena(level, InvaderRole.VOID_SENTINEL, i, offsets.get(idx++));
            }
            for (int i = 0; i < spec.juggernautCount(); i++) {
                spawnRoleInArena(level, InvaderRole.PHASE_JUGGERNAUT, i, offsets.get(idx++));
            }
            if (spec.hasBoss()) {
                spawnRoleInArena(level, InvaderRole.DIMENSIONAL_SOVEREIGN, 0, offsets.get(idx));
                level.playSound(null, arenaCenter, ModSounds.SOVEREIGN_ROAR_OVERDRIVE.get(),
                        SoundSource.HOSTILE, 1.0F, 0.88F);
                Component bossMsg = Component.translatable("message.industrialcore.rift_raid_sovereign_spawn", tier)
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
                for (ServerPlayer sp : nearbyPlayers(level, arenaCenter, ARENA_ENTITY_RADIUS)) {
                    sp.displayClientMessage(bossMsg, true);
                }
            }

            if (!inDedicatedArena || ageTicks >= 12) {
                syncBossBarPlayers(level);
            }
            bossEvent.setName(Component.translatable("bossbar.industrialcore.rift_raid_arena",
                    tier, currentWave, DimensionalRaidLogic.TOTAL_WAVES,
                    RiftArenaLogic.formatCountdown(remainingTicks), invaderIds.size()));
            bossEvent.setProgress(1.0F);
        }

        private void spawnRoleInArena(ServerLevel level, InvaderRole role, int roleIdx, SpawnOffset fallback) {
            double sx, sy, sz;
            if (inDedicatedArena) {
                switch (role) {
                    case VOID_SENTINEL -> {
                        // Enemy-favored high ground: hover directly above the 4 elevated sniper pillars (+6..+9)!
                        PillarSpec p = RiftArenaLogic.SNIPER_PILLARS.get(roleIdx % RiftArenaLogic.SNIPER_PILLARS.size());
                        sx = arenaCenter.getX() + p.dx() + 0.5D;
                        sy = arenaCenter.getY() + p.height() + 2.2D;
                        sz = arenaCenter.getZ() + p.dz() + 0.5D;
                    }
                    case PHASE_JUGGERNAUT -> {
                        // Enemy-favored chokepoint: block the 4 narrow stone bridges!
                        int[][] bridges = {{0, -16}, {0, 16}, {16, 0}, {-16, 0}};
                        int[] b = bridges[roleIdx % bridges.length];
                        sx = arenaCenter.getX() + b[0] + 0.5D;
                        sy = arenaCenter.getY() + 1.1D;
                        sz = arenaCenter.getZ() + b[1] + 0.5D;
                    }
                    case DIMENSIONAL_SOVEREIGN -> {
                        sx = arenaCenter.getX() + 0.5D;
                        sy = arenaCenter.getY() + 3.8D;
                        sz = arenaCenter.getZ() - 15.5D;
                    }
                    default -> {
                        // Rift Stalkers ambush from the outer obsidian ring
                        double ang = (roleIdx * Math.PI * 2.0D / Math.max(1, waveTotal)) + currentWave;
                        sx = arenaCenter.getX() + Math.cos(ang) * 24.5D + 0.5D;
                        sy = arenaCenter.getY() + 1.1D;
                        sz = arenaCenter.getZ() + Math.sin(ang) * 24.5D + 0.5D;
                    }
                }
            } else {
                sx = arenaCenter.getX() + fallback.dx() + 0.5D;
                sy = arenaCenter.getY() + (role == InvaderRole.VOID_SENTINEL || role == InvaderRole.DIMENSIONAL_SOVEREIGN ? 2.5D : 1.0D);
                sz = arenaCenter.getZ() + fallback.dz() + 0.5D;
            }

            AbstractRiftInvaderEntity mob = switch (role) {
                case RIFT_STALKER -> ModEntities.RIFT_STALKER.get().create(level);
                case VOID_SENTINEL -> ModEntities.VOID_SENTINEL.get().create(level);
                case PHASE_JUGGERNAUT -> ModEntities.PHASE_JUGGERNAUT.get().create(level);
                case DIMENSIONAL_SOVEREIGN -> ModEntities.DIMENSIONAL_SOVEREIGN.get().create(level);
            };
            if (mob == null) {
                return;
            }
            mob.moveTo(sx, sy, sz, level.random.nextFloat() * 360.0F, 0.0F);
            mob.configureForTier(tier, diffMult, arenaCenter);
            if (role == InvaderRole.DIMENSIONAL_SOVEREIGN) {
                mob.setCustomName(Component.translatable("entity.industrialcore.dimensional_sovereign")
                        .append(Component.literal(" [Tier " + tier + "]"))
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
                mob.setCustomNameVisible(true);
            }
            level.addFreshEntity(mob);
            invaderIds.add(mob.getUUID());

            boolean isBoss = (role == InvaderRole.DIMENSIONAL_SOVEREIGN);
            PacketDistributor.sendToPlayersNear(level, null, sx, sy, sz, 96.0D,
                    new WeaponBeamFxPayload(
                            WeaponBeamFxPayload.KIND_RIFT_PORTAL_VORTEX,
                            sx, arenaCenter.getY() + 1.05D, sz,
                            sx, sy + (isBoss ? 10.0D : 5.5D), sz,
                            isBoss ? 0xFFD84DFF : 0xFFB84DFF,
                            isBoss ? 40 : 26,
                            isBoss ? 4.4F : 2.2F,
                            isBoss ? 1.0F : 0.45F,
                            isBoss));
        }

        private void grantVictory(ServerLevel level, List<ServerPlayer> livePlayers) {
            RewardBundle rew = DimensionalRaidLogic.victoryRewards(tier, diffMult);
            level.playSound(null, arenaCenter, ModSounds.RIFT_RAID_VICTORY_FANFARE.get(),
                    SoundSource.BLOCKS, 1.0F, 1.00F);
            for (ServerPlayer sp : livePlayers) {
                sp.playNotifySound(ModSounds.RIFT_RAID_VICTORY_FANFARE.get(), SoundSource.MASTER, 0.95F, 1.00F);
            }

            double cx = arenaCenter.getX() + 0.5D;
            double cy = arenaCenter.getY() + 2.0D;
            double cz = arenaCenter.getZ() + 0.5D;
            for (int i = 0; i < 32; i++) {
                double ang = i * (Math.PI * 2.0D / 32.0D);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, cx, cy, cz, 0,
                        Math.cos(ang) * 0.34D, 0.38D, Math.sin(ang) * 0.34D, 1.0D);
            }

            List<ItemStack> drops = List.of(
                    new ItemStack(ModItems.SPECIALIZATION_MODULE.get(), rew.specializationModules()),
                    new ItemStack(ModItems.PHASE_CORE.get(), rew.phaseCores()),
                    new ItemStack(ModItems.QUANTUM_SHARD.get(), rew.quantumShards()),
                    new ItemStack(ModItems.NEBULA_CRYSTAL.get(), rew.nebulaCrystals()),
                    new ItemStack(Items.DIAMOND, rew.diamonds())
            );
            for (ItemStack st : drops) {
                ItemEntity ie = new ItemEntity(level, cx, cy, cz, st);
                ie.setGlowingTag(true);
                ie.setDefaultPickUpDelay();
                level.addFreshEntity(ie);
            }

            ServerLevel overworld = level.getServer().overworld();
            if (initiatorUuid != null) {
                MachineProgressSavedData.recordWorkBatch(
                        overworld, initiatorUuid, SingularityReactorBlockEntity.MACHINE_ID, rew.machineXp());
                MachineProgressSavedData.recordRiftTierClear(overworld, initiatorUuid, tier);
            }
            Component winMsg = Component.translatable("message.industrialcore.rift_raid_victory_tier",
                            tier, rew.specializationModules(), String.format("%,d", rew.machineXp()))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            for (ServerPlayer sp : livePlayers) {
                sp.displayClientMessage(winMsg, true);
                sp.sendSystemMessage(winMsg);
                AegisAdvancements.grant(sp, AegisAdvancements.DIMENSION_DEFENDER);
                int unlockedNext = -1;
                if (initiatorUuid == null || !initiatorUuid.equals(sp.getUUID())) {
                    MachineProgressSavedData.recordWorkBatch(
                            overworld, sp.getUUID(), SingularityReactorBlockEntity.MACHINE_ID, rew.machineXp());
                    unlockedNext = MachineProgressSavedData.recordRiftTierClear(overworld, sp.getUUID(), tier);
                } else {
                    int maxNow = MachineProgressSavedData.getUnlockedRiftTier(overworld, sp.getUUID());
                    if (maxNow == tier + 1) {
                        unlockedNext = maxNow;
                    }
                }
                if (unlockedNext > 0) {
                    sp.sendSystemMessage(Component.translatable("message.industrialcore.rift_tier_unlocked", unlockedNext)
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
                }
            }
        }

        private void syncBossBarPlayers(ServerLevel level) {
            double maxDistSq = ARENA_ENTITY_RADIUS * ARENA_ENTITY_RADIUS;
            double cx = arenaCenter.getX() + 0.5D;
            double cy = arenaCenter.getY();
            double cz = arenaCenter.getZ() + 0.5D;
            Set<ServerPlayer> nearSet = new HashSet<>();
            for (ServerPlayer sp : level.players()) {
                if (participantIds.contains(sp.getUUID()) || sp.distanceToSqr(cx, cy, cz) <= maxDistSq) {
                    nearSet.add(sp);
                }
            }
            for (ServerPlayer existing : new ArrayList<>(bossEvent.getPlayers())) {
                if (!nearSet.contains(existing)) {
                    bossEvent.removePlayer(existing);
                }
            }
            for (ServerPlayer sp : nearSet) {
                bossEvent.addPlayer(sp);
            }
        }

        void cleanupAndReturn(ServerLevel level) {
            bossEvent.removeAllPlayers();
            // Purge ALL lingering invaders & anchor crystals in this arena instance!
            AABB arenaBox = new AABB(arenaCenter).inflate(ARENA_ENTITY_RADIUS);
            for (Entity ent : level.getEntities(null, arenaBox)) {
                if (ent instanceof AbstractRiftInvaderEntity || ent instanceof RiftAnchorCrystalEntity) {
                    ent.discard();
                }
            }
            invaderIds.clear();

            if (inDedicatedArena) {
                MinecraftServer server = level.getServer();
                for (UUID pid : participantIds) {
                    ServerPlayer sp = server.getPlayerList().getPlayer(pid);
                    if (sp != null && sp.serverLevel().dimension().equals(RIFT_ARENA_KEY)) {
                        returnPlayerSafely(server, sp);
                    }
                }
            }
        }
    }

    private enum InvaderRole {
        RIFT_STALKER,
        VOID_SENTINEL,
        PHASE_JUGGERNAUT,
        DIMENSIONAL_SOVEREIGN
    }
}

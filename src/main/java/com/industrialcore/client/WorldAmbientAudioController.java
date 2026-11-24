package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.blockentity.SingularityReactorBlockEntity;
import com.industrialcore.client.WorldAmbientLogic.Candidate;
import com.industrialcore.client.WorldAmbientLogic.Category;
import com.industrialcore.client.WorldAmbientLogic.Selection;
import com.industrialcore.network.BeatPayload;
import com.industrialcore.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 102: Dedicated client-side controller for 3D spatial World Ambient Machine Hums.
 *
 * <p>Completely separated from {@link SoundDirector}'s Machine GUI channel:
 * runs even when all GUIs are closed, scans active machines within 14 blocks every 6 ticks,
 * enforces the strict nearest-4 cap with category crowding ducking via {@link WorldAmbientLogic},
 * mutes the open machine GUI's own coordinate to prevent doubling, and guarantees 100% cleanup
 * on machine stop/break, player departure, dimension change, or logout.</p>
 */
public final class WorldAmbientAudioController {
    private static final Map<Long, WorldHumSoundInstance> ACTIVE = new HashMap<>();
    private static final Map<Class<?>, Method> MENU_POS_METHOD_CACHE = new HashMap<>();
    private static final Set<Long> SERVER_ACTIVE_POSITIONS = new HashSet<>();
    private static long serverActiveUpdatedMs = 0L;

    private static ResourceKey<Level> lastDimension;
    private static int scanTicker = 0;
    private static int totalWorldHumsStarted = 0;

    private WorldAmbientAudioController() {}

    /** Receives server-authoritative active machine coordinates from {@link BeatPayload}. */
    public static void onBeatPacket(BeatPayload payload) {
        SERVER_ACTIVE_POSITIONS.clear();
        if (payload != null && payload.entries() != null) {
            for (BeatPayload.Entry entry : payload.entries()) {
                SERVER_ACTIVE_POSITIONS.add(entry.pos().asLong());
            }
        }
        serverActiveUpdatedMs = System.nanoTime() / 1_000_000L;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) {
            if (!ACTIVE.isEmpty() || lastDimension != null) {
                stopAll();
            }
            return;
        }

        // Immediate full release on dimension change
        ResourceKey<Level> curDim = level.dimension();
        if (lastDimension != null && !lastDimension.equals(curDim)) {
            stopAll();
        }
        lastDimension = curDim;

        // Phase 116: Block all automatic ambient machine hum startups during 60-tick world-join grace
        if (WorldJoinAudioGuard.isInJoinGrace()) {
            if (!ACTIVE.isEmpty()) {
                stopAll();
            }
            WorldJoinAudioGuard.recordGraceSuppression();
            return;
        }

        // Prune instances that finished fading out
        pruneStopped();

        scanTicker++;
        if (scanTicker < WorldAmbientLogic.SCAN_INTERVAL_TICKS) {
            return;
        }
        scanTicker = 0;

        reconcile(mc, level, player);
    }

    /** Immediately stops and releases every active 3D world hum (logout / dimension change / reset). */
    public static void stopAll() {
        SoundManager sm = soundManagerOrNull();
        for (WorldHumSoundInstance inst : ACTIVE.values()) {
            inst.stopImmediately();
            if (sm != null) {
                try {
                    sm.stop(inst);
                } catch (Throwable ignored) {
                }
            }
        }
        ACTIVE.clear();
        SERVER_ACTIVE_POSITIONS.clear();
        serverActiveUpdatedMs = 0L;
        lastDimension = null;
        scanTicker = 0;
    }

    private static void reconcile(Minecraft mc, ClientLevel level, Player player) {
        SoundManager sm = mc.getSoundManager();
        double px = player.getX();
        double py = player.getY() + player.getEyeHeight() * 0.5D;
        double pz = player.getZ();

        int r = (int) Math.ceil(WorldAmbientLogic.MAX_RADIUS);
        int minCx = ((int) Math.floor(px) - r) >> 4;
        int maxCx = ((int) Math.floor(px) + r) >> 4;
        int minCz = ((int) Math.floor(pz) - r) >> 4;
        int maxCz = ((int) Math.floor(pz) + r) >> 4;

        List<Candidate> candidates = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be == null || be.isRemoved()) {
                        continue;
                    }
                    BlockPos pos = be.getBlockPos();
                    double mx = pos.getX() + 0.5D;
                    double my = pos.getY() + 0.5D;
                    double mz = pos.getZ() + 0.5D;
                    double dx = mx - px;
                    double dy = my - py;
                    double dz = mz - pz;
                    double distSqr = dx * dx + dy * dy + dz * dz;
                    if (distSqr > WorldAmbientLogic.MAX_RADIUS_SQR) {
                        continue;
                    }
                    if (!isMachineActivelyRunning(be)) {
                        continue;
                    }
                    Category cat = classifyMachine(be);
                    if (cat != null) {
                        candidates.add(new Candidate(pos.asLong(), mx, my, mz, cat, distSqr));
                    }
                }
            }
        }

        long mutedGuiPos = resolveOpenMachinePosKey(mc);
        List<Selection> selected = WorldAmbientLogic.selectActiveSources(candidates, mutedGuiPos);

        Set<Long> selectedKeys = new HashSet<>(selected.size());
        for (Selection sel : selected) {
            selectedKeys.add(sel.posKey());
        }

        // 1. Fade out & release sources no longer selected (stopped, broken, moved >14m, GUI opened, or displaced by closer machine)
        Iterator<Map.Entry<Long, WorldHumSoundInstance>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, WorldHumSoundInstance> entry = it.next();
            long key = entry.getKey();
            WorldHumSoundInstance inst = entry.getValue();
            if (!selectedKeys.contains(key)) {
                BlockPos pos = BlockPos.of(key);
                BlockEntity curBe = level.getBlockEntity(pos);
                boolean goneOrStopped = curBe == null || curBe.isRemoved() || !isMachineActivelyRunning(curBe);
                if (goneOrStopped || key == mutedGuiPos) {
                    inst.stopImmediately();
                    sm.stop(inst);
                    it.remove();
                } else {
                    inst.fadeOutAndStop();
                    if (inst.isStopped()) {
                        it.remove();
                    }
                }
            }
        }

        // 2. Start or update selected nearest-4 sources
        boolean masterAudible = mc.options != null
                && mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) > 0.0f;
        for (Selection sel : selected) {
            WorldHumSoundInstance existing = ACTIVE.get(sel.posKey());
            if (existing != null && !existing.isStopped() && !existing.isFadingOut()
                    && existing.getCategory() == sel.category()) {
                existing.setTargetVolume(sel.targetVolume());
                if (masterAudible && !sm.isActive(existing)) {
                    sm.play(existing);
                }
                continue;
            }
            if (existing != null) {
                existing.stopImmediately();
                sm.stop(existing);
                ACTIVE.remove(sel.posKey());
            }
            BlockPos pos = BlockPos.of(sel.posKey());
            WorldHumSoundInstance fresh = new WorldHumSoundInstance(
                    soundEventFor(sel.category()), pos, sel.category(), sel.targetVolume());
            sm.play(fresh);
            ACTIVE.put(sel.posKey(), fresh);
            totalWorldHumsStarted++;
        }
    }

    private static void pruneStopped() {
        ACTIVE.entrySet().removeIf(e -> e.getValue().isStopped());
    }

    private static boolean isMachineActivelyRunning(BlockEntity be) {
        long nowMs = System.nanoTime() / 1_000_000L;
        if (!SERVER_ACTIVE_POSITIONS.isEmpty() && (nowMs - serverActiveUpdatedMs) <= 1500L
                && SERVER_ACTIVE_POSITIONS.contains(be.getBlockPos().asLong())) {
            return true;
        }
        return SoundDirector.isActiveWorldMachine(be);
    }

    static Category classifyMachine(BlockEntity be) {
        BlockState state = be.getBlockState();
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!IndustrialCore.MOD_ID.equals(key.getNamespace())) {
            return null;
        }
        if (be instanceof SingularityReactorBlockEntity) {
            return Category.GEN_REACTOR;
        }
        if (be instanceof GenerationBlockEntity gen) {
            return WorldAmbientLogic.fromGeneratorCategory(
                    GeneratorAudioProfile.forGeneration(gen.kind()).category());
        }
        if (be instanceof RecoveryBlockEntity rec) {
            return WorldAmbientLogic.fromGeneratorCategory(
                    GeneratorAudioProfile.forRecovery(rec.kind()).category());
        }
        return Category.MACHINE_HUM;
    }

    private static SoundEvent soundEventFor(Category cat) {
        return switch (cat) {
            case GEN_THERMAL -> ModSounds.WORLD_GEN_THERMAL.get();
            case GEN_KINETIC -> ModSounds.WORLD_GEN_KINETIC.get();
            case GEN_REACTOR -> ModSounds.WORLD_GEN_REACTOR.get();
            case MACHINE_HUM -> ModSounds.WORLD_MACHINE_HUM.get();
        };
    }

    /**
     * Resolves the BlockPos of any currently open Industrial Core machine GUI so its world 3D hum
     * is cleanly muted while the GUI channel handles its audio.
     */
    private static long resolveOpenMachinePosKey(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> acs)) {
            return WorldAmbientLogic.NO_MUTED_POS;
        }
        if (!acs.getClass().getPackageName().equals("com.industrialcore.client")) {
            return WorldAmbientLogic.NO_MUTED_POS;
        }
        AbstractContainerMenu menu = acs.getMenu();
        if (menu == null) {
            return WorldAmbientLogic.NO_MUTED_POS;
        }
        Class<?> cls = menu.getClass();
        Method getter = MENU_POS_METHOD_CACHE.computeIfAbsent(cls, c -> {
            for (String name : new String[]{"getBlockPos", "getPos"}) {
                try {
                    Method m = c.getMethod(name);
                    if (BlockPos.class.isAssignableFrom(m.getReturnType())) {
                        return m;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
            return null;
        });
        if (getter == null) {
            return WorldAmbientLogic.NO_MUTED_POS;
        }
        try {
            Object res = getter.invoke(menu);
            if (res instanceof BlockPos bp) {
                return bp.asLong();
            }
        } catch (Throwable ignored) {
        }
        return WorldAmbientLogic.NO_MUTED_POS;
    }

    private static SoundManager soundManagerOrNull() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc == null ? null : mc.getSoundManager();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Diagnostics & Telemetry
    // ------------------------------------------------------------------

    public static int getActiveWorldHumCount() {
        pruneStopped();
        int count = 0;
        for (WorldHumSoundInstance inst : ACTIVE.values()) {
            if (!inst.isFadingOut() && !inst.isStopped()) {
                count++;
            }
        }
        return count;
    }

    public static int getTotalWorldHumsStarted() {
        return totalWorldHumsStarted;
    }

    public static List<String> getActiveSoundIds() {
        pruneStopped();
        List<String> ids = new ArrayList<>();
        for (WorldHumSoundInstance inst : ACTIVE.values()) {
            if (!inst.isFadingOut() && !inst.isStopped()) {
                ids.add(inst.getCategory().soundId());
            }
        }
        return ids;
    }

    public static void forceScanNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null && mc.player != null) {
            reconcile(mc, mc.level, mc.player);
        }
    }

    public static void resetCounters() {
        stopAll();
        totalWorldHumsStarted = 0;
    }
}

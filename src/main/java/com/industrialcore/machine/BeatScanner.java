package com.industrialcore.machine;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.AlloyFurnaceBlockEntity;
import com.industrialcore.blockentity.AssemblerBlockEntity;
import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.LargeQuarryBlockEntity;
import com.industrialcore.blockentity.MatterAmplifierBlockEntity;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.blockentity.PhaseReactorBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.blockentity.QuantumCompressorBlockEntity;
import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.blockentity.ReconstructorBlockEntity;
import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.blockentity.SeedCultivatorBlockEntity;
import com.industrialcore.blockentity.TransporterBlockEntity;
import com.industrialcore.network.BeatPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 77 / 102 - lightweight server-side chunk sampler broadcasting {@link BeatPayload}
 * every 6 ticks for active machines within 14 blocks of each player so 3D world ambient hums
 * and micro-particles track real server-side running state accurately.
 */
public final class BeatScanner {
    public static final int SCAN_INTERVAL_TICKS = 6;
    /** Legacy particle beat radius preserved for Phase 77 compatibility. */
    public static final int SCAN_RADIUS = 12;
    /** Phase 102: Expanded sync radius for 3D World Ambient Machine Hums (14 blocks). */
    public static final int AMBIENT_SYNC_RADIUS = 14;

    private static final Set<UUID> PLAYERS_WITH_ACTIVE = ConcurrentHashMap.newKeySet();
    private static int tickCounter;

    private BeatScanner() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if ((tickCounter % SCAN_INTERVAL_TICKS) != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            sampleForPlayer(player);
        }
    }

    private static void sampleForPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = player.blockPosition();
        int r = AMBIENT_SYNC_RADIUS;
        int r2 = r * r;
        int minCx = (center.getX() - r) >> 4;
        int maxCx = (center.getX() + r) >> 4;
        int minCz = (center.getZ() - r) >> 4;
        int maxCz = (center.getZ() + r) >> 4;

        List<Candidate> found = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    double distSq = pos.distSqr(center);
                    if (distSq > r2) {
                        continue;
                    }
                    BeatPayload.Entry entry = classifyActive(be);
                    if (entry != null) {
                        found.add(new Candidate(distSq, entry));
                    }
                }
            }
        }
        if (found.isEmpty()) {
            if (PLAYERS_WITH_ACTIVE.remove(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new BeatPayload(List.of()));
            }
            return;
        }
        PLAYERS_WITH_ACTIVE.add(player.getUUID());
        found.sort(Comparator.comparingDouble(c -> c.distSq));
        int limit = Math.min(BeatPayload.MAX_ENTRIES, found.size());
        List<BeatPayload.Entry> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(found.get(i).entry);
        }
        PacketDistributor.sendToPlayer(player, new BeatPayload(out));
    }

    private static BeatPayload.Entry classifyActive(BlockEntity be) {
        var key = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
        if (!IndustrialCore.MOD_ID.equals(key.getNamespace())) {
            return null;
        }
        if (be instanceof ProcessingBlockEntity p && p.getStatus() == MachineStatus.WORKING) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 75);
        }
        if (be instanceof GenerationBlockEntity g && (g.getRate() > 0 || g.getBurnTime() > 0)) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_GENERATION, (byte) 85);
        }
        if (be instanceof RecoveryBlockEntity rec && rec.getRate() > 0) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_GENERATION, (byte) 70);
        }
        if (be instanceof AlloyFurnaceBlockEntity af && (af.getStatus() == MachineStatus.WORKING || af.getBurnTime() > 0)) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 70);
        }
        if (be instanceof QuantumCompressorBlockEntity qc && qc.getStatus() == MachineStatus.WORKING) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 95);
        }
        if (be instanceof OreSynthesizerBlockEntity os && os.getStatusForTesting() == MachineStatus.WORKING) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 85);
        }
        if (be instanceof PhaseReactorBlockEntity pr && pr.getStatus() == MachineStatus.WORKING) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 90);
        }
        if (be instanceof MatterAmplifierBlockEntity ma && ma.getStatus() == MachineStatus.WORKING) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 80);
        }
        if (be instanceof SeedCultivatorBlockEntity sc && sc.getStatus() == MachineStatus.WORKING) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 65);
        }
        if (be instanceof ReconstructorBlockEntity rc && rc.getStatus() == MachineStatus.WORKING) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 75);
        }
        if (be instanceof AssemblerBlockEntity as && as.getStatus() == MachineStatus.WORKING) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 70);
        }
        if (be instanceof CrafterBlockEntity cr && cr.getStatus() == CrafterStatus.CRAFTING) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 70);
        }
        if (be instanceof QuarryBlockEntity q && q.isRunning()) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 65);
        }
        if (be instanceof LargeQuarryBlockEntity lq && lq.isRunning()) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_PROCESSING, (byte) 85);
        }
        if (be instanceof TransporterBlockEntity tr && tr.getStatus() == TransporterStatus.WORKING) {
            return new BeatPayload.Entry(be.getBlockPos(), BeatPayload.CAT_TRANSPORTER, (byte) 80);
        }
        BlockState state = be.getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
            byte cat = be instanceof com.industrialcore.blockentity.SingularityReactorBlockEntity
                    ? BeatPayload.CAT_GENERATION : BeatPayload.CAT_PROCESSING;
            return new BeatPayload.Entry(be.getBlockPos(), cat, (byte) 80);
        }
        return null;
    }

    private record Candidate(double distSq, BeatPayload.Entry entry) {}
}

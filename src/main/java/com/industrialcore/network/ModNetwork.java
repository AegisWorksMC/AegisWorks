package com.industrialcore.network;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.blockentity.ItemDistributorBlockEntity;
import com.industrialcore.blockentity.LargeQuarryBlockEntity;
import com.industrialcore.blockentity.MatterAmplifierBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.blockentity.ReconstructorBlockEntity;
import com.industrialcore.blockentity.SeedCultivatorBlockEntity;
import com.industrialcore.blockentity.TransporterBlockEntity;
import com.industrialcore.machine.MachineActions;
import com.industrialcore.machine.ReconstructorActions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

/**
 * Network entry point. Every payload is client -> server only: the client never decides where or
 * what to mine, which recipe to run or when items appear, it only asks the server to change a
 * machine's configuration.
 */
public final class ModNetwork {
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0D;

    private ModNetwork() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetRangePayload.TYPE, SetRangePayload.CODEC, ModNetwork::handleSetRange);
        registrar.playToServer(QuarryActionPayload.TYPE, QuarryActionPayload.CODEC, ModNetwork::handleAction);
        registrar.playToServer(SetCrafterRecipePayload.TYPE, SetCrafterRecipePayload.CODEC,
                ModNetwork::handleSetCrafterRecipe);
        registrar.playToServer(MachineConfigPayload.TYPE, MachineConfigPayload.CODEC,
                ModNetwork::handleMachineConfig);
        registrar.playToServer(ReconstructorActionPayload.TYPE, ReconstructorActionPayload.CODEC,
                ModNetwork::handleReconstructorAction);
        registrar.playToServer(LargeQuarryOrePayload.TYPE, LargeQuarryOrePayload.CODEC,
                ModNetwork::handleLargeQuarryOre);
        registrar.playToServer(TransporterConfigPayload.TYPE, TransporterConfigPayload.CODEC,
                ModNetwork::handleTransporterConfig);
        registrar.playToServer(RequestProgressPayload.TYPE, RequestProgressPayload.CODEC,
                ModNetwork::handleRequestProgress);
        registrar.playToServer(MarkTreeRevealedPayload.TYPE, MarkTreeRevealedPayload.CODEC,
                ModNetwork::handleMarkTreeRevealed);
        registrar.playToServer(BranchLimitBreakPayload.TYPE, BranchLimitBreakPayload.CODEC,
                ModNetwork::handleBranchLimitBreak);
        registrar.playToServer(QuantumStorageActionPayload.TYPE, QuantumStorageActionPayload.CODEC,
                ModNetwork::handleQuantumStorageAction);
        registrar.playToServer(QuantumStorageJeiTransferPayload.TYPE, QuantumStorageJeiTransferPayload.CODEC,
                ModNetwork::handleQuantumStorageJeiTransfer);
        registrar.playToServer(XpCollectorActionPayload.TYPE, XpCollectorActionPayload.CODEC,
                ModNetwork::handleXpCollectorAction);
        registrar.playToServer(AutoBuilderActionPayload.TYPE, AutoBuilderActionPayload.CODEC,
                ModNetwork::handleAutoBuilderAction);
        registrar.playToServer(ChunkLoaderActionPayload.TYPE, ChunkLoaderActionPayload.CODEC,
                ModNetwork::handleChunkLoaderAction);
        registrar.playToServer(ClimateActionPayload.TYPE, ClimateActionPayload.CODEC,
                ModNetwork::handleClimateAction);
        registrar.playToServer(WarpActionPayload.TYPE, WarpActionPayload.CODEC,
                ModNetwork::handleWarpAction);
        registrar.playToServer(QuantumFreqActionPayload.TYPE, QuantumFreqActionPayload.CODEC,
                ModNetwork::handleQuantumFreqAction);
        registrar.playToServer(QuantumIoActionPayload.TYPE, QuantumIoActionPayload.CODEC,
                ModNetwork::handleQuantumIoAction);
        registrar.playToServer(SuitModifierActionPayload.TYPE, SuitModifierActionPayload.CODEC,
                SuitModifierActionPayload::handle);
        registrar.playToServer(WeaponSwingPayload.TYPE, WeaponSwingPayload.CODEC,
                WeaponSwingPayload::handle);
        registrar.playToServer(LaserModeTogglePayload.TYPE, LaserModeTogglePayload.CODEC,
                LaserModeTogglePayload::handle);
        registrar.playToServer(LaserRangeTogglePayload.TYPE, LaserRangeTogglePayload.CODEC,
                LaserRangeTogglePayload::handle);
        registrar.playToServer(UpgradeBankActionPayload.TYPE, UpgradeBankActionPayload.CODEC,
                ModNetwork::handleUpgradeBankAction);
        registrar.playToServer(ReactorOverdriveTogglePayload.TYPE, ReactorOverdriveTogglePayload.CODEC,
                ModNetwork::handleReactorOverdriveToggle);
        registrar.playToServer(RiftRaidStartPayload.TYPE, RiftRaidStartPayload.CODEC,
                ModNetwork::handleRiftRaidStart);
        registrar.playToClient(BeatPayload.TYPE, BeatPayload.CODEC, BeatPayload::handle);
        registrar.playToClient(MachineProgressSyncPayload.TYPE, MachineProgressSyncPayload.CODEC,
                MachineProgressSyncPayload::handle);
        registrar.playToClient(QuantumStorageSyncPayload.TYPE, QuantumStorageSyncPayload.CODEC,
                QuantumStorageSyncPayload::handle);
        registrar.playToClient(WarpListSyncPayload.TYPE, WarpListSyncPayload.CODEC,
                WarpListSyncPayload::handle);
        registrar.playToClient(QuantumIoSyncPayload.TYPE, QuantumIoSyncPayload.CODEC,
                QuantumIoSyncPayload::handle);
        registrar.playToClient(SquadStatusSyncPayload.TYPE, SquadStatusSyncPayload.CODEC,
                SquadStatusSyncPayload::handle);
        registrar.playToClient(WeaponBeamFxPayload.TYPE, WeaponBeamFxPayload.CODEC,
                WeaponBeamFxPayload::handle);
        registrar.playToClient(UpgradeBankSyncPayload.TYPE, UpgradeBankSyncPayload.CODEC,
                UpgradeBankSyncPayload::handle);
    }

    private static void handleUpgradeBankAction(UpgradeBankActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position()) > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            BlockEntity be = level.getBlockEntity(payload.pos());
            if (!(be instanceof com.industrialcore.machine.IUpgradeableMachine upg)) {
                return;
            }
            var bank = upg.getUpgradeBank();
            int limit = player.isCreative()
                    ? com.industrialcore.level.MachineLevelMath.LIMIT_BROKEN_MAX_TOTAL_UPGRADE_CAP
                    : upg.getTotalUpgradeLimit();
            if (payload.action() != UpgradeBankActionPayload.ACTION_REQUEST_SYNC) {
                var kind = com.industrialcore.machine.VirtualUpgradeBank.UpgradeKind.byId(payload.kindId());
                if (bank.handlePlayerInteraction(player, kind, payload.action(), limit, upg.getLegacyUpgradeCount())) {
                    upg.onUpgradeBankChanged();
                    be.setChanged();
                    if (bank.specials() >= 10) {
                        com.industrialcore.level.AegisAdvancements.grant(
                                player, com.industrialcore.level.AegisAdvancements.SPECIALIZATION_MASTER);
                    }
                }
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new UpgradeBankSyncPayload(
                            payload.pos(),
                            bank.motors(),
                            bank.batteries(),
                            bank.specials(),
                            limit,
                            upg.getSpecialCategory().ordinal(),
                            upg.getUpgradeMachineId()));
        });
    }

    private static void handleReactorOverdriveToggle(ReactorOverdriveTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position()) > 196.0D) {
                return;
            }
            BlockEntity be = level.getBlockEntity(payload.pos());
            if (be instanceof com.industrialcore.blockentity.GenerationBlockEntity gen
                    && gen.kind() == com.industrialcore.machine.GenerationKind.NUCLEAR) {
                gen.toggleOverdriveMode(level, player);
            } else if (be instanceof com.industrialcore.blockentity.SingularityReactorBlockEntity sing) {
                sing.toggleOverdriveMode(level, player);
            }
        });
    }

    private static void handleRiftRaidStart(RiftRaidStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.reactorPos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.reactorPos()).distanceToSqr(player.position()) > 196.0D) {
                return;
            }
            com.industrialcore.raid.DimensionalRaidManager.tryStartRaid(
                    level, payload.reactorPos(), player, payload.selectedTier());
        });
    }

    private static void handleQuantumStorageAction(QuantumStorageActionPayload payload,
                                                   IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof com.industrialcore.menu.QuantumStorageMenu menu)) {
                return;
            }
            if (menu.isPortableMode()) {
                if (menu.stillValid(player)) {
                    menu.handleAction(player, payload);
                }
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            menu.handleAction(player, payload);
        });
    }

    private static void handleQuantumStorageJeiTransfer(QuantumStorageJeiTransferPayload payload,
                                                        IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof com.industrialcore.menu.QuantumStorageMenu menu)) {
                return;
            }
            if (menu.isPortableMode()) {
                if (menu.stillValid(player)) {
                    menu.handleJeiTransfer(player, payload.recipeId(), payload.maxTransfer());
                }
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (menu.getBlockPos().equals(payload.pos())) {
                menu.handleJeiTransfer(player, payload.recipeId(), payload.maxTransfer());
            }
        });
    }

    private static void handleQuantumFreqAction(QuantumFreqActionPayload payload,
                                                IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.containerMenu instanceof com.industrialcore.menu.QuantumStorageMenu menu) {
                if (menu.isPortableMode()) {
                    if (menu.stillValid(player)) {
                        menu.handleFreqAction(player, payload);
                    }
                    return;
                }
                if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                    return;
                }
                if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                        > MAX_INTERACTION_DISTANCE_SQR) {
                    return;
                }
                menu.handleFreqAction(player, payload);
                return;
            }
            if (player.containerMenu instanceof com.industrialcore.menu.QuantumIoMenu ioMenu
                    && ioMenu.getBlockPos().equals(payload.pos())
                    && player.level() instanceof ServerLevel level
                    && level.isLoaded(payload.pos())
                    && Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position()) <= MAX_INTERACTION_DISTANCE_SQR
                    && level.getBlockEntity(payload.pos())
                    instanceof com.industrialcore.blockentity.AbstractQuantumIoBlockEntity ioBe) {
                ioBe.handleFreqAction(player, payload);
            }
        });
    }

    private static void handleQuantumIoAction(QuantumIoActionPayload payload,
                                              IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (player.containerMenu instanceof com.industrialcore.menu.QuantumIoMenu menu
                    && menu.getBlockPos().equals(payload.pos())
                    && level.getBlockEntity(payload.pos())
                    instanceof com.industrialcore.blockentity.AbstractQuantumIoBlockEntity be) {
                be.handleIoAction(player, payload);
            }
        });
    }

    private static void handleXpCollectorAction(XpCollectorActionPayload payload,
                                                IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (player.containerMenu instanceof com.industrialcore.menu.XpCollectorMenu menu
                    && menu.getBlockPos().equals(payload.pos())
                    && level.getBlockEntity(payload.pos())
                    instanceof com.industrialcore.blockentity.XpCollectorBlockEntity be) {
                be.withdrawToPlayer(player, payload.action());
            }
        });
    }

    private static void handleAutoBuilderAction(AutoBuilderActionPayload payload,
                                                IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (player.containerMenu instanceof com.industrialcore.menu.AutoBuilderMenu menu
                    && menu.getBlockPos().equals(payload.pos())
                    && level.getBlockEntity(payload.pos())
                    instanceof com.industrialcore.blockentity.AutoBuilderBlockEntity be) {
                be.handleAction(payload.action());
            }
        });
    }

    private static void handleChunkLoaderAction(ChunkLoaderActionPayload payload,
                                                IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (player.containerMenu instanceof com.industrialcore.menu.ChunkLoaderMenu menu
                    && menu.getBlockPos().equals(payload.pos())
                    && level.getBlockEntity(payload.pos())
                    instanceof com.industrialcore.blockentity.ChunkLoaderBlockEntity be) {
                be.handleAction(payload.action());
            }
        });
    }

    private static void handleClimateAction(ClimateActionPayload payload,
                                            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (player.containerMenu instanceof com.industrialcore.menu.ClimateControllerMenu menu
                    && menu.getBlockPos().equals(payload.pos())
                    && level.getBlockEntity(payload.pos())
                    instanceof com.industrialcore.blockentity.ClimateControllerBlockEntity be) {
                be.handleAction(payload.action());
            }
        });
    }

    private static void handleWarpAction(WarpActionPayload payload,
                                         IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (player.containerMenu instanceof com.industrialcore.menu.WarpPadMenu menu
                    && menu.getBlockPos().equals(payload.pos())
                    && level.getBlockEntity(payload.pos())
                    instanceof com.industrialcore.blockentity.WarpPadBlockEntity be) {
                be.handleAction(player, payload.action(), payload.value());
            }
        });
    }

    private static void handleRequestProgress(RequestProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.industrialcore.level.MachineProgressSavedData.syncToPlayer(player);
            }
        });
    }

    private static void handleMarkTreeRevealed(MarkTreeRevealedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.industrialcore.level.MachineProgressSavedData.markRevealed(player, payload.machineIds());
            }
        });
    }

    private static void handleBranchLimitBreak(BranchLimitBreakPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.industrialcore.level.MachineSkillTree.Branch branch =
                        com.industrialcore.level.MachineSkillTree.Branch.byId(payload.branchId());
                com.industrialcore.level.MachineProgressSavedData.tryLimitBreakBranch(player, branch);
            }
        });
    }

    /**
     * Phase 26-29: channel name, role and toggles of a transporter. Distance and loaded state are
     * checked here, the block entity validates the channel name itself, and the lower tiers
     * ignore the FE-related toggles - a modified client cannot reach further than a player at
     * the machine.
     */
    private static void handleTransporterConfig(TransporterConfigPayload payload,
                                                IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (!TransporterConfigPayload.isValidAction(payload.action())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (!(level.getBlockEntity(payload.pos()) instanceof TransporterBlockEntity transporter)) {
                return;
            }
            switch (payload.action()) {
                case TransporterConfigPayload.ACTION_SET_CHANNEL ->
                        transporter.setChannel(payload.value());
                case TransporterConfigPayload.ACTION_CYCLE_ROLE ->
                        transporter.setRole(transporter.getRole().opposite());
                case TransporterConfigPayload.ACTION_TOGGLE_ITEM -> transporter.toggleItemEnabled();
                case TransporterConfigPayload.ACTION_TOGGLE_POWER -> transporter.togglePowerEnabled();
                default -> IndustrialCore.LOGGER.debug(
                        "Ignoring unknown transporter action {}", payload.action());
            }
        });
    }

    /**
     * Phase 22: change which ores the large quarry may mine. The index is resolved against the
     * server's live candidate list, so a modified client cannot select a non ore block, and the
     * player has to stand at the machine like for every other machine action.
     */
    private static void handleLargeQuarryOre(LargeQuarryOrePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (!(level.getBlockEntity(payload.pos()) instanceof LargeQuarryBlockEntity quarry)) {
                return;
            }
            switch (payload.action()) {
                case LargeQuarryOrePayload.ACTION_TOGGLE -> quarry.toggleOre(payload.index());
                case LargeQuarryOrePayload.ACTION_SELECT_ALL -> quarry.selectAllOres();
                case LargeQuarryOrePayload.ACTION_CLEAR_ALL -> quarry.clearOres();
                default -> IndustrialCore.LOGGER.debug(
                        "Ignoring unknown large quarry ore action {}", payload.action());
            }
        });
    }

    /**
     * Phase 11: machine wide actions and per-face settings. Every value is validated here and in
     * the block entity; nothing from the client is trusted beyond "which face, which action".
     */
    private static void handleMachineConfig(MachineConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (payload.side() < -1 || payload.side() > 5
                    || !MachineActions.isValid(payload.action())) {
                return;
            }
            // Machine wide actions must not name a face, face actions must name one. Without this
            // check a face action with side -1 would fall through to a block entity that treats
            // the missing side as face 0.
            if (MachineActions.isMachineWide(payload.action()) != (payload.side() < 0)) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position()) > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            ItemStack carried = player.containerMenu == null
                    ? ItemStack.EMPTY : player.containerMenu.getCarried();
            if (level.getBlockEntity(payload.pos()) instanceof CrafterBlockEntity crafter) {
                if (payload.side() < 0) {
                    switch (payload.action()) {
                        case MachineActions.REGISTER_GRID -> crafter.registerRecipeFromGrid();
                        default -> IndustrialCore.LOGGER.debug(
                                "Ignoring unknown crafter action {}", payload.action());
                    }
                    return;
                }
                crafter.applyFaceAction(payload.side(), payload.action(), carried);
            } else if (level.getBlockEntity(payload.pos()) instanceof ProcessingBlockEntity machine) {
                if (payload.side() >= 0) {
                    machine.applyFaceAction(payload.side(), payload.action(), carried);
                }
            } else if (level.getBlockEntity(payload.pos()) instanceof ItemDistributorBlockEntity distributor) {
                if (payload.side() >= 0) {
                    distributor.applyFaceAction(payload.side(), payload.action(), carried);
                }
            } else if (level.getBlockEntity(payload.pos()) instanceof SeedCultivatorBlockEntity cultivator) {
                if (payload.side() >= 0) {
                    cultivator.applyFaceAction(payload.side(), payload.action(), carried);
                }
            } else if (level.getBlockEntity(payload.pos()) instanceof MatterAmplifierBlockEntity amplifier) {
                if (payload.side() >= 0) {
                    amplifier.applyFaceAction(payload.side(), payload.action(), carried);
                }
            }
        });
    }

    /**
     * Phase 15: analyse a sample, cycle through the unlocked minerals or select one of them.
     *
     * <p>Everything is validated on the server: the player must be near the machine, the machine
     * must exist and the mineral must already be unlocked. A modified client therefore cannot
     * unlock a mineral without a matching sample, and it cannot select a recipe that the machine
     * does not know.</p>
     */
    private static void handleReconstructorAction(ReconstructorActionPayload payload,
                                                  IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (!ReconstructorActions.isValid(payload.action())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position()) > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (!(level.getBlockEntity(payload.pos()) instanceof ReconstructorBlockEntity machine)) {
                return;
            }
            switch (payload.action()) {
                case ReconstructorActions.ANALYZE -> {
                    ReconstructorBlockEntity.AnalysisResult result = machine.analyzeSample();
                    reportAnalysis(player, result, machine.getSelectedMineral());
                }
                case ReconstructorActions.CYCLE_MINERAL -> machine.cycleMineral();
                case ReconstructorActions.SELECT_MINERAL -> {
                    String mineral = payload.mineral();
                    if (mineral == null || mineral.isBlank()) {
                        return;
                    }
                    // The block entity only accepts a mineral that is already unlocked.
                    if (!machine.getUnlockedMinerals().contains(mineral)) {
                        return;
                    }
                    machine.selectMineral(mineral);
                }
                default -> IndustrialCore.LOGGER.debug(
                        "Ignoring unknown reconstructor action {}", payload.action());
            }
        });
    }

    /** Shows the outcome of an analysis in the action bar. */
    private static void reportAnalysis(ServerPlayer player, ReconstructorBlockEntity.AnalysisResult result,
                                        String mineral) {
        String key = switch (result) {
            case UNLOCKED -> "gui.industrialcore.reconstructor.analyze_ok";
            case NO_SAMPLE -> "gui.industrialcore.reconstructor.analyze_no_sample";
            case NO_MATCH -> "gui.industrialcore.reconstructor.analyze_no_match";
            case ALREADY_UNLOCKED -> "gui.industrialcore.reconstructor.analyze_already";
            case REJECTED -> null;
        };
        if (key == null) {
            return;
        }
        Component message = Component.translatable(key);
        if (result == ReconstructorBlockEntity.AnalysisResult.UNLOCKED) {
            message = Component.translatable(key, mineral);
        }
        player.displayClientMessage(message, true);
    }

    private static void handleSetCrafterRecipe(SetCrafterRecipePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position()) > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            if (level.getBlockEntity(payload.pos()) instanceof CrafterBlockEntity crafter) {
                // The block entity validates the id against the live recipe manager, so a stale
                // client list or an unknown id can never select an invalid recipe.
                ResourceLocation id = payload.recipeId().isEmpty()
                        ? null
                        : ResourceLocation.tryParse(payload.recipeId());
                crafter.setSelectedRecipe(id);
            }
        });
    }

    private static void handleSetRange(SetRangePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> withQuarry(context, payload.pos(),
                quarry -> quarry.applyRange(payload.width(), payload.depth(), payload.height())));
    }

    /**
     * Start/stop requests of the quarry family. The payload is shared by the normal quarry and the
     * Phase 20 large quarry; the block entity decides what the action means, and the server checks
     * the player's distance before anything happens.
     */
    private static void handleAction(QuarryActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(payload.pos())) {
                return;
            }
            if (Vec3.atCenterOf(payload.pos()).distanceToSqr(player.position())
                    > MAX_INTERACTION_DISTANCE_SQR) {
                return;
            }
            BlockEntity blockEntity = level.getBlockEntity(payload.pos());
            if (blockEntity instanceof LargeQuarryBlockEntity largeQuarry) {
                switch (payload.action()) {
                    case QuarryActionPayload.ACTION_START -> largeQuarry.setRunning(true);
                    case QuarryActionPayload.ACTION_STOP -> largeQuarry.setRunning(false);
                    case QuarryActionPayload.ACTION_TOGGLE_RUNNING -> largeQuarry.toggleRunning();
                    // The large quarry has no drawn area preview.
                    case QuarryActionPayload.ACTION_TOGGLE_PREVIEW -> { }
                    default -> IndustrialCore.LOGGER.debug(
                            "Ignoring unknown large quarry action {}", payload.action());
                }
                return;
            }
            if (blockEntity instanceof QuarryBlockEntity quarry) {
                switch (payload.action()) {
                    case QuarryActionPayload.ACTION_START -> quarry.setRunning(true);
                    case QuarryActionPayload.ACTION_STOP -> quarry.setRunning(false);
                    case QuarryActionPayload.ACTION_TOGGLE_PREVIEW -> quarry.togglePreview();
                    case QuarryActionPayload.ACTION_TOGGLE_RUNNING -> quarry.toggleRunning();
                    default -> IndustrialCore.LOGGER.debug(
                            "Ignoring unknown quarry action {}", payload.action());
                }
            }
        });
    }

    private static void withQuarry(IPayloadContext context, BlockPos pos, Consumer<QuarryBlockEntity> action) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(pos)) {
            return;
        }
        if (Vec3.atCenterOf(pos).distanceToSqr(player.position()) > MAX_INTERACTION_DISTANCE_SQR) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof QuarryBlockEntity quarry) {
            action.accept(quarry);
        }
    }
}

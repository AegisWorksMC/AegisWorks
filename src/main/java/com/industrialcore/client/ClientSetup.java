package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModEntities;
import com.industrialcore.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid = IndustrialCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.QUARRY.get(), QuarryScreen::new);
        event.register(ModMenus.LARGE_QUARRY.get(), LargeQuarryScreen::new);
        event.register(ModMenus.GENERATION.get(), GenerationScreen::new);
        event.register(ModMenus.CRAFTER.get(), CrafterScreen::new);
        event.register(ModMenus.CRUSHER.get(), ProcessingScreen::new);
        event.register(ModMenus.ELECTRIC_FURNACE.get(), ProcessingScreen::new);
        event.register(ModMenus.ELECTROLYZER.get(), ProcessingScreen::new);
        event.register(ModMenus.PRECISION_CRUSHER.get(), ProcessingScreen::new);
        event.register(ModMenus.RECONSTRUCTOR.get(), ReconstructorScreen::new);
        event.register(ModMenus.METAL_PRESS.get(), ProcessingScreen::new);
        event.register(ModMenus.FAST_CRUSHER.get(), ProcessingScreen::new);
        event.register(ModMenus.ALLOY_FURNACE.get(), AlloyFurnaceScreen::new);
        event.register(ModMenus.ENERGY_BUFFER.get(), EnergyBufferScreen::new);
        event.register(ModMenus.ITEM_DISTRIBUTOR.get(), ItemDistributorScreen::new);
        event.register(ModMenus.ASSEMBLER.get(), AssemblerScreen::new);
        event.register(ModMenus.SEED_CULTIVATOR.get(), SeedCultivatorScreen::new);
        event.register(ModMenus.MATTER_AMPLIFIER.get(), MatterAmplifierScreen::new);
        event.register(ModMenus.PHASE_REACTOR.get(), PhaseReactorScreen::new);
        event.register(ModMenus.QUANTUM_COMPRESSOR.get(), QuantumCompressorScreen::new);
        event.register(ModMenus.TRANSPORTER.get(), TransporterScreen::new);
        event.register(ModMenus.ORE_SYNTHESIZER.get(), OreSynthesizerScreen::new);
        event.register(ModMenus.FACTORY_MONITOR.get(), MonitorScreen::new);
        event.register(ModMenus.QUANTUM_STORAGE_CORE.get(), QuantumStorageScreen::new);
        event.register(ModMenus.CROP_HARVESTER.get(), CropHarvesterScreen::new);
        event.register(ModMenus.TREE_HARVESTER.get(), TreeHarvesterScreen::new);
        event.register(ModMenus.AUTO_FISHER.get(), AutoFisherScreen::new);
        event.register(ModMenus.RANCH_HARVESTER.get(), RanchHarvesterScreen::new);
        event.register(ModMenus.GROWTH_ACCELERATOR.get(), GrowthAcceleratorScreen::new);
        event.register(ModMenus.BIO_FEEDER.get(), BioFeederScreen::new);
        event.register(ModMenus.MOB_FAN.get(), MobFanScreen::new);
        event.register(ModMenus.ROTARY_DRILL.get(), RotaryDrillScreen::new);
        event.register(ModMenus.ITEM_VACUUM.get(), ItemVacuumScreen::new);
        event.register(ModMenus.XP_COLLECTOR.get(), XpCollectorScreen::new);
        event.register(ModMenus.SPAWN_SUPPRESSOR.get(), SpawnSuppressorScreen::new);
        event.register(ModMenus.LASER_TURRET.get(), LaserTurretScreen::new);
        event.register(ModMenus.MOB_DUPLICATOR.get(), MobDuplicatorScreen::new);
        event.register(ModMenus.AUTO_BUILDER.get(), AutoBuilderScreen::new);
        event.register(ModMenus.FLUID_PUMP.get(), FluidPumpScreen::new);
        event.register(ModMenus.WIRELESS_CHARGER.get(), WirelessChargerScreen::new);
        event.register(ModMenus.CHUNK_LOADER.get(), ChunkLoaderScreen::new);
        event.register(ModMenus.CLIMATE_CONTROLLER.get(), ClimateControllerScreen::new);
        event.register(ModMenus.WARP_PAD.get(), WarpPadScreen::new);
        event.register(ModMenus.QUANTUM_IO.get(), QuantumIoScreen::new);
        event.register(ModMenus.SUIT_MODIFIER.get(), SuitModifierScreen::new);
        event.register(ModMenus.SINGULARITY_REACTOR.get(), SingularityReactorScreen::new);
    }

    /**
     * Registers the item renderer of the Phase 5 logistics blocks. The block models stay as they
     * are (a belt is a full block, a pipe is a multipart model); the renderer only adds the
     * travelling items and the wrench face markers on top of them.
     */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ITEM_PIPE.get(), ItemPipeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CABLE.get(), CableArmRenderer::new);
        event.registerEntityRenderer(ModEntities.ENERGY_ARROW.get(), EnergyArrowRenderer::new);
        // Phase 116: 4 original Dimensional Rift Raid enemies + non-destructive plasma bolt
        event.registerEntityRenderer(ModEntities.RIFT_STALKER.get(),
                ctx -> new RiftInvaderRenderer<>(ctx, RiftInvaderRenderer.Kind.RIFT_STALKER));
        event.registerEntityRenderer(ModEntities.VOID_SENTINEL.get(),
                ctx -> new RiftInvaderRenderer<>(ctx, RiftInvaderRenderer.Kind.VOID_SENTINEL));
        event.registerEntityRenderer(ModEntities.PHASE_JUGGERNAUT.get(),
                ctx -> new RiftInvaderRenderer<>(ctx, RiftInvaderRenderer.Kind.PHASE_JUGGERNAUT));
        event.registerEntityRenderer(ModEntities.DIMENSIONAL_SOVEREIGN.get(),
                ctx -> new RiftInvaderRenderer<>(ctx, RiftInvaderRenderer.Kind.DIMENSIONAL_SOVEREIGN));
        event.registerEntityRenderer(ModEntities.RIFT_PLASMA_BOLT.get(), RiftPlasmaBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFT_ANCHOR_CRYSTAL.get(), RiftAnchorCrystalRenderer::new);
        // Phase 104: Expanded multi-block 3D renderers for the 5 upgraded high-tier machines
        event.registerBlockEntityRenderer(ModBlockEntities.MOB_DUPLICATOR.get(),
                ctx -> new LargeMachineRenderer<>(ctx, com.industrialcore.multiblock.LargeMachineLogic.MachineSpec.MOB_DUPLICATOR));
        event.registerBlockEntityRenderer(ModBlockEntities.WARP_PAD.get(),
                ctx -> new LargeMachineRenderer<>(ctx, com.industrialcore.multiblock.LargeMachineLogic.MachineSpec.WARP_PAD));
        event.registerBlockEntityRenderer(ModBlockEntities.CLIMATE_CONTROLLER.get(),
                ctx -> new LargeMachineRenderer<>(ctx, com.industrialcore.multiblock.LargeMachineLogic.MachineSpec.CLIMATE_CONTROLLER));
        event.registerBlockEntityRenderer(ModBlockEntities.QUANTUM_STORAGE_CORE.get(),
                ctx -> new LargeMachineRenderer<>(ctx, com.industrialcore.multiblock.LargeMachineLogic.MachineSpec.QUANTUM_STORAGE_CORE));
        event.registerBlockEntityRenderer(ModBlockEntities.SUIT_MODIFIER.get(),
                ctx -> new LargeMachineRenderer<>(ctx, com.industrialcore.multiblock.LargeMachineLogic.MachineSpec.SUIT_MODIFIER));
        // Phase 109 Tier 2: Lightweight distance-culled hero 3D moving-parts renderers
        event.registerBlockEntityRenderer(ModBlockEntities.SINGULARITY_REACTOR.get(), SingularityReactorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GENERATION.get(), NuclearReactorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MOB_FAN.get(), MobFanRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PROCESSING.get(), CrusherRollerRenderer::new);
        // Phase 117: Alloy Furnace induction heating coil & molten crucible core BER
        event.registerBlockEntityRenderer(ModBlockEntities.ALLOY_FURNACE.get(), FurnaceCoreRenderer::new);
        // Phase 110 Tier 2: Expanded lightweight 3D moving-parts renderers (wind turbine shares GENERATION)
        event.registerBlockEntityRenderer(ModBlockEntities.RECOVERY.get(), FlywheelRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ROTARY_DRILL.get(), RotaryDrillRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.AUTO_FISHER.get(), AutoFisherRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SPAWN_SUPPRESSOR.get(), BeaconRingRenderer::spawnSuppressor);
        event.registerBlockEntityRenderer(ModBlockEntities.WIRELESS_CHARGER.get(), BeaconRingRenderer::wirelessCharger);
        event.registerBlockEntityRenderer(ModBlockEntities.CHUNK_LOADER.get(), ChunkLoaderRenderer::new);
        // Phase 111 Tier 2: Complete 3D physical moving-parts coverage (19 groups / 22 blocks total)
        // (metal_press shares PROCESSING, hydro_generator shares GENERATION, pressure_recovery shares RECOVERY)
        event.registerBlockEntityRenderer(ModBlockEntities.ASSEMBLER.get(),
                ctx -> new ProductionHeroRenderer<>(ctx, ProductionHeroRenderer.Kind.ASSEMBLER));
        event.registerBlockEntityRenderer(ModBlockEntities.QUANTUM_COMPRESSOR.get(),
                ctx -> new ProductionHeroRenderer<>(ctx, ProductionHeroRenderer.Kind.QUANTUM_COMPRESSOR));
        event.registerBlockEntityRenderer(ModBlockEntities.RECONSTRUCTOR.get(),
                ctx -> new ProductionHeroRenderer<>(ctx, ProductionHeroRenderer.Kind.RECONSTRUCTOR));
        event.registerBlockEntityRenderer(ModBlockEntities.QUARRY.get(),
                ctx -> new ProductionHeroRenderer<>(ctx, ProductionHeroRenderer.Kind.QUARRY));
        event.registerBlockEntityRenderer(ModBlockEntities.LARGE_QUARRY.get(),
                ctx -> new ProductionHeroRenderer<>(ctx, ProductionHeroRenderer.Kind.LARGE_QUARRY));
        event.registerBlockEntityRenderer(ModBlockEntities.ORE_SYNTHESIZER.get(),
                ctx -> new ProductionHeroRenderer<>(ctx, ProductionHeroRenderer.Kind.ORE_SYNTHESIZER));
        event.registerBlockEntityRenderer(ModBlockEntities.PHASE_REACTOR.get(),
                ctx -> new ProductionHeroRenderer<>(ctx, ProductionHeroRenderer.Kind.PHASE_REACTOR));
        event.registerBlockEntityRenderer(ModBlockEntities.MATTER_AMPLIFIER.get(),
                ctx -> new ProductionHeroRenderer<>(ctx, ProductionHeroRenderer.Kind.MATTER_AMPLIFIER));
        event.registerBlockEntityRenderer(ModBlockEntities.CROP_HARVESTER.get(),
                ctx -> new NatureHeroRenderer<>(ctx, NatureHeroRenderer.Kind.CROP_HARVESTER));
        event.registerBlockEntityRenderer(ModBlockEntities.TREE_HARVESTER.get(),
                ctx -> new NatureHeroRenderer<>(ctx, NatureHeroRenderer.Kind.TREE_HARVESTER));
        event.registerBlockEntityRenderer(ModBlockEntities.GROWTH_ACCELERATOR.get(),
                ctx -> new NatureHeroRenderer<>(ctx, NatureHeroRenderer.Kind.GROWTH_ACCELERATOR));
        event.registerBlockEntityRenderer(ModBlockEntities.ITEM_VACUUM.get(),
                ctx -> new TrapHeroRenderer<>(ctx, TrapHeroRenderer.Kind.ITEM_VACUUM));
        event.registerBlockEntityRenderer(ModBlockEntities.XP_COLLECTOR.get(),
                ctx -> new TrapHeroRenderer<>(ctx, TrapHeroRenderer.Kind.XP_COLLECTOR));
        event.registerBlockEntityRenderer(ModBlockEntities.LASER_TURRET.get(),
                ctx -> new TrapHeroRenderer<>(ctx, TrapHeroRenderer.Kind.LASER_TURRET));
        event.registerBlockEntityRenderer(ModBlockEntities.FLUID_PUMP.get(),
                ctx -> new LifeHeroRenderer<>(ctx, LifeHeroRenderer.Kind.FLUID_PUMP));
        event.registerBlockEntityRenderer(ModBlockEntities.AUTO_BUILDER.get(),
                ctx -> new LifeHeroRenderer<>(ctx, LifeHeroRenderer.Kind.AUTO_BUILDER));
        event.registerBlockEntityRenderer(ModBlockEntities.BASIC_ITEM_TRANSPORTER.get(), TransporterHeroRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ADVANCED_ITEM_TRANSPORTER.get(), TransporterHeroRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.QUANTUM_TRANSPORTER.get(), TransporterHeroRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.QUANTUM_TRANSPORTER_ARRAY.get(), TransporterHeroRenderer::new);
    }

    @SubscribeEvent
    public static void addEntityLayers(EntityRenderersEvent.AddLayers event) {
        var models = event.getEntityModels();
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new VirtualArmorGlowLayer<>(
                        renderer,
                        new net.minecraft.client.model.HumanoidModel<>(
                                models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_INNER_ARMOR)),
                        new net.minecraft.client.model.HumanoidModel<>(
                                models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_OUTER_ARMOR))));
            }
        }
        // Also attach to Armor Stand and any HumanoidMobRenderer (zombies, skeletons, etc.)
        var standRenderer = event.getRenderer(net.minecraft.world.entity.EntityType.ARMOR_STAND);
        if (standRenderer instanceof net.minecraft.client.renderer.entity.ArmorStandRenderer asr) {
            asr.addLayer(new VirtualArmorGlowLayer<>(
                    asr,
                    new net.minecraft.client.model.ArmorStandArmorModel(
                            models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ARMOR_STAND_INNER_ARMOR)),
                    new net.minecraft.client.model.ArmorStandArmorModel(
                            models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.ARMOR_STAND_OUTER_ARMOR))));
        }
        for (net.minecraft.world.entity.EntityType<?> type : event.getEntityTypes()) {
            if (type == net.minecraft.world.entity.EntityType.ARMOR_STAND) {
                continue;
            }
            var r = event.getRenderer(type);
            if (r instanceof net.minecraft.client.renderer.entity.HumanoidMobRenderer<?, ?> hmr) {
                addGlowLayerToHumanoid(hmr, models);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends net.minecraft.world.entity.Mob, M extends net.minecraft.client.model.HumanoidModel<T>>
            void addGlowLayerToHumanoid(
                    net.minecraft.client.renderer.entity.HumanoidMobRenderer<T, M> renderer,
                    net.minecraft.client.model.geom.EntityModelSet models) {
        renderer.addLayer(new VirtualArmorGlowLayer<>(
                renderer,
                new net.minecraft.client.model.HumanoidModel<>(
                        models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidModel<>(
                        models.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_OUTER_ARMOR))));
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        ModKeyMappings.register(event);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        com.industrialcore.network.BeatPayload.clientHandler = payload -> {
            SoundDirector.onBeatPacket(payload);
            WorldAmbientAudioController.onBeatPacket(payload);
        };
        com.industrialcore.network.MachineProgressSyncPayload.clientHandler = ClientMachineProgress::apply;
        com.industrialcore.network.UpgradeBankSyncPayload.clientHandler = ClientUpgradeBankCache::apply;
        com.industrialcore.machine.LivingFactoryParticleHook.handler = LivingFactoryParticles::onAnimateTick;
        com.industrialcore.network.QuantumStorageSyncPayload.clientHandler = payload -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null
                    && mc.player.containerMenu instanceof com.industrialcore.menu.QuantumStorageMenu menu) {
                menu.applyClientSync(payload);
            }
        };
        com.industrialcore.network.WarpListSyncPayload.clientHandler = payload -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null
                    && mc.player.containerMenu instanceof com.industrialcore.menu.WarpPadMenu menu) {
                menu.applyClientSync(payload);
            }
        };
        com.industrialcore.network.QuantumIoSyncPayload.clientHandler = payload -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null
                    && mc.player.containerMenu instanceof com.industrialcore.menu.QuantumIoMenu menu) {
                menu.applyClientSync(payload);
            }
        };
        com.industrialcore.network.SquadStatusSyncPayload.clientHandler = ClientSquadCache::apply;
        com.industrialcore.network.WeaponBeamFxPayload.clientHandler = payload -> {
            WeaponWorldFxRenderer.addFx(payload);
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
                double dx = payload.x0() - eye.x;
                double dy = payload.y0() - eye.y;
                double dz = payload.z0() - eye.z;
                boolean nearSelf = dx * dx + dy * dy + dz * dz <= 9.0D;
                if (nearSelf) {
                    if (payload.fxKind() == com.industrialcore.network.WeaponBeamFxPayload.KIND_LASER_BEAM) {
                        if (mc.player.isUsingItem()
                                && mc.player.getUseItem().getItem() instanceof com.industrialcore.item.PulseLaserItem) {
                            LaserAudioController.notifyLocalLaserBeam(payload.charge(), payload.hittingTarget());
                        }
                    } else if (payload.fxKind() == com.industrialcore.network.WeaponBeamFxPayload.KIND_RAILGUN_BEAM) {
                        WeaponFirstPersonRenderer.triggerRailgunRecoil();
                    } else if (payload.fxKind() == com.industrialcore.network.WeaponBeamFxPayload.KIND_BLINK_TRAIL) {
                        WeaponFirstPersonRenderer.triggerBladeDash();
                    }
                }
            }
        };
        com.industrialcore.menu.QuantumStorageMenu.clientCraftSound = () -> SoundDirector.playComplete(1, 1);
        com.industrialcore.menu.QuantumStorageMenu.clientLockedSound = SoundDirector::playUiLocked;
        com.industrialcore.level.UpgradeCapBridge.clientUpgradeLimit = ClientMachineProgress::upgradeLimit;
        com.industrialcore.level.CraftUnlockGate.clientLevelLookup = ClientMachineProgress::level;
        com.industrialcore.level.CraftUnlockGate.clientLockedSound = SoundDirector::playUiLocked;
        com.industrialcore.reactor.ReactorGuideHook.clientToggle = ReactorGuideRenderer::handleShiftRightClick;
        // Phase 116: 3-layer World-Join Audio Guard (60-tick mute + silent initial suit sync + global limiter)
        NeoForge.EVENT_BUS.addListener(WorldJoinAudioGuard::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(WorldJoinAudioGuard::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(WorldJoinAudioGuard::onClientTick);
        NeoForge.EVENT_BUS.addListener(WorldJoinAudioGuard::onPlaySound);
        NeoForge.EVENT_BUS.addListener(SoundDirector::onClientTick);
        NeoForge.EVENT_BUS.addListener(ReactorGuideRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(ReactorGuideRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ReactorGuideRenderer::onRenderGuiPost);
        NeoForge.EVENT_BUS.addListener(WorldAmbientAudioController::onClientTick);
        NeoForge.EVENT_BUS.addListener(LaserAudioController::onClientTick);
        NeoForge.EVENT_BUS.addListener(ModKeyMappings::onClientTick);
        NeoForge.EVENT_BUS.addListener(LevelToastOverlay::onRenderGuiPost);
        NeoForge.EVENT_BUS.addListener(VirtualHudOverlay::onRenderGuiLayerPre);
        NeoForge.EVENT_BUS.addListener(VirtualHudOverlay::onRenderGuiPost);
        NeoForge.EVENT_BUS.addListener(VirtualHudOverlay::onComputeFovModifier);
        NeoForge.EVENT_BUS.addListener(WeaponWorldFxRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(WeaponWorldFxRenderer::onRenderFramePre);
        NeoForge.EVENT_BUS.addListener(WeaponWorldFxRenderer::onLeftClickEmpty);
        NeoForge.EVENT_BUS.addListener(WeaponWorldFxRenderer::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(WeaponWorldFxRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(WeaponFirstPersonRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(WeaponFirstPersonRenderer::onRenderHand);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Opening e) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (e.getNewScreen() != null
                    && e.getNewScreen().getClass() == net.minecraft.client.gui.screens.inventory.InventoryScreen.class
                    && VirtualHudOverlay.isArHudActive(mc)
                    && mc != null && mc.player != null && !mc.player.isCreative()) {
                e.setNewScreen(new VirtualInventoryScreen(mc.player));
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientMachineProgress::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(GuiDopamine::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(GuiDopamine::onScreenClosing);
        NeoForge.EVENT_BUS.addListener(GuiDopamine::onScreenMousePressed);
        NeoForge.EVENT_BUS.addListener(GuiDopamine::onScreenKeyPressed);
        NeoForge.EVENT_BUS.addListener(GuiDopamine::onScreenRenderPost);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientMachineProgress.reset();
            ClientUpgradeBankCache.clear();
            VirtualHudOverlay.reset();
            WeaponWorldFxRenderer.clear();
            WeaponFirstPersonRenderer.reset();
            SoundDirector.stopAllOnLogout();
            WorldAmbientAudioController.stopAll();
            LaserAudioController.stopAll();
            FactoryAudioController.reset();
            ReactorGuideRenderer.clear();
            GuiDopamine.reset();
        });
    }
}

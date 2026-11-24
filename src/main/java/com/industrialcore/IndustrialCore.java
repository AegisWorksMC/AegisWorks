package com.industrialcore;

import com.industrialcore.block.MachinePortDefinitions;
import com.industrialcore.command.ModCommands;
import com.industrialcore.network.ModNetwork;
import com.industrialcore.machine.TransporterRegistry;
import com.industrialcore.registry.ModArmorMaterials;
import com.industrialcore.registry.ModBlockEntities;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModCreativeTabs;
import com.industrialcore.registry.ModEntities;
import com.industrialcore.registry.ModItems;
import com.industrialcore.registry.ModMenus;
import com.industrialcore.registry.ModRecipes;
import com.industrialcore.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Entry point of the Industrial Core mod.
 *
 * <p>The mod id and display name are temporary ("Phase 1" working title). Rename by changing
 * the mod id in gradle.properties and neoforge.mods.toml, the package name, and the assets
 * namespace. All content is registered here.</p>
 */
@Mod(IndustrialCore.MOD_ID)
public final class IndustrialCore {
    public static final String MOD_ID = "industrialcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IndustrialCore(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        ModNetwork.register(modEventBus);
        modEventBus.addListener(IndustrialCore::registerCapabilities);
        modEventBus.addListener(IndustrialCore::registerEntityAttributes);
        modEventBus.addListener(com.industrialcore.life.ChunkLoaderTickets::register);
        // Phase 45: machine port tables are registered once the block registry is frozen.
        modEventBus.addListener(IndustrialCore::registerMachinePorts);
        // Operator command for large-factory diagnostics; reads existing counters only.
        NeoForge.EVENT_BUS.addListener(ModCommands::onRegisterCommands);
        // Phase 26-29: transporter connection caches die with their level and with the server,
        // so no channel information outlives the world it belongs to.
        NeoForge.EVENT_BUS.addListener(TransporterRegistry::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(TransporterRegistry::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.machine.BeatScanner::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.level.MachineProgressEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.level.MachineProgressEvents::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.level.MachineProgressEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.level.MachineProgressEvents::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.level.MachineProgressEvents::onContainerOpen);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.trap.SpawnSuppressorRegistry::onMobSpawnCheck);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.trap.SpawnSuppressorRegistry::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.trap.SpawnSuppressorRegistry::onServerStopped);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.industrialcore.life.ChunkLoaderTickets.onLevelUnload(sl);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) ->
                com.industrialcore.life.ChunkLoaderTickets.onServerStopped());
        // Phase 98: Virtual Aegis suit runtime events (FE damage absorption, flight, attributes, modules).
        NeoForge.EVENT_BUS.addListener(com.industrialcore.suit.VirtualSuitEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.suit.VirtualSuitEvents::onLivingFall);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.suit.VirtualSuitEvents::onLivingJump);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.suit.VirtualSuitEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.suit.VirtualSuitEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.suit.VirtualSuitEvents::onServerStopped);
        // Phase 101: Electric Weapon runtime events (gravity fields, quantum loot, plasma wave).
        NeoForge.EVENT_BUS.addListener(com.industrialcore.weapon.WeaponRuntimeEvents::onLevelTickPost);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.weapon.WeaponRuntimeEvents::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.weapon.WeaponRuntimeEvents::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.weapon.WeaponRuntimeEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.weapon.WeaponRuntimeEvents::onServerStopped);
        // Phase 103: Multiblock reactor ownership registry cleanup.
        NeoForge.EVENT_BUS.addListener(com.industrialcore.reactor.ReactorMultiblockManager::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.reactor.ReactorMultiblockManager::onServerStopped);
        // Phase 114: AegisWorks 28-entry Advancements progression hooks.
        NeoForge.EVENT_BUS.addListener(com.industrialcore.level.AegisAdvancements::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.level.AegisAdvancements::onEquipmentChange);
        // Phase 115: Weapon config scaling + Dimensional Rift Raid factory defense challenge.
        NeoForge.EVENT_BUS.addListener(com.industrialcore.weapon.WeaponRuntimeEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.raid.DimensionalRaidManager::onLevelTickPost);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.raid.DimensionalRaidManager::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.raid.DimensionalRaidManager::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.raid.DimensionalRaidManager::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.raid.DimensionalRaidManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.raid.DimensionalRaidManager::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.raid.DimensionalRaidManager::onPlayerChangedDimension);
        // Open Beta first-login Discord welcome notice.
        NeoForge.EVENT_BUS.addListener(com.industrialcore.welcome.BetaWelcomeHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(com.industrialcore.welcome.BetaWelcomeHandler::onPlayerClone);
        LOGGER.info("AegisWorks (industrialcore) initialized");
    }

    private static void registerMachinePorts(FMLCommonSetupEvent event) {
        MachinePortDefinitions.register();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.QUARRY.get(),
                (quarry, side) -> quarry.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.QUARRY.get(),
                (quarry, side) -> quarry.getOutputHandler());
        // Phase 20: the large quarry accepts FE like any machine and exposes its collected ore
        // and dust through an extraction-only item handler.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.LARGE_QUARRY.get(),
                (quarry, side) -> quarry.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LARGE_QUARRY.get(),
                (quarry, side) -> quarry.getOutputHandler());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CABLE.get(),
                (cable, side) -> cable.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CRAFTER.get(),
                (crafter, side) -> crafter.getEnergyStorage());
        // Phase 11: the item handler is looked up per face so the machine can route "top = material
        // A, side = material B, bottom = output". A null side keeps the old unrestricted view.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.CRAFTER.get(),
                (crafter, side) -> crafter.getItemHandler(side));
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.PROCESSING.get(),
                (machine, side) -> machine.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.PROCESSING.get(),
                (machine, side) -> machine.getItemHandler(side));
        // Phase 15: the reconstructor is a machine of its own with its own energy buffer.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.RECONSTRUCTOR.get(),
                (machine, side) -> machine.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.RECONSTRUCTOR.get(),
                (machine, side) -> machine.getItemHandler(side));
        // Phase 17/18: the energy buffer stores FE for the grid, so it has to accept FE from
        // cables and generators like any machine (both tiers share this block entity type).
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ENERGY_BUFFER.get(),
                (buffer, side) -> buffer.getEnergyStorage());
        // Phase 18: the assembler is a normal electric machine with three input slots.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ASSEMBLER.get(),
                (assembler, side) -> assembler.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ASSEMBLER.get(),
                (assembler, side) -> assembler.getItemHandler());
        // Phase 19: the item distributor behaves like a machine face-by-face: it accepts items on
        // every configured input side and never lets automation drain its buffer.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ITEM_DISTRIBUTOR.get(),
                (distributor, side) -> distributor.getItemHandler(side));
        // Phase 21: both seed line machines accept FE and expose their item slots per face.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.SEED_CULTIVATOR.get(),
                (machine, side) -> machine.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SEED_CULTIVATOR.get(),
                (machine, side) -> machine.getItemHandler(side));
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.MATTER_AMPLIFIER.get(),
                (machine, side) -> machine.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.MATTER_AMPLIFIER.get(),
                (machine, side) -> machine.getItemHandler(side));
        // Phase 24: the phase reactor accepts FE and lets a pipe insert into its three ingredient
        // slots and extract from the result slot.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.PHASE_REACTOR.get(),
                (machine, side) -> machine.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.PHASE_REACTOR.get(),
                (machine, side) -> machine.getItemHandler(side));
        // Phase 30: the synthesizer accepts FE from cables and exposes its thirteen slots to
        // pipes; automation may fill the input slots and pull from the output slot, nothing else.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ORE_SYNTHESIZER.get(),
                (machine, side) -> machine.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ORE_SYNTHESIZER.get(),
                (machine, side) -> machine.getItemHandler());
        // Phase 26-29: every transporter is a plain item buffer on every face; only the two FE
        // tiers (28/29) register an energy capability at all, the lower ones have no FE buffer.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.BASIC_ITEM_TRANSPORTER.get(),
                (transporter, side) -> transporter.getItemHandler(side));
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ADVANCED_ITEM_TRANSPORTER.get(),
                (transporter, side) -> transporter.getItemHandler(side));
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.QUANTUM_TRANSPORTER.get(),
                (transporter, side) -> transporter.getItemHandler(side));
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.QUANTUM_TRANSPORTER.get(),
                (transporter, side) -> transporter.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.QUANTUM_TRANSPORTER_ARRAY.get(),
                (transporter, side) -> transporter.getItemHandler(side));
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.QUANTUM_TRANSPORTER_ARRAY.get(),
                (transporter, side) -> transporter.getEnergyStorage());
        // Phase 33: the six generators expose their FE buffer and their fuel slot; the four
        // recovery devices only expose FE (the flywheel accepts FE through this storage).
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.GENERATION.get(),
                (device, side) -> device.getEnergyStorage());
        // Phase 41: only the fuel-burning kinds expose the fuel slot. A hydro / wind / solar /
        // geothermal device has no fuel slot in its GUI, so an item a pipe pushed in could never
        // be taken out again and was silently lost when the block was broken.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.GENERATION.get(),
                (device, side) -> device.hasFuel() ? device.getInventory() : null);
        // Phase 105.6: Nuclear Generator exposes its internal water-coolant FluidTank.
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.GENERATION.get(),
                (device, side) -> device.kind() == com.industrialcore.machine.GenerationKind.NUCLEAR
                        ? device.getCoolantFluidHandler() : null);
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.RECOVERY.get(),
                (device, side) -> device.getEnergyStorage());
        // Phase 89: Quantum Storage Core exposes unified item + fluid capabilities on every side.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.QUANTUM_STORAGE_CORE.get(),
                (storage, side) -> storage.getItemHandler());
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.QUANTUM_STORAGE_CORE.get(),
                (storage, side) -> storage.getFluidHandler());
        // Phase 90: Agriculture & Forestry machines expose FE & upgrade-protected item automation.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CROP_HARVESTER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.CROP_HARVESTER.get(),
                (be, side) -> be.getItemHandler());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.TREE_HARVESTER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.TREE_HARVESTER.get(),
                (be, side) -> be.getItemHandler());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.AUTO_FISHER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.AUTO_FISHER.get(),
                (be, side) -> be.getItemHandler());
        // Phase 91: Agriculture & Forestry branch wave 2 capabilities.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.RANCH_HARVESTER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.RANCH_HARVESTER.get(),
                (be, side) -> be.getItemHandler());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.GROWTH_ACCELERATOR.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.BIO_FEEDER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.BIO_FEEDER.get(),
                (be, side) -> be.getItemHandler());
        // Phase 92: Trap branch wave 1 capabilities.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.MOB_FAN.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ROTARY_DRILL.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ITEM_VACUUM.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ITEM_VACUUM.get(),
                (be, side) -> be.getItemHandler());
        // Phase 93: Trap branch wave 2 capabilities.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.XP_COLLECTOR.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.XP_COLLECTOR.get(),
                (be, side) -> be.getItemHandler());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.SPAWN_SUPPRESSOR.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.LASER_TURRET.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.MOB_DUPLICATOR.get(),
                (be, side) -> be.getEnergyStorage());
        // Phase 94: Building & Life branch wave 1 capabilities.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.AUTO_BUILDER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.AUTO_BUILDER.get(),
                (be, side) -> be.getItemHandler());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.FLUID_PUMP.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.FLUID_PUMP.get(),
                (be, side) -> be.getFluidHandler());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.FLUID_PUMP.get(),
                (be, side) -> be.getItemHandler());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.WIRELESS_CHARGER.get(),
                (be, side) -> be.getEnergyStorage());
        // Phase 95: Building & Life branch wave 2 capabilities.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CHUNK_LOADER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CLIMATE_CONTROLLER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.WARP_PAD.get(),
                (be, side) -> be.getEnergyStorage());
        // Phase 96: Quantum Storage Network capabilities (Importer, Exporter, Portable Terminal).
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.QUANTUM_IMPORTER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.QUANTUM_EXPORTER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerItem(
                Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> com.industrialcore.item.PortableQuantumTerminalItem.createEnergyCapability(stack),
                ModItems.PORTABLE_QUANTUM_TERMINAL.get());
        // Phase 98: Suit Module Modifier FE capability & Virtual Aegis 4-piece item FE capabilities.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.SUIT_MODIFIER.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerItem(
                Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> com.industrialcore.item.VirtualSuitArmorItem.createEnergyCapability(stack),
                ModItems.VIRTUAL_HELMET.get(),
                ModItems.VIRTUAL_CHESTPLATE.get(),
                ModItems.VIRTUAL_LEGGINGS.get(),
                ModItems.VIRTUAL_BOOTS.get());
        // Phase 99: Singularity Reactor (FE + fuel IItemHandler) & Hyper Energy Conduit FE capability.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.SINGULARITY_REACTOR.get(),
                (be, side) -> be.getEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SINGULARITY_REACTOR.get(),
                (be, side) -> be.getAutomationItemHandler());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.HYPER_ENERGY_CONDUIT.get(),
                (be, side) -> be.getEnergyStorage(side));
        // Phase 101: 4 Electric Weapon FE item capabilities (compatible with wireless_charger & suit_modifier).
        event.registerItem(
                Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> com.industrialcore.item.ElectricWeaponItem.createEnergyCapability(stack),
                ModItems.PULSE_LASER.get(),
                ModItems.RAILGUN.get(),
                ModItems.QUANTUM_BLADE.get(),
                ModItems.ENERGY_BOW.get());
        // Phase 103: Reactor Port delegates Energy & Item capabilities to its formed controller.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.REACTOR_PORT.get(),
                (port, side) -> port.getProxiedEnergyStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.REACTOR_PORT.get(),
                (port, side) -> port.getProxiedItemHandler());
        // Phase 105.6: Reactor Port delegates cooling-water FluidHandler; Fluid Pipe routes fluids.
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.REACTOR_PORT.get(),
                (port, side) -> port.getProxiedFluidHandler());
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.FLUID_PIPE.get(),
                (pipe, side) -> pipe.getFluidHandler(side));
        // Phase 104: Common Large Machine Bounding Proxy delegates Energy, Item, and Fluid capabilities to master.
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.MACHINE_BOUNDING.get(),
                (proxy, side) -> proxy.getProxiedEnergy(side));
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.MACHINE_BOUNDING.get(),
                (proxy, side) -> proxy.getProxiedItems(side));
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.MACHINE_BOUNDING.get(),
                (proxy, side) -> proxy.getProxiedFluids(side));
    }

    private static void registerEntityAttributes(
            net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModEntities.RIFT_STALKER.get(),
                com.industrialcore.raid.entity.RiftStalkerEntity.createAttributes().build());
        event.put(ModEntities.VOID_SENTINEL.get(),
                com.industrialcore.raid.entity.VoidSentinelEntity.createAttributes().build());
        event.put(ModEntities.PHASE_JUGGERNAUT.get(),
                com.industrialcore.raid.entity.PhaseJuggernautEntity.createAttributes().build());
        event.put(ModEntities.DIMENSIONAL_SOVEREIGN.get(),
                com.industrialcore.raid.entity.DimensionalSovereignEntity.createAttributes().build());
        event.put(ModEntities.RIFT_ANCHOR_CRYSTAL.get(),
                com.industrialcore.raid.entity.RiftAnchorCrystalEntity.createAttributes().build());
    }
}

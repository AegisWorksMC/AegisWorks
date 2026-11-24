package com.industrialcore.registry;

import com.industrialcore.IndustrialCore;

import com.industrialcore.block.AlloyFurnaceBlock;
import com.industrialcore.block.AssemblerBlock;
import com.industrialcore.block.CableBlock;
import com.industrialcore.block.ConveyorBlock;
import com.industrialcore.block.CrafterBlock;
import com.industrialcore.block.EnergyBufferBlock;
import com.industrialcore.block.GenerationBlock;
import com.industrialcore.block.ItemDistributorBlock;
import com.industrialcore.block.ItemPipeBlock;
import com.industrialcore.block.LargeQuarryBlock;
import com.industrialcore.block.MatterAmplifierBlock;
import com.industrialcore.block.MonitorBlock;
import com.industrialcore.block.OreSynthesizerBlock;
import com.industrialcore.block.PhaseReactorBlock;
import com.industrialcore.block.ProcessingBlock;
import com.industrialcore.block.QuantumCompressorBlock;
import com.industrialcore.block.AutoBuilderBlock;
import com.industrialcore.block.AutoFisherBlock;
import com.industrialcore.block.BioFeederBlock;
import com.industrialcore.block.ChunkLoaderBlock;
import com.industrialcore.block.ClimateControllerBlock;
import com.industrialcore.block.CropHarvesterBlock;
import com.industrialcore.block.FluidPumpBlock;
import com.industrialcore.block.GrowthAcceleratorBlock;
import com.industrialcore.block.ItemVacuumBlock;
import com.industrialcore.block.LaserTurretBlock;
import com.industrialcore.block.MobDuplicatorBlock;
import com.industrialcore.block.MobFanBlock;
import com.industrialcore.block.QuantumStorageBlock;
import com.industrialcore.block.RanchHarvesterBlock;
import com.industrialcore.block.RotaryDrillBlock;
import com.industrialcore.block.SpawnSuppressorBlock;
import com.industrialcore.block.TreeHarvesterBlock;
import com.industrialcore.block.WarpPadBlock;
import com.industrialcore.block.WirelessChargerBlock;
import com.industrialcore.block.XpCollectorBlock;
import com.industrialcore.block.QuarryBlock;
import com.industrialcore.block.ReconstructorBlock;
import com.industrialcore.block.RecoveryBlock;
import com.industrialcore.block.SeedCultivatorBlock;
import com.industrialcore.block.AdvancedTransporterDriveBlock;
import com.industrialcore.block.AdvancedTransporterPartBlock;
import com.industrialcore.block.TransporterBlock;
import com.industrialcore.block.TransporterCasingBlock;
import com.industrialcore.machine.BufferTier;
import com.industrialcore.machine.CableTier;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.PipeTier;
import com.industrialcore.machine.ProcessingKind;
import com.industrialcore.machine.RecoveryKind;
import com.industrialcore.machine.TransporterKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IndustrialCore.MOD_ID);
    public static final DeferredBlock<QuarryBlock> QUARRY = BLOCKS.register("quarry", () -> new QuarryBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 8.0f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<LargeQuarryBlock> LARGE_QUARRY = BLOCKS.register("large_quarry", () -> new LargeQuarryBlock(ModBlocks.machineProperties(6.0f, 10.0f)));
    public static final DeferredBlock<ConveyorBlock> CONVEYOR = BLOCKS.register("conveyor", () -> new ConveyorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 4.0f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<ItemPipeBlock> ITEM_PIPE = ModBlocks.registerPipe("item_pipe", PipeTier.BASIC);
    public static final DeferredBlock<ItemPipeBlock> IMPROVED_ITEM_PIPE = ModBlocks.registerPipe("pipe_improved", PipeTier.IMPROVED);
    public static final DeferredBlock<ItemPipeBlock> ADVANCED_ITEM_PIPE = ModBlocks.registerPipe("pipe_advanced", PipeTier.ADVANCED);
    public static final DeferredBlock<ItemPipeBlock> ULTIMATE_ITEM_PIPE = ModBlocks.registerPipe("pipe_ultimate", PipeTier.ULTIMATE);
    public static final DeferredBlock<CableBlock> CABLE_BASIC = ModBlocks.registerCable("cable_basic", CableTier.BASIC);
    public static final DeferredBlock<CableBlock> CABLE_IMPROVED = ModBlocks.registerCable("cable_improved", CableTier.IMPROVED);
    public static final DeferredBlock<CableBlock> CABLE_ADVANCED = ModBlocks.registerCable("cable_advanced", CableTier.ADVANCED);
    public static final DeferredBlock<CableBlock> CABLE_ULTIMATE = ModBlocks.registerCable("cable_ultimate", CableTier.ULTIMATE);
    public static final DeferredBlock<CrafterBlock> CRAFTER = BLOCKS.register("crafter", () -> new CrafterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()));
    public static final DeferredBlock<ProcessingBlock> CRUSHER = BLOCKS.register("crusher", () -> new ProcessingBlock(ModBlocks.machineProperties(4.0f, 6.0f), ProcessingKind.CRUSHER));
    public static final DeferredBlock<ProcessingBlock> ELECTRIC_FURNACE = BLOCKS.register("electric_furnace", () -> new ProcessingBlock(ModBlocks.machineProperties(4.0f, 6.0f), ProcessingKind.ELECTRIC_FURNACE));
    public static final DeferredBlock<ProcessingBlock> ELECTROLYZER = BLOCKS.register("electrolyzer", () -> new ProcessingBlock(ModBlocks.machineProperties(4.5f, 7.0f), ProcessingKind.ELECTROLYZER));
    public static final DeferredBlock<ProcessingBlock> PRECISION_CRUSHER = BLOCKS.register("precision_crusher", () -> new ProcessingBlock(ModBlocks.machineProperties(5.0f, 8.0f), ProcessingKind.PRECISION_CRUSHER));
    public static final DeferredBlock<ProcessingBlock> FAST_CRUSHER = BLOCKS.register("fast_crusher", () -> new ProcessingBlock(ModBlocks.machineProperties(5.0f, 8.0f), ProcessingKind.FAST_CRUSHER));
    public static final DeferredBlock<ReconstructorBlock> RECONSTRUCTOR = BLOCKS.register("reconstructor", () -> new ReconstructorBlock(ModBlocks.machineProperties(5.0f, 8.0f)));
    public static final DeferredBlock<ProcessingBlock> METAL_PRESS = BLOCKS.register("metal_press", () -> new ProcessingBlock(ModBlocks.machineProperties(4.0f, 6.0f), ProcessingKind.METAL_PRESS));
    public static final DeferredBlock<AlloyFurnaceBlock> ALLOY_FURNACE = BLOCKS.register("alloy_furnace", () -> new AlloyFurnaceBlock(ModBlocks.machineProperties(4.0f, 6.0f)));
    public static final DeferredBlock<EnergyBufferBlock> ENERGY_BUFFER = BLOCKS.register("energy_buffer", () -> new EnergyBufferBlock(ModBlocks.machineProperties(5.0f, 8.0f), BufferTier.BASIC));
    public static final DeferredBlock<AssemblerBlock> ASSEMBLER = BLOCKS.register("assembler", () -> new AssemblerBlock(ModBlocks.machineProperties(4.5f, 7.0f)));
    public static final DeferredBlock<EnergyBufferBlock> ENERGY_BUFFER_MK2 = BLOCKS.register("energy_buffer_mk2", () -> new EnergyBufferBlock(ModBlocks.machineProperties(5.5f, 9.0f), BufferTier.REINFORCED));
    public static final DeferredBlock<EnergyBufferBlock> ENERGY_BUFFER_MK3 = BLOCKS.register("energy_buffer_mk3", () -> new EnergyBufferBlock(ModBlocks.machineProperties(6.0f, 10.0f), BufferTier.HIGH_DENSITY));
    public static final DeferredBlock<ItemDistributorBlock> ITEM_DISTRIBUTOR = BLOCKS.register("item_distributor", () -> new ItemDistributorBlock(ModBlocks.machineProperties(4.0f, 6.0f)));
    public static final DeferredBlock<SeedCultivatorBlock> SEED_CULTIVATOR = BLOCKS.register("seed_cultivator", () -> new SeedCultivatorBlock(ModBlocks.machineProperties(5.0f, 8.0f)));
    public static final DeferredBlock<MatterAmplifierBlock> MATTER_AMPLIFIER = BLOCKS.register("matter_amplifier", () -> new MatterAmplifierBlock(ModBlocks.machineProperties(5.5f, 9.0f)));
    /** Phase 82: factory monitor terminal (read-only statistics panel). */
    public static final DeferredBlock<MonitorBlock> FACTORY_MONITOR = BLOCKS.register("factory_monitor", () -> new MonitorBlock(ModBlocks.machineProperties(5.0f, 8.0f)));
    /** Phase 89: Quantum Storage Core (BigInteger item + fluid vault with 3x3 auto-refill crafting). */
    public static final DeferredBlock<QuantumStorageBlock> QUANTUM_STORAGE_CORE = BLOCKS.register("quantum_storage_core", () -> new QuantumStorageBlock(ModBlocks.machineProperties(6.0f, 12.0f)));
    /** Phase 90: Agriculture & Forestry branch wave 1 (crop harvester, tree harvester, auto fisher). */
    public static final DeferredBlock<CropHarvesterBlock> CROP_HARVESTER = BLOCKS.register("crop_harvester", () -> new CropHarvesterBlock(ModBlocks.machineProperties(4.5f, 7.0f)));
    public static final DeferredBlock<TreeHarvesterBlock> TREE_HARVESTER = BLOCKS.register("tree_harvester", () -> new TreeHarvesterBlock(ModBlocks.machineProperties(5.0f, 8.0f)));
    public static final DeferredBlock<AutoFisherBlock> AUTO_FISHER = BLOCKS.register("auto_fisher", () -> new AutoFisherBlock(ModBlocks.machineProperties(4.5f, 7.0f)));
    /** Phase 91: Agriculture & Forestry branch wave 2 (ranch harvester, growth accelerator, bio feeder). */
    public static final DeferredBlock<RanchHarvesterBlock> RANCH_HARVESTER = BLOCKS.register("ranch_harvester", () -> new RanchHarvesterBlock(ModBlocks.machineProperties(5.0f, 8.0f)));
    public static final DeferredBlock<GrowthAcceleratorBlock> GROWTH_ACCELERATOR = BLOCKS.register("growth_accelerator", () -> new GrowthAcceleratorBlock(ModBlocks.machineProperties(5.5f, 9.0f)));
    public static final DeferredBlock<BioFeederBlock> BIO_FEEDER = BLOCKS.register("bio_feeder", () -> new BioFeederBlock(ModBlocks.machineProperties(6.0f, 10.0f)));
    /** Phase 92: Trap branch wave 1 (mob fan, rotary drill, item vacuum). */
    public static final DeferredBlock<MobFanBlock> MOB_FAN = BLOCKS.register("mob_fan", () -> new MobFanBlock(ModBlocks.machineProperties(4.5f, 7.0f)));
    public static final DeferredBlock<RotaryDrillBlock> ROTARY_DRILL = BLOCKS.register("rotary_drill", () -> new RotaryDrillBlock(ModBlocks.machineProperties(5.0f, 8.0f)));
    public static final DeferredBlock<ItemVacuumBlock> ITEM_VACUUM = BLOCKS.register("item_vacuum", () -> new ItemVacuumBlock(ModBlocks.machineProperties(4.5f, 7.0f)));
    /** Phase 93: Trap branch wave 2 (xp collector, spawn suppressor, laser turret, mob duplicator). */
    public static final DeferredBlock<XpCollectorBlock> XP_COLLECTOR = BLOCKS.register("xp_collector", () -> new XpCollectorBlock(ModBlocks.machineProperties(5.0f, 8.0f)));
    public static final DeferredBlock<SpawnSuppressorBlock> SPAWN_SUPPRESSOR = BLOCKS.register("spawn_suppressor", () -> new SpawnSuppressorBlock(ModBlocks.machineProperties(5.0f, 8.0f)));
    public static final DeferredBlock<LaserTurretBlock> LASER_TURRET = BLOCKS.register("laser_turret", () -> new LaserTurretBlock(ModBlocks.machineProperties(5.5f, 9.0f)));
    public static final DeferredBlock<MobDuplicatorBlock> MOB_DUPLICATOR = BLOCKS.register("mob_duplicator", () -> new MobDuplicatorBlock(ModBlocks.machineProperties(6.0f, 10.0f)));
    /** Phase 94: Building & Life branch wave 1 (auto builder, fluid pump, wireless charger). */
    public static final DeferredBlock<AutoBuilderBlock> AUTO_BUILDER = BLOCKS.register("auto_builder", () -> new AutoBuilderBlock(ModBlocks.machineProperties(4.5f, 7.0f)));
    public static final DeferredBlock<FluidPumpBlock> FLUID_PUMP = BLOCKS.register("fluid_pump", () -> new FluidPumpBlock(ModBlocks.machineProperties(5.0f, 8.0f)));
    public static final DeferredBlock<WirelessChargerBlock> WIRELESS_CHARGER = BLOCKS.register("wireless_charger", () -> new WirelessChargerBlock(ModBlocks.machineProperties(5.5f, 9.0f)));
    /** Phase 95: Building & Life branch wave 2 (chunk loader, climate controller, warp pad). */
    public static final DeferredBlock<ChunkLoaderBlock> CHUNK_LOADER = BLOCKS.register("chunk_loader", () -> new ChunkLoaderBlock(ModBlocks.machineProperties(5.5f, 9.0f)));
    public static final DeferredBlock<ClimateControllerBlock> CLIMATE_CONTROLLER = BLOCKS.register("climate_controller", () -> new ClimateControllerBlock(ModBlocks.machineProperties(6.0f, 10.0f)));
    public static final DeferredBlock<WarpPadBlock> WARP_PAD = BLOCKS.register("warp_pad", () -> new WarpPadBlock(ModBlocks.machineProperties(6.5f, 12.0f)));
    /** Phase 96: Quantum Storage Network (importer & exporter). */
    public static final DeferredBlock<com.industrialcore.block.QuantumImporterBlock> QUANTUM_IMPORTER = BLOCKS.register("quantum_importer", () -> new com.industrialcore.block.QuantumImporterBlock(ModBlocks.machineProperties(5.5f, 9.0f)));
    public static final DeferredBlock<com.industrialcore.block.QuantumExporterBlock> QUANTUM_EXPORTER = BLOCKS.register("quantum_exporter", () -> new com.industrialcore.block.QuantumExporterBlock(ModBlocks.machineProperties(5.5f, 9.0f)));
    /** Phase 98: Virtual Aegis Suit Module Modifier. */
    public static final DeferredBlock<com.industrialcore.block.SuitModifierBlock> SUIT_MODIFIER = BLOCKS.register("suit_modifier", () -> new com.industrialcore.block.SuitModifierBlock(ModBlocks.machineProperties(5.0f, 9.0f)));
    /** Phase 99: Endgame Inflation (Singularity Reactor, Hyper Energy Conduit, Hyper Item Conduit). */
    public static final DeferredBlock<com.industrialcore.block.SingularityReactorBlock> SINGULARITY_REACTOR = BLOCKS.register("singularity_reactor", () -> new com.industrialcore.block.SingularityReactorBlock(ModBlocks.machineProperties(7.0f, 15.0f)));
    public static final DeferredBlock<com.industrialcore.block.HyperEnergyConduitBlock> HYPER_ENERGY_CONDUIT = BLOCKS.register("hyper_energy_conduit", () -> new com.industrialcore.block.HyperEnergyConduitBlock(ModBlocks.machineProperties(2.5f, 6.0f).forceSolidOn()));
    public static final DeferredBlock<com.industrialcore.block.HyperItemConduitBlock> HYPER_ITEM_CONDUIT = BLOCKS.register("hyper_item_conduit", () -> new com.industrialcore.block.HyperItemConduitBlock(ModBlocks.machineProperties(2.5f, 6.0f).forceSolidOn()));
    public static final DeferredBlock<PhaseReactorBlock> PHASE_REACTOR = BLOCKS.register("phase_reactor", () -> new PhaseReactorBlock(ModBlocks.machineProperties(6.0f, 10.0f)));
    public static final DeferredBlock<QuantumCompressorBlock> QUANTUM_COMPRESSOR = BLOCKS.register("quantum_compressor", () -> new QuantumCompressorBlock(ModBlocks.machineProperties(6.0f, 10.0f)));
    public static final DeferredBlock<TransporterBlock> BASIC_ITEM_TRANSPORTER = BLOCKS.register("basic_item_transporter", () -> new TransporterBlock(ModBlocks.machineProperties(4.5f, 7.0f), TransporterKind.BASIC));
    public static final DeferredBlock<TransporterBlock> ADVANCED_ITEM_TRANSPORTER = BLOCKS.register("advanced_item_transporter", () -> new TransporterBlock(ModBlocks.machineProperties(5.0f, 8.0f), TransporterKind.ADVANCED));
    public static final DeferredBlock<TransporterBlock> QUANTUM_TRANSPORTER = BLOCKS.register("quantum_transporter", () -> new TransporterBlock(ModBlocks.machineProperties(6.0f, 10.0f), TransporterKind.QUANTUM));
    public static final DeferredBlock<TransporterBlock> QUANTUM_TRANSPORTER_ARRAY = BLOCKS.register("quantum_transporter_array", () -> new TransporterBlock(ModBlocks.machineProperties(6.5f, 12.0f), TransporterKind.ARRAY));
    public static final DeferredBlock<TransporterCasingBlock> TRANSPORTER_CASING =
            BLOCKS.register("transporter_casing", () -> new TransporterCasingBlock(
                    ModBlocks.machineProperties(4.5f, 7.0f)));
    // Phase 63: the self-building Advanced Transporter. The drive is the controller, the part is
    // every other cell of the 3x2x3 machine. Phase 62's controller and casing stay registered.
    public static final DeferredBlock<AdvancedTransporterDriveBlock> ADVANCED_TRANSPORTER_DRIVE =
            BLOCKS.register("advanced_transporter_drive", () -> new AdvancedTransporterDriveBlock(
                    ModBlocks.machineProperties(5.0f, 8.0f)));
    public static final DeferredBlock<AdvancedTransporterPartBlock> ADVANCED_TRANSPORTER_PART =
            BLOCKS.register("advanced_transporter_part", () -> new AdvancedTransporterPartBlock(
                    ModBlocks.machineProperties(5.0f, 8.0f)));

    // Phase 62: the Advanced casing carries the visual part of the formed machine (part + facing).
    public static final DeferredBlock<TransporterCasingBlock> ADVANCED_TRANSPORTER_CASING =
            BLOCKS.register("advanced_transporter_casing", () -> new TransporterCasingBlock(
                    ModBlocks.machineProperties(5.0f, 8.0f)));
    public static final DeferredBlock<TransporterCasingBlock> QUANTUM_TRANSPORTER_CASING =
            BLOCKS.register("quantum_transporter_casing", () -> new TransporterCasingBlock(
                    ModBlocks.machineProperties(6.0f, 10.0f)));
    public static final DeferredBlock<Block> ASTRAL_SILVER_ORE = ModBlocks.registerOre("astral_silver_ore");
    public static final DeferredBlock<Block> MITHRIL_ORE = ModBlocks.registerOre("mithril_ore");
    public static final DeferredBlock<Block> NEBULA_ORE = ModBlocks.registerOre("nebula_ore");
    public static final DeferredBlock<Block> ORICHALCUM_ORE = ModBlocks.registerOre("orichalcum_ore");
    public static final DeferredBlock<Block> QUANTUM_ORE = ModBlocks.registerOre("quantum_ore");
    public static final DeferredBlock<Block> ADAMANTITE_ORE = ModBlocks.registerOre("adamantite_ore");
    public static final DeferredBlock<Block> AETHER_ORE = ModBlocks.registerOre("aether_ore");
    public static final DeferredBlock<Block> VOID_IRON_ORE = ModBlocks.registerOre("void_iron_ore");
    // Phase 105.6: Uranium Ore (stone & deepslate variants) & connected Fluid Pipe.
    public static final DeferredBlock<Block> URANIUM_ORE = ModBlocks.registerOre("uranium_ore");
    public static final DeferredBlock<Block> DEEPSLATE_URANIUM_ORE = BLOCKS.register("deepslate_uranium_ore",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE)));
    public static final DeferredBlock<com.industrialcore.block.FluidPipeBlock> FLUID_PIPE = BLOCKS.register("fluid_pipe",
            () -> new com.industrialcore.block.FluidPipeBlock(ModBlocks.machineProperties(2.0f, 4.5f).forceSolidOn()));
    public static final DeferredBlock<OreSynthesizerBlock> ORE_SYNTHESIZER = BLOCKS.register("ore_synthesizer", () -> new OreSynthesizerBlock(ModBlocks.machineProperties(6.0f, 10.0f)));

    private static DeferredBlock<Block> registerOre(String name) {
        return BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.STONE)));
    }

    /**
     * Properties of every custom 3D machine. Phase 79: every machine model has bevels, recesses or
     * gaps, so none of them may occlude their neighbours - otherwise a face of the stone, grass or
     * iron block behind the model is culled and the world shows through (the "X-ray" bug).
     */
    private static BlockBehaviour.Properties machineProperties(float strength, float resistance) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(strength, resistance)
                .requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion();
    }

    private static DeferredBlock<Block> registerCasing(String name, float strength, float resistance) {
        return BLOCKS.register(name, () -> new Block(ModBlocks.machineProperties(strength, resistance)));
    }

    private static DeferredBlock<CableBlock> registerCable(String name, CableTier tier) {
        return BLOCKS.register(name, () -> new CableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.2f, 2.5f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().forceSolidOn(), tier));
    }

    private static DeferredBlock<ItemPipeBlock> registerPipe(String name, PipeTier tier) {
        return BLOCKS.register(name, () -> new ItemPipeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f, 3.0f).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().forceSolidOn(), tier));
    }

    // Phase 33: power system - six conventional generators (shared GenerationBlock, the kind
    // comes from the block instance; one block entity, one menu, one screen serve all six).
    public static final DeferredBlock<GenerationBlock> THERMAL_GENERATOR =
            registerPower("thermal_generator", 4.0F, 6.0F, GenerationKind.THERMAL);
    public static final DeferredBlock<GenerationBlock> HYDRO_GENERATOR =
            registerPower("hydro_generator", 4.0F, 6.0F, GenerationKind.HYDRO);
    public static final DeferredBlock<GenerationBlock> WIND_GENERATOR =
            registerPower("wind_generator", 4.0F, 6.0F, GenerationKind.WIND);
    public static final DeferredBlock<GenerationBlock> SOLAR_GENERATOR =
            registerPower("solar_generator", 4.0F, 6.0F, GenerationKind.SOLAR);
    public static final DeferredBlock<GenerationBlock> COMBUSTION_GENERATOR =
            registerPower("combustion_generator", 4.5F, 7.0F, GenerationKind.COMBUSTION);
    public static final DeferredBlock<GenerationBlock> NUCLEAR_GENERATOR =
            registerPower("nuclear_generator", 6.0F, 10.0F, GenerationKind.NUCLEAR);
    // Phase 41: geothermal generator - no fuel, only an adjacent vanilla lava block.
    public static final DeferredBlock<GenerationBlock> GEOTHERMAL_GENERATOR =
            registerPower("geothermal_generator", 4.5F, 7.0F, GenerationKind.GEOTHERMAL);

    // Phase 33: four recovery devices (shared RecoveryBlock, no facing, single model).
    public static final DeferredBlock<RecoveryBlock> FLYWHEEL =
            registerRecovery("flywheel", 5.0F, 8.0F, RecoveryKind.FLYWHEEL);
    public static final DeferredBlock<RecoveryBlock> PRESSURE_RECOVERY =
            registerRecovery("pressure_recovery", 4.5F, 7.0F, RecoveryKind.PRESSURE);
    public static final DeferredBlock<RecoveryBlock> HEAT_RECOVERY =
            registerRecovery("heat_recovery", 4.5F, 7.0F, RecoveryKind.HEAT);
    public static final DeferredBlock<RecoveryBlock> REGEN_GENERATOR =
            registerRecovery("regen_generator", 4.5F, 7.0F, RecoveryKind.REGEN);

    private static DeferredBlock<GenerationBlock> registerPower(String name, float hardness,
                                                                float resistance, GenerationKind kind) {
        if (kind == GenerationKind.NUCLEAR) {
            return BLOCKS.register(name, () -> new com.industrialcore.block.NuclearGeneratorBlock(
                    machineProperties(hardness, resistance)
                            .lightLevel(s -> s.getValue(com.industrialcore.block.NuclearGeneratorBlock.LIT) ? 10 : 0)));
        }
        return BLOCKS.register(name, () -> new GenerationBlock(
                machineProperties(hardness, resistance), kind));
    }

    private static DeferredBlock<RecoveryBlock> registerRecovery(String name, float hardness,
                                                                 float resistance, RecoveryKind kind) {
        return BLOCKS.register(name, () -> new RecoveryBlock(
                machineProperties(hardness, resistance), kind));
    }

    // Phase 103: Multiblock Reactor structure blocks (5 blocks).
    public static final DeferredBlock<com.industrialcore.block.ReactorPartBlock> REACTOR_CASING =
            BLOCKS.register("reactor_casing", () -> new com.industrialcore.block.ReactorPartBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(5.0F, 12.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
    public static final DeferredBlock<com.industrialcore.block.ReactorGlassBlock> REACTOR_GLASS =
            BLOCKS.register("reactor_glass", () -> new com.industrialcore.block.ReactorGlassBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.NONE)
                            .strength(4.0F, 12.0F).requiresCorrectToolForDrops().sound(SoundType.GLASS)
                            .noOcclusion()
                            .isValidSpawn((s, l, p, e) -> false)
                            .isRedstoneConductor((s, l, p) -> false)
                            .isSuffocating((s, l, p) -> false)
                            .isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<com.industrialcore.block.ReactorCoreCellBlock> REACTOR_CORE_CELL =
            BLOCKS.register("reactor_core_cell", () -> new com.industrialcore.block.ReactorCoreCellBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(5.0F, 12.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
                            .lightLevel(s -> s.getValue(com.industrialcore.block.ReactorCoreCellBlock.LIT) ? 15 : 3)));
    public static final DeferredBlock<com.industrialcore.block.ReactorPartBlock> REACTOR_COOLANT_BLOCK =
            BLOCKS.register("reactor_coolant_block", () -> new com.industrialcore.block.ReactorPartBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.ICE)
                            .strength(4.5F, 10.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
                            .lightLevel(s -> 4)));
    public static final DeferredBlock<com.industrialcore.block.ReactorPortBlock> REACTOR_PORT =
            BLOCKS.register("reactor_port", () -> new com.industrialcore.block.ReactorPortBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(5.0F, 12.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
    // Phase 104: Common invisible multiblock bounding proxy (internal block, NOT registered in creative tab).
    public static final DeferredBlock<com.industrialcore.block.MachineBoundingBlock> MACHINE_BOUNDING =
            BLOCKS.register("machine_bounding", () -> new com.industrialcore.block.MachineBoundingBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(5.5F, 12.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
                            .noOcclusion()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    private ModBlocks() {
    }
}

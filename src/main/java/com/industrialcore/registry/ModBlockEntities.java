package com.industrialcore.registry;

import com.industrialcore.IndustrialCore;

import com.industrialcore.blockentity.AdvancedTransporterDriveBlockEntity;
import com.industrialcore.blockentity.AlloyFurnaceBlockEntity;
import com.industrialcore.blockentity.AssemblerBlockEntity;
import com.industrialcore.blockentity.CableBlockEntity;
import com.industrialcore.blockentity.ConveyorBlockEntity;
import com.industrialcore.blockentity.CrafterBlockEntity;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.ItemDistributorBlockEntity;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.blockentity.LargeQuarryBlockEntity;
import com.industrialcore.blockentity.MatterAmplifierBlockEntity;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.blockentity.PhaseReactorBlockEntity;
import com.industrialcore.blockentity.ProcessingBlockEntity;
import com.industrialcore.blockentity.MonitorBlockEntity;
import com.industrialcore.blockentity.QuantumCompressorBlockEntity;
import com.industrialcore.blockentity.AutoFisherBlockEntity;
import com.industrialcore.blockentity.BioFeederBlockEntity;
import com.industrialcore.blockentity.CropHarvesterBlockEntity;
import com.industrialcore.blockentity.GrowthAcceleratorBlockEntity;
import com.industrialcore.blockentity.ItemVacuumBlockEntity;
import com.industrialcore.blockentity.LaserTurretBlockEntity;
import com.industrialcore.blockentity.MobDuplicatorBlockEntity;
import com.industrialcore.blockentity.MobFanBlockEntity;
import com.industrialcore.blockentity.QuantumStorageBlockEntity;
import com.industrialcore.blockentity.RanchHarvesterBlockEntity;
import com.industrialcore.blockentity.RotaryDrillBlockEntity;
import com.industrialcore.blockentity.SpawnSuppressorBlockEntity;
import com.industrialcore.blockentity.TreeHarvesterBlockEntity;
import com.industrialcore.blockentity.XpCollectorBlockEntity;
import com.industrialcore.blockentity.QuarryBlockEntity;
import com.industrialcore.blockentity.ReconstructorBlockEntity;
import com.industrialcore.blockentity.RecoveryBlockEntity;
import com.industrialcore.blockentity.SeedCultivatorBlockEntity;
import com.industrialcore.blockentity.TransporterBlockEntity;
import com.industrialcore.machine.TransporterKind;
import com.industrialcore.registry.ModBlocks;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IndustrialCore.MOD_ID);
    public static final Supplier<BlockEntityType<QuarryBlockEntity>> QUARRY = BLOCK_ENTITIES.register("quarry", () -> BlockEntityType.Builder.of(QuarryBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.QUARRY.get()}).build(null));
    public static final Supplier<BlockEntityType<LargeQuarryBlockEntity>> LARGE_QUARRY = BLOCK_ENTITIES.register("large_quarry", () -> BlockEntityType.Builder.of(LargeQuarryBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.LARGE_QUARRY.get()}).build(null));
    public static final Supplier<BlockEntityType<ConveyorBlockEntity>> CONVEYOR = BLOCK_ENTITIES.register("conveyor", () -> BlockEntityType.Builder.of(ConveyorBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.CONVEYOR.get()}).build(null));
    public static final Supplier<BlockEntityType<ItemPipeBlockEntity>> ITEM_PIPE = BLOCK_ENTITIES.register("item_pipe", () -> BlockEntityType.Builder.of(ItemPipeBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.ITEM_PIPE.get(), (Block)ModBlocks.IMPROVED_ITEM_PIPE.get(), (Block)ModBlocks.ADVANCED_ITEM_PIPE.get(), (Block)ModBlocks.ULTIMATE_ITEM_PIPE.get()}).build(null));
    public static final Supplier<BlockEntityType<CableBlockEntity>> CABLE = BLOCK_ENTITIES.register("cable", () -> BlockEntityType.Builder.of(CableBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.CABLE_BASIC.get(), (Block)ModBlocks.CABLE_IMPROVED.get(), (Block)ModBlocks.CABLE_ADVANCED.get(), (Block)ModBlocks.CABLE_ULTIMATE.get()}).build(null));
    public static final Supplier<BlockEntityType<CrafterBlockEntity>> CRAFTER = BLOCK_ENTITIES.register("crafter", () -> BlockEntityType.Builder.of(CrafterBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.CRAFTER.get()}).build(null));
    public static final Supplier<BlockEntityType<ProcessingBlockEntity>> PROCESSING = BLOCK_ENTITIES.register("processing", () -> BlockEntityType.Builder.of(ProcessingBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.CRUSHER.get(), (Block)ModBlocks.ELECTRIC_FURNACE.get(), (Block)ModBlocks.ELECTROLYZER.get(), (Block)ModBlocks.PRECISION_CRUSHER.get(), (Block)ModBlocks.FAST_CRUSHER.get(), (Block)ModBlocks.METAL_PRESS.get()}).build(null));
    public static final Supplier<BlockEntityType<ReconstructorBlockEntity>> RECONSTRUCTOR = BLOCK_ENTITIES.register("reconstructor", () -> BlockEntityType.Builder.of(ReconstructorBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.RECONSTRUCTOR.get()}).build(null));
    public static final Supplier<BlockEntityType<AlloyFurnaceBlockEntity>> ALLOY_FURNACE = BLOCK_ENTITIES.register("alloy_furnace", () -> BlockEntityType.Builder.of(AlloyFurnaceBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.ALLOY_FURNACE.get()}).build(null));
    public static final Supplier<BlockEntityType<EnergyBufferBlockEntity>> ENERGY_BUFFER = BLOCK_ENTITIES.register("energy_buffer", () -> BlockEntityType.Builder.of(EnergyBufferBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.ENERGY_BUFFER.get(), (Block)ModBlocks.ENERGY_BUFFER_MK2.get(), (Block)ModBlocks.ENERGY_BUFFER_MK3.get()}).build(null));
    public static final Supplier<BlockEntityType<AssemblerBlockEntity>> ASSEMBLER = BLOCK_ENTITIES.register("assembler", () -> BlockEntityType.Builder.of(AssemblerBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.ASSEMBLER.get()}).build(null));
    public static final Supplier<BlockEntityType<ItemDistributorBlockEntity>> ITEM_DISTRIBUTOR = BLOCK_ENTITIES.register("item_distributor", () -> BlockEntityType.Builder.of(ItemDistributorBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.ITEM_DISTRIBUTOR.get()}).build(null));
    public static final Supplier<BlockEntityType<SeedCultivatorBlockEntity>> SEED_CULTIVATOR = BLOCK_ENTITIES.register("seed_cultivator", () -> BlockEntityType.Builder.of(SeedCultivatorBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.SEED_CULTIVATOR.get()}).build(null));
    public static final Supplier<BlockEntityType<MatterAmplifierBlockEntity>> MATTER_AMPLIFIER = BLOCK_ENTITIES.register("matter_amplifier", () -> BlockEntityType.Builder.of(MatterAmplifierBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.MATTER_AMPLIFIER.get()}).build(null));
    /** Phase 82: factory monitor. */
    public static final Supplier<BlockEntityType<MonitorBlockEntity>> FACTORY_MONITOR = BLOCK_ENTITIES.register("factory_monitor", () -> BlockEntityType.Builder.of(MonitorBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.FACTORY_MONITOR.get()}).build(null));
    /** Phase 89: Quantum Storage Core. */
    public static final Supplier<BlockEntityType<QuantumStorageBlockEntity>> QUANTUM_STORAGE_CORE = BLOCK_ENTITIES.register("quantum_storage_core", () -> BlockEntityType.Builder.of(QuantumStorageBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.QUANTUM_STORAGE_CORE.get()}).build(null));
    /** Phase 90: Agriculture & Forestry branch wave 1. */
    public static final Supplier<BlockEntityType<CropHarvesterBlockEntity>> CROP_HARVESTER = BLOCK_ENTITIES.register("crop_harvester", () -> BlockEntityType.Builder.of(CropHarvesterBlockEntity::new, ModBlocks.CROP_HARVESTER.get()).build(null));
    public static final Supplier<BlockEntityType<TreeHarvesterBlockEntity>> TREE_HARVESTER = BLOCK_ENTITIES.register("tree_harvester", () -> BlockEntityType.Builder.of(TreeHarvesterBlockEntity::new, ModBlocks.TREE_HARVESTER.get()).build(null));
    public static final Supplier<BlockEntityType<AutoFisherBlockEntity>> AUTO_FISHER = BLOCK_ENTITIES.register("auto_fisher", () -> BlockEntityType.Builder.of(AutoFisherBlockEntity::new, ModBlocks.AUTO_FISHER.get()).build(null));
    /** Phase 91: Agriculture & Forestry branch wave 2. */
    public static final Supplier<BlockEntityType<RanchHarvesterBlockEntity>> RANCH_HARVESTER = BLOCK_ENTITIES.register("ranch_harvester", () -> BlockEntityType.Builder.of(RanchHarvesterBlockEntity::new, ModBlocks.RANCH_HARVESTER.get()).build(null));
    public static final Supplier<BlockEntityType<GrowthAcceleratorBlockEntity>> GROWTH_ACCELERATOR = BLOCK_ENTITIES.register("growth_accelerator", () -> BlockEntityType.Builder.of(GrowthAcceleratorBlockEntity::new, ModBlocks.GROWTH_ACCELERATOR.get()).build(null));
    public static final Supplier<BlockEntityType<BioFeederBlockEntity>> BIO_FEEDER = BLOCK_ENTITIES.register("bio_feeder", () -> BlockEntityType.Builder.of(BioFeederBlockEntity::new, ModBlocks.BIO_FEEDER.get()).build(null));
    /** Phase 92: Trap branch wave 1. */
    public static final Supplier<BlockEntityType<MobFanBlockEntity>> MOB_FAN = BLOCK_ENTITIES.register("mob_fan", () -> BlockEntityType.Builder.of(MobFanBlockEntity::new, ModBlocks.MOB_FAN.get()).build(null));
    public static final Supplier<BlockEntityType<RotaryDrillBlockEntity>> ROTARY_DRILL = BLOCK_ENTITIES.register("rotary_drill", () -> BlockEntityType.Builder.of(RotaryDrillBlockEntity::new, ModBlocks.ROTARY_DRILL.get()).build(null));
    public static final Supplier<BlockEntityType<ItemVacuumBlockEntity>> ITEM_VACUUM = BLOCK_ENTITIES.register("item_vacuum", () -> BlockEntityType.Builder.of(ItemVacuumBlockEntity::new, ModBlocks.ITEM_VACUUM.get()).build(null));
    /** Phase 93: Trap branch wave 2. */
    public static final Supplier<BlockEntityType<XpCollectorBlockEntity>> XP_COLLECTOR = BLOCK_ENTITIES.register("xp_collector", () -> BlockEntityType.Builder.of(XpCollectorBlockEntity::new, ModBlocks.XP_COLLECTOR.get()).build(null));
    public static final Supplier<BlockEntityType<SpawnSuppressorBlockEntity>> SPAWN_SUPPRESSOR = BLOCK_ENTITIES.register("spawn_suppressor", () -> BlockEntityType.Builder.of(SpawnSuppressorBlockEntity::new, ModBlocks.SPAWN_SUPPRESSOR.get()).build(null));
    public static final Supplier<BlockEntityType<LaserTurretBlockEntity>> LASER_TURRET = BLOCK_ENTITIES.register("laser_turret", () -> BlockEntityType.Builder.of(LaserTurretBlockEntity::new, ModBlocks.LASER_TURRET.get()).build(null));
    public static final Supplier<BlockEntityType<MobDuplicatorBlockEntity>> MOB_DUPLICATOR = BLOCK_ENTITIES.register("mob_duplicator", () -> BlockEntityType.Builder.of(MobDuplicatorBlockEntity::new, ModBlocks.MOB_DUPLICATOR.get()).build(null));
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.AutoBuilderBlockEntity>> AUTO_BUILDER = BLOCK_ENTITIES.register("auto_builder", () -> BlockEntityType.Builder.of(com.industrialcore.blockentity.AutoBuilderBlockEntity::new, ModBlocks.AUTO_BUILDER.get()).build(null));
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.FluidPumpBlockEntity>> FLUID_PUMP = BLOCK_ENTITIES.register("fluid_pump", () -> BlockEntityType.Builder.of(com.industrialcore.blockentity.FluidPumpBlockEntity::new, ModBlocks.FLUID_PUMP.get()).build(null));
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.WirelessChargerBlockEntity>> WIRELESS_CHARGER = BLOCK_ENTITIES.register("wireless_charger", () -> BlockEntityType.Builder.of(com.industrialcore.blockentity.WirelessChargerBlockEntity::new, ModBlocks.WIRELESS_CHARGER.get()).build(null));
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.ChunkLoaderBlockEntity>> CHUNK_LOADER = BLOCK_ENTITIES.register("chunk_loader", () -> BlockEntityType.Builder.of(com.industrialcore.blockentity.ChunkLoaderBlockEntity::new, ModBlocks.CHUNK_LOADER.get()).build(null));
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.ClimateControllerBlockEntity>> CLIMATE_CONTROLLER = BLOCK_ENTITIES.register("climate_controller", () -> BlockEntityType.Builder.of(com.industrialcore.blockentity.ClimateControllerBlockEntity::new, ModBlocks.CLIMATE_CONTROLLER.get()).build(null));
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.WarpPadBlockEntity>> WARP_PAD = BLOCK_ENTITIES.register("warp_pad", () -> BlockEntityType.Builder.of(com.industrialcore.blockentity.WarpPadBlockEntity::new, ModBlocks.WARP_PAD.get()).build(null));
    /** Phase 96: Quantum Storage Network importer & exporter. */
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.QuantumImporterBlockEntity>> QUANTUM_IMPORTER = BLOCK_ENTITIES.register("quantum_importer", () -> BlockEntityType.Builder.of(com.industrialcore.blockentity.QuantumImporterBlockEntity::new, ModBlocks.QUANTUM_IMPORTER.get()).build(null));
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.QuantumExporterBlockEntity>> QUANTUM_EXPORTER = BLOCK_ENTITIES.register("quantum_exporter", () -> BlockEntityType.Builder.of(com.industrialcore.blockentity.QuantumExporterBlockEntity::new, ModBlocks.QUANTUM_EXPORTER.get()).build(null));
    public static final Supplier<BlockEntityType<QuantumCompressorBlockEntity>> QUANTUM_COMPRESSOR = BLOCK_ENTITIES.register("quantum_compressor", () -> BlockEntityType.Builder.of(QuantumCompressorBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.QUANTUM_COMPRESSOR.get()}).build(null));
    public static final Supplier<BlockEntityType<PhaseReactorBlockEntity>> PHASE_REACTOR = BLOCK_ENTITIES.register("phase_reactor", () -> BlockEntityType.Builder.of(PhaseReactorBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.PHASE_REACTOR.get()}).build(null));
    public static final Supplier<BlockEntityType<TransporterBlockEntity>> BASIC_ITEM_TRANSPORTER = BLOCK_ENTITIES.register("basic_item_transporter", () -> BlockEntityType.Builder.of(TransporterBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.BASIC_ITEM_TRANSPORTER.get()}).build(null));
    public static final Supplier<BlockEntityType<TransporterBlockEntity>> ADVANCED_ITEM_TRANSPORTER = BLOCK_ENTITIES.register("advanced_item_transporter", () -> BlockEntityType.Builder.of(TransporterBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.ADVANCED_ITEM_TRANSPORTER.get()}).build(null));
    public static final Supplier<BlockEntityType<TransporterBlockEntity>> QUANTUM_TRANSPORTER = BLOCK_ENTITIES.register("quantum_transporter", () -> BlockEntityType.Builder.of(TransporterBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.QUANTUM_TRANSPORTER.get()}).build(null));
    public static final Supplier<BlockEntityType<TransporterBlockEntity>> QUANTUM_TRANSPORTER_ARRAY = BLOCK_ENTITIES.register("quantum_transporter_array", () -> BlockEntityType.Builder.of(TransporterBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.QUANTUM_TRANSPORTER_ARRAY.get()}).build(null));
    // Phase 63: the self-building Advanced Transporter shares the transporter block entity, but
    // needs its own type because the block class (and therefore the factory) differs.
    public static final Supplier<BlockEntityType<AdvancedTransporterDriveBlockEntity>>
            ADVANCED_TRANSPORTER_DRIVE = BLOCK_ENTITIES.register("advanced_transporter_drive",
            () -> BlockEntityType.Builder.of(AdvancedTransporterDriveBlockEntity::new,
                    ModBlocks.ADVANCED_TRANSPORTER_DRIVE.get()).build(null));

    public static final Supplier<BlockEntityType<OreSynthesizerBlockEntity>> ORE_SYNTHESIZER = BLOCK_ENTITIES.register("ore_synthesizer", () -> BlockEntityType.Builder.of(OreSynthesizerBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.ORE_SYNTHESIZER.get()}).build(null));

    // Phase 33: shared power types - all six generators use one type, all four recovery
    // devices use the other (the block instance carries the kind).
    public static final Supplier<BlockEntityType<GenerationBlockEntity>> GENERATION =
            BLOCK_ENTITIES.register("generation", () -> BlockEntityType.Builder.of(
                    GenerationBlockEntity::new,
                    ModBlocks.THERMAL_GENERATOR.get(),
                    ModBlocks.HYDRO_GENERATOR.get(),
                    ModBlocks.WIND_GENERATOR.get(),
                    ModBlocks.SOLAR_GENERATOR.get(),
                    ModBlocks.COMBUSTION_GENERATOR.get(),
                    ModBlocks.NUCLEAR_GENERATOR.get(),
                    ModBlocks.GEOTHERMAL_GENERATOR.get()).build(null));

    public static final Supplier<BlockEntityType<RecoveryBlockEntity>> RECOVERY =
            BLOCK_ENTITIES.register("recovery", () -> BlockEntityType.Builder.of(
                    RecoveryBlockEntity::new,
                    ModBlocks.FLYWHEEL.get(),
                    ModBlocks.PRESSURE_RECOVERY.get(),
                    ModBlocks.HEAT_RECOVERY.get(),
                    ModBlocks.REGEN_GENERATOR.get()).build(null));

    /** Phase 98: Virtual Aegis Suit Module Modifier. */
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.SuitModifierBlockEntity>> SUIT_MODIFIER =
            BLOCK_ENTITIES.register("suit_modifier", () -> BlockEntityType.Builder.of(
                    com.industrialcore.blockentity.SuitModifierBlockEntity::new,
                    ModBlocks.SUIT_MODIFIER.get()).build(null));

    /** Phase 99: Endgame Inflation (Singularity Reactor & Unlimited Conduits). */
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.SingularityReactorBlockEntity>> SINGULARITY_REACTOR =
            BLOCK_ENTITIES.register("singularity_reactor", () -> BlockEntityType.Builder.of(
                    com.industrialcore.blockentity.SingularityReactorBlockEntity::new,
                    ModBlocks.SINGULARITY_REACTOR.get()).build(null));
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.HyperEnergyConduitBlockEntity>> HYPER_ENERGY_CONDUIT =
            BLOCK_ENTITIES.register("hyper_energy_conduit", () -> BlockEntityType.Builder.of(
                    com.industrialcore.blockentity.HyperEnergyConduitBlockEntity::new,
                    ModBlocks.HYPER_ENERGY_CONDUIT.get()).build(null));
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.HyperItemConduitBlockEntity>> HYPER_ITEM_CONDUIT =
            BLOCK_ENTITIES.register("hyper_item_conduit", () -> BlockEntityType.Builder.of(
                    com.industrialcore.blockentity.HyperItemConduitBlockEntity::new,
                    ModBlocks.HYPER_ITEM_CONDUIT.get()).build(null));
    /** Phase 103: Multiblock Reactor I/O Port capability proxy. */
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.ReactorPortBlockEntity>> REACTOR_PORT =
            BLOCK_ENTITIES.register("reactor_port", () -> BlockEntityType.Builder.of(
                    com.industrialcore.blockentity.ReactorPortBlockEntity::new,
                    ModBlocks.REACTOR_PORT.get()).build(null));
    /** Phase 104: Common auto-expanding large machine bounding proxy. */
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.MachineBoundingBlockEntity>> MACHINE_BOUNDING =
            BLOCK_ENTITIES.register("machine_bounding", () -> BlockEntityType.Builder.of(
                    com.industrialcore.blockentity.MachineBoundingBlockEntity::new,
                    ModBlocks.MACHINE_BOUNDING.get()).build(null));
    /** Phase 105.6: 6-way connected industrial fluid pipe. */
    public static final Supplier<BlockEntityType<com.industrialcore.blockentity.FluidPipeBlockEntity>> FLUID_PIPE =
            BLOCK_ENTITIES.register("fluid_pipe", () -> BlockEntityType.Builder.of(
                    com.industrialcore.blockentity.FluidPipeBlockEntity::new,
                    ModBlocks.FLUID_PIPE.get()).build(null));

    public static BlockEntityType<TransporterBlockEntity> transporterType(TransporterKind kind) {
        return switch (kind) {
            default -> throw new MatchException(null, null);
            case TransporterKind.BASIC -> BASIC_ITEM_TRANSPORTER.get();
            case TransporterKind.ADVANCED -> ADVANCED_ITEM_TRANSPORTER.get();
            case TransporterKind.QUANTUM -> QUANTUM_TRANSPORTER.get();
            case TransporterKind.ARRAY -> QUANTUM_TRANSPORTER_ARRAY.get();
        };
    }

    private ModBlockEntities() {
    }
}

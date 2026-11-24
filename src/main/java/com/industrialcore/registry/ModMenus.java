package com.industrialcore.registry;

import com.industrialcore.IndustrialCore;
import com.industrialcore.menu.AlloyFurnaceMenu;
import com.industrialcore.menu.AssemblerMenu;
import com.industrialcore.menu.CrafterMenu;
import com.industrialcore.menu.EnergyBufferMenu;
import com.industrialcore.menu.MetalPressMenu;
import com.industrialcore.menu.CrusherMenu;
import com.industrialcore.menu.ElectricFurnaceMenu;
import com.industrialcore.menu.FastCrusherMenu;
import com.industrialcore.menu.ElectrolyzerMenu;
import com.industrialcore.menu.GenerationMenu;
import com.industrialcore.menu.ItemDistributorMenu;
import com.industrialcore.menu.LargeQuarryMenu;
import com.industrialcore.menu.MatterAmplifierMenu;
import com.industrialcore.menu.MonitorMenu;
import com.industrialcore.menu.OreSynthesizerMenu;
import com.industrialcore.menu.PhaseReactorMenu;
import com.industrialcore.menu.PrecisionCrusherMenu;
import com.industrialcore.menu.QuantumCompressorMenu;
import com.industrialcore.menu.AutoFisherMenu;
import com.industrialcore.menu.BioFeederMenu;
import com.industrialcore.menu.CropHarvesterMenu;
import com.industrialcore.menu.GrowthAcceleratorMenu;
import com.industrialcore.menu.ItemVacuumMenu;
import com.industrialcore.menu.LaserTurretMenu;
import com.industrialcore.menu.MobDuplicatorMenu;
import com.industrialcore.menu.MobFanMenu;
import com.industrialcore.menu.QuantumStorageMenu;
import com.industrialcore.menu.RanchHarvesterMenu;
import com.industrialcore.menu.RotaryDrillMenu;
import com.industrialcore.menu.SpawnSuppressorMenu;
import com.industrialcore.menu.TreeHarvesterMenu;
import com.industrialcore.menu.XpCollectorMenu;
import com.industrialcore.menu.QuarryMenu;
import com.industrialcore.menu.ReconstructorMenu;
import com.industrialcore.menu.SeedCultivatorMenu;
import com.industrialcore.menu.TransporterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, IndustrialCore.MOD_ID);

    public static final Supplier<MenuType<QuarryMenu>> QUARRY =
            MENUS.register("quarry", () -> IMenuTypeExtension.create(QuarryMenu::new));

    /** Phase 20: large quarry (fixed area, mining plus crushing). */
    public static final Supplier<MenuType<LargeQuarryMenu>> LARGE_QUARRY =
            MENUS.register("large_quarry", () -> IMenuTypeExtension.create(LargeQuarryMenu::new));

    public static final Supplier<MenuType<CrafterMenu>> CRAFTER =
            MENUS.register("crafter", () -> IMenuTypeExtension.create(CrafterMenu::new));

    public static final Supplier<MenuType<CrusherMenu>> CRUSHER =
            MENUS.register("crusher", () -> IMenuTypeExtension.create(CrusherMenu::new));

    public static final Supplier<MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE =
            MENUS.register("electric_furnace", () -> IMenuTypeExtension.create(ElectricFurnaceMenu::new));

    /** Phase 12: 3x / 6x ore line. */
    public static final Supplier<MenuType<ElectrolyzerMenu>> ELECTROLYZER =
            MENUS.register("electrolyzer", () -> IMenuTypeExtension.create(ElectrolyzerMenu::new));

    public static final Supplier<MenuType<PrecisionCrusherMenu>> PRECISION_CRUSHER =
            MENUS.register("precision_crusher",
                    () -> IMenuTypeExtension.create(PrecisionCrusherMenu::new));

    /** Phase 17: metal press, alloy furnace and energy buffer. */
    public static final Supplier<MenuType<MetalPressMenu>> METAL_PRESS =
            MENUS.register("metal_press", () -> IMenuTypeExtension.create(MetalPressMenu::new));

    /** Phase 23: fast crusher (batch cycles over the shared crushing recipes). */
    public static final Supplier<MenuType<FastCrusherMenu>> FAST_CRUSHER =
            MENUS.register("fast_crusher", () -> IMenuTypeExtension.create(FastCrusherMenu::new));

    public static final Supplier<MenuType<AlloyFurnaceMenu>> ALLOY_FURNACE =
            MENUS.register("alloy_furnace", () -> IMenuTypeExtension.create(AlloyFurnaceMenu::new));

    public static final Supplier<MenuType<EnergyBufferMenu>> ENERGY_BUFFER =
            MENUS.register("energy_buffer", () -> IMenuTypeExtension.create(EnergyBufferMenu::new));

    /** Phase 15: the reconstructor has its own menu type. */
    public static final Supplier<MenuType<ReconstructorMenu>> RECONSTRUCTOR =
            MENUS.register("reconstructor",
                    () -> IMenuTypeExtension.create(ReconstructorMenu::new));

    /** Phase 18: assembler menu (three ingredient slots and one result slot). */
    public static final Supplier<MenuType<AssemblerMenu>> ASSEMBLER =
            MENUS.register("assembler", () -> IMenuTypeExtension.create(AssemblerMenu::new));

    /** Phase 19: item distributor menu (one buffer slot). */
    public static final Supplier<MenuType<ItemDistributorMenu>> ITEM_DISTRIBUTOR =
            MENUS.register("item_distributor",
                    () -> IMenuTypeExtension.create(ItemDistributorMenu::new));

    /** Phase 21: the seed cultivator (three slots, batch progress). */
    public static final Supplier<MenuType<SeedCultivatorMenu>> SEED_CULTIVATOR =
            MENUS.register("seed_cultivator",
                    () -> IMenuTypeExtension.create(SeedCultivatorMenu::new));

    /** Phase 21: the matter amplifier (four slots, two outputs). */
    public static final Supplier<MenuType<MatterAmplifierMenu>> MATTER_AMPLIFIER =
            MENUS.register("matter_amplifier",
                    () -> IMenuTypeExtension.create(MatterAmplifierMenu::new));

    /** Phase 82: the factory monitor (read-only terminal, no item slots). */
    public static final Supplier<MenuType<MonitorMenu>> FACTORY_MONITOR =
            MENUS.register("factory_monitor",
                    () -> IMenuTypeExtension.create(MonitorMenu::new));

    /** Phase 24: the phase reactor (three ingredients, one result). */
    public static final Supplier<MenuType<PhaseReactorMenu>> PHASE_REACTOR =
            MENUS.register("phase_reactor",
                    () -> IMenuTypeExtension.create(PhaseReactorMenu::new));

    /** Phase 25: the quantum compressor (one input, four outputs, batch cycles). */
    public static final Supplier<MenuType<QuantumCompressorMenu>> QUANTUM_COMPRESSOR =
            MENUS.register("quantum_compressor",
                    () -> IMenuTypeExtension.create(QuantumCompressorMenu::new));

    /**
     * Phase 26-29: one menu type for all four transporters - the tier (and with it the buffer
     * grid) is resolved from the block entity, exactly like the channel settings themselves.
     */
    public static final Supplier<MenuType<TransporterMenu>> TRANSPORTER =
            MENUS.register("transporter", () -> IMenuTypeExtension.create(TransporterMenu::new));

    /** Phase 30: the synthetic ore synthesizer. */
    public static final Supplier<MenuType<OreSynthesizerMenu>> ORE_SYNTHESIZER =
            MENUS.register("ore_synthesizer", () -> IMenuTypeExtension.create(OreSynthesizerMenu::new));

    /** Phase 33: one menu type serves all ten power devices. */
    public static final Supplier<MenuType<GenerationMenu>> GENERATION =
            MENUS.register("generation", () -> IMenuTypeExtension.create(GenerationMenu::new));

    /** Phase 89: Quantum Storage Core. */
    public static final Supplier<MenuType<QuantumStorageMenu>> QUANTUM_STORAGE_CORE =
            MENUS.register("quantum_storage_core", () -> IMenuTypeExtension.create(QuantumStorageMenu::new));

    /** Phase 90: Agriculture & Forestry branch wave 1. */
    public static final Supplier<MenuType<CropHarvesterMenu>> CROP_HARVESTER =
            MENUS.register("crop_harvester", () -> IMenuTypeExtension.create(CropHarvesterMenu::new));
    public static final Supplier<MenuType<TreeHarvesterMenu>> TREE_HARVESTER =
            MENUS.register("tree_harvester", () -> IMenuTypeExtension.create(TreeHarvesterMenu::new));
    public static final Supplier<MenuType<AutoFisherMenu>> AUTO_FISHER =
            MENUS.register("auto_fisher", () -> IMenuTypeExtension.create(AutoFisherMenu::new));

    /** Phase 91: Agriculture & Forestry branch wave 2. */
    public static final Supplier<MenuType<RanchHarvesterMenu>> RANCH_HARVESTER =
            MENUS.register("ranch_harvester", () -> IMenuTypeExtension.create(RanchHarvesterMenu::new));
    public static final Supplier<MenuType<GrowthAcceleratorMenu>> GROWTH_ACCELERATOR =
            MENUS.register("growth_accelerator", () -> IMenuTypeExtension.create(GrowthAcceleratorMenu::new));
    public static final Supplier<MenuType<BioFeederMenu>> BIO_FEEDER =
            MENUS.register("bio_feeder", () -> IMenuTypeExtension.create(BioFeederMenu::new));

    /** Phase 92: Trap branch wave 1. */
    public static final Supplier<MenuType<MobFanMenu>> MOB_FAN =
            MENUS.register("mob_fan", () -> IMenuTypeExtension.create(MobFanMenu::new));
    public static final Supplier<MenuType<RotaryDrillMenu>> ROTARY_DRILL =
            MENUS.register("rotary_drill", () -> IMenuTypeExtension.create(RotaryDrillMenu::new));
    public static final Supplier<MenuType<ItemVacuumMenu>> ITEM_VACUUM =
            MENUS.register("item_vacuum", () -> IMenuTypeExtension.create(ItemVacuumMenu::new));

    /** Phase 93: Trap branch wave 2. */
    public static final Supplier<MenuType<XpCollectorMenu>> XP_COLLECTOR =
            MENUS.register("xp_collector", () -> IMenuTypeExtension.create(XpCollectorMenu::new));
    public static final Supplier<MenuType<SpawnSuppressorMenu>> SPAWN_SUPPRESSOR =
            MENUS.register("spawn_suppressor", () -> IMenuTypeExtension.create(SpawnSuppressorMenu::new));
    public static final Supplier<MenuType<LaserTurretMenu>> LASER_TURRET =
            MENUS.register("laser_turret", () -> IMenuTypeExtension.create(LaserTurretMenu::new));
    public static final Supplier<MenuType<MobDuplicatorMenu>> MOB_DUPLICATOR =
            MENUS.register("mob_duplicator", () -> IMenuTypeExtension.create(MobDuplicatorMenu::new));

    /** Phase 94: Building & Life branch wave 1. */
    public static final Supplier<MenuType<com.industrialcore.menu.AutoBuilderMenu>> AUTO_BUILDER =
            MENUS.register("auto_builder", () -> IMenuTypeExtension.create(com.industrialcore.menu.AutoBuilderMenu::new));
    public static final Supplier<MenuType<com.industrialcore.menu.FluidPumpMenu>> FLUID_PUMP =
            MENUS.register("fluid_pump", () -> IMenuTypeExtension.create(com.industrialcore.menu.FluidPumpMenu::new));
    public static final Supplier<MenuType<com.industrialcore.menu.WirelessChargerMenu>> WIRELESS_CHARGER =
            MENUS.register("wireless_charger", () -> IMenuTypeExtension.create(com.industrialcore.menu.WirelessChargerMenu::new));

    /** Phase 95: Building & Life branch wave 2. */
    public static final Supplier<MenuType<com.industrialcore.menu.ChunkLoaderMenu>> CHUNK_LOADER =
            MENUS.register("chunk_loader", () -> IMenuTypeExtension.create(com.industrialcore.menu.ChunkLoaderMenu::new));
    public static final Supplier<MenuType<com.industrialcore.menu.ClimateControllerMenu>> CLIMATE_CONTROLLER =
            MENUS.register("climate_controller", () -> IMenuTypeExtension.create(com.industrialcore.menu.ClimateControllerMenu::new));
    public static final Supplier<MenuType<com.industrialcore.menu.WarpPadMenu>> WARP_PAD =
            MENUS.register("warp_pad", () -> IMenuTypeExtension.create(com.industrialcore.menu.WarpPadMenu::new));

    /** Phase 96: Quantum Importer / Exporter shared menu. */
    public static final Supplier<MenuType<com.industrialcore.menu.QuantumIoMenu>> QUANTUM_IO =
            MENUS.register("quantum_io", () -> IMenuTypeExtension.create(com.industrialcore.menu.QuantumIoMenu::new));

    /** Phase 98: Virtual Aegis Suit Module Modifier. */
    public static final Supplier<MenuType<com.industrialcore.menu.SuitModifierMenu>> SUIT_MODIFIER =
            MENUS.register("suit_modifier", () -> IMenuTypeExtension.create(com.industrialcore.menu.SuitModifierMenu::new));

    /** Phase 99: Singularity Reactor Generator. */
    public static final Supplier<MenuType<com.industrialcore.menu.SingularityReactorMenu>> SINGULARITY_REACTOR =
            MENUS.register("singularity_reactor", () -> IMenuTypeExtension.create(com.industrialcore.menu.SingularityReactorMenu::new));

    private ModMenus() {}
}

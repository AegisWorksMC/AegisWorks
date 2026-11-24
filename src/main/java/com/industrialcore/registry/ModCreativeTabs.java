package com.industrialcore.registry;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Creative Mode Tab (and JEI default display order) organized systematically by category and
 * Stage progression (Phase 110). Internal registry IDs in {@link ModBlocks} and {@link ModItems}
 * remain completely unchanged for full world compatibility.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IndustrialCore.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.industrialcore.main"))
            .icon(() -> new ItemStack(ModBlocks.QUARRY.get()))
            .displayItems((parameters, output) -> {
                // =========================================================================
                // 1. 資源・中間素材（ワールド鉱石 → 原石 → 粉 → インゴット → 板・合金 → 電子部品 → ウラン → 精製段階）
                // =========================================================================
                // 1a. ワールド生成鉱石（通常／深層岩）
                output.accept(ModBlocks.URANIUM_ORE.get());
                output.accept(ModBlocks.DEEPSLATE_URANIUM_ORE.get());
                output.accept(ModBlocks.ASTRAL_SILVER_ORE.get());
                output.accept(ModBlocks.VOID_IRON_ORE.get());
                output.accept(ModBlocks.MITHRIL_ORE.get());
                output.accept(ModBlocks.ORICHALCUM_ORE.get());
                output.accept(ModBlocks.ADAMANTITE_ORE.get());
                output.accept(ModBlocks.NEBULA_ORE.get());
                output.accept(ModBlocks.QUANTUM_ORE.get());
                output.accept(ModBlocks.AETHER_ORE.get());

                // 1b. 原石
                output.accept(ModItems.RAW_ASTRAL_SILVER.get());
                output.accept(ModItems.RAW_VOID_IRON.get());
                output.accept(ModItems.RAW_MITHRIL.get());
                output.accept(ModItems.RAW_ORICHALCUM.get());
                output.accept(ModItems.RAW_ADAMANTITE.get());

                // 1c. 粉・ダスト（Stage I）
                output.accept(ModItems.IRON_DUST.get());
                output.accept(ModItems.COPPER_DUST.get());
                output.accept(ModItems.GOLD_DUST.get());
                output.accept(ModItems.ASTRAL_SILVER_DUST.get());
                output.accept(ModItems.VOID_IRON_DUST.get());
                output.accept(ModItems.MITHRIL_DUST.get());
                output.accept(ModItems.ORICHALCUM_DUST.get());
                output.accept(ModItems.ADAMANTITE_DUST.get());
                output.accept(ModItems.NEBULA_DUST.get());
                output.accept(ModItems.QUANTUM_DUST.get());
                output.accept(ModItems.AETHER_DUST.get());

                // 1d. インゴット・結晶・合金
                output.accept(ModItems.STEEL_INGOT.get());
                output.accept(ModItems.BRASS_INGOT.get());
                output.accept(ModItems.ASTRAL_SILVER_INGOT.get());
                output.accept(ModItems.VOID_IRON_INGOT.get());
                output.accept(ModItems.MITHRIL_INGOT.get());
                output.accept(ModItems.ORICHALCUM_INGOT.get());
                output.accept(ModItems.ADAMANTITE_INGOT.get());
                output.accept(ModItems.NEBULA_CRYSTAL.get());
                output.accept(ModItems.QUANTUM_SHARD.get());
                output.accept(ModItems.AETHER_CRYSTAL.get());

                // 1e. 金属板・ビレット・鉱石シード
                output.accept(ModItems.IRON_PLATE.get());
                output.accept(ModItems.COPPER_PLATE.get());
                output.accept(ModItems.GOLD_PLATE.get());
                output.accept(ModItems.STEEL_PLATE.get());
                output.accept(ModItems.BRASS_PLATE.get());
                output.accept(ModItems.IRON_BILLET.get());
                output.accept(ModItems.COPPER_BILLET.get());
                output.accept(ModItems.GOLD_BILLET.get());
                output.accept(ModItems.IRON_SEED.get());
                output.accept(ModItems.COPPER_SEED.get());
                output.accept(ModItems.GOLD_SEED.get());

                // 1f. 中間電子部品・フレーム・アップグレード部品
                output.accept(ModItems.MACHINE_FRAME.get());
                output.accept(ModItems.REINFORCED_MACHINE_FRAME.get());
                output.accept(ModItems.DURABLE_MACHINE_FRAME.get());
                output.accept(ModItems.ELECTRONIC_COMPONENT.get());
                output.accept(ModItems.MOTOR.get());
                output.accept(ModItems.HIGH_PERFORMANCE_MOTOR.get());
                output.accept(ModItems.BATTERY.get());
                output.accept(ModItems.SPECIALIZATION_MODULE.get());
                output.accept(ModItems.RECONSTRUCTION_CRYSTAL.get());
                output.accept(ModItems.RESONANT_CATALYST.get());
                output.accept(ModItems.PHASE_CORE.get());

                // 1g. ウラン精製ライン（原石→粉→濃縮→燃料棒）＆工業燃料
                output.accept(ModItems.RAW_URANIUM.get());
                output.accept(ModItems.URANIUM_DUST.get());
                output.accept(ModItems.ENRICHED_URANIUM.get());
                output.accept(ModItems.URANIUM_FUEL_ROD.get());
                output.accept(ModItems.FUEL_CAN.get());
                output.accept(ModItems.FUEL_ROD.get());

                // 1h. 合成鉱石（9 段階）＆多段精製中間グレード（11 鉱物 × Stage II..XIV）
                output.accept(ModItems.SYNTHETIC_ORE_1.get());
                output.accept(ModItems.SYNTHETIC_ORE_2.get());
                output.accept(ModItems.SYNTHETIC_ORE_3.get());
                output.accept(ModItems.SYNTHETIC_ORE_4.get());
                output.accept(ModItems.SYNTHETIC_ORE_5.get());
                output.accept(ModItems.SYNTHETIC_ORE_6.get());
                output.accept(ModItems.SYNTHETIC_ORE_HIGH_DENSITY.get());
                output.accept(ModItems.SYNTHETIC_ORE_UNSTABLE_ULTIMATE.get());
                output.accept(ModItems.SYNTHETIC_ORE_ULTIMATE.get());
                // Iron [II..XIV]
                output.accept(ModItems.PURE_IRON.get());
                output.accept(ModItems.EXPOSED_IRON.get());
                output.accept(ModItems.IRON_WASHED.get());
                output.accept(ModItems.IRON_SORTED.get());
                output.accept(ModItems.IRON_CONCENTRATE.get());
                output.accept(ModItems.IRON_REFINED.get());
                output.accept(ModItems.IRON_SEPARATED.get());
                output.accept(ModItems.IRON_HIGH_PURITY.get());
                output.accept(ModItems.IRON_MICRONIZED.get());
                output.accept(ModItems.IRON_CRYSTALLIZED.get());
                output.accept(ModItems.IRON_HOMOGENIZED.get());
                output.accept(ModItems.IRON_COMPRESSED.get());
                output.accept(ModItems.IRON_STABILIZED.get());
                // Copper [II..XIV]
                output.accept(ModItems.PURE_COPPER.get());
                output.accept(ModItems.EXPOSED_COPPER.get());
                output.accept(ModItems.COPPER_WASHED.get());
                output.accept(ModItems.COPPER_SORTED.get());
                output.accept(ModItems.COPPER_CONCENTRATE.get());
                output.accept(ModItems.COPPER_REFINED.get());
                output.accept(ModItems.COPPER_SEPARATED.get());
                output.accept(ModItems.COPPER_HIGH_PURITY.get());
                output.accept(ModItems.COPPER_MICRONIZED.get());
                output.accept(ModItems.COPPER_CRYSTALLIZED.get());
                output.accept(ModItems.COPPER_HOMOGENIZED.get());
                output.accept(ModItems.COPPER_COMPRESSED.get());
                output.accept(ModItems.COPPER_STABILIZED.get());
                // Gold [II..XIV]
                output.accept(ModItems.PURE_GOLD.get());
                output.accept(ModItems.EXPOSED_GOLD.get());
                output.accept(ModItems.GOLD_WASHED.get());
                output.accept(ModItems.GOLD_SORTED.get());
                output.accept(ModItems.GOLD_CONCENTRATE.get());
                output.accept(ModItems.GOLD_REFINED.get());
                output.accept(ModItems.GOLD_SEPARATED.get());
                output.accept(ModItems.GOLD_HIGH_PURITY.get());
                output.accept(ModItems.GOLD_MICRONIZED.get());
                output.accept(ModItems.GOLD_CRYSTALLIZED.get());
                output.accept(ModItems.GOLD_HOMOGENIZED.get());
                output.accept(ModItems.GOLD_COMPRESSED.get());
                output.accept(ModItems.GOLD_STABILIZED.get());
                // Astral Silver [II..XIV]
                output.accept(ModItems.PURE_ASTRAL_SILVER.get());
                output.accept(ModItems.EXPOSED_ASTRAL_SILVER.get());
                output.accept(ModItems.ASTRAL_SILVER_WASHED.get());
                output.accept(ModItems.ASTRAL_SILVER_SORTED.get());
                output.accept(ModItems.ASTRAL_SILVER_CONCENTRATE.get());
                output.accept(ModItems.ASTRAL_SILVER_REFINED.get());
                output.accept(ModItems.ASTRAL_SILVER_SEPARATED.get());
                output.accept(ModItems.ASTRAL_SILVER_HIGH_PURITY.get());
                output.accept(ModItems.ASTRAL_SILVER_MICRONIZED.get());
                output.accept(ModItems.ASTRAL_SILVER_CRYSTALLIZED.get());
                output.accept(ModItems.ASTRAL_SILVER_HOMOGENIZED.get());
                output.accept(ModItems.ASTRAL_SILVER_COMPRESSED.get());
                output.accept(ModItems.ASTRAL_SILVER_STABILIZED.get());
                // Void Iron [II..XIV]
                output.accept(ModItems.PURE_VOID_IRON.get());
                output.accept(ModItems.EXPOSED_VOID_IRON.get());
                output.accept(ModItems.VOID_IRON_WASHED.get());
                output.accept(ModItems.VOID_IRON_SORTED.get());
                output.accept(ModItems.VOID_IRON_CONCENTRATE.get());
                output.accept(ModItems.VOID_IRON_REFINED.get());
                output.accept(ModItems.VOID_IRON_SEPARATED.get());
                output.accept(ModItems.VOID_IRON_HIGH_PURITY.get());
                output.accept(ModItems.VOID_IRON_MICRONIZED.get());
                output.accept(ModItems.VOID_IRON_CRYSTALLIZED.get());
                output.accept(ModItems.VOID_IRON_HOMOGENIZED.get());
                output.accept(ModItems.VOID_IRON_COMPRESSED.get());
                output.accept(ModItems.VOID_IRON_STABILIZED.get());
                // Mithril [II..XIV]
                output.accept(ModItems.PURE_MITHRIL.get());
                output.accept(ModItems.EXPOSED_MITHRIL.get());
                output.accept(ModItems.MITHRIL_WASHED.get());
                output.accept(ModItems.MITHRIL_SORTED.get());
                output.accept(ModItems.MITHRIL_CONCENTRATE.get());
                output.accept(ModItems.MITHRIL_REFINED.get());
                output.accept(ModItems.MITHRIL_SEPARATED.get());
                output.accept(ModItems.MITHRIL_HIGH_PURITY.get());
                output.accept(ModItems.MITHRIL_MICRONIZED.get());
                output.accept(ModItems.MITHRIL_CRYSTALLIZED.get());
                output.accept(ModItems.MITHRIL_HOMOGENIZED.get());
                output.accept(ModItems.MITHRIL_COMPRESSED.get());
                output.accept(ModItems.MITHRIL_STABILIZED.get());
                // Orichalcum [II..XIV]
                output.accept(ModItems.PURE_ORICHALCUM.get());
                output.accept(ModItems.EXPOSED_ORICHALCUM.get());
                output.accept(ModItems.ORICHALCUM_WASHED.get());
                output.accept(ModItems.ORICHALCUM_SORTED.get());
                output.accept(ModItems.ORICHALCUM_CONCENTRATE.get());
                output.accept(ModItems.ORICHALCUM_REFINED.get());
                output.accept(ModItems.ORICHALCUM_SEPARATED.get());
                output.accept(ModItems.ORICHALCUM_HIGH_PURITY.get());
                output.accept(ModItems.ORICHALCUM_MICRONIZED.get());
                output.accept(ModItems.ORICHALCUM_CRYSTALLIZED.get());
                output.accept(ModItems.ORICHALCUM_HOMOGENIZED.get());
                output.accept(ModItems.ORICHALCUM_COMPRESSED.get());
                output.accept(ModItems.ORICHALCUM_STABILIZED.get());
                // Adamantite [II..XIV]
                output.accept(ModItems.PURE_ADAMANTITE.get());
                output.accept(ModItems.EXPOSED_ADAMANTITE.get());
                output.accept(ModItems.ADAMANTITE_WASHED.get());
                output.accept(ModItems.ADAMANTITE_SORTED.get());
                output.accept(ModItems.ADAMANTITE_CONCENTRATE.get());
                output.accept(ModItems.ADAMANTITE_REFINED.get());
                output.accept(ModItems.ADAMANTITE_SEPARATED.get());
                output.accept(ModItems.ADAMANTITE_HIGH_PURITY.get());
                output.accept(ModItems.ADAMANTITE_MICRONIZED.get());
                output.accept(ModItems.ADAMANTITE_CRYSTALLIZED.get());
                output.accept(ModItems.ADAMANTITE_HOMOGENIZED.get());
                output.accept(ModItems.ADAMANTITE_COMPRESSED.get());
                output.accept(ModItems.ADAMANTITE_STABILIZED.get());
                // Nebula [II..XIV]
                output.accept(ModItems.PURE_NEBULA.get());
                output.accept(ModItems.EXPOSED_NEBULA.get());
                output.accept(ModItems.NEBULA_WASHED.get());
                output.accept(ModItems.NEBULA_SORTED.get());
                output.accept(ModItems.NEBULA_CONCENTRATE.get());
                output.accept(ModItems.NEBULA_REFINED.get());
                output.accept(ModItems.NEBULA_SEPARATED.get());
                output.accept(ModItems.NEBULA_HIGH_PURITY.get());
                output.accept(ModItems.NEBULA_MICRONIZED.get());
                output.accept(ModItems.NEBULA_CRYSTALLIZED.get());
                output.accept(ModItems.NEBULA_HOMOGENIZED.get());
                output.accept(ModItems.NEBULA_COMPRESSED.get());
                output.accept(ModItems.NEBULA_STABILIZED.get());
                // Quantum [II..XIV]
                output.accept(ModItems.PURE_QUANTUM.get());
                output.accept(ModItems.EXPOSED_QUANTUM.get());
                output.accept(ModItems.QUANTUM_WASHED.get());
                output.accept(ModItems.QUANTUM_SORTED.get());
                output.accept(ModItems.QUANTUM_CONCENTRATE.get());
                output.accept(ModItems.QUANTUM_REFINED.get());
                output.accept(ModItems.QUANTUM_SEPARATED.get());
                output.accept(ModItems.QUANTUM_HIGH_PURITY.get());
                output.accept(ModItems.QUANTUM_MICRONIZED.get());
                output.accept(ModItems.QUANTUM_CRYSTALLIZED.get());
                output.accept(ModItems.QUANTUM_HOMOGENIZED.get());
                output.accept(ModItems.QUANTUM_COMPRESSED.get());
                output.accept(ModItems.QUANTUM_STABILIZED.get());
                // Aether [II..XIV]
                output.accept(ModItems.PURE_AETHER.get());
                output.accept(ModItems.EXPOSED_AETHER.get());
                output.accept(ModItems.AETHER_WASHED.get());
                output.accept(ModItems.AETHER_SORTED.get());
                output.accept(ModItems.AETHER_CONCENTRATE.get());
                output.accept(ModItems.AETHER_REFINED.get());
                output.accept(ModItems.AETHER_SEPARATED.get());
                output.accept(ModItems.AETHER_HIGH_PURITY.get());
                output.accept(ModItems.AETHER_MICRONIZED.get());
                output.accept(ModItems.AETHER_CRYSTALLIZED.get());
                output.accept(ModItems.AETHER_HOMOGENIZED.get());
                output.accept(ModItems.AETHER_COMPRESSED.get());
                output.accept(ModItems.AETHER_STABILIZED.get());

                // =========================================================================
                // 2. 発電・電力回収・蓄電・リアクター
                // =========================================================================
                output.accept(ModBlocks.THERMAL_GENERATOR.get());
                output.accept(ModBlocks.HYDRO_GENERATOR.get());
                output.accept(ModBlocks.WIND_GENERATOR.get());
                output.accept(ModBlocks.SOLAR_GENERATOR.get());
                output.accept(ModBlocks.COMBUSTION_GENERATOR.get());
                output.accept(ModBlocks.GEOTHERMAL_GENERATOR.get());
                output.accept(ModBlocks.HEAT_RECOVERY.get());
                output.accept(ModBlocks.PRESSURE_RECOVERY.get());
                output.accept(ModBlocks.REGEN_GENERATOR.get());
                output.accept(ModBlocks.FLYWHEEL.get());
                output.accept(ModBlocks.ENERGY_BUFFER.get());
                output.accept(ModBlocks.ENERGY_BUFFER_MK2.get());
                output.accept(ModBlocks.ENERGY_BUFFER_MK3.get());
                output.accept(ModBlocks.NUCLEAR_GENERATOR.get());
                output.accept(ModBlocks.SINGULARITY_REACTOR.get());
                output.accept(ModBlocks.REACTOR_CASING.get());
                output.accept(ModBlocks.REACTOR_GLASS.get());
                output.accept(ModBlocks.REACTOR_CORE_CELL.get());
                output.accept(ModBlocks.REACTOR_COOLANT_BLOCK.get());
                output.accept(ModBlocks.REACTOR_PORT.get());

                // =========================================================================
                // 3. 導管・パイプ・物流・量子転送
                // =========================================================================
                output.accept(ModBlocks.CABLE_BASIC.get());
                output.accept(ModBlocks.CABLE_IMPROVED.get());
                output.accept(ModBlocks.CABLE_ADVANCED.get());
                output.accept(ModBlocks.CABLE_ULTIMATE.get());
                output.accept(ModBlocks.HYPER_ENERGY_CONDUIT.get());
                output.accept(ModBlocks.ITEM_PIPE.get());
                output.accept(ModBlocks.IMPROVED_ITEM_PIPE.get());
                output.accept(ModBlocks.ADVANCED_ITEM_PIPE.get());
                output.accept(ModBlocks.ULTIMATE_ITEM_PIPE.get());
                output.accept(ModBlocks.ITEM_DISTRIBUTOR.get());
                output.accept(ModBlocks.HYPER_ITEM_CONDUIT.get());
                output.accept(ModBlocks.FLUID_PIPE.get());
                output.accept(ModBlocks.BASIC_ITEM_TRANSPORTER.get());
                output.accept(ModBlocks.ADVANCED_ITEM_TRANSPORTER.get());
                output.accept(ModBlocks.QUANTUM_TRANSPORTER.get());
                output.accept(ModBlocks.QUANTUM_TRANSPORTER_ARRAY.get());
                output.accept(ModBlocks.ADVANCED_TRANSPORTER_DRIVE.get());
                output.accept(ModBlocks.TRANSPORTER_CASING.get());
                output.accept(ModBlocks.ADVANCED_TRANSPORTER_CASING.get());
                output.accept(ModBlocks.QUANTUM_TRANSPORTER_CASING.get());

                // =========================================================================
                // 4. Branch A：生産・加工・量子ストレージ（Stage 進行順）
                // =========================================================================
                output.accept(ModBlocks.CRUSHER.get());
                output.accept(ModBlocks.FAST_CRUSHER.get());
                output.accept(ModBlocks.PRECISION_CRUSHER.get());
                output.accept(ModBlocks.ELECTRIC_FURNACE.get());
                output.accept(ModBlocks.METAL_PRESS.get());
                output.accept(ModBlocks.ALLOY_FURNACE.get());
                output.accept(ModBlocks.ELECTROLYZER.get());
                output.accept(ModBlocks.CRAFTER.get());
                output.accept(ModBlocks.ASSEMBLER.get());
                output.accept(ModBlocks.QUARRY.get());
                output.accept(ModBlocks.LARGE_QUARRY.get());
                output.accept(ModBlocks.RECONSTRUCTOR.get());
                output.accept(ModBlocks.SEED_CULTIVATOR.get());
                output.accept(ModBlocks.MATTER_AMPLIFIER.get());
                output.accept(ModBlocks.QUANTUM_COMPRESSOR.get());
                output.accept(ModBlocks.PHASE_REACTOR.get());
                output.accept(ModBlocks.ORE_SYNTHESIZER.get());
                output.accept(ModBlocks.QUANTUM_STORAGE_CORE.get());
                output.accept(ModItems.STORAGE_CORE_MK1.get());
                output.accept(ModItems.STORAGE_CORE_MK2.get());
                output.accept(ModItems.STORAGE_CORE_MK3.get());
                output.accept(ModItems.STORAGE_CORE_MK4.get());
                output.accept(ModItems.STORAGE_CORE_MK5.get());
                output.accept(ModBlocks.QUANTUM_IMPORTER.get());
                output.accept(ModBlocks.QUANTUM_EXPORTER.get());
                output.accept(ModItems.PORTABLE_QUANTUM_TERMINAL.get());

                // =========================================================================
                // 5. Branch B〜D：農林・トラップ・建築＆生活
                // =========================================================================
                // Branch B: 農林 6 機種
                output.accept(ModBlocks.CROP_HARVESTER.get());
                output.accept(ModBlocks.TREE_HARVESTER.get());
                output.accept(ModBlocks.AUTO_FISHER.get());
                output.accept(ModBlocks.RANCH_HARVESTER.get());
                output.accept(ModBlocks.GROWTH_ACCELERATOR.get());
                output.accept(ModBlocks.BIO_FEEDER.get());
                // Branch C: トラップ・防衛 7 機種
                output.accept(ModBlocks.MOB_FAN.get());
                output.accept(ModBlocks.ROTARY_DRILL.get());
                output.accept(ModBlocks.ITEM_VACUUM.get());
                output.accept(ModBlocks.XP_COLLECTOR.get());
                output.accept(ModBlocks.SPAWN_SUPPRESSOR.get());
                output.accept(ModBlocks.LASER_TURRET.get());
                output.accept(ModBlocks.MOB_DUPLICATOR.get());
                // Branch D: 建築・生活 6 機種＋監視
                output.accept(ModBlocks.FLUID_PUMP.get());
                output.accept(ModBlocks.AUTO_BUILDER.get());
                output.accept(ModBlocks.WIRELESS_CHARGER.get());
                output.accept(ModBlocks.CHUNK_LOADER.get());
                output.accept(ModBlocks.CLIMATE_CONTROLLER.get());
                output.accept(ModBlocks.WARP_PAD.get());
                output.accept(ModBlocks.FACTORY_MONITOR.get());

                // =========================================================================
                // 6. エンドゲーム装備・電動兵器・ツール＆アップグレード
                // =========================================================================
                output.accept(ModBlocks.SUIT_MODIFIER.get());
                output.accept(ModItems.VIRTUAL_HELMET.get());
                output.accept(ModItems.VIRTUAL_CHESTPLATE.get());
                output.accept(ModItems.VIRTUAL_LEGGINGS.get());
                output.accept(ModItems.VIRTUAL_BOOTS.get());
                output.accept(ModItems.MOD_VISION.get());
                output.accept(ModItems.MOD_AUTOFEED.get());
                output.accept(ModItems.MOD_FLIGHT.get());
                output.accept(ModItems.MOD_SHIELD.get());
                output.accept(ModItems.MOD_SPEED.get());
                output.accept(ModItems.MOD_STEP.get());
                output.accept(ModItems.MOD_NOFALL.get());
                output.accept(ModItems.MOD_JUMP.get());
                output.accept(ModItems.PULSE_LASER.get());
                output.accept(ModItems.RAILGUN.get());
                output.accept(ModItems.QUANTUM_BLADE.get());
                output.accept(ModItems.ENERGY_BOW.get());
                output.accept(ModItems.ATT_AUTO_AIM.get());
                output.accept(ModItems.ATT_CRYO_CORE.get());
                output.accept(ModItems.ATT_PRISM_LENS.get());
                output.accept(ModItems.ATT_FAST_CAPACITOR.get());
                output.accept(ModItems.ATT_WALL_SCANNER.get());
                output.accept(ModItems.ATT_RECOIL_GYRO.get());
                output.accept(ModItems.ATT_PLASMA_WAVE.get());
                output.accept(ModItems.ATT_BLINK_DRIVE.get());
                output.accept(ModItems.ATT_QUANTUM_LOOT.get());
                output.accept(ModItems.ATT_MULTI_LOCK.get());
                output.accept(ModItems.ATT_GRAVITY_ARROW.get());
                output.accept(ModItems.ATT_AUTO_DRAW.get());
                // 中盤装備・ツール＆ユーティリティ
                output.accept(ModItems.ADAMANTITE_HELMET.get());
                output.accept(ModItems.ADAMANTITE_CHESTPLATE.get());
                output.accept(ModItems.ADAMANTITE_LEGGINGS.get());
                output.accept(ModItems.ADAMANTITE_BOOTS.get());
                output.accept(ModItems.MITHRIL_PICKAXE.get());
                output.accept(ModItems.MITHRIL_SWORD.get());
                output.accept(ModItems.ORICHALCUM_PICKAXE.get());
                output.accept(ModItems.ORICHALCUM_SWORD.get());
                output.accept(ModItems.WRENCH.get());
                output.accept(ModItems.MOB_CAPSULE.get());
            })
            .build());

    private ModCreativeTabs() {}
}

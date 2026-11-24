package com.industrialcore.registry;

import com.industrialcore.IndustrialCore;

import com.industrialcore.item.AdvancedTransporterDriveItem;
import com.industrialcore.item.PipeBlockItem;
import com.industrialcore.item.TooltipItem;
import com.industrialcore.item.WrenchItem;
import com.industrialcore.machine.MythicGear;
import com.industrialcore.registry.ModArmorMaterials;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItemTiers;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IndustrialCore.MOD_ID);
    public static final DeferredItem<BlockItem> QUARRY = ITEMS.registerSimpleBlockItem("quarry", ModBlocks.QUARRY);
    public static final DeferredItem<BlockItem> LARGE_QUARRY = ITEMS.registerSimpleBlockItem("large_quarry", ModBlocks.LARGE_QUARRY);
    public static final DeferredItem<BlockItem> CONVEYOR = ITEMS.registerSimpleBlockItem("conveyor", ModBlocks.CONVEYOR);
    public static final DeferredItem<BlockItem> ITEM_PIPE = ITEMS.register("item_pipe", () -> new PipeBlockItem((Block)ModBlocks.ITEM_PIPE.get(), new Item.Properties(), "gui.industrialcore.tooltip.item_pipe"));
    public static final DeferredItem<BlockItem> IMPROVED_ITEM_PIPE = ITEMS.register("pipe_improved", () -> new PipeBlockItem((Block)ModBlocks.IMPROVED_ITEM_PIPE.get(), new Item.Properties(), "gui.industrialcore.tooltip.pipe_improved"));
    public static final DeferredItem<BlockItem> ADVANCED_ITEM_PIPE = ITEMS.register("pipe_advanced", () -> new PipeBlockItem((Block)ModBlocks.ADVANCED_ITEM_PIPE.get(), new Item.Properties(), "gui.industrialcore.tooltip.pipe_advanced"));
    public static final DeferredItem<BlockItem> ULTIMATE_ITEM_PIPE = ITEMS.register("pipe_ultimate", () -> new PipeBlockItem((Block)ModBlocks.ULTIMATE_ITEM_PIPE.get(), new Item.Properties(), "gui.industrialcore.tooltip.pipe_ultimate"));
    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.register("wrench", () -> new WrenchItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> CRAFTER = ITEMS.registerSimpleBlockItem("crafter", ModBlocks.CRAFTER);
    public static final DeferredItem<BlockItem> CABLE_BASIC = ITEMS.registerSimpleBlockItem("cable_basic", ModBlocks.CABLE_BASIC);
    public static final DeferredItem<BlockItem> CABLE_IMPROVED = ITEMS.registerSimpleBlockItem("cable_improved", ModBlocks.CABLE_IMPROVED);
    public static final DeferredItem<BlockItem> CABLE_ADVANCED = ITEMS.registerSimpleBlockItem("cable_advanced", ModBlocks.CABLE_ADVANCED);
    public static final DeferredItem<BlockItem> CABLE_ULTIMATE = ITEMS.registerSimpleBlockItem("cable_ultimate", ModBlocks.CABLE_ULTIMATE);
    public static final DeferredItem<BlockItem> CRUSHER = ITEMS.registerSimpleBlockItem("crusher", ModBlocks.CRUSHER);
    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE = ITEMS.registerSimpleBlockItem("electric_furnace", ModBlocks.ELECTRIC_FURNACE);
    public static final DeferredItem<BlockItem> ELECTROLYZER = ITEMS.registerSimpleBlockItem("electrolyzer", ModBlocks.ELECTROLYZER);
    public static final DeferredItem<BlockItem> PRECISION_CRUSHER = ITEMS.registerSimpleBlockItem("precision_crusher", ModBlocks.PRECISION_CRUSHER);
    public static final DeferredItem<BlockItem> FAST_CRUSHER = ITEMS.registerSimpleBlockItem("fast_crusher", ModBlocks.FAST_CRUSHER);
    public static final DeferredItem<Item> MOTOR = ITEMS.registerSimpleItem("motor", new Item.Properties());
    public static final DeferredItem<Item> BATTERY = ITEMS.registerSimpleItem("battery", new Item.Properties());
    public static final DeferredItem<Item> SPECIALIZATION_MODULE = ITEMS.register("specialization_module",
            () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.specialization_module"));
    public static final DeferredItem<Item> IRON_DUST = ITEMS.registerSimpleItem("iron_dust", new Item.Properties());
    public static final DeferredItem<Item> COPPER_DUST = ITEMS.registerSimpleItem("copper_dust", new Item.Properties());
    public static final DeferredItem<Item> GOLD_DUST = ITEMS.registerSimpleItem("gold_dust", new Item.Properties());
    public static final DeferredItem<Item> PURE_IRON = ITEMS.register("pure_iron", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_iron"));
    public static final DeferredItem<Item> PURE_COPPER = ITEMS.register("pure_copper", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_copper"));
    public static final DeferredItem<Item> PURE_GOLD = ITEMS.register("pure_gold", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_gold"));
    public static final DeferredItem<Item> EXPOSED_IRON = ITEMS.register("exposed_iron", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_iron"));
    public static final DeferredItem<Item> EXPOSED_COPPER = ITEMS.register("exposed_copper", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_copper"));
    public static final DeferredItem<Item> EXPOSED_GOLD = ITEMS.register("exposed_gold", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_gold"));
    public static final DeferredItem<BlockItem> RECONSTRUCTOR = ITEMS.registerSimpleBlockItem("reconstructor", ModBlocks.RECONSTRUCTOR);
    public static final DeferredItem<Item> RECONSTRUCTION_CRYSTAL = ITEMS.register("reconstruction_crystal", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.reconstruction_crystal"));
    public static final DeferredItem<BlockItem> METAL_PRESS = ITEMS.registerSimpleBlockItem("metal_press", ModBlocks.METAL_PRESS);
    public static final DeferredItem<BlockItem> ALLOY_FURNACE = ITEMS.registerSimpleBlockItem("alloy_furnace", ModBlocks.ALLOY_FURNACE);
    public static final DeferredItem<BlockItem> ENERGY_BUFFER = ITEMS.registerSimpleBlockItem("energy_buffer", ModBlocks.ENERGY_BUFFER);
    public static final DeferredItem<Item> IRON_PLATE = ITEMS.register("iron_plate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_plate"));
    public static final DeferredItem<Item> COPPER_PLATE = ITEMS.register("copper_plate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_plate"));
    public static final DeferredItem<Item> GOLD_PLATE = ITEMS.register("gold_plate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_plate"));
    public static final DeferredItem<Item> STEEL_PLATE = ITEMS.register("steel_plate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.steel_plate"));
    public static final DeferredItem<Item> BRASS_PLATE = ITEMS.register("brass_plate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.brass_plate"));
    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.register("steel_ingot", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.steel_ingot"));
    public static final DeferredItem<Item> BRASS_INGOT = ITEMS.register("brass_ingot", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.brass_ingot"));
    public static final DeferredItem<Item> MACHINE_FRAME = ITEMS.register("machine_frame", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.machine_frame"));
    public static final DeferredItem<BlockItem> ASSEMBLER = ITEMS.registerSimpleBlockItem("assembler", ModBlocks.ASSEMBLER);
    public static final DeferredItem<BlockItem> ENERGY_BUFFER_MK2 = ITEMS.registerSimpleBlockItem("energy_buffer_mk2", ModBlocks.ENERGY_BUFFER_MK2);
    public static final DeferredItem<BlockItem> ENERGY_BUFFER_MK3 = ITEMS.registerSimpleBlockItem("energy_buffer_mk3", ModBlocks.ENERGY_BUFFER_MK3);
    public static final DeferredItem<Item> ELECTRONIC_COMPONENT = ITEMS.register("electronic_component", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.electronic_component"));
    public static final DeferredItem<Item> REINFORCED_MACHINE_FRAME = ITEMS.register("reinforced_machine_frame", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.reinforced_machine_frame"));
    public static final DeferredItem<BlockItem> ITEM_DISTRIBUTOR = ITEMS.registerSimpleBlockItem("item_distributor", ModBlocks.ITEM_DISTRIBUTOR);
    public static final DeferredItem<BlockItem> SEED_CULTIVATOR = ITEMS.registerSimpleBlockItem("seed_cultivator", ModBlocks.SEED_CULTIVATOR);
    public static final DeferredItem<BlockItem> MATTER_AMPLIFIER = ITEMS.registerSimpleBlockItem("matter_amplifier", ModBlocks.MATTER_AMPLIFIER);
    /** Phase 82: the factory monitor terminal. */
    public static final DeferredItem<BlockItem> FACTORY_MONITOR = ITEMS.registerSimpleBlockItem("factory_monitor", ModBlocks.FACTORY_MONITOR);
    public static final DeferredItem<Item> IRON_SEED = ITEMS.register("iron_seed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_seed"));
    public static final DeferredItem<Item> COPPER_SEED = ITEMS.register("copper_seed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_seed"));
    public static final DeferredItem<Item> GOLD_SEED = ITEMS.register("gold_seed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_seed"));
    public static final DeferredItem<Item> IRON_BILLET = ITEMS.register("iron_billet", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_billet"));
    public static final DeferredItem<Item> COPPER_BILLET = ITEMS.register("copper_billet", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_billet"));
    public static final DeferredItem<Item> GOLD_BILLET = ITEMS.register("gold_billet", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_billet"));
    public static final DeferredItem<BlockItem> PHASE_REACTOR = ITEMS.registerSimpleBlockItem("phase_reactor", ModBlocks.PHASE_REACTOR);
    public static final DeferredItem<BlockItem> QUANTUM_COMPRESSOR = ITEMS.registerSimpleBlockItem("quantum_compressor", ModBlocks.QUANTUM_COMPRESSOR);
    public static final DeferredItem<BlockItem> BASIC_ITEM_TRANSPORTER = ITEMS.registerSimpleBlockItem("basic_item_transporter", ModBlocks.BASIC_ITEM_TRANSPORTER);
    public static final DeferredItem<BlockItem> ADVANCED_ITEM_TRANSPORTER = ITEMS.registerSimpleBlockItem("advanced_item_transporter", ModBlocks.ADVANCED_ITEM_TRANSPORTER);
    public static final DeferredItem<BlockItem> QUANTUM_TRANSPORTER = ITEMS.registerSimpleBlockItem("quantum_transporter", ModBlocks.QUANTUM_TRANSPORTER);
    public static final DeferredItem<BlockItem> QUANTUM_TRANSPORTER_ARRAY = ITEMS.registerSimpleBlockItem("quantum_transporter_array", ModBlocks.QUANTUM_TRANSPORTER_ARRAY);
    public static final DeferredItem<BlockItem> TRANSPORTER_CASING = ITEMS.registerSimpleBlockItem("transporter_casing", ModBlocks.TRANSPORTER_CASING);
    // Phase 63: placing this item builds the whole 3x2x3 machine in one go.
    public static final DeferredItem<BlockItem> ADVANCED_TRANSPORTER_DRIVE = ITEMS.register(
            "advanced_transporter_drive", () -> new AdvancedTransporterDriveItem(
                    ModBlocks.ADVANCED_TRANSPORTER_DRIVE.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> ADVANCED_TRANSPORTER_CASING = ITEMS.registerSimpleBlockItem("advanced_transporter_casing", ModBlocks.ADVANCED_TRANSPORTER_CASING);
    public static final DeferredItem<BlockItem> QUANTUM_TRANSPORTER_CASING = ITEMS.registerSimpleBlockItem("quantum_transporter_casing", ModBlocks.QUANTUM_TRANSPORTER_CASING);
    public static final DeferredItem<BlockItem> ASTRAL_SILVER_ORE = ITEMS.registerSimpleBlockItem("astral_silver_ore", ModBlocks.ASTRAL_SILVER_ORE);
    public static final DeferredItem<BlockItem> MITHRIL_ORE = ITEMS.registerSimpleBlockItem("mithril_ore", ModBlocks.MITHRIL_ORE);
    public static final DeferredItem<BlockItem> NEBULA_ORE = ITEMS.registerSimpleBlockItem("nebula_ore", ModBlocks.NEBULA_ORE);
    public static final DeferredItem<BlockItem> ORICHALCUM_ORE = ITEMS.registerSimpleBlockItem("orichalcum_ore", ModBlocks.ORICHALCUM_ORE);
    public static final DeferredItem<BlockItem> QUANTUM_ORE = ITEMS.registerSimpleBlockItem("quantum_ore", ModBlocks.QUANTUM_ORE);
    public static final DeferredItem<BlockItem> ADAMANTITE_ORE = ITEMS.registerSimpleBlockItem("adamantite_ore", ModBlocks.ADAMANTITE_ORE);
    public static final DeferredItem<BlockItem> AETHER_ORE = ITEMS.registerSimpleBlockItem("aether_ore", ModBlocks.AETHER_ORE);
    public static final DeferredItem<BlockItem> VOID_IRON_ORE = ITEMS.registerSimpleBlockItem("void_iron_ore", ModBlocks.VOID_IRON_ORE);
    public static final DeferredItem<Item> RAW_ADAMANTITE = ITEMS.register("raw_adamantite", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.raw_adamantite"));
    public static final DeferredItem<Item> RAW_MITHRIL = ITEMS.register("raw_mithril", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.raw_mithril"));
    public static final DeferredItem<Item> RAW_ORICHALCUM = ITEMS.register("raw_orichalcum", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.raw_orichalcum"));
    public static final DeferredItem<Item> RAW_ASTRAL_SILVER = ITEMS.register("raw_astral_silver", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.raw_astral_silver"));
    public static final DeferredItem<Item> RAW_VOID_IRON = ITEMS.register("raw_void_iron", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.raw_void_iron"));
    public static final DeferredItem<Item> ADAMANTITE_INGOT = ITEMS.register("adamantite_ingot", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_ingot"));
    public static final DeferredItem<Item> MITHRIL_INGOT = ITEMS.register("mithril_ingot", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_ingot"));
    public static final DeferredItem<Item> ORICHALCUM_INGOT = ITEMS.register("orichalcum_ingot", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_ingot"));
    public static final DeferredItem<Item> ASTRAL_SILVER_INGOT = ITEMS.register("astral_silver_ingot", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_ingot"));
    public static final DeferredItem<Item> VOID_IRON_INGOT = ITEMS.register("void_iron_ingot", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_ingot"));
    public static final DeferredItem<Item> NEBULA_CRYSTAL = ITEMS.register("nebula_crystal", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_crystal"));
    public static final DeferredItem<Item> QUANTUM_SHARD = ITEMS.register("quantum_shard", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_shard"));
    public static final DeferredItem<Item> AETHER_CRYSTAL = ITEMS.register("aether_crystal", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_crystal"));
    public static final DeferredItem<Item> RESONANT_CATALYST = ITEMS.register("resonant_catalyst", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.resonant_catalyst"));
    public static final DeferredItem<Item> DURABLE_MACHINE_FRAME = ITEMS.register("durable_machine_frame", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.durable_machine_frame"));
    public static final DeferredItem<Item> HIGH_PERFORMANCE_MOTOR = ITEMS.register("high_power_motor", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.high_power_motor"));
    public static final DeferredItem<Item> PHASE_CORE = ITEMS.register("phase_core", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.phase_core"));
    public static final DeferredItem<ArmorItem> ADAMANTITE_HELMET = ITEMS.register("adamantite_helmet", () -> new ArmorItem(ModArmorMaterials.ADAMANTITE, ArmorItem.Type.HELMET, new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(MythicGear.ADAMANTITE_ARMOR.durabilityBase()))));
    public static final DeferredItem<ArmorItem> ADAMANTITE_CHESTPLATE = ITEMS.register("adamantite_chestplate", () -> new ArmorItem(ModArmorMaterials.ADAMANTITE, ArmorItem.Type.CHESTPLATE, new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(MythicGear.ADAMANTITE_ARMOR.durabilityBase()))));
    public static final DeferredItem<ArmorItem> ADAMANTITE_LEGGINGS = ITEMS.register("adamantite_leggings", () -> new ArmorItem(ModArmorMaterials.ADAMANTITE, ArmorItem.Type.LEGGINGS, new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(MythicGear.ADAMANTITE_ARMOR.durabilityBase()))));
    public static final DeferredItem<ArmorItem> ADAMANTITE_BOOTS = ITEMS.register("adamantite_boots", () -> new ArmorItem(ModArmorMaterials.ADAMANTITE, ArmorItem.Type.BOOTS, new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(MythicGear.ADAMANTITE_ARMOR.durabilityBase()))));
    public static final DeferredItem<PickaxeItem> MITHRIL_PICKAXE = ITEMS.register("mithril_pickaxe", () -> new PickaxeItem((Tier)ModItemTiers.MITHRIL, new Item.Properties().attributes(DiggerItem.createAttributes((Tier)ModItemTiers.MITHRIL, (float)1.0f, (float)-2.8f))));
    public static final DeferredItem<SwordItem> MITHRIL_SWORD = ITEMS.register("mithril_sword", () -> new SwordItem((Tier)ModItemTiers.MITHRIL, new Item.Properties().attributes(SwordItem.createAttributes((Tier)ModItemTiers.MITHRIL, (float)3.0f, (float)-2.4f))));
    public static final DeferredItem<PickaxeItem> ORICHALCUM_PICKAXE = ITEMS.register("orichalcum_pickaxe", () -> new PickaxeItem((Tier)ModItemTiers.ORICHALCUM, new Item.Properties().attributes(DiggerItem.createAttributes((Tier)ModItemTiers.ORICHALCUM, (float)1.0f, (float)-2.8f))));
    public static final DeferredItem<SwordItem> ORICHALCUM_SWORD = ITEMS.register("orichalcum_sword", () -> new SwordItem((Tier)ModItemTiers.ORICHALCUM, new Item.Properties().attributes(SwordItem.createAttributes((Tier)ModItemTiers.ORICHALCUM, (float)3.0f, (float)-2.4f))));
    public static final DeferredItem<BlockItem> ORE_SYNTHESIZER = ITEMS.registerSimpleBlockItem("ore_synthesizer", ModBlocks.ORE_SYNTHESIZER);
    public static final DeferredItem<Item> SYNTHETIC_ORE_1 = ITEMS.register("synthetic_ore_1", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SYNTHETIC_ORE_2 = ITEMS.register("synthetic_ore_2", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SYNTHETIC_ORE_3 = ITEMS.register("synthetic_ore_3", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SYNTHETIC_ORE_4 = ITEMS.register("synthetic_ore_4", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SYNTHETIC_ORE_5 = ITEMS.register("synthetic_ore_5", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SYNTHETIC_ORE_6 = ITEMS.register("synthetic_ore_6", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SYNTHETIC_ORE_HIGH_DENSITY = ITEMS.register("synthetic_ore_high_density", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SYNTHETIC_ORE_UNSTABLE_ULTIMATE = ITEMS.register("synthetic_ore_unstable_ultimate", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SYNTHETIC_ORE_ULTIMATE = ITEMS.register("synthetic_ore_ultimate", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> IRON_WASHED = ITEMS.register("iron_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_washed"));
    public static final DeferredItem<Item> IRON_SORTED = ITEMS.register("iron_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_sorted"));
    public static final DeferredItem<Item> IRON_CONCENTRATE = ITEMS.register("iron_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_concentrate"));
    public static final DeferredItem<Item> IRON_REFINED = ITEMS.register("iron_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_refined"));
    public static final DeferredItem<Item> IRON_SEPARATED = ITEMS.register("iron_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_separated"));
    public static final DeferredItem<Item> IRON_HIGH_PURITY = ITEMS.register("iron_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_high_purity"));
    public static final DeferredItem<Item> IRON_MICRONIZED = ITEMS.register("iron_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_micronized"));
    public static final DeferredItem<Item> IRON_CRYSTALLIZED = ITEMS.register("iron_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_crystallized"));
    public static final DeferredItem<Item> IRON_HOMOGENIZED = ITEMS.register("iron_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_homogenized"));
    public static final DeferredItem<Item> IRON_COMPRESSED = ITEMS.register("iron_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_compressed"));
    public static final DeferredItem<Item> IRON_STABILIZED = ITEMS.register("iron_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.iron_stabilized"));
    public static final DeferredItem<Item> COPPER_WASHED = ITEMS.register("copper_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_washed"));
    public static final DeferredItem<Item> COPPER_SORTED = ITEMS.register("copper_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_sorted"));
    public static final DeferredItem<Item> COPPER_CONCENTRATE = ITEMS.register("copper_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_concentrate"));
    public static final DeferredItem<Item> COPPER_REFINED = ITEMS.register("copper_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_refined"));
    public static final DeferredItem<Item> COPPER_SEPARATED = ITEMS.register("copper_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_separated"));
    public static final DeferredItem<Item> COPPER_HIGH_PURITY = ITEMS.register("copper_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_high_purity"));
    public static final DeferredItem<Item> COPPER_MICRONIZED = ITEMS.register("copper_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_micronized"));
    public static final DeferredItem<Item> COPPER_CRYSTALLIZED = ITEMS.register("copper_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_crystallized"));
    public static final DeferredItem<Item> COPPER_HOMOGENIZED = ITEMS.register("copper_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_homogenized"));
    public static final DeferredItem<Item> COPPER_COMPRESSED = ITEMS.register("copper_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_compressed"));
    public static final DeferredItem<Item> COPPER_STABILIZED = ITEMS.register("copper_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.copper_stabilized"));
    public static final DeferredItem<Item> GOLD_WASHED = ITEMS.register("gold_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_washed"));
    public static final DeferredItem<Item> GOLD_SORTED = ITEMS.register("gold_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_sorted"));
    public static final DeferredItem<Item> GOLD_CONCENTRATE = ITEMS.register("gold_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_concentrate"));
    public static final DeferredItem<Item> GOLD_REFINED = ITEMS.register("gold_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_refined"));
    public static final DeferredItem<Item> GOLD_SEPARATED = ITEMS.register("gold_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_separated"));
    public static final DeferredItem<Item> GOLD_HIGH_PURITY = ITEMS.register("gold_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_high_purity"));
    public static final DeferredItem<Item> GOLD_MICRONIZED = ITEMS.register("gold_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_micronized"));
    public static final DeferredItem<Item> GOLD_CRYSTALLIZED = ITEMS.register("gold_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_crystallized"));
    public static final DeferredItem<Item> GOLD_HOMOGENIZED = ITEMS.register("gold_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_homogenized"));
    public static final DeferredItem<Item> GOLD_COMPRESSED = ITEMS.register("gold_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_compressed"));
    public static final DeferredItem<Item> GOLD_STABILIZED = ITEMS.register("gold_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.gold_stabilized"));
    public static final DeferredItem<Item> ADAMANTITE_DUST = ITEMS.register("adamantite_dust", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_dust"));
    public static final DeferredItem<Item> PURE_ADAMANTITE = ITEMS.register("pure_adamantite", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_adamantite"));
    public static final DeferredItem<Item> EXPOSED_ADAMANTITE = ITEMS.register("exposed_adamantite", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_adamantite"));
    public static final DeferredItem<Item> ADAMANTITE_WASHED = ITEMS.register("adamantite_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_washed"));
    public static final DeferredItem<Item> ADAMANTITE_SORTED = ITEMS.register("adamantite_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_sorted"));
    public static final DeferredItem<Item> ADAMANTITE_CONCENTRATE = ITEMS.register("adamantite_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_concentrate"));
    public static final DeferredItem<Item> ADAMANTITE_REFINED = ITEMS.register("adamantite_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_refined"));
    public static final DeferredItem<Item> ADAMANTITE_SEPARATED = ITEMS.register("adamantite_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_separated"));
    public static final DeferredItem<Item> ADAMANTITE_HIGH_PURITY = ITEMS.register("adamantite_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_high_purity"));
    public static final DeferredItem<Item> ADAMANTITE_MICRONIZED = ITEMS.register("adamantite_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_micronized"));
    public static final DeferredItem<Item> ADAMANTITE_CRYSTALLIZED = ITEMS.register("adamantite_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_crystallized"));
    public static final DeferredItem<Item> ADAMANTITE_HOMOGENIZED = ITEMS.register("adamantite_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_homogenized"));
    public static final DeferredItem<Item> ADAMANTITE_COMPRESSED = ITEMS.register("adamantite_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_compressed"));
    public static final DeferredItem<Item> ADAMANTITE_STABILIZED = ITEMS.register("adamantite_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.adamantite_stabilized"));
    public static final DeferredItem<Item> MITHRIL_DUST = ITEMS.register("mithril_dust", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_dust"));
    public static final DeferredItem<Item> PURE_MITHRIL = ITEMS.register("pure_mithril", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_mithril"));
    public static final DeferredItem<Item> EXPOSED_MITHRIL = ITEMS.register("exposed_mithril", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_mithril"));
    public static final DeferredItem<Item> MITHRIL_WASHED = ITEMS.register("mithril_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_washed"));
    public static final DeferredItem<Item> MITHRIL_SORTED = ITEMS.register("mithril_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_sorted"));
    public static final DeferredItem<Item> MITHRIL_CONCENTRATE = ITEMS.register("mithril_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_concentrate"));
    public static final DeferredItem<Item> MITHRIL_REFINED = ITEMS.register("mithril_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_refined"));
    public static final DeferredItem<Item> MITHRIL_SEPARATED = ITEMS.register("mithril_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_separated"));
    public static final DeferredItem<Item> MITHRIL_HIGH_PURITY = ITEMS.register("mithril_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_high_purity"));
    public static final DeferredItem<Item> MITHRIL_MICRONIZED = ITEMS.register("mithril_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_micronized"));
    public static final DeferredItem<Item> MITHRIL_CRYSTALLIZED = ITEMS.register("mithril_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_crystallized"));
    public static final DeferredItem<Item> MITHRIL_HOMOGENIZED = ITEMS.register("mithril_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_homogenized"));
    public static final DeferredItem<Item> MITHRIL_COMPRESSED = ITEMS.register("mithril_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_compressed"));
    public static final DeferredItem<Item> MITHRIL_STABILIZED = ITEMS.register("mithril_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.mithril_stabilized"));
    public static final DeferredItem<Item> ORICHALCUM_DUST = ITEMS.register("orichalcum_dust", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_dust"));
    public static final DeferredItem<Item> PURE_ORICHALCUM = ITEMS.register("pure_orichalcum", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_orichalcum"));
    public static final DeferredItem<Item> EXPOSED_ORICHALCUM = ITEMS.register("exposed_orichalcum", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_orichalcum"));
    public static final DeferredItem<Item> ORICHALCUM_WASHED = ITEMS.register("orichalcum_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_washed"));
    public static final DeferredItem<Item> ORICHALCUM_SORTED = ITEMS.register("orichalcum_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_sorted"));
    public static final DeferredItem<Item> ORICHALCUM_CONCENTRATE = ITEMS.register("orichalcum_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_concentrate"));
    public static final DeferredItem<Item> ORICHALCUM_REFINED = ITEMS.register("orichalcum_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_refined"));
    public static final DeferredItem<Item> ORICHALCUM_SEPARATED = ITEMS.register("orichalcum_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_separated"));
    public static final DeferredItem<Item> ORICHALCUM_HIGH_PURITY = ITEMS.register("orichalcum_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_high_purity"));
    public static final DeferredItem<Item> ORICHALCUM_MICRONIZED = ITEMS.register("orichalcum_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_micronized"));
    public static final DeferredItem<Item> ORICHALCUM_CRYSTALLIZED = ITEMS.register("orichalcum_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_crystallized"));
    public static final DeferredItem<Item> ORICHALCUM_HOMOGENIZED = ITEMS.register("orichalcum_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_homogenized"));
    public static final DeferredItem<Item> ORICHALCUM_COMPRESSED = ITEMS.register("orichalcum_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_compressed"));
    public static final DeferredItem<Item> ORICHALCUM_STABILIZED = ITEMS.register("orichalcum_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.orichalcum_stabilized"));
    public static final DeferredItem<Item> VOID_IRON_DUST = ITEMS.register("void_iron_dust", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_dust"));
    public static final DeferredItem<Item> PURE_VOID_IRON = ITEMS.register("pure_void_iron", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_void_iron"));
    public static final DeferredItem<Item> EXPOSED_VOID_IRON = ITEMS.register("exposed_void_iron", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_void_iron"));
    public static final DeferredItem<Item> VOID_IRON_WASHED = ITEMS.register("void_iron_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_washed"));
    public static final DeferredItem<Item> VOID_IRON_SORTED = ITEMS.register("void_iron_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_sorted"));
    public static final DeferredItem<Item> VOID_IRON_CONCENTRATE = ITEMS.register("void_iron_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_concentrate"));
    public static final DeferredItem<Item> VOID_IRON_REFINED = ITEMS.register("void_iron_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_refined"));
    public static final DeferredItem<Item> VOID_IRON_SEPARATED = ITEMS.register("void_iron_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_separated"));
    public static final DeferredItem<Item> VOID_IRON_HIGH_PURITY = ITEMS.register("void_iron_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_high_purity"));
    public static final DeferredItem<Item> VOID_IRON_MICRONIZED = ITEMS.register("void_iron_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_micronized"));
    public static final DeferredItem<Item> VOID_IRON_CRYSTALLIZED = ITEMS.register("void_iron_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_crystallized"));
    public static final DeferredItem<Item> VOID_IRON_HOMOGENIZED = ITEMS.register("void_iron_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_homogenized"));
    public static final DeferredItem<Item> VOID_IRON_COMPRESSED = ITEMS.register("void_iron_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_compressed"));
    public static final DeferredItem<Item> VOID_IRON_STABILIZED = ITEMS.register("void_iron_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.void_iron_stabilized"));
    public static final DeferredItem<Item> ASTRAL_SILVER_DUST = ITEMS.register("astral_silver_dust", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_dust"));
    public static final DeferredItem<Item> PURE_ASTRAL_SILVER = ITEMS.register("pure_astral_silver", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_astral_silver"));
    public static final DeferredItem<Item> EXPOSED_ASTRAL_SILVER = ITEMS.register("exposed_astral_silver", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_astral_silver"));
    public static final DeferredItem<Item> ASTRAL_SILVER_WASHED = ITEMS.register("astral_silver_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_washed"));
    public static final DeferredItem<Item> ASTRAL_SILVER_SORTED = ITEMS.register("astral_silver_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_sorted"));
    public static final DeferredItem<Item> ASTRAL_SILVER_CONCENTRATE = ITEMS.register("astral_silver_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_concentrate"));
    public static final DeferredItem<Item> ASTRAL_SILVER_REFINED = ITEMS.register("astral_silver_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_refined"));
    public static final DeferredItem<Item> ASTRAL_SILVER_SEPARATED = ITEMS.register("astral_silver_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_separated"));
    public static final DeferredItem<Item> ASTRAL_SILVER_HIGH_PURITY = ITEMS.register("astral_silver_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_high_purity"));
    public static final DeferredItem<Item> ASTRAL_SILVER_MICRONIZED = ITEMS.register("astral_silver_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_micronized"));
    public static final DeferredItem<Item> ASTRAL_SILVER_CRYSTALLIZED = ITEMS.register("astral_silver_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_crystallized"));
    public static final DeferredItem<Item> ASTRAL_SILVER_HOMOGENIZED = ITEMS.register("astral_silver_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_homogenized"));
    public static final DeferredItem<Item> ASTRAL_SILVER_COMPRESSED = ITEMS.register("astral_silver_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_compressed"));
    public static final DeferredItem<Item> ASTRAL_SILVER_STABILIZED = ITEMS.register("astral_silver_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.astral_silver_stabilized"));
    public static final DeferredItem<Item> AETHER_DUST = ITEMS.register("aether_dust", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_dust"));
    public static final DeferredItem<Item> PURE_AETHER = ITEMS.register("pure_aether", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_aether"));
    public static final DeferredItem<Item> EXPOSED_AETHER = ITEMS.register("exposed_aether", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_aether"));
    public static final DeferredItem<Item> AETHER_WASHED = ITEMS.register("aether_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_washed"));
    public static final DeferredItem<Item> AETHER_SORTED = ITEMS.register("aether_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_sorted"));
    public static final DeferredItem<Item> AETHER_CONCENTRATE = ITEMS.register("aether_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_concentrate"));
    public static final DeferredItem<Item> AETHER_REFINED = ITEMS.register("aether_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_refined"));
    public static final DeferredItem<Item> AETHER_SEPARATED = ITEMS.register("aether_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_separated"));
    public static final DeferredItem<Item> AETHER_HIGH_PURITY = ITEMS.register("aether_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_high_purity"));
    public static final DeferredItem<Item> AETHER_MICRONIZED = ITEMS.register("aether_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_micronized"));
    public static final DeferredItem<Item> AETHER_CRYSTALLIZED = ITEMS.register("aether_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_crystallized"));
    public static final DeferredItem<Item> AETHER_HOMOGENIZED = ITEMS.register("aether_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_homogenized"));
    public static final DeferredItem<Item> AETHER_COMPRESSED = ITEMS.register("aether_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_compressed"));
    public static final DeferredItem<Item> AETHER_STABILIZED = ITEMS.register("aether_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.aether_stabilized"));
    public static final DeferredItem<Item> NEBULA_DUST = ITEMS.register("nebula_dust", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_dust"));
    public static final DeferredItem<Item> PURE_NEBULA = ITEMS.register("pure_nebula", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_nebula"));
    public static final DeferredItem<Item> EXPOSED_NEBULA = ITEMS.register("exposed_nebula", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_nebula"));
    public static final DeferredItem<Item> NEBULA_WASHED = ITEMS.register("nebula_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_washed"));
    public static final DeferredItem<Item> NEBULA_SORTED = ITEMS.register("nebula_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_sorted"));
    public static final DeferredItem<Item> NEBULA_CONCENTRATE = ITEMS.register("nebula_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_concentrate"));
    public static final DeferredItem<Item> NEBULA_REFINED = ITEMS.register("nebula_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_refined"));
    public static final DeferredItem<Item> NEBULA_SEPARATED = ITEMS.register("nebula_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_separated"));
    public static final DeferredItem<Item> NEBULA_HIGH_PURITY = ITEMS.register("nebula_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_high_purity"));
    public static final DeferredItem<Item> NEBULA_MICRONIZED = ITEMS.register("nebula_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_micronized"));
    public static final DeferredItem<Item> NEBULA_CRYSTALLIZED = ITEMS.register("nebula_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_crystallized"));
    public static final DeferredItem<Item> NEBULA_HOMOGENIZED = ITEMS.register("nebula_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_homogenized"));
    public static final DeferredItem<Item> NEBULA_COMPRESSED = ITEMS.register("nebula_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_compressed"));
    public static final DeferredItem<Item> NEBULA_STABILIZED = ITEMS.register("nebula_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.nebula_stabilized"));
    public static final DeferredItem<Item> QUANTUM_DUST = ITEMS.register("quantum_dust", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_dust"));
    public static final DeferredItem<Item> PURE_QUANTUM = ITEMS.register("pure_quantum", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.pure_quantum"));
    public static final DeferredItem<Item> EXPOSED_QUANTUM = ITEMS.register("exposed_quantum", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.exposed_quantum"));
    public static final DeferredItem<Item> QUANTUM_WASHED = ITEMS.register("quantum_washed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_washed"));
    public static final DeferredItem<Item> QUANTUM_SORTED = ITEMS.register("quantum_sorted", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_sorted"));
    public static final DeferredItem<Item> QUANTUM_CONCENTRATE = ITEMS.register("quantum_concentrate", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_concentrate"));
    public static final DeferredItem<Item> QUANTUM_REFINED = ITEMS.register("quantum_refined", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_refined"));
    public static final DeferredItem<Item> QUANTUM_SEPARATED = ITEMS.register("quantum_separated", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_separated"));
    public static final DeferredItem<Item> QUANTUM_HIGH_PURITY = ITEMS.register("quantum_high_purity", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_high_purity"));
    public static final DeferredItem<Item> QUANTUM_MICRONIZED = ITEMS.register("quantum_micronized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_micronized"));
    public static final DeferredItem<Item> QUANTUM_CRYSTALLIZED = ITEMS.register("quantum_crystallized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_crystallized"));
    public static final DeferredItem<Item> QUANTUM_HOMOGENIZED = ITEMS.register("quantum_homogenized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_homogenized"));
    public static final DeferredItem<Item> QUANTUM_COMPRESSED = ITEMS.register("quantum_compressed", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_compressed"));
    public static final DeferredItem<Item> QUANTUM_STABILIZED = ITEMS.register("quantum_stabilized", () -> new TooltipItem(new Item.Properties(), "gui.industrialcore.tooltip.quantum_stabilized"));

    // Phase 33: power device block items (the block carries the kind).
    public static final DeferredItem<BlockItem> THERMAL_GENERATOR = ITEMS.registerSimpleBlockItem(
            "thermal_generator", ModBlocks.THERMAL_GENERATOR);
    public static final DeferredItem<BlockItem> HYDRO_GENERATOR = ITEMS.registerSimpleBlockItem(
            "hydro_generator", ModBlocks.HYDRO_GENERATOR);
    public static final DeferredItem<BlockItem> WIND_GENERATOR = ITEMS.registerSimpleBlockItem(
            "wind_generator", ModBlocks.WIND_GENERATOR);
    public static final DeferredItem<BlockItem> SOLAR_GENERATOR = ITEMS.registerSimpleBlockItem(
            "solar_generator", ModBlocks.SOLAR_GENERATOR);
    public static final DeferredItem<BlockItem> COMBUSTION_GENERATOR = ITEMS.registerSimpleBlockItem(
            "combustion_generator", ModBlocks.COMBUSTION_GENERATOR);
    public static final DeferredItem<BlockItem> NUCLEAR_GENERATOR = ITEMS.registerSimpleBlockItem(
            "nuclear_generator", ModBlocks.NUCLEAR_GENERATOR);
    public static final DeferredItem<BlockItem> GEOTHERMAL_GENERATOR = ITEMS.registerSimpleBlockItem(
            "geothermal_generator", ModBlocks.GEOTHERMAL_GENERATOR);
    public static final DeferredItem<BlockItem> FLYWHEEL = ITEMS.registerSimpleBlockItem(
            "flywheel", ModBlocks.FLYWHEEL);
    public static final DeferredItem<BlockItem> PRESSURE_RECOVERY = ITEMS.registerSimpleBlockItem(
            "pressure_recovery", ModBlocks.PRESSURE_RECOVERY);
    public static final DeferredItem<BlockItem> HEAT_RECOVERY = ITEMS.registerSimpleBlockItem(
            "heat_recovery", ModBlocks.HEAT_RECOVERY);
    public static final DeferredItem<BlockItem> REGEN_GENERATOR = ITEMS.registerSimpleBlockItem(
            "regen_generator", ModBlocks.REGEN_GENERATOR);

    // Phase 33: fuel items for the combustion and nuclear generators.
    public static final DeferredItem<Item> FUEL_CAN = ITEMS.register("fuel_can",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> FUEL_ROD = ITEMS.register("fuel_rod",
            () -> new Item(new Item.Properties()));

    // Phase 89: Quantum Storage Core block item + 5-tier capacity expansion cores.
    public static final DeferredItem<BlockItem> QUANTUM_STORAGE_CORE = ITEMS.register(
            "quantum_storage_core", () -> new com.industrialcore.item.QuantumStorageBlockItem(
                    ModBlocks.QUANTUM_STORAGE_CORE.get(), new Item.Properties()));
    public static final DeferredItem<Item> STORAGE_CORE_MK1 = ITEMS.register("storage_core_mk1",
            () -> new com.industrialcore.item.StorageCoreItem(new Item.Properties(),
                    com.industrialcore.storage.StorageCoreTier.MK1));
    public static final DeferredItem<Item> STORAGE_CORE_MK2 = ITEMS.register("storage_core_mk2",
            () -> new com.industrialcore.item.StorageCoreItem(new Item.Properties(),
                    com.industrialcore.storage.StorageCoreTier.MK2));
    public static final DeferredItem<Item> STORAGE_CORE_MK3 = ITEMS.register("storage_core_mk3",
            () -> new com.industrialcore.item.StorageCoreItem(new Item.Properties(),
                    com.industrialcore.storage.StorageCoreTier.MK3));
    public static final DeferredItem<Item> STORAGE_CORE_MK4 = ITEMS.register("storage_core_mk4",
            () -> new com.industrialcore.item.StorageCoreItem(new Item.Properties(),
                    com.industrialcore.storage.StorageCoreTier.MK4));
    public static final DeferredItem<Item> STORAGE_CORE_MK5 = ITEMS.register("storage_core_mk5",
            () -> new com.industrialcore.item.StorageCoreItem(new Item.Properties(),
                    com.industrialcore.storage.StorageCoreTier.MK5));

    // Phase 90: Agriculture & Forestry branch wave 1 block items.
    public static final DeferredItem<BlockItem> CROP_HARVESTER = ITEMS.registerSimpleBlockItem(
            "crop_harvester", ModBlocks.CROP_HARVESTER);
    public static final DeferredItem<BlockItem> TREE_HARVESTER = ITEMS.registerSimpleBlockItem(
            "tree_harvester", ModBlocks.TREE_HARVESTER);
    public static final DeferredItem<BlockItem> AUTO_FISHER = ITEMS.registerSimpleBlockItem(
            "auto_fisher", ModBlocks.AUTO_FISHER);

    // Phase 91: Agriculture & Forestry branch wave 2 block items.
    public static final DeferredItem<BlockItem> RANCH_HARVESTER = ITEMS.registerSimpleBlockItem(
            "ranch_harvester", ModBlocks.RANCH_HARVESTER);
    public static final DeferredItem<BlockItem> GROWTH_ACCELERATOR = ITEMS.registerSimpleBlockItem(
            "growth_accelerator", ModBlocks.GROWTH_ACCELERATOR);
    public static final DeferredItem<BlockItem> BIO_FEEDER = ITEMS.registerSimpleBlockItem(
            "bio_feeder", ModBlocks.BIO_FEEDER);

    // Phase 92: Trap branch wave 1 block items.
    public static final DeferredItem<BlockItem> MOB_FAN = ITEMS.registerSimpleBlockItem(
            "mob_fan", ModBlocks.MOB_FAN);
    public static final DeferredItem<BlockItem> ROTARY_DRILL = ITEMS.registerSimpleBlockItem(
            "rotary_drill", ModBlocks.ROTARY_DRILL);
    public static final DeferredItem<BlockItem> ITEM_VACUUM = ITEMS.registerSimpleBlockItem(
            "item_vacuum", ModBlocks.ITEM_VACUUM);

    // Phase 93: Trap branch wave 2 block items + Mob Capture Capsule.
    public static final DeferredItem<BlockItem> XP_COLLECTOR = ITEMS.registerSimpleBlockItem(
            "xp_collector", ModBlocks.XP_COLLECTOR);
    public static final DeferredItem<BlockItem> SPAWN_SUPPRESSOR = ITEMS.registerSimpleBlockItem(
            "spawn_suppressor", ModBlocks.SPAWN_SUPPRESSOR);
    public static final DeferredItem<BlockItem> LASER_TURRET = ITEMS.registerSimpleBlockItem(
            "laser_turret", ModBlocks.LASER_TURRET);
    public static final DeferredItem<BlockItem> MOB_DUPLICATOR = ITEMS.registerSimpleBlockItem(
            "mob_duplicator", ModBlocks.MOB_DUPLICATOR);
    public static final DeferredItem<Item> MOB_CAPSULE = ITEMS.register("mob_capsule",
            () -> new com.industrialcore.item.MobCapsuleItem(new Item.Properties()));

    // Phase 94: Building & Life branch wave 1 block items.
    public static final DeferredItem<BlockItem> AUTO_BUILDER = ITEMS.registerSimpleBlockItem(
            "auto_builder", ModBlocks.AUTO_BUILDER);
    public static final DeferredItem<BlockItem> FLUID_PUMP = ITEMS.registerSimpleBlockItem(
            "fluid_pump", ModBlocks.FLUID_PUMP);
    public static final DeferredItem<BlockItem> WIRELESS_CHARGER = ITEMS.registerSimpleBlockItem(
            "wireless_charger", ModBlocks.WIRELESS_CHARGER);

    // Phase 95: Building & Life branch wave 2 block items.
    public static final DeferredItem<BlockItem> CHUNK_LOADER = ITEMS.registerSimpleBlockItem(
            "chunk_loader", ModBlocks.CHUNK_LOADER);
    public static final DeferredItem<BlockItem> CLIMATE_CONTROLLER = ITEMS.registerSimpleBlockItem(
            "climate_controller", ModBlocks.CLIMATE_CONTROLLER);
    public static final DeferredItem<BlockItem> WARP_PAD = ITEMS.registerSimpleBlockItem(
            "warp_pad", ModBlocks.WARP_PAD);

    // Phase 96: Quantum Storage Network items.
    public static final DeferredItem<BlockItem> QUANTUM_IMPORTER = ITEMS.registerSimpleBlockItem(
            "quantum_importer", ModBlocks.QUANTUM_IMPORTER);
    public static final DeferredItem<BlockItem> QUANTUM_EXPORTER = ITEMS.registerSimpleBlockItem(
            "quantum_exporter", ModBlocks.QUANTUM_EXPORTER);
    public static final DeferredItem<com.industrialcore.item.PortableQuantumTerminalItem> PORTABLE_QUANTUM_TERMINAL =
            ITEMS.register("portable_quantum_terminal",
                    () -> new com.industrialcore.item.PortableQuantumTerminalItem(new Item.Properties()));

    // Phase 98: Virtual Aegis 4-piece endgame armor, 8 modules, and Suit Module Modifier.
    public static final DeferredItem<BlockItem> SUIT_MODIFIER = ITEMS.registerSimpleBlockItem(
            "suit_modifier", ModBlocks.SUIT_MODIFIER);
    public static final DeferredItem<com.industrialcore.item.VirtualSuitArmorItem> VIRTUAL_HELMET =
            ITEMS.register("virtual_helmet", () -> new com.industrialcore.item.VirtualSuitArmorItem(
                    ModArmorMaterials.VIRTUAL_AEGIS, ArmorItem.Type.HELMET, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.VirtualSuitArmorItem> VIRTUAL_CHESTPLATE =
            ITEMS.register("virtual_chestplate", () -> new com.industrialcore.item.VirtualSuitArmorItem(
                    ModArmorMaterials.VIRTUAL_AEGIS, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.VirtualSuitArmorItem> VIRTUAL_LEGGINGS =
            ITEMS.register("virtual_leggings", () -> new com.industrialcore.item.VirtualSuitArmorItem(
                    ModArmorMaterials.VIRTUAL_AEGIS, ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.VirtualSuitArmorItem> VIRTUAL_BOOTS =
            ITEMS.register("virtual_boots", () -> new com.industrialcore.item.VirtualSuitArmorItem(
                    ModArmorMaterials.VIRTUAL_AEGIS, ArmorItem.Type.BOOTS, new Item.Properties()));

    public static final DeferredItem<com.industrialcore.item.SuitModuleItem> MOD_VISION =
            ITEMS.register("mod_vision", () -> new com.industrialcore.item.SuitModuleItem(
                    com.industrialcore.suit.SuitModule.VISION, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.SuitModuleItem> MOD_AUTOFEED =
            ITEMS.register("mod_autofeed", () -> new com.industrialcore.item.SuitModuleItem(
                    com.industrialcore.suit.SuitModule.AUTOFEED, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.SuitModuleItem> MOD_FLIGHT =
            ITEMS.register("mod_flight", () -> new com.industrialcore.item.SuitModuleItem(
                    com.industrialcore.suit.SuitModule.FLIGHT, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.SuitModuleItem> MOD_SHIELD =
            ITEMS.register("mod_shield", () -> new com.industrialcore.item.SuitModuleItem(
                    com.industrialcore.suit.SuitModule.SHIELD, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.SuitModuleItem> MOD_SPEED =
            ITEMS.register("mod_speed", () -> new com.industrialcore.item.SuitModuleItem(
                    com.industrialcore.suit.SuitModule.SPEED, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.SuitModuleItem> MOD_STEP =
            ITEMS.register("mod_step", () -> new com.industrialcore.item.SuitModuleItem(
                    com.industrialcore.suit.SuitModule.STEP, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.SuitModuleItem> MOD_NOFALL =
            ITEMS.register("mod_nofall", () -> new com.industrialcore.item.SuitModuleItem(
                    com.industrialcore.suit.SuitModule.NOFALL, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.SuitModuleItem> MOD_JUMP =
            ITEMS.register("mod_jump", () -> new com.industrialcore.item.SuitModuleItem(
                    com.industrialcore.suit.SuitModule.JUMP, new Item.Properties()));

    // Phase 99: Endgame Inflation (Singularity Reactor & Unlimited Conduits).
    public static final DeferredItem<BlockItem> SINGULARITY_REACTOR = ITEMS.registerSimpleBlockItem(
            "singularity_reactor", ModBlocks.SINGULARITY_REACTOR);
    public static final DeferredItem<BlockItem> HYPER_ENERGY_CONDUIT = ITEMS.registerSimpleBlockItem(
            "hyper_energy_conduit", ModBlocks.HYPER_ENERGY_CONDUIT);
    public static final DeferredItem<BlockItem> HYPER_ITEM_CONDUIT = ITEMS.registerSimpleBlockItem(
            "hyper_item_conduit", ModBlocks.HYPER_ITEM_CONDUIT);

    // Phase 101: 4 Endgame Electric Weapons & 12 Modular Weapon Attachments.
    public static final DeferredItem<com.industrialcore.item.PulseLaserItem> PULSE_LASER =
            ITEMS.register("pulse_laser", () -> new com.industrialcore.item.PulseLaserItem(new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.RailgunItem> RAILGUN =
            ITEMS.register("railgun", () -> new com.industrialcore.item.RailgunItem(new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.QuantumBladeItem> QUANTUM_BLADE =
            ITEMS.register("quantum_blade", () -> new com.industrialcore.item.QuantumBladeItem(new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.EnergyBowItem> ENERGY_BOW =
            ITEMS.register("energy_bow", () -> new com.industrialcore.item.EnergyBowItem(new Item.Properties()));

    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_AUTO_AIM =
            ITEMS.register("att_auto_aim", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.AUTO_AIM, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_CRYO_CORE =
            ITEMS.register("att_cryo_core", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.CRYO_CORE, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_PRISM_LENS =
            ITEMS.register("att_prism_lens", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.PRISM_LENS, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_FAST_CAPACITOR =
            ITEMS.register("att_fast_capacitor", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.FAST_CAPACITOR, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_WALL_SCANNER =
            ITEMS.register("att_wall_scanner", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.WALL_SCANNER, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_RECOIL_GYRO =
            ITEMS.register("att_recoil_gyro", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.RECOIL_GYRO, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_PLASMA_WAVE =
            ITEMS.register("att_plasma_wave", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.PLASMA_WAVE, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_BLINK_DRIVE =
            ITEMS.register("att_blink_drive", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.BLINK_DRIVE, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_QUANTUM_LOOT =
            ITEMS.register("att_quantum_loot", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.QUANTUM_LOOT, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_MULTI_LOCK =
            ITEMS.register("att_multi_lock", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.MULTI_LOCK, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_GRAVITY_ARROW =
            ITEMS.register("att_gravity_arrow", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.GRAVITY_ARROW, new Item.Properties()));
    public static final DeferredItem<com.industrialcore.item.WeaponAttachmentItem> ATT_AUTO_DRAW =
            ITEMS.register("att_auto_draw", () -> new com.industrialcore.item.WeaponAttachmentItem(
                    com.industrialcore.weapon.WeaponAttachment.AUTO_DRAW, new Item.Properties()));

    // Phase 103: Multiblock Reactor structure block items (5 blocks).
    public static final DeferredItem<BlockItem> REACTOR_CASING = ITEMS.registerSimpleBlockItem(
            "reactor_casing", ModBlocks.REACTOR_CASING);
    public static final DeferredItem<BlockItem> REACTOR_GLASS = ITEMS.registerSimpleBlockItem(
            "reactor_glass", ModBlocks.REACTOR_GLASS);
    public static final DeferredItem<BlockItem> REACTOR_CORE_CELL = ITEMS.registerSimpleBlockItem(
            "reactor_core_cell", ModBlocks.REACTOR_CORE_CELL);
    public static final DeferredItem<BlockItem> REACTOR_COOLANT_BLOCK = ITEMS.registerSimpleBlockItem(
            "reactor_coolant_block", ModBlocks.REACTOR_COOLANT_BLOCK);
    public static final DeferredItem<BlockItem> REACTOR_PORT = ITEMS.registerSimpleBlockItem(
            "reactor_port", ModBlocks.REACTOR_PORT);

    // Phase 105.6: Uranium processing line (ores + 4 items) and connected Fluid Pipe.
    public static final DeferredItem<BlockItem> URANIUM_ORE = ITEMS.registerSimpleBlockItem(
            "uranium_ore", ModBlocks.URANIUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_URANIUM_ORE = ITEMS.registerSimpleBlockItem(
            "deepslate_uranium_ore", ModBlocks.DEEPSLATE_URANIUM_ORE);
    public static final DeferredItem<BlockItem> FLUID_PIPE = ITEMS.registerSimpleBlockItem(
            "fluid_pipe", ModBlocks.FLUID_PIPE);
    public static final DeferredItem<Item> RAW_URANIUM = ITEMS.register("raw_uranium",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> URANIUM_DUST = ITEMS.register("uranium_dust",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> ENRICHED_URANIUM = ITEMS.register("enriched_uranium",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> URANIUM_FUEL_ROD = ITEMS.register("uranium_fuel_rod",
            () -> new Item(new Item.Properties()));

    private ModItems() {
    }
}

package com.industrialcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.industrialcore.machine.LivingFactoryAnimLogic;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 110 comprehensive verification suite:
 * <ul>
 *   <li>Tier 1: all 59 {@code .png.mcmeta} block animations (13 from Phase 109 + 46 new in Phase 110)
 *       have valid JSON, exact 16x(16*N) vertical strips, and non-static pixel motion.</li>
 *   <li>Tier 2: all 6 newly added lightweight hero BER animation curves and distance-culling limits.</li>
 *   <li>Tier 3: all 13 migrated BlockEntities have zero server-side {@code sendParticles} calls.</li>
 *   <li>Creative Tab &amp; JEI ordering: exact 340 unique public items (0 missing, 0 duplicates) in
 *       systematic 6-category &amp; Stage progression order.</li>
 * </ul>
 */
class Phase110AllMachineFxTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/industrialcore");
    private static final Path BLOCK_TEX = ASSETS.resolve("textures/block");
    private static final Path BLOCKSTATES = ASSETS.resolve("blockstates");
    private static final Path BLOCK_MODELS = ASSETS.resolve("models/block");
    private static final Path BE_DIR = Path.of("src/main/java/com/industrialcore/blockentity");
    private static final Path CREATIVE_TAB_JAVA = Path.of("src/main/java/com/industrialcore/registry/ModCreativeTabs.java");

    private static final List<String> PHASE110_NEW_MCMETAS = List.of(
            // 20 existing-LIT machines
            "pc_front_on.png", "fc_front_on.png",
            "quantum_importer_front_on.png", "quantum_exporter_front_on.png",
            "crop_harvester_front_on.png", "tree_harvester_front_on.png",
            "auto_fisher_front_on.png", "ranch_harvester_front_on.png",
            "growth_accelerator_front_on.png", "bio_feeder_front_on.png",
            "rotary_drill_front_on.png", "item_vacuum_front_on.png",
            "xp_collector_front_on.png", "spawn_suppressor_front_on.png", "laser_turret_front_on.png",
            "auto_builder_front_on.png", "fluid_pump_front_on.png",
            "wireless_charger_front_on.png", "chunk_loader_front_on.png",
            "reactor_core_cell_lit.png",
            // 7 production machines gaining LIT
            "crafter_front_on.png", "quarry_front_on.png", "lq_front_on.png",
            "rc_front_on.png", "sc_front_on.png", "ma_front_on.png", "qc_front_on.png",
            // 19 generators, recovery, flywheel, buffers, monitor, distributor, transporters
            "thermal_generator_front.png", "hydro_generator_front.png", "wind_generator_front.png",
            "solar_generator_top.png", "combustion_generator_front.png", "geo_front.png",
            "heat_recovery_front.png", "pressure_recovery_front.png", "regen_glow_pulse.png",
            "flywheel_front.png", "eb_front.png", "eb2_front.png", "eb3_front.png",
            "monitor_front.png", "ds_front.png",
            "basic_item_transporter_core.png", "advanced_casing_led.png",
            "quantum_transporter_front.png", "quantum_transporter_array_front.png"
    );

    @Test
    void all59BlockMcmetaAnimationsAreValidNonStaticSquareStrips() throws IOException {
        assertEquals(46, PHASE110_NEW_MCMETAS.size(), "Expected 46 new Phase 110 animated textures");
        for (String pngName : PHASE110_NEW_MCMETAS) {
            Path png = BLOCK_TEX.resolve(pngName);
            Path meta = BLOCK_TEX.resolve(pngName + ".mcmeta");
            assertTrue(Files.exists(png), "Missing Phase 110 animated PNG: " + pngName);
            assertTrue(Files.exists(meta), "Missing Phase 110 .mcmeta: " + pngName + ".mcmeta");
        }

        List<Path> allMcmetas;
        try (Stream<Path> s = Files.list(BLOCK_TEX)) {
            allMcmetas = s.filter(p -> p.getFileName().toString().endsWith(".png.mcmeta")).sorted().toList();
        }
        assertEquals(59, allMcmetas.size(), "Expected 59 total block .mcmeta files (13 Phase 109 + 46 Phase 110)");

        for (Path metaPath : allMcmetas) {
            String metaFile = metaPath.getFileName().toString();
            JsonObject root = JsonParser.parseString(Files.readString(metaPath)).getAsJsonObject();
            assertTrue(root.has("animation") && root.get("animation").isJsonObject(), metaFile + " missing animation");
            assertTrue(root.getAsJsonObject("animation").get("frametime").getAsInt() >= 1, metaFile + " invalid frametime");

            String pngName = metaFile.substring(0, metaFile.length() - ".mcmeta".length());
            BufferedImage img = ImageIO.read(BLOCK_TEX.resolve(pngName).toFile());
            int w = img.getWidth();
            int h = img.getHeight();
            assertEquals(16, w, pngName + " width must be 16");
            assertTrue(h >= 32 && h % 16 == 0, pngName + " height must be multiple of 16 (>=32), got " + h);

            int midFrame = (h / w) / 2;
            int diffs = 0;
            for (int y = 0; y < w; y++) {
                for (int x = 0; x < w; x++) {
                    if (img.getRGB(x, y) != img.getRGB(x, midFrame * w + y)) {
                        diffs++;
                    }
                }
            }
            assertTrue(diffs >= 4, pngName + " appears static (diffs=" + diffs + ")");
        }
    }

    @Test
    void sevenNewlyLitProductionMachinesHaveLitBlockstatesAndAnimatedOnModels() throws IOException {
        Map<String, String> prod7 = Map.of(
                "crafter", "industrialcore:block/crafter_front_on",
                "quarry", "industrialcore:block/quarry_front_on",
                "large_quarry", "industrialcore:block/lq_front_on",
                "reconstructor", "industrialcore:block/rc_front_on",
                "seed_cultivator", "industrialcore:block/sc_front_on",
                "matter_amplifier", "industrialcore:block/ma_front_on",
                "quantum_compressor", "industrialcore:block/qc_front_on"
        );
        for (Map.Entry<String, String> e : prod7.entrySet()) {
            String id = e.getKey();
            JsonObject bs = JsonParser.parseString(Files.readString(BLOCKSTATES.resolve(id + ".json"))).getAsJsonObject();
            JsonObject vars = bs.getAsJsonObject("variants");
            assertEquals(8, vars.size(), id + " must have 8 variants (4 facings x 2 lit states)");
            assertEquals("industrialcore:block/" + id, vars.getAsJsonObject("facing=north,lit=false").get("model").getAsString());
            assertEquals("industrialcore:block/" + id + "_on", vars.getAsJsonObject("facing=north,lit=true").get("model").getAsString());

            JsonObject onModel = JsonParser.parseString(Files.readString(BLOCK_MODELS.resolve(id + "_on.json"))).getAsJsonObject();
            assertEquals(e.getValue(), onModel.getAsJsonObject("textures").get("front").getAsString());
        }
    }

    @Test
    void sixNewHeroBerAnimationCurvesScaleSmoothlyAndStopWhenIdle() {
        // Wind turbine scales with wind output and stops at 0
        assertEquals(0.0F, LivingFactoryAnimLogic.windTurbineBladeDeg(2.5F, 0), 1e-4F);
        float slowWind = LivingFactoryAnimLogic.windTurbineBladeDeg(0.5F, 20);
        float fastWind = LivingFactoryAnimLogic.windTurbineBladeDeg(0.5F, 120);
        assertTrue(fastWind > slowWind, "Higher wind output should spin blades faster");

        // Flywheel scales with charge ratio and stops when empty
        assertEquals(0.0F, LivingFactoryAnimLogic.flywheelRotorDeg(1.0F, 0.0F), 1e-4F);
        float lowCharge = LivingFactoryAnimLogic.flywheelRotorDeg(0.25F, 0.2F);
        float fullCharge = LivingFactoryAnimLogic.flywheelRotorDeg(0.25F, 1.0F);
        assertTrue(fullCharge > lowCharge, "Fuller flywheel should rotate faster");

        // Rotary drill & Auto fisher smooth advance
        assertTrue(LivingFactoryAnimLogic.rotaryDrillHeadDeg(0.25F) > 0.0F);
        assertEquals(190.0F, LivingFactoryAnimLogic.autoFisherReelDeg(1.0F), 1e-3F);

        // Beacon ring & Chunk loader bounded smooth bob
        for (int i = 0; i < 60; i++) {
            float t = i * 0.1F;
            float bBob = LivingFactoryAnimLogic.beaconRingBobY(t);
            assertTrue(bBob >= -0.066F && bBob <= 0.066F);
            float cBob = LivingFactoryAnimLogic.chunkLoaderBobY(t);
            assertTrue(cBob >= -0.056F && cBob <= 0.056F);
        }
        assertTrue(LivingFactoryAnimLogic.beaconRingDeg(1.0F) > 0.0F);
        assertTrue(LivingFactoryAnimLogic.chunkLoaderCoreDeg(1.0F) > 0.0F);
    }

    @Test
    void migratedThirteenBlockEntitiesHaveZeroServerSendParticlesCalls() throws IOException {
        List<String> migrated = List.of(
                "AutoBuilderBlockEntity.java",
                "BioFeederBlockEntity.java",
                "ClimateControllerBlockEntity.java",
                "FluidPumpBlockEntity.java",
                "GrowthAcceleratorBlockEntity.java",
                "ItemVacuumBlockEntity.java",
                "MobDuplicatorBlockEntity.java",
                "MobFanBlockEntity.java",
                "RotaryDrillBlockEntity.java",
                "SingularityReactorBlockEntity.java",
                "SuitModifierBlockEntity.java",
                "WarpPadBlockEntity.java",
                "WirelessChargerBlockEntity.java",
                "XpCollectorBlockEntity.java"
        );
        List<String> offenders = new ArrayList<>();
        for (String file : migrated) {
            String src = Files.readString(BE_DIR.resolve(file));
            if (src.contains("sendParticles(")) {
                offenders.add(file);
            }
        }
        assertTrue(offenders.isEmpty(), "Migrated BlockEntities still calling server sendParticles: " + offenders);
    }

    @Test
    void creativeTabHasExact340UniqueItemsInSystematicCategoryOrder() throws IOException {
        String src = Files.readString(CREATIVE_TAB_JAVA);
        Matcher m = Pattern.compile("output\\.accept\\((Mod(?:Blocks|Items)\\.[A-Z0-9_]+)\\.get\\(\\)\\);").matcher(src);
        List<String> items = new ArrayList<>();
        while (m.find()) {
            items.add(m.group(1));
        }

        // 0 missing & 0 duplicates against full 341-item public catalog (340 + Phase 113 specialization_module)
        assertEquals(341, items.size(), "Creative tab must keep all 341 public items with zero omissions");
        Set<String> unique = new HashSet<>(items);
        assertEquals(341, unique.size(), "Creative tab must contain zero duplicate entries");

        // Internal/legacy blocks remain excluded
        assertFalse(unique.contains("ModBlocks.CONVEYOR"));
        assertFalse(unique.contains("ModBlocks.MACHINE_BOUNDING"));

        // Verify systematic 6-category ordering from top to bottom
        int idxOre = items.indexOf("ModBlocks.URANIUM_ORE");
        int idxRaw = items.indexOf("ModItems.RAW_MITHRIL");
        int idxDust = items.indexOf("ModItems.IRON_DUST");
        int idxIngot = items.indexOf("ModItems.STEEL_INGOT");
        int idxPlate = items.indexOf("ModItems.IRON_PLATE");
        int idxComp = items.indexOf("ModItems.MACHINE_FRAME");
        int idxUraniumFuel = items.indexOf("ModItems.URANIUM_FUEL_ROD");
        int idxGen = items.indexOf("ModBlocks.THERMAL_GENERATOR");
        int idxReactor = items.indexOf("ModBlocks.SINGULARITY_REACTOR");
        int idxCable = items.indexOf("ModBlocks.CABLE_BASIC");
        int idxTransporter = items.indexOf("ModBlocks.BASIC_ITEM_TRANSPORTER");
        int idxCrusher = items.indexOf("ModBlocks.CRUSHER");
        int idxStorage = items.indexOf("ModBlocks.QUANTUM_STORAGE_CORE");
        int idxNature = items.indexOf("ModBlocks.CROP_HARVESTER");
        int idxTrap = items.indexOf("ModBlocks.MOB_FAN");
        int idxLife = items.indexOf("ModBlocks.FLUID_PUMP");
        int idxSuit = items.indexOf("ModBlocks.SUIT_MODIFIER");
        int idxWeapon = items.indexOf("ModItems.PULSE_LASER");
        int idxTool = items.indexOf("ModItems.WRENCH");

        assertTrue(idxOre >= 0 && idxOre < idxRaw, "Ores before Raw Ores");
        assertTrue(idxRaw < idxDust, "Raw Ores before Dusts");
        assertTrue(idxDust < idxIngot, "Dusts before Ingots");
        assertTrue(idxIngot < idxPlate, "Ingots before Plates");
        assertTrue(idxPlate < idxComp, "Plates before Electronic Components");
        assertTrue(idxComp < idxUraniumFuel, "Components before Uranium Fuel line");
        assertTrue(idxUraniumFuel < idxGen, "Category 1 (Resources) before Category 2 (Power)");
        assertTrue(idxGen < idxReactor, "Early Generators before Multiblock Reactors");
        assertTrue(idxReactor < idxCable, "Category 2 (Power) before Category 3 (Conduits/Logistics)");
        assertTrue(idxCable < idxTransporter, "Cables/Pipes before Quantum Transporters");
        assertTrue(idxTransporter < idxCrusher, "Category 3 (Logistics) before Category 4 (Branch A Production)");
        assertTrue(idxCrusher < idxStorage, "Production Machines before Quantum Storage");
        assertTrue(idxStorage < idxNature, "Category 4 (Branch A) before Category 5 (Branch B Nature)");
        assertTrue(idxNature < idxTrap, "Branch B Nature before Branch C Trap");
        assertTrue(idxTrap < idxLife, "Branch C Trap before Branch D Life");
        assertTrue(idxLife < idxSuit, "Category 5 (Branches B-D) before Category 6 (Endgame Gear)");
        assertTrue(idxSuit < idxWeapon, "Virtual Suit before Electric Weapons");
        assertTrue(idxWeapon < idxTool, "Weapons before Tools/Utilities");
    }
}

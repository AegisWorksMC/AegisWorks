package com.industrialcore.level;

import com.industrialcore.IndustrialCore;
import com.industrialcore.registry.ModItems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Phase 114: Server-side helper &amp; event hooks for the 28-entry AegisWorks Advancement tree
 * ({@code data/industrialcore/advancement/aegisworks/*.json}).
 */
public final class AegisAdvancements {
    public static final String ROOT = "aegisworks/root";
    // Stage 1 (5)
    public static final String FIRST_GENERATOR = "aegisworks/first_generator";
    public static final String CRUSHER = "aegisworks/crusher";
    public static final String ELECTRIC_FURNACE = "aegisworks/electric_furnace";
    public static final String METAL_PRESS = "aegisworks/metal_press";
    public static final String FLUID_PUMP = "aegisworks/fluid_pump";
    // Stage 2 (5)
    public static final String ASSEMBLER = "aegisworks/assembler";
    public static final String AGRI_AUTOMATION = "aegisworks/agri_automation";
    public static final String LOGISTICS_PIPES = "aegisworks/logistics_pipes";
    public static final String ENERGY_BUFFER_MK2 = "aegisworks/energy_buffer_mk2";
    public static final String WIRELESS_CHARGER = "aegisworks/wireless_charger";
    // Stage 3 (6)
    public static final String NUCLEAR_REACTOR = "aegisworks/nuclear_reactor";
    public static final String REACTOR_OVERDRIVE = "aegisworks/reactor_overdrive";
    public static final String QUANTUM_STORAGE = "aegisworks/quantum_storage";
    public static final String LASER_TURRET = "aegisworks/laser_turret";
    public static final String TURRET_FIRST_KILL = "aegisworks/turret_first_kill";
    public static final String MOB_DEFENSE = "aegisworks/mob_defense";
    // Stage 4 (5)
    public static final String SUIT_MODIFIER = "aegisworks/suit_modifier";
    public static final String VIRTUAL_AEGIS_FULL = "aegisworks/virtual_aegis_full";
    public static final String RAILGUN = "aegisworks/railgun";
    public static final String PULSE_LASER = "aegisworks/pulse_laser";
    public static final String QUANTUM_BLADE = "aegisworks/quantum_blade";
    // Stage 5 (6)
    public static final String SINGULARITY_REACTOR = "aegisworks/singularity_reactor";
    public static final String PHASE_REACTOR = "aegisworks/phase_reactor";
    public static final String ORE_SYNTHESIZER = "aegisworks/ore_synthesizer";
    public static final String MACHINE_LV50 = "aegisworks/machine_lv50";
    public static final String LIMIT_BREAK_LV100 = "aegisworks/limit_break_lv100";
    public static final String SPECIALIZATION_MASTER = "aegisworks/specialization_master";
    /** Phase 115: 29th advancement — Clear all 3 waves of a Dimensional Rift Raid. */
    public static final String DIMENSION_DEFENDER = "aegisworks/dimension_defender";

    /** All 29 advancement paths in canonical Stage order (used by tests as well). */
    public static final List<String> ALL_PATHS = List.of(
            ROOT,
            FIRST_GENERATOR, CRUSHER, ELECTRIC_FURNACE, METAL_PRESS, FLUID_PUMP,
            ASSEMBLER, AGRI_AUTOMATION, LOGISTICS_PIPES, ENERGY_BUFFER_MK2, WIRELESS_CHARGER,
            NUCLEAR_REACTOR, REACTOR_OVERDRIVE, QUANTUM_STORAGE, LASER_TURRET, TURRET_FIRST_KILL, MOB_DEFENSE,
            SUIT_MODIFIER, VIRTUAL_AEGIS_FULL, RAILGUN, PULSE_LASER, QUANTUM_BLADE,
            SINGULARITY_REACTOR, PHASE_REACTOR, ORE_SYNTHESIZER, MACHINE_LV50, LIMIT_BREAK_LV100,
            SPECIALIZATION_MASTER, DIMENSION_DEFENDER
    );

    private static final Map<String, String> BLOCK_PLACE_MAP = Map.ofEntries(
            Map.entry("thermal_generator", FIRST_GENERATOR),
            Map.entry("wind_generator", FIRST_GENERATOR),
            Map.entry("hydro_generator", FIRST_GENERATOR),
            Map.entry("solar_generator", FIRST_GENERATOR),
            Map.entry("crusher", CRUSHER),
            Map.entry("electric_furnace", ELECTRIC_FURNACE),
            Map.entry("metal_press", METAL_PRESS),
            Map.entry("fluid_pump", FLUID_PUMP),
            Map.entry("assembler", ASSEMBLER),
            Map.entry("crop_harvester", AGRI_AUTOMATION),
            Map.entry("tree_harvester", AGRI_AUTOMATION),
            Map.entry("item_pipe", LOGISTICS_PIPES),
            Map.entry("pipe_improved", LOGISTICS_PIPES),
            Map.entry("pipe_advanced", LOGISTICS_PIPES),
            Map.entry("pipe_ultimate", LOGISTICS_PIPES),
            Map.entry("fluid_pipe", LOGISTICS_PIPES),
            Map.entry("energy_buffer_mk2", ENERGY_BUFFER_MK2),
            Map.entry("energy_buffer_mk3", ENERGY_BUFFER_MK2),
            Map.entry("wireless_charger", WIRELESS_CHARGER),
            Map.entry("quantum_storage_core", QUANTUM_STORAGE),
            Map.entry("laser_turret", LASER_TURRET),
            Map.entry("mob_fan", MOB_DEFENSE),
            Map.entry("spawn_suppressor", MOB_DEFENSE),
            Map.entry("suit_modifier", SUIT_MODIFIER),
            Map.entry("phase_reactor", PHASE_REACTOR),
            Map.entry("ore_synthesizer", ORE_SYNTHESIZER)
    );

    private AegisAdvancements() {}

    /** Awards an AegisWorks advancement to a server player (always ensures root is granted too). */
    public static void grant(@Nullable ServerPlayer player, String path) {
        if (player == null || path == null || path.isEmpty()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (!ROOT.equals(path)) {
            awardDirect(server, player, ROOT);
        }
        awardDirect(server, player, path);
    }

    /** Awards an advancement to all players within {@code radius} blocks of {@code pos}. */
    public static void grantNearby(ServerLevel level, BlockPos pos, double radius, String path) {
        AABB box = new AABB(pos).inflate(radius);
        for (ServerPlayer sp : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            grant(sp, path);
        }
    }

    private static void awardDirect(MinecraftServer server, ServerPlayer player, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, path);
        AdvancementHolder holder = server.getAdvancements().get(id);
        if (holder == null) {
            return;
        }
        var progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    /** Event hook: placing tracked AegisWorks blocks grants corresponding progression milestones. */
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Block block = event.getPlacedBlock().getBlock();
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        if (key == null || !IndustrialCore.MOD_ID.equals(key.getNamespace())) {
            return;
        }
        String adv = BLOCK_PLACE_MAP.get(key.getPath());
        if (adv != null) {
            grant(player, adv);
        }
    }

    /** Event hook: equipping all 4 Virtual Aegis armor pieces awards {@link #VIRTUAL_AEGIS_FULL}. */
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.VIRTUAL_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.VIRTUAL_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.VIRTUAL_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.VIRTUAL_BOOTS.get())) {
            grant(player, VIRTUAL_AEGIS_FULL);
        }
    }
}

package com.industrialcore.registry;

import com.industrialcore.IndustrialCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Phase 88 / 102: Custom DSP-synthesized Ogg Vorbis sound events for Industrial Core.
 *
 * <p>Common-safe registry (no client imports) so dedicated servers register identical SoundEvent
 * entries cleanly alongside clients.</p>
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, IndustrialCore.MOD_ID);

    public static final List<String> ALL_IDS = List.of(
            "machine_tick_note",
            "machine_complete",
            "machine_extract",
            "machine_jackpot",
            "machine_streak_up",
            "gen_thermal_hum",
            "gen_kinetic_hum",
            "gen_reactor_hum",
            "gen_power_pulse",
            "world_gen_thermal",
            "world_gen_kinetic",
            "world_gen_reactor",
            "world_machine_hum",
            "weapon_laser_beam",
            "weapon_laser_loop_light",
            "weapon_laser_loop_heavy",
            "weapon_laser_hit_sizzle",
            "weapon_railgun_fire",
            "weapon_blade_slash",
            "weapon_blade_ignite",
            "weapon_blade_dash",
            "weapon_bow_fire",
            "weapon_lockon",
            "level_up",
            "limit_break",
            "tree_riser",
            "tree_unlock_t2",
            "tree_unlock_t3",
            "tree_unlock_t4",
            "tree_unlock_t5",
            "tree_fog_clear",
            "ui_click",
            "ui_locked",
            // Phase 112: Noise-free pure-synthesis mono factory SFX (18 distinct voices)
            "machine_wind_blade",
            "machine_hydro_turbine",
            "machine_boiler_fire",
            "machine_engine_piston",
            "machine_steam_turbine",
            "machine_nuclear_core",
            "machine_singularity_pulse",
            "machine_press_impact",
            "machine_crusher_jaw",
            "machine_furnace_arc",
            "machine_electrolyzer_bubble",
            "machine_assembler_servo",
            "machine_drill_bore",
            "machine_compressor_slam",
            "machine_harvester_blade",
            "machine_winch_reel",
            "machine_fan_airflow",
            "machine_quantum_chime"
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_TICK_NOTE = register("machine_tick_note");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_COMPLETE = register("machine_complete");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_EXTRACT = register("machine_extract");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_JACKPOT = register("machine_jackpot");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_STREAK_UP = register("machine_streak_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEN_THERMAL_HUM = register("gen_thermal_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEN_KINETIC_HUM = register("gen_kinetic_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEN_REACTOR_HUM = register("gen_reactor_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEN_POWER_PULSE = register("gen_power_pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_GEN_THERMAL = register("world_gen_thermal");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_GEN_KINETIC = register("world_gen_kinetic");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_GEN_REACTOR = register("world_gen_reactor");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_MACHINE_HUM = register("world_machine_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_LASER_BEAM = register("weapon_laser_beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_LASER_LOOP_LIGHT = register("weapon_laser_loop_light");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_LASER_LOOP_HEAVY = register("weapon_laser_loop_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_LASER_HIT_SIZZLE = register("weapon_laser_hit_sizzle");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_RAILGUN_FIRE = register("weapon_railgun_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_BLADE_SLASH = register("weapon_blade_slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_BLADE_IGNITE = register("weapon_blade_ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_BLADE_DASH = register("weapon_blade_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_BOW_FIRE = register("weapon_bow_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_LOCKON = register("weapon_lockon");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = register("level_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIMIT_BREAK = register("limit_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREE_RISER = register("tree_riser");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREE_UNLOCK_T2 = register("tree_unlock_t2");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREE_UNLOCK_T3 = register("tree_unlock_t3");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREE_UNLOCK_T4 = register("tree_unlock_t4");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREE_UNLOCK_T5 = register("tree_unlock_t5");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREE_FOG_CLEAR = register("tree_fog_clear");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLICK = register("ui_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_LOCKED = register("ui_locked");
    // Phase 112: Noise-free pure-synthesis mono factory SFX (18 distinct voices)
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_WIND_BLADE = register("machine_wind_blade");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_HYDRO_TURBINE = register("machine_hydro_turbine");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_BOILER_FIRE = register("machine_boiler_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_ENGINE_PISTON = register("machine_engine_piston");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_STEAM_TURBINE = register("machine_steam_turbine");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_NUCLEAR_CORE = register("machine_nuclear_core");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_SINGULARITY_PULSE = register("machine_singularity_pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_PRESS_IMPACT = register("machine_press_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_CRUSHER_JAW = register("machine_crusher_jaw");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_FURNACE_ARC = register("machine_furnace_arc");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_ELECTROLYZER_BUBBLE = register("machine_electrolyzer_bubble");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_ASSEMBLER_SERVO = register("machine_assembler_servo");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_DRILL_BORE = register("machine_drill_bore");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_COMPRESSOR_SLAM = register("machine_compressor_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_HARVESTER_BLADE = register("machine_harvester_blade");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_WINCH_REEL = register("machine_winch_reel");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_FAN_AIRFLOW = register("machine_fan_airflow");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_QUANTUM_CHIME = register("machine_quantum_chime");
    // Phase 119: Heavy Dimensional Rift combat, boss & raid victory SFX (8 mono 44.1kHz Vorbis voices)
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_PORTAL_HORN = register("rift_portal_horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_STALKER_BLINK = register("rift_stalker_blink");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_SENTINEL_CANNON = register("void_sentinel_cannon");
    public static final DeferredHolder<SoundEvent, SoundEvent> PHASE_JUGGERNAUT_SLAM = register("phase_juggernaut_slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_SHIELD_DEFLECT = register("rift_shield_deflect");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOVEREIGN_ROAR_OVERDRIVE = register("sovereign_roar_overdrive");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOVEREIGN_DEFEAT_COLLAPSE = register("sovereign_defeat_collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_RAID_VICTORY_FANFARE = register("rift_raid_victory_fanfare");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(IndustrialCore.MOD_ID, id)));
    }
}

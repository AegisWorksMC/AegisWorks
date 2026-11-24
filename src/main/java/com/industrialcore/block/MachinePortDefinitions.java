package com.industrialcore.block;

import com.industrialcore.machine.port.PortLayout;
import com.industrialcore.machine.port.PortTables;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers the {@link PortTables} layouts against the machine blocks (Phase 45-47).
 *
 * <p>The geometry lives in {@link PortTables}, which is plain Java and therefore readable by the
 * unit tests; this class only maps a table name to the registered block. Keeping the two apart is
 * what lets {@code PortModelConsistencyTest} compare the table against the generated model files
 * without bootstrapping a game.</p>
 */
public final class MachinePortDefinitions {
    private MachinePortDefinitions() {
    }

    /** Block lookups for every table entry; a name without a block fails loudly at setup. */
    private static Map<String, Block> blocks() {
        Map<String, Block> map = new LinkedHashMap<>();
        map.put("crusher", ModBlocks.CRUSHER.get());
        map.put("assembler", ModBlocks.ASSEMBLER.get());
        map.put("fast_crusher", ModBlocks.FAST_CRUSHER.get());
        map.put("precision_crusher", ModBlocks.PRECISION_CRUSHER.get());
        map.put("electric_furnace", ModBlocks.ELECTRIC_FURNACE.get());
        map.put("electrolyzer", ModBlocks.ELECTROLYZER.get());
        map.put("metal_press", ModBlocks.METAL_PRESS.get());
        map.put("alloy_furnace", ModBlocks.ALLOY_FURNACE.get());
        map.put("quarry", ModBlocks.QUARRY.get());
        map.put("large_quarry", ModBlocks.LARGE_QUARRY.get());
        map.put("crafter", ModBlocks.CRAFTER.get());
        map.put("reconstructor", ModBlocks.RECONSTRUCTOR.get());
        map.put("energy_buffer", ModBlocks.ENERGY_BUFFER.get());
        map.put("energy_buffer_mk2", ModBlocks.ENERGY_BUFFER_MK2.get());
        map.put("energy_buffer_mk3", ModBlocks.ENERGY_BUFFER_MK3.get());
        map.put("item_distributor", ModBlocks.ITEM_DISTRIBUTOR.get());
        map.put("seed_cultivator", ModBlocks.SEED_CULTIVATOR.get());
        map.put("matter_amplifier", ModBlocks.MATTER_AMPLIFIER.get());
        map.put("phase_reactor", ModBlocks.PHASE_REACTOR.get());
        map.put("quantum_compressor", ModBlocks.QUANTUM_COMPRESSOR.get());
        map.put("ore_synthesizer", ModBlocks.ORE_SYNTHESIZER.get());
        map.put("combustion_generator", ModBlocks.COMBUSTION_GENERATOR.get());  // Phase 71
        map.put("thermal_generator", ModBlocks.THERMAL_GENERATOR.get());        // Phase 72
        map.put("nuclear_generator", ModBlocks.NUCLEAR_GENERATOR.get());        // Phase 73
        map.put("hydro_generator", ModBlocks.HYDRO_GENERATOR.get());            // Phase 74.1
        map.put("wind_generator", ModBlocks.WIND_GENERATOR.get());              // Phase 74.2
        map.put("solar_generator", ModBlocks.SOLAR_GENERATOR.get());            // Phase 74.3
        map.put("geothermal_generator", ModBlocks.GEOTHERMAL_GENERATOR.get());  // Phase 74.4
        map.put("pressure_recovery", ModBlocks.PRESSURE_RECOVERY.get());        // Phase 75
        map.put("heat_recovery", ModBlocks.HEAT_RECOVERY.get());                // Phase 75
        map.put("flywheel", ModBlocks.FLYWHEEL.get());                          // Phase 75
        map.put("regen_generator", ModBlocks.REGEN_GENERATOR.get());            // Phase 75
        map.put("quantum_transporter", ModBlocks.QUANTUM_TRANSPORTER.get());    // Phase 77
        map.put("basic_item_transporter", ModBlocks.BASIC_ITEM_TRANSPORTER.get());
        map.put("advanced_item_transporter", ModBlocks.ADVANCED_ITEM_TRANSPORTER.get());
        map.put("quantum_transporter_array", ModBlocks.QUANTUM_TRANSPORTER_ARRAY.get());
        return map;
    }

    /** Called once after the registries are frozen, from the common setup event. */
    public static void register() {
        for (Map.Entry<String, Block> entry : blocks().entrySet()) {
            PortLayout layout = PortTables.layout(entry.getKey());
            if (layout == null) {
                throw new IllegalStateException("no port table for " + entry.getKey());
            }
            MachinePorts.register(entry.getValue(), layout);
        }
    }
}

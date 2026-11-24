package com.industrialcore.machine.port;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The port table of every modelled machine, keyed by model name, in model space.
 *
 * <p>This is the single source of the port geometry. It is deliberately free of Minecraft types so
 * {@code PortModelConsistencyTest} can compare it against the generated model files, and so the
 * block registry only has to map a name to a block. The values match
 * {@code docs/model_tools/ports.json}, which the model generator writes after checking each face
 * for functional parts that would block a socket.</p>
 *
 * <p>Sockets are centred ({@code u = 0, v = 0}) unless the middle of the face is occupied; the
 * shifted ones are the reason the pipe/cable arm has to adapt, and they are listed with the part
 * that pushed them there.</p>
 *
 * <pre>
 * fast_crusher       v = -1.0   drum deck overhangs the back and west faces
 * electrolyzer       v = -1.0   lid terminals sit above the back window
 * alloy_furnace      v = -1.0   molten channel crosses the front, back kept low
 * crafter            v = -1.0   grid rim overhangs the back panel
 * item_distributor   v = +1.0   shutter band runs low around the body
 * seed_cultivator    v = -1.5   sight glass and tray take the lower window
 * ore_synthesizer    v = -1.0   crucible collar overhangs the back
 * </pre>
 */
public final class PortTables {
    private static final Map<String, PortLayout> TABLES = new LinkedHashMap<>();

    private PortTables() {
    }

    private static void put(String machine, PortSpec... specs) {
        TABLES.put(machine, new PortLayout(List.of(specs)));
    }

    private static PortSpec centre(PortSide side, PortKind kind) {
        return PortSpec.centred(side, kind);
    }

    private static PortSpec at(PortSide side, double v, PortKind kind) {
        return new PortSpec(side, 0.0, v, 3.5, 1.0, kind);
    }

    /** The layout of {@code machine} (its model name), or {@code null} if it has no table. */
    public static PortLayout layout(String machine) {
        return TABLES.get(machine);
    }

    /** Every table, in registration order; used by diagnostics and tests. */
    public static Map<String, PortLayout> all() {
        return java.util.Collections.unmodifiableMap(TABLES);
    }

    static {
        PortKind any = PortKind.ANY;
        PortKind power = PortKind.POWER;
        PortKind item = PortKind.ITEM;

        put("crusher", centre(PortSide.SOUTH, any), centre(PortSide.WEST, any));
        put("assembler", centre(PortSide.SOUTH, any), centre(PortSide.EAST, any),
                centre(PortSide.WEST, any));

        put("fast_crusher", at(PortSide.SOUTH, -1.0, any), at(PortSide.WEST, -1.0, any));
        put("precision_crusher", centre(PortSide.SOUTH, any));
        put("electric_furnace", centre(PortSide.SOUTH, any));
        put("electrolyzer", at(PortSide.SOUTH, -1.0, any));
        put("metal_press", centre(PortSide.SOUTH, any));
        put("alloy_furnace", at(PortSide.SOUTH, -1.0, any));

        put("quarry", centre(PortSide.WEST, any));
        put("large_quarry", centre(PortSide.SOUTH, any));

        put("crafter", at(PortSide.SOUTH, -1.0, any));
        put("reconstructor", centre(PortSide.SOUTH, any));

        put("energy_buffer", centre(PortSide.WEST, power), centre(PortSide.EAST, power));
        put("energy_buffer_mk2", centre(PortSide.WEST, power), centre(PortSide.EAST, power));
        put("energy_buffer_mk3", centre(PortSide.WEST, power), centre(PortSide.EAST, power));

        put("item_distributor", at(PortSide.SOUTH, 1.0, item), at(PortSide.WEST, 1.0, item),
                at(PortSide.EAST, 1.0, item));

        put("seed_cultivator", at(PortSide.SOUTH, -1.5, any), at(PortSide.WEST, -1.5, any));
        put("matter_amplifier", centre(PortSide.SOUTH, any));
        put("phase_reactor", centre(PortSide.SOUTH, any), centre(PortSide.WEST, any),
                centre(PortSide.EAST, any));
        put("quantum_compressor", centre(PortSide.SOUTH, any));
        put("ore_synthesizer", at(PortSide.SOUTH, -1.0, any));

        // Phase 71: the combustion generator's bus bar is on the back (model south), the fuel
        // filler on the top and the inspection panel on the west face; the front (model north,
        // z = 0) stays free for the flywheel.
        put("combustion_generator", centre(PortSide.SOUTH, power), centre(PortSide.WEST, any),
                at(PortSide.UP, 2.5, item));

        // Phase 72: the thermal generator places its FE bus bar on the back (model south) and
        // its solid-fuel feed hatch on the top rear (UP, v = +2.5).
        put("thermal_generator", centre(PortSide.SOUTH, power), at(PortSide.UP, 2.5, item));

        // Phase 73: the nuclear generator exposes its 900 FE/t HV bus bar on the back (south),
        // an auxiliary service port on the west panel, and the fuel-rod hatch on top (UP, v = +2.5).
        put("nuclear_generator", centre(PortSide.SOUTH, power), centre(PortSide.WEST, any),
                at(PortSide.UP, 2.5, item));

        // Phase 74.1: the hydro generator exposes only its 200 FE/t FE output bus bar on the back
        // (south); it has no fuel slot and requires no item/fluid pipe port.
        put("hydro_generator", centre(PortSide.SOUTH, power));

        // Phase 74.2: the wind generator exposes only its 200 FE/t FE output bus bar on the back
        // (south); it has no fuel slot and requires no item/fluid pipe port.
        put("wind_generator", centre(PortSide.SOUTH, power));

        // Phase 74.3: the low-profile solar generator places its single 200 FE/t FE output bus bar
        // on the back (south) lowered by v = -1.0 px to (8, 7, 16); no fuel/item port exists.
        put("solar_generator", at(PortSide.SOUTH, -1.0, power));

        // Phase 74.4: the geothermal generator exposes only its 256 FE/t FE output bus bar on the
        // back (south); it has no fuel slot and requires no item pipe port.
        put("geothermal_generator", centre(PortSide.SOUTH, power));

        // Phase 75: four horizontally symmetric recovery devices expose FE-only power ports on
        // all four cardinal horizontal faces; regen_generator is lowered by v = -1.0 px.
        put("pressure_recovery", centre(PortSide.NORTH, power), centre(PortSide.SOUTH, power),
                centre(PortSide.WEST, power), centre(PortSide.EAST, power));
        put("heat_recovery", centre(PortSide.NORTH, power), centre(PortSide.SOUTH, power),
                centre(PortSide.WEST, power), centre(PortSide.EAST, power));
        put("flywheel", centre(PortSide.NORTH, power), centre(PortSide.SOUTH, power),
                centre(PortSide.WEST, power), centre(PortSide.EAST, power));
        put("regen_generator", at(PortSide.NORTH, -1.0, power), at(PortSide.SOUTH, -1.0, power),
                at(PortSide.WEST, -1.0, power), at(PortSide.EAST, -1.0, power));

        // Phase 77: four transporter controllers
        put("quantum_transporter", centre(PortSide.SOUTH, any), centre(PortSide.WEST, any),
                centre(PortSide.EAST, any), centre(PortSide.UP, item));
        put("basic_item_transporter", centre(PortSide.NORTH, item));
        put("advanced_item_transporter", centre(PortSide.NORTH, item));
        put("quantum_transporter_array", centre(PortSide.NORTH, any));
    }
}

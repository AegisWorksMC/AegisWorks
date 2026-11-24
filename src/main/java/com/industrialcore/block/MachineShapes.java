package com.industrialcore.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Collision/selection shapes for machines whose model is larger than one block.
 *
 * <p>The boxes are authored with the machine facing north and its overhang on the -Z side; every
 * box is then rotated about the block centre by the same amount the blockstate rotates the model,
 * so the shape can never drift away from what is drawn.</p>
 *
 * <p>Minecraft only draws the part of a selection outline that sits inside the block's own cell
 * (see {@code docs/MACHINE_COLLISION_DESIGN.md}); collision is not clipped, which is why the
 * overhang is covered here.</p>
 */
public final class MachineShapes {
    /** Crushing drum housing: 6/16 of a block in front of the front face. */
    private static final AABB[] CRUSHER = {
        box(0, 0, 0, 16, 2, 16),
        box(0, 2, 2, 16, 13, 16),
        box(1, 13, 1, 15, 16, 15),
        box(1, 4, -6, 15, 10, 2),
        box(5, 2, -6, 11, 4, -1),
    };
    /** Exhaust stack on top and a radiator that sticks 4/16 out of the front. */
    private static final AABB[] GENERATOR = {
        box(0, 0, 0, 16, 2, 16),
        box(0, 2, 1, 16, 13, 16),
        box(2, 4, -4, 14, 11, 0),
        box(6, 13, 5, 10, 24, 9),
        box(5, 24, 4, 11, 25, 10),
        box(16, 5, 5, 20, 11, 11),
    };

    /** Work table that reaches 8/16 in front, plus the assembly arm above the frame. */
    private static final AABB[] ASSEMBLER = {
        box(0, 0, 0, 16, 3, 16),
        box(1, 3, 1, 15, 12, 15),
        box(0, 10, -8, 16, 12, 1),
        box(2, 3, -7, 4, 10, -5),
        box(12, 3, -7, 14, 10, -5),
        box(6, 12, 6, 10, 20, 10),
        box(5, 18, -5, 15, 20, -1),
        box(7, 16, -5, 13, 18, -1),
    };

    private static final VoxelShape PRESSURE_RECOVERY = Shapes.or(
            Shapes.create(box(
                    com.industrialcore.machine.PressureShape.CELL.x0(),
                    com.industrialcore.machine.PressureShape.CELL.y0(),
                    com.industrialcore.machine.PressureShape.CELL.z0(),
                    com.industrialcore.machine.PressureShape.CELL.x1(),
                    com.industrialcore.machine.PressureShape.CELL.y1(),
                    com.industrialcore.machine.PressureShape.CELL.z1())),
            Shapes.create(box(
                    com.industrialcore.machine.PressureShape.TOWER.x0(),
                    com.industrialcore.machine.PressureShape.TOWER.y0(),
                    com.industrialcore.machine.PressureShape.TOWER.z0(),
                    com.industrialcore.machine.PressureShape.TOWER.x1(),
                    com.industrialcore.machine.PressureShape.TOWER.y1(),
                    com.industrialcore.machine.PressureShape.TOWER.z1()))
    ).optimize();

    private static final VoxelShape HEAT_RECOVERY = Shapes.or(
            Shapes.create(box(
                    com.industrialcore.machine.HeatShape.CELL.x0(),
                    com.industrialcore.machine.HeatShape.CELL.y0(),
                    com.industrialcore.machine.HeatShape.CELL.z0(),
                    com.industrialcore.machine.HeatShape.CELL.x1(),
                    com.industrialcore.machine.HeatShape.CELL.y1(),
                    com.industrialcore.machine.HeatShape.CELL.z1())),
            Shapes.create(box(
                    com.industrialcore.machine.HeatShape.HOOD.x0(),
                    com.industrialcore.machine.HeatShape.HOOD.y0(),
                    com.industrialcore.machine.HeatShape.HOOD.z0(),
                    com.industrialcore.machine.HeatShape.HOOD.x1(),
                    com.industrialcore.machine.HeatShape.HOOD.y1(),
                    com.industrialcore.machine.HeatShape.HOOD.z1()))
    ).optimize();

    private static final VoxelShape REGEN_GENERATOR = Shapes.or(
            Shapes.create(box(
                    com.industrialcore.machine.RegenShape.BASE.x0(),
                    com.industrialcore.machine.RegenShape.BASE.y0(),
                    com.industrialcore.machine.RegenShape.BASE.z0(),
                    com.industrialcore.machine.RegenShape.BASE.x1(),
                    com.industrialcore.machine.RegenShape.BASE.y1(),
                    com.industrialcore.machine.RegenShape.BASE.z1())),
            Shapes.create(box(
                    com.industrialcore.machine.RegenShape.COIL.x0(),
                    com.industrialcore.machine.RegenShape.COIL.y0(),
                    com.industrialcore.machine.RegenShape.COIL.z0(),
                    com.industrialcore.machine.RegenShape.COIL.x1(),
                    com.industrialcore.machine.RegenShape.COIL.y1(),
                    com.industrialcore.machine.RegenShape.COIL.z1()))
    ).optimize();

    private MachineShapes() {}

    public static VoxelShape crusher(Direction facing) {
        return shape(CRUSHER, facing);
    }

    public static VoxelShape generator(Direction facing) {
        return shape(GENERATOR, facing);
    }

    /**
     * Shape of the combustion generator (Phase 71).
     *
     * <p>The boxes live in {@link com.industrialcore.machine.CombustionShape} so a unit test can
     * compare them with the model file; this method only converts them to AABBs and rotates them
     * with the same helper as every other large machine.</p>
     */
    public static VoxelShape combustion(Direction facing) {
        VoxelShape result = Shapes.empty();
        for (com.industrialcore.machine.CombustionShape.Box part
                : com.industrialcore.machine.CombustionShape.BOXES) {
            result = Shapes.or(result, Shapes.create(rotate(new AABB(
                    part.x0() / 16.0, part.y0() / 16.0, part.z0() / 16.0,
                    part.x1() / 16.0, part.y1() / 16.0, part.z1() / 16.0), facing)));
        }
        return result.optimize();
    }

    /**
     * Shape of the nuclear generator (Phase 73).
     *
     * <p>The boxes live in {@link com.industrialcore.machine.NuclearShape} so a unit test can
     * verify them without booting Minecraft.</p>
     */
    public static VoxelShape nuclear(Direction facing) {
        VoxelShape result = Shapes.empty();
        for (com.industrialcore.machine.NuclearShape.Box part
                : com.industrialcore.machine.NuclearShape.BOXES) {
            result = Shapes.or(result, Shapes.create(rotate(new AABB(
                    part.x0() / 16.0, part.y0() / 16.0, part.z0() / 16.0,
                    part.x1() / 16.0, part.y1() / 16.0, part.z1() / 16.0), facing)));
        }
        return result.optimize();
    }

    /**
     * Shape of the wind generator (Phase 74.2).
     *
     * <p>The six disjoint boxes live in {@link com.industrialcore.machine.WindShape} so a unit
     * test can verify them without booting Minecraft.</p>
     */
    public static VoxelShape wind(Direction facing) {
        VoxelShape result = Shapes.empty();
        for (com.industrialcore.machine.WindShape.Box part
                : com.industrialcore.machine.WindShape.BOXES) {
            result = Shapes.or(result, Shapes.create(rotate(new AABB(
                    part.x0() / 16.0, part.y0() / 16.0, part.z0() / 16.0,
                    part.x1() / 16.0, part.y1() / 16.0, part.z1() / 16.0), facing)));
        }
        return result.optimize();
    }

    /**
     * Shape of the solar generator (Phase 74.3).
     *
     * <p>The three disjoint stepped boxes live in {@link com.industrialcore.machine.SolarShape} so
     * a unit test can verify them without booting Minecraft.</p>
     */
    public static VoxelShape solar(Direction facing) {
        VoxelShape result = Shapes.empty();
        for (com.industrialcore.machine.SolarShape.Box part
                : com.industrialcore.machine.SolarShape.BOXES) {
            result = Shapes.or(result, Shapes.create(rotate(new AABB(
                    part.x0() / 16.0, part.y0() / 16.0, part.z0() / 16.0,
                    part.x1() / 16.0, part.y1() / 16.0, part.z1() / 16.0), facing)));
        }
        return result.optimize();
    }

    /**
     * Shape of the geothermal generator (Phase 74.4).
     *
     * <p>The two disjoint boxes live in {@link com.industrialcore.machine.GeoShape} so a unit
     * test can verify them without booting Minecraft.</p>
     */
    public static VoxelShape geothermal(Direction facing) {
        VoxelShape result = Shapes.empty();
        for (com.industrialcore.machine.GeoShape.Box part
                : com.industrialcore.machine.GeoShape.BOXES) {
            result = Shapes.or(result, Shapes.create(rotate(new AABB(
                    part.x0() / 16.0, part.y0() / 16.0, part.z0() / 16.0,
                    part.x1() / 16.0, part.y1() / 16.0, part.z1() / 16.0), facing)));
        }
        return result.optimize();
    }

    public static VoxelShape assembler(Direction facing) {
        return shape(ASSEMBLER, facing);
    }

    /** Shape of the pressure recovery device (Phase 75, non-directional). */
    public static VoxelShape pressureRecovery() {
        return PRESSURE_RECOVERY;
    }

    /** Shape of the waste-heat recovery device (Phase 75, non-directional). */
    public static VoxelShape heatRecovery() {
        return HEAT_RECOVERY;
    }

    /** Shape of the regenerative generator (Phase 75, non-directional). */
    public static VoxelShape regenGenerator() {
        return REGEN_GENERATOR;
    }

    private static VoxelShape shape(AABB[] local, Direction facing) {
        VoxelShape result = Shapes.empty();
        for (AABB part : local) {
            result = Shapes.or(result, Shapes.create(rotate(part, facing)));
        }
        return result.optimize();
    }

    private static AABB box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new AABB(x0 / 16.0, y0 / 16.0, z0 / 16.0, x1 / 16.0, y1 / 16.0, z1 / 16.0);
    }

    /** Rotates a north-facing box about the block centre, matching the blockstate model rotation. */
    static AABB rotate(AABB box, Direction facing) {
        double x0 = box.minX;
        double x1 = box.maxX;
        double z0 = box.minZ;
        double z1 = box.maxZ;
        double nx0;
        double nx1;
        double nz0;
        double nz1;
        switch (facing) {
            case EAST -> {
                // (x, z) -> (1 - z, x)
                nx0 = 1.0 - z1;
                nx1 = 1.0 - z0;
                nz0 = x0;
                nz1 = x1;
            }
            case SOUTH -> {
                // (x, z) -> (1 - x, 1 - z)
                nx0 = 1.0 - x1;
                nx1 = 1.0 - x0;
                nz0 = 1.0 - z1;
                nz1 = 1.0 - z0;
            }
            case WEST -> {
                // (x, z) -> (z, 1 - x)
                nx0 = z0;
                nx1 = z1;
                nz0 = 1.0 - x1;
                nz1 = 1.0 - x0;
            }
            default -> {
                return box;
            }
        }
        return new AABB(nx0, box.minY, nz0, nx1, box.maxY, nz1);
    }
}

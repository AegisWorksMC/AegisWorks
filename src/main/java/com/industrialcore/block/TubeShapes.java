package com.industrialcore.block;

import com.industrialcore.machine.TubeProfile;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Selection and collision boxes for pipes and cables, derived from {@link TubeProfile}.
 *
 * <p>The shape is exactly what the model draws: the tube of every connected arm plus the junction
 * core. There is no separate flange box any more, because the connector that meets a neighbour is
 * drawn in front of the block boundary by the renderer and is not part of this block's volume.</p>
 *
 * <p>These live next to the blocks instead of inside {@link TubeProfile} so that the profile stays
 * a plain arithmetic class: the unit tests run without the game on the classpath and compare the
 * generated models against those numbers.</p>
 */
public final class TubeShapes {
    private TubeShapes() {
    }

    /** The junction core as a selection shape. */
    public static VoxelShape core(double tubePx) {
        double low = 8.0D - TubeProfile.core(tubePx) / 2.0D;
        double high = 8.0D + TubeProfile.core(tubePx) / 2.0D;
        return Block.box(low, low, low, high, high, high);
    }

    /** The tube of one arm, from its boundary to the block centre. */
    public static VoxelShape arm(Direction direction, double tubePx) {
        double low = 8.0D - tubePx / 2.0D;
        double high = 8.0D + tubePx / 2.0D;
        return switch (direction) {
            case DOWN -> Block.box(low, 0.0D, low, high, 8.0D, high);
            case UP -> Block.box(low, 8.0D, low, high, 16.0D, high);
            case NORTH -> Block.box(low, low, 0.0D, high, high, 8.0D);
            case SOUTH -> Block.box(low, low, 8.0D, high, high, 16.0D);
            case WEST -> Block.box(0.0D, low, low, 8.0D, high, high);
            case EAST -> Block.box(8.0D, low, low, 16.0D, high, high);
        };
    }
}

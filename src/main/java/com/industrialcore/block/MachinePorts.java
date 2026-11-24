package com.industrialcore.block;

import com.industrialcore.machine.port.PortFacing;
import com.industrialcore.machine.port.PortLayout;
import com.industrialcore.machine.port.PortSide;
import com.industrialcore.machine.port.PortSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the connection ports a machine exposes to cables and pipes (Phase 45).
 *
 * <p>A machine is registered once with a {@link PortLayout} in model space; the layout is then
 * queried with the block state, which supplies the facing. Every answer - which face has a port,
 * where it sits, how wide its socket is - therefore rotates with the machine and cannot drift from
 * the model or the collision shape (both use the same 0/90/180/270 transform).</p>
 *
 * <h2>What this does not change</h2>
 *
 * <p>Transport is still decided by the NeoForge capability queries in {@code CableBlock} and
 * {@code ItemPipeBlock}. A face without a declared port keeps working exactly as before; the
 * registry decides only how a connection is drawn (the socket in the machine model) and gives the
 * connection logic a place to look up geometry. That separation is deliberate: silently refusing a
 * capability because a port declaration is missing would break existing factories.</p>
 *
 * <p>Blocks are keyed by identity, so a tier variant of the same model can reuse a layout without
 * a lookup by registry name in the render path.</p>
 */
public final class MachinePorts {
    private static final Map<Block, PortLayout> LAYOUTS = new IdentityHashMap<>();

    private MachinePorts() {}

    /** Registers the ports of a machine; replaces any earlier registration. */
    public static void register(Block block, PortSpec... specs) {
        register(block, new PortLayout(List.of(specs)));
    }

    /** Registers the ports of a machine; replaces any earlier registration. */
    public static void register(Block block, PortLayout layout) {
        if (block == null || layout == null) {
            throw new IllegalArgumentException("block and layout are required");
        }
        LAYOUTS.put(block, layout);
    }

    /** The layout of a placed machine, or {@code null} if it has no ports. */
    @Nullable
    public static PortLayout layout(BlockState state) {
        if (state == null) {
            return null;
        }
        return LAYOUTS.get(state.getBlock());
    }

    /** The port that ends up on {@code worldSide}, or {@code null} if there is none. */
    @Nullable
    public static PortSpec portOn(BlockState state, Direction worldSide) {
        PortLayout layout = layout(state);
        if (layout == null || worldSide == null) {
            return null;
        }
        return layout.portOn(facingOf(state), sideOf(worldSide));
    }

    public static boolean hasPort(BlockState state, Direction worldSide) {
        return portOn(state, worldSide) != null;
    }

    /**
     * World centre of the port on {@code worldSide}.
     *
     * @return the centre, or {@code null} if that face has no port
     */
    @Nullable
    public static Vec3 anchor(BlockState state, BlockPos pos, Direction worldSide) {
        PortLayout layout = layout(state);
        if (layout == null) {
            return null;
        }
        double[] center = layout.centerOn(facingOf(state), sideOf(worldSide));
        if (center == null) {
            return null;
        }
        return new Vec3(pos.getX() + center[0], pos.getY() + center[1], pos.getZ() + center[2]);
    }

    /** Facing of a placed machine, defaulting to north for blocks without a facing property. */
    public static PortFacing facingOf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return facingOf(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return facingOf(state.getValue(BlockStateProperties.FACING));
        }
        return PortFacing.NORTH;
    }

    public static PortFacing facingOf(Direction direction) {
        return switch (direction) {
            case EAST -> PortFacing.EAST;
            case SOUTH -> PortFacing.SOUTH;
            case WEST -> PortFacing.WEST;
            default -> PortFacing.NORTH;
        };
    }

    public static PortSide sideOf(Direction direction) {
        return switch (direction) {
            case DOWN -> PortSide.DOWN;
            case UP -> PortSide.UP;
            case NORTH -> PortSide.NORTH;
            case SOUTH -> PortSide.SOUTH;
            case WEST -> PortSide.WEST;
            case EAST -> PortSide.EAST;
        };
    }

    public static Direction directionOf(PortSide side) {
        return switch (side) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }
}

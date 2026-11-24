package com.industrialcore.client;

import com.industrialcore.block.CableBlock;
import com.industrialcore.block.ConveyorBlock;
import com.industrialcore.block.ItemPipeBlock;
import com.industrialcore.block.MachinePorts;
import com.industrialcore.machine.port.PortFacing;
import com.industrialcore.machine.port.PortSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Per-block cache of "what does this face meet, and where on that surface?".
 *
 * <p>An arm render asks the same question every frame. The answer only changes when the neighbour
 * changes, so it is resolved once per face and kept in flat arrays:
 * {@link ConnectorKind kinds}, one anchor ({@code double[18]}, six faces of three coordinates) and
 * one radius per face. {@link #invalidate()} is called from the block's {@code updateShape} /
 * {@code neighborChanged} hooks, which is the only way the answer can change, so a placed factory
 * costs no lookups while nothing moves.</p>
 *
 * <p>Anchors are stored in <em>this</em> block's coordinate space, so the render path can feed them
 * straight to the connector geometry. The cache is independent of the transport: whether items or
 * energy actually move is decided by the NeoForge capabilities in the block entity, never here.</p>
 */
public final class PortArmCache {
    /** One flag per direction plus the resolved target for each. */
    private final boolean[] resolved = new boolean[6];
    private final ConnectorKind[] kinds = new ConnectorKind[6];
    private final double[] anchors = new double[18];
    private final double[] radii = new double[6];
    private final double[] scratch = new double[3];

    private static final double[] CENTRE = {0.5D, 0.5D, 0.5D};

    /** Drops every cached answer; called whenever a neighbour may have changed. */
    public void invalidate() {
        java.util.Arrays.fill(resolved, false);
    }

    /**
     * Resolves the neighbour on one face.
     *
     * @return what the arm meets there ({@link ConnectorKind#PIPE} when the two models already meet)
     */
    public ConnectorKind resolve(Level level, BlockPos pos, Direction direction) {
        int index = direction.get3DDataValue();
        if (resolved[index]) {
            return kinds[index];
        }
        resolved[index] = true;

        BlockPos neighbourPos = pos.relative(direction);
        BlockState neighbour = level.getBlockState(neighbourPos);
        if (neighbour.getBlock() instanceof ItemPipeBlock
                || neighbour.getBlock() instanceof CableBlock
                || neighbour.getBlock() instanceof ConveyorBlock) {
            kinds[index] = ConnectorKind.PIPE;
            return ConnectorKind.PIPE;
        }

        int base = index * 3;
        PortSpec spec = MachinePorts.portOn(neighbour, direction.getOpposite());
        if (spec != null) {
            // The port centre is expressed inside the neighbour; shift it onto our boundary.
            PortFacing facing = MachinePorts.facingOf(neighbour);
            spec.centerInto(facing, scratch);
            anchors[base] = scratch[0] + direction.getStepX();
            anchors[base + 1] = scratch[1] + direction.getStepY();
            anchors[base + 2] = scratch[2] + direction.getStepZ();
            radii[index] = spec.radius() / 16.0D;
            kinds[index] = ConnectorKind.PORT;
            return ConnectorKind.PORT;
        }

        // Anything else that connects gets a generic connector on the face centre. Keeping the
        // anchor on the boundary means the connector lies on the neighbour's surface instead of
        // being pushed into a model that has no socket.
        anchors[base] = CENTRE[0] + direction.getStepX() * 0.5D;
        anchors[base + 1] = CENTRE[1] + direction.getStepY() * 0.5D;
        anchors[base + 2] = CENTRE[2] + direction.getStepZ() * 0.5D;
        radii[index] = 0.0D;
        kinds[index] = ConnectorKind.FLUSH;
        return ConnectorKind.FLUSH;
    }

    /** Anchor X in block units, valid after {@link #resolve}. */
    public double anchorX(Direction direction) {
        return anchors[direction.get3DDataValue() * 3];
    }

    /** Anchor Y in block units, valid after {@link #resolve}. */
    public double anchorY(Direction direction) {
        return anchors[direction.get3DDataValue() * 3 + 1];
    }

    /** Anchor Z in block units, valid after {@link #resolve}. */
    public double anchorZ(Direction direction) {
        return anchors[direction.get3DDataValue() * 3 + 2];
    }

    /** Port socket radius in block units, or 0 for a generic surface. */
    public double radius(Direction direction) {
        return radii[direction.get3DDataValue()];
    }
}

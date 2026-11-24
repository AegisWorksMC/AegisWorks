package com.industrialcore.blockentity;

import com.industrialcore.block.AdvancedTransporterDriveBlock;
import com.industrialcore.machine.AdvancedTransportLayout;
import com.industrialcore.machine.AdvancedTransportMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity of the Phase 63 Advanced Transporter.
 *
 * <p>It is the same block entity as every other transporter - buffer, channel, role, FE, status,
 * menu and sync all come from {@link TransporterBlockEntity} - with exactly one difference: the
 * structure it validates is the 3x2x3 machine it owns, not the old hollow casing shell.</p>
 *
 * <p>Ownership is proven from block states alone: every cell must hold the part block with this
 * cell's index and this machine's facing. A Phase 62 casing in the same spot fails that test, so
 * an old structure can never be adopted by the new machine. Unloaded cells make the machine
 * invalid instead of force-loading a chunk, and no part ever ticks.</p>
 */
public class AdvancedTransporterDriveBlockEntity extends TransporterBlockEntity {

    public AdvancedTransporterDriveBlockEntity(BlockPos pos, BlockState state) {
        super(com.industrialcore.registry.ModBlockEntities.ADVANCED_TRANSPORTER_DRIVE.get(),
                com.industrialcore.machine.TransporterKind.ADVANCED, pos, state);
    }

    /** The 3x2x3 machine has no casing shell to style; Phase 62's styling stays on its own block. */
    @Override
    protected boolean stylesShell() {
        return false;
    }

    @Override
    protected boolean structureMatches(ServerLevel level, int facing, int size) {
        Direction direction = Direction.from3DDataValue(facing);
        if (direction.getAxis().isVertical()) {
            return false;
        }
        for (AdvancedTransportLayout.Cell cell : AdvancedTransportLayout.cells()) {
            BlockPos position = AdvancedTransportMachine.position(worldPosition, direction, cell);
            if (!level.isLoaded(position)) {
                return false; // never force-load a chunk just to validate
            }
            if (!AdvancedTransportMachine.isOwnedCell(level, worldPosition, direction, cell)) {
                return false;
            }
        }
        return true;
    }

    /** True when this block entity's controller block is the Phase 63 drive. */
    public boolean isDriveBlock() {
        return getBlockState().getBlock() instanceof AdvancedTransporterDriveBlock;
    }
}

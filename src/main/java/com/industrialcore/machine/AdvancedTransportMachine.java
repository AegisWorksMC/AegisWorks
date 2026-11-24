package com.industrialcore.machine;

import com.industrialcore.block.AdvancedTransporterDriveBlock;
import com.industrialcore.block.AdvancedTransporterPartBlock;
import com.industrialcore.blockentity.TransporterBlockEntity;
import com.industrialcore.registry.ModBlocks;
import com.industrialcore.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server side assembly and disassembly of the Phase 63 Advanced Transporter.
 *
 * <p>One item placement builds all 18 cells and one broken cell removes all 18. Both directions
 * are deliberately written as a single, guarded transaction so no path can leave a half-built
 * machine behind or hand out a second machine item:</p>
 *
 * <ul>
 *   <li>{@link #place} checks every cell first and only then writes; if a write still fails every
 *       cell written so far is rolled back, so a failure costs nothing but the item is not
 *       consumed either (the item only shrinks after {@code place} returned true).</li>
 *   <li>{@link #remove} holds a re-entrancy guard for the duration of the teardown. Removing the
 *       remaining cells makes their {@code onRemove} run, which would normally start another
 *       teardown; because the controller position is guarded, that inner call returns
 *       immediately. Exactly one caller therefore drops exactly one item.</li>
 * </ul>
 *
 * <p>Everything here is driven by block states only: a part stores its own index and facing, so
 * the owning controller can always be recomputed without loading a chunk, reading a block entity
 * or trusting a stale UUID.</p>
 */
public final class AdvancedTransportMachine {

    /** Controller positions whose teardown is currently running (server thread only). */
    private static final Set<BlockPos> REMOVING = new HashSet<>();

    private AdvancedTransportMachine() {}

    /** True while a teardown of this controller is running; used by the blocks' removal hooks. */
    public static boolean isRemoving(BlockPos controllerPos) {
        return REMOVING.contains(controllerPos);
    }

    /**
     * Checks all 18 cells without touching the world: every cell must be inside the build height
     * and must currently be air. Containers, machines and every other block are never overwritten.
     */
    public static boolean canPlace(ServerLevel level, BlockPos controllerPos, Direction facing) {
        if (controllerPos.getY() < level.getMinBuildHeight()
                || controllerPos.getY() + AdvancedTransportLayout.HEIGHT - 1 >= level.getMaxBuildHeight()) {
            return false;
        }
        for (AdvancedTransportLayout.Cell cell : AdvancedTransportLayout.cells()) {
            BlockPos position = position(controllerPos, facing, cell);
            if (position.getY() < level.getMinBuildHeight()
                    || position.getY() >= level.getMaxBuildHeight()) {
                return false;
            }
            if (!level.getBlockState(position).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds all 18 cells. Returns false (with the world untouched) when the space is not free or
     * a write fails; the caller must only consume the item on true.
     */
    public static boolean place(ServerLevel level, BlockPos controllerPos, Direction facing) {
        if (!canPlace(level, controllerPos, facing)) {
            return false;
        }
        if (isRemoving(controllerPos)) {
            return false;
        }
        List<BlockPos> written = new ArrayList<>(AdvancedTransportLayout.CELL_COUNT);
        try {
            BlockState part = ModBlocks.ADVANCED_TRANSPORTER_PART.get().defaultBlockState();
            for (AdvancedTransportLayout.Cell cell : AdvancedTransportLayout.cells()) {
                if (cell.controller()) {
                    continue;
                }
                BlockPos position = position(controllerPos, facing, cell);
                BlockState state = part
                        .setValue(AdvancedTransporterPartBlock.PART, cell.index())
                        .setValue(AdvancedTransporterPartBlock.FACING, facing);
                if (!level.setBlock(position, state, Block.UPDATE_ALL)) {
                    rollback(level, written);
                    return false;
                }
                written.add(position);
            }
            // The controller goes last: until it exists the parts are inert orphans, and its own
            // block entity validates the machine as soon as it is placed.
            BlockState drive = ModBlocks.ADVANCED_TRANSPORTER_DRIVE.get().defaultBlockState()
                    .setValue(AdvancedTransporterDriveBlock.FACING, facing);
            if (!level.setBlock(controllerPos, drive, Block.UPDATE_ALL)) {
                rollback(level, written);
                return false;
            }
            // The controller's block entity has not ticked yet - and in a chunk outside entity
            // ticking range it will not tick at all - so it would report NO_STRUCTURE for an
            // unspecified while. Validate once here: the machine is usable the moment it is
            // built, and a save that happens right after placement stores a consistent verdict.
            if (level.getBlockEntity(controllerPos) instanceof TransporterBlockEntity transporter) {
                transporter.revalidateNow();
            }
            return true;
        } catch (RuntimeException failure) {
            rollback(level, written);
            throw failure;
        }
    }

    /** Puts every cell written so far back to air; never drops anything. */
    private static void rollback(ServerLevel level, List<BlockPos> written) {
        for (int index = written.size() - 1; index >= 0; index--) {
            level.removeBlock(written.get(index), false);
        }
        written.clear();
    }

    /**
     * Tears the whole machine down: contents first, then every part, then the controller, and
     * finally (and only once) the machine item.
     *
     * @param dropPos  where the machine item appears, usually the cell the player broke
     * @param dropItem false for creative breaks
     * @return true when this call performed the teardown
     */
    public static boolean remove(ServerLevel level, BlockPos controllerPos, @Nullable BlockPos dropPos,
                                 boolean dropItem) {
        return remove(level, controllerPos, dropPos, dropItem, null);
    }

    /**
     * Teardown variant for callers that no longer have the controller state at hand, because it
     * was removed before their hook ran ({@code /setblock ... air} only fires {@code onRemove},
     * and by then the position already holds air). The facing is taken from the state the caller
     * saw, and the call is refused when no part of this machine is left, so a second teardown can
     * never hand out a second machine item.
     */
    public static boolean remove(ServerLevel level, BlockPos controllerPos, @Nullable BlockPos dropPos,
                                 boolean dropItem, @Nullable Direction knownFacing) {
        if (!REMOVING.add(controllerPos)) {
            return false; // a teardown of this machine is already running
        }
        try {
            BlockState own = level.getBlockState(controllerPos);
            Direction facing;
            if (own.getBlock() instanceof AdvancedTransporterDriveBlock) {
                facing = own.getValue(AdvancedTransporterDriveBlock.FACING);
            } else if (knownFacing != null && hasPartCell(level, controllerPos, knownFacing)) {
                facing = knownFacing;
            } else {
                // Not a controller any more and no cell left: nothing to own, nothing to drop. An
                // orphaned part therefore never produces a machine item.
                return false;
            }
            TransporterBlockEntity transporter = blockEntityAt(level, controllerPos);
            if (transporter != null) {
                // Buffer contents become item entities before the block entity disappears.
                transporter.dropContents();
            }
            for (AdvancedTransportLayout.Cell cell : AdvancedTransportLayout.cells()) {
                if (!cell.controller()) {
                    level.removeBlock(position(controllerPos, facing, cell), false);
                }
            }
            level.removeBlock(controllerPos, false);
            if (dropItem) {
                Block.popResource(level, dropPos != null ? dropPos : controllerPos,
                        new ItemStack(ModItems.ADVANCED_TRANSPORTER_DRIVE.get()));
            }
            return true;
        } finally {
            REMOVING.remove(controllerPos);
        }
    }

    /** True when at least one of the 17 part cells for this controller and facing holds a part. */
    private static boolean hasPartCell(ServerLevel level, BlockPos controllerPos, Direction facing) {
        for (AdvancedTransportLayout.Cell cell : AdvancedTransportLayout.cells()) {
            if (cell.controller()) {
                continue;
            }
            if (level.getBlockState(position(controllerPos, facing, cell)).getBlock()
                    == ModBlocks.ADVANCED_TRANSPORTER_PART.get()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Block entity at a position, or {@code null} when there is none. A world saved with a block
     * entity of the wrong type (an older build, a hand-edited world) makes
     * {@code getBlockEntity} throw; a teardown must never crash a chunk tick because of that, so
     * the failure is treated as "no block entity" and the contents are simply not dropped.
     */
    @Nullable
    private static TransporterBlockEntity blockEntityAt(ServerLevel level, BlockPos pos) {
        try {
            return level.getBlockEntity(pos) instanceof TransporterBlockEntity transporter
                    ? transporter : null;
        } catch (RuntimeException invalidEntity) {
            return null;
        }
    }

    /** World position of one cell. */
    public static BlockPos position(BlockPos controllerPos, Direction facing,
                                    AdvancedTransportLayout.Cell cell) {
        GridPos world = AdvancedTransportLayout.world(controllerPos.getX(), controllerPos.getY(),
                controllerPos.getZ(), facing.get3DDataValue(), cell);
        return new BlockPos(world.x(), world.y(), world.z());
    }

    /**
     * Controller that owns this part, or {@code null} when the state is not a part. The result is
     * a position, not a promise: the caller must verify that the block there is really the
     * machine's controller and still expects this cell.
     */
    @Nullable
    public static BlockPos ownerOf(BlockState state, BlockPos partPos) {
        if (!(state.getBlock() instanceof AdvancedTransporterPartBlock)) {
            return null;
        }
        int partIndex = state.getValue(AdvancedTransporterPartBlock.PART);
        Direction facing = state.getValue(AdvancedTransporterPartBlock.FACING);
        try {
            AdvancedTransportLayout.Cell cell = AdvancedTransportLayout.cell(partIndex);
            if (cell.controller()) {
                return null;
            }
            GridPos owner = AdvancedTransportLayout.controllerOf(partPos.getX(), partPos.getY(),
                    partPos.getZ(), facing.get3DDataValue(), cell);
            return new BlockPos(owner.x(), owner.y(), owner.z());
        } catch (IllegalArgumentException outside) {
            return null; // a tampered part index cannot own anything
        }
    }

    /**
     * True when this cell of this controller really holds the part block for that cell, with the
     * machine's facing and part index. This is the ownership test the controller uses, and it is
     * the reason a Phase 62 casing (a different block) can never be mistaken for a Phase 63 part.
     */
    public static boolean isOwnedCell(ServerLevel level, BlockPos controllerPos, Direction facing,
                                      AdvancedTransportLayout.Cell cell) {
        if (cell.controller()) {
            return level.getBlockState(controllerPos).getBlock()
                    == ModBlocks.ADVANCED_TRANSPORTER_DRIVE.get();
        }
        BlockPos position = position(controllerPos, facing, cell);
        BlockState state = level.getBlockState(position);
        return state.getBlock() == ModBlocks.ADVANCED_TRANSPORTER_PART.get()
                && state.getValue(AdvancedTransporterPartBlock.PART) == cell.index()
                && state.getValue(AdvancedTransporterPartBlock.FACING) == facing;
    }
}

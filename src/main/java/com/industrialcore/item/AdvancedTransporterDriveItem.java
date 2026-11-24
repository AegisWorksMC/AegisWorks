package com.industrialcore.item;

import com.industrialcore.machine.AdvancedTransportMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Item of the Phase 63 Advanced Transporter.
 *
 * <p>Placing it once builds the whole 3x2x3 machine instead of a single block. The item is only
 * consumed after {@link AdvancedTransportMachine#place} reported success, so a blocked site costs
 * the player nothing, and a successful site costs exactly one item (creative consumes none).</p>
 */
public class AdvancedTransporterDriveItem extends BlockItem {

    public AdvancedTransporterDriveItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            // The client only predicts; the server decides and syncs the result.
            return InteractionResult.SUCCESS;
        }
        BlockPos controllerPos = context.getClickedPos().relative(context.getClickedFace());
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!AdvancedTransportMachine.place(level, controllerPos, facing)) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}

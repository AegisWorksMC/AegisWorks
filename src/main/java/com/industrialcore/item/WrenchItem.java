package com.industrialcore.item;

import com.industrialcore.block.ItemPipeBlock;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.machine.PipeConnectionRules;
import com.industrialcore.machine.PipeWrenchTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Wrench: cycles the transport mode of one pipe face.
 *
 * <p>The click side is resolved from the hit position (see {@link PipeWrenchTarget}) because a
 * pipe arm can be hit while the block face points somewhere else. Sneaking cycles backwards. All
 * logic runs on the server: the client sends no packet, so there is nothing a modified client
 * could forge. The result is shown in the action bar so the player sees which face changed.</p>
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ItemPipeBlockEntity pipe)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            // The server performs the change and reports it; do not predict it on the client.
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        Vec3 hit = context.getClickLocation();
        int side = PipeWrenchTarget.sideFor(hit.x, hit.y, hit.z, pos.getX(), pos.getY(), pos.getZ(),
                context.getClickedFace().get3DDataValue());
        Direction direction = Direction.from3DDataValue(side);
        boolean reverse = player != null && player.isShiftKeyDown();
        PipeConnectionMode previous = pipe.getMode(direction);
        PipeConnectionMode next = PipeConnectionRules.cycle(previous, reverse);
        if (!pipe.setMode(direction, next)) {
            return InteractionResult.SUCCESS;
        }
        float pitch = switch (next) {
            case NONE -> 0.707F;
            case NORMAL -> 0.891F;
            case PULL -> 1.189F;
            case PUSH -> 1.414F;
        };
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.35F, pitch);
        if (player != null) {
            // Phase 82: the action bar also reports what the face actually connects to and which
            // mode is in effect, so the automatic opening between two pipes is never a mystery.
            PipeConnectionMode effective = pipe.effectiveMode(direction);
            BlockPos neighbourPos = pos.relative(direction);
            boolean pipeNeighbour = level.getBlockState(neighbourPos).getBlock() instanceof ItemPipeBlock;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                    neighbourPos, direction.getOpposite());
            Component neighbourLabel = Component.translatable(pipeNeighbour
                    ? "message.industrialcore.pipe_neighbour.pipe"
                    : handler != null
                    ? "message.industrialcore.pipe_neighbour.inventory"
                    : "message.industrialcore.pipe_neighbour.none");
            player.displayClientMessage(Component.empty()
                    .append(Component.translatable(
                            "message.industrialcore.pipe_mode",
                            Component.translatable(PipeWrenchTarget.sideKey(side)),
                            Component.translatable(next.translationKey()),
                            Component.translatable(next.descriptionKey())))
                    .append("  •  ")
                    .append(Component.translatable("message.industrialcore.pipe_face",
                            neighbourLabel,
                            Component.translatable(effective.translationKey())))
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("gui.industrialcore.tooltip.wrench").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("gui.industrialcore.tooltip.wrench_sneak")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}

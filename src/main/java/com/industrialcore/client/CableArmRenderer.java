package com.industrialcore.client;

import com.industrialcore.block.CableBlock;
import com.industrialcore.blockentity.CableBlockEntity;
import com.industrialcore.machine.ConnectorStyle;
import com.industrialcore.machine.TubeProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the connector where a cable arm meets a block that is not another cable.
 *
 * <p>The cable model ends flat on the block boundary and the neighbour keeps its own design, so the
 * joint is what adapts: it aims at a machine port centre when there is one, lies flat on a chest or
 * a furnace otherwise, and is not drawn at all against another cable. See
 * {@link ConnectorJoint} for the geometry and {@link PortArmCache} for the per-face cache.</p>
 *
 * <p>The only work per face is one cached enum read; the neighbour lookup happens once per change,
 * not once per frame.</p>
 */
public final class CableArmRenderer implements BlockEntityRenderer<CableBlockEntity> {
    /** Sealing gasket colour, a shade lighter than the pipe joint so the two read apart. */
    private static final float RED = 0.30F;
    private static final float GREEN = 0.33F;
    private static final float BLUE = 0.37F;
    private static final float ALPHA = 0.95F;
    /** Connector design of the current frame; a static read, no per-frame parsing. */
    private static ConnectorStyle style() {
        return ConnectorStyle.active();
    }

    public CableArmRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CableBlockEntity cable, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Level level = cable.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = cable.getBlockState();
        if (!(state.getBlock() instanceof CableBlock)) {
            return;
        }
        PortArmCache cache = cable.armCache();
        // Match the tube width of this tier, exactly as the baked arm models do.
        double armHalf = TubeProfile.armHalf(((CableBlock) state.getBlock()).tier());
        VertexConsumer consumer = null;
        for (Direction direction : Direction.values()) {
            if (!state.getValue(CableBlock.CONNECTION_BY_DIRECTION.get(direction))) {
                continue;
            }
            ConnectorKind kind = cache.resolve(level, cable.getBlockPos(), direction);
            if (kind == ConnectorKind.PIPE) {
                continue;
            }
            if (consumer == null) {
                consumer = buffers.getBuffer(RenderType.debugQuads());
            }
            ConnectorJoint.draw(consumer, pose.last(), direction, kind,
                    cache.anchorX(direction), cache.anchorY(direction), cache.anchorZ(direction),
                    armHalf, style(),
                    RED, GREEN, BLUE, ALPHA);
        }
    }
}

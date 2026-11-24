package com.industrialcore.client;

import com.industrialcore.IndustrialCore;
import com.industrialcore.machine.DrillInterpolation;
import com.industrialcore.machine.MiningPlan;
import com.industrialcore.machine.MiningSync;
import com.industrialcore.machine.QuarryFrame;
import com.industrialcore.preview.PreviewData;
import com.industrialcore.preview.PreviewRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Collection;

/**
 * Client-side only visualisation of the server-authoritative mining area.
 *
 * <p>Nothing is placed in the world: the frame, layer rails and drill head are lines drawn
 * from the synced range/cursor. Removing the machine therefore cannot leave temporary blocks
 * behind, and the drawn drill position always equals the block the server will break next.</p>
 */
@EventBusSubscriber(modid = IndustrialCore.MOD_ID, value = Dist.CLIENT)
public final class QuarryPreviewRenderer {
    private QuarryPreviewRenderer() {}

    /** Leaving a world must not keep preview entries from the previous session. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PreviewRegistry.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Collection<PreviewRegistry.Entry> previews = PreviewRegistry.snapshot();
        if (previews.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());

        for (PreviewRegistry.Entry entry : previews) {
            if (!level.isLoaded(entry.data().machinePos())) {
                continue;
            }
            renderQuarry(poseStack, consumer, entry, gameTime, partialTick);
        }

        buffers.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static void renderQuarry(PoseStack poseStack, VertexConsumer consumer,
                                     PreviewRegistry.Entry entry, long gameTime, float partialTick) {
        PreviewData data = entry.data();
        int facingIndex = data.facing().get3DDataValue();
        int width = Math.max(1, data.width());
        int depth = Math.max(1, data.depth());
        int height = Math.max(1, data.height());

        // One shared world-space definition with the server, so an east/west frame can no longer
        // collapse onto the wrong axis (the Phase 5 renderer mixed up the X/Z components).
        QuarryFrame.Bounds bounds = QuarryFrame.bounds(
                data.machinePos().getX(), data.machinePos().getY(), data.machinePos().getZ(),
                facingIndex, width, depth, height);
        AABB outer = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        float red = data.running() ? 1.0F : 0.85F;
        float green = data.running() ? 0.72F : 0.55F;
        float blue = 0.18F;
        LevelRenderer.renderLineBox(poseStack, consumer, outer, red, green, blue, 0.9F);

        // Layer rails: top, current layer and bottom make the structure readable.
        int currentLayer = 0;
        BlockPos target = null;
        double headX = 0.0D;
        double headY = 0.0D;
        double headZ = 0.0D;
        if (data.volume() > 0) {
            MiningPlan plan = new MiningPlan(width, depth, height);
            int last = plan.volume() - 1;
            int newest = Math.max(0, Math.min(data.cursor(), last));
            // Phase 9: a single progress value drives the frame and the drill head. Both are
            // derived from the confirmed drill index (cursor + banked work), so the frame cannot
            // move at a different rate than the blocks the server processes. Interpolating
            // between two confirmed indices only smooths the update; it never shows progress
            // the server has not reported.
            float progress = DrillInterpolation.progress(gameTime - entry.arrivedAtTick(), partialTick,
                    data.syncIntervalTicks());
            double currentIndex = Math.min(data.drillIndex(), last + 1.0D);
            double previousIndex = Math.min(data.previousDrillIndex(), currentIndex);
            double blended = MiningSync.interpolate(previousIndex, currentIndex, progress);
            int frame = MiningSync.frameIndex(blended, newest, plan.volume());
            currentLayer = plan.localY(frame);
            QuarryFrame.Point targetPoint = QuarryFrame.target(
                    data.machinePos().getX(), data.machinePos().getY(), data.machinePos().getZ(),
                    facingIndex, width, depth, height, frame);
            target = new BlockPos(targetPoint.x(), targetPoint.y(), targetPoint.z());

            double index = Math.max(0.0D, Math.min(last, blended));
            int lower = (int) Math.floor(index);
            int upper = Math.min(last, lower + 1);
            double fraction = index - lower;
            double localX = plan.localX(lower) + (plan.localX(upper) - plan.localX(lower)) * fraction;
            double localZ = plan.localZ(lower) + (plan.localZ(upper) - plan.localZ(lower)) * fraction;
            double localY = plan.localY(lower) + (plan.localY(upper) - plan.localY(lower)) * fraction;
            QuarryFrame.Point origin = QuarryFrame.origin(
                    data.machinePos().getX(), data.machinePos().getY(), data.machinePos().getZ(), facingIndex);
            int right = QuarryFrame.rightOf(facingIndex);
            headX = (origin.x() + QuarryFrame.stepX(right) * localX
                    + QuarryFrame.stepX(facingIndex) * localZ) + 0.5D;
            headY = origin.y() - localY;
            headZ = (origin.z() + QuarryFrame.stepZ(right) * localX
                    + QuarryFrame.stepZ(facingIndex) * localZ) + 0.5D;
        }
        int[] layers = currentLayer == 0 || currentLayer == height - 1
                ? new int[] {0, height - 1}
                : new int[] {0, currentLayer, height - 1};
        for (int layer : layers) {
            double y = QuarryFrame.layerTopY(data.machinePos().getY(), layer);
            line(poseStack, consumer, bounds.minX(), y, bounds.minZ(), bounds.maxX(), y, bounds.minZ(), 0.55F, 0.75F, 0.95F, 0.75F);
            line(poseStack, consumer, bounds.minX(), y, bounds.maxZ(), bounds.maxX(), y, bounds.maxZ(), 0.55F, 0.75F, 0.95F, 0.75F);
            line(poseStack, consumer, bounds.minX(), y, bounds.minZ(), bounds.minX(), y, bounds.maxZ(), 0.55F, 0.75F, 0.95F, 0.75F);
            line(poseStack, consumer, bounds.maxX(), y, bounds.minZ(), bounds.maxX(), y, bounds.maxZ(), 0.55F, 0.75F, 0.95F, 0.75F);
        }

        if (target != null) {
            AABB targetBox = new AABB(target).inflate(0.002D);
            LevelRenderer.renderLineBox(poseStack, consumer, targetBox, 0.2F, 1.0F, 0.85F, 1.0F);
            // Drill head marker: interpolated between confirmed positions, drawn above the layer.
            line(poseStack, consumer, headX, headY + 1.05D, headZ,
                    headX, headY + 1.6D, headZ, 0.2F, 1.0F, 0.85F, 1.0F);
            line(poseStack, consumer, headX - 0.25D, headY + 1.6D, headZ,
                    headX + 0.25D, headY + 1.6D, headZ, 0.2F, 1.0F, 0.85F, 1.0F);
            line(poseStack, consumer, headX, headY + 1.6D, headZ - 0.25D,
                    headX, headY + 1.6D, headZ + 0.25D, 0.2F, 1.0F, 0.85F, 1.0F);
        }
    }

    private static void line(PoseStack poseStack, VertexConsumer consumer,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             float red, float green, float blue, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        consumer.addVertex(matrix, (float) x1, (float) y1, (float) z1)
                .setColor(red, green, blue, alpha)
                .setNormal(0.0F, 1.0F, 0.0F);
        consumer.addVertex(matrix, (float) x2, (float) y2, (float) z2)
                .setColor(red, green, blue, alpha)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}

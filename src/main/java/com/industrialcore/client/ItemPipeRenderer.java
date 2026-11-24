package com.industrialcore.client;

import com.industrialcore.block.ItemPipeBlock;
import com.industrialcore.blockentity.ItemPipeBlockEntity;
import com.industrialcore.machine.PipeConnectionMode;
import com.industrialcore.machine.ConnectorStyle;
import com.industrialcore.machine.TubeProfile;
import com.industrialcore.machine.PipeMarkerStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws everything a pipe shows on top of its baked block model: the face markers set with the
 * wrench and the stack that is travelling through the tube.
 *
 * <h2>Face markers</h2>
 *
 * <p>A coloured collar is drawn around the arm of a configured face - green for
 * {@code PULL} (the pipe extracts from the inventory on that face), orange for {@code PUSH}
 * (it inserts into it), grey for {@code NONE} (the face is closed) and nothing for
 * {@code NORMAL}, so a normally configured factory stays plain. The mode comes from the block
 * entity display tag, which the server already syncs, and the pipe block state stays at
 * 64 combinations: mirroring the modes into the state would have multiplied that by 4<sup>6</sup>
 * and made the client bake a million models at start-up (the Phase 22 regression).</p>
 *
 * <p>Because the marker is renderer output rather than a block state, a wrench change never
 * rebuilds the chunk mesh, and nothing is computed while the mode does not change: each render
 * only reads six enum values and emits at most six small boxes.</p>
 *
 * <h2>Travelling stack</h2>
 *
 * <p>The server reports the entry side, the exit side and the progress through this block. The
 * renderer moves the item along {@code entry face -> centre -> exit face}; corners therefore pass
 * through the tube centre instead of teleporting, and a straight run stays on the tube axis. The
 * progress is extrapolated by the ticks since the update arrived, so a 2-tick sync interval still
 * looks continuous. A blocked stack is pinned at the exit (never shown drifting into a full
 * destination). Nothing here creates, moves or removes an item; it only draws a received copy.</p>
 */
public final class ItemPipeRenderer implements BlockEntityRenderer<ItemPipeBlockEntity> {
    /** Vertical centre of the pipe core (the core spans 0.25 .. 0.75). */
    private static final float ITEM_Y = 0.5F;
    /** Compact enough to travel visibly inside the 5.0 px hollow translucent glass tube. */
    private static final float ITEM_SCALE = 0.25F;
    /** Distance from the block centre to the centre of a face. */
    private static final float FACE_OFFSET = 0.5F;
    /** Opaque enough to read against any tier texture, translucent enough not to hide the arm. */
    private static final float MARKER_ALPHA = 0.75F;
    /** Joint gasket colour: darker than the cable one, so cable and pipe joints read apart. */
    private static final float ARM_RED = 0.21F;
    private static final float ARM_GREEN = 0.23F;
    private static final float ARM_BLUE = 0.26F;
    private static final float ARM_ALPHA = 0.95F;
    /**
     * Last drawn position of the travelling stack, per pipe. Renderers are shared by every block
     * entity of a type, so the state has to be keyed by position; the cap keeps a long session
     * from growing the map without bound when pipes are unloaded.
     */
    private static final int MAX_SMOOTH_ENTRIES = 512;
    private static final Map<BlockPos, float[]> DRAWN_POSITION = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, float[]> eldest) {
            return size() > MAX_SMOOTH_ENTRIES;
        }
    };

    /** Connector design of the current frame; a static read, no per-frame parsing. */
    private static ConnectorStyle style() {
        return ConnectorStyle.active();
    }

    public ItemPipeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ItemPipeBlockEntity pipe, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        renderPortArms(pipe, pose, buffers);
        renderFaceMarkers(pipe, pose, buffers);
        renderTravellingStack(pipe, partialTick, pose, buffers);
    }

    /**
     * Draws the connector on every connected face.
     *
     * <p>What is drawn follows the neighbour, not the pipe: another pipe needs nothing (the two
     * models meet), a machine port gets a joint bent to the port centre, and anything else that
     * connects - a chest, a furnace, a machine from another mod - gets a joint flat on its surface.
     * The connector never enters the neighbour, so no block has to be modelled around pipes.</p>
     */
    private static void renderPortArms(ItemPipeBlockEntity pipe, PoseStack pose,
                                       MultiBufferSource buffers) {
        Level level = pipe.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = pipe.getBlockState();
        if (!(state.getBlock() instanceof ItemPipeBlock)) {
            return;
        }
        PortArmCache cache = pipe.armCache();
        // The sleeve has to match the tube the model draws, so take the width from the tier.
        double armHalf = TubeProfile.armHalf(((ItemPipeBlock) state.getBlock()).tier());
        VertexConsumer consumer = null;
        for (Direction direction : Direction.values()) {
            if (!state.getValue(ItemPipeBlock.CONNECTION_BY_DIRECTION.get(direction))) {
                continue;
            }
            ConnectorKind kind = cache.resolve(level, pipe.getBlockPos(), direction);
            if (kind == ConnectorKind.PIPE) {
                continue;
            }
            if (consumer == null) {
                consumer = buffers.getBuffer(RenderType.debugQuads());
            }
            ConnectorJoint.draw(consumer, pose.last(), direction, kind,
                    cache.anchorX(direction), cache.anchorY(direction), cache.anchorZ(direction),
                    armHalf, style(),
                    ARM_RED, ARM_GREEN, ARM_BLUE, ARM_ALPHA);
        }
    }

    // ------------------------------------------------------------------
    // Face markers
    // ------------------------------------------------------------------

    private static void renderFaceMarkers(ItemPipeBlockEntity pipe, PoseStack pose,
                                          MultiBufferSource buffers) {
        BlockState state = pipe.getBlockState();
        if (!(state.getBlock() instanceof ItemPipeBlock)) {
            return;
        }
        ItemPipeBlockEntity.ClientVisual visual = pipe.getClientVisual();
        if (visual == null) {
            return;
        }
        Level level = pipe.getLevel();
        PortArmCache cache = pipe.armCache();
        VertexConsumer consumer = null;
        for (Direction direction : Direction.values()) {
            if (!state.getValue(ItemPipeBlock.CONNECTION_BY_DIRECTION.get(direction))) {
                // No arm on this face, so there is nothing to mark.
                continue;
            }
            PipeConnectionMode mode = visual.mode(direction);
            int color = PipeMarkerStyle.color(mode);
            if (color == 0) {
                continue;
            }
            // Two conduits already meet seamlessly: a grey box here was the "mysterious outline"
            // seen in game. Only an explicit wrench setting (PULL/PUSH) may mark a pipe-to-pipe
            // face, with the slim direction ring below.
            if (level != null && mode != PipeConnectionMode.PULL
                    && cache.resolve(level, pipe.getBlockPos(), direction) == ConnectorKind.PIPE) {
                continue;
            }
            if (consumer == null) {
                consumer = buffers.getBuffer(RenderType.debugQuads());
            }
            drawCollar(consumer, pose.last(), direction, color);
        }
    }

    /**
     * Emits a thin collar around the arm of one face. The collar is slightly wider than the arm
     * and sits just inside the block boundary, so it neither z-fights with the arm nor sticks out
     * into the neighbouring block.
     */
    private static void drawCollar(VertexConsumer consumer, PoseStack.Pose pose,
                                   Direction direction, int color) {
        float near = PipeMarkerStyle.COLLAR_NEAR;
        float far = PipeMarkerStyle.COLLAR_FAR;
        float low = 0.5F - PipeMarkerStyle.COLLAR_HALF;
        float high = 0.5F + PipeMarkerStyle.COLLAR_HALF;
        float min;
        float max;
        if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            min = 1.0F - far;
            max = 1.0F - near;
        } else {
            min = near;
            max = far;
        }
        float x0;
        float y0;
        float z0;
        float x1;
        float y1;
        float z1;
        switch (direction.getAxis()) {
            case X -> {
                x0 = min;
                x1 = max;
                y0 = low;
                y1 = high;
                z0 = low;
                z1 = high;
            }
            case Y -> {
                x0 = low;
                x1 = high;
                y0 = min;
                y1 = max;
                z0 = low;
                z1 = high;
            }
            default -> {
                x0 = low;
                x1 = high;
                y0 = low;
                y1 = high;
                z0 = min;
                z1 = max;
            }
        }
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        box(consumer, pose, x0, y0, z0, x1, y1, z1, red, green, blue, MARKER_ALPHA);
    }

    private static void box(VertexConsumer consumer, PoseStack.Pose pose,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float red, float green, float blue, float alpha) {
        quad(consumer, pose, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, red, green, blue, alpha);
        quad(consumer, pose, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, red, green, blue, alpha);
        quad(consumer, pose, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, red, green, blue, alpha);
        quad(consumer, pose, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, red, green, blue, alpha);
        quad(consumer, pose, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, red, green, blue, alpha);
        quad(consumer, pose, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float red, float green, float blue, float alpha) {
        consumer.addVertex(pose, ax, ay, az).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, bx, by, bz).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, cx, cy, cz).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, dx, dy, dz).setColor(red, green, blue, alpha);
    }

    // ------------------------------------------------------------------
    // Travelling stack
    // ------------------------------------------------------------------

    private static void renderTravellingStack(ItemPipeBlockEntity pipe, float partialTick,
                                              PoseStack pose, MultiBufferSource buffers) {
        ItemPipeBlockEntity.ClientVisual visual = pipe.getClientVisual();
        if (visual == null || visual.isEmpty()) {
            // Nothing to draw: forget the filter state so the next stack starts at its own entry.
            DRAWN_POSITION.remove(pipe.getBlockPos());
            return;
        }
        Level level = pipe.getLevel();
        if (level == null) {
            return;
        }
        ItemStack stack = visual.stack();
        if (stack.isEmpty()) {
            DRAWN_POSITION.remove(pipe.getBlockPos());
            return;
        }

        float progress = visual.progress();
        if (!visual.blocked() && visual.travelTicks() > 1) {
            double elapsed = level.getGameTime() - visual.receivedTick() + partialTick;
            progress = (float) Math.min(1.0D, progress + elapsed / visual.travelTicks());
        }
        progress = Math.max(0.0F, Math.min(1.0F, progress));

        float entryX = 0.5F;
        float entryY = ITEM_Y;
        float entryZ = 0.5F;
        Direction from = visual.from();
        if (from != null) {
            entryX += from.getStepX() * FACE_OFFSET;
            entryY += from.getStepY() * FACE_OFFSET;
            entryZ += from.getStepZ() * FACE_OFFSET;
        }
        float exitX = 0.5F;
        float exitY = ITEM_Y;
        float exitZ = 0.5F;
        Direction to = visual.to();
        if (to != null) {
            exitX += to.getStepX() * FACE_OFFSET;
            exitY += to.getStepY() * FACE_OFFSET;
            exitZ += to.getStepZ() * FACE_OFFSET;
        }

        // One quadratic curve entry -> centre -> exit. Straight runs stay on the axis (the control
        // point is on it), corners bend instead of changing direction at a single point.
        float targetX = PipeSmoothMotion.path(entryX, 0.5F, exitX, progress);
        float targetY = PipeSmoothMotion.path(entryY, ITEM_Y, exitY, progress);
        float targetZ = PipeSmoothMotion.path(entryZ, 0.5F, exitZ, progress);

        // Pull the drawn position towards the target instead of snapping to it. The time delta is
        // in ticks (game time + partial tick), so the glide is identical at 30 and 240 fps.
        BlockPos key = pipe.getBlockPos();
        double now = level.getGameTime() + partialTick;
        float[] previous = DRAWN_POSITION.get(key);
        float x;
        float y;
        float z;
        if (previous == null) {
            x = targetX;
            y = targetY;
            z = targetZ;
        } else {
            double delta = now - previous[3];
            x = PipeSmoothMotion.approach(previous[0], targetX, delta);
            y = PipeSmoothMotion.approach(previous[1], targetY, delta);
            z = PipeSmoothMotion.approach(previous[2], targetZ, delta);
        }
        DRAWN_POSITION.put(key, new float[] {x, y, z, (float) now});

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        int packedLight = LevelRenderer.getLightColor(level, pipe.getBlockPos());

        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
    }

}

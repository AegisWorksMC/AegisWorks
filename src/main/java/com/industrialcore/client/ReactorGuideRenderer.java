package com.industrialcore.client;

import com.industrialcore.block.ReactorCoreCellBlock;
import com.industrialcore.reactor.ReactorGuideLogic;
import com.industrialcore.reactor.ReactorGuideLogic.CellSample;
import com.industrialcore.reactor.ReactorGuideLogic.CellState;
import com.industrialcore.reactor.ReactorGuideLogic.GuideProgress;
import com.industrialcore.reactor.ReactorMultiblockLogic.GuideCell;
import com.industrialcore.reactor.ReactorMultiblockLogic.IntPos;
import com.industrialcore.reactor.ReactorMultiblockLogic.PartKind;
import com.industrialcore.reactor.ReactorMultiblockLogic.ReactorType;
import com.industrialcore.reactor.ReactorWorldHelper;
import com.industrialcore.registry.ModBlocks;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Optional;

/**
 * Phase 103.5 / 108: Factory-Sim 3D Multiblock Assembly Guide &amp; Compact Progress HUD.
 *
 * <ul>
 *   <li>Supports variable guide presets ({@code 3x3x3..5x5x5} Nuclear, {@code 5x5x5..7x7x7} Singularity).</li>
 *   <li>Layer-by-layer slice toggle ({@code ALL} &harr; {@code Layer 1..N}) via Shift+Right-Click on
 *       controller, {@code G} hotkey, or GUI button right-click.</li>
 *   <li>Vivid translucent red warning box + wireframe on misplaced non-air blocks.</li>
 *   <li>Compact top-left missing-block HUD overlay (zero overlap with AR HUD) + automatic
 *       "Construction Complete!" toast when the multiblock forms.</li>
 * </ul>
 */
public final class ReactorGuideRenderer {
    private static final double MAX_DISTANCE_SQR = 48.0D * 48.0D;
    private static final float GHOST_ALPHA_ALL = 0.44F;
    private static final float GHOST_ALPHA_LAYER = 0.62F;

    @Nullable
    private static BlockPos activePos;
    @Nullable
    private static Direction activeFacing;
    @Nullable
    private static ReactorType activeType;
    @Nullable
    private static ResourceKey<Level> activeDim;
    private static int activeSize = 3;
    private static int layerMode = ReactorGuideLogic.ALL_LAYERS;
    private static int checkTick = 0;

    // Auto-complete toast shown briefly after multiblock formation
    private static long completeToastUntilMs = 0L;
    private static String completeToastDims = "";

    private ReactorGuideRenderer() {}

    public static boolean isGuideActiveFor(@Nullable BlockPos pos) {
        return pos != null && pos.equals(activePos);
    }

    public static boolean isAnyGuideActive() {
        return activePos != null;
    }

    public static int getLayerMode() {
        return layerMode;
    }

    public static int getGuideSize() {
        return activeSize;
    }

    public static void setLayerMode(int mode) {
        if (activePos == null) {
            return;
        }
        if (mode < 0 || mode >= ReactorGuideLogic.layerCount(activeSize)) {
            layerMode = ReactorGuideLogic.ALL_LAYERS;
        } else {
            layerMode = mode;
        }
    }

    public static void setGuideSize(int size) {
        if (activePos == null || activeType == null) {
            return;
        }
        activeSize = ReactorGuideLogic.clampGuideSize(activeType, size);
        if (layerMode >= ReactorGuideLogic.layerCount(activeSize)) {
            layerMode = ReactorGuideLogic.ALL_LAYERS;
        }
    }

    /** Direct ON/OFF toggle used by GUI button left-click. */
    public static boolean toggleGuide(BlockPos pos, Direction facing, ReactorType type) {
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || mc == null || mc.level == null) {
            clear();
            return false;
        }
        if (pos.equals(activePos)) {
            clear();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.industrialcore.reactor.guide_off")
                                .withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }
        activate(pos, facing, type, mc);
        return true;
    }

    /**
     * Shift+Right-Click handler on the controller block:
     * OFF -> ON (All layers) -> Layer 1 -> Layer 2 -> ... -> Layer N -> OFF.
     */
    public static boolean handleShiftRightClick(BlockPos pos, Direction facing, ReactorType type) {
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || mc == null || mc.level == null) {
            clear();
            return false;
        }
        if (!pos.equals(activePos)) {
            activate(pos, facing, type, mc);
            SoundDirector.playClick();
            return true;
        }
        int next = ReactorGuideLogic.nextShiftClickLayerOrOff(layerMode, ReactorGuideLogic.layerCount(activeSize));
        if (next == Integer.MIN_VALUE) {
            clear();
            SoundDirector.playClick();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.industrialcore.reactor.guide_off")
                                .withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }
        layerMode = next;
        SoundDirector.playClick();
        notifyLayerStatus(mc);
        return true;
    }

    /** Cycles layer slice (ALL -> Layer 1..N -> ALL) via hotkey or GUI right-click. */
    public static void cycleLayer() {
        Minecraft mc = Minecraft.getInstance();
        if (activePos == null || mc == null) {
            return;
        }
        layerMode = ReactorGuideLogic.nextLayerMode(layerMode, ReactorGuideLogic.layerCount(activeSize));
        SoundDirector.playClick();
        notifyLayerStatus(mc);
    }

    /** Cycles guide cube size preset (Nuclear 3..5, Singularity 5..7) via Shift+G or GUI Shift+Right-Click. */
    public static void cycleSize() {
        Minecraft mc = Minecraft.getInstance();
        if (activePos == null || activeType == null || mc == null) {
            return;
        }
        activeSize = ReactorGuideLogic.nextGuideSize(activeType, activeSize);
        if (layerMode >= ReactorGuideLogic.layerCount(activeSize)) {
            layerMode = ReactorGuideLogic.ALL_LAYERS;
        }
        SoundDirector.playClick();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("message.industrialcore.reactor.guide_size",
                                    activeSize + "x" + activeSize + "x" + activeSize)
                            .withStyle(ChatFormatting.AQUA), true);
        }
    }

    private static void activate(BlockPos pos, Direction facing, ReactorType type, Minecraft mc) {
        activePos = pos.immutable();
        activeFacing = facing;
        activeType = type;
        activeDim = mc.level.dimension();
        activeSize = ReactorGuideLogic.defaultGuideSize(type);
        layerMode = ReactorGuideLogic.ALL_LAYERS;
        checkTick = 0;
        completeToastUntilMs = 0L;
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("message.industrialcore.reactor.guide_on_v2",
                                    activeSize + "x" + activeSize + "x" + activeSize)
                            .withStyle(ChatFormatting.AQUA), true);
        }
    }

    private static void notifyLayerStatus(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        if (layerMode == ReactorGuideLogic.ALL_LAYERS) {
            mc.player.displayClientMessage(
                    Component.translatable("message.industrialcore.reactor.guide_layer_all",
                                    ReactorGuideLogic.layerCount(activeSize))
                            .withStyle(ChatFormatting.AQUA), true);
        } else {
            mc.player.displayClientMessage(
                    Component.translatable("message.industrialcore.reactor.guide_layer_single",
                                    layerMode + 1, ReactorGuideLogic.layerCount(activeSize))
                            .withStyle(ChatFormatting.GOLD), true);
        }
    }

    public static void clear() {
        activePos = null;
        activeFacing = null;
        activeType = null;
        activeDim = null;
        activeSize = 3;
        layerMode = ReactorGuideLogic.ALL_LAYERS;
        checkTick = 0;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (activePos == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            clear();
            return;
        }
        if (activeDim == null || !activeDim.equals(mc.level.dimension())) {
            clear();
            return;
        }
        if (Vec3.atCenterOf(activePos).distanceToSqr(mc.player.position()) > MAX_DISTANCE_SQR) {
            clear();
            return;
        }
        checkTick++;
        if (checkTick < 8) {
            return;
        }
        checkTick = 0;

        PartKind ctrlKind = ReactorWorldHelper.classifyState(mc.level.getBlockState(activePos));
        boolean matchesCtrl = (activeType == ReactorType.NUCLEAR && ctrlKind == PartKind.NUCLEAR_CTRL)
                || (activeType == ReactorType.SINGULARITY && ctrlKind == PartKind.SINGULARITY_CTRL);
        if (!matchesCtrl) {
            clear();
            return;
        }
        var res = ReactorWorldHelper.validateInWorld(mc.level, activeType, activePos, activeFacing);
        if (res.formed()) {
            completeToastDims = res.width() + "x" + res.height() + "x" + res.depth();
            completeToastUntilMs = Util.getMillis() + ReactorGuideLogic.COMPLETE_TOAST_MS;
            SoundDirector.playComplete(1, 1);
            mc.player.displayClientMessage(
                    Component.translatable("message.industrialcore.reactor.guide_complete", completeToastDims)
                            .withStyle(ChatFormatting.GREEN), true);
            clear();
        }
    }

    private static List<GuideCell> currentAllCells() {
        if (activePos == null || activeFacing == null) {
            return List.of();
        }
        return ReactorGuideLogic.buildGuide(
                new IntPos(activePos.getX(), activePos.getY(), activePos.getZ()),
                ReactorWorldHelper.toFacing2D(activeFacing),
                activeSize);
    }

    private static List<GuideCell> currentVisibleCells(List<GuideCell> allCells) {
        if (activePos == null) {
            return List.of();
        }
        int minY = ReactorGuideLogic.guideMinY(activePos.getY());
        return ReactorGuideLogic.filterByLayer(allCells, minY, layerMode);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (activePos == null || activeFacing == null || activeType == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }

        List<GuideCell> allCells = currentAllCells();
        List<GuideCell> visibleCells = currentVisibleCells(allCells);
        if (visibleCells.isEmpty()) {
            return;
        }

        boolean singleLayer = layerMode != ReactorGuideLogic.ALL_LAYERS;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        BlockState casingState = ModBlocks.REACTOR_CASING.get().defaultBlockState();
        BlockState glassState = ModBlocks.REACTOR_GLASS.get().defaultBlockState();
        BlockState coreState = ModBlocks.REACTOR_CORE_CELL.get().defaultBlockState()
                .setValue(ReactorCoreCellBlock.LIT, true);

        BakedModel casingModel = dispatcher.getBlockModel(casingState);
        BakedModel glassModel = dispatcher.getBlockModel(glassState);
        BakedModel coreModel = dispatcher.getBlockModel(coreState);

        float pulse = 0.90F + 0.10F * (float) Math.sin((Util.getMillis() % 60_000L) / 260.0D);
        float baseAlpha = singleLayer ? GHOST_ALPHA_LAYER : GHOST_ALPHA_ALL;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Pass 1: Actual BlockState models rendered as translucent ghosts for MISSING (air) cells
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        VertexConsumer translucentBase = buffers.getBuffer(RenderType.translucent());
        boolean renderedAnyGhost = false;

        for (GuideCell cell : visibleCells) {
            cursor.set(cell.x(), cell.y(), cell.z());
            BlockState existingState = mc.level.getBlockState(cursor);
            CellState st = ReactorGuideLogic.classifyCell(cell.role(),
                    new CellSample(existingState.isAir(), ReactorWorldHelper.classifyState(existingState)));
            if (st != CellState.MISSING) {
                continue;
            }

            BlockState ghostState;
            BakedModel ghostModel;
            float alpha;
            switch (cell.role()) {
                case FRAME_CASING -> {
                    ghostState = casingState;
                    ghostModel = casingModel;
                    alpha = baseAlpha * pulse;
                }
                case WALL_GLASS -> {
                    ghostState = glassState;
                    ghostModel = glassModel;
                    alpha = Math.min(0.75F, (baseAlpha + 0.04F) * pulse);
                }
                default -> { // INTERIOR_CORE
                    ghostState = coreState;
                    ghostModel = coreModel;
                    alpha = Math.min(0.80F, (baseAlpha + 0.06F) * pulse);
                }
            }

            pose.pushPose();
            pose.translate(cell.x() - cam.x + 0.5D, cell.y() - cam.y + 0.5D, cell.z() - cam.z + 0.5D);
            pose.scale(0.996F, 0.996F, 0.996F);
            pose.translate(-0.5D, -0.5D, -0.5D);

            VertexConsumer ghostConsumer = new AlphaTintedVertexConsumer(translucentBase, alpha);
            dispatcher.getModelRenderer().renderModel(
                    pose.last(), ghostConsumer, ghostState, ghostModel,
                    1.0F, 1.0F, 1.0F,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, RenderType.translucent());
            pose.popPose();
            renderedAnyGhost = true;
        }
        if (renderedAnyGhost) {
            buffers.endBatch(RenderType.translucent());
        }

        // Pass 2: Crisp cyan wireframes on single-layer missing cells + vivid crimson highlight on MISPLACED blocks
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder fillBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean anyFill = false;
        boolean anyMisplaced = false;
        boolean anySliceMissing = false;

        for (GuideCell cell : visibleCells) {
            cursor.set(cell.x(), cell.y(), cell.z());
            BlockState existingState = mc.level.getBlockState(cursor);
            CellState st = ReactorGuideLogic.classifyCell(cell.role(),
                    new CellSample(existingState.isAir(), ReactorWorldHelper.classifyState(existingState)));
            if (st == CellState.MISPLACED) {
                emitTranslucentBox(fillBuf, mat,
                        cell.x() - 0.008F, cell.y() - 0.008F, cell.z() - 0.008F,
                        cell.x() + 1.008F, cell.y() + 1.008F, cell.z() + 1.008F,
                        1.00F, 0.12F, 0.16F, 0.34F * pulse);
                anyFill = true;
                anyMisplaced = true;
            } else if (st == CellState.MISSING && singleLayer) {
                anySliceMissing = true;
            }
        }
        if (anyFill) {
            var mesh = fillBuf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        if (anyMisplaced || anySliceMissing) {
            BufferBuilder edgeBuf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (GuideCell cell : visibleCells) {
                cursor.set(cell.x(), cell.y(), cell.z());
                BlockState existingState = mc.level.getBlockState(cursor);
                CellState st = ReactorGuideLogic.classifyCell(cell.role(),
                        new CellSample(existingState.isAir(), ReactorWorldHelper.classifyState(existingState)));
                if (st == CellState.MISPLACED) {
                    emitWireBox(edgeBuf, mat,
                            cell.x() - 0.010F, cell.y() - 0.010F, cell.z() - 0.010F,
                            cell.x() + 1.010F, cell.y() + 1.010F, cell.z() + 1.010F,
                            0.024F, 1.00F, 0.22F, 0.26F, 0.96F * pulse);
                } else if (st == CellState.MISSING && singleLayer) {
                    emitWireBox(edgeBuf, mat,
                            cell.x() + 0.002F, cell.y() + 0.002F, cell.z() + 0.002F,
                            cell.x() + 0.998F, cell.y() + 0.998F, cell.z() + 0.998F,
                            0.012F, 0.22F, 0.92F, 1.00F, 0.72F * pulse);
                }
            }
            var edgeMesh = edgeBuf.build();
            if (edgeMesh != null) {
                BufferUploader.drawWithShader(edgeMesh);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /**
     * Renders:
     * <ol>
     *   <li>Compact top-left Missing-Block HUD overlay (within 16 blocks, zero overlap with AR HUD).</li>
     *   <li>Auto-complete notification toast for 4 seconds right after multiblock formation.</li>
     *   <li>Crosshair-aimed block placement / wrong-block hint pill.</li>
     * </ol>
     */
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null
                || mc.screen != null || mc.options.hideGui) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;

        long nowMs = Util.getMillis();
        if (activePos == null) {
            if (nowMs < completeToastUntilMs) {
                renderCompleteToast(g, font, nowMs);
            }
            return;
        }
        if (activeFacing == null || activeType == null) {
            return;
        }

        List<GuideCell> allCells = currentAllCells();
        if (allCells.isEmpty()) {
            return;
        }
        List<GuideCell> visibleCells = currentVisibleCells(allCells);

        // 1. Compact Top-Left Missing-Block Count HUD (within 16 blocks of controller)
        double distSq = Vec3.atCenterOf(activePos).distanceToSqr(mc.player.position());
        if (ReactorGuideLogic.isWithinHudRange(distSq)) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            ReactorGuideLogic.CellInspector inspector = (x, y, z) -> {
                cursor.set(x, y, z);
                BlockState bs = mc.level.getBlockState(cursor);
                return new CellSample(bs.isAir(), ReactorWorldHelper.classifyState(bs));
            };
            GuideProgress totalProg = ReactorGuideLogic.evaluate(allCells, inspector);
            GuideProgress layerProg = layerMode == ReactorGuideLogic.ALL_LAYERS
                    ? totalProg
                    : ReactorGuideLogic.evaluate(visibleCells, inspector);
            renderProgressHud(g, font, totalProg, layerProg);
        }

        // 2. Crosshair target slot hint pill (checks visible cells on active layer)
        renderAimHintPill(g, font, mc, visibleCells);
    }

    private static void renderProgressHud(GuiGraphics g, Font font,
                                          GuideProgress totalProg, GuideProgress layerProg) {
        int x = 8;
        int y = 8;
        int w = 188;
        boolean sliced = layerMode != ReactorGuideLogic.ALL_LAYERS;
        int totalLayers = ReactorGuideLogic.layerCount(activeSize);

        // Calculate exact height dynamically so every row fits with zero overflow
        int rows = 3; // Casing, Glass, Core
        boolean showMisplaced = totalProg.misplacedCount() > 0;
        int h = 42 + rows * 16 + (showMisplaced ? 14 : 0) + 14;

        // Translucent dark cyber-slate panel + cyan neon border
        int borderCol = showMisplaced ? 0xFFFF4A58 : 0xFF38D4FF;
        g.fill(x, y, x + w, y + h, 0xCC060E18);
        g.fill(x, y, x + w, y + 1, borderCol);
        g.fill(x, y + h - 1, x + w, y + h, borderCol);
        g.fill(x, y, x + 1, y + h, borderCol);
        g.fill(x + w - 1, y, x + w, y + h, borderCol);
        // Header gradient bar
        g.fill(x + 1, y + 1, x + w - 1, y + 15, 0xFF0C2033);
        g.fill(x + 1, y + 15, x + w - 1, y + 16, 0xFF1F4E6E);

        // Title + size badge + completion %
        String sizeTag = activeSize + "x" + activeSize + "x" + activeSize;
        Component title = Component.translatable("gui.industrialcore.reactor.hud_title", sizeTag);
        String pctStr = totalProg.completionPercent() + "%";
        int pctW = font.width(pctStr);
        g.drawString(font, MachineTheme.fit(font, title, w - pctW - 14),
                x + 5, y + 4, 0xFF8BEFFF, false);
        g.drawString(font, pctStr, x + w - pctW - 5, y + 4,
                totalProg.isComplete() ? 0xFF4BFFB2 : 0xFFFFD166, false);

        // 3px progress bar
        int barX = x + 5;
        int barY = y + 19;
        int barW = w - 10;
        g.fill(barX, barY, barX + barW, barY + 3, 0xFF0B1622);
        int fillW = (int) Math.round(barW * (totalProg.completionPercent() / 100.0D));
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + 3,
                    showMisplaced ? 0xFFFFB84D : 0xFF38D4FF);
        }

        // Active layer row
        Component layerLabel = sliced
                ? Component.translatable("gui.industrialcore.reactor.hud_layer_single", layerMode + 1, totalLayers)
                : Component.translatable("gui.industrialcore.reactor.hud_layer_all", totalLayers);
        g.drawString(font, MachineTheme.fit(font, layerLabel, w - 10),
                x + 5, y + 26, sliced ? 0xFFFFD166 : 0xFFB8DCEB, false);
        g.fill(x + 4, y + 37, x + w - 4, y + 38, 0xFF183145);

        // Missing-block rows with 16x16 block icons
        int ry = y + 41;
        ry = drawMissingRow(g, font, x + 5, ry, w - 10,
                new ItemStack(ModBlocks.REACTOR_CASING.get()),
                Component.translatable("block.industrialcore.reactor_casing"),
                totalProg.missingCasing(), sliced ? layerProg.missingCasing() : -1);
        ry = drawMissingRow(g, font, x + 5, ry, w - 10,
                new ItemStack(ModBlocks.REACTOR_GLASS.get()),
                Component.translatable("block.industrialcore.reactor_glass"),
                totalProg.missingGlass(), sliced ? layerProg.missingGlass() : -1);
        ry = drawMissingRow(g, font, x + 5, ry, w - 10,
                new ItemStack(ModBlocks.REACTOR_CORE_CELL.get()),
                Component.translatable("block.industrialcore.reactor_core_cell"),
                totalProg.missingCore(), sliced ? layerProg.missingCore() : -1);

        if (showMisplaced) {
            g.fill(x + 4, ry, x + w - 4, ry + 12, 0x88420C14);
            Component warn = Component.translatable(
                    "gui.industrialcore.reactor.hud_misplaced", totalProg.misplacedCount());
            g.drawString(font, MachineTheme.fit(font, warn, w - 14),
                    x + 7, ry + 2, 0xFFFF7A85, false);
            ry += 14;
        }

        // Bottom compact control legend
        Component hint = Component.translatable("gui.industrialcore.reactor.hud_controls");
        g.drawString(font, MachineTheme.fit(font, hint, w - 10),
                x + 5, y + h - 11, 0xFF6D8AA0, false);
    }

    private static int drawMissingRow(GuiGraphics g, Font font, int x, int y, int w,
                                      ItemStack icon, Component name, int totalMissing, int layerMissing) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();

        Component countComp;
        int countCol;
        if (totalMissing <= 0) {
            countComp = Component.translatable("gui.industrialcore.reactor.hud_done");
            countCol = 0xFF4BFFB2;
        } else if (layerMissing >= 0) {
            countComp = Component.translatable("gui.industrialcore.reactor.hud_remain_layer",
                    totalMissing, layerMissing);
            countCol = layerMissing > 0 ? 0xFFFFD166 : 0xFF8BEFFF;
        } else {
            countComp = Component.translatable("gui.industrialcore.reactor.hud_remain", totalMissing);
            countCol = 0xFFE8FAFF;
        }
        int cw = font.width(countComp);
        g.drawString(font, MachineTheme.fit(font, name, Math.max(24, w - cw - 18)),
                x + 14, y + 2, totalMissing <= 0 ? 0xFF7B9EB0 : 0xFFE2F1F8, false);
        g.drawString(font, countComp, x + w - cw, y + 2, countCol, false);
        return y + 16;
    }

    private static void renderCompleteToast(GuiGraphics g, Font font, long nowMs) {
        long rem = completeToastUntilMs - nowMs;
        if (rem <= 0L) {
            return;
        }
        float alpha = rem < 600L ? (rem / 600.0F) : 1.0F;
        int aBg = Math.max(20, Math.round(0xCC * alpha));
        int aFg = Math.max(40, Math.round(0xFF * alpha));

        Component msg = Component.translatable("message.industrialcore.reactor.guide_complete", completeToastDims);
        int x = 8;
        int y = 8;
        int w = Math.min(220, font.width(msg) + 16);
        int h = 20;
        int border = (aFg << 24) | 0x3BF5A8;
        g.fill(x, y, x + w, y + h, (aBg << 24) | 0x071C15);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
        g.drawString(font, MachineTheme.fit(font, msg, w - 12),
                x + 6, y + 6, (aFg << 24) | 0x9BFFCE, false);
    }

    private static void renderAimHintPill(GuiGraphics g, Font font, Minecraft mc, List<GuideCell> visibleCells) {
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 rayEnd = eye.add(look.scale(8.0D));

        GuideCell bestCell = null;
        boolean bestWrong = false;
        double bestDistSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (GuideCell cell : visibleCells) {
            cursor.set(cell.x(), cell.y(), cell.z());
            BlockState st = mc.level.getBlockState(cursor);
            CellState eval = ReactorGuideLogic.classifyCell(cell.role(),
                    new CellSample(st.isAir(), ReactorWorldHelper.classifyState(st)));
            if (eval == CellState.SATISFIED) {
                continue;
            }
            AABB box = new AABB(cell.x(), cell.y(), cell.z(), cell.x() + 1.0D, cell.y() + 1.0D, cell.z() + 1.0D);
            Optional<Vec3> hit = box.clip(eye, rayEnd);
            if (hit.isPresent()) {
                double d2 = eye.distanceToSqr(hit.get());
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    bestCell = cell;
                    bestWrong = (eval == CellState.MISPLACED);
                }
            }
        }
        if (bestCell == null) {
            return;
        }

        Component blockName = Component.translatable("block.industrialcore." + bestCell.expectedBlockId());
        Component label = Component.translatable(
                bestWrong ? "gui.industrialcore.reactor.guide_hud_wrong"
                        : "gui.industrialcore.reactor.guide_hud_place",
                blockName);

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int tw = font.width(label);
        int pw = tw + 12;
        int px = (sw - pw) / 2;
        int py = sh / 2 + 16;
        int border = bestWrong ? 0xFFFF4A58 : 0xFF42F5FF;
        int textCol = bestWrong ? 0xFFFF9AA2 : 0xFFE8FAFF;

        g.fill(px, py, px + pw, py + 14, 0xC406101B);
        g.fill(px, py, px + pw, py + 1, border);
        g.fill(px, py + 13, px + pw, py + 14, border);
        g.fill(px, py, px + 1, py + 14, border);
        g.fill(px + pw - 1, py, px + pw, py + 14, border);
        g.drawString(font, label, px + 6, py + 3, textCol, false);
    }

    /** Wraps a translucent {@link VertexConsumer} to multiply emitted vertex alpha cleanly. */
    private static final class AlphaTintedVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alphaScale;

        private AlphaTintedVertexConsumer(VertexConsumer delegate, float alphaScale) {
            this.delegate = delegate;
            this.alphaScale = Math.max(0.05F, Math.min(1.0F, alphaScale));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            int scaledAlpha = Math.max(1, Math.min(255, Math.round(alpha * alphaScale)));
            delegate.setColor(red, green, blue, scaledAlpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, (LightTexture.FULL_BRIGHT >>> 16) & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            delegate.setNormal(nx, ny, nz);
            return this;
        }
    }

    private static void emitTranslucentBox(BufferBuilder buf, Matrix4f mat,
                                           float x0, float y0, float z0,
                                           float x1, float y1, float z1,
                                           float r, float g, float b, float a) {
        quad(buf, mat, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        quad(buf, mat, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, a);
        quad(buf, mat, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
        quad(buf, mat, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, r, g, b, a);
        quad(buf, mat, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r, g, b, a);
        quad(buf, mat, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
    }

    private static void emitWireBox(BufferBuilder buf, Matrix4f mat,
                                    float x0, float y0, float z0,
                                    float x1, float y1, float z1,
                                    float t, float r, float g, float b, float a) {
        barX(buf, mat, x0, x1, y0, z0, t, r, g, b, a);
        barX(buf, mat, x0, x1, y1, z0, t, r, g, b, a);
        barX(buf, mat, x0, x1, y0, z1, t, r, g, b, a);
        barX(buf, mat, x0, x1, y1, z1, t, r, g, b, a);
        barY(buf, mat, y0, y1, x0, z0, t, r, g, b, a);
        barY(buf, mat, y0, y1, x1, z0, t, r, g, b, a);
        barY(buf, mat, y0, y1, x0, z1, t, r, g, b, a);
        barY(buf, mat, y0, y1, x1, z1, t, r, g, b, a);
        barZ(buf, mat, z0, z1, x0, y0, t, r, g, b, a);
        barZ(buf, mat, z0, z1, x1, y0, t, r, g, b, a);
        barZ(buf, mat, z0, z1, x0, y1, t, r, g, b, a);
        barZ(buf, mat, z0, z1, x1, y1, t, r, g, b, a);
    }

    private static void barX(BufferBuilder buf, Matrix4f mat, float x0, float x1, float y, float z,
                             float t, float r, float g, float b, float a) {
        quad(buf, mat, x0, y - t, z, x1, y - t, z, x1, y + t, z, x0, y + t, z, r, g, b, a);
        quad(buf, mat, x0, y, z - t, x1, y, z - t, x1, y, z + t, x0, y, z + t, r, g, b, a);
    }

    private static void barY(BufferBuilder buf, Matrix4f mat, float y0, float y1, float x, float z,
                             float t, float r, float g, float b, float a) {
        quad(buf, mat, x - t, y0, z, x + t, y0, z, x + t, y1, z, x - t, y1, z, r, g, b, a);
        quad(buf, mat, x, y0, z - t, x, y0, z + t, x, y1, z + t, x, y1, z - t, r, g, b, a);
    }

    private static void barZ(BufferBuilder buf, Matrix4f mat, float z0, float z1, float x, float y,
                             float t, float r, float g, float b, float a) {
        quad(buf, mat, x, y - t, z0, x, y - t, z1, x, y + t, z1, x, y + t, z0, r, g, b, a);
        quad(buf, mat, x - t, y, z0, x - t, y, z1, x + t, y, z1, x + t, y, z0, r, g, b, a);
    }

    private static void quad(BufferBuilder buf, Matrix4f mat,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        buf.addVertex(mat, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        buf.addVertex(mat, x3, y3, z3).setColor(r, g, b, a);
    }
}

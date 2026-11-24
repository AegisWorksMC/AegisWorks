package com.industrialcore.client;

import com.industrialcore.block.GenerationBlock;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.menu.GenerationLayout;
import com.industrialcore.menu.GenerationMenu;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.reactor.NuclearCoolantLogic;
import com.industrialcore.reactor.ReactorMultiblockLogic;
import com.industrialcore.reactor.ReactorMultiblockLogic.FailReason;
import com.industrialcore.reactor.ReactorMultiblockLogic.ReactorType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

import static com.industrialcore.client.MachineTheme.CYAN;
import static com.industrialcore.client.MachineTheme.CYAN_BRIGHT;
import static com.industrialcore.client.MachineTheme.CYAN_DEEP;
import static com.industrialcore.client.MachineTheme.HEAT;
import static com.industrialcore.client.MachineTheme.HEAT_BRIGHT;
import static com.industrialcore.client.MachineTheme.MUTED;
import static com.industrialcore.client.MachineTheme.SLOT_CELL;
import static com.industrialcore.client.MachineTheme.SLOT_EDGE;
import static com.industrialcore.client.MachineTheme.STATUS_BAD;
import static com.industrialcore.client.MachineTheme.STATUS_OK;
import static com.industrialcore.client.MachineTheme.STATUS_WARN;

/**
 * Shared GUI of the eleven power devices (seven generators and four recovery devices).
 *
 * <p>Phase 43 puts the panel on {@link GuiMetrics}: the machine name in a 30 px header band, the
 * state in the shared status strip, and only the three read-outs a player needs in the body — how
 * much it produces, how much is stored, and what it burns or needs. The FE gauge is a vertical
 * column on the right edge so the text lines keep the full content width.</p>
 *
 * <p>Fuel devices show the fuel well and the burn gauge; a condition device (hydro, wind, solar,
 * geothermal) draws a source plate on the same well position instead, lit while its condition is
 * met and crossed out when it is missing.</p>
 */
public class GenerationScreen extends AdaptiveContainerScreen<GenerationMenu> {
    private static final Rect NUCLEAR_GUIDE_BTN = GenerationLayout.NUCLEAR_GUIDE_BTN;
    private static final Rect NUCLEAR_OVERDRIVE_BTN = GenerationLayout.NUCLEAR_OVERDRIVE_BTN;
    private static final int NEON_EMERALD = 0xFF3BF5A8;
    private static final int NEON_AMBER = 0xFFFFB84D;
    private static final int NEON_OVERDRIVE = 0xFFFF5E3A;

    public GenerationScreen(GenerationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GenerationLayout.WIDTH;
        this.imageHeight = GenerationLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        MachineTheme.chrome(graphics, left, top, GenerationLayout.HEIGHT,
                GenerationLayout.BODY_BOTTOM, statusColor(this.menu.getDeviceState()));

        Rect column = GenerationLayout.ENERGY_COLUMN;
        MachineTheme.energyColumn(graphics, left + column.x(), top + column.y(),
                column.width(), column.height(), this.menu.getEnergyStored(),
                this.menu.getMaxEnergy());

        MachineTheme.slots(graphics, left, top, this.menu.slots);

        if (this.menu.hasFuelSlot()) {
            Rect burn = this.menu.isNuclearMultiblock()
                    ? GenerationLayout.NUCLEAR_BURN_BAR
                    : GenerationLayout.BURN_BAR;
            MachineTheme.burnBar(graphics, left + burn.x(), top + burn.y(), burn.width(),
                    burn.height(), this.menu.getBurnProgress(), this.menu.getBurnTotal());
        } else if (this.menu.isGeothermal()) {
            drawSource(graphics, left, top, isRunning(), true);
        } else if (this.menu.isHydro() || this.menu.isSolar() || this.menu.isWind()) {
            drawSource(graphics, left, top, isRunning(), false);
        }

        if (this.menu.isNuclearMultiblock()) {
            drawCoolantColumn(graphics, left, top, this.menu.getCoolantMb(), this.menu.isMultiblockFormed());

            boolean guideOn = ReactorGuideRenderer.isGuideActiveFor(this.menu.getBlockPos());
            int bx = left + NUCLEAR_GUIDE_BTN.x();
            int by = top + NUCLEAR_GUIDE_BTN.y();
            int bw = NUCLEAR_GUIDE_BTN.width();
            int bh = NUCLEAR_GUIDE_BTN.height();
            boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
            int bg = guideOn ? 0xFF0A3242 : (hovered ? 0xFF1A2838 : 0xFF101924);
            int border = guideOn ? CYAN : (hovered ? 0xFF7CA4BC : SLOT_EDGE);
            graphics.fill(bx, by, bx + bw, by + bh, bg);
            graphics.fill(bx, by, bx + bw, by + 1, border);
            graphics.fill(bx, by + bh - 1, bx + bw, by + bh, border);
            graphics.fill(bx, by, bx + 1, by + bh, border);
            graphics.fill(bx + bw - 1, by, bx + bw, by + bh, border);

            // Phase 114: High-Output Overdrive toggle button
            boolean od = this.menu.isOverdriveMode();
            boolean cd = this.menu.getOverdriveCooldownTicks() > 0;
            int ox = left + NUCLEAR_OVERDRIVE_BTN.x();
            int oy = top + NUCLEAR_OVERDRIVE_BTN.y();
            int ow = NUCLEAR_OVERDRIVE_BTN.width();
            int oh = NUCLEAR_OVERDRIVE_BTN.height();
            boolean odHov = mouseX >= ox && mouseX < ox + ow && mouseY >= oy && mouseY < oy + oh;
            int odBg = cd ? 0xFF2B1212 : (od ? 0xFF3B170A : (odHov ? 0xFF1A2838 : 0xFF101924));
            int odBorder = cd ? STATUS_BAD : (od ? NEON_OVERDRIVE : (odHov ? 0xFF7CA4BC : SLOT_EDGE));
            if (od && !cd) {
                MachineTheme.glowRect(graphics, ox, oy, ow, oh, NEON_OVERDRIVE, 0.45D);
            }
            graphics.fill(ox, oy, ox + ow, oy + oh, odBg);
            graphics.fill(ox, oy, ox + ow, oy + 1, odBorder);
            graphics.fill(ox, oy + oh - 1, ox + ow, oy + oh, odBorder);
            graphics.fill(ox, oy, ox + 1, oy + oh, odBorder);
            graphics.fill(ox + ow - 1, oy, ox + ow, oy + oh, odBorder);
        }

        GeneratorAudioProfile.Profile audioProfile = this.menu.getGenerationKind() != null
                ? GeneratorAudioProfile.forGeneration(this.menu.getGenerationKind())
                : GeneratorAudioProfile.forRecovery(this.menu.getRecoveryKind());
        boolean generating = GeneratorAudioProfile.isActivelyGenerating(
                this.menu.getRate(), this.menu.getBurnProgress(), isRunning());
        GuiDopamine.observeGenerator(this, audioProfile, generating);
    }

    private static void drawCoolantColumn(GuiGraphics graphics, int left, int top, int coolantMb, boolean formed) {
        Rect col = GenerationLayout.NUCLEAR_COOLANT_COLUMN;
        int x = left + col.x();
        int y = top + col.y();
        int w = col.width();
        int h = col.height();
        boolean dryWarn = formed && coolantMb <= 0;
        int border = dryWarn ? NEON_AMBER : SLOT_EDGE;

        // Inset dark-slate well + crisp 1px border
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, border);
        graphics.fill(x, y, x + w, y + h, 0xFF09131E);

        int filledH = Math.round(h * NuclearCoolantLogic.fillRatio(coolantMb));
        if (filledH > 0) {
            int fy = y + h - filledH;
            graphics.fill(x, fy, x + w, y + h, 0xFF1888DB);
            graphics.fill(x + 1, fy, x + 3, y + h, 0xFF42BEFF);
            graphics.fill(x, fy, x + w, Math.min(y + h, fy + 1), 0xFF9BE6FF);
        }
        // Subtle 25% / 50% / 75% tick marks
        for (int q = 1; q <= 3; q++) {
            int ty = y + h * q / 4;
            graphics.fill(x, ty, x + 3, ty + 1, 0x660B1520);
        }
    }

    /**
     * Condition plate: lit when the requirement is met, crossed out when it is not. {@code molten}
     * switches the lit colour from cyan (air, water, light) to heat orange (lava).
     */
    private void drawSource(GuiGraphics graphics, int left, int top, boolean lit, boolean molten) {
        Rect plate = GenerationLayout.SOURCE_PLATE;
        int x = left + plate.x();
        int y = top + plate.y();
        int size = plate.width();
        int litColor = molten ? HEAT : CYAN;
        int fill = lit ? litColor : SLOT_CELL;
        int edge = lit ? litColor : SLOT_EDGE;

        MachineTheme.slotWell(graphics, x, y);
        if (lit) {
            graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, CYAN_DEEP);
            graphics.fill(x - 2, y - 2, x + size + 2, y - 1, litColor);
            graphics.fill(x - 2, y + size + 1, x + size + 2, y + size + 2, litColor);
            graphics.fill(x - 2, y - 1, x - 1, y + size + 1, litColor);
            graphics.fill(x + size + 1, y - 1, x + size + 2, y + size + 1, litColor);
            graphics.fill(x, y, x + size, y + size, fill);
            graphics.fill(x + 2, y + 2, x + size - 2, y + 5,
                    molten ? HEAT_BRIGHT : CYAN_BRIGHT);
            graphics.fill(x + 4, y + 9, x + size - 4, y + 12, molten ? 0xFFFFB347 : CYAN);
        } else {
            graphics.fill(x, y, x + size, y + size, fill);
            graphics.fill(x + 3, y + 7, x + size - 3, y + 9, edge);
            graphics.fill(x + 7, y + 3, x + 9, y + size - 3, edge);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int state = this.menu.getDeviceState();
        int accent = statusColor(state);

        int stored = this.menu.getEnergyStored();
        int capacity = this.menu.getMaxEnergy();
        int percent = capacity <= 0 ? 0 : Math.min(100, stored * 100 / capacity);
        Component percentText = Component.literal(percent + "%");

        MachineTheme.title(this.font, graphics, 0, 0, this.title);
        if (this.menu.isNuclearMultiblock()) {
            Component statusComp;
            int mbColor;
            if (this.menu.getOverdriveCooldownTicks() > 0) {
                int sec = Math.max(1, (this.menu.getOverdriveCooldownTicks() + 19) / 20);
                statusComp = Component.translatable("gui.industrialcore.reactor.status.cooldown", sec);
                mbColor = STATUS_BAD;
            } else if (this.menu.isMultiblockFormed()) {
                if (this.menu.getCoolantMb() <= 0) {
                    statusComp = Component.translatable("gui.industrialcore.reactor.coolant_low_warning");
                    mbColor = NEON_AMBER;
                } else {
                    int packed = this.menu.getMultiblockDimsPacked();
                    String mult = String.format(Locale.ROOT, "%.2f", this.menu.getMultiblockMultX100() / 100.0D);
                    statusComp = Component.translatable("gui.industrialcore.reactor.status.formed",
                            ReactorMultiblockLogic.unpackWidth(packed),
                            ReactorMultiblockLogic.unpackHeight(packed),
                            ReactorMultiblockLogic.unpackDepth(packed),
                            this.menu.getMultiblockCoreCells(),
                            this.menu.getMultiblockCoolantBlocks(),
                            mult);
                    mbColor = this.menu.isOverdriveMode() ? NEON_OVERDRIVE : NEON_EMERALD;
                }
            } else {
                FailReason reason = FailReason.byOrdinal(this.menu.getMultiblockFailReason());
                statusComp = Component.translatable(reason.translationKey());
                mbColor = reason == FailReason.NO_CORE_CELL ? NEON_AMBER : STATUS_BAD;
            }
            MachineTheme.statusText(this.font, graphics, 0, 0, statusComp, mbColor,
                    this.font.width(percentText));
        } else {
            MachineTheme.statusText(this.font, graphics, 0, 0,
                    Component.translatable(statusKey(state)), accent,
                    this.font.width(percentText));
        }
        MachineTheme.statusValue(this.font, graphics, 0, 0, percentText, MUTED);

        int maxLeftW = this.menu.isNuclearMultiblock() ? 136 : 152;
        int maxRateW = this.menu.isNuclearMultiblock() ? 92 : maxLeftW;
        int rateCol = (this.menu.isNuclearMultiblock() && this.menu.isMultiblockFormed() && this.menu.getCoolantMb() <= 0)
                ? NEON_AMBER : (this.menu.isNuclearMultiblock() && this.menu.isOverdriveMode() ? NEON_OVERDRIVE : MUTED);
        graphics.drawString(this.font,
                MachineTheme.fit(this.font,
                        Component.translatable("gui.industrialcore.generation.rate", this.menu.getRate()),
                        maxRateW),
                GuiMetrics.SIDE, GenerationLayout.RATE_TEXT_Y, rateCol, false);
        graphics.drawString(this.font,
                MachineTheme.fit(this.font,
                        Component.translatable("gui.industrialcore.energy_value", stored, capacity),
                        maxLeftW),
                GuiMetrics.SIDE, GenerationLayout.ENERGY_TEXT_Y, CYAN_BRIGHT, false);

        if (this.menu.isNuclearMultiblock()) {
            boolean od = this.menu.isOverdriveMode();
            boolean cd = this.menu.getOverdriveCooldownTicks() > 0;
            Component odTxt = Component.translatable(cd
                    ? "gui.industrialcore.reactor.overdrive_btn_cooldown"
                    : (od ? "gui.industrialcore.reactor.overdrive_btn_on"
                          : "gui.industrialcore.reactor.overdrive_btn_off"));
            Component fittedOd = MachineTheme.fit(this.font, odTxt, NUCLEAR_OVERDRIVE_BTN.width() - 4);
            int otx = NUCLEAR_OVERDRIVE_BTN.x() + (NUCLEAR_OVERDRIVE_BTN.width() - this.font.width(fittedOd)) / 2;
            int oty = NUCLEAR_OVERDRIVE_BTN.y() + 3;
            graphics.drawString(this.font, fittedOd, otx, oty,
                    cd ? STATUS_BAD : (od ? NEON_AMBER : MUTED), false);
        }

        if (this.menu.hasFuelSlot()) {
            if (this.menu.isNuclearMultiblock()) {
                graphics.drawString(this.font, Component.translatable("gui.industrialcore.reactor.water_short"),
                        GenerationLayout.NUCLEAR_BUCKET_LABEL_X, GenerationLayout.SLOT_LABEL_Y, 0xFF4CC9FF, false);
            }
            graphics.drawString(this.font, Component.translatable("gui.industrialcore.fuel_title"),
                    GenerationLayout.SLOT_LABEL_X, GenerationLayout.SLOT_LABEL_Y, MUTED, false);
            if (this.menu.isNuclearMultiblock()) {
                Component hint = this.menu.isMultiblockFormed()
                        ? Component.translatable("gui.industrialcore.reactor.stats_short",
                                this.menu.getMultiblockCoreCells(),
                                this.menu.getMultiblockCoolantBlocks(),
                                String.format(Locale.ROOT, "%.2f", this.menu.getMultiblockMultX100() / 100.0D))
                        : Component.translatable("gui.industrialcore.reactor.min_req_short");
                int hintCol = this.menu.isMultiblockFormed() ? NEON_EMERALD : NEON_AMBER;
                graphics.drawString(this.font, MachineTheme.fit(this.font, hint, 88),
                        58, GenerationLayout.SLOT_LABEL_Y, hintCol, false);

                boolean guideOn = ReactorGuideRenderer.isGuideActiveFor(this.menu.getBlockPos());
                Component btnTxt = Component.translatable(guideOn
                        ? "gui.industrialcore.reactor.guide_btn_on"
                        : "gui.industrialcore.reactor.guide_btn_off");
                Component fittedBtn = MachineTheme.fit(this.font, btnTxt, NUCLEAR_GUIDE_BTN.width() - 4);
                int tx = NUCLEAR_GUIDE_BTN.x() + (NUCLEAR_GUIDE_BTN.width() - this.font.width(fittedBtn)) / 2;
                int ty = NUCLEAR_GUIDE_BTN.y() + 3;
                graphics.drawString(this.font, fittedBtn, tx, ty, guideOn ? CYAN_BRIGHT : MUTED, false);
            }
        } else {
            graphics.drawString(this.font, Component.translatable(sourceTitleKey()),
                    GenerationLayout.SOURCE_LABEL_X, GenerationLayout.SOURCE_LABEL_Y,
                    sourceTitleColor(), false);
        }

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                GenerationLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    private boolean isRunning() {
        return this.menu.getDeviceState() == GenerationBlockEntity.Status.RUNNING.ordinal();
    }

    private String sourceTitleKey() {
        boolean lit = isRunning();
        if (this.menu.isGeothermal()) {
            return lit ? "gui.industrialcore.generation.lava.present"
                    : "gui.industrialcore.generation.lava.missing";
        }
        if (this.menu.isHydro()) {
            return lit ? "gui.industrialcore.generation.water.present"
                    : "gui.industrialcore.generation.water.missing";
        }
        if (this.menu.isSolar()) {
            return lit ? "gui.industrialcore.generation.sky.present"
                    : "gui.industrialcore.generation.sky.missing";
        }
        return lit ? "gui.industrialcore.generation.air.present"
                : "gui.industrialcore.generation.air.missing";
    }

    private int sourceTitleColor() {
        if (this.menu.isGeothermal()) {
            return isRunning() ? HEAT : MUTED;
        }
        return isRunning() ? CYAN : MUTED;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && this.menu.isNuclearMultiblock()) {
            double pmx = panelMouseX(mouseX);
            double pmy = panelMouseY(mouseY);
            int ox = this.leftPos + NUCLEAR_OVERDRIVE_BTN.x();
            int oy = this.topPos + NUCLEAR_OVERDRIVE_BTN.y();
            if (button == 0 && pmx >= ox && pmx < ox + NUCLEAR_OVERDRIVE_BTN.width()
                    && pmy >= oy && pmy < oy + NUCLEAR_OVERDRIVE_BTN.height()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.industrialcore.network.ReactorOverdriveTogglePayload(this.menu.getBlockPos()));
                return true;
            }
            int bx = this.leftPos + NUCLEAR_GUIDE_BTN.x();
            int by = this.topPos + NUCLEAR_GUIDE_BTN.y();
            if (pmx >= bx && pmx < bx + NUCLEAR_GUIDE_BTN.width()
                    && pmy >= by && pmy < by + NUCLEAR_GUIDE_BTN.height()) {
                if (button == 1 && ReactorGuideRenderer.isGuideActiveFor(this.menu.getBlockPos())) {
                    if (hasShiftDown()) {
                        ReactorGuideRenderer.cycleSize();
                    } else {
                        ReactorGuideRenderer.cycleLayer();
                    }
                } else {
                    toggleNuclearGuide();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleNuclearGuide() {
        BlockPos pos = this.menu.getBlockPos();
        Direction facing = Direction.NORTH;
        if (this.minecraft != null && this.minecraft.level != null) {
            BlockState st = this.minecraft.level.getBlockState(pos);
            if (st.hasProperty(GenerationBlock.FACING)) {
                facing = st.getValue(GenerationBlock.FACING);
            }
        }
        ReactorGuideRenderer.toggleGuide(pos, facing, ReactorType.NUCLEAR);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.menu.isNuclearMultiblock()) {
            double pmx = panelMouseX(mouseX);
            double pmy = panelMouseY(mouseY);
            int ox = this.leftPos + NUCLEAR_OVERDRIVE_BTN.x();
            int oy = this.topPos + NUCLEAR_OVERDRIVE_BTN.y();
            if (pmx >= ox && pmx < ox + NUCLEAR_OVERDRIVE_BTN.width()
                    && pmy >= oy && pmy < oy + NUCLEAR_OVERDRIVE_BTN.height()) {
                graphics.renderComponentTooltip(this.font,
                        buildOverdriveTooltip(this.menu.isOverdriveMode(), this.menu.getOverdriveCooldownTicks(), true),
                        mouseX, mouseY);
                return;
            }
            Rect col = GenerationLayout.NUCLEAR_COOLANT_COLUMN;
            int cx = this.leftPos + col.x();
            int cy = this.topPos + col.y();
            if (pmx >= cx - 1 && pmx < cx + col.width() + 1
                    && pmy >= cy - 1 && pmy < cy + col.height() + 1) {
                int mb = this.menu.getCoolantMb();
                graphics.renderComponentTooltip(this.font, List.of(
                        Component.translatable("gui.industrialcore.reactor.coolant_tooltip_amount",
                                mb, NuclearCoolantLogic.TANK_CAPACITY_MB)
                                .withStyle(net.minecraft.ChatFormatting.AQUA),
                        Component.translatable(mb > 0
                                        ? "gui.industrialcore.reactor.coolant_tooltip_ok"
                                        : "gui.industrialcore.reactor.coolant_low_warning")
                                .withStyle(mb > 0 ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GOLD),
                        Component.translatable("gui.industrialcore.reactor.coolant_tooltip_hint")
                                .withStyle(net.minecraft.ChatFormatting.GRAY)
                ), mouseX, mouseY);
                return;
            }
            int bx = this.leftPos + NUCLEAR_GUIDE_BTN.x();
            int by = this.topPos + NUCLEAR_GUIDE_BTN.y();
            boolean overBtn = pmx >= bx && pmx < bx + NUCLEAR_GUIDE_BTN.width()
                    && pmy >= by && pmy < by + NUCLEAR_GUIDE_BTN.height();
            boolean overReq = !this.menu.isMultiblockFormed()
                    && pmx >= this.leftPos + 58 && pmx < this.leftPos + 148
                    && pmy >= this.topPos + GenerationLayout.SLOT_LABEL_Y - 1
                    && pmy < this.topPos + GenerationLayout.SLOT_LABEL_Y + 10;
            if (overBtn || overReq) {
                boolean guideOn = ReactorGuideRenderer.isGuideActiveFor(this.menu.getBlockPos());
                graphics.renderComponentTooltip(this.font,
                        buildReactorGuideTooltip(guideOn, ReactorType.NUCLEAR), mouseX, mouseY);
                return;
            }
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /** Phase 114: Shared risk/reward tooltip for Nuclear &amp; Singularity Overdrive toggle button. */
    static List<Component> buildOverdriveTooltip(boolean overdriveOn, int cooldownTicks, boolean nuclear) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (cooldownTicks > 0) {
            int sec = Math.max(1, (cooldownTicks + 19) / 20);
            lines.add(Component.translatable("gui.industrialcore.reactor.status.cooldown", sec)
                    .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD));
        } else {
            lines.add(Component.translatable(overdriveOn
                            ? "gui.industrialcore.reactor.overdrive_tip_on"
                            : "gui.industrialcore.reactor.overdrive_tip_off")
                    .withStyle(overdriveOn ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.AQUA));
        }
        lines.add(Component.translatable("gui.industrialcore.reactor.overdrive_tip_gain")
                .withStyle(net.minecraft.ChatFormatting.GREEN));
        lines.add(Component.translatable(nuclear
                        ? "gui.industrialcore.reactor.overdrive_tip_cost_nuclear"
                        : "gui.industrialcore.reactor.overdrive_tip_cost_singularity")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        lines.add(Component.translatable("gui.industrialcore.reactor.overdrive_tip_safety")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        return lines;
    }

    /** Shared rich tooltip listing 3D ghost guide toggle + minimum 3x3x3 block counts. */
    static List<Component> buildReactorGuideTooltip(boolean guideOn, ReactorType type) {
        return List.of(
                Component.translatable(guideOn
                                ? "gui.industrialcore.reactor.guide_tooltip_on"
                                : "gui.industrialcore.reactor.guide_tooltip_off")
                        .withStyle(net.minecraft.ChatFormatting.AQUA),
                Component.translatable("gui.industrialcore.reactor.guide_tooltip_controls")
                        .withStyle(net.minecraft.ChatFormatting.GREEN),
                Component.translatable("gui.industrialcore.reactor.req_header")
                        .withStyle(net.minecraft.ChatFormatting.GOLD),
                Component.translatable("gui.industrialcore.reactor.req_casing")
                        .withStyle(net.minecraft.ChatFormatting.GRAY),
                Component.translatable("gui.industrialcore.reactor.req_wall")
                        .withStyle(net.minecraft.ChatFormatting.GRAY),
                Component.translatable("gui.industrialcore.reactor.req_core")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW),
                Component.translatable(type == ReactorType.NUCLEAR
                                ? "gui.industrialcore.reactor.req_size_nuclear"
                                : "gui.industrialcore.reactor.req_size_singularity")
                        .withStyle(net.minecraft.ChatFormatting.DARK_AQUA)
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int statusColor(int state) {
        if (state == GenerationBlockEntity.Status.RUNNING.ordinal()) {
            return STATUS_OK;
        }
        if (state == GenerationBlockEntity.Status.NO_WATER.ordinal()
                || state == GenerationBlockEntity.Status.NO_LAVA.ordinal()
                || state == GenerationBlockEntity.Status.NO_AIR.ordinal()
                || state == GenerationBlockEntity.Status.INCOMPLETE.ordinal()
                || state == GenerationBlockEntity.Status.COOLDOWN.ordinal()) {
            return STATUS_BAD;
        }
        if (state == GenerationBlockEntity.Status.NO_FUEL.ordinal()
                || state == GenerationBlockEntity.Status.NIGHT.ordinal()) {
            return STATUS_WARN;
        }
        return MUTED;
    }

    private static String statusKey(int state) {
        return switch (state) {
            case 0 -> "gui.industrialcore.generation.status.running";
            case 2 -> "gui.industrialcore.generation.status.no_fuel";
            case 3 -> "gui.industrialcore.generation.status.no_water";
            case 4 -> "gui.industrialcore.generation.status.night";
            case 5 -> "gui.industrialcore.generation.status.no_lava";
            case 6 -> "gui.industrialcore.generation.status.no_air";
            case 7 -> "gui.industrialcore.reactor.fail.incomplete_frame";
            case 8 -> "gui.industrialcore.stop_reason.overdrive_cooldown";
            default -> "gui.industrialcore.generation.status.idle";
        };
    }

    @Override
    protected BlockPos upgradeDockPos() {
        return this.menu.getGenerationKind() != null ? this.menu.getBlockPos() : null;
    }

    @Override
    protected boolean hasSkillTreeButton() {
        return true;
    }

    @Override
    protected int skillTreeDefaultTab() {
        return com.industrialcore.level.SkillTreeLayout.TAB_INFRA;
    }
}

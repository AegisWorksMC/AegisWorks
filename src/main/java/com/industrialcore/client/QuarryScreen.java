package com.industrialcore.client;

import com.industrialcore.machine.RangeInput;
import com.industrialcore.machine.UpgradeMath;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout;
import com.industrialcore.menu.QuarryMenu;
import com.industrialcore.network.QuarryActionPayload;
import com.industrialcore.network.SetRangePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Quarry GUI, split into two tabs so the settings and the status/loot inventory never overlap.
 *
 * <p>Tab 1 edits width/depth/height and holds the motor/battery slots; tab 2 shows the operating
 * state, progress and the 18 collected-item slots. The player inventory stays visible on both
 * tabs. Only the drawing and the slot positions change with the tab; the menu, the items and the
 * machine state are untouched by a tab switch.</p>
 *
 * <p>The three range fields are edited through {@link RangeInput}: typed text is kept when the
 * focus moves to another field, and a single Apply validates and sends all three values. Server
 * values are mirrored into the fields only while nothing is being edited.</p>
 */
public class QuarryScreen extends AdaptiveContainerScreen<QuarryMenu> {
    /** Client ticks to wait for the server echo of an applied range before mirroring again. */
    private static final int APPLY_SYNC_GRACE = 40;

    // Palette and primitives come from MachineTheme (Phase 42).
    private static final int COLOR_BORDER = MachineTheme.FRAME_MID;
    private static final int COLOR_TEXT = MachineTheme.TEXT;
    private static final int COLOR_MUTED = MachineTheme.MUTED;
    private static final int COLOR_ENERGY = MachineTheme.CYAN_BRIGHT;
    private static final int COLOR_PROGRESS = MachineTheme.CYAN;
    private static final int COLOR_TAB = MachineTheme.BAY_BOTTOM;
    private static final int COLOR_TAB_ACTIVE = MachineTheme.PANEL_TOP;
    private static final int COLOR_ACCENT = MachineTheme.CYAN;

    private final RangeInput range = new RangeInput(3, 3, 3);

    private EditBox widthField;
    private EditBox depthField;
    private EditBox heightField;
    private Button applyButton;
    private Button startButton;
    private Button previewButton;

    private int tab = QuarryLayout.TAB_SETTINGS;
    private int applySyncGrace;
    /** Guard so programmatic field updates are not mistaken for player edits. */
    private boolean settingFields;

    public QuarryScreen(QuarryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = QuarryLayout.WIDTH;
        this.imageHeight = QuarryLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000; // Hidden; the shared label pass draws it.
    }

    @Override
    protected void init() {
        super.init();

        // Menu data may not have arrived yet (all zero). Keep the server defaults until it does.
        this.serverAppliedToRange();

        this.widthField = createField(RangeInput.WIDTH, QuarryLayout.WIDTH_FIELD);
        this.depthField = createField(RangeInput.DEPTH, QuarryLayout.DEPTH_FIELD);
        this.heightField = createField(RangeInput.HEIGHT, QuarryLayout.HEIGHT_FIELD);
        this.addRenderableWidget(this.widthField);
        this.addRenderableWidget(this.depthField);
        this.addRenderableWidget(this.heightField);

        this.applyButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.apply"),
                        button -> applyRange())
                .bounds(this.leftPos + QuarryLayout.APPLY_BUTTON.x(), this.topPos + QuarryLayout.APPLY_BUTTON.y(),
                        QuarryLayout.APPLY_BUTTON.width(), QuarryLayout.APPLY_BUTTON.height())
                .build());

        this.startButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.start"),
                        button -> PacketDistributor.sendToServer(
                                new QuarryActionPayload(this.menu.getBlockPos(), QuarryActionPayload.ACTION_TOGGLE_RUNNING)))
                .bounds(this.leftPos + QuarryLayout.START_BUTTON.x(), this.topPos + QuarryLayout.START_BUTTON.y(),
                        QuarryLayout.START_BUTTON.width(), QuarryLayout.START_BUTTON.height())
                .build());

        this.previewButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.preview_off"),
                        button -> PacketDistributor.sendToServer(
                                new QuarryActionPayload(this.menu.getBlockPos(), QuarryActionPayload.ACTION_TOGGLE_PREVIEW)))
                .bounds(this.leftPos + QuarryLayout.PREVIEW_BUTTON.x(), this.topPos + QuarryLayout.PREVIEW_BUTTON.y(),
                        QuarryLayout.PREVIEW_BUTTON.width(), QuarryLayout.PREVIEW_BUTTON.height())
                .build());

        updateButtonLabels();
        applyTabState();
    }

    /** Pulls applied values from the menu into the range state if the player is not editing. */
    private void serverAppliedToRange() {
        int width = this.menu.getRangeWidth();
        int depth = this.menu.getRangeDepth();
        int height = this.menu.getRangeHeight();
        if (width <= 0 || depth <= 0 || height <= 0) {
            // Container data has not been synchronised yet; 0 is not a valid range value.
            return;
        }
        if (this.applySyncGrace > 0) {
            if (width == this.range.applied(RangeInput.WIDTH)
                    && depth == this.range.applied(RangeInput.DEPTH)
                    && height == this.range.applied(RangeInput.HEIGHT)) {
                this.applySyncGrace = 0;
            }
            return;
        }
        this.range.syncFromServer(width, depth, height);
    }

    private EditBox createField(int axis, QuarryLayout.Rect rect) {
        EditBox field = new EditBox(this.font, this.leftPos + rect.x(), this.topPos + rect.y(),
                rect.width(), rect.height(), Component.empty());
        field.setMaxLength(3);
        field.setFilter(text -> text.isEmpty() || text.matches("\\d{0,3}"));
        this.settingFields = true;
        field.setValue(this.range.text(axis));
        this.settingFields = false;
        field.setResponder(text -> {
            if (!this.settingFields) {
                this.range.setText(axis, text);
            }
        });
        return field;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.applySyncGrace > 0) {
            this.applySyncGrace--;
        }
        this.serverAppliedToRange();
        // Only mirror values while the player has not typed anything; a sync must never erase
        // half-finished input (that was the cause of the field resetting on focus change).
        if (!this.range.isDirty()) {
            refreshFieldValues();
        }
        updateButtonLabels();
    }

    private void refreshFieldValues() {
        this.settingFields = true;
        setFieldValue(this.widthField, this.range.text(RangeInput.WIDTH));
        setFieldValue(this.depthField, this.range.text(RangeInput.DEPTH));
        setFieldValue(this.heightField, this.range.text(RangeInput.HEIGHT));
        this.settingFields = false;
    }

    private static void setFieldValue(@Nullable EditBox field, String value) {
        if (field != null && !field.getValue().equals(value)) {
            field.setValue(value);
        }
    }

    private void updateButtonLabels() {
        if (this.startButton != null) {
            this.startButton.setMessage(Component.translatable(
                    this.menu.isRunning() ? "gui.industrialcore.stop" : "gui.industrialcore.start"));
        }
        if (this.previewButton != null) {
            this.previewButton.setMessage(Component.translatable(
                    this.menu.isPreviewEnabled() ? "gui.industrialcore.preview_on" : "gui.industrialcore.preview_off"));
        }
    }

    /** Validates all three fields together and sends exactly one request. */
    private void applyRange() {
        int[] values = this.range.apply();
        refreshFieldValues();
        this.applySyncGrace = APPLY_SYNC_GRACE;
        PacketDistributor.sendToServer(new SetRangePayload(
                this.menu.getBlockPos(), values[RangeInput.WIDTH], values[RangeInput.DEPTH], values[RangeInput.HEIGHT]));
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    private void switchTab(int newTab) {
        if (this.tab == newTab) {
            return;
        }
        this.tab = newTab;
        this.setFocused(null);
        applyTabState();
    }

    /** Shows/hides the widgets and selects the page whose slots are active. */
    private void applyTabState() {
        boolean settings = this.tab == QuarryLayout.TAB_SETTINGS;
        setVisible(this.widthField, settings);
        setVisible(this.depthField, settings);
        setVisible(this.heightField, settings);
        setVisible(this.applyButton, settings);
        this.menu.setVisibleTab(this.tab);
        // A slot from the page we just left must not stay highlighted under the cursor.
        this.hoveredSlot = null;
    }

    private static void setVisible(@Nullable Button button, boolean visible) {
        if (button != null) {
            button.visible = visible;
        }
    }

    private static void setVisible(@Nullable EditBox field, boolean visible) {
        if (field != null) {
            field.visible = visible;
            if (!visible) {
                field.setFocused(false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double localX = panelMouseX(mouseX) - this.leftPos;
            double localY = panelMouseY(mouseY) - this.topPos;
            if (QuarryLayout.SETTINGS_TAB.contains(localX, localY)) {
                switchTab(QuarryLayout.TAB_SETTINGS);
                return true;
            }
            if (QuarryLayout.STATUS_TAB.contains(localX, localY)) {
                switchTab(QuarryLayout.TAB_STATUS);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        MachineTheme.chrome(graphics, left, top, QuarryLayout.HEIGHT, QuarryLayout.BODY_BOTTOM,
                this.menu.getStatus().isPaused() ? MachineTheme.STATUS_WARN
                        : MachineTheme.STATUS_OK);

        drawTab(graphics, QuarryLayout.SETTINGS_TAB, this.tab == QuarryLayout.TAB_SETTINGS);
        drawTab(graphics, QuarryLayout.STATUS_TAB, this.tab == QuarryLayout.TAB_STATUS);

        if (this.tab == QuarryLayout.TAB_SETTINGS) {
            drawSettingsTab(graphics, left, top);
        } else {
            drawStatusTab(graphics, left, top);
        }

        // Slot backgrounds: vanilla skips inactive slots for items and clicks, so do the same here.
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            MachineTheme.slotWell(graphics, left + slot.x, top + slot.y);
        }
    }

    private void drawTab(GuiGraphics graphics, QuarryLayout.Rect tab, boolean selected) {
        int left = this.leftPos + tab.x();
        int top = this.topPos + tab.y();
        graphics.fill(left, top, left + tab.width(), top + tab.height(),
                selected ? COLOR_TAB_ACTIVE : COLOR_TAB);
        graphics.fill(left, top, left + tab.width(), top + 1, COLOR_BORDER);
        graphics.fill(left, top + tab.height() - 1, left + tab.width(), top + tab.height(), COLOR_BORDER);
        graphics.fill(left, top, left + 1, top + tab.height(), COLOR_BORDER);
        graphics.fill(left + tab.width() - 1, top, left + tab.width(), top + tab.height(), COLOR_BORDER);
        if (selected) {
            graphics.fill(left + 1, top + tab.height() - 2, left + tab.width() - 1, top + tab.height() - 1, COLOR_ACCENT);
        }
    }

    private void drawSettingsTab(GuiGraphics graphics, int left, int top) {
        QuarryLayout.Rect bar = QuarryLayout.ENERGY_BAR;
        MachineTheme.energyBar(graphics, left + bar.x(), top + bar.y(), bar.width(),
                bar.height(), this.menu.getEnergyStored(), this.menu.getEnergyCapacity());
    }

    private void drawStatusTab(GuiGraphics graphics, int left, int top) {
        QuarryLayout.Rect bar = QuarryLayout.PROGRESS_BAR;
        int percent = UpgradeMath.progressPercent(this.menu.getCursor(), this.menu.getVolume());
        MachineTheme.progressBar(graphics, left + bar.x(), top + bar.y(), bar.width(),
                bar.height(), percent, 100);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineTheme.title(this.font, graphics, 0, 0, this.title);

        // The state lives in the shared status strip, so both tabs answer "what is it doing?"
        // without repeating a text row inside the body.
        Component status = Component.translatable(this.menu.getStatus().translationKey());
        MachineTheme.statusText(this.font, graphics, 0, 0, status,
                this.menu.getStatus().isPaused() ? MachineTheme.STATUS_WARN : COLOR_TEXT);

        boolean settings = this.tab == QuarryLayout.TAB_SETTINGS;
        drawTabLabel(graphics, QuarryLayout.SETTINGS_TAB,
                Component.translatable("gui.industrialcore.tab.settings"), settings);
        drawTabLabel(graphics, QuarryLayout.STATUS_TAB,
                Component.translatable("gui.industrialcore.tab.status"), !settings);

        if (settings) {
            graphics.drawString(this.font, Component.translatable(
                            "gui.industrialcore.energy_value", this.menu.getEnergyStored(),
                            this.menu.getEnergyCapacity()),
                    GuiMetrics.SIDE, QuarryLayout.ENERGY_TEXT_Y, COLOR_ENERGY, false);
            graphics.drawString(this.font, Component.translatable("gui.industrialcore.range_width"),
                    QuarryLayout.WIDTH_FIELD.x(), QuarryLayout.FIELD_LABEL_Y, COLOR_MUTED, false);
            graphics.drawString(this.font, Component.translatable("gui.industrialcore.range_depth"),
                    QuarryLayout.DEPTH_FIELD.x(), QuarryLayout.FIELD_LABEL_Y, COLOR_MUTED, false);
            graphics.drawString(this.font, Component.translatable("gui.industrialcore.range_height"),
                    QuarryLayout.HEIGHT_FIELD.x(), QuarryLayout.FIELD_LABEL_Y, COLOR_MUTED, false);

            graphics.drawString(this.font, Component.translatable("gui.industrialcore.motor_short"),
                    QuarryLayout.MOTOR_LABEL_X, QuarryLayout.SLOT_LABEL_Y, COLOR_MUTED, false);
            graphics.drawString(this.font, Component.translatable("gui.industrialcore.battery_short"),
                    QuarryLayout.BATTERY_LABEL_X, QuarryLayout.SLOT_LABEL_Y, COLOR_MUTED, false);
        } else {
            graphics.drawString(this.font, Component.translatable(
                            "gui.industrialcore.progress_value", this.menu.getCursor(),
                            this.menu.getVolume()),
                    GuiMetrics.SIDE, QuarryLayout.PROGRESS_TEXT_Y, COLOR_MUTED, false);
            graphics.drawString(this.font, Component.translatable(
                            "gui.industrialcore.stats_value", this.menu.getBlocksMined(),
                            this.menu.getBlocksSkipped()),
                    GuiMetrics.SIDE, QuarryLayout.STATS_TEXT_Y, COLOR_MUTED, false);
            graphics.drawString(this.font, Component.translatable("gui.industrialcore.loot_title"),
                    GuiMetrics.SIDE, QuarryLayout.LOOT_TITLE_Y, COLOR_MUTED, false);
            if (this.menu.getStatus().isPaused()) {
                graphics.drawString(this.font,
                        MachineTheme.fit(this.font,
                                Component.translatable("gui.industrialcore.paused_hint"),
                                GuiMetrics.CONTENT),
                        GuiMetrics.SIDE, QuarryLayout.PAUSED_HINT_Y, COLOR_MUTED, false);
            }
        }

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                QuarryLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    private void drawTabLabel(GuiGraphics graphics, QuarryLayout.Rect tab, Component text, boolean selected) {
        int textWidth = this.font.width(text);
        graphics.drawString(this.font, text,
                tab.x() + (tab.width() - textWidth) / 2,
                tab.y() + (tab.height() - this.font.lineHeight) / 2 + 1,
                selected ? COLOR_TEXT : COLOR_MUTED, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.hoveredSlot != null && this.tab == QuarryLayout.TAB_SETTINGS
                && this.hoveredSlot.index < 2) {
            // One tooltip only: the upgrade slots used to draw the item name and then the
            // explanation at the same spot, so the two texts were printed on top of each other.
            Component itemName = this.hoveredSlot.getItem().getHoverName();
            boolean motor = this.hoveredSlot.index == 0;
            Component explanation = Component.translatable(motor
                    ? "gui.industrialcore.tooltip.motor"
                    : "gui.industrialcore.tooltip.battery");
            java.util.List<Component> lines = new java.util.ArrayList<>();
            if (this.hoveredSlot.hasItem()) {
                lines.add(itemName);
            }
            lines.add(explanation);
            lines.add(motor ? motorValue() : batteryValue());
            ClientMachineProgress.appendUpgradeTooltip(lines, "quarry");
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        } else {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    /** Phase 82: current effect of the installed motors, not just the item description. */
    private Component motorValue() {
        int motors = this.menu.getMotorCount();
        double ops = com.industrialcore.machine.UpgradeMath.operationsPerTick(motors,
                com.industrialcore.Config.baseOpsPerTick(),
                com.industrialcore.Config.opsPerMotor(),
                com.industrialcore.Config.maxOpsPerTick());
        return Component.translatable("gui.industrialcore.tooltip.motor_value",
                motors,
                String.format(java.util.Locale.ROOT, "%.2f", ops),
                String.format(java.util.Locale.ROOT, "%.2f", com.industrialcore.Config.maxOpsPerTick()))
                .withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    /** Phase 82: current battery capacity and FE saving. */
    private Component batteryValue() {
        int batteries = this.menu.getBatteryCount();
        int capacity = com.industrialcore.machine.UpgradeMath.capacity(batteries,
                com.industrialcore.Config.baseEnergyCapacity(),
                com.industrialcore.Config.energyPerBattery(),
                com.industrialcore.Config.maxEnergyCapacity());
        int percent = (int) Math.round(
                (1.0D - com.industrialcore.machine.UpgradeMath.energyCostMultiplier(batteries)) * 100.0D);
        return Component.translatable("gui.industrialcore.tooltip.battery_value",
                batteries, capacity, percent)
                .withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    @Override
    protected String skillTreeMachineId() {
        return "quarry";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

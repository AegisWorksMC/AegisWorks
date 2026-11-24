package com.industrialcore.client;

import com.industrialcore.machine.TransportChannel;
import com.industrialcore.machine.TransportRole;
import com.industrialcore.machine.TransporterKind;
import com.industrialcore.machine.TransporterStatus;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.TransporterLayout;
import com.industrialcore.menu.TransporterMenu;
import com.industrialcore.network.TransporterConfigPayload;
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
 * GUI of the Phase 26-29 transporters.
 *
 * <p>Everything is drawn from {@link TransporterLayout} and only server state is displayed: the
 * channel field mirrors the block entity's channel while it is not focused (typing is never
 * overwritten), the buttons send configuration requests, and the two text lines show structure
 * validity plus transfer status and the registered peers plus the last transfer. The FE bar and
 * the two toggles only exist on the Phase 28/29 tiers.</p>
 */
public class TransporterScreen extends AdaptiveContainerScreen<TransporterMenu> {
    // Palette and primitives come from MachineTheme (Phase 42).
    private static final int COLOR_MUTED = MachineTheme.MUTED;
    private static final int COLOR_ENERGY = MachineTheme.CYAN_BRIGHT;
    private static final int COLOR_OK = MachineTheme.STATUS_OK;
    private static final int COLOR_WARN = MachineTheme.STATUS_WARN;

    private final TransporterKind kind;

    @Nullable
    private EditBox channelField;
    @Nullable
    private Button setButton;
    @Nullable
    private Button roleButton;
    @Nullable
    private Button itemButton;
    @Nullable
    private Button powerButton;

    /** Guard so server mirrors are not mistaken for player edits. */
    private boolean settingChannel;
    private int lastItemsObserved = -1;
    private int lastPowerObserved = -1;

    public TransporterScreen(TransporterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.kind = menu.getKind();
        this.imageWidth = TransporterLayout.WIDTH;
        this.imageHeight = TransporterLayout.height(this.kind);
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000; // Hidden; the shared label pass draws it.
    }

    @Override
    protected void init() {
        super.init();

        Rect channelRect = TransporterLayout.CHANNEL_FIELD;
        this.channelField = new EditBox(this.font,
                this.leftPos + channelRect.x(), this.topPos + channelRect.y(),
                channelRect.width(), channelRect.height(),
                Component.translatable("gui.industrialcore.transporter.channel"));
        this.channelField.setMaxLength(TransportChannel.MAX_LENGTH);
        this.channelField.setFilter(text -> text.chars()
                .allMatch(character -> character >= 0x20 && character != 0x7F));
        this.settingChannel = true;
        this.channelField.setValue(this.menu.getChannel());
        this.settingChannel = false;
        this.addRenderableWidget(this.channelField);

        Rect setRect = TransporterLayout.SET_BUTTON;
        this.setButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.industrialcore.apply"), button -> applyChannel())
                .bounds(this.leftPos + setRect.x(), this.topPos + setRect.y(),
                        setRect.width(), setRect.height())
                .build());

        Rect roleRect = TransporterLayout.ROLE_BUTTON;
        this.roleButton = this.addRenderableWidget(Button.builder(roleLabel(), button ->
                        PacketDistributor.sendToServer(new TransporterConfigPayload(
                                this.menu.getBlockPos(),
                                TransporterConfigPayload.ACTION_CYCLE_ROLE, "")))
                .bounds(this.leftPos + roleRect.x(), this.topPos + roleRect.y(),
                        roleRect.width(), roleRect.height())
                .build());

        Rect itemRect = TransporterLayout.ITEM_BUTTON;
        this.itemButton = Button.builder(itemLabel(), button ->
                        PacketDistributor.sendToServer(new TransporterConfigPayload(
                                this.menu.getBlockPos(),
                                TransporterConfigPayload.ACTION_TOGGLE_ITEM, "")))
                .bounds(this.leftPos + itemRect.x(), this.topPos + itemRect.y(),
                        itemRect.width(), itemRect.height())
                .build();
        Rect powerRect = TransporterLayout.POWER_BUTTON;
        this.powerButton = Button.builder(powerLabel(), button ->
                        PacketDistributor.sendToServer(new TransporterConfigPayload(
                                this.menu.getBlockPos(),
                                TransporterConfigPayload.ACTION_TOGGLE_POWER, "")))
                .bounds(this.leftPos + powerRect.x(), this.topPos + powerRect.y(),
                        powerRect.width(), powerRect.height())
                .build();
        // The lower tiers have no FE and no toggles at all; their row stays empty.
        if (this.kind.supportsPower()) {
            this.addRenderableWidget(this.itemButton);
            this.addRenderableWidget(this.powerButton);
        }
    }

    private void applyChannel() {
        if (this.channelField != null) {
            PacketDistributor.sendToServer(new TransporterConfigPayload(this.menu.getBlockPos(),
                    TransporterConfigPayload.ACTION_SET_CHANNEL, this.channelField.getValue()));
        }
    }

    private Component roleLabel() {
        return Component.translatable(this.menu.getRole() == TransportRole.SEND
                ? "gui.industrialcore.transporter.role_send"
                : "gui.industrialcore.transporter.role_receive");
    }

    private Component itemLabel() {
        return Component.translatable("gui.industrialcore.transporter.item_toggle",
                Component.translatable(this.menu.isItemEnabled()
                        ? "gui.industrialcore.on" : "gui.industrialcore.off"));
    }

    private Component powerLabel() {
        return Component.translatable("gui.industrialcore.transporter.power_toggle",
                Component.translatable(this.menu.isPowerEnabled()
                        ? "gui.industrialcore.on" : "gui.industrialcore.off"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Mirror the server channel only while the player is not typing.
        if (this.channelField != null && !this.channelField.isFocused()) {
            String server = this.menu.getChannel();
            if (!this.channelField.getValue().equals(server)) {
                this.settingChannel = true;
                this.channelField.setValue(server);
                this.settingChannel = false;
            }
        }
        if (this.roleButton != null) {
            this.roleButton.setMessage(roleLabel());
        }
        if (this.itemButton != null) {
            this.itemButton.setMessage(itemLabel());
        }
        if (this.powerButton != null) {
            this.powerButton.setMessage(powerLabel());
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, this.imageHeight,
                TransporterLayout.bodyBottom(this.kind), statusColor(this.menu.getStatus()));

        if (this.kind.supportsPower()) {
            Rect energyBar = TransporterLayout.ENERGY_BAR;
            MachineTheme.energyBar(graphics, left + energyBar.x(), top + energyBar.y(),
                    energyBar.width(), energyBar.height(), this.menu.getEnergyStored(),
                    this.menu.getEnergyCapacity());
        }

        if (this.menu.getStatus() == TransporterStatus.WORKING) {
            int cx = left + this.imageWidth / 2;
            int cy = top + GuiMetrics.HEADER_HEIGHT + 28;
            int col = this.menu.getRole() == TransportRole.SEND ? MachineTheme.PURPLE_BRIGHT : MachineTheme.CYAN_BRIGHT;
            double phase = GuiDopamine.beatPhase(GuiDopamine.BPM_GUI);
            int span = 34;
            int offset = this.menu.getRole() == TransportRole.SEND
                    ? (int) Math.round((1.0 - phase) * span)
                    : (int) Math.round(phase * span);
            MachineTheme.glowRect(graphics, cx - offset, cy - 3, offset * 2, 6, col, 0.55);
            MachineTheme.glowRect(graphics, cx - 3, cy - 3, 6, 6, 0xFFFFFF, 0.75);
        }
        int curItems = this.menu.getLastItemsMoved();
        int curPower = this.menu.getLastPowerMoved();
        if (this.lastItemsObserved >= 0 && (curItems > this.lastItemsObserved || curPower > this.lastPowerObserved)) {
            int moved = Math.max(1, curItems - this.lastItemsObserved);
            GuiDopamine.triggerCompletion(left + this.imageWidth / 2, top + GuiMetrics.HEADER_HEIGHT + 28, moved);
            SoundDirector.playWarpChime();
        }
        this.lastItemsObserved = curItems;
        this.lastPowerObserved = curPower;

        MachineTheme.slots(graphics, left, top, this.menu.slots);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineTheme.title(this.font, graphics, 0, 0, this.title);

        // The status strip carries one line, so it shows the most pressing fact: a broken
        // structure hides everything else (the machine cannot work at all), otherwise the
        // transfer state. No value is drawn on the right, so a long translation cannot collide.
        boolean structureOk = this.menu.isStructureValid();
        TransporterStatus status = this.menu.getStatus();
        MachineTheme.statusText(this.font, graphics, 0, 0,
                Component.translatable(structureOk
                        ? status.translationKey()
                        : "gui.industrialcore.transporter.structure_bad"),
                structureOk ? statusColor(status) : COLOR_WARN);

        if (this.kind.supportsPower()) {
            graphics.drawString(this.font, Component.translatable(
                            "gui.industrialcore.energy_value", this.menu.getEnergyStored(),
                            this.menu.getEnergyCapacity()),
                    GuiMetrics.SIDE, TransporterLayout.ENERGY_TEXT_Y, COLOR_ENERGY, false);
        }

        // One line under the controls: peers on the left, the last transfer right aligned. The
        // right text is trimmed to whatever the left one leaves, so the two never collide.
        int destY = TransporterLayout.destTextY(this.kind);
        Component peers = Component.translatable(
                this.menu.getRole() == TransportRole.SEND
                        ? "gui.industrialcore.transporter.destinations"
                        : "gui.industrialcore.transporter.sources",
                this.menu.getPeerCount());
        graphics.drawString(this.font, peers, GuiMetrics.SIDE, destY, COLOR_MUTED, false);

        Component last = Component.translatable("gui.industrialcore.transporter.last_transfer",
                this.menu.getLastItemsMoved(), this.menu.getLastPowerMoved());
        int room = GuiMetrics.CONTENT - this.font.width(peers) - 6;
        Component fitted = MachineTheme.fit(this.font, last, room);
        graphics.drawString(this.font, fitted,
                GuiMetrics.contentRight() - this.font.width(fitted), destY, COLOR_MUTED, false);

        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                TransporterLayout.bodyBottom(this.kind), this.playerInventoryTitle);
    }

    private static int statusColor(TransporterStatus status) {
        return switch (status) {
            case WORKING -> COLOR_OK;
            case NO_STRUCTURE, NO_CHANNEL, NO_RECEIVER, RECEIVER_FULL -> COLOR_WARN;
            case IDLE -> COLOR_MUTED;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected boolean hasSkillTreeButton() {
        return true;
    }

    @Override
    protected int skillTreeDefaultTab() {
        return com.industrialcore.level.SkillTreeLayout.TAB_INFRA;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

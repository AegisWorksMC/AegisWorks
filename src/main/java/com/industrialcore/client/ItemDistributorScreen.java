package com.industrialcore.client;

import com.industrialcore.machine.DistributorOutputState;
import com.industrialcore.machine.MachineFaceConfig;
import com.industrialcore.machine.MachineStatus;
import com.industrialcore.machine.PipeWrenchTarget;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.ItemDistributorLayout;
import com.industrialcore.menu.ItemDistributorMenu;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.network.MachineConfigPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI of the Phase 19 item distributor.
 *
 * <p>The screen answers three questions at a glance: what is buffered, which outputs exist and
 * whether they can take items, and why nothing is moving. One row per face shows the side name,
 * its state (ready / full / not connected / disabled / the arrival side) and the row is also the
 * click target for the existing per-face settings, exactly like the other machine screens.</p>
 *
 * <p>Everything is server state: the buffer slot, the status, the output states and the face
 * configuration all come from the menu's container data or the synced block entity.</p>
 */
public class ItemDistributorScreen extends AdaptiveContainerScreen<ItemDistributorMenu> {

    // Palette and primitives come from MachineTheme (Phase 42).
    private static final int COLOR_TEXT = MachineTheme.TEXT;
    private static final int COLOR_MUTED = MachineTheme.MUTED;
    private static final int COLOR_WORKING = MachineTheme.STATUS_OK;
    private static final int COLOR_WARN = MachineTheme.STATUS_WARN;
    private static final int COLOR_INTAKE = MachineTheme.CYAN;
    private static final int COLOR_OFF = 0xFF5A6672;
    private static final int COLOR_ROW_HOVER = MachineTheme.PANEL_TOP;

    public ItemDistributorScreen(ItemDistributorMenu menu, Inventory playerInventory,
                                 Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = ItemDistributorLayout.WIDTH;
        this.imageHeight = ItemDistributorLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        MachineTheme.chrome(graphics, left, top, ItemDistributorLayout.HEIGHT,
                ItemDistributorLayout.BODY_BOTTOM,
                MachineTheme.statusColor(this.menu.getStatus()));
        MachineTheme.ingredientGroup(graphics, left, top,
                ItemDistributorLayout.BUFFER_SLOT.x() - 4, ItemDistributorLayout.BUFFER_SLOT.y() - 4,
                24, 24);

        int hovered = faceAt(mouseX, mouseY);
        for (int side = 0; side < 6; side++) {
            Rect row = ItemDistributorLayout.faceRow(side);
            if (side == hovered) {
                graphics.fill(left + row.x(), top + row.y(), left + row.right(),
                        top + row.bottom(), COLOR_ROW_HOVER);
            }
            DistributorOutputState state = this.menu.getOutputState(side);
            int color = colorOf(state);
            graphics.fill(left + ItemDistributorLayout.FACE_COLOR_X,
                    top + row.y() + 3,
                    left + ItemDistributorLayout.FACE_COLOR_X + 6,
                    top + row.y() + 9, color);
        }

        MachineTheme.slots(graphics, left, top, this.menu.slots);
    }

    private int faceAt(double mouseX, double mouseY) {
        double x = panelMouseX(mouseX) - this.leftPos;
        double y = panelMouseY(mouseY) - this.topPos;
        for (int side = 0; side < 6; side++) {
            Rect row = ItemDistributorLayout.faceRow(side);
            if (x >= row.x() && x < row.right() && y >= row.y() && y < row.bottom()) {
                return side;
            }
        }
        return -1;
    }

    /**
     * One row per face: left click cycles the face mode, shift+left cycles the filter, shift+right
     * adds the carried item to the filter and ctrl+left clears it. The server validates every
     * click, so a modified client cannot change anything else.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int side = faceAt(mouseX, mouseY);
        if (side >= 0) {
            int action;
            if (hasControlDown()) {
                action = MachineConfigPayload.ACTION_FACE_CLEAR;
            } else if (hasShiftDown()) {
                action = button == 1
                        ? MachineConfigPayload.ACTION_FACE_ADD_ITEM
                        : MachineConfigPayload.ACTION_FACE_FILTER;
            } else {
                if (button != 0) {
                    return true;
                }
                action = MachineConfigPayload.ACTION_FACE_MODE;
            }
            PacketDistributor.sendToServer(new MachineConfigPayload(
                    this.menu.getBlockPos(), side, action));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MachineStatus status = this.menu.getStatus();
        MachineTheme.title(this.font, graphics, 0, 0, this.title);
        MachineTheme.statusText(this.font, graphics, 0, 0,
                Component.translatable(statusKey(status)), statusColor(status));

        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.distributor.buffer"),
                ItemDistributorLayout.BUFFER_TEXT_X, ItemDistributorLayout.BUFFER_TEXT_Y,
                COLOR_MUTED, false);

        Component mode = Component.translatable("gui.industrialcore.distributor.mode",
                Component.translatable("gui.industrialcore.distributor.mode.round_robin"));
        graphics.drawString(this.font, mode, ItemDistributorLayout.MODE_TEXT_X,
                ItemDistributorLayout.MODE_TEXT_Y, COLOR_MUTED, false);
        // The mode has its own row: "Round robin" and "Output: West (default)" are too long
        // together in English, and the header is the anchor the six rows belong to.
        Component next = Component.translatable("gui.industrialcore.distributor.next",
                Component.translatable(PipeWrenchTarget.sideKey(this.menu.getNextSide())));
        graphics.drawString(this.font, Component.translatable(
                        "gui.industrialcore.distributor.outputs"),
                ItemDistributorLayout.OUTPUT_HEADER_X, ItemDistributorLayout.OUTPUT_HEADER_Y,
                COLOR_MUTED, false);
        graphics.drawString(this.font, next,
                ItemDistributorLayout.NEXT_X - this.font.width(next),
                ItemDistributorLayout.OUTPUT_HEADER_Y, COLOR_MUTED, false);

        for (int side = 0; side < 6; side++) {
            Rect row = ItemDistributorLayout.faceRow(side);
            DistributorOutputState state = this.menu.getOutputState(side);
            Component name = Component.translatable(PipeWrenchTarget.sideKey(side));
            graphics.drawString(this.font, name, ItemDistributorLayout.FACE_NAME_X,
                    row.y(), COLOR_TEXT, false);
            graphics.drawString(this.font, Component.translatable(state.translationKey()),
                    ItemDistributorLayout.FACE_STATE_X, row.y(), colorOf(state), false);
        }

        graphics.drawString(this.font, Component.translatable("gui.industrialcore.distributor.hint"),
                ItemDistributorLayout.HINT_TEXT_X, ItemDistributorLayout.HINT_TEXT_Y, COLOR_OFF,
                false);
        MachineTheme.inventoryLabel(this.font, graphics, 0, 0,
                ItemDistributorLayout.BODY_BOTTOM, this.playerInventoryTitle);
    }

    /** Distributor specific wording: "no input" would read like a machine waiting for a recipe. */
    private static String statusKey(MachineStatus status) {
        return switch (status) {
            case NO_INPUT -> "gui.industrialcore.distributor.status.waiting";
            case WORKING -> "gui.industrialcore.distributor.status.working";
            case OUTPUT_FULL -> "gui.industrialcore.distributor.status.blocked";
            default -> status.translationKey();
        };
    }

    private static int statusColor(MachineStatus status) {
        return MachineTheme.statusColor(status);
    }

    private static int colorOf(DistributorOutputState state) {
        return switch (state) {
            case READY -> COLOR_WORKING;
            case FULL -> COLOR_WARN;
            case UNCONNECTED -> COLOR_OFF;
            case DISABLED -> COLOR_MUTED;
            case INTAKE -> COLOR_INTAKE;
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        int side = faceAt(mouseX, mouseY);
        if (side >= 0) {
            MachineFaceConfig config = this.menu.getFaceConfig();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.industrialcore.face.title",
                    Component.translatable(PipeWrenchTarget.sideKey(side))));
            lines.add(Component.translatable(config.mode(side).translationKey())
                    .withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable(this.menu.getOutputState(side).translationKey())
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.industrialcore.face.filter",
                    Component.translatable(config.filterMode(side).translationKey()),
                    config.allowedIds(side).size()));
            lines.add(Component.translatable("gui.industrialcore.distributor.face_help")
                    .withStyle(ChatFormatting.DARK_GRAY));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
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

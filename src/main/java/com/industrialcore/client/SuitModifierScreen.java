package com.industrialcore.client;

import com.industrialcore.blockentity.SuitModifierBlockEntity;
import com.industrialcore.item.ElectricWeaponItem;
import com.industrialcore.item.PulseLaserItem;
import com.industrialcore.item.SuitModuleItem;
import com.industrialcore.item.VirtualSuitArmorItem;
import com.industrialcore.item.WeaponAttachmentItem;
import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.menu.SuitModifierMenu;
import com.industrialcore.network.SuitModifierActionPayload;
import com.industrialcore.suit.SuitModifierLayout;
import com.industrialcore.suit.SuitModule;
import com.industrialcore.suit.VirtualHudMath;
import com.industrialcore.suit.VirtualSuitLogic;
import com.industrialcore.weapon.LaserAimRange;
import com.industrialcore.weapon.VirtualWeaponLogic;
import com.industrialcore.weapon.WeaponAttachment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;

/**
 * Phase 98: Adaptive neon industrial GUI for the Suit Module Modifier ({@code suit_modifier}).
 */
public class SuitModifierScreen extends AdaptiveContainerScreen<SuitModifierMenu> {

    public SuitModifierScreen(SuitModifierMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SuitModifierLayout.WIDTH;
        this.imageHeight = SuitModifierLayout.HEIGHT;
        this.titleLabelX = GuiMetrics.TITLE_X;
        this.titleLabelY = GuiMetrics.TITLE_Y;
        this.inventoryLabelX = GuiMetrics.SIDE;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int st = this.menu.getDisplayState();
        int accent = statusAccent(st);
        MachineTheme.chrome(g, left, top, SuitModifierLayout.HEIGHT, SuitModifierLayout.BODY_BOTTOM, accent);

        Rect eb = SuitModifierLayout.ENERGY_BAR;
        MachineTheme.energyBar(g, left + eb.x(), top + eb.y(), eb.width(), eb.height(),
                this.menu.getEnergyStored(), this.menu.getEnergyCapacity());

        int lx = (int) Math.round(panelMouseX(mouseX) - left);
        int ly = (int) Math.round(panelMouseY(mouseY) - top);

        // Install button
        boolean canInstall = canInstallNow();
        boolean instHover = canInstall && SuitModifierLayout.INSTALL_BUTTON.contains(lx, ly);
        drawBtn(g, left, top, SuitModifierLayout.INSTALL_BUTTON, instHover,
                canInstall ? 0xFF103645 : 0xFF0A1118,
                canInstall ? MachineTheme.CYAN_BRIGHT : 0xFF283A4A);

        // Pulse Laser aim-range button (shown cleanly below Install when pulse_laser is inserted)
        ItemStack suit = this.menu.getSuitStack();
        if (suit.getItem() instanceof PulseLaserItem) {
            LaserAimRange rng = PulseLaserItem.getAimRange(suit);
            int rngBorder = switch (rng) {
                case NARROW -> MachineTheme.CYAN_BRIGHT;
                case WIDE -> 0xFF5CFFA0;
                case FULL_360 -> 0xFFE26BFF;
            };
            boolean rngHover = SuitModifierLayout.LASER_RANGE_BUTTON.contains(lx, ly);
            drawBtn(g, left, top, SuitModifierLayout.LASER_RANGE_BUTTON, rngHover, 0xFF0B1C28, rngBorder);
        }

        // Right-side module management panel
        Rect mp = SuitModifierLayout.MODULE_PANEL;
        g.fill(left + mp.x(), top + mp.y(), left + mp.right(), top + mp.bottom(), 0xFF070D14);
        MachineTheme.glowRect(g, left + mp.x(), top + mp.y(), mp.width(), mp.height(), accent, 0.28D);
        if (suit.getItem() instanceof VirtualSuitArmorItem suitItem) {
            List<SuitModule> slotMods = SuitModule.forSlot(suitItem.getSlotKind());
            Map<String, Boolean> installed = VirtualSuitArmorItem.getModules(suit);
            for (int i = 0; i < Math.min(SuitModifierLayout.VISIBLE_ROWS, slotMods.size()); i++) {
                SuitModule m = slotMods.get(i);
                renderModRowBg(g, left, top, lx, ly, i,
                        installed.containsKey(m.id()),
                        Boolean.TRUE.equals(installed.get(m.id())),
                        m.accentColor());
            }
        } else if (suit.getItem() instanceof ElectricWeaponItem weaponItem) {
            List<WeaponAttachment> weaponAtts = WeaponAttachment.forWeapon(weaponItem.getWeaponKind());
            Map<String, Boolean> installed = ElectricWeaponItem.getAttachments(suit);
            for (int i = 0; i < Math.min(SuitModifierLayout.VISIBLE_ROWS, weaponAtts.size()); i++) {
                WeaponAttachment a = weaponAtts.get(i);
                renderModRowBg(g, left, top, lx, ly, i,
                        installed.containsKey(a.id()),
                        Boolean.TRUE.equals(installed.get(a.id())),
                        a.accentColor());
            }
        }

        MachineTheme.slots(g, left, top, this.menu.slots);
    }

    @Override
    protected boolean hasSkillTreeButton() {
        return true;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int st = this.menu.getDisplayState();
        int accent = statusAccent(st);
        MachineTheme.title(this.font, g, 0, 0, this.title);

        String eStr = VirtualHudMath.formatCompactFe(this.menu.getEnergyStored())
                + " / " + VirtualHudMath.formatCompactFe(this.menu.getEnergyCapacity()) + " FE";
        Component valComp = Component.literal(eStr);
        int valW = Math.min(82, this.font.width(valComp));
        MachineTheme.statusText(this.font, g, 0, 0, Component.translatable(statusKey(st)), accent, valW);
        MachineTheme.statusValue(this.font, g, 0, 0, valComp, MachineTheme.CYAN_BRIGHT);

        ItemStack suit = this.menu.getSuitStack();

        // Install button label
        boolean canInstall = canInstallNow();
        Component instLabel = Component.translatable("gui.industrialcore.suit_modifier.btn_install");
        drawCentered(g, SuitModifierLayout.INSTALL_BUTTON, instLabel,
                canInstall ? 0xFFE6FCFF : MachineTheme.MUTED);

        // Pulse Laser FOV button label
        if (suit.getItem() instanceof PulseLaserItem) {
            LaserAimRange rng = PulseLaserItem.getAimRange(suit);
            int rngCol = switch (rng) {
                case NARROW -> MachineTheme.CYAN_BRIGHT;
                case WIDE -> 0xFF66FFB2;
                case FULL_360 -> 0xFFE26BFF;
            };
            drawCentered(g, SuitModifierLayout.LASER_RANGE_BUTTON,
                    Component.literal("FOV:" + rng.hudCode()), rngCol);
        }

        // Module / Attachment panel content
        if (suit.getItem() instanceof VirtualSuitArmorItem suitItem) {
            List<SuitModule> slotMods = SuitModule.forSlot(suitItem.getSlotKind());
            Map<String, Boolean> installed = VirtualSuitArmorItem.getModules(suit);
            for (int i = 0; i < Math.min(SuitModifierLayout.VISIBLE_ROWS, slotMods.size()); i++) {
                SuitModule m = slotMods.get(i);
                renderModRowLabels(g, i, Component.translatable(m.itemTranslationKey()),
                        installed.containsKey(m.id()),
                        Boolean.TRUE.equals(installed.get(m.id())),
                        m.accentColor());
            }
        } else if (suit.getItem() instanceof ElectricWeaponItem weaponItem) {
            List<WeaponAttachment> weaponAtts = WeaponAttachment.forWeapon(weaponItem.getWeaponKind());
            Map<String, Boolean> installed = ElectricWeaponItem.getAttachments(suit);
            for (int i = 0; i < Math.min(SuitModifierLayout.VISIBLE_ROWS, weaponAtts.size()); i++) {
                WeaponAttachment a = weaponAtts.get(i);
                renderModRowLabels(g, i, Component.translatable(a.itemTranslationKey()),
                        installed.containsKey(a.id()),
                        Boolean.TRUE.equals(installed.get(a.id())),
                        a.accentColor());
            }
        } else {
            Component hint = Component.translatable("gui.industrialcore.suit_modifier.insert_suit");
            Rect mp = SuitModifierLayout.MODULE_PANEL;
            g.drawWordWrap(this.font, hint, mp.x() + 5, mp.y() + 24, mp.width() - 10, 0xFF6C849B);
        }
    }

    private void renderModRowBg(GuiGraphics g, int left, int top, int lx, int ly,
                                int rowIndex, boolean has, boolean enabled, int accentColor) {
        Rect row = SuitModifierLayout.MODULE_ROWS.get(rowIndex);
        int rowBg = has ? (enabled ? 0xFF0E2530 : 0xFF141822) : 0xFF0A1018;
        int rowBorder = has ? (enabled ? accentColor : 0xFF445668) : 0xFF1E2C38;

        g.fill(left + row.x(), top + row.y(), left + row.right(), top + row.bottom(), rowBg);
        drawOutline(g, left + row.x(), top + row.y(), row.width(), row.height(), rowBorder);

        if (has) {
            Rect tBtn = SuitModifierLayout.TOGGLE_BUTTONS.get(rowIndex);
            drawBtn(g, left, top, tBtn, tBtn.contains(lx, ly),
                    enabled ? 0xFF123B2B : 0xFF2B1C22,
                    enabled ? 0xFF5CFFA0 : 0xFFFF6B7A);

            Rect rBtn = SuitModifierLayout.REMOVE_BUTTONS.get(rowIndex);
            drawBtn(g, left, top, rBtn, rBtn.contains(lx, ly),
                    0xFF2E161E, 0xFFFF7A8A);
        }
    }

    private void renderModRowLabels(GuiGraphics g, int rowIndex, Component name,
                                    boolean has, boolean enabled, int accentColor) {
        Rect row = SuitModifierLayout.MODULE_ROWS.get(rowIndex);
        g.drawString(this.font, MachineTheme.fit(this.font, name, row.width() - 6),
                row.x() + 3, row.y() + 2,
                has ? (enabled ? accentColor : 0xFFAAB8C4) : 0xFF607488, false);

        if (has) {
            g.drawString(this.font,
                    Component.translatable(enabled
                            ? "gui.industrialcore.suit_modifier.state_on"
                            : "gui.industrialcore.suit_modifier.state_off"),
                    row.x() + 3, row.y() + 13,
                    enabled ? 0xFF66FFB2 : 0xFFFF8899, false);

            Rect tBtn = SuitModifierLayout.TOGGLE_BUTTONS.get(rowIndex);
            drawCentered(g, tBtn, Component.literal(enabled ? "ON" : "OFF"),
                    enabled ? 0xFFE6FFF2 : 0xFFFFDCE2);

            Rect rBtn = SuitModifierLayout.REMOVE_BUTTONS.get(rowIndex);
            drawCentered(g, rBtn, Component.literal("×"), 0xFFFFD4DA);
        } else {
            g.drawString(this.font,
                    Component.translatable("gui.industrialcore.suit_modifier.not_installed"),
                    row.x() + 3, row.y() + 13, 0xFF4F6275, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        if (!renderMachineUpgradeTooltips(g, mouseX, mouseY,
                SuitModifierLayout.MOTOR_SLOT_BOX, SuitModifierLayout.BATTERY_SLOT_BOX,
                this.menu.getMotorCount(), this.menu.getBatteryCount(), false)) {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int lx = (int) Math.round(panelMouseX(mouseX) - this.leftPos);
            int ly = (int) Math.round(panelMouseY(mouseY) - this.topPos);

            if (SuitModifierLayout.INSTALL_BUTTON.contains(lx, ly)) {
                if (canInstallNow()) {
                    SoundDirector.playClick();
                    PacketDistributor.sendToServer(new SuitModifierActionPayload(
                            this.menu.getPos(), SuitModifierActionPayload.ACTION_INSTALL, 0));
                } else {
                    SoundDirector.playUiLocked();
                }
                return true;
            }

            ItemStack suit = this.menu.getSuitStack();
            if (suit.getItem() instanceof PulseLaserItem
                    && SuitModifierLayout.LASER_RANGE_BUTTON.contains(lx, ly)) {
                SoundDirector.playClick();
                PacketDistributor.sendToServer(new SuitModifierActionPayload(
                        this.menu.getPos(), SuitModifierActionPayload.ACTION_CYCLE_LASER_RANGE, 0));
                return true;
            }
            if (suit.getItem() instanceof VirtualSuitArmorItem suitItem) {
                List<SuitModule> slotMods = SuitModule.forSlot(suitItem.getSlotKind());
                Map<String, Boolean> installed = VirtualSuitArmorItem.getModules(suit);

                int toggleRow = SuitModifierLayout.toggleButtonAt(lx, ly);
                if (toggleRow >= 0 && toggleRow < slotMods.size()
                        && installed.containsKey(slotMods.get(toggleRow).id())) {
                    SoundDirector.playClick();
                    PacketDistributor.sendToServer(new SuitModifierActionPayload(
                            this.menu.getPos(), SuitModifierActionPayload.ACTION_TOGGLE, toggleRow));
                    return true;
                }

                int removeRow = SuitModifierLayout.removeButtonAt(lx, ly);
                if (removeRow >= 0 && removeRow < slotMods.size()
                        && installed.containsKey(slotMods.get(removeRow).id())) {
                    SoundDirector.playClick();
                    PacketDistributor.sendToServer(new SuitModifierActionPayload(
                            this.menu.getPos(), SuitModifierActionPayload.ACTION_REMOVE, removeRow));
                    return true;
                }
            } else if (suit.getItem() instanceof ElectricWeaponItem weaponItem) {
                List<WeaponAttachment> weaponAtts = WeaponAttachment.forWeapon(weaponItem.getWeaponKind());
                Map<String, Boolean> installed = ElectricWeaponItem.getAttachments(suit);

                int toggleRow = SuitModifierLayout.toggleButtonAt(lx, ly);
                if (toggleRow >= 0 && toggleRow < weaponAtts.size()
                        && installed.containsKey(weaponAtts.get(toggleRow).id())) {
                    SoundDirector.playClick();
                    PacketDistributor.sendToServer(new SuitModifierActionPayload(
                            this.menu.getPos(), SuitModifierActionPayload.ACTION_TOGGLE, toggleRow));
                    return true;
                }

                int removeRow = SuitModifierLayout.removeButtonAt(lx, ly);
                if (removeRow >= 0 && removeRow < weaponAtts.size()
                        && installed.containsKey(weaponAtts.get(removeRow).id())) {
                    SoundDirector.playClick();
                    PacketDistributor.sendToServer(new SuitModifierActionPayload(
                            this.menu.getPos(), SuitModifierActionPayload.ACTION_REMOVE, removeRow));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean canInstallNow() {
        ItemStack suit = this.menu.getSuitStack();
        ItemStack mod = this.menu.getModuleStack();
        boolean creative = this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.isCreative();
        if (!creative && this.menu.getEnergyStored() < this.menu.getInstallCost()) {
            return false;
        }
        if (suit.getItem() instanceof VirtualSuitArmorItem suitItem
                && mod.getItem() instanceof SuitModuleItem modItem) {
            return VirtualSuitLogic.canInstall(
                    suitItem.getSlotKind(), modItem.getModule(), VirtualSuitArmorItem.getModules(suit));
        }
        if (suit.getItem() instanceof ElectricWeaponItem weaponItem
                && mod.getItem() instanceof WeaponAttachmentItem attItem) {
            return VirtualWeaponLogic.canInstall(
                    weaponItem.getWeaponKind(), attItem.getAttachment(), ElectricWeaponItem.getAttachments(suit));
        }
        return false;
    }

    private static int statusAccent(int state) {
        return switch (state) {
            case VirtualSuitLogic.STATE_CHARGING -> MachineTheme.CYAN_BRIGHT;
            case VirtualSuitLogic.STATE_NO_ENERGY -> MachineTheme.STATUS_BAD;
            default -> 0xFF66C2FF;
        };
    }

    private static String statusKey(int state) {
        return switch (state) {
            case VirtualSuitLogic.STATE_CHARGING -> "gui.industrialcore.suit_modifier.status.charging";
            case VirtualSuitLogic.STATE_NO_ENERGY -> "gui.industrialcore.machine.status.no_energy";
            default -> "gui.industrialcore.suit_modifier.status.ready";
        };
    }

    private void drawCentered(GuiGraphics g, Rect r, Component text, int color) {
        Component fit = MachineTheme.fit(this.font, text, r.width() - 4);
        int x = r.x() + (r.width() - this.font.width(fit)) / 2;
        int y = r.y() + (r.height() - this.font.lineHeight) / 2;
        g.drawString(this.font, fit, x, y, color, false);
    }

    private static void drawBtn(GuiGraphics g, int left, int top, Rect r, boolean hover, int bg, int border) {
        int bx = left + r.x();
        int by = top + r.y();
        if (hover) {
            MachineTheme.glowRect(g, bx, by, r.width(), r.height(), border, 0.42D);
        }
        g.fill(bx, by, bx + r.width(), by + r.height(), hover ? brighten(bg) : bg);
        drawOutline(g, bx, by, r.width(), r.height(), border);
    }

    private static void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 20);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 24);
        int b = Math.min(255, (argb & 0xFF) + 28);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}

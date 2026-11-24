package com.industrialcore.compat.jei;

import com.industrialcore.client.QuantumStorageScreen;
import com.industrialcore.menu.QuarryLayout.Rect;
import com.industrialcore.storage.QuantumStorageLayout;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import net.minecraft.client.renderer.Rect2i;

import java.util.List;

/**
 * Reports the Quantum Storage Core / Portable Terminal's active right-side dock (Capacity Core
 * dock, Frequency Network dock, or collapsed side tabs) to JEI so JEI's ingredient overlay never
 * overlaps the side panel at any window size or GUI scale.
 */
public final class QuantumStorageJeiGuiHandler implements IGuiContainerHandler<QuantumStorageScreen> {

    @Override
    public List<Rect2i> getGuiExtraAreas(QuantumStorageScreen screen) {
        Rect side = QuantumStorageLayout.activeSideArea(
                screen.getDockMode(), screen.getMenu().isPortableMode());
        float scale = screen.panelScale();
        int sx = screen.panelX() + Math.round(side.x() * scale);
        int sy = screen.panelY() + Math.round(side.y() * scale);
        int sw = Math.max(1, Math.round(side.width() * scale) + 2);
        int sh = Math.max(1, Math.round(side.height() * scale) + 2);
        return List.of(new Rect2i(sx, sy, sw, sh));
    }
}

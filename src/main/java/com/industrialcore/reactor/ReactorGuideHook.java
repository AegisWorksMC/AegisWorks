package com.industrialcore.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Phase 103: Dedicated-server-safe bridge allowing common blocks ({@code nuclear_generator} and
 * {@code singularity_reactor}) to toggle the client-side 3D multiblock assembly guide on
 * sneak-right-click without importing any {@code net.minecraft.client} classes.
 */
public final class ReactorGuideHook {
    @FunctionalInterface
    public interface ToggleHandler {
        boolean toggle(BlockPos pos, Direction facing, ReactorMultiblockLogic.ReactorType type);
    }

    public static volatile ToggleHandler clientToggle = (pos, facing, type) -> false;

    private ReactorGuideHook() {}
}

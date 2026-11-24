package com.industrialcore.client;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Optional client-side bridge between Quantum Storage search bar and JEI's ingredient filter.
 *
 * <p>Zero JEI imports: wired dynamically by {@code IndustrialCoreJeiPlugin} only when JEI is
 * present at runtime, so the mod runs cleanly without JEI installed.</p>
 */
public final class JeiSearchBridge {
    @Nullable
    private static volatile Supplier<String> filterGetter;
    @Nullable
    private static volatile Consumer<String> filterSetter;

    private JeiSearchBridge() {}

    public static void bind(@Nullable Supplier<String> getter, @Nullable Consumer<String> setter) {
        filterGetter = getter;
        filterSetter = setter;
    }

    public static void unbind() {
        filterGetter = null;
        filterSetter = null;
    }

    public static boolean isAvailable() {
        return filterGetter != null && filterSetter != null;
    }

    public static String getFilterText() {
        Supplier<String> g = filterGetter;
        if (g == null) {
            return "";
        }
        String text = g.get();
        return text != null ? text : "";
    }

    public static void setFilterText(String text) {
        Consumer<String> s = filterSetter;
        if (s != null) {
            s.accept(text != null ? text : "");
        }
    }
}

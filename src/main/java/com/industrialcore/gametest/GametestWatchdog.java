package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Developer aid for the automated GameTest server only.
 *
 * <p>Vanilla starts every test structure in a grid at a random far-away position and only ticks
 * a test once all of its chunks reached {@code ENTITY_TICKING}. If a chunk never promotes (seen
 * occasionally at those far coordinates), that test never starts and therefore never times out:
 * the suite hangs forever with no result.</p>
 *
 * <p>This watchdog gives such a run a bounded lifetime: after a generous number of server ticks
 * it logs an explicit error and exits with a non-zero status, so the run can never be mistaken
 * for a pass. It is inert on any other server and does no work per tick beyond the type check.</p>
 */
@EventBusSubscriber(modid = IndustrialCore.MOD_ID)
public final class GametestWatchdog {
    /**
     * The suite finishes in a few dozen ticks (all tests run in parallel, the slowest test has a
     * 800 tick timeout). A run still alive after this many ticks is stuck, not slow.
     */
    private static final long MAX_GAMETEST_TICKS = 4_000L;

    private static GameTestServer watchedServer;
    private static long firstTick = -1L;

    private GametestWatchdog() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!(event.getServer() instanceof GameTestServer server)) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        if (server != watchedServer) {
            watchedServer = server;
            firstTick = gameTime;
            return;
        }
        if (gameTime - firstTick < MAX_GAMETEST_TICKS) {
            return;
        }
        IndustrialCore.LOGGER.error(
                "GameTest run did not finish within {} ticks: at least one test structure never "
                        + "started (vanilla only ticks a test after its chunks reach ENTITY_TICKING). "
                        + "Treating this run as FAILED instead of hanging forever.",
                MAX_GAMETEST_TICKS);
        IndustrialCore.LOGGER.error(
                "Re-run the suite; if it repeats, inspect the reported platform position and the "
                        + "unstarted test names in the log above.");
        // Vanilla exits with the number of failed required tests, which would be 0 here.
        System.exit(2);
    }
}

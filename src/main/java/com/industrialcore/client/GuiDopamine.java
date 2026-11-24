package com.industrialcore.client;

import com.industrialcore.menu.GuiMetrics;
import com.industrialcore.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Phase 76-77 Part 3/5 - client-only visual dopamine & multilayer neon bloom controller.
 *
 * <p>Observes frame telemetry passed from {@link MachineTheme} during {@code renderBg} without
 * shifting any {@code *Layout} coordinates by even a single pixel, and renders beat-synchronized
 * bay flashes, double shockwave rings, soft-halo physics sparks, floating {@code +N} popups,
 * and combo badges cleanly on {@link ScreenEvent.Render.Post}. Also dispatches Phase 77 hover,
 * slot insert/collect, milestone, ignition, charge, and open/close triggers to
 * {@link SoundDirector}.</p>
 */
public final class GuiDopamine {
    public static final double BPM_GUI = 150.0;
    public static final double BPM_WORLD = 120.0;
    private static final int MAX_SPARKS = 96;
    private static final long COMBO_WINDOW_MS = 4500L;
    private static final long COMPLETION_DEDUP_MS = 90L;
    private static final long BAY_FLASH_DURATION_MS = 110L;

    // ---------------------------------------------------------------- Phase 78: item flash
    /** Diagonal specular sweep across the item icon. */
    private static final long SHEEN_DURATION_MS = 320L;
    /** The four point star glint starts once the sweep has passed the middle. */
    private static final long GLINT_START_MS = 50L;
    private static final long GLINT_END_MS = 360L;
    /** Bouncing {@code +N} number lifetime. */
    private static final long POPUP_DURATION_MS = 850L;
    /** Hard, mobile-game style outline colour. */
    private static final int OUTLINE = 0x090C14;
    /** Five rarity fills and their top gloss, from common to mythic jackpot. */
    private static final int[] TIER_FILL = {0x5CFFA0, 0x3BE2FF, 0xDF68FF, 0xFFD438, 0xFFE24D};
    private static final int[] TIER_GLOSS = {0xE6FFF2, 0xFFFFFF, 0xFFE8FF, 0xFFFFE0, 0xFFFFFF};
    /** Prism colours the mythic tier cycles through. */
    private static final int[] PRISM = {0xFF5E7A, 0xFFB03A, 0xF4FF54, 0x5CFFA0, 0x4DD2FF, 0xC06BFF};
    private static final int[] OUTLINE_DIRS = {-1, 0, 1};

    private static final List<Shockwave> SHOCKWAVES = new ArrayList<>();
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final List<FloatingPopup> POPUPS = new ArrayList<>();
    private static final List<Sheen> SHEENS = new ArrayList<>();
    private static final List<Integer> PENDING_AUDIO_COMPLETIONS = new ArrayList<>();

    private static Screen trackedScreen;
    private static int trackedLeft;
    private static int trackedTop;
    private static int trackedWidth = GuiMetrics.WIDTH;
    private static int trackedHeight = 200;
    private static int trackedStatusColor = MachineTheme.MUTED;

    private static boolean hasProgress;
    private static int prevProgress = -1;
    private static int prevTotal = -1;
    private static int curProgress;
    private static int curTotal;
    private static int lastMilestoneQuarter;
    private static long lastActiveSparkMs;

    private static boolean wasActive;
    private static boolean wasEnergyFull;
    private static boolean chromeObserved;
    private static boolean energyObserved;

    private static boolean hasOutputCenter;
    private static int outCenterX;
    private static int outCenterY;

    private static final int[] LAST_SLOT_COUNTS = new int[64];
    private static final Object[] LAST_SLOT_ITEMS = new Object[64];
    private static boolean slotCountsInitialized;

    private static int lastHoverSlotIndex = -1;
    private static Object lastHoverWidget;
    private static long lastOutputSlotInteractionMs;
    private static int lastCarriedCount;

    private static Object generatorAudioScreen;
    private static GeneratorAudioProfile.Profile generatorAudioProfile;
    private static boolean generatorAudioActive;

    private static long lastCompletionMs;
    private static long bayFlashStartMs;
    private static int comboStreak;
    private static int totalCompletionsTriggered;
    private static double latestActiveRatio;
    private static boolean activeThisFrame;
    /** Suppresses audio delta triggers for the first few ticks after opening a machine GUI. */
    private static int openGraceTicks;
    private static long seedState = 0x5EED76L;

    private GuiDopamine() {}

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Normalized [0, 1) phase within a beat at {@code bpm}. */
    public static double beatPhase(double bpm) {
        return beatPhaseAt(nowMs(), bpm);
    }

    /** Deterministic beat phase at explicit millisecond timestamp. */
    public static double beatPhaseAt(long millis, double bpm) {
        double msPerBeat = 60000.0 / Math.max(1.0, bpm);
        double phase = (millis % msPerBeat) / msPerBeat;
        return phase < 0.0 ? phase + 1.0 : phase;
    }

    /** Crisp [0, 1] beat envelope peaking cleanly at beat downstroke and decaying exponentially. */
    public static double beatPulse(double bpm) {
        return beatPulseAt(nowMs(), bpm);
    }

    public static double beatPulseAt(long millis, double bpm) {
        double phase = beatPhaseAt(millis, bpm);
        return Math.exp(-phase * 4.5);
    }

    /** Resets all transient visual effects and tracking state. */
    public static void reset() {
        SHOCKWAVES.clear();
        SPARKS.clear();
        POPUPS.clear();
        SHEENS.clear();
        PENDING_AUDIO_COMPLETIONS.clear();
        trackedScreen = null;
        trackedStatusColor = MachineTheme.MUTED;
        hasProgress = false;
        prevProgress = -1;
        prevTotal = -1;
        curProgress = 0;
        curTotal = 0;
        lastMilestoneQuarter = 0;
        wasActive = false;
        wasEnergyFull = false;
        chromeObserved = false;
        energyObserved = false;
        hasOutputCenter = false;
        slotCountsInitialized = false;
        lastHoverSlotIndex = -1;
        lastHoverWidget = null;
        lastOutputSlotInteractionMs = 0L;
        lastCarriedCount = 0;
        generatorAudioScreen = null;
        generatorAudioProfile = null;
        generatorAudioActive = false;
        comboStreak = 0;
        lastCompletionMs = 0L;
        bayFlashStartMs = 0L;
        activeThisFrame = false;
        latestActiveRatio = 0.0;
        openGraceTicks = SoundCadenceController.OPEN_GRACE_TICKS;
    }

    /** Clears queued audio triggers and active-frame flags without discarding visual state. */
    static void clearPendingAudio() {
        PENDING_AUDIO_COMPLETIONS.clear();
        activeThisFrame = false;
        latestActiveRatio = 0.0;
        trackedStatusColor = MachineTheme.MUTED;
        generatorAudioScreen = null;
        generatorAudioProfile = null;
        generatorAudioActive = false;
    }

    /** Snapshot of the currently open generator GUI's audio profile and active generation state. */
    public record GeneratorAudioState(GeneratorAudioProfile.Profile profile, boolean generating) {}

    /** Called each frame by {@link GenerationScreen#renderBg} to report live generator audio state. */
    static void observeGenerator(Object screen, GeneratorAudioProfile.Profile profile, boolean generating) {
        generatorAudioScreen = screen;
        generatorAudioProfile = profile;
        generatorAudioActive = generating;
    }

    /** Returns the active generator screen's audio state, or {@code null} for non-generator screens. */
    static GeneratorAudioState currentGeneratorAudioState() {
        if (trackedScreen == null || generatorAudioScreen != trackedScreen || generatorAudioProfile == null) {
            return null;
        }
        return new GeneratorAudioState(generatorAudioProfile, generatorAudioActive);
    }

    static void tickOpenGrace() {
        if (openGraceTicks > 0) {
            openGraceTicks--;
        }
    }

    static boolean isInOpenGrace() {
        return openGraceTicks > 0;
    }

    static int currentCycleTicks() {
        return hasProgress ? curTotal : 0;
    }

    public static boolean isTrackedActive() {
        return activeThisFrame || trackedStatusColor == MachineTheme.STATUS_OK;
    }

    static void observeChrome(int left, int top, int width, int height, int statusColor) {
        trackedLeft = left;
        trackedTop = top;
        trackedWidth = width;
        trackedHeight = height;
        trackedStatusColor = statusColor;
        boolean nowActive = (statusColor == MachineTheme.STATUS_OK) || activeThisFrame;
        if (!chromeObserved || openGraceTicks > 0) {
            wasActive = nowActive;
            chromeObserved = true;
            return;
        }
        if (!wasActive && nowActive) {
            SoundDirector.playIgnition();
        }
        wasActive = nowActive;
    }

    static void observeEnergy(int stored, int capacity) {
        boolean nowFull = capacity > 0 && stored >= capacity;
        if (!energyObserved || openGraceTicks > 0) {
            wasEnergyFull = nowFull;
            energyObserved = true;
            return;
        }
        if (!wasEnergyFull && nowFull) {
            SoundDirector.playFullCharge();
        }
        wasEnergyFull = nowFull;
    }

    static void observeOutput(int slotX, int slotY) {
        hasOutputCenter = true;
        outCenterX = slotX + 8;
        outCenterY = slotY + 8;
    }

    static void observeProgress(int x0, int y0, int width, int height, int progress, int total) {
        hasProgress = true;
        curProgress = Math.max(0, progress);
        curTotal = Math.max(1, total);
        if (curProgress > 0) {
            activeThisFrame = true;
            latestActiveRatio = Math.min(1.0, (double) curProgress / curTotal);
            int q = (curProgress * 4) / curTotal;
            if (openGraceTicks > 0) {
                if (q >= 0 && q <= 3) {
                    lastMilestoneQuarter = q;
                }
            } else if (q >= 1 && q <= 3 && q > lastMilestoneQuarter) {
                lastMilestoneQuarter = q;
                SoundDirector.playMilestone(q);
            }
            long now = nowMs();
            if (now - lastActiveSparkMs >= 90L && curProgress < curTotal) {
                lastActiveSparkMs = now;
                int innerW = Math.max(0, width - 2);
                int edgeX = x0 + 1 + (int) Math.round(innerW * latestActiveRatio);
                int midY = y0 + Math.max(1, height / 2);
                spawnSpark(edgeX, midY, -0.4f - nextRand() * 0.9f, (nextRand() - 0.5f) * 1.4f,
                        0x9DF8FF, 280L);
            }
        } else {
            lastMilestoneQuarter = 0;
        }
    }

    /** Triggers a full completion flash + double shockwave + halo sparks + {@code +N} popup. */
    public static void triggerCompletion(int centerX, int centerY, int amount) {
        triggerCompletion(centerX, centerY, amount, 0);
    }

    /**
     * Completion triggers: bay flash, double shockwave, halo sparks, a diagonal specular sweep and
     * four point star glint on the item icon, and a bouncing rarity coloured {@code +N} number.
     *
     * @param stage rarity stage of the produced item (1..5); the higher of stage and amount decides
     *              the number's rarity tier, so a single mythic drop still flashes gold
     */
    public static void triggerCompletion(int centerX, int centerY, int amount, int stage) {
        long now = nowMs();
        if (now - lastCompletionMs <= COMBO_WINDOW_MS) {
            comboStreak++;
        } else {
            comboStreak = 1;
        }
        lastCompletionMs = now;
        bayFlashStartMs = now;
        totalCompletionsTriggered++;

        int count = Math.max(1, amount);
        SHOCKWAVES.add(new Shockwave(centerX, centerY, now, 6f, 34f, 540L, MachineTheme.CYAN_BRIGHT));
        SHOCKWAVES.add(new Shockwave(centerX, centerY, now + 45L, 4f, 22f, 440L,
                count >= 16 || comboStreak >= 4 ? 0xFFFFD866 : MachineTheme.PURPLE_BRIGHT));

        int sparkCount = Math.min(28, 16 + Math.min(12, count / 4));
        for (int i = 0; i < sparkCount; i++) {
            double ang = (Math.PI * 2.0 * i) / sparkCount + (nextRand() - 0.5) * 0.3;
            float speed = 1.4f + nextRand() * 2.2f;
            int col = (i % 3 == 0) ? 0xFFFFFF
                    : (i % 3 == 1 ? 0x42F2FF : 0xFFC94A);
            spawnSpark(centerX, centerY, (float) (Math.cos(ang) * speed),
                    (float) (Math.sin(ang) * speed) - 0.45f, col, 480L + (long) (nextRand() * 240));
        }

        int tier = rarityTier(count, stage);
        int col = tierColor(tier, now);
        String text = switch (tier) {
            case 5 -> "\u2726 +" + count + " \u2726";
            case 4 -> "\u2726+" + count;
            default -> "+" + count;
        };
        float vx = (nextRand() - 0.5f) * 32.0f;
        POPUPS.add(new FloatingPopup(centerX, centerY - 10, text, comboStreak, now, POPUP_DURATION_MS,
                col, tier, vx));
        SHEENS.add(new Sheen(centerX - 8, centerY - 8, now, tier, count));
        PENDING_AUDIO_COMPLETIONS.add((count << 12) | Math.min(4095, comboStreak));
    }

    // ------------------------------------------------------------------
    // Phase 78: rarity rules (pure, unit tested)
    // ------------------------------------------------------------------

    /**
     * Rarity tier 1..5 from how many items arrived and how rare the item is.
     *
     * <p>Counts: {@code +1..3} common, {@code +4..7} rare, {@code +8..15} epic, {@code +16..31}
     * legendary, {@code +32} and up mythic. Stages: {@code [IV]..[VI]} rare, {@code [VII]..[X]}
     * epic, {@code [XI]..[XIII]} legendary, {@code [XIV]}/{@code [XV]} mythic. The higher of the two
     * wins, so one mythic drop is as loud as a stack of 32.</p>
     */
    public static int rarityTier(int delta, int stage) {
        int byCount = delta >= 32 ? 5 : delta >= 16 ? 4 : delta >= 8 ? 3 : delta >= 4 ? 2 : 1;
        int byStage = stage >= 14 ? 5
                : stage >= 11 ? 4
                : stage >= 7 ? 3
                : stage >= 4 ? 2
                : 1;
        return Math.max(byCount, byStage);
    }

    /** Fill colour of a tier; the mythic tier cycles a prism and flashes white. */
    public static int tierColor(int tier, long millis) {
        int clamped = Math.max(1, Math.min(5, tier)) - 1;
        if (clamped < 4) {
            return TIER_FILL[clamped];
        }
        long phase = Math.max(0L, millis) / 70L;
        if (phase % 8L == 0L) {
            return 0xFFFFFF;
        }
        return PRISM[(int) (phase % PRISM.length)];
    }

    /** Top gloss colour of a tier. */
    public static int tierGloss(int tier) {
        return TIER_GLOSS[Math.max(1, Math.min(5, tier)) - 1];
    }

    /**
     * Rarity stage of a produced stack: its vanilla rarity (1..4) raised by a {@code [XIV]}/{@code [XV]}
     * tier tag in the item name, so a mythic product always reaches the jackpot tier.
     */
    static int stageOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int stage = switch (stack.getRarity()) {
            case EPIC -> 4;
            case RARE -> 3;
            case UNCOMMON -> 2;
            default -> 1;
        };
        String name = stack.getHoverName().getString();
        if (name.contains("[XV]") || name.contains("[XIV]")) {
            return 5;
        }
        if (name.contains("[XI") || name.contains("[XII") || name.contains("[XIII")) {
            return Math.max(stage, 4);
        }
        if (name.contains("[VII") || name.contains("[VIII") || name.contains("[IX") || name.contains("[X]")) {
            return Math.max(stage, 3);
        }
        return stage;
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next != trackedScreen) {
            SoundDirector.stopMachineGuiSounds();
            reset();
        }
    }

    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (isModMachineScreen(event.getScreen()) || event.getScreen() == trackedScreen) {
            SoundDirector.stopMachineGuiSounds();
            reset();
        }
    }

    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        recordOutputSlotInteractionIfHovered(event.getScreen());
    }

    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        recordOutputSlotInteractionIfHovered(event.getScreen());
    }

    private static void recordOutputSlotInteractionIfHovered(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> cs) || !isModMachineScreen(screen)) {
            return;
        }
        Slot under = cs.getSlotUnderMouse();
        if (under == null || under.container instanceof Inventory) {
            return;
        }
        ItemStack stack = under.getItem();
        if (!stack.isEmpty() && !isUpgradeItem(stack) && !under.mayPlace(stack)) {
            lastOutputSlotInteractionMs = nowMs();
        }
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> cs) || !isModMachineScreen(screen)) {
            return;
        }

        if (trackedScreen != screen) {
            reset();
            trackedScreen = screen;
        }

        detectCompletionAndSlotSignals(cs);
        renderEffects(event.getGuiGraphics(), Minecraft.getInstance().font, cs);
    }

    private static boolean isModMachineScreen(Screen screen) {
        return screen instanceof AbstractContainerScreen<?>
                && screen.getClass().getPackageName().equals("com.industrialcore.client");
    }

    static double consumeActiveAudioRatio() {
        if (trackedScreen == null) {
            activeThisFrame = false;
            return -1.0;
        }
        boolean active = activeThisFrame && hasProgress && curProgress > 0;
        boolean ultraFastWorking = !active && hasProgress
                && trackedStatusColor == MachineTheme.STATUS_OK
                && lastCompletionMs > 0L && (nowMs() - lastCompletionMs) <= 400L;
        activeThisFrame = false;
        if (active) {
            return Math.max(0.05, latestActiveRatio);
        }
        return ultraFastWorking ? 0.55 : -1.0;
    }

    static List<Integer> drainAudioCompletions() {
        if (PENDING_AUDIO_COMPLETIONS.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(PENDING_AUDIO_COMPLETIONS);
        PENDING_AUDIO_COMPLETIONS.clear();
        return out;
    }

    private static void detectHover(AbstractContainerScreen<?> cs, int mouseX, int mouseY) {
        Slot under = cs.getSlotUnderMouse();
        int slotIdx = (under != null && !under.getItem().isEmpty()) ? under.index : -1;
        if (slotIdx != -1 && slotIdx != lastHoverSlotIndex) {
            lastHoverSlotIndex = slotIdx;
            SoundDirector.playHoverTick();
            return;
        }
        if (slotIdx == -1) {
            lastHoverSlotIndex = -1;
        }

        double mx = cs instanceof AdaptiveContainerScreen<?> acs ? acs.panelMouseX(mouseX) : mouseX;
        double my = cs instanceof AdaptiveContainerScreen<?> acs ? acs.panelMouseY(mouseY) : mouseY;
        Object hoveredWidget = null;
        for (GuiEventListener child : cs.children()) {
            if (child instanceof Renderable && child.isMouseOver(mx, my)) {
                hoveredWidget = child;
                break;
            }
        }
        if (hoveredWidget != null && hoveredWidget != lastHoverWidget) {
            lastHoverWidget = hoveredWidget;
            SoundDirector.playHoverTick();
        } else if (hoveredWidget == null) {
            lastHoverWidget = null;
        }
    }

    /** True for the two items an upgrade slot takes; they get their own rising sound. */
    private static boolean isUpgradeItem(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ModItems.MOTOR.get()) || stack.is(ModItems.BATTERY.get()));
    }

    private static void detectCompletionAndSlotSignals(AbstractContainerScreen<?> cs) {
        long now = nowMs();
        int outputDelta = 0;
        int bestStage = 0;
        int bestOutputX = hasOutputCenter ? outCenterX : (cs.getGuiLeft() + cs.getXSize() - 28);
        int bestOutputY = hasOutputCenter ? outCenterY : (cs.getGuiTop() + 48);

        List<Slot> slots = cs.getMenu().slots;
        int limit = Math.min(LAST_SLOT_COUNTS.length, slots.size());
        // Suppress first-sync false positives while openGraceTicks > 0!
        int curCarriedCount = cs.getMenu().getCarried().getCount();
        if (!slotCountsInitialized || openGraceTicks > 0) {
            for (int i = 0; i < limit; i++) {
                ItemStack cur = slots.get(i).getItem();
                LAST_SLOT_COUNTS[i] = cur.getCount();
                LAST_SLOT_ITEMS[i] = cur.isEmpty() ? ItemStack.EMPTY : cur.copyWithCount(1);
            }
            lastCarriedCount = curCarriedCount;
            slotCountsInitialized = true;
            if (hasProgress) {
                prevProgress = curProgress;
                prevTotal = curTotal;
            }
            return;
        }
        boolean carriedGrew = curCarriedCount > lastCarriedCount;
        lastCarriedCount = curCarriedCount;
        {
            int collectDelta = 0;
            boolean insertedInput = false;
            boolean insertedUpgrade = false;
            for (int i = 0; i < limit; i++) {
                Slot slot = slots.get(i);
                ItemStack cur = slot.getItem();
                int count = cur.getCount();
                int prev = LAST_SLOT_COUNTS[i];
                ItemStack prevItem = LAST_SLOT_ITEMS[i] instanceof ItemStack s ? s : ItemStack.EMPTY;
                LAST_SLOT_COUNTS[i] = count;
                LAST_SLOT_ITEMS[i] = cur.isEmpty() ? ItemStack.EMPTY : cur.copyWithCount(1);
                if (slot.container instanceof Inventory || count == prev) {
                    continue;
                }
                ItemStack probe = !cur.isEmpty() ? cur : prevItem;
                boolean upgradeSlot = !probe.isEmpty() && isUpgradeItem(probe) && slot.mayPlace(probe);
                // True output-only slots reject their own item in mayPlace(probe), unlike SlotItemHandler
                // input slots which accept their own item but reject ItemStack.EMPTY.
                boolean outputOnly = !upgradeSlot && !probe.isEmpty() && !slot.mayPlace(probe);
                if (outputOnly) {
                    if (count > prev) {
                        outputDelta += (count - prev);
                        bestOutputX = cs.getGuiLeft() + slot.x + 8;
                        bestOutputY = cs.getGuiTop() + slot.y + 8;
                        bestStage = Math.max(bestStage, stageOf(cur));
                    } else {
                        collectDelta += (prev - count);
                    }
                } else if (count > prev) {
                    insertedInput = true;
                    if (isUpgradeItem(cur)) {
                        // Phase 81: motors and batteries climb a pitch ladder by their stack size.
                        SoundDirector.playUpgradeInsert(count, count - prev);
                        insertedUpgrade = true;
                    }
                }
            }
            if (insertedInput && !insertedUpgrade) {
                SoundDirector.playSlotInsert();
            }
            if (collectDelta > 0) {
                boolean manual = carriedGrew
                        || (lastOutputSlotInteractionMs > 0L
                        && (now - lastOutputSlotInteractionMs) <= 220L);
                SoundDirector.playOutputExtract(collectDelta, manual);
            }
        }

        boolean progressCompleted = false;
        if (hasProgress && prevProgress >= 0 && prevTotal > 0) {
            if ((curProgress < prevProgress && prevProgress >= Math.max(1, prevTotal / 2))
                    || (curProgress >= curTotal && prevProgress < prevTotal)) {
                progressCompleted = true;
            }
        }
        if (hasProgress) {
            prevProgress = curProgress;
            prevTotal = curTotal;
        }

        if ((outputDelta > 0 || progressCompleted) && (now - lastCompletionMs > COMPLETION_DEDUP_MS)) {
            triggerCompletion(bestOutputX, bestOutputY, Math.max(1, outputDelta), bestStage);
        }
    }

    // ------------------------------------------------------------------
    // Phase 78: drawn effects
    // ------------------------------------------------------------------

    /**
     * Diagonal 45 degree specular sweep across the 16x16 icon: a soft rarity glow band with a sharp
     * pure white core, drawn strictly inside the slot rectangle so it never bleeds out.
     */
    private static void drawSheen(GuiGraphics graphics, Sheen sheen, long elapsed, int tint) {
        double t = elapsed / (double) SHEEN_DURATION_MS;
        double ease = 1.0 - Math.pow(1.0 - t, 3.0);
        double sweep = -6.0 + 42.0 * ease;
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                double dist = Math.abs(col + row - sweep);
                int px = sheen.x + col;
                int py = sheen.y + row;
                if (dist <= 1.1) {
                    graphics.fill(px, py, px + 1, py + 1, (0xEB << 24) | 0xFFFFFF);
                } else if (dist <= 4.2) {
                    int alpha = (int) Math.round(0x60 * (1.0 - (dist - 1.1) / 3.1));
                    if (alpha > 2) {
                        graphics.fill(px, py, px + 1, py + 1, (alpha << 24) | (tint & 0xFFFFFF));
                    }
                }
            }
        }
    }

    /**
     * Four point diamond glint: a 2x2 white core, tapered white rays that reach 9 px and shrink back,
     * three pixel 45 degree sub rays, and a rarity coloured halo.
     */
    private static void drawGlint(GuiGraphics graphics, int cx, int cy, long elapsed, int tint) {
        if (elapsed < GLINT_START_MS || elapsed > GLINT_END_MS) {
            return;
        }
        double t = (elapsed - GLINT_START_MS) / (double) (GLINT_END_MS - GLINT_START_MS);
        double pulse = Math.sin(Math.PI * t);
        if (pulse <= 0.02) {
            return;
        }
        int ray = (int) Math.round(9.0 * pulse);
        int halo = (int) Math.round(0x50 * pulse);
        for (int d = 1; d <= 2; d++) {
            int a = halo / (d + 1);
            int col = (a << 24) | (tint & 0xFFFFFF);
            graphics.fill(cx - d, cy, cx + d + 1, cy + 1, col);
            graphics.fill(cx, cy - d, cx + 1, cy + d + 1, col);
        }
        for (int d = 1; d <= ray; d++) {
            int a = Math.max(0x30, 0xF0 - d * 22);
            int col = (a << 24) | 0xFFFFFF;
            graphics.fill(cx - d, cy, cx - d + 1, cy + 1, col);
            graphics.fill(cx + d, cy, cx + d + 1, cy + 1, col);
            graphics.fill(cx, cy - d, cx + 1, cy - d + 1, col);
            graphics.fill(cx, cy + d, cx + 1, cy + d + 1, col);
        }
        int sub = Math.min(3, ray);
        for (int d = 1; d <= sub; d++) {
            int a = Math.max(0x20, 0xB0 - d * 48);
            int col = (a << 24) | 0xFFFFFF;
            graphics.fill(cx - d, cy - d, cx - d + 1, cy - d + 1, col);
            graphics.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, col);
            graphics.fill(cx - d, cy + d, cx - d + 1, cy + d + 1, col);
            graphics.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, col);
        }
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
    }

    /**
     * The bouncing number: elastic pop scale, fountain rise, hard 8 direction outline, rarity fill
     * with a top gloss, and twinkling star dust around it.
     */
    private static void drawPopup(GuiGraphics graphics, Font font, FloatingPopup pop, long elapsed, long now) {
        double seconds = elapsed / 1000.0;
        float peak = pop.tier >= 4 ? 1.95f : 1.65f;
        float scale;
        if (elapsed < 75L) {
            scale = (float) (0.30 + (peak - 0.30) * (elapsed / 75.0));
        } else if (elapsed < 165L) {
            double k = (elapsed - 75L) / 90.0;
            scale = k < 0.5
                    ? (float) (peak + (0.94 - peak) * (k / 0.5))
                    : (float) (0.94 + (1.16 - 0.94) * ((k - 0.5) / 0.5));
        } else if (elapsed < 650L) {
            scale = 1.12f;
        } else {
            scale = (float) (1.12 - 0.10 * ((elapsed - 650L) / 200.0));
        }
        double px = pop.x + pop.vx * seconds;
        double py = pop.y - 56.0 * seconds + 20.0 * seconds * seconds;
        int alpha = elapsed <= 650L
                ? 255
                : Math.max(0, (int) Math.round(255.0 * (1.0 - (elapsed - 650L) / 200.0)));
        if (alpha <= 6) {
            return;
        }
        int fill = tierColor(pop.tier, now);
        int gloss = tierGloss(pop.tier);

        int dust = 5 + Math.min(5, pop.tier * 2);
        for (int i = 0; i < dust; i++) {
            double twinkle = Math.sin(seconds * 7.0 + i * 1.7);
            twinkle *= twinkle;
            if (twinkle < 0.10) {
                continue;
            }
            double ang = i * (Math.PI * 2.0 / dust) + seconds * 1.6;
            double radius = 10.0 + 9.0 * ((i % 3) / 3.0 + seconds * 0.8);
            int sx = (int) Math.round(px + Math.cos(ang) * radius);
            int sy = (int) Math.round(py + 3.0 + Math.sin(ang) * radius * 0.6 - seconds * 8.0);
            int a = (int) Math.round(alpha * twinkle * 0.85);
            if (a <= 6) {
                continue;
            }
            int rgb = (i % 3 == 0) ? 0xFFFFFF : (fill & 0xFFFFFF);
            graphics.fill(sx, sy, sx + 1, sy + 1, (a << 24) | rgb);
            if (i % 4 == 0) {
                int arm = (a / 2) << 24;
                graphics.fill(sx - 1, sy, sx + 2, sy + 1, arm | rgb);
                graphics.fill(sx, sy - 1, sx + 1, sy + 2, arm | rgb);
            }
        }

        graphics.pose().pushPose();
        graphics.pose().translate((float) px, (float) py, 380.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        int w = font.width(pop.label);
        int x = -w / 2;
        int y = -4;
        for (int dx : OUTLINE_DIRS) {
            for (int dy : OUTLINE_DIRS) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                graphics.drawString(font, pop.label, x + dx, y + dy, (alpha << 24) | OUTLINE, false);
            }
        }
        graphics.drawString(font, pop.label, x, y + 2, (alpha << 24) | OUTLINE, false);
        graphics.drawString(font, pop.label, x, y, (alpha << 24) | fill, false);
        graphics.drawString(font, pop.label, x, y - 1,
                (Math.min(alpha, 0x4A) << 24) | gloss, false);
        graphics.pose().popPose();

        if (pop.combo >= 2 && elapsed < 560L) {
            String badge = "x" + pop.combo;
            int bColor = (alpha << 24) | (pop.combo >= 10 ? 0xFF6AE8 : (pop.combo >= 5 ? 0xFFD966 : 0x5CE6FF));
            graphics.drawString(font, badge,
                    (int) Math.round(px + (w * scale) / 2.0) + 3,
                    (int) Math.round(py) - 6, bColor, true);
        }
    }

    private static void renderEffects(GuiGraphics graphics, Font font, AbstractContainerScreen<?> cs) {
        long now = nowMs();
        graphics.pose().pushPose();
        if (cs instanceof AdaptiveContainerScreen<?> acs) {
            graphics.pose().translate(acs.panelX(), acs.panelY(), 0.0F);
            graphics.pose().scale(acs.panelScale(), acs.panelScale(), 1.0F);
            graphics.pose().translate(-acs.getGuiLeft(), -acs.getGuiTop(), 0.0F);
        }
        graphics.pose().translate(0.0f, 0.0f, 350.0f);

        // 0. Bay luminance flash right on completion (<= 110ms, max alpha 0x22)
        if (bayFlashStartMs > 0L) {
            long flashElapsed = now - bayFlashStartMs;
            if (flashElapsed < BAY_FLASH_DURATION_MS) {
                double fade = 1.0 - (double) flashElapsed / BAY_FLASH_DURATION_MS;
                int a = Math.max(0, Math.min(0x22, (int) Math.round(fade * 0x22)));
                int fx0 = trackedLeft + 4;
                int fy0 = trackedTop + GuiMetrics.HEADER_HEIGHT + 4;
                int fx1 = trackedLeft + trackedWidth - 4;
                int fy1 = trackedTop + Math.max(60, trackedHeight - 102);
                graphics.fill(fx0, fy0, fx1, fy1, (a << 24) | 0xA8F6FF);
            }
        }

        // 1. Expanding double shockwaves
        Iterator<Shockwave> swIt = SHOCKWAVES.iterator();
        while (swIt.hasNext()) {
            Shockwave sw = swIt.next();
            long elapsed = now - sw.startMs;
            if (elapsed < 0L) {
                continue;
            }
            if (elapsed >= sw.durationMs) {
                swIt.remove();
                continue;
            }
            double t = (double) elapsed / sw.durationMs;
            double ease = 1.0 - Math.pow(1.0 - t, 3.0);
            float radius = (float) (sw.r0 + (sw.r1 - sw.r0) * ease);
            int alpha = Math.max(0, Math.min(255, (int) Math.round((1.0 - t) * 220.0)));
            int color = (alpha << 24) | (sw.rgb & 0x00FFFFFF);
            drawCircleOutline(graphics, sw.cx, sw.cy, Math.round(radius), color);
            if (radius > 5f) {
                int innerAlpha = Math.max(0, alpha / 2);
                drawCircleOutline(graphics, sw.cx, sw.cy, Math.round(radius - 2.0f),
                        (innerAlpha << 24) | (sw.rgb & 0x00FFFFFF));
            }
        }

        // 2. Soft-halo + pure white center physics sparks
        Iterator<Spark> spIt = SPARKS.iterator();
        while (spIt.hasNext()) {
            Spark sp = spIt.next();
            long elapsed = now - sp.bornMs;
            if (elapsed >= sp.lifeMs) {
                spIt.remove();
                continue;
            }
            sp.x += sp.vx;
            sp.y += sp.vy;
            sp.vx *= 0.92f;
            sp.vy = sp.vy * 0.92f + 0.06f;
            double t = (double) elapsed / sp.lifeMs;
            double fade = 1.0 - t * t;
            int coreAlpha = Math.max(0, Math.min(255, (int) Math.round(fade * 255.0)));
            int haloAlpha = Math.max(0, Math.min(120, (int) Math.round(fade * 95.0)));
            int px = Math.round(sp.x);
            int py = Math.round(sp.y);
            graphics.fill(px - 1, py - 1, px + 2, py + 2, (haloAlpha << 24) | (sp.rgb & 0x00FFFFFF));
            graphics.fill(px, py, px + 1, py + 1, (coreAlpha << 24) | 0xFFFFFF);
        }

        // 3. Diagonal specular sweep and four point star glint on the item icon
        Iterator<Sheen> shIt = SHEENS.iterator();
        while (shIt.hasNext()) {
            Sheen sh = shIt.next();
            long elapsed = now - sh.startMs;
            if (elapsed >= SHEEN_DURATION_MS) {
                shIt.remove();
                continue;
            }
            if (elapsed < 0L) {
                continue;
            }
            int tint = tierColor(sh.tier, now);
            drawSheen(graphics, sh, elapsed, tint);
            drawGlint(graphics, sh.x + 12, sh.y + 3, elapsed, tint);
            drawGlint(graphics, sh.x + 3, sh.y + 12, elapsed, tint);
        }

        // 4. Bouncing +N numbers: hard eight direction outline, rarity fill and star dust
        Iterator<FloatingPopup> popIt = POPUPS.iterator();
        while (popIt.hasNext()) {
            FloatingPopup pop = popIt.next();
            long elapsed = now - pop.startMs;
            if (elapsed >= pop.durationMs) {
                popIt.remove();
                continue;
            }
            drawPopup(graphics, font, pop, elapsed, now);
        }

        // Phase 79: the combo banner was removed from the screen. The streak itself still runs
        // internally - it drives the 24 step Shepard riser in SoundRhythm - it is just no longer
        // drawn as text.
        graphics.pose().popPose();
    }

    private static void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int r, int color) {
        if (r <= 1) {
            return;
        }
        int x = r;
        int y = 0;
        int err = 1 - x;
        while (x >= y) {
            plot(graphics, cx + x, cy + y, color);
            plot(graphics, cx + y, cy + x, color);
            plot(graphics, cx - y, cy + x, color);
            plot(graphics, cx - x, cy + y, color);
            plot(graphics, cx - x, cy - y, color);
            plot(graphics, cx - y, cy - x, color);
            plot(graphics, cx + y, cy - x, color);
            plot(graphics, cx + x, cy - y, color);
            y++;
            if (err < 0) {
                err += 2 * y + 1;
            } else {
                x--;
                err += 2 * (y - x) + 1;
            }
        }
    }

    private static void plot(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 1, y + 1, color);
    }

    private static void spawnSpark(float x, float y, float vx, float vy, int rgb, long lifeMs) {
        if (SPARKS.size() >= MAX_SPARKS) {
            SPARKS.remove(0);
        }
        SPARKS.add(new Spark(x, y, vx, vy, rgb & 0x00FFFFFF, nowMs(), lifeMs));
    }

    private static float nextRand() {
        seedState = seedState * 6364136223846793005L + 1442695040888963407L;
        return ((seedState >>> 40) & 0xFFFF) / 65535.0f;
    }

    public static int getShockwaveCount() {
        return SHOCKWAVES.size();
    }

    public static int getActiveSparkCount() {
        return SPARKS.size();
    }

    public static int getPopupCount() {
        return POPUPS.size();
    }

    public static int getSheenCount() {
        return SHEENS.size();
    }

    public static int getComboStreak() {
        return comboStreak;
    }

    public static int getTotalCompletionsTriggered() {
        return totalCompletionsTriggered;
    }

    private record Shockwave(int cx, int cy, long startMs, float r0, float r1, long durationMs, int rgb) {}

    private static final class Spark {
        float x;
        float y;
        float vx;
        float vy;
        final int rgb;
        final long bornMs;
        final long lifeMs;

        Spark(float x, float y, float vx, float vy, int rgb, long bornMs, long lifeMs) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.rgb = rgb;
            this.bornMs = bornMs;
            this.lifeMs = lifeMs;
        }
    }

    private record FloatingPopup(int x, int y, String label, int combo, long startMs, long durationMs,
                                 int rgb, int tier, float vx) {}

    /** A specular sweep over one 16x16 item icon, with the rarity tier of the produced item. */
    private record Sheen(int x, int y, long startMs, int tier, int count) {}
}

package com.industrialcore.machine;

/**
 * Balance of the Phase 24 phase reactor.
 *
 * <p>The reactor is the first machine that consumes the Phase 24 ores. It has three ingredient
 * slots and one result slot, so every recipe of the machine is exactly three items in and one
 * item out - the same shape as the Phase 18 assembler, which keeps the block entity, the menu and
 * the GUI simple and makes the machine usable by a pipe without any filter.</p>
 *
 * <p>The four runs are deliberately ordered by cost:</p>
 * <table>
 *   <caption>Phase reactor runs</caption>
 *   <tr><th>Output</th><th>Time</th><th>FE/t</th><th>Total FE</th></tr>
 *   <tr><td>Resonant catalyst</td><td>200</td><td>20</td><td>4,000</td></tr>
 *   <tr><td>Durable machine frame</td><td>300</td><td>25</td><td>7,500</td></tr>
 *   <tr><td>High-performance motor</td><td>300</td><td>25</td><td>7,500</td></tr>
 *   <tr><td>Phase core</td><td>400</td><td>40</td><td>16,000</td></tr>
 * </table>
 *
 * <p>Reference points: the precision crusher spends 3,000 FE per run, the assembler 1,200 and the
 * matter amplifier 72,000. A reactor run therefore sits between the mid-game parts line and the
 * end-game amplification, which is where the eight new ores enter the progression.</p>
 */
public final class PhaseReactorBalance {
    /** Internal FE buffer: twice the standard machine buffer, because a run draws 40 FE/t. */
    public static final int CAPACITY = 40_000;
    /** Maximum FE accepted per tick. A basic cable (128 FE/t) already runs the machine. */
    public static final int MAX_RECEIVE = 1_000;
    /** Retry backoff of an idle reactor (same value as the other machines). */
    public static final int RETRY_INTERVAL_TICKS = ProcessingBalance.RETRY_INTERVAL_TICKS;

    /** Ingredient slots of the machine (fixed at three: the recipe shape depends on it). */
    public static final int INPUT_SLOTS = 3;

    /** Resonant catalyst: 200 ticks at 20 FE/t. */
    public static final int CATALYST_TIME_TICKS = 200;
    public static final int CATALYST_ENERGY_PER_TICK = 20;
    /** Durable machine frame: 300 ticks at 25 FE/t. */
    public static final int FRAME_TIME_TICKS = 300;
    public static final int FRAME_ENERGY_PER_TICK = 25;
    /** High-performance motor: 300 ticks at 25 FE/t. */
    public static final int MOTOR_TIME_TICKS = 300;
    public static final int MOTOR_ENERGY_PER_TICK = 25;
    /** Phase core: 400 ticks at 40 FE/t. */
    public static final int CORE_TIME_TICKS = 400;
    public static final int CORE_ENERGY_PER_TICK = 40;

    /** FE of one run at the shipped recipe settings. */
    public static long totalEnergy(int timeTicks, int energyPerTick) {
        return ProcessingBalance.totalEnergy(timeTicks, energyPerTick);
    }

    /** FE of the four shipped runs, keyed by their output id, for the documentation tests. */
    public static long catalystCost() {
        return totalEnergy(CATALYST_TIME_TICKS, CATALYST_ENERGY_PER_TICK);
    }

    public static long frameCost() {
        return totalEnergy(FRAME_TIME_TICKS, FRAME_ENERGY_PER_TICK);
    }

    public static long motorCost() {
        return totalEnergy(MOTOR_TIME_TICKS, MOTOR_ENERGY_PER_TICK);
    }

    public static long coreCost() {
        return totalEnergy(CORE_TIME_TICKS, CORE_ENERGY_PER_TICK);
    }

    private PhaseReactorBalance() {}
}

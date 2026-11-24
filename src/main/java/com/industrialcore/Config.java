package com.industrialcore;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All tunable balance values live here so that the quarry behaviour can be adjusted
 * without touching the machine logic. Values are read at runtime through the helpers,
 * which keeps the machine code free of magic numbers.
 */
public final class Config {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue MAX_RANGE_WIDTH;
    private static final ModConfigSpec.IntValue MAX_RANGE_DEPTH;
    private static final ModConfigSpec.IntValue MAX_RANGE_HEIGHT;
    private static final ModConfigSpec.IntValue MAX_VOLUME;
    private static final ModConfigSpec.IntValue ENERGY_PER_BLOCK;
    private static final ModConfigSpec.DoubleValue BASE_OPS_PER_TICK;
    private static final ModConfigSpec.DoubleValue OPS_PER_MOTOR;
    private static final ModConfigSpec.DoubleValue MAX_OPS_PER_TICK;
    private static final ModConfigSpec.IntValue BASE_ENERGY_CAPACITY;
    private static final ModConfigSpec.IntValue ENERGY_PER_BATTERY;
    private static final ModConfigSpec.IntValue MAX_ENERGY_CAPACITY;
    private static final ModConfigSpec.IntValue MAX_ENERGY_RECEIVE;
    private static final ModConfigSpec.IntValue MAX_UPGRADE_COUNT;
    private static final ModConfigSpec.IntValue GLOBAL_MAX_OPS_PER_TICK;
    private static final ModConfigSpec.IntValue VISUAL_SYNC_MIN_INTERVAL;
    private static final ModConfigSpec.IntValue VISUAL_SYNC_MAX_INTERVAL;
    private static final ModConfigSpec.IntValue VISUAL_SYNC_FULL_RATE_MACHINES;
    private static final ModConfigSpec.IntValue VISUAL_MAX_BLOCKS_PER_UPDATE;
    private static final ModConfigSpec.IntValue INVENTORY_RETRY_COOLDOWN;
    private static final ModConfigSpec.BooleanValue ENABLE_MINING_PROFILING;
    private static final ModConfigSpec.IntValue PIPE_ITEMS_PER_TICK;
    private static final ModConfigSpec.IntValue PIPE_MAX_HOPS;
    private static final ModConfigSpec.IntValue LOGISTICS_VISUAL_MIN_INTERVAL;
    private static final ModConfigSpec.IntValue CRAFTER_CAPACITY;
    private static final ModConfigSpec.IntValue CRAFTER_MAX_RECEIVE;
    private static final ModConfigSpec.IntValue CRAFTER_CRAFT_TIME;
    private static final ModConfigSpec.IntValue CRAFTER_ENERGY_PER_TICK;
    private static final ModConfigSpec.IntValue CRAFTER_RETRY_INTERVAL;
    private static final ModConfigSpec.IntValue CRUSHER_CAPACITY;
    private static final ModConfigSpec.IntValue CRUSHER_MAX_RECEIVE;
    private static final ModConfigSpec.IntValue CRUSHER_TIME_TICKS;
    private static final ModConfigSpec.IntValue CRUSHER_ENERGY_PER_TICK;
    private static final ModConfigSpec.IntValue FURNACE_CAPACITY;
    private static final ModConfigSpec.IntValue FURNACE_MAX_RECEIVE;
    private static final ModConfigSpec.IntValue FURNACE_SPEED_PERCENT;
    private static final ModConfigSpec.IntValue FURNACE_ENERGY_PER_TICK;
    private static final ModConfigSpec.IntValue CABLE_REBUILD_NODES_PER_TICK;
    private static final ModConfigSpec.IntValue RECONSTRUCTOR_CAPACITY;
    private static final ModConfigSpec.IntValue RECONSTRUCTOR_MAX_RECEIVE;
    private static final ModConfigSpec.IntValue ENERGY_BUFFER_CAPACITY;
    private static final ModConfigSpec.IntValue ENERGY_BUFFER_MAX_RECEIVE;
    private static final ModConfigSpec.IntValue ENERGY_BUFFER_PUSH_PER_TICK;
    private static final ModConfigSpec.IntValue LARGE_QUARRY_WIDTH;
    private static final ModConfigSpec.IntValue LARGE_QUARRY_DEPTH;
    private static final ModConfigSpec.IntValue LARGE_QUARRY_HEIGHT;
    private static final ModConfigSpec.IntValue LARGE_QUARRY_ENERGY_PER_BLOCK;
    private static final ModConfigSpec.IntValue LARGE_QUARRY_CAPACITY;
    private static final ModConfigSpec.IntValue LARGE_QUARRY_MAX_RECEIVE;
    private static final ModConfigSpec.DoubleValue LARGE_QUARRY_OPS_PER_TICK;
    private static final ModConfigSpec.DoubleValue LARGE_QUARRY_MAX_OPS_PER_TICK;
    private static final ModConfigSpec.IntValue SEED_CULTIVATOR_CAPACITY;
    private static final ModConfigSpec.IntValue SEED_CULTIVATOR_MAX_RECEIVE;
    private static final ModConfigSpec.IntValue MATTER_AMPLIFIER_CAPACITY;
    private static final ModConfigSpec.IntValue MATTER_AMPLIFIER_MAX_RECEIVE;
    private static final ModConfigSpec.IntValue SYNTHESIZER_MAX_RECEIVE;
    private static final ModConfigSpec.BooleanValue ENABLE_CRAFT_UNLOCK_GATE;
    private static final ModConfigSpec.BooleanValue SHOW_BETA_WELCOME_MESSAGE;
    // Phase 115: Modpack-ready 5-category balance TOML config
    private static final ModConfigSpec.DoubleValue GENERATION_OUTPUT_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue OVERDRIVE_OUTPUT_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue PROCESSING_SPEED_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue ENERGY_CONSUMPTION_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue TURRET_DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.IntValue TURRET_BASE_RANGE;
    private static final ModConfigSpec.DoubleValue WEAPON_DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue MACHINE_XP_MULTIPLIER;
    private static final ModConfigSpec.IntValue BASE_UPGRADE_LIMIT;
    private static final ModConfigSpec.IntValue MAX_STANDARD_UPGRADE_LIMIT;
    private static final ModConfigSpec.BooleanValue ENABLE_DIMENSIONAL_RAID;
    private static final ModConfigSpec.IntValue RAID_ACTIVATION_COST_FE;
    private static final ModConfigSpec.DoubleValue RAID_DIFFICULTY_MULTIPLIER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Quarry mining area and processing limits.").push("quarry");
        MAX_RANGE_WIDTH = builder
                .comment("Maximum quarry width (blocks). The GUI input is clamped to this value.")
                .defineInRange("maxRangeWidth", 32, 1, 128);
        MAX_RANGE_DEPTH = builder
                .comment("Maximum quarry depth (blocks, horizontal).")
                .defineInRange("maxRangeDepth", 32, 1, 128);
        MAX_RANGE_HEIGHT = builder
                .comment("Maximum quarry height (blocks, downward). Also limited by the world height.")
                .defineInRange("maxRangeHeight", 32, 1, 128);
        MAX_VOLUME = builder
                .comment("Maximum number of blocks in one quarry area.")
                .defineInRange("maxVolume", 32768, 1, 1_000_000);
        ENERGY_PER_BLOCK = builder
                .comment("FE consumed for each mined block. Skipped (air/fluid/protected) blocks are free.")
                .defineInRange("energyPerBlock", 100, 0, 1_000_000);
        BASE_OPS_PER_TICK = builder
                .comment("Base mining operations per tick (0.25 = one block every four ticks).")
                .defineInRange("baseOperationsPerTick", 0.25D, 0.01D, 64.0D);
        OPS_PER_MOTOR = builder
                .comment("Additional operations per tick granted by one motor upgrade.")
                .defineInRange("operationsPerMotor", 0.05D, 0.0D, 1.0D);
        MAX_OPS_PER_TICK = builder
                .comment("Hard safety cap for mining operations per tick, regardless of motor count.")
                .defineInRange("maxOperationsPerTick", 8.0D, 1.0D, 64.0D);
        builder.pop();

        builder.comment("Energy storage and upgrade limits.").push("energy");
        BASE_ENERGY_CAPACITY = builder
                .comment("Internal FE buffer without battery upgrades.")
                .defineInRange("baseCapacity", 20_000, 1, 2_000_000_000);
        ENERGY_PER_BATTERY = builder
                .comment("Additional FE capacity granted by one battery upgrade.")
                .defineInRange("capacityPerBattery", 10_000, 0, 1_000_000_000);
        MAX_ENERGY_CAPACITY = builder
                .comment("Internal safety cap for the FE buffer (prevents int overflow).")
                .defineInRange("maxCapacity", 2_000_000_000, 1, 2_000_000_000);
        MAX_ENERGY_RECEIVE = builder
                .comment("Maximum FE accepted per tick from cables/generators.")
                .defineInRange("maxReceivePerTick", 1_000, 1, 100_000_000);
        MAX_UPGRADE_COUNT = builder
                .comment("Safety cap for stored motor/battery counts (no gameplay level cap).")
                .defineInRange("maxUpgradeCount", 1_000_000, 1, 10_000_000);
        builder.pop();

        builder.comment("FE cables: there is no hard network size limit, only a rebuild budget.").push("cables");
        CABLE_REBUILD_NODES_PER_TICK = builder
                .comment("Cable nodes a server may (re)discover per tick, shared by every network.",
                        "Cable networks have no size cap any more: when the topology changes, the",
                        "connected component is re-scanned incrementally over as many ticks as this",
                        "budget needs. A network is fully connected and distributes energy again as",
                        "soon as its scan finishes; buffers keep accepting and holding FE meanwhile.",
                        "2,048 nodes per tick costs well under 1 ms per tick on the GameTest server",
                        "(measured ~0.1 us per visited node). Raise it to reconnect huge networks",
                        "faster, lower it if a busy server needs the headroom. Minimum is 16 so a",
                        "rebuild always makes progress.")
                .defineInRange("networkRebuildNodesPerTick", 2048, 16, 1_000_000);
        builder.pop();

        builder.comment("Server wide safety limits and network tuning for large factories.").push("performance");
        GLOBAL_MAX_OPS_PER_TICK = builder
                .comment("Total block operations ALL running quarries may perform per server tick.",
                        "The budget is shared evenly between running machines, so a huge factory slows down",
                        "instead of stalling the server. 0 disables the global budget.",
                        "Measured cost on the GameTest server is roughly 100 us per mined block",
                        "(drops + inventory simulation + block removal), so 64 costs ~6 ms per tick at",
                        "worst case. Raise it only after measuring on your own hardware.")
                .defineInRange("globalOperationsPerTick", 64, 0, 100_000);
        VISUAL_SYNC_MIN_INTERVAL = builder
                .comment("Shortest interval (ticks) between visual (block entity) updates of a running quarry.",
                        "Keep this around 10: the drill head is interpolated on the client, so a longer",
                        "interval does not look jumpy and costs fewer packets.")
                .defineInRange("visualSyncMinIntervalTicks", 10, 1, 200);
        VISUAL_SYNC_MAX_INTERVAL = builder
                .comment("Longest interval (ticks) between visual updates, used once many machines run.")
                .defineInRange("visualSyncMaxIntervalTicks", 20, 1, 400);
        VISUAL_SYNC_FULL_RATE_MACHINES = builder
                .comment("Number of machines that still get the shortest visual sync interval.",
                        "Beyond this the interval is stretched so the total packet rate stays bounded.")
                .defineInRange("visualSyncFullRateMachines", 16, 1, 4096);
        VISUAL_MAX_BLOCKS_PER_UPDATE = builder
                .comment("Maximum mining positions one visual update of a fast quarry may cover.",
                        "The frame and the drill head are drawn from the same confirmed progress, so",
                        "this bounds how far the drawn frame can trail the real mining position.",
                        "A machine mining 8 positions per tick syncs every tick at the default of 4,",
                        "while a slow machine keeps the (cheaper) factory wide interval.")
                .defineInRange("visualMaxBlocksPerUpdate", 4, 1, 256);
        INVENTORY_RETRY_COOLDOWN = builder
                .comment("Ticks to wait before retrying after the output inventory was full.",
                        "A full-inventory attempt runs the block loot table and simulates all 18",
                        "output slots, so retries are backed off instead of repeating every tick.",
                        "Extracting anything from the output clears the wait immediately, so the",
                        "machine resumes as soon as there is space again.")
                .defineInRange("inventoryRetryCooldownTicks", 8, 1, 200);
        ENABLE_MINING_PROFILING = builder
                .comment("Measure per-machine mining time (ns) for diagnostics. Very cheap, but optional.")
                .define("enableMiningProfiling", true);
        builder.pop();

        builder.comment("Item pipes (Phase 5.1: four tiers; conveyor belts are legacy).").push("logistics");
        PIPE_ITEMS_PER_TICK = builder
                .comment("Maximum items a pipe extracts from one slot per tick.")
                .defineInRange("pipeItemsPerTick", 8, 1, 64);
        PIPE_MAX_HOPS = builder
                .comment("Maximum pipe-to-pipe handoffs for one item (loop safety).",
                        "An item that used up its budget stays in the pipe and is dropped when the",
                        "pipe is broken; it is never duplicated. Raise this for very long pipe runs.")
                .defineInRange("pipeMaxHops", 64, 1, 512);
        LOGISTICS_VISUAL_MIN_INTERVAL = builder
                .comment("Minimum ticks between two visual updates of one pipe.",
                        "The client extrapolates the received progress, so this does not make the",
                        "motion choppy; it only bounds how many packets a busy network sends.")
                .defineInRange("visualSyncMinIntervalTicks", 2, 1, 200);
        builder.pop();

        builder.comment("Auto crafter (Phase 6): recipe crafting machine.").push("crafter");
        CRAFTER_CAPACITY = builder
                .comment("Internal FE buffer of the auto crafter.")
                .defineInRange("capacity", 20_000, 1, 2_000_000_000);
        CRAFTER_MAX_RECEIVE = builder
                .comment("Maximum FE accepted per tick from generators/cables.")
                .defineInRange("maxReceivePerTick", 500, 1, 100_000_000);
        CRAFTER_CRAFT_TIME = builder
                .comment("Ticks one craft takes (20 ticks = one second).")
                .defineInRange("craftTimeTicks", 60, 1, 20 * 60 * 10);
        CRAFTER_ENERGY_PER_TICK = builder
                .comment("FE consumed per crafting tick. The total per craft is this value times craftTimeTicks.")
                .defineInRange("energyPerTick", 2, 0, 1_000_000);
        CRAFTER_RETRY_INTERVAL = builder
                .comment("Ticks to wait before re-checking a crafter that is missing materials, power or output space.",
                        "Inserting/extracting items wakes it up immediately. This keeps idle machines cheap.")
                .defineInRange("retryIntervalTicks", 10, 1, 200);
        builder.pop();

        builder.comment("Electric crusher and electric furnace (Phase 7).",
                        "Default values come from ProcessingBalance; both machines share one block entity type.")
                .push("processing");
        CRUSHER_CAPACITY = builder
                .comment("Internal FE buffer of the electric crusher.")
                .defineInRange("crusherCapacity", com.industrialcore.machine.ProcessingBalance.MACHINE_CAPACITY,
                        1, 2_000_000_000);
        CRUSHER_MAX_RECEIVE = builder
                .comment("Maximum FE the crusher accepts per tick from generators/cables.")
                .defineInRange("crusherMaxReceivePerTick",
                        com.industrialcore.machine.ProcessingBalance.MACHINE_MAX_RECEIVE, 1, 100_000_000);
        CRUSHER_TIME_TICKS = builder
                .comment("Default crushing time in ticks (20 ticks = one second).",
                        "A crushing recipe may override this with its own \"time\" field.")
                .defineInRange("crusherTimeTicks",
                        com.industrialcore.machine.ProcessingBalance.CRUSHER_TIME_TICKS, 1, 20 * 60 * 10);
        CRUSHER_ENERGY_PER_TICK = builder
                .comment("Default FE per crushing tick. A recipe may override this with \"energy\".")
                .defineInRange("crusherEnergyPerTick",
                        com.industrialcore.machine.ProcessingBalance.CRUSHER_ENERGY_PER_TICK, 0, 1_000_000);
        FURNACE_CAPACITY = builder
                .comment("Internal FE buffer of the electric furnace.")
                .defineInRange("furnaceCapacity", com.industrialcore.machine.ProcessingBalance.MACHINE_CAPACITY,
                        1, 2_000_000_000);
        FURNACE_MAX_RECEIVE = builder
                .comment("Maximum FE the electric furnace accepts per tick from generators/cables.")
                .defineInRange("furnaceMaxReceivePerTick",
                        com.industrialcore.machine.ProcessingBalance.MACHINE_MAX_RECEIVE, 1, 100_000_000);
        FURNACE_SPEED_PERCENT = builder
                .comment("Electric furnace speed as a percentage of the vanilla cooking time.",
                        "50 means twice as fast as a vanilla furnace; 100 means vanilla speed.")
                .defineInRange("furnaceSpeedPercent",
                        com.industrialcore.machine.ProcessingBalance.FURNACE_SPEED_PERCENT, 1, 1000);
        FURNACE_ENERGY_PER_TICK = builder
                .comment("FE the electric furnace consumes per smelting tick.")
                .defineInRange("furnaceEnergyPerTick",
                        com.industrialcore.machine.ProcessingBalance.FURNACE_ENERGY_PER_TICK, 0, 1_000_000);
        PROCESSING_SPEED_MULTIPLIER = builder
                .comment("Global processing machine speed multiplier for modpack balancing (default 1.0).")
                .defineInRange("processingSpeedMultiplier",
                        ConfigBalanceHelper.DEFAULT_PROCESSING_SPEED_MULT, 0.1D, 100.0D);
        ENERGY_CONSUMPTION_MULTIPLIER = builder
                .comment("Global machine energy consumption multiplier for modpack balancing (default 1.0).")
                .defineInRange("energyConsumptionMultiplier",
                        ConfigBalanceHelper.DEFAULT_ENERGY_CONSUMPTION_MULT, 0.0D, 100.0D);
        builder.pop();

        builder.comment("Seed cultivator (Phase 21).",
                        "The recipes carry the time and the energy per tick; only the buffer and the",
                        "input limit can be tuned here. The largest batch asks for 153 FE/t, so the",
                        "default receive limit is never the bottleneck.")
                .push("seed_cultivator");
        SEED_CULTIVATOR_CAPACITY = builder
                .comment("Internal FE buffer of the seed cultivator.")
                .defineInRange("capacity",
                        com.industrialcore.machine.SeedLineBalance.MACHINE_CAPACITY, 1, 2_000_000_000);
        SEED_CULTIVATOR_MAX_RECEIVE = builder
                .comment("Maximum FE the seed cultivator accepts per tick from generators/cables.")
                .defineInRange("maxReceivePerTick",
                        com.industrialcore.machine.SeedLineBalance.MACHINE_MAX_RECEIVE, 1, 100_000_000);
        builder.pop();

        builder.comment("Matter amplifier (Phase 21).",
                        "One run costs 72,000 FE at the recipe defaults; a supply below 60 FE/t",
                        "slows the run instead of stalling it.")
                .push("matter_amplifier");
        MATTER_AMPLIFIER_CAPACITY = builder
                .comment("Internal FE buffer of the matter amplifier.")
                .defineInRange("capacity",
                        com.industrialcore.machine.SeedLineBalance.MACHINE_CAPACITY, 1, 2_000_000_000);
        MATTER_AMPLIFIER_MAX_RECEIVE = builder
                .comment("Maximum FE the matter amplifier accepts per tick from generators/cables.")
                .defineInRange("maxReceivePerTick",
                        com.industrialcore.machine.SeedLineBalance.MACHINE_MAX_RECEIVE, 1, 100_000_000);
        builder.pop();

        builder.comment("Matter reconstructor (Phase 15).",
                        "The recipes carry the time and the energy per tick; only the buffer and the",
                        "input limit can be tuned here.")
                .push("reconstructor");
        RECONSTRUCTOR_CAPACITY = builder
                .comment("Internal FE buffer of the matter reconstructor.")
                .defineInRange("capacity",
                        com.industrialcore.machine.ReconstructorBalance.CAPACITY, 1, 2_000_000_000);
        RECONSTRUCTOR_MAX_RECEIVE = builder
                .comment("Maximum FE the matter reconstructor accepts per tick from generators/cables.")
                .defineInRange("maxReceivePerTick",
                        com.industrialcore.machine.ReconstructorBalance.MAX_RECEIVE, 1, 100_000_000);
        builder.pop();

        builder.comment("Synthetic ore synthesizer (Phase 30/31).",
                        "The synthesizer gets its own receive limit instead of raising the global",
                        "one that every older machine uses.",
                        "The section name ore_amplifier is kept for compatibility: the first",
                        "release of the synthesizer shipped its key there, and renaming the",
                        "section would silently discard a value an existing config.toml tuned.")
                .push("ore_amplifier");
        SYNTHESIZER_MAX_RECEIVE = builder
                .comment("Maximum FE the synthetic ore synthesizer accepts per tick.")
                .defineInRange("synthesizerMaxReceivePerTick", 1_000, 1, 100_000_000);
        builder.pop();

        builder.comment("Large energy buffer (Phase 17).",
                        "Stores surplus FE of a grid and feeds it back to its neighbours.")
                .push("energy_buffer");
        ENERGY_BUFFER_CAPACITY = builder
                .comment("Internal FE buffer of one energy buffer block.")
                .defineInRange("capacity", 250_000, 1, 2_000_000_000);
        ENERGY_BUFFER_MAX_RECEIVE = builder
                .comment("Maximum FE the buffer accepts per receive call (Phase 36.1: each",
                         "connected pusher calls receiveEnergy once per tick, so with up to six",
                         "connected pushers the per-tick intake can reach six times this value;",
                         "the practical total also depends on what each pusher supplies).")
                .defineInRange("maxReceivePerTick", 512, 1, 100_000_000);
        ENERGY_BUFFER_PUSH_PER_TICK = builder
                .comment("Maximum FE the buffer pushes into one neighbour per tick - a per-face",
                         "limit, not a whole-block total (six connected neighbours can receive up",
                         "to six times this value per tick).")
                .defineInRange("pushPerTick", 256, 0, 100_000_000);
        builder.pop();

        builder.comment("Large quarry (Phase 20).",
                        "The area is a fixed server setting on purpose: the machine is not a quarry",
                        "with a bigger GUI range but a different role (mining plus in-place crushing).")
                .push("large_quarry");
        LARGE_QUARRY_WIDTH = builder
                .comment("Fixed mining width in blocks (right of the machine).",
                        "Defaults come from LargeQuarryBalance; the hard limit is 32.")
                .defineInRange("width",
                        com.industrialcore.machine.LargeQuarryBalance.DEFAULT_WIDTH, 1, 32);
        LARGE_QUARRY_DEPTH = builder
                .comment("Fixed mining depth in blocks (in front of the machine).")
                .defineInRange("depth",
                        com.industrialcore.machine.LargeQuarryBalance.DEFAULT_DEPTH, 1, 32);
        LARGE_QUARRY_HEIGHT = builder
                .comment("Fixed mining height in blocks (downwards from the machine).")
                .defineInRange("height",
                        com.industrialcore.machine.LargeQuarryBalance.DEFAULT_HEIGHT, 1, 32);
        LARGE_QUARRY_ENERGY_PER_BLOCK = builder
                .comment("FE consumed for each destroyed block. Crushing a drop costs the",
                        "recipe's whole processing energy on top (see LargeQuarryBalance).")
                .defineInRange("energyPerBlock",
                        com.industrialcore.machine.LargeQuarryBalance.ENERGY_PER_BLOCK, 0, 1_000_000);
        LARGE_QUARRY_OPS_PER_TICK = builder
                .comment("Blocks per tick the machine may break (0.5 = one block every two ticks).")
                .defineInRange("operationsPerTick",
                        com.industrialcore.machine.LargeQuarryBalance.OPERATIONS_PER_TICK, 0.05D, 8.0D);
        LARGE_QUARRY_MAX_OPS_PER_TICK = builder
                .comment("Hard cap of blocks per tick, regardless of the configured speed.")
                .defineInRange("maxOperationsPerTick",
                        com.industrialcore.machine.LargeQuarryBalance.MAX_OPERATIONS_PER_TICK, 1.0D, 16.0D);
        LARGE_QUARRY_CAPACITY = builder
                .comment("Internal FE buffer of the large quarry.")
                .defineInRange("capacity",
                        com.industrialcore.machine.LargeQuarryBalance.CAPACITY, 1, 2_000_000_000);
        LARGE_QUARRY_MAX_RECEIVE = builder
                .comment("Maximum FE the large quarry accepts per tick from cables and generators.")
                .defineInRange("maxReceivePerTick",
                        com.industrialcore.machine.LargeQuarryBalance.MAX_RECEIVE, 1, 100_000_000);
        builder.pop();

        builder.comment("Phase 97: Skill Tree progression and craft unlock gates.").push("progression");
        ENABLE_CRAFT_UNLOCK_GATE = builder
                .comment("When true, Tier 2-5 machines require their prerequisite machine level in the Skill Tree to craft in Survival mode.",
                        "Tier 1 starter machines and Creative mode are always allowed.")
                .define("enableCraftUnlockGate", true);
        builder.pop();

        builder.comment("Open Beta welcome notice and community invite link.").push("general");
        SHOW_BETA_WELCOME_MESSAGE = builder
                .comment("When true, sends a one-time AegisWorks Open Beta welcome message with the official Discord link",
                        "the first time each player joins a world. Set to false for streamers or modpacks.")
                .define("showBetaWelcomeMessage", true);
        builder.pop();

        builder.comment("Phase 115: Global power generation multipliers (thermal, hydro, wind, solar, nuclear, singularity).")
                .push("generation");
        GENERATION_OUTPUT_MULTIPLIER = builder
                .comment("Global FE/t output multiplier applied across all AegisWorks generators and reactors.")
                .defineInRange("generationOutputMultiplier",
                        ConfigBalanceHelper.DEFAULT_GENERATION_MULT, 0.1D, 100.0D);
        OVERDRIVE_OUTPUT_MULTIPLIER = builder
                .comment("FE/t output multiplier when Reactor High-Output Overdrive Mode is active (default 1.45).")
                .defineInRange("overdriveOutputMultiplier",
                        ConfigBalanceHelper.DEFAULT_OVERDRIVE_MULT, 1.0D, 10.0D);
        builder.pop();

        builder.comment("Phase 115: Defense turret and electric weapon combat scaling.").push("defense_and_weapons");
        TURRET_DAMAGE_MULTIPLIER = builder
                .comment("Base damage multiplier for Defense Laser Turrets (default 1.0).")
                .defineInRange("turretDamageMultiplier",
                        ConfigBalanceHelper.DEFAULT_TURRET_DAMAGE_MULT, 0.1D, 100.0D);
        TURRET_BASE_RANGE = builder
                .comment("Base detection & firing range in blocks for Defense Laser Turrets (default 16).")
                .defineInRange("turretBaseRange",
                        ConfigBalanceHelper.DEFAULT_TURRET_BASE_RANGE, 4, 64);
        WEAPON_DAMAGE_MULTIPLIER = builder
                .comment("Global damage multiplier for AegisWorks electric weapons (default 1.0).")
                .defineInRange("weaponDamageMultiplier",
                        ConfigBalanceHelper.DEFAULT_WEAPON_DAMAGE_MULT, 0.1D, 100.0D);
        builder.pop();

        builder.comment("Phase 115: Machine XP gain and level-linked virtual upgrade caps.").push("upgrades_and_leveling");
        MACHINE_XP_MULTIPLIER = builder
                .comment("Global multiplier for machine XP gained from active operation (default 1.0).")
                .defineInRange("machineXpMultiplier",
                        ConfigBalanceHelper.DEFAULT_MACHINE_XP_MULT, 0.0D, 100.0D);
        BASE_UPGRADE_LIMIT = builder
                .comment("Initial total virtual upgrade capacity at Machine Level 1 (default 4).")
                .defineInRange("baseUpgradeLimit",
                        ConfigBalanceHelper.DEFAULT_BASE_UPGRADE_LIMIT, 1, 64);
        MAX_STANDARD_UPGRADE_LIMIT = builder
                .comment("Total virtual upgrade capacity at standard max Machine Level 99 (default 96).")
                .defineInRange("maxStandardUpgradeLimit",
                        ConfigBalanceHelper.DEFAULT_MAX_STANDARD_UPGRADE_LIMIT, 4, 512);
        builder.pop();

        builder.comment("Phase 115: Dimensional Rift Raid voluntary factory defense challenge settings.")
                .push("dimensional_raid");
        ENABLE_DIMENSIONAL_RAID = builder
                .comment("When true, players can voluntarily trigger Dimensional Rift Raids from a formed Singularity Reactor.")
                .define("enableDimensionalRaid", ConfigBalanceHelper.DEFAULT_ENABLE_DIMENSIONAL_RAID);
        RAID_ACTIVATION_COST_FE = builder
                .comment("FE consumed from the Singularity Reactor buffer to trigger a Dimensional Rift Raid (default 500000).")
                .defineInRange("raidActivationCostFe",
                        ConfigBalanceHelper.DEFAULT_RAID_ACTIVATION_COST_FE, 0, 1_000_000_000);
        RAID_DIFFICULTY_MULTIPLIER = builder
                .comment("Health and attack damage multiplier for Dimensional Rift Raid invaders & boss (default 1.0).")
                .defineInRange("raidDifficultyMultiplier",
                        ConfigBalanceHelper.DEFAULT_RAID_DIFFICULTY_MULT, 0.25D, 10.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private Config() {}

    public static int maxRangeWidth() {
        return MAX_RANGE_WIDTH.get();
    }

    public static int maxRangeDepth() {
        return MAX_RANGE_DEPTH.get();
    }

    public static int maxRangeHeight() {
        return MAX_RANGE_HEIGHT.get();
    }

    public static int maxVolume() {
        return MAX_VOLUME.get();
    }

    public static int energyPerBlock() {
        return ENERGY_PER_BLOCK.get();
    }

    public static double baseOpsPerTick() {
        return BASE_OPS_PER_TICK.get();
    }

    public static double opsPerMotor() {
        return OPS_PER_MOTOR.get();
    }

    public static double maxOpsPerTick() {
        return MAX_OPS_PER_TICK.get();
    }

    public static int baseEnergyCapacity() {
        return BASE_ENERGY_CAPACITY.get();
    }

    public static int energyPerBattery() {
        return ENERGY_PER_BATTERY.get();
    }

    public static int maxEnergyCapacity() {
        return MAX_ENERGY_CAPACITY.get();
    }

    /** FE the synthetic ore synthesizer accepts per tick (Phase 30). */
    public static int synthesizerMaxReceive() {
        return Math.max(1, SYNTHESIZER_MAX_RECEIVE.get());
    }

    public static int maxEnergyReceive() {
        return MAX_ENERGY_RECEIVE.get();
    }

    public static int maxUpgradeCount() {
        return MAX_UPGRADE_COUNT.get();
    }

    /** Cable nodes (re)discovered per server tick; there is no network size cap. */
    public static int cableRebuildNodesPerTick() {
        return Math.max(1, CABLE_REBUILD_NODES_PER_TICK.get());
    }

    /** Total operations all quarries may perform per tick; 0 means unlimited. */
    public static int globalOperationsPerTick() {
        return GLOBAL_MAX_OPS_PER_TICK.get();
    }

    public static int visualSyncMinIntervalTicks() {
        return Math.max(1, VISUAL_SYNC_MIN_INTERVAL.get());
    }

    public static int visualSyncMaxIntervalTicks() {
        return Math.max(visualSyncMinIntervalTicks(), VISUAL_SYNC_MAX_INTERVAL.get());
    }

    public static int visualSyncFullRateMachines() {
        return Math.max(1, VISUAL_SYNC_FULL_RATE_MACHINES.get());
    }

    /** Positions one visual update of a fast quarry may cover (Phase 9 sync bound). */
    public static int visualMaxBlocksPerUpdate() {
        return Math.max(1, VISUAL_MAX_BLOCKS_PER_UPDATE.get());
    }

    public static boolean enableMiningProfiling() {
        return ENABLE_MINING_PROFILING.get();
    }

    /** Backoff in ticks between full-inventory retries. */
    public static int inventoryRetryCooldownTicks() {
        return Math.max(1, INVENTORY_RETRY_COOLDOWN.get());
    }

    public static int pipeItemsPerTick() {
        return Math.max(1, PIPE_ITEMS_PER_TICK.get());
    }

    public static int pipeMaxHops() {
        return Math.max(1, PIPE_MAX_HOPS.get());
    }

    public static int logisticsVisualMinIntervalTicks() {
        return Math.max(1, LOGISTICS_VISUAL_MIN_INTERVAL.get());
    }

    /** Internal FE buffer of one energy buffer block (Phase 17). */
    public static int energyBufferCapacity() {
        return Math.max(1, ENERGY_BUFFER_CAPACITY.get());
    }

    /** FE the energy buffer accepts per tick (Phase 17). */
    public static int energyBufferMaxReceive() {
        return Math.max(1, ENERGY_BUFFER_MAX_RECEIVE.get());
    }

    /** FE the energy buffer pushes into one neighbour per tick (Phase 17). */
    public static int energyBufferPushPerTick() {
        return Math.max(0, ENERGY_BUFFER_PUSH_PER_TICK.get());
    }

    /** Fixed mining width of the large quarry (Phase 20). */
    public static int largeQuarryWidth() {
        return Math.max(1, LARGE_QUARRY_WIDTH.get());
    }

    /** Fixed mining depth of the large quarry (Phase 20). */
    public static int largeQuarryDepth() {
        return Math.max(1, LARGE_QUARRY_DEPTH.get());
    }

    /** Fixed mining height of the large quarry (Phase 20). */
    public static int largeQuarryHeight() {
        return Math.max(1, LARGE_QUARRY_HEIGHT.get());
    }

    /** FE per destroyed block of the large quarry (Phase 20). */
    public static int largeQuarryEnergyPerBlock() {
        return Math.max(0, LARGE_QUARRY_ENERGY_PER_BLOCK.get());
    }

    /** Configured blocks per tick of the large quarry (Phase 20). */
    public static double largeQuarryOperationsPerTick() {
        return Math.max(0.0D, LARGE_QUARRY_OPS_PER_TICK.get());
    }

    /** Hard per-tick block cap of the large quarry (Phase 20). */
    public static double largeQuarryMaxOperationsPerTick() {
        return Math.max(1.0D, LARGE_QUARRY_MAX_OPS_PER_TICK.get());
    }

    /** Internal FE buffer of the large quarry (Phase 20). */
    public static int largeQuarryCapacity() {
        return Math.max(1, LARGE_QUARRY_CAPACITY.get());
    }

    /** FE the large quarry accepts per tick (Phase 20). */
    public static int largeQuarryMaxReceive() {
        return Math.max(1, LARGE_QUARRY_MAX_RECEIVE.get());
    }

    public static int crafterCapacity() {
        return Math.max(1, CRAFTER_CAPACITY.get());
    }

    public static int crafterMaxReceive() {
        return Math.max(1, CRAFTER_MAX_RECEIVE.get());
    }

    public static int crafterCraftTimeTicks() {
        return Math.max(1, CRAFTER_CRAFT_TIME.get());
    }

    public static int crafterEnergyPerTick() {
        return Math.max(0, CRAFTER_ENERGY_PER_TICK.get());
    }

    public static int crafterRetryIntervalTicks() {
        return Math.max(1, CRAFTER_RETRY_INTERVAL.get());
    }

    public static int crusherCapacity() {
        return Math.max(1, CRUSHER_CAPACITY.get());
    }

    public static int crusherMaxReceive() {
        return Math.max(1, CRUSHER_MAX_RECEIVE.get());
    }

    public static int crusherTimeTicks() {
        return Math.max(1, CRUSHER_TIME_TICKS.get());
    }

    public static int crusherEnergyPerTick() {
        return Math.max(0, CRUSHER_ENERGY_PER_TICK.get());
    }

    public static int furnaceCapacity() {
        return Math.max(1, FURNACE_CAPACITY.get());
    }

    public static int furnaceMaxReceive() {
        return Math.max(1, FURNACE_MAX_RECEIVE.get());
    }

    public static int furnaceSpeedPercent() {
        return Math.max(1, FURNACE_SPEED_PERCENT.get());
    }

    public static int furnaceEnergyPerTick() {
        return Math.max(0, FURNACE_ENERGY_PER_TICK.get());
    }

    /** Ticks to wait before an idle processing machine re-checks its recipe. */
    public static int machineRetryIntervalTicks() {
        return com.industrialcore.machine.ProcessingBalance.RETRY_INTERVAL_TICKS;
    }

    /** Internal FE buffer of the matter reconstructor (Phase 15). */
    public static int reconstructorCapacity() {
        return Math.max(1, RECONSTRUCTOR_CAPACITY.get());
    }

    /** FE the matter reconstructor accepts per tick (Phase 15). */
    public static int reconstructorMaxReceive() {
        return Math.max(1, RECONSTRUCTOR_MAX_RECEIVE.get());
    }

    /** Internal FE buffer of the seed cultivator (Phase 21). */
    public static int seedCultivatorCapacity() {
        return Math.max(1, SEED_CULTIVATOR_CAPACITY.get());
    }

    /** FE the seed cultivator accepts per tick (Phase 21). */
    public static int seedCultivatorMaxReceive() {
        return Math.max(1, SEED_CULTIVATOR_MAX_RECEIVE.get());
    }

    /** Internal FE buffer of the matter amplifier (Phase 21). */
    public static int matterAmplifierCapacity() {
        return Math.max(1, MATTER_AMPLIFIER_CAPACITY.get());
    }

    /** FE the matter amplifier accepts per tick (Phase 21). */
    public static int matterAmplifierMaxReceive() {
        return Math.max(1, MATTER_AMPLIFIER_MAX_RECEIVE.get());
    }

    /** Phase 97: Whether Survival crafting of Tier 2-5 machines is gated by prerequisite machine levels. */
    public static boolean enableCraftUnlockGate() {
        try {
            return ENABLE_CRAFT_UNLOCK_GATE.get();
        } catch (IllegalStateException ex) {
            return true;
        }
    }

    /** Whether to display the one-time AegisWorks Open Beta Discord welcome message on first world login. */
    public static boolean showBetaWelcomeMessage() {
        try {
            return SHOW_BETA_WELCOME_MESSAGE.get();
        } catch (IllegalStateException ex) {
            return true;
        }
    }

    // -------------------------------------------------------------------------
    // Phase 115: Modpack-Ready 5-Category Balance Accessors (JVM-test safe)
    // -------------------------------------------------------------------------

    public static double generationOutputMultiplier() {
        try {
            return ConfigBalanceHelper.clampMultiplier(
                    GENERATION_OUTPUT_MULTIPLIER.get(), 0.1D, 100.0D, ConfigBalanceHelper.DEFAULT_GENERATION_MULT);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_GENERATION_MULT;
        }
    }

    public static double overdriveOutputMultiplier() {
        try {
            return ConfigBalanceHelper.clampMultiplier(
                    OVERDRIVE_OUTPUT_MULTIPLIER.get(), 1.0D, 10.0D, ConfigBalanceHelper.DEFAULT_OVERDRIVE_MULT);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_OVERDRIVE_MULT;
        }
    }

    public static double processingSpeedMultiplier() {
        try {
            return ConfigBalanceHelper.clampMultiplier(
                    PROCESSING_SPEED_MULTIPLIER.get(), 0.1D, 100.0D, ConfigBalanceHelper.DEFAULT_PROCESSING_SPEED_MULT);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_PROCESSING_SPEED_MULT;
        }
    }

    public static double energyConsumptionMultiplier() {
        try {
            return ConfigBalanceHelper.clampMultiplier(
                    ENERGY_CONSUMPTION_MULTIPLIER.get(), 0.0D, 100.0D, ConfigBalanceHelper.DEFAULT_ENERGY_CONSUMPTION_MULT);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_ENERGY_CONSUMPTION_MULT;
        }
    }

    public static double turretDamageMultiplier() {
        try {
            return ConfigBalanceHelper.clampMultiplier(
                    TURRET_DAMAGE_MULTIPLIER.get(), 0.1D, 100.0D, ConfigBalanceHelper.DEFAULT_TURRET_DAMAGE_MULT);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_TURRET_DAMAGE_MULT;
        }
    }

    public static int turretBaseRange() {
        try {
            return ConfigBalanceHelper.clampInt(TURRET_BASE_RANGE.get(), 4, 64);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_TURRET_BASE_RANGE;
        }
    }

    public static double weaponDamageMultiplier() {
        try {
            return ConfigBalanceHelper.clampMultiplier(
                    WEAPON_DAMAGE_MULTIPLIER.get(), 0.1D, 100.0D, ConfigBalanceHelper.DEFAULT_WEAPON_DAMAGE_MULT);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_WEAPON_DAMAGE_MULT;
        }
    }

    public static double machineXpMultiplier() {
        try {
            return ConfigBalanceHelper.clampMultiplier(
                    MACHINE_XP_MULTIPLIER.get(), 0.0D, 100.0D, ConfigBalanceHelper.DEFAULT_MACHINE_XP_MULT);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_MACHINE_XP_MULT;
        }
    }

    public static int baseUpgradeLimit() {
        try {
            return ConfigBalanceHelper.clampInt(BASE_UPGRADE_LIMIT.get(), 1, 64);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_BASE_UPGRADE_LIMIT;
        }
    }

    public static int maxStandardUpgradeLimit() {
        try {
            return ConfigBalanceHelper.clampInt(MAX_STANDARD_UPGRADE_LIMIT.get(), 4, 512);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_MAX_STANDARD_UPGRADE_LIMIT;
        }
    }

    public static boolean enableDimensionalRaid() {
        try {
            return ENABLE_DIMENSIONAL_RAID.get();
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_ENABLE_DIMENSIONAL_RAID;
        }
    }

    public static int raidActivationCostFe() {
        try {
            return ConfigBalanceHelper.clampInt(RAID_ACTIVATION_COST_FE.get(), 0, 1_000_000_000);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_RAID_ACTIVATION_COST_FE;
        }
    }

    public static double raidDifficultyMultiplier() {
        try {
            return ConfigBalanceHelper.clampMultiplier(
                    RAID_DIFFICULTY_MULTIPLIER.get(), 0.25D, 10.0D, ConfigBalanceHelper.DEFAULT_RAID_DIFFICULTY_MULT);
        } catch (IllegalStateException ex) {
            return ConfigBalanceHelper.DEFAULT_RAID_DIFFICULTY_MULT;
        }
    }
}

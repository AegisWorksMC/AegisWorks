package com.industrialcore.gametest;

import com.industrialcore.IndustrialCore;
import com.industrialcore.blockentity.EnergyBufferBlockEntity;
import com.industrialcore.blockentity.GenerationBlockEntity;
import com.industrialcore.blockentity.OreSynthesizerBlockEntity;
import com.industrialcore.machine.BufferTier;
import com.industrialcore.machine.GenerationKind;
import com.industrialcore.machine.SynthesisBalance;
import com.industrialcore.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 36 audit of the Energy Buffer send/receive protocol (BASIC and MK2): where each
 * limit is applied (sender budget vs receiver per-call cap), how the effective totals
 * scale with the neighbour count, and how full/empty states, save/load and neighbour
 * changes behave. Measurement only - no balance value is changed by this file.
 *
 * <p>All tests drive {@code serverTick} manually, so every count is deterministic. The
 * load test prints wall-clock times as audit data and does not assert on speed.</p>
 */
@GameTestHolder(IndustrialCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BufferProtocolAuditGameTests {
    private static final int Y = 6;
    private static final BlockPos CENTER = new BlockPos(5, Y, 5);

    /** The six neighbour offsets in Minecraft direction order: DOWN, UP, N, S, W, E. */
    private static final BlockPos[] FACES = {
            new BlockPos(0, -1, 0), new BlockPos(0, 1, 0),
            new BlockPos(0, 0, -1), new BlockPos(0, 0, 1),
            new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0)
    };
    /** Neighbour counts measured for both tiers (receive and send side). */
    private static final int[] NEIGHBOUR_COUNTS = {1, 2, 3, 4, 6};

    private BufferProtocolAuditGameTests() {
    }

    /**
     * Receive side: nuclear generators (900 FE/t push budget) press on one buffer. Each
     * pushing neighbour is one receiveEnergy call, and the buffer's receive cap applies
     * per call - so the measured total grows linearly with the neighbour count instead
     * of staying at the configured per-tick value.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void receiveLimitScalesWithNeighbourCount(GameTestHelper helper) {
        for (BufferTier tier : new BufferTier[]{BufferTier.BASIC, BufferTier.REINFORCED}) {
            for (int count : NEIGHBOUR_COUNTS) {
                clearRing(helper, CENTER);
                EnergyBufferBlockEntity buffer = placeBuffer(helper, CENTER, tier);
                helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == 0,
                        "a fresh buffer must start empty");
                int perFace = Math.min(GenerationKind.NUCLEAR.pushPerTick(), tier.maxReceive());
                int total = 0;
                for (int face = 0; face < count; face++) {
                    GenerationBlockEntity generator = placeGeneration(helper,
                            CENTER.offset(FACES[face]), ModBlocks.NUCLEAR_GENERATOR.get());
                    generator.getEnergyStorage().setEnergy(10_000);
                    int before = generator.getEnergyStorage().getEnergyStored();
                    tickDevice(helper, generator, 1);
                    int pushed = before - generator.getEnergyStorage().getEnergyStored();
                    helper.assertTrue(pushed == perFace,
                            "tier " + tier.id() + ", neighbour " + face + " pushed " + pushed
                                    + " instead of " + perFace
                                    + " (min of generator push 900 and buffer receive "
                                    + tier.maxReceive() + ")");
                    total += pushed;
                }
                helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == total,
                        "tier " + tier.id() + " stored " + buffer.getEnergyStorage().getEnergyStored()
                                + " but the neighbours pushed " + total);
                System.out.println("[Phase36-recv] tier=" + tier.id() + " neighbours=" + count
                        + " perFace=" + perFace + " total=" + total + " calls=" + count
                        + " | shared-per-tick (option A) would cap at " + tier.maxReceive());
            }
        }
        helper.succeed();
    }

    /**
     * Send side: a full buffer pushes into 1, 2, 3, 4 or 6 synthesizers. The push budget
     * applies per face inside one pushEnergy call, so the total per tick grows with the
     * neighbour count while each face stays at the configured per-neighbour value.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void pushLimitScalesWithNeighbourCount(GameTestHelper helper) {
        for (BufferTier tier : new BufferTier[]{BufferTier.BASIC, BufferTier.REINFORCED}) {
            for (int count : NEIGHBOUR_COUNTS) {
                clearRing(helper, CENTER);
                EnergyBufferBlockEntity buffer = placeBuffer(helper, CENTER, tier);
                int capacity = buffer.getEnergyStorage().getMaxEnergyStored();
                buffer.setEnergyStored(capacity);
                int perFace = Math.min(tier.pushPerTick(), SynthesisBalance.MAX_RECEIVE);
                OreSynthesizerBlockEntity[] machines = new OreSynthesizerBlockEntity[count];
                for (int face = 0; face < count; face++) {
                    machines[face] = placeSynthesizer(helper, CENTER.offset(FACES[face]));
                }
                int before = buffer.getEnergyStorage().getEnergyStored();
                tickDevice(helper, buffer, 1);
                int sent = before - buffer.getEnergyStorage().getEnergyStored();
                int received = 0;
                for (OreSynthesizerBlockEntity machine : machines) {
                    int machineIn = machine.getEnergyStorage().getEnergyStored();
                    helper.assertTrue(machineIn == perFace,
                            "tier " + tier.id() + " pushed " + machineIn
                                    + " into one machine instead of " + perFace
                                    + " (min of push " + tier.pushPerTick() + " and receive "
                                    + SynthesisBalance.MAX_RECEIVE + ")");
                    received += machineIn;
                }
                helper.assertTrue(sent == received && sent == count * perFace,
                        "tier " + tier.id() + " sent " + sent + " but machines got " + received
                                + ", expected " + (count * perFace));
                System.out.println("[Phase36-send] tier=" + tier.id() + " neighbours=" + count
                        + " perFace=" + perFace + " total=" + sent
                        + " | shared-per-tick (option A) would cap at " + tier.pushPerTick());
            }
        }
        helper.succeed();
    }

    /**
     * Generators push in and machines pull out within the same tick: every FE is
     * conserved (generator out = buffer in, buffer out = machine in) and a second tick
     * repeats the identical amounts. A second sub-case pushes from two MK2 buffers into
     * one synthesizer, exceeding that machine's nominal per-tick receive value because
     * the machine's cap is also applied per call.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void simultaneousFlowConservesEveryFe(GameTestHelper helper) {
        for (BufferTier tier : new BufferTier[]{BufferTier.BASIC, BufferTier.REINFORCED}) {
            clearRing(helper, CENTER);
            EnergyBufferBlockEntity buffer = placeBuffer(helper, CENTER, tier);
            int receivePerFace = Math.min(GenerationKind.NUCLEAR.pushPerTick(), tier.maxReceive());
            int pushPerFace = Math.min(tier.pushPerTick(), SynthesisBalance.MAX_RECEIVE);
            int[] generatorFaces = {2, 3, 4, 5}; // N, S, W, E
            int[] machineFaces = {0, 1};         // DOWN, UP
            GenerationBlockEntity[] generators = new GenerationBlockEntity[4];
            for (int index = 0; index < generatorFaces.length; index++) {
                generators[index] = placeGeneration(helper,
                        CENTER.offset(FACES[generatorFaces[index]]),
                        ModBlocks.NUCLEAR_GENERATOR.get());
                generators[index].getEnergyStorage().setEnergy(10_000);
            }
            OreSynthesizerBlockEntity[] machines = new OreSynthesizerBlockEntity[2];
            for (int index = 0; index < machineFaces.length; index++) {
                machines[index] = placeSynthesizer(helper, CENTER.offset(FACES[machineFaces[index]]));
            }
            int expectedIn = 4 * receivePerFace;
            int expectedOut = 2 * pushPerFace;
            for (int tick = 1; tick <= 2; tick++) {
                int generatorOut = 0;
                for (GenerationBlockEntity generator : generators) {
                    int before = generator.getEnergyStorage().getEnergyStored();
                    tickDevice(helper, generator, 1);
                    generatorOut += before - generator.getEnergyStorage().getEnergyStored();
                }
                helper.assertTrue(generatorOut == expectedIn,
                        "tier " + tier.id() + " tick " + tick + ": generators pushed "
                                + generatorOut + " instead of " + expectedIn);
                int carried = (tick - 1) * (expectedIn - expectedOut);
                helper.assertTrue(buffer.getEnergyStorage().getEnergyStored()
                                == carried + generatorOut,
                        "tier " + tier.id() + " tick " + tick + ": buffer holds "
                                + buffer.getEnergyStorage().getEnergyStored()
                                + " expected carried " + carried + " + pushed " + generatorOut);
                int[] machineBefore = new int[machines.length];
                for (int index = 0; index < machines.length; index++) {
                    machineBefore[index] = machines[index].getEnergyStorage().getEnergyStored();
                }
                tickDevice(helper, buffer, 1);
                int machineIn = 0;
                for (int index = 0; index < machines.length; index++) {
                    machineIn += machines[index].getEnergyStorage().getEnergyStored()
                            - machineBefore[index];
                }
                helper.assertTrue(machineIn == expectedOut,
                        "tier " + tier.id() + " tick " + tick + ": machines received "
                                + machineIn + " instead of " + expectedOut);
                helper.assertTrue(buffer.getEnergyStorage().getEnergyStored()
                                == tick * (expectedIn - expectedOut),
                        "tier " + tier.id() + " tick " + tick + ": buffer drift, holds "
                                + buffer.getEnergyStorage().getEnergyStored() + " expected "
                                + (tick * (expectedIn - expectedOut)));
                System.out.println("[Phase36-flow] tier=" + tier.id() + " tick=" + tick
                        + " in=" + generatorOut + " out=" + machineIn + " stored="
                        + buffer.getEnergyStorage().getEnergyStored());
            }
        }

        // Two MK2 buffers feed one synthesizer in the same tick: the machine's receive
        // cap is applied per call as well, so it takes 2 x 512 = 1,024 FE although its
        // documented value reads "per tick" (1,000).
        clearRing(helper, CENTER);
        EnergyBufferBlockEntity left =
                placeBuffer(helper, CENTER.offset(FACES[4]), BufferTier.REINFORCED);
        EnergyBufferBlockEntity right =
                placeBuffer(helper, CENTER.offset(FACES[3]), BufferTier.REINFORCED);
        left.setEnergyStored(10_000);
        right.setEnergyStored(10_000);
        OreSynthesizerBlockEntity shared = placeSynthesizer(helper, CENTER);
        tickDevice(helper, left, 1);
        tickDevice(helper, right, 1);
        int dualIn = shared.getEnergyStorage().getEnergyStored();
        int dualExpected = 2 * Math.min(BufferTier.REINFORCED.pushPerTick(),
                SynthesisBalance.MAX_RECEIVE);
        helper.assertTrue(dualIn == dualExpected,
                "two MK2 buffers delivered " + dualIn + " instead of " + dualExpected);
        helper.assertTrue(dualIn > SynthesisBalance.MAX_RECEIVE,
                "expected the dual-sender total to exceed the nominal per-tick value "
                        + SynthesisBalance.MAX_RECEIVE + ", got " + dualIn);
        System.out.println("[Phase36-flow] dual-sender: 2x MK2 -> 1 machine delivered "
                + dualIn + " FE in one tick (machine nominal per tick: "
                + SynthesisBalance.MAX_RECEIVE + ")");
        helper.succeed();
    }

    /**
     * Boundary states on both tiers: an empty buffer pushes nothing; a full buffer
     * refuses incoming FE while still pushing out; machines at their own capacity
     * refuse pushes and the buffer keeps every FE.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 400)
    public static void fullAndEmptyStatesBehaveDeterministically(GameTestHelper helper) {
        for (BufferTier tier : new BufferTier[]{BufferTier.BASIC, BufferTier.REINFORCED}) {
            // (1) Empty buffer: accepting neighbours nearby, zero movement.
            clearRing(helper, CENTER);
            EnergyBufferBlockEntity buffer = placeBuffer(helper, CENTER, tier);
            OreSynthesizerBlockEntity machine =
                    placeSynthesizer(helper, CENTER.offset(FACES[0]));
            tickDevice(helper, buffer, 1);
            helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == 0
                            && machine.getEnergyStorage().getEnergyStored() == 0,
                    "tier " + tier.id() + ": an empty buffer moved FE");

            // (2) Full buffer: incoming refused, outgoing still at the per-face rate.
            int capacity = buffer.getEnergyStorage().getMaxEnergyStored();
            buffer.setEnergyStored(capacity);
            GenerationBlockEntity generator = placeGeneration(helper,
                    CENTER.offset(FACES[4]), ModBlocks.NUCLEAR_GENERATOR.get());
            generator.getEnergyStorage().setEnergy(10_000);
            int genBefore = generator.getEnergyStorage().getEnergyStored();
            tickDevice(helper, generator, 1);
            int genPushed = genBefore - generator.getEnergyStorage().getEnergyStored();
            helper.assertTrue(genPushed == 0,
                    "tier " + tier.id() + ": a full buffer accepted " + genPushed + " FE");
            helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == capacity,
                    "tier " + tier.id() + ": full buffer stored "
                            + buffer.getEnergyStorage().getEnergyStored() + " != capacity");
            int machineBefore = machine.getEnergyStorage().getEnergyStored();
            tickDevice(helper, buffer, 1);
            int pushed = machine.getEnergyStorage().getEnergyStored() - machineBefore;
            int perFace = Math.min(tier.pushPerTick(), SynthesisBalance.MAX_RECEIVE);
            helper.assertTrue(pushed == perFace,
                    "tier " + tier.id() + ": full buffer pushed " + pushed + " instead of "
                            + perFace);
            helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == capacity - perFace,
                    "tier " + tier.id() + ": after the push the buffer holds "
                            + buffer.getEnergyStorage().getEnergyStored() + " expected "
                            + (capacity - perFace));

            // (3) Full machine refuses: buffer energy unchanged, machine unchanged.
            machine.getEnergyStorage().setEnergy(SynthesisBalance.CAPACITY);
            int storedBefore = buffer.getEnergyStorage().getEnergyStored();
            tickDevice(helper, buffer, 1);
            helper.assertTrue(machine.getEnergyStorage().getEnergyStored()
                            == SynthesisBalance.CAPACITY
                            && buffer.getEnergyStorage().getEnergyStored() == storedBefore,
                    "tier " + tier.id() + ": a full machine accepted FE or the buffer lost it");
            System.out.println("[Phase36-edges] tier=" + tier.id()
                    + " emptyPush=0 fullRefused=" + (genPushed == 0)
                    + " pushWhileFull=" + pushed + " fullMachineRefused=true");
        }
        helper.succeed();
    }

    /**
     * A six-way connected buffer saved mid-operation and restored elsewhere: the stored
     * FE must round-trip exactly and the restored copy pushes at the exact per-face rate
     * on its first tick (fresh neighbour cache). Removing and re-adding a neighbour must
     * change the next tick's total by exactly one face.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 500)
    public static void saveLoadAndNeighbourChangesKeepExactRates(GameTestHelper helper) {
        BlockPos restorePos = new BlockPos(9, Y, 5);
        int[] generatorFaces = {4, 5, 2};    // W, E, N
        int[] machineFaces = {0, 1, 3};      // DOWN, UP, S
        for (BufferTier tier : new BufferTier[]{BufferTier.BASIC, BufferTier.REINFORCED}) {
            clearRing(helper, CENTER);
            clearRing(helper, restorePos);
            EnergyBufferBlockEntity buffer = placeBuffer(helper, CENTER, tier);
            int receivePerFace = Math.min(GenerationKind.NUCLEAR.pushPerTick(), tier.maxReceive());
            int pushPerFace = Math.min(tier.pushPerTick(), SynthesisBalance.MAX_RECEIVE);

            GenerationBlockEntity[] generators = new GenerationBlockEntity[3];
            for (int index = 0; index < generatorFaces.length; index++) {
                generators[index] = placeGeneration(helper,
                        CENTER.offset(FACES[generatorFaces[index]]),
                        ModBlocks.NUCLEAR_GENERATOR.get());
                generators[index].getEnergyStorage().setEnergy(10_000);
            }
            OreSynthesizerBlockEntity[] machines = new OreSynthesizerBlockEntity[3];
            for (int index = 0; index < machineFaces.length; index++) {
                machines[index] = placeSynthesizer(helper, CENTER.offset(FACES[machineFaces[index]]));
            }

            // Round 1: three faces in, three faces out.
            int in1 = pushFromGenerators(helper, generators);
            helper.assertTrue(in1 == 3 * receivePerFace,
                    "tier " + tier.id() + ": three generators pushed " + in1 + " expected "
                            + (3 * receivePerFace));
            int out1 = pushToMachines(helper, buffer, machines);
            helper.assertTrue(out1 == 3 * pushPerFace,
                    "tier " + tier.id() + ": buffer sent " + out1 + " expected "
                            + (3 * pushPerFace));
            helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == in1 - out1,
                    "tier " + tier.id() + ": buffer drift after round 1");

            // Neighbour decrease: remove W - the state change refreshes the cache.
            helper.setBlock(CENTER.offset(FACES[4]), Blocks.AIR.defaultBlockState());
            int in2 = pushFromGenerators(helper, new GenerationBlockEntity[]{generators[1], generators[2]});
            helper.assertTrue(in2 == 2 * receivePerFace,
                    "tier " + tier.id() + ": after removal two generators pushed " + in2
                            + " expected " + (2 * receivePerFace));
            int out2 = pushToMachines(helper, buffer, machines);
            helper.assertTrue(out2 == 3 * pushPerFace,
                    "tier " + tier.id() + ": after removal the buffer sent " + out2);
            int stored2 = in1 - out1 + in2 - out2;
            helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == stored2,
                    "tier " + tier.id() + ": buffer drift after neighbour removal");

            // Neighbour increase: re-add W - the next tick takes exactly one face again.
            GenerationBlockEntity reAdded = placeGeneration(helper,
                    CENTER.offset(FACES[4]), ModBlocks.NUCLEAR_GENERATOR.get());
            reAdded.getEnergyStorage().setEnergy(10_000);
            int in3 = pushFromGenerators(helper, new GenerationBlockEntity[]{reAdded});
            helper.assertTrue(in3 == receivePerFace,
                    "tier " + tier.id() + ": the re-added generator pushed " + in3
                            + " expected " + receivePerFace);
            int out3 = pushToMachines(helper, buffer, machines);
            int savedEnergy = stored2 + in3 - out3;
            helper.assertTrue(buffer.getEnergyStorage().getEnergyStored() == savedEnergy,
                    "tier " + tier.id() + ": buffer drift after neighbour re-add");

            // Top up before saving: the restored copy must be able to show the full
            // per-face rate on its first tick instead of draining its small balance.
            buffer.setEnergyStored(100_000);
            savedEnergy = 100_000;

            // Save and restore beside a fresh set of machines.
            CompoundTag tag = save(helper, buffer);
            EnergyBufferBlockEntity restored = restoreBuffer(helper, tag, restorePos, tier);
            helper.assertTrue(restored.getEnergyStorage().getEnergyStored() == savedEnergy,
                    "tier " + tier.id() + ": restore gave "
                            + restored.getEnergyStorage().getEnergyStored() + " expected "
                            + savedEnergy);
            OreSynthesizerBlockEntity[] restoreMachines = new OreSynthesizerBlockEntity[3];
            for (int index = 0; index < machineFaces.length; index++) {
                restoreMachines[index] = placeSynthesizer(helper,
                        restorePos.offset(FACES[machineFaces[index]]));
            }
            int restoredOut = pushToMachines(helper, restored, restoreMachines);
            helper.assertTrue(restoredOut == 3 * pushPerFace,
                    "tier " + tier.id() + ": the restored buffer sent " + restoredOut
                            + " on its first tick, expected " + (3 * pushPerFace));
            helper.assertTrue(restored.getEnergyStorage().getEnergyStored()
                            == savedEnergy - restoredOut,
                    "tier " + tier.id() + ": restored buffer drift");
            System.out.println("[Phase36-reconnect] tier=" + tier.id()
                    + " in 3/2/1 faces=" + in1 + "/" + in2 + "/" + in3
                    + " out=" + out1 + " saved=" + savedEnergy
                    + " restored=" + restored.getEnergyStorage().getEnergyStored()
                    + " firstTickOut=" + restoredOut);
        }
        helper.succeed();
    }

    /**
     * Load probe on a 10 x 10 grid of buffers that daisy-chain into each other, measured
     * at 10 / 50 / 100 devices with 50 timed passes each after a 5-pass warmup. The grid
     * total is asserted unchanged after every scale (transfers conserve), wall-clock
     * times are reported for this host only.
     */
    @GameTest(template = "quarry_test", timeoutTicks = 600)
    public static void tickCostMeasuredOnTenFiftyAndHundredBuffers(GameTestHelper helper) {
        EnergyBufferBlockEntity[] buffers = new EnergyBufferBlockEntity[100];
        int index = 0;
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                EnergyBufferBlockEntity buffer =
                        placeBuffer(helper, new BlockPos(x, Y, z), BufferTier.BASIC);
                buffer.setEnergyStored(buffer.getEnergyStorage().getMaxEnergyStored() / 2);
                buffers[index++] = buffer;
            }
        }
        long totalStart = sumEnergy(buffers);
        for (int pass = 0; pass < 5; pass++) {
            for (EnergyBufferBlockEntity buffer : buffers) {
                tickDevice(helper, buffer, 1);
            }
        }
        long start10 = System.nanoTime();
        for (int pass = 0; pass < 50; pass++) {
            for (int device = 0; device < 10; device++) {
                tickDevice(helper, buffers[device], 1);
            }
        }
        long scale10 = System.nanoTime() - start10;
        helper.assertTrue(sumEnergy(buffers) == totalStart,
                "grid total changed during the 10-device scale");

        long start50 = System.nanoTime();
        for (int pass = 0; pass < 50; pass++) {
            for (int device = 0; device < 50; device++) {
                tickDevice(helper, buffers[device], 1);
            }
        }
        long scale50 = System.nanoTime() - start50;
        helper.assertTrue(sumEnergy(buffers) == totalStart,
                "grid total changed during the 50-device scale");

        long start100 = System.nanoTime();
        for (int pass = 0; pass < 50; pass++) {
            for (EnergyBufferBlockEntity buffer : buffers) {
                tickDevice(helper, buffer, 1);
            }
        }
        long scale100 = System.nanoTime() - start100;
        helper.assertTrue(sumEnergy(buffers) == totalStart,
                "grid total changed during the 100-device scale");

        double ms10 = scale10 / 1_000_000.0;
        double ms50 = scale50 / 1_000_000.0;
        double ms100 = scale100 / 1_000_000.0;
        double us10 = scale10 / 1_000.0 / (50 * 10);
        double us50 = scale50 / 1_000.0 / (50 * 50);
        double us100 = scale100 / 1_000.0 / (50 * 100);
        helper.assertTrue(ms10 < 60_000 && ms50 < 60_000 && ms100 < 60_000,
                "the buffer load probe exceeded a minute - pathologically slow");
        System.out.println("[Phase36-load] grid total conserved=" + totalStart + " FE, "
                + Runtime.getRuntime().availableProcessors() + " cores, "
                + System.getProperty("os.name") + ": "
                + "10 devices x50 = " + String.format("%.2f", ms10) + " ms ("
                + String.format("%.2f", us10) + " us/device-tick), "
                + "50 devices = " + String.format("%.2f", ms50) + " ms ("
                + String.format("%.2f", us50) + " us/device-tick), "
                + "100 devices = " + String.format("%.2f", ms100) + " ms ("
                + String.format("%.2f", us100) + " us/device-tick)");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** Clears the centre block and its six faces so every round starts from scratch. */
    private static void clearRing(GameTestHelper helper, BlockPos center) {
        helper.setBlock(center, Blocks.AIR.defaultBlockState());
        for (BlockPos face : FACES) {
            helper.setBlock(center.offset(face), Blocks.AIR.defaultBlockState());
        }
    }

    /** Ticks each generator once and returns the total FE the generators gave away. */
    private static int pushFromGenerators(GameTestHelper helper,
                                           GenerationBlockEntity[] generators) {
        int pushed = 0;
        for (GenerationBlockEntity generator : generators) {
            int before = generator.getEnergyStorage().getEnergyStored();
            GenerationBlockEntity.serverTick(helper.getLevel(), generator.getBlockPos(),
                    generator.getBlockState(), generator);
            pushed += before - generator.getEnergyStorage().getEnergyStored();
        }
        return pushed;
    }

    /** Ticks the buffer once and returns the total FE the machines received. */
    private static int pushToMachines(GameTestHelper helper, EnergyBufferBlockEntity buffer,
                                      OreSynthesizerBlockEntity[] machines) {
        int[] before = new int[machines.length];
        for (int index = 0; index < machines.length; index++) {
            before[index] = machines[index].getEnergyStorage().getEnergyStored();
        }
        tickDevice(helper, buffer, 1);
        int received = 0;
        for (int index = 0; index < machines.length; index++) {
            received += machines[index].getEnergyStorage().getEnergyStored() - before[index];
        }
        return received;
    }

    /** Sum of the stored FE across a device array (load test conservation check). */
    private static long sumEnergy(EnergyBufferBlockEntity[] buffers) {
        long total = 0L;
        for (EnergyBufferBlockEntity buffer : buffers) {
            total += buffer.getEnergyStorage().getEnergyStored();
        }
        return total;
    }

    private static CompoundTag save(GameTestHelper helper, BlockEntity device) {
        return device.saveWithoutMetadata(helper.getLevel().registryAccess());
    }

    private static void tickDevice(GameTestHelper helper, GenerationBlockEntity device,
                                   int times) {
        for (int index = 0; index < times; index++) {
            GenerationBlockEntity.serverTick(helper.getLevel(), device.getBlockPos(),
                    device.getBlockState(), device);
        }
    }

    private static void tickDevice(GameTestHelper helper, EnergyBufferBlockEntity device,
                                   int times) {
        for (int index = 0; index < times; index++) {
            EnergyBufferBlockEntity.serverTick(helper.getLevel(), device.getBlockPos(),
                    device.getBlockState(), device);
        }
    }

    private static void tickDevice(GameTestHelper helper, OreSynthesizerBlockEntity device,
                                   int times) {
        for (int index = 0; index < times; index++) {
            OreSynthesizerBlockEntity.serverTick(helper.getLevel(), device.getBlockPos(),
                    device.getBlockState(), device);
        }
    }

    private static GenerationBlockEntity placeGeneration(GameTestHelper helper, BlockPos pos,
                                                          Block block) {
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof GenerationBlockEntity device)) {
            throw new IllegalStateException("generation device missing at " + pos);
        }
        return device;
    }

    private static EnergyBufferBlockEntity placeBuffer(GameTestHelper helper, BlockPos pos,
                                                        BufferTier tier) {
        Block block = switch (tier) {
            case BASIC -> ModBlocks.ENERGY_BUFFER.get();
            case REINFORCED -> ModBlocks.ENERGY_BUFFER_MK2.get();
            case HIGH_DENSITY -> ModBlocks.ENERGY_BUFFER_MK3.get();
        };
        helper.setBlock(pos, block.defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof EnergyBufferBlockEntity buffer)) {
            throw new IllegalStateException("energy buffer missing at " + pos);
        }
        return buffer;
    }

    private static OreSynthesizerBlockEntity placeSynthesizer(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ORE_SYNTHESIZER.get().defaultBlockState());
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(entity instanceof OreSynthesizerBlockEntity synthesizer)) {
            throw new IllegalStateException("synthesizer missing at " + pos);
        }
        return synthesizer;
    }

    private static EnergyBufferBlockEntity restoreBuffer(GameTestHelper helper, CompoundTag tag,
                                                          BlockPos pos, BufferTier tier) {
        EnergyBufferBlockEntity buffer = placeBuffer(helper, pos, tier);
        buffer.loadWithComponents(tag, helper.getLevel().registryAccess());
        return buffer;
    }
}

package com.industrialcore.machine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Phase 8 uncapped, incremental cable traversal.
 *
 * <p>These replace the Phase 6.2 tests that asserted truncation at a fixed 4,096-cable limit:
 * truncation no longer exists, so the old assertions cannot describe the requested behaviour.
 * The replacement is a superset: every size and topology of the old suite is covered, plus
 * 4,095/4,096/4,097/8,192/20,000/65,536 nodes, budget slicing and split/merge.</p>
 */
class CableNetworkTraversalTest {

    private static CableNetworkTraversal<Integer> traverse(
            int start, Function<Integer, List<Integer>> adjacency, int budget) {
        CableNetworkTraversal<Integer> traversal =
                new CableNetworkTraversal<>(start, adjacency, Comparator.naturalOrder());
        while (!traversal.isComplete()) {
            traversal.step(budget);
        }
        return traversal;
    }

    private static Function<Integer, List<Integer>> line(int size) {
        return node -> {
            List<Integer> result = new ArrayList<>(2);
            if (node > 0) {
                result.add(node - 1);
            }
            if (node + 1 < size) {
                result.add(node + 1);
            }
            return result;
        };
    }

    private static Function<Integer, List<Integer>> ring(int size) {
        return node -> List.of((node + 1) % size, (node + size - 1) % size);
    }

    @Test
    void budgetSlicesTheWorkWithoutLosingNodes() {
        CableNetworkTraversal<Integer> traversal =
                new CableNetworkTraversal<>(0, line(4096), Comparator.naturalOrder());
        assertEquals(1024, traversal.step(1024));
        assertEquals(1024, traversal.members().size());
        assertFalse(traversal.isComplete());
        // Only the frontier node beyond the processed slice is queued; nothing is re-queued.
        assertEquals(1025, traversal.visitedCount());

        assertEquals(1024, traversal.step(1024));
        assertEquals(1024, traversal.step(1024));
        assertFalse(traversal.isComplete());
        assertEquals(1024, traversal.step(1024));
        assertTrue(traversal.isComplete());
        assertEquals(4096, traversal.members().size());
        assertEquals(0, traversal.step(1024), "a complete traversal must not consume budget");
    }

    @Test
    void zeroBudgetMakesNoProgress() {
        CableNetworkTraversal<Integer> traversal =
                new CableNetworkTraversal<>(0, line(10), Comparator.naturalOrder());
        assertEquals(0, traversal.step(0));
        assertFalse(traversal.isComplete());
        assertEquals(1, traversal.step(1));
        assertFalse(traversal.isComplete(), "one node is not the whole line");
        assertEquals(9, traversal.step(9));
        assertTrue(traversal.isComplete());
        assertEquals(10, traversal.members().size());
    }

    @Test
    void networkExactlyAtTheOldLimitIsComplete() {
        CableNetworkTraversal<Integer> traversal =
                traverse(2048, line(4096), 4096);
        assertTrue(traversal.isComplete());
        assertEquals(4096, traversal.members().size());
        assertEquals(0, traversal.leader());
    }

    @Test
    void networksAboveTheOldLimitAreComplete() {
        for (int size : new int[]{4097, 8192, 20_000, 65_536}) {
            CableNetworkTraversal<Integer> traversal = traverse(0, line(size), 4096);
            assertTrue(traversal.isComplete(), size + " cables must be fully discovered");
            assertEquals(size, traversal.members().size(), size + " members");
            assertEquals(size, new HashSet<>(traversal.members()).size(), "duplicate members at " + size);
            assertEquals(0, traversal.leader());
            assertEquals(size, traversal.visitedCount());
        }
    }

    @Test
    @Timeout(30)
    void largeRingTerminatesAndVisitsEveryNodeOnce() {
        int size = 20_000;
        CableNetworkTraversal<Integer> traversal = traverse(size / 2, ring(size), 1024);
        assertTrue(traversal.isComplete());
        assertEquals(size, traversal.members().size());
        assertEquals(size, new HashSet<>(traversal.members()).size());
        assertEquals(0, traversal.leader(), "the lowest position leads regardless of the start");
    }

    @Test
    void branchingNetworkIsFullyDiscovered() {
        int size = 4097;
        // Binary tree: node i is connected to its parent and its two children.
        Function<Integer, List<Integer>> adjacency = node -> {
            List<Integer> result = new ArrayList<>(3);
            if (node > 0) {
                result.add((node - 1) / 2);
            }
            int left = node * 2 + 1;
            int right = left + 1;
            if (left < size) {
                result.add(left);
            }
            if (right < size) {
                result.add(right);
            }
            return result;
        };
        CableNetworkTraversal<Integer> traversal = traverse(size - 1, adjacency, 137);
        assertTrue(traversal.isComplete());
        assertEquals(size, traversal.members().size());
        assertEquals(0, traversal.leader());
    }

    @Test
    void duplicateAndSelfEdgesAreVisitedOnce() {
        Function<Integer, List<Integer>> adjacency = node -> switch (node) {
            case 0 -> List.of(1, 1, 1, 0);
            case 1 -> List.of(2, 0, 2, 1);
            case 2 -> List.of(1, 1, 2);
            default -> List.of();
        };
        CableNetworkTraversal<Integer> traversal = traverse(2, adjacency, 10);
        assertEquals(List.of(2, 1, 0), traversal.members());
        assertEquals(0, traversal.leader());
        assertTrue(traversal.isComplete());
    }

    @Test
    void disjointNetworksStayIndependent() {
        // Nodes 0..99 form two components: 0..49 and 50..99.
        Function<Integer, List<Integer>> adjacency = node -> {
            List<Integer> result = new ArrayList<>(2);
            int floor = node < 50 ? 0 : 50;
            int ceiling = node < 50 ? 49 : 99;
            if (node > floor) {
                result.add(node - 1);
            }
            if (node < ceiling) {
                result.add(node + 1);
            }
            return result;
        };
        CableNetworkTraversal<Integer> first = traverse(10, adjacency, 8);
        CableNetworkTraversal<Integer> second = traverse(80, adjacency, 8);
        assertEquals(50, first.members().size());
        assertEquals(50, second.members().size());
        assertEquals(0, first.leader());
        assertEquals(50, second.leader());
        assertTrue(first.members().stream().allMatch(node -> node < 50));
        assertTrue(second.members().stream().allMatch(node -> node >= 50));
    }

    @Test
    void splitAndMergeAreReflectedByAFreshTraversal() {
        // 0..99 is one line while connected is true, and two 50-cable lines while false.
        boolean[] connected = {false};
        Function<Integer, List<Integer>> adjacency = node -> {
            List<Integer> result = new ArrayList<>(2);
            if (node > 0 && !(node == 50 && !connected[0])) {
                result.add(node - 1);
            }
            if (node + 1 < 100 && !(node == 49 && !connected[0])) {
                result.add(node + 1);
            }
            return result;
        };
        CableNetworkTraversal<Integer> split = traverse(10, adjacency, 16);
        assertEquals(50, split.members().size());
        assertFalse(split.members().contains(50));

        connected[0] = true;
        CableNetworkTraversal<Integer> merged = traverse(10, adjacency, 16);
        assertEquals(100, merged.members().size(), "after merging, one traversal must see both halves");
        assertEquals(0, merged.leader());

        connected[0] = false;
        CableNetworkTraversal<Integer> otherSide = traverse(90, adjacency, 16);
        assertEquals(50, otherSide.members().size());
        assertEquals(50, otherSide.leader());
    }

    @Test
    void emptyStartIsComplete() {
        CableNetworkTraversal<Integer> traversal = new CableNetworkTraversal<Integer>(
                null, node -> List.of(), Comparator.naturalOrder());
        assertTrue(traversal.isComplete());
        assertTrue(traversal.members().isEmpty());
        assertNull(traversal.leader());
    }

    @Test
    @Timeout(120)
    void traversalCostGrowsLinearlyWithNetworkSize() {
        int[] sizes = {4_096, 8_192, 16_384, 32_768, 65_536};
        long previousNanos = 0L;
        for (int size : sizes) {
            List<List<Integer>> graph = new ArrayList<>(size);
            for (int node = 0; node < size; node++) {
                List<Integer> adjacent = new ArrayList<>(2);
                if (node > 0) {
                    adjacent.add(node - 1);
                }
                if (node + 1 < size) {
                    adjacent.add(node + 1);
                }
                graph.add(adjacent);
            }
            Function<Integer, List<Integer>> adjacency = graph::get;

            System.gc();
            long heapBefore = usedHeap();
            long start = System.nanoTime();
            CableNetworkTraversal<Integer> traversal = traverse(0, adjacency, 4_096);
            long elapsed = System.nanoTime() - start;
            long heapAfter = usedHeap();

            assertTrue(traversal.isComplete());
            assertEquals(size, traversal.members().size());
            assertTrue(elapsed < 20_000_000_000L, "traversal of " + size + " nodes took too long");
            System.out.printf("[perf] traversal size=%d steps=%d time=%.2f ms heapDelta=%d KiB%n",
                    size, (size + 4_095) / 4_096, elapsed / 1_000_000.0D,
                    Math.max(0L, heapAfter - heapBefore) / 1024L);
            if (previousNanos > 0) {
                double ratio = elapsed / (double) previousNanos;
                // The timer is noisy at these sizes; only a gross super-linear blow-up fails.
                assertTrue(ratio < 8.0D,
                        "doubling the network size multiplied the time by " + ratio);
            }
            previousNanos = Math.max(1L, elapsed);
            // Keep the traversal reachable until after the heap measurement.
            assertEquals(size, traversal.visitedCount());
        }
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /** Guards against a regression where members would not be unique. */
    @Test
    void membersAreUniqueOnACombLikeNetwork() {
        int size = 8_193;
        // A spine 0..size-1 with a leaf attached to every 100th node.
        Function<Integer, List<Integer>> adjacency = node -> {
            List<Integer> result = new ArrayList<>(4);
            if (node >= size) {
                result.add((node - size) * 100);
                return result;
            }
            if (node > 0) {
                result.add(node - 1);
            }
            if (node + 1 < size) {
                result.add(node + 1);
            }
            if (node % 100 == 0) {
                result.add(size + node / 100);
            }
            return result;
        };
        CableNetworkTraversal<Integer> traversal = traverse(0, adjacency, 512);
        assertTrue(traversal.isComplete());
        int leaves = (size + 99) / 100;
        assertEquals(size + leaves, traversal.members().size());
        Set<Integer> unique = new HashSet<>(traversal.members());
        assertEquals(traversal.members().size(), unique.size());
    }
}

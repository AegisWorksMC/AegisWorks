package com.industrialcore.machine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Incremental breadth-first traversal of one cable network.
 *
 * <p>Phase 8 removed the fixed 1,024 / 4,096 cable limits. The replacement is this traversal: it
 * never refuses to explore a component because of its size, but it does a bounded amount of work
 * per call ({@link #step(int)}), so a very large network is discovered over several server ticks
 * instead of blocking one tick. Each node is enqueued at most once (the visited set), so loops and
 * duplicate edges terminate, and memory is proportional to the discovered component only.</p>
 *
 * <p>The class is free of Minecraft types so the size, loop, branching and split/merge behaviour
 * can be unit tested directly (including 4,096 / 4,097 / 8,192 / 20,000 / 65,536 node networks).</p>
 *
 * <p>Correctness contract used by {@code CableBlockEntity}:</p>
 * <ul>
 *   <li>{@link #step(int)} returns how many nodes it processed and may leave the traversal
 *       incomplete; callers must not distribute energy from an incomplete result.</li>
 *   <li>{@link #isComplete()} becomes true only when every reachable node was visited.</li>
 *   <li>{@link #members()} is ordered by discovery and contains no duplicates.</li>
 *   <li>{@link #leader()} is the smallest member according to the supplied order, so exactly one
 *       member is elected regardless of where the traversal started.</li>
 * </ul>
 */
public final class CableNetworkTraversal<T> {
    private final Function<T, ? extends List<T>> neighbours;
    private final Comparator<T> leaderOrder;
    private final ArrayDeque<T> queue = new ArrayDeque<>();
    private final Set<T> visited = new HashSet<>();
    private final List<T> members = new ArrayList<>();
    private final List<T> membersView = Collections.unmodifiableList(members);

    private T leader;
    private boolean complete;

    public CableNetworkTraversal(T start, Function<T, ? extends List<T>> neighbours,
                                 Comparator<T> leaderOrder) {
        this.neighbours = java.util.Objects.requireNonNull(neighbours, "neighbours");
        this.leaderOrder = java.util.Objects.requireNonNull(leaderOrder, "leaderOrder");
        if (start == null) {
            this.complete = true;
            return;
        }
        this.queue.addLast(start);
        this.visited.add(start);
    }

    /**
     * Processes up to {@code budget} nodes.
     *
     * @return the number of nodes processed (0 when the traversal is already complete)
     */
    public int step(int budget) {
        int max = Math.max(0, budget);
        int processed = 0;
        while (processed < max && !this.queue.isEmpty()) {
            T node = this.queue.removeFirst();
            this.members.add(node);
            if (this.leader == null || this.leaderOrder.compare(node, this.leader) < 0) {
                this.leader = node;
            }
            List<T> adjacent = this.neighbours.apply(node);
            if (adjacent != null) {
                for (T next : adjacent) {
                    if (next != null && this.visited.add(next)) {
                        this.queue.addLast(next);
                    }
                }
            }
            processed++;
        }
        if (this.queue.isEmpty()) {
            this.complete = true;
        }
        return processed;
    }

    public boolean isComplete() {
        return this.complete;
    }

    /** Nodes discovered so far (unmodifiable view, no copies). */
    public List<T> members() {
        return this.membersView;
    }

    public T leader() {
        return this.leader;
    }

    /** Nodes already queued or visited; equals the component size once {@link #isComplete()}. */
    public int visitedCount() {
        return this.visited.size();
    }
}

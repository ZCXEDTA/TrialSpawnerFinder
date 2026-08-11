package cn.trialfinder.sim.util;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

/**
 * Port of net.minecraft.util.SequencedPriorityIterator (1.21.11).
 * Elements are processed in descending priority order; within one priority, FIFO.
 */
public final class SequencedPriorityIterator<T> {
    private final TreeMap<Integer, Deque<T>> queuesByPriority = new TreeMap<>(Comparator.reverseOrder());

    public void add(T item, int priority) {
        this.queuesByPriority.computeIfAbsent(priority, ignored -> new ArrayDeque<>()).addLast(item);
    }

    public boolean hasNext() {
        for (Deque<T> queue : this.queuesByPriority.values()) {
            if (!queue.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public T next() {
        for (Map.Entry<Integer, Deque<T>> entry : this.queuesByPriority.entrySet()) {
            Deque<T> queue = entry.getValue();
            if (!queue.isEmpty()) {
                return queue.removeFirst();
            }
        }
        throw new NoSuchElementException();
    }
}

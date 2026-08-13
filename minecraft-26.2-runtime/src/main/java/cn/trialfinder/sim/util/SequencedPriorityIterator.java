package cn.trialfinder.sim.util;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

/**
 * 复刻 {@code net.minecraft.util.SequencedPriorityIterator}（26.2 语义）。
 * 按优先级降序处理；同一优先级内 FIFO。它决定 Jigsaw 拼接的 BFS 顺序，进而决定 RNG 消费顺序。
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

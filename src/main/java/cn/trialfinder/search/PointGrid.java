package cn.trialfinder.search;

import java.util.Arrays;

/**
 * Int-array open-addressing hash grid used by {@link CircleClusters} for neighbour lookups.
 * Replaces {@code Map<Long,List<Integer>>} with flat arrays to eliminate boxing, per-cell
 * {@code ArrayList} allocation and iteration overhead. Each cell maps to a singly-linked list of
 * point indices (via {@code next[]}); insertion is append-only, so no deletion is supported.
 *
 * <p>Thread-safety: not thread-safe by itself; {@link CircleClusters#find} builds a fresh grid per
 * call and each call runs on a single thread.
 */
final class PointGrid {
    private final long[] keys;
    private final int[] heads;
    private final int[] tails;
    private final int[] next;
    private final int mask;

    PointGrid(int pointCount) {
        int capacity = 2;
        long target = Math.max(2L, pointCount * 2L);
        while (capacity < target) {
            capacity = Math.multiplyExact(capacity, 2);
        }
        keys = new long[capacity];
        heads = new int[capacity];
        tails = new int[capacity];
        next = new int[pointCount];
        Arrays.fill(heads, -1);
        Arrays.fill(next, -1);
        mask = capacity - 1;
    }

    void add(int cellX, int cellZ, int pointIndex) {
        long key = key(cellX, cellZ);
        int slot = slot(key);
        if (heads[slot] < 0) {
            keys[slot] = key;
            heads[slot] = pointIndex;
            tails[slot] = pointIndex;
            return;
        }
        next[tails[slot]] = pointIndex;
        tails[slot] = pointIndex;
    }

    /** Returns the head point index of the cell's list, or -1 when the cell is empty. */
    int first(int cellX, int cellZ) {
        long key = key(cellX, cellZ);
        int slot = mix(key) & mask;
        while (heads[slot] >= 0) {
            if (keys[slot] == key) {
                return heads[slot];
            }
            slot = (slot + 1) & mask;
        }
        return -1;
    }

    /** Returns the next point index in the cell's list, or -1 at the tail. */
    int next(int pointIndex) {
        return next[pointIndex];
    }

    private int slot(long key) {
        int slot = mix(key) & mask;
        while (heads[slot] >= 0 && keys[slot] != key) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private static int mix(long value) {
        value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
        value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return (int) (value ^ (value >>> 32));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffff_ffffL);
    }
}

package com.github.kd_gaming1.skyblockenhancements.compat.rrv.search;

import java.util.BitSet;

/**
 * Thread-local pool of reusable {@link BitSet}s to eliminate per-search allocation.
 *
 * <p>Each search borrows BitSets, mutates them in-place, and returns them. If the pool
 * is exhausted (should never happen in normal use), it falls back to {@code new BitSet()}.
 *
 * <p>The pool size of 4 covers the maximum number of concurrent BitSets needed by
 * {@link SkyblockSearchIndex#search}: keywordMatches, statMatches, candidates, plus one
 * spare for ranking operations.
 */
final class BitSetPool {

    private static final int POOL_SIZE = 4;

    private static final ThreadLocal<BitSet[]> POOL = ThreadLocal.withInitial(() -> {
        BitSet[] arr = new BitSet[POOL_SIZE];
        for (int i = 0; i < POOL_SIZE; i++) {
            arr[i] = new BitSet();
        }
        return arr;
    });

    private static final ThreadLocal<boolean[]> IN_USE = ThreadLocal.withInitial(() ->
            new boolean[POOL_SIZE]);

    private BitSetPool() {}

    /**
     * Borrows a clean BitSet from the pool. Must be paired with {@link #release}.
     *
     * @param capacityHint expected highest bit index; the BitSet auto-grows anyway
     */
    static BitSet borrow(int capacityHint) {
        boolean[] used = IN_USE.get();
        BitSet[] pool = POOL.get();
        for (int i = 0; i < POOL_SIZE; i++) {
            if (!used[i]) {
                used[i] = true;
                BitSet bs = pool[i];
                bs.clear();
                if (capacityHint > 0) {
                    bs.set(0, capacityHint);
                    bs.clear();
                }
                return bs;
            }
        }
        // Pool exhausted — defensive fallback
        return new BitSet();
    }

    /** Returns a BitSet to the pool. Safe to call with BitSets not from the pool. */
    static void release(BitSet bs) {
        if (bs == null) return;
        BitSet[] pool = POOL.get();
        for (int i = 0; i < POOL_SIZE; i++) {
            if (pool[i] == bs) {
                IN_USE.get()[i] = false;
                return;
            }
        }
        // Not from pool — ignore
    }

    /** Convenience: borrow and pre-fill with {@code set(0, size)}. */
    static BitSet borrowAll(int size) {
        BitSet bs = borrow(size);
        bs.set(0, size);
        return bs;
    }
}

package com.survivalz.core.pool;

import java.util.ArrayDeque;

/**
 * Generic pool to keep hot-path entities off the garbage collector.
 * Pre-allocates objects up to prefill count; grows on demand up to maxSize.
 */
public abstract class ObjectPool<T> {
    private final ArrayDeque<T> items;
    private final int maxSize;

    public ObjectPool(int prefill, int maxSize) {
        this.maxSize = maxSize;
        this.items = new ArrayDeque<>(prefill);
        for (int i = 0; i < prefill; i++) {
            items.addLast(newObject());
        }
    }

    /** Factory method — subclasses provide the concrete type. */
    protected abstract T newObject();

    /** Claim an object from the pool (allocates if empty). */
    public T obtain() {
        return items.isEmpty() ? newObject() : items.pollFirst();
    }

    /** Return an object to the pool. */
    public void free(T obj) {
        if (items.size() < maxSize) {
            items.addLast(obj);
        }
    }

    public int size() { return items.size(); }
    public int maxSize() { return maxSize; }
}

package com.survivalz.core;

import java.util.ArrayDeque;

/** Generic pool to keep hot-path entities off the garbage collector. */
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

    protected abstract T newObject();

    public T obtain() {
        return items.isEmpty() ? newObject() : items.pollFirst();
    }

    public void free(T obj) {
        if (items.size() < maxSize) {
            items.addLast(obj);
        }
    }
}

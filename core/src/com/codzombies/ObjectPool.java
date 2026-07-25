package com.codzombies;

/**
 * Zero-allocation generic object pool for the hot loop.
 * Pre-allocates objects at init; free() returns them to the pool.
 * Never calls new inside update() or render().
 */
public class ObjectPool<T> {

    public interface Factory<T> {
        T create();
    }

    private final T[] pool;
    private final boolean[] active;
    private int size;

    @SuppressWarnings("unchecked")
    public ObjectPool(int maxSize, Factory<T> factory) {
        pool = (T[]) new Object[maxSize];
        active = new boolean[maxSize];
        for (int i = 0; i < maxSize; i++) {
            pool[i] = factory.create();
        }
        size = maxSize;
    }

    /** Claim an inactive object. Returns null if none free. */
    public T obtain() {
        for (int i = 0; i < pool.length; i++) {
            if (!active[i]) {
                active[i] = true;
                return pool[i];
            }
        }
        return null;
    }

    /** Release an object back into the pool. */
    public void free(T obj) {
        for (int i = 0; i < pool.length; i++) {
            if (pool[i] == obj && active[i]) {
                active[i] = false;
                return;
            }
        }
    }

    /** Return an object to the pool by array index (faster). */
    public void freeIndex(int index) {
        if (index >= 0 && index < pool.length) {
            active[index] = false;
        }
    }

    public T get(int index) { return pool[index]; }

    public boolean isActive(int index) { return active[index]; }

    public int capacity() { return pool.length; }

    public int activeCount() {
        int count = 0;
        for (boolean a : active) if (a) count++;
        return count;
    }

    public void forEachActive(ObjectPoolConsumer<T> consumer) {
        for (int i = 0; i < pool.length; i++) {
            if (active[i]) consumer.accept(pool[i], i);
        }
    }

    public interface ObjectPoolConsumer<T> {
        void accept(T obj, int index);
    }
}

import java.util.ArrayDeque;

/**
 * Mutable 2-D vector used for all world-space positions.
 * Reusing this avoids GC pressure from millions of $$dx, dy$$ calculations.
 */
public final class Vector2 {
    public float x;
    public float y;

    public Vector2() {}

    public Vector2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /** Returns squared distance to $$(ox, oy)$$ to skip sqrt when possible. */
    public float dist2(float ox, float oy) {
        float dx = this.x - ox;
        float dy = this.y - oy;
        return dx * dx + dy * dy;
    }
}

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

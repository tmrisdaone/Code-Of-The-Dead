public abstract class ObjectPool<T> {
    private final T[] free;
    private int freeCount;
    protected abstract T create();
    public T obtain();
    public void free(T obj);
}

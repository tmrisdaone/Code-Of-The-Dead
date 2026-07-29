package com.survivalz.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple type-safe event bus using an EnumMap of event types to listener lists.
 * Zero-allocation in the hot path when no events are posted.
 * Singleton access via {@link #INSTANCE}.
 */
public final class EventBus {

    public static final EventBus INSTANCE = new EventBus();

    private final EnumMap<GameEvent.Type, List<Consumer<GameEvent>>> listeners =
            new EnumMap<>(GameEvent.Type.class);

    private EventBus() {} // singleton

    /**
     * Register a listener for a specific event type.
     */
    public void subscribe(GameEvent.Type type, Consumer<GameEvent> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>(2)).add(listener);
    }

    /**
     * Unregister a listener.
     */
    public void unsubscribe(GameEvent.Type type, Consumer<GameEvent> listener) {
        List<Consumer<GameEvent>> list = listeners.get(type);
        if (list != null) {
            list.remove(listener);
            if (list.isEmpty()) listeners.remove(type);
        }
    }

    /**
     * Post an event to all registered listeners.
     */
    public void post(GameEvent event) {
        List<Consumer<GameEvent>> list = listeners.get(event.type);
        if (list != null) {
            // Copy to avoid ConcurrentModification if a listener unsubscribes
            var copy = new ArrayList<>(list);
            for (Consumer<GameEvent> l : copy) {
                l.accept(event);
            }
        }
    }

    /** Remove all listeners. */
    public void clear() {
        listeners.clear();
    }
}

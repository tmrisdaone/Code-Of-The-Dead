package com.survivalz.core;

/**
 * Minimal observer bus. Kept tiny intentionally: the game core exposes
 * events via plain enum dispatch and leaves delivery policy to the host
 * (Android UI thread, game thread, etc.).
 */
public final class EventBus {
    public enum EventType {
        ROUND_STARTED,
        ROUND_ENDED,
        ZOMBIE_KILLED,
        POWERUP_SPAWNED,
        DOOR_OPENED,
        WEAPON_PURCHASED,
        PLAYER_DOWN
    }

    public interface EventListener {
        /**
         * @param type  the event kind
         * @param arg   optional payload (round number, weapon id, etc.)
         */
        void onEvent(EventType type, Object arg);
    }

    public void subscribe(EventType type, EventListener listener) {
        // Implementation intentionally left as a host-side concern; the core
        // only defines the contract so the Android layer can plug in a real
        // dispatcher without modifying core types.
    }

    public void post(EventType type, Object arg) {
        // No-op in core; host wires a real dispatcher.
    }
}

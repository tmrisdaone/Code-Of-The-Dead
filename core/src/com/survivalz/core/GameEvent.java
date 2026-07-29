package com.survivalz.core;

/**
 * Event type enumeration and payload container for the EventBus.
 */
public final class GameEvent {

    public enum Type {
        ROUND_STARTED,
        ROUND_ENDED,
        PLAYER_DAMAGED,
        PLAYER_DIED,
        ZOMBIE_KILLED,
        DOOR_OPENED,
        BARRIER_BREACHED,
        BARRIER_REPAIRED,
        WALLBUY_PURCHASED,
        MYSTERY_BOX_USED,
        POWERUP_COLLECTED,
        WEAPON_SWAPPED,
        GAME_OVER,
        GAME_RESET
    }

    public final Type type;
    public final Object data;

    private GameEvent(Type type, Object data) {
        this.type = type;
        this.data = data;
    }

    /** Create a simple event with no payload. */
    public static GameEvent of(Type type) {
        return new GameEvent(type, null);
    }

    /** Create an event with a data payload. */
    public static GameEvent of(Type type, Object data) {
        return new GameEvent(type, data);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData() {
        return (T) data;
    }
}

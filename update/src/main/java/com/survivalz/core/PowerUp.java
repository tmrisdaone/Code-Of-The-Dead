package com.survivalz.core;

/** Dropped by killed zombies; expires after a short time. */
public class PowerUp {
    public enum Type {
        INSTAKILL(30f), DOUBLE_POINTS(30f),
        MAX_AMMO(0f), NUKE(0f), CARPENTER(0f);

        final float duration;
        Type(float d) { this.duration = d; }
        public float getDuration() { return duration; }
    }

    private final Vector2 position = new Vector2();
    private final Type type;
    private float lifetime = 20f; // disappears after 20 s

    public PowerUp(float x, float y, Type type) {
        this.position.set(x, y);
        this.type = type;
    }

    public void update(float deltaTime) {
        lifetime -= deltaTime;
    }

    public boolean isExpired() { return lifetime <= 0f; }

    public void apply(Player player, RoundManager rm) {
        switch (type) {
            case INSTAKILL:
                player.activateBuff(Player.Buff.INSTAKILL, type.duration);
                break;
            case DOUBLE_POINTS:
                player.activateBuff(Player.Buff.DOUBLE_POINTS, type.duration);
                break;
            case MAX_AMMO:
                player.maxAmmoAllWeapons();
                break;
            case NUKE:
                rm.killAllActiveZombies();
                player.addPoints(400);
                break;
            case CARPENTER:
                // Repair all barricades in active zones
                break;
        }
    }

    public Vector2 getPosition() { return position; }
    public Type getType() { return type; }
}

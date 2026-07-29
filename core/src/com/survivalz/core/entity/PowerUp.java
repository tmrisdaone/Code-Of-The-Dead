package com.survivalz.core.entity;

import com.survivalz.core.math.Vec2;

/**
 * Dropped by killed zombies; expires after a short time.
 * Player picks these up for temporary buffs or instant effects.
 */
public class PowerUp {
    public enum Type {
        INSTAKILL(30f),
        DOUBLE_POINTS(30f),
        MAX_AMMO(0f),
        NUKE(0f),
        CARPENTER(0f);

        final float duration;
        Type(float d) { this.duration = d; }
    }

    private final Vec2 position = new Vec2();
    private final Type type;
    private float lifetime = 20f; // disappears after 20 s

    public PowerUp(float x, float y, Type type) {
        this.position.set(x, y);
        this.type = type;
    }

    public void update(float deltaTime) {
        lifetime -= deltaTime;
    }

    public boolean isExpired() {
        return lifetime <= 0f;
    }

    public void apply(Player player) {
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
                // GameWorld handles killing all zombies + points
                break;
            case CARPENTER:
                // GameWorld handles repairing all barricades
                break;
        }
    }

    public Vec2 getPosition() { return position; }
    public Type getType() { return type; }
}

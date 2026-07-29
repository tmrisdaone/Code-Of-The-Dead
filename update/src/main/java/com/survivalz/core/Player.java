package com.survivalz.core;

import java.util.HashMap;
import java.util.ArrayList;

public class Player {
    final Vector2 position = new Vector2();
    private float aimAngle; // radians, 0 points along positive x axis

    private int health = 100;
    private static final int MAX_HEALTH = 100;
    private int points = 500;

    // Movement speed in m/s
    private static final float MOVE_SPEED = 5.0f;

    // Inventory
    private final HashMap<String, Weapon> arsenal = new HashMap<>();
    private final ArrayList<String> weaponSlots = new ArrayList<>();
    private int currentSlot = 0;

    // Combat
    private float fireTimer = 0f;

    // Status buffs (InstaKill, DoublePoints, etc.)
    private final float[] buffTimers = new float[Buff.values().length];

    public enum Buff { INSTAKILL, DOUBLE_POINTS, CARPENTER }

    // Collision / interaction constants
    public static final float RADIUS = 0.4f;
    public static final float INTERACT_RADIUS = 1.2f;

    /**
     * @param moveX normalized joystick x in [-1, 1]
     * @param moveY normalized joystick y in [-1, 1]
     * @param aimX  aim vector x
     * @param aimY  aim vector y
     */
    public void update(float deltaTime,
                       float moveX, float moveY,
                       float aimX, float aimY,
                       boolean firing) {
        position.x += moveX * MOVE_SPEED * deltaTime;
        position.y += moveY * MOVE_SPEED * deltaTime;

        if (aimX != 0f || aimY != 0f) {
            aimAngle = (float) Math.atan2(aimY, aimX);
        }

        for (int b = 0; b < buffTimers.length; b++) {
            if (buffTimers[b] > 0f) buffTimers[b] -= deltaTime;
        }

        if (fireTimer > 0f) fireTimer -= deltaTime;

        if (firing && fireTimer <= 0f) {
            Weapon w = getCurrentWeapon();
            if (w != null && w.consumeAmmo(1)) {
                fireTimer = w.getFireInterval();
                // In a full engine, fire a pooled Ray/Bullet here.
            }
        }
    }

    public void applyDamage(int dmg) {
        health -= dmg;
        if (health < 0) health = 0;
    }

    public boolean spendPoints(int cost) {
        if (points >= cost) {
            points -= cost;
            return true;
        }
        return false;
    }

    public void addPoints(int amount) {
        if (hasBuff(Buff.DOUBLE_POINTS)) amount *= 2;
        points += amount;
    }

    public void activateBuff(Buff buff, float duration) {
        buffTimers[buff.ordinal()] = duration;
    }

    public boolean hasBuff(Buff buff) {
        return buffTimers[buff.ordinal()] > 0f;
    }

    // --- Inventory helpers ---

    public boolean hasWeapon(String id) { return arsenal.containsKey(id); }

    public void addWeapon(String id) {
        if (!hasWeapon(id)) {
            // Factory lookup omitted for brevity; supply real stats here.
            arsenal.put(id, new Weapon(id, 0.15f, 30, 300));
            weaponSlots.add(id);
            currentSlot = weaponSlots.size() - 1;
        }
    }

    public void refillAmmo(String id) {
        Weapon w = arsenal.get(id);
        if (w != null) w.refill();
    }

    public void maxAmmoAllWeapons() {
        for (Weapon w : arsenal.values()) w.refill();
    }

    public Weapon getCurrentWeapon() {
        if (weaponSlots.isEmpty()) return null;
        return arsenal.get(weaponSlots.get(currentSlot));
    }

    public Vector2 getPosition() { return position; }
    public float getAimAngle() { return aimAngle; }
    public int getHealth() { return health; }
    public int getPoints() { return points; }
}

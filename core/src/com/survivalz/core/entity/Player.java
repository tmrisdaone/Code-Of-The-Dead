package com.survivalz.core.entity;

import com.survivalz.core.math.Vec2;
import com.survivalz.core.config.BalanceConfig;
import com.survivalz.core.weapon.Weapon;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Player entity — holds position, health, points, inventory, and buff state.
 * Movement and input handling are in PlayerController.
 */
public class Player {
    private final Vec2 position = new Vec2();
    private float aimDirX = 1f; // unit vector x for aim direction
    private float aimDirY = 0f; // unit vector y for aim direction

    private int health;
    private int maxHealth;
    private int points = 500;

    // Stats
    private int zombieKills = 0;
    private int headshots = 0;

    // Inventory
    private final HashMap<String, Weapon> arsenal = new HashMap<>();
    private final ArrayList<String> weaponSlots = new ArrayList<>();
    private int currentSlot = 0;

    // Status buffs (InstaKill, DoublePoints, etc.)
    private final float[] buffTimers = new float[Buff.values().length];

    public enum Buff { INSTAKILL, DOUBLE_POINTS, CARPENTER }

    public Player() {
        this.health = (int) BalanceConfig.PLAYER_HEALTH_MAX;
        this.maxHealth = (int) BalanceConfig.PLAYER_HEALTH_MAX;
        position.set(6f * BalanceConfig.TILE_SIZE, 6f * BalanceConfig.TILE_SIZE);
    }

    public void update(float deltaTime,
                       float moveX, float moveY,
                       boolean firing) {
        position.x += moveX * BalanceConfig.PLAYER_SPEED * deltaTime;
        position.y += moveY * BalanceConfig.PLAYER_SPEED * deltaTime;

        // Count down buff timers
        for (int b = 0; b < buffTimers.length; b++) {
            if (buffTimers[b] > 0f) buffTimers[b] -= deltaTime;
        }
    }

    /** Set the aim direction unit vector (from camera yaw). */
    public void setAimDirection(float dirX, float dirY) {
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len > 0f) {
            this.aimDirX = dirX / len;
            this.aimDirY = dirY / len;
        }
    }

    // ── Health ───────────────────────────────────────────────

    public void applyDamage(int dmg) {
        health -= dmg;
        if (health < 0) health = 0;
    }

    public boolean isAlive() {
        return health > 0;
    }

    public void heal(int amount) {
        health = Math.min(maxHealth, health + amount);
    }

    // ── Points ───────────────────────────────────────────────

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

    // ── Buffs ────────────────────────────────────────────────

    public void activateBuff(Buff buff, float duration) {
        buffTimers[buff.ordinal()] = duration;
    }

    public boolean hasBuff(Buff buff) {
        return buffTimers[buff.ordinal()] > 0f;
    }

    // ── Inventory ────────────────────────────────────────────

    public boolean hasWeapon(String id) {
        return arsenal.containsKey(id);
    }

    public void addWeapon(String id) {
        if (!hasWeapon(id)) {
            arsenal.put(id, Weapon.createFromCatalog(id));
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

    public void switchWeapon() {
        if (weaponSlots.size() <= 1) return;
        currentSlot = (currentSlot + 1) % weaponSlots.size();
    }

    // ── Reset ────────────────────────────────────────────────

    public void reset() {
        health = (int) BalanceConfig.PLAYER_HEALTH_MAX;
        maxHealth = (int) BalanceConfig.PLAYER_HEALTH_MAX;
        points = 0;
        zombieKills = 0;
        headshots = 0;
        position.set(6f * BalanceConfig.TILE_SIZE, 6f * BalanceConfig.TILE_SIZE);
        aimDirX = 1f;
        aimDirY = 0f;
        arsenal.clear();
        weaponSlots.clear();
        currentSlot = 0;
        for (int i = 0; i < buffTimers.length; i++) buffTimers[i] = 0f;
    }

    // ── Accessors ────────────────────────────────────────────

    public Vec2 getPosition() { return position; }
    public float getAimAngle() { return (float) Math.atan2(aimDirY, aimDirX); }
    public float getAimDirX() { return aimDirX; }
    public float getAimDirY() { return aimDirY; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public int getPoints() { return points; }
    public int getZombieKills() { return zombieKills; }
    public int getHeadshots() { return headshots; }
    public void incrementKills(boolean headshot) {
        zombieKills++;
        if (headshot) headshots++;
    }
}

package com.survivalz.core;

public class Weapon {
    private final String id;
    private final float fireInterval; // seconds between shots
    private final int damage;        // damage per hit
    private int ammo;
    private final int maxAmmo;

    public Weapon(String id, float fireInterval, int startAmmo, int maxAmmo, int damage) {
        this.id = id;
        this.fireInterval = fireInterval;
        this.damage = damage;
        this.ammo = startAmmo;
        this.maxAmmo = maxAmmo;
    }

    public boolean consumeAmmo(int amount) {
        if (ammo >= amount) {
            ammo -= amount;
            return true;
        }
        return false;
    }

    public void refill() { ammo = maxAmmo; }
    public String getId() { return id; }
    public int getAmmo() { return ammo; }
    public int getMaxAmmo() { return maxAmmo; }
    public float getFireInterval() { return fireInterval; }
    public int getDamage() { return damage; }
}

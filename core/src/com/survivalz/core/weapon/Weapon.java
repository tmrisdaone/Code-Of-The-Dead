package com.survivalz.core.weapon;

/**
 * A weapon instance owned by the player — holds runtime ammo state.
 * The static stats come from a WeaponDef reference.
 */
public class Weapon {
    private final WeaponDef def;
    private int currentMag;
    private int currentReserve;

    public Weapon(WeaponDef def) {
        this.def = def;
        this.currentMag = def.magCapacity;
        this.currentReserve = def.reserveCapacity;
    }

    /** Create a weapon instance from the catalog by weapon name. */
    public static Weapon createFromCatalog(String weaponId) {
        return new Weapon(WeaponDef.forName(weaponId));
    }

    public boolean consumeAmmo(int amount) {
        if (currentMag >= amount) {
            currentMag -= amount;
            return true;
        }
        return false;
    }

    public void refill() {
        currentMag = def.magCapacity;
        currentReserve = def.reserveCapacity;
    }

    public boolean canReload() {
        return currentMag < def.magCapacity && currentReserve > 0;
    }

    public void performReload() {
        int needed = def.magCapacity - currentMag;
        int available = Math.min(needed, currentReserve);
        currentMag += available;
        currentReserve -= available;
    }

    // ── Accessors ────────────────────────────────────────────

    public String getName() { return def.name; }
    public float getDamage() { return def.damage; }
    public float getFireInterval() { return 1f / def.fireRate; }
    public float getFireRate() { return def.fireRate; }
    public int getMagCapacity() { return def.magCapacity; }
    public int getReserveCapacity() { return def.reserveCapacity; }
    public int getCurrentMag() { return currentMag; }
    public int getCurrentReserve() { return currentReserve; }
    public float getReloadTime() { return def.reloadTime; }
    public float getRecoilAmount() { return def.recoilAmount; }
    public boolean isAutomatic() { return def.isAutomatic; }
    public boolean isRayGun() { return def.isRayGun; }
    public float getSplashRadius() { return def.splashRadius; }
    public int getWallBuyCost() { return def.wallBuyCost; }
    public String getWallBuyLabel() { return def.wallBuyLabel; }
    public WeaponDef getDef() { return def; }
}

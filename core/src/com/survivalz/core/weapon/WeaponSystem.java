package com.survivalz.core.weapon;

/**
 * Manages weapon state: firing, reloading, swapping between inventory slots.
 * Interfaces with the game world for hit detection.
 */
public class WeaponSystem {

    private final Weapon[] inventory;
    private int activeSlot = 0;

    // Timers / state
    private float fireTimer = 0f;
    private float reloadTimer = 0f;
    private boolean isReloading = false;
    private float swapTimer = 0f;
    private boolean isSwapping = false;

    public WeaponSystem() {
        inventory = new Weapon[2];
        inventory[0] = new Weapon(WeaponDef.M1911);
        inventory[1] = null;
    }

    public WeaponSystem(Weapon primary, Weapon secondary) {
        inventory = new Weapon[2];
        inventory[0] = primary;
        inventory[1] = secondary;
    }

    /** Called every frame — returns true if a shot was fired this frame. */
    public boolean update(float deltaTime, boolean firePressed, boolean reloadPressed) {
        Weapon wep = getActiveWeapon();
        if (wep == null) return false;

        // Advance timers
        if (fireTimer > 0f) fireTimer -= deltaTime;

        // Reload
        if (isReloading) {
            reloadTimer -= deltaTime;
            if (reloadTimer <= 0f) finishReload(wep);
            return false;
        }

        // Swap
        if (isSwapping) {
            swapTimer -= deltaTime;
            if (swapTimer <= 0f) isSwapping = false;
            return false;
        }

        // Reload trigger
        if (reloadPressed && !isReloading) {
            if (wep.canReload()) {
                startReload(wep);
                return false;
            }
        }

        // Auto-reload on empty
        if (wep.getCurrentMag() <= 0 && wep.getCurrentReserve() > 0 && !isReloading && !isSwapping) {
            startReload(wep);
            return false;
        }

        // Fire
        if (firePressed && fireTimer <= 0f && !isReloading && !isSwapping) {
            if (wep.getCurrentMag() <= 0) {
                if (wep.getCurrentReserve() > 0) startReload(wep);
                return false;
            }
            return fire(wep);
        }

        return false;
    }

    /** Switch to next weapon slot. */
    public boolean switchWeapon() {
        if (isSwapping) return false;
        int nextSlot = (activeSlot + 1) % inventory.length;
        if (inventory[nextSlot] == null) return false;
        if (getActiveWeapon() != null) {
            activeSlot = nextSlot;
            isSwapping = true;
            swapTimer = 0.5f;
            isReloading = false;
            fireTimer = 0f;
            return true;
        }
        return false;
    }

    public boolean buyWallWeapon(WeaponDef template) {
        // Returns false; caller handles points check
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] == null) {
                inventory[i] = new Weapon(template);
                activeSlot = i;
                return true;
            }
        }
        // All slots full — replace active slot
        inventory[activeSlot] = new Weapon(template);
        return true;
    }

    // ── Private ──────────────────────────────────────────────

    private boolean fire(Weapon wep) {
        fireTimer = wep.getFireInterval();
        wep.consumeAmmo(1);
        return true;
    }

    private void startReload(Weapon wep) {
        isReloading = true;
        reloadTimer = wep.getReloadTime();
    }

    private void finishReload(Weapon wep) {
        if (!isReloading) return;
        isReloading = false;
        wep.performReload();
    }

    // ── Accessors ────────────────────────────────────────────

    public Weapon getActiveWeapon() {
        return inventory[activeSlot];
    }

    public Weapon getWeaponInSlot(int slot) {
        if (slot < 0 || slot >= inventory.length) return null;
        return inventory[slot];
    }

    public int getActiveSlot() { return activeSlot; }
    public boolean isReloading() { return isReloading; }
    public boolean isSwapping() { return isSwapping; }

    public void reset() {
        activeSlot = 0;
        fireTimer = 0f;
        reloadTimer = 0f;
        isReloading = false;
        swapTimer = 0f;
        isSwapping = false;
        inventory[0] = new Weapon(WeaponDef.M1911);
        inventory[1] = null;
    }
}

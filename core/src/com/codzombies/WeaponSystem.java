package com.codzombies;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Manages weapon state: firing, reloading, ADS interpolation, swapping.
 * Interfaces with PlayerController for recoil and with ZombieManager for hit detection.
 *
 * Zero object allocations in update().
 * Uses pre-allocated temporaries for all math.
 */
public class WeaponSystem {

    private final PlayerController player;

    // ── Current weapon ───────────────────────────────────────
    public Weapon[]   inventory;         // slot 0 and 1
    public int        activeSlot = 0;

    // ── Timers / state ───────────────────────────────────────
    private float fireTimer      = 0f;
    private float reloadTimer    = 0f;
    private boolean isReloading  = false;
    private float swapTimer      = 0f;
    private boolean isSwapping   = false;
    public  boolean weaponReady  = true;
    public  boolean isReloading  = false; // public for HUD read

    // ── ADS ──────────────────────────────────────────────────
    public float adsLerp    = 0f;    // 0=hip, 1=ads
    public boolean isADS    = false;

    // ── Muzzle flash ─────────────────────────────────────────
    public boolean muzzleFlash = false;
    private float muzzleTimer  = 0f;

    // ── Temp vectors (no alloc) ──────────────────────────────
    private final Vector3 tmpDir  = new Vector3();
    private final Vector3 tmpPos  = new Vector3();

    public WeaponSystem(PlayerController player) {
        this.player = player;
        inventory = new Weapon[Constants.MAX_WEAPON_SLOTS];
        inventory[0] = new Weapon(
                Weapon.M1911.name, Weapon.M1911.damage,
                Weapon.M1911.fireRate, Weapon.M1911.magCapacity,
                Weapon.M1911.reserveCapacity, Weapon.M1911.reloadTime,
                Weapon.M1911.recoilAmount, Weapon.M1911.isAutomatic,
                Weapon.M1911.isRayGun, Weapon.M1911.splashRadius,
                Weapon.M1911.wallBuyCost, Weapon.M1911.wallBuyLabel
        );
        inventory[0].refillAmmo();
        // Slot 1 empty until wall buy / mystery box
        inventory[1] = null;
    }

    // ── Public API ───────────────────────────────────────────

    /** Called every frame. Returns true if a shot was fired this frame. */
    public ShotResult update(float dt, ZombieManager zombieManager) {
        if (dt > Constants.DELTA_MAX) dt = Constants.DELTA_MAX;

        Weapon wep = getActiveWeapon();
        if (wep == null) return ShotResult.NONE;

        // Timers
        if (fireTimer > 0f)   fireTimer   -= dt;
        if (muzzleTimer > 0f) {
            muzzleTimer -= dt;
            if (muzzleTimer <= 0f) muzzleFlash = false;
        }

        // Reload
        if (isReloading) {
            reloadTimer -= dt;
            if (reloadTimer <= 0f) finishReload(wep);
            return ShotResult.NONE;
        }

        // Swap
        if (isSwapping) {
            swapTimer -= dt;
            if (swapTimer <= 0f) isSwapping = false;
            return ShotResult.NONE;
        }

        // Reload trigger
        if (player.reloadPressed && !isReloading && !isSwapping) {
            if (wep.currentMag < wep.magCapacity && wep.currentReserve > 0) {
                startReload(wep);
                player.reloadPressed = false;
                return ShotResult.NONE;
            }
        }

        // Auto-reload on empty
        if (wep.currentMag <= 0 && wep.currentReserve > 0 && !isReloading) {
            startReload(wep);
            return ShotResult.NONE;
        }

        // Fire
        if (player.firePressed && fireTimer <= 0f && !isReloading && !isSwapping) {
            if (wep.currentMag <= 0) {
                // Empty — trigger reload automatically
                if (wep.currentReserve > 0) startReload(wep);
                return ShotResult.NONE;
            }
            return fire(wep, zombieManager);
        }

        return ShotResult.NONE;
    }

    /** Switch to next weapon slot. */
    public void switchWeapon() {
        if (isSwapping) return;
        int nextSlot = (activeSlot + 1) % Constants.MAX_WEAPON_SLOTS;
        if (inventory[nextSlot] == null) {
            // Skip empty slot, try next
            nextSlot = (nextSlot + 1) % Constants.MAX_WEAPON_SLOTS;
            if (inventory[nextSlot] == null) return;
        }
        Weapon current = getActiveWeapon();
        if (current != null) {
            activeSlot = nextSlot;
            isSwapping = true;
            swapTimer  = 0.5f;
            isReloading = false;
            fireTimer   = 0f;
        }
    }

    public Weapon getActiveWeapon() {
        return inventory[activeSlot];
    }

    public Weapon getWeaponInSlot(int slot) {
        if (slot < 0 || slot >= inventory.length) return null;
        return inventory[slot];
    }

    /** Buy a wall weapon from a specific node. Returns true if bought. */
    public boolean buyWallWeapon(int wallBuyIndex) {
        if (wallBuyIndex < 0 || wallBuyIndex >= Weapon.WALL_BUY_WEAPONS.length) return false;
        Weapon template = Weapon.WALL_BUY_WEAPONS[wallBuyIndex];
        if (player.points < template.wallBuyCost) return false;

        player.addPoints(-template.wallBuyCost);

        // Replace an empty slot or the active slot
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] == null) {
                inventory[i] = new Weapon(
                        template.name, template.damage,
                        template.fireRate, template.magCapacity,
                        template.reserveCapacity, template.reloadTime,
                        template.recoilAmount, template.isAutomatic,
                        template.isRayGun, template.splashRadius,
                        template.wallBuyCost, template.wallBuyLabel
                );
                inventory[i].refillAmmo();
                activeSlot = i;
                return true;
            }
        }
        // All slots full: replace active
        int slot = activeSlot;
        inventory[slot] = new Weapon(
                template.name, template.damage,
                template.fireRate, template.magCapacity,
                template.reserveCapacity, template.reloadTime,
                template.recoilAmount, template.isAutomatic,
                template.isRayGun, template.splashRadius,
                template.wallBuyCost, template.wallBuyLabel
        );
        inventory[slot].refillAmmo();
        return true;
    }

    // ── Private ──────────────────────────────────────────────

    private ShotResult fire(Weapon wep, ZombieManager zombieManager) {
        // Raycast from camera center
        Vector3 dir = player.camera.direction;
        Vector3 pos = player.camera.position;

        // Muzzle flash
        muzzleFlash = true;
        muzzleTimer = 0.05f;

        // Fire rate cooldown
        fireTimer = 1f / wep.fireRate;

        // Consume ammo
        wep.currentMag--;

        // Apply recoil
        float recoil = wep.recoilAmount;
        // Hip-fire spreads recoil more
        if (!isADS) recoil *= 1.5f;
        player.applyRecoil(recoil);

        // Ray-gun projectile
        if (wep.isRayGun) {
            return zombieManager.spawnBullet(
                    pos.x, pos.y, pos.z,
                    dir.x, dir.y, dir.z,
                    40f,                    // speed
                    wep.damage,
                    wep.splashRadius,
                    3f                      // lifetime
            ) ? ShotResult.FIRED_RAYGUN : ShotResult.FIRED;
        }

        // Standard bullet: raycast hit-scan from camera
        // Use zombieManager to get hit direction
        ZombieManager.HitResult hit = zombieManager.raycastHit(
                pos, dir, 50f, 0.5f        // range, headshot-height
        );

        if (hit != null) {
            player.addPoints(hit.headshot
                    ? Constants.POINTS_PER_HEADSHOT
                    : Constants.POINTS_PER_KILL);
            if (hit.headshot) player.headshots++;
            player.zombieKills++;
        } else {
            player.addPoints(Constants.POINTS_PER_HIT);
        }

        return ShotResult.FIRED;
    }

    private void startReload(Weapon wep) {
        isReloading = true;
        reloadTimer = wep.reloadTime;
    }

    private void finishReload(Weapon wep) {
        if (!isReloading) return;
        isReloading = false;

        int needed = wep.magCapacity - wep.currentMag;
        int available = Math.min(needed, wep.currentReserve);
        wep.currentMag      += available;
        wep.currentReserve  -= available;
    }

    // ── Shot result enum ─────────────────────────────────────
    public enum ShotResult {
        NONE,
        FIRED,
        FIRED_RAYGUN
    }
}

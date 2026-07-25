package com.codzombies;

/**
 * Full catalog of weapons available in the game.
 * Each weapon is a static instance — no runtime allocation for weapon configs.
 */
public class Weapon {

    public final String name;
    public final float  damage;
    public final float  fireRate;           // rounds per second
    public final int    magCapacity;
    public final int    reserveCapacity;
    public       int    currentMag;
    public       int    currentReserve;
    public final float  reloadTime;         // seconds
    public final float  recoilAmount;       // vertical kick per shot (degrees)
    public final boolean isAutomatic;
    public final boolean isRayGun;          // projectile weapon
    public final float  splashRadius;       // 0 = raycast hit only
    public final int    wallBuyCost;        // 0 = not wall-buyable
    public final String wallBuyLabel;

    // ── Constructor ──────────────────────────────────────────
    public Weapon(String name, float damage, float fireRate,
                  int magCapacity, int reserveCapacity,
                  float reloadTime, float recoilAmount,
                  boolean isAutomatic, boolean isRayGun,
                  float splashRadius, int wallBuyCost,
                  String wallBuyLabel) {
        this.name            = name;
        this.damage          = damage;
        this.fireRate        = fireRate;
        this.magCapacity     = magCapacity;
        this.reserveCapacity = reserveCapacity;
        this.currentMag      = magCapacity;
        this.currentReserve  = reserveCapacity;
        this.reloadTime      = reloadTime;
        this.recoilAmount    = recoilAmount;
        this.isAutomatic     = isAutomatic;
        this.isRayGun        = isRayGun;
        this.splashRadius    = splashRadius;
        this.wallBuyCost     = wallBuyCost;
        this.wallBuyLabel    = wallBuyLabel;
    }

    /** Deep-copy state from another weapon (for loadout / swap). */
    public void copyStateFrom(Weapon other) {
        this.currentMag      = other.currentMag;
        this.currentReserve  = other.currentReserve;
    }

    public void refillAmmo() {
        currentMag     = magCapacity;
        currentReserve = reserveCapacity;
    }

    // ── Static Weapon Catalog ────────────────────────────────
    // All pre-allocated — no new Weapon() in hot paths.

    public static final Weapon M1911 = new Weapon(
            "M1911",            // name
            25f,                // damage
            5f,                 // fireRate (semi-auto capped)
            8,                  // magCapacity
            48,                 // reserveCapacity
            1.5f,               // reloadTime
            2.5f,               // recoilAmount
            false,              // isAutomatic
            false,              // isRayGun
            0f,                 // splashRadius
            0,                  // wallBuyCost (starter)
            ""                  // wallBuyLabel
    );

    public static final Weapon M14 = new Weapon(
            "M14",
            60f,
            3.5f,
            20,
            60,
            2.2f,
            3.0f,
            false,
            false,
            0f,
            500,
            "M14 - 500 pts"
    );

    public static final Weapon RAY_GUN = new Weapon(
            "Ray Gun",
            100f,               // direct hit
            3f,                 // fireRate
            20,
            40,
            2.5f,
            4.0f,
            false,              // semi-auto (semi-auto feel)
            true,               // isRayGun
            3.0f,               // splash radius in world units
            0,                  // mystery box only
            ""
    );

    public static final Weapon MP40 = new Weapon(
            "MP40",
            30f,
            10f,                // full auto
            32,
            160,
            2.0f,
            2.0f,
            true,
            false,
            0f,
            1000,
            "MP40 - 1000 pts"
    );

    public static final Weapon STG44 = new Weapon(
            "STG-44",
            40f,
            8f,
            30,
            120,
            2.0f,
            2.2f,
            true,
            false,
            0f,
            1200,
            "STG-44 - 1200 pts"
    );

    public static final Weapon THOMPSON = new Weapon(
            "Thompson",
            35f,
            9f,
            30,
            150,
            2.0f,
            2.5f,
            true,
            false,
            0f,
            800,
            "Thompson - 800 pts"
    );

    /** All wall-buyable weapons for quick lookup. */
    public static final Weapon[] WALL_BUY_WEAPONS = { M14, MP40, STG44, THOMPSON };

    /** All weapons (including specials). */
    public static final Weapon[] ALL = { M1911, M14, MP40, STG44, THOMPSON, RAY_GUN };
}

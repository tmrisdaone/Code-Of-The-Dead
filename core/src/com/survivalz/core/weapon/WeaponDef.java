package com.survivalz.core.weapon;

/**
 * Weapon definitions and static catalog.
 * Each weapon has damage, fire rate, magazine, reload, recoil, and wall-buy data.
 */
public class WeaponDef {
    public final String name;
    public final float damage;
    public final float fireRate;           // rounds per second
    public final int magCapacity;
    public final int reserveCapacity;
    public final float reloadTime;         // seconds
    public final float recoilAmount;       // vertical kick per shot (degrees)
    public final boolean isAutomatic;
    public final boolean isRayGun;
    public final float splashRadius;       // 0 = raycast hit only
    public final int wallBuyCost;
    public final String wallBuyLabel;

    public WeaponDef(String name, float damage, float fireRate,
                     int magCapacity, int reserveCapacity,
                     float reloadTime, float recoilAmount,
                     boolean isAutomatic, boolean isRayGun,
                     float splashRadius, int wallBuyCost,
                     String wallBuyLabel) {
        this.name = name;
        this.damage = damage;
        this.fireRate = fireRate;
        this.magCapacity = magCapacity;
        this.reserveCapacity = reserveCapacity;
        this.reloadTime = reloadTime;
        this.recoilAmount = recoilAmount;
        this.isAutomatic = isAutomatic;
        this.isRayGun = isRayGun;
        this.splashRadius = splashRadius;
        this.wallBuyCost = wallBuyCost;
        this.wallBuyLabel = wallBuyLabel;
    }

    // ── Static Catalog ────────────────────────────────────────

    public static final WeaponDef M1911 = new WeaponDef(
            "M1911", 25f, 5f, 8, 48, 1.5f, 2.5f,
            false, false, 0f, 0, "");

    public static final WeaponDef M14 = new WeaponDef(
            "M14", 60f, 3.5f, 20, 60, 2.2f, 3.0f,
            false, false, 0f, 500, "M14 - 500 pts");

    public static final WeaponDef RAY_GUN = new WeaponDef(
            "Ray Gun", 100f, 3f, 20, 40, 2.5f, 4.0f,
            false, true, 3.0f, 0, "");

    public static final WeaponDef MP40 = new WeaponDef(
            "MP40", 30f, 10f, 32, 160, 2.0f, 2.0f,
            true, false, 0f, 1000, "MP40 - 1000 pts");

    public static final WeaponDef STG44 = new WeaponDef(
            "STG-44", 40f, 8f, 30, 120, 2.0f, 2.2f,
            true, false, 0f, 1200, "STG-44 - 1200 pts");

    public static final WeaponDef THOMPSON = new WeaponDef(
            "Thompson", 35f, 9f, 30, 150, 2.0f, 2.5f,
            true, false, 0f, 800, "Thompson - 800 pts");

    public static final WeaponDef[] WALL_BUY_WEAPONS = { M14, MP40, STG44, THOMPSON };
    public static final WeaponDef[] ALL = { M1911, M14, MP40, STG44, THOMPSON, RAY_GUN };

    /** Look up a weapon def by name. */
    public static WeaponDef forName(String name) {
        for (WeaponDef w : ALL) {
            if (w.name.equals(name)) return w;
        }
        return M1911;
    }
}

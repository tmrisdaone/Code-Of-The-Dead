package com.survivalz.core;

/** Purchases a weapon or refills its ammo if already owned. */
public class WallBuy implements Interactable {
    private final float x, y;
    private final String weaponId;
    private final int weaponCost;
    private final int ammoCost;

    public WallBuy(float x, float y, String weaponId, int weaponCost, int ammoCost) {
        this.x = x; this.y = y;
        this.weaponId = weaponId;
        this.weaponCost = weaponCost;
        this.ammoCost = ammoCost;
    }

    @Override
    public boolean canInteract(Player player) {
        return player.getPosition().dist2(x, y) <= Player.INTERACT_RADIUS * Player.INTERACT_RADIUS;
    }

    @Override
    public void onInteract(Player player) {
        boolean owns = player.hasWeapon(weaponId);
        int cost = owns ? ammoCost : weaponCost;
        if (player.spendPoints(cost)) {
            if (owns) player.refillAmmo(weaponId);
            else      player.addWeapon(weaponId);
        }
    }

    @Override
    public String getPrompt(Player player) {
        if (player.hasWeapon(weaponId)) return "Buy Ammo [" + ammoCost + "]";
        return "Buy Weapon [" + weaponCost + "]";
    }

    @Override public float getX() { return x; }
    @Override public float getY() { return y; }
    public String getWeaponId() { return weaponId; }
}

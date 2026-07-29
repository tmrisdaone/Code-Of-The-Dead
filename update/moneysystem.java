public interface Interactable {
    boolean canInteract(Player player);
    void onInteract(Player player);
    String getPrompt(Player player);
    float getX();
    float getY();
}

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
}

/** Opens a locked map zone when the player pays the point cost. */
public class DoorBuy implements Interactable {
    private final float x, y;
    private final int cost;
    private final String zoneId;
    private boolean unlocked = false;

    public DoorBuy(float x, float y, int cost, String zoneId) {
        this.x = x; this.y = y; this.cost = cost; this.zoneId = zoneId;
    }

    @Override
    public boolean canInteract(Player player) {
        if (unlocked) return false;
        return player.getPosition().dist2(x, y) <= Player.INTERACT_RADIUS * Player.INTERACT_RADIUS;
    }

    @Override
    public void onInteract(Player player) {
        if (unlocked) return;
        if (player.spendPoints(cost)) {
            unlocked = true;
            // Publish a DoorOpenedEvent to your nav-mesh / collision layer.
        }
    }

    @Override
    public String getPrompt(Player player) {
        return "Open Door [" + cost + "]";
    }

    @Override public float getX() { return x; }
    @Override public float getY() { return y; }
    public boolean isUnlocked() { return unlocked; }
    public String getZoneId() { return zoneId; }
}

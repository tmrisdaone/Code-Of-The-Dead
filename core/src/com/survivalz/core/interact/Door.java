package com.survivalz.core.interact;

import com.survivalz.core.entity.Player;
import com.survivalz.core.config.BalanceConfig;

/**
 * Opens a locked map zone when the player pays the point cost.
 */
public class DoorBuy implements Interactable {
    private final float x, y;
    private final int cost;
    private final String zoneId;
    private boolean unlocked = false;

    public DoorBuy(float x, float y, int cost, String zoneId) {
        this.x = x;
        this.y = y;
        this.cost = cost;
        this.zoneId = zoneId;
    }

    @Override
    public boolean canInteract(Player player) {
        if (unlocked) return false;
        return player.getPosition().dist2(x, y) <=
                BalanceConfig.INTERACT_RADIUS * BalanceConfig.INTERACT_RADIUS;
    }

    @Override
    public void onInteract(Player player) {
        if (unlocked) return;
        if (player.spendPoints(cost)) {
            unlocked = true;
            // The GameWorld handles updating the nav-mesh / collision layer
        }
    }

    @Override
    public String getPrompt(Player player) {
        return "Open Door [$" + cost + "]";
    }

    @Override public float getX() { return x; }
    @Override public float getY() { return y; }
    public boolean isUnlocked() { return unlocked; }
    public String getZoneId() { return zoneId; }
}

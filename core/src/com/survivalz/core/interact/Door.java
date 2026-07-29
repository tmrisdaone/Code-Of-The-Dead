package com.survivalz.core.interact;

import com.survivalz.core.entity.Player;
import com.survivalz.core.config.BalanceConfig;

/**
 * Opens a locked map zone when the player pays the point cost.
 * tileX/tileY identify the map grid cell that should become passable when unlocked.
 */
public class Door implements Interactable {
    private final float x, y;
    private final int tileX, tileY;
    private final int cost;
    private final String zoneId;
    private boolean unlocked = false;

    /** @param worldX, worldZ  world-unit position for distance checks */
    public Door(float worldX, float worldZ, int tileX, int tileY, int cost, String zoneId) {
        this.x = worldX;
        this.y = worldZ;
        this.tileX = tileX;
        this.tileY = tileY;
        this.cost = cost;
        this.zoneId = zoneId;
    }

    /** Backwards-compatible constructor — derives tile coords from world coords. */
    public Door(float x, float y, int cost, String zoneId) {
        this(x, y,
             (int)(x / BalanceConfig.TILE_SIZE),
             (int)(y / BalanceConfig.TILE_SIZE),
             cost, zoneId);
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
            // The GameWorld handles updating the nav-mesh / collision layer via DOOR_OPENED event
        }
    }

    @Override
    public String getPrompt(Player player) {
        return "Open Door [$" + cost + "]";
    }

    @Override public float getX() { return x; }
    @Override public float getY() { return y; }
    public int getTileX() { return tileX; }
    public int getTileY() { return tileY; }
    public boolean isUnlocked() { return unlocked; }
    public String getZoneId() { return zoneId; }
}

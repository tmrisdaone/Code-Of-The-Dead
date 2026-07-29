package com.survivalz.core.interact;

import com.survivalz.core.entity.Player;
import com.survivalz.core.config.BalanceConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * Mystery Box — spend 950 points for a random weapon after a short roulette.
 */
public class MysteryBox implements Interactable {
    private final float x, y;
    private final List<String> lootTable;
    private boolean inUse = false;
    private float cycleTimer = 0f;

    public MysteryBox(float x, float y, List<String> lootTable) {
        this.x = x;
        this.y = y;
        this.lootTable = new ArrayList<>(lootTable);
    }

    @Override
    public boolean canInteract(Player player) {
        if (inUse) return false;
        return player.getPosition().dist2(x, y) <=
                BalanceConfig.INTERACT_RADIUS * BalanceConfig.INTERACT_RADIUS;
    }

    @Override
    public void onInteract(Player player) {
        if (player.spendPoints(950)) {
            inUse = true;
            cycleTimer = 3.0f; // 3 second roulette
        }
    }

    /** Call from GameWorld.update so the box ticks even when the player stands still. */
    public void update(float deltaTime) {
        if (!inUse) return;
        cycleTimer -= deltaTime;
        if (cycleTimer <= 0f) {
            inUse = false;
            // Emit event: a random weapon from lootTable is now available for pickup
        }
    }

    @Override
    public String getPrompt(Player player) {
        return inUse ? "Rolling..." : "Use Mystery Box [950]";
    }

    @Override public float getX() { return x; }
    @Override public float getY() { return y; }
    public boolean isInUse() { return inUse; }
}

package com.survivalz.core;

import java.util.ArrayList;
import java.util.List;

public class MysteryBox implements Interactable {
    private final float x, y;
    private final List<String> lootTable;
    private boolean inUse = false;
    private float cycleTimer = 0f;
    private String pendingWeapon = null; // weapon awaiting pickup after roulette ends

    public MysteryBox(float x, float y, List<String> lootTable) {
        this.x = x; this.y = y;
        this.lootTable = new ArrayList<>(lootTable);
    }

    @Override
    public boolean canInteract(Player player) {
        if (inUse) return false;
        // If a weapon is waiting to be picked up, allow interaction to claim it.
        if (pendingWeapon != null) {
            return player.getPosition().dist2(x, y) <= Player.INTERACT_RADIUS * Player.INTERACT_RADIUS;
        }
        return player.getPosition().dist2(x, y) <= Player.INTERACT_RADIUS * Player.INTERACT_RADIUS;
    }

    @Override
    public void onInteract(Player player) {
        // Claim pending weapon first (no second charge).
        if (pendingWeapon != null) {
            player.addWeapon(pendingWeapon);
            pendingWeapon = null;
            return;
        }
        if (player.spendPoints(950)) {
            inUse = true;
            cycleTimer = 3.0f; // 3 second roulette
        }
    }

    /** Call this from GameWorld.update so the box ticks even when the player stands still. */
    public void update(float deltaTime) {
        if (!inUse) return;
        cycleTimer -= deltaTime;
        if (cycleTimer <= 0f) {
            inUse = false;
            // Roll a random weapon from the loot table and leave it for pickup.
            if (!lootTable.isEmpty()) {
                int idx = (int) (Math.random() * lootTable.size());
                pendingWeapon = lootTable.get(idx);
            }
        }
    }

    @Override
    public String getPrompt(Player player) {
        if (pendingWeapon != null) return "Take " + pendingWeapon;
        return inUse ? "Rolling..." : "Use Mystery Box [950]";
    }

    @Override public float getX() { return x; }
    @Override public float getY() { return y; }
    public boolean isInUse() { return inUse; }
    public String getPendingWeapon() { return pendingWeapon; }
}

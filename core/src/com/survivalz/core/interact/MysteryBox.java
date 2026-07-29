package com.survivalz.core.interact;

import com.survivalz.core.entity.Player;
import com.survivalz.core.config.BalanceConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Mystery Box — spend 950 points for a random weapon after a short roulette.
 * The callback is invoked when the roulette finishes to give the weapon.
 */
public class MysteryBox implements Interactable {
    private final float x, y;
    private final List<String> lootTable;
    private boolean inUse = false;
    private float cycleTimer = 0f;
    private Consumer<String> onWeaponSelected;

    public MysteryBox(float x, float y, List<String> lootTable) {
        this.x = x;
        this.y = y;
        this.lootTable = new ArrayList<>(lootTable);
    }

    /** Set the callback that fires when the roulette ends with the chosen weapon ID. */
    public void setOnWeaponSelected(Consumer<String> callback) {
        this.onWeaponSelected = callback;
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
            if (onWeaponSelected != null && !lootTable.isEmpty()) {
                String chosen = lootTable.get((int) (Math.random() * lootTable.size()));
                onWeaponSelected.accept(chosen);
            }
        }
    }

    @Override
    public String getPrompt(Player player) {
        return inUse ? "Rolling..." : "Use Mystery Box [950]";
    }

    @Override public float getX() { return x; }
    @Override public float getY() { return y; }
    public boolean isInUse() { return inUse; }
    public List<String> getLootTable() { return lootTable; }
}

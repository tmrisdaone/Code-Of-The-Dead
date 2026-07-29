package com.survivalz.core.entity;

import com.survivalz.core.config.BalanceConfig;

/**
 * Handles player input and movement.
 * Works with a platform-specific input layer that provides normalized axes.
 */
public class PlayerController {
    private final Player player;

    // Movement input (-1..1)
    private float moveX = 0f;
    private float moveY = 0f;
    private float aimX = 0f;
    private float aimY = 0f;
    private boolean firing = false;
    private boolean interactPressed = false;
    private boolean reloadPressed = false;

    // Recoil
    private float recoilAccum = 0f;

    // Health regen state machine
    private static final int REGEN_IDLE = 0;
    private static final int REGEN_DELAY = 1;
    private static final int REGEN_ACTIVE = 2;
    private int regenState = REGEN_IDLE;
    private float lastHitTime = 0f;

    public PlayerController(Player player) {
        this.player = player;
    }

    public void update(float deltaTime) {
        if (!player.isAlive()) return;

        // Movement
        player.setAimDirection(aimX, aimY);
        player.update(deltaTime, moveX, moveY, firing);

        // Recoil recovery (handled in camera layer)
        if (recoilAccum > 0f) {
            float recovery = 60f * deltaTime;
            recoilAccum = Math.max(0f, recoilAccum - recovery);
        }

        // Health regen
        updateHealthRegen(deltaTime);

        // Reset per-frame inputs
        moveX = 0f;
        moveY = 0f;
        aimX = 0f;
        aimY = 0f;
        firing = false;
        interactPressed = false;
        reloadPressed = false;
    }

    public void applyRecoil(float amount) {
        recoilAccum += amount;
    }

    // ── Health Regen ─────────────────────────────────────────

    private void updateHealthRegen(float dt) {
        if (player.getHealth() >= player.getMaxHealth()) {
            regenState = REGEN_IDLE;
            return;
        }

        switch (regenState) {
            case REGEN_IDLE:
                regenState = REGEN_DELAY;
                lastHitTime = 0f;
                break;
            case REGEN_DELAY:
                lastHitTime += dt;
                if (lastHitTime >= BalanceConfig.HEALTH_REGEN_DELAY) {
                    regenState = REGEN_ACTIVE;
                }
                break;
            case REGEN_ACTIVE:
                player.heal((int) (BalanceConfig.HEALTH_REGEN_RATE * dt));
                if (player.getHealth() >= player.getMaxHealth()) {
                    regenState = REGEN_IDLE;
                }
                break;
        }
    }

    // ── Input setters ────────────────────────────────────────

    public void setMove(float x, float y) { this.moveX = x; this.moveY = y; }
    public void setAim(float x, float y) { this.aimX = x; this.aimY = y; }
    public void setFiring(boolean f) { this.firing = f; }
    public void setInteract() { this.interactPressed = true; }
    public void setReload() { this.reloadPressed = true; }

    public float getMoveX() { return moveX; }
    public float getMoveY() { return moveY; }
    public boolean isFiring() { return firing; }
    public boolean isInteractPressed() { return interactPressed; }
    public boolean isReloadPressed() { return reloadPressed; }
    public float getRecoilAccum() { return recoilAccum; }

    public void reset() {
        moveX = 0f;
        moveY = 0f;
        aimX = 0f;
        aimY = 0f;
        firing = false;
        interactPressed = false;
        reloadPressed = false;
        recoilAccum = 0f;
        regenState = REGEN_IDLE;
        lastHitTime = 0f;
    }
}

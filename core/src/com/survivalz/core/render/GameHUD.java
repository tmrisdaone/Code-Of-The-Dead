package com.survivalz.core.render;

import com.survivalz.core.EventBus;
import com.survivalz.core.GameEvent;
import com.survivalz.core.GameWorld;
import com.survivalz.core.entity.Player;
import com.survivalz.core.round.RoundManager;
import com.survivalz.core.config.GameConfig;
import com.survivalz.core.weapon.Weapon;
import com.survivalz.core.weapon.WeaponSystem;

/**
 * Manages all HUD display state — round announcements, damage flash, etc.
 * The actual rendering is done by LibGDXGameRenderer.
 */
public class GameHUD {

    private final GameWorld world;

    // Round announcement state
    private String roundText = "";
    private float roundTimer = 0f;
    private boolean showRound = false;

    // Damage vignette
    private float damageAlpha = 0f;

    // Interact prompt
    private String interactPrompt = "";

    public GameHUD(GameWorld world) {
        this.world = world;

        // Subscribe to events
        EventBus.INSTANCE.subscribe(GameEvent.Type.ROUND_STARTED, event -> {
            int round = event.getData();
            roundText = "ROUND " + round;
            roundTimer = 3.0f;
            showRound = true;
        });
    }

    /** Called every frame to update HUD state. */
    public void update(float deltaTime) {
        if (showRound) {
            roundTimer -= deltaTime;
            if (roundTimer <= 0f) showRound = false;
        }

        if (damageAlpha > 0f) {
            damageAlpha -= deltaTime * 2f;
            if (damageAlpha < 0f) damageAlpha = 0f;
        }

        // Update interact prompt
        var hovered = world.getHoveredInteractable();
        interactPrompt = (hovered != null) ? hovered.getPrompt(world.getPlayer()) : "";
    }

    public void triggerDamageFlash() {
        damageAlpha = 0.6f;
    }

    // ── HUD data accessors for renderer ──────────────────────

    public String getRoundText() { return roundText; }
    public float getRoundTimer() { return roundTimer; }
    public boolean isShowRound() { return showRound; }
    public float getDamageAlpha() { return damageAlpha; }
    public String getInteractPrompt() { return interactPrompt; }
}

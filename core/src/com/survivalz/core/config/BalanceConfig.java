package com.survivalz.core.config;

/**
 * Balance-parameter constants and scaling formulas.
 * All magic numbers live here — zero hardcoded values in update/render loops.
 */
public final class BalanceConfig {

    private BalanceConfig() {}

    // ── Display ──────────────────────────────────────────────
    public static final int    VIEWPORT_WIDTH       = 800;
    public static final int    VIEWPORT_HEIGHT      = 600;
    public static final float  FOV_DEFAULT          = 75f;
    public static final float  FOV_ADS              = 45f;
    public static final float  NEAR_PLANE           = 0.1f;
    public static final float  FAR_PLANE            = 200f;

    // ── Rendering ────────────────────────────────────────────
    public static final float  TARGET_FPS           = 60f;
    public static final float  DELTA_MAX            = 0.05f;

    // ── Player ───────────────────────────────────────────────
    public static final float  PLAYER_HEALTH_MAX    = 100f;
    public static final float  PLAYER_SPEED         = 6f;
    public static final float  PLAYER_HEIGHT        = 0.8f;
    public static final float  HEALTH_REGEN_DELAY   = 5f;
    public static final float  HEALTH_REGEN_RATE    = 10f;
    public static final float  MOUSE_SENSITIVITY    = 0.15f;
    public static final float  TOUCH_SENSITIVITY    = 0.2f;
    public static final float  PITCH_MIN            = -89f;
    public static final float  PITCH_MAX            = 89f;
    public static final float  PLAYER_RADIUS        = 0.4f;
    public static final float  INTERACT_RADIUS      = 1.2f;

    // ── Points ───────────────────────────────────────────────
    public static final int    POINTS_PER_HIT       = 10;
    public static final int    POINTS_PER_KILL      = 60;
    public static final int    POINTS_PER_HEADSHOT   = 100;
    public static final int    POINTS_PER_BOARD     = 10;

    // ── Weapons ──────────────────────────────────────────────
    public static final float  ADS_INTERP_SPEED     = 8f;
    public static final int    MAX_WEAPON_SLOTS     = 2;

    // ── Zombies ──────────────────────────────────────────────
    public static final float  ZOMBIE_BASE_HEALTH   = 150f;
    public static final float  ZOMBIE_HEALTH_SCALE  = 0.10f;
    public static final float  ZOMBIE_SPEED         = 2.5f;
    public static final float  ZOMBIE_ATTACK_RANGE  = 1.5f;
    public static final float  ZOMBIE_ATTACK_COOLDOWN = 1.5f;
    public static final float  ZOMBIE_ATTACK_DAMAGE = 20f;
    public static final int    ZOMBIE_MAX_POOL      = 64;

    // ── Map ──────────────────────────────────────────────────
    public static final int    TILE_SIZE            = 4;
    public static final float  BARRIER_REBUILD_POINTS = 10f;
    public static final int    MAX_BARRIER_BOARDS   = 5;
    public static final int    MAP_SIZE             = 12;

    // ── Round formulas ───────────────────────────────────────

    public static int zombieCountForRound(int round) {
        if (round < 1) round = 1;
        return (int) Math.floor(0.24 * round * round + 12.0 * round + 6.0);
    }

    public static float zombieHealthForRound(int round) {
        if (round <= 9) return ZOMBIE_BASE_HEALTH;
        float factor = 1f + ZOMBIE_HEALTH_SCALE * (round - 9);
        return ZOMBIE_BASE_HEALTH * factor;
    }

    // ── Door costs ───────────────────────────────────────────
    public static final int DOOR_COST_FIRST   = 750;
    public static final int DOOR_COST_SECOND  = 1000;
    public static final int DOOR_COST_THIRD   = 1250;
}

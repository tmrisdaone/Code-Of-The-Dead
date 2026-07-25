package com.codzombies;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * First-person camera controller with dual-stick mobile touch input,
 * mouse/keyboard desktop fallback, recoil calculation, and player stats.
 *
 * No object allocations in update() or render().
 * Uses pre-allocated Vector3 temporaries for all math.
 */
public class PlayerController {

    // ── Vitals ───────────────────────────────────────────────
    public float health        = Constants.PLAYER_HEALTH_MAX;
    public float healthMax     = Constants.PLAYER_HEALTH_MAX;
    public int   points        = 0;
    public int   round         = 0;      // current wave
    public int   zombieKills   = 0;
    public int   headshots     = 0;

    // ── Camera ───────────────────────────────────────────────
    public final PerspectiveCamera camera;
    public final Vector3 position;       // player feet position
    public float yaw   = 0f;
    public float pitch = 0f;
    public float fovTarget = Constants.FOV_DEFAULT;

    // ── Regen ────────────────────────────────────────────────
    private float lastHitTime      = 0f;

    // ── Input State (set by GameHUD touch callbacks) ─────────
    public float moveX      = 0f;     // -1..1 strafe
    public float moveY      = 0f;     // -1..1 forward/back
    public float lookX      = 0f;     // delta from right-side drag
    public float lookY      = 0f;
    public boolean firePressed   = false;
    public boolean adsPressed    = false;
    public boolean reloadPressed = false;

    // ── Recoil ───────────────────────────────────────────────
    private float recoilAccum = 0f;
    private final Vector3 tmp = new Vector3();

    // ── Reference to weapon system (set by CodZombiesGame) ──
    public WeaponSystem weaponSystem;

    // ── Health-regen state machine ───────────────────────────
    private static final int REGEN_IDLE    = 0;
    private static final int REGEN_DELAY   = 1;
    private static final int REGEN_ACTIVE  = 2;
    private int regenState = REGEN_IDLE;

    public PlayerController() {
        camera = new PerspectiveCamera(
                Constants.FOV_DEFAULT,
                Constants.VIEWPORT_WIDTH,
                Constants.VIEWPORT_HEIGHT
        );
        camera.near = Constants.NEAR_PLANE;
        camera.far  = Constants.FAR_PLANE;

        position = new Vector3(0f, 0f, 0f);
        updateCamera(0f);
    }

    // ── Main update ──────────────────────────────────────────
    public void update(float dt) {
        // Clamp delta to avoid physics explosion on frame lag
        if (dt > Constants.DELTA_MAX) dt = Constants.DELTA_MAX;

        // ── Movement ─────────────────────────────────────────
        // Forward/strafe vectors (yaw only, no pitch)
        float cosYaw = MathUtils.cosDeg(yaw);
        float sinYaw = MathUtils.sinDeg(yaw);

        float forward = moveY * Constants.PLAYER_SPEED * dt;
        float strafe  = moveX * Constants.PLAYER_SPEED * dt;

        position.x += cosYaw * forward - sinYaw * strafe;
        position.z += sinYaw * forward + cosYaw * strafe;
        // Y stays 0 — we're walking on a flat plane

        // ── Look ─────────────────────────────────────────────
        yaw   += lookX * Constants.TOUCH_SENSITIVITY;
        pitch += lookY * Constants.TOUCH_SENSITIVITY;
        pitch  = MathUtils.clamp(pitch, Constants.PITCH_MIN, Constants.PITCH_MAX);

        // Apply recoil recovery
        if (recoilAccum > 0f) {
            float recoilRecovery = 60f * dt; // degrees per second
            float recover = Math.min(recoilAccum, recoilRecovery);
            pitch -= recover;
            recoilAccum -= recover;
        }

        // ── Health Regen ─────────────────────────────────────
        updateHealthRegen(dt);

        // ── ADS FOV interpolation ────────────────────────────
        float fovCurrent = camera.fieldOfView;
        float target = adsPressed ? Constants.FOV_ADS : Constants.FOV_DEFAULT;
        float fovLerped = fovCurrent + (target - fovCurrent)
                * Constants.ADS_INTERP_SPEED * dt;
        camera.fieldOfView = fovLerped;

        updateCamera(dt);

        // Reset per-frame input deltas
        lookX = 0f;
        lookY = 0f;
    }

    // ── Recoil ───────────────────────────────────────────────
    public void applyRecoil(float amount) {
        pitch -= amount;
        recoilAccum += amount * 0.3f;
    }

    // ── Damage ───────────────────────────────────────────────
    public boolean takeDamage(float amount) {
        if (health <= 0f) return false;
        health -= amount;
        lastHitTime = 0f;
        regenState = REGEN_DELAY;
        if (health <= 0f) {
            health = 0f;
            return true; // dead
        }
        return false;
    }

    public void heal(float amount) {
        health = Math.min(healthMax, health + amount);
    }

    public void addPoints(int amount) {
        points += amount;
    }

    public boolean isAlive() {
        return health > 0f;
    }

    public void reset() {
        health      = Constants.PLAYER_HEALTH_MAX;
        points      = 0;
        round       = 0;
        zombieKills = 0;
        headshots   = 0;
        position.set(0f, 0f, 0f);
        yaw   = 0f;
        pitch = 0f;
        fovTarget = Constants.FOV_DEFAULT;
        camera.fieldOfView = Constants.FOV_DEFAULT;
        recoilAccum = 0f;
        regenState = REGEN_IDLE;
        lastHitTime = 0f;
    }

    // ── Private ──────────────────────────────────────────────
    private void updateCamera(float dt) {
        // Eye height: player stands at y=0, camera at PLAYER_HEIGHT above feet
        camera.position.set(position.x, Constants.PLAYER_HEIGHT, position.z);
        camera.direction.set(
                MathUtils.sinDeg(yaw) * MathUtils.cosDeg(pitch),
                MathUtils.sinDeg(pitch),
                MathUtils.cosDeg(yaw) * MathUtils.cosDeg(pitch)
        ).nor();
        camera.up.set(0f, 1f, 0f);
        camera.update();
    }

    private void updateHealthRegen(float dt) {
        switch (regenState) {
            case REGEN_IDLE:
                if (health < healthMax) {
                    regenState = REGEN_DELAY;
                    lastHitTime = 0f;
                }
                break;
            case REGEN_DELAY:
                lastHitTime += dt;
                if (lastHitTime >= Constants.HEALTH_REGEN_DELAY && health < healthMax) {
                    regenState = REGEN_ACTIVE;
                }
                break;
            case REGEN_ACTIVE:
                health += Constants.HEALTH_REGEN_RATE * dt;
                if (health >= healthMax) {
                    health = healthMax;
                    regenState = REGEN_IDLE;
                }
                break;
        }
    }
}

package com.codzombies;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.MathUtils;

/**
 * Player state: FPS camera orientation, health + delayed regen, ammo + reload,
 * and a hitscan-shot query object the GameScreen consumes to apply damage.
 *
 * Design notes:
 *  - The player does NOT own the OpenGL camera. It stores orientation as
 *    yaw (around world-up Y) and pitch; a heading vector is derived on demand
 *    from yaw/pitch via spherical->cartesian conversion (see aimVector()).
 *  - Position is a top-down XZ vector stored in a Vector3 where .y == 0;
 *    the GameScreen lifts it to eye height when writing the camera.
 *  - Hitscan is REQUEST-only: fire() fills a pending Shot record; the
 *    GameScreen reads & clears it, then asks ZombieManager to resolve the ray
 *    against the world. This keeps OpenGL/collision concerns out of the player.
 */
public class Player {

    // ── Movement request (set by TouchControls, consumed by the GameScreen) ──
    public final Vector3 position = new Vector3(0f, 0f, 0f);

    // ── Look orientation ──────────────────────────────────────────
    /** Yaw in degrees: rotation around world-up Y, 0 = looking toward +Z. */
    public float yaw   = 0f;
    /** Pitch in degrees: positive = looking up, clamped to [PITCH_MIN, PITCH_MAX]. */
    public float pitch = 0f;

    // ── Health / regen ───────────────────────────────────────────
    private float health = Constants.PLAYER_HEALTH_MAX;
    private float timeSinceDamage = 0f;
    private boolean alive = true;

    // ── Movement request (set by TouchControls, consumed by Gizun jährlem GameScreen) ─
    // Normalized in [-1,1]. X = strafe (right positive), Z = forward (-Z forward).
    public float inputForward = 0f;
    public float inputStrafe  = 0f;
    public boolean adsActive  = false;   // ADS slows the player; toggled by the HUD

    // ── Weapon: a single rifle for this build (clip + reserve + reload) ──
    private static final int CLIP_SIZE   = 30;
    private static final int RESERVE_MAX = 240;
    private static final float FIRE_INTERVAL = 0.10f;   // 600 RPM
    private static final float RELOAD_TIME    = 2.0f;
    private int  clip    = CLIP_SIZE;
    private int  reserve = RESERVE_MAX;
    private boolean reloading = false;
    private float   reloadTimer = 0f;
    private float   fireCooldown = 0f;

    // ── Hitscan request (consumed by GameScreen each frame) ──
    /** Non-null on a frame where a shot should be resolved, then cleared by the GameScreen. */
    public Shot pendingShot = null;

    public static final class Shot {
        public final Vector3 origin = new Vector3();
        public final Vector3 direction = new Vector3();
        public final float damage;
        public Shot(Vector3 o, Vector3 d, float dmg) { origin.set(o); direction.set(d); damage = dmg; }
    }

    // ── Damage feedback (read by GameScreen to tint the screen) ──
    /** 0..1, decays each frame; bumped toward 1 when the player takes damage. */
    public float damageFlash = 0f;

    // =================================================================
    //  UPDATE
    // =================================================================

    /**
     * Advance player state by dt seconds.
     * @param dt      Clamped frame delta (seconds).
     * @param moveX   Joystick strafe   [-1..1] (+ = right)
     * @param moveZ   Joystick forward  [ 1..-1] (+ = forward). Forward is -Z in world,
     *                 so GameScreen maps +forward to -Z when integrating position.
     */
    public void update(float dt, float moveX, float moveZ) {
        // Cache input; GameScreen integrates the actual world-space movement
        // because that's where wall collision lives.
        inputForward = moveZ;
        inputStrafe  = moveX;

        // ── Reload timer ──
        if (reloading) {
            reloadTimer -= dt;
            if (reloadTimer <= 0f) finishReload();
        }

        // ── Fire rate cooldown ──
        if (fireCooldown > 0f) fireCooldown -= dt;

        // ── Health regen ──
        // COD Zombies-style: after HEALTH_REGEN_DELAY seconds without taking damage,
        // health ticks back up at HEALTH_REGEN_RATE HP/s. A single hit resets the timer.
        timeSinceDamage += dt;
        if (alive && timeSinceDamage >= Constants.HEALTH_REGEN_DELAY
                && health < Constants.PLAYER_HEALTH_MAX) {
            health = Math.min(Constants.PLAYER_HEALTH_MAX,
                              health + Constants.HEALTH_REGEN_RATE * dt);
        }

        // ── Damage flash fade ──
        if (damageFlash > 0f) damageFlash = Math.max(0f, damageFlash - dt * 1.5f);
    }

    // =================================================================
    //  COMBAT
    // =================================================================

    /**
     * Attempt to fire one shot this frame.
     * @param eye     Camera/world world-space origin of the ray (player eye height).
     *                Forward vector is derived from yaw/pitch.
     * @return true if a shot was actually fired (clip had ammo, not reloading,
     *                fire cooldown elapsed). The GameScreen then reads pendingShot.
     */
    public boolean tryFire(Vector3 eye) {
        if (!alive || reloading) return false;
        if (fireCooldown > 0f) return false;
        if (clip <= 0) return false;

        clip--;
        fireCooldown = FIRE_INTERVAL;

        // Build the hitscan request: origin = eye, direction = camera forward.
        Vector3 dir = aimVector(new Vector3());
        pendingShot = new Shot(eye, dir, 40f);
        return true;
    }

    /** Called by GameScreen after it has used pendingShot to apply damage. */
    public void clearPendingShot() { pendingShot = null; }

    /** Begin a reload if possible (clip not full, reserve available, not already reloading). */
    public void startReload() {
        if (reloading) return;
        if (clip >= CLIP_SIZE) return;
        if (reserve <= 0) return;
        reloading = true;
        reloadTimer = RELOAD_TIME;
    }

    private void finishReload() {
        int need = CLIP_SIZE - clip;
        int take = Math.min(need, reserve);
        clip    += take;
        reserve -= take;
        reloading = false;
        reloadTimer = 0f;
    }

    // =================================================================
    //  DAMAGE / HEALTH
    // =================================================================

    public void takeDamage(float amount) {
        if (!alive) return;
        health -= amount;
        timeSinceDamage = 0f;          // reset regen delay on every hit
        damageFlash = Math.min(1f, damageFlash + amount / Constants.PLAYER_HEALTH_MAX);
        if (health <= 0f) {
            health = 0f;
            alive = false;
        }
    }

    public void reset() {
        position.set(0f, 0f, 0f);
        yaw = 0f; pitch = 0f;
        health = Constants.PLAYER_HEALTH_MAX;
        timeSinceDamage = 0f;
        alive = true;
        clip = CLIP_SIZE;
        reserve = RESERVE_MAX;
        reloading = false; reloadTimer = 0f; fireCooldown = 0f;
        damageFlash = 0f;
        pendingShot = null;
    }

    // =================================================================
    //  AIM MATH
    // =================================================================

    /**
     * Convert (yaw, pitch) into a unit world-space forward vector.
     *
     * We use a right-handed Y-up convention where yaw=0 looks toward +Z.
     * Standard spherical->cartesian with yaw measured from +Z toward +X
     * (i.e. rotating the camera left = +yaw), and pitch measured from
     * the XZ plane toward +Y:
     *
     *     dirX = sin(yaw) * cos(pitch)
     *     dirY = sin(pitch)
     *     dirZ = cos(yaw) * cos(pitch)
     *
     * Angles are stored in degrees, so we convert to radians on the fly.
     *
     * @param out Reused output vector (avoids alloc in the hot loop).
     */
    public Vector3 aimVector(Vector3 out) {
        float yawRad   = yaw   * MathUtils.degreesToRadians;
        float pitchRad = pitch * MathUtils.degreesToRadians;
        float cp = MathUtils.cos(pitchRad);
        out.set(MathUtils.sin(yawRad) * cp,
                MathUtils.sin(pitchRad),
                MathUtils.cos(yawRad) * cp);
        return out.nor();
    }

    /** Unit world-forward from yaw only (for strafe math; pitch irrelevant). */
    private static Vector3 forwardVec(float yawDeg, Vector3 out) {
        float yawRad = yawDeg * MathUtils.degreesToRadians;
        return out.set(MathUtils.sin(yawRad), 0f, MathUtils.cos(yawRad));
    }

    /** Unit world-right from yaw only. */
    private static Vector3 rightVec(float yawDeg, Vector3 out) {
        // Right = forward × worldUp(0,1,0) = (cos, 0, -sin)
        float yawRad = yawDeg * MathUtils.degreesToRadians;
        return out.set(MathUtils.cos(yawRad), 0f, -MathUtils.sin(yawRad));
    }

    /**
     * Apply one frame of movement request to position, using yaw to map
     * joystick input into worldspace. Caller handles collision/wall pushback
     * after this returns. Movement is scaled by ADS slowdown when aiming.
     */
    public void integrateMovement(float dt) {
        float speed = Constants.PLAYER_SPEED * (adsActive ? 0.45f : 1f);
        Vector3 fwd = forwardVec(yaw, new Vector3());
        Vector3 rgt = rightVec(yaw, new Vector3());

        // inputForward is +1 forward for joystick-up. Forward in world is -Z when yaw=0,
        // which is exactly what forwardVec() returns; so add forward * inputForward.
        position.x += (fwd.x * inputForward + rgt.x * inputStrafe) * speed * dt;
        position.z += (fwd.z * inputForward + rgt.z * inputStrafe) * speed * dt;
    }

    // =================================================================
    //  ACCESSORS
    // =================================================================
    public float getHealth()        { return health; }
    public int   getClip()          { return clip; }
    public int   getReserve()       { return reserve; }
    public int   getClipSize()      { return CLIP_SIZE; }
    public boolean isReloading()    { return reloading; }
    public float getReloadTimer()   { return reloadTimer; }
    public float getReloadTime()    { return RELOAD_TIME; }
    public boolean isAlive()        { return alive; }
    public float getTimeSinceDamage() { return timeSinceDamage; }
}

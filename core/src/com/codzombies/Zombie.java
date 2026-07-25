package com.codzombies;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Individual zombie entity with a simple state machine.
 * Pre-allocated via ObjectPool — never use new in hot loops.
 */
public class Zombie {

    // ── State machine ────────────────────────────────────────
    public static final int STATE_SPAWNING  = 0;
    public static final int STATE_PURSUING  = 1;
    public static final int STATE_ATTACKING = 2;
    public static final int STATE_DEATH     = 3;

    public int state = STATE_DEATH;

    // ── Position / Movement ──────────────────────────────────
    public final Vector3 position    = new Vector3();
    public final Vector3 velocity    = new Vector3();
    public float speed;
    public float health;
    public float maxHealth;

    // ── Combat ───────────────────────────────────────────────
    public float attackCooldown = 0f;
    public float damagePerHit   = Constants.ZOMBIE_ATTACK_DAMAGE;

    // ── Visual ──────────────────────────────────────────────
    public float deathTimer     = 0f;
    public float spawnTimer     = 0f;
    public float hitFlashTimer  = 0f;    // white flash on damage
    public int   bodyPartHits   = 0;     // headshot flag

    // ── Animation (simple bob) ───────────────────────────────
    public float bobPhase = 0f;

    /** Reset all fields for pool reuse. */
    public void init(float x, float z, float health, float speed) {
        position.set(x, 0f, z);
        velocity.set(0f, 0f, 0f);
        this.health    = health;
        this.maxHealth = health;
        this.speed     = speed;
        state          = STATE_SPAWNING;
        spawnTimer     = 1.0f;
        attackCooldown = 0f;
        deathTimer     = 0f;
        hitFlashTimer  = 0f;
        bodyPartHits   = 0;
        bobPhase       = MathUtils.random(0f, MathUtils.PI2);
    }

    /** Mark as dead and return to pool. */
    public void kill() {
        state      = STATE_DEATH;
        deathTimer = 1.5f;
        health     = 0f;
    }

    public boolean isDead() {
        return state == STATE_DEATH;
    }

    public boolean isActive() {
        return state != STATE_DEATH;
    }

    /** Distance to a position (squared, for cheap comparisons). */
    public float dist2To(float x, float z) {
        float dx = position.x - x;
        float dz = position.z - z;
        return dx * dx + dz * dz;
    }
}
